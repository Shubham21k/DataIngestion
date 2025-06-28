package com.example.metrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Custom Metrics Service for Kafka Ingestion Platform
 * 
 * Provides:
 * - Real-time metrics collection from Spark jobs
 * - Aggregated KPIs for ingestion pipelines
 * - Historical metrics storage and querying
 * - Prometheus metrics export
 * - Custom alerting thresholds
 */
@SpringBootApplication
@EnableScheduling
public class MetricsServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MetricsServiceApplication.class, args);
    }
}
