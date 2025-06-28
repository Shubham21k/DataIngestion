#!/bin/bash
set -e

# Color codes for better readability
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================================${NC}"
echo -e "${GREEN}Ingestion Framework - Service Initialization${NC}"
echo -e "${BLUE}=========================================================${NC}"

# Function to print section headers
section() {
  echo -e "\n${YELLOW}>>> $1${NC}"
}

# Function to wait for service with timeout
wait_for_service() {
  local service_name=$1
  local check_command=$2
  local timeout=${3:-60}
  local interval=${4:-2}
  
  echo "Waiting for $service_name to be ready (timeout: ${timeout}s)..."
  local count=0
  until eval "$check_command" > /dev/null 2>&1; do
    if [ $count -ge $timeout ]; then
      echo -e "${RED}Timeout waiting for $service_name${NC}"
      return 1
    fi
    echo -n "."
    sleep $interval
    count=$((count + interval))
  done
  echo -e "\n${GREEN}✓ $service_name is ready${NC}"
}

# =============================================================================
# POSTGRES INITIALIZATION
# =============================================================================
section "Initializing PostgreSQL databases"

# Function to create database (used by postgres init)
create_db() {
    local database=$1
    local username=$1
    local password=$1
    echo "Creating database '$database' with user '$username'..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE USER $username WITH PASSWORD '$password';
        CREATE DATABASE $database;
        GRANT ALL PRIVILEGES ON DATABASE $database TO $username;
EOSQL
}

# Create multiple databases if specified
if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
    echo "Multiple database creation requested: $POSTGRES_MULTIPLE_DATABASES"
    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
        create_db "$db"
    done
    echo -e "${GREEN}✓ Multiple databases created${NC}"
else
    echo "No multiple databases specified, skipping..."
fi

# =============================================================================
# LOCALSTACK/S3 INITIALIZATION
# =============================================================================
section "Initializing LocalStack S3 buckets"

# Wait for LocalStack to be ready
wait_for_service "LocalStack" "curl -s http://localstack:4566/health | grep -q '\"s3\": \"running\"'" 60 2

# Create necessary S3 buckets
echo "Creating S3 buckets..."
awslocal s3 mb s3://raw --region us-east-1 2>/dev/null || echo "Bucket 'raw' already exists"
awslocal s3 mb s3://lake --region us-east-1 2>/dev/null || echo "Bucket 'lake' already exists"
awslocal s3 mb s3://checkpoints --region us-east-1 2>/dev/null || echo "Bucket 'checkpoints' already exists"

# Create jars bucket for transformers
awslocal s3 mb s3://lake/jars --region us-east-1 2>/dev/null || echo "Bucket 'lake/jars' already exists"

echo -e "${GREEN}✓ S3 buckets created${NC}"

# =============================================================================
# KAFKA/REDPANDA INITIALIZATION
# =============================================================================
section "Initializing Kafka topics"

# Wait for Redpanda to be ready
wait_for_service "Redpanda" "curl -s http://redpanda:8082/admin/topics" 60 2

# Create test Kafka topic
echo "Creating test Kafka topic 'orders'..."
curl -s -X POST redpanda:8082/admin/topics \
  -H "Content-Type: application/json" \
  -d '{"name":"orders","partitions":3,"replication_factor":1}' || echo "Topic may already exist"

echo -e "${GREEN}✓ Kafka topics created${NC}"

# =============================================================================
# SPARK INITIALIZATION
# =============================================================================
section "Initializing Spark environment"

# Set environment variables for LocalStack S3 access
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

echo "Spark environment variables configured for LocalStack S3 access"
echo -e "${GREEN}✓ Spark initialization complete${NC}"

# =============================================================================
# AIRFLOW INITIALIZATION
# =============================================================================
section "Initializing Airflow connections"

# Wait for Airflow webserver to be ready
wait_for_service "Airflow" "curl -s http://airflow-webserver:8080/health" 120 5

# Add Spark connection
echo "Adding Spark connection to Airflow..."
airflow connections add 'spark_default' \
    --conn-type 'spark' \
    --conn-host 'spark://spark-master' \
    --conn-port '7077' \
    --conn-extra '{"deploy-mode":"client"}' 2>/dev/null || echo "Connection may already exist"

echo -e "${GREEN}✓ Airflow connections configured${NC}"

# =============================================================================
# METASTORE INITIALIZATION
# =============================================================================
section "Initializing Metastore service"

# Wait for Metastore to be ready
wait_for_service "Metastore" "curl -s http://metastore:8000/health" 60 2

echo -e "${GREEN}✓ Metastore service is ready${NC}"

# =============================================================================
# COMPLETION
# =============================================================================
echo -e "\n${BLUE}=========================================================${NC}"
echo -e "${GREEN}All services initialized successfully!${NC}"
echo -e "${BLUE}=========================================================${NC}"

echo -e "\n${YELLOW}Service Status:${NC}"
echo -e "  - PostgreSQL: Multiple databases created"
echo -e "  - LocalStack: S3 buckets ready"
echo -e "  - Redpanda: Kafka topics created"
echo -e "  - Spark: Environment configured"
echo -e "  - Airflow: Connections established"
echo -e "  - Metastore: Service ready"

echo -e "\n${YELLOW}Next Steps:${NC}"
echo -e "  1. Create datasets via Metastore API"
echo -e "  2. Upload transformer JARs to S3"
echo -e "  3. Trigger Airflow DAGs"
echo -e "  4. Monitor processing via UIs"
