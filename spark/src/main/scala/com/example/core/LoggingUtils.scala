package com.example.core

import org.apache.log4j.{Level, Logger}

/**
 * Centralized logging utilities to reduce code duplication.
 * Provides consistent logging setup and common logging patterns.
 */
object LoggingUtils {
  
  /**
   * Sets up logging for a Spark job with consistent configuration.
   * @param className The class name for the logger
   * @param logLevel The log level to set (default: INFO)
   * @return Configured logger instance
   */
  def setupJobLogging(className: String, logLevel: Level = Level.INFO): Logger = {
    val rootLogger = Logger.getRootLogger
    rootLogger.setLevel(logLevel)
    val logger = Logger.getLogger(className)
    logger
  }
  
  /**
   * Logs a job start banner with consistent formatting.
   * @param logger The logger instance
   * @param jobName The name of the job
   */
  def logJobStart(logger: Logger, jobName: String): Unit = {
    logger.info("=" * 60)
    logger.info(s"$jobName STARTING")
    logger.info("=" * 60)
  }
  
  /**
   * Logs a job completion banner with consistent formatting.
   * @param logger The logger instance
   * @param jobName The name of the job
   */
  def logJobEnd(logger: Logger, jobName: String): Unit = {
    logger.info("=" * 60)
    logger.info(s"$jobName COMPLETED")
    logger.info("=" * 60)
  }
  
  /**
   * Logs Spark session information.
   * @param logger The logger instance
   * @param spark The Spark session
   */
  def logSparkInfo(logger: Logger, spark: org.apache.spark.sql.SparkSession): Unit = {
    logger.info(s"Spark session created with app ID: ${spark.sparkContext.applicationId}")
    logger.info(s"Spark version: ${spark.version}")
    logger.info(s"Spark master: ${spark.sparkContext.master}")
  }
  
  /**
   * Logs configuration parameters in a consistent format.
   * @param logger The logger instance
   * @param config Configuration object with toString method
   */
  def logConfig(logger: Logger, config: Any): Unit = {
    logger.info(s"Job configuration: $config")
  }
  
  /**
   * Logs streaming query information.
   * @param logger The logger instance
   * @param query The streaming query
   */
  def logStreamingQuery(logger: Logger, query: org.apache.spark.sql.streaming.StreamingQuery): Unit = {
    logger.info("Streaming query started successfully")
    logger.info(s"Query ID: ${query.id}")
    logger.info(s"Query name: ${query.name}")
  }
  
  /**
   * Logs streaming progress in a consistent format.
   * @param logger The logger instance
   * @param progress The streaming query progress
   */
  def logStreamingProgress(logger: Logger, progress: org.apache.spark.sql.streaming.StreamingQueryProgress): Unit = {
    if (progress != null) {
      logger.info(s"Processing progress: inputRowsPerSecond=${progress.inputRowsPerSecond}, " +
                 s"processedRowsPerSecond=${progress.processedRowsPerSecond}, " +
                 s"batchId=${progress.batchId}")
      
      if (progress.sources.nonEmpty) {
        progress.sources.foreach { source =>
          logger.info(s"Source progress: description=${source.description}, " +
                     s"inputRowsPerSecond=${source.inputRowsPerSecond}")
        }
      }
    }
  }
  
  /**
   * Logs error with consistent formatting and optional exception.
   * @param logger The logger instance
   * @param message Error message
   * @param exception Optional exception to log
   */
  def logError(logger: Logger, message: String, exception: Option[Throwable] = None): Unit = {
    exception match {
      case Some(e) => logger.error(message, e)
      case None => logger.error(message)
    }
  }
}
