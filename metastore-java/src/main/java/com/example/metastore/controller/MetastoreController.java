package com.example.metastore.controller;

import com.example.metastore.dto.DatasetRequest;
import com.example.metastore.dto.SchemaEvolutionRequest;
import com.example.metastore.entity.Dataset;
import com.example.metastore.entity.SchemaVersion;
import com.example.metastore.exception.DatasetNotFoundException;
import com.example.metastore.exception.DuplicateDatasetException;
import com.example.metastore.service.DatasetService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Metastore operations.
 */
@RestController
@RequestMapping("/")
@CrossOrigin(origins = "*")
public class MetastoreController {

    private static final Logger logger = LoggerFactory.getLogger(MetastoreController.class);

    private final DatasetService datasetService;

    @Autowired
    public MetastoreController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    /**
     * Root endpoint with API information.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
            "message", "Metastore API is running",
            "version", "2.0.0",
            "technology", "Java Spring Boot",
            "endpoints", Map.of(
                "health", "/actuator/health",
                "datasets", "/datasets",
                "schema", "/datasets/{id}/schema"
            )
        ));
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        logger.debug("Health check requested");
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "database", "connected",
            "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Get all datasets.
     */
    @GetMapping("/datasets")
    public ResponseEntity<List<Dataset>> getAllDatasets() {
        logger.info("GET /datasets - Retrieving all datasets");
        List<Dataset> datasets = datasetService.getAllDatasets();
        return ResponseEntity.ok(datasets);
    }

    /**
     * Create a new dataset.
     */
    @PostMapping("/datasets")
    public ResponseEntity<Dataset> createDataset(@Valid @RequestBody DatasetRequest request) {
        logger.info("POST /datasets - Creating dataset: {}", request.getName());
        try {
            Dataset dataset = datasetService.createDataset(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(dataset);
        } catch (DuplicateDatasetException e) {
            logger.warn("Dataset creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Get dataset by ID or name.
     */
    @GetMapping("/datasets/{identifier}")
    public ResponseEntity<Dataset> getDataset(@PathVariable String identifier) {
        logger.info("GET /datasets/{} - Retrieving dataset", identifier);
        try {
            Dataset dataset = datasetService.getDataset(identifier);
            return ResponseEntity.ok(dataset);
        } catch (DatasetNotFoundException e) {
            logger.warn("Dataset not found: {}", identifier);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update dataset.
     */
    @PatchMapping("/datasets/{id}")
    public ResponseEntity<Dataset> updateDataset(@PathVariable Long id, 
                                               @Valid @RequestBody DatasetRequest request) {
        logger.info("PATCH /datasets/{} - Updating dataset", id);
        try {
            Dataset dataset = datasetService.updateDataset(id, request);
            return ResponseEntity.ok(dataset);
        } catch (DatasetNotFoundException e) {
            logger.warn("Dataset not found for update: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get active schema version for a dataset.
     */
    @GetMapping("/datasets/{id}/schema/active")
    public ResponseEntity<SchemaVersion> getActiveSchema(@PathVariable Long id) {
        logger.info("GET /datasets/{}/schema/active - Retrieving active schema", id);
        try {
            SchemaVersion activeSchema = datasetService.getActiveSchema(id);
            return ResponseEntity.ok(activeSchema);
        } catch (DatasetNotFoundException e) {
            logger.warn("Active schema not found for dataset: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all schema versions for a dataset.
     */
    @GetMapping("/datasets/{id}/schema/versions")
    public ResponseEntity<List<SchemaVersion>> getSchemaVersions(@PathVariable Long id) {
        logger.info("GET /datasets/{}/schema/versions - Retrieving schema versions", id);
        try {
            List<SchemaVersion> versions = datasetService.getSchemaVersions(id);
            return ResponseEntity.ok(versions);
        } catch (DatasetNotFoundException e) {
            logger.warn("Dataset not found for schema versions: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Handle schema evolution.
     */
    @PostMapping("/datasets/{id}/schema/evolve")
    public ResponseEntity<Map<String, Object>> handleSchemaEvolution(
            @PathVariable Long id,
            @Valid @RequestBody SchemaEvolutionRequest request) {
        logger.info("POST /datasets/{}/schema/evolve - Handling schema evolution: {}", 
                   id, request.getChangeType());
        try {
            Map<String, Object> result = datasetService.handleSchemaEvolution(id, request);
            return ResponseEntity.ok(result);
        } catch (DatasetNotFoundException e) {
            logger.warn("Dataset not found for schema evolution: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Global exception handler for validation errors.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now().toString()
                ));
    }
}
