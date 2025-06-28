package com.example.metastore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO for dataset creation and update requests.
 */
public class DatasetRequest {

    @NotBlank(message = "Dataset name is required")
    private String name;

    @NotBlank(message = "Kafka topic is required")
    private String kafkaTopic;

    @NotBlank(message = "Mode is required")
    private String mode;

    private List<String> pkFields;
    private List<String> partitionKeys;
    private List<String> transformJars;

    // Constructors
    public DatasetRequest() {}

    public DatasetRequest(String name, String kafkaTopic, String mode) {
        this.name = name;
        this.kafkaTopic = kafkaTopic;
        this.mode = mode;
    }

    // Getters and Setters
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
        this.pkFields = pkFields;
    }

    public List<String> getPartitionKeys() {
        return partitionKeys;
    }

    public void setPartitionKeys(List<String> partitionKeys) {
        this.partitionKeys = partitionKeys;
    }

    public List<String> getTransformJars() {
        return transformJars;
    }

    public void setTransformJars(List<String> transformJars) {
        this.transformJars = transformJars;
    }

    @Override
    public String toString() {
        return "DatasetRequest{" +
                "name='" + name + '\'' +
                ", kafkaTopic='" + kafkaTopic + '\'' +
                ", mode='" + mode + '\'' +
                ", pkFields=" + pkFields +
                ", partitionKeys=" + partitionKeys +
                ", transformJars=" + transformJars +
                '}';
    }
}
