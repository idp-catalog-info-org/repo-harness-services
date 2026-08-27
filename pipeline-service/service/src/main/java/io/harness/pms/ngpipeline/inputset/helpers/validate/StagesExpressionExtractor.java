/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers.validate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.common.ExpressionConstants.EXPR_END;
import static io.harness.expression.common.ExpressionConstants.EXPR_END_CEL;
import static io.harness.expression.common.ExpressionConstants.EXPR_START;
import static io.harness.expression.common.ExpressionConstants.EXPR_START_CEL;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_TRUE_VALUE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.stages.BasicStageInfo;
import io.harness.pms.stages.StageExecutionSelectorHelper;
import io.harness.pms.stages.StageExpressionInfo;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class StagesExpressionExtractor {
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final PMSPipelineService pmsPipelineService;
  private final PMSPipelineServiceHelper pmsPipelineServiceHelper;

  public Set<String> getNonLocalExpressions(String pipelineYaml, List<String> stageIdentifiers, String harnessVersion,
      String accountId, boolean loadFromCache, boolean isParentIdQueryingEnabledForPipeline,
      boolean shouldResolveChildPipeline) {
    StageExpressionInfo stageExpressionInfo =
        getAllExpressionsInListOfStages(pipelineYaml, stageIdentifiers, harnessVersion);
    return removeLocalExpressions(stageExpressionInfo, harnessVersion, accountId, loadFromCache,
        isParentIdQueryingEnabledForPipeline, shouldResolveChildPipeline);
  }

  public Map<String, List<String>> getNonLocalExpressionsPerStage(String pipelineYaml, List<String> stageIdentifiers,
      String harnessVersion, String accountId, boolean loadFromCache, boolean isParentIdQueryingEnabledForPipeline,
      boolean shouldResolveChildPipeline) {
    StageExpressionInfo stageExpressionInfo =
        getAllExpressionsInListOfStages(pipelineYaml, stageIdentifiers, harnessVersion);
    return removeLocalExpressionsPreservingStageMappingAndOrder(stageExpressionInfo, harnessVersion, accountId,
        loadFromCache, isParentIdQueryingEnabledForPipeline, shouldResolveChildPipeline);
  }

  public StageExpressionInfo getAllExpressionsInListOfStages(
      String pipelineYaml, List<String> stageIdentifiers, String harnessVersion) {
    Map<String, List<String>> stageIdToListOfExpressions = new LinkedHashMap<>();
    Map<String, BasicStageInfo> stageIdToInfo = new LinkedHashMap<>();
    List<BasicStageInfo> stageYamlList;
    if (HarnessYamlVersion.V0.equals(harnessVersion)) {
      stageYamlList = getStageYamlList(pipelineYaml, stageIdentifiers);
    } else {
      stageYamlList = getStageYamlListV1(pipelineYaml, stageIdentifiers);
    }

    stageYamlList.forEach(stageYaml -> {
      List<String> listOfExpressions = getListOfExpressions(stageYaml.getYaml());
      if (!HarnessYamlVersion.V0.equals(harnessVersion)) {
        List<String> listOfExpressionsV1 = getListOfExpressionsV1(stageYaml.getYaml());
        listOfExpressions.addAll(listOfExpressionsV1);
      }
      stageIdToListOfExpressions.put(stageYaml.getIdentifier(), listOfExpressions);
      stageIdToInfo.put(stageYaml.getIdentifier(), stageYaml);
    });
    return StageExpressionInfo.builder()
        .expressionsMap(stageIdToListOfExpressions)
        .stageYamlList(stageIdToInfo)
        .build();
  }

  List<BasicStageInfo> getStageYamlList(String pipelineYaml, List<String> stageIdentifiers) {
    try {
      List<BasicStageInfo> stageYamlList = new ArrayList<>();
      YamlField pipelineYamlField = YamlUtils.readTree(pipelineYaml);
      List<YamlNode> stagesYamlNodes = pipelineYamlField.getNode()
                                           .getField(YAMLFieldNameConstants.PIPELINE)
                                           .getNode()
                                           .getField(YAMLFieldNameConstants.STAGES)
                                           .getNode()
                                           .asArray();
      for (YamlNode stageYamlNode : stagesYamlNodes) {
        if (stageYamlNode.getField(YAMLFieldNameConstants.STAGE) != null) {
          handleStageNode(stageIdentifiers, stageYamlNode, stageYamlList);
        } else if (stageYamlNode.getField(YAMLFieldNameConstants.INSERT) != null) {
          handleInjectStages(stageIdentifiers, stageYamlList, stageYamlNode);
        } else {
          handleParallelStages(stageIdentifiers, stageYamlNode, stageYamlList);
        }
      }
      return stageYamlList;
    } catch (IOException e) {
      log.error("Could not read pipeline yaml while extracting stage yaml list. Yaml:\n" + pipelineYaml, e);
      throw new InvalidYamlException("Could not read pipeline yaml while extracting stage yaml list");
    }
  }

  private static void handleStageNode(
      List<String> stageIdentifiers, YamlNode stageYamlNode, List<BasicStageInfo> stageYamlList) throws IOException {
    BasicStageInfo basicStageInfoWithYaml = getBasicStageInfoWithYaml(stageYamlNode);
    if (stageIdentifiers.contains(basicStageInfoWithYaml.getIdentifier())) {
      stageYamlList.add(basicStageInfoWithYaml);
    }
  }

  private static void handleParallelStages(
      List<String> stageIdentifiers, YamlNode stageYamlNode, List<BasicStageInfo> stageYamlList) throws IOException {
    List<YamlNode> parallelStagesYamlNode = stageYamlNode.getField(YAMLFieldNameConstants.PARALLEL).getNode().asArray();
    for (YamlNode parallelStageYamlNode : parallelStagesYamlNode) {
      if (parallelStageYamlNode.getField(YAMLFieldNameConstants.STAGE) != null) {
        handleStageNode(stageIdentifiers, parallelStageYamlNode, stageYamlList);
      } else if (parallelStageYamlNode.getField(YAMLFieldNameConstants.INSERT) != null) {
        handleInjectStages(stageIdentifiers, stageYamlList, parallelStageYamlNode);
      }
    }
  }

  private static void handleInjectStages(List<String> stageIdentifiers, List<BasicStageInfo> stageYamlList,
      YamlNode insertStageYamlNode) throws IOException {
    List<YamlNode> insertStagesYamlNode = getInsertStages(insertStageYamlNode);
    for (YamlNode yamlNode : insertStagesYamlNode) {
      if (yamlNode.getField(YAMLFieldNameConstants.STAGE) != null) {
        handleStageNode(stageIdentifiers, yamlNode, stageYamlList);
      } else if (yamlNode.getField(YAMLFieldNameConstants.INSERT) != null) {
        throw NestedExceptionUtils.hintWithExplanationException("Nested Insert is not supported",
            "Insert block cannot contain insert again in any of its direct/indirect children",
            new InvalidRequestException("Nested Insert is not allowed."));
      } else if (yamlNode.getField(YAMLFieldNameConstants.PARALLEL) != null) {
        handleParallelStages(stageIdentifiers, yamlNode, stageYamlList);
      } else {
        throw new InvalidYamlException("Invalid key detected in 'Insert' block. Accepted keys are: [stage, parallel]. "
            + "Please verify the YAML structure and correct the key.");
      }
    }
  }

  private static List<YamlNode> getInsertStages(YamlNode insertStageYamlNode) {
    if (insertStageYamlNode != null && insertStageYamlNode.getField(YAMLFieldNameConstants.INSERT) != null
        && insertStageYamlNode.getField(YAMLFieldNameConstants.INSERT).getNode().getField(YAMLFieldNameConstants.STAGES)
            != null) {
      return insertStageYamlNode.getField(YAMLFieldNameConstants.INSERT)
          .getNode()
          .getField(YAMLFieldNameConstants.STAGES)
          .getNode()
          .asArray();
    }
    return new ArrayList<>();
  }

  List<BasicStageInfo> getStageYamlListV1(String pipelineYaml, List<String> stageIdentifiers) {
    try {
      List<BasicStageInfo> stageYamlList =
          StageExecutionSelectorHelper.getStageInfoListV1(pipelineYaml)
              .stream()
              .filter(stageInfo -> stageIdentifiers.contains(stageInfo.getIdentifier()))
              .collect(Collectors.toList());
      for (BasicStageInfo stageInfo : stageYamlList) {
        stageInfo.setYaml(YamlPipelineUtils.writeYamlString(stageInfo.getStageYamlNode().getCurrJsonNode()));
      }
      return stageYamlList;
    } catch (Exception e) {
      log.error("Could not read pipeline yaml while extracting stage yaml list. Yaml:\n" + pipelineYaml, e);
      throw new InvalidYamlException("Could not read pipeline yaml while extracting stage yaml list");
    }
  }

  @VisibleForTesting
  static BasicStageInfo getBasicStageInfoWithYaml(YamlNode stageYamlNode) throws IOException {
    String identifier = stageYamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getIdentifier();
    String name = stageYamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getName();
    String type = stageYamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getType();
    if (StepSpecTypeConstants.PIPELINE_STAGE.equals(type)) {
      /* We need to ignore expressions in Pipeline Chaining stage outputs, since they are referring to
         the chained pipeline, and we don't support selective stage execution in chained pipelines anyway. */
      stageYamlNode.getField(YAMLFieldNameConstants.STAGE)
          .getNode()
          .getField(YAMLFieldNameConstants.SPEC)
          .getNode()
          .removePath(YAMLFieldNameConstants.OUTPUTS);
    }
    String yaml = YamlPipelineUtils.getYamlString(stageYamlNode.getCurrJsonNode());

    return BasicStageInfo.builder().identifier(identifier).name(name).type(type).yaml(yaml).build();
  }

  List<String> getListOfExpressions(String stageYaml) {
    return NGExpressionUtils.getListOfExpressions(stageYaml);
  }

  List<String> getListOfExpressionsV1(String stageYaml) {
    return NGExpressionUtils.getListOfExpressionsV1(stageYaml);
  }

  Set<String> removeLocalExpressions(StageExpressionInfo stageExpressionInfo, String harnessVersion, String accountId,
      boolean loadFromCache, boolean isParentIdQueryingEnabledForPipeline, boolean shouldResolveChildPipeline) {
    Set<String> expressionsToOtherStages = new HashSet<>();
    Set<String> stageIdentifiers = stageExpressionInfo.getExpressionsMap().keySet();
    for (String stageIdentifier : stageIdentifiers) {
      List<String> allExpressions = stageExpressionInfo.getExpressionsMap().get(stageIdentifier);
      List<String> otherStageExpressions =
          allExpressions.stream()
              .filter(expression -> {
                if (isLocalToStage(expression) || isReferringToNonStageValue(expression, harnessVersion)) {
                  return false;
                }
                String stageInExpression = getStageIdentifierInExpression(expression, harnessVersion);
                BasicStageInfo stageInfo = stageExpressionInfo.getStageYamlList().get(stageIdentifier);
                Set<String> nestedStageIds = processChildPipelineStages(stageInfo, accountId, loadFromCache,
                    isParentIdQueryingEnabledForPipeline, shouldResolveChildPipeline);
                return stageInExpression != null
                    && (!stageIdentifiers.contains(stageInExpression) && !nestedStageIds.contains(stageInExpression));
              })
              .collect(Collectors.toList());
      expressionsToOtherStages.addAll(otherStageExpressions);
    }
    return expressionsToOtherStages;
  }

  Map<String, List<String>> removeLocalExpressionsPreservingStageMappingAndOrder(
      StageExpressionInfo stageExpressionInfo, String harnessVersion, String accountId, boolean loadFromCache,
      boolean isParentIdQueryingEnabledForPipeline, boolean shouldResolveChildPipeline) {
    Map<String, List<String>> desiredExpressionsMap = new LinkedHashMap<>();
    Set<String> stageIdentifiers = stageExpressionInfo.getExpressionsMap().keySet();
    for (String stageIdentifier : stageIdentifiers) {
      List<String> stageExpressions = stageExpressionInfo.getExpressionsMap().get(stageIdentifier);
      if (isEmpty(stageExpressions)) {
        continue;
      }
      List<String> otherStageExpressions =
          stageExpressions.stream()
              .filter(expression -> {
                if (isLocalToStage(expression) || isReferringToNonStageValue(expression, harnessVersion)) {
                  return false;
                }
                String stageInExpression = getStageIdentifierInExpression(expression, harnessVersion);
                BasicStageInfo stageInfo = stageExpressionInfo.getStageYamlList().get(stageIdentifier);
                Set<String> nestedStageIds = processChildPipelineStages(stageInfo, accountId, loadFromCache,
                    isParentIdQueryingEnabledForPipeline, shouldResolveChildPipeline);
                return stageInExpression != null
                    && (!stageIdentifiers.contains(stageInExpression) && !nestedStageIds.contains(stageInExpression));
              })
              .collect(Collectors.toList());
      if (isEmpty(otherStageExpressions)) {
        continue;
      }
      desiredExpressionsMap.put(stageIdentifier, otherStageExpressions);
    }
    return desiredExpressionsMap;
  }

  boolean isLocalToStage(String expression) {
    String firstKeyOfExpression = NGExpressionUtils.getFirstKeyOfExpression(expression);
    return !firstKeyOfExpression.equals("pipeline") && !firstKeyOfExpression.equals("stages");
  }

  boolean isReferringToNonStageValue(String expression, String harnessVersion) {
    String[] wordsInExpression = expression.replace(EXPR_START, "").replace(EXPR_END, "").split("\\.");
    if (wordsInExpression.length < 2) {
      return true;
    }
    boolean res = wordsInExpression[0].equals("pipeline") && !wordsInExpression[1].equals("stages");
    if (HarnessYamlVersion.isV1(harnessVersion)) {
      wordsInExpression = expression.replace(EXPR_START_CEL, "").replace(EXPR_END_CEL, "").split("\\.");
      res = res || wordsInExpression[0].equals("pipeline") && !wordsInExpression[1].equals("stages");
    }
    return res;
  }

  String getStageIdentifierInExpression(String expression, String harnessVersion) {
    String firstKeyOfExpression = NGExpressionUtils.getFirstKeyOfExpression(expression);
    String[] wordsInExpression = expression.replace(EXPR_START, "").replace(EXPR_END, "").split("\\.");
    String[] wordsInCelExpression = HarnessYamlVersion.isV1(harnessVersion)
        ? expression.replace(EXPR_START_CEL, "").replace(EXPR_END_CEL, "").split("\\.")
        : new String[] {};
    if (firstKeyOfExpression.equals("pipeline")) {
      if (wordsInExpression.length > 2) {
        return wordsInExpression[2];
      } else if (wordsInCelExpression.length > 2) {
        return wordsInCelExpression[2];
      } else {
        return null;
      }
    } else if (firstKeyOfExpression.equals("stages")) {
      if (wordsInExpression.length > 1) {
        return wordsInExpression[1];
      } else if (wordsInCelExpression.length > 1) {
        return wordsInCelExpression[1];
      } else {
        return null;
      }
    }
    throw new InvalidRequestException(expression + " is not a pipeline level or stages level expression");
  }

  @VisibleForTesting
  Set<String> processChildPipelineStages(BasicStageInfo stageInfo, String accountId, boolean loadFromCache,
      boolean isParentIdQueryingEnabledForPipeline, boolean shouldResolveChildPipeline) {
    if (!shouldResolveChildPipeline || stageInfo == null
        || !StepSpecTypeConstants.PIPELINE_STAGE.equals(stageInfo.getType())) {
      return new HashSet<>();
    }
    try {
      YamlField stageYamlField = YamlUtils.readTree(stageInfo.getYaml());
      if (stageYamlField != null && stageYamlField.getNode().getField(YAMLFieldNameConstants.STAGE) != null) {
        YamlNode stageNode = stageYamlField.getNode().getField(YAMLFieldNameConstants.STAGE).getNode();
        if (stageNode != null && stageNode.getField(YAMLFieldNameConstants.SPEC) != null) {
          YamlNode specNode = stageNode.getField(YAMLFieldNameConstants.SPEC).getNode();
          if (specNode != null) {
            String org = specNode.getStringValue("org");
            String project = specNode.getStringValue("project");
            String pipeline = specNode.getStringValue(YAMLFieldNameConstants.PIPELINE);
            if (isEmpty(org) || isEmpty(project) || isEmpty(pipeline)) {
              log.warn(
                  "Failed to process child pipeline stage: {} for account: {}, org/project/pipeline id of the child "
                      + "pipeline cannot be empty",
                  stageInfo.getIdentifier(), accountId);
              return new HashSet<>();
            }
            // We can't use the original scope info as it's project/org can be different
            ScopeInfo scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, org, project, null);
            Optional<PipelineEntity> nestedPipelineEntity = pmsPipelineService.getPipeline(accountId, org, project,
                pipeline, false, false, false, loadFromCache, scopeInfo, isParentIdQueryingEnabledForPipeline);

            if (nestedPipelineEntity.isPresent()) {
              String nestedPipelineYaml = nestedPipelineEntity.get().getYaml();
              if (HarnessYamlVersion.isV1(nestedPipelineEntity.get().getHarnessVersion())) {
                nestedPipelineYaml = pmsPipelineServiceHelper.preProcessPipelineYaml(nestedPipelineYaml, false);
              }
              String processedNestedYaml =
                  getYaml(accountId, org, project, nestedPipelineYaml, nestedPipelineEntity, loadFromCache, scopeInfo);
              return extractStageIdentifiersFromPipelineYaml(
                  processedNestedYaml, nestedPipelineEntity.get().getHarnessVersion());
            }
          }
        }
      }
    } catch (IOException e) {
      log.warn("Failed to process child pipeline stage: {} for account: {}", stageInfo.getIdentifier(), accountId, e);
    }
    return new HashSet<>();
  }

  public Set<String> extractStageIdentifiersFromPipelineYaml(String pipelineYaml, String harnessVersion) {
    Set<String> stageIds = new HashSet<>();
    try {
      if (HarnessYamlVersion.V0.equals(harnessVersion)) {
        return extractStageIdentifiersFromPipelineYamlV0(pipelineYaml);
      } else {
        return extractStageIdentifiersFromPipelineYamlV1(pipelineYaml);
      }
    } catch (Exception e) {
      log.warn("Failed to extract stage identifiers from pipeline YAML", e);
    }
    return stageIds;
  }

  private Set<String> extractStageIdentifiersFromPipelineYamlV0(String pipelineYaml) throws IOException {
    Set<String> stageIds = new HashSet<>();
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYaml);
    List<YamlNode> stagesYamlNodes = pipelineYamlField.getNode()
                                         .getField(YAMLFieldNameConstants.PIPELINE)
                                         .getNode()
                                         .getField(YAMLFieldNameConstants.STAGES)
                                         .getNode()
                                         .asArray();

    for (YamlNode stageYamlNode : stagesYamlNodes) {
      if (stageYamlNode.getField(YAMLFieldNameConstants.STAGE) != null) {
        handleStageNode(stageYamlNode, stageIds);
      } else if (stageYamlNode.getField(YAMLFieldNameConstants.INSERT) != null) {
        handleInjectStages(stageYamlNode, stageIds);
      } else {
        handleParallelStages(stageYamlNode, stageIds);
      }
    }
    return stageIds;
  }

  private Set<String> extractStageIdentifiersFromPipelineYamlV1(String pipelineYaml) throws IOException {
    // TODO: Add support of selective stage child pipeline in V1.
    return new HashSet<>();
  }

  private static void handleStageNode(YamlNode stageYamlNode, Set<String> stageIds) throws IOException {
    BasicStageInfo basicStageInfoWithYaml = getBasicStageInfoWithYaml(stageYamlNode);
    stageIds.add(basicStageInfoWithYaml.getIdentifier());
  }

  private static void handleParallelStages(YamlNode stageYamlNode, Set<String> stageIds) throws IOException {
    List<YamlNode> parallelStagesYamlNode = stageYamlNode.getField(YAMLFieldNameConstants.PARALLEL).getNode().asArray();
    for (YamlNode parallelStageYamlNode : parallelStagesYamlNode) {
      if (parallelStageYamlNode.getField(YAMLFieldNameConstants.STAGE) != null) {
        handleStageNode(parallelStageYamlNode, stageIds);
      } else if (parallelStageYamlNode.getField(YAMLFieldNameConstants.INSERT) != null) {
        handleInjectStages(parallelStageYamlNode, stageIds);
      }
    }
  }

  private static void handleInjectStages(YamlNode stageYamlNode, Set<String> stageIds) throws IOException {
    List<YamlNode> insertStages = getInsertStages(stageYamlNode);
    for (YamlNode insertStage : insertStages) {
      if (insertStage.getField(YAMLFieldNameConstants.STAGE) != null) {
        handleStageNode(insertStage, stageIds);
      } else if (insertStage.getField(YAMLFieldNameConstants.INSERT) != null) {
        throw NestedExceptionUtils.hintWithExplanationException("Nested Insert is not supported",
            "Insert block cannot contain insert again in any of its direct/indirect children",
            new InvalidRequestException("Nested Insert is not allowed."));
      } else if (insertStage.getField(YAMLFieldNameConstants.PARALLEL) != null) {
        handleParallelStages(insertStage, stageIds);
      } else {
        throw new InvalidYamlException("Invalid key detected in 'Insert' block. Accepted keys are: [stage, parallel]. "
            + "Please verify the YAML structure and correct the key.");
      }
    }
  }

  public String getYaml(String accountId, String orgIdentifier, String projectIdentifier, String pipelineYaml,
      Optional<PipelineEntity> optionalPipelineEntity, boolean loadFromCache, ScopeInfo scopeInfo) {
    if (optionalPipelineEntity.isPresent()
        && Boolean.TRUE.equals(optionalPipelineEntity.get().getTemplateReference())) {
      // returning resolved yaml
      orgIdentifier = scopeInfo != null ? scopeInfo.getOrgIdentifier() : orgIdentifier;
      projectIdentifier = scopeInfo != null ? scopeInfo.getProjectIdentifier() : projectIdentifier;
      return pipelineTemplateHelper
          .resolveTemplateRefsInPipeline(accountId, orgIdentifier, projectIdentifier, pipelineYaml,
              loadFromCache ? BOOLEAN_TRUE_VALUE : BOOLEAN_FALSE_VALUE,
              optionalPipelineEntity.get().getHarnessVersion())
          .getMergedPipelineYaml();
    }
    return pipelineYaml;
  }
}
