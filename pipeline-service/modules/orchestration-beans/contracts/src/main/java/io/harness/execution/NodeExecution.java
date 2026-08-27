/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.data.ResolverUtils;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.interrupts.InterruptEffect;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.logging.AutoLogContext;
import io.harness.logging.UnitProgress;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.timeout.TimeoutDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import dev.morphia.annotations.Entity;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.UtilityClass;
import lombok.experimental.Wither;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Value
@Builder
@FieldNameConstants(innerTypeName = "NodeExecutionKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "nodeExecutions", noClassnameStored = true)
@Document("nodeExecutions")
@TypeAlias("nodeExecution")
public class NodeExecution implements PersistentEntity, UuidAccess, PmsNodeExecution, PersistentRegularIterable {
  public static final long TTL_MONTHS = 3;

  // Immutable
  @Wither @Id @dev.morphia.annotations.Id String uuid;
  @NotNull
  @JsonIgnore
  @Deprecated
  // @JsonIgnore: Ambiance carries identity_execution_context.workloadToken; block REST serialization.
  Ambiance ambiance; // Please dont use this field anymore. We will remove it in future. Use methods from
                     // NodeExecutionContextUtils to get value for any field
  // @JsonIgnore: ExecutionContext carries IdentityExecutionContext.workloadToken (short-lived HMAC credential).
  // Morphia/Spring-Data Mongo codecs use their own serialization and are unaffected; only REST Jackson is blocked.
  @JsonIgnore ExecutionContext executionContext;
  @NotNull ExecutionMode mode;
  // Required for debugging, can be removed later
  @Wither @FdIndex @CreatedDate Long createdAt;
  private Long startTs;
  private Long endTs;
  private Duration initialWaitDuration;
  private Integer levelCount;
  // TTL index
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  // Resolved StepParameters stored just before invoking step.
  @Deprecated Map<String, Object> resolvedStepParameters;
  @Deprecated PmsStepParameters resolvedInputs;

  @Wither PmsStepParameters resolvedParams;

  // For Wait Notify
  String notifyId;

  // Relationships
  String parentId;
  String nextId;
  String previousId;
  Boolean processingEvent;
  Long processingEventStartedAt;

  // Mutable
  @Wither @LastModifiedDate Long lastUpdatedAt;
  Status status;
  @Wither @Version Long version;

  @Singular List<ExecutableResponse> executableResponses;
  @Singular List<InterruptEffect> interruptHistories;
  FailureInfo failureInfo;
  NodeRunInfo nodeRunInfo;

  @Builder.Default Boolean executionInputConfigured = false;
  // Retries
  @Singular List<String> retryIds;
  @Builder.Default Boolean oldRetry = false;

  // Timeout
  List<String> timeoutInstanceIds;
  TimeoutDetails timeoutDetails;

  // Todo: Move unitProgress and progressData to another collection
  @Singular @Deprecated List<UnitProgress> unitProgresses;
  // Ordering token guarding out-of-order unitProgresses updates; distinct from the optimistic-locking `version`.
  Long unitProgressesTimestamp;
  Map<String, Object> progressData;
  AdviserResponse adviserResponse;
  // Timeouts for advisers
  List<String> adviserTimeoutInstanceIds;
  TimeoutDetails adviserTimeoutDetails;

  // If this is a retry node then this field is populated
  String originalNodeExecutionId;

  SkipType skipGraphType;
  String module;
  String name;
  // Count of direct children for wrapper/container nodes (NG_FORK, STRATEGY_V1, GROUP). Used by UI.
  // Initialized to 0 to allow atomic $inc operations in MongoDB (which fails on null values).
  @Builder.Default Long childrenCount = 0L;
  StepType stepType;
  String nodeId;
  String identifier;
  String stageFqn;
  String group;
  Boolean skipExpressionChain;
  List<String> levelRuntimeIdx;
  String nodeType;
  List<String> excludedKeysFromStepInputs;
  @Builder.Default @Getter @NonFinal @Setter List<AdviserObtainment> adviserObtainments = new ArrayList<>();
  @Builder.Default Integer resolvedParamsVersion = 0;
  @Getter @NonFinal @Setter Long nextIteration;
  @Builder.Default Boolean advisorsProcessed = false;

  @Builder.Default @Getter @NonFinal @Setter List<String> nextIds = new ArrayList<>();
  @Builder.Default @Getter @NonFinal @Setter List<String> previousIds = new ArrayList<>();

  public Boolean getAdvisorsProcessed() {
    if (null == advisorsProcessed) {
      return true;
    }
    return advisorsProcessed;
  }

  public ExecutableResponse obtainLatestExecutableResponse() {
    if (isEmpty(executableResponses)) {
      return null;
    }
    return executableResponses.get(executableResponses.size() - 1);
  }

  public List<String> getLevelRuntimeIdx() {
    if (EmptyPredicate.isEmpty(levelRuntimeIdx)) {
      if (executionContext != null) {
        return NodeExecutionContextUtils.prepareLevelRuntimeIdIndices(executionContext);
      }
      if (ambiance != null) {
        return ResolverUtils.prepareLevelRuntimeIdIndices(ambiance);
      }
      return null;
    }
    return levelRuntimeIdx;
  }

  @Override
  public NodeType getNodeType() {
    if (null == executionContext && null == ambiance && null == nodeType) {
      return null;
    }
    if (EmptyPredicate.isEmpty(nodeType)) {
      return NodeType.valueOf(NodeExecutionContextUtils.obtainNodeType(this));
    }
    return NodeType.valueOf(nodeType);
  }

  public boolean getSkipExpressionChain() {
    if (skipExpressionChain == null) {
      Level level = NodeExecutionContextUtils.obtainCurrentLevel(this);
      if (level != null) {
        return level.getSkipExpressionChain();
      }
    }
    return Boolean.TRUE.equals(skipExpressionChain);
  }

  public String getPlanExecutionId() {
    return NodeExecutionContextUtils.getPlanExecutionId(this);
  }

  public String getPlanId() {
    return NodeExecutionContextUtils.getPlanId(this);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @UtilityClass
  public static class NodeExecutionKeys {
    public static final String id = "_id";
    public static final String planExecutionId = NodeExecutionKeys.ambiance + "."
        + "planExecutionId";

    public static final String stepCategory = NodeExecutionKeys.stepType + "."
        + "stepCategory";

    public static final String accountId = NodeExecutionKeys.ambiance + "."
        + "setupAbstractions"
        + "." + SetupAbstractionKeys.accountId;

    public static final String planId = NodeExecutionKeys.ambiance + "."
        + "planId";

    public static final String stageExecutionId = NodeExecutionKeys.ambiance + "."
        + "stageExecutionId";

    public static final String executionContextStageExecutionId = NodeExecutionKeys.executionContext + "."
        + "stageExecutionId";

    public static final String executionContextPlanId = NodeExecutionKeys.executionContext + "."
        + "planId";

    public static final String type = NodeExecutionKeys.stepType + "."
        + "type";
    public static final String mode = "mode";
    public static final String nodeRunInfo = "nodeRunInfo";
    public static final String advisorsProcessed = "advisorsProcessed";
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList
        .<MongoIndex>builder()
        // used by getByPlanNodeUuid
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_nodeId_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.nodeId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_identifier_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_oldRetry_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.oldRetry)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_status_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.status)
                 .build())
        // Used by findCountByParentIdAndStatusIn and fetchChildrenNodeExecutionsIterator
        .add(CompoundMongoIndex.builder()
                 .name("parentId_status_idx")
                 .field(NodeExecutionKeys.parentId)
                 .field(NodeExecutionKeys.status)
                 .field(NodeExecutionKeys.oldRetry)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_mode_status_oldRetry_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.mode)
                 .field(NodeExecutionKeys.status)
                 .field(NodeExecutionKeys.oldRetry)
                 .build())
        // Used by fetchAllStepNodeExecutions
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_stepCategory_identifier_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.stepCategory)
                 .field(NodeExecutionKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_stageFqn_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.stageFqn)
                 .build())
        // updateRelationShipsForRetryNode
        .add(CompoundMongoIndex.builder().name("previous_id_idx").field(NodeExecutionKeys.previousId).build())
        // fetchChildrenNodeExecutionsIterator
        .add(SortCompoundMongoIndex.builder()
                 .name("planExecutionId_parentId_createdAt_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.parentId)
                 .descRangeField(NodeExecutionKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder().name("status_idx").field(NodeExecutionKeys.status).build())
        .add(SortCompoundMongoIndex.builder()
                 .name("processingEvent_nextIteration_status")
                 .field(NodeExecutionKeys.processingEvent)
                 .rangeField(NodeExecutionKeys.nextIteration)
                 .rangeField(NodeExecutionKeys.status)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("planExecutionId_lastUpdatedAt_createdAt_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.lastUpdatedAt)
                 .ascSortField(NodeExecutionKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder().name("accountId_idx").field(NodeExecutionKeys.accountId).build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_createdAt_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.createdAt)
                 .build())
        .build();
  }

  public ByteString getResolvedStepParametersBytes() {
    if (this.getNodeType().equals(NodeType.IDENTITY_PLAN_NODE)) {
      IdentityStepParameters build =
          IdentityStepParameters.builder().originalNodeExecutionId(originalNodeExecutionId).build();
      return ByteString.copyFromUtf8(emptyIfNull(RecastOrchestrationUtils.toJson(build)));
    }
    String resolvedStepParams = RecastOrchestrationUtils.toJson(this.getResolvedStepParameters());
    return ByteString.copyFromUtf8(emptyIfNull(resolvedStepParams));
  }

  public String getResolvedStepParametersString() {
    if (this.getNodeType().equals(NodeType.IDENTITY_PLAN_NODE)) {
      IdentityStepParameters build =
          IdentityStepParameters.builder().originalNodeExecutionId(originalNodeExecutionId).build();
      return emptyIfNull(RecastOrchestrationUtils.toJson(build));
    }
    String resolvedStepParams = RecastOrchestrationUtils.toJson(this.getResolvedStepParameters());
    return emptyIfNull(resolvedStepParams);
  }

  public PmsStepParameters getPmsStepParameters() {
    return PmsStepParameters.parse(resolvedInputs);
  }

  public OrchestrationMap getPmsProgressData() {
    return OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(progressData);
  }

  public PmsStepParameters getResolvedStepParameters() {
    return resolvedParams;
  }

  public Level getCurrentLevel() {
    return NodeExecutionContextUtils.obtainCurrentLevel(this);
  }

  public List<Level> getLevels() {
    return NodeExecutionContextUtils.getLevelList(this);
  }

  public String getAccountId() {
    return NodeExecutionContextUtils.getAccountId(this);
  }

  public String getOrgIdentifier() {
    return NodeExecutionContextUtils.getOrgIdentifier(this);
  }

  public String getProjectIdentifier() {
    return NodeExecutionContextUtils.getProjectIdentifier(this);
  }

  public String getStageExecutionId() {
    return NodeExecutionContextUtils.getStageExecutionId(this);
  }

  public Map<String, String> getSetupAbstractionsMap() {
    return NodeExecutionContextUtils.getSetupAbstractionsMap(this);
  }

  public io.harness.pms.contracts.plan.ExecutionMode getExecutionMode() {
    return NodeExecutionContextUtils.getExecutionMode(this);
  }

  public String getPipelineIdentifier() {
    return NodeExecutionContextUtils.getPipelineIdentifier(this);
  }

  public AutoLogContext autoLogContext() {
    return new AutoLogContext(logContextMap(), OVERRIDE_NESTS);
  }

  private Map<String, String> logContextMap() {
    Map<String, String> logContext = new HashMap<>();
    logContext.put("nodeExecutionId", uuid);
    logContext.put("accountId", getAccountId());
    logContext.put("orgIdentifier", getOrgIdentifier());
    logContext.put("projectIdentifier", getProjectIdentifier());
    logContext.put("pipelineIdentifier", getPipelineIdentifier());
    logContext.put("planExecutionId", getPlanExecutionId());
    return logContext;
  }
}
