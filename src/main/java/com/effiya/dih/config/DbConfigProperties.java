package com.effiya.dih.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
public class DbConfigProperties {

    private int batchSize = 5000;
    private List<DatabaseConfig> databases;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public List<DatabaseConfig> getDatabases() {
        return databases;
    }

    public void setDatabases(List<DatabaseConfig> databases) {
        this.databases = databases;
    }

    public static class DatabaseConfig {
        private String name;
        private String url;
        private String username;
        private String password;
        private String schema;
        private List<TableConfig> tables;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public List<TableConfig> getTables() {
            return tables;
        }

        public void setTables(List<TableConfig> tables) {
            this.tables = tables;
        }
    }

    public static class TableConfig {
        private String name;
        private String columns;
        private String primaryKey;
        private String core;
        private String fields;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getColumns() {
            return columns;
        }

        public void setColumns(String columns) {
            this.columns = columns;
        }

        public String getPrimaryKey() {
            return primaryKey;
        }

        public void setPrimaryKey(String primaryKey) {
            this.primaryKey = primaryKey;
        }

        public String getCore() {
            return core;
        }

        public void setCore(String core) {
            this.core = core;
        }

        public String getFields() {
            return fields;
        }

        public void setFields(String fields) {
            this.fields = fields;
        }
    }
}
