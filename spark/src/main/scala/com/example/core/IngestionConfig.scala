package com.example.core

import org.apache.log4j.Logger

/**
 * Configuration management for ingestion jobs.
 * Centralizes all configuration parsing and validation.
 */
object IngestionConfig {
  private val logger = Logger.getLogger(getClass.getName)

  case class Phase1Config(
    topic: String,
    rawPath: String,
    checkpoint: String,
    startingOffsets: String = "earliest",
    kafkaBootstrapServers: String = "kafka:9092"
  )

  case class Phase2Config(
    dataset: String,
    rawPath: String,
    lakePath: String,
    checkpoint: String,
    mode: String = "append",
    metastoreUrl: String = "http://metastore:8000"
  )

  case class MetastoreDataset(
    id: Int,
    name: String,
    kafkaTopic: String,
    mode: String,
    pkFields: List[String],
    partitionKeys: List[String],
    transformJars: List[String]
  )

  def parsePhase1Args(args: Array[String]): Phase1Config = {
    logger.info("Parsing Phase-1 arguments...")
    
    val parser = new scopt.OptionParser[Phase1Config]("phase1") {
      head("Phase-1 Ingestion Job", "1.0")
      
      opt[String]("topic")
        .required()
        .action((x, c) => c.copy(topic = x))
        .text("Kafka topic to consume from")
        
      opt[String]("raw-path")
        .required()
        .action((x, c) => c.copy(rawPath = x))
        .text("S3 path for raw JSON output")
        
      opt[String]("checkpoint")
        .required()
        .action((x, c) => c.copy(checkpoint = x))
        .text("S3 path for checkpoint location")
        
      opt[String]("starting-offsets")
        .optional()
        .action((x, c) => c.copy(startingOffsets = x))
        .text("Kafka starting offsets (earliest/latest)")
        
      opt[String]("kafka-servers")
        .optional()
        .action((x, c) => c.copy(kafkaBootstrapServers = x))
        .text("Kafka bootstrap servers")
    }

    parser.parse(args, Phase1Config("", "", "")) match {
      case Some(config) =>
        logger.info(s"Phase-1 config: $config")
        config
      case None =>
        logger.error("Failed to parse Phase-1 arguments")
        sys.exit(1)
    }
  }

  def parsePhase2Args(args: Array[String]): Phase2Config = {
    logger.info("Parsing Phase-2 arguments...")
    
    val parser = new scopt.OptionParser[Phase2Config]("phase2") {
      head("Phase-2 Ingestion Job", "1.0")
      
      opt[String]("dataset")
        .required()
        .action((x, c) => c.copy(dataset = x))
        .text("Dataset name")
        
      opt[String]("raw-path")
        .required()
        .action((x, c) => c.copy(rawPath = x))
        .text("S3 path for raw JSON input")
        
      opt[String]("lake-path")
        .required()
        .action((x, c) => c.copy(lakePath = x))
        .text("S3 path for lakehouse output")
        
      opt[String]("checkpoint")
        .required()
        .action((x, c) => c.copy(checkpoint = x))
        .text("S3 path for checkpoint location")
        
      opt[String]("mode")
        .optional()
        .action((x, c) => c.copy(mode = x))
        .text("Write mode (append/upsert)")
        
      opt[String]("metastore-url")
        .optional()
        .action((x, c) => c.copy(metastoreUrl = x))
        .text("Metastore service URL")
    }

    parser.parse(args, Phase2Config("", "", "", "")) match {
      case Some(config) =>
        logger.info(s"Phase-2 config: $config")
        config
      case None =>
        logger.error("Failed to parse Phase-2 arguments")
        sys.exit(1)
    }
  }
}
