package com.example.core

import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.apache.log4j.Logger
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.util.{Try, Success, Failure}

/**
 * Client for interacting with the Metastore service.
 * Handles dataset configuration retrieval and schema management.
 */
class MetastoreClient(baseUrl: String) {
  private val logger = Logger.getLogger(getClass.getName)
  private implicit val formats: DefaultFormats.type = DefaultFormats

  def getDatasetConfig(datasetName: String): Option[IngestionConfig.MetastoreDataset] = {
    logger.info(s"Fetching dataset config for: $datasetName")
    
    Try {
      val client = HttpClients.createDefault()
      val request = new HttpGet(s"$baseUrl/datasets/$datasetName")
      
      val response = client.execute(request)
      val responseBody = EntityUtils.toString(response.getEntity)
      client.close()
      
      if (response.getStatusLine.getStatusCode == 200) {
        logger.info(s"Successfully fetched config for $datasetName")
        val json = parse(responseBody)
        Some(json.extract[IngestionConfig.MetastoreDataset])
      } else {
        logger.error(s"Failed to fetch config for $datasetName: ${response.getStatusLine}")
        None
      }
    } match {
      case Success(result) => result
      case Failure(exception) =>
        logger.error(s"Error fetching dataset config: ${exception.getMessage}")
        None
    }
  }

  def getActiveSchemaVersion(datasetId: Int): Option[String] = {
    logger.info(s"Fetching active schema version for dataset: $datasetId")
    
    Try {
      val client = HttpClients.createDefault()
      val request = new HttpGet(s"$baseUrl/datasets/$datasetId/schema/active")
      
      val response = client.execute(request)
      val responseBody = EntityUtils.toString(response.getEntity)
      client.close()
      
      if (response.getStatusLine.getStatusCode == 200) {
        val json = parse(responseBody)
        val schemaJson = (json \ "schema_json").extract[String]
        logger.info(s"Retrieved active schema for dataset $datasetId")
        Some(schemaJson)
      } else {
        logger.warn(s"No active schema found for dataset $datasetId")
        None
      }
    } match {
      case Success(result) => result
      case Failure(exception) =>
        logger.error(s"Error fetching schema version: ${exception.getMessage}")
        None
    }
  }

  def updateActiveSchema(datasetId: Int, schemaJson: String, changeType: String): Boolean = {
    logger.info(s"Updating active schema for dataset $datasetId: $changeType")
    
    Try {
      val client = HttpClients.createDefault()
      val request = new org.apache.http.client.methods.HttpPost(s"$baseUrl/datasets/$datasetId/schema/evolve")
      request.setHeader("Content-Type", "application/json")
      
      // Create JSON payload
      val jsonPayload = s"""{"schema_json": "${schemaJson.replace("\"", "\\\"")}", "change_type": "$changeType"}"""
      val entity = new org.apache.http.entity.StringEntity(jsonPayload)
      request.setEntity(entity)
      
      val response = client.execute(request)
      val statusCode = response.getStatusLine.getStatusCode
      client.close()
      
      statusCode == 200
    } match {
      case Success(result) => 
        logger.info(s"Schema updated successfully: $result")
        result
      case Failure(exception) =>
        logger.error(s"Failed to update schema: ${exception.getMessage}")
        false
    }
  }
  
  def reportSchemaChange(datasetId: Int, newSchema: String, changeType: String): Boolean = {
    // Delegate to updateActiveSchema for consistency
    updateActiveSchema(datasetId, newSchema, changeType)
  }
}
