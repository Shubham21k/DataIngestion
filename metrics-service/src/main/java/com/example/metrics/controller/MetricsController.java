package com.example.metrics.controller;

import com.example.metrics.dto.IngestionMetricRequest;
import com.example.metrics.dto.MetricsSummary;
import com.example.metrics.entity.IngestionMetric;
import com.example.metrics.service.MetricsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST API for metrics collection and querying
 */
@RestController
@RequestMapping("/api/v1/metrics")
@CrossOrigin(origins = "*")
public class MetricsController {
    
    @Autowired
    private MetricsService metricsService;
    
    /**
     * Submit ingestion metrics from Spark jobs or Airflow
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestionMetric> submitMetrics(@Valid @RequestBody IngestionMetricRequest request) {
        IngestionMetric metric = metricsService.recordMetric(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(metric);
    }
    
    /**
     * Bulk submit metrics
     */
    @PostMapping("/ingest/batch")
    public ResponseEntity<List<IngestionMetric>> submitBatchMetrics(@Valid @RequestBody List<IngestionMetricRequest> requests) {
        List<IngestionMetric> metrics = metricsService.recordBatchMetrics(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(metrics);
    }
    
    /**
     * Get metrics for a specific pipeline
     */
    @GetMapping("/pipeline/{pipeline}")
    public ResponseEntity<Page<IngestionMetric>> getPipelineMetrics(
            @PathVariable String pipeline,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        
        Page<IngestionMetric> metrics = metricsService.getPipelineMetrics(pipeline, from, to, pageable);
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get metrics for a specific dataset
     */
    @GetMapping("/dataset/{dataset}")
    public ResponseEntity<Page<IngestionMetric>> getDatasetMetrics(
            @PathVariable String dataset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        
        Page<IngestionMetric> metrics = metricsService.getDatasetMetrics(dataset, from, to, pageable);
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get aggregated metrics summary
     */
    @GetMapping("/summary")
    public ResponseEntity<MetricsSummary> getMetricsSummary(
            @RequestParam(required = false) String pipeline,
            @RequestParam(required = false) String dataset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        
        MetricsSummary summary = metricsService.getMetricsSummary(pipeline, dataset, from, to);
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Get current system health metrics
     */
    @GetMapping("/health")
    public ResponseEntity<Object> getHealthMetrics() {
        return ResponseEntity.ok(metricsService.getSystemHealth());
    }
    
    /**
     * Get real-time metrics (last 5 minutes)
     */
    @GetMapping("/realtime")
    public ResponseEntity<List<IngestionMetric>> getRealtimeMetrics(
            @RequestParam(required = false) String pipeline,
            @RequestParam(required = false) String dataset) {
        
        List<IngestionMetric> metrics = metricsService.getRealtimeMetrics(pipeline, dataset);
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get metrics for alerting (failures, high latency, etc.)
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<IngestionMetric>> getAlertMetrics() {
        List<IngestionMetric> alertMetrics = metricsService.getAlertMetrics();
        return ResponseEntity.ok(alertMetrics);
    }
}
