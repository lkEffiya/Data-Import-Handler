package com.effiya.dih.dao.impl;

import com.effiya.dih.config.DbConfigProperties;
import com.effiya.dih.dao.RequestServiceDao;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RequestServiceDaoImpl implements RequestServiceDao {

    private final Map<String, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    private HikariDataSource getDataSource(DbConfigProperties.DatabaseConfig dbConfig) {
        return dataSourceCache.computeIfAbsent(dbConfig.getUrl(), url -> {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbConfig.getUrl());
            config.setUsername(dbConfig.getUsername());
            config.setPassword(dbConfig.getPassword());
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            return new HikariDataSource(config);
        });
    }

    @Override
    public void processTableData(DbConfigProperties.DatabaseConfig dbConfig, DbConfigProperties.TableConfig tableConfig, int fetchSize, RowCallbackHandler rowCallbackHandler) {
        HikariDataSource dataSource = getDataSource(dbConfig);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(fetchSize);

        String tableName = (dbConfig.getSchema() != null && !dbConfig.getSchema().isEmpty())
                ? dbConfig.getSchema() + "." + tableConfig.getName()
                : tableConfig.getName();

        String sql = "SELECT " + tableConfig.getColumns() + " FROM " + tableName;

        jdbcTemplate.query(sql, rowCallbackHandler);
    }

    @Override
    public void processIncrementalTableData(DbConfigProperties.DatabaseConfig dbConfig, DbConfigProperties.TableConfig tableConfig, int fetchSize, java.time.LocalDateTime lastSyncDttm, String lastSyncPrimaryKey, RowCallbackHandler rowCallbackHandler) {
        HikariDataSource dataSource = getDataSource(dbConfig);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(fetchSize);

        String tableName = (dbConfig.getSchema() != null && !dbConfig.getSchema().isEmpty())
                ? dbConfig.getSchema() + "." + tableConfig.getName()
                : tableConfig.getName();

        String columns = tableConfig.getColumns() != null ? tableConfig.getColumns() : "*";
        String primaryKey=tableConfig.getPrimaryKey();
        
        // Basic incremental query logic
        String sql = "SELECT " + columns + " FROM " + tableName + 
                     " WHERE updated_dttm > '"+lastSyncDttm + "'" +
                     " OR (updated_dttm = '"+lastSyncDttm+"' AND " + primaryKey + " > "+lastSyncPrimaryKey+") " +
                     " ORDER BY updated_dttm ASC, " + primaryKey + " ASC";
        
        // Ensure you cast string primary keys correctly or let JDBC handle it
        jdbcTemplate.query(sql, rowCallbackHandler);
    }

    @PreDestroy
    public void cleanup() {
        for (HikariDataSource ds : dataSourceCache.values()) {
            if (ds != null && !ds.isClosed()) {
                ds.close();
            }
        }
    }
}
