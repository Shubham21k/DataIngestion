package com.example.metrics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

/**
 * DTO for submitting ingestion metrics
 */
public class IngestionMetricRequest {
    
    @NotBlank(message = "Pipeline name is required")
    private String pipeline;
    
    @NotBlank(message = "Dataset name is required")
    private String dataset;
    
    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;
    
    @PositiveOrZero(message = "Records processed must be non-negative")
    private Long recordsProcessed;
    
    @PositiveOrZero(message = "Records failed must be non-negative")
    private Long recordsFailed;
    
    @PositiveOrZero(message = "Duration must be non-negative")
    private Long durationMs;
    
    @PositiveOrZero(message = "Lag must be non-negative")
    private Long lagSeconds;
    
    private String batchId;
    private String errorMessage;
    
    // Constructors
    public IngestionMetricRequest() {}
    
    public IngestionMetricRequest(String pipeline, String dataset, LocalDateTime timestamp) {
        this.pipeline = pipeline;
        this.dataset = dataset;
        this.timestamp = timestamp;
    }
    
    // Getters and Setters
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
    
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
