/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.beans.steps.constants.ServiceStepConstants.OVERLAY;
import static io.harness.ci.execution.common.InfraEntityProcessor.getInfraInputsYaml;
import static io.harness.ci.execution.common.ManifestTemplateConstants.OUTPUT_KEY_PLUGIN;
import static io.harness.ci.execution.common.ManifestTemplateConstants.PRIMARY;
import static io.harness.ci.execution.states.helpers.ManifestsStepUtils.getRunnerRelativePath;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.unified.cd.service.manifests.ManifestType.NO_OP_ACTION;
import static io.harness.unified.service.NGOutcomes.NG_OUTCOMES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.beans.IdentifierRef;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.cd.beans.outcomes.NgServiceOverridesYamlOutcome;
import io.harness.cd.beans.outcomes.NgServiceYamlOutcome;
import io.harness.cd.beans.outcomes.RenderingSweepingOutput;
import io.harness.cd.beans.outcomes.ServiceConfigOutcome;
import io.harness.cd.mappers.UnifiedServiceEntityMapper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactSourceCandidatesOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.ci.cd.service.InfrastructureEntityService;
import io.harness.ci.cd.service.ServiceEntityService;
import io.harness.ci.execution.common.ServiceEntityMetadata.ServiceEntityMetadataBuilder;
import io.harness.ci.execution.states.helpers.ManifestsStepUtils;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.execution.states.helpers.ServiceStepUtility;
import io.harness.common.NGExpressionUtils;
import io.harness.common.utils.CdStepsInputsMergeUtility;
import io.harness.common.utils.YamlParsingUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.InputSetMergeHelperV1;
import io.harness.expression.common.ExpressionMode;
import io.harness.filters.WithPaths;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.infrastructure.unified.UnifiedGitEntityInfoResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConverterRequestDTO;
import io.harness.infrastructure.unified.UnifiedInfraConvertorResponse;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.overrides.SingleOverrideConvertorResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.runner.request.helpers.infra.InfraBasedHelper;
import io.harness.serializer.JsonUtils;
import io.harness.unified.cd.service.annotations.ObjectFlattener;
import io.harness.unified.cd.service.artifacts.ArtifactConfig;
import io.harness.unified.cd.service.artifacts.ArtifactWrapper;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.hooks.ServiceHookConfig;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.manifests.ManifestType;
import io.harness.unified.cd.service.manifests.ManifestWrapper;
import io.harness.unified.cd.service.overrides.OverridesConfig;
import io.harness.unified.cd.service.overrides.OverridesWrapperDTO;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.unified.cd.service.spec.ServiceInfoConfig;
import io.harness.unified.cd.service.spec.ServiceSpec;
import io.harness.unified.cd.service.spec.SpotServiceSpec;
import io.harness.unified.cd.service.startupscript.StartupScriptConfiguration;
import io.harness.unified.error.NgManagerErrorResponseDTO;
import io.harness.unified.service.NGEntityFetchRequest;
import io.harness.unified.service.NGOutcomes;
import io.harness.unified.service.NGServiceEntityMetadata;
import io.harness.unified.service.NgServicePropertiesResponse;
import io.harness.unified.service.NgServicePropertiesResponseDTO;
import io.harness.unified.service.NgServiceResourceClient;
import io.harness.unified.service.OverrideFetchRequest;
import io.harness.unified.service.ServiceFetchRequest;
import io.harness.unified.service.UnifiedServiceConverterRequestDTO;
import io.harness.unified.service.UnifiedServiceConverterResponse;
import io.harness.unified.service.UnifiedServiceConverterResponseDTO;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.KebabCaseExpressionsUtility;
import io.harness.utils.TemplateYamlConfig;
import io.harness.utils.TemplateYamlEntityType;
import io.harness.utils.TemplateYamlGenerator;
import io.harness.utils.TemplateYamlResult;
import io.harness.utils.TemplateYamlSourceType;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@OwnedBy(HarnessTeam.CI)
@Slf4j
public class ServiceEntityProcessor {
  public static final String PATHS = "paths";
  @Inject private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject private NgServiceResourceClient ngServiceResourceClient;
  @Inject private InfraBasedHelper infraBasedHelper;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject private ConnectorInputsMapper connectorInputsMapper;
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject InfrastructureResourceClient infrastructureResourceClient;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject OverrideApplyHelper overrideApplyHelper;
  @Inject private ServiceStepOutcomeHelper serviceStepOutcomeHelper;
  @Inject private EnvOutcomeHelper envOutcomeHelper;
  @Inject private TemplateYamlGenerator templateYamlGenerator;
  @Inject private NgServiceYamlHelper ngServiceYamlHelper;
  @Inject private OverrideVariablesHelper overrideVariablesHelper;
  @Inject private RuntimeExpressionConversionHelper expressionConversionHelper;

  private static final String SERVICE = "service";
  private static final String INPUTS = "inputs";
  private static final String VALUE = "value";
  private static final String ARTIFACTS = "artifacts";
  private static final String MANIFESTS = "manifests";
  private static final String PLUGIN_INFO = "pluginInfo";
  private static final String ID = "id";
  public static final String TYPE = "type";
  public static final String SECRET = "secret";

  public ProcessedServiceResult getMergedServiceYamlAndSaveOutput(Ambiance ambiance, String serviceRef, String envRef,
      String envBranchRef, String infraId, String accountId, String orgIdentifier, String projectIdentifier,
      Map<String, Object> serviceInputs, ParameterField<Map<String, Object>> infraInputs,
      Map<String, Object> envOverridesInputs, Map<String, Object> svcOverridesInputs, String branch,
      @Nullable String envGroupRef) {
    validateInfraScopeWithService(
        serviceRef, envRef, envBranchRef, infraId, accountId, orgIdentifier, projectIdentifier, infraInputs);

    ServiceFetchResult serviceFetchResult =
        fetchService(ambiance, serviceRef, accountId, orgIdentifier, projectIdentifier, serviceInputs, branch, envRef,
            envBranchRef, infraId, envOverridesInputs, svcOverridesInputs);

    log.debug("Processing service: {}", serviceRef);
    ServiceConfig serviceConfig = toServiceConfig(serviceFetchResult.getMergedServiceYaml());

    Map<ServiceOverridesType, OverridesWrapperDTO> overrides =
        deriveOverridesFromResponses(serviceFetchResult.getOverridesResponses());

    // Save overrides YAML as sweeping output for expression resolution
    if (isNotEmpty(serviceFetchResult.getV0OverridesYamlMap())) {
      saveServiceOverridesYamlOutput(ambiance, serviceFetchResult.getV0OverridesYamlMap());
    }

    overrideVariablesHelper.saveVariablesAndCheckAccess(ambiance, serviceConfig, overrides);
    overrideApplyHelper.handleOverrides(ambiance, serviceConfig, overrides);

    // Resolved late (after serviceVariables are published above) so an expression primaryArtifactRef, e.g.
    // <+serviceVariables.artifactRef>, can be rendered against the ambiance.
    String resolvedPrimaryArtifactId = handlePrimaryArtifact(ambiance, serviceConfig);
    String resolvedPrimaryManifestId = handlePrimaryManifest(ambiance, serviceConfig);

    String resolvedMergedV0ServiceYaml = ngServiceYamlHelper.inlineResolvedPrimaryArtifact(
        serviceFetchResult.getResolvedMergedV0ServiceYaml(), resolvedPrimaryArtifactId);
    resolvedMergedV0ServiceYaml =
        ngServiceYamlHelper.filterResolvedPrimaryManifest(resolvedMergedV0ServiceYaml, resolvedPrimaryManifestId);

    Map<String, Map<String, Object>> serviceOutputMap =
        saveServiceOutput(ambiance, serviceConfig, resolvedMergedV0ServiceYaml);
    EnvironmentOutcome environmentOutcome = getEnvironmentOutcome(ambiance, envRef, accountId, orgIdentifier,
        projectIdentifier, envGroupRef, overrides, envBranchRef, serviceFetchResult.getEnvOutcomeYaml());

    return ProcessedServiceResult.builder()
        .serviceConfig(serviceConfig)
        .environmentOutcome(environmentOutcome)
        .serviceEntityMetadata(serviceFetchResult.getServiceEntityMetadata())
        .serviceOutputMap(isNotEmpty(serviceOutputMap) ? serviceOutputMap : new HashMap<>())
        .build();
  }

  private EnvironmentOutcome getEnvironmentOutcome(Ambiance ambiance, String envRef, String accountId,
      String orgIdentifier, String projectIdentifier, @Nullable String envGroupRef,
      Map<ServiceOverridesType, OverridesWrapperDTO> overrides, String envBranchRef, String envOutcomeYaml) {
    EnvironmentOutcome environmentOutcome = null;
    if (isNotEmpty(envRef)) {
      Map<String, Object> combinedInputs = OverrideVariablesHelper.getCombinedInputs(overrides, new HashMap<>());
      Map<String, Object> envOverrideVariables = OverrideVariablesHelper.getOutputVariables(combinedInputs);
      GitBranchInfo gitBranchInfo = prepareGitBranchInfo(envBranchRef);
      environmentOutcome = saveEnvironmentOutcome(ambiance, envRef, envGroupRef, accountId, orgIdentifier,
          projectIdentifier, envOverrideVariables, gitBranchInfo, envOutcomeYaml);
    }
    return environmentOutcome;
  }

  private EnvironmentOutcome saveEnvironmentOutcome(Ambiance ambiance, String environmentRef, String envGroupRef,
      String accountId, String orgIdentifier, String projectIdentifier, Map<String, Object> envOverrideVariables,
      GitBranchInfo gitBranchInfo, String envOutcomeYaml) {
    EnvironmentEntity environmentEntity = serviceStepOutcomeHelper.getEnvironmentEntity(environmentRef, accountId,
        orgIdentifier, projectIdentifier, gitBranchInfo.getBranch(), gitBranchInfo.getParentEntityRepoName());

    EnvironmentOutcome environmentOutcome;
    if (HarnessYamlVersion.isV1(environmentEntity.getHarnessVersion())) {
      environmentOutcome = envOutcomeHelper.getEnvironmentOutcome(environmentRef, environmentEntity, envGroupRef);
    } else {
      IdentifierRef envGroupIdentifierRef = null;
      if (isNotEmpty(envGroupRef)) {
        envGroupIdentifierRef = IdentifierRefHelper.getIdentifierRefWithScope(AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance), envGroupRef);
      }
      environmentOutcome =
          envOutcomeHelper.getEnvironmentOutcomeFromNGEnv(environmentRef, environmentEntity, envGroupIdentifierRef);
    }
    environmentOutcome.setVariables(envOverrideVariables);

    // Only V0 is exposed under `env`; every property read as an expression is a V0 property.
    io.harness.steps.environment.EnvironmentOutcome v0EnvironmentOutcome =
        buildV0EnvironmentOutcome(envOutcomeYaml, envOverrideVariables);
    if (v0EnvironmentOutcome != null) {
      serviceStepSweepingOutputHelper.saveV0EnvironmentOutcome(ambiance, v0EnvironmentOutcome);
    }
    return environmentOutcome;
  }

  /** Reads the V0 environment outcome from the NG outcome YAML. Returns null when the YAML is absent or unparseable. */
  @VisibleForTesting
  io.harness.steps.environment.EnvironmentOutcome buildV0EnvironmentOutcome(
      String envOutcomeYaml, Map<String, Object> envOverrideVariables) {
    if (isEmpty(envOutcomeYaml)) {
      return null;
    }
    try {
      return YamlUtils.read(envOutcomeYaml, io.harness.steps.environment.EnvironmentOutcome.class)
          .toBuilder()
          .variables(envOverrideVariables)
          .build();
    } catch (Exception ex) {
      log.warn("Failed to deserialize the NG environment outcome YAML", ex);
      return null;
    }
  }

  private void validateInfraScopeWithService(String serviceRef, String envRef, String envBranchRef, String infraId,
      String accountId, String orgIdentifier, String projectIdentifier,
      ParameterField<Map<String, Object>> infraInputs) {
    if (isNotEmpty(envRef)) {
      IdentifierRef envIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(envRef, accountId, orgIdentifier, projectIdentifier);
      String infraInputsYaml = getInfraInputsYaml(infraInputs);
      UnifiedInfraConvertorResponse infraEntityNgResponse = null;

      if (isNotEmpty(envRef) && isNotEmpty(infraId)) {
        Optional<InfrastructureEntity> infrastructureEntityOp = infrastructureEntityService.get(
            envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
            envIdentifierRef.getProjectIdentifier(), envIdentifierRef.getIdentifier(), infraId);
        if (infrastructureEntityOp.isEmpty()) {
          // Creating context for remote infra
          GitEntityInfo pipelineGitInfo = GitContextHelper.getGitEntityInfo();
          UnifiedGitEntityInfoResponseDTO gitEnvGitInfo =
              getResponse(infrastructureResourceClient.getInfraGitDetails(accountId, orgIdentifier, projectIdentifier,
                  envRef, envBranchRef, pipelineGitInfo.getBranch(), pipelineGitInfo.getParentEntityRepoName()));

          throwIfNgError(gitEnvGitInfo == null ? null : gitEnvGitInfo.getError(),
              String.format("Failed to fetch git details for infrastructure [%s] in environment [%s], in project [%s], "
                      + "in org [%s]",
                  infraId, envRef, projectIdentifier, orgIdentifier));

          infraEntityNgResponse = getResponse(infrastructureResourceClient.convertToUnified(infraId, accountId,
              envIdentifierRef.getOrgIdentifier(), envIdentifierRef.getProjectIdentifier(),
              envIdentifierRef.getIdentifier(), gitEnvGitInfo.getGitEntityInfo().getBranch(),
              gitEnvGitInfo.getGitEntityInfo().getParentEntityRepoName(),
              UnifiedInfraConverterRequestDTO.builder().infraInputsYaml(infraInputsYaml).build()));

          throwIfNgError(infraEntityNgResponse == null ? null : infraEntityNgResponse.getError(),
              String.format("Failed to convert infrastructure [%s] to unified infrastructure in environment [%s], in "
                      + "project [%s], in org [%s]",
                  infraId, envRef, projectIdentifier, orgIdentifier));
          if (infraEntityNgResponse != null && infraEntityNgResponse.getResponseDTO() != null
              && isNotEmpty(infraEntityNgResponse.getResponseDTO().getMergedInfrastructureYaml())) {
            List<String> scopedServiceRefs = infraEntityNgResponse.getResponseDTO().getScopedServiceRefs();
            if (isNotEmpty(scopedServiceRefs) && !scopedServiceRefs.contains(serviceRef)) {
              throw new InvalidRequestException(String.format("Infra Id [%s] provided in stage is scoped to specific "
                      + "services. Provided serviceRef [%s] is not part of them",
                  infraId, serviceRef));
            }
          }
        }
        // Todo: v1 infra handling (when infrastructureEntityOp.isPresent()) is not yet implemented
      }
    }
  }

  private ServiceFetchResult fetchService(Ambiance ambiance, String serviceRef, String accountId, String orgIdentifier,
      String projectIdentifier, Map<String, Object> serviceInputs, String branch, String envRef, String envBranchRef,
      String infraId, Map<String, Object> envOverridesInputs, Map<String, Object> svcOverridesInputs) {
    // Try CI-Manager first
    Optional<ServiceEntity> serviceEntityOp =
        serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceRef);

    if (serviceEntityOp.isPresent()) {
      return fetchFromCIManager(serviceEntityOp.get(), serviceInputs);
    }

    // Fallback to NG-Manager
    return fetchFromNGManager(ambiance, serviceRef, accountId, orgIdentifier, projectIdentifier, serviceInputs, branch,
        envRef, envBranchRef, infraId, envOverridesInputs, svcOverridesInputs);
  }

  private ServiceFetchResult fetchFromCIManager(ServiceEntity serviceEntity, Map<String, Object> serviceInputs) {
    String mergedServiceYaml = serviceEntity.getYaml();

    if (isNotEmpty(serviceInputs)) {
      mergedServiceYaml = getMergedServiceYaml(serviceInputs, serviceEntity);
    }

    ServiceEntityMetadata metadata = getServiceEntityMetadata(Optional.of(serviceEntity), null);

    return ServiceFetchResult.builder().mergedServiceYaml(mergedServiceYaml).serviceEntityMetadata(metadata).build();
  }

  private ServiceFetchResult fetchFromNGManager(Ambiance ambiance, String serviceRef, String accountId,
      String orgIdentifier, String projectIdentifier, Map<String, Object> serviceInputs, String branch, String envRef,
      String envBranchRef, String infraId, Map<String, Object> envOverridesInputs,
      Map<String, Object> svcOverridesInputs) {
    String serviceInputsYaml = getInputsYaml(serviceInputs);
    IdentifierRef serviceIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgIdentifier, projectIdentifier);
    GitBranchInfo gitBranchInfo = prepareGitBranchInfo(branch);

    NgServicePropertiesResponseDTO ngServiceProperties =
        fetchNgServiceProperties(ambiance, serviceRef, accountId, orgIdentifier, projectIdentifier, serviceInputsYaml,
            serviceIdentifierRef, envRef, envBranchRef, infraId, gitBranchInfo, envOverridesInputs, svcOverridesInputs);

    String serviceStepOutcomeYaml = extractV0OutcomeYaml(ngServiceProperties, NGOutcomes.SERVICE.getName());
    String envOutcomeYaml = extractV0OutcomeYaml(ngServiceProperties, NGOutcomes.ENVIRONMENT.getName());

    String resolvedMergedV0ServiceYaml =
        resolveServiceYamlExpressions(ambiance, ngServiceProperties.getMergedV0ServiceYaml());
    String resolvedMergedV0OverrideYaml =
        resolveOverrideYamlExpressions(ambiance, ngServiceProperties.getMergedV0OverrideYaml());

    UnifiedServiceConverterResponse unifiedResponse =
        convertToUnifiedService(serviceIdentifierRef, serviceInputsYaml, resolvedMergedV0ServiceYaml,
            resolvedMergedV0OverrideYaml, gitBranchInfo.getBranch(), gitBranchInfo.getParentEntityRepoName());
    unifiedResponse = convertUnifiedServiceResponse(ambiance, unifiedResponse);

    String finalMergedServiceYaml = extractMergedYamlFromResponse(unifiedResponse);

    validateServiceYaml(finalMergedServiceYaml, serviceRef, projectIdentifier, orgIdentifier);

    ServiceEntityMetadata metadata = getServiceEntityMetadata(Optional.empty(), unifiedResponse);
    saveNgOutcomesSweepingOutput(ambiance, unifiedResponse, serviceStepOutcomeYaml, envOutcomeYaml);

    // Extract override data from response (both POJO and template paths)
    java.util.EnumMap<ServiceOverridesType, String> v0OverridesYamlMap =
        unifiedResponse.getResponseDTO() != null ? unifiedResponse.getResponseDTO().getV0OverridesYamlMap() : null;

    Map<ServiceOverridesType, SingleOverrideConvertorResponseDTO> overridesResponses =
        unifiedResponse.getResponseDTO() != null ? unifiedResponse.getResponseDTO().getOverridesResponses() : null;

    return ServiceFetchResult.builder()
        .mergedServiceYaml(finalMergedServiceYaml)
        .serviceEntityMetadata(metadata)
        .resolvedMergedV0ServiceYaml(unifiedResponse.getResponseDTO().getMergedV0ServiceYaml())
        .v0OverridesYamlMap(v0OverridesYamlMap)
        .overridesResponses(overridesResponses)
        .envOutcomeYaml(envOutcomeYaml)
        .build();
  }

  private NgServicePropertiesResponseDTO fetchNgServiceProperties(Ambiance ambiance, String serviceRef,
      String accountId, String orgIdentifier, String projectIdentifier, String serviceInputsYaml,
      IdentifierRef serviceIdentifierRef, String envRef, String envBranchRef, String infraId,
      GitBranchInfo gitBranchInfo, Map<String, Object> envOverridesInputs, Map<String, Object> svcOverridesInputs) {
    NGEntityFetchRequest ngEntityFetchRequest = buildNgEntityFetchRequest(serviceInputsYaml, serviceIdentifierRef,
        envRef, envBranchRef, infraId, gitBranchInfo, envOverridesInputs, svcOverridesInputs);

    NgServicePropertiesResponse ngServicePropertiesResponse =
        getResponse(ngServiceResourceClient.processNgEntity(accountId, orgIdentifier, projectIdentifier,
            gitBranchInfo.getBranch(), gitBranchInfo.getParentEntityRepoName(), ngEntityFetchRequest));

    throwIfNgError(ngServicePropertiesResponse == null ? null : ngServicePropertiesResponse.getError(),
        String.format("Failed to fetch service entity [%s] in project [%s], in org [%s]", serviceRef, projectIdentifier,
            orgIdentifier));

    validateNgServiceResponse(ngServicePropertiesResponse, serviceRef, projectIdentifier, orgIdentifier);

    NgServicePropertiesResponseDTO responseDTO = ngServicePropertiesResponse.getResponseDTO();
    validateMergedServiceYaml(responseDTO.getMergedV0ServiceYaml(), serviceRef, projectIdentifier, orgIdentifier);

    return convertNgServicePropertiesExpressions(ambiance, responseDTO);
  }

  private NGEntityFetchRequest buildNgEntityFetchRequest(String serviceInputsYaml, IdentifierRef serviceIdentifierRef,
      String envRef, String envBranchRef, String infraId, GitBranchInfo gitBranchInfo,
      Map<String, Object> envOverridesInputs, Map<String, Object> svcOverridesInputs) {
    ServiceFetchRequest serviceFetchRequest = ServiceFetchRequest.builder()
                                                  .serviceInputsYaml(serviceInputsYaml)
                                                  .serviceRef(serviceIdentifierRef.buildScopedIdentifier())
                                                  .build();

    String svcBranch = gitBranchInfo != null ? gitBranchInfo.getBranch() : null;
    String serviceOverridesInputsYaml = getInputsYaml(svcOverridesInputs);
    String envGlobalOverridesInputsYaml = getInputsYaml(envOverridesInputs);

    return NGEntityFetchRequest.builder()
        .serviceFetchRequest(serviceFetchRequest)
        .overrideFetchRequest(OverrideFetchRequest.builder()
                                  .envRef(envRef)
                                  .envBranch(envBranchRef)
                                  .infraId(infraId)
                                  .svcBranch(svcBranch)
                                  .serviceOverridesInputsYaml(serviceOverridesInputsYaml)
                                  .envGlobalOverridesInputsYaml(envGlobalOverridesInputsYaml)
                                  .build())
        .build();
  }

  private void validateNgServiceResponse(
      NgServicePropertiesResponse response, String serviceRef, String projectIdentifier, String orgIdentifier) {
    if (response == null || response.getResponseDTO() == null) {
      throw new InvalidRequestException(
          String.format("No service entity found with identifier [%s] in project [%s], in org [%s]", serviceRef,
              projectIdentifier, orgIdentifier));
    }
  }

  private void validateMergedServiceYaml(
      String mergedServiceYaml, String serviceRef, String projectIdentifier, String orgIdentifier) {
    if (isEmpty(mergedServiceYaml)) {
      throw new InvalidRequestException(
          String.format("No service entity found with identifier [%s] in project [%s], in org [%s]", serviceRef,
              projectIdentifier, orgIdentifier));
    }
  }

  private void validateServiceYaml(
      String serviceYaml, String serviceRef, String projectIdentifier, String orgIdentifier) {
    if (isEmpty(serviceYaml)) {
      throw new InvalidRequestException(
          String.format("No service entity found with identifier [%s] in project [%s], in org [%s]", serviceRef,
              projectIdentifier, orgIdentifier));
    }
  }

  private String extractV0OutcomeYaml(NgServicePropertiesResponseDTO ngServiceProperties, String outcomeName) {
    if (isEmpty(ngServiceProperties.getNgOutcomes())) {
      return "";
    }
    return ngServiceProperties.getNgOutcomes().get(outcomeName);
  }

  private String resolveServiceYamlExpressions(Ambiance ambiance, String mergedServiceYaml) {
    return cdStepsExpressionResolver.renderValue(ambiance, mergedServiceYaml, true);
  }

  private String resolveOverrideYamlExpressions(Ambiance ambiance, String mergedOverrideYaml) {
    if (isEmpty(mergedOverrideYaml)) {
      return null;
    }
    return cdStepsExpressionResolver.renderValue(ambiance, mergedOverrideYaml, true);
  }

  private GitBranchInfo prepareGitBranchInfo(String branch) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (isEmpty(branch)) {
      return GitBranchInfo.builder()
          .branch(gitEntityInfo.getBranch())
          .parentEntityRepoName(gitEntityInfo.getParentEntityRepoName())
          .build();
    }
    return GitBranchInfo.builder().branch(branch).parentEntityRepoName(null).build();
  }

  private UnifiedServiceConverterResponse convertToUnifiedService(IdentifierRef serviceIdentifierRef,
      String serviceInputsYaml, String resolvedMergedV0ServiceYaml, String resolvedMergedV0OverrideYaml, String branch,
      String parentEntityRepoName) {
    UnifiedServiceConverterRequestDTO unifiedServiceConverterRequestDTO =
        UnifiedServiceConverterRequestDTO.builder()
            .serviceInputsYaml(serviceInputsYaml)
            .mergedV0ServiceYaml(resolvedMergedV0ServiceYaml)
            .mergedV0OverridesYaml(resolvedMergedV0OverrideYaml)
            .build();

    UnifiedServiceConverterResponse response = getResponse(ngServiceResourceClient.convertToUnified(
        serviceIdentifierRef.getIdentifier(), serviceIdentifierRef.getAccountIdentifier(),
        serviceIdentifierRef.getOrgIdentifier(), serviceIdentifierRef.getProjectIdentifier(), branch,
        parentEntityRepoName, unifiedServiceConverterRequestDTO));

    throwIfNgError(response == null ? null : response.getError(),
        String.format("Failed to convert service [%s] to unified service in project [%s], in org [%s]",
            serviceIdentifierRef.getIdentifier(), serviceIdentifierRef.getProjectIdentifier(),
            serviceIdentifierRef.getOrgIdentifier()));

    return response;
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

  private void saveNgOutcomesSweepingOutput(Ambiance ambiance, UnifiedServiceConverterResponse response,
      String serviceStepOutcomeYaml, String envOutcomeYaml) {
    UnifiedServiceConverterResponseDTO responseDTO = response.getResponseDTO();
    VariablesSweepingOutput ngOutcomesSweepingOutput = new VariablesSweepingOutput();
    ngOutcomesSweepingOutput.put(NGOutcomes.SERVICE.getName(), serviceStepOutcomeYaml);
    ngOutcomesSweepingOutput.put(NGOutcomes.ENVIRONMENT.getName(), envOutcomeYaml);

    if (responseDTO != null && isNotEmpty(responseDTO.getNgOutcomes())) {
      ngOutcomesSweepingOutput.putAll(responseDTO.getNgOutcomes());
    }

    sweepingOutputService.consumeUpsert(ambiance, NG_OUTCOMES, ngOutcomesSweepingOutput, StepCategory.STAGE.name());
  }

  private String extractMergedYamlFromResponse(UnifiedServiceConverterResponse response) {
    return Optional.ofNullable(response)
        .map(UnifiedServiceConverterResponse::getResponseDTO)
        .map(UnifiedServiceConverterResponseDTO::getMergedServiceYaml)
        .filter(StringUtils::isNotEmpty)
        .orElse(null);
  }

  private ServiceConfig toServiceConfig(String mergedServiceYaml) {
    if (isEmpty(mergedServiceYaml)) {
      throw new InvalidRequestException("Merged service YAML cannot be empty");
    }
    return UnifiedServiceEntityMapper.toServiceConfig(mergedServiceYaml);
  }

  @VisibleForTesting
  String handlePrimaryArtifact(Ambiance ambiance, ServiceConfig serviceConfig) {
    if (!ServiceStepUtility.isArtifactPresent(serviceConfig)) {
      return null;
    }

    ArtifactWrapper artifacts = Optional.ofNullable(serviceConfig)
                                    .map(ServiceConfig::getServiceInfoConfig)
                                    .map(serviceInfoConfig -> serviceInfoConfig.getWith())
                                    .map(ServiceSpec::getArtifacts)
                                    .orElse(null);

    if (artifacts == null || !ServiceStepUtility.isOnlyPrimaryArtifactIdGiven(artifacts)) {
      return null;
    }

    String resolvedPrimaryArtifactId = resolvePrimaryArtifactRef(ambiance, artifacts);
    if (resolvedPrimaryArtifactId == null) {
      return null;
    }

    updatePrimaryArtifactInServiceConfig(artifacts, resolvedPrimaryArtifactId);
    restoreArtifactsOutcomePrimary(ambiance, resolvedPrimaryArtifactId);
    return resolvedPrimaryArtifactId;
  }

  /**
   * Resolves the primary artifact reference. A fixed reference (or id-only reference) is used as is, an expression
   * based one, for example <+serviceVariables.artifactRef>, is rendered against the ambiance and reduced to its id.
   */
  private String resolvePrimaryArtifactRef(Ambiance ambiance, ArtifactWrapper artifacts) {
    ParameterField<?> primaryField = artifacts.getPrimary();
    Object primaryValue = primaryField.obtainValue();
    if (primaryValue instanceof ArtifactConfig artifactConfig) {
      return artifactConfig.getId();
    }

    String primaryArtifactId = primaryValue instanceof String stringValue ? stringValue : null;
    if (isEmpty(primaryArtifactId)) {
      if (!primaryField.isExpression()) {
        return null;
      }
      if (NGExpressionUtils.isRuntimeField(primaryField.getExpressionValue())) {
        List<String> primarySourceIds = primaryEligibleArtifactIds(artifacts);
        if (primarySourceIds.size() > 1) {
          throw new InvalidRequestException(unresolvedRuntimeInputRefMessage(
              "primaryArtifactRef", primarySourceIds, "artifact", primaryField.getExpressionValue()));
        }
        // Unfilled runtime input (<+input>) with a single non sidecar source: nothing to disambiguate here, that
        // source is picked downstream exactly as before.
        return null;
      }
      primaryArtifactId = cdStepsExpressionResolver.renderValue(ambiance, primaryField.getExpressionValue(), true);
    }

    primaryArtifactId = primaryArtifactId == null ? null : primaryArtifactId.trim();
    if (isEmpty(primaryArtifactId) || EngineExpressionEvaluator.hasExpressions(primaryArtifactId)
        || EngineExpressionEvaluator.hasCelExpressions(primaryArtifactId)) {
      throw new InvalidRequestException(
          String.format("Unable to resolve primaryArtifactRef. Please check the expression %s",
              primaryField.isExpression() ? primaryField.getExpressionValue() : StringUtils.EMPTY));
    }

    return primaryArtifactId;
  }

  private static List<String> primaryEligibleArtifactIds(ArtifactWrapper artifacts) {
    if (isEmpty(artifacts.getSources())) {
      return Collections.emptyList();
    }
    return artifacts.getSources()
        .stream()
        .filter(source -> !source.isSidecar())
        .map(ArtifactConfig::getId)
        .collect(Collectors.toList());
  }

  /**
   * Restores {@code ngOutcomes.artifacts.primary} once the primary artifact ref is resolved: reads the candidate
   * outcome NG emitted for the resolved id under {@code artifactSourceCandidates}, rebuilds the {@code artifacts}
   * outcome with it as primary (keeping the existing sidecars), and removes the now-consumed candidates key.
   * No-op when NG did not emit any candidates (e.g. the fixed-ref flow).
   */
  private void restoreArtifactsOutcomePrimary(Ambiance ambiance, String resolvedPrimaryArtifactId) {
    OptionalSweepingOutput ngOutcomesOutput =
        sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES));
    if (ngOutcomesOutput == null || !ngOutcomesOutput.isFound()) {
      return;
    }
    VariablesSweepingOutput ngOutcomesSweepingOutput = (VariablesSweepingOutput) ngOutcomesOutput.getOutput();
    Object artifactSourceCandidatesYamlObj =
        ngOutcomesSweepingOutput.get(NGOutcomes.ARTIFACT_SOURCE_CANDIDATES.getName());
    if (!(artifactSourceCandidatesYamlObj instanceof String artifactSourceCandidatesYaml)
        || isEmpty(artifactSourceCandidatesYaml)) {
      return;
    }

    ArtifactSourceCandidatesOutcome candidates;
    ArtifactsOutcome existingArtifactsOutcome = null;
    try {
      candidates = YamlUtils.read(artifactSourceCandidatesYaml, ArtifactSourceCandidatesOutcome.class);
      Object existingArtifactsYamlObj = ngOutcomesSweepingOutput.get(NGOutcomes.ARTIFACTS.getName());
      if (existingArtifactsYamlObj instanceof String existingArtifactsYaml && isNotEmpty(existingArtifactsYaml)) {
        existingArtifactsOutcome = YamlUtils.read(existingArtifactsYaml, ArtifactsOutcome.class);
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Failed to read artifact source candidates outcome", e);
    }

    ArtifactOutcome primaryOutcome = candidates == null ? null : candidates.get(resolvedPrimaryArtifactId);
    if (primaryOutcome == null) {
      throw new InvalidRequestException(String.format(
          "Unable to resolve primaryArtifactRef. Please check the expression %s", resolvedPrimaryArtifactId));
    }

    ArtifactsOutcome rebuiltArtifactsOutcome =
        ArtifactsOutcome.builder()
            .primary(primaryOutcome)
            .sidecars(existingArtifactsOutcome == null ? null : existingArtifactsOutcome.getSidecars())
            .build();

    ngOutcomesSweepingOutput.put(NGOutcomes.ARTIFACTS.getName(), YamlUtils.writeYamlString(rebuiltArtifactsOutcome));
    ngOutcomesSweepingOutput.remove(NGOutcomes.ARTIFACT_SOURCE_CANDIDATES.getName());
    sweepingOutputService.consumeUpsert(ambiance, NG_OUTCOMES, ngOutcomesSweepingOutput, StepCategory.STAGE.name());
  }

  @VisibleForTesting
  String handlePrimaryManifest(Ambiance ambiance, ServiceConfig serviceConfig) {
    if (!ServiceStepUtility.isManifestPresent(serviceConfig)) {
      return null;
    }

    ManifestWrapper manifests = Optional.ofNullable(serviceConfig)
                                    .map(ServiceConfig::getServiceInfoConfig)
                                    .map(ServiceInfoConfig::getWith)
                                    .map(ServiceSpec::getManifests)
                                    .orElse(null);

    if (manifests == null || !ParameterField.isNotNull(manifests.getPrimary())) {
      return null;
    }

    ManifestConfig primary = resolvePrimaryManifestRef(ambiance, manifests);
    if (primary == null) {
      return null;
    }

    ManifestConfig resolvedPrimary = updatePrimaryManifestInServiceConfig(manifests, primary);
    manifests.setPrimary(ParameterField.createValueField(resolvedPrimary));
    restoreManifestsOutcomePrimary(ambiance, resolvedPrimary.getId());
    return resolvedPrimary.getId();
  }

  /**
   * Resolves the primary manifest reference. A fixed reference is used as is, an expression based one, for example
   * <+serviceVariables.manifestRef>, is rendered against the ambiance and converted into a manifest config carrying
   * only the referenced manifest id.
   */
  private ManifestConfig resolvePrimaryManifestRef(Ambiance ambiance, ManifestWrapper manifests) {
    ParameterField<?> primaryField = manifests.getPrimary();
    Object primaryValue = primaryField.obtainValue();
    if (primaryValue instanceof ManifestConfig manifestConfig) {
      return manifestConfig;
    }

    String primaryManifestId = primaryValue instanceof String stringValue ? stringValue : null;
    if (isEmpty(primaryManifestId)) {
      if (!primaryField.isExpression()) {
        return null;
      }
      if (NGExpressionUtils.isRuntimeField(primaryField.getExpressionValue())) {
        throw new InvalidRequestException(unresolvedRuntimeInputRefMessage("primaryManifestRef",
            primaryEligibleManifestIds(manifests), "manifest", primaryField.getExpressionValue()));
      }
      primaryManifestId = cdStepsExpressionResolver.renderValue(ambiance, primaryField.getExpressionValue(), true);
    }

    primaryManifestId = primaryManifestId == null ? null : primaryManifestId.trim();
    if (isEmpty(primaryManifestId) || EngineExpressionEvaluator.hasExpressions(primaryManifestId)
        || EngineExpressionEvaluator.hasCelExpressions(primaryManifestId)) {
      throw new InvalidRequestException(
          String.format("Unable to resolve primaryManifestRef. Please check the expression %s",
              primaryField.isExpression() ? primaryField.getExpressionValue() : StringUtils.EMPTY));
    }

    return ManifestConfig.builder().id(primaryManifestId).build();
  }

  private static List<String> primaryEligibleManifestIds(ManifestWrapper manifests) {
    if (isEmpty(manifests.getSources())) {
      return Collections.emptyList();
    }
    return manifests.getSources()
        .stream()
        .filter(manifest -> ManifestType.getPrimarySupportedManifestTypes().contains(manifest.getUses()))
        .map(ManifestConfig::getId)
        .collect(Collectors.toList());
  }

  /**
   * Common message for an unfilled {@code <+input>} reference that cannot pick a winner on its own. Failing here, with
   * the candidate ids spelled out, is far more actionable than the generic "only one primary should be provided"
   * error the run would otherwise hit much later while building the outcomes.
   */
  private static String unresolvedRuntimeInputRefMessage(
      String refName, List<String> candidateIds, String entityName, String expressionValue) {
    return String.format(
        "%s was left as a runtime input (%s) and could not be resolved, but the service has multiple %s sources [%s]. "
            + "Provide a value for %s at runtime, or pin it in the service/pipeline yaml, so that exactly one primary "
            + "%s can be selected.",
        refName, expressionValue, entityName, String.join(", ", candidateIds), refName, entityName);
  }

  /**
   * Restores {@code ngOutcomes.manifests.primary} once the primary manifest ref is resolved: reads the candidate
   * outcomes NG emitted under {@code manifestSourceCandidates}, promotes the resolved id to {@code primary}, drops the
   * losing manifests of the same type (static-ref parity, see PrimaryManifestFilterUtils on the NG side) and removes
   * the now-consumed candidates key. No-op when NG did not emit any candidates (e.g. the fixed-ref flow).
   *
   * <p>The candidates payload is read as a plain map rather than deserialized into the typed
   * {@code ManifestSourceCandidatesOutcome}: CI does not depend on the cd-nextgen manifest outcome hierarchy, and the
   * only thing needed here is to move nodes between two maps.
   */
  private void restoreManifestsOutcomePrimary(Ambiance ambiance, String resolvedPrimaryManifestId) {
    if (isEmpty(resolvedPrimaryManifestId)) {
      return;
    }
    OptionalSweepingOutput ngOutcomesOutput =
        sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES));
    if (ngOutcomesOutput == null || !ngOutcomesOutput.isFound()) {
      return;
    }
    VariablesSweepingOutput ngOutcomesSweepingOutput = (VariablesSweepingOutput) ngOutcomesOutput.getOutput();
    Object candidatesYamlObj = ngOutcomesSweepingOutput.get(NGOutcomes.MANIFEST_SOURCE_CANDIDATES.getName());
    if (!(candidatesYamlObj instanceof String candidatesYaml) || isEmpty(candidatesYaml)) {
      return;
    }

    Map<String, Object> candidates = YamlParsingUtils.parseYamlStringToMap(candidatesYaml);
    if (isEmpty(candidates) || !(candidates.get(resolvedPrimaryManifestId) instanceof Map)) {
      String availableManifestIds = isEmpty(candidates) ? StringUtils.EMPTY : String.join(", ", candidates.keySet());
      throw new InvalidRequestException(
          String.format("primaryManifestRef resolved to [%s] which does not match any manifest in the service. "
                  + "Available manifests: [%s]",
              resolvedPrimaryManifestId, availableManifestIds));
    }

    Object primaryManifest = candidates.get(resolvedPrimaryManifestId);
    Object existingManifestsYamlObj = ngOutcomesSweepingOutput.get(NGOutcomes.MANIFESTS.getName());
    Map<String, Object> manifestsOutcomeMap =
        existingManifestsYamlObj instanceof String existingManifestsYaml && isNotEmpty(existingManifestsYaml)
        ? new LinkedHashMap<>(YamlParsingUtils.parseYamlStringToMap(existingManifestsYaml))
        : new LinkedHashMap<>(candidates);

    String primaryType = getManifestOutcomeType(primaryManifest);
    manifestsOutcomeMap.entrySet().removeIf(entry
        -> !PRIMARY.equals(entry.getKey()) && !resolvedPrimaryManifestId.equals(entry.getKey()) && primaryType != null
            && primaryType.equals(getManifestOutcomeType(entry.getValue())));
    manifestsOutcomeMap.put(PRIMARY, primaryManifest);

    ngOutcomesSweepingOutput.put(NGOutcomes.MANIFESTS.getName(), YamlUtils.writeYamlString(manifestsOutcomeMap));
    ngOutcomesSweepingOutput.remove(NGOutcomes.MANIFEST_SOURCE_CANDIDATES.getName());
    sweepingOutputService.consumeUpsert(ambiance, NG_OUTCOMES, ngOutcomesSweepingOutput, StepCategory.STAGE.name());
  }

  private static String getManifestOutcomeType(Object manifestOutcome) {
    if (manifestOutcome instanceof Map<?, ?> manifestOutcomeMap
        && manifestOutcomeMap.get(TYPE) instanceof String type) {
      return type;
    }
    return null;
  }

  private static ManifestConfig updatePrimaryManifestInServiceConfig(
      ManifestWrapper manifests, ManifestConfig primary) {
    List<ManifestConfig> manifestSources = manifests.getSources();

    if (isEmpty(manifestSources)) {
      manifestSources = new ArrayList<>();
      // case where only primary manifest is present in service yaml
      if (isNotEmpty(primary.getInputs())) {
        throw new InvalidYamlException(
            String.format("no primary manifest config found in service definition with id: [%s], Please provide list "
                    + "of manifest sources to pick primary manifest from or provide primary manifest config itself",
                primary.getId()));
      }
      manifestSources.add(primary);
      return primary;
    } else {
      // case when primary manifest id or config is present and manifest config exist in sources array
      ManifestConfig primaryConfig;
      if (isNotEmpty(primary.getInputs())) {
        primaryConfig = primary;
      } else {
        primaryConfig = manifestSources.stream()
                            .filter(manifest -> manifest.getId().equals(primary.getId()))
                            .findFirst()
                            .orElseThrow(()
                                             -> new InvalidRequestException(String.format(
                                                 "primaryManifestRef: %s does not match to any [%s] manifests",
                                                 primary.getId(), getPrimarySupportedManifestTypeNames())));
      }

      if (!ManifestType.getPrimarySupportedManifestTypes().contains(primaryConfig.getUses())) {
        throw new InvalidRequestException(
            "primary manifest is not supported for manifest type: " + primaryConfig.getUses().getDisplayName());
      }
      List<ManifestConfig> filteredManifests =
          manifestSources.stream().filter(shouldKeepManifest()).collect(Collectors.toList());
      filteredManifests.add(primaryConfig);

      manifests.setSources(filteredManifests);
      return primaryConfig;
    }
  }

  private static String getPrimarySupportedManifestTypeNames() {
    return ManifestType.getPrimarySupportedManifestTypes()
        .stream()
        .map(ManifestType::getDisplayName)
        .collect(Collectors.joining(", "));
  }

  /**
   * Returns the id of the configured primary manifest, or null when no primary manifest ref is set.
   */
  private static String getConfiguredPrimaryManifestId(ManifestWrapper manifests) {
    if (manifests == null || !ParameterField.isNotNull(manifests.getPrimary())) {
      return null;
    }
    ParameterField<?> primaryField = manifests.getPrimary();
    Object primaryValue = primaryField.obtainValue();
    if (primaryValue instanceof ManifestConfig manifestConfig) {
      return manifestConfig.getId();
    }
    return primaryValue instanceof String stringValue ? stringValue : null;
  }

  private static Predicate<ManifestConfig> shouldKeepManifest() {
    return manifest
        ->
        // Keep if not a primary supported type OR if it's the primary manifest
        !ManifestType.getPrimarySupportedManifestTypes().contains(manifest.getUses());
  }

  /**
   * Derive OverridesWrapperDTO from overridesResponses.
   * Uses the converted responses from NG Manager directly.
   */
  private Map<ServiceOverridesType, OverridesWrapperDTO> deriveOverridesFromResponses(
      Map<ServiceOverridesType, SingleOverrideConvertorResponseDTO> overridesResponses) {
    if (isEmpty(overridesResponses)) {
      return Collections.emptyMap();
    }

    Map<ServiceOverridesType, OverridesWrapperDTO> result = new HashMap<>();

    for (Map.Entry<ServiceOverridesType, SingleOverrideConvertorResponseDTO> entry : overridesResponses.entrySet()) {
      ServiceOverridesType type = entry.getKey();
      SingleOverrideConvertorResponseDTO response = entry.getValue();

      // Parse merged YAML to OverridesConfig (already template-converted by NG Manager)
      OverridesConfig config = null;
      try {
        config = YamlUtils.read(response.getMergedYaml(), OverridesConfig.class);
      } catch (IOException e) {
        throw new InvalidYamlException(
            String.format("Override yaml is not compatible with stage for override : [%s], error: [%s]",
                response.getIdentifier(), e.getMessage()));
      }

      OverridesWrapperDTO wrapper = OverridesWrapperDTO.builder()
                                        .identifier(response.getIdentifier())
                                        .type(type)
                                        .environmentRef(response.getEnvironmentRef())
                                        .config(config)
                                        .build();

      result.put(type, wrapper);
    }

    return result;
  }

  private static ServiceEntityMetadata getServiceEntityMetadata(
      Optional<ServiceEntity> serviceEntityOp, UnifiedServiceConverterResponse serviceEntityNGResponse) {
    ServiceEntityMetadataBuilder builder = ServiceEntityMetadata.builder();
    if (serviceEntityOp.isPresent()) {
      ServiceEntity entity = serviceEntityOp.get();
      builder.identifier(entity.getIdentifier())
          .name(entity.getName())
          .tags(convertToMap(entity.getTags()))
          .description(entity.getDescription());
    } else if (serviceEntityNGResponse != null && serviceEntityNGResponse.getResponseDTO() != null) {
      UnifiedServiceConverterResponseDTO serviceNGResponseDTO = serviceEntityNGResponse.getResponseDTO();
      builder.identifier(serviceNGResponseDTO.getIdentifier())
          .name(serviceNGResponseDTO.getName())
          .tags(serviceNGResponseDTO.getTags())
          .description(serviceNGResponseDTO.getDescription());
    }
    return builder.build();
  }

  private String getMergedServiceYaml(Map<String, Object> serviceInputsWrapper, ServiceEntity serviceEntity) {
    String mergedServiceYaml = serviceEntity.getYaml();
    if (isNotEmpty(serviceInputsWrapper)) {
      mergedServiceYaml = mergeKeyValueInputsToServiceYaml(serviceInputsWrapper, serviceEntity);
      if (serviceInputsWrapper.get(OVERLAY) != null && serviceInputsWrapper.get(OVERLAY) instanceof Map) {
        Map<String, Object> serviceOverlayInputs = (Map<String, Object>) serviceInputsWrapper.get(OVERLAY);
        mergedServiceYaml = mergeServiceOverlay(serviceOverlayInputs, mergedServiceYaml);
      }
    }

    return mergedServiceYaml;
  }

  private static String mergeServiceOverlay(Map<String, Object> serviceRuntimeInputs, String originalServiceYaml) {
    String finalServiceYaml = updateServiceYamlWitPrimaryArtifact(serviceRuntimeInputs, originalServiceYaml);
    finalServiceYaml = updateServiceYamlWithPrimaryManifest(serviceRuntimeInputs, finalServiceYaml);

    // serviceRuntimeInputs starts with manifests/artifacts node, to make original yaml and input yaml follow same yaml
    // hierarchy we need to add with and service node
    Map<String, Object> serviceWithJsonInputsMap = new HashMap<>();
    serviceWithJsonInputsMap.put("with", serviceRuntimeInputs);

    Map<String, Object> serviceJsonInputsMap = new HashMap<>();
    serviceJsonInputsMap.put(SERVICE, serviceWithJsonInputsMap);

    finalServiceYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        finalServiceYaml, YamlPipelineUtils.writeYamlString(serviceJsonInputsMap), true, true, false);
    return finalServiceYaml;
  }

  /**
   * Updates the service YAML by setting a specific artifact as the primary artifact based on runtime inputs.
   * This method looks for a primary artifact specification in the runtime inputs and if found, it updates
   * the service YAML to use that artifact as the primary artifact. The primary artifact must exist in the
   * sources list of the original service YAML.
   *
   * Example runtime input:
   * <pre>
   * artifacts:
   *   primary:
   *     id: docker1
   * </pre>
   *
   * @param serviceInputs Runtime inputs containing the primary artifact specification
   * @param originalServiceYaml Original service YAML content where primary artifact needs to be updated
   * @return Updated service YAML with the specified primary artifact
   * @throws InvalidYamlException if the specified primary artifact ID is not found in the sources
   */
  private static String updateServiceYamlWitPrimaryArtifact(
      Map<String, Object> serviceInputs, String originalServiceYaml) {
    String finalServiceYaml = originalServiceYaml;
    if (serviceInputs.containsKey(ARTIFACTS) && serviceInputs.get(ARTIFACTS) instanceof Map) {
      Map<String, Object> artifactsInputs = (HashMap<String, Object>) serviceInputs.get(ARTIFACTS);
      if (artifactsInputs.containsKey(PRIMARY) && artifactsInputs.get(PRIMARY) instanceof Map) {
        Map<String, Object> primaryArtifactsInputs = (HashMap<String, Object>) artifactsInputs.get(PRIMARY);

        if (primaryArtifactsInputs.containsKey(ID)) {
          String primaryArtifactId = (String) primaryArtifactsInputs.get(ID);
          ServiceConfig originalServiceConfig = UnifiedServiceEntityMapper.toServiceConfig(finalServiceYaml);
          if (ParameterField.isNotNull(
                  originalServiceConfig.getServiceInfoConfig().getWith().getArtifacts().getPrimary())
              && originalServiceConfig.getServiceInfoConfig().getWith().getArtifacts().getPrimary().isExpression()) {
            List<ArtifactConfig> artifactSources =
                originalServiceConfig.getServiceInfoConfig().getWith().getArtifacts().getSources();
            Optional<ArtifactConfig> primaryArtifactOp =
                artifactSources.stream().filter(artifact -> artifact.getId().equals(primaryArtifactId)).findAny();
            if (primaryArtifactOp.isEmpty()) {
              throw new InvalidYamlException("Could not find primary artifact with id: " + primaryArtifactId);
            }

            ArtifactConfig primaryArtifact = primaryArtifactOp.get();
            originalServiceConfig.getServiceInfoConfig().getWith().getArtifacts().setPrimary(
                ParameterField.createValueField(primaryArtifact));

            finalServiceYaml = YamlUtils.writeYamlString(originalServiceConfig);
          }
        }
      }
    }
    return finalServiceYaml;
  }

  /**
   * Updates the service YAML by setting a specific manifest as the primary manifest based on runtime inputs.
   * This method looks for a primary manifest specification in the runtime inputs and if found, it updates
   * the service YAML to use that manifest as the primary manifest. The primary manifest must exist in the
   * sources list of the original service YAML.
   * This is needed to align with overlay inputs structure, where we accept primary manifest input as object
   *
   * @param serviceInputs Runtime inputs containing the primary manifest specification
   * @param originalServiceYaml Original service YAML content where primary manifest needs to be updated
   * @return Updated service YAML with the specified primary manifest
   * @throws InvalidYamlException if the specified primary manifest ID is not found in the sources
   */
  private static String updateServiceYamlWithPrimaryManifest(
      Map<String, Object> serviceInputs, String originalServiceYaml) {
    String finalServiceYaml = originalServiceYaml;
    if (serviceInputs.containsKey(MANIFESTS) && serviceInputs.get(MANIFESTS) instanceof Map) {
      Map<String, Object> manifestsInputs = (HashMap<String, Object>) serviceInputs.get(MANIFESTS);
      if (manifestsInputs.containsKey(PRIMARY) && manifestsInputs.get(PRIMARY) instanceof Map) {
        Map<String, Object> primaryManifestsInputs = (HashMap<String, Object>) manifestsInputs.get(PRIMARY);

        if (primaryManifestsInputs.containsKey(ID)) {
          String primaryManifestId = (String) primaryManifestsInputs.get(ID);
          ServiceConfig originalServiceConfig = UnifiedServiceEntityMapper.toServiceConfig(finalServiceYaml);
          if (ParameterField.isNotNull(
                  originalServiceConfig.getServiceInfoConfig().getWith().getManifests().getPrimary())
              && originalServiceConfig.getServiceInfoConfig().getWith().getManifests().getPrimary().isExpression()) {
            List<ManifestConfig> manifestSources =
                originalServiceConfig.getServiceInfoConfig().getWith().getManifests().getSources();
            Optional<ManifestConfig> primaryManifestOp =
                manifestSources.stream().filter(manifest -> manifest.getId().equals(primaryManifestId)).findAny();
            if (primaryManifestOp.isEmpty()) {
              throw new InvalidYamlException("Could not find primary manifest with id: " + primaryManifestId);
            }

            ManifestConfig primaryManifest = primaryManifestOp.get();
            originalServiceConfig.getServiceInfoConfig().getWith().getManifests().setPrimary(
                ParameterField.createValueField(primaryManifest));

            finalServiceYaml = YamlUtils.writeYamlString(originalServiceConfig);
          }
        }
      }
    }
    return finalServiceYaml;
  }

  private String mergeKeyValueInputsToServiceYaml(Map<String, Object> inputs, ServiceEntity serviceEntity) {
    JsonNode inputsJsonNode = CdStepsInputsMergeUtility.parseInputsMapToJsonNode(inputs);
    String serviceYaml = mergeUserInputsToServiceEntityInputs(serviceEntity.getYaml(), inputsJsonNode);

    return InputSetMergeHelperV1.mergeInputSetIntoEntityYaml(inputsJsonNode, serviceYaml, connectorInputsMapper,
        serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(),
        YAMLFieldNameConstants.SERVICE);
  }

  private String mergeUserInputsToServiceEntityInputs(String serviceYaml, JsonNode inputsJsonNode) {
    String updatedServiceYaml = serviceYaml;
    try {
      JsonNode inputInputsJsonNode = inputsJsonNode.get(INPUTS);

      JsonNode serviceEntityJsonNode = YamlUtils.readAsJsonNode(serviceYaml);
      ObjectNode serviceEntityInputs = (ObjectNode) serviceEntityJsonNode.get(SERVICE).get(INPUTS);

      if (serviceEntityInputs != null) {
        serviceEntityInputs.fieldNames().forEachRemaining(fieldName -> {
          if (inputInputsJsonNode.has(fieldName)) {
            JsonNode serviceField = serviceEntityInputs.get(fieldName);
            ((ObjectNode) serviceField).put(VALUE, inputInputsJsonNode.get(fieldName));
          }
        });
        updatedServiceYaml = YamlUtils.writeYamlString(
            new ObjectNode(JsonNodeFactory.instance).set(SERVICE, serviceEntityJsonNode.get(SERVICE)));
      }
    } catch (Exception ex) {
      throw new InvalidYamlException("Could not merge inputs to service yaml, Please check inputs format provided", ex);
    }
    return updatedServiceYaml;
  }

  /**
   * Saves service config sweeping output and returns a map of entity-type -> entity map for parent access.
   * Keys: {@link ProcessedServiceResult#ARTIFACTS_KEY}, {@link ProcessedServiceResult#MANIFESTS_KEY},
   * {@link ProcessedServiceResult#CONFIG_FILES_KEY}, and any future entity.
   */
  private Map<String, Map<String, Object>> saveServiceOutput(
      Ambiance ambiance, ServiceConfig serviceConfig, String resolvedV0ServiceYaml) {
    Map<String, Object> specMap = null;
    if (isNotEmpty(resolvedV0ServiceYaml)) {
      specMap = ngServiceYamlHelper.buildNgServiceYamlSpecMap(resolvedV0ServiceYaml);
      if (isNotEmpty(specMap)) {
        NgServiceYamlOutcome ngServiceYamlOutcome = NgServiceYamlOutcome.builder().spec(specMap).build();
        serviceStepSweepingOutputHelper.saveNgServiceYamlSweepingOutput(ambiance, ngServiceYamlOutcome);
      }
    }

    // These maps are v1 templatised map, where each artifact/manifests contains inputs,
    // so that user can use expression like <+serviceOutput.artifacts/manifests.inputs.connector>
    Map<String, Object> manifestsMap = processManifests(ambiance, serviceConfig);
    Map<String, Object> artifactMap = processArtifacts(ambiance, serviceConfig);
    Map<String, Object> configFilesMap = processConfigFiles(ambiance, serviceConfig);
    Map<String, Object> startupScriptMap = processStartupScript(ambiance, serviceConfig);
    Map<String, Object> hooksMap = processHooks(serviceConfig);
    Map<String, Object> pluginInfoMap = processServicePluginInfo(specMap);

    ServiceConfigOutcome serviceConfigOutcome = ServiceConfigOutcome.builder()
                                                    .manifests(manifestsMap)
                                                    .artifacts(artifactMap)
                                                    .configFiles(configFilesMap)
                                                    .startupScript(startupScriptMap)
                                                    .hooks(hooksMap)
                                                    .pluginInfo(pluginInfoMap)
                                                    .build();
    serviceStepSweepingOutputHelper.saveServiceConfigSweepingOutput(ambiance, serviceConfigOutcome);

    Map<String, Map<String, Object>> serviceOutputMap = new HashMap<>();
    serviceOutputMap.put(ProcessedServiceResult.MANIFESTS_KEY, manifestsMap != null ? manifestsMap : new HashMap<>());
    serviceOutputMap.put(ProcessedServiceResult.ARTIFACTS_KEY, artifactMap != null ? artifactMap : new HashMap<>());
    serviceOutputMap.put(
        ProcessedServiceResult.CONFIG_FILES_KEY, configFilesMap != null ? configFilesMap : new HashMap<>());
    return serviceOutputMap;
  }

  /**
   * Extracts pluginInfo from the resolved v0 service spec map and exposes it under the serviceOutput root, so that
   * templates can reference values like {@code <+serviceOutput.pluginInfo.runtimeLanguage>}.
   *
   * <p>Purely additive: returns an empty map when the service does not define pluginInfo (or the spec map is
   * unavailable), so services that do not use pluginInfo are unaffected.
   */
  @VisibleForTesting
  Map<String, Object> processServicePluginInfo(Map<String, Object> specMap) {
    Map<String, Object> pluginInfoMap = new HashMap<>();
    if (isEmpty(specMap)) {
      return pluginInfoMap;
    }
    Object pluginInfo = specMap.get(PLUGIN_INFO);
    if (pluginInfo instanceof Map) {
      @SuppressWarnings("unchecked") Map<String, Object> pluginInfoValues = (Map<String, Object>) pluginInfo;
      pluginInfoMap.putAll(pluginInfoValues);
    }
    return pluginInfoMap;
  }

  /**
   * Saves service overrides YAML as sweeping output for template expression resolution.
   * Processes each override type's YAML and normalizes manifests/configFiles by identifier.
   * Expressions: ngServiceOverrides.envGlobalOverride.manifests.ID_PLACEHOLDER.manifest.spec.store.spec.branch
   */
  private void saveServiceOverridesYamlOutput(
      Ambiance ambiance, java.util.EnumMap<ServiceOverridesType, String> v0OverridesYamlMap) {
    Map<String, Map<String, Object>> overridesMap = new HashMap<>();

    for (Map.Entry<ServiceOverridesType, String> entry : v0OverridesYamlMap.entrySet()) {
      ServiceOverridesType overrideType = entry.getKey();
      String overrideYaml = entry.getValue();

      if (isEmpty(overrideYaml)) {
        continue;
      }

      // Build normalized spec map for this override
      Map<String, Object> overrideSpec = ngServiceYamlHelper.buildOverrideSpecMap(overrideYaml);
      if (isNotEmpty(overrideSpec)) {
        overridesMap.put(overrideType.getDisplayName(), overrideSpec);
      }
    }

    if (isNotEmpty(overridesMap)) {
      NgServiceOverridesYamlOutcome outcome = new NgServiceOverridesYamlOutcome();
      outcome.putAll(overridesMap);
      serviceStepSweepingOutputHelper.saveNgServiceOverridesYamlSweepingOutput(ambiance, outcome);
    }
  }

  private Map<String, Object> processConfigFiles(Ambiance ambiance, ServiceConfig serviceConfig) {
    Map<String, Object> configFilesMap = new HashMap<>();
    ServiceSpec with = serviceConfig.getServiceInfoConfig().getWith();
    if (with == null || isEmpty(with.getConfigFiles())) {
      return configFilesMap;
    }
    Infrastructure infrastructure = infraBasedHelper.getStageInfra(ambiance);
    String basePath = infraBasedHelper.getBasePath(ambiance, infrastructure);
    List<ConfigFile> configFiles = resolveConfigFileInputs(ambiance, with.getConfigFiles());
    for (ConfigFile configFile : configFiles) {
      if (configFile == null || isEmpty(configFile.getId())) {
        continue;
      }
      configFilesMap.put(configFile.getId(), new HashMap<>(getConfigFileOutput(configFile, basePath)));
    }
    return configFilesMap;
  }

  private Map<String, Object> processStartupScript(Ambiance ambiance, ServiceConfig serviceConfig) {
    ServiceSpec with = serviceConfig.getServiceInfoConfig().getWith();
    if (!SpotServiceSpec.hasStartupScript(with)) {
      return new HashMap<>();
    }
    StartupScriptConfiguration startupScript = ((SpotServiceSpec) with).getStartupScript();
    SpotStartupScriptHelper.validateStoreType(startupScript.getStore());
    Map<String, Object> outcome = new HashMap<>(SpotStartupScriptHelper.buildInitialOutcome(startupScript));
    if (SpotStartupScriptHelper.requiresCodeFetch(startupScript)) {
      Map<String, Object> fetchInputs = SpotStartupScriptHelper.buildCodeFetchInputs(startupScript);
      TemplateYamlResult result = generateYamlWithMergedDefaults(ambiance,
          SpotStartupScriptHelper.STARTUP_SCRIPT_CODE_ACTION, SpotStartupScriptHelper.STARTUP_SCRIPT_UNIT_ID,
          fetchInputs, TemplateYamlEntityType.STARTUP_SCRIPT, TemplateYamlSourceType.SERVICE);
      if (result != null && isNotEmpty(result.getMergedInputs())) {
        outcome.putAll(result.getMergedInputs());
      }

      // Compute absolute runner paths so that ${{serviceOutput.startupScript.paths}} resolves
      // to the workspace-relative locations where git clone lands the files.
      Infrastructure infrastructure = infraBasedHelper.getStageInfra(ambiance);
      String basePath = infraBasedHelper.getBasePath(ambiance, infrastructure);
      Object rawPaths = outcome.get(SpotStartupScriptHelper.PATHS_KEY);
      if (rawPaths instanceof List<?> pathList && isNotEmpty((List<?>) pathList)) {
        List<String> absolutePaths =
            pathList.stream()
                .filter(String.class ::isInstance)
                .map(String.class ::cast)
                .map(p -> getRunnerRelativePath(p, basePath, SpotStartupScriptHelper.STARTUP_SCRIPT_UNIT_ID))
                .toList();
        outcome.put(PATHS, String.join(",", absolutePaths));
      }
    }
    cdStepsExpressionResolver.updateExpressions(
        ambiance, outcome, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    return outcome;
  }

  private List<ConfigFile> resolveConfigFileInputs(Ambiance ambiance, List<ConfigFile> files) {
    List<ConfigFile> out = new ArrayList<>();
    for (ConfigFile cf : files) {
      if (cf == null) {
        continue;
      }
      if (isEmpty(cf.getAction())) {
        out.add(cf);
        continue;
      }

      TemplateYamlResult result = generateYamlWithMergedDefaults(ambiance, cf.getAction(), cf.getId(), cf.getInputs(),
          TemplateYamlEntityType.CONFIG_FILES, TemplateYamlSourceType.SERVICE);

      if (result != null && isNotEmpty(result.getMergedInputs())) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> defaults = result.getMergedInputs();
        if (isNotEmpty(defaults)) {
          merged.putAll(defaults);
        }
        if (cf.getInputs() != null) {
          merged.putAll(cf.getInputs());
        }
        out.add(cf.toBuilder().inputs(merged).build());
      }
    }
    cdStepsExpressionResolver.updateExpressions(ambiance, out, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    return out;
  }

  private static Map<String, Object> getConfigFileOutput(ConfigFile configFile, String basePath) {
    try {
      Map<String, Object> flattenConfigFile = ObjectFlattener.flatten(configFile);
      List<String> paths = getConfigFilePaths(configFile);
      if (isNotEmpty(paths)) {
        List<String> absolutePaths =
            paths.stream().map(path -> getRunnerRelativePath(path, basePath, configFile.getId())).toList();
        flattenConfigFile.put(PATHS, String.join(",", absolutePaths));
      }
      return flattenConfigFile;
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<String> getConfigFilePaths(ConfigFile configFile) {
    Map<String, Object> inputs = configFile.getInputs();
    if (isNotEmpty(inputs)) {
      List<String> paths = ManifestTemplatesPathsUtils.getPathsFromInputs(inputs);
      if (isNotEmpty(paths)) {
        return paths;
      }
    }
    if (configFile.getStoreConfigWrapper() != null
        && configFile.getStoreConfigWrapper().getWith() instanceof WithPaths) {
      WithPaths storeWithPaths = (WithPaths) configFile.getStoreConfigWrapper().getWith();
      if (ParameterField.isNotNull(storeWithPaths.getPaths()) && isNotEmpty(storeWithPaths.getPaths().obtainValue())) {
        return storeWithPaths.getPaths().obtainValue();
      }
    }
    return Collections.emptyList();
  }

  private Map<String, Object> processHooks(ServiceConfig serviceConfig) {
    Map<String, Object> hooksMap = new HashMap<>();
    ServiceSpec with = serviceConfig.getServiceInfoConfig().getWith();
    if (with == null || isEmpty(with.getHooks())) {
      return hooksMap;
    }
    for (ServiceHookConfig hook : with.getHooks()) {
      if (hook == null || isEmpty(hook.getIdentifier())) {
        continue;
      }
      Map<String, Object> hookData = new HashMap<>();
      hookData.put("identifier", hook.getIdentifier());
      hookData.put("type", hook.getType() != null ? hook.getType().getDisplayName() : null);
      hookData.put("actions",
          hook.getActions() != null
              ? hook.getActions().stream().map(a -> a.getDisplayName()).collect(Collectors.toList())
              : Collections.emptyList());
      hookData.put("order", hook.getOrder());
      if (hook.getStore() != null) {
        Map<String, Object> storeData = new HashMap<>();
        storeData.put("content", hook.getStore().getContent());
        hookData.put("store", storeData);
      }
      hooksMap.put(hook.getIdentifier(), hookData);
    }
    return hooksMap;
  }

  public Map<String, Object> processManifests(Ambiance ambiance, ServiceConfig serviceConfig) {
    Map<String, Object> manifestsMap = new HashMap<>();
    ManifestWrapper manifests = serviceConfig.getServiceInfoConfig().getWith().getManifests();
    if (manifests == null) {
      return manifestsMap;
    }

    List<ManifestConfig> manifestsSources = manifests.getSources();
    if (isEmpty(manifestsSources)) {
      return manifestsMap;
    }

    ManifestsStepUtils.validateManifestsUniqueIdentifiers(manifestsSources);
    ManifestsStepUtils.validateManifestIdsAgainstManifestTypes(manifestsSources);

    Infrastructure infrastructure = infraBasedHelper.getStageInfra(ambiance);
    String basePath = infraBasedHelper.getBasePath(ambiance, infrastructure);

    return processTemplatizedManifests(ambiance, manifestsSources, basePath, getConfiguredPrimaryManifestId(manifests));
  }

  /**
   * Process templatized manifests using inputs map.
   */
  private Map<String, Object> processTemplatizedManifests(
      Ambiance ambiance, List<ManifestConfig> manifestsSources, String basePath, String configuredPrimaryManifestId) {
    Map<String, Object> manifestsMap = new HashMap<>();

    // Merge defaults with actual inputs first (actual takes precedence)
    List<ManifestConfig> resolvedManifests = mergeManifestsInputsWithDefaults(ambiance, manifestsSources);
    cdStepsExpressionResolver.updateExpressions(ambiance, resolvedManifests, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    // Process manifests based on type
    processManifestBasedOnType(manifestsMap, resolvedManifests, basePath);
    String primaryManifestId =
        handlePrimaryManifestProcessing(manifestsMap, resolvedManifests, basePath, configuredPrimaryManifestId);
    resolvedManifests.forEach(manifest -> {
      Map<String, Object> manifestOutput = getManifestOutputFromInputs(manifest, basePath);
      manifestsMap.put(manifest.getId(), manifestOutput);
      // Additionally expose the manifest under a kebab->camelCase key so that expression paths like
      // serviceOutput.manifests.<id> remain resolvable when the manifest id contains '-'. Purely additive:
      // the raw-id key is preserved and no extra key is added when the id has no '-'.
      String expressionSafeId = KebabCaseExpressionsUtility.capitalizeAfterHyphen(manifest.getId());
      if (!expressionSafeId.equals(manifest.getId())) {
        manifestsMap.putIfAbsent(expressionSafeId, manifestOutput);
      }
    });

    if (manifestsMap.containsKey(ManifestTemplateConstants.OVERRIDES)) {
      throw new InvalidRequestException("[overrides] keyword is reserved, please update manifest id");
    }

    handleOverridesFiles(manifestsMap, resolvedManifests, basePath, primaryManifestId);
    handleFilesToRender(ambiance, manifestsMap, resolvedManifests, basePath, primaryManifestId);
    handleManifestsFilesForTemplating(manifestsMap, resolvedManifests, basePath, primaryManifestId);

    return manifestsMap;
  }

  /**
   * Merges defaults with actual inputs for each manifest (actual takes precedence).
   * Returns a new list of manifests with resolved inputs so subsequent logic uses merged values.
   */
  private List<ManifestConfig> mergeManifestsInputsWithDefaults(
      Ambiance ambiance, List<ManifestConfig> manifestsSources) {
    if (isEmpty(manifestsSources)) {
      return manifestsSources;
    }
    return manifestsSources.stream()
        .map(manifest -> buildManifestConfigWithDefaults(ambiance, manifest))
        .collect(Collectors.toList());
  }

  /**
   * For manifest with inputs: merge defaults with actual inputs (actual takes precedence)
   * and return a new ManifestConfig with merged inputs.
   */
  private ManifestConfig buildManifestConfigWithDefaults(Ambiance ambiance, ManifestConfig manifest) {
    if (NO_OP_ACTION.equals(manifest.getAction())) {
      return manifest;
    }
    String actionName = manifest.getAction();
    TemplateYamlResult result = generateYamlWithMergedDefaults(ambiance, actionName, manifest.getId(),
        manifest.getInputs(), TemplateYamlEntityType.MANIFEST, TemplateYamlSourceType.SERVICE);
    if (result != null) {
      return manifest.toBuilder().inputs(result.getMergedInputs()).build();
    }
    return manifest;
  }

  private Map<String, Object> processArtifacts(Ambiance ambiance, ServiceConfig serviceConfig) {
    Map<String, Object> artifactMap = new HashMap<>();
    ArtifactWrapper artifactWrapper = serviceConfig.getServiceInfoConfig().getWith().getArtifacts();
    if (artifactWrapper == null) {
      return artifactMap;
    }

    if (!hasAnyArtifact(artifactWrapper)) {
      return artifactMap;
    }

    // Merge defaults with actual inputs
    List<ArtifactConfig> resolvedArtifacts = mergeArtifactsInputsWithDefaults(ambiance, artifactWrapper);
    cdStepsExpressionResolver.updateExpressions(
        ambiance, resolvedArtifacts, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

    Optional<ArtifactConfig> primary = resolvedArtifacts.stream().filter(a -> !a.isSidecar()).findFirst();
    primary.ifPresent(artifact -> {
      Map<String, Object> entry = buildArtifactEntryFromConfig(ambiance, artifact);
      artifactMap.put(PRIMARY, entry);
      artifactMap.put(artifact.getId(), entry);
    });

    resolvedArtifacts.stream().filter(ArtifactConfig::isSidecar).forEach(artifact -> {
      Map<String, Object> entry = buildArtifactEntryFromConfig(ambiance, artifact);
      artifactMap.put(artifact.getId(), entry);
    });

    return artifactMap;
  }

  private boolean hasAnyArtifact(ArtifactWrapper artifactWrapper) {
    return getPrimaryArtifact(artifactWrapper).isPresent() || isNotEmpty(getSidecarsArtifactsMap(artifactWrapper));
  }

  /**
   * Merges defaults with actual inputs for each artifact; returns list with primary first, then sidecars.
   */
  private List<ArtifactConfig> mergeArtifactsInputsWithDefaults(Ambiance ambiance, ArtifactWrapper artifactWrapper) {
    List<ArtifactConfig> result = new ArrayList<>();
    getPrimaryArtifact(artifactWrapper).ifPresent(a -> result.add(buildArtifactConfigWithDefaults(ambiance, a)));
    getSidecarsArtifactsMap(artifactWrapper)
        .values()
        .forEach(a -> result.add(buildArtifactConfigWithDefaults(ambiance, a)));
    return result;
  }

  /**
   * Converts an ArtifactConfig (with merged inputs) to the map entry for the artifact map.
   */
  private Map<String, Object> buildArtifactEntryFromConfig(Ambiance ambiance, ArtifactConfig artifact) {
    return new HashMap<>(getArtifactOutput(artifact));
  }

  /**
   * Merges defaults with given inputs (actual takes precedence) and generates YAML.
   * Shared by artifact and manifest processing.
   *
   * @return result with merged inputs and yaml, or null if inputs empty or generation fails
   */
  @Nullable
  private TemplateYamlResult generateYamlWithMergedDefaults(Ambiance ambiance, String actionType, String entityId,
      Map<String, Object> structuredInputsMap, TemplateYamlEntityType entityType, TemplateYamlSourceType entitySource) {
    TemplateYamlConfig config = TemplateYamlConfig.builder()
                                    .templateType(actionType)
                                    .entityType(entityType)
                                    .sourceType(entitySource)
                                    .entityId(entityId)
                                    .structuredInputsMap(structuredInputsMap)
                                    .inputsFlattener(TemplateYamlGenerator::flattenInputsMap)
                                    .build();
    return templateYamlGenerator.generateTemplateYamlWithDefaults(ambiance, config);
  }

  /**
   * For artifacts with inputs: merge defaults with actual (actual takes precedence)
   * and return ArtifactConfig with merged inputs.
   */
  private ArtifactConfig buildArtifactConfigWithDefaults(Ambiance ambiance, ArtifactConfig artifact) {
    if (isEmpty(artifact.getAction())) {
      return artifact;
    }
    String actionType = artifact.getAction();
    TemplateYamlResult result = generateYamlWithMergedDefaults(ambiance, actionType, artifact.getId(),
        artifact.getInputs(), TemplateYamlEntityType.ARTIFACT, TemplateYamlSourceType.SERVICE);
    if (result != null) {
      return artifact.toBuilder().inputs(result.getMergedInputs()).build();
    }
    log.warn("Failed to merge defaults for artifact {}, using actual inputs only", artifact.getId());
    return artifact;
  }

  private void saveFilePathsForRendering(Ambiance ambiance, List<String> filePathsForRendering, String basePath,
      List<ManifestConfig> manifestsSources, String primaryManifestId) {
    List<String> allPaths = new ArrayList<>();

    if (isNotEmpty(filePathsForRendering)) {
      allPaths.addAll(filePathsForRendering);
    }

    if (isEmpty(allPaths)) {
      return;
    }

    RenderingSweepingOutput renderingSweepingOutput = RenderingSweepingOutput.builder().filePaths(allPaths).build();
    serviceStepSweepingOutputHelper.saveFilePathsForRendering(ambiance, renderingSweepingOutput);
  }

  private Optional<ArtifactConfig> getPrimaryArtifact(ArtifactWrapper artifactWrapper) {
    ArtifactConfig primaryArtifact = null;
    if (ParameterField.isNotNull(artifactWrapper.getPrimary()) && artifactWrapper.getPrimary().getValue() != null
        && !artifactWrapper.getPrimary().isExpression()) {
      primaryArtifact = artifactWrapper.getPrimary().getValue();
    } else if (isNotEmpty(artifactWrapper.getSources())) {
      List<ArtifactConfig> primaryArtifactList =
          artifactWrapper.getSources().stream().filter(config -> !config.isSidecar()).toList();
      if (isEmpty(primaryArtifactList)) {
        throw new InvalidRequestException("At least one primary artifact is required");
      }

      if (primaryArtifactList.size() > 1) {
        throw new InvalidRequestException(String.format(
            "primaryArtifactRef is unresolved, so the primary artifact cannot be picked from the multiple artifact "
                + "sources [%s]. Provide a primaryArtifactRef that matches exactly one of them.",
            primaryArtifactList.stream().map(ArtifactConfig::getId).collect(Collectors.joining(", "))));
      }
      primaryArtifact = primaryArtifactList.get(0);
    }
    return Optional.ofNullable(primaryArtifact);
  }

  private static String getInputsYaml(Map<String, Object> inputs) {
    String inputsYaml = StringUtils.EMPTY;
    if (isNotEmpty(inputs)) {
      inputsYaml = YamlPipelineUtils.writeYamlString(inputs);
    }
    return inputsYaml;
  }

  /**
   * Resolves {@code artifacts.primary} to the matching source config for the given resolved id, and drops every
   * losing source from {@code artifacts.sources} so the unified {@link ServiceConfig} ends up byte-equivalent to
   * the static-ref flow (which never ships losing sources): only the winner plus the sidecars remain.
   */
  private static void updatePrimaryArtifactInServiceConfig(ArtifactWrapper artifacts, String primaryArtifactId) {
    List<ArtifactConfig> artifactSources = artifacts.getSources();
    if (isEmpty(artifactSources)) {
      throw new InvalidYamlException("Could not find primary artifact with id: " + primaryArtifactId);
    }

    ArtifactConfig primaryArtifact =
        artifactSources.stream()
            .filter(artifact -> artifact.getId().equals(primaryArtifactId))
            .findFirst()
            .orElseThrow(()
                             -> new InvalidRequestException(
                                 String.format("primaryArtifactRef: %s does not match to any [%s] artifacts",
                                     primaryArtifactId, getNonSidecarArtifactIdNames(artifactSources))));

    if (primaryArtifact.isSidecar()) {
      throw new InvalidRequestException(String.format(
          "primaryArtifactRef: %s matches a sidecar artifact, primary artifact is required", primaryArtifactId));
    }

    List<ArtifactConfig> filteredSources = new ArrayList<>();
    filteredSources.add(primaryArtifact);
    artifactSources.stream().filter(ArtifactConfig::isSidecar).forEach(filteredSources::add);

    artifacts.setPrimary(ParameterField.createValueField(primaryArtifact));
    artifacts.setSources(filteredSources);
  }

  private static String getNonSidecarArtifactIdNames(List<ArtifactConfig> artifactSources) {
    return artifactSources.stream()
        .filter(artifact -> !artifact.isSidecar())
        .map(ArtifactConfig::getId)
        .collect(Collectors.joining(", "));
  }

  private static Map<String, Object> getArtifactOutput(ArtifactConfig artifact) {
    Map<String, Object> flattenArtifact;
    try {
      flattenArtifact = ObjectFlattener.flatten(artifact);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return flattenArtifact;
  }

  private Map<String, ArtifactConfig> getSidecarsArtifactsMap(ArtifactWrapper artifactWrapper) {
    return artifactWrapper.getSources() != null
        ? artifactWrapper.getSources()
              .stream()
              .filter(artifact -> artifact.isSidecar())
              .collect(Collectors.toMap(ArtifactConfig::getId, Function.identity()))
        : Collections.emptyMap();
  }

  /**
   * Process manifests based on type.
   * Uses Set/Map lookups instead of instanceof checks.
   */
  private void processManifestBasedOnType(
      Map<String, Object> manifestsMap, List<ManifestConfig> manifestsSources, String basePath) {
    Map<String, List<ManifestConfig>> multipleAllowedManifests = new HashMap<>();
    Map<String, ManifestConfig> singleAllowedManifests = new HashMap<>();

    manifestsSources.forEach(manifest -> {
      String manifestType = manifest.getUses().getExpressionKey();
      ManifestType type = manifest.getUses();

      if (ManifestTypesValidationUtils.allowsMultipleManifests(type)) {
        multipleAllowedManifests.computeIfAbsent(manifestType, k -> new ArrayList<>()).add(manifest);
      } else {
        if (singleAllowedManifests.containsKey(manifestType)) {
          throw new InvalidRequestException(
              String.format("Multiple manifests are not allowed for manifest type : [%s], Please check manifest "
                      + "section of your service configuration",
                  manifestType));
        }
        singleAllowedManifests.put(manifestType, manifest);
      }
    });

    processMultiManifestsTypes(manifestsMap, basePath, multipleAllowedManifests);
    processSingleManifestsTypes(manifestsMap, basePath, singleAllowedManifests);
  }

  /**
   * Process manifest types that allow only single manifest.
   */
  private void processSingleManifestsTypes(
      Map<String, Object> manifestsMap, String basePath, Map<String, ManifestConfig> singleAllowedManifest) {
    singleAllowedManifest.forEach((manifestType, manifest) -> {
      Map<String, Object> manifestOutput = getManifestOutputFromInputs(manifest, basePath);
      manifestsMap.put(manifestType, manifestOutput);
    });
  }

  /**
   * Process manifest types that allow multiple manifests.
   * Uses inputs to get collective paths.
   */
  private void processMultiManifestsTypes(
      Map<String, Object> manifestsMap, String basePath, Map<String, List<ManifestConfig>> multipleAllowedManifest) {
    multipleAllowedManifest.forEach((manifestType, manifestConfigs) -> {
      Map<String, Object> typeBasedProperties = new HashMap<>();
      List<String> allPaths = new ArrayList<>();
      manifestConfigs.forEach(manifest -> {
        // Use inputs to get paths
        Map<String, Object> inputs = manifest.getInputs();
        if (isNotEmpty(inputs) && ManifestTypesValidationUtils.hasCollectivePaths(manifest.getUses())) {
          List<String> paths = ManifestTemplatesPathsUtils.getPathsFromInputs(inputs);
          if (isNotEmpty(paths)) {
            paths.forEach(path -> {
              String runnerPath = getRunnerRelativePath(path, basePath, manifest.getId());
              allPaths.add(runnerPath);
            });
          }
        }
      });

      if (!allPaths.isEmpty()) {
        typeBasedProperties.put(PATHS, allPaths);
        manifestsMap.put(manifestType, typeBasedProperties);
      }
    });
  }

  /**
   * Handle primary manifest processing.
   */
  private static String handlePrimaryManifestProcessing(Map<String, Object> manifestsMap,
      List<ManifestConfig> manifestsSources, String basePath, String configuredPrimaryManifestId) {
    Optional<ManifestConfig> manifestConfigOptional = Optional.empty();
    if (isNotEmpty(configuredPrimaryManifestId)) {
      manifestConfigOptional = manifestsSources.stream()
                                   .filter(manifest -> configuredPrimaryManifestId.equals(manifest.getId()))
                                   .findFirst();
    }
    if (manifestConfigOptional.isEmpty()) {
      manifestConfigOptional =
          manifestsSources.stream()
              .filter(manifest -> ManifestType.getSingleDeployPathSupportingTypes().contains(manifest.getUses()))
              .findFirst();
    }
    return manifestConfigOptional
        .map(manifestConfig -> {
          manifestsMap.put(PRIMARY, getManifestOutputFromInputs(manifestConfig, basePath));
          return manifestConfig.getId();
        })
        .orElse(null);
  }

  /**
   * Get manifest output from inputs.
   * Uses inputs map to extract paths and overrides.
   */
  @VisibleForTesting
  static Map<String, Object> getManifestOutputFromInputs(ManifestConfig manifest, String basePath) {
    Map<String, Object> flattenManifest;
    try {
      flattenManifest = ObjectFlattener.flatten(manifest);

      // Handle paths from inputs
      Map<String, Object> inputs = manifest.getInputs();
      if (isNotEmpty(inputs)) {
        List<String> paths = ManifestTemplatesPathsUtils.getPathsFromInputs(inputs);
        if (isNotEmpty(paths)) {
          List<String> absolutePaths =
              paths.stream().map(path -> getRunnerRelativePath(path, basePath, manifest.getId())).toList();
          String jsonPathsString = toJsonString(absolutePaths);
          flattenManifest.put(PATHS, jsonPathsString);
        }
      }

      // Emit overlay folder path as its own output key; plugin joins it against PLUGIN_MANIFEST_PATH.
      String overlayFolder = ManifestTemplatesPathsUtils.getKustomizeYamlFolderPathFromInputs(inputs);
      if (isNotEmpty(overlayFolder)) {
        flattenManifest.put(ManifestTemplateConstants.OUTPUT_KEY_KUSTOMIZE_YAML_FOLDER_PATH, overlayFolder);
      }

      // Handle pluginPath from inputs (Kustomize exec plugin support).
      // Convert to an absolute runner path so the plugin receives the full path, just like MANIFEST_PATH.
      if (isNotEmpty(inputs) && inputs.containsKey(ManifestTemplateConstants.INPUTS_KEY_PLUGIN_PATH)) {
        Object pluginPathValue = inputs.get(ManifestTemplateConstants.INPUTS_KEY_PLUGIN_PATH);
        if (pluginPathValue instanceof String pluginPath && isNotEmpty(pluginPath)) {
          String absolutePluginPath = getRunnerRelativePath(pluginPath, basePath, manifest.getId());
          flattenManifest.put(OUTPUT_KEY_PLUGIN, absolutePluginPath);
        }
      }

      // Handle overrides from inputs
      if (isNotEmpty(inputs)) {
        List<String> overridePaths = ManifestTemplatesPathsUtils.getOverridesFromInputs(inputs);
        if (isNotEmpty(overridePaths)) {
          Set<String> runnerRelativeOverridePaths =
              ManifestsStepUtils.getRunnerRelativePath(basePath, manifest.getId(), overridePaths);
          flattenManifest.put(ManifestTemplateConstants.OVERRIDES, runnerRelativeOverridePaths);
        }
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return flattenManifest;
  }

  /**
   * Get override file paths from inputs.
   */
  private List<String> getOverrideFilePathsFromInputs(
      String basePath, List<ManifestConfig> manifestsSources, String primaryManifestId) {
    List<String> valuesYamlPaths = new ArrayList<>();
    if (isNotEmpty(manifestsSources)) {
      manifestsSources.forEach(manifest -> {
        Map<String, Object> inputs = manifest.getInputs();
        if (isNotEmpty(inputs)) {
          List<String> manifestValuesYamlPaths = ManifestTemplatesPathsUtils.getOverridesFromInputs(inputs);
          if (isNotEmpty(manifestValuesYamlPaths)) {
            List<String> valuesYamlAbsolutePaths =
                manifestValuesYamlPaths.stream()
                    .map(path -> ManifestsStepUtils.getRunnerRelativePath(path, basePath, manifest.getId()))
                    .toList();
            valuesYamlPaths.addAll(valuesYamlAbsolutePaths);
          }
        }
      });
    }
    return valuesYamlPaths;
  }

  /**
   * Handle overrides files.
   */
  private void handleOverridesFiles(Map<String, Object> manifestsMap, List<ManifestConfig> manifestsSources,
      String basePath, String primaryManifestId) {
    List<String> runnerRelativeOverridesFilePaths =
        getOverrideFilePathsFromInputs(basePath, manifestsSources, primaryManifestId);
    if (isNotEmpty(runnerRelativeOverridesFilePaths)) {
      manifestsMap.put(ManifestTemplateConstants.OVERRIDES, runnerRelativeOverridesFilePaths);
    }
  }

  /**
   * Get manifest files path for rendering from inputs.
   */
  private List<String> getManifestsFilesPathForRendering(
      String basePath, List<ManifestConfig> manifests, String primaryManifestId) {
    Map<String, List<String>> filesPathForRendering = new HashMap<>();

    manifests.forEach(manifest -> {
      Map<String, Object> inputs = manifest.getInputs();
      if (isNotEmpty(inputs)) {
        List<String> filesToRender = ManifestTemplatesPathsUtils.getFilesToRenderFromInputs(manifest.getUses(), inputs);
        if (isNotEmpty(filesToRender)) {
          filesPathForRendering.put(manifest.getId(), filesToRender);
        }
      }
    });

    return ServiceStepUtility.getAbsFilePaths(basePath, filesPathForRendering);
  }

  /**
   * Handle files to render.
   */
  private void handleFilesToRender(Ambiance ambiance, Map<String, Object> manifestsMap,
      List<ManifestConfig> manifestsSources, String basePath, String primaryManifestId) {
    List<String> filePathsForRendering =
        getManifestsFilesPathForRendering(basePath, manifestsSources, primaryManifestId);
    if (isNotEmpty(filePathsForRendering)) {
      manifestsMap.put(ManifestTemplateConstants.TO_RENDER, filePathsForRendering);
    }
    saveFilePathsForRendering(ambiance, filePathsForRendering, basePath, manifestsSources, primaryManifestId);
  }

  /**
   * Get manifest files path for templating from inputs.
   */
  private List<String> getManifestsFilesPathForTemplating(
      String basePath, List<ManifestConfig> manifests, String primaryManifestId) {
    if (isEmpty(manifests)) {
      return new ArrayList<>();
    }
    Map<String, List<String>> filesPathForTemplating = new HashMap<>();

    manifests.forEach(manifest -> {
      Map<String, Object> inputs = manifest.getInputs();
      if (isNotEmpty(inputs)) {
        if (ManifestTemplatesPathsUtils.supportsFilesToTemplate(manifest.getUses())) {
          List<String> filesToTemplate =
              ManifestTemplatesPathsUtils.getFilesToTemplateFromInputs(manifest.getUses(), inputs);
          if (isNotEmpty(filesToTemplate)) {
            filesPathForTemplating.put(manifest.getId(), filesToTemplate);
          }
        }
      }
    });

    return ServiceStepUtility.getAbsFilePaths(basePath, filesPathForTemplating);
  }

  /**
   * Handle manifest files for templating.
   */
  private void handleManifestsFilesForTemplating(Map<String, Object> manifestsMap,
      List<ManifestConfig> manifestsSources, String basePath, String primaryManifestId) {
    List<String> filePathsForTemplating =
        getManifestsFilesPathForTemplating(basePath, manifestsSources, primaryManifestId);
    if (isNotEmpty(filePathsForTemplating)) {
      manifestsMap.put(ManifestTemplateConstants.TO_TEMPLATE, filePathsForTemplating);
    }
  }

  @Data
  @Builder
  private static class GitBranchInfo {
    private final String branch;
    private final String parentEntityRepoName;
  }

  @Data
  @Builder
  private static class ServiceFetchResult {
    private final String mergedServiceYaml;
    private final ServiceEntityMetadata serviceEntityMetadata;
    /** Resolved v0 service YAML from NG (for expression resolution). Only set when fetched from NG. */
    private final String resolvedMergedV0ServiceYaml;
    /** Override yamls by type from NG Manager response. */
    private final EnumMap<ServiceOverridesType, String> v0OverridesYamlMap;
    /** Override responses from NG Manager. */
    private final Map<ServiceOverridesType, SingleOverrideConvertorResponseDTO> overridesResponses;
    /** V0 environment outcome YAML from NG (for expression resolution). Only set when fetched from NG. */
    private final String envOutcomeYaml;
  }

  private static String toJsonString(Object object) {
    return JsonUtils.asJson(object);
  }

  private Map<String, String> convertMapExpressions(Map<String, String> map, String pipelineYaml) {
    if (isEmpty(map)) {
      return map;
    }
    Map<String, String> converted = new HashMap<>(map);
    for (Map.Entry<String, String> entry : converted.entrySet()) {
      if (isNotEmpty(entry.getValue())
          && (EngineExpressionEvaluator.hasExpressions(entry.getValue())
              || EngineExpressionEvaluator.hasCelExpressions(entry.getValue()))) {
        String result = expressionConversionHelper.convertExpressions(entry.getValue(), pipelineYaml);
        if (isNotEmpty(result)) {
          entry.setValue(result);
        }
      }
    }
    return converted;
  }

  private NgServicePropertiesResponseDTO convertNgServicePropertiesExpressions(
      Ambiance ambiance, NgServicePropertiesResponseDTO responseDTO) {
    if (!expressionConversionHelper.isExpressionConversionEnabled(ambiance)) {
      return responseDTO;
    }
    String pipelineYaml = expressionConversionHelper.fetchPipelineYaml(ambiance);
    if (isEmpty(pipelineYaml)) {
      return responseDTO;
    }
    String convertedMergedV0ServiceYaml =
        expressionConversionHelper.convertExpressions(responseDTO.getMergedV0ServiceYaml(), pipelineYaml);
    String convertedMergedV0OverrideYaml =
        expressionConversionHelper.convertExpressions(responseDTO.getMergedV0OverrideYaml(), pipelineYaml);
    Map<String, String> convertedNgOutcomes = convertMapExpressions(responseDTO.getNgOutcomes(), pipelineYaml);

    NGServiceEntityMetadata metadata = responseDTO.getNgServiceEntityMetadata();
    Map<String, String> convertedTags =
        metadata != null ? convertMapExpressions(metadata.getTags(), pipelineYaml) : null;
    NGServiceEntityMetadata convertedMetadata = metadata != null ? NGServiceEntityMetadata.builder()
                                                                       .identifier(metadata.getIdentifier())
                                                                       .description(metadata.getDescription())
                                                                       .name(metadata.getName())
                                                                       .tags(convertedTags)
                                                                       .build()
                                                                 : null;

    return NgServicePropertiesResponseDTO.builder()
        .mergedV0ServiceYaml(convertedMergedV0ServiceYaml)
        .mergedV0OverrideYaml(convertedMergedV0OverrideYaml)
        .ngOutcomes(convertedNgOutcomes)
        .ngServiceEntityMetadata(convertedMetadata)
        .build();
  }

  private UnifiedServiceConverterResponse convertUnifiedServiceResponse(
      Ambiance ambiance, UnifiedServiceConverterResponse response) {
    if (!expressionConversionHelper.isExpressionConversionEnabled(ambiance) || response == null
        || response.getResponseDTO() == null) {
      return response;
    }
    String pipelineYaml = expressionConversionHelper.fetchPipelineYaml(ambiance);
    if (pipelineYaml == null) {
      return response;
    }
    UnifiedServiceConverterResponseDTO responseDTO = response.getResponseDTO();
    String convertedMergedV0ServiceYaml =
        expressionConversionHelper.convertExpressions(responseDTO.getMergedV0ServiceYaml(), pipelineYaml);
    if (convertedMergedV0ServiceYaml != null
        && !convertedMergedV0ServiceYaml.equals(responseDTO.getMergedV0ServiceYaml())) {
      return UnifiedServiceConverterResponse.builder()
          .responseDTO(UnifiedServiceConverterResponseDTO.builder()
                           .mergedServiceYaml(responseDTO.getMergedServiceYaml())
                           .identifier(responseDTO.getIdentifier())
                           .description(responseDTO.getDescription())
                           .name(responseDTO.getName())
                           .tags(responseDTO.getTags())
                           .ngOutcomes(responseDTO.getNgOutcomes())
                           .templateBased(responseDTO.getTemplateBased())
                           .mergedV0ServiceYaml(convertedMergedV0ServiceYaml)
                           .v0OverridesYamlMap(responseDTO.getV0OverridesYamlMap())
                           .overridesResponses(responseDTO.getOverridesResponses())
                           .build())
          .build();
    }
    return response;
  }
}
