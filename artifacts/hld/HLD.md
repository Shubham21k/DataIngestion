# High-Level Design (HLD)

_This document summarizes the architecture of the Kafka-to-Lakehouse ingestion platform, focusing on the P1 scope._

---

## 1. Goals
* Ingest event streams from Kafka to a Lakehouse on S3 at large scale (petabyte-level design).
* Provide two ingestion modes per dataset: **append** and **upsert**.
* Support runtime **schema evolution** (add / drop columns) and **pluggable transformations**.
* Offer basic **observability** (custom metrics via HTTP) and orchestration with **Airflow**.

P1 demo will run locally (Docker Compose) with tiny data volumes while preserving production-grade patterns.

---

## 2. Component Overview
| # | Component | Purpose |
|---|-----------|---------|
| 1 | **Metastore Service** | Source of truth for dataset configs: topic ⇄ table mapping, mode (append/upsert), primary keys, partition keys, allowed schema changes, list of transformation JARs. Backed by a lightweight relational DB. Exposes a REST API and propagates DDL/DML updates to AWS Glue catalog. |
| 2 | **Airflow DAGs** | Orchestrate Phase-1 and Phase-2 Spark jobs, retries, and ad-hoc backfills. Reads pipeline metadata from Metastore. |
| 3 | **Ingestion Framework** | Spark Structured-Streaming jobs split into two logical phases. Performs checkpointing via Kafka offsets, applies schema evolution & transformations, and writes to Lakehouse paths. |
| 4 | **Metrics Service** | Small HTTP server embedded in the Spark driver that exposes JSON metrics (lag, commit latency, records/s, error counts). |
| 5 | **Lakehouse (S3)†** | Destination storage. Phase-2 writes Parquet (or Iceberg/Hudi) partitions in append or merge-on-read mode. |
| 6 | **Raw Dump Bucket (S3)** | Phase-1 writes raw JSON, partitioned by ingestion timestamp. Serves as the failure-recovery / backfill store. |

† Catalog (e.g., Glue) is out-of-scope for P1 but the layout is compatible.

---

## 3. End-to-End Data Flow

```
Kafka Topic  ──▶  Phase-1 Spark Job  ──▶  Raw JSON  ──▶  Phase-2 Spark Job  ──▶  Lakehouse
                     |  (append)            (S3)          | (transform +            (append / upsert)
                     |                                   schema evolution)
                     │
                     └── Checkpoint (Kafka offsets)
```

1. **Phase-1: Raw Dump**  
   * Consumes Kafka with `startingOffsets=latest` (or earliest for first run).  
   * Stores each event as line-delimited JSON in `s3://raw/<topic>/date=YYYY-MM-DD/`.  
   * Captures Kafka metadata columns (offset, partition, timestamp, key).  
   * Persists Structured-Streaming checkpoint to keep offsets.

2. **Phase-2: Ingest to Lakehouse**  
   * Triggered continuously (or by separate DAG for backfill).  
   * Reads raw files after **exactly-once commit watermarks** ensure completeness.  
   * Applies ordered list of **transform JARs** (e.g., decrypt, filter, cast).  
   * Validates / evolves schema based on Metastore DDL rules (add/drop).  
   * Writes output in table’s configured mode:  
     * **Append** → new partitions only.  
     * **Upsert** → merge on primary key using copy-on-write (Hudi/Iceberg Delta).  

3. **Metrics**  
   * Each Spark job pushes counters to an embedded HTTP endpoint `/metrics` scraped by a dashboard/alerting tool.

4. **Backfill / Recovery**  
   * If upstream failure drops Kafka data, Airflow can trigger Phase-2 on past raw paths without code change.

<!-- Extra sections removed -->
```
Docker Compose
├─ kafka  (Redpanda)      ──┐
├─ localstack (S3)        ──┴─> shared "s3://" volumes
├─ metastore (Flask + SQLite)
├─ spark-standalone (driver + workers)
└─ airflow (scheduler + webserver)
```

---

<!-- Interfaces section removed -->
### 5.1 Metastore API (excerpt)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/config/{table}` | Retrieve full config JSON. |
| POST | `/ddl` | Submit `add_column` / `drop_column` ops. |
| POST | `/dml` | Enable/disable upsert mode, change PK, etc. |

_Complete schema left for LLD._

### 5.2 Metrics JSON (example)
```json
{
  "pipeline": "orders",
  "phase": "phase2",
  "timestamp": "2025-06-24T00:00:00Z",
  "records_ingested": 120000,
  "lag_seconds": 5,
  "last_commit": "2025-06-24T00:00:00Z"
}
```

---

<!-- Non-Goals section removed -->
* Data reconciliation framework.
* Advanced observability (Prometheus, Grafana dashboards). These can be layered later without code change.
* Multi-AZ high availability; demo runs single-instance containers.

---

## 5. Glossary
| Term | Meaning |
|------|---------|
| **Append** | Insert-only writes; no record updates. |
| **Upsert** | Merge existing rows on primary key, else insert new. |
| **Transformation JAR** | User-supplied Spark `Dataset` transformer implementing a common interface. |
| **Schema Evolution** | Runtime handling of add/drop column DDL without full reload. |

---

![High-Level Diagram](./Ingestion%20Framework.png)
