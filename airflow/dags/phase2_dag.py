"""Airflow DAG for Phase-2: Raw S3 ➜ Lakehouse write.
Launches the transform+upsert Spark job and then pings the metrics endpoint.
"""
from datetime import datetime, timedelta
from airflow import DAG
from airflow.models import Variable
from airflow.providers.apache.spark.operators.spark_submit import SparkSubmitOperator
from airflow.operators.python import PythonOperator
from airflow.utils.log.logging_mixin import LoggingMixin
import requests, json, time
import logging

# Set up logger
logger = logging.getLogger(__name__)

# Get configuration from Airflow variables
DATASET = Variable.get("dataset_name", default_var="orders")
METASTORE = Variable.get("metastore_url", default_var="http://metastore:8000")
METRICS_PORT = Variable.get("metrics_port", default_var="8090")

logger.info(f"Phase-2 DAG initializing for dataset: {DATASET}")
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
    f"--dataset={DATASET}",
    f"--raw-path=s3a://raw/{dataset_config['kafkaTopic']}",
    f"--lake-path=s3a://lake/{dataset_config['name']}",
    f"--checkpoint=s3a://checkpoints/{dataset_config['name']}/phase2",
    f"--mode={dataset_config['mode'].lower()}"
]



def push_metrics():
    logger.info(f"Starting metrics collection for {DATASET}...")
    # In standalone mode, query the Spark REST API for application metrics
    try:
        # Get list of applications
        spark_api_url = "http://spark-master:8080/api/v1/applications"
        logger.info(f"Querying Spark API: {spark_api_url}")
        apps = requests.get(spark_api_url, timeout=5).json()
        logger.info(f"Found {len(apps)} Spark applications")
        
        # Find our application (most recent one with matching name)
        app_id = None
        for app in apps:
            if f"Phase2-{DATASET}" in app.get("name", ""):
                app_id = app.get("id")
                break
        
        if app_id:
            # Get application metrics
            app_url = f"{spark_api_url}/{app_id}"
            app_info = requests.get(app_url, timeout=5).json()
            
            # Get executor metrics
            executors_url = f"{app_url}/executors"
            executors = requests.get(executors_url, timeout=5).json()
            
            # Collect relevant metrics
            metrics = {
                "app_id": app_id,
                "name": app_info.get("name"),
                "status": app_info.get("status"),
                "duration": app_info.get("duration", 0),
                "executor_count": len(executors),
                "records_processed": sum(e.get("totalRecordsRead", 0) for e in executors),
                "timestamp": datetime.now().isoformat()
            }
            
            metrics_json = json.dumps(metrics, indent=2)
            logger.info(f"Collected metrics:\n{metrics_json}")
            print("Metrics: ", metrics_json)
            return metrics
        else:
            msg = f"No active Spark application found for {DATASET}"
            logger.warning(msg)
            print(msg)
            return {"status": "not_found", "dataset": DATASET}
    except Exception as exc:
        error_msg = f"Metrics unavailable: {exc}"
        logger.error(error_msg)
        print(error_msg)
        return {"status": "error", "message": str(exc)}

def create_dag():
    logger.info("Creating Phase-2 DAG for dataset: %s", DATASET)
    with DAG(
        dag_id=f"ingestion_phase2_{DATASET}",
        start_date=datetime(2025, 1, 1),
        schedule_interval=timedelta(minutes=5),
        catchup=False,
    ) as dag:
        # Log the Spark job configuration
        logger.info(f"Configuring Spark job with args: {SPARK_ARGS}")
        
        spark_ingest = SparkSubmitOperator(
            task_id="spark_ingest_lakehouse",
            conn_id="spark_default",
            application="/opt/spark/jobs/ingestion.jar",
            java_class="com.example.Phase2Job",
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

        logger.info("Configuring metrics collection task")
        metrics = PythonOperator(task_id="metrics_push", python_callable=push_metrics)

        logger.info("Setting up task dependencies")
        spark_ingest >> metrics
    return dag

# Create the DAG instance
dag_instance = create_dag()
logger.info(f"Phase-2 DAG created with ID: ingestion_phase2_{DATASET}")

# Register the DAG
globals()[f"ingestion_phase2_{DATASET}"] = dag_instance
