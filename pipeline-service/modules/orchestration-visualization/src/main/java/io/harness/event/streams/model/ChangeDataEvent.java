/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.streams.model;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a CDC (Change Data Capture) event from MongoDB Kafka Connector.
 *
 * MongoDB change stream events have this format:
 * {
 *   "operationType": "insert" | "update" | "replace" | "delete",
 *   "fullDocument": { ... },       // full doc on insert/replace, null on update
 *   "documentKey": { "_id": "..." },
 *   "ns": { "db": "pms-harness", "coll": "nodeExecutions" },
 *   "updateDescription": {
 *     "updatedFields": { ... },
 *     "removedFields": [ ... ],
 *     "truncatedArrays": [ ... ]
 *   }
 * }
 *
 * With SimplifiedJson formatter, BSON types are converted to plain JSON
 * (no $date, $numberLong wrappers).
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeDataEvent implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Operation type: insert, update, replace, delete
   */
  @JsonProperty("operationType") private String operationType;

  /**
   * Full document after the change. Present on insert/replace, null on update (change_streams mode).
   */
  @JsonProperty("fullDocument") private Map<String, Object> fullDocument;

  /**
   * Document key containing the _id of the changed document.
   */
  @JsonProperty("documentKey") private Map<String, Object> documentKey;

  /**
   * Namespace: database and collection name.
   */
  @JsonProperty("ns") private Namespace ns;

  /**
   * Update description containing only the changed fields (for update operations).
   */
  @JsonProperty("updateDescription") private UpdateDescription updateDescription;

  /**
   * Cluster time of the change event.
   */
  @JsonProperty("clusterTime") private Object clusterTime;

  /**
   * Namespace information from MongoDB change stream.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Namespace implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("db") private String db;

    @JsonProperty("coll") private String coll;
  }

  /**
   * Update description from MongoDB change streams.
   * Contains only the fields that were modified in an update operation.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class UpdateDescription implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Map of field paths to their new values.
     * Field paths use dot notation for nested fields (e.g., "ambiance.planExecutionId").
     */
    @JsonProperty("updatedFields") private Map<String, Object> updatedFields;

    /**
     * List of field paths that were removed.
     */
    @JsonProperty("removedFields") private List<String> removedFields;

    /**
     * Information about truncated arrays.
     */
    @JsonProperty("truncatedArrays") private List<Map<String, Object>> truncatedArrays;
  }

  // Convenience methods

  public String getCollection() {
    return ns != null ? ns.getColl() : null;
  }

  public boolean isCreate() {
    return "insert".equals(operationType) || "replace".equals(operationType);
  }

  public boolean isUpdate() {
    return "update".equals(operationType);
  }

  public boolean isDelete() {
    return "delete".equals(operationType);
  }

  /**
   * Check if this update event has updatedFields.
   */
  public boolean hasUpdatedFields() {
    return isUpdate() && updateDescription != null && updateDescription.getUpdatedFields() != null
        && !updateDescription.getUpdatedFields().isEmpty();
  }

  /**
   * Get the updated fields map from updateDescription.
   * Returns null if not an update or no updatedFields available.
   */
  public Map<String, Object> getUpdatedFields() {
    if (updateDescription != null && updateDescription.getUpdatedFields() != null) {
      return updateDescription.getUpdatedFields();
    }
    return null;
  }

  /**
   * Get the document _id from documentKey.
   * With SimplifiedJson, _id is a plain string (no $oid wrapper).
   */
  public String getDocumentId() {
    if (documentKey == null) {
      return null;
    }
    Object id = documentKey.get("_id");
    if (id instanceof String) {
      return (String) id;
    }
    return id != null ? id.toString() : null;
  }

  /**
   * Extract planExecutionId from the fullDocument based on collection type.
   * Only works for CREATE events (fullDocument is null on UPDATE).
   * For UPDATE events, the consumer should use documentId to find the existing row.
   */
  @SuppressWarnings("unchecked")
  public String extractPlanExecutionId() {
    if (fullDocument == null) {
      return null;
    }

    String collection = getCollection();
    if (collection == null) {
      return null;
    }

    switch (collection) {
      case "nodeExecutions":
        Map<String, Object> ambiance = (Map<String, Object>) fullDocument.get("ambiance");
        if (ambiance != null) {
          return (String) ambiance.get("planExecutionId");
        }
        return null;
      case "nodeExecutionsInfo":
      case "outcomeInstances":
      case "graphUpdateInfo":
        return (String) fullDocument.get("planExecutionId");
      case "planExecutions":
        return (String) fullDocument.get("_id");
      default:
        return null;
    }
  }

  /**
   * Extract accountId from the fullDocument.
   * Only works for CREATE events.
   */
  @SuppressWarnings("unchecked")
  public String extractAccountId() {
    if (fullDocument == null) {
      return null;
    }

    if (fullDocument.containsKey("accountId")) {
      return (String) fullDocument.get("accountId");
    }
    if (fullDocument.containsKey("accountIdentifier")) {
      return (String) fullDocument.get("accountIdentifier");
    }
    if (fullDocument.containsKey("setupAbstractions")) {
      Map<String, String> setupAbstractions = (Map<String, String>) fullDocument.get("setupAbstractions");
      if (setupAbstractions != null) {
        return setupAbstractions.get("accountId");
      }
    }
    return null;
  }

  /**
   * Generate a unique event ID for deduplication/logging.
   */
  public String generateEventId() {
    return getCollection() + ":" + getDocumentId() + ":" + operationType;
  }
}
