/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.execution.NodeExecution.NodeExecutionKeys;
import static io.harness.expression.common.ExpressionConstants.EXPR_END;
import static io.harness.expression.common.ExpressionConstants.EXPR_END_CEL;
import static io.harness.expression.common.ExpressionConstants.EXPR_START;
import static io.harness.expression.common.ExpressionConstants.EXPR_START_CEL;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.constants.OrchestrationConstants;
import io.harness.engine.expressions.functors.type.NodeExecutionEntityType;
import io.harness.engine.expressions.metadata.ExecutionSweepingOutputMetadata;
import io.harness.engine.expressions.metadata.OutcomeMetadata;
import io.harness.engine.pms.data.OutcomeException;
import io.harness.engine.pms.data.SweepingOutputException;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.HarnessJexlEngine;
import io.harness.expression.InputsFunctor;
import io.harness.expression.LateBindingMap;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.plancreator.strategy.StrategyConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.DependencyEntry;
import io.harness.pms.contracts.plan.StringArray;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.utils.FunctorUtils;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * NodeExecutionMap resolves expressions for a single node execution.
 *
 * Suppose the current node has identifier `node1` and we see an expression `node1.child1`:
 * 1. We first try to find a child with identifier `child1`
 * 2. Then we try to find a property of node1's step parameters with name `child1`
 * 3. Then we try to find an outcome in node1's scope with name `child1`
 * 4. Then we try to find an sweeping output in node1's scope with name `child1`
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Value
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Slf4j
public class NodeExecutionMap extends LateBindingMap {
  transient NodeExecutionsCache nodeExecutionsCache;
  transient PmsOutcomeService pmsOutcomeService;
  transient PmsSweepingOutputService pmsSweepingOutputService;
  transient NodeExecutionInfoService nodeExecutionInfoService;
  transient PlanExecutionMetadataService planExecutionMetadataService;
  transient PlanExecutionService planExecutionService;
  transient Ambiance ambiance;
  transient NodeExecution nodeExecution;
  transient Set<NodeExecutionEntityType> entityTypes;
  transient Map<String, Object> children;
  transient HarnessJexlEngine harnessJexlEngine;
  transient ExecutionSweepingOutputMetadata outputMetadata;
  transient OutcomeMetadata outcomeMetadata;
  transient ExecutionInputService executionInputService;
  transient boolean isCel;
  transient String EXPRESSION_PREFIX;
  transient String EXPRESSION_SUFFIX;

  public static final String RETRY_COUNT = "retryCount";
  public static final String NODE_EXECUTION_ID = "nodeExecutionId";

  public static final String EXECUTION_ID = "executionId";
  public static final String GET_PARENT_STEP_GROUP = "getParentStepGroup";
  private static final List<String> STRATEGY_KEYWORDS = List.of(StrategyConstants.CURRENT_GLOBAL_ITERATION,
      StrategyConstants.TOTAL_ITERATIONS, StrategyConstants.ITERATION, StrategyConstants.ITERATIONS,
      StrategyConstants.IDENTIFIER_POSTFIX, StrategyConstants.MATRIX, StrategyConstants.REPEAT);

  // Bidirectional mapping for backward compatibility between V0 and V1 field names.
  // Allows lookup in either direction: identifier <-> id
  private static final Map<String, String> BACKWARD_COMPATIBLE_FIELDS = Map.of(YAMLFieldNameConstants.IDENTIFIER,
      YAMLFieldNameConstants.ID, YAMLFieldNameConstants.ID, YAMLFieldNameConstants.IDENTIFIER);

  @Builder
  NodeExecutionMap(NodeExecutionsCache nodeExecutionsCache, PmsOutcomeService pmsOutcomeService,
      PmsSweepingOutputService pmsSweepingOutputService, Ambiance ambiance, NodeExecution nodeExecution,
      Set<NodeExecutionEntityType> entityTypes, Map<String, Object> children, HarnessJexlEngine harnessJexlEngine,
      NodeExecutionInfoService nodeExecutionInfoService, ExecutionSweepingOutputMetadata outputMetadata,
      OutcomeMetadata outcomeMetadata, PlanExecutionMetadataService planExecutionMetadataService,
      PlanExecutionService planExecutionService, ExecutionInputService executionInputService, boolean isCel) {
    this.nodeExecutionsCache = nodeExecutionsCache;
    this.pmsOutcomeService = pmsOutcomeService;
    this.pmsSweepingOutputService = pmsSweepingOutputService;
    this.ambiance = ambiance;
    this.planExecutionMetadataService = planExecutionMetadataService;
    this.nodeExecution = nodeExecution;
    this.entityTypes = entityTypes == null ? NodeExecutionEntityType.allEntities() : entityTypes;
    this.planExecutionService = planExecutionService;
    if (children == null) {
      this.children = Collections.emptyMap();
    } else {
      this.children = new LateBindingMap();
      this.children.putAll(children);
    }
    this.harnessJexlEngine = harnessJexlEngine;
    this.nodeExecutionInfoService = nodeExecutionInfoService;
    this.outcomeMetadata = outcomeMetadata;
    this.outputMetadata = outputMetadata;
    this.executionInputService = executionInputService;
    this.isCel = isCel;
    this.EXPRESSION_PREFIX = isCel ? EXPR_START_CEL : EXPR_START;
    this.EXPRESSION_SUFFIX = isCel ? EXPR_END_CEL : EXPR_END;
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String)) {
      return null;
    }
    return FunctorUtils.fetchFirst(
        asList(this::fetchCurrentStatus, this::fetchExecutionUrl, this::fetchCurrentStatusIncludingChildOfStrategy,
            this::fetchDependencyStatus, this::fetchChild, this::fetchNodeExecutionField, this::fetchStepParameters,
            this::fetchOutcomeOrOutput, this::fetchStrategyData, this::fetchParentStepGroup, this::fetchInputs,
            this::fetchExecutionInputs),
        (String) key);
  }

  private Optional<Object> fetchChild(String key) {
    return children.containsKey(key) ? Optional.of(children.get(key)) : Optional.empty();
  }

  public Optional<Object> fetchParentStepGroup(String key) {
    if (!key.equals(GET_PARENT_STEP_GROUP)) {
      return Optional.empty();
    }
    if (!(nodeExecution.getGroup().equals(AmbianceUtils.STEP_GROUP)
            || nodeExecution.getGroup().equals(AmbianceUtils.STEP_GROUP_V1))) {
      return Optional.empty();
    }
    // nodeExecution will be of nearest stepGroup, as our expression starts with stepGroup
    Level parentStepGroupLevel =
        AmbianceUtils.getParentStepGroupLevel(nodeExecution.getUuid(), ambiance.getLevelsList());
    if (null == parentStepGroupLevel) {
      return Optional.empty();
    }
    String parentStepGroupNodeExecutionId = parentStepGroupLevel.getRuntimeId();

    NodeExecution parentNodeExecution = nodeExecutionsCache.fetch(parentStepGroupNodeExecutionId);

    return parentNodeExecution == null ? Optional.empty()
                                       : Optional.of(NodeExecutionValue.builder()
                                                         .nodeExecutionsCache(nodeExecutionsCache)
                                                         .pmsOutcomeService(pmsOutcomeService)
                                                         .pmsSweepingOutputService(pmsSweepingOutputService)
                                                         .nodeExecutionInfoService(nodeExecutionInfoService)
                                                         .ambiance(ambiance)
                                                         .startNodeExecution(parentNodeExecution)
                                                         .entityTypes(entityTypes)
                                                         .executionInputService(executionInputService)
                                                         .harnessJexlEngine(harnessJexlEngine)
                                                         .outcomeMetadata(outcomeMetadata)
                                                         .planExecutionMetadataService(planExecutionMetadataService)
                                                         .outputMetadata(outputMetadata)
                                                         .planExecutionService(planExecutionService)
                                                         .isCel(isCel)
                                                         .build()
                                                         .bind());
  }

  private Optional<Object> fetchExecutionInputs(String key) {
    if (!NGExpressionUtils.EXPRESSION_INPUT_CONSTANT.equals(key)) {
      return Optional.empty();
    }
    Map<String, Object> expressionValuesMap = new HashMap<>();
    List<ExecutionInputInstance> inputInstances =
        executionInputService.getExecutionInputInstances(Collections.singleton(nodeExecution.getUuid()));
    for (ExecutionInputInstance instance : inputInstances) {
      if (instance.getMergedInputTemplate() != null) {
        expressionValuesMap.putAll(instance.getMergedInputTemplate());
      }
    }
    return Optional.of(expressionValuesMap);
  }

  private Optional<Object> fetchInputs(String key) {
    if (!key.equals(YAMLFieldNameConstants.INPUTS)
        || !(ambiance.getMetadata().getHarnessVersion().equals(HarnessYamlVersion.V1))) {
      return Optional.empty();
    }
    Optional<PlanExecutionMetadata> planExecutionMetadataOptional = planExecutionMetadataService.findByPlanExecutionId(
        AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
    if (planExecutionMetadataOptional.isEmpty()) {
      return Optional.empty();
    }
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataOptional.get();
    PlanExecution planExecution = null;
    boolean readSwitchEnabled =
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
          ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.processedYaml));
      if (planExecutionOptional.isPresent()) {
        planExecution = planExecutionOptional.get();
      }
    }
    YamlNode pipelineNode = new YamlNode(YamlUtils.readAsJsonNode(
        PlanExecutionMigrationHelper.readProcessedYamlWithFallBackOnMetadata(planExecutionMetadata, planExecution)));
    // This is the inputsYamlNode from pipeline yaml. It contains all metadata of the inputs.
    YamlNode inputsYamlNode =
        pipelineNode.gotoPath(YAMLFieldNameConstants.PIPELINE + "/" + YAMLFieldNameConstants.INPUTS);
    if (inputsYamlNode == null) {
      return Optional.empty();
    }
    JsonNode inputSetJsonNode = null;
    if (!EmptyPredicate.isEmpty(planExecutionMetadata.getInputSetYaml())) {
      inputSetJsonNode = YamlUtils.readAsJsonNode(planExecutionMetadata.getInputSetYaml());
    }
    Map<String, Object> inputsMap = InputsFunctor.getMergedInputsMap(inputsYamlNode, inputSetJsonNode);
    for (Map.Entry<String, Object> entry : inputsMap.entrySet()) {
      if (inputsYamlNode.getField(entry.getKey()).getNode().getField(YAMLFieldNameConstants.TYPE) == null) {
        continue;
      }
      // The connector/secret/expression post-processing below only applies to String-valued inputs. Non-string
      // inputs (boolean, integer/number, object, array) are surfaced as their native value. Without this guard the
      // (String) cast on such a value throws ClassCastException, which aborts resolution of the ENTIRE
      // pipeline.inputs map and makes every input (even the string ones) resolve to null.
      if (!(entry.getValue() instanceof String inputValue)) {
        continue;
      }
      if (EngineExpressionEvaluator.hasExpressions(inputValue)
          || EngineExpressionEvaluator.hasCelExpressions(inputValue)) {
        if (inputsYamlNode.getField(entry.getKey())
                .getNode()
                .getField(YAMLFieldNameConstants.TYPE)
                .getNode()
                .getCurrJsonNode()
                .asText()
                .equals(YAMLFieldNameConstants.CONNECTOR)) {
          entry.setValue("${{connectorInputs.get(\"" + inputValue + "\")}}");
        }
      }
      if (inputsYamlNode.getField(entry.getKey()).getNode().getField(YAMLFieldNameConstants.TYPE) != null
          && inputsYamlNode.getField(entry.getKey())
                 .getNode()
                 .getField(YAMLFieldNameConstants.TYPE)
                 .getNode()
                 .getCurrJsonNode()
                 .asText()
                 .equals(YAMLFieldNameConstants.SECRET)) {
        entry.setValue(NGVariablesUtils.fetchSecretExpression(inputValue));
      }
    }
    return Optional.of(inputsMap);
  }

  // This function calculates final status of the node TILL now.
  private Optional<Object> fetchCurrentStatus(String key) {
    if (!key.equals(OrchestrationConstants.CURRENT_STATUS)) {
      return Optional.empty();
    }
    if (nodeExecution == null) {
      return Optional.empty();
    }
    Optional<Object> cachedStatus = fetchCachedCurrentStatus();
    if (cachedStatus.isPresent()) {
      return cachedStatus;
    }
    List<Status> childStatuses =
        nodeExecutionsCache.findAllTerminalChildrenStatusOnly(nodeExecution.getUuid(), false, true);
    return Optional.of(StatusUtils.calculateStatus(childStatuses, ambiance.getPlanExecutionId()).name());
  }

  // This function calculates executionUrl of the node TILL now.
  Optional<Object> fetchExecutionUrl(String key) {
    if (!key.equals(OrchestrationConstants.EXECUTION_URL)) {
      return Optional.empty();
    }
    // if Pipeline Node then skip as it would be resolved via PipelineExecutionFunctor
    if (nodeExecution == null || OrchestrationUtils.isPipelineNode(nodeExecution.getStepType())) {
      return Optional.empty();
    }

    /*
     * Following cases exists -
     * 1. Step execution url
     * a) Inside Normal stage (inside a matrix step/step-group is same as normal)
     * b) Inside a matrix stage
     * c) Inside a child pipeline stage -> for this output child execution url, thus same as 1.a case
     *
     * 2. Stage Execution url
     * a) Normal stage
     * b) Matrix stage
     * c) a Pipeline Stage -> which is same as normal stage
     */

    String pipelineExecutionUrl =
        EXPRESSION_PREFIX + "pipeline." + OrchestrationConstants.EXECUTION_URL + EXPRESSION_SUFFIX;
    Ambiance nodeAmbiance = nodeExecutionsCache.getAmbiance(nodeExecution.getUuid());
    boolean currentLevelInsideStage = AmbianceUtils.isCurrentLevelInsideStage(nodeAmbiance);
    // If any other node expression is called, then return pipeline execution url.
    if (!currentLevelInsideStage) {
      return Optional.of(pipelineExecutionUrl);
    }

    String stageSetupId = AmbianceUtils.getStageSetupIdAmbiance(nodeAmbiance);
    String stageExecutionUrl = EXPRESSION_PREFIX + pipelineExecutionUrl + String.format("+'?stage=%s", stageSetupId);

    boolean escapeAmpersand = !isCel
        && AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.PIPE_DISABLE_ESCAPE_AMPERSAND_IN_STAGE_EXEC_URL.name());

    // Check for stage if under matrix
    boolean currentStrategyLevelAtStage = AmbianceUtils.isCurrentNodeUnderStageStrategy(nodeAmbiance);
    if (currentStrategyLevelAtStage) {
      String stageRuntimeId = nodeAmbiance.getStageExecutionId();
      if (escapeAmpersand) {
        stageExecutionUrl += String.format("\\&stageExecId=%s", stageRuntimeId);
      } else {
        stageExecutionUrl += String.format("&stageExecId=%s", stageRuntimeId);
      }
    }

    boolean currentLevelAtStep = AmbianceUtils.isCurrentLevelAtStep(nodeAmbiance);
    if (currentLevelAtStep) {
      String stepId = AmbianceUtils.obtainCurrentRuntimeId(nodeAmbiance);
      String stepUrl;
      if (escapeAmpersand) {
        stepUrl = stageExecutionUrl + String.format("\\&step=%s'", stepId) + EXPRESSION_SUFFIX;
      } else {
        stepUrl = stageExecutionUrl + String.format("&step=%s'", stepId) + EXPRESSION_SUFFIX;
      }
      return Optional.of(stepUrl);
    }

    stageExecutionUrl += "'" + EXPRESSION_SUFFIX;

    return Optional.of(stageExecutionUrl);
  }

  // This function calculates final status of the node TILL now.
  private Optional<Object> fetchCurrentStatusIncludingChildOfStrategy(String key) {
    if (!key.equals(OrchestrationConstants.LIVE_STATUS)) {
      return Optional.empty();
    }
    if (nodeExecution == null) {
      return Optional.empty();
    }
    List<Status> childStatuses =
        nodeExecutionsCache.findAllTerminalChildrenStatusOnly(nodeExecution.getUuid(), true, false);
    return Optional.of(StatusUtils.calculateStatus(childStatuses, ambiance.getPlanExecutionId()).name());
  }

  /**
   * Statuses that count as a satisfied (succeeded) dependency for DAG when-condition evaluation.
   * Intentionally excludes SKIPPED (means an upstream dep failed and that failure must still
   * propagate through the chain) and SUSPENDED (paused, not finished).
   */
  private static final EnumSet<Status> DAG_DEPENDENCY_SATISFIED_STATUSES =
      EnumSet.of(Status.SUCCEEDED, Status.IGNORE_FAILED);

  /**
   * Resolves <code>&lt;+&lt;scope&gt;.allDependantsSucceeded&gt;</code> and
   * <code>&lt;+&lt;scope&gt;.anyDependantFailed&gt;</code> against the current node's DAG
   * dependencies.
   *
   * <p>Works generically for any level — stage today, step / step-group once DAG lands there —
   * because it only relies on:
   * <ul>
   *   <li>{@code nodeExecution.getParentId()} to locate the parent container</li>
   *   <li>{@code nodeExecution.getNodeId()} to look up this node's entry in the parent's
   *       {@link io.harness.pms.contracts.plan.DependencyGraphProto}</li>
   *   <li>{@link NodeExecutionsCache#fetchChildren(String)} to read sibling statuses in one
   *       cached Mongo call</li>
   * </ul>
   *
   * <p>Sibling retries are excluded via the {@code oldRetry} flag so a retried dependency
   * reports the status of its current live attempt rather than a superseded failed one.
   *
   * <p>If the parent plan node carries no dependency graph (sequential pipelines, unknown
   * scope), the vacuous result is returned:
   * {@code allDependantsSucceeded=true}, {@code anyDependantFailed=false}.
   */
  private Optional<Object> fetchDependencyStatus(String key) {
    if (!OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED.equals(key)
        && !OrchestrationConstants.ANY_DEPENDANT_FAILED.equals(key)) {
      return Optional.empty();
    }
    if (nodeExecution == null) {
      return Optional.empty();
    }

    boolean isAllSucceeded = OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED.equals(key);
    try {
      Optional<DagDeps> deps = getDeps();
      if (!deps.isPresent()) {
        return Optional.of(isAllSucceeded);
      }
      return Optional.of(isAllSucceeded ? allDepsSucceeded(deps.get()) : anyDepFailed(deps.get()));
    } catch (Exception e) {
      log.error("Error resolving DAG dependency status '{}' for nodeExecution {} in planExecution {}", key,
          nodeExecution.getUuid(), ambiance.getPlanExecutionId(), e);
      return Optional.of(isAllSucceeded);
    }
  }

  /**
   * Collects the current-attempt status of every DAG dependency declared for this node.
   *
   * <p>Returns {@link Optional#empty()} when there is nothing to evaluate — no parent runtime,
   * parent not backed by a {@link PlanNode}, parent carries no {@link DependencyGraphProto},
   * or this node has no entry in the graph. Callers must treat an empty result as "no deps to
   * check" and fall back to a vacuous default.
   *
   * <p>Old retry attempts ({@code oldRetry=true}) are skipped so a retried dependency reports
   * its current live attempt's status rather than a superseded failed one.
   */
  private Optional<DagDeps> getDeps() {
    String parentRuntimeId = nodeExecution.getParentId();
    if (parentRuntimeId == null) {
      return Optional.empty();
    }
    NodeExecution parentExec = nodeExecutionsCache.fetch(parentRuntimeId);
    if (parentExec == null) {
      return Optional.empty();
    }
    Node parentNode = nodeExecutionsCache.fetchNode(parentExec.getNodeId());
    if (!(parentNode instanceof PlanNode)) {
      return Optional.empty();
    }
    PlanNode parentPlanNode = (PlanNode) parentNode;
    if (!parentPlanNode.hasDependencyGraph()) {
      return Optional.empty();
    }
    StringArray deps = null;
    for (DependencyEntry entry : parentPlanNode.getDependencyGraph().getEntriesList()) {
      if (entry.getNodeId().equals(nodeExecution.getNodeId())) {
        deps = entry.getDependencies();
        break;
      }
    }
    if (deps == null || deps.getValuesList().isEmpty()) {
      return Optional.empty();
    }
    Set<String> depPlanNodeUuids = new HashSet<>(deps.getValuesList());

    Map<String, Status> statusByDep = new HashMap<>();
    for (NodeExecution sibling : nodeExecutionsCache.fetchChildren(parentRuntimeId)) {
      if (Boolean.TRUE.equals(sibling.getOldRetry())) {
        continue;
      }
      if (depPlanNodeUuids.contains(sibling.getNodeId())) {
        statusByDep.put(sibling.getNodeId(), sibling.getStatus());
      }
    }
    return Optional.of(new DagDeps(depPlanNodeUuids, statusByDep));
  }

  /**
   * True iff every declared dependency has a live attempt in {@link #DAG_DEPENDENCY_SATISFIED_STATUSES}.
   * Missing siblings (dep declared but not yet executed) count as not-satisfied.
   */
  private static boolean allDepsSucceeded(DagDeps deps) {
    for (String depUuid : deps.getDeclaredPlanNodeUuids()) {
      Status st = deps.getStatusByDep().get(depUuid);
      if (st == null || !DAG_DEPENDENCY_SATISFIED_STATUSES.contains(st)) {
        return false;
      }
    }
    return true;
  }

  /**
   * True iff at least one observed dependency is in a terminal failure state. Missing siblings
   * can never count as "failed" here — they haven't finished yet.
   */
  private static boolean anyDepFailed(DagDeps deps) {
    for (Status st : deps.getStatusByDep().values()) {
      if (StatusUtils.brokeStatuses().contains(st)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Immutable holder for a node's declared DAG dependencies and the current-attempt statuses
   * observed so far. Produced by {@link #getDeps()} and consumed by the aggregation helpers.
   */
  @Value
  private static class DagDeps {
    Set<String> declaredPlanNodeUuids;
    Map<String, Status> statusByDep;
  }

  private Optional<Object> fetchCachedCurrentStatus() {
    if (!AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_CACHE_CURRENT_STATUS.name())) {
      return Optional.empty();
    }
    // TODO: Handle the insert nodes here
    Optional<Status> cachedStatus = nodeExecutionInfoService.getCurrentStatus(nodeExecution.getUuid());
    return cachedStatus.map(Enum::name);
  }

  @VisibleForTesting
  protected Optional<Object> fetchNodeExecutionField(String key) {
    if (nodeExecution == null || !entityTypes.contains(NodeExecutionEntityType.NODE_EXECUTION_FIELDS)) {
      return Optional.empty();
    }

    if (NodeExecutionKeys.status.equals(key)) {
      return nodeExecution.getStatus() == null ? Optional.empty() : Optional.of(nodeExecution.getStatus().name());
    } else if (NodeExecutionKeys.startTs.equals(key)) {
      return Optional.ofNullable(nodeExecution.getStartTs());
    } else if (NodeExecutionKeys.endTs.equals(key)) {
      return Optional.ofNullable(nodeExecution.getEndTs());
    } else if (RETRY_COUNT.equals(key)) {
      return Optional.ofNullable(nodeExecution.getRetryIds() != null ? nodeExecution.getRetryIds().size() : 0);
    } else if (NODE_EXECUTION_ID.equals(key)) {
      return Optional.ofNullable(nodeExecution.getUuid());
    } else {
      return Optional.empty();
    }
  }

  private Optional<Object> fetchStepParameters(String key) {
    if (nodeExecution == null || !entityTypes.contains(NodeExecutionEntityType.STEP_PARAMETERS)) {
      return Optional.empty();
    }
    if (!HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(ambiance))) {
      return ExpressionEvaluatorUtils.fetchField(
          harnessJexlEngine.getEngine(), extractFinalStepParameters(nodeExecution, nodeExecutionsCache), key);
    } else {
      return fetchStepParametersWithBackwardCompatibility(key, BACKWARD_COMPATIBLE_FIELDS.get(key));
    }
  }

  private Optional<Object> fetchStepParametersWithBackwardCompatibility(String key, String alternateKey) {
    Object resolvedParameters = extractFinalStepParameters(nodeExecution, nodeExecutionsCache);
    Optional<Object> res = ExpressionEvaluatorUtils.fetchField(harnessJexlEngine.getEngine(), resolvedParameters, key);
    if (res.isPresent()) {
      return res;
    }
    // Fallback to the alternate key for backward compatibility
    return alternateKey != null
        ? ExpressionEvaluatorUtils.fetchField(harnessJexlEngine.getEngine(), resolvedParameters, alternateKey)
        : Optional.empty();
  }

  private Optional<Object> fetchStrategyData(String key) {
    if (nodeExecution == null || !entityTypes.contains(NodeExecutionEntityType.STRATEGY)) {
      return Optional.empty();
    }

    if (STRATEGY_KEYWORDS.contains(key)) {
      return ExpressionEvaluatorUtils.fetchField(
          harnessJexlEngine.getEngine(), extractStrategyMetadata(nodeExecution), key);
    }
    return Optional.empty();
  }

  private Optional<Object> fetchOutcomeOrOutput(String key) {
    if (nodeExecution == null
        || (!entityTypes.contains(NodeExecutionEntityType.OUTCOME)
            && !entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT))) {
      return Optional.empty();
    }

    List<String> levelRuntimeIdx = nodeExecution.getLevelRuntimeIdx();
    if (levelRuntimeIdx == null) {
      return Optional.empty();
    }

    String planExecutionId = ambiance.getPlanExecutionId();
    if (AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK.name())) {
      planExecutionId = nodeExecution.getPlanExecutionId();
    }
    Optional<Object> value = fetchOutcome(planExecutionId, levelRuntimeIdx, key);
    if (!value.isPresent()) {
      value = fetchSweepingOutput(planExecutionId, levelRuntimeIdx, key);
    }
    return value;
  }

  private Optional<Object> fetchOutcome(String planExecutionId, List<String> levelRuntimeIdIdx, String key) {
    if (!entityTypes.contains(NodeExecutionEntityType.OUTCOME) || !outcomeMetadata.existsOutcomeName(key)) {
      return Optional.empty();
    }

    try {
      return jsonToObject(pmsOutcomeService.resolveUsingLevelRuntimeIdx(
          planExecutionId, levelRuntimeIdIdx, RefObjectUtils.getOutcomeRefObject(key)));
    } catch (OutcomeException ignored) {
      return Optional.empty();
    }
  }

  /**
   * <+manifests.abc>
   *
   * @param planExecutionId
   * @param levelRuntimeIdIdx
   * @param key
   * @return
   */
  private Optional<Object> fetchSweepingOutput(String planExecutionId, List<String> levelRuntimeIdIdx, String key) {
    if (!entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT)
        || !outputMetadata.getExistingOutputNames().contains(key)) {
      return Optional.empty();
    }

    try {
      return jsonToObject(pmsSweepingOutputService.resolveUsingLevelRuntimeIdx(
          planExecutionId, levelRuntimeIdIdx, RefObjectUtils.getSweepingOutputRefObject(key)));
    } catch (SweepingOutputException ignored) {
      return Optional.empty();
    }
  }

  private static Map<String, Object> extractFinalStepParameters(
      NodeExecution nodeExecution, NodeExecutionsCache nodeExecutionsCache) {
    if (nodeExecution.getResolvedStepParameters() != null) {
      Map<String, Object> stepParameters =
          (Map<String, Object>) NodeExecutionUtils.resolveObject(nodeExecution.getResolvedStepParameters());
      if (stepParameters != null) {
        return stepParameters;
      }
    }
    Node node = nodeExecutionsCache.fetchNode(nodeExecution.getNodeId());
    return (Map<String, Object>) NodeExecutionUtils.resolveObject(node.getStepParameters());
  }

  private Map<String, Object> extractStrategyMetadata(NodeExecution nodeExecution) {
    if (nodeExecution.getUuid() != null) {
      return nodeExecutionInfoService.fetchStrategyObjectMap(nodeExecution.getUuid());
    }
    return new HashMap<>();
  }

  private static Optional<Object> jsonToObject(String json) {
    return Optional.ofNullable(NodeExecutionUtils.extractAndProcessObject(json));
  }

  // This is required for CEL because CEL first calls the containsKey method and only if is true does it call get method
  // where we have our logic. That's why we are returning true here so that it can go to the get method.
  @Override
  public boolean containsKey(Object key) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      return true;
    } else {
      return super.containsKey(key);
    }
  }
}
