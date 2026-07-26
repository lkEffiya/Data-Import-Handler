package com.effiya.dih.dao;

import com.effiya.dih.config.DbConfigProperties;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.time.LocalDateTime;

public interface RequestServiceDao {
    void processTableData(DbConfigProperties.DatabaseConfig dbConfig, DbConfigProperties.TableConfig tableConfig, int fetchSize, RowCallbackHandler rowCallbackHandler);
    void processIncrementalTableData(DbConfigProperties.DatabaseConfig dbConfig, DbConfigProperties.TableConfig tableConfig, int fetchSize, LocalDateTime lastSyncDttm, String lastSyncPrimaryKey, RowCallbackHandler rowCallbackHandler);
}
