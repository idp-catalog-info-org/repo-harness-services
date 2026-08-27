/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.rollback;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.delegate.task.helm.flag.HelmChartInfo;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.NativeHelmDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.GitOpsInstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.dtos.instanceinfo.NativeHelmInstanceInfoDTO;
import io.harness.dtos.rollback.GitOpsPostProdRollbackInfo;
import io.harness.dtos.rollback.GoogleMigPostProdRollbackInfo;
import io.harness.dtos.rollback.K8sPostProdRollbackInfo;
import io.harness.dtos.rollback.NativeHelmPostProdRollbackInfo;
import io.harness.dtos.rollback.PostProdRollbackSwimLaneInfo;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.entities.instanceinfo.NativeHelmInstanceInfo;
import io.harness.mappers.instanceinfo.InstanceInfoMapper;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class PostProdRollbackHelperUtils {
  @Inject private DeploymentSummaryService deploymentSummaryService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  public PostProdRollbackSwimLaneInfo getSwimlaneInfo(Instance instance) {
    Optional<ArtifactDetails> optionalCurrentArtifactDetail = Optional.ofNullable(instance.getPrimaryArtifact());
    Optional<DeploymentSummaryDTO> deploymentSummaryDTOOptional = getPreviousActiveInstanceArtifactDetails(instance);
    Optional<ArtifactDetails> optionalPreviousArtifactDetails =
        deploymentSummaryDTOOptional.map(DeploymentSummaryDTO::getArtifactDetails);
    switch (instance.getInstanceType()) {
      case K8S_INSTANCE:
        if (InfrastructureKind.GITOPS.equals(instance.getInfrastructureKind())) {
          GitOpsInstanceInfoDTO gitOpsInfo =
              (GitOpsInstanceInfoDTO) InstanceInfoMapper.toDTO(instance.getInstanceInfo());
          return GitOpsPostProdRollbackInfo.builder()
              .lastPipelineExecutionName(instance.getLastPipelineExecutionName())
              .lastPipelineExecutionId(instance.getLastPipelineExecutionId())
              .lastDeployedAt(instance.getLastDeployedAt())
              .envName(instance.getEnvName())
              .envIdentifier(instance.getEnvIdentifier())
              .currentArtifactDisplayName(
                  optionalCurrentArtifactDetail.map(ArtifactDetails::getDisplayName).orElse(null))
              .currentArtifactId(optionalCurrentArtifactDetail.map(ArtifactDetails::getArtifactId).orElse(null))
              .clusterIdentifier(gitOpsInfo.getClusterIdentifier())
              .agentIdentifier(gitOpsInfo.getAgentIdentifier())
              .appIdentifier(gitOpsInfo.getAppIdentifier())
              .build();
        }
        String currentk8sChartVersion = getCurrentChartVersion(instance);
        String previousk8sChartVersion = getPreviousChartVersion(deploymentSummaryDTOOptional);
        return K8sPostProdRollbackInfo.builder()
            .lastPipelineExecutionName(instance.getLastPipelineExecutionName())
            .lastPipelineExecutionId(instance.getLastPipelineExecutionId())
            .lastDeployedAt(instance.getLastDeployedAt())
            .envName(instance.getEnvName())
            .envIdentifier(instance.getEnvIdentifier())
            .infraName(instance.getInfraName())
            .infraIdentifier(instance.getInfraIdentifier())
            .currentArtifactDisplayName(
                getHelmArtifactOrChartVersionName(optionalCurrentArtifactDetail, currentk8sChartVersion))
            .currentArtifactId(optionalCurrentArtifactDetail.map(ArtifactDetails::getArtifactId).orElse(null))
            .previousArtifactDisplayName(
                getHelmArtifactOrChartVersionName(optionalPreviousArtifactDetails, previousk8sChartVersion))
            .previousArtifactId(optionalPreviousArtifactDetails.map(ArtifactDetails::getArtifactId).orElse(null))
            .build();

      case NATIVE_HELM_INSTANCE:
        String currentChartVersion = getCurrentChartVersion(instance);
        String previousChartVersion = getPreviousChartVersion(deploymentSummaryDTOOptional);
        return NativeHelmPostProdRollbackInfo.builder()
            .lastPipelineExecutionName(instance.getLastPipelineExecutionName())
            .lastPipelineExecutionId(instance.getLastPipelineExecutionId())
            .lastDeployedAt(instance.getLastDeployedAt())
            .envName(instance.getEnvName())
            .envIdentifier(instance.getEnvIdentifier())
            .infraName(instance.getInfraName())
            .infraIdentifier(instance.getInfraIdentifier())
            .currentArtifactDisplayName(
                getHelmArtifactOrChartVersionName(optionalCurrentArtifactDetail, currentChartVersion))
            .currentArtifactId(optionalCurrentArtifactDetail.map(ArtifactDetails::getArtifactId).orElse(null))
            .previousArtifactDisplayName(
                getHelmArtifactOrChartVersionName(optionalPreviousArtifactDetails, previousChartVersion))
            .previousArtifactId(optionalPreviousArtifactDetails.map(ArtifactDetails::getArtifactId).orElse(null))
            .build();

      case GOOGLE_MIG_INSTANCE:
        return GoogleMigPostProdRollbackInfo.builder()
            .lastPipelineExecutionName(instance.getLastPipelineExecutionName())
            .lastPipelineExecutionId(instance.getLastPipelineExecutionId())
            .lastDeployedAt(instance.getLastDeployedAt())
            .envName(instance.getEnvName())
            .envIdentifier(instance.getEnvIdentifier())
            .infraName(instance.getInfraName())
            .infraIdentifier(instance.getInfraIdentifier())
            .currentArtifactDisplayName(optionalCurrentArtifactDetail.map(ArtifactDetails::getDisplayName).orElse(null))
            .currentArtifactId(optionalCurrentArtifactDetail.map(ArtifactDetails::getArtifactId).orElse(null))
            .previousArtifactDisplayName(
                optionalPreviousArtifactDetails.map(ArtifactDetails::getDisplayName).orElse(null))
            .previousArtifactId(optionalPreviousArtifactDetails.map(ArtifactDetails::getArtifactId).orElse(null))
            .build();

      default:
        return null;
    }
  }

  @Nullable
  private static String getHelmArtifactOrChartVersionName(
      Optional<ArtifactDetails> optionalCurrentArtifactDetail, String currentChartVersion) {
    if (optionalCurrentArtifactDetail.isEmpty()) {
      return null;
    }

    String displayName = optionalCurrentArtifactDetail.get().getDisplayName();

    // displayName takes precedence if non-null and non-empty
    if (displayName != null && !displayName.isEmpty()) {
      return displayName;
    }
    // fallback to currentChartVersion
    if (currentChartVersion != null) {
      return currentChartVersion;
    }

    // if currentChartVersion is null
    // return whatever displayName was (could be null or empty)
    return displayName;
  }

  @Nullable
  private static String getPreviousChartVersion(Optional<DeploymentSummaryDTO> deploymentSummaryDTOOptional) {
    return deploymentSummaryDTOOptional.map(DeploymentSummaryDTO::getDeploymentInfoDTO)
        .filter(dto -> dto instanceof NativeHelmDeploymentInfoDTO || dto instanceof K8sDeploymentInfoDTO)
        .map(dto -> {
          if (dto instanceof NativeHelmDeploymentInfoDTO) {
            return ((NativeHelmDeploymentInfoDTO) dto).getHelmChartInfo();
          } else {
            return ((K8sDeploymentInfoDTO) dto).getHelmChartInfo();
          }
        })
        .filter(Objects::nonNull)
        .map(HelmChartInfo::getVersion)
        .orElse(null);
  }

  @Nullable
  private static String getCurrentChartVersion(Instance instance) {
    return Optional.ofNullable(instance)
        .map(Instance::getInstanceInfo)
        .filter(info -> info instanceof NativeHelmInstanceInfo || info instanceof K8sInstanceInfo)
        .map(info -> {
          if (info instanceof NativeHelmInstanceInfo) {
            return ((NativeHelmInstanceInfo) info).getHelmChartInfo();
          } else {
            return ((K8sInstanceInfo) info).getHelmChartInfo();
          }
        })
        .filter(Objects::nonNull)
        .map(HelmChartInfo::getVersion)
        .orElse(null);
  }

  @Nullable
  private String getRollbackTargetFromCurrentSummary(
      String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO) {
    return deploymentSummaryService.getNthDeploymentSummaryFromNow(1, instanceSyncKey, infrastructureMappingDTO, false)
        .map(DeploymentSummaryDTO::getDeploymentInfoDTO)
        .filter(dto -> dto instanceof K8sDeploymentInfoDTO)
        .map(dto -> ((K8sDeploymentInfoDTO) dto).getRollbackTargetVersion())
        .orElse(null);
  }

  private Optional<DeploymentSummaryDTO> getPreviousActiveInstanceArtifactDetails(Instance instance) {
    // GitOps instances have null infrastructureMappingId — skip the lookup to avoid
    // IllegalArgumentException from Spring Data's findById(null).
    if (instance.getInfrastructureMappingId() == null) {
      return Optional.empty();
    }
    Optional<InfrastructureMappingDTO> optionalInfrastructureMappingDTO =
        infrastructureMappingService.getByInfrastructureMappingId(instance.getInfrastructureMappingId());
    if (optionalInfrastructureMappingDTO.isEmpty()) {
      return Optional.empty();
    }

    return getDeploymentSummaryDtoOptional(optionalInfrastructureMappingDTO.get(), instance);
  }

  private Optional<DeploymentSummaryDTO> getDeploymentSummaryDtoOptional(
      InfrastructureMappingDTO infrastructureMappingDTO, Instance instance) {
    int N = 2;
    String instanceSyncKey = InstanceInfoMapper.toDTO(instance.getInstanceInfo()).prepareInstanceSyncHandlerKey();
    if (InstanceType.K8S_INSTANCE.equals(instance.getInstanceType())) {
      K8sInstanceInfoDTO k8sInstanceInfoDTO = (K8sInstanceInfoDTO) InstanceInfoMapper.toDTO(instance.getInstanceInfo());
      String blueGreenColor = k8sInstanceInfoDTO.getBlueGreenColor();
      String version = k8sInstanceInfoDTO.getVersion();
      if (isNotEmpty(blueGreenColor)) {
        N = 1;
        k8sInstanceInfoDTO.swapBlueGreenColor();
        instanceSyncKey = k8sInstanceInfoDTO.prepareInstanceSyncHandlerKey();
      } else if (isNotEmpty(version)) {
        String rollbackTarget = getRollbackTargetFromCurrentSummary(instanceSyncKey, infrastructureMappingDTO);
        if (isNotEmpty(rollbackTarget)) {
          k8sInstanceInfoDTO.setVersion(rollbackTarget);
          String targetKey = k8sInstanceInfoDTO.prepareInstanceSyncHandlerKey();
          return deploymentSummaryService.getNthDeploymentSummaryFromNow(1, targetKey, infrastructureMappingDTO, false);
        }
        return Optional.empty();
      }
    }
    if (InstanceType.NATIVE_HELM_INSTANCE.equals(instance.getInstanceType())) {
      NativeHelmInstanceInfoDTO nativeHelmInstanceInfoDTO =
          (NativeHelmInstanceInfoDTO) InstanceInfoMapper.toDTO(instance.getInstanceInfo());
      String blueGreenColor = nativeHelmInstanceInfoDTO.getBlueGreenColor();
      if (isNotEmpty(blueGreenColor)) {
        N = 1;
        nativeHelmInstanceInfoDTO.swapBlueGreenColor();
        instanceSyncKey = nativeHelmInstanceInfoDTO.prepareInstanceSyncHandlerKey();
      }
    }
    return deploymentSummaryService.getNthDeploymentSummaryFromNow(N, instanceSyncKey, infrastructureMappingDTO, false);
  }
}
