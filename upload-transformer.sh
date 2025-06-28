#!/bin/bash
set -e

# Color codes for better readability
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if JAR path is provided
if [ "$#" -ne 1 ]; then
    echo -e "${RED}Error: Please provide the path to the transformer JAR${NC}"
    echo -e "Usage: $0 /path/to/transformer.jar"
    exit 1
fi

JAR_PATH="$1"
JAR_NAME=$(basename "$JAR_PATH")

# Check if JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${RED}Error: JAR file not found: $JAR_PATH${NC}"
    exit 1
fi

# Check if LocalStack is running
if ! docker-compose ps | grep -q "localstack.*running"; then
    echo -e "${RED}Error: LocalStack is not running. Please start the stack with 'docker-compose up -d'${NC}"
    exit 1
fi

echo -e "${BLUE}=========================================================${NC}"
echo -e "${GREEN}Uploading Transformer JAR to S3${NC}"
echo -e "${BLUE}=========================================================${NC}"

# Create jars bucket if it doesn't exist
echo -e "${YELLOW}Creating jars bucket if it doesn't exist...${NC}"
docker-compose exec -T localstack awslocal s3 mb s3://lake/jars --region us-east-1 2>/dev/null || true

# Upload JAR to S3
echo -e "${YELLOW}Uploading $JAR_NAME to S3...${NC}"
docker cp "$JAR_PATH" localstack:/tmp/"$JAR_NAME"
docker-compose exec -T localstack awslocal s3 cp /tmp/"$JAR_NAME" s3://lake/jars/"$JAR_NAME" --region us-east-1

# Verify upload
echo -e "${YELLOW}Verifying upload...${NC}"
if docker-compose exec -T localstack awslocal s3 ls s3://lake/jars/"$JAR_NAME" --region us-east-1 >/dev/null 2>&1; then
    echo -e "${GREEN}✓ JAR uploaded successfully to s3://lake/jars/$JAR_NAME${NC}"
else
    echo -e "${RED}Failed to upload JAR${NC}"
    exit 1
fi

echo -e "\n${YELLOW}To use this transformer, update your dataset in the Metastore:${NC}"
echo -e "curl -X PATCH http://localhost:8000/datasets/1 \\"
echo -e "  -H \"Content-Type: application/json\" \\"
echo -e "  -d '{\"transformJars\": [\"s3://lake/jars/$JAR_NAME\"]}'"

echo -e "\n${YELLOW}Quick build and upload sample transformers:${NC}"
echo -e "cd sample-transformers && mvn clean package -DskipTests"
echo -e "./upload-transformer.sh sample-transformers/target/sample-transformers-0.1.0.jar"

echo -e "\n${BLUE}=========================================================${NC}"
