/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.cd.beans.outcomes.CdOutcomeConstants.ARTIFACTS_OUTCOME_EXPRESSION;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.CONFIG_FILES_OUTCOME_EXPRESSION;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.MANIFEST_OUTCOME_EXPRESSION;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.SERVICE_HOOKS_OUTCOME_EXPRESSION;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.SERVICE_OUTCOME_EXPRESSION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.beans.IdentifierRef;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.cd.beans.outcomes.ArtifactsOutcome;
import io.harness.cd.beans.outcomes.ConfigFilesOutcome;
import io.harness.cd.beans.outcomes.ManifestOutputVarsSweepingOutput;
import io.harness.cd.beans.outcomes.ManifestsOutcome;
import io.harness.cd.beans.outcomes.ServiceConfigOutcome;
import io.harness.cd.beans.outcomes.ServiceHooksOutcome;
import io.harness.cd.beans.outcomes.UnifiedServiceOutcome;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.ci.cd.service.ServiceEntityService;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.common.utils.YamlParsingUtils;
import io.harness.environment.remote.EnvironmentResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.infrastructure.unified.UnifiedEnvConvertorResponse;
import io.harness.infrastructure.unified.UnifiedEnvironmentConverterResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.unified.service.NGOutcomes;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ServiceStepOutcomeHelper {
  public static final String PRIMARY = "primary";
  public static final String IDENTIFIER = "identifier";
  public static final String PLUGIN_ARTIFACT_DOWNLOAD_PATH = "PLUGIN_ARTIFACT_DOWNLOAD_PATH";
  public static final String ARTIFACT_DOWNLOAD_PATH = "ARTIFACT_DOWNLOAD_PATH";
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private EnvironmentEntityService environmentEntityService;
  @Inject private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject private EnvironmentResourceClient environmentResourceClient;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  public void addManifestsStepOutcome(
      Ambiance ambiance, List<StepResponse.StepOutcome> stepOutcomes, VariablesSweepingOutput ngOutcomes) {
    // Use manifests from ngOutcomes if available
    if (ngOutcomes != null && ngOutcomes.containsKey(NGOutcomes.MANIFESTS.getName())) {
      String manifestsYamlString = (String) ngOutcomes.get(NGOutcomes.MANIFESTS.getName());
      if (isNotEmpty(manifestsYamlString)) {
        Map<String, Object> manifestsMapFromNgOutcomes = YamlParsingUtils.parseYamlStringToMap(manifestsYamlString);
        populatePrimaryManifest(manifestsMapFromNgOutcomes, ambiance);
        stepOutcomes.add(StepResponse.StepOutcome.builder()
                             .name(MANIFEST_OUTCOME_EXPRESSION)
                             .group(StepCategory.STAGE.name())
                             .outcome(new ManifestsOutcome(manifestsMapFromNgOutcomes))
                             .build());
        return;
      }
    }

    OptionalSweepingOutput opServiceConfigOutcome =
        serviceStepSweepingOutputHelper.fetchServiceConfigMetadataOutput(ambiance);
    if (opServiceConfigOutcome.isFound()) {
      addManifestOutcomeUsingV1Service(ambiance, stepOutcomes, opServiceConfigOutcome);
    }
  }

  private void populatePrimaryManifest(Map<String, Object> ngManifestsMap, Ambiance ambiance) {
    OptionalSweepingOutput sweepingOutput =
        serviceStepSweepingOutputHelper.fetchManifestOutputVarsSweepingOutput(ambiance);
    if (!sweepingOutput.isFound()) {
      return;
    }
    ManifestOutputVarsSweepingOutput manifestOutputVarsSweepingOutput =
        (ManifestOutputVarsSweepingOutput) sweepingOutput.getOutput();
    Map<String, Map<String, String>> manifestOutputVars = manifestOutputVarsSweepingOutput.getManifestsOutputVars();
    if (!ngManifestsMap.containsKey(PRIMARY) || isEmpty(manifestOutputVars)) {
      return;
    }
    Object primaryRaw = ngManifestsMap.get(PRIMARY);
    if (!(primaryRaw instanceof Map)) {
      return;
    }
    @SuppressWarnings("unchecked") Map<String, Object> primaryManifestMap = (Map<String, Object>) primaryRaw;
    Object idObj = primaryManifestMap.get(IDENTIFIER);
    if (!(idObj instanceof String)) {
      return;
    }
    String primaryManifestId = (String) idObj;
    if (primaryManifestId.isEmpty()) {
      return;
    }
    Map<String, String> varsForPrimary = manifestOutputVars.get(primaryManifestId);
    if (varsForPrimary == null) {
      return;
    }
    if (isNotEmpty(varsForPrimary.get(ARTIFACT_DOWNLOAD_PATH))) {
      primaryManifestMap.put(ARTIFACT_DOWNLOAD_PATH, varsForPrimary.get(ARTIFACT_DOWNLOAD_PATH));
    }
    if (isNotEmpty(varsForPrimary.get(PLUGIN_ARTIFACT_DOWNLOAD_PATH))) {
      primaryManifestMap.put(PLUGIN_ARTIFACT_DOWNLOAD_PATH, varsForPrimary.get(PLUGIN_ARTIFACT_DOWNLOAD_PATH));
    }
    ngManifestsMap.put(PRIMARY, primaryManifestMap);
  }

  public void addArtifactsStepOutcome(List<StepResponse.StepOutcome> stepOutcomes, ArtifactsOutcome artifactsOutcome,
      VariablesSweepingOutput ngOutcomes) {
    // Use artifacts from ngOutcomes if available
    if (ngOutcomes != null && ngOutcomes.containsKey(NGOutcomes.ARTIFACTS.getName())) {
      String artifactsYamlString = (String) ngOutcomes.get(NGOutcomes.ARTIFACTS.getName());
      if (isNotEmpty(artifactsYamlString)) {
        Map<String, Object> artifactsMapFromNgOutcomes = YamlParsingUtils.parseYamlStringToMap(artifactsYamlString);
        stepOutcomes.add(StepResponse.StepOutcome.builder()
                             .name(ARTIFACTS_OUTCOME_EXPRESSION)
                             .group(StepCategory.STAGE.name())
                             .outcome(new ArtifactsOutcome(artifactsMapFromNgOutcomes))
                             .build());
        return;
      }
    }

    stepOutcomes.add(StepResponse.StepOutcome.builder()
                         .name(ARTIFACTS_OUTCOME_EXPRESSION)
                         .group(StepCategory.STAGE.name())
                         .outcome(artifactsOutcome)
                         .build());
  }

  public void addConfigFilesStepOutcome(List<StepResponse.StepOutcome> stepOutcomes,
      ConfigFilesOutcome configFilesOutcome, VariablesSweepingOutput ngOutcomes) {
    // Use configFiles from ngOutcomes if available
    if (ngOutcomes != null && ngOutcomes.containsKey("configFiles")) {
      String configFilesYamlString = (String) ngOutcomes.get("configFiles");
      if (isNotEmpty(configFilesYamlString)) {
        Map<String, Object> configFilesMapFromNgOutcomes = YamlParsingUtils.parseYamlStringToMap(configFilesYamlString);
        VariablesSweepingOutput output = new VariablesSweepingOutput();
        output.putAll(configFilesMapFromNgOutcomes);

        if (isNotEmpty(configFilesMapFromNgOutcomes)) {
          stepOutcomes.add(StepResponse.StepOutcome.builder()
                               .name(CONFIG_FILES_OUTCOME_EXPRESSION)
                               .group(StepCategory.STAGE.name())
                               .outcome(output)
                               .build());
        }
        return;
      }
    }

    stepOutcomes.add(StepResponse.StepOutcome.builder()
                         .name(CONFIG_FILES_OUTCOME_EXPRESSION)
                         .group(StepCategory.STAGE.name())
                         .outcome(configFilesOutcome)
                         .build());
  }

  public void addServiceHooksStepOutcome(
      List<StepResponse.StepOutcome> stepOutcomes, ServiceHooksOutcome serviceHooksOutcome) {
    stepOutcomes.add(StepResponse.StepOutcome.builder()
                         .name(SERVICE_HOOKS_OUTCOME_EXPRESSION)
                         .group(StepCategory.STAGE.name())
                         .outcome(serviceHooksOutcome)
                         .build());
  }

  public void addServiceOutcome(List<StepResponse.StepOutcome> stepOutcomes, UnifiedServiceOutcome serviceOutcome,
      VariablesSweepingOutput ngOutcomes) {
    // Use service from ngOutcomes if available
    if (ngOutcomes != null && ngOutcomes.containsKey(NGOutcomes.SERVICE.getName())) {
      String serviceYamlString = (String) ngOutcomes.get(NGOutcomes.SERVICE.getName());
      if (isNotEmpty(serviceYamlString)) {
        Map<String, Object> serviceMapFromNgOutcomes = YamlParsingUtils.parseYamlStringToMap(serviceYamlString);
        if (isNotEmpty(serviceMapFromNgOutcomes)) {
          VariablesSweepingOutput variablesSweepingOutput = new VariablesSweepingOutput();
          variablesSweepingOutput.putAll(serviceMapFromNgOutcomes);
          stepOutcomes.add(StepResponse.StepOutcome.builder()
                               .name(SERVICE_OUTCOME_EXPRESSION)
                               .group(StepCategory.STAGE.name())
                               .outcome(variablesSweepingOutput)
                               .build());
          return;
        }
      }
    }

    stepOutcomes.add(StepResponse.StepOutcome.builder()
                         .name(SERVICE_OUTCOME_EXPRESSION)
                         .group(StepCategory.STAGE.name())
                         .outcome(serviceOutcome)
                         .build());
  }

  public Optional<ServiceEntity> getServiceEntity(
      String serviceRef, String accountId, String orgIdentifier, String projectIdentifier) {
    return serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceRef);
  }

  public EnvironmentEntity getEnvironmentEntity(String envRef, String accountId, String orgIdentifier,
      String projectIdentifier, String branch, String parentEntityRepoName) {
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(envRef, accountId, orgIdentifier, projectIdentifier);
    Optional<EnvironmentEntity> environmentEntityOp = environmentEntityService.get(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());

    EnvironmentEntity environmentEntity = null;
    if (environmentEntityOp.isEmpty()) {
      UnifiedEnvConvertorResponse responseNg = getResponse(environmentResourceClient.convertToUnifiedEnvironment(
          identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
          identifierRef.getProjectIdentifier(), branch, parentEntityRepoName));

      if (responseNg == null) {
        throw new InvalidRequestException(String.format("Environment with environment ref [%s] does not exist, please "
                + "check environment provided in related pipeline stage",
            envRef));
      }

      UnifiedEnvironmentConverterResponseDTO responseDTONg = responseNg.getResponseDTO();
      environmentEntity = EnvironmentEntity.builder()
                              .accountId(identifierRef.getAccountIdentifier())
                              .orgIdentifier(identifierRef.getOrgIdentifier())
                              .projectIdentifier(identifierRef.getProjectIdentifier())
                              .identifier(responseDTONg.getIdentifier())
                              .name(responseDTONg.getName())
                              .type(responseDTONg.getType())
                              .color(responseDTONg.getColor())
                              .tags(convertToList(responseDTONg.getTags()))
                              .harnessVersion(HarnessYamlVersion.V0)
                              .build();

    } else {
      environmentEntity = environmentEntityOp.get();
    }

    return environmentEntity;
  }

  private void addManifestOutcomeUsingV1Service(
      Ambiance ambiance, List<StepResponse.StepOutcome> stepOutcomes, OptionalSweepingOutput opServiceConfigOutcome) {
    ServiceConfigOutcome serviceConfigOutcome = (ServiceConfigOutcome) opServiceConfigOutcome.getOutput();
    if (isNotEmpty(serviceConfigOutcome.getManifests())) {
      Map<String, Object> manifestOutcomeMap = serviceConfigOutcome.getManifests();

      OptionalSweepingOutput sweepingOutput =
          serviceStepSweepingOutputHelper.fetchManifestOutputVarsSweepingOutput(ambiance);
      if (sweepingOutput.isFound()) {
        ManifestOutputVarsSweepingOutput manifestOutputVarsSweepingOutput =
            (ManifestOutputVarsSweepingOutput) sweepingOutput.getOutput();
        Map<String, String> singleDeployManifestOutputs =
            manifestOutputVarsSweepingOutput.getSingleDeployManifestOutputVars();
        Map<String, Map<String, String>> manifestsOutputVars =
            manifestOutputVarsSweepingOutput.getManifestsOutputVars();
        if (manifestOutcomeMap.containsKey(PRIMARY) && isNotEmpty(singleDeployManifestOutputs)) {
          Map<String, Object> primaryManifestYamlMap = (Map<String, Object>) manifestOutcomeMap.get(PRIMARY);
          primaryManifestYamlMap.putAll(singleDeployManifestOutputs);
          manifestOutcomeMap.putAll(singleDeployManifestOutputs);
        }

        manifestsOutputVars.forEach((manifestId, outputVars) -> {
          if (manifestOutcomeMap.containsKey(manifestId) && isNotEmpty(outputVars)) {
            Map<String, String> manifestYamlMap = (Map<String, String>) manifestOutcomeMap.get(manifestId);
            manifestYamlMap.putAll(outputVars);
          }
        });
      }

      stepOutcomes.add(StepResponse.StepOutcome.builder()
                           .name(MANIFEST_OUTCOME_EXPRESSION)
                           .group(StepCategory.STAGE.name())
                           .outcome(new ManifestsOutcome(manifestOutcomeMap))
                           .build());
    }
  }
}
