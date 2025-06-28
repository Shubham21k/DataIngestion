#!/bin/bash

# Validation Script for Working Demo
# This script validates that the working demo completed successfully

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}🔍 VALIDATING WORKING DEMO RESULTS${NC}"
echo -e "${CYAN}===================================${NC}"
echo

# Track validation results
VALIDATION_PASSED=0
TOTAL_CHECKS=0

# Function to check and report
check_result() {
    local description="$1"
    local command="$2"
    local expected_result="$3"
    
    ((TOTAL_CHECKS++))
    echo -n "Checking $description... "
    
    if eval "$command" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ PASS${NC}"
        ((VALIDATION_PASSED++))
        return 0
    else
        echo -e "${RED}❌ FAIL${NC}"
        return 1
    fi
}

# Function to check and show result
check_and_show() {
    local description="$1"
    local command="$2"
    
    ((TOTAL_CHECKS++))
    echo -e "${YELLOW}$description:${NC}"
    
    if result=$(eval "$command" 2>/dev/null); then
        echo -e "${GREEN}✅ SUCCESS${NC}"
        echo "$result"
        ((VALIDATION_PASSED++))
        echo
        return 0
    else
        echo -e "${RED}❌ FAILED${NC}"
        echo
        return 1
    fi
}

echo -e "${YELLOW}📊 DOCKER SERVICES VALIDATION${NC}"
echo "================================"

# Check if Docker Compose services are running
check_result "Docker Compose services" "docker-compose -f docker-compose-working.yml ps | grep -q 'Up'"

# Check individual services
check_result "Kafka service" "docker-compose -f docker-compose-working.yml ps kafka | grep -q 'Up'"
check_result "PostgreSQL service" "docker-compose -f docker-compose-working.yml ps postgres | grep -q 'Up'"
check_result "LocalStack service" "docker-compose -f docker-compose-working.yml ps localstack | grep -q 'Up'"
check_result "Kafka UI service" "docker-compose -f docker-compose-working.yml ps kafka-ui | grep -q 'Up'"

echo
echo -e "${YELLOW}🌐 SERVICE CONNECTIVITY VALIDATION${NC}"
echo "==================================="

# Check service ports
check_result "Kafka port (9092)" "nc -z localhost 9092"
check_result "PostgreSQL port (5432)" "nc -z localhost 5432"
check_result "LocalStack port (4566)" "nc -z localhost 4566"
check_result "Kafka UI port (8080)" "nc -z localhost 8080"

echo
echo -e "${YELLOW}📋 KAFKA TOPICS VALIDATION${NC}"
echo "=========================="

# Check Kafka topics
check_and_show "Kafka Topics List" "docker-compose -f docker-compose-working.yml exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list"

# Check specific topics exist
check_result "E-commerce orders topic" "docker-compose -f docker-compose-working.yml exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list | grep -q 'ecommerce.orders'"
check_result "RDS users topic" "docker-compose -f docker-compose-working.yml exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list | grep -q 'mysql.inventory.users'"
check_result "MongoDB products topic" "docker-compose -f docker-compose-working.yml exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list | grep -q 'mongodb.catalog.products'"
check_result "Schema evolution topic" "docker-compose -f docker-compose-working.yml exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list | grep -q 'schema.evolution.demo'"

echo
echo -e "${YELLOW}📊 DATA VALIDATION${NC}"
echo "=================="

# Check data in topics
echo -e "${BLUE}Sample data from E-commerce orders topic:${NC}"
if docker-compose -f docker-compose-working.yml exec -T kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic ecommerce.orders --from-beginning --max-messages 1 --timeout-ms 5000 2>/dev/null; then
    echo -e "${GREEN}✅ E-commerce data found${NC}"
    ((VALIDATION_PASSED++))
else
    echo -e "${RED}❌ No E-commerce data found${NC}"
fi
((TOTAL_CHECKS++))

echo
echo -e "${BLUE}Sample data from RDS users topic:${NC}"
if docker-compose -f docker-compose-working.yml exec -T kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic mysql.inventory.users --from-beginning --max-messages 1 --timeout-ms 5000 2>/dev/null; then
    echo -e "${GREEN}✅ RDS CDC data found${NC}"
    ((VALIDATION_PASSED++))
else
    echo -e "${RED}❌ No RDS CDC data found${NC}"
fi
((TOTAL_CHECKS++))

echo
echo -e "${BLUE}Sample data from MongoDB products topic:${NC}"
if docker-compose -f docker-compose-working.yml exec -T kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic mongodb.catalog.products --from-beginning --max-messages 1 --timeout-ms 5000 2>/dev/null; then
    echo -e "${GREEN}✅ MongoDB CDC data found${NC}"
    ((VALIDATION_PASSED++))
else
    echo -e "${RED}❌ No MongoDB CDC data found${NC}"
fi
((TOTAL_CHECKS++))

echo
echo -e "${YELLOW}🗄️ S3 BUCKETS VALIDATION${NC}"
echo "========================"

# Check S3 buckets
check_and_show "S3 Buckets List" "docker-compose -f docker-compose-working.yml exec -T localstack awslocal s3 ls"

# Check specific buckets
check_result "Raw data bucket" "docker-compose -f docker-compose-working.yml exec -T localstack awslocal s3 ls | grep -q 'raw'"
check_result "Lake data bucket" "docker-compose -f docker-compose-working.yml exec -T localstack awslocal s3 ls | grep -q 'lake'"
check_result "Jars bucket" "docker-compose -f docker-compose-working.yml exec -T localstack awslocal s3 ls | grep -q 'jars'"

echo
echo -e "${YELLOW}🔗 ACCESS POINTS VALIDATION${NC}"
echo "=========================="

# Check web interfaces
check_result "Kafka UI accessibility" "curl -s -f http://localhost:8080 > /dev/null"
check_result "LocalStack S3 API" "docker-compose -f docker-compose-working.yml exec -T localstack awslocal s3 ls > /dev/null"

echo
echo -e "${CYAN}📊 VALIDATION SUMMARY${NC}"
echo -e "${CYAN}===================${NC}"

if [ $VALIDATION_PASSED -eq $TOTAL_CHECKS ]; then
    echo -e "${GREEN}🎉 ALL VALIDATIONS PASSED! ($VALIDATION_PASSED/$TOTAL_CHECKS)${NC}"
    echo -e "${GREEN}✅ Your demo is working perfectly!${NC}"
    echo
    echo -e "${CYAN}🌐 ACCESS YOUR DEMO:${NC}"
    echo -e "${BLUE}• Kafka UI:${NC} http://localhost:8080"
    echo -e "${BLUE}• PostgreSQL:${NC} localhost:5432 (postgres/password)"
    echo -e "${BLUE}• LocalStack S3:${NC} http://localhost:4566"
    echo -e "${BLUE}• Kafka Bootstrap:${NC} localhost:9092"
    echo
    echo -e "${CYAN}🎯 PROOF OF SUCCESS:${NC}"
    echo -e "${GREEN}• 4 Kafka topics created with multi-source CDC data${NC}"
    echo -e "${GREEN}• 3 S3 buckets ready for data lake operations${NC}"
    echo -e "${GREEN}• All services running and accessible${NC}"
    echo -e "${GREEN}• Complete data pipeline infrastructure ready${NC}"
    exit 0
else
    echo -e "${RED}❌ SOME VALIDATIONS FAILED ($VALIDATION_PASSED/$TOTAL_CHECKS passed)${NC}"
    echo -e "${YELLOW}⚠️  Check the failed items above and re-run the demo if needed${NC}"
    exit 1
fi
