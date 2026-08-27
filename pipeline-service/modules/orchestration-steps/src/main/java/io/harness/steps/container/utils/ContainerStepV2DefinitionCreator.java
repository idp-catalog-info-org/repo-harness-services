/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.beans.serializer.RunTimeInputHandler.UNRESOLVED_PARAMETER;
import static io.harness.ci.commonconstants.CIExecutionConstants.NULL_STR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_PREFIX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils.getKubernetesStandardPodName;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.quantity.unit.DecimalQuantityUnit;
import io.harness.beans.quantity.unit.StorageQuantityUnit;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.buildstate.StepContainerUtils;
import io.harness.ci.utils.QuantityUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.ImageDetailsUtils;
import io.harness.pms.sdk.core.plugin.SecretNgVariableUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepV2DefinitionCreator {
  public List<ContainerDefinitionInfo> getContainerDefinitionInfo(InitContainerV2StepInfo initContainerV2StepInfo,
      String stepGroupIdentifier, Ambiance ambiance, boolean isCustomResourceEnabled) {
    ParameterField<OSType> osField = getOsParameterField(initContainerV2StepInfo.getInfrastructure());
    OSType osForCommand = osField != null && !ParameterField.isNull(osField) && osField.getValue() != null
        ? OSType.getOSType(String.valueOf(osField.getValue()))
        : null;
    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();

    initContainerV2StepInfo.getPluginsData().forEach((stepInfo1, value) -> {
      for (PluginCreationResponseWrapper response : value.getResponseList()) {
        PluginDetails pluginDetails = response.getResponse().getPluginDetails();
        if (!response.getShouldSkip()) {
          StepInfoProto stepInfo = response.getStepInfo();
          String stepIdentifier = stepInfo.getIdentifier();
          if (isNotEmpty(stepGroupIdentifier)) {
            stepIdentifier = stepGroupIdentifier + "_" + stepIdentifier;
          }
          String identifier = getKubernetesStandardPodName(stepInfo.getIdentifier());
          String containerName = String.format("%s%s", STEP_PREFIX, identifier).toLowerCase();
          Map<String, String> envMap = new HashMap<>(pluginDetails.getEnvVariablesMap());
          Map<String, String> envVarsWithPlainTextSecret = new HashMap<>();
          if (EmptyPredicate.isNotEmpty(pluginDetails.getEnvVariablesWithPlainTextSecretMap())) {
            envVarsWithPlainTextSecret = new HashMap<>(pluginDetails.getEnvVariablesWithPlainTextSecretMap());
          }
          List<SecretNGVariable> secretNGVariableMap = pluginDetails.getSecretVariableList()
                                                           .stream()
                                                           .map(SecretNgVariableUtils::getSecretNgVariable)
                                                           .collect(Collectors.toList());
          String connectorIdentifier = pluginDetails.getImageDetails().getConnectorDetails().getConnectorRef();
          String registryRef = pluginDetails.getImageDetails().getConnectorDetails().getRegistryRef();

          Integer runAsUser = null;
          Boolean privileged = null;
          if (AmbianceUtils.checkIfFeatureFlagEnabled(
                  ambiance, FeatureName.CDS_CONTAINER_STEP_GROUP_RUN_AS_USER_AND_PRIVILEGED_FIX.name())) {
            if (pluginDetails.hasRunAsUserV1()) {
              runAsUser = pluginDetails.getRunAsUserV1().getValue();
            }
            if (pluginDetails.hasPrivilegedV1()) {
              privileged = pluginDetails.getPrivilegedV1().getValue();
            }
          } else {
            runAsUser = pluginDetails.getRunAsUser();
            privileged = pluginDetails.getPrivileged();
          }

          containerDefinitionInfos.add(
              ContainerDefinitionInfo.builder()
                  .name(containerName)
                  .commands(StepContainerUtils.getCommand(osForCommand))
                  .args(StepContainerUtils.getArguments(pluginDetails.getPortUsed(0)))
                  .envVars(envMap)
                  .envVarsWithPlainTextSecret(envVarsWithPlainTextSecret)
                  .secretVariables(secretNGVariableMap)
                  .containerImageDetails(
                      ContainerImageDetails.builder()
                          .imageDetails(
                              ImageDetailsUtils.getImageDetails(pluginDetails.getImageDetails().getImageInformation()))
                          .connectorIdentifier(EmptyPredicate.isEmpty(connectorIdentifier) ? null : connectorIdentifier)
                          .registryRef(EmptyPredicate.isEmpty(registryRef) ? null : registryRef)
                          .build())
                  .isHarnessManagedImage(
                      !pluginDetails.hasIsHarnessManaged() || pluginDetails.getIsHarnessManaged().getValue())
                  .containerResourceParams(
                      getContainerResourceParams(pluginDetails, stepInfo1, isCustomResourceEnabled))
                  // Using this as proto object is being serialized
                  .ports(new ArrayList<Integer>(pluginDetails.getPortUsedList()))
                  .containerType(CIContainerType.PLUGIN)
                  .stepIdentifier(stepIdentifier)
                  .stepName(stepInfo.getIdentifier())
                  .imagePullPolicy(StringValueUtils.getStringFromStringValue(
                      pluginDetails.getImageDetails().getImageInformation().getImagePullPolicy()))
                  .privileged(privileged)
                  .runAsUser(runAsUser)
                  .build());
        }
      }
    });
    return containerDefinitionInfos;
  }

  private static ParameterField<OSType> getOsParameterField(ContainerStepInfra infrastructure) {
    if (infrastructure instanceof ContainerK8sInfra) {
      return ((ContainerK8sInfra) infrastructure).getSpec().getOs();
    }
    return null;
  }

  private ContainerResourceParams getContainerResourceParams(
      PluginDetails pluginDetails, StepInfo stepInfo, boolean isCustomResourceEnabled) {
    Integer reqMem = STEP_REQUEST_MEMORY_MIB;
    Integer reqCpu = STEP_REQUEST_MILLI_CPU;

    Pair<Integer, Integer> resourceRequests =
        getCustomResourceRequests(reqMem, reqCpu, stepInfo, isCustomResourceEnabled);

    return ContainerResourceParams.builder()
        .resourceRequestMemoryMiB(resourceRequests.getLeft())
        .resourceRequestMilliCpu(resourceRequests.getRight())
        .resourceLimitMemoryMiB(pluginDetails.getResource().getMemory())
        .resourceLimitMilliCpu(pluginDetails.getResource().getCpu())
        .build();
  }

  private Pair<Integer, Integer> getCustomResourceRequests(
      Integer reqMem, Integer reqCpu, StepInfo stepInfo, boolean isCustomResourceEnabled) {
    if (isCustomResourceEnabled) {
      try {
        JsonNode stepNode = stepInfo.getExecutionWrapperConfig().getStep();
        if (stepNode != null && stepNode.has("spec")) {
          JsonNode spec = stepNode.get("spec");
          // navigate to requests
          JsonNode requests = spec.path("resources").path("requests");
          if (!requests.isMissingNode()) {
            // CPU
            JsonNode cpuNode = requests.get("cpu");
            if (cpuNode != null && !cpuNode.isNull()) {
              String cpuRequestQuantity = cpuNode.asText();
              if (isNotEmpty(cpuRequestQuantity) && !UNRESOLVED_PARAMETER.equals(cpuRequestQuantity)
                  && !NULL_STR.equals(cpuRequestQuantity)) {
                reqCpu = QuantityUtils.getCpuQuantityValueInUnit(cpuRequestQuantity, DecimalQuantityUnit.m);
              }
            }
            // Memory
            JsonNode memNode = requests.get("memory");
            if (memNode != null && !memNode.isNull()) {
              String memRequestQuantity = memNode.asText();
              if (isNotEmpty(memRequestQuantity) && !UNRESOLVED_PARAMETER.equals(memRequestQuantity)
                  && !NULL_STR.equals(memRequestQuantity)) {
                reqMem = QuantityUtils.getStorageQuantityValueInUnit(memRequestQuantity, StorageQuantityUnit.Mi);
              }
            }
          }
          return Pair.of(reqMem, reqCpu);
        }
      } catch (Exception ex) {
        log.warn("Cannot obtain resource requests from stepInfo: {}", ex.getMessage());
      }
    }
    return Pair.of(reqMem, reqCpu);
  }
}
