package com.example.metastore.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing a schema version for a dataset.
 */
@Entity
@Table(name = "schema_versions", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"dataset_id", "version"}))
@EntityListeners(AuditingEntityListener.class)
public class SchemaVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    @JsonBackReference
    private Dataset dataset;

    @NotNull
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "schema_json", columnDefinition = "TEXT")
    private String schemaJson;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SchemaStatus status = SchemaStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public SchemaVersion() {}

    public SchemaVersion(Dataset dataset, Integer version, String schemaJson, SchemaStatus status) {
        this.dataset = dataset;
        this.version = version;
        this.schemaJson = schemaJson;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getSchemaJson() {
        return schemaJson;
    }

    public void setSchemaJson(String schemaJson) {
        this.schemaJson = schemaJson;
    }

    public SchemaStatus getStatus() {
        return status;
    }

    public void setStatus(SchemaStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "SchemaVersion{" +
                "id=" + id +
                ", version=" + version +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }

    /**
     * Enum for schema version status.
     */
    public enum SchemaStatus {
        ACTIVE,    // Currently active schema
        PENDING,   // Pending approval/activation
        OBSOLETE,  // Superseded by newer version
        BLOCKED    // Blocked due to breaking changes
    }
}
