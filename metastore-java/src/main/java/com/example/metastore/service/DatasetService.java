package com.example.metastore.service;

import com.example.metastore.dto.DatasetRequest;
import com.example.metastore.dto.SchemaEvolutionRequest;
import com.example.metastore.entity.Dataset;
import com.example.metastore.entity.SchemaVersion;
import com.example.metastore.exception.DatasetNotFoundException;
import com.example.metastore.exception.DuplicateDatasetException;
import com.example.metastore.repository.DatasetRepository;
import com.example.metastore.repository.SchemaVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service class for dataset operations.
 */
@Service
@Transactional
public class DatasetService {

    private static final Logger logger = LoggerFactory.getLogger(DatasetService.class);

    private final DatasetRepository datasetRepository;
    private final SchemaVersionRepository schemaVersionRepository;

    @Autowired
    public DatasetService(DatasetRepository datasetRepository, SchemaVersionRepository schemaVersionRepository) {
        this.datasetRepository = datasetRepository;
        this.schemaVersionRepository = schemaVersionRepository;
    }

    /**
     * Get all datasets.
     * @return list of all datasets
     */
    @Transactional(readOnly = true)
    public List<Dataset> getAllDatasets() {
        logger.info("Retrieving all datasets");
        List<Dataset> datasets = datasetRepository.findAll();
        logger.info("Found {} datasets", datasets.size());
        return datasets;
    }

    /**
     * Get dataset by ID or name.
     * @param identifier dataset ID or name
     * @return the dataset
     * @throws DatasetNotFoundException if dataset not found
     */
    @Transactional(readOnly = true)
    public Dataset getDataset(String identifier) {
        logger.info("Retrieving dataset: {}", identifier);
        
        // Try to parse as Long (ID) first
        try {
            Long id = Long.parseLong(identifier);
            Optional<Dataset> dataset = datasetRepository.findById(id);
            if (dataset.isPresent()) {
                logger.info("Found dataset by ID: {} (name: {})", id, dataset.get().getName());
                return dataset.get();
            }
        } catch (NumberFormatException e) {
            // Not a number, try as name
        }
        
        // Try as name
        Optional<Dataset> dataset = datasetRepository.findByName(identifier);
        if (dataset.isPresent()) {
            logger.info("Found dataset by name: {} (ID: {})", identifier, dataset.get().getId());
            return dataset.get();
        }
        
        logger.warn("Dataset not found: {}", identifier);
        throw new DatasetNotFoundException("Dataset not found: " + identifier);
    }

    /**
     * Create a new dataset.
     * @param request the dataset creation request
     * @return the created dataset
     * @throws DuplicateDatasetException if dataset with same name already exists
     */
    public Dataset createDataset(DatasetRequest request) {
        logger.info("Creating dataset: {}", request.getName());
        
        // Check if dataset with same name already exists
        if (datasetRepository.existsByName(request.getName())) {
            logger.warn("Dataset with name {} already exists", request.getName());
            throw new DuplicateDatasetException("Dataset with name " + request.getName() + " already exists");
        }
        
        // Create new dataset
        Dataset dataset = new Dataset();
        dataset.setName(request.getName());
        dataset.setKafkaTopic(request.getKafkaTopic());
        dataset.setMode(request.getMode());
        dataset.setPkFields(request.getPkFields());
        dataset.setPartitionKeys(request.getPartitionKeys());
        dataset.setTransformJars(request.getTransformJars());
        
        Dataset savedDataset = datasetRepository.save(dataset);
        logger.info("Dataset created successfully: {} (ID: {})", savedDataset.getName(), savedDataset.getId());
        
        return savedDataset;
    }

    /**
     * Update an existing dataset.
     * @param id the dataset ID
     * @param request the update request
     * @return the updated dataset
     * @throws DatasetNotFoundException if dataset not found
     */
    public Dataset updateDataset(Long id, DatasetRequest request) {
        logger.info("Updating dataset: {}", id);
        
        Dataset dataset = datasetRepository.findById(id)
                .orElseThrow(() -> new DatasetNotFoundException("Dataset not found: " + id));
        
        // Update fields if provided
        if (request.getKafkaTopic() != null) {
            dataset.setKafkaTopic(request.getKafkaTopic());
        }
        if (request.getMode() != null) {
            dataset.setMode(request.getMode());
        }
        if (request.getPkFields() != null) {
            dataset.setPkFields(request.getPkFields());
        }
        if (request.getPartitionKeys() != null) {
            dataset.setPartitionKeys(request.getPartitionKeys());
        }
        if (request.getTransformJars() != null) {
            dataset.setTransformJars(request.getTransformJars());
        }
        
        Dataset updatedDataset = datasetRepository.save(dataset);
        logger.info("Dataset updated successfully: {}", id);
        
        return updatedDataset;
    }

    /**
     * Get active schema version for a dataset.
     * @param datasetId the dataset ID
     * @return the active schema version
     * @throws DatasetNotFoundException if dataset or active schema not found
     */
    @Transactional(readOnly = true)
    public SchemaVersion getActiveSchema(Long datasetId) {
        logger.info("Retrieving active schema for dataset: {}", datasetId);
        
        // Verify dataset exists
        if (!datasetRepository.existsById(datasetId)) {
            throw new DatasetNotFoundException("Dataset not found: " + datasetId);
        }
        
        Optional<SchemaVersion> activeSchema = schemaVersionRepository.findActiveByDatasetId(datasetId);
        if (activeSchema.isPresent()) {
            logger.info("Found active schema version {} for dataset {}", 
                       activeSchema.get().getVersion(), datasetId);
            return activeSchema.get();
        }
        
        logger.warn("No active schema found for dataset: {}", datasetId);
        throw new DatasetNotFoundException("No active schema found for dataset: " + datasetId);
    }

    /**
     * Get all schema versions for a dataset.
     * @param datasetId the dataset ID
     * @return list of schema versions
     * @throws DatasetNotFoundException if dataset not found
     */
    @Transactional(readOnly = true)
    public List<SchemaVersion> getSchemaVersions(Long datasetId) {
        logger.info("Retrieving schema versions for dataset: {}", datasetId);
        
        // Verify dataset exists
        if (!datasetRepository.existsById(datasetId)) {
            throw new DatasetNotFoundException("Dataset not found: " + datasetId);
        }
        
        List<SchemaVersion> versions = schemaVersionRepository.findByDatasetIdOrderByVersionDesc(datasetId);
        logger.info("Found {} schema versions for dataset {}", versions.size(), datasetId);
        
        return versions;
    }

    /**
     * Handle schema evolution for a dataset.
     * @param datasetId the dataset ID
     * @param request the schema evolution request
     * @return response map with evolution details
     * @throws DatasetNotFoundException if dataset not found
     */
    public Map<String, Object> handleSchemaEvolution(Long datasetId, SchemaEvolutionRequest request) {
        logger.info("Handling schema evolution for dataset {}: {}", datasetId, request.getChangeType());
        
        // Verify dataset exists
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new DatasetNotFoundException("Dataset not found: " + datasetId));
        
        // Get current active schema
        Optional<SchemaVersion> currentActiveSchema = schemaVersionRepository.findActiveByDatasetId(datasetId);
        
        SchemaVersion newVersion;
        String changeType = request.getChangeType().toUpperCase();
        
        switch (changeType) {
            case "INITIAL":
                // First schema for the dataset
                newVersion = new SchemaVersion(dataset, 1, request.getSchemaJson(), SchemaVersion.SchemaStatus.ACTIVE);
                schemaVersionRepository.save(newVersion);
                logger.info("Initial schema captured for dataset {} - version: 1", datasetId);
                break;
                
            case "BREAKING":
                // Mark current schema as blocked
                if (currentActiveSchema.isPresent()) {
                    currentActiveSchema.get().setStatus(SchemaVersion.SchemaStatus.BLOCKED);
                    schemaVersionRepository.save(currentActiveSchema.get());
                }
                
                // Create new blocked schema version
                Integer nextVersion = schemaVersionRepository.findLatestVersionByDatasetId(datasetId) + 1;
                newVersion = new SchemaVersion(dataset, nextVersion, request.getSchemaJson(), SchemaVersion.SchemaStatus.BLOCKED);
                schemaVersionRepository.save(newVersion);
                
                logger.warn("Breaking schema change detected for dataset {} - schema blocked at version {}", 
                           datasetId, nextVersion);
                break;
                
            default: // NON_BREAKING
                // Mark current schema as obsolete
                if (currentActiveSchema.isPresent()) {
                    currentActiveSchema.get().setStatus(SchemaVersion.SchemaStatus.OBSOLETE);
                    schemaVersionRepository.save(currentActiveSchema.get());
                }
                
                // Create new active schema version
                Integer newVersionNumber = schemaVersionRepository.findLatestVersionByDatasetId(datasetId) + 1;
                newVersion = new SchemaVersion(dataset, newVersionNumber, request.getSchemaJson(), SchemaVersion.SchemaStatus.ACTIVE);
                schemaVersionRepository.save(newVersion);
                
                logger.info("Non-breaking schema change applied for dataset {} - new version: {}", 
                           datasetId, newVersionNumber);
                break;
        }
        
        return Map.of(
            "status", "success",
            "changeType", changeType,
            "newVersion", newVersion.getVersion(),
            "message", "Schema evolution handled successfully"
        );
    }
}
