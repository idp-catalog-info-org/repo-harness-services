/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.cdstage.remote.CDNGStageSummaryResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.ng.core.cdstage.CDStageSummaryResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.notification.PipelineEventType;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.notification.ModuleInfo.ModuleInfoBuilder;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.utils.execution.ExecutionModeUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Slf4j
public class WebhookNotificationServiceImpl implements WebhookNotificationService {
  private final CDNGStageSummaryResourceClient cdngStageSummaryResourceClient;
  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final NodeExecutionService nodeExecutionService;
  private final PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;

  @Inject
  public WebhookNotificationServiceImpl(CDNGStageSummaryResourceClient cdngStageSummaryResourceClient,
      PlanExecutionMetadataService planExecutionMetadataService, PmsFeatureFlagHelper pmsFeatureFlagHelper,
      NodeExecutionService nodeExecutionService, PMSPipelineTemplateHelper pmsPipelineTemplateHelper,
      ScopeResolutionHelper scopeResolutionHelper) {
    this.cdngStageSummaryResourceClient = cdngStageSummaryResourceClient;
    this.planExecutionMetadataService = planExecutionMetadataService;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
    this.nodeExecutionService = nodeExecutionService;
    this.pmsPipelineTemplateHelper = pmsPipelineTemplateHelper;
    this.scopeResolutionHelper = scopeResolutionHelper;
  }
  @Override
  public ModuleInfo getModuleInfo(
      Ambiance ambiance, PipelineExecutionSummaryEntity executionSummaryEntity, PipelineEventType eventType) {
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    boolean shouldAddInputYaml =
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_INPUT_YAML_IN_WEBHOOK_NOTIFICATION.name());
    if (currentLevel == null || currentLevel.getStepType().getStepCategory() == StepCategory.PIPELINE) {
      return getModuleInfoForPipelineLevel(executionSummaryEntity, eventType, shouldAddInputYaml);
    }
    if (currentLevel.getStepType().getStepCategory() == StepCategory.STAGE) {
      return getModuleInfoForStage(executionSummaryEntity, ambiance, eventType, shouldAddInputYaml);
    }
    if (currentLevel.getStepType().getStepCategory() == StepCategory.STEP
        && currentLevel.getStepType().getType().equals(YAMLFieldNameConstants.VERIFY_STEP)) {
      return getModuleInfoForVerifyStep(executionSummaryEntity, ambiance);
    }
    return null;
  }

  private ModuleInfo getModuleInfoForVerifyStep(
      PipelineExecutionSummaryEntity executionSummaryEntity, Ambiance ambiance) {
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    NodeExecution nodeExecution = nodeExecutionService.get(currentLevel.getRuntimeId());
    Map<String, Object> resolvedParams = nodeExecution.getResolvedParams();
    Map<String, Object> cvngStepParams = (Map<String, Object>) resolvedParams.get(YAMLFieldNameConstants.SPEC);
    String monitoredServiceType = getMonitoredServiceType(cvngStepParams);
    if (monitoredServiceType.equalsIgnoreCase(YAMLFieldNameConstants.DEFAULT)) {
      return getModuleInfoForDefaultMonitoredService(cvngStepParams);
    } else if (monitoredServiceType.equalsIgnoreCase(YAMLFieldNameConstants.TEMPLATE)) {
      ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(
          executionSummaryEntity.getAccountId(), executionSummaryEntity.getParentUniqueId());

      return getModuleInfoForTemplateMonitoredService(cvngStepParams, scopeInfo.getAccountIdentifier(),
          scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier());
    }
    log.info("Could not Create moduleInfo for Verification Step: "
            + "WebhookNotificationServiceImpl.getModuleInfoForVerifyStep for cvngStepParams:{}",
        cvngStepParams);
    return null;
  }

  private String getMonitoredServiceType(Map<String, Object> cvngStepParams) {
    Map<String, Object> monitoredService =
        (Map<String, Object>) cvngStepParams.get(YAMLFieldNameConstants.MONITORED_SERVICE);
    return (String) monitoredService.get(YAMLFieldNameConstants.TYPE);
  }

  private ModuleInfo getModuleInfoForDefaultMonitoredService(Map<String, Object> cvngStepParams) {
    try {
      ModuleInfoBuilder moduleInfoBuilder = ModuleInfo.builder();
      String serviceRef =
          (String) RecastOrchestrationUtils
              .fromMap((Map<String, Object>) cvngStepParams.get(YAMLFieldNameConstants.SERVICE_IDENTIFIER),
                  ParameterField.class)
              .getJsonFieldValue();
      String envRef = (String) RecastOrchestrationUtils
                          .fromMap((Map<String, Object>) cvngStepParams.get(YAMLFieldNameConstants.ENV_IDENTIFIER),
                              ParameterField.class)
                          .getJsonFieldValue();
      moduleInfoBuilder.services(Collections.singletonList(serviceRef));
      moduleInfoBuilder.environments(Collections.singletonList(envRef));
      return moduleInfoBuilder.build();
    } catch (Exception e) {
      log.error("Error Occurred while getting moduleInfo for Default Monitored Service: ", e);
    }
    return null;
  }

  private ModuleInfo getModuleInfoForTemplateMonitoredService(Map<String, Object> cvngStepParams,
      @NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier) {
    try {
      ModuleInfoBuilder moduleInfoBuilder = ModuleInfo.builder();
      Map<String, Object> monitoredService =
          (Map<String, Object>) cvngStepParams.get(YAMLFieldNameConstants.MONITORED_SERVICE);
      Map<String, Object> monitoredServiceSpec =
          (Map<String, Object>) monitoredService.get(YAMLFieldNameConstants.SPEC);
      TemplateResponseDTO templateResponseDTO =
          getTemplateResponseDTO(accountId, orgIdentifier, projectIdentifier, monitoredServiceSpec);
      Map<String, Object> templateInputs =
          (Map<String, Object>) monitoredServiceSpec.get(YAMLFieldNameConstants.TEMPLATE_INPUTS);
      String serviceRef = getServiceReferenceForMonitoredService(templateResponseDTO, templateInputs);
      String envRef = getEnvironmentReferenceForMonitoredService(templateResponseDTO, templateInputs);
      moduleInfoBuilder.services(Collections.singletonList(serviceRef));
      moduleInfoBuilder.environments(Collections.singletonList(envRef));
      return moduleInfoBuilder.build();
    } catch (Exception e) {
      log.error("Error Occurred while getting moduleInfo for Template Monitored Service: ", e);
    }
    return null;
  }

  private TemplateResponseDTO getTemplateResponseDTO(
      String accountId, String orgIdentifier, String projectIdentifier, Map<String, Object> monitoredServiceSpec) {
    String monitoredServiceTemplateRef = (String) RecastOrchestrationUtils
                                             .fromMap((Map<String, Object>) monitoredServiceSpec.get(
                                                          YAMLFieldNameConstants.MONITORED_SERVICE_TEMPLATE_REF),
                                                 ParameterField.class)
                                             .getJsonFieldValue();
    String monitoredServiceTemplateVersionLabel =
        (String) RecastOrchestrationUtils
            .fromMap((Map<String, Object>) monitoredServiceSpec.get(YAMLFieldNameConstants.TEMPLATE_VERSION),
                ParameterField.class)
            .getJsonFieldValue();
    String monitoredServiceTemplateLabel = null;
    Map<String, Object> templateLabelMap =
        (Map<String, Object>) monitoredServiceSpec.get(YAMLFieldNameConstants.TEMPLATE_LABEL);
    if (templateLabelMap != null) {
      ParameterField<?> labelField = RecastOrchestrationUtils.fromMap(templateLabelMap, ParameterField.class);
      if (labelField != null) {
        monitoredServiceTemplateLabel = (String) labelField.getJsonFieldValue();
      }
    }
    TemplateResponseDTO templateResponseDTO;
    if (monitoredServiceTemplateRef.startsWith(YAMLFieldNameConstants.ACCOUNT_PREFIX)) {
      monitoredServiceTemplateRef = monitoredServiceTemplateRef.replace(YAMLFieldNameConstants.ACCOUNT_PREFIX, "");
      templateResponseDTO = pmsPipelineTemplateHelper.getTemplate(monitoredServiceTemplateRef, accountId, null, null,
          monitoredServiceTemplateVersionLabel, monitoredServiceTemplateLabel, "false", null);
    } else if (monitoredServiceTemplateRef.startsWith(YAMLFieldNameConstants.ORG_PREFIX)) {
      monitoredServiceTemplateRef = monitoredServiceTemplateRef.replace(YAMLFieldNameConstants.ORG_PREFIX, "");
      templateResponseDTO = pmsPipelineTemplateHelper.getTemplate(monitoredServiceTemplateRef, accountId, orgIdentifier,
          null, monitoredServiceTemplateVersionLabel, monitoredServiceTemplateLabel, "false", null);
    } else {
      templateResponseDTO = pmsPipelineTemplateHelper.getTemplate(monitoredServiceTemplateRef, accountId, orgIdentifier,
          projectIdentifier, monitoredServiceTemplateVersionLabel, monitoredServiceTemplateLabel, "false", null);
    }
    return templateResponseDTO;
  }

  private String getServiceReferenceForMonitoredService(
      TemplateResponseDTO templateResponseDTO, Map<String, Object> templateInputs) {
    List<String> refs = getModuleInfoReferencesFromTemplateYaml(templateResponseDTO.getYaml());
    Map<String, Object> encodedValue = (Map<String, Object>) templateInputs.get(YAMLFieldNameConstants.ENCODED_VALUE);
    if (YAMLFieldNameConstants.RUNTIME_INPUT.equalsIgnoreCase(refs.get(0))) {
      return (String) encodedValue.get(YAMLFieldNameConstants.SERVICE_REF);
    }
    return refs.get(0);
  }

  private String getEnvironmentReferenceForMonitoredService(
      TemplateResponseDTO templateResponseDTO, Map<String, Object> templateInputs) {
    List<String> refs = getModuleInfoReferencesFromTemplateYaml(templateResponseDTO.getYaml());
    Map<String, Object> encodedValue = (Map<String, Object>) templateInputs.get(YAMLFieldNameConstants.ENCODED_VALUE);
    if (YAMLFieldNameConstants.RUNTIME_INPUT.equalsIgnoreCase(refs.get(1))) {
      return (String) encodedValue.get(YAMLFieldNameConstants.ENVIRONMENT_REF);
    }
    return refs.get(1);
  }

  private List<String> getModuleInfoReferencesFromTemplateYaml(String templateYaml) {
    JsonNode templateSpec = YamlUtils.readYamlTree(templateYaml)
                                .getNode()
                                .getCurrJsonNode()
                                .get(YAMLFieldNameConstants.TEMPLATE)
                                .get(YAMLFieldNameConstants.SPEC);
    return new ArrayList<>(Arrays.asList(templateSpec.get(YAMLFieldNameConstants.SERVICE_REF).asText(),
        templateSpec.get(YAMLFieldNameConstants.ENVIRONMENT_REF).asText()));
  }

  private ModuleInfo getModuleInfoForPipelineLevel(
      PipelineExecutionSummaryEntity executionSummaryEntity, PipelineEventType eventType, boolean shouldAddInputYaml) {
    ModuleInfoBuilder moduleInfo = ModuleInfo.builder();
    Map<String, Object> moduleInfoMap = executionSummaryEntity.getModuleInfo().get("cd");
    if (shouldAddInputYaml && eventType == PipelineEventType.PIPELINE_START) {
      PlanExecutionMetadata planExecutionMetadata =
          planExecutionMetadataService.getWithFieldsIncludedFromSecondary(executionSummaryEntity.getAccountId(),
              executionSummaryEntity.getPlanExecutionId(), Sets.newHashSet(PlanExecutionMetadataKeys.inputSetYaml));
      if (planExecutionMetadata != null) {
        moduleInfo.inputYaml(getInputsJsonString(planExecutionMetadata.getInputSetYaml()));
      }
    }
    if (EmptyPredicate.isEmpty(moduleInfoMap)) {
      return moduleInfo.build();
    }
    if (moduleInfoMap.containsKey("infrastructureIdentifiers")) {
      moduleInfo.infrastructures((List<String>) moduleInfoMap.get("infrastructureIdentifiers"));
    }
    if (moduleInfoMap.containsKey("envIdentifiers")) {
      moduleInfo.environments((List<String>) moduleInfoMap.get("envIdentifiers"));
    }
    if (moduleInfoMap.containsKey("serviceIdentifiers")) {
      moduleInfo.services((List<String>) moduleInfoMap.get("serviceIdentifiers"));
    }
    if (moduleInfoMap.containsKey("envGroupIdentifiers")) {
      moduleInfo.envGroups((List<String>) moduleInfoMap.get("envGroupIdentifiers"));
    }

    return moduleInfo.build();
  }

  // TODO: Make this generic
  private ModuleInfo getModuleInfoForStage(PipelineExecutionSummaryEntity executionSummaryEntity, Ambiance ambiance,
      PipelineEventType pipelineEventType, boolean shouldAddInputYaml) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(
        executionSummaryEntity.getAccountId(), executionSummaryEntity.getParentUniqueId());
    boolean useScopeInfo = scopeInfo != null;

    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    Map<String, CDStageSummaryResponseDTO> stageSummaryResponseDTOMap = null;
    Optional<Level> strategyLevel = AmbianceUtils.getStrategyLevelFromAmbiance(ambiance);
    String stageIdentifier =
        strategyLevel.isEmpty() ? currentLevel.getIdentifier() : strategyLevel.get().getIdentifier();
    ModuleInfoBuilder moduleInfoBuilder = ModuleInfo.builder();
    if (shouldAddInputYaml && pipelineEventType == PipelineEventType.STAGE_START) {
      PlanExecutionMetadata planExecutionMetadata =
          planExecutionMetadataService.getWithFieldsIncludedFromSecondary(executionSummaryEntity.getAccountId(),
              executionSummaryEntity.getPlanExecutionId(), Sets.newHashSet(PlanExecutionMetadataKeys.inputSetYaml));
      if (planExecutionMetadata != null) {
        moduleInfoBuilder.inputYaml(getInputsJsonString(planExecutionMetadata.getInputSetYaml()));
      }
    }
    // This will work for rollbacks. But it will not work for multiple retries.
    boolean useOriginalNodeToGetModuleInfo = useOriginalNodeToGetModuleInfo(ambiance);
    String stageExecutionId = useOriginalNodeToGetModuleInfo ? ambiance.getOriginalStageExecutionIdForRollbackMode()
                                                             : currentLevel.getRuntimeId();
    String planExecutionId = useOriginalNodeToGetModuleInfo
        ? ambiance.getMetadata().getOriginalPlanExecutionIdForRollbackMode()
        : executionSummaryEntity.getPlanExecutionId();
    try {
      if (pipelineEventType != PipelineEventType.STAGE_START) {
        stageSummaryResponseDTOMap = getResponse(
            cdngStageSummaryResourceClient.listStageExecutionFormattedSummary(executionSummaryEntity.getAccountId(),
                useScopeInfo ? scopeInfo.getOrgIdentifier() : executionSummaryEntity.getOrgIdentifier(),
                useScopeInfo ? scopeInfo.getProjectIdentifier() : executionSummaryEntity.getProjectIdentifier(),
                Lists.newArrayList(stageExecutionId), false));
      } else {
        stageSummaryResponseDTOMap = getResponse(
            cdngStageSummaryResourceClient.listStagePlanCreationFormattedSummary(executionSummaryEntity.getAccountId(),
                useScopeInfo ? scopeInfo.getOrgIdentifier() : executionSummaryEntity.getOrgIdentifier(),
                useScopeInfo ? scopeInfo.getProjectIdentifier() : executionSummaryEntity.getProjectIdentifier(),
                planExecutionId, Lists.newArrayList(stageIdentifier), false));
      }
    } catch (Exception ex) {
      log.error("Exception occurred while updating module info during webhook notification", ex);
      return moduleInfoBuilder.build();
    }
    if (EmptyPredicate.isEmpty(stageSummaryResponseDTOMap)) {
      return moduleInfoBuilder.build();
    }
    CDStageSummaryResponseDTO stageSummaryResponseDTO = stageSummaryResponseDTOMap.get(stageExecutionId);
    if (stageSummaryResponseDTO == null) {
      stageSummaryResponseDTO = stageSummaryResponseDTOMap.get(stageIdentifier);
      if (stageSummaryResponseDTO == null) {
        return moduleInfoBuilder.build();
      }
    }
    if (pipelineEventType != PipelineEventType.STAGE_START) {
      if ((EmptyPredicate.isNotEmpty(stageSummaryResponseDTO.getService())
              && stageSummaryResponseDTO.getService().equals("NA"))
          || EmptyPredicate.isEmpty(stageSummaryResponseDTO.getArtifactDisplayName())) {
        return ModuleInfo.getModuleInfo(ambiance, executionSummaryEntity);
      }
    }

    return moduleInfoBuilder
        .services(EmptyPredicate.isEmpty(stageSummaryResponseDTO.getServices())
                ? Lists.newArrayList(stageSummaryResponseDTO.getService())
                : Lists.newArrayList(stageSummaryResponseDTO.getServices()))
        .artifactInfo(Lists.newArrayList(stageSummaryResponseDTO.getArtifactDisplayName()))
        .environments(EmptyPredicate.isEmpty(stageSummaryResponseDTO.getEnvironments())
                ? Lists.newArrayList(stageSummaryResponseDTO.getEnvironment())
                : Lists.newArrayList(stageSummaryResponseDTO.getEnvironments()))
        .infrastructures(EmptyPredicate.isEmpty(stageSummaryResponseDTO.getInfras())
                ? Lists.newArrayList(stageSummaryResponseDTO.getInfra())
                : Lists.newArrayList(stageSummaryResponseDTO.getInfras()))
        .build();
  }

  String getInputsJsonString(String inputsYaml) {
    JsonNode inputs;
    String inputsJsonString = null;
    if (EmptyPredicate.isEmpty(inputsYaml)) {
      return null;
    }
    try {
      inputs = YamlUtils.readTree(inputsYaml).getNode().getCurrJsonNode();
      ObjectMapper objectMapper = new ObjectMapper();
      inputsJsonString = objectMapper.writeValueAsString(inputs);
    } catch (Exception e) {
      log.error("Couldn't convert yaml to JsonNode", e);
    }
    return inputsJsonString;
  }

  private boolean useOriginalNodeToGetModuleInfo(Ambiance ambiance) {
    return ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())
        && NodeType.IDENTITY_PLAN_NODE.equals(
            NodeType.valueOf(AmbianceUtils.obtainCurrentLevel(ambiance).getNodeType()))
        && pmsFeatureFlagHelper.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_USE_PARENT_NODE_TO_GET_MODULE_INFO);
  }
}
