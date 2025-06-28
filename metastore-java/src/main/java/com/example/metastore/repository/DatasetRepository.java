package com.example.metastore.repository;

import com.example.metastore.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Dataset entity operations.
 */
@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    /**
     * Find dataset by name.
     * @param name the dataset name
     * @return Optional containing the dataset if found
     */
    Optional<Dataset> findByName(String name);

    /**
     * Check if dataset exists by name.
     * @param name the dataset name
     * @return true if dataset exists
     */
    boolean existsByName(String name);

    /**
     * Find dataset by Kafka topic.
     * @param kafkaTopic the Kafka topic name
     * @return Optional containing the dataset if found
     */
    Optional<Dataset> findByKafkaTopic(String kafkaTopic);

    /**
     * Find dataset with schema versions eagerly loaded.
     * @param id the dataset ID
     * @return Optional containing the dataset with schema versions
     */
    @Query("SELECT d FROM Dataset d LEFT JOIN FETCH d.schemaVersions WHERE d.id = :id")
    Optional<Dataset> findByIdWithSchemaVersions(@Param("id") Long id);

    /**
     * Find dataset by name with schema versions eagerly loaded.
     * @param name the dataset name
     * @return Optional containing the dataset with schema versions
     */
    @Query("SELECT d FROM Dataset d LEFT JOIN FETCH d.schemaVersions WHERE d.name = :name")
    Optional<Dataset> findByNameWithSchemaVersions(@Param("name") String name);
}
