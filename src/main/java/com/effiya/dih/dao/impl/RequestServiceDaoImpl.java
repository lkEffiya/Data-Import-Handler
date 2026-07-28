package com.effiya.dih.dao.impl;

import com.effiya.dih.config.DbConfigProperties;
import com.effiya.dih.dao.RequestServiceDao;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
            config.setAutoCommit(false);
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

        String sql = tableConfig.getQuery();
        if (sql == null || sql.trim().isEmpty()) {
            String tableName = (dbConfig.getSchema() != null && !dbConfig.getSchema().isEmpty())
                    ? dbConfig.getSchema() + "." + tableConfig.getName()
                    : tableConfig.getName();

            sql = "SELECT " + tableConfig.getColumns() + " FROM " + tableName;
            if (tableConfig.getPrimaryKey() != null && !tableConfig.getPrimaryKey().isEmpty()) {
                sql += " ORDER BY " + tableConfig.getPrimaryKey() + " ASC";
            }
        }

        jdbcTemplate.query(sql, rowCallbackHandler);
    }

    @Override
    public void processIncrementalTableData(DbConfigProperties.DatabaseConfig dbConfig, DbConfigProperties.TableConfig tableConfig, int fetchSize, java.time.LocalDateTime lastSyncDttm, String lastSyncPrimaryKey, RowCallbackHandler rowCallbackHandler) {
        HikariDataSource dataSource = getDataSource(dbConfig);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(fetchSize);
        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);

        Object pkParam = lastSyncPrimaryKey;
        try {
            pkParam = Long.parseLong(lastSyncPrimaryKey);
        } catch (NumberFormatException e) {
            // Keep as string if it's not a number (e.g. UUID)
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("lastSyncDttm", java.sql.Timestamp.valueOf(lastSyncDttm));
        params.addValue("lastSyncPrimaryKey", pkParam);

        String sql = tableConfig.getDeltaQuery();
        if (sql == null || sql.trim().isEmpty()) {
            String tableName = (dbConfig.getSchema() != null && !dbConfig.getSchema().isEmpty())
                    ? dbConfig.getSchema() + "." + tableConfig.getName()
                    : tableConfig.getName();

            String columns = tableConfig.getColumns() != null ? tableConfig.getColumns() : "*";
            String primaryKey = tableConfig.getPrimaryKey();

            sql = "SELECT " + columns + " FROM " + tableName + 
                         " WHERE updated_dttm > :lastSyncDttm" +
                         " OR (updated_dttm = :lastSyncDttm AND " + primaryKey + " > :lastSyncPrimaryKey) " +
                         " ORDER BY updated_dttm ASC, " + primaryKey + " ASC";
        }
        
        namedJdbcTemplate.query(sql, params, rowCallbackHandler);
    }

    @Override
    public void processArchivedTableData(DbConfigProperties.DatabaseConfig dbConfig, DbConfigProperties.ArchivedTableConfig archiveConfig, String primaryKeyColumn, int fetchSize, java.time.LocalDateTime lastSyncDttm, RowCallbackHandler rowCallbackHandler) {
        if (archiveConfig == null || archiveConfig.getName() == null || archiveConfig.getName().isEmpty()) {
            return;
        }
        HikariDataSource dataSource = getDataSource(dbConfig);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(fetchSize);
        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("lastSyncDttm", java.sql.Timestamp.valueOf(lastSyncDttm));

        String sql = archiveConfig.getDeletePkQuery();
        if (sql == null || sql.trim().isEmpty()) {
            String tableName = (dbConfig.getSchema() != null && !dbConfig.getSchema().isEmpty())
                    ? dbConfig.getSchema() + "." + archiveConfig.getName()
                    : archiveConfig.getName();

            sql = "SELECT " + primaryKeyColumn + " FROM " + tableName +
                         " WHERE updated_dttm > :lastSyncDttm";
        }

        namedJdbcTemplate.query(sql, params, rowCallbackHandler);
    }

    @PreDestroy
    public void cleanup() {
        for (HikariDataSource ds : dataSourceCache.values()) {
            if (ds != null && ds.isRunning()) {
                ds.close();
            }
        }
    }
}
