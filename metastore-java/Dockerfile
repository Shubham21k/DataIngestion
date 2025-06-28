# Multi-stage build for Java Metastore service
FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first for better Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S metastore && \
    adduser -u 1001 -S metastore -G metastore

# Copy the built JAR
COPY --from=builder /app/target/metastore-service-*.jar app.jar

# Change ownership
RUN chown -R metastore:metastore /app

# Switch to non-root user
USER metastore

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8000/health || exit 1

# Expose port
EXPOSE 8000

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
