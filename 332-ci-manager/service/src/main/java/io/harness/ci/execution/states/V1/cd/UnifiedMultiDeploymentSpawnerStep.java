/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.ALL;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.ENVIRONMENT_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.ENV_BRANCH_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.INFRA_ID;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.INFRA_INPUTS;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.SERVICE_INPUTS;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.SERVICE_REF;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.SVC_BRANCH_REF;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.BRANCH;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.EnvironmentGroupEntity;
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.app.beans.entities.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.beans.IdentifierRef;
import io.harness.beans.stages.UnifiedMultiDeploymentStepParameters;
import io.harness.cd.mappers.UnifiedServiceEntityMapper;
import io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils;
import io.harness.cd.multi.deploy.UnifiedServiceTypeValidatorUtils;
import io.harness.ci.cd.service.EnvironmentGroupService;
import io.harness.ci.cd.service.InfrastructureEntityService;
import io.harness.ci.cd.service.ServiceEntityService;
import io.harness.common.utils.EnvironmentInfraFilterUtils;
import io.harness.envgroup.remote.EnvironmentGroupResourceClient;
import io.harness.envgroup.unified.UnifiedEnvGroupResponseDTO;
import io.harness.environment.remote.EnvironmentResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.infrastructure.unified.UnifiedEnvListConverterResponse;
import io.harness.infrastructure.unified.UnifiedEnvListRequestDTO;
import io.harness.infrastructure.unified.UnifiedEnvironmentConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfrasConverterRequestDTO;
import io.harness.infrastructure.unified.UnifiedInfrasConvertorResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.rbac.CDNGRbacPermissions;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.JsonUtils;
import io.harness.steps.executable.ChildrenExecutableWithRollbackAndRbac;
import io.harness.tasks.ResponseData;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.unified.cd.service.spec.ServiceInfoConfig;
import io.harness.unified.cd.service.spec.ServiceType;
import io.harness.unified.error.NgManagerErrorResponseDTO;
import io.harness.unified.service.NgServiceResourceClient;
import io.harness.unified.service.UnifiedServiceTypeResponse;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class UnifiedMultiDeploymentSpawnerStep
    extends ChildrenExecutableWithRollbackAndRbac<UnifiedMultiDeploymentStepParameters> {
  private static final String ITEMS = "items";
  private static final String GROUP = "group";
  private static final String ID = "id";
  private static final String DEPLOY_TO = "deploy-to";
  private static final String WITH = "with";
  private static final String DEPLOY_TO_ALL = "deploy-to-all";
  private static final String MATRIX_EXPRESSION_PREFIX = "<+matrix.";
  private static final List<String> SKIP_KEYS_LIST_FROM_STAGE_NAME =
      Arrays.asList("environmentInputs", "serviceInputs", "infraInputs");
  private static final String MULTI_ENV_DEPLOYMENT = "MULTI_ENV_DEPLOYMENT";
  private static final String MULTI_SERVICE_ENV_DEPLOYMENT = "MULTI_SERVICE_ENV_DEPLOYMENT";
  private static final String MULTI_SERVICE_DEPLOYMENT = "MULTI_SERVICE_DEPLOYMENT";
  public static final int INFRA_LIST_SIZE = 1000;

  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType("unifiedMultiDeployment").setStepCategory(StepCategory.STRATEGY).build();

  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private AccessControlClient accessControlClient;
  @Inject EnvironmentGroupService environmentGroupService;
  @Inject EnvironmentResourceClient environmentResourceClient;
  @Inject InfrastructureResourceClient infrastructureResourceClient;
  @Inject EnvironmentGroupResourceClient environmentGroupResourceClient;
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private NgServiceResourceClient ngServiceResourceClient;

  @Override
  public Class<UnifiedMultiDeploymentStepParameters> getStepParametersClass() {
    return UnifiedMultiDeploymentStepParameters.class;
  }

  @Override
  public StepResponse handleChildrenResponseInternal(Ambiance ambiance,
      UnifiedMultiDeploymentStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed execution for MultiDeploymentSpawner Step [{}]", stepParameters);
    if (StatusUtils.checkIfAllChildrenSkipped(responseDataMap.values()
                                                  .stream()
                                                  .filter(o -> o instanceof StepResponseNotifyData)
                                                  .map(o -> ((StepResponseNotifyData) o).getStatus())
                                                  .collect(Collectors.toList()))) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }
    return createStepResponseFromChildResponse(responseDataMap);
  }

  @Override
  public void validateResources(Ambiance ambiance, UnifiedMultiDeploymentStepParameters stepParameters) {
    ParameterField<Object> environments = stepParameters.getEnvironments();

    if (isEnvironmentFieldValid(environments)) {
      Map<?, ?> envNodeAsMap = (Map<?, ?>) environments.obtainValue();
      if (envNodeAsMap.containsKey(GROUP)) {
        String envGroupRef = extractEnvironmentGroupRef(envNodeAsMap);
        validateEnvironmentGroup(ambiance, envGroupRef);
      }
    }
  }

  @Override
  public ChildrenExecutableResponse obtainChildrenAfterRbac(
      Ambiance ambiance, UnifiedMultiDeploymentStepParameters stepParameters, StepInputPackage inputPackage) {
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    final String childNodeId = stepParameters.getChildNodeId();

    List<Map<String, String>> servicesMatrixMetadataList = getServicesMatrixMetadataList(stepParameters.getServices());
    if (isNotEmpty(servicesMatrixMetadataList)) {
      validateServicesSwimlane(ambiance, stepParameters.getServices());
    }
    List<Map<String, String>> environmentMatrixMetaDataList = getEnvironmentsMatrixMetadataList(
        stepParameters.getEnvironments(), projectIdentifier, orgIdentifier, accountId);

    if (isEmpty(servicesMatrixMetadataList) && isEmpty(environmentMatrixMetaDataList)) {
      throw new InvalidYamlException(
          "Invalid multi deployment configuration, Please check service or environment section of stage");
    }

    if (isNotEmpty(environmentMatrixMetaDataList)) {
      validateNoUnresolvedMatrixExpression(environmentMatrixMetaDataList, ENVIRONMENT_REF,
          "multi deployment set up is wrong, could not find environment candidates for deployment");
    }

    if (isNotEmpty(servicesMatrixMetadataList)) {
      validateNoUnresolvedMatrixExpression(servicesMatrixMetadataList, SERVICE_REF,
          "multi deployment set up is wrong, could not find service candidates for deployment");
    }

    if (isEmpty(servicesMatrixMetadataList)) {
      return getChildrenExecutionResponseForMultiEnvironment(
          stepParameters, children, environmentMatrixMetaDataList, childNodeId, ambiance);
    }

    if (isEmpty(environmentMatrixMetaDataList)) {
      return getChildrenExecutableResponseForMultiService(
          ambiance, stepParameters, children, childNodeId, servicesMatrixMetadataList);
    }

    return getChildrenExecutableResponseMultiServiceInfra(
        ambiance, stepParameters, children, childNodeId, servicesMatrixMetadataList, environmentMatrixMetaDataList);
  }

  private ChildrenExecutableResponse getChildrenExecutableResponseMultiServiceInfra(Ambiance ambiance,
      UnifiedMultiDeploymentStepParameters stepParameters, List<ChildrenExecutableResponse.Child> children,
      String childNodeId, List<Map<String, String>> servicesMatrixMetadataList,
      List<Map<String, String>> environmentMatrixMetaDataList) {
    boolean isEnvironmentParallel = UnifiedMultiDeploymentUtils.isEnvironmentParallel(stepParameters.getEnvironments());
    boolean isServiceParallel = UnifiedMultiDeploymentUtils.isServiceParallel(stepParameters.getServices());
    int currentIteration = 0;
    int totalIterations = servicesMatrixMetadataList.size() * environmentMatrixMetaDataList.size();
    int maxConcurrency;

    if (isServiceParallel) {
      if (!isEnvironmentParallel) {
        maxConcurrency = servicesMatrixMetadataList.size();
      } else {
        maxConcurrency = totalIterations;
      }

      for (Map<String, String> envMatrixMetadata : environmentMatrixMetaDataList) {
        for (Map<String, String> serviceMatrixMetadata : servicesMatrixMetadataList) {
          children.add(getChildForMultiServiceInfra(
              childNodeId, currentIteration, totalIterations, serviceMatrixMetadata, envMatrixMetadata, ambiance));
          currentIteration++;
        }
      }
    } else if (isEnvironmentParallel) {
      maxConcurrency = environmentMatrixMetaDataList.size();
      for (Map<String, String> envMatrixMetadataMap : environmentMatrixMetaDataList) {
        for (Map<String, String> serviceMatrixMetadataMap : servicesMatrixMetadataList) {
          children.add(getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations,
              serviceMatrixMetadataMap, envMatrixMetadataMap, ambiance));
          currentIteration++;
        }
      }
    } else {
      maxConcurrency = 1;
      for (Map<String, String> envMatrixMetadataMap : environmentMatrixMetaDataList) {
        for (Map<String, String> serviceMatrixMetadataMap : servicesMatrixMetadataList) {
          children.add(getChildForMultiServiceInfra(childNodeId, currentIteration, totalIterations,
              serviceMatrixMetadataMap, envMatrixMetadataMap, ambiance));
          currentIteration++;
        }
      }
    }
    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
  }

  private ChildrenExecutableResponse getChildrenExecutableResponseForMultiService(Ambiance ambiance,
      UnifiedMultiDeploymentStepParameters stepParameters, List<ChildrenExecutableResponse.Child> children,
      String childNodeId, List<Map<String, String>> servicesMatrixMetadataList) {
    int currentIteration = 0;
    int totalIterations = servicesMatrixMetadataList.size();
    int maxConcurrency = servicesMatrixMetadataList.size();
    if (!UnifiedMultiDeploymentUtils.isServiceParallel(stepParameters.getServices())) {
      maxConcurrency = 1;
    }
    for (Map<String, String> serviceMatrixMetadata : servicesMatrixMetadataList) {
      children.add(getChild(
          childNodeId, currentIteration, totalIterations, serviceMatrixMetadata, MULTI_SERVICE_DEPLOYMENT, ambiance));
      currentIteration++;
    }

    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
  }

  private ChildrenExecutableResponse.Child getChildForMultiServiceInfra(String childNodeId, int currentIteration,
      int totalIterations, Map<String, String> serviceMatrixMetadata, Map<String, String> envMatrixMetadata,
      Ambiance ambiance) {
    Map<String, String> matrixMetadataMap = new HashMap<>(serviceMatrixMetadata);
    matrixMetadataMap.putAll(envMatrixMetadata);
    String subType = UnifiedMultiDeploymentUtils.getSubType(serviceMatrixMetadata, envMatrixMetadata);
    return getChild(childNodeId, currentIteration, totalIterations, matrixMetadataMap, subType, ambiance);
  }

  private ChildrenExecutableResponse getChildrenExecutionResponseForMultiEnvironment(
      UnifiedMultiDeploymentStepParameters stepParameters, List<ChildrenExecutableResponse.Child> children,
      List<Map<String, String>> environmentMatrixMetaDataList, String childNodeId, Ambiance ambiance) {
    int currentIteration = 0;
    int totalIterations = environmentMatrixMetaDataList.size();
    int maxConcurrency = environmentMatrixMetaDataList.size();
    if (!UnifiedMultiDeploymentUtils.isEnvironmentParallel(stepParameters.getEnvironments())) {
      maxConcurrency = 1;
    }
    for (Map<String, String> envMatrixMetadata : environmentMatrixMetaDataList) {
      children.add(
          getChild(childNodeId, currentIteration, totalIterations, envMatrixMetadata, MULTI_ENV_DEPLOYMENT, ambiance));
      currentIteration++;
    }
    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
  }

  private List<Map<String, String>> getEnvironmentsMatrixMetadataList(
      ParameterField<Object> environments, String projectIdentifier, String orgIdentifier, String accountId) {
    if (ParameterField.isNull(environments) || environments.obtainValue() == null) {
      return new ArrayList<>();
    }

    if (!(environments.obtainValue() instanceof Map<?, ?> envNodeAsMap)) {
      throw new InvalidYamlException("Invalid environment YAML. Please check the environment section in the stage.");
    }

    List<Object> environmentObjects =
        extractEnvironmentObjects(envNodeAsMap, projectIdentifier, orgIdentifier, accountId);

    List<Map<String, String>> envMatrixMetadataList = new ArrayList<>();
    for (Object environment : environmentObjects) {
      envMatrixMetadataList.addAll(
          getSinlgeEnvironmentMatrixMetadataList(environment, projectIdentifier, orgIdentifier, accountId));
    }

    return envMatrixMetadataList;
  }

  /**
   * Guards against a misconfigured multi-deployment matrix where a candidate's ref (environmentRef/serviceRef)
   * still holds an unresolved {@code <+matrix...>} expression instead of a concrete value. This can happen when
   * the matrix values were not substituted correctly, leaving no real environment/service candidates to deploy to.
   */
  private void validateNoUnresolvedMatrixExpression(
      List<Map<String, String>> matrixMetadataList, String refKey, String errorMessage) {
    for (Map<String, String> matrixMetadata : matrixMetadataList) {
      String refValue = matrixMetadata.get(refKey);
      if (refValue != null && refValue.contains(MATRIX_EXPRESSION_PREFIX)) {
        throw new InvalidYamlException(errorMessage);
      }
    }
  }

  private List<Object> extractEnvironmentObjects(
      Map<?, ?> envNodeAsMap, String projectIdentifier, String orgIdentifier, String accountId) {
    if (envNodeAsMap.containsKey(GROUP)) {
      return extractEnvironmentObjectsFromEnvGroup(envNodeAsMap, projectIdentifier, orgIdentifier, accountId);
    } else if (envNodeAsMap.containsKey(ITEMS)) {
      return extractEnvironmentObjectsFromItemsNode(envNodeAsMap, projectIdentifier, orgIdentifier, accountId);
    } else if (envNodeAsMap.containsKey(YAMLFieldNameConstants.FILTERS)) {
      return extractEnvObjectsFromFiltersNode(
          envNodeAsMap.get(YAMLFieldNameConstants.FILTERS), projectIdentifier, orgIdentifier, accountId);
    } else if (envNodeAsMap.containsKey(ID)) {
      // Single environment YAML, e.g. `environment: { id: e1, deploy-to: all }`, is not wrapped in an `items`
      // array. Treat it as a single-entry item list so it is processed the same way as
      // `environment: { items: [{ id: e1, deploy-to: all }] }` would be.
      return processEnvItemsNodeWithFilter(List.of((Object) envNodeAsMap), projectIdentifier, orgIdentifier, accountId);
    }
    return new ArrayList<>();
  }

  private List<Object> extractFilteredEnvObjectsFromEnvGroup(
      String envGroupRef, Object filtersNode, String projectIdentifier, String orgIdentifier, String accountId) {
    UnifiedEnvGroupResponseDTO responseDTO = getResponse(environmentGroupResourceClient.getUnifiedEnvironmentGroup(
        envGroupRef, accountId, orgIdentifier, projectIdentifier));
    return extractFilteredEnvObjects(
        responseDTO.getEnvRefs(), filtersNode, projectIdentifier, orgIdentifier, accountId);
  }

  private List<Object> extractFilteredEnvObjects(
      List<String> envRefs, Object filtersNode, String projectIdentifier, String orgIdentifier, String accountId) {
    // validating yaml structure
    EnvironmentInfraFilterUtils.validateFiltersYaml(filtersNode, "multi-deployment env group");
    Map<String, List<Object>> entityTypeToObjectMap =
        EnvironmentInfraFilterUtils.extractEntityTypeToFiltersMap(filtersNode);
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    UnifiedEnvListConverterResponse unifiedEnvListConverterResponse =
        getResponse(environmentResourceClient.convertToUnifiedEnvironmentsList(accountId, orgIdentifier,
            projectIdentifier, gitEntityInfo.getBranch(), gitEntityInfo.getParentEntityRepoName(),
            UnifiedEnvListRequestDTO.builder().fetchAllEnvs(false).envRefs(envRefs).build()));

    // Filter infra list
    List<Object> infraFilters = entityTypeToObjectMap.get(YAMLFieldNameConstants.INFRASTRUCTURES);
    return getFilteredEnvironmentAndInfra(
        unifiedEnvListConverterResponse.getEnvironments(), infraFilters, projectIdentifier, orgIdentifier, accountId);
  }

  private List<Object> processEnvItemsNodeWithFilter(
      List<Object> itemList, String projectIdentifier, String orgIdentifier, String accountId) {
    List<Object> results = new ArrayList<>();
    for (Object item : itemList) {
      if (item instanceof Map<?, ?> itemsAsMap) {
        Object filterNode = itemsAsMap.get(YAMLFieldNameConstants.FILTERS);
        if (filterNode != null) {
          results.add(extractFilteredEnvObjectWithFilter(
              (String) itemsAsMap.get(ID), filterNode, projectIdentifier, orgIdentifier, accountId));
        } else {
          results.add(item);
        }
      }
    }
    return results;
  }

  private Object extractFilteredEnvObjectWithFilter(
      String envRef, Object filterNode, String projectIdentifier, String orgIdentifier, String accountId) {
    // validating yaml structure
    EnvironmentInfraFilterUtils.validateFiltersYaml(filterNode, "multi-deployment");

    Map<String, List<Object>> entityTypeToObjectMap =
        EnvironmentInfraFilterUtils.extractEntityTypeToFiltersMap(filterNode);

    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    UnifiedEnvListConverterResponse unifiedEnvListConverterResponse =
        getResponse(environmentResourceClient.convertToUnifiedEnvironmentsList(accountId, orgIdentifier,
            projectIdentifier, gitEntityInfo.getBranch(), gitEntityInfo.getParentEntityRepoName(),
            UnifiedEnvListRequestDTO.builder().fetchAllEnvs(false).envRefs(List.of(envRef)).build()));

    // Filter infra list
    List<Object> infraFilters = entityTypeToObjectMap.get(YAMLFieldNameConstants.INFRASTRUCTURES);
    List<Object> results = getFilteredEnvironmentAndInfra(
        unifiedEnvListConverterResponse.getEnvironments(), infraFilters, projectIdentifier, orgIdentifier, accountId);
    if (isEmpty(results)) {
      throw new InvalidRequestException(
          String.format("Could not find environment with ref: [%s] or infras as per filters", envRef));
    }
    return results.get(0);
  }

  private List<Object> extractEnvObjectsFromFiltersNode(
      Object filtersNode, String projectIdentifier, String orgIdentifier, String accountId) {
    // validating yaml structure
    EnvironmentInfraFilterUtils.validateFiltersYaml(filtersNode, "multi-deployment");

    Map<String, List<Object>> entityTypeToObjectMap =
        EnvironmentInfraFilterUtils.extractEntityTypeToFiltersMap(filtersNode);

    // Filter environments list
    List<Object> environmentFilters = entityTypeToObjectMap.get(YAMLFieldNameConstants.ENVIRONMENTS);
    List<UnifiedEnvironmentConverterResponseDTO> environments =
        getFilteredEnvironments(environmentFilters, projectIdentifier, orgIdentifier, accountId);

    // Filter infra list
    List<Object> infraFilters = entityTypeToObjectMap.get(YAMLFieldNameConstants.INFRASTRUCTURES);
    return getFilteredEnvironmentAndInfra(environments, infraFilters, projectIdentifier, orgIdentifier, accountId);
  }

  private List<UnifiedEnvironmentConverterResponseDTO> getFilteredEnvironments(
      List<Object> environmentFilters, String projectIdentifier, String orgIdentifier, String accountId) {
    GitEntityInfo pipelineGitEntityInfo = GitContextHelper.getGitEntityInfo();
    UnifiedEnvListConverterResponse unifiedEnvListConverterResponse =
        getResponse(environmentResourceClient.convertToUnifiedEnvironmentsList(accountId, orgIdentifier,
            projectIdentifier, pipelineGitEntityInfo.getBranch(), pipelineGitEntityInfo.getParentEntityRepoName(),
            UnifiedEnvListRequestDTO.builder().fetchAllEnvs(true).build()));
    return EnvironmentInfraFilterUtils.applyFilters(
        unifiedEnvListConverterResponse.getEnvironments(), environmentFilters);
  }

  private String getEnvBranchName(UnifiedEnvironmentConverterResponseDTO environment) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo != null && GitAwareContextHelper.getParentEntityRepoNameFromGitContext() != null) {
      return GitAwareContextHelper.getParentEntityRepoNameFromGitContext().equals(environment.getRepo())
          ? gitEntityInfo.getBranch()
          : "";
    }
    return "";
  }

  /**
   * This method returns list of environments and infra list for a given environment list. Example:
   * - id: env1
   *   deploy-to:
   *      - infra_name
   *      - infra_2
   */
  private List<Object> getFilteredEnvironmentAndInfra(
      List<UnifiedEnvironmentConverterResponseDTO> environmentConverterResponseDTOS, List<Object> infraFilters,
      String projectIdentifier, String orgIdentifier, String accountId) {
    List<Object> results = new ArrayList<>();
    for (UnifiedEnvironmentConverterResponseDTO environment : environmentConverterResponseDTOS) {
      UnifiedInfrasConvertorResponse unifiedInfrasConvertorResponse =
          getResponse(infrastructureResourceClient.convertToUnifiedInfrastructureList(accountId, orgIdentifier,
              projectIdentifier, environment.getEnvRef(), getEnvBranchName(environment), environment.getRepo(),
              UnifiedInfrasConverterRequestDTO.builder().infraIdsToInputYaml(Map.of("all", "")).build()));
      throwIfNgError(unifiedInfrasConvertorResponse == null ? null : unifiedInfrasConvertorResponse.getError(),
          String.format("Failed to fetch infrastructures in environment [%s], in project [%s], in org [%s]",
              environment.getEnvRef(), projectIdentifier, orgIdentifier));
      List<String> infraIds =
          EnvironmentInfraFilterUtils.applyFilters(unifiedInfrasConvertorResponse.getResponseDTOs(), infraFilters)
              .stream()
              .map(UnifiedInfraConverterResponseDTO::getIdentifier)
              .toList();
      results.add(Map.of(ID, environment.getEnvRef(), DEPLOY_TO, infraIds));
    }
    return results;
  }

  private List<Object> extractEnvironmentObjectsFromEnvGroup(
      Map<?, ?> envNodeAsMap, String projectIdentifier, String orgIdentifier, String accountId) {
    Object groupNode = envNodeAsMap.get(GROUP);
    if (groupNode instanceof Map<?, ?> envGroupAsMap) {
      if (envGroupAsMap.containsKey(ITEMS)) {
        return validateAndExtractEnvironmentsFromArray(
            envGroupAsMap.get(ITEMS), projectIdentifier, orgIdentifier, accountId);
      } else if (shouldDeployToAll(envGroupAsMap)) {
        String envGroupRef = extractEnvironmentGroupRef(envNodeAsMap);
        return fetchEnvironmentsFromGroup(envGroupRef, projectIdentifier, orgIdentifier, accountId);
      } else if (envGroupAsMap.containsKey(YAMLFieldNameConstants.FILTERS)) {
        String envGroupRef = extractEnvironmentGroupRef(envNodeAsMap);
        Object filtersNode = envGroupAsMap.get(YAMLFieldNameConstants.FILTERS);
        return extractFilteredEnvObjectsFromEnvGroup(
            envGroupRef, filtersNode, projectIdentifier, orgIdentifier, accountId);
      }
    }
    return new ArrayList<>();
  }

  private boolean shouldDeployToAll(Map<?, ?> envGroupAsMap) {
    return envGroupAsMap.containsKey(DEPLOY_TO_ALL) && envGroupAsMap.get(DEPLOY_TO_ALL) instanceof Boolean deployToAll
        && Boolean.TRUE.equals(deployToAll);
  }

  private List<Object> fetchEnvironmentsFromGroup(
      String envGroupRef, String projectIdentifier, String orgIdentifier, String accountId) {
    Optional<EnvironmentGroupEntity> environmentGroupEntityOpt =
        environmentGroupService.get(accountId, orgIdentifier, projectIdentifier, envGroupRef);

    if (environmentGroupEntityOpt.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Could not find environment group with ref: [%s] as mentioned in stage", envGroupRef));
    }

    EnvironmentGroupEntity environmentGroupEntity = environmentGroupEntityOpt.get();
    List<String> envsInGroup = environmentGroupEntity.getEnvironments();

    if (isEmpty(envsInGroup)) {
      throw new InvalidRequestException(
          String.format("Environment group: [%s] does not contain any environments", envGroupRef));
    }

    return getEnvObjectMapDeployToAllCase(envsInGroup);
  }

  private List<Object> extractEnvironmentObjectsFromItemsNode(
      Map<?, ?> envNodeAsMap, String projectIdentifier, String orgIdentifier, String accountId) {
    Object itemsNode = envNodeAsMap.get(ITEMS);
    return validateAndExtractEnvironmentsFromArray(itemsNode, projectIdentifier, orgIdentifier, accountId);
  }

  private List<Object> validateAndExtractEnvironmentsFromArray(
      Object itemsNode, String projectIdentifier, String orgIdentifier, String accountId) {
    if (!(itemsNode instanceof List<?> itemsList)) {
      throw new InvalidYamlException(
          "For multi-deployment, an array of environments is expected. Please check the environment section.");
    }
    return processEnvItemsNodeWithFilter((List<Object>) itemsList, projectIdentifier, orgIdentifier, accountId);
  }
  private List<Map<String, String>> getSinlgeEnvironmentMatrixMetadataList(
      Object environment, String projectIdentifier, String orgIdentifier, String accountId) {
    List<Map<String, String>> envMatrixMetadataList = new ArrayList<>();
    if (!(environment instanceof Map<?, ?> envNodeAsMap)) {
      throw new InvalidYamlException("Invalid yaml found for environment provided as part of multi environment config");
    }

    String environmentRef;
    if (envNodeAsMap.containsKey(ID)) {
      environmentRef = (String) envNodeAsMap.get(ID);
    } else {
      throw new InvalidYamlException(
          "Environment identifier is not defined for environment provided as part of multi environment config");
    }

    Object deployToNode = envNodeAsMap.get(DEPLOY_TO);
    if (deployToNode == null) {
      throw new InvalidYamlException("[deploy-to] is not defined for environment provided as part of multi environment "
          + "config, Please add [deploy-to] section in environment");
    }
    boolean deployToAllInfra = UnifiedMultiDeploymentUtils.isDeployToAllInfra(deployToNode);
    boolean isMultiInfra = UnifiedMultiDeploymentUtils.isDeployToMultiInfra(deployToNode);
    boolean isSingleInfra = UnifiedMultiDeploymentUtils.isDeployToSingleInfra(deployToNode);
    String envBranchRef = (String) envNodeAsMap.getOrDefault(BRANCH, null);

    if (deployToAllInfra) {
      return getEnvMatrixMetadataAllInfra(environmentRef, envBranchRef, projectIdentifier, orgIdentifier, accountId);
    }
    if (isMultiInfra) {
      return getEnvMatrixMetadataMultiInfra(environmentRef, envBranchRef, (List<Object>) deployToNode);
    }
    if (isSingleInfra) {
      return List.of(getEnvMatrixMetadataSingleInfra(environmentRef, envBranchRef, deployToNode));
    }
    throw new InvalidYamlException(String.format(
        "deploy-to yaml is misconfigured in stage, please check yaml configured for environment: [%s] in stage",
        environmentRef));
  }

  private List<Map<String, String>> getEnvMatrixMetadataMultiInfra(
      String environmentRef, String envBranchRef, List<Object> infraNodes) {
    List<Map<String, String>> envMetadataMapList = new ArrayList<>();
    for (Object infraNode : infraNodes) {
      Map<String, String> envMatrixMetadataSingleInfra =
          getEnvMatrixMetadataSingleInfra(environmentRef, envBranchRef, infraNode);
      if (isNotEmpty(envMatrixMetadataSingleInfra)) {
        envMetadataMapList.add(envMatrixMetadataSingleInfra);
      }
    }
    return envMetadataMapList;
  }

  private Map<String, String> getEnvMatrixMetadataSingleInfra(
      String environmentRef, String envBranchRef, Object infraNode) {
    Map<String, String> envMatrixMetadata = new HashMap<>();
    if (infraNode instanceof String infraId) {
      envMatrixMetadata =
          getEnvironmentMatrixMetadataSingleInfra(infraId, environmentRef, envBranchRef, StringUtils.EMPTY);
    } else if (infraNode instanceof Map<?, ?> infraNodeAsMap) {
      if (!infraNodeAsMap.containsKey(ID)) {
        throw new InvalidYamlException(
            "No infra identifier is found for infra configured as part of [deploy-to] config");
      }
      String infraId = (String) infraNodeAsMap.get(ID);

      if (infraNodeAsMap.containsKey(WITH)) {
        if (!(infraNodeAsMap.get(WITH) instanceof Map)) {
          throw new InvalidYamlException("Infra inputs should in form of key value pairs");
        }
      }
      envMatrixMetadata = getEnvironmentMatrixMetadataSingleInfra(
          infraId, environmentRef, envBranchRef, JsonUtils.asJson(infraNodeAsMap.get(WITH)));

    } else {
      throw new InvalidYamlException(
          "Invalid yaml configuration for deploy-to provided as part of multi environment config");
    }
    return envMatrixMetadata;
  }

  private Map<String, String> getEnvironmentMatrixMetadataSingleInfra(
      String infraId, String environmentRef, String envBranchRef, String infraInputsAsJson) {
    Map<String, String> envMetadataMap = new HashMap<>();
    envMetadataMap.put(ENVIRONMENT_REF, environmentRef);
    envMetadataMap.put(ENV_BRANCH_REF, envBranchRef != null ? envBranchRef : "");
    envMetadataMap.put(INFRA_ID, infraId);
    if (isNotEmpty(infraInputsAsJson)) {
      envMetadataMap.put(INFRA_INPUTS, infraInputsAsJson);
    }
    return envMetadataMap;
  }

  private List<Map<String, String>> getEnvMatrixMetadataAllInfra(
      String environmentRef, String envBranchRef, String projectIdentifier, String orgIdentifier, String accountId) {
    List<Map<String, String>> envMatrixMetadataList = new ArrayList<>();

    Pageable pageRequest = PageUtils.getPageRequest(0, INFRA_LIST_SIZE, new ArrayList<>());
    List<InfrastructureEntity> infrastructureEntities = infrastructureEntityService.listByEnvRef(accountId,
        orgIdentifier, projectIdentifier, environmentRef, List.of(InfrastructureEntityKeys.identifier), pageRequest);

    if (isNotEmpty(infrastructureEntities)) {
      for (InfrastructureEntity infra : infrastructureEntities) {
        envMatrixMetadataList.add(getEnvironmentMatrixMetadataSingleInfra(
            infra.getIdentifier(), environmentRef, envBranchRef, StringUtils.EMPTY));
      }
    } else {
      UnifiedInfrasConvertorResponse unifiedInfrasConvertorResponse =
          getResponse(infrastructureResourceClient.convertToUnifiedInfrastructureList(accountId, orgIdentifier,
              projectIdentifier, environmentRef, null, null,
              UnifiedInfrasConverterRequestDTO.builder().infraIdsToInputYaml(Map.of("all", "")).build()));
      throwIfNgError(unifiedInfrasConvertorResponse == null ? null : unifiedInfrasConvertorResponse.getError(),
          String.format("Failed to fetch infrastructures in environment [%s], in project [%s], in org [%s]",
              environmentRef, projectIdentifier, orgIdentifier));
      List<UnifiedInfraConverterResponseDTO> infras = unifiedInfrasConvertorResponse.getResponseDTOs();
      if (isNotEmpty(infras)) {
        for (UnifiedInfraConverterResponseDTO infra : infras) {
          envMatrixMetadataList.add(getEnvironmentMatrixMetadataSingleInfra(
              infra.getIdentifier(), environmentRef, envBranchRef, StringUtils.EMPTY));
        }
      } else {
        throw new InvalidRequestException(
            String.format("No infrastructure found in environment [%s], project [%s], organization [%s]",
                environmentRef, projectIdentifier, orgIdentifier));
      }
    }
    return envMatrixMetadataList;
  }

  private void throwIfNgError(NgManagerErrorResponseDTO error, String contextMessage) {
    if (error == null) {
      return;
    }
    // NG has already composed a Harness-extracted message with its own context; relay it as-is and only fall back
    // to the local context when NG did not populate a message.
    String ngErrorMessage = isNotEmpty(error.getErrorMessage()) ? error.getErrorMessage() : error.getDetailedMessage();
    String message = isNotEmpty(ngErrorMessage) ? ngErrorMessage : contextMessage;
    if (isNotEmpty(error.getErrorCode())) {
      message = String.format("%s [errorCode: %s]", message, error.getErrorCode());
    }
    throw new InvalidRequestException(message);
  }

  private List<Map<String, String>> getServicesMatrixMetadataList(ParameterField<Object> services) {
    List<Map<String, String>> servicesMatrixMetadataList = new ArrayList<>();

    if (ParameterField.isNull(services) || services.obtainValue() == null) {
      return new ArrayList<>();
    }

    if (!(services.obtainValue() instanceof Map<?, ?> servicesNodeAsMap)) {
      return new ArrayList<>();
    }

    if (servicesNodeAsMap.get(ITEMS) == null) {
      return new ArrayList<>();
    }

    if (!(servicesNodeAsMap.get(ITEMS) instanceof List<?>) ) {
      throw new InvalidYamlException(
          "For multi deployment, array of services is expected in stage, Please check service section in stage");
    }

    List<Object> serviceObjects = (List<Object>) servicesNodeAsMap.get(ITEMS);
    if (isEmpty(serviceObjects)) {
      throw new InvalidYamlException("No service is provided as part of multi  service config");
    }
    for (Object service : serviceObjects) {
      servicesMatrixMetadataList.add(getServiceMetadataMap(service));
    }
    return servicesMatrixMetadataList;
  }

  private Map<String, String> getServiceMetadataMap(Object serviceNodeObject) {
    Map<String, String> serviceMetadataMap = new HashMap<>();
    if (serviceNodeObject instanceof String serviceRef) {
      // A bare service id cannot carry a ref, so svcBranchRef is not emitted; the child's unresolved
      // <+matrix.svcBranchRef> then falls back to the pipeline Git context.
      serviceMetadataMap.put(SERVICE_REF, serviceRef);
    } else if (serviceNodeObject instanceof Map<?, ?> serviceInfoMap) {
      serviceMetadataMap.put(SERVICE_REF, (String) serviceInfoMap.get(ID));
      serviceMetadataMap.put(SERVICE_INPUTS, JsonUtils.asJson(serviceInfoMap.get(WITH)));
      // Per-item ref (Git branch) for remote services. Only emitted when present; otherwise the child's
      // unresolved <+matrix.svcBranchRef> falls back to the pipeline Git context.
      Object branch = serviceInfoMap.get(BRANCH);
      if (branch != null) {
        serviceMetadataMap.put(SVC_BRANCH_REF, String.valueOf(branch));
      }
    } else {
      throw new InvalidYamlException("Service provided as an element for multi services array has invalid yaml");
    }
    return serviceMetadataMap;
  }

  /**
   * Enforces swimlane consistency for a multi-service deployment at spawn time. In a multi-service group every service
   * reference is concrete here: they are either fixed ids or a runtime input ({@code items: <+input>}) that has already
   * been substituted with the provided values before plan creation. A mix of fixed values and runtime inputs, and
   * non-runtime-input expressions, are not supported for multi-service (matching V0), so the whole set can be validated
   * up front:
   * <ul>
   *   <li>a group-level {@code type} IS declared: every service must belong to the declared swimlane
   *       ({@link ServiceType}); this reuses the same check {@code UnifiedServiceStep} runs at runtime;</li>
   *   <li>no {@code type} is declared: every service must belong to the same swimlane.</li>
   * </ul>
   *
   * <p>The swimlane type is a static, immutable field on each service's DB metadata, so it is resolved by a
   * branch-agnostic DB read (no Git fetch) that works for inline and remote services alike.
   *
   * <p>Single-service (including a non-runtime-input expression ref) does not reach here; it is validated against a
   * declared {@code type}, if any, at the runtime service step where the ref resolves.
   */
  private void validateServicesSwimlane(Ambiance ambiance, ParameterField<Object> services) {
    List<UnifiedServiceTypeValidatorUtils.ServiceInfo> servicesInfo =
        UnifiedServiceTypeValidatorUtils.extractServicesInfo(services);
    if (isEmpty(servicesInfo)) {
      return;
    }
    String declaredType = UnifiedServiceTypeValidatorUtils.extractDeclaredServiceType(services);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    ServiceType referenceSwimlane = null;
    String referenceServiceRef = null;
    for (UnifiedServiceTypeValidatorUtils.ServiceInfo serviceInfo : servicesInfo) {
      ServiceType serviceType =
          resolveServiceType(accountId, orgIdentifier, projectIdentifier, serviceInfo.getServiceId());
      if (serviceType == null) {
        continue;
      }
      if (isNotEmpty(declaredType)) {
        // Declared type: validate every service against it (reuses the runtime check and its message).
        UnifiedServiceTypeValidatorUtils.validateResolvedServiceType(
            declaredType, serviceType, serviceInfo.getServiceId());
      } else if (referenceSwimlane == null) {
        referenceSwimlane = serviceType;
        referenceServiceRef = serviceInfo.getServiceId();
      } else if (!referenceSwimlane.equals(serviceType)) {
        throw new InvalidRequestException(String.format(
            "All services in a multi-service deployment must belong to the same swimlane. Service [%s] is of type [%s] "
                + "but service [%s] is of type [%s]. Please use services of the same type or declare the 'type' for "
                + "the "
                + "service group.",
            referenceServiceRef, referenceSwimlane.getDisplayName(), serviceInfo.getServiceId(),
            serviceType.getDisplayName()));
      }
    }
  }

  private ServiceType resolveServiceType(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    // Try from CI first. Reading the swimlane type only deserializes the stored YAML, so it is
    // safe even when the service still carries unresolved runtime inputs.
    Optional<ServiceEntity> serviceEntityOpt =
        serviceEntityService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceId, false);
    if (serviceEntityOpt.isPresent()) {
      String mergedServiceYaml = serviceEntityOpt.get().getYaml();
      if (isEmpty(mergedServiceYaml)) {
        throw new InvalidRequestException(
            String.format("Could not find service with id: [%s]. Check if service exist", serviceId));
      }
      ServiceConfig serviceConfig = UnifiedServiceEntityMapper.toServiceConfig(mergedServiceYaml);
      ServiceInfoConfig serviceInfoConfig = serviceConfig != null ? serviceConfig.getServiceInfoConfig() : null;
      return serviceInfoConfig != null ? serviceInfoConfig.getUses() : null;
    }

    // Fallback to NG service (inline or remote/Git-backed). The unified swimlane type is a static, immutable field
    // persisted on the service's single (branch-agnostic) DB metadata record, so a lightweight DB read resolves it
    // without the service's runtime inputs or a Git branch/repo - it works for remote services too and succeeds even
    // when the service still carries unresolved runtime inputs (the full unified conversion would fail in that case).
    IdentifierRef serviceIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(serviceId, accountIdentifier, orgIdentifier, projectIdentifier);
    UnifiedServiceTypeResponse response = getResponse(ngServiceResourceClient.getServiceType(
        serviceIdentifierRef.getIdentifier(), serviceIdentifierRef.getAccountIdentifier(),
        serviceIdentifierRef.getOrgIdentifier(), serviceIdentifierRef.getProjectIdentifier()));

    throwIfNgError(response == null ? null : response.getError(),
        String.format("Failed to fetch service with id: [%s]", serviceId));

    if (response == null) {
      throw new InvalidRequestException(String.format(
          "Could not find service with id: [%s]. Check if service exist or branch is correct if service is remote",
          serviceId));
    }

    // A resolved-but-unmapped type yields null, which the caller treats as "skip" for swimlane validation.
    return UnifiedServiceTypeValidatorUtils.parseServiceType(response.getServiceType()).orElse(null);
  }

  private ChildrenExecutableResponse.Child getChild(String childNodeId, int currentIteration, int totalIterations,
      Map<String, String> entityMap, String subType, Ambiance ambiance) {
    StrategyMetadata metadata = StrategyMetadata.newBuilder()
                                    .setCurrentIteration(currentIteration)
                                    .setTotalIterations(totalIterations)
                                    .setMatrixMetadata(MatrixMetadata.newBuilder()
                                                           .setSubType(subType)
                                                           .addMatrixCombination(currentIteration)
                                                           .addAllMatrixKeysToSkipInName(SKIP_KEYS_LIST_FROM_STAGE_NAME)
                                                           .putAllMatrixValues(entityMap)
                                                           .build())
                                    .build();
    String nodeName = AmbianceUtils.getStrategyPostFixUsingMetadata(metadata, true);
    metadata = metadata.toBuilder().setIdentifierPostFix(nodeName).build();
    return ChildrenExecutableResponse.Child.newBuilder()
        .setChildNodeId(childNodeId)
        .setStrategyMetadata(metadata)
        .build();
  }

  private boolean isEnvironmentFieldValid(ParameterField<Object> environments) {
    return ParameterField.isNotNull(environments) && environments.obtainValue() != null
        && environments.obtainValue() instanceof Map<?, ?>;
  }

  private String extractEnvironmentGroupRef(Map<?, ?> envNodeAsMap) {
    Object envGroupNode = envNodeAsMap.get(GROUP);

    if (envGroupNode instanceof String) {
      return (String) envGroupNode;
    } else if (envGroupNode instanceof Map<?, ?> envGroupNodeAsMap) {
      Object groupId = envGroupNodeAsMap.get(ID);
      if (groupId == null) {
        throw new InvalidYamlException("Environment group ID is not provided");
      }
      if (!(groupId instanceof String)) {
        throw new InvalidYamlException("Invalid YAML: Environment group ID field value should be a string");
      }
      return (String) groupId;
    }
    throw new InvalidYamlException("Invalid YAML: Unable to extract environment group reference");
  }

  private void validateEnvironmentGroup(Ambiance ambiance, String envGroupRef) {
    if (isEmpty(envGroupRef)) {
      return;
    }

    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    Optional<EnvironmentGroupEntity> environmentGroupEntityOp =
        environmentGroupService.get(accountIdentifier, orgIdentifier, projectIdentifier, envGroupRef);

    if (environmentGroupEntityOp.isEmpty()) {
      UnifiedEnvGroupResponseDTO envGroup = getResponse(environmentGroupResourceClient.getUnifiedEnvironmentGroup(
          envGroupRef, accountIdentifier, orgIdentifier, projectIdentifier));
      if (envGroup == null) {
        throw new InvalidRequestException(
            String.format("Could not find environment group with ref: [%s] as mentioned in stage", envGroupRef));
      }
    }
    validateRuntimeAccess(ambiance, envGroupRef, accountIdentifier, orgIdentifier, projectIdentifier);
  }

  private void validateRuntimeAccess(
      Ambiance ambiance, String envGroupRef, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    IdentifierRef envGroupIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(envGroupRef, accountIdentifier, orgIdentifier, projectIdentifier);

    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    if (isEmpty(principal)) {
      return;
    }

    PrincipalType principalType = PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
        executionPrincipalInfo.getPrincipalType());

    accessControlClient.checkForAccessOrThrow(
        Principal.of(principalType, principal, executionPrincipalInfo.getPrincipalUniqueId()),
        ResourceScope.of(envGroupIdentifierRef.getAccountIdentifier(), envGroupIdentifierRef.getOrgIdentifier(),
            envGroupIdentifierRef.getProjectIdentifier()),
        Resource.of(NGResourceType.ENVIRONMENT_GROUP, envGroupRef),
        CDNGRbacPermissions.ENVIRONMENT_GROUP_RUNTIME_PERMISSION,
        String.format("Validation for runtime access to environmentGroup: [%s] failed", envGroupRef));
  }

  private List<Object> getEnvObjectMapDeployToAllCase(List<String> envsInEnvGroup) {
    List<Object> environmentItems = new ArrayList<>();
    for (String envRef : envsInEnvGroup) {
      LinkedHashMap<String, Object> envObjectMap = new LinkedHashMap<>();
      envObjectMap.put(ID, envRef);
      envObjectMap.put(DEPLOY_TO, ALL);
      environmentItems.add(envObjectMap);
    }
    return environmentItems;
  }
}
