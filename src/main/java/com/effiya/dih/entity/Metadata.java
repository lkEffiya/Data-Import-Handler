package com.effiya.dih.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dih_metadata_lk")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Metadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "db_name")
    private String dbName;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "solr_core")
    private String core;

    @Column(name = "created_dttm")
    private LocalDateTime createdDttm;

    @Column(name = "updated_dttm")
    private LocalDateTime updatedDttm;

    @Column(name = "last_sync_timestamp")
    private LocalDateTime lastSyncTimestamp;

    @Column(name = "last_sync_primary_key")
    private String lastSyncPrimaryKey;
}
