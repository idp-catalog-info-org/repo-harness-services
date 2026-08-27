/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dataretention.entity.beans;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/*
 * This DTO is used to update the ExecutionRetentionMetadata entity in MongoDB
 * Which is done by the ExecutionRetentionSyncIterator, when it saves the metadata to DB
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionRetentionMetadataUpdateDTO {
  String planExecutionId;
  String accountId;
  Long endTs;
  String bucketName;
  List<RetentionFileData> retentionFileData;
  String parentUniqueId;
  String pipelineIdentifier;
  String orgIdentifier;
  String projectIdentifier;
}
