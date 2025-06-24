# Ingestion Flows

This note walks through **two** typical scenarios:
1. **First-time ingestion** of a brand-new Kafka topic into a Lakehouse table.
2. **Incremental ingestion** once the table is live.

The steps reference components and behaviour already defined in the HLD/LLD.

---

## 1. New-Table (Bootstrap) Ingestion
| Step | Actor | Action |
|------|-------|--------|
| 1 | Admin | Calls `POST /datasets` on Metastore with topic→table mapping & config (mode, PKs, partitions, transforms). |
| 2 | Metastore | Persists dataset row; inserts **schema version 1** (captured from Confluent schema or inferred from first message). |
| 3 | Airflow Plugin | Detects new dataset → generates `ingestion_phase1_<table>` DAG (schedule `@once`). |
| 4 | DAG Run | Phase-1 Spark job consumes **from earliest** offsets, dumps raw JSON to `s3://raw/<topic>/…`, checkpoints offsets to `s3a://checkpoints/<table>/phase1`. |
| 5 | Completion | Once Phase-1 has produced ≥ 1 partition, plugin enables `ingestion_phase2_<table>` DAG (continuous schedule, e.g. `*/5 * * * *`). |
| 6 | Phase-2 First Run | Reads raw dump, applies transforms, writes to `s3://lake/<table>` in chosen mode (append/upsert); creates **Hudi/Parquet** table if absent. |
| 7 | Glue Sync | Celery worker creates Glue table + partitions. |
| 8 | Validation | Metrics Service exposes `records_ingested`, `lag_seconds`; dashboard shows zero backlog. Table is now live.

Edge notes
* **Backfill**: If historical Kafka data spans multiple days, Phase-1 keeps running until caught-up; Phase-2 processes in parallel using watermark‐based trigger.
* **Schema**: Any unseen columns trigger add-column flow (§1.4 in LLD).

---

## 2. Incremental (Ongoing) Ingestion
Below is a simple five-step loop that repeats every few minutes depending on ingestion frequency.

| # | Stage | What happens? |
|---|-------|---------------|
| 1 | Read Kafka | Phase-1 picks up **only new offsets** since its last checkpoint. |
| 2 | Dump RAW | Events are appended as JSON to `s3://raw/<topic>/yyyy/mm/dd/HH/`. |
| 3 | Detect new dump | Phase-2 sees the fresh folder (watermark > last_commit). |
| 4 | Transform & Write | Phase-2 reads, applies transforms, projects to active schema, then **idempotently** writes to the Lakehouse. |
| 5 | Commit & Metrics | Phase-2 advances its checkpoint and ships lag / throughput metrics. |

Loop = 1→2→3→4→5 → back to 1.

### Routine Maintenance
* **Hourly**  – Airflow sensor checks `lag_seconds`; auto-restarts stale jobs.
* **Daily**   – Checkpoint cleaner keeps the last *N* commits.
* **Ad-hoc**  – Admin DDL (add/drop col) → Metastore; next Phase-2 batch reloads schema.

### Failure Scenarios
* Phase-1 crash → offsets intact; Airflow retry = no data loss.
* Phase-2 crash → raw files pile up; restarting Phase-2 replays pending partitions.
* Breaking schema → job fails fast; Metastore marks schema `BLOCKED` for operator fix.

The system runs in an endless **produce → dump → transform → commit** loop. Think of it as a conveyor belt where each component hands data to the next in near-real-time.

### 2.1 Per-Batch Flow
1. **Kafka publishes events**.  
2. **Phase-1 Spark job** reads the newest offsets (respecting its checkpoint) and **appends** fresh JSON files under that minute’s S3 prefix.  
3. **Phase-2 Spark job** notices new raw partitions (watermark later than last commit) and starts a micro-batch:
   a. Reads the JSON.  
   b. Runs the configured transform JARs.  
   c. **Schema check:** compares batch schema with the cached *activeSchema* in metastore service
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
---

## Quick Visual
```
Kafka ─► Phase-1 (raw dump, earliest→latest) ─► S3 raw
                                      ▲
                                      │ (continuous)
                                      ▼
                        Phase-2 (transform, write) ─► Lakehouse
```

These flows give operators a clear mental model for both bootstrapping a new dataset and keeping it steadily up-to-date.
