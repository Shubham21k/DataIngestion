package com.example.transform

import org.apache.spark.sql.{Dataset, Row}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

/**
 * Sample transformer that adds a timestamp column.
 * This demonstrates a simple column addition transformation.
 */
class TimestampEnricher extends Transformer {
  override def transform(ds: Dataset[Row]): Dataset[Row] = {
    ds.withColumn("processed_at", current_timestamp())
      .withColumn("processing_date", current_date())
  }
}

/**
 * Sample transformer that calculates a derived value.
 * This demonstrates how to perform calculations on existing columns.
 */
class AmountCalculator extends Transformer {
  override def transform(ds: Dataset[Row]): Dataset[Row] = {
    // Only apply if the amount column exists
    if (ds.columns.contains("amount")) {
      ds.withColumn("amount_with_tax", col("amount") * lit(1.1))
        .withColumn("amount_category", 
          when(col("amount") < 100, "small")
          .when(col("amount") < 500, "medium")
          .otherwise("large")
        )
    } else {
      // Return unchanged if amount column doesn't exist
      ds
    }
  }
}

/**
 * Sample transformer that performs data quality checks.
 * This demonstrates how to validate and clean data.
 */
class DataQualityChecker extends Transformer {
  override def transform(ds: Dataset[Row]): Dataset[Row] = {
    // Add data quality flags
    ds.withColumn("has_customer", col("customer").isNotNull)
      .withColumn("has_valid_amount", 
        when(col("amount").isNotNull && col("amount") > 0, true)
        .otherwise(false)
      )
      .withColumn("data_quality_score",
        when(col("has_customer") && col("has_valid_amount"), 100)
        .when(col("has_customer") || col("has_valid_amount"), 50)
        .otherwise(0)
      )
  }
}

/**
 * Base trait that all transformers must implement.
 * This is copied from the main project to ensure compatibility.
 */
trait Transformer extends Serializable {
  def transform(ds: Dataset[Row]): Dataset[Row]
}
