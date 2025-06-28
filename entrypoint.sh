#!/bin/bash
set -e

# Color codes for better readability
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================================${NC}"
echo -e "${GREEN}Kafka to Lakehouse Ingestion Framework - Setup Script${NC}"
echo -e "${BLUE}=========================================================${NC}"

# Function to print section headers
section() {
  echo -e "\n${YELLOW}>>> $1${NC}"
}

# Function to check if a command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
section "Checking prerequisites"

# Check Docker
if ! command_exists docker; then
  echo -e "${RED}Docker is not installed. Please install Docker first.${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Docker is installed${NC}"

# Check Docker Compose
if ! command_exists docker-compose; then
  echo -e "${RED}Docker Compose is not installed. Please install Docker Compose first.${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Docker Compose is installed${NC}"

# Check SBT
if ! command_exists sbt; then
  echo -e "${RED}SBT is not installed. Please install SBT first.${NC}"
  echo -e "${YELLOW}You can install SBT using:${NC}"
  echo -e "  - Mac: brew install sbt"
  echo -e "  - Linux: https://www.scala-sbt.org/download.html"
  exit 1
fi
echo -e "${GREEN}✓ SBT is installed${NC}"

# Check Java
if ! command_exists java; then
  echo -e "${RED}Java is not installed. Please install Java 8 or 11 first.${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Java is installed${NC}"

# Make init scripts executable
section "Making init scripts executable"
chmod +x init-services.sh upload-transformer.sh
echo -e "${GREEN}✓ Made init scripts executable${NC}"

# Build Java Metastore service
section "Building Java Metastore service"
echo "Building Java Metastore service..."
cd metastore-java
mvn clean package -DskipTests
cd ..

echo "Building Metrics service..."
cd metrics-service
mvn clean package -DskipTests
cd ..
echo -e "${GREEN}✓ Java Metastore service built successfully${NC}"

# Build Spark jobs
section "Building Spark jobs"
echo "This may take a few minutes..."
cd spark
mvn clean package -DskipTests
cd ..

# Build sample transformers
section "Building sample transformers"
echo "These will be available for use in your pipelines..."
cd sample-transformers
mvn clean package -DskipTests
cd ..
echo -e "${GREEN}✓ All components built successfully${NC}"

# Start Docker Compose stack
section "Starting Docker Compose stack"
docker-compose up -d
echo -e "${GREEN}✓ Docker Compose stack started${NC}"

# Wait for services to be ready
section "Waiting for services to be ready"
echo "This may take a minute or two..."

# Wait for PostgreSQL
echo "Waiting for PostgreSQL..."
until docker-compose exec -T postgres pg_isready -U postgres > /dev/null 2>&1; do
  echo -n "."
  sleep 2
done
echo -e "\n${GREEN}✓ PostgreSQL is ready${NC}"

# Wait for Metastore
echo "Waiting for Metastore..."
until curl -s http://localhost:8000/health > /dev/null 2>&1; do
  echo -n "."
  sleep 2
done
echo -e "\n${GREEN}✓ Metastore is ready${NC}"

# Wait for Airflow
echo "Waiting for Airflow..."
until curl -s http://localhost:8002/health > /dev/null 2>&1; do
  echo -n "."
  sleep 5
done
echo -e "\n${GREEN}✓ Airflow is ready${NC}"

# Wait for LocalStack
echo "Waiting for LocalStack..."
until docker-compose exec -T localstack awslocal s3 ls > /dev/null 2>&1; do
  echo -n "."
  sleep 2
done
echo -e "\n${GREEN}✓ LocalStack is ready${NC}"

# Wait for Spark
echo "Waiting for Spark..."
until curl -s http://localhost:8001 > /dev/null 2>&1; do
  echo -n "."
  sleep 2
done
echo -e "\n${GREEN}✓ Spark is ready${NC}"

# Set up Airflow connection
section "Setting up Airflow connection"
docker-compose exec -T airflow-webserver airflow connections add 'spark_default' \
  --conn-type 'spark' \
  --conn-host 'spark://spark-master' \
  --conn-port '7077' \
  --conn-extra '{"deploy-mode":"client"}' 2>/dev/null || echo "Connection may already exist"
echo -e "${GREEN}✓ Airflow connection set up${NC}"

# Upload sample transformers to S3
section "Uploading sample transformers to S3"
echo "Making transformers available in S3..."
docker-compose exec -T localstack awslocal s3 mb s3://lake/jars --region us-east-1 2>/dev/null || true
docker cp sample-transformers/target/scala-2.12/sample-transformers-0.1.0.jar localstack:/tmp/sample-transformers.jar
docker-compose exec -T localstack awslocal s3 cp /tmp/sample-transformers.jar s3://lake/jars/sample-transformers.jar --region us-east-1
echo -e "${GREEN}✓ Sample transformers uploaded to S3${NC}"

# Create test dataset
section "Creating test dataset in Metastore"
curl -s -X POST http://localhost:8000/datasets \
  -H "Content-Type: application/json" \
  -d '{
    "name": "orders",
    "kafka_topic": "orders",
    "mode": "append",
    "pk_fields": ["id"],
    "partition_keys": ["date"],
    "transform_jars": ["s3://lake/jars/sample-transformers.jar"]
  }' > /dev/null
echo -e "${GREEN}✓ Test dataset created (schema will be auto-inferred from first data)${NC}"

# Create test data in Kafka
section "Creating test data in Kafka"
echo '{"id": 1, "customer": "John", "amount": 100, "date": "2025-06-24"}' > /tmp/test_data.json
echo '{"id": 2, "customer": "Jane", "amount": 200, "date": "2025-06-24"}' >> /tmp/test_data.json
docker-compose exec -T redpanda rpk topic create orders || echo "Topic already exists"
cat /tmp/test_data.json | docker-compose exec -T redpanda rpk topic produce orders
rm /tmp/test_data.json
echo -e "${GREEN}✓ Test data created in Kafka${NC}"

# Enable and trigger Airflow DAGs
section "Enabling and triggering Airflow DAGs"
docker-compose exec -T airflow-webserver airflow dags unpause ingestion_phase1_orders
docker-compose exec -T airflow-webserver airflow dags trigger ingestion_phase1_orders
echo -e "${GREEN}✓ Phase-1 DAG triggered${NC}"

# Wait for Phase-1 to complete
echo "Waiting for Phase-1 to complete (30 seconds)..."
sleep 30

# Enable and trigger Phase-2 DAG
docker-compose exec -T airflow-webserver airflow dags unpause ingestion_phase2_orders
docker-compose exec -T airflow-webserver airflow dags trigger ingestion_phase2_orders
echo -e "${GREEN}✓ Phase-2 DAG triggered${NC}"

# Verify data in S3
section "Verifying data in S3"
echo "Raw data:"
docker-compose exec -T localstack awslocal s3 ls s3://raw/orders/ || echo "No data yet"
echo "Lakehouse data:"
docker-compose exec -T localstack awslocal s3 ls s3://lake/orders/ || echo "No data yet"

# Print access URLs
section "Access URLs"
echo -e "Airflow: ${BLUE}http://localhost:8002${NC} (username: admin, password: admin)"
echo -e "Metastore API: ${BLUE}http://localhost:8000/docs${NC}"
echo -e "Spark UI: ${BLUE}http://localhost:8001${NC}"
echo -e "Redpanda Console: ${BLUE}http://localhost:8080${NC}"

echo -e "\n${GREEN}Setup complete! The ingestion framework is now running.${NC}"
echo -e "${YELLOW}To monitor logs:${NC}"
echo -e "  - Spark: docker-compose logs -f spark-master"
echo -e "  - Metastore: docker-compose logs -f metastore"
echo -e "  - Airflow: docker-compose logs -f airflow-scheduler"

echo -e "\n${YELLOW}Sample transformers:${NC}"
echo -e "The following transformers are available in the sample JAR:"
echo -e "  - ${BLUE}TimestampEnricher${NC}: Adds processed_at timestamp and processing_date columns"
echo -e "  - ${BLUE}AmountCalculator${NC}: Calculates amount_with_tax and adds amount_category"
echo -e "  - ${BLUE}DataQualityChecker${NC}: Adds data quality flags and scoring"

echo -e "\n${BLUE}=========================================================${NC}"
echo -e "${GREEN}Thank you for using the Kafka to Lakehouse Ingestion Framework${NC}"
echo -e "${BLUE}=========================================================${NC}"
