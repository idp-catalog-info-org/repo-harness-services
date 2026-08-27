/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.governance;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.pms.yaml.YAMLFieldNameConstants.DEPLOY_TO;
import static io.harness.pms.yaml.YAMLFieldNameConstants.GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ID;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ITEMS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.SEQUENTIAL;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.EnvironmentGroupEntity;
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cd.mappers.InfrastructureEntityMapper;
import io.harness.ci.cd.governance.UnifiedEnvironmentExpandedValue.UnifiedEnvironmentExpandedValueBuilder;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.ci.cd.service.EnvironmentGroupService;
import io.harness.ci.cd.service.InfrastructureEntityService;
import io.harness.common.NGExpressionUtils;
import io.harness.common.utils.CdStepsInputsMergeUtility;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.envgroup.remote.EnvironmentGroupResourceClient;
import io.harness.envgroup.unified.UnifiedEnvGroupResponseDTO;
import io.harness.environment.remote.EnvironmentResourceClient;
import io.harness.exception.ExceptionUtils;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.InputSetMergeHelperV1;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.infrastructure.unified.UnifiedEnvConvertorResponse;
import io.harness.infrastructure.unified.UnifiedEnvironmentConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfrasConverterRequestDTO;
import io.harness.infrastructure.unified.UnifiedInfrasConvertorResponse;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.handler.ExpansionResponse;
import io.harness.pms.sdk.core.governance.handler.JsonExpansionHandler;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.unified.cd.infrastructure.InfraConfig;
import io.harness.unified.cd.infrastructure.InfraInfoConfig;
import io.harness.unified.cd.infrastructure.InfrastructureSpec;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PageUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;

@OwnedBy(CI)
@Singleton
@Slf4j
public class UnifiedEnvironmentExpansionHandler implements JsonExpansionHandler {
  @Inject private ConnectorInputsMapper connectorInputsMapper;
  @Inject private EnvironmentEntityService environmentEntityService;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private ConnectorResourceClient connectorResourceClient;
  @Inject private EnvironmentResourceClient environmentResourceClient;
  @Inject private InfrastructureResourceClient infrastructureResourceClient;
  @Inject private EnvironmentGroupService environmentGroupService;
  @Inject private EnvironmentGroupResourceClient environmentGroupResourceClient;

  @Override
  public ExpansionResponse expand(JsonNode envNode, ExpansionRequestMetadata metadata, String fqn) {
    try {
      boolean isValid = validateEnvNode(envNode);
      if (!isValid) {
        return sendErrorResponseForEmptyEnvironments();
      }

      boolean isMultiDeployment = isMultiDeployment(envNode);
      UnifiedEnvironmentExpandedValue expandedValue;
      if (!isMultiDeployment) {
        expandedValue = expandEnvironment(envNode, metadata);
      } else {
        expandedValue = expandMultiEnvironments(envNode, metadata);
      }
      return ExpansionResponse.builder()
          .success(true)
          .placement(ExpansionPlacementStrategy.REPLACE)
          .key(expandedValue.getKey())
          .value(expandedValue)
          .build();

    } catch (Exception ex) {
      log.error("Exception in unified environment expansion", ex);
      return ExpansionResponse.builder().success(false).errorMessage(ExceptionUtils.getMessage(ex)).build();
    }
  }

  boolean validateEnvNode(JsonNode envNode) {
    return envNode != null
        && (getEnvironment(envNode) != null || isNotEmpty(getEnvironments(envNode.get(GROUP)))
            || isNotEmpty(getEnvironments(envNode)));
  }

  private boolean isEnvGroupPresent(JsonNode envNode) {
    return envNode != null && envNode.get(GROUP) != null;
  }

  private UnifiedEnvironmentExpandedValue expandMultiEnvironments(JsonNode envNode, ExpansionRequestMetadata metadata) {
    boolean isEnvGroup = isEnvGroupPresent(envNode);
    UnifiedEnvironmentExpandedValueBuilder envExpandedValueBuilder =
        UnifiedEnvironmentExpandedValue.builder().sequential(isSequential(envNode)).isMultiEnv(true);
    if (isEnvGroup) {
      UnifiedEnvGroupExpandedValue envGroupExpandedValue = expandEnvironmentGroup(envNode, metadata);
      return envExpandedValueBuilder.isEnvGroup(true).environmentGroup(envGroupExpandedValue).build();
    } else {
      List<UnifiedSingleEnvironmentExpandedValue> expandedValues = expandEnvironments(envNode, metadata);
      return envExpandedValueBuilder.environments(expandedValues).build();
    }
  }

  private UnifiedEnvironmentExpandedValue expandEnvironment(JsonNode envNode, ExpansionRequestMetadata metadata) {
    EnvironmentInfraData envInfraData = getEnvironment(envNode);
    if (envInfraData == null) {
      return null;
    }
    UnifiedSingleEnvironmentExpandedValue envExpandedValue = toUnifiedSingleEnvironmentExpandedValue(
        metadata, envInfraData.getEnvironmentId(), envInfraData.getInfraDataList());

    return UnifiedEnvironmentExpandedValue.builder()
        .environment(envExpandedValue)
        .sequential(isSequential(envNode))
        .build();
  }

  private UnifiedEnvGroupExpandedValue expandEnvironmentGroup(JsonNode envNode, ExpansionRequestMetadata metadata) {
    JsonNode envGroupNode = envNode.get(GROUP);
    String envGroupRef = getEnvOrEnvGroupId(envGroupNode);
    final String accountIdentifier = metadata.getAccountId();
    final String orgIdentifier = metadata.getOrgId();
    final String projectIdentifier = metadata.getProjectId();

    Optional<EnvironmentGroupEntity> environmentGroupEntityOp =
        environmentGroupService.get(accountIdentifier, orgIdentifier, projectIdentifier, envGroupRef);
    if (environmentGroupEntityOp.isPresent()) {
      return UnifiedEnvGroupExpandedValue.builder()
          .id(environmentGroupEntityOp.get().getIdentifier())
          .name(environmentGroupEntityOp.get().getName())
          .items(expandEnvironments(envGroupNode, metadata))
          .build();
    } else {
      UnifiedEnvGroupResponseDTO responseDTO = getResponse(environmentGroupResourceClient.getUnifiedEnvironmentGroup(
          envGroupRef, accountIdentifier, orgIdentifier, projectIdentifier));
      return UnifiedEnvGroupExpandedValue.builder()
          .id(responseDTO.getId())
          .name(responseDTO.getName())
          .items(expandEnvironments(envGroupNode, metadata))
          .build();
    }
  }

  private List<UnifiedSingleEnvironmentExpandedValue> expandEnvironments(
      JsonNode envNode, ExpansionRequestMetadata metadata) {
    List<UnifiedSingleEnvironmentExpandedValue> expandedEnvironments = new ArrayList<>();
    List<EnvironmentInfraData> environments = getEnvironments(envNode);
    environments.forEach(environmentInfraData
        -> expandedEnvironments.add(toUnifiedSingleEnvironmentExpandedValue(
            metadata, environmentInfraData.getEnvironmentId(), environmentInfraData.getInfraDataList())));
    return expandedEnvironments;
  }

  private EnvironmentInfraData getEnvironment(JsonNode envNode) {
    String envId = getEnvOrEnvGroupId(envNode);
    if (isNotEmpty(envId)) {
      List<InfraData> infraDataList = getInfraDataList(envNode);
      return EnvironmentInfraData.builder().environmentId(envId).infraDataList(infraDataList).build();
    }
    return null;
  }

  private List<EnvironmentInfraData> getEnvironments(JsonNode envNode) {
    List<EnvironmentInfraData> environments = new ArrayList<>();
    if (envNode == null) {
      return environments;
    }

    JsonNode envItems = envNode.get(ITEMS);
    if (envItems != null && envItems.isArray()) {
      ArrayNode envNodes = (ArrayNode) envItems;
      for (JsonNode envNodeItem : envNodes) {
        EnvironmentInfraData envInfraData = getEnvironment(envNodeItem);
        if (envInfraData != null) {
          environments.add(envInfraData);
        }
      }
    }
    return environments;
  }

  private String getEnvOrEnvGroupId(JsonNode envNode) {
    if (envNode == null) {
      return null;
    }

    JsonNode idNode = envNode.get(ID);
    if (idNode != null) {
      return idNode.asText();
    }

    return null;
  }

  private List<InfraData> getInfraDataList(JsonNode envNode) {
    List<InfraData> infraDataList = new ArrayList<>();
    if (envNode == null || envNode.get(DEPLOY_TO) == null) {
      return infraDataList;
    }

    JsonNode deployToNode = envNode.get(DEPLOY_TO);

    // Case 1: deploy-to is a single string
    if (deployToNode.isTextual()) {
      String infraId = deployToNode.asText();
      if (isNotEmpty(infraId)) {
        infraDataList.add(InfraData.builder().id(infraId).build());
      }
      return infraDataList;
    }

    // Case 2: deploy-to is an array of strings or objects
    if (deployToNode.isArray()) {
      ArrayNode deployToArray = (ArrayNode) deployToNode;
      for (JsonNode infraNode : deployToArray) {
        InfraData infraData = InfraData.fromJsonNode(infraNode);
        if (infraData != null) {
          infraDataList.add(infraData);
        }
      }
      return infraDataList;
    }

    // Case 3: deploy-to is a single object
    InfraData infraData = InfraData.fromJsonNode(deployToNode);
    if (infraData != null) {
      infraDataList.add(infraData);
    }

    return infraDataList;
  }

  private Boolean isSequential(JsonNode envNode) {
    return isMultiDeployment(envNode) && envNode.has(SEQUENTIAL) && envNode.get(SEQUENTIAL).isBoolean()
        && envNode.get(SEQUENTIAL).asBoolean();
  }

  private Boolean isMultiDeployment(JsonNode envNode) {
    return envNode != null && (envNode.get(ITEMS) != null || envNode.get(GROUP) != null);
  }

  private ExpansionResponse sendErrorResponseForEmptyEnvironments() {
    return ExpansionResponse.builder().success(false).errorMessage("No unified environments are present").build();
  }

  private UnifiedSingleEnvironmentExpandedValue toUnifiedSingleEnvironmentExpandedValue(
      ExpansionRequestMetadata metadata, String environmentRef, List<InfraData> infraDataList) {
    final String accountIdentifier = metadata.getAccountId();
    final String orgIdentifier = metadata.getOrgId();
    final String projectIdentifier = metadata.getProjectId();

    if (NGExpressionUtils.matchesGenericJexlOrCelExpressionPattern(environmentRef)) {
      log.warn(String.format("Environment id %s is an expression. Skipping policy expansion for it", environmentRef));
      return UnifiedSingleEnvironmentExpandedValue.builder().id(environmentRef).build();
    }

    EnvironmentResult environmentResult = getUnifiedSingleEnvironmentExpandedValueAndIsNG(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentRef);

    if (environmentResult.getEnvironment() == null) {
      log.warn(String.format("Environment %s does not exist. Skipping policy expansion for it", environmentRef));
      return UnifiedSingleEnvironmentExpandedValue.builder().id(environmentRef).build();
    }

    List<UnifiedInfrastructureExpandedValue> infrastructureExpandedValues = environmentResult.isNg()
        ? buildNgInfrastructureList(metadata, environmentRef, infraDataList)
        : buildInfrastructureList(metadata, environmentRef, infraDataList);

    environmentResult.getEnvironment().setInfrastructure(infrastructureExpandedValues);
    return environmentResult.getEnvironment();
  }

  EnvironmentResult getUnifiedSingleEnvironmentExpandedValueAndIsNG(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String environmentId) {
    IdentifierRef envIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(environmentId, accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<EnvironmentEntity> environmentOpt = environmentEntityService.get(envIdentifierRef.getAccountIdentifier(),
        envIdentifierRef.getOrgIdentifier(), envIdentifierRef.getProjectIdentifier(), environmentId);

    if (environmentOpt.isPresent()) {
      return EnvironmentResult.builder()
          .environment(buildUnifiedSingleEnvironmentExpandedValue(environmentOpt.get()))
          .isNg(false)
          .build();
    } else {
      UnifiedEnvConvertorResponse unifiedEnvConvertorResponse =
          getResponse(environmentResourceClient.convertToUnifiedEnvironment(envIdentifierRef.getIdentifier(),
              envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
              envIdentifierRef.getProjectIdentifier(), null, null));

      if (unifiedEnvConvertorResponse != null && unifiedEnvConvertorResponse.getResponseDTO() != null) {
        return EnvironmentResult.builder()
            .environment(buildUnifiedSingleEnvironmentExpandedValue(unifiedEnvConvertorResponse, envIdentifierRef))
            .isNg(true)
            .build();
      }
    }
    log.warn(String.format("Unified environment %s does not exist", environmentId));
    return EnvironmentResult.builder().environment(null).isNg(false).build();
  }

  private static UnifiedSingleEnvironmentExpandedValue buildUnifiedSingleEnvironmentExpandedValue(
      UnifiedEnvConvertorResponse unifiedEnvConvertorResponse, IdentifierRef envIdentifierRef) {
    UnifiedEnvironmentConverterResponseDTO responseDTO = unifiedEnvConvertorResponse.getResponseDTO();
    return UnifiedSingleEnvironmentExpandedValue.builder()
        .type(responseDTO.getType())
        .id(responseDTO.getIdentifier())
        .accountIdentifier(envIdentifierRef.getAccountIdentifier())
        .orgIdentifier(envIdentifierRef.getOrgIdentifier())
        .projectIdentifier(envIdentifierRef.getProjectIdentifier())
        .description(responseDTO.getDescription())
        .tags(responseDTO.getTags())
        .color(responseDTO.getColor())
        .name(responseDTO.getName())
        .build();
  }

  private static UnifiedSingleEnvironmentExpandedValue buildUnifiedSingleEnvironmentExpandedValue(
      EnvironmentEntity environmentEntity) {
    return UnifiedSingleEnvironmentExpandedValue.builder()
        .type(environmentEntity.getType())
        .id(environmentEntity.getIdentifier())
        .accountIdentifier(environmentEntity.getAccountId())
        .orgIdentifier(environmentEntity.getOrgIdentifier())
        .projectIdentifier(environmentEntity.getProjectIdentifier())
        .description(environmentEntity.getDescription())
        .tags(convertToMap(environmentEntity.getTags()))
        .color(environmentEntity.getColor())
        .name(environmentEntity.getName())
        .build();
  }

  List<UnifiedInfrastructureExpandedValue> buildNgInfrastructureList(
      ExpansionRequestMetadata metadata, String envRef, List<InfraData> infraDataList) {
    String accountIdentifier = metadata.getAccountId();
    String orgIdentifier = metadata.getOrgId();
    String projectIdentifier = metadata.getProjectId();

    UnifiedInfrasConverterRequestDTO requestDTO = getRequestDTO(infraDataList);
    UnifiedInfrasConvertorResponse unifiedInfrasConvertorResponse =
        getResponse(infrastructureResourceClient.convertToUnifiedInfrastructureList(
            accountIdentifier, orgIdentifier, projectIdentifier, envRef, null, null, requestDTO));

    // Expansion handlers must not fail the flow: on NG error, log and treat the response as absent.
    if (unifiedInfrasConvertorResponse == null || unifiedInfrasConvertorResponse.getError() != null) {
      if (unifiedInfrasConvertorResponse != null && unifiedInfrasConvertorResponse.getError() != null) {
        log.warn("Failed to fetch unified infrastructure list for environment {}: {}", envRef,
            unifiedInfrasConvertorResponse.getError().getErrorMessage());
      }
      return List.of();
    }

    List<UnifiedInfraConverterResponseDTO> responseDTOs = unifiedInfrasConvertorResponse.getResponseDTOs();
    List<UnifiedInfrastructureExpandedValue> infraExpandedValues = new ArrayList<>();
    responseDTOs.forEach(responseDTO -> {
      String mergedInfraYaml = responseDTO.getMergedInfrastructureYaml();
      JsonNode infraConfigNode =
          getInfraConfigJsonNode(accountIdentifier, orgIdentifier, projectIdentifier, mergedInfraYaml);
      UnifiedInfrastructureExpandedValue expandedValue = UnifiedInfrastructureExpandedValue.builder()
                                                             .id(responseDTO.getIdentifier())
                                                             .name(responseDTO.getName())
                                                             .description(responseDTO.getDescription())
                                                             .tags(responseDTO.getTags())
                                                             .infraNode(infraConfigNode.get("infrastructure"))
                                                             .build();
      infraExpandedValues.add(expandedValue);
    });
    return infraExpandedValues;
  }

  UnifiedInfrasConverterRequestDTO getRequestDTO(List<InfraData> infraDataList) {
    Map<String, String> infraIdToInputYamlMap =
        infraDataList.stream()
            .filter(infraData -> {
              String id = infraData.getId();
              boolean isExpression = NGExpressionUtils.matchesGenericJexlOrCelExpressionPattern(id);
              if (isExpression) {
                log.warn(String.format(
                    "Infrastructure id %s is an expression. Skipping to fetch this infra for expansion", id));
              }
              return !isExpression;
            })
            .collect(Collectors.toMap(InfraData::getId,
                infraData
                -> Optional.ofNullable(infraData.getInputs())
                       .map(inputs -> inputs.get("overlay"))
                       .map(YamlUtils::writeYamlString)
                       .orElse("")));
    return UnifiedInfrasConverterRequestDTO.builder().infraIdsToInputYaml(infraIdToInputYamlMap).build();
  }

  List<UnifiedInfrastructureExpandedValue> buildInfrastructureList(
      ExpansionRequestMetadata metadata, String envRef, List<InfraData> infraDataList) {
    String accountIdentifier = metadata.getAccountId();
    String orgIdentifier = metadata.getOrgId();
    String projectIdentifier = metadata.getProjectId();

    Pageable pageRequest = PageUtils.getPageRequest(0, 1000, new ArrayList<>());
    List<InfrastructureEntity> infrastructureEntities = infrastructureEntityService.listByEnvRef(
        accountIdentifier, orgIdentifier, projectIdentifier, envRef, null, pageRequest);

    if (isEmpty(infrastructureEntities)) {
      return List.of();
    }

    Map<String, InfraData> infraIdToInfraDataMap =
        infraDataList.stream()
            .filter(infraData -> {
              String id = infraData.getId();
              boolean isExpression = NGExpressionUtils.matchesGenericJexlOrCelExpressionPattern(id);
              if (isExpression) {
                log.warn(String.format(
                    "Infrastructure id %s is an expression. Skipping to fetch this infra for expansion", id));
              }
              return !isExpression;
            })
            .collect(Collectors.toMap(InfraData::getId, Function.identity()));

    List<UnifiedInfrastructureExpandedValue> infraExpandedValues = new ArrayList<>();
    boolean isAllInfra = infraDataList.stream().anyMatch(data -> YAMLFieldNameConstants.ALL.equals(data.getId()));

    infrastructureEntities.forEach(infraEntity -> {
      String infraId = infraEntity.getIdentifier();
      if (isAllInfra || infraIdToInfraDataMap.containsKey(infraId)) {
        InfraData infraData = infraIdToInfraDataMap.get(infraId);
        String mergedInfraYaml = getMergedInfraYaml(infraData, infraEntity);
        JsonNode infraConfigNode =
            getInfraConfigJsonNode(accountIdentifier, orgIdentifier, projectIdentifier, mergedInfraYaml);
        UnifiedInfrastructureExpandedValue expandedValue = UnifiedInfrastructureExpandedValue.builder()
                                                               .id(infraEntity.getIdentifier())
                                                               .name(infraEntity.getName())
                                                               .description(infraEntity.getDescription())
                                                               .tags(convertToMap(infraEntity.getTags()))
                                                               .infraNode(infraConfigNode.get("infrastructure"))
                                                               .build();
        infraExpandedValues.add(expandedValue);
      }
    });
    return infraExpandedValues;
  }

  private JsonNode getInfraConfigJsonNode(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String mergedInfraYaml) {
    InfraConfig infraConfig = InfrastructureEntityMapper.toConfig(mergedInfraYaml);
    JsonNode infraConfigNode = JsonPipelineUtils.asTree(infraConfig);
    Optional<ConnectorDTO> connectorDTOOpt =
        getConnectorDTO(accountIdentifier, orgIdentifier, projectIdentifier, infraConfig);
    if (connectorDTOOpt.isPresent() && infraConfigNode.has("infrastructure")
        && infraConfigNode.get("infrastructure").has("with")) {
      ConnectorDTO connectorDTO = connectorDTOOpt.get();
      ObjectNode withNode = (ObjectNode) infraConfigNode.get("infrastructure").get("with");
      withNode.set("connector", JsonPipelineUtils.asTree(connectorDTO).get("connector"));
    }
    return infraConfigNode;
  }

  private Optional<ConnectorDTO> getConnectorDTO(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, InfraConfig infraConfig) {
    String connectorRef = Optional.ofNullable(infraConfig.getInfraInfoConfig())
                              .map(InfraInfoConfig::getWith)
                              .map(InfrastructureSpec::getConnector)
                              .map(ParameterField::obtainValue)
                              .orElse(null);

    if (connectorRef == null) {
      return Optional.empty();
    }

    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    return getResponse(connectorResourceClient.get(connectorIdentifierRef.getIdentifier(),
        connectorIdentifierRef.getAccountIdentifier(), connectorIdentifierRef.getOrgIdentifier(),
        connectorIdentifierRef.getProjectIdentifier()));
  }

  private String getMergedInfraYaml(InfraData infraData, InfrastructureEntity infrastructureEntity) {
    String mergedInfraYaml = infrastructureEntity.getYaml();
    if (infraData != null && isNotEmpty(infraData.getInputs())) {
      mergedInfraYaml = mergeKeyValueInputsToInfraYaml(infraData.getInputs(), infrastructureEntity);
    }

    JsonNode infraConfig = YamlUtils.readAsJsonNode(mergedInfraYaml);
    ObjectNode infrastructeNode = (ObjectNode) infraConfig.get("infrastructure");
    if (infrastructeNode != null && infrastructeNode.has("inputs")) {
      infrastructeNode.remove("inputs");
      mergedInfraYaml = YamlUtils.writeYamlString(infraConfig);
    }
    return mergedInfraYaml;
  }

  private String mergeKeyValueInputsToInfraYaml(
      Map<String, Object> infraInputs, InfrastructureEntity infrastructureEntity) {
    JsonNode infraInputsJsonNodes = CdStepsInputsMergeUtility.parseInputsMapToJsonNode(infraInputs);
    return InputSetMergeHelperV1.mergeInputSetIntoEntityYaml(infraInputsJsonNodes, infrastructureEntity.getYaml(),
        connectorInputsMapper, infrastructureEntity.getAccountId(), infrastructureEntity.getOrgIdentifier(),
        infrastructureEntity.getProjectIdentifier(), YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE);
  }

  @Value
  @Builder
  public static class EnvironmentInfraData {
    String environmentId;
    List<InfraData> infraDataList;
  }

  @Value
  @Builder
  public static class EnvironmentResult {
    UnifiedSingleEnvironmentExpandedValue environment;
    boolean isNg;
  }
}
