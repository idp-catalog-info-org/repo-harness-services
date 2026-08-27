/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans;

import io.harness.abort.AbortedBy;
import io.harness.annotation.HarnessEntity;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.validator.Trimmed;
import io.harness.dto.FailureInfoDTO;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.execution.PriorityType;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;
import io.harness.persistence.UuidAware;
import io.harness.pms.contracts.execution.ExecutionErrorInfo;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.template.TemplateReferenceSummary;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.yaml.core.NGLabel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Setter;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PlanExecutionSummaryKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "planExecutionsSummary", noClassnameStored = true)
@Document("planExecutionsSummary")
@TypeAlias("planExecutionsSummary")
@HarnessEntity(exportable = true)
@ChangeDataCapture(table = "pipeline_execution_summary_ci", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntity")
@ChangeDataCapture(table = "pipeline_execution_summary_ci_committers", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityCICommitters")
@ChangeDataCapture(table = "pipeline_execution_summary", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityAllStages")
@ChangeDataCapture(table = "pipeline_execution_summary_cd", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityCD")
@ChangeDataCapture(table = "service_infra_info", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityServiceAndInfra")
@ChangeDataCapture(table = "stage_execution_summary_ci", dataStore = "pms-harness", fields = {},
    handler = "PipelineExecutionSummaryEntityCIStage")
@ChangeDataCapture(table = "execution_tags_info_ng", dataStore = "pms-harness", fields = {}, handler = "TagsInfoNGCD")
@ChangeDataCapture(table = "runtime_inputs_info", dataStore = "pms-harness", fields = {}, handler = "RuntimeInputsInfo")
@ChangeDataCapture(table = "stage_execution", dataStore = "pms-harness", fields = {}, handler = "ApprovalStage")
public class PipelineExecutionSummaryEntity implements PersistentEntity, UuidAware, UniqueIdAware {
  public static final long TTL_MONTHS = 6;

  @Setter @NonFinal @Id @dev.morphia.annotations.Id String uuid;

  @NotEmpty @Builder.Default Integer runSequence = 0;
  @NotEmpty String accountId;
  @NotEmpty @Deprecated String orgIdentifier;
  @Trimmed @NotEmpty @Deprecated String projectIdentifier;

  @NotEmpty String pipelineIdentifier;
  // Get on PlanExecutionId index
  @NotEmpty @FdUniqueIndex String planExecutionId;
  @NotEmpty String name;

  @Builder.Default Boolean pipelineDeleted = Boolean.FALSE;

  Status internalStatus;
  ExecutionStatus status;
  AbortedBy abortedBy;

  String resolvedUserInputSetYaml;
  String pipelineTemplate; // saving the template here because after an execution, the pipeline can be updated
  Boolean executionInputConfigured;

  @Singular @Size(max = 128) List<NGTag> tags;
  @Singular @Size(max = 128) List<NGLabel> labels;

  @Setter @NonFinal @Builder.Default Map<String, org.bson.Document> moduleInfo = new HashMap<>();
  @Setter @NonFinal @Builder.Default Map<String, GraphLayoutNodeDTO> layoutNodeMap = new HashMap<>();
  String firstRollbackStageGraphId;
  List<String> modules;
  Set<String> executedModules;
  String startingNodeId;
  List<String> startingNodeIds; // For DAG support - multiple root nodes can start simultaneously
  Boolean isDagEnabled; // True when DAG execution is enabled for this pipeline
  Map<String, List<String>> dependencyGraph; // Stage dependency graph (nodeId -> list of dependency nodeIds)

  ExecutionTriggerInfo executionTriggerInfo;
  @Deprecated ExecutionErrorInfo executionErrorInfo;
  @Deprecated ByteString gitSyncBranchContext;
  EntityGitDetails entityGitDetails;
  FailureInfoDTO failureInfo;

  // Stored as bytes in ProtoWrite/ReadConverter, thus do not create index directly
  GovernanceMetadata governanceMetadata;
  OpaOnSaveStatusDTO opaOnSaveStatus;
  StagesExecutionMetadata stagesExecutionMetadata;
  Boolean allowStagesExecution;

  // git simplification params
  StoreType storeType;
  String connectorRef;

  Long startTs;
  Long endTs;

  String pipelineVersion;

  Boolean notifyOnlyMe;

  ExecutionMode executionMode; // this is used to filter out rollback mode executions from executions list API
  RollbackExecutionInfo rollbackExecutionInfo;
  Boolean notesExistForPlanExecutionId;
  Boolean shouldUseSimplifiedLogBaseKey;
  Boolean isDynamicExecution;
  Boolean isOriginalYamlUsedOnRerun;
  Boolean cdcGraphEnabled;

  @Setter @NonFinal String uniqueId;
  @Setter @NonFinal String parentUniqueId;

  PriorityType priorityType;

  // List of input set identifiers used in this execution
  List<String> inputSetIdentifiers;

  // Branch name used to fetch input sets for this execution
  String inputSetBranchName;

  TemplateReferenceSummary templateReferenceSummary;
  String notes;

  // Absolute epoch-millis timestamp at which the pipeline-level timeout expires.
  // Stamped when the pipeline node starts and its TimeoutInstance is registered (post-queue),
  // so it reflects the real timeout anchor rather than the summary-creation time.
  // Null when the pipeline has no pipeline-level timeout configured.
  Long pipelineTimeoutTs;

  // TTL index
  @Setter
  @NonFinal
  @FdTtlIndex
  @Builder.Default
  Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  // TODO: removing these getters after 6 months (13/10/21)
  public Boolean isLatestExecution() {
    if (isLatestExecution == null) {
      return true;
    }
    return isLatestExecution;
  }

  public RetryExecutionMetadata getRetryExecutionMetadata() {
    if (retryExecutionMetadata == null) {
      return RetryExecutionMetadata.builder()
          .parentExecutionId(planExecutionId)
          .rootExecutionId(planExecutionId)
          .build();
    }
    return retryExecutionMetadata;
  }

  RetryExecutionMetadata retryExecutionMetadata;
  PipelineStageInfo parentStageInfo;
  @Deprecated Boolean isLatestExecution;
  // Required Index for PipelineTelemetryPublisher
  @Setter @NonFinal @SchemaIgnore @FdIndex @CreatedDate @Builder.Default Long createdAt = 0L;
  @Setter @NonFinal @SchemaIgnore @NotNull @LastModifiedDate @Builder.Default Long lastUpdatedAt = 0L;
  @Setter @NonFinal @Version Long version;

  public ExecutionStatus getStatus() {
    if (internalStatus == null) {
      // For backwards compatibility when internalStatus was not there
      return status;
    }
    return internalStatus == Status.NO_OP ? ExecutionStatus.NOTSTARTED
                                          : ExecutionStatus.getExecutionStatus(internalStatus);
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList
        .<MongoIndex>builder()
        // Required from PmsExecutionSummaryRepository
        .add(CompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_pipelineId")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.parentUniqueId)
                 .field(PlanExecutionSummaryKeys.pipelineIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_createdAt_idx")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.parentUniqueId)
                 .field(PlanExecutionSummaryKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_createdAt_modules_idx")
                 .field(PlanExecutionSummaryKeys.modules)
                 .field(PlanExecutionSummaryKeys.parentUniqueId)
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.createdAt)
                 .build())
        // fetchPipelineSummaryEntityFromRootParentId in repoCustomImpl
        .add(SortCompoundMongoIndex.builder()
                 .name("rootExecution_createdAt_id")
                 .field(PlanExecutionSummaryKeys.rootExecutionId)
                 .descSortField(PlanExecutionSummaryKeys.createdAt)
                 .build())
        // Sort queries are added for list page
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_startTs_repo_branch_pipelineIds_status_modules_parent_info_range_idx")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.parentUniqueId)
                 .descSortField(PlanExecutionSummaryKeys.startTs)
                 .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsRepoName)
                 .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsBranch)
                 .ascRangeField(PlanExecutionSummaryKeys.pipelineIdentifier)
                 .ascRangeField(PlanExecutionSummaryKeys.status)
                 .ascRangeField(PlanExecutionSummaryKeys.modules)
                 .ascRangeField(PlanExecutionSummaryKeys.isChildPipeline)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_name_startTs_repo_branch_pipelineIds_status_modules_parent_info_"
                     + "range_idx")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.parentUniqueId)
                 .descSortField(PlanExecutionSummaryKeys.name)
                 .ascRangeField(PlanExecutionSummaryKeys.startTs)
                 .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsRepoName)
                 .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsBranch)
                 .ascRangeField(PlanExecutionSummaryKeys.pipelineIdentifier)
                 .ascRangeField(PlanExecutionSummaryKeys.status)
                 .ascRangeField(PlanExecutionSummaryKeys.modules)
                 .ascRangeField(PlanExecutionSummaryKeys.isChildPipeline)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_status_startTs_repo_branch_pipelineIds_modules_parent_info_range_idx")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.parentUniqueId)
                 .descSortField(PlanExecutionSummaryKeys.status)
                 .ascRangeField(PlanExecutionSummaryKeys.startTs)
                 .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsRepoName)
                 .ascRangeField(PlanExecutionSummaryKeys.entityGitDetailsBranch)
                 .ascRangeField(PlanExecutionSummaryKeys.pipelineIdentifier)
                 .ascRangeField(PlanExecutionSummaryKeys.modules)
                 .ascRangeField(PlanExecutionSummaryKeys.isChildPipeline)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_createdAt")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .ascSortField(PlanExecutionSummaryKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_isLatestExecution_startTs_executionMode_isChildPipeline")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.parentUniqueId)
                 .field(PlanExecutionSummaryKeys.isLatestExecution)
                 .descSortField(PlanExecutionSummaryKeys.startTs)
                 .rangeField(PlanExecutionSummaryKeys.executionMode)
                 .rangeField(PlanExecutionSummaryKeys.isChildPipeline)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_parentUniqueId_startTs_planExecutionId_status_pipelineIdentifier")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.parentUniqueId)
                 .descSortField(PlanExecutionSummaryKeys.startTs)
                 .descSortField(PlanExecutionSummaryKeys.planExecutionId)
                 .rangeField(PlanExecutionSummaryKeys.status)
                 .rangeField(PlanExecutionSummaryKeys.pipelineIdentifier)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("endTs_idx")
                 .field(PlanExecutionSummaryKeys.endTs)
                 .ascSortField(PlanExecutionSummaryKeys.endTs)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("internalStatus_startTs_idx")
                 .field(PlanExecutionSummaryKeys.internalStatus)
                 .field(PlanExecutionSummaryKeys.startTs)
                 .ascSortField(PlanExecutionSummaryKeys.startTs)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_endTs_idx")
                 .field(PlanExecutionSummaryKeys.accountId)
                 .field(PlanExecutionSummaryKeys.endTs)
                 .ascSortField(PlanExecutionSummaryKeys.endTs)
                 .build())
        .build();
  }

  @UtilityClass
  public static class PlanExecutionSummaryKeys {
    public String triggerType = PlanExecutionSummaryKeys.executionTriggerInfo + "."
        + "triggerType";
    public String triggeredByEmail = PlanExecutionSummaryKeys.executionTriggerInfo + "."
        + "triggeredBy.extraInfo.email";
    public String triggeredByGitUser = PlanExecutionSummaryKeys.executionTriggerInfo + "."
        + "triggeredBy.extraInfo.gitUser";

    public String triggerIdentifier = PlanExecutionSummaryKeys.executionTriggerInfo + "."
        + "triggeredBy"
        + "."
        + "triggerIdentifier";
    public String rootExecutionId = PlanExecutionSummaryKeys.retryExecutionMetadata + "."
        + "rootExecutionId";
    public String parentExecutionId = PlanExecutionSummaryKeys.retryExecutionMetadata + "."
        + "parentExecutionId";
    public String entityGitDetailsRepoName = PlanExecutionSummaryKeys.entityGitDetails + "."
        + "repoName";
    public String entityGitDetailsRepoIdentifier = PlanExecutionSummaryKeys.entityGitDetails + "."
        + "repoIdentifier";
    public String entityGitDetailsBranch = PlanExecutionSummaryKeys.entityGitDetails + "."
        + "branch";
    public String tagsKey = PlanExecutionSummaryKeys.tags + "."
        + "key";
    public String tagsValue = PlanExecutionSummaryKeys.tags + "."
        + "value";
    public String labelsKey = PlanExecutionSummaryKeys.labels + "."
        + "key";
    public String labelsValue = PlanExecutionSummaryKeys.labels + "."
        + "value";
    public String isChildPipeline = PlanExecutionSummaryKeys.parentStageInfo + "."
        + "hasParentPipeline";
    public String rollbackModeExecutionId = PlanExecutionSummaryKeys.rollbackExecutionInfo + "."
        + "rollbackModeExecutionId";
  }

  public boolean isStagesExecutionAllowed() {
    return allowStagesExecution != null && allowStagesExecution;
  }

  public String getRollbackModeExecutionId() {
    return rollbackExecutionInfo != null ? rollbackExecutionInfo.getRollbackModeExecutionId() : null;
  }

  public String getPipelineVersion() {
    if (null == pipelineVersion || pipelineVersion.equals("0")) {
      return HarnessYamlVersion.V0;
    }
    return pipelineVersion;
  }
}
