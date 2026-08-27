/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.entity.beans;

import static io.harness.dataretention.entity.beans.RetentionFileFormat.JSON;
import static io.harness.dataretention.entity.beans.RetentionFileFormat.JSON_ZSTD;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.DbAliases;

import lombok.Getter;

/*
 * This enum stores the collection, it's folder name and the file format to store objects in object store
 * Currently we have only 4 collections to be synced to object store, in future we can extend this to retain more
 * collections.
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
@Getter
public enum ExecutionRetentionObjectStoreCollection {
  EXECUTION_SUMMARY(
      "EXECUTION_SUMMARY", DbAliases.PMS, "pipeline_execution_summary", JSON, false, "planExecutionsSummary"),
  EXECUTION_METADATA(
      "EXECUTION_METADATA", DbAliases.PMS, "pipeline_execution_metadata", JSON, false, "planExecutionsMetadata"),
  EXECUTION_GRAPH("EXECUTION_GRAPH", DbAliases.PMS, "pipeline_execution_graph", JSON_ZSTD, false, "cacheEntities"),
  APPROVAL_INSTANCES(
      "APPROVAL_INSTANCES", DbAliases.PMS, "pipeline_execution_approval_instances", JSON, true, "approvalInstances"),
  EXECUTION_SUB_GRAPH(
      "EXECUTION_SUB_GRAPH", DbAliases.PMS, "pipeline_execution_graph", JSON_ZSTD, false, "cacheEntities");

  private final String name;
  private final String dbName;
  private final String folderName;
  private final RetentionFileFormat fileFormat;
  private final boolean useRecasterForConversion;
  private final String collectionName;

  ExecutionRetentionObjectStoreCollection(String name, String dbName, String folderName, RetentionFileFormat fileFormat,
      boolean useRecasterForConversion, String collectionName) {
    this.name = name;
    this.dbName = dbName;
    this.folderName = folderName;
    this.fileFormat = fileFormat;
    this.useRecasterForConversion = useRecasterForConversion;
    this.collectionName = collectionName;
  }

  public RetentionFileFormat getOriginalFileFormat() {
    if (fileFormat.getOriginalFileFormat() == null) {
      return fileFormat;
    }
    return fileFormat.getOriginalFileFormat();
  }

  public String getFileExtension() {
    return fileFormat.getFileExtension();
  }
}
