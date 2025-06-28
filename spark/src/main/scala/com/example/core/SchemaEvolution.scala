package com.example.core

import org.apache.spark.sql.types._
import org.apache.log4j.Logger
import scala.util.{Try, Success, Failure}

/**
 * Handles schema evolution logic according to the LLD specifications.
 * Determines if schema changes are breaking or non-breaking.
 */
object SchemaEvolution {
  private val logger = Logger.getLogger(getClass.getName)

  sealed trait SchemaChangeType
  case object NoChange extends SchemaChangeType
  case object NonBreaking extends SchemaChangeType
  case object Breaking extends SchemaChangeType

  case class SchemaComparison(
    changeType: SchemaChangeType,
    addedFields: List[String] = List.empty,
    removedFields: List[String] = List.empty,
    modifiedFields: List[String] = List.empty,
    message: String = ""
  )

  /**
   * Compares two schemas and determines the type of change.
   * Based on LLD schema evolution rules.
   */
  def compareSchemas(oldSchema: StructType, newSchema: StructType): SchemaComparison = {
    logger.info("Comparing schemas for evolution...")
    logger.info(s"Old schema: ${oldSchema.treeString}")
    logger.info(s"New schema: ${newSchema.treeString}")

    val oldFields = oldSchema.fields.map(f => f.name -> f).toMap
    val newFields = newSchema.fields.map(f => f.name -> f).toMap

    val addedFields = newFields.keySet -- oldFields.keySet
    val removedFields = oldFields.keySet -- newFields.keySet
    val commonFields = oldFields.keySet intersect newFields.keySet

    val modifiedFields = commonFields.filter { fieldName =>
      val oldField = oldFields(fieldName)
      val newField = newFields(fieldName)
      !isCompatibleFieldChange(oldField, newField)
    }

    val comparison = if (addedFields.isEmpty && removedFields.isEmpty && modifiedFields.isEmpty) {
      SchemaComparison(NoChange, message = "No schema changes detected")
    } else if (removedFields.nonEmpty || modifiedFields.nonEmpty) {
      SchemaComparison(
        Breaking,
        addedFields.toList,
        removedFields.toList,
        modifiedFields.toList,
        s"Breaking changes: removed fields [${removedFields.mkString(", ")}], " +
        s"modified fields [${modifiedFields.mkString(", ")}]"
      )
    } else {
      SchemaComparison(
        NonBreaking,
        addedFields.toList,
        message = s"Non-breaking changes: added fields [${addedFields.mkString(", ")}]"
      )
    }

    logger.info(s"Schema comparison result: ${comparison.message}")
    comparison
  }

  /**
   * Determines if a field change is compatible (non-breaking).
   */
  private def isCompatibleFieldChange(oldField: StructField, newField: StructField): Boolean = {
    // Same name is assumed (checked by caller)
    if (oldField.dataType != newField.dataType) {
      // Type changes are generally breaking
      isCompatibleTypeChange(oldField.dataType, newField.dataType)
    } else if (oldField.nullable && !newField.nullable) {
      // Making a nullable field non-nullable is breaking
      false
    } else {
      // Making a non-nullable field nullable is non-breaking
      // Same type with same or more permissive nullability is compatible
      true
    }
  }

  /**
   * Determines if a data type change is compatible.
   * Based on Spark's type promotion rules.
   */
  private def isCompatibleTypeChange(oldType: DataType, newType: DataType): Boolean = {
    (oldType, newType) match {
      // Numeric promotions (safe widening)
      case (IntegerType, LongType) => true
      case (FloatType, DoubleType) => true
      case (IntegerType, DoubleType) => true
      case (LongType, DoubleType) => true
      
      // String is generally compatible with most types for reading
      case (_, StringType) => true
      
      // Most other changes are breaking
      case _ => false
    }
  }

  /**
   * Handles schema evolution based on the comparison result.
   * Returns true if processing should continue, false if it should fail.
   */
  def handleSchemaEvolution(
    comparison: SchemaComparison,
    datasetId: Int,
    metastoreClient: MetastoreClient
  ): Boolean = {
    comparison.changeType match {
      case NoChange =>
        logger.info("No schema evolution required")
        true

      case NonBreaking =>
        logger.info(s"Non-breaking schema change detected: ${comparison.message}")
        // Report to metastore for tracking
        metastoreClient.reportSchemaChange(datasetId, "", "NON_BREAKING")
        true

      case Breaking =>
        logger.error(s"Breaking schema change detected: ${comparison.message}")
        logger.error("Job will fail fast as per LLD specification")
        // Report to metastore and fail
        metastoreClient.reportSchemaChange(datasetId, "", "BREAKING")
        false
    }
  }

  /**
   * Parses a schema JSON string into a StructType.
   */
  def parseSchemaJson(schemaJson: String): Try[StructType] = {
    Try {
      DataType.fromJson(schemaJson).asInstanceOf[StructType]
    }
  }
}
