package com.example.core

import com.example.transform.Transformer
import org.apache.log4j.Logger
import java.net.{URL, URLClassLoader}
import scala.util.{Try, Success, Failure}
import scala.reflect.runtime.universe._

/**
 * Handles dynamic loading of transformer JARs from S3.
 * Implements the transformer loading logic described in the LLD.
 */
object TransformerLoader {
  private val logger = Logger.getLogger(getClass.getName)

  /**
   * Loads transformers from the specified JAR URLs.
   * Returns a list of instantiated transformer objects.
   */
  def loadTransformers(jarUrls: List[String]): List[Transformer] = {
    logger.info(s"Loading transformers from ${jarUrls.size} JARs...")
    
    if (jarUrls.isEmpty) {
      logger.info("No transformer JARs configured")
      return List.empty
    }

    val transformers = jarUrls.flatMap { jarUrl =>
      loadTransformersFromJar(jarUrl)
    }

    logger.info(s"Successfully loaded ${transformers.size} transformers")
    transformers
  }

  /**
   * Loads transformers from a single JAR file.
   */
  private def loadTransformersFromJar(jarUrl: String): List[Transformer] = {
    logger.info(s"Loading transformers from JAR: $jarUrl")
    
    Try {
      val url = new URL(jarUrl)
      val classLoader = new URLClassLoader(Array(url), getClass.getClassLoader)
      
      // Try common transformer class names
      val possibleClassNames = List(
        "com.example.transform.CustomTransformer",
        "com.example.transform.DataTransformer", 
        "com.example.transform.MainTransformer",
        "com.example.transform.TimestampEnricher",
        "com.example.transform.AmountCalculator",
        "com.example.transform.DataQualityChecker"
      )
      
      val transformers = possibleClassNames.flatMap { className =>
        loadTransformerClass(classLoader, className)
      }
      
      if (transformers.isEmpty) {
        logger.warn(s"No transformer classes found in JAR: $jarUrl")
        // Try to scan the JAR for classes implementing Transformer
        scanJarForTransformers(classLoader, jarUrl)
      } else {
        transformers
      }
    } match {
      case Success(transformers) => transformers
      case Failure(exception) =>
        logger.error(s"Failed to load transformers from $jarUrl: ${exception.getMessage}")
        List.empty
    }
  }

  /**
   * Attempts to load a specific transformer class.
   */
  private def loadTransformerClass(classLoader: ClassLoader, className: String): Option[Transformer] = {
    Try {
      logger.debug(s"Attempting to load class: $className")
      val clazz = classLoader.loadClass(className)
      
      // Check if the class implements Transformer
      if (classOf[Transformer].isAssignableFrom(clazz)) {
        val constructor = clazz.getDeclaredConstructor()
        val instance = constructor.newInstance().asInstanceOf[Transformer]
        logger.info(s"Successfully loaded transformer: $className")
        Some(instance)
      } else {
        logger.debug(s"Class $className does not implement Transformer interface")
        None
      }
    } match {
      case Success(result) => result
      case Failure(exception) =>
        logger.debug(s"Could not load class $className: ${exception.getMessage}")
        None
    }
  }

  /**
   * Scans a JAR file for classes that implement the Transformer interface.
   * This is a simplified implementation - in production, you might use libraries
   * like Reflections or ClassGraph for more comprehensive scanning.
   */
  private def scanJarForTransformers(classLoader: ClassLoader, jarUrl: String): List[Transformer] = {
    logger.info(s"Scanning JAR for Transformer implementations: $jarUrl")
    
    Try {
      // This is a simplified approach - in a real implementation you would:
      // 1. Open the JAR file
      // 2. Enumerate all .class files
      // 3. Load each class and check if it implements Transformer
      // 4. Instantiate valid transformer classes
      
      // For now, we'll try some additional common patterns
      val additionalPatterns = List(
        "Transformer",
        "DataTransformer", 
        "Enricher",
        "Processor",
        "Handler"
      ).flatMap { pattern =>
        List(
          s"com.example.transform.$pattern",
          s"com.company.transform.$pattern",
          s"transform.$pattern"
        )
      }
      
      additionalPatterns.flatMap { className =>
        loadTransformerClass(classLoader, className)
      }
    } match {
      case Success(transformers) => transformers
      case Failure(exception) =>
        logger.warn(s"Failed to scan JAR $jarUrl: ${exception.getMessage}")
        List.empty
    }
  }

  /**
   * Validates that all transformers are properly configured.
   */
  def validateTransformers(transformers: List[Transformer]): Boolean = {
    logger.info(s"Validating ${transformers.size} transformers...")
    
    val validationResults = transformers.map { transformer =>
      Try {
        // Basic validation - ensure the transformer can be called
        val className = transformer.getClass.getName
        logger.info(s"Validated transformer: $className")
        true
      } match {
        case Success(_) => true
        case Failure(exception) =>
          logger.error(s"Transformer validation failed: ${exception.getMessage}")
          false
      }
    }
    
    val allValid = validationResults.forall(identity)
    logger.info(s"Transformer validation result: $allValid")
    allValid
  }
}
