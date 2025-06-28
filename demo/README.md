# Kafka Ingestion Platform - Demo

## 🚀 **Quick Start**

### **1. Run the Demo**
```bash
cd demo
./demo.sh
```

### **2. Validate Everything Works**
```bash
./validate.sh
```

### **3. Test Complete Pipeline**
```bash
./test_pipeline.sh
```

## 📁 **Demo Files**

- **`demo.sh`** - Main demo script (starts services, creates data)
- **`validate.sh`** - Validates infrastructure and data (23 checks)
- **`test_pipeline.sh`** - Tests ingestion, schema evolution, transformations (10 tests)
- **`demo.md`** - Detailed documentation and manual steps
- **`README.md`** - This quick reference

## ✅ **What the Demo Proves**

- ✅ **Infrastructure**: Kafka, PostgreSQL, LocalStack S3 running
- ✅ **Multi-source CDC**: E-commerce, RDS, MongoDB data streams
- ✅ **Schema Evolution**: Backward compatible schema changes
- ✅ **Data Transformations**: Business logic with calculated fields
- ✅ **Upsert Operations**: CDC data processing with state management
- ✅ **End-to-End Pipeline**: Complete data flow validation

## 🌐 **Access Points**

- **Kafka UI**: http://localhost:8080
- **PostgreSQL**: localhost:5432 (postgres/password)
- **LocalStack S3**: http://localhost:4566
- **Kafka Bootstrap**: localhost:9092

## 🛑 **Stop Demo**
```bash
docker-compose -f docker-compose-working.yml down
```

## 🚨 **Troubleshooting**

### **If Demo Fails**

#### **Platform Deployment Issues**
```bash
# Check if entrypoint.sh is executable
ls -la ../entrypoint.sh
chmod +x ../entrypoint.sh

# Try quick demo instead
./quick_demo.sh
```

#### **Service Startup Issues**
```bash
# Check Docker resources
docker system df
docker system prune -f

# Restart Docker Compose
cd ..
docker-compose down
docker-compose up -d
```

#### **Build Issues**
```bash
# Check Java/Maven versions
java -version
mvn -version

# Clean and rebuild
cd ..
mvn clean compile -f spark/pom.xml
mvn clean compile -f metastore-java/pom.xml
```

#### **Port Conflicts**
```bash
# Check what's using the ports
lsof -i :8080  # Airflow
lsof -i :8000  # Metastore
lsof -i :9090  # Metrics
lsof -i :9092  # Kafka

# Kill conflicting processes if needed
sudo kill -9 <PID>
```

### **Demo Validation**
```bash
# Validate demo results
./validate_demo.sh

# Check service logs
cd ..
docker-compose logs -f
```
