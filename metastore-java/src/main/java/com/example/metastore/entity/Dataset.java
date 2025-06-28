package com.example.metastore.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a dataset configuration in the ingestion platform.
 */
@Entity
@Table(name = "datasets")
@EntityListeners(AuditingEntityListener.class)
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Dataset name is required")
    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @NotBlank(message = "Kafka topic is required")
    @Column(name = "kafka_topic", nullable = false)
    private String kafkaTopic;

    @NotBlank(message = "Mode is required")
    @Column(name = "mode", nullable = false)
    private String mode; // append, upsert

    @ElementCollection
    @CollectionTable(name = "dataset_pk_fields", joinColumns = @JoinColumn(name = "dataset_id"))
    @Column(name = "field_name")
    private List<String> pkFields = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "dataset_partition_keys", joinColumns = @JoinColumn(name = "dataset_id"))
    @Column(name = "key_name")
    private List<String> partitionKeys = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "dataset_transform_jars", joinColumns = @JoinColumn(name = "dataset_id"))
    @Column(name = "jar_url")
    private List<String> transformJars = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<SchemaVersion> schemaVersions = new ArrayList<>();

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<DDLHistory> ddlHistory = new ArrayList<>();

    // Constructors
    public Dataset() {}

    public Dataset(String name, String kafkaTopic, String mode) {
        this.name = name;
        this.kafkaTopic = kafkaTopic;
        this.mode = mode;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public void setKafkaTopic(String kafkaTopic) {
        this.kafkaTopic = kafkaTopic;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<String> getPkFields() {
        return pkFields;
    }

    public void setPkFields(List<String> pkFields) {
        this.pkFields = pkFields != null ? pkFields : new ArrayList<>();
    }

    public List<String> getPartitionKeys() {
        return partitionKeys;
    }

    public void setPartitionKeys(List<String> partitionKeys) {
        this.partitionKeys = partitionKeys != null ? partitionKeys : new ArrayList<>();
    }

    public List<String> getTransformJars() {
        return transformJars;
    }

    public void setTransformJars(List<String> transformJars) {
        this.transformJars = transformJars != null ? transformJars : new ArrayList<>();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<SchemaVersion> getSchemaVersions() {
        return schemaVersions;
    }

    public void setSchemaVersions(List<SchemaVersion> schemaVersions) {
        this.schemaVersions = schemaVersions;
    }

    public List<DDLHistory> getDdlHistory() {
        return ddlHistory;
    }

    public void setDdlHistory(List<DDLHistory> ddlHistory) {
        this.ddlHistory = ddlHistory;
    }

    @Override
    public String toString() {
        return "Dataset{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", kafkaTopic='" + kafkaTopic + '\'' +
                ", mode='" + mode + '\'' +
                ", pkFields=" + pkFields +
                ", partitionKeys=" + partitionKeys +
                ", transformJars=" + transformJars +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
