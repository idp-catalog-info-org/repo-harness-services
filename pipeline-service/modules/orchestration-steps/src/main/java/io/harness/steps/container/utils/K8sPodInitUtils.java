/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.ci.commonconstants.CIExecutionConstants.CI_UPLOAD_LOGS_VIA_HARNESS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.ACCOUNT_ID_ATTR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.ADDON_VOLUME;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.ADDON_VOL_MOUNT_PATH;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_CUSTOM_ERROR_CATEGORIZATION;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.CI_NEW_VERSION_GODOTENV;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_ENGINE_LOG_UPLOAD_CONCURRENCY_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_INCREASE_LOG_LIMIT;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_TEST_SUMMARY_OUTPUT_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_LOG_PREFIX_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.IMAGE_PATH_SPLIT_REGEX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LABEL_REGEX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LOG_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LOG_SERVICE_TOKEN_PLACEHOLDER;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LOG_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.ORG_ID_ATTR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PIPELINE_EXECUTION_ID_ATTR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PIPELINE_ID_ATTR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.POD_MAX_WAIT_UNTIL_READY_SECS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PROJECT_ID_ATTR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.SHARED_VOLUME_PREFIX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_VOLUME;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_WORK_DIR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.VOLUME_PREFIX;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_TOKEN_VARIABLE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.steps.container.constants.ContainerStepExecutionConstants.POD_NAME_PREFIX;
import static io.harness.steps.container.constants.ContainerStepExecutionConstants.STEP_ID_ATTR;
import static io.harness.steps.container.utils.ContainerStepResolverUtils.resolveOSType;
import static io.harness.steps.plugin.infrastructure.ContainerStepInfra.Type.KUBERNETES_DIRECT;

import static java.lang.Character.toLowerCase;
import static java.lang.String.format;
import static org.apache.commons.lang3.CharUtils.isAsciiAlphanumeric;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.quantity.unit.DecimalQuantityUnit;
import io.harness.beans.quantity.unit.StorageQuantityUnit;
import io.harness.beans.yaml.extended.MatchExpressions;
import io.harness.beans.yaml.extended.NodePolicy;
import io.harness.beans.yaml.extended.PodSpecOverlayWrapper;
import io.harness.beans.yaml.extended.TopologySpreadConstraints;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.k8.Capabilities;
import io.harness.beans.yaml.extended.infrastrucutre.k8.SecurityContext;
import io.harness.beans.yaml.extended.infrastrucutre.k8.Toleration;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.ConfigMapVolumeYaml;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.HostPathYaml;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml;
import io.harness.beans.yaml.extended.volumes.SecretVolumeYaml;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.utils.CIEnvironmentVariablesUtils;
import io.harness.ci.utils.QuantityUtils;
import io.harness.delegate.beans.ci.pod.ConfigMapVolume;
import io.harness.delegate.beans.ci.pod.ConfigMapVolume.ConfigMapVolumeBuilder;
import io.harness.delegate.beans.ci.pod.ContainerCapabilities;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume.EmptyDirVolumeBuilder;
import io.harness.delegate.beans.ci.pod.HostPathVolume;
import io.harness.delegate.beans.ci.pod.PVCVolume;
import io.harness.delegate.beans.ci.pod.PodLabelSelector;
import io.harness.delegate.beans.ci.pod.PodMatchExpressions;
import io.harness.delegate.beans.ci.pod.PodToleration;
import io.harness.delegate.beans.ci.pod.PodTopologySpreadConstraints;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.ci.pod.SecretVolume;
import io.harness.delegate.beans.ci.pod.SecretVolume.SecretVolumeBuilder;
import io.harness.encryption.Scope;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.K8TaskCommonUtils;
import io.harness.k8s.model.ImageDetails;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.YamlUtils;
import io.harness.steps.container.beans.ServiceEnvironmentVars;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.container.execution.output.ContainerDetailsSweepingOutput;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerEcsInfra;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.utils.CIScopeInfoHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class K8sPodInitUtils {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private SecretUtils secretUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;

  @Inject private PmsFeatureFlagHelper featureFlagHelper;
  @Inject private LogStreamingServiceConfiguration logStreamingServiceConfiguration;
  @Inject ContainerExecutionConfig containerExecutionConfig;
  @Inject LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject ContainerInitCpuMemHelper containerInitCpuMemHelper;
  @Inject(optional = true) @Nullable private NGSettingsClient settingsClient;
  @Inject(optional = true) @Nullable private CIScopeInfoHelper ciScopeInfoHelper;
  @Inject @Named("CiSecretResolutionExecutorService") private ExecutorService executorService;

  @Inject private STOServiceUtils stoServiceUtils;

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;
  static final String SOURCE = "123456789bcdfghjklmnpqrstvwxyz";
  static final Integer RANDOM_LENGTH = 8;
  private static final SecureRandom random = new SecureRandom();
  public static String UNRESOLVED_PARAMETER = "UNRESOLVED_PARAMETER";

  public String generatePodName(String identifier) {
    return POD_NAME_PREFIX + "-" + getK8PodIdentifier(identifier) + "-"
        + generateRandomAlphaNumericString(RANDOM_LENGTH);
  }

  private String getK8PodIdentifier(String identifier) {
    StringBuilder sb = new StringBuilder(15);
    for (char c : identifier.toCharArray()) {
      if (c == '_') {
        continue;
      }
      if (isAsciiAlphanumeric(c)) {
        sb.append(toLowerCase(c));
      }
      if (sb.length() == 15) {
        return sb.toString();
      }
    }
    return sb.toString();
  }

  private static String generateRandomAlphaNumericString(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(SOURCE.charAt(random.nextInt(SOURCE.length())));
    }
    return sb.toString();
  }

  public Map<String, String> getLabels(Ambiance ambiance, String stepId) {
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final String pipelineExecutionID = ambiance.getPlanExecutionId();

    Map<String, String> labels = new HashMap<>();
    if (isLabelAllowed(accountID)) {
      labels.put(ACCOUNT_ID_ATTR, accountID);
    }
    if (isLabelAllowed(orgID)) {
      labels.put(ORG_ID_ATTR, K8TaskCommonUtils.trimLabel(orgID));
    }
    if (isLabelAllowed(projectID)) {
      labels.put(PROJECT_ID_ATTR, K8TaskCommonUtils.trimLabel(projectID));
    }
    if (isLabelAllowed(pipelineID)) {
      labels.put(PIPELINE_ID_ATTR, K8TaskCommonUtils.trimLabel(pipelineID));
    }
    if (isLabelAllowed(pipelineExecutionID)) {
      labels.put(PIPELINE_EXECUTION_ID_ATTR, pipelineExecutionID);
    }
    if (isLabelAllowed(stepId)) {
      labels.put(STEP_ID_ATTR, K8TaskCommonUtils.trimLabel(stepId));
    }
    // todo(abhinav): check if anything else needed here
    return labels;
  }

  private boolean isLabelAllowed(String label) {
    if (label == null) {
      return false;
    }

    return label.matches(LABEL_REGEX);
  }

  public String getWorkDir() {
    return STEP_WORK_DIR;
  }

  public OSType getOS(ContainerStepInfra infrastructure) {
    if (infrastructure.getType() == ContainerStepInfra.Type.ECS_DIRECT) {
      // ECS direct has no OS in YAML; same default as missing K8s os (see resolveOSType).
      return resolveOSType(null);
    }
    if (infrastructure.getType() != KUBERNETES_DIRECT) {
      return OSType.Linux;
    }

    if (((ContainerK8sInfra) infrastructure).getSpec() == null) {
      throw new ContainerStepExecutionException("Input infrastructure can not be empty");
    }

    ContainerK8sInfra k8sDirectInfraYaml = (ContainerK8sInfra) infrastructure;
    return resolveOSType(k8sDirectInfraYaml.getSpec().getOs());
  }

  public int getPodWaitUntilReadTimeoutForEcs(ContainerEcsInfra ecsInfra) {
    ParameterField<String> timeout = ecsInfra.getSpec().getInitTimeout();
    int waitSecs = POD_MAX_WAIT_UNTIL_READY_SECS;
    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      long timeoutInMillis = Timeout.fromString((String) timeout.fetchFinalValue()).getTimeoutInMillis();
      waitSecs = (int) (timeoutInMillis / 1000);
    }
    return waitSecs;
  }

  public Map<String, String> getVolumeToMountPath(
      List<String> sharedPaths, List<PodVolume> volumes, boolean includeAddonVolume) {
    Map<String, String> volumeToMountPath = new HashMap<>();
    int index = 0;
    if (sharedPaths != null) {
      for (String path : sharedPaths) {
        if (isEmpty(path)) {
          continue;
        }

        String volumeName = format("%s%d", SHARED_VOLUME_PREFIX, index);
        if (path.equals(STEP_MOUNT_PATH) || path.equals(ADDON_VOL_MOUNT_PATH)) {
          throw new InvalidRequestException(format("Shared path: %s is a reserved keyword ", path));
        }
        volumeToMountPath.put(volumeName, path);
        index++;
      }
    }

    volumeToMountPath.put(STEP_VOLUME, STEP_MOUNT_PATH);
    if (includeAddonVolume) {
      volumeToMountPath.put(ADDON_VOLUME, ADDON_VOL_MOUNT_PATH);
    }

    if (isNotEmpty(volumes)) {
      for (PodVolume volume : volumes) {
        if (volume.getType() == PodVolume.Type.EMPTY_DIR) {
          EmptyDirVolume emptyDirVolume = (EmptyDirVolume) volume;
          volumeToMountPath.put(emptyDirVolume.getName(), emptyDirVolume.getMountPath());
        } else if (volume.getType() == PodVolume.Type.HOST_PATH) {
          HostPathVolume hostPathVolume = (HostPathVolume) volume;
          volumeToMountPath.put(hostPathVolume.getName(), hostPathVolume.getMountPath());
        } else if (volume.getType() == PodVolume.Type.PVC) {
          PVCVolume pvcVolume = (PVCVolume) volume;
          volumeToMountPath.put(pvcVolume.getName(), pvcVolume.getMountPath());
        } else if (volume.getType() == PodVolume.Type.CONFIG_MAP) {
          ConfigMapVolume configMapVolume = (ConfigMapVolume) volume;
          volumeToMountPath.put(configMapVolume.getName(), configMapVolume.getMountPath());
        } else if (volume.getType() == PodVolume.Type.SECRET) {
          SecretVolume secretVolume = (SecretVolume) volume;
          volumeToMountPath.put(secretVolume.getName(), secretVolume.getMountPath());
        }
      }
    }
    return volumeToMountPath;
  }

  public List<PodVolume> convertDirectK8Volumes(ContainerK8sInfra k8sDirectInfraYaml, Ambiance ambiance) {
    List<PodVolume> podVolumes = new ArrayList<>();

    List<CIVolume> volumes = k8sDirectInfraYaml.getSpec().getVolumes().getValue();
    if (isEmpty(volumes)) {
      return podVolumes;
    }

    int index = 0;
    for (CIVolume volume : volumes) {
      String volumeName = format("%s%d", VOLUME_PREFIX, index);
      if (volume.getType() == CIVolume.Type.EMPTY_DIR) {
        podVolumes.add(convertEmptyDir(volumeName, (EmptyDirYaml) volume));
      } else if (volume.getType() == CIVolume.Type.HOST_PATH) {
        podVolumes.add(convertHostPath(volumeName, (HostPathYaml) volume));
      } else if (volume.getType() == CIVolume.Type.PERSISTENT_VOLUME_CLAIM) {
        podVolumes.add(convertPVCVolume(volumeName, (PersistentVolumeClaimYaml) volume));
      } else if (volume.getType() == CIVolume.Type.CONFIG_MAP) {
        podVolumes.add(convertConfigMapVolume(volumeName, (ConfigMapVolumeYaml) volume));
      } else if (volume.getType() == CIVolume.Type.SECRET) {
        podVolumes.add(convertSecretVolume(volumeName, (SecretVolumeYaml) volume));
      }

      index++;
    }
    return podVolumes;
  }

  public List<PodVolume> convertDirectEcsVolumes(ContainerEcsInfra ecsInfra) {
    List<PodVolume> podVolumes = new ArrayList<>();
    if (ecsInfra.getSpec() == null) {
      return podVolumes;
    }
    ParameterField<List<CIVolume>> volumesField = ecsInfra.getSpec().getVolumes();
    if (volumesField == null || isEmpty(volumesField.getValue())) {
      return podVolumes;
    }
    int index = 0;
    for (CIVolume volume : volumesField.getValue()) {
      String volumeName = format("%s%d", VOLUME_PREFIX, index);
      if (volume.getType() == CIVolume.Type.EMPTY_DIR) {
        podVolumes.add(convertEmptyDir(volumeName, (EmptyDirYaml) volume));
      } else if (volume.getType() == CIVolume.Type.HOST_PATH) {
        podVolumes.add(convertHostPath(volumeName, (HostPathYaml) volume));
      } else {
        throw new ContainerStepExecutionException(
            "ECS direct infrastructure supports only EmptyDir and HostPath in spec.volumes; unsupported type: "
            + volume.getType());
      }
      index++;
    }
    return podVolumes;
  }

  private EmptyDirVolume convertEmptyDir(String volumeName, EmptyDirYaml emptyDirYaml) {
    if (emptyDirYaml.getSpec() == null) {
      throw new ContainerStepExecutionException(
          "Invalid volume configuration: 'spec' is null. Please ensure the volume follows Harness format: volumes: [{ "
          + "mountPath: path, type: volumeType, spec: {} }]");
    }

    EmptyDirVolumeBuilder emptyDirVolumeBuilder = EmptyDirVolume.builder()
                                                      .name(volumeName)
                                                      .mountPath(emptyDirYaml.getMountPath().getValue())
                                                      .medium(emptyDirYaml.getSpec().getMedium().getValue());
    String sizeStr = emptyDirYaml.getSpec().getSize().getValue();
    if (isNotEmpty(sizeStr)) {
      emptyDirVolumeBuilder.sizeMib(QuantityUtils.getStorageQuantityValueInUnit(sizeStr, StorageQuantityUnit.Mi));
    }
    return emptyDirVolumeBuilder.build();
  }

  private HostPathVolume convertHostPath(String volumeName, HostPathYaml hostPathYaml) {
    return HostPathVolume.builder()
        .name(volumeName)
        .mountPath(hostPathYaml.getMountPath().getValue())
        .path(hostPathYaml.getSpec().getPath().getValue())
        .hostPathType(hostPathYaml.getSpec().getType().getValue())
        .build();
  }

  private PVCVolume convertPVCVolume(String volumeName, PersistentVolumeClaimYaml pvcYaml) {
    ParameterField<Boolean> readOnly = pvcYaml.getSpec().getReadOnly();
    return PVCVolume.builder()
        .name(volumeName)
        .mountPath(pvcYaml.getMountPath().getValue())
        .claimName(pvcYaml.getSpec().getClaimName().getValue())
        .readOnly(readOnly != null ? readOnly.getValue() : Boolean.FALSE)
        .build();
  }

  private ConfigMapVolume convertConfigMapVolume(String volumeName, ConfigMapVolumeYaml configMapYaml) {
    ConfigMapVolumeBuilder configMapVolumeBuilder = ConfigMapVolume.builder()
                                                        .name(volumeName)
                                                        .mountPath(configMapYaml.getMountPath().getValue())
                                                        .configMapName(configMapYaml.getSpec().getName().getValue());

    ParameterField<Boolean> optionalField = configMapYaml.getSpec().getOptional();
    if (ParameterField.isNotNull(optionalField)) {
      configMapVolumeBuilder.optional(optionalField.getValue());
    }
    return configMapVolumeBuilder.build();
  }

  private SecretVolume convertSecretVolume(String volumeName, SecretVolumeYaml secretVolumeYaml) {
    SecretVolumeBuilder secretVolumeBuilder = SecretVolume.builder()
                                                  .name(volumeName)
                                                  .mountPath(secretVolumeYaml.getMountPath().getValue())
                                                  .secretName(secretVolumeYaml.getSpec().getName().getValue());
    ParameterField<Boolean> optionalField = secretVolumeYaml.getSpec().getOptional();
    if (ParameterField.isNotNull(optionalField)) {
      secretVolumeBuilder.optional(optionalField.getValue());
    }
    return secretVolumeBuilder.build();
  }

  private List<Toleration> resolveTolerations(ParameterField<List<Toleration>> tolerations) {
    if (tolerations == null || tolerations.isExpression() || tolerations.getValue() == null) {
      return null;
    } else {
      return tolerations.getValue();
    }
  }

  public List<PodToleration> getPodTolerations(ParameterField<List<Toleration>> parameterizedTolerations) {
    List<PodToleration> podTolerations = new ArrayList<>();
    List<Toleration> tolerations = resolveTolerations(parameterizedTolerations);
    if (tolerations == null) {
      return podTolerations;
    }

    for (Toleration toleration : tolerations) {
      String effect = ExpressionResolverUtils.resolveStringParameter(
          "effect", null, "infrastructure", toleration.getEffect(), false);
      String key =
          ExpressionResolverUtils.resolveStringParameter("key", null, "infrastructure", toleration.getKey(), false);
      String operator = ExpressionResolverUtils.resolveStringParameter(
          "operator", null, "infrastructure", toleration.getOperator(), false);
      String value =
          ExpressionResolverUtils.resolveStringParameter("value", null, "infrastructure", toleration.getValue(), false);
      Integer tolerationSeconds =
          ExpressionResolverUtils.resolveIntegerParameter(toleration.getTolerationSeconds(), null);

      validateTolerationEffect(effect);
      validateTolerationOperator(operator);

      podTolerations.add(PodToleration.builder()
                             .effect(effect)
                             .key(key)
                             .operator(operator)
                             .value(value)
                             .tolerationSeconds(tolerationSeconds)
                             .build());
    }
    return podTolerations;
  }

  public List<PodTopologySpreadConstraints> getTopologySpreadConstraintsList(
      ParameterField<String> parameterizedPodSpecOverlay) {
    List<PodTopologySpreadConstraints> podTopologySpreadConstraintsList = new ArrayList<>();
    String specYaml =
        resolveStringParameter("key", null, "topologySpreadConstraints", parameterizedPodSpecOverlay, false);
    PodSpecOverlayWrapper podSpecOverlayWrapper = null;

    if (isEmpty(specYaml)) {
      return podTopologySpreadConstraintsList;
    }

    try {
      podSpecOverlayWrapper = new YamlUtils().read(specYaml, PodSpecOverlayWrapper.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read spec yaml for podSpecOverlay", e);
    }

    List<TopologySpreadConstraints> topologySpreadConstraintsList =
        podSpecOverlayWrapper.getTopologySpreadConstraints();
    if (isEmpty(topologySpreadConstraintsList)) {
      return podTopologySpreadConstraintsList;
    }

    for (TopologySpreadConstraints topologySpreadConstraints : topologySpreadConstraintsList) {
      podTopologySpreadConstraintsList.add(getTopologySpreadConstraints(topologySpreadConstraints));
    }
    return podTopologySpreadConstraintsList;
  }

  private PodTopologySpreadConstraints getTopologySpreadConstraints(
      TopologySpreadConstraints topologySpreadConstraints) {
    Map<String, String> podMatchLabels = new HashMap<>();
    List<PodMatchExpressions> podMatchExpressions = new ArrayList<>();
    if (topologySpreadConstraints.getLabelSelector() != null) {
      if (!isEmpty(topologySpreadConstraints.getLabelSelector().getMatchLabels())) {
        podMatchLabels = topologySpreadConstraints.getLabelSelector().getMatchLabels();
      }

      if (!isEmpty(topologySpreadConstraints.getLabelSelector().getMatchExpressions())) {
        podMatchExpressions = getMatchExpressions(topologySpreadConstraints.getLabelSelector().getMatchExpressions());
      }
    }

    List<String> matchLabelsKeys = topologySpreadConstraints.getMatchLabelKeys();
    PodLabelSelector podLabelSelector =
        PodLabelSelector.builder().matchLabels(podMatchLabels).matchExpressions(podMatchExpressions).build();
    String nodeAffinity = topologySpreadConstraints.getNodeAffinityPolicy() == null
        ? null
        : NodePolicy.fromString(topologySpreadConstraints.getNodeAffinityPolicy().toString()).getYamlName();
    String nodeTaintsPolicy = topologySpreadConstraints.getNodeTaintsPolicy() == null
        ? null
        : NodePolicy.fromString(topologySpreadConstraints.getNodeTaintsPolicy().toString()).getYamlName();
    return PodTopologySpreadConstraints.builder()
        .maxSkew(topologySpreadConstraints.getMaxSkew())
        .minDomains(topologySpreadConstraints.getMinDomains())
        .topologyKey(topologySpreadConstraints.getTopologyKey())
        .whenUnsatisfiable(topologySpreadConstraints.getWhenUnsatisfiable())
        .labelSelector(podLabelSelector)
        .matchLabelKeys(matchLabelsKeys)
        .nodeAffinityPolicy(nodeAffinity)
        .nodeTaintsPolicy(nodeTaintsPolicy)
        .build();
  }

  private List<PodMatchExpressions> getMatchExpressions(List<MatchExpressions> matchExpressionsList) {
    List<PodMatchExpressions> podMatchExpressionsList = new ArrayList<>();

    if (matchExpressionsList == null) {
      return podMatchExpressionsList;
    }

    for (MatchExpressions matchExpressions : matchExpressionsList) {
      podMatchExpressionsList.add(PodMatchExpressions.builder()
                                      .key(matchExpressions.getKey())
                                      .operator(matchExpressions.getOperator())
                                      .values(matchExpressions.getValues())
                                      .build());
    }
    return podMatchExpressionsList;
  }

  private void validateTolerationEffect(String effect) {
    if (isNotEmpty(effect)) {
      if (!effect.equals("NoSchedule") && !effect.equals("PreferNoSchedule") && !effect.equals("NoExecute")) {
        throw new ContainerStepExecutionException(format("Invalid value %s for effect in toleration", effect));
      }
    }
  }

  private void validateTolerationOperator(String operator) {
    if (isNotEmpty(operator)) {
      if (!operator.equals("Equal") && !operator.equals("Exists")) {
        throw new ContainerStepExecutionException(format("Invalid value %s for operator in toleration", operator));
      }
    }
  }

  public int getPodWaitUntilReadTimeout(ContainerK8sInfra k8sDirectInfraYaml) {
    ParameterField<String> timeout = k8sDirectInfraYaml.getSpec().getInitTimeout();

    int podWaitUntilReadyTimeout = POD_MAX_WAIT_UNTIL_READY_SECS;
    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      long timeoutInMillis = Timeout.fromString((String) timeout.fetchFinalValue()).getTimeoutInMillis();
      podWaitUntilReadyTimeout = (int) (timeoutInMillis / 1000);
    }
    return podWaitUntilReadyTimeout;
  }

  public boolean shouldSkipImagePullSecret(Ambiance ambiance) {
    if (settingsClient == null) {
      return false; // Default to not skipping if settings client is null
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    try {
      // If STEP_GROUP_IMAGE_PULL_SECRET_PROVIDED is true, we should NOT skip (return false)
      // If STEP_GROUP_IMAGE_PULL_SECRET_PROVIDED is false, we should skip (return true)
      String value =
          NGRestUtils
              .getResponse(settingsClient.getSetting(SettingIdentifiers.STEP_GROUP_IMAGE_PULL_SECRET_PROVIDED,
                  accountId, orgIdentifier, projectIdentifier))
              .getValue();

      return !Boolean.parseBoolean(value); // Negate the value
    } catch (Exception e) {
      log.warn("Failed to get harness setting {} value for account {}, org {}, project {}",
          SettingIdentifiers.STEP_GROUP_IMAGE_PULL_SECRET_PROVIDED, accountId, orgIdentifier, projectIdentifier);
      return false; // Default to not skipping if there's an error
    }
  }

  public ContainerSecurityContext getCtrSecurityContext(ContainerK8sInfra infrastructure) {
    if (infrastructure.getSpec() == null) {
      throw new ContainerStepExecutionException("Input infrastructure can not be empty");
    }
    OSType os = getOS(infrastructure);
    ParameterField<SecurityContext> scField = infrastructure.getSpec().getContainerSecurityContext();
    SecurityContext securityContext = scField != null ? scField.getValue() : null;

    if (securityContext == null || os == OSType.Windows) {
      return ContainerSecurityContext.builder().build();
    }
    return buildContainerSecurityContextFromYaml(securityContext);
  }

  public ContainerSecurityContext getCtrSecurityContext(ContainerEcsInfra infrastructure) {
    if (infrastructure.getSpec() == null) {
      throw new ContainerStepExecutionException("Input infrastructure can not be empty");
    }
    ParameterField<SecurityContext> scField = infrastructure.getSpec().getContainerSecurityContext();
    if (scField == null || scField.getValue() == null) {
      return ContainerSecurityContext.builder().build();
    }
    return buildContainerSecurityContextFromYaml(scField.getValue());
  }

  private static ContainerSecurityContext buildContainerSecurityContextFromYaml(SecurityContext securityContext) {
    return ContainerSecurityContext.builder()
        .allowPrivilegeEscalation(booleanParameterValue(securityContext.getAllowPrivilegeEscalation()))
        .privileged(booleanParameterValue(securityContext.getPrivileged()))
        .procMount(stringParameterValue(securityContext.getProcMount()))
        .readOnlyRootFilesystem(booleanParameterValue(securityContext.getReadOnlyRootFilesystem()))
        .runAsNonRoot(booleanParameterValue(securityContext.getRunAsNonRoot()))
        .runAsGroup(integerParameterValue(securityContext.getRunAsGroup()))
        .runAsUser(ExpressionResolverUtils.resolveIntegerParameter(securityContext.getRunAsUser(), null))
        .capabilities(getCtrCapabilities(capabilitiesValue(securityContext.getCapabilities())))
        .build();
  }

  private static Boolean booleanParameterValue(ParameterField<Boolean> field) {
    return field == null || field.getValue() == null ? null : field.getValue();
  }

  private static String stringParameterValue(ParameterField<String> field) {
    return field == null || field.getValue() == null ? null : field.getValue();
  }

  private static Integer integerParameterValue(ParameterField<Integer> field) {
    return field == null || field.getValue() == null ? null : field.getValue();
  }

  private static Capabilities capabilitiesValue(ParameterField<Capabilities> field) {
    return field == null ? null : field.getValue();
  }

  private static ContainerCapabilities getCtrCapabilities(Capabilities capabilities) {
    if (capabilities == null) {
      return ContainerCapabilities.builder().build();
    }
    ParameterField<List<String>> addField = capabilities.getAdd();
    ParameterField<List<String>> dropField = capabilities.getDrop();
    List<String> add = addField != null && addField.getValue() != null ? addField.getValue() : null;
    List<String> drop = dropField != null && dropField.getValue() != null ? dropField.getValue() : null;
    return ContainerCapabilities.builder().add(add).drop(drop).build();
  }

  public <T extends ExecutionSweepingOutput> void consumeSweepingOutput(Ambiance ambiance, T value, String key) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(key));
    if (!optionalSweepingOutput.isFound()) {
      executionSweepingOutputResolver.consume(ambiance, key, value, StepCategory.STEP_GROUP.name());
    }
  }

  public Map<String, String> getLogServiceEnvVariables(ContainerDetailsSweepingOutput k8PodDetails, String accountID) {
    Map<String, String> envVars = new HashMap<>();
    final String logServiceBaseUrl = containerExecutionConfig.getLogStreamingContainerStepBaseUrl();
    log.info("log base url {}", logServiceBaseUrl);
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to fetch log service token attempt: {}"),
            format("Failed to fetch log service token after retrying {} times"));

    String logServiceToken = LOG_SERVICE_TOKEN_PLACEHOLDER;

    // Make a call to the log service and get back the token.
    try {
      logServiceToken = Failsafe.with(retryPolicy)
                            .get(()
                                     -> getLogServiceToken(accountID, logServiceBaseUrl,
                                         logStreamingServiceConfiguration.getServiceToken()));
    } catch (Exception e) {
      if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_DISABLE_LOG_SERVICE_RESILIENCE)) {
        throw e;
      }
      log.warn("Could not call token endpoint for log service", e);
    }

    envVars.put(LOG_SERVICE_TOKEN_VARIABLE, logServiceToken);
    envVars.put(LOG_SERVICE_ENDPOINT_VARIABLE, logServiceBaseUrl);

    return envVars;
  }

  @NotNull
  public Map<String, String> getSTOServiceEnvVariables(String accountId) {
    Map<String, String> envVars = new HashMap<>();
    final String stoServiceBaseUrl = stoServiceUtils.getStoServiceConfig().getBaseUrl();

    String stoServiceToken = "token";

    // Make a call to the STO service and get back the token.
    try {
      stoServiceToken = stoServiceUtils.getSTOServiceToken(accountId, List.of("sto-plugin"));
    } catch (Exception e) {
      log.error("Could not call token endpoint for STO service", e);
    }

    envVars.put(STO_SERVICE_TOKEN_VARIABLE, stoServiceToken);
    envVars.put(STO_SERVICE_ENDPOINT_VARIABLE, stoServiceBaseUrl);

    return envVars;
  }

  public ServiceEnvironmentVars getServiceEnvironmentVars(
      ContainerDetailsSweepingOutput k8PodDetails, String accountId) {
    Map<String, String> logEnvVars = getLogServiceEnvVariables(k8PodDetails, accountId);
    return ServiceEnvironmentVars.builder().logEnvVars(logEnvVars).stoEnvVars(Collections.emptyMap()).build();
  }

  public String getLogServiceToken(String accountID, String url, String token) {
    try {
      return logStreamingStepClientFactory.retrieveLogStreamingAccountToken(accountID);
    } catch (IOException e) {
      throw new GeneralException(format("Token request to log service call failed with url %s", url), e);
    }
  }

  public Map<String, String> getCommonStepEnvVariables(
      ContainerStepSpec containerStepInfo, String workDirPath, String logPrefix, Ambiance ambiance) {
    Map<String, String> envVars = new HashMap<>();
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    // Use stage identifier (like CI) if feature flag is enabled, otherwise use stage execution ID (runtime UUID)
    final String stageID = featureFlagHelper.isEnabled(accountID, FeatureName.CDS_CONTAINER_STEP_USE_STAGE_IDENTIFIER)
        ? AmbianceUtils.getStageIdentifierFromAmbiance(ambiance)
        : ambiance.getStageExecutionId();
    final String executionID = ambiance.getPlanExecutionId();

    String ciUploadLogsViaHarness = null;
    // Applying try-catch because there is a possibility that setting won't exist in case of ci-manager running with old
    // ng manager.
    if (settingsClient != null) {
      try {
        ciUploadLogsViaHarness = NGRestUtils
                                     .getResponse(settingsClient.getSetting(
                                         SettingIdentifiers.CI_UPLOAD_LOGS_VIA_HARNESS, accountID, null, null))
                                     .getValue();
      } catch (Exception e) {
        log.error("Setting {} is not found", CI_UPLOAD_LOGS_VIA_HARNESS);
      }
    }

    // Check whether FF to enable blob upload to log service (as opposed to directly blob storage) is enabled OR Setting
    // to Upload logs via log service is being set to true.
    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_INDIRECT_LOG_UPLOAD)
        || Boolean.parseBoolean(ciUploadLogsViaHarness)) {
      log.info("Indirect log upload FF is enabled or Setting to Upload logs via log service is being set to true for "
              + "accountID: {}",
          accountID);
      envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    }

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_ENABLE_TEST_SUMMARY_AS_OUTPUTS)) {
      envVars.put(HARNESS_CI_TEST_SUMMARY_OUTPUT_FF, "true");
    }

    if (containerStepInfo instanceof InitContainerV2StepInfo
        && featureFlagHelper.isEnabled(accountID, FeatureName.CI_ENABLE_MULTILINE_OUTPUTS_SECRETS)) {
      envVars.put(CI_NEW_VERSION_GODOTENV, "true");
    }

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING)) {
      envVars.put(CI_ENABLE_EXTRA_CHARACTERS_SECRETS_MASKING, "true");
    }

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_CUSTOM_ERROR_CATEGORIZATION)) {
      envVars.put(CI_CUSTOM_ERROR_CATEGORIZATION, "true");
    }

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_INCREASE_LOG_LIMIT)) {
      envVars.put(HARNESS_CI_INCREASE_LOG_LIMIT, "true");
    }

    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_ENGINE_LOG_UPLOAD_CONCURRENCY)) {
      envVars.put(HARNESS_CI_ENGINE_LOG_UPLOAD_CONCURRENCY_FF, "true");
    }

    // Add other environment variables needed in the containers
    envVars.put(HARNESS_WORKSPACE, workDirPath);
    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, accountID);
    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    envVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    envVars.put(HARNESS_LOG_PREFIX_VARIABLE, logPrefix);

    // Add parentUniqueId if feature flag is enabled
    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_USE_UNIQUE_PARENT_ID_FOR_QUERY)) {
      CIEnvironmentVariablesUtils.addParentUniqueIdToEnv(
          envVars, ambiance, accountID, orgID, projectID, ciScopeInfoHelper);
    }

    return envVars;
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  @NotNull
  public List<SecretVariableDetails> getSecretVariableDetails(NGAccess ngAccess,
      ContainerDefinitionInfo containerDefinitionInfo, List<SecretVariableDetails> scriptsSecretVariableDetails) {
    List<SecretVariableDetails> secretVariableDetails = new ArrayList<>();
    secretVariableDetails.addAll(scriptsSecretVariableDetails);
    if (isNotEmpty(containerDefinitionInfo.getSecretVariables())) {
      CompletableFutures<SecretVariableDetails> completableFutures = new CompletableFutures<>(executorService);
      containerDefinitionInfo.getSecretVariables().forEach(secretVariable
          -> completableFutures.supplyAsync(() -> secretUtils.getSecretVariableDetails(ngAccess, secretVariable)));
      try {
        secretVariableDetails.addAll(completableFutures.allOf().get(5, TimeUnit.MINUTES));
      } catch (Exception e) {
        throw new ContainerStepExecutionException(e.getMessage());
      }
    }
    return secretVariableDetails.stream().filter(Objects::nonNull).collect(Collectors.toList());
  }

  public Map<String, String> removeEnvVarsWithSecretRef(Map<String, String> envVars) {
    HashMap<String, String> hashMap = new HashMap<>();
    final Map<String, String> secretEnvVariables =
        envVars.entrySet()
            .stream()
            .filter(entry -> entry.getValue().contains("ngSecretManager"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    envVars.entrySet().removeAll(secretEnvVariables.entrySet());

    return secretEnvVariables;
  }

  public void checkSecretAccess(Ambiance ambiance, List<SecretVariableDetails> secretVariableDetails,
      String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    List<EntityDetail> entityDetails =
        secretVariableDetails.stream()
            .map(secretVariableDetail -> {
              Scope scope = secretVariableDetail.getSecretVariableDTO().getSecret().getScope();
              return createEntityDetails(
                  secretVariableDetail.getSecretVariableDTO().getSecret().toSecretRefStringValue(), accountIdentifier,
                  Scope.PROJECT.equals(scope) ? projectIdentifier : null,
                  (Scope.PROJECT.equals(scope) || Scope.ORG.equals(scope)) ? orgIdentifier : null);
            })
            .collect(Collectors.toList());

    if (isNotEmpty(entityDetails)) {
      pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, false);
    }
  }

  private EntityDetail createEntityDetails(
      String secretIdentifier, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(secretIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    return EntityDetail.builder().entityRef(connectorRef).type(EntityType.SECRETS).build();
  }

  public Pair<Integer, Integer> getStepLimits(
      ContainerStepSpec containerStepInfo, String accountId, boolean flexibleTemplateEnabled) {
    if (containerStepInfo instanceof InitContainerV2StepInfo) {
      return getStepGroupRequest((InitContainerV2StepInfo) containerStepInfo, accountId, flexibleTemplateEnabled);
    }

    ContainerResource resources = ((ContainerK8sInfra) containerStepInfo.getInfrastructure()).getSpec().getResources();
    Integer containerCpuLimit =
        getContainerCpuLimit(resources, "Container", containerStepInfo.getIdentifier(), accountId);
    Integer containerMemoryLimit =
        getContainerMemoryLimit(resources, "Container", containerStepInfo.getIdentifier(), accountId);

    return Pair.of(containerCpuLimit, containerMemoryLimit);
  }

  public Pair<Integer, Integer> getStepRequests(
      ContainerStepSpec containerStepInfo, String accountId, boolean flexibleTemplateEnabled) {
    if (containerStepInfo instanceof InitContainerV2StepInfo) {
      return getStepGroupRequest((InitContainerV2StepInfo) containerStepInfo, accountId, flexibleTemplateEnabled);
    }

    ContainerResource resources = ((ContainerK8sInfra) containerStepInfo.getInfrastructure()).getSpec().getResources();
    Integer containerCpuRequest =
        getContainerCpuRequest(resources, "Container", containerStepInfo.getIdentifier(), accountId);
    Integer containerMemoryRequest =
        getContainerMemoryRequest(resources, "Container", containerStepInfo.getIdentifier(), accountId);

    return Pair.of(containerCpuRequest, containerMemoryRequest);
  }

  public Pair<Integer, Integer> getStepGroupRequest(
      InitContainerV2StepInfo initContainerV2StepInfo, String accountId, boolean flexibleTemplateEnabled) {
    return containerInitCpuMemHelper.getStepGroupRequest(initContainerV2StepInfo, accountId, flexibleTemplateEnabled);
  }

  private Integer getContainerCpuLimit(ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer cpuLimit = null;

    if (resource != null && resource.getLimits() != null && resource.getLimits().getCpu() != null) {
      String cpuLimitQuantity =
          ExpressionResolverUtils.resolveStringParameter("cpu", stepType, stepId, resource.getLimits().getCpu(), false);
      if (isNotEmpty(cpuLimitQuantity) && !UNRESOLVED_PARAMETER.equals(cpuLimitQuantity)) {
        cpuLimit = QuantityUtils.getCpuQuantityValueInUnit(cpuLimitQuantity, DecimalQuantityUnit.m);
      }
    }
    return cpuLimit;
  }

  private Integer getContainerMemoryLimit(
      ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer memoryLimit = 0;
    if (resource != null && resource.getLimits() != null && resource.getLimits().getMemory() != null) {
      String memoryLimitMemoryQuantity = ExpressionResolverUtils.resolveStringParameter(
          "memory", stepType, stepId, resource.getLimits().getMemory(), false);
      if (isNotEmpty(memoryLimitMemoryQuantity) && !UNRESOLVED_PARAMETER.equals(memoryLimitMemoryQuantity)) {
        memoryLimit = QuantityUtils.getStorageQuantityValueInUnit(memoryLimitMemoryQuantity, StorageQuantityUnit.Mi);
      }
    }
    return memoryLimit;
  }

  private Integer getContainerCpuRequest(ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer cpuRequest = null;

    if (resource != null && resource.getRequests() != null && resource.getRequests().getCpu() != null) {
      String cpuRequestQuantity = ExpressionResolverUtils.resolveStringParameter(
          "cpu", stepType, stepId, resource.getRequests().getCpu(), false);
      if (isNotEmpty(cpuRequestQuantity) && !UNRESOLVED_PARAMETER.equals(cpuRequestQuantity)) {
        cpuRequest = QuantityUtils.getCpuQuantityValueInUnit(cpuRequestQuantity, DecimalQuantityUnit.m);
      }
    }
    return cpuRequest;
  }

  private Integer getContainerMemoryRequest(
      ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer memoryRequest = 0;
    if (resource != null && resource.getRequests() != null && resource.getRequests().getMemory() != null) {
      String memoryRequestQuantity = ExpressionResolverUtils.resolveStringParameter(
          "memory", stepType, stepId, resource.getRequests().getMemory(), false);
      if (isNotEmpty(memoryRequestQuantity) && !UNRESOLVED_PARAMETER.equals(memoryRequestQuantity)) {
        memoryRequest = QuantityUtils.getStorageQuantityValueInUnit(memoryRequestQuantity, StorageQuantityUnit.Mi);
      }
    }
    return memoryRequest;
  }

  public ImageDetails getImageInfo(String image) {
    String tag = "";
    String name = image;

    if (image.contains(IMAGE_PATH_SPLIT_REGEX)) {
      String[] subTokens = image.split(IMAGE_PATH_SPLIT_REGEX);
      if (subTokens.length > 1) {
        tag = subTokens[subTokens.length - 1];
        String[] nameparts = Arrays.copyOf(subTokens, subTokens.length - 1);
        name = String.join(IMAGE_PATH_SPLIT_REGEX, nameparts);
      }
    }

    return ImageDetails.builder().name(name).tag(tag).build();
  }
}
