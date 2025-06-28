"""Airflow DAG for Phase-1: Kafka -> Raw S3 dump.
   Reads dataset configuration from Metastore and triggers a Spark
   Structured-Streaming job via SparkSubmitOperator.
"""
from datetime import datetime
from airflow import DAG
from airflow.providers.apache.spark.operators.spark_submit import SparkSubmitOperator
from airflow.models import Variable
from airflow.utils.log.logging_mixin import LoggingMixin
import requests
import logging

# Set up logger
logger = logging.getLogger(__name__)

# Get configuration from Airflow variables
DATASET = Variable.get("dataset_name", default_var="orders")
METASTORE = Variable.get("metastore_url", default_var="http://metastore:8000")

logger.info(f"Phase-1 DAG initializing for dataset: {DATASET}")
logger.info(f"Using metastore at: {METASTORE}")

# Fetch dataset configuration from Java Metastore
logger.info("Fetching dataset configuration from Java Metastore service...")
try:
    response = requests.get(f"{METASTORE}/datasets/{DATASET}")
    if response.status_code == 200:
        dataset_config = response.json()
        logger.info(f"Dataset config retrieved from Java service: {dataset_config['name']}")
    else:
        logger.error(f"Failed to fetch dataset config from Java service: {response.status_code}")
        # Fallback configuration
        dataset_config = {
            "name": DATASET,
            "kafkaTopic": "orders",  # Note: Java service uses camelCase
            "mode": "append"
        }
except Exception as e:
    logger.error(f"Error fetching dataset config from Java service: {str(e)}")
    # Fallback configuration
    dataset_config = {
        "name": DATASET,
        "kafkaTopic": "orders",  # Note: Java service uses camelCase
        "mode": "append"
    }

# Arguments for the Spark job
SPARK_ARGS = [
    f"--topic={dataset_config['kafkaTopic']}",
    f"--raw-path=s3a://raw/{dataset_config['kafkaTopic']}",
    f"--checkpoint=s3a://checkpoints/{dataset_config['name']}/phase1"
]



def create_dag():
    logger.info("Creating Phase-1 DAG for dataset: %s", DATASET)
    with DAG(
        dag_id=f"ingestion_phase1_{DATASET}",
        start_date=datetime(2025, 1, 1),
        schedule_interval="@once",
        catchup=False,
    ) as dag:
        # Log the Spark job configuration
        logger.info(f"Configuring Spark job with args: {SPARK_ARGS}")
        
        SparkSubmitOperator(
            task_id="spark_raw_dump",
            conn_id="spark_default",
            application="/opt/spark/jobs/ingestion.jar",
            java_class="com.example.Phase1Job",
            application_args=SPARK_ARGS,
            conf={
                "spark.master": "spark://spark-master:7077",
                "spark.driver.memory": "1g",
                "spark.executor.memory": "1g",
                "spark.executor.cores": "1",
                "spark.hadoop.fs.s3a.endpoint": "http://localstack:4566",
                "spark.hadoop.fs.s3a.access.key": "test",
                "spark.hadoop.fs.s3a.secret.key": "test",
                "spark.hadoop.fs.s3a.path.style.access": "true",
                "spark.hadoop.fs.s3a.impl": "org.apache.hadoop.fs.s3a.S3AFileSystem"
            },
            verbose=True
        )
    return dag

# Create the DAG instance
dag_instance = create_dag()
logger.info(f"Phase-1 DAG created with ID: ingestion_phase1_{DATASET}")

# Register the DAG
globals()[f"ingestion_phase1_{DATASET}"] = dag_instance
