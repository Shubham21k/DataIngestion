# Kafka-Based Ingestion Platform

**Enterprise-grade, petabyte-scale ingestion platform** for streaming data from Kafka to Lakehouse with schema evolution, transformations, and comprehensive monitoring.

## 🎯 **Overview**

Complete solution for ingesting multi-source CDC streams (RDS, MongoDB, Aerospike, ClickStream) from Kafka into a Lakehouse architecture with:

- **Two-phase ingestion**: Kafka → Raw S3 → Transformed Lakehouse
- **Dual processing modes**: Append and Upsert via configuration
- **Schema evolution**: Automatic detection with fail-fast protection
- **Dynamic transformations**: Runtime JAR loading from S3
- **Failure recovery**: Raw data dump with Kafka metadata for backfilling
- **Enterprise monitoring**: Custom metrics service with Prometheus export
- **Airflow orchestration**: Complete workflow management


## 📋 **Prerequisites**

- **Java 17+**
- **Maven 3.8+**
- **Docker & Docker Compose**
- **8GB+ RAM** recommended

## 🛠️ **Technology Stack**

| Component | Technology | Purpose |
|-----------|------------|----------|
| **Data Processing** | Spark 3.4.1 + Scala 2.12 | Phase-1 and Phase-2 jobs |
| **Metastore** | Java 17 + Spring Boot 3.2 | Dataset configuration management |
| **Metrics** | Java 17 + Spring Boot 3.2 | Custom metrics collection |
| **Orchestration** | Apache Airflow | Workflow management |
| **Storage** | PostgreSQL | Metadata persistence |
| **Streaming** | Redpanda (Kafka) | Message broker |
| **Object Storage** | LocalStack S3 | Data lake storage |
| **Upserts** | Apache Hudi | ACID transactions |
| **Deployment** | Docker Compose | Local environment |

## 🚀 **Quick Start**

### 1. One-Command Deployment
```bash
# Clone and navigate to project
cd ingestion_framework

Alternatively, you can manually add the connection in the Airflow UI:
1. Go to Admin > Connections
2. Add a new connection:
   - Conn Id: `spark_default`
   - Conn Type: `Spark`
   - Host: `spark://spark-master`
   - Port: `7077`
   - Extra: `{"deploy-mode":"client"}`

### 5. Access the Services

- **Airflow**: http://localhost:8002 (username: admin, password: admin)
- **Metastore API**: http://localhost:8000/docs (Swagger UI)
- **Spark UI**: http://localhost:8001
- **Redpanda Console**: http://localhost:8080
- **LocalStack**: http://localhost:4566

## Creating a New Dataset

1. Build the Spark JAR first if you haven't already:

```bash
cd spark
mvn clean package -DskipTests
cd ..
```

2. Register a dataset in the Metastore:

```bash
curl -X POST http://localhost:8000/datasets \
  -H "Content-Type: application/json" \
  -d '{
    "name": "orders",
    "kafka_topic": "orders",
    "mode": "append",
    "pk_fields": ["id"],
    "partition_keys": ["date"],
    "transform_jars": []
  }'
```

2. Create test data in Kafka:

```bash
docker-compose exec redpanda rpk topic produce orders
{"id": 1, "customer": "John", "amount": 100, "date": "2025-06-24"}
{"id": 2, "customer": "Jane", "amount": 200, "date": "2025-06-24"}
```

3. Trigger the Airflow DAGs:

- Go to Airflow UI
- Enable and trigger `ingestion_phase1_orders`
- Once it completes, enable and trigger `ingestion_phase2_orders`

4. Verify data in S3:

```bash
docker-compose exec localstack awslocal s3 ls s3://raw/orders/
docker-compose exec localstack awslocal s3 ls s3://lake/orders/
```

## Schema Evolution

To add a new column:

```bash
curl -X POST http://localhost:8000/datasets/1/ddl \
  -H "Content-Type: application/json" \
  -d '{
    "ddl_sql": "ALTER TABLE orders ADD COLUMN status STRING"
  }'
```

The next Phase-2 run will automatically pick up the schema change.

## Transformations

### Built-in Transformers

The framework includes several built-in transformers in the main JAR:

1. `CastTransformer`: Casts a column to a specified data type
2. `FlattenTransformer`: Flattens nested structures to top-level columns
3. `ArrayJsonToStructTypeTransformer`: Converts JSON array strings to structured types

### Custom Transformers

To create a custom transformer:

1. Create a Scala class implementing the `Transformer` interface:

```scala
package com.example.transform

import org.apache.spark.sql.{Dataset, Row}
import org.apache.spark.sql.functions._

class CustomTransformer extends Transformer {
  override def transform(ds: Dataset[Row]): Dataset[Row] = {
    // Your transformation logic here
    ds.withColumn("transformed_at", current_timestamp())
  }
}
```

2. Build the JAR:

```bash
mvn clean package -DskipTests
```

3. Upload it to S3 using the provided helper script:

```bash
./upload-transformer.sh /path/to/your-transformer.jar
```

Or manually:

```bash
docker-compose exec localstack awslocal s3 cp my-transformer.jar s3://lake/jars/
```

4. Update the dataset to use the transformer:

```bash
curl -X PATCH http://localhost:8000/datasets/1 \
  -H "Content-Type: application/json" \
  -d '{
    "transform_jars": ["s3://lake/jars/my-transformer.jar"]
  }'
```

### How Transformers Are Loaded

Transformers are loaded dynamically at runtime:

1. The Phase-2 job fetches the list of transformer JARs from the Metastore
2. For each JAR, it creates a new ClassLoader
3. It attempts to load transformer classes with common naming patterns
4. Each transformer is instantiated and applied to the dataset in sequence

This allows you to extend the ingestion framework without modifying the core code.

## Extending the Metastore

The Metastore uses Java Spring Boot with JPA/Hibernate. To add new endpoints:

1. Add a new controller method in `metastore-java/src/main/java/com/example/metastore/controller/`
2. Update the JPA entities in `metastore-java/src/main/java/com/example/metastore/entity/`
3. Add repository methods if needed in `metastore-java/src/main/java/com/example/metastore/repository/`
4. Rebuild with `mvn clean package -DskipTests`

## Monitoring

Metrics are available via the Spark UI or by checking the Airflow logs for the `metrics_push` task.

## Troubleshooting

### Viewing Logs

```bash
# View Spark master logs
docker-compose logs -f spark-master

# View Metastore logs
docker-compose logs -f metastore

# View Airflow scheduler logs
docker-compose logs -f airflow-scheduler
```

### Common Issues

- **Metastore not starting**: 
  - Check PostgreSQL connection: `docker-compose logs postgres`
  - Ensure health check passes: `docker-compose ps postgres`

- **Spark jobs failing**: 
  - Check Spark UI for error logs: http://localhost:8001
  - Verify JAR exists: `ls -la spark/target/scala-2.12/`

- **S3 access issues**: 
  - Verify LocalStack is running: `docker-compose ps localstack`
  - Check if buckets are created: `docker-compose exec localstack awslocal s3 ls`

- **Airflow connection errors**: 
  - Run the init script: `docker-compose exec airflow-webserver bash -c '/opt/airflow/dags/init-airflow.sh'`
  - Or set up connection manually in Airflow Admin UI

- **Volume mount errors**:
  - Ensure directories exist before starting: `mkdir -p spark/target/scala-2.12/`

## Development

### Adding New Transformers

Create a class that implements the `Transformer` trait:

```scala
package com.example.transform

import org.apache.spark.sql.{Dataset, Row}

class MyCustomTransformer extends Transformer {
  override def transform(ds: Dataset[Row]): Dataset[Row] = {
    // Your transformation logic here
    ds
  }
}
```

Build and package it as a JAR, then follow the steps above to use it.

