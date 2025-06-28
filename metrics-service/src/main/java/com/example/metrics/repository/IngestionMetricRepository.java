package com.example.metrics.repository;

import com.example.metrics.entity.IngestionMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for ingestion metrics
 */
@Repository
public interface IngestionMetricRepository extends JpaRepository<IngestionMetric, Long> {
    
    // Pipeline-based queries
    Page<IngestionMetric> findByPipelineOrderByTimestampDesc(String pipeline, Pageable pageable);
    Page<IngestionMetric> findByPipelineAndTimestampBetweenOrderByTimestampDesc(String pipeline, LocalDateTime from, LocalDateTime to, Pageable pageable);
    List<IngestionMetric> findByPipelineAndTimestampBetween(String pipeline, LocalDateTime from, LocalDateTime to);
    List<IngestionMetric> findByPipelineAndTimestampAfterOrderByTimestampDesc(String pipeline, LocalDateTime after);
    
    // Dataset-based queries
    Page<IngestionMetric> findByDatasetOrderByTimestampDesc(String dataset, Pageable pageable);
    Page<IngestionMetric> findByDatasetAndTimestampBetweenOrderByTimestampDesc(String dataset, LocalDateTime from, LocalDateTime to, Pageable pageable);
    List<IngestionMetric> findByDatasetAndTimestampBetween(String dataset, LocalDateTime from, LocalDateTime to);
    List<IngestionMetric> findByDatasetAndTimestampAfterOrderByTimestampDesc(String dataset, LocalDateTime after);
    
    // Combined pipeline and dataset queries
    List<IngestionMetric> findByPipelineAndDatasetAndTimestampBetween(String pipeline, String dataset, LocalDateTime from, LocalDateTime to);
    List<IngestionMetric> findByPipelineAndDatasetAndTimestampAfterOrderByTimestampDesc(String pipeline, String dataset, LocalDateTime after);
    
    // Time-based queries
    List<IngestionMetric> findByTimestampBetween(LocalDateTime from, LocalDateTime to);
    List<IngestionMetric> findByTimestampAfterOrderByTimestampDesc(LocalDateTime after);
    
    // Status-based queries
    long countByStatusAndTimestampAfter(IngestionMetric.MetricStatus status, LocalDateTime after);
    long countByTimestampAfter(LocalDateTime after);
    
    // Alert queries
    @Query("SELECT m FROM IngestionMetric m WHERE m.timestamp > :since AND " +
           "(m.status = 'FAILURE' OR m.errorRate > 10.0 OR m.lagSeconds > 300)")
    List<IngestionMetric> findAlertMetrics(@Param("since") LocalDateTime since);
    
    // Performance queries
    @Query("SELECT AVG(m.throughputRps) FROM IngestionMetric m WHERE m.pipeline = :pipeline AND m.timestamp > :since")
    Double getAverageThroughputByPipeline(@Param("pipeline") String pipeline, @Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(m.lagSeconds) FROM IngestionMetric m WHERE m.dataset = :dataset AND m.timestamp > :since")
    Double getAverageLagByDataset(@Param("dataset") String dataset, @Param("since") LocalDateTime since);
    
    @Query("SELECT SUM(m.recordsProcessed) FROM IngestionMetric m WHERE m.timestamp BETWEEN :from AND :to")
    Long getTotalRecordsProcessed(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    
    @Query("SELECT COUNT(m) FROM IngestionMetric m WHERE m.status = 'SUCCESS' AND m.timestamp > :since")
    Long getSuccessfulBatchCount(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(m) FROM IngestionMetric m WHERE m.status = 'FAILURE' AND m.timestamp > :since")
    Long getFailedBatchCount(@Param("since") LocalDateTime since);
}
