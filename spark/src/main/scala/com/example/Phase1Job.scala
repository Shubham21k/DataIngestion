package com.example

import com.example.core.{IngestionConfig, LoggingUtils, SparkUtils}
import org.apache.spark.sql.SparkSession

/**
 * Phase-1 Ingestion Job: Kafka → Raw S3 JSON Dump
 * 
 * This job reads from Kafka topics and writes raw JSON files to S3.
 * It preserves all Kafka metadata for downstream processing and debugging.
 * 
 * Key features:
 * - Exactly-once processing with checkpointing
 * - Kafka metadata preservation (topic, partition, offset, timestamp, key)
 * - Configurable starting offsets for bootstrap vs incremental modes
 * - Comprehensive logging and error handling
 */
object Phase1Job {
  
  def main(cmdArgs: Array[String]): Unit = {
    // Set up logging
    val logger = LoggingUtils.setupJobLogging(getClass.getName)
    LoggingUtils.logJobStart(logger, "PHASE-1 JOB: KAFKA TO RAW S3 DUMP")
    
    // Parse configuration
    val config = IngestionConfig.parsePhase1Args(cmdArgs)
    
    // Create Spark session with optimized settings
    logger.info("Creating Spark session with optimized settings...")
    val s3Config = SparkUtils.getS3ALocalStackConfig()
    val spark = SparkUtils.createOptimizedSparkSession(s"Phase1-${config.topic}", s3Config)
    
    LoggingUtils.logSparkInfo(logger, spark)
    LoggingUtils.logConfig(logger, config)
    
    import spark.implicits._
    
    try {
      // Set up Kafka source stream with comprehensive configuration
      logger.info("Setting up Kafka source stream...")
      logger.info(s"Kafka servers: ${config.kafkaBootstrapServers}")
      logger.info(s"Topic: ${config.topic}")
      logger.info(s"Starting offsets: ${config.startingOffsets}")
      
      val kafkaOptions = SparkUtils.getKafkaSourceOptions(
        config.kafkaBootstrapServers, 
        config.topic, 
        config.startingOffsets
      )
      
      val kafkaStream = spark.readStream.format("kafka")
      kafkaOptions.foreach { case (key, value) => kafkaStream.option(key, value) }
      val stream = kafkaStream.load()
      
      logger.info("Kafka stream configured successfully")
      logger.info(s"Kafka stream schema: ${stream.schema.treeString}")
      
      // Transform and structure the data
      logger.info("Configuring data transformation...")
      val structuredStream = stream
        .selectExpr(
          "CAST(value AS STRING) AS json",
          "struct(topic, partition, offset, timestamp, CAST(key AS STRING) as key) AS _meta"
        )
      
      logger.info(s"Structured stream schema: ${structuredStream.schema.treeString}")
      
      // Configure the output stream
      logger.info("Configuring output stream...")
      logger.info(s"Output path: ${config.rawPath}")
      logger.info(s"Checkpoint location: ${config.checkpoint}")
      
      val query = structuredStream.writeStream
        .format("json")
        .option("path", config.rawPath)
        .option("checkpointLocation", config.checkpoint)
        .outputMode("append")
        .trigger(SparkUtils.Triggers.FAST_PROCESSING)
        .start()
      
      LoggingUtils.logStreamingQuery(logger, query)
      
      // Set up graceful shutdown and monitoring
      SparkUtils.setupGracefulShutdown(query, spark, logger)
      SparkUtils.monitorStreamingQuery(query, logger)
      
      // Wait for termination
      query.awaitTermination()
      
    } catch {
      case e: Exception =>
        logger.error(s"Phase-1 job failed with error: ${e.getMessage}", e)
        throw e
    } finally {
      LoggingUtils.logJobEnd(logger, "PHASE-1 JOB: EXECUTION")
      spark.stop()
    }
  }
}
