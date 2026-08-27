/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers.validate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.inputmetadata.InputsMetadataRequestMetadata;
import io.harness.pms.contracts.inputmetadata.InputsMetadataResponse;
import io.harness.pms.contracts.plan.InputsMetadataInfo;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.inputset.inputmetadata.InputsMetadata;
import io.harness.pms.inputset.inputmetadata.InputsMetadataGenerator;
import io.harness.pms.inputset.inputmetadata.InputsMetadataRequest;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.merger.yaml.YamlConfig;
import io.harness.pms.merger.yaml.YamlSubMapExtractor;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.security.PrincipalProtoMapper;
import io.harness.security.SecurityContextBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InputsMetadataHelper {
  private final InputsMetadataGenerator inputsMetadataGenerator;
  private final PmsGitSyncHelper gitSyncHelper;
  private final PmsSdkInstanceService pmsSdkInstanceService;
  private static final String FQN_DISPLAY_DELIMITER = String.valueOf(FQN.DISPLAY_DELIMITER);

  public String mergeRuntimeInputsMetadataIntoTemplate(
      IdentifierRef identifierRef, String pipelineYaml, String runtimeInputFormYaml) {
    String pipelineIdentifier = identifierRef.getIdentifier();
    Map<String, io.harness.pms.inputset.inputmetadata.InputsMetadata> inputsMetadata = new HashMap<>();
    try {
      YamlNode templateYamlField = YamlUtils.readTree(runtimeInputFormYaml).getNode();
      Set<InputsMetadataRequest> inputsMetadataRequests =
          getInputsMetadataRequests(pipelineYaml, templateYamlField.getCurrJsonNode());
      if (!inputsMetadataRequests.isEmpty()) {
        ByteString gitSyncBranchContextBytes = gitSyncHelper.getGitSyncBranchContextBytesThreadLocal();
        InputsMetadataRequestMetadata.Builder requestMetadataBuilder =
            InputsMetadataRequestMetadata.newBuilder()
                .setAccountIdentifier(identifierRef.getAccountIdentifier())
                .setOrgIdentifier(identifierRef.getOrgIdentifier())
                .setPrincipal(PrincipalProtoMapper.toPrincipalProto(SecurityContextBuilder.getPrincipal()))
                .setProjectIdentifier(identifierRef.getProjectIdentifier());
        if (gitSyncBranchContextBytes != null) {
          requestMetadataBuilder.setGitContext(gitSyncBranchContextBytes);
        }
        InputsMetadataRequestMetadata requestMetadata = requestMetadataBuilder.build();
        Set<InputsMetadataResponse> inputsMetadataResponses =
            inputsMetadataGenerator.fetchInputsMetadata(inputsMetadataRequests, requestMetadata);
        inputsMetadata = parseInputsMetadataResponses(inputsMetadataResponses, pipelineIdentifier);
      }

      for (String fqn : inputsMetadata.keySet()) {
        InputsMetadata inputsMetadataEntry = inputsMetadata.get(fqn);
        YamlNode inputsMetadataYamlNode =
            YamlNodeUtils.goToPathUsingFqn(templateYamlField, fqn, inputsMetadataEntry.getEntityIdentifierKey());
        if (inputsMetadataYamlNode != null
            && inputsMetadataYamlNode.getCurrJsonNode() instanceof ObjectNode inputsMetadataObjectNode) {
          if (inputsMetadataEntry.isRequired()) {
            inputsMetadataObjectNode.set(
                YAMLFieldNameConstants.REQUIRED, BooleanNode.valueOf(inputsMetadataEntry.isRequired()));
          }
          if (EmptyPredicate.isNotEmpty(inputsMetadataEntry.getDescription())) {
            inputsMetadataObjectNode.set(
                YAMLFieldNameConstants.DESCRIPTION, TextNode.valueOf(inputsMetadataEntry.getDescription()));
          }
        }
      }
      return YamlUtils.writeYamlString(templateYamlField.getCurrJsonNode());
    } catch (Exception ex) {
      log.error(String.format(
                    "Failed to generate runtime inputs metadata for pipeline with identifier [%s]", pipelineIdentifier),
          ex);
      return runtimeInputFormYaml;
    }
  }

  protected Set<InputsMetadataRequest> getInputsMetadataRequests(
      String pipelineYaml, JsonNode runtimeInputFormJsonNode) {
    List<PmsSdkInstance> activeInstances = pmsSdkInstanceService.getActiveInstances();
    Map<String, ModuleType> entityInputsKeyToModuleMap = getEntityInputsKeyToModuleMap(activeInstances);
    Map<String, String> inputsKeyToEntityRefKeyMap = getEntityInputsKeyToEntityRefKeyMap(activeInstances);
    YamlConfig pipelineYamlConfig = new YamlConfig(pipelineYaml);
    YamlConfig runtimeInputFormYamlConfig = new YamlConfig(runtimeInputFormJsonNode);
    Set<InputsMetadataRequest> inputsMetadataRequests = new HashSet<>();
    Set<FQN> fqnsWithRawInputValue = RuntimeInputFormHelper.fetchFQNsWithRawInputFieldValue(pipelineYaml);
    for (FQN fqn : fqnsWithRawInputValue) {
      for (String entityKey : inputsKeyToEntityRefKeyMap.keySet()) {
        FQN baseFQN = fqn.getBaseFQNTillOneOfGivenFields(Set.of(entityKey));
        if (baseFQN != null && baseFQN.getParent() != null) {
          FQN parentFQN = baseFQN.getParent();
          String entityRef = getEntityRef(pipelineYamlConfig, parentFQN, inputsKeyToEntityRefKeyMap.get(entityKey));
          if (!(NGExpressionUtils.matchesRawInputSetPatternV2(entityRef) || EmptyPredicate.isEmpty(entityRef))) {
            String innerInputFormYaml = getInnerYamlFromFQN(runtimeInputFormYamlConfig, parentFQN);
            inputsMetadataRequests.add(InputsMetadataRequest.builder()
                                           .module(entityInputsKeyToModuleMap.get(entityKey))
                                           .entityId(entityRef)
                                           .entityType(entityKey)
                                           .entityIdentifierKey(inputsKeyToEntityRefKeyMap.get(entityKey))
                                           .inputFormYaml(innerInputFormYaml)
                                           .fqn(parentFQN.getExpressionFqn())
                                           .build());
            break;
          }
        }
      }
    }
    return inputsMetadataRequests;
  }

  private Map<String, io.harness.pms.inputset.inputmetadata.InputsMetadata> parseInputsMetadataResponses(
      Set<InputsMetadataResponse> inputsMetadataResponses, String pipelineIdentifier) {
    Map<String, io.harness.pms.inputset.inputmetadata.InputsMetadata> inputsMetadata = new HashMap<>();
    for (InputsMetadataResponse inputsMetadataResponse : inputsMetadataResponses) {
      String baseFqn = inputsMetadataResponse.getFqn();
      if (!inputsMetadataResponse.getSuccess()) {
        log.error(
            String.format("Error fetching inputsMetadata for pipeline with identifier: [%s], fqn: [%s], message: [%s]",
                pipelineIdentifier, baseFqn, inputsMetadataResponse.getError().getErrorMessage()));
        continue;
      }
      for (String innerFqn : inputsMetadataResponse.getResultMap().keySet()) {
        io.harness.pms.contracts.inputmetadata.InputsMetadata inputsMetadataEntry =
            inputsMetadataResponse.getResultMap().get(innerFqn);
        inputsMetadata.put(baseFqn.concat(FQN_DISPLAY_DELIMITER).concat(innerFqn),
            io.harness.pms.inputset.inputmetadata.InputsMetadata.builder()
                .description(inputsMetadataEntry.getDescription())
                .required(inputsMetadataEntry.getRequired())
                .entityIdentifierKey(inputsMetadataResponse.getEntityIdentifierKey())
                .build());
      }
    }
    return inputsMetadata;
  }

  private String getEntityRef(YamlConfig pipelineYamlConfig, FQN entityFQN, String entityRefKey) {
    JsonNode entityNode = YamlSubMapExtractor.getNodeForFQN(pipelineYamlConfig, entityFQN).get(entityRefKey);
    if (entityNode != null && entityNode.isTextual()) {
      return entityNode.asText();
    }
    return "";
  }

  private String getInnerYamlFromFQN(YamlConfig yaml, FQN fqn) {
    return YamlUtils.writeYamlString(YamlSubMapExtractor.getNodeForFQN(yaml, fqn));
  }

  @VisibleForTesting
  protected Map<String, ModuleType> getEntityInputsKeyToModuleMap(List<PmsSdkInstance> activeInstances) {
    Map<String, ModuleType> entityInputsKeyToModuleMap = new HashMap<>();
    activeInstances.forEach(sdkInstance -> {
      String sdkInstanceName = sdkInstance.getName();
      ModuleType module = ModuleType.fromString(sdkInstanceName);
      List<InputsMetadataInfo> inputsMetadataInfoList = sdkInstance.getInputsMetadataInfo();
      if (inputsMetadataInfoList == null) {
        return;
      }
      inputsMetadataInfoList.forEach(
          inputsMetadataInfo -> entityInputsKeyToModuleMap.put(inputsMetadataInfo.getEntityInputsKey(), module));
    });
    return entityInputsKeyToModuleMap;
  }

  @VisibleForTesting
  protected Map<String, String> getEntityInputsKeyToEntityRefKeyMap(List<PmsSdkInstance> activeInstances) {
    Map<String, String> entityInputsKeyToEntityRefKeyMap = new HashMap<>();
    activeInstances.forEach(sdkInstance -> {
      List<InputsMetadataInfo> inputsMetadataInfoList = sdkInstance.getInputsMetadataInfo();
      if (inputsMetadataInfoList == null) {
        return;
      }
      inputsMetadataInfoList.forEach(inputsMetadataInfo
          -> entityInputsKeyToEntityRefKeyMap.put(
              inputsMetadataInfo.getEntityInputsKey(), inputsMetadataInfo.getEntityKey()));
    });
    return entityInputsKeyToEntityRefKeyMap;
  }
}