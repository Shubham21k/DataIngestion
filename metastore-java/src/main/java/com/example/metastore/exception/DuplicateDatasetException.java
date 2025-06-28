package com.example.metastore.exception;

/**
 * Exception thrown when attempting to create a dataset that already exists.
 */
public class DuplicateDatasetException extends RuntimeException {

    public DuplicateDatasetException(String message) {
        super(message);
    }

    public DuplicateDatasetException(String message, Throwable cause) {
        super(message, cause);
    }
}
