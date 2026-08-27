/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.entity.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.validator.Trimmed;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.search.entity.beans.PipelineGitDetails.PipelineGitDetailsKeys;
import io.harness.search.entity.beans.PipelineRetryExecutionMetadata.PipelineRetryExecutionMetadataKeys;
import io.harness.search.entity.beans.PipelineTriggeredBy.PipelineTriggeredByKeys;
import io.harness.search.entity.beans.cd.CDPipelineSearchModuleInfo;
import io.harness.search.entity.beans.ci.CIPipelineSearchModuleInfo;
import io.harness.yaml.core.NGLabel;
import io.harness.yaml.core.NGLabel.NGLabelKeys;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(PIPELINE)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldNameConstants(innerTypeName = "PipelineSearchExecutionSummaryDTOKeys")
public class PipelineSearchExecutionSummaryDTO {
  @NotEmpty String uuid;
  @NotEmpty String accountId;
  @NotEmpty @Deprecated String orgIdentifier;
  @Trimmed @NotEmpty @Deprecated String projectIdentifier;
  String parentUniqueId;
  @NotEmpty String pipelineIdentifier;
  @NotEmpty String planExecutionId;
  @NotEmpty String name;
  String status;

  List<NGTag> tags;
  List<NGLabel> labels;

  Long startTs;
  Long endTs;

  PipelineGitDetails entityGitDetails;
  List<String> modules;
  CDPipelineSearchModuleInfo cdModuleInfo;
  CIPipelineSearchModuleInfo ciModuleInfo;
  String executionMode;
  String triggerType;
  PipelineTriggeredBy triggeredBy;

  Long createdAt;
  PipelineRetryExecutionMetadata retryExecutionMetadata;

  /*
   * Below JsonProperty is done because this was previously primitive due to which it was "deleted"
   * and now it is Boolean which saves as "isDeleted" instead, so for backward compatibility it's named as "deleted" now
   */
  @Setter @NonFinal @JsonProperty("deleted") Boolean isDeleted;
  @JsonProperty("childPipeline") Boolean isChildPipeline;
  @NotEmpty Integer runSequence;
  List<String> inputSetIdentifiers;

  String notes;
  Long pipelineTimeoutTs;

  @UtilityClass
  public static class PipelineSearchExecutionSummaryDTOKeys {
    public String isDeleted = "deleted";
    public String triggerIdentifier =
        PipelineSearchExecutionSummaryDTOKeys.triggeredBy + "." + PipelineTriggeredByKeys.triggerIdentifier;
    public String triggeredByEmail =
        PipelineSearchExecutionSummaryDTOKeys.triggeredBy + "." + PipelineTriggeredByKeys.email;
    public String triggeredByGitUser =
        PipelineSearchExecutionSummaryDTOKeys.triggeredBy + "." + PipelineTriggeredByKeys.gitUser;
    public String entityGitDetailsRepoName =
        PipelineSearchExecutionSummaryDTOKeys.entityGitDetails + "." + PipelineGitDetailsKeys.repoName;
    public String entityGitDetailsRepoIdentifier =
        PipelineSearchExecutionSummaryDTOKeys.entityGitDetails + "." + PipelineGitDetailsKeys.repoIdentifier;
    public String entityGitDetailsBranch =
        PipelineSearchExecutionSummaryDTOKeys.entityGitDetails + "." + PipelineGitDetailsKeys.branch;
    public String tagsKey = PipelineSearchExecutionSummaryDTOKeys.tags + "." + NGTagKeys.key;
    public String tagsValue = PipelineSearchExecutionSummaryDTOKeys.tags + "." + NGTagKeys.value;
    public String labelsKey = PipelineSearchExecutionSummaryDTOKeys.labels + "." + NGLabelKeys.key;
    public String labelsValue = PipelineSearchExecutionSummaryDTOKeys.labels + "." + NGLabelKeys.value;
    public String rootExecutionId = PipelineSearchExecutionSummaryDTOKeys.retryExecutionMetadata + "."
        + PipelineRetryExecutionMetadataKeys.rootExecutionId;
  }
}
