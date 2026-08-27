/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.common.NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN;
import static io.harness.pms.yaml.YamlNode.PATH_SEP;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
@Singleton
public class ExpansionRequestsExtractor {
  @Inject ExpansionRequestsHelper expansionRequestsHelper;
  @Inject PmsSdkInstanceService pmsSdkInstanceService;

  public Set<ExpansionRequest> fetchExpansionRequests(String pipelineYaml, String accountId, boolean isV1) {
    YamlNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(pipelineYaml).getNode();
    } catch (IOException e) {
      if (YamlUtils.isYamlSizeLimitExceeded(e)) {
        throw new InvalidRequestException(PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE, e);
      }
      throw new InvalidRequestException("Could not read pipeline yaml", e);
    }
    Stack<ModuleType> namespace = new Stack<>();
    namespace.push(isV1 ? ModuleType.CI : ModuleType.PMS);
    List<PmsSdkInstance> activeInstances = pmsSdkInstanceService.getActiveInstances();
    Map<ModuleType, Set<String>> expandableFieldsPerService =
        expansionRequestsHelper.getExpandableFieldsPerService(activeInstances);
    Map<String, ModuleType> typeToService = getTypeToService(isV1, activeInstances);

    Set<ExpansionRequest> serviceCalls = new HashSet<>();
    getServiceCalls(pipelineNode, expandableFieldsPerService, typeToService, namespace, serviceCalls, isV1);
    List<LocalFQNExpansionInfo> localFQNRequestMetadata =
        expansionRequestsHelper.getLocalFQNRequestMetadata(activeInstances);
    if (EmptyPredicate.isNotEmpty(localFQNRequestMetadata)) {
      getFQNBasedServiceCalls(pipelineNode, localFQNRequestMetadata, serviceCalls, accountId, isV1);
    }
    return serviceCalls;
  }

  /**
   * It returns map of module types to their corresponding service types, empty for unified as all the expansion will be
   * handled by CI service
   */
  private Map<String, ModuleType> getTypeToService(boolean isV1, List<PmsSdkInstance> activeInstances) {
    return isV1 ? new HashMap<>() : expansionRequestsHelper.getTypeToService(activeInstances);
  }

  void getServiceCalls(YamlNode node, Map<ModuleType, Set<String>> expandableFieldsPerService,
      Map<String, ModuleType> typeToService, Stack<ModuleType> namespace, Set<ExpansionRequest> serviceCalls,
      boolean isV1) {
    if (node.isObject()) {
      getServiceCallsForObject(node, expandableFieldsPerService, typeToService, namespace, serviceCalls, isV1);
    } else if (node.isArray()) {
      getServiceCallsForArray(node, expandableFieldsPerService, typeToService, namespace, serviceCalls, isV1);
    }
  }

  void getServiceCallsForObject(YamlNode node, Map<ModuleType, Set<String>> expandableFieldsPerService,
      Map<String, ModuleType> typeToService, Stack<ModuleType> namespace, Set<ExpansionRequest> serviceCalls,
      boolean isV1) {
    List<String> keys = node.fetchKeys();
    boolean popNamespace = checkAndUpdateNamespace(node, keys, typeToService, namespace, isV1);
    Set<String> expandableKeys = expandableFieldsPerService.get(namespace.peek()) != null
        ? expandableFieldsPerService.get(namespace.peek())
        : Collections.emptySet();
    for (String key : keys) {
      if (expandableKeys.contains(key)) {
        JsonNode value = node.getFieldOrThrow(key).getNode().getCurrJsonNode();
        if (value.isTextual() && NGExpressionUtils.containsPattern(GENERIC_EXPRESSIONS_PATTERN, value.textValue())) {
          continue;
        }
        ExpansionRequest request = ExpansionRequest.builder()
                                       .module(namespace.peek())
                                       .fqn(node.getYamlPath() + PATH_SEP + key)
                                       .key(key)
                                       .fieldValue(value)
                                       .build();
        serviceCalls.add(request);
        continue;
      }
      getServiceCalls(node.getFieldOrThrow(key).getNode(), expandableFieldsPerService, typeToService, namespace,
          serviceCalls, isV1);
    }
    updateNamespace(namespace, popNamespace, isV1);
  }

  /**
   * It updates the namespace with service based on node type and return true otherwise return false for unified
   * pipelines as service CI will handle it
   */
  boolean checkAndUpdateNamespace(YamlNode node, List<String> keys, Map<String, ModuleType> typeToService,
      Stack<ModuleType> namespace, boolean isV1) {
    if (isV1) {
      return false;
    }
    if (keys.contains(YAMLFieldNameConstants.TYPE) && typeToService.containsKey(node.getType())) {
      namespace.push(typeToService.get(node.getType()));
      return true;
    }
    return false;
  }

  /**
   * It updates the namespace by removing service based on popNamespace flag and skip for unified as it will always be
   * CI service in namespace
   */
  void updateNamespace(Stack<ModuleType> namespace, boolean popNamespace, boolean isV1) {
    if (isV1) {
      return;
    }
    if (popNamespace) {
      namespace.pop();
    }
  }

  void getServiceCallsForArray(YamlNode node, Map<ModuleType, Set<String>> expandableFieldsPerService,
      Map<String, ModuleType> typeToService, Stack<ModuleType> namespace, Set<ExpansionRequest> serviceCalls,
      boolean isV1) {
    List<YamlNode> nodes = node.asArray();
    for (YamlNode internalNode : nodes) {
      getServiceCalls(internalNode, expandableFieldsPerService, typeToService, namespace, serviceCalls, isV1);
    }
  }

  void getFQNBasedServiceCalls(YamlNode pipelineNode, List<LocalFQNExpansionInfo> localFQNRequestMetadata,
      Set<ExpansionRequest> serviceCalls, String accountId, boolean isV1) {
    YamlNode internalNode = pipelineNode.getFieldOrThrow(YAMLFieldNameConstants.PIPELINE).getNode();
    List<YamlNode> stagesList = internalNode.getFieldOrThrow(YAMLFieldNameConstants.STAGES).getNode().asArray();
    for (YamlNode stageNode : stagesList) {
      if (stageNode.getField(YAMLFieldNameConstants.PARALLEL) != null) {
        handleParallel(localFQNRequestMetadata, serviceCalls, stageNode, isV1);
      } else if (stageNode.getField(YAMLFieldNameConstants.INSERT) != null) {
        handleInsert(localFQNRequestMetadata, serviceCalls, stageNode, isV1);
      } else {
        getServiceCallsForStage(stageNode, localFQNRequestMetadata, serviceCalls, isV1);
      }
    }
  }

  private void handleNode(List<LocalFQNExpansionInfo> localFQNRequestMetadata, Set<ExpansionRequest> serviceCalls,
      YamlNode stageNode, boolean isV1) {
    if (stageNode.getField(YAMLFieldNameConstants.PARALLEL) != null) {
      handleParallel(localFQNRequestMetadata, serviceCalls, stageNode, isV1);
    } else if (stageNode.getField(YAMLFieldNameConstants.INSERT) != null) {
      handleInsert(localFQNRequestMetadata, serviceCalls, stageNode, isV1);
    } else {
      getServiceCallsForStage(stageNode, localFQNRequestMetadata, serviceCalls, isV1);
    }
  }

  private void handleParallel(List<LocalFQNExpansionInfo> localFQNRequestMetadata, Set<ExpansionRequest> serviceCalls,
      YamlNode stageNode, boolean isV1) {
    YamlNode parallelNode = stageNode.getFieldOrThrow(YAMLFieldNameConstants.PARALLEL).getNode();
    List<YamlNode> parallelStages = parallelNode.asArray();
    for (YamlNode parallelStage : parallelStages) {
      handleNode(localFQNRequestMetadata, serviceCalls, parallelStage, isV1);
    }
  }

  private void handleInsert(List<LocalFQNExpansionInfo> localFQNRequestMetadata, Set<ExpansionRequest> serviceCalls,
      YamlNode stageNode, boolean isV1) {
    YamlNode injectNode = stageNode.getField(YAMLFieldNameConstants.INSERT).getNode();
    if (null == injectNode.getField(YAMLFieldNameConstants.STAGES)) {
      return;
    }
    YamlField injectStagesField = injectNode.getField(YAMLFieldNameConstants.STAGES);
    YamlNode injectStagesNode = injectStagesField.getNode();
    if (injectStagesNode == null || !injectStagesNode.isArray()) {
      return;
    }
    List<YamlNode> injectedStages = injectStagesNode.asArray();
    for (YamlNode injectedStage : injectedStages) {
      handleNode(localFQNRequestMetadata, serviceCalls, injectedStage, isV1);
    }
  }

  void getServiceCallsForStage(YamlNode stageNode, List<LocalFQNExpansionInfo> localFQNRequestMetadata,
      Set<ExpansionRequest> serviceCalls, boolean isV1) {
    String stageType = getStageType(stageNode, isV1);
    List<LocalFQNExpansionInfo> currStageRequestsData = getRequestsDataForStageType(localFQNRequestMetadata, stageType);
    if (EmptyPredicate.isNotEmpty(currStageRequestsData)) {
      getServiceCalls(stageNode, currStageRequestsData, serviceCalls);
    }
  }

  /**
   * It returns the stage type as CI for unified pipeline or extract it from stageNode
   */
  private String getStageType(YamlNode stageNode, boolean isV1) {
    return isV1 ? ModuleType.CI.name() : stageNode.getFieldOrThrow(YAMLFieldNameConstants.STAGE).getNode().getType();
  }

  List<LocalFQNExpansionInfo> getRequestsDataForStageType(
      List<LocalFQNExpansionInfo> localFQNRequestMetadata, String stageType) {
    return localFQNRequestMetadata.stream()
        .filter(e -> stageType.equals(e.getStageType()))
        .collect(Collectors.toList());
  }

  void getServiceCalls(
      YamlNode node, List<LocalFQNExpansionInfo> currStageRequestsData, Set<ExpansionRequest> serviceCalls) {
    if (node.isObject()) {
      getServiceCallsForObject(node, currStageRequestsData, serviceCalls);
    } else if (node.isArray()) {
      getServiceCallsForArray(node, currStageRequestsData, serviceCalls);
    }
  }

  void getServiceCallsForObject(
      YamlNode node, List<LocalFQNExpansionInfo> currStageRequestsData, Set<ExpansionRequest> serviceCalls) {
    List<YamlField> fields = node.fields();
    for (YamlField field : fields) {
      YamlNode currNode = field.getNode();
      String yamlPath = currNode.getYamlPath();
      String localPath = currNode.extractStageLocalYamlPath();
      List<LocalFQNExpansionInfo> selectedExpansionRequestData =
          currStageRequestsData.stream().filter(e -> e.getLocalFQN().equals(localPath)).collect(Collectors.toList());
      selectedExpansionRequestData.forEach(e -> {
        ExpansionRequest expansionRequest = ExpansionRequest.builder()
                                                .module(e.getModule())
                                                .fqn(yamlPath)
                                                .key(localPath)
                                                .fieldValue(currNode.getCurrJsonNode())
                                                .build();
        serviceCalls.add(expansionRequest);
      });
      getServiceCalls(field.getNode(), currStageRequestsData, serviceCalls);
    }
  }

  void getServiceCallsForArray(
      YamlNode node, List<LocalFQNExpansionInfo> currStageRequestsData, Set<ExpansionRequest> serviceCalls) {
    List<YamlNode> nodes = node.asArray();
    for (YamlNode internalNode : nodes) {
      getServiceCalls(internalNode, currStageRequestsData, serviceCalls);
    }
  }
}
