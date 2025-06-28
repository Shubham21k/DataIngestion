package com.example.metrics.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing ingestion pipeline metrics
 */
@Entity
@Table(name = "ingestion_metrics", indexes = {
    @Index(name = "idx_pipeline_timestamp", columnList = "pipeline, timestamp"),
    @Index(name = "idx_dataset_timestamp", columnList = "dataset, timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
@EntityListeners(AuditingEntityListener.class)
public class IngestionMetric {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Column(nullable = false)
    private String pipeline; // phase1_orders, phase2_orders
    
    @NotBlank
    @Column(nullable = false)
    private String dataset;
    
    @NotNull
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @PositiveOrZero
    @Column(name = "records_processed")
    private Long recordsProcessed;
    
    @PositiveOrZero
    @Column(name = "records_failed")
    private Long recordsFailed;
    
    @PositiveOrZero
    @Column(name = "duration_ms")
    private Long durationMs;
    
    @PositiveOrZero
    @Column(name = "lag_seconds")
    private Long lagSeconds;
    
    @Column(name = "throughput_rps")
    private Double throughputRps; // records per second
    
    @Column(name = "error_rate")
    private Double errorRate; // percentage
    
    @Column(name = "batch_id")
    private String batchId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetricStatus status;
    
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public IngestionMetric() {}
    
    public IngestionMetric(String pipeline, String dataset, LocalDateTime timestamp) {
        this.pipeline = pipeline;
        this.dataset = dataset;
        this.timestamp = timestamp;
        this.status = MetricStatus.SUCCESS;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getPipeline() { return pipeline; }
    public void setPipeline(String pipeline) { this.pipeline = pipeline; }
    
    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public Long getRecordsProcessed() { return recordsProcessed; }
    public void setRecordsProcessed(Long recordsProcessed) { this.recordsProcessed = recordsProcessed; }
    
    public Long getRecordsFailed() { return recordsFailed; }
    public void setRecordsFailed(Long recordsFailed) { this.recordsFailed = recordsFailed; }
    
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    
    public Long getLagSeconds() { return lagSeconds; }
    public void setLagSeconds(Long lagSeconds) { this.lagSeconds = lagSeconds; }
    
    public Double getThroughputRps() { return throughputRps; }
    public void setThroughputRps(Double throughputRps) { this.throughputRps = throughputRps; }
    
    public Double getErrorRate() { return errorRate; }
    public void setErrorRate(Double errorRate) { this.errorRate = errorRate; }
    
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    
    public MetricStatus getStatus() { return status; }
    public void setStatus(MetricStatus status) { this.status = status; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public enum MetricStatus {
        SUCCESS, FAILURE, WARNING
    }
}
