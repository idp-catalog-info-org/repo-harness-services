/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.execution;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.data.ResolverUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionContextUtils {
  public static String getAccountId(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null || executionContext.getSetupAbstractionsMap().isEmpty()) {
      return AmbianceUtils.getAccountId(nodeExecution.getAmbiance());
    } else {
      return executionContext.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId);
    }
  }

  public static String getOrgIdentifier(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null || executionContext.getSetupAbstractionsMap().isEmpty()) {
      return AmbianceUtils.getOrgIdentifier(nodeExecution.getAmbiance());
    } else {
      return executionContext.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier);
    }
  }

  public static String getProjectIdentifier(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null || executionContext.getSetupAbstractionsMap().isEmpty()) {
      return AmbianceUtils.getProjectIdentifier(nodeExecution.getAmbiance());
    } else {
      return executionContext.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier);
    }
  }

  public static Level obtainCurrentLevel(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null || isEmpty(executionContext.getLevelsList())) {
      return AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance());
    }
    return executionContext.getLevelsList().get(executionContext.getLevelsList().size() - 1);
  }

  public static StepType getCurrentStepType(NodeExecution nodeExecution) {
    if (nodeExecution.getExecutionContext() == null) {
      return AmbianceUtils.getCurrentStepType(nodeExecution.getAmbiance());
    }
    Level level = obtainCurrentLevel(nodeExecution);
    return level == null ? null : level.getStepType();
  }

  public static String obtainStepIdentifier(NodeExecution nodeExecution) {
    Level level = obtainCurrentLevel(nodeExecution);
    return level == null || isEmpty(level.getIdentifier()) ? null : level.getIdentifier();
  }

  public Optional<Level> getStrategyLevelFromExecutionContext(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return AmbianceUtils.getStrategyLevelFromAmbiance(nodeExecution.getAmbiance());
    }
    Optional<Level> stageLevel = Optional.empty();
    for (Level level : executionContext.getLevelsList()) {
      if (level.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        stageLevel = Optional.of(level);
      }
    }
    return stageLevel;
  }

  public Integer getRunSequence(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return nodeExecution.getAmbiance().getMetadata().getRunSequence();
    }
    return executionContext.getRunSequence();
  }

  public String getStageExecutionId(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return nodeExecution.getAmbiance().getStageExecutionId();
    }
    return executionContext.getStageExecutionId();
  }

  public ExecutionMode getExecutionMode(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return nodeExecution.getAmbiance().getMetadata().getExecutionMode();
    }
    return executionContext.getPipelineExecutionMode();
  }

  public static String obtainCurrentSetupId(NodeExecution nodeExecution) {
    Level level = obtainCurrentLevel(nodeExecution);
    return level == null || isEmpty(level.getSetupId()) ? null : level.getSetupId();
  }

  public static String obtainCurrentRuntimeId(NodeExecution nodeExecution) {
    Level level = obtainCurrentLevel(nodeExecution);
    return level == null || isEmpty(level.getRuntimeId()) ? null : level.getRuntimeId();
  }

  public boolean isNotificationConfigured(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return nodeExecution.getAmbiance().getMetadata().getIsNotificationConfigured();
    }
    return executionContext.getIsNotificationConfigured();
  }

  public static AutoLogContext autoLogContext(NodeExecution nodeExecution) {
    return new AutoLogContext(logContextMap(nodeExecution), OVERRIDE_NESTS);
  }

  public static Map<String, String> logContextMap(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return AmbianceUtils.logContextMap(nodeExecution.getAmbiance());
    }
    Map<String, String> logContext = executionContext.getSetupAbstractionsMap() == null
        ? new HashMap<>()
        : new HashMap<>(executionContext.getSetupAbstractionsMap());
    logContext.put("planExecutionId", executionContext.getPlanExecutionId());
    Level level = obtainCurrentLevel(nodeExecution);
    if (level != null) {
      logContext.put("identifier", level.getIdentifier());
      logContext.put("runtimeId", level.getRuntimeId());
      logContext.put("setupId", level.getSetupId());
      logContext.put("stepType", level.getStepType().getType());
    }
    if (isNotEmpty(executionContext.getPipelineIdentifier())) {
      logContext.put("pipelineIdentifier", executionContext.getPipelineIdentifier());
    }
    return logContext;
  }

  public Optional<Level> getStepGroupLevelFromExecutionContext(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return AmbianceUtils.getStepGroupLevelFromAmbiance(nodeExecution.getAmbiance());
    }
    Optional<Level> stageLevel = Optional.empty();
    for (Level level : executionContext.getLevelsList()) {
      if (level.getStepType().getType().equals("STEP_GROUP")) {
        stageLevel = Optional.of(level);
      }
    }
    return stageLevel;
  }

  public Optional<Level> getNearestStepGroupLevelWithStrategyFromExecutionContext(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return AmbianceUtils.getNearestStepGroupLevelWithStrategyFromAmbiance(nodeExecution.getAmbiance());
    }
    for (int index = executionContext.getLevelsCount() - 1; index > 0; index--) {
      Level level = executionContext.getLevelsList().get(index);
      Level nextLevel = executionContext.getLevelsList().get(index - 1);
      if (level.getStepType().getType().equals("STEP_GROUP")
          && nextLevel.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        return Optional.of(level);
      }
    }
    return Optional.empty();
  }

  public String obtainNodeType(NodeExecution nodeExecution) {
    Level level = obtainCurrentLevel(nodeExecution);
    return level == null || isEmpty(level.getNodeType()) ? null : level.getNodeType();
  }

  public String getPlanExecutionId(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return nodeExecution.getAmbiance().getPlanExecutionId();
    }
    return executionContext.getPlanExecutionId();
  }

  public String getPlanId(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return nodeExecution.getAmbiance().getPlanId();
    }
    return executionContext.getPlanId();
  }

  public String getPipelineIdentifier(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return nodeExecution.getAmbiance().getMetadata().getPipelineIdentifier();
    }
    return executionContext.getPipelineIdentifier();
  }

  public static String getHarnessYamlVersion(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null || EmptyPredicate.isEmpty(executionContext.getHarnessYamlVersion())) {
      return AmbianceUtils.getPipelineVersion(nodeExecution.getAmbiance());
    }
    return executionContext.getHarnessYamlVersion();
  }

  public Map<String, String> getSetupAbstractionsMap(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return nodeExecution.getAmbiance().getSetupAbstractionsMap();
    }
    return executionContext.getSetupAbstractionsMap();
  }

  public List<String> prepareLevelRuntimeIdIndices(@NotNull ExecutionContext executionContext) {
    if (EmptyPredicate.isEmpty(executionContext.getLevelsList())) {
      // If the executionContext has no levels, the instance also shouldn't have any levels.
      return Collections.singletonList("");
    }

    List<String> levelRuntimeIdIndices = new ArrayList<>();
    levelRuntimeIdIndices.add("");
    for (int i = 1; i <= executionContext.getLevelsList().size(); i++) {
      levelRuntimeIdIndices.add(ResolverUtils.prepareLevelRuntimeIdIdx(executionContext.getLevelsList().subList(0, i)));
    }
    return levelRuntimeIdIndices;
  }

  public List<Level> getLevelList(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return nodeExecution.getAmbiance().getLevelsList();
    } else {
      return executionContext.getLevelsList();
    }
  }

  public Optional<Level> getStageLevelFromExecutionContext(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      return AmbianceUtils.getStageLevelFromAmbiance(nodeExecution.getAmbiance());
    }
    Optional<Level> stageLevel = Optional.empty();
    for (Level level : executionContext.getLevelsList()) {
      if (level.getStepType().getStepCategory() == StepCategory.STAGE || Objects.equals(level.getGroup(), "STAGE")) {
        stageLevel = Optional.of(level);
      }
    }
    return stageLevel;
  }

  public String getStageRuntimeId(NodeExecution nodeExecution) {
    Optional<Level> stageLevel = getStageLevelFromExecutionContext(nodeExecution);
    if (stageLevel.isPresent()) {
      return stageLevel.get().getRuntimeId();
    }
    throw new InvalidRequestException("Stage not present");
  }

  public static Level obtainParentLevel(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null || isEmpty(executionContext.getLevelsList())
        || executionContext.getLevelsCount() == 1) {
      return AmbianceUtils.obtainParentLevel(nodeExecution.getAmbiance());
    }
    return executionContext.getLevelsList().get(executionContext.getLevelsList().size() - 2);
  }

  public static StepType getParentStepType(NodeExecution nodeExecution) {
    Level level = obtainParentLevel(nodeExecution);
    return level == null ? null : level.getStepType();
  }

  public String getStrategySetupId(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    if (executionContext == null) {
      Ambiance ambiance = nodeExecution.getAmbiance();
      return ambiance.getLevels(ambiance.getLevelsCount() - 2).getSetupId();
    }
    return executionContext.getLevels(executionContext.getLevelsCount() - 2).getSetupId();
  }

  public List<RepairActionCode> getManualInterventionAvailableActions(NodeExecution nodeExecution) {
    if (nodeExecution.getAdviserResponse() != null && nodeExecution.getAdviserResponse().hasInterventionWaitAdvise()) {
      return new ArrayList<>(nodeExecution.getAdviserResponse().getInterventionWaitAdvise().getAvailableActionsList());
    }
    return new ArrayList<>();
  }

  public static Level obtainParentLevelAtDepth(ExecutionContext executionContext, int parentDepth) {
    if (executionContext == null || isEmpty(executionContext.getLevelsList())
        || executionContext.getLevelsCount() <= parentDepth) {
      return null;
    }
    return executionContext.getLevelsList().get(executionContext.getLevelsList().size() - parentDepth - 1);
  }

  public static boolean isCurrentLevelStageInGroup(NodeExecution nodeExecution) {
    ExecutionContext executionContext = nodeExecution.getExecutionContext();
    Level currentParent = obtainParentLevelAtDepth(executionContext, 1);
    if (currentParent == null) {
      return false;
    }

    StepCategory parentStepCategory = currentParent.getStepType().getStepCategory();

    // Case 1: Direct stage in group (group -> stages -> stage)
    if (StepCategory.STAGES.equals(parentStepCategory)) {
      Level groupLevel = obtainParentLevelAtDepth(executionContext, 2);
      if (groupLevel != null && NGCommonUtilPlanCreationConstants.GROUP.equals(groupLevel.getStepType().getType())) {
        return true;
      }

      // Case 2: Stage inside parallel block in group (group -> stages -> parallel -> stages -> stage)
      // Check if stages -> parallel -> stages -> stage pattern exists
      Level parallelLevel = obtainParentLevelAtDepth(executionContext, 2);
      Level outerStagesLevel = obtainParentLevelAtDepth(executionContext, 3);
      Level outerGroupLevel = obtainParentLevelAtDepth(executionContext, 4);

      return parallelLevel != null && outerStagesLevel != null && outerGroupLevel != null
          && StepCategory.FORK.equals(parallelLevel.getStepType().getStepCategory())
          && StepCategory.STAGES.equals(outerStagesLevel.getStepType().getStepCategory())
          && NGCommonUtilPlanCreationConstants.GROUP.equals(outerGroupLevel.getStepType().getType());
    }

    return false;
  }
}
