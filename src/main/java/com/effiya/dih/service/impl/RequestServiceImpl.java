package com.effiya.dih.service.impl;

import com.effiya.dih.config.DbConfigProperties;
import com.effiya.dih.dao.RequestServiceDao;
import com.effiya.dih.entity.Metadata;
import com.effiya.dih.repository.MetadataRepository;
import com.effiya.dih.service.RequestService;
import com.effiya.dih.service.helper.SolrApiServiceHelper;
import com.effiya.dih.service.helper.SolrDocumentServiceHelper;
import com.effiya.dih.service.helper.UpdateMetadataServiceHelper;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RequestServiceImpl implements RequestService {

    private static final Logger logger = LoggerFactory.getLogger(RequestServiceImpl.class);

    private final DbConfigProperties dbConfigProperties;
    private final ObjectMapper objectMapper;
    private final RequestServiceDao requestDao;
    private final SolrApiServiceHelper solrApiServiceHelper;
    private final SolrDocumentServiceHelper solrDocumentServiceHelper;
    private final UpdateMetadataServiceHelper updateMetadataServiceHelper;
    private final MetadataRepository metadataRepository;

    public RequestServiceImpl(DbConfigProperties dbConfigProperties, ObjectMapper objectMapper, RequestServiceDao requestDao,
                              SolrApiServiceHelper solrApiServiceHelper, SolrDocumentServiceHelper solrDocumentServiceHelper,
                              UpdateMetadataServiceHelper updateMetadataServiceHelper, MetadataRepository metadataRepository) {
        this.dbConfigProperties = dbConfigProperties;
        this.objectMapper = objectMapper;
        this.requestDao = requestDao;
        this.solrApiServiceHelper = solrApiServiceHelper;
        this.solrDocumentServiceHelper = solrDocumentServiceHelper;
        this.updateMetadataServiceHelper = updateMetadataServiceHelper;
        this.metadataRepository = metadataRepository;
    }

    @Override
    public void fullLoad() {
        if (dbConfigProperties.getDatabases() == null) return;

        for (DbConfigProperties.DatabaseConfig dbConfig : dbConfigProperties.getDatabases()) {
            if (dbConfig.getTables() == null) continue;

            for (DbConfigProperties.TableConfig tableConfig : dbConfig.getTables()) {
                String coreName = tableConfig.getCore();
                if (coreName == null || coreName.isEmpty()) continue;

                logger.info("Starting FULL LOAD for table: {} to core: {}", tableConfig.getName(), coreName);
                
                // Clear the core before full load
                solrApiServiceHelper.clearCore(coreName);

                List<Map<String, Object>> batch = new ArrayList<>();
                final String[] lastSyncPk = {null};
                final String[] lastSuccessfulPk = {null};
                LocalDateTime syncTime = LocalDateTime.now();

                try {
                    requestDao.processTableData(dbConfig, tableConfig, dbConfigProperties.getBatchSize(), rs -> {
                        try {
                            java.sql.ResultSetMetaData metaData = rs.getMetaData();
                            int columnCount = metaData.getColumnCount();
                            Map<String, Object> row = new java.util.HashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                Object val = rs.getObject(i);
                                if (val != null) {
                                    row.put(metaData.getColumnLabel(i), val);
                                }
                            }
                            batch.add(row);
                            
                            if (tableConfig.getPrimaryKey() != null && row.get(tableConfig.getPrimaryKey()) != null) {
                                lastSyncPk[0] = String.valueOf(row.get(tableConfig.getPrimaryKey()));
                            }

                            if (batch.size() >= dbConfigProperties.getBatchSize()) {
                                processBatch(coreName, tableConfig, batch, lastSyncPk[0], lastSuccessfulPk);
                            }
                        } catch (Exception e) {
                            logger.error("Error processing row during full load", e);
                            throw new RuntimeException("Error processing row during full load", e);
                        }
                    });

                    // Process remaining
                    if (!batch.isEmpty()) {
                        processBatch(coreName, tableConfig, batch, lastSyncPk[0], lastSuccessfulPk);
                    }

                    if (lastSuccessfulPk[0] != null) {
                        solrApiServiceHelper.commit(coreName);
                        updateMetadataServiceHelper.updateMetadata(dbConfig.getName(), tableConfig.getName(), coreName, syncTime, lastSuccessfulPk[0]);
                    } else if (lastSyncPk[0] == null) {
                        updateMetadataServiceHelper.updateMetadata(dbConfig.getName(), tableConfig.getName(), coreName, syncTime, "0");
                    }
                } catch (Exception e) {
                    logger.error("Exception occurred during full load for table {}. Checkpointing successful batches.", tableConfig.getName(), e);
                    if (lastSuccessfulPk[0] != null) {
                        try {
                            solrApiServiceHelper.commit(coreName);
                            updateMetadataServiceHelper.updateMetadata(dbConfig.getName(), tableConfig.getName(), coreName, syncTime, lastSuccessfulPk[0]);
                            logger.info("Successfully checkpointed data up to PK {} before throwing exception.", lastSuccessfulPk[0]);
                        } catch (Exception checkpointEx) {
                            logger.error("Failed to checkpoint during exception handling", checkpointEx);
                        }
                    }
                    throw e;
                }
            }
        }
    }

    @Override
    public void incrementalLoad() {
        if (dbConfigProperties.getDatabases() == null) return;

        for (DbConfigProperties.DatabaseConfig dbConfig : dbConfigProperties.getDatabases()) {
            if (dbConfig.getTables() == null && dbConfig.getArchivedTables() == null) continue;

            for (int i = 0; i < dbConfig.getTables().size(); i++) {
                DbConfigProperties.TableConfig tableConfig = dbConfig.getTables().get(i);
                String coreName = tableConfig.getCore();
                if (coreName == null || coreName.isEmpty()) continue;

                Optional<Metadata> metaOpt = metadataRepository.findByDbNameAndTableNameAndCore(dbConfig.getName(), tableConfig.getName(), coreName);
                
                if (metaOpt.isEmpty() || metaOpt.get().getLastSyncTimestamp() == null) {
                    logger.warn("No metadata found for incremental load on table: {}. Please run full load first.", tableConfig.getName());
                    continue;
                }

                Metadata meta = metaOpt.get();
                LocalDateTime lastSyncDttm = meta.getLastSyncTimestamp();
                String lastSyncPrimaryKey = meta.getLastSyncPrimaryKey() != null ? meta.getLastSyncPrimaryKey() : "0";

                logger.info("Starting INCREMENTAL LOAD for table: {} from timestamp: {}", tableConfig.getName(), lastSyncDttm);

                DbConfigProperties.ArchivedTableConfig archiveConfig = null;
                
                if (dbConfig.getArchivedTables() != null && dbConfig.getArchivedTables().size() > i) {
                    archiveConfig = dbConfig.getArchivedTables().get(i);
                }

                if (archiveConfig != null && archiveConfig.getName() != null && !archiveConfig.getName().isEmpty()) {
                    logger.info("Processing deletions from archive table: {}", archiveConfig.getName());
                    List<String> deleteIds = new ArrayList<>();
                    String finalArchiveTablePrimaryKey = archiveConfig.getPrimaryKey() != null ? archiveConfig.getPrimaryKey() : tableConfig.getPrimaryKey();
                    requestDao.processArchivedTableData(dbConfig, archiveConfig, finalArchiveTablePrimaryKey, dbConfigProperties.getBatchSize(), lastSyncDttm, rs -> {
                        try {
                            String pkValue = String.valueOf(rs.getObject(finalArchiveTablePrimaryKey)).trim();
                            deleteIds.add(pkValue);
                            if (deleteIds.size() >= dbConfigProperties.getBatchSize()) {
                                solrApiServiceHelper.deleteDocumentsByIds(coreName, deleteIds);
                                deleteIds.clear();
                            }
                        } catch (Exception e) {
                            logger.error("Error processing row during archive data load", e);
                            throw new RuntimeException("Error processing row during archive data load", e);
                        }
                    });
                    if (!deleteIds.isEmpty()) {
                        System.out.println("deleted ids: "+deleteIds);
                        solrApiServiceHelper.deleteDocumentsByIds(coreName, deleteIds);
                    }
                }

                List<Map<String, Object>> batch = new ArrayList<>();
                final String[] lastSyncPk = {lastSyncPrimaryKey};
                final String[] lastSuccessfulPk = {null};
                LocalDateTime syncTime = LocalDateTime.now();

                try {
                    requestDao.processIncrementalTableData(dbConfig, tableConfig, dbConfigProperties.getBatchSize(), lastSyncDttm, lastSyncPrimaryKey, rs -> {
                        try {
                            java.sql.ResultSetMetaData metaData = rs.getMetaData();
                            int columnCount = metaData.getColumnCount();
                            Map<String, Object> row = new java.util.HashMap<>();
                            for (int colIdx = 1; colIdx <= columnCount; colIdx++) {
                                Object val = rs.getObject(colIdx);
                                if (val != null) {
                                    row.put(metaData.getColumnLabel(colIdx), val);
                                }
                            }
                            batch.add(row);

                            if (tableConfig.getPrimaryKey() != null && row.get(tableConfig.getPrimaryKey()) != null) {
                                lastSyncPk[0] = String.valueOf(row.get(tableConfig.getPrimaryKey()));
                            }

                            if (batch.size() >= dbConfigProperties.getBatchSize()) {
                                processBatch(coreName, tableConfig, batch, lastSyncPk[0], lastSuccessfulPk);
                            }
                        } catch (Exception e) {
                            logger.error("Error processing row during incremental load", e);
                            throw new RuntimeException("Error processing row during incremental load", e);
                        }
                    });

                    // Process remaining
                    if (!batch.isEmpty()) {
                        processBatch(coreName, tableConfig, batch, lastSyncPk[0], lastSuccessfulPk);
                    }

                    if (lastSuccessfulPk[0] != null) {
                        solrApiServiceHelper.commit(coreName);
                        updateMetadataServiceHelper.updateMetadata(dbConfig.getName(), tableConfig.getName(), coreName, syncTime, lastSuccessfulPk[0]);
                    } else {
                        // Update syncTime even if no new rows, so it advances the clock
                        updateMetadataServiceHelper.updateMetadata(dbConfig.getName(), tableConfig.getName(), coreName, syncTime, lastSyncPrimaryKey);
                    }
                } catch (Exception e) {
                    logger.error("Exception occurred during incremental load for table {}. Checkpointing successful batches.", tableConfig.getName(), e);
                    if (lastSuccessfulPk[0] != null) {
                        try {
                            solrApiServiceHelper.commit(coreName);
                            updateMetadataServiceHelper.updateMetadata(dbConfig.getName(), tableConfig.getName(), coreName, syncTime, lastSuccessfulPk[0]);
                            logger.info("Successfully checkpointed incremental data up to PK {} before throwing exception.", lastSuccessfulPk[0]);
                        } catch (Exception checkpointEx) {
                            logger.error("Failed to checkpoint during exception handling", checkpointEx);
                        }
                    }
                    throw e;
                }
            }
        }
    }

    private void processBatch(String coreName, DbConfigProperties.TableConfig tableConfig, List<Map<String, Object>> batch, String currentPk, String[] lastSuccessfulPk) {
        List<SolrInputDocument> docs = solrDocumentServiceHelper.convertToSolrDocuments(batch, tableConfig);
        solrApiServiceHelper.addDocuments(coreName, docs);
        lastSuccessfulPk[0] = currentPk;
        batch.clear();
    }

    @Override
    public void streamAllData(OutputStream outputStream) {
        try (JsonGenerator jg = objectMapper.getFactory().createGenerator(outputStream, JsonEncoding.UTF8)) {
            jg.writeStartObject(); // Start root object

            if (dbConfigProperties.getDatabases() != null) {
                for (DbConfigProperties.DatabaseConfig dbConfig : dbConfigProperties.getDatabases()) {
                    if (dbConfig.getTables() != null) {
                        for (DbConfigProperties.TableConfig tableConfig : dbConfig.getTables()) {
                            String key = dbConfig.getName() + "_" + tableConfig.getName();
                            jg.writeFieldName(key);
                            jg.writeStartArray(); // Start table data array

                            requestDao.processTableData(dbConfig, tableConfig, dbConfigProperties.getBatchSize(), rs -> {
                                try {
                                    int columnCount = rs.getMetaData().getColumnCount();
                                    jg.writeStartObject();
                                    for (int i = 1; i <= columnCount; i++) {
                                        String columnName = rs.getMetaData().getColumnLabel(i);
                                        Object value = rs.getObject(i);
                                        jg.writeObjectField(columnName, value);
                                    }
                                    jg.writeEndObject();
                                } catch (Exception e) {
                                    throw new RuntimeException("Error serializing row", e);
                                }
                            });

                            jg.writeEndArray(); // End table data array
                        }
                    }
                }
            }
            jg.writeEndObject(); // End root object
            jg.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error streaming JSON", e);
        }
    }
}
