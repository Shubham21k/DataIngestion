package com.example.metastore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Main application class for the Metastore service.
 * 
 * This service manages dataset configurations, schema versions, and DDL history
 * for the Kafka-based ingestion platform.
 */
@SpringBootApplication
@EnableJpaAuditing
public class MetastoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetastoreApplication.class, args);
    }
}
