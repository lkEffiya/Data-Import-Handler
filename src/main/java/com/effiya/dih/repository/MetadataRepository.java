package com.effiya.dih.repository;

import com.effiya.dih.entity.Metadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MetadataRepository extends JpaRepository<Metadata, Integer> {
    Optional<Metadata> findByDbNameAndTableNameAndCore(String dbName, String tableName, String core);
}
