package com.example.core

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.{StreamingQuery, Trigger}
import org.apache.log4j.Logger
import scala.concurrent.duration._

/**
 * Common Spark utilities to reduce code duplication across jobs.
 * Provides standardized Spark session creation and streaming configurations.
 */
object SparkUtils {
  
  /**
   * Creates a Spark session with optimized settings for streaming jobs.
   * @param appName The application name
   * @param additionalConfigs Additional Spark configurations
   * @return Configured SparkSession
   */
  def createOptimizedSparkSession(
    appName: String, 
    additionalConfigs: Map[String, String] = Map.empty
  ): SparkSession = {
    
    val baseConfigs = Map(
      "spark.sql.streaming.checkpointLocation.compression.codec" -> "lz4",
      "spark.sql.streaming.minBatchesToRetain" -> "10",
      "spark.serializer" -> "org.apache.spark.serializer.KryoSerializer",
      "spark.sql.adaptive.enabled" -> "true",
      "spark.sql.adaptive.coalescePartitions.enabled" -> "true",
      "spark.sql.streaming.stateStore.compression.codec" -> "lz4"
    )
    
    val allConfigs = baseConfigs ++ additionalConfigs
    
    val builder = SparkSession.builder().appName(appName)
    allConfigs.foreach { case (key, value) =>
      builder.config(key, value)
    }
    
    builder.getOrCreate()
  }
  
  /**
   * Sets up graceful shutdown hook for streaming queries.
   * @param query The streaming query to manage
   * @param spark The Spark session
   * @param logger Logger for shutdown messages
   */
  def setupGracefulShutdown(
    query: StreamingQuery, 
    spark: SparkSession, 
    logger: Logger
  ): Unit = {
    sys.addShutdownHook {
      logger.info("Shutdown hook triggered - stopping streaming query...")
      try {
        query.stop()
        spark.stop()
        logger.info("Graceful shutdown completed")
      } catch {
        case e: Exception =>
          logger.error(s"Error during shutdown: ${e.getMessage}", e)
      }
    }
  }
  
  /**
   * Monitors a streaming query with consistent logging.
   * @param query The streaming query to monitor
   * @param logger Logger for progress messages
   * @param progressInterval Interval between progress logs (default: 30 seconds)
   */
  def monitorStreamingQuery(
    query: StreamingQuery, 
    logger: Logger, 
    progressInterval: Duration = 30.seconds
  ): Unit = {
    logger.info("Starting query monitoring...")
    
    while (query.isActive) {
      val progress = query.lastProgress
      LoggingUtils.logStreamingProgress(logger, progress)
      Thread.sleep(progressInterval.toMillis)
    }
  }
  
  /**
   * Common Kafka source configuration.
   * @param kafkaServers Kafka bootstrap servers
   * @param topic Kafka topic to subscribe to
   * @param startingOffsets Starting offsets (earliest/latest)
   * @return Map of Kafka options
   */
  def getKafkaSourceOptions(
    kafkaServers: String,
    topic: String,
    startingOffsets: String = "earliest"
  ): Map[String, String] = {
    Map(
      "kafka.bootstrap.servers" -> kafkaServers,
      "subscribe" -> topic,
      "startingOffsets" -> startingOffsets,
      "failOnDataLoss" -> "false",
      "kafka.session.timeout.ms" -> "30000",
      "kafka.request.timeout.ms" -> "40000"
    )
  }
  
  /**
   * Common S3A configuration for LocalStack.
   * @return Map of S3A configurations
   */
  def getS3ALocalStackConfig(): Map[String, String] = {
    Map(
      "spark.hadoop.fs.s3a.endpoint" -> "http://localstack:4566",
      "spark.hadoop.fs.s3a.access.key" -> "test",
      "spark.hadoop.fs.s3a.secret.key" -> "test",
      "spark.hadoop.fs.s3a.path.style.access" -> "true",
      "spark.hadoop.fs.s3a.impl" -> "org.apache.hadoop.fs.s3a.S3AFileSystem"
    )
  }
  
  /**
   * Common Hudi write options for upsert mode.
   * @param tableName The Hudi table name
   * @param recordKey The record key field
   * @param precombineField The precombine field
   * @return Map of Hudi options
   */
  def getHudiUpsertOptions(
    tableName: String,
    recordKey: String,
    precombineField: String = "timestamp"
  ): Map[String, String] = {
    Map(
      "hoodie.datasource.write.operation" -> "upsert",
      "hoodie.table.name" -> tableName,
      "hoodie.datasource.write.recordkey.field" -> recordKey,
      "hoodie.datasource.write.precombine.field" -> precombineField,
      "hoodie.datasource.write.keygenerator.class" -> "org.apache.hudi.keygen.SimpleKeyGenerator",
      // Disable Hive/Glue sync for local development
      "hoodie.datasource.hive_sync.enable" -> "false",
      "hoodie.datasource.meta_sync.enable" -> "false"
    )
  }
  
  /**
   * Common trigger configurations for different use cases.
   */
  object Triggers {
    val FAST_PROCESSING = Trigger.ProcessingTime(30.seconds)
    val NORMAL_PROCESSING = Trigger.ProcessingTime(1.minute)
    val SLOW_PROCESSING = Trigger.ProcessingTime(5.minutes)
    val CONTINUOUS = Trigger.Continuous("1 second")
  }
}
