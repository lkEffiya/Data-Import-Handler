package com.effiya.dih.service.impl;

import com.effiya.dih.config.DbConfigProperties;
import com.effiya.dih.dao.RequestServiceDao;
import com.effiya.dih.service.RequestService;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;

@Service
public class RequestServiceImpl implements RequestService {

    private final DbConfigProperties dbConfigProperties;
    private final ObjectMapper objectMapper;
    private final RequestServiceDao requestDao;

    public RequestServiceImpl(DbConfigProperties dbConfigProperties, ObjectMapper objectMapper, RequestServiceDao requestDao) {
        this.dbConfigProperties = dbConfigProperties;
        this.objectMapper = objectMapper;
        this.requestDao = requestDao;
    }

    @Override
    public void streamAllData(OutputStream outputStream) {
        try (JsonGenerator jg = objectMapper.getFactory().createGenerator(outputStream, JsonEncoding.UTF8)) {
            jg.writeStartObject(); // Start root object

            if (dbConfigProperties.getDatabases() != null) {
                for (DbConfigProperties.DatabaseConfig dbConfig : dbConfigProperties.getDatabases()) {
                    if (dbConfig.getTables() != null) {
                        for (DbConfigProperties.TableConfig tableConfig : dbConfig.getTables()) {
                            String key = dbConfig.getUrl() + "_" + tableConfig.getName();
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
