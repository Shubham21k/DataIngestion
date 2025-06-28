package com.example.metastore.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing DDL operation history for a dataset.
 */
@Entity
@Table(name = "ddl_history")
@EntityListeners(AuditingEntityListener.class)
public class DDLHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    @JsonBackReference
    private Dataset dataset;

    @Column(name = "ddl_sql", columnDefinition = "TEXT")
    private String ddlSql;

    @Column(name = "glue_synced", nullable = false)
    private Boolean glueSynced = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public DDLHistory() {}

    public DDLHistory(Dataset dataset, String ddlSql) {
        this.dataset = dataset;
        this.ddlSql = ddlSql;
        this.glueSynced = false;
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

    public String getDdlSql() {
        return ddlSql;
    }

    public void setDdlSql(String ddlSql) {
        this.ddlSql = ddlSql;
    }

    public Boolean getGlueSynced() {
        return glueSynced;
    }

    public void setGlueSynced(Boolean glueSynced) {
        this.glueSynced = glueSynced;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "DDLHistory{" +
                "id=" + id +
                ", ddlSql='" + ddlSql + '\'' +
                ", glueSynced=" + glueSynced +
                ", createdAt=" + createdAt +
                '}';
    }
}
