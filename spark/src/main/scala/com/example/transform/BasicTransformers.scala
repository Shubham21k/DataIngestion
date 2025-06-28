package com.example.transform

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, Row}
import org.apache.log4j.Logger

trait Transformer extends Serializable {
  // Logger for each transformer implementation
  protected val logger = Logger.getLogger(this.getClass.getName)
  def transform(ds: Dataset[Row]): Dataset[Row]
}

class CastTransformer(column: String, toType: String) extends Transformer {
  logger.info(s"Initializing CastTransformer for column=$column to type=$toType")
  override def transform(ds: Dataset[Row]): Dataset[Row] = {
    logger.info(s"Casting column '$column' to type '$toType'")
    val result = ds.withColumn(column, col(column).cast(toType))
    logger.info("Cast transformation completed")
    result
  }
}

class FlattenTransformer extends Transformer {
  logger.info("Initializing FlattenTransformer")
  override def transform(ds: Dataset[Row]): Dataset[Row] = {
    val flatCols = ds.schema.fields.flatMap { f =>
      f.dataType match {
        case struct: org.apache.spark.sql.types.StructType => struct.fields.map(sf => col(s"${f.name}.${sf.name}").alias(s"${f.name}_${sf.name}"))
        case _ => Seq(col(f.name))
      }
    }
    val result = ds.select(flatCols: _*)
    logger.info(s"Flattening complete. New column count: ${result.columns.length}")
    logger.info(s"Output schema after flattening: ${result.schema.treeString}")
    result
  }
}

class ArrayJsonToStructTypeTransformer(column: String) extends Transformer {
  logger.info(s"Initializing ArrayJsonToStructTypeTransformer for column=$column")
  override def transform(ds: Dataset[Row]): Dataset[Row] = {
    import ds.sparkSession.implicits._
    val sample = ds.select(column).as[String].collect().headOption.getOrElse("[]")
    val elementSchema = ds.sparkSession.read.json(ds.sparkSession.createDataset(Seq(sample))).schema
    logger.info(s"Inferred schema for JSON array: ${elementSchema.treeString}")
    val result = ds.withColumn(column, from_json(col(column), elementSchema))
    logger.info("JSON array conversion completed")
    result
  }
}
