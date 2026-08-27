/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plugin;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.steps.stepinfo.PluginCompatibleStep;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.execution.integrationstage.k8s.K8InitializeStepUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.PortFinder;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.filters.WithConnectorRef;
import io.harness.iacm.execution.PluginSettingUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.PortDetails;
import io.harness.pms.contracts.plan.SecretVariable;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.plugin.ImageDetailsUtils;
import io.harness.pms.sdk.core.plugin.PluginInfoProvider;
import io.harness.pms.sdk.core.plugin.SecretNgVariableUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.execution.SSCALicenseHelper;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
public class CiPluginStepInfoProvider implements PluginInfoProvider {
  private static final int CACHE_EVICTION_TIME_MINUTES = 5;
  private final LoadingCache<AmbianceSummary, Map<String, String>> sscaServiceEnvMap =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_EVICTION_TIME_MINUTES, TimeUnit.MINUTES)
          .build(new CacheLoader<>() {
            @NotNull
            @Override
            public Map<String, String> load(@NotNull final AmbianceSummary ambianceSummary) {
              return getSscaServiceEnvVariables(ambianceSummary);
            }
          });

  @Inject K8InitializeStepUtils k8InitializeStepUtils;
  @Inject SSCAServiceUtils sscaServiceUtils;
  @Inject CIFeatureFlagService featureFlagService;
  @Inject SSCALicenseHelper sscaLicenseHelper;
  @Inject(optional = true) PluginSettingUtils pluginSettingUtils;
  @Inject HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  @Inject HarnessCodeServiceConfig harnessCodeServiceConfig;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CIAbstractStepNode ciAbstractStepNode;
    try {
      ciAbstractStepNode = YamlUtils.read(stepJsonNode, CIAbstractStepNode.class);
    } catch (InvalidTypeIdException e) {
      throw new ContainerPluginParseException(
          String.format("Step type [%s] is not supported to run inside Containerize Step Group", request.getType()), e);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CI step for step type [%s]", request.getType()), e);
    }
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();
    ContainerDefinitionInfo containerDefinitionInfo =
        k8InitializeStepUtils.createStepContainerDefinition(ciAbstractStepNode, null, null, portFinder, 0,
            request.getAccountId(), OSType.fromString(request.getOsType()), ambiance, 0, 0);
    List<SecretVariable> secretVariables = containerDefinitionInfo.getSecretVariables()
                                               .stream()
                                               .map(SecretNgVariableUtils::getSecretVariable)
                                               .collect(Collectors.toList());
    HashSet<Integer> ports = new HashSet<>(portFinder.getUsedPorts());
    ports.addAll(containerDefinitionInfo.getPorts());
    Map<String, String> envVarsWithSecret = getSscaServiceSecrets(ambiance);

    PluginDetails.Builder pluginDetailsBuilder =
        PluginDetails.newBuilder()
            .putAllEnvVariables(containerDefinitionInfo.getEnvVars())
            .setIsHarnessManaged(BoolValue.of(containerDefinitionInfo.isHarnessManagedImage()))
            .setImageDetails(
                ImageDetails.newBuilder()
                    .setImageInformation(ImageDetailsUtils.getImageDetails(
                        containerDefinitionInfo.getContainerImageDetails().getImageDetails(),
                        containerDefinitionInfo.getImagePullPolicy()))
                    .setConnectorDetails(
                        ConnectorDetails.newBuilder()
                            .setConnectorRef(emptyIfNull(
                                containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier()))
                            .build())
                    .build())
            .setPrivileged(containerDefinitionInfo.getPrivileged() == null || containerDefinitionInfo.getPrivileged())
            .addAllPortUsed(containerDefinitionInfo.getPorts())
            .setTotalPortUsedDetails(PortDetails.newBuilder().addAllUsedPorts(ports).build())
            .setResource(getPluginContainerResources(containerDefinitionInfo))
            .addAllSecretVariable(secretVariables)
            .putAllEnvVariablesWithPlainTextSecret(envVarsWithSecret);

    if (containerDefinitionInfo.getRunAsUser() != null) {
      pluginDetailsBuilder.setRunAsUser(containerDefinitionInfo.getRunAsUser());
      pluginDetailsBuilder.setRunAsUserV1(Int32Value.of(containerDefinitionInfo.getRunAsUser()));
    }

    if (containerDefinitionInfo.getPrivileged() != null) {
      pluginDetailsBuilder.setPrivilegedV1(BoolValue.of(containerDefinitionInfo.getPrivileged()));
    }

    if ((ciAbstractStepNode.getStepSpecType() instanceof PluginCompatibleStep)
        && (ciAbstractStepNode.getStepSpecType() instanceof WithConnectorRef)
        && ((PluginCompatibleStep) ciAbstractStepNode.getStepSpecType()).isConnectorMandatory()) {
      PluginCompatibleStep step = (PluginCompatibleStep) ciAbstractStepNode.getStepSpecType();
      Map<EnvVariableEnum, String> rawEnvMap =
          PluginSettingUtils.getConnectorSecretEnvMap(step.getNonYamlInfo().getStepInfoType());
      if (!featureFlagService.isEnabled(
              FeatureName.CI_AWS_SESSION_TOKEN_SUPPORT, AmbianceUtils.getAccountId(ambiance))) {
        rawEnvMap.remove(EnvVariableEnum.AWS_SESSION_TOKEN);
      }
      Map<String, String> connectorSecretEnvMap = new HashMap<>();
      rawEnvMap.forEach((key, value) -> connectorSecretEnvMap.put(key.name(), value));
      String connectorRef = PluginSettingUtils.getConnectorRef(step);
      String registryRef = pluginSettingUtils.getRegistryRef(step);
      boolean isHarnessCodeRepo = false;
      String harnessCodeCloneToken = null;

      if (step instanceof GitCloneStepInfo && connectorRef == null) {
        GitCloneStepInfo gitCloneStepInfo = (GitCloneStepInfo) step;
        isHarnessCodeRepo = true;
        ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
        String principal = executionPrincipalInfo.getPrincipal();
        io.harness.pms.contracts.plan.PrincipalType principalType = executionPrincipalInfo.getPrincipalType();
        String uniqueId = null;
        if (io.harness.pms.contracts.plan.PrincipalType.SERVICE_ACCOUNT.equals(principalType)) {
          uniqueId = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoOrDefault("uniqueId", null);
        }
        harnessCodeCloneToken = harnessCodeConnectorUtils.getTokenWithClaims(
            harnessCodeServiceConfig.getServiceSecret(), AmbianceUtils.getNgAccess(ambiance),
            gitCloneStepInfo.getRepoName().getValue(), principal, principalType.name(), uniqueId, 1);
      }
      if (isNotEmpty(connectorRef) || isNotEmpty(registryRef)) {
        pluginDetailsBuilder.addConnectorsForStep(ConnectorDetails.newBuilder()
                                                      .setConnectorRef(isNotEmpty(connectorRef) ? connectorRef : "")
                                                      .setRegistryRef(isNotEmpty(registryRef) ? registryRef : "")
                                                      .putAllConnectorSecretEnvMap(connectorSecretEnvMap)
                                                      .build());
      }
      if (isHarnessCodeRepo) {
        pluginDetailsBuilder.addConnectorsForStep(ConnectorDetails.newBuilder()
                                                      .setConnectorRef("")
                                                      .setRegistryRef("")
                                                      .putAllConnectorSecretEnvMap(connectorSecretEnvMap)
                                                      .setIsHarnessCodeRepo(true)
                                                      .setHarnessCodeToken(harnessCodeCloneToken)
                                                      .build());
      }
    }

    if (ciAbstractStepNode.getStepSpecType() instanceof RunStepInfo
        && featureFlagService.isEnabled(FeatureName.HAR_CD_RUN_STEP, request.getAccountId())) {
      RunStepInfo runStepInfo = (RunStepInfo) ciAbstractStepNode.getStepSpecType();
      String registryRef =
          runStepInfo.getRegistryRef() != null ? (String) runStepInfo.getRegistryRef().fetchFinalValue() : null;
      if (isNotEmpty(registryRef)) {
        pluginDetailsBuilder.setImageDetails(
            ImageDetails.newBuilder()
                .setImageInformation(ImageDetailsUtils.getImageDetails(
                    containerDefinitionInfo.getContainerImageDetails().getImageDetails(),
                    containerDefinitionInfo.getImagePullPolicy()))
                .setConnectorDetails(
                    ConnectorDetails.newBuilder()
                        .setConnectorRef(
                            emptyIfNull(containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier()))
                        .setRegistryRef(registryRef)
                        .build())
                .build());
      }
    }

    PluginCreationResponse response =
        PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
    StepInfoProto stepInfoProto = StepInfoProto.newBuilder()
                                      .setIdentifier(ciAbstractStepNode.getIdentifier())
                                      .setName(ciAbstractStepNode.getName())
                                      .setUuid(ciAbstractStepNode.getUuid())
                                      .build();
    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  private Map<String, String> getSscaServiceSecrets(Ambiance ambiance) {
    try {
      return sscaServiceEnvMap.get(AmbianceSummary.builder()
                                       .accountId(AmbianceUtils.getAccountId(ambiance))
                                       .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                                       .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                                       .build());
    } catch (Exception e) {
      log.error("Unable to get ssca service endpoint and secret", e);
      return Collections.emptyMap();
    }
  }

  private PluginContainerResources getPluginContainerResources(ContainerDefinitionInfo containerDefinitionInfo) {
    return PluginContainerResources.newBuilder()
        .setCpu(containerDefinitionInfo.getContainerResourceParams().getResourceLimitMilliCpu())
        .setMemory(containerDefinitionInfo.getContainerResourceParams().getResourceLimitMemoryMiB())
        .build();
  }

  @Override
  public boolean isSupported(String stepType) {
    return true;
  }

  private Map<String, String> getSscaServiceEnvVariables(AmbianceSummary ambiance) {
    String accountId = ambiance.getAccountId();
    Map<String, String> envVars = new HashMap<>();
    if (featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, accountId)
        || sscaLicenseHelper.hasActiveLicense(accountId)) {
      envVars.putAll(sscaServiceUtils.getSSCAServiceEnvVariables(accountId));
    }
    return envVars;
  }

  @Getter
  @Builder
  @EqualsAndHashCode
  private static class AmbianceSummary {
    String accountId;
    String orgIdentifier;
    String projectIdentifier;
  }
}
