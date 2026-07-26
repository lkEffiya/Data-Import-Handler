package com.effiya.dih.service.helper;

import com.effiya.dih.entity.Metadata;
import com.effiya.dih.repository.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UpdateMetadataServiceHelper {

    private static final Logger logger = LoggerFactory.getLogger(UpdateMetadataServiceHelper.class);
    private final MetadataRepository metadataRepository;

    public UpdateMetadataServiceHelper(MetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    @Transactional
    public void updateMetadata(String dbName, String tableName, String core, LocalDateTime syncTimestamp, String lastSyncPrimaryKey) {
        Metadata metadata = metadataRepository.findByDbNameAndTableNameAndCore(dbName, tableName, core)
                .orElseGet(() -> {
                    Metadata newMeta = new Metadata();
                    newMeta.setDbName(dbName);
                    newMeta.setTableName(tableName);
                    newMeta.setCore(core);
                    newMeta.setCreatedDttm(LocalDateTime.now());
                    return newMeta;
                });

        metadata.setUpdatedDttm(LocalDateTime.now());
        metadata.setLastSyncTimestamp(syncTimestamp);
        
        if (lastSyncPrimaryKey != null) {
            metadata.setLastSyncPrimaryKey(lastSyncPrimaryKey);
        }

        metadataRepository.save(metadata);
        logger.info("Updated metadata for table: {} to primary_key: {}", tableName, lastSyncPrimaryKey);
    }
}
