# Ingestion Flow Specification

This document details the end-to-end data flow for the Kafka-based ingestion platform, covering:
1. **Initial Dataset Setup** and first-time ingestion
2. **Continuous Incremental Processing** with schema evolution
3. **Error Handling and Recovery** scenarios

The flows leverage the Java Metastore service, modular Spark framework, and Airflow orchestration.

## Deployment Options

**Two Docker Compose configurations are available:**

### Full Platform (`docker-compose.yml`)
- **Purpose**: Complete production-ready platform
- **Command**: `./entrypoint.sh`
- **Services**: Airflow, Metastore, Metrics, Spark, Redpanda/Kafka, PostgreSQL, LocalStack
- **Use Case**: Full development and production-like testing

### Demo Platform (`docker-compose-working.yml`)
- **Purpose**: Simplified, reliable demo environment
- **Command**: `./demo/demo.sh`
- **Services**: Confluent Kafka, PostgreSQL, LocalStack, Kafka UI
- **Use Case**: Interviews, demos, quick testing

---

## 1. Initial Dataset Setup and Bootstrap

### 1.1 Dataset Registration

**Current Technology Stack:**
- **Metastore**: Java 17 + Spring Boot 3.2 + JPA/Hibernate
- **Database**: PostgreSQL 13+ with HikariCP connection pooling
- **API**: RESTful endpoints with Jakarta Bean Validation
- **Monitoring**: Spring Boot Actuator with health checks

| Step | Component | Action |
|------|-----------|--------|
| 1 | Admin/API | `POST /datasets` to Java Metastore with dataset configuration |
| 2 | Metastore | Validates request using Jakarta Bean Validation |
| 3 | Metastore | Creates Dataset entity in PostgreSQL with JPA/Hibernate |
| 4 | Metastore | **No initial schema created** - will be auto-inferred from first data |
| 5 | Response | Returns dataset configuration with unique ID and metadata |

**Dataset Configuration**:
```json
{
  "name": "orders",
  "kafkaTopic": "orders",
  "mode": "append",
  "pkFields": ["id"],
  "partitionKeys": ["date"],
  "transformJars": ["s3://lake/jars/sample-transformers.jar"]
}
```

### 1.2 First-Time Ingestion Flow
| Step | Component | Action |
|------|-----------|--------|
| 1 | Airflow | Phase-1 DAG triggered (manual or scheduled) |
| 2 | Phase-1 Job | Fetches dataset config from Java Metastore |
| 3 | Phase-1 Job | Consumes from Kafka topic (earliest offsets) |
| 4 | Phase-1 Job | Writes raw JSON to `s3a://raw/{topic}/` with metadata |
| 5 | Phase-1 Job | Maintains exactly-once semantics via checkpointing |
| 6 | Airflow | Phase-2 DAG triggered after Phase-1 completion |
| 7 | Phase-2 Job | Reads raw JSON files, **auto-infers schema** |
| 8 | Phase-2 Job | **Captures initial schema** in Metastore (version 1, ACTIVE) |
| 9 | Phase-2 Job | Loads and applies transformers from S3 JARs |
| 10 | Phase-2 Job | Writes to Lakehouse (Parquet/Hudi) based on mode |
| 11 | Monitoring | Metrics collected and exposed via Spark driver |

Edge notes
* **Backfill**: If historical Kafka data spans multiple days, Phase-1 keeps running until caught-up; Phase-2 processes in parallel using watermark‐based trigger.
* **Schema**: Any unseen columns trigger add-column flow (§1.4 in LLD).

---

## 2. Continuous Incremental Processing

### 2.1 Normal Processing Loop
The platform operates in a continuous loop with both phases running independently:

| # | Component | Action | Frequency |
|---|-----------|--------|----------|
| 1 | Phase-1 Job | Reads new Kafka offsets since last checkpoint | Continuous |
| 2 | Phase-1 Job | Writes raw JSON to `s3a://raw/{topic}/` with metadata | Real-time |
| 3 | Phase-2 Job | Detects new raw files via Spark streaming | Continuous |
| 4 | Phase-2 Job | **Schema Evolution Check** - compares current vs active schema | Per batch |
| 5 | Phase-2 Job | Loads and applies transformers dynamically | Per batch |
| 6 | Phase-2 Job | Writes to Lakehouse (Parquet/Hudi) with exactly-once semantics | Per batch |
| 7 | Monitoring | Updates metrics and checkpoints | Per batch |

### 2.2 Schema Evolution During Processing

**Automatic Schema Detection**:
```
New Data Batch → Spark Infers Schema → Compare with Active Schema
                                              ↓
                                    ┌─── No Change ────┐
                                    │                  │
                                    ├─ Non-Breaking ──┤→ Update Active Schema
                                    │                  │
                                    └─── Breaking ─────┘→ FAIL FAST
```

**Schema Evolution Types**:
- **INITIAL**: First schema for dataset (auto-captured)
- **NON_BREAKING**: Compatible changes (new nullable fields, type widening)
- **BREAKING**: Incompatible changes (field removal, type narrowing) → Job terminates

**Evolution Process**:
1. **Detection**: Every micro-batch compares inferred vs active schema
2. **Classification**: SchemaEvolution.compareSchemas() determines change type
3. **Action**: 
   - Non-breaking: Update active schema, continue processing
   - Breaking: Mark schema as BLOCKED, terminate job with detailed error
4. **Persistence**: New schema versions stored in PostgreSQL with status tracking

## 3. Error Handling and Recovery

### 3.1 Fault Tolerance Mechanisms

**Exactly-Once Processing**:
- **Kafka Offsets**: Committed to S3 checkpoints, not Kafka
- **Idempotent Writes**: Hudi provides ACID transactions, Parquet uses atomic commits
- **State Recovery**: Spark structured streaming handles state restoration

**Failure Recovery**:
| Failure Type | Detection | Recovery Action | Data Loss |
|--------------|-----------|-----------------|----------|
| Phase-1 Crash | Airflow monitoring | Restart from last checkpoint | None |
| Phase-2 Crash | Airflow monitoring | Replay from last committed offset | None |
| Breaking Schema | Automatic detection | Fail fast, mark schema BLOCKED | None |
| Transformer Error | Exception handling | Log error, terminate job | None |
| Metastore Unavailable | HTTP client timeout | Use fallback config, retry | None |

### 3.2 Operational Procedures

**Monitoring**:
- **Health Checks**: All services expose `/health` endpoints
- **Metrics**: Spark driver exposes processing metrics
- **Alerting**: Airflow DAG failures trigger notifications
- **Logging**: Structured logging throughout the pipeline

**Maintenance**:
- **Checkpoint Cleanup**: Automated retention policies
- **Schema Management**: Version history and rollback capabilities
- **Resource Management**: Automatic scaling and resource optimization
- **Data Quality**: Built-in validation and error reporting

The system runs in an endless **produce → dump → transform → commit** loop. Think of it as a conveyor belt where each component hands data to the next in near-real-time.

### 2.1 Per-Batch Flow
1. **Kafka publishes events**.  
2. **Phase-1 Spark job** reads the newest offsets (respecting its checkpoint) and **appends** fresh JSON files under that minute’s S3 prefix.  
3. **Phase-2 Spark job** notices new raw partitions (watermark later than last commit) and starts a micro-batch:
   a. Reads the JSON.  
   b. Runs the configured transform JARs.  
   c. **Schema check:** compares batch schema with cached *activeSchemaVersionId* (fetched from Metastore)
      • **No change** → proceed.  
      • **Add / drop (non-breaking)** → call Metastore `ADD/DROP COLUMN`; batch proceeds with old projection; next batch loads new schema.  
      • **Breaking change** (type narrowing, PK change, rename) → job **fails fast**; metric `breaking_schema_change=1`; Airflow alerts.  
   d. Projects to the valid schema (after handling above).  
   e. Writes to the Lakehouse (append or upsert) **idempotently**.  
4. Upon successful commit Phase-2:
   * Advances its checkpoint.  
   * Pushes metrics (`records`, `lag_seconds`) to the Metrics Service.

This loop repeats every few minutes, keeping end-to-end lag low.

### 2.2 Scheduled Maintenance
| Frequency | Task |
|-----------|------|
| Hourly | Airflow task verifies Phase-1/2 are <max_lag> and restarts pods if needed. |
| Daily  | Checkpoint cleaner trims `_spark_metadata` to the last *N* commits to cap S3 growth. |
| Ad-hoc | Admin issues DDL (add / drop col) → Metastore; next Phase-2 batch reloads schema automatically. |

## 4. Architecture Flow Summary

### 4.1 Complete Data Flow
```
┌─────────────┐    ┌──────────────┐    ┌─────────────┐    ┌──────────────┐
│   Kafka     │───▶│   Phase-1    │───▶│   Raw S3    │───▶│   Phase-2    │
│  (Source)   │    │ Spark Job    │    │  Storage    │    │ Spark Job    │
└─────────────┘    └──────────────┘    └─────────────┘    └──────────────┘
                           │                                       │
                           ▼                                       ▼
                   ┌──────────────┐                       ┌──────────────┐
                   │ Checkpoints  │                       │  Lakehouse   │
                   │   (S3A)      │                       │(Parquet/Hudi)│
                   └──────────────┘                       └──────────────┘
                                                                  │
                                                                  ▼
┌─────────────┐    ┌──────────────┐    ┌─────────────┐    ┌──────────────┐
│   Airflow   │◄──▶│ Java Spring  │◄──▶│ PostgreSQL  │    │  Monitoring  │
│(Orchestrator)│    │  Metastore   │    │ (Metadata)  │    │ & Metrics    │
└─────────────┘    └──────────────┘    └─────────────┘    └──────────────┘
```

### 4.2 Key Design Principles

1. **Two-Phase Decoupling**: Raw storage enables independent scaling and fault tolerance
2. **Exactly-Once Processing**: S3 checkpointing ensures no data loss or duplication
3. **Automatic Schema Evolution**: Spark infers schemas, handles compatible changes automatically
4. **Fail-Fast Safety**: Breaking schema changes terminate jobs immediately
5. **Dynamic Transformations**: JAR loading enables runtime business logic updates
6. **Enterprise Integration**: Java Metastore provides robust metadata management

### 4.3 Operational Benefits

- **Fault Tolerance**: Any component failure can be recovered without data loss
- **Scalability**: Each phase scales independently based on throughput requirements
- **Flexibility**: Schema evolution and transformer updates without downtime
- **Observability**: Comprehensive logging, metrics, and health monitoring
- **Maintainability**: Clean separation of concerns and modular architecture

---

**The platform operates as a resilient, self-healing data pipeline that automatically adapts to schema changes while maintaining strict data quality and consistency guarantees.**
