package com.effiya.dih.dao;

import com.effiya.dih.config.DbConfigProperties;
import org.springframework.jdbc.core.RowCallbackHandler;

public interface RequestServiceDao {
    void processTableData(DbConfigProperties.DatabaseConfig dbConfig, DbConfigProperties.TableConfig tableConfig, int fetchSize, RowCallbackHandler rowCallbackHandler);
}
