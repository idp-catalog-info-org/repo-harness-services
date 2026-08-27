/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator;

import static io.harness.beans.steps.outcome.CIOutcomeNames.INTEGRATION_STAGE_OUTCOME;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.INFRA_STEP_OUTCOME;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.unified.service.NGOutcomes.NG_OUTCOMES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.beans.steps.outcome.IntegrationStageOutcome;
import io.harness.cd.beans.moduleinfo.UnifiedInfraExecutionSummary;
import io.harness.cd.beans.moduleinfo.UnifiedInfraExecutionSummary.UnifiedInfraExecutionSummaryBuilder;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineCDInfo;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineCDInfo.UnifiedPipelineCDInfoBuilder;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineCIInfo;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineCIInfo.UnifiedPipelineCIInfoBuilder;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineExecutionModuleInfo;
import io.harness.cd.beans.moduleinfo.UnifiedPipelineExecutionModuleInfo.UnifiedPipelineExecutionModuleInfoBuilder;
import io.harness.cd.beans.moduleinfo.UnifiedServiceExecutionSummary;
import io.harness.cd.beans.moduleinfo.UnifiedServiceExecutionSummary.ArtifactsSummary.ArtifactsSummaryBuilder;
import io.harness.cd.beans.moduleinfo.UnifiedServiceExecutionSummary.UnifiedServiceExecutionSummaryBuilder;
import io.harness.cd.beans.moduleinfo.UnifiedStageModuleInfo;
import io.harness.cd.beans.moduleinfo.UnifiedStageModuleInfo.UnifiedStageModuleInfoBuilder;
import io.harness.cd.beans.outcomes.EnvGroupOutcome;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.cd.beans.outcomes.InfraStepOutcome;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.outcome.SidecarsOutcome;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.ManifestStoreInfo;
import io.harness.cdng.service.beans.ServiceOutcome;
import io.harness.ci.states.V1.cd.UnifiedCDInfraStep;
import io.harness.ci.states.V1.cd.UnifiedServiceStep;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.unified.service.NGOutcomes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Helper class for building unified CD module info from sweeping outputs.
 * Extracts service, environment, and infrastructure information for Unified Stages.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class UnifiedModuleInfoHelper {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public boolean isUnifiedServiceStepType(StepType stepType) {
    return stepType != null && Objects.equals(stepType.getType(), UnifiedServiceStep.STEP_TYPE.getType());
  }

  public boolean isUnifiedInfraStepType(StepType stepType) {
    return stepType != null && Objects.equals(stepType.getType(), UnifiedCDInfraStep.STEP_TYPE.getType());
  }

  public boolean isUnifiedServiceNodeAndCompleted(StepType stepType, Status status) {
    return isUnifiedServiceStepType(stepType) && StatusUtils.isFinalStatus(status);
  }

  public boolean isUnifiedInfraNodeAndCompleted(StepType stepType, Status status) {
    return isUnifiedInfraStepType(stepType) && StatusUtils.isFinalStatus(status);
  }

  public Optional<ServiceOutcome> getServiceOutcome(Ambiance ambiance) {
    try {
      OptionalSweepingOutput ngOutcomesSweepingOutput = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES));
      if (!ngOutcomesSweepingOutput.isFound()) {
        return Optional.empty();
      }

      VariablesSweepingOutput ngOutcomes = (VariablesSweepingOutput) ngOutcomesSweepingOutput.getOutput();
      if (ngOutcomes == null || !ngOutcomes.containsKey(NGOutcomes.SERVICE.getName())) {
        return Optional.empty();
      }

      String serviceYamlString = (String) ngOutcomes.get(NGOutcomes.SERVICE.getName());
      if (isEmpty(serviceYamlString)) {
        return Optional.empty();
      }

      // Parse YAML string directly to ServiceOutcome
      ServiceOutcome serviceOutcome = YamlUtils.read(serviceYamlString, ServiceOutcome.class);
      return Optional.ofNullable(serviceOutcome);
    } catch (Exception ex) {
      log.warn("Failed to fetch service outcome from NG_OUTCOMES", ex);
      return Optional.empty();
    }
  }

  public Optional<InfraStepOutcome> getInfraStepOutcome(Ambiance ambiance) {
    try {
      OptionalSweepingOutput infraStepSweepingOutput = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME));
      if (!infraStepSweepingOutput.isFound()) {
        return Optional.empty();
      }

      InfraStepOutcome infraStepOutcome = (InfraStepOutcome) infraStepSweepingOutput.getOutput();
      if (infraStepOutcome != null) {
        // Populate fields from HashMap after deserialization
        infraStepOutcome.populateFieldsFromMap();
      }
      return Optional.ofNullable(infraStepOutcome);
    } catch (Exception ex) {
      log.warn("Failed to fetch infrastructure outcome from INFRA_STEP_OUTCOME", ex);
      return Optional.empty();
    }
  }

  private Optional<VariablesSweepingOutput> getNgOutcomes(Ambiance ambiance) {
    try {
      OptionalSweepingOutput ngOutcomesSweepingOutput = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES));
      if (ngOutcomesSweepingOutput.isFound()) {
        return Optional.of((VariablesSweepingOutput) ngOutcomesSweepingOutput.getOutput());
      }
    } catch (Exception ex) {
      log.warn("Failed to fetch NG_OUTCOMES", ex);
    }
    return Optional.empty();
  }

  public Optional<ArtifactsOutcome> getArtifactsOutcome(Ambiance ambiance) {
    try {
      Optional<VariablesSweepingOutput> ngOutcomesOpt = getNgOutcomes(ambiance);
      if (ngOutcomesOpt.isEmpty()) {
        return Optional.empty();
      }

      VariablesSweepingOutput ngOutcomes = ngOutcomesOpt.get();
      if (!ngOutcomes.containsKey(NGOutcomes.ARTIFACTS.getName())) {
        return Optional.empty();
      }

      String artifactsYamlString = (String) ngOutcomes.get(NGOutcomes.ARTIFACTS.getName());
      if (isEmpty(artifactsYamlString)) {
        return Optional.empty();
      }

      // Parse YAML string to ArtifactsOutcome
      ArtifactsOutcome artifactsOutcome = YamlUtils.read(artifactsYamlString, ArtifactsOutcome.class);
      return Optional.ofNullable(artifactsOutcome);
    } catch (Exception ex) {
      log.warn("Failed to fetch artifacts outcome from NG_OUTCOMES", ex);
      return Optional.empty();
    }
  }

  public Optional<ManifestsOutcome> getManifestsOutcome(Ambiance ambiance) {
    try {
      Optional<VariablesSweepingOutput> ngOutcomesOpt = getNgOutcomes(ambiance);
      if (ngOutcomesOpt.isEmpty()) {
        return Optional.empty();
      }

      VariablesSweepingOutput ngOutcomes = ngOutcomesOpt.get();
      if (!ngOutcomes.containsKey(NGOutcomes.MANIFESTS.getName())) {
        return Optional.empty();
      }

      String manifestsYamlString = (String) ngOutcomes.get(NGOutcomes.MANIFESTS.getName());
      if (isEmpty(manifestsYamlString)) {
        return Optional.empty();
      }

      // Parse YAML string to typed ManifestsOutcome
      ManifestsOutcome manifestsOutcome = YamlUtils.read(manifestsYamlString, ManifestsOutcome.class);
      return Optional.ofNullable(manifestsOutcome);
    } catch (Exception ex) {
      log.warn("Failed to fetch manifests outcome from NG_OUTCOMES", ex);
      return Optional.empty();
    }
  }

  public UnifiedPipelineExecutionModuleInfo buildUnifiedPipelineExecutionModuleInfoFromServiceStep(
      OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String stageExecutionId = ambiance.getStageExecutionId();

    UnifiedPipelineExecutionModuleInfoBuilder builder = UnifiedPipelineExecutionModuleInfo.builder();

    // Populate aggregated pipeline-level CD info
    UnifiedPipelineCDInfoBuilder pipelineCDInfoBuilder = UnifiedPipelineCDInfo.builder();
    populatePipelineLevelServiceInfo(ambiance, pipelineCDInfoBuilder);
    builder.pipelineCDInfo(pipelineCDInfoBuilder.build());

    // Build stage-level service info
    UnifiedStageModuleInfo stageModuleInfo = buildUnifiedStageModuleInfoFromServiceStep(ambiance);
    if (stageModuleInfo != null && isNotEmpty(stageExecutionId)) {
      builder.stageInfo(stageExecutionId, stageModuleInfo);
    }

    return builder.build();
  }

  public UnifiedPipelineExecutionModuleInfo buildUnifiedPipelineExecutionModuleInfoFromInfraStep(
      OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String stageExecutionId = ambiance.getStageExecutionId();

    UnifiedPipelineExecutionModuleInfoBuilder builder = UnifiedPipelineExecutionModuleInfo.builder();

    // Populate aggregated pipeline-level CD info
    UnifiedPipelineCDInfoBuilder pipelineCDInfoBuilder = UnifiedPipelineCDInfo.builder();
    populatePipelineLevelInfraInfo(ambiance, pipelineCDInfoBuilder);
    builder.pipelineCDInfo(pipelineCDInfoBuilder.build());

    // Build stage-level infra info
    UnifiedStageModuleInfo stageModuleInfo = buildUnifiedStageModuleInfoFromInfraStep(ambiance);
    if (stageModuleInfo != null && isNotEmpty(stageExecutionId)) {
      builder.stageInfo(stageExecutionId, stageModuleInfo);
    }

    return builder.build();
  }

  private void populatePipelineLevelServiceInfo(Ambiance ambiance, UnifiedPipelineCDInfoBuilder builder) {
    try {
      Optional<ServiceOutcome> serviceOutcome = getServiceOutcome(ambiance);
      serviceOutcome.ifPresent(outcome -> {
        if (isNotEmpty(outcome.getIdentifier())) {
          builder.serviceIdentifier(outcome.getIdentifier());
        }
        if (isNotEmpty(outcome.getType())) {
          builder.serviceType(outcome.getType());
        }
      });

      // Populate artifact display names at pipeline level
      Optional<ArtifactsOutcome> artifactsOutcome = getArtifactsOutcome(ambiance);
      artifactsOutcome.ifPresent(outcome -> {
        if (outcome.getPrimary() != null && outcome.getPrimary().getArtifactSummary() != null) {
          List<String> artifactDisplayNames = buildArtifactDisplayNames(
              outcome.getPrimary().getMetaTags(), outcome.getPrimary().getArtifactSummary().getDisplayName());
          builder.artifactDisplayNames(artifactDisplayNames);
        }
      });
    } catch (Exception ex) {
      log.warn("Failed to populate pipeline-level service info", ex);
    }
  }

  private List<String> buildArtifactDisplayNames(Set<String> metaTags, String displayName) {
    Set<String> names = new HashSet<>();
    if (isNotEmpty(displayName)) {
      names.add(displayName);
    }
    if (isNotEmpty(metaTags)) {
      names.addAll(metaTags.stream().filter(StringUtils::isNotBlank).collect(Collectors.toSet()));
    }
    return new ArrayList<>(names);
  }

  private void populatePipelineLevelInfraInfo(Ambiance ambiance, UnifiedPipelineCDInfoBuilder builder) {
    try {
      Optional<InfraStepOutcome> infraStepOutcomeOpt = getInfraStepOutcome(ambiance);
      infraStepOutcomeOpt.ifPresent(infraOutcome -> {
        if (isNotEmpty(infraOutcome.getIdentifier())) {
          builder.infrastructureIdentifier(infraOutcome.getIdentifier());
        }
        if (isNotEmpty(infraOutcome.getName())) {
          builder.infrastructureName(infraOutcome.getName());
        }
        if (isNotEmpty(infraOutcome.getKind())) {
          builder.infrastructureType(infraOutcome.getKind());
        }

        // Environment info from InfraStepOutcome
        EnvironmentOutcome env = infraOutcome.getEnvironment();
        if (env != null) {
          if (isNotEmpty(env.getIdentifier())) {
            builder.envIdentifier(env.getIdentifier());
          }
          if (env.getType() != null) {
            builder.environmentType(env.getType());
          }
          EnvGroupOutcome envGroup = env.getGroup();
          if (envGroup != null && isNotEmpty(envGroup.getRef())) {
            builder.envGroupIdentifier(envGroup.getRef());
          }
        }
      });
    } catch (Exception ex) {
      log.warn("Failed to populate pipeline-level infra info", ex);
    }
  }

  public UnifiedStageModuleInfo buildUnifiedStageModuleInfoFromServiceStep(Ambiance ambiance) {
    UnifiedStageModuleInfoBuilder builder = UnifiedStageModuleInfo.builder();
    populateServiceInfo(ambiance, builder);
    return builder.build();
  }

  public UnifiedStageModuleInfo buildUnifiedStageModuleInfoFromInfraStep(Ambiance ambiance) {
    UnifiedStageModuleInfoBuilder builder = UnifiedStageModuleInfo.builder();
    populateInfraInfo(ambiance, builder);
    return builder.build();
  }

  private void populateServiceInfo(Ambiance ambiance, UnifiedStageModuleInfoBuilder builder) {
    try {
      Optional<ServiceOutcome> serviceOutcome = getServiceOutcome(ambiance);
      serviceOutcome.ifPresent(outcome -> {
        UnifiedServiceExecutionSummaryBuilder serviceBuilder = UnifiedServiceExecutionSummary.builder()
                                                                   .identifier(outcome.getIdentifier())
                                                                   .displayName(outcome.getName())
                                                                   .deploymentType(outcome.getType());
        // Add artifacts info
        populateArtifactsInfo(ambiance, serviceBuilder);

        // Add manifests info
        populateManifestsInfo(ambiance, serviceBuilder);

        builder.serviceInfo(serviceBuilder.build());
      });
    } catch (Exception ex) {
      log.warn("Failed to populate service info", ex);
    }
  }

  private void populateInfraInfo(Ambiance ambiance, UnifiedStageModuleInfoBuilder builder) {
    try {
      Optional<InfraStepOutcome> infraStepOutcomeOpt = getInfraStepOutcome(ambiance);
      infraStepOutcomeOpt.ifPresent(infraOutcome -> {
        UnifiedInfraExecutionSummaryBuilder infraBuilder = UnifiedInfraExecutionSummary.builder()
                                                               .infrastructureIdentifier(infraOutcome.getIdentifier())
                                                               .infrastructureName(infraOutcome.getName())
                                                               .infrastructureType(infraOutcome.getKind());

        // Environment info from InfraStepOutcome
        EnvironmentOutcome env = infraOutcome.getEnvironment();
        if (env != null) {
          infraBuilder.identifier(env.getIdentifier())
              .name(env.getName())
              .type(env.getType() != null ? env.getType().name() : null);

          EnvGroupOutcome envGroup = env.getGroup();
          if (envGroup != null) {
            infraBuilder.envGroupId(envGroup.getRef()).envGroupName(envGroup.getName());
          }
        }

        builder.infraExecutionSummary(infraBuilder.build());
      });
    } catch (Exception ex) {
      log.warn("Failed to populate infra info", ex);
    }
  }

  private void populateArtifactsInfo(Ambiance ambiance, UnifiedServiceExecutionSummaryBuilder serviceBuilder) {
    try {
      Optional<ArtifactsOutcome> artifactsOutcomeOpt = getArtifactsOutcome(ambiance);
      artifactsOutcomeOpt.ifPresent(artifactsOutcome -> {
        ArtifactsSummaryBuilder artifactsSummaryBuilder = UnifiedServiceExecutionSummary.ArtifactsSummary.builder();

        // Primary artifact
        ArtifactOutcome primary = artifactsOutcome.getPrimary();
        if (primary != null) {
          ArtifactSummary primarySummary = primary.getArtifactSummary();
          artifactsSummaryBuilder.primary(primarySummary);
          if (primarySummary != null) {
            artifactsSummaryBuilder.artifactDisplayName(primarySummary.getDisplayName());
          }
        }

        // Sidecar artifacts
        SidecarsOutcome sidecars = artifactsOutcome.getSidecars();
        if (sidecars != null && !sidecars.isEmpty()) {
          List<ArtifactSummary> sidecarSummaries = new ArrayList<>();
          for (ArtifactOutcome sidecarOutcome : sidecars.values()) {
            if (sidecarOutcome != null) {
              ArtifactSummary sidecarSummary = sidecarOutcome.getArtifactSummary();
              if (sidecarSummary != null) {
                sidecarSummaries.add(sidecarSummary);
              }
            }
          }
          artifactsSummaryBuilder.sidecars(sidecarSummaries);
        }

        serviceBuilder.artifacts(artifactsSummaryBuilder.build());
      });
    } catch (Exception ex) {
      log.warn("Failed to populate artifacts info", ex);
    }
  }

  private void populateManifestsInfo(Ambiance ambiance, UnifiedServiceExecutionSummaryBuilder serviceBuilder) {
    try {
      Optional<ManifestsOutcome> manifestsOutcomeOpt = getManifestsOutcome(ambiance);
      manifestsOutcomeOpt.ifPresent(manifestsOutcome -> {
        ManifestStoreInfo manifestStoreInfo = mapManifestsOutcomeToSummary(manifestsOutcomeOpt);
        serviceBuilder.manifestInfo(manifestStoreInfo);
      });
    } catch (Exception ex) {
      log.warn("Failed to populate manifests info", ex);
    }
  }

  private ManifestStoreInfo mapManifestsOutcomeToSummary(Optional<ManifestsOutcome> manifestsOutcome) {
    if (manifestsOutcome.isEmpty()) {
      return ManifestStoreInfo.builder().build();
    }
    List<ManifestOutcome> manifestOutcomes = new ArrayList<>(manifestsOutcome.get().values());
    for (ManifestOutcome manifestOutcome : manifestOutcomes) {
      Optional<ManifestStoreInfo> manifestStoreInfo = manifestOutcome.toManifestStoreInfo();
      if (manifestStoreInfo.isPresent()) {
        return manifestStoreInfo.get();
      }
    }
    return ManifestStoreInfo.builder().build();
  }

  /**
   * Builds UnifiedPipelineExecutionModuleInfo from OrchestrationEvent when an Integration Stage completes.
   * Resolves IntegrationStageOutcome from the event and extracts CI artifacts for the Artifacts tab.
   * Populates both stage-level (stageInfoMap) and pipeline-level (pipelineCIInfo) aggregates.
   */
  public UnifiedPipelineExecutionModuleInfo buildUnifiedPipelineExecutionModuleInfoFromIntegrationStage(
      OrchestrationEvent event, OutcomeService outcomeService) {
    try {
      Ambiance ambiance = event.getAmbiance();
      String stageExecutionId = ambiance.getStageExecutionId();
      if (isEmpty(stageExecutionId)) {
        return null;
      }

      OptionalOutcome optionalOutcome =
          outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(INTEGRATION_STAGE_OUTCOME));
      if (!optionalOutcome.isFound() || optionalOutcome.getOutcome() == null) {
        return null;
      }

      Outcome outcome = optionalOutcome.getOutcome();
      if (!(outcome instanceof IntegrationStageOutcome integrationStageOutcome)) {
        return null;
      }

      UnifiedStageModuleInfo stageModuleInfo =
          buildUnifiedStageModuleInfoFromIntegrationStageOutcome(integrationStageOutcome);

      UnifiedPipelineCIInfo pipelineCIInfo = buildPipelineLevelCIInfo(integrationStageOutcome);

      return UnifiedPipelineExecutionModuleInfo.builder()
          .pipelineCIInfo(pipelineCIInfo)
          .stageInfo(stageExecutionId, stageModuleInfo)
          .build();
    } catch (Exception ex) {
      log.warn("Failed to build unified pipeline module info from IntegrationStageOutcome", ex);
      return null;
    }
  }

  private UnifiedStageModuleInfo buildUnifiedStageModuleInfoFromIntegrationStageOutcome(
      IntegrationStageOutcome integrationStageOutcome) {
    if (integrationStageOutcome == null) {
      return UnifiedStageModuleInfo.builder().build();
    }
    return UnifiedStageModuleInfo.builder()
        .ciImageArtifacts(isNotEmpty(integrationStageOutcome.getImageArtifacts())
                ? new HashSet<>(integrationStageOutcome.getImageArtifacts())
                : null)
        .ciFileArtifacts(isNotEmpty(integrationStageOutcome.getFileArtifacts())
                ? new HashSet<>(integrationStageOutcome.getFileArtifacts())
                : null)
        .ciSbomArtifacts(isNotEmpty(integrationStageOutcome.getSbomArtifacts())
                ? new HashSet<>(integrationStageOutcome.getSbomArtifacts())
                : null)
        .build();
  }

  private UnifiedPipelineCIInfo buildPipelineLevelCIInfo(IntegrationStageOutcome integrationStageOutcome) {
    UnifiedPipelineCIInfoBuilder builder = UnifiedPipelineCIInfo.builder();

    if (isNotEmpty(integrationStageOutcome.getImageArtifacts())) {
      integrationStageOutcome.getImageArtifacts().forEach(builder::imageArtifact);
    }
    if (isNotEmpty(integrationStageOutcome.getFileArtifacts())) {
      integrationStageOutcome.getFileArtifacts().forEach(builder::fileArtifact);
    }
    if (isNotEmpty(integrationStageOutcome.getSbomArtifacts())) {
      integrationStageOutcome.getSbomArtifacts().forEach(builder::sbomArtifact);
    }

    return builder.build();
  }
}
