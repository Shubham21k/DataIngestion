# Schema Evolution in Kafka Ingestion Platform

## Overview

The Kafka ingestion platform implements automatic schema evolution with fail-fast behavior for breaking changes. This document explains how schema evolution works, where it happens, and the design decisions behind it.

## Schema Evolution Flow

### 1. Initial Schema Capture

**When**: First time data flows through Phase-2 job for a dataset
**Where**: `Phase2Job.scala` - lines 70-110
**How**: 
- Spark automatically infers schema from JSON data using `spark.readStream.json()`
- The inferred schema is captured as `StructType` and converted to JSON
- Schema is sent to Metastore via `MetastoreClient.updateActiveSchema()`
- Metastore creates first `SchemaVersion` record with status "ACTIVE"

```scala
// In Phase2Job.scala
val currentSchema = dataStream.schema  // Spark infers schema automatically
val initialSchemaJson = currentSchema.json
metastoreClient.updateActiveSchema(datasetConfig.id, initialSchemaJson, "INITIAL")
```

### 2. Schema Evolution Detection

**When**: Every time Phase-2 job runs with new data
**Where**: `Phase2Job.scala` - schema evolution section
**How**:
- Current batch schema is compared with active schema from Metastore
- `SchemaEvolution.compareSchemas()` determines if changes are breaking or non-breaking
- Decision is made whether to continue processing or fail fast

```scala
// Schema comparison logic
val comparison = SchemaEvolution.compareSchemas(activeSchema, currentSchema)
if (!SchemaEvolution.handleSchemaEvolution(comparison, datasetConfig.id, metastoreClient)) {
  throw new RuntimeException("Breaking schema change detected - job terminated")
}
```

### 3. Schema Update Process

**When**: Non-breaking changes are detected
**Where**: `Phase2Job.scala` and `MetastoreClient.scala`
**How**:
- New schema JSON is sent to Metastore
- Previous schema version is marked as "OBSOLETE"
- New schema version is created with status "ACTIVE"

## Database Schema

### SchemaVersion Table Structure

```sql
CREATE TABLE schema_versions (
    id SERIAL PRIMARY KEY,
    dataset_id INTEGER REFERENCES datasets(id),
    version INTEGER NOT NULL,
    schema_json TEXT,  -- Spark StructType as JSON string
    status VARCHAR NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, PENDING, OBSOLETE, BLOCKED
    created_at TIMESTAMP DEFAULT NOW()
);
```

### Schema JSON Format

The `schema_json` column stores Spark's `StructType` schema in JSON format:

```json
{
  "type": "struct",
  "fields": [
    {
      "name": "id",
      "type": "long",
      "nullable": false,
      "metadata": {}
    },
    {
      "name": "customer",
      "type": "string",
      "nullable": true,
      "metadata": {}
    },
    {
      "name": "amount",
      "type": "double",
      "nullable": true,
      "metadata": {}
    }
  ]
}
```

## Schema Evolution Rules

### Non-Breaking Changes (Allowed)
- **Adding new nullable fields**
- **Making non-nullable fields nullable**
- **Widening numeric types** (int → long, float → double)
- **Converting to string type** (most types can be read as string)

### Breaking Changes (Fail-Fast)
- **Removing existing fields**
- **Making nullable fields non-nullable**
- **Narrowing numeric types** (long → int, double → float)
- **Incompatible type changes** (string → int, etc.)

### Implementation in SchemaEvolution.scala

```scala
def compareSchemas(oldSchema: StructType, newSchema: StructType): SchemaComparison = {
  val addedFields = newFields.keySet -- oldFields.keySet
  val removedFields = oldFields.keySet -- newFields.keySet
  val modifiedFields = commonFields.filter { fieldName =>
    !isCompatibleFieldChange(oldFields(fieldName), newFields(fieldName))
  }
  
  if (removedFields.nonEmpty || modifiedFields.nonEmpty) {
    SchemaComparison(Breaking, ...)
  } else if (addedFields.nonEmpty) {
    SchemaComparison(NonBreaking, ...)
  } else {
    SchemaComparison(NoChange, ...)
  }
}
```

## Design Decisions

### 1. Automatic Schema Inference vs User-Provided Schema

**Decision**: Use Spark's automatic schema inference
**Rationale**:
- **Simplicity**: No need for users to manually define schemas
- **Accuracy**: Schema reflects actual data structure
- **Flexibility**: Handles nested JSON structures automatically
- **Evolution**: Natural evolution as data changes

**Alternative Considered**: User-provided schema
- **Pros**: More control, explicit contracts
- **Cons**: Additional complexity, maintenance burden, potential mismatches

### 2. When Schema Evolution Happens

**Decision**: During Phase-2 job execution (not Phase-1)
**Rationale**:
- **Raw Data Preservation**: Phase-1 stores raw JSON without schema constraints
- **Transformation Context**: Schema evolution happens where transformations are applied
- **Performance**: Schema inference happens once per batch, not per message

### 3. Fail-Fast vs Graceful Degradation

**Decision**: Fail-fast for breaking changes
**Rationale**:
- **Data Quality**: Prevents data corruption or loss
- **Explicit Handling**: Forces conscious decision about breaking changes
- **Operational Safety**: Better to stop than produce incorrect results

## Schema Evolution Scenarios

### Scenario 1: Initial Dataset Creation

```bash
# 1. Create dataset (no schema yet)
curl -X POST http://localhost:8000/datasets \
  -d '{"name": "orders", "kafka_topic": "orders", ...}'

# 2. First data arrives → Phase-2 job runs
# 3. Schema automatically inferred and stored
# 4. SchemaVersion created with status="ACTIVE"
```

### Scenario 2: Non-Breaking Change (New Field)

```json
// Old schema: {"id": 1, "customer": "John"}
// New schema: {"id": 1, "customer": "John", "email": "john@example.com"}
```

**Result**: 
- Processing continues
- New schema version created
- Old version marked "OBSOLETE"

### Scenario 3: Breaking Change (Field Removal)

```json
// Old schema: {"id": 1, "customer": "John", "amount": 100.0}
// New schema: {"id": 1, "customer": "John"}  // amount field removed
```

**Result**:
- Job fails immediately
- Error logged with details
- Schema marked "BLOCKED"
- Manual intervention required

## Monitoring and Troubleshooting

### 1. Check Schema Versions

```bash
# Get all schema versions for a dataset
curl http://localhost:8000/datasets/1/schema/versions

# Get active schema
curl http://localhost:8000/datasets/1/schema/active
```

### 2. Schema Evolution Logs

Look for these log messages in Phase-2 job:
- `"Current inferred schema: ..."`
- `"Found existing active schema, comparing for evolution..."`
- `"Breaking schema change detected - job terminated"`
- `"Updating schema in metastore due to non-breaking changes"`

### 3. Common Issues

**Issue**: "No active schema found" on subsequent runs
**Cause**: Initial schema creation failed
**Solution**: Check Metastore connectivity and database

**Issue**: "Breaking schema change detected" 
**Cause**: Incompatible data structure changes
**Solution**: Review data source changes, consider data migration

## Best Practices

### 1. Data Source Management
- Coordinate schema changes with data producers
- Use additive changes when possible
- Test schema changes in development first

### 2. Monitoring
- Monitor schema evolution endpoints
- Set up alerts for breaking changes
- Regular review of schema versions

### 3. Recovery Procedures
- Have rollback procedures for breaking changes
- Document schema change processes
- Maintain schema change history

## API Endpoints

### Schema Management Endpoints

```bash
# Get active schema
GET /datasets/{id}/schema/active

# Get all schema versions
GET /datasets/{id}/schema/versions

# Handle schema evolution (used by Spark jobs)
POST /datasets/{id}/schema/evolve
{
  "schema_json": "...",
  "change_type": "INITIAL|NON_BREAKING|BREAKING"
}
```

## Future Enhancements

1. **Schema Registry Integration**: Connect with Confluent Schema Registry
2. **Schema Validation**: Pre-validate schemas before processing
3. **Schema Migration Tools**: Automated data migration for breaking changes
4. **Schema Lineage**: Track schema evolution history and impact
5. **Custom Evolution Rules**: Allow custom rules per dataset
