package com.example.metastore.repository;

import com.example.metastore.entity.SchemaVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for SchemaVersion entity operations.
 */
@Repository
public interface SchemaVersionRepository extends JpaRepository<SchemaVersion, Long> {

    /**
     * Find all schema versions for a dataset ordered by version descending.
     * @param datasetId the dataset ID
     * @return list of schema versions
     */
    @Query("SELECT sv FROM SchemaVersion sv WHERE sv.dataset.id = :datasetId ORDER BY sv.version DESC")
    List<SchemaVersion> findByDatasetIdOrderByVersionDesc(@Param("datasetId") Long datasetId);

    /**
     * Find active schema version for a dataset.
     * @param datasetId the dataset ID
     * @return Optional containing the active schema version
     */
    @Query("SELECT sv FROM SchemaVersion sv WHERE sv.dataset.id = :datasetId AND sv.status = 'ACTIVE'")
    Optional<SchemaVersion> findActiveByDatasetId(@Param("datasetId") Long datasetId);

    /**
     * Find the latest version number for a dataset.
     * @param datasetId the dataset ID
     * @return the latest version number, or 0 if no versions exist
     */
    @Query("SELECT COALESCE(MAX(sv.version), 0) FROM SchemaVersion sv WHERE sv.dataset.id = :datasetId")
    Integer findLatestVersionByDatasetId(@Param("datasetId") Long datasetId);

    /**
     * Find schema version by dataset ID and version number.
     * @param datasetId the dataset ID
     * @param version the version number
     * @return Optional containing the schema version
     */
    Optional<SchemaVersion> findByDatasetIdAndVersion(Long datasetId, Integer version);

    /**
     * Count schema versions by dataset ID and status.
     * @param datasetId the dataset ID
     * @param status the schema status
     * @return count of schema versions
     */
    @Query("SELECT COUNT(sv) FROM SchemaVersion sv WHERE sv.dataset.id = :datasetId AND sv.status = :status")
    Long countByDatasetIdAndStatus(@Param("datasetId") Long datasetId, @Param("status") SchemaVersion.SchemaStatus status);
}
