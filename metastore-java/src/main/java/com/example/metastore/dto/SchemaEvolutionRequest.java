package com.example.metastore.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for schema evolution requests.
 */
public class SchemaEvolutionRequest {

    @NotBlank(message = "Schema JSON is required")
    private String schemaJson;

    @NotBlank(message = "Change type is required")
    private String changeType;

    // Constructors
    public SchemaEvolutionRequest() {}

    public SchemaEvolutionRequest(String schemaJson, String changeType) {
        this.schemaJson = schemaJson;
        this.changeType = changeType;
    }

    // Getters and Setters
    public String getSchemaJson() {
        return schemaJson;
    }

    public void setSchemaJson(String schemaJson) {
        this.schemaJson = schemaJson;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    @Override
    public String toString() {
        return "SchemaEvolutionRequest{" +
                "changeType='" + changeType + '\'' +
                ", schemaJsonLength=" + (schemaJson != null ? schemaJson.length() : 0) +
                '}';
    }
}
