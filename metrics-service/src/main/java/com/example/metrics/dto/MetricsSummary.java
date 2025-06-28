package com.example.metrics.dto;

import java.time.LocalDateTime;

/**
 * DTO for aggregated metrics summary
 */
public class MetricsSummary {
    
    private LocalDateTime fromTime;
    private LocalDateTime toTime;
    private Long totalRecordsProcessed;
    private Long totalRecordsFailed;
    private Long totalDurationMs;
    private Double avgThroughputRps;
    private Double avgErrorRate;
    private Double avgLagSeconds;
    private Long successfulBatches;
    private Long failedBatches;
    
    // Constructors
    public MetricsSummary() {}
    
    public MetricsSummary(LocalDateTime fromTime, LocalDateTime toTime, Long totalRecordsProcessed,
                         Long totalRecordsFailed, Long totalDurationMs, Double avgThroughputRps,
                         Double avgErrorRate, Double avgLagSeconds, Long successfulBatches, Long failedBatches) {
        this.fromTime = fromTime;
        this.toTime = toTime;
        this.totalRecordsProcessed = totalRecordsProcessed;
        this.totalRecordsFailed = totalRecordsFailed;
        this.totalDurationMs = totalDurationMs;
        this.avgThroughputRps = avgThroughputRps;
        this.avgErrorRate = avgErrorRate;
        this.avgLagSeconds = avgLagSeconds;
        this.successfulBatches = successfulBatches;
        this.failedBatches = failedBatches;
    }
    
    // Getters and Setters
    public LocalDateTime getFromTime() { return fromTime; }
    public void setFromTime(LocalDateTime fromTime) { this.fromTime = fromTime; }
    
    public LocalDateTime getToTime() { return toTime; }
    public void setToTime(LocalDateTime toTime) { this.toTime = toTime; }
    
    public Long getTotalRecordsProcessed() { return totalRecordsProcessed; }
    public void setTotalRecordsProcessed(Long totalRecordsProcessed) { this.totalRecordsProcessed = totalRecordsProcessed; }
    
    public Long getTotalRecordsFailed() { return totalRecordsFailed; }
    public void setTotalRecordsFailed(Long totalRecordsFailed) { this.totalRecordsFailed = totalRecordsFailed; }
    
    public Long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(Long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
    
    public Double getAvgThroughputRps() { return avgThroughputRps; }
    public void setAvgThroughputRps(Double avgThroughputRps) { this.avgThroughputRps = avgThroughputRps; }
    
    public Double getAvgErrorRate() { return avgErrorRate; }
    public void setAvgErrorRate(Double avgErrorRate) { this.avgErrorRate = avgErrorRate; }
    
    public Double getAvgLagSeconds() { return avgLagSeconds; }
    public void setAvgLagSeconds(Double avgLagSeconds) { this.avgLagSeconds = avgLagSeconds; }
    
    public Long getSuccessfulBatches() { return successfulBatches; }
    public void setSuccessfulBatches(Long successfulBatches) { this.successfulBatches = successfulBatches; }
    
    public Long getFailedBatches() { return failedBatches; }
    public void setFailedBatches(Long failedBatches) { this.failedBatches = failedBatches; }
}
