package com.example.metrics.service;

import com.example.metrics.dto.IngestionMetricRequest;
import com.example.metrics.dto.MetricsSummary;
import com.example.metrics.entity.IngestionMetric;
import com.example.metrics.repository.IngestionMetricRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for metrics collection, aggregation, and querying
 */
@Service
@Transactional
public class MetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    
    @Autowired
    private IngestionMetricRepository metricRepository;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    // Prometheus metrics
    private final Counter recordsProcessedCounter;
    private final Counter recordsFailedCounter;
    private final Timer processingTimer;
    private final AtomicLong currentLag = new AtomicLong(0);
    private final AtomicLong currentThroughput = new AtomicLong(0);
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize Prometheus metrics
        this.recordsProcessedCounter = Counter.builder("ingestion_records_processed_total")
                .description("Total number of records processed")
                .tag("service", "ingestion-platform")
                .register(meterRegistry);
                
        this.recordsFailedCounter = Counter.builder("ingestion_records_failed_total")
                .description("Total number of records failed")
                .tag("service", "ingestion-platform")
                .register(meterRegistry);
                
        this.processingTimer = Timer.builder("ingestion_processing_duration")
                .description("Processing duration in milliseconds")
                .tag("service", "ingestion-platform")
                .register(meterRegistry);
                
        // Gauges for current values
        Gauge.builder("ingestion_current_lag_seconds")
                .description("Current lag in seconds")
                .tag("service", "ingestion-platform")
                .register(meterRegistry, this, MetricsService::getCurrentLag);
                
        Gauge.builder("ingestion_current_throughput_rps")
                .description("Current throughput in records per second")
                .tag("service", "ingestion-platform")
                .register(meterRegistry, this, MetricsService::getCurrentThroughput);
    }
    
    /**
     * Record a single ingestion metric
     */
    public IngestionMetric recordMetric(IngestionMetricRequest request) {
        logger.info("Recording metric for pipeline: {}, dataset: {}", request.getPipeline(), request.getDataset());
        
        IngestionMetric metric = new IngestionMetric(request.getPipeline(), request.getDataset(), request.getTimestamp());
        metric.setRecordsProcessed(request.getRecordsProcessed());
        metric.setRecordsFailed(request.getRecordsFailed());
        metric.setDurationMs(request.getDurationMs());
        metric.setLagSeconds(request.getLagSeconds());
        metric.setBatchId(request.getBatchId());
        metric.setErrorMessage(request.getErrorMessage());
        
        // Calculate derived metrics
        if (request.getRecordsProcessed() != null && request.getDurationMs() != null && request.getDurationMs() > 0) {
            double throughput = (request.getRecordsProcessed() * 1000.0) / request.getDurationMs();
            metric.setThroughputRps(throughput);
            currentThroughput.set(Math.round(throughput));
        }
        
        if (request.getRecordsProcessed() != null && request.getRecordsFailed() != null) {
            long total = request.getRecordsProcessed() + request.getRecordsFailed();
            if (total > 0) {
                double errorRate = (request.getRecordsFailed() * 100.0) / total;
                metric.setErrorRate(errorRate);
            }
        }
        
        // Determine status
        if (request.getRecordsFailed() != null && request.getRecordsFailed() > 0) {
            metric.setStatus(IngestionMetric.MetricStatus.WARNING);
        } else if (request.getErrorMessage() != null && !request.getErrorMessage().trim().isEmpty()) {
            metric.setStatus(IngestionMetric.MetricStatus.FAILURE);
        } else {
            metric.setStatus(IngestionMetric.MetricStatus.SUCCESS);
        }
        
        // Update Prometheus metrics
        if (request.getRecordsProcessed() != null) {
            recordsProcessedCounter.increment(request.getRecordsProcessed());
        }
        if (request.getRecordsFailed() != null) {
            recordsFailedCounter.increment(request.getRecordsFailed());
        }
        if (request.getDurationMs() != null) {
            processingTimer.record(request.getDurationMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        if (request.getLagSeconds() != null) {
            currentLag.set(request.getLagSeconds());
        }
        
        return metricRepository.save(metric);
    }
    
    /**
     * Record multiple metrics in batch
     */
    public List<IngestionMetric> recordBatchMetrics(List<IngestionMetricRequest> requests) {
        logger.info("Recording batch of {} metrics", requests.size());
        return requests.stream().map(this::recordMetric).toList();
    }
    
    /**
     * Get metrics for a specific pipeline
     */
    @Transactional(readOnly = true)
    public Page<IngestionMetric> getPipelineMetrics(String pipeline, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        if (from != null && to != null) {
            return metricRepository.findByPipelineAndTimestampBetweenOrderByTimestampDesc(pipeline, from, to, pageable);
        } else {
            return metricRepository.findByPipelineOrderByTimestampDesc(pipeline, pageable);
        }
    }
    
    /**
     * Get metrics for a specific dataset
     */
    @Transactional(readOnly = true)
    public Page<IngestionMetric> getDatasetMetrics(String dataset, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        if (from != null && to != null) {
            return metricRepository.findByDatasetAndTimestampBetweenOrderByTimestampDesc(dataset, from, to, pageable);
        } else {
            return metricRepository.findByDatasetOrderByTimestampDesc(dataset, pageable);
        }
    }
    
    /**
     * Get aggregated metrics summary
     */
    @Transactional(readOnly = true)
    public MetricsSummary getMetricsSummary(String pipeline, String dataset, LocalDateTime from, LocalDateTime to) {
        LocalDateTime fromTime = from != null ? from : LocalDateTime.now().minusHours(24);
        LocalDateTime toTime = to != null ? to : LocalDateTime.now();
        
        List<IngestionMetric> metrics;
        if (pipeline != null && dataset != null) {
            metrics = metricRepository.findByPipelineAndDatasetAndTimestampBetween(pipeline, dataset, fromTime, toTime);
        } else if (pipeline != null) {
            metrics = metricRepository.findByPipelineAndTimestampBetween(pipeline, fromTime, toTime);
        } else if (dataset != null) {
            metrics = metricRepository.findByDatasetAndTimestampBetween(dataset, fromTime, toTime);
        } else {
            metrics = metricRepository.findByTimestampBetween(fromTime, toTime);
        }
        
        return calculateSummary(metrics, fromTime, toTime);
    }
    
    /**
     * Get real-time metrics (last 5 minutes)
     */
    @Transactional(readOnly = true)
    public List<IngestionMetric> getRealtimeMetrics(String pipeline, String dataset) {
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        
        if (pipeline != null && dataset != null) {
            return metricRepository.findByPipelineAndDatasetAndTimestampAfterOrderByTimestampDesc(pipeline, dataset, fiveMinutesAgo);
        } else if (pipeline != null) {
            return metricRepository.findByPipelineAndTimestampAfterOrderByTimestampDesc(pipeline, fiveMinutesAgo);
        } else if (dataset != null) {
            return metricRepository.findByDatasetAndTimestampAfterOrderByTimestampDesc(dataset, fiveMinutesAgo);
        } else {
            return metricRepository.findByTimestampAfterOrderByTimestampDesc(fiveMinutesAgo);
        }
    }
    
    /**
     * Get metrics that require alerting
     */
    @Transactional(readOnly = true)
    public List<IngestionMetric> getAlertMetrics() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        return metricRepository.findAlertMetrics(oneHourAgo);
    }
    
    /**
     * Get system health information
     */
    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentMetricsCount = metricRepository.countByTimestampAfter(oneHourAgo);
        long failedMetricsCount = metricRepository.countByStatusAndTimestampAfter(IngestionMetric.MetricStatus.FAILURE, oneHourAgo);
        
        health.put("status", failedMetricsCount == 0 ? "HEALTHY" : "DEGRADED");
        health.put("recentMetricsCount", recentMetricsCount);
        health.put("failedMetricsCount", failedMetricsCount);
        health.put("currentLagSeconds", currentLag.get());
        health.put("currentThroughputRps", currentThroughput.get());
        health.put("timestamp", LocalDateTime.now());
        
        return health;
    }
    
    private MetricsSummary calculateSummary(List<IngestionMetric> metrics, LocalDateTime from, LocalDateTime to) {
        if (metrics.isEmpty()) {
            return new MetricsSummary(from, to, 0L, 0L, 0L, 0.0, 0.0, 0.0, 0L, 0L);
        }
        
        long totalRecordsProcessed = metrics.stream().mapToLong(m -> m.getRecordsProcessed() != null ? m.getRecordsProcessed() : 0).sum();
        long totalRecordsFailed = metrics.stream().mapToLong(m -> m.getRecordsFailed() != null ? m.getRecordsFailed() : 0).sum();
        long totalDuration = metrics.stream().mapToLong(m -> m.getDurationMs() != null ? m.getDurationMs() : 0).sum();
        
        double avgThroughput = metrics.stream().mapToDouble(m -> m.getThroughputRps() != null ? m.getThroughputRps() : 0.0).average().orElse(0.0);
        double avgErrorRate = metrics.stream().mapToDouble(m -> m.getErrorRate() != null ? m.getErrorRate() : 0.0).average().orElse(0.0);
        double avgLag = metrics.stream().mapToDouble(m -> m.getLagSeconds() != null ? m.getLagSeconds() : 0.0).average().orElse(0.0);
        
        long successCount = metrics.stream().mapToLong(m -> m.getStatus() == IngestionMetric.MetricStatus.SUCCESS ? 1 : 0).sum();
        long failureCount = metrics.stream().mapToLong(m -> m.getStatus() == IngestionMetric.MetricStatus.FAILURE ? 1 : 0).sum();
        
        return new MetricsSummary(from, to, totalRecordsProcessed, totalRecordsFailed, totalDuration, 
                                avgThroughput, avgErrorRate, avgLag, successCount, failureCount);
    }
    
    // Helper methods for Prometheus gauges
    private double getCurrentLag(MetricsService service) {
        return service.currentLag.get();
    }
    
    private double getCurrentThroughput(MetricsService service) {
        return service.currentThroughput.get();
    }
}
