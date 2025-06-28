package com.example

import com.example.core.{IngestionConfig, SchemaEvolution}
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Row, SparkSession}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

/**
 * Unit tests for multi-source CDC scenarios
 * Tests RDS CDC, MongoDB CDC, Aerospike CDC, and ClickStream data processing
 */
class MultiSourceCDCTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  var spark: SparkSession = _

  override def beforeAll(): Unit = {
    spark = SparkSession.builder()
      .appName("MultiSourceCDCTest")
      .master("local[2]")
      .config("spark.sql.shuffle.partitions", "2")
      .getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
  }

  override def afterAll(): Unit = {
    if (spark != null) {
      spark.stop()
    }
  }

  test("RDS CDC - MySQL binlog format processing") {
    import spark.implicits._

    // Simulate MySQL CDC data with binlog metadata
    val rdsData = Seq(
      Row("INSERT", "users", 1L, """{"id": 1, "name": "John Doe", "email": "john@example.com", "created_at": "2025-06-28T10:00:00Z"}""", "mysql-bin.000001", 12345L, 1719565200000L),
      Row("UPDATE", "users", 1L, """{"id": 1, "name": "John Smith", "email": "john.smith@example.com", "updated_at": "2025-06-28T11:00:00Z"}""", "mysql-bin.000001", 12346L, 1719568800000L),
      Row("DELETE", "users", 1L, """{"id": 1}""", "mysql-bin.000001", 12347L, 1719572400000L)
    )

    val rdsSchema = StructType(Array(
      StructField("operation", StringType, nullable = false),
      StructField("table_name", StringType, nullable = false),
      StructField("primary_key", LongType, nullable = false),
      StructField("data", StringType, nullable = false),
      StructField("binlog_file", StringType, nullable = false),
      StructField("binlog_position", LongType, nullable = false),
      StructField("timestamp", LongType, nullable = false)
    ))

    val rdsDF = spark.createDataFrame(spark.sparkContext.parallelize(rdsData), rdsSchema)

    // Validate RDS CDC structure
    rdsDF.count() shouldBe 3
    rdsDF.filter($"operation" === "INSERT").count() shouldBe 1
    rdsDF.filter($"operation" === "UPDATE").count() shouldBe 1
    rdsDF.filter($"operation" === "DELETE").count() shouldBe 1

    // Test data extraction from JSON payload
    val extractedData = rdsDF.selectExpr(
      "operation",
      "table_name",
      "primary_key",
      "get_json_object(data, '$.id') as id",
      "get_json_object(data, '$.name') as name",
      "get_json_object(data, '$.email') as email",
      "binlog_file",
      "binlog_position",
      "timestamp"
    )

    extractedData.filter($"operation" === "INSERT").select("name").collect().head.getString(0) shouldBe "John Doe"
    extractedData.filter($"operation" === "UPDATE").select("name").collect().head.getString(0) shouldBe "John Smith"
  }

  test("MongoDB CDC - Change Stream format processing") {
    import spark.implicits._

    // Simulate MongoDB Change Stream data
    val mongoData = Seq(
      Row("insert", "orders", """{"_id": {"$oid": "507f1f77bcf86cd799439011"}, "customer_id": 123, "amount": 99.99, "status": "pending"}""", """{"_id": {"$oid": "507f1f77bcf86cd799439011"}}""", "2025-06-28T10:00:00Z", "orders.123"),
      Row("update", "orders", """{"$set": {"status": "completed", "completed_at": "2025-06-28T11:00:00Z"}}""", """{"_id": {"$oid": "507f1f77bcf86cd799439011"}}""", "2025-06-28T11:00:00Z", "orders.124"),
      Row("delete", "orders", null, """{"_id": {"$oid": "507f1f77bcf86cd799439011"}}""", "2025-06-28T12:00:00Z", "orders.125")
    )

    val mongoSchema = StructType(Array(
      StructField("operationType", StringType, nullable = false),
      StructField("ns_coll", StringType, nullable = false),
      StructField("fullDocument", StringType, nullable = true),
      StructField("documentKey", StringType, nullable = false),
      StructField("clusterTime", StringType, nullable = false),
      StructField("resumeToken", StringType, nullable = false)
    ))

    val mongoDF = spark.createDataFrame(spark.sparkContext.parallelize(mongoData), mongoSchema)

    // Validate MongoDB CDC structure
    mongoDF.count() shouldBe 3
    mongoDF.filter($"operationType" === "insert").count() shouldBe 1
    mongoDF.filter($"operationType" === "update").count() shouldBe 1
    mongoDF.filter($"operationType" === "delete").count() shouldBe 1

    // Test MongoDB-specific data extraction
    val extractedMongo = mongoDF.selectExpr(
      "operationType",
      "ns_coll",
      "get_json_object(fullDocument, '$.customer_id') as customer_id",
      "get_json_object(fullDocument, '$.amount') as amount",
      "get_json_object(fullDocument, '$.status') as status",
      "get_json_object(documentKey, '$._id.$oid') as document_id",
      "clusterTime",
      "resumeToken"
    )

    val insertRow = extractedMongo.filter($"operationType" === "insert").collect().head
    insertRow.getString(2) shouldBe "123" // customer_id
    insertRow.getString(3) shouldBe "99.99" // amount
  }

  test("Aerospike CDC - Record-level change processing") {
    import spark.implicits._

    // Simulate Aerospike CDC data
    val aerospikeData = Seq(
      Row("WRITE", "test", "users", "user:1", """{"name": "Alice", "age": 30, "city": "NYC"}""", 1719565200000L, 1L),
      Row("WRITE", "test", "users", "user:1", """{"name": "Alice", "age": 31, "city": "NYC", "updated": true}""", 1719568800000L, 2L),
      Row("DELETE", "test", "users", "user:1", null, 1719572400000L, 3L)
    )

    val aerospikeSchema = StructType(Array(
      StructField("operation", StringType, nullable = false),
      StructField("namespace", StringType, nullable = false),
      StructField("set_name", StringType, nullable = false),
      StructField("user_key", StringType, nullable = false),
      StructField("bins", StringType, nullable = true),
      StructField("timestamp", LongType, nullable = false),
      StructField("generation", LongType, nullable = false)
    ))

    val aerospikeDF = spark.createDataFrame(spark.sparkContext.parallelize(aerospikeData), aerospikeSchema)

    // Validate Aerospike CDC structure
    aerospikeDF.count() shouldBe 3
    aerospikeDF.filter($"operation" === "WRITE").count() shouldBe 2
    aerospikeDF.filter($"operation" === "DELETE").count() shouldBe 1

    // Test Aerospike-specific processing
    val processedAerospike = aerospikeDF.selectExpr(
      "operation",
      "namespace",
      "set_name",
      "user_key",
      "get_json_object(bins, '$.name') as name",
      "get_json_object(bins, '$.age') as age",
      "get_json_object(bins, '$.city') as city",
      "timestamp",
      "generation"
    )

    val writeRows = processedAerospike.filter($"operation" === "WRITE").collect()
    writeRows.length shouldBe 2
    writeRows(0).getString(4) shouldBe "Alice" // name
    writeRows(1).getString(5) shouldBe "31" // updated age
  }

  test("ClickStream - Event stream processing") {
    import spark.implicits._

    // Simulate ClickStream data
    val clickStreamData = Seq(
      Row("page_view", "user123", "/home", "2025-06-28T10:00:00Z", """{"referrer": "google.com", "user_agent": "Chrome/91.0", "session_id": "sess_abc123"}""", "192.168.1.100"),
      Row("click", "user123", "/product/123", "2025-06-28T10:01:00Z", """{"element": "buy_button", "product_id": "123", "session_id": "sess_abc123"}""", "192.168.1.100"),
      Row("purchase", "user123", "/checkout", "2025-06-28T10:05:00Z", """{"order_id": "ord_456", "amount": 99.99, "payment_method": "credit_card", "session_id": "sess_abc123"}""", "192.168.1.100")
    )

    val clickStreamSchema = StructType(Array(
      StructField("event_type", StringType, nullable = false),
      StructField("user_id", StringType, nullable = false),
      StructField("page_url", StringType, nullable = false),
      StructField("timestamp", StringType, nullable = false),
      StructField("properties", StringType, nullable = false),
      StructField("ip_address", StringType, nullable = false)
    ))

    val clickStreamDF = spark.createDataFrame(spark.sparkContext.parallelize(clickStreamData), clickStreamSchema)

    // Validate ClickStream structure
    clickStreamDF.count() shouldBe 3
    clickStreamDF.filter($"event_type" === "page_view").count() shouldBe 1
    clickStreamDF.filter($"event_type" === "click").count() shouldBe 1
    clickStreamDF.filter($"event_type" === "purchase").count() shouldBe 1

    // Test ClickStream event processing
    val processedClickStream = clickStreamDF.selectExpr(
      "event_type",
      "user_id",
      "page_url",
      "timestamp",
      "get_json_object(properties, '$.session_id') as session_id",
      "get_json_object(properties, '$.referrer') as referrer",
      "get_json_object(properties, '$.order_id') as order_id",
      "get_json_object(properties, '$.amount') as amount",
      "ip_address"
    )

    val purchaseEvent = processedClickStream.filter($"event_type" === "purchase").collect().head
    purchaseEvent.getString(6) shouldBe "ord_456" // order_id
    purchaseEvent.getString(7) shouldBe "99.99" // amount
  }

  test("Schema Evolution - Multi-source compatibility") {
    // Test schema evolution across different CDC sources
    val oldRdsSchema = StructType(Array(
      StructField("id", LongType, nullable = false),
      StructField("name", StringType, nullable = false),
      StructField("email", StringType, nullable = false)
    ))

    val newRdsSchema = StructType(Array(
      StructField("id", LongType, nullable = false),
      StructField("name", StringType, nullable = false),
      StructField("email", StringType, nullable = false),
      StructField("phone", StringType, nullable = true), // New nullable field
      StructField("created_at", TimestampType, nullable = true) // New nullable field
    ))

    val schemaComparison = SchemaEvolution.compareSchemas(oldRdsSchema, newRdsSchema)
    schemaComparison.changeType shouldBe SchemaEvolution.NonBreaking
    schemaComparison.addedFields should contain("phone")
    schemaComparison.addedFields should contain("created_at")

    // Test breaking change
    val breakingSchema = StructType(Array(
      StructField("id", LongType, nullable = false),
      StructField("name", StringType, nullable = false)
      // email field removed - breaking change
    ))

    val breakingComparison = SchemaEvolution.compareSchemas(oldRdsSchema, breakingSchema)
    breakingComparison.changeType shouldBe SchemaEvolution.Breaking
    breakingComparison.removedFields should contain("email")
  }

  test("Configuration parsing for multi-source datasets") {
    // Test configuration for different source types
    val rdsConfig = IngestionConfig.MetastoreDataset(
      id = 1,
      name = "rds_users",
      kafkaTopic = "mysql.inventory.users",
      mode = "upsert",
      pkFields = List("id"),
      partitionKeys = List("created_date"),
      transformJars = List("s3://lake/jars/rds-transformers.jar")
    )

    val mongoConfig = IngestionConfig.MetastoreDataset(
      id = 2,
      name = "mongo_orders",
      kafkaTopic = "mongodb.ecommerce.orders",
      mode = "upsert",
      pkFields = List("_id"),
      partitionKeys = List("order_date"),
      transformJars = List("s3://lake/jars/mongo-transformers.jar")
    )

    val clickStreamConfig = IngestionConfig.MetastoreDataset(
      id = 3,
      name = "clickstream_events",
      kafkaTopic = "clickstream.web.events",
      mode = "append",
      pkFields = List(),
      partitionKeys = List("event_date", "event_hour"),
      transformJars = List("s3://lake/jars/clickstream-transformers.jar")
    )

    // Validate configurations
    rdsConfig.mode shouldBe "upsert"
    rdsConfig.pkFields should contain("id")
    
    mongoConfig.kafkaTopic shouldBe "mongodb.ecommerce.orders"
    mongoConfig.pkFields should contain("_id")
    
    clickStreamConfig.mode shouldBe "append"
    clickStreamConfig.pkFields shouldBe empty
    clickStreamConfig.partitionKeys should contain("event_date")
  }
}
