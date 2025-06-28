package com.example

import com.example.core.{IngestionConfig, MetastoreClient, SchemaEvolution, TransformerLoader, LoggingUtils, SparkUtils}
import com.example.transform.Transformer
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{Dataset, Row, SparkSession}

/**
 * Phase-2 Ingestion Job: Raw S3 → Lakehouse
 * 
 * This job reads raw JSON from S3, applies transformations, handles schema evolution,
 * and writes to the Lakehouse in either append or upsert mode.
 * 
 * Key features:
 * - Schema evolution with fail-fast on breaking changes
 * - Dynamic transformer loading from S3 JARs
 * - Support for both append and upsert modes (Hudi)
 * - Comprehensive error handling and monitoring
 * - Integration with Metastore for configuration management
 */
object Phase2Job {
  
  def main(cmdArgs: Array[String]): Unit = {
    // Set up logging
    val logger = LoggingUtils.setupJobLogging(getClass.getName)
    LoggingUtils.logJobStart(logger, "PHASE-2 JOB: RAW TO LAKEHOUSE")
    
    // Parse configuration
    val config = IngestionConfig.parsePhase2Args(cmdArgs)
    
    // Create Spark session with optimized settings
    logger.info("Creating Spark session with optimized settings...")
    val s3Config = SparkUtils.getS3ALocalStackConfig()
    val spark = SparkUtils.createOptimizedSparkSession(s"Phase2-${config.dataset}", s3Config)
    
    LoggingUtils.logSparkInfo(logger, spark)
    LoggingUtils.logConfig(logger, config)
    
    // Initialize Metastore client
    val metastoreClient = new MetastoreClient(config.metastoreUrl)
    
    try {
      // Fetch dataset configuration from Metastore
      logger.info("Fetching dataset configuration from Metastore...")
      val datasetConfig = metastoreClient.getDatasetConfig(config.dataset) match {
        case Some(cfg) => 
          logger.info(s"Dataset configuration retrieved: $cfg")
          cfg
        case None =>
          logger.error(s"Failed to retrieve configuration for dataset: ${config.dataset}")
          throw new RuntimeException(s"Dataset configuration not found: ${config.dataset}")
      }
      
      // Load transformers
      logger.info("Loading transformers...")
      val transformers = TransformerLoader.loadTransformers(datasetConfig.transformJars)
      
      if (!TransformerLoader.validateTransformers(transformers)) {
        throw new RuntimeException("Transformer validation failed")
      }
      
      // Set up raw JSON source stream
      logger.info("Setting up raw JSON source stream...")
      logger.info(s"Raw path: ${config.rawPath}")
      
      var dataStream = spark.readStream
        .option("multiline", "false")
        .json(config.rawPath)
      
      logger.info(s"Initial raw schema: ${dataStream.schema.treeString}")
      
      // Handle schema evolution and capture
      logger.info("Checking for schema evolution...")
      val currentSchema = dataStream.schema
      logger.info(s"Current inferred schema: ${currentSchema.treeString}")
      
      val activeSchemaOpt = metastoreClient.getActiveSchemaVersion(datasetConfig.id)
      
      activeSchemaOpt match {
        case Some(activeSchemaJson) =>
          logger.info("Found existing active schema, comparing for evolution...")
          SchemaEvolution.parseSchemaJson(activeSchemaJson) match {
            case scala.util.Success(activeSchema) =>
              val comparison = SchemaEvolution.compareSchemas(activeSchema, currentSchema)
              
              // Handle schema evolution and update if needed
              if (!SchemaEvolution.handleSchemaEvolution(comparison, datasetConfig.id, metastoreClient)) {
                throw new RuntimeException("Breaking schema change detected - job terminated")
              }
              
              // Update schema in metastore if there were non-breaking changes
              if (comparison.changeType == SchemaEvolution.NonBreaking) {
                val newSchemaJson = currentSchema.json
                logger.info("Updating schema in metastore due to non-breaking changes")
                metastoreClient.updateActiveSchema(datasetConfig.id, newSchemaJson, "NON_BREAKING")
              }
              
            case scala.util.Failure(e) =>
              logger.warn(s"Could not parse active schema JSON: ${e.getMessage}")
              logger.info("Treating as initial schema due to parsing error")
              val initialSchemaJson = currentSchema.json
              metastoreClient.updateActiveSchema(datasetConfig.id, initialSchemaJson, "INITIAL")
          }
        case None =>
          logger.info("No active schema found - capturing initial schema")
          val initialSchemaJson = currentSchema.json
          logger.info(s"Capturing initial schema: ${initialSchemaJson}")
          metastoreClient.updateActiveSchema(datasetConfig.id, initialSchemaJson, "INITIAL")
      }
      
      // Apply transformers
      logger.info(s"Applying ${transformers.size} transformers...")
      transformers.zipWithIndex.foreach { case (transformer, index) =>
        logger.info(s"Applying transformer ${index + 1}/${transformers.size}: ${transformer.getClass.getName}")
        try {
          dataStream = transformer.transform(dataStream)
          logger.info(s"Transformer ${index + 1} applied successfully")
        } catch {
          case e: Exception =>
            logger.error(s"Transformer ${index + 1} failed: ${e.getMessage}", e)
            throw new RuntimeException(s"Transformation failed at step ${index + 1}", e)
        }
      }
      
      logger.info(s"Final schema after transformations: ${dataStream.schema.treeString}")
      
      // Configure writer based on mode
      logger.info(s"Configuring writer for mode: ${config.mode}")
      val writer = config.mode.toLowerCase match {
        case "append" =>
          logger.info("Using append mode with Parquet format")
          dataStream.writeStream
            .format("parquet")
            .outputMode("append")
            
        case "upsert" =>
          logger.info("Using upsert mode with Hudi format")
          val primaryKey = datasetConfig.pkFields.headOption.getOrElse("id")
          logger.info(s"Primary key for upsert: $primaryKey")
          
          val hudiOptions = SparkUtils.getHudiUpsertOptions(config.dataset, primaryKey)
          val writer = dataStream.writeStream.format("hudi")
          hudiOptions.foreach { case (key, value) => writer.option(key, value) }
          writer.outputMode("append")
            
        case _ =>
          throw new RuntimeException(s"Unsupported write mode: ${config.mode}")
      }
      
      // Start the streaming query
      logger.info("Starting streaming query to Lakehouse...")
      logger.info(s"Output path: ${config.lakePath}")
      logger.info(s"Checkpoint location: ${config.checkpoint}")
      
      val query = writer
        .option("path", config.lakePath)
        .option("checkpointLocation", config.checkpoint)
        .trigger(SparkUtils.Triggers.NORMAL_PROCESSING)
        .start()
      
      LoggingUtils.logStreamingQuery(logger, query)
      
      // Set up graceful shutdown and monitoring
      SparkUtils.setupGracefulShutdown(query, spark, logger)
      SparkUtils.monitorStreamingQuery(query, logger, scala.concurrent.duration.Duration(60, "seconds"))
      
      // Wait for termination
      query.awaitTermination()
      
    } catch {
      case e: Exception =>
        logger.error(s"Phase-2 job failed with error: ${e.getMessage}", e)
        throw e
    } finally {
      LoggingUtils.logJobEnd(logger, "PHASE-2 JOB: EXECUTION")
      spark.stop()
    }
  }
}
