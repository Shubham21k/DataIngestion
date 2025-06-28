package com.example.e2e

import org.apache.spark.sql.SparkSession
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.containers.{DockerComposeContainer, PostgreSQLContainer}
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.time.Duration
import scala.sys.process._
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * End-to-End tests for multi-source CDC ingestion
 * Tests the complete pipeline from Kafka to Lakehouse for different CDC sources
 */
class MultiSourceE2ETest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  private var dockerCompose: DockerComposeContainer[_] = _
  private var spark: SparkSession = _
  private val httpClient = HttpClient.newHttpClient()

  override def beforeAll(): Unit = {
    // Start Docker Compose environment
    dockerCompose = new DockerComposeContainer(new File("../docker-compose.yml"))
      .withExposedService("metastore", 8000, Wait.forHttp("/health").withStartupTimeout(Duration.ofMinutes(3)))
      .withExposedService("metrics-service", 9090, Wait.forHttp("/actuator/health").withStartupTimeout(Duration.ofMinutes(2)))
      .withExposedService("redpanda", 9092, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))
      .withExposedService("localstack", 4566, Wait.forHttp("/health").withStartupTimeout(Duration.ofMinutes(2)))
      .withExposedService("postgres", 5432, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)))

    dockerCompose.start()

    // Initialize Spark session
    spark = SparkSession.builder()
      .appName("MultiSourceE2ETest")
      .master("local[4]")
      .config("spark.sql.shuffle.partitions", "4")
      .config("spark.hadoop.fs.s3a.endpoint", s"http://localhost:${dockerCompose.getServicePort("localstack", 4566)}")
      .config("spark.hadoop.fs.s3a.access.key", "test")
      .config("spark.hadoop.fs.s3a.secret.key", "test")
      .config("spark.hadoop.fs.s3a.path.style.access", "true")
      .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    // Wait for services to be ready
    Thread.sleep(30000) // 30 seconds for all services to stabilize
  }

  override def afterAll(): Unit = {
    if (spark != null) {
      spark.stop()
    }
    if (dockerCompose != null) {
      dockerCompose.stop()
    }
  }

  test("E2E: RDS CDC Pipeline - MySQL Users Table") {
    val metastorePort = dockerCompose.getServicePort("metastore", 8000)
    val metricsPort = dockerCompose.getServicePort("metrics-service", 9090)
    val kafkaPort = dockerCompose.getServicePort("redpanda", 9092)

    // 1. Create dataset configuration in Metastore
    val datasetRequest = s"""{
      "name": "rds_users",
      "kafkaTopic": "mysql.inventory.users",
      "mode": "upsert",
      "pkFields": ["id"],
      "partitionKeys": ["created_date"],
      "transformJars": []
    }"""

    val createDatasetRequest = HttpRequest.newBuilder()
      .uri(URI.create(s"http://localhost:$metastorePort/datasets"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(datasetRequest))
      .build()

    val createResponse = httpClient.send(createDatasetRequest, HttpResponse.BodyHandlers.ofString())
    createResponse.statusCode() shouldBe 201

    // 2. Produce RDS CDC messages to Kafka
    val rdsMessages = Seq(
      s"""{"operation": "INSERT", "table_name": "users", "primary_key": 1, "data": "{\\"id\\": 1, \\"name\\": \\"John Doe\\", \\"email\\": \\"john@example.com\\", \\"created_date\\": \\"2025-06-28\\"}", "binlog_file": "mysql-bin.000001", "binlog_position": 12345, "timestamp": ${System.currentTimeMillis()}}""",
      s"""{"operation": "UPDATE", "table_name": "users", "primary_key": 1, "data": "{\\"id\\": 1, \\"name\\": \\"John Smith\\", \\"email\\": \\"john.smith@example.com\\", \\"updated_date\\": \\"2025-06-28\\"}", "binlog_file": "mysql-bin.000001", "binlog_position": 12346, "timestamp": ${System.currentTimeMillis()}}""",
      s"""{"operation": "INSERT", "table_name": "users", "primary_key": 2, "data": "{\\"id\\": 2, \\"name\\": \\"Jane Doe\\", \\"email\\": \\"jane@example.com\\", \\"created_date\\": \\"2025-06-28\\"}", "binlog_file": "mysql-bin.000001", "binlog_position": 12347, "timestamp": ${System.currentTimeMillis()}}"""
    )

    produceKafkaMessages("mysql.inventory.users", rdsMessages, kafkaPort)

    // 3. Run Phase-1 ingestion (Kafka -> Raw S3)
    val phase1Result = runSparkJob("Phase1Job", Array(
      "--topic", "mysql.inventory.users",
      "--raw-path", "s3a://raw/rds_users/",
      "--checkpoint", "s3a://checkpoints/phase1_rds_users/",
      "--kafka-servers", s"localhost:$kafkaPort"
    ))

    phase1Result shouldBe 0

    // 4. Verify raw data in S3
    Thread.sleep(10000) // Wait for processing
    val rawData = spark.read.json("s3a://raw/rds_users/")
    rawData.count() should be > 0L

    // 5. Run Phase-2 ingestion (Raw S3 -> Lakehouse)
    val phase2Result = runSparkJob("Phase2Job", Array(
      "--dataset", "rds_users",
      "--raw-path", "s3a://raw/rds_users/",
      "--lake-path", "s3a://lake/rds_users/",
      "--checkpoint", "s3a://checkpoints/phase2_rds_users/",
      "--metastore-url", s"http://localhost:$metastorePort"
    ))

    phase2Result shouldBe 0

    // 6. Verify processed data in Lakehouse
    Thread.sleep(10000) // Wait for processing
    val lakeData = spark.read.parquet("s3a://lake/rds_users/")
    lakeData.count() should be > 0L

    // Verify upsert behavior - should have 2 unique users
    val uniqueUsers = lakeData.select("id").distinct().count()
    uniqueUsers shouldBe 2

    // 7. Verify metrics were recorded
    val metricsRequest = HttpRequest.newBuilder()
      .uri(URI.create(s"http://localhost:$metricsPort/api/v1/metrics/dataset/rds_users"))
      .GET()
      .build()

    val metricsResponse = httpClient.send(metricsRequest, HttpResponse.BodyHandlers.ofString())
    metricsResponse.statusCode() shouldBe 200
    metricsResponse.body() should include("rds_users")
  }

  test("E2E: MongoDB CDC Pipeline - Orders Collection") {
    val metastorePort = dockerCompose.getServicePort("metastore", 8000)
    val metricsPort = dockerCompose.getServicePort("metrics-service", 9090)
    val kafkaPort = dockerCompose.getServicePort("redpanda", 9092)

    // 1. Create MongoDB dataset configuration
    val mongoDatasetRequest = s"""{
      "name": "mongo_orders",
      "kafkaTopic": "mongodb.ecommerce.orders",
      "mode": "upsert",
      "pkFields": ["_id"],
      "partitionKeys": ["order_date"],
      "transformJars": []
    }"""

    val createMongoRequest = HttpRequest.newBuilder()
      .uri(URI.create(s"http://localhost:$metastorePort/datasets"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(mongoDatasetRequest))
      .build()

    val mongoResponse = httpClient.send(createMongoRequest, HttpResponse.BodyHandlers.ofString())
    mongoResponse.statusCode() shouldBe 201

    // 2. Produce MongoDB CDC messages
    val mongoMessages = Seq(
      s"""{"operationType": "insert", "ns": {"db": "ecommerce", "coll": "orders"}, "fullDocument": "{\\"_id\\": \\"507f1f77bcf86cd799439011\\", \\"customer_id\\": 123, \\"amount\\": 99.99, \\"status\\": \\"pending\\", \\"order_date\\": \\"2025-06-28\\"}", "documentKey": "{\\"_id\\": \\"507f1f77bcf86cd799439011\\"}", "clusterTime": "${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}Z"}""",
      s"""{"operationType": "update", "ns": {"db": "ecommerce", "coll": "orders"}, "updateDescription": {"updatedFields": {"status": "completed"}}, "documentKey": "{\\"_id\\": \\"507f1f77bcf86cd799439011\\"}", "clusterTime": "${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}Z"}""",
      s"""{"operationType": "insert", "ns": {"db": "ecommerce", "coll": "orders"}, "fullDocument": "{\\"_id\\": \\"507f1f77bcf86cd799439012\\", \\"customer_id\\": 456, \\"amount\\": 149.99, \\"status\\": \\"pending\\", \\"order_date\\": \\"2025-06-28\\"}", "documentKey": "{\\"_id\\": \\"507f1f77bcf86cd799439012\\"}", "clusterTime": "${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}Z"}"""
    )

    produceKafkaMessages("mongodb.ecommerce.orders", mongoMessages, kafkaPort)

    // 3. Run complete pipeline
    runCompletePipeline("mongo_orders", "mongodb.ecommerce.orders", kafkaPort, metastorePort)

    // 4. Verify results
    val mongoLakeData = spark.read.parquet("s3a://lake/mongo_orders/")
    mongoLakeData.count() should be > 0L

    // Verify MongoDB-specific data structure
    val columns = mongoLakeData.columns
    columns should contain("_id")
    columns should contain("customer_id")
    columns should contain("amount")
  }

  test("E2E: ClickStream Pipeline - Web Events") {
    val metastorePort = dockerCompose.getServicePort("metastore", 8000)
    val kafkaPort = dockerCompose.getServicePort("redpanda", 9092)

    // 1. Create ClickStream dataset (append-only)
    val clickStreamRequest = s"""{
      "name": "clickstream_events",
      "kafkaTopic": "clickstream.web.events",
      "mode": "append",
      "pkFields": [],
      "partitionKeys": ["event_date", "event_hour"],
      "transformJars": []
    }"""

    val createClickRequest = HttpRequest.newBuilder()
      .uri(URI.create(s"http://localhost:$metastorePort/datasets"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(clickStreamRequest))
      .build()

    val clickResponse = httpClient.send(createClickRequest, HttpResponse.BodyHandlers.ofString())
    clickResponse.statusCode() shouldBe 201

    // 2. Produce ClickStream events
    val clickMessages = Seq(
      s"""{"event_type": "page_view", "user_id": "user123", "page_url": "/home", "timestamp": "${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}Z", "properties": "{\\"referrer\\": \\"google.com\\", \\"session_id\\": \\"sess_abc123\\"}", "ip_address": "192.168.1.100", "event_date": "2025-06-28", "event_hour": "10"}""",
      s"""{"event_type": "click", "user_id": "user123", "page_url": "/product/123", "timestamp": "${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}Z", "properties": "{\\"element\\": \\"buy_button\\", \\"product_id\\": \\"123\\"}", "ip_address": "192.168.1.100", "event_date": "2025-06-28", "event_hour": "10"}""",
      s"""{"event_type": "purchase", "user_id": "user123", "page_url": "/checkout", "timestamp": "${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}Z", "properties": "{\\"order_id\\": \\"ord_456\\", \\"amount\\": 99.99\\"}", "ip_address": "192.168.1.100", "event_date": "2025-06-28", "event_hour": "10"}"""
    )

    produceKafkaMessages("clickstream.web.events", clickMessages, kafkaPort)

    // 3. Run pipeline
    runCompletePipeline("clickstream_events", "clickstream.web.events", kafkaPort, metastorePort)

    // 4. Verify append-only behavior
    val clickLakeData = spark.read.parquet("s3a://lake/clickstream_events/")
    clickLakeData.count() shouldBe 3 // All events should be preserved

    // Verify event types
    import spark.implicits._
    val eventTypes = clickLakeData.select("event_type").distinct().as[String].collect().toSet
    eventTypes should contain("page_view")
    eventTypes should contain("click")
    eventTypes should contain("purchase")
  }

  test("E2E: Schema Evolution - Breaking Change Detection") {
    val metastorePort = dockerCompose.getServicePort("metastore", 8000)
    val kafkaPort = dockerCompose.getServicePort("redpanda", 9092)

    // 1. Create test dataset
    val schemaTestRequest = s"""{
      "name": "schema_test",
      "kafkaTopic": "test.schema.evolution",
      "mode": "append",
      "pkFields": [],
      "partitionKeys": ["date"],
      "transformJars": []
    }"""

    val createSchemaRequest = HttpRequest.newBuilder()
      .uri(URI.create(s"http://localhost:$metastorePort/datasets"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(schemaTestRequest))
      .build()

    httpClient.send(createSchemaRequest, HttpResponse.BodyHandlers.ofString())

    // 2. Produce initial data with original schema
    val originalMessages = Seq(
      s"""{"id": 1, "name": "Test1", "value": 100, "date": "2025-06-28"}""",
      s"""{"id": 2, "name": "Test2", "value": 200, "date": "2025-06-28"}"""
    )

    produceKafkaMessages("test.schema.evolution", originalMessages, kafkaPort)
    runCompletePipeline("schema_test", "test.schema.evolution", kafkaPort, metastorePort)

    // 3. Produce data with evolved schema (non-breaking - new nullable field)
    val evolvedMessages = Seq(
      s"""{"id": 3, "name": "Test3", "value": 300, "date": "2025-06-28", "new_field": "additional_data"}"""
    )

    produceKafkaMessages("test.schema.evolution", evolvedMessages, kafkaPort)
    
    // This should succeed (non-breaking change)
    val evolvedResult = runSparkJob("Phase2Job", Array(
      "--dataset", "schema_test",
      "--raw-path", "s3a://raw/schema_test/",
      "--lake-path", "s3a://lake/schema_test/",
      "--checkpoint", "s3a://checkpoints/phase2_schema_test_evolved/",
      "--metastore-url", s"http://localhost:$metastorePort"
    ))

    evolvedResult shouldBe 0

    // 4. Verify schema versions in Metastore
    val schemaVersionsRequest = HttpRequest.newBuilder()
      .uri(URI.create(s"http://localhost:$metastorePort/datasets/schema_test/schema/versions"))
      .GET()
      .build()

    val schemaVersionsResponse = httpClient.send(schemaVersionsRequest, HttpResponse.BodyHandlers.ofString())
    schemaVersionsResponse.statusCode() shouldBe 200
    schemaVersionsResponse.body() should include("new_field")
  }

  // Helper methods
  private def produceKafkaMessages(topic: String, messages: Seq[String], kafkaPort: Int): Unit = {
    messages.foreach { message =>
      val cmd = s"""docker-compose exec -T redpanda rpk topic produce $topic --brokers localhost:$kafkaPort"""
      val process = Process(cmd)
      val writer = process.run()
      writer.stdin.write(s"$message\n".getBytes())
      writer.stdin.close()
      writer.exitValue() shouldBe 0
    }
    Thread.sleep(5000) // Wait for messages to be produced
  }

  private def runSparkJob(jobClass: String, args: Array[String]): Int = {
    val sparkSubmit = s"""spark-submit --class com.example.$jobClass --master local[2] ../spark/target/ingestion-framework-assembly-0.1.0-SNAPSHOT.jar ${args.mkString(" ")}"""
    Process(sparkSubmit).!
  }

  private def runCompletePipeline(dataset: String, topic: String, kafkaPort: Int, metastorePort: Int): Unit = {
    // Phase 1
    val phase1Result = runSparkJob("Phase1Job", Array(
      "--topic", topic,
      "--raw-path", s"s3a://raw/$dataset/",
      "--checkpoint", s"s3a://checkpoints/phase1_$dataset/",
      "--kafka-servers", s"localhost:$kafkaPort"
    ))
    phase1Result shouldBe 0

    Thread.sleep(10000) // Wait for Phase 1

    // Phase 2
    val phase2Result = runSparkJob("Phase2Job", Array(
      "--dataset", dataset,
      "--raw-path", s"s3a://raw/$dataset/",
      "--lake-path", s"s3a://lake/$dataset/",
      "--checkpoint", s"s3a://checkpoints/phase2_$dataset/",
      "--metastore-url", s"http://localhost:$metastorePort"
    ))
    phase2Result shouldBe 0

    Thread.sleep(10000) // Wait for Phase 2
  }
}
