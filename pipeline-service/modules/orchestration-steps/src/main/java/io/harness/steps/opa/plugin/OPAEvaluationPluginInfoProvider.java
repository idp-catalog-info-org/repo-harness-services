/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa.plugin;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.EXPRESSION_EVALUATION_FAILED;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.utils.ContainerSecretEvaluator;
import io.harness.ci.utils.PortFinder;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.exception.InternalServerErrorException;
import io.harness.expression.EngineExpressionService;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.NGAccess;
import io.harness.opaclient.model.PolicyData;
import io.harness.opaclient.model.PolicySetData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.ImageInformation;
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
import io.harness.pms.sdk.core.plugin.PluginInfoProvider;
import io.harness.pms.sdk.core.plugin.SecretNgVariableUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.opa.OPAEvaluationStepInfo;
import io.harness.steps.opa.OPAEvaluationStepNode;
import io.harness.steps.opa.step.OPAEvaluationStepHelper;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class OPAEvaluationPluginInfoProvider implements PluginInfoProvider {
  @Inject private OPAEvaluationStepHelper opaEvaluationStepHelper;
  @Inject private SecretUtils secretUtils;
  @Inject private EngineExpressionService engineExpressionService;
  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String planExecutionId = ambiance != null ? ambiance.getPlanExecutionId() : "unknown";
    log.info("OPAEvaluationPluginInfoProvider.getPluginInfo: Creating plugin info. stepType={}, stepJsonNodeSize={}, "
            + "usedPorts={}, planExecutionId={}",
        request.getType(), request.getStepJsonNode().length(), usedPorts, planExecutionId);

    String stepJsonNode = request.getStepJsonNode();
    OPAEvaluationStepNode opaEvaluationStepNode;
    try {
      opaEvaluationStepNode = YamlUtils.read(stepJsonNode, OPAEvaluationStepNode.class);
      log.info("OPAEvaluationPluginInfoProvider.getPluginInfo: Successfully parsed OPAEvaluationStepNode. "
              + "stepIdentifier={}, planExecutionId={}",
          opaEvaluationStepNode.getIdentifier(), planExecutionId);
    } catch (IOException e) {
      log.error("OPAEvaluationPluginInfoProvider.getPluginInfo: Failed to parse step JSON. stepType={}, error={}",
          request.getType(), e.getMessage(), e);
      throw new ContainerPluginParseException(
          String.format("Error in parsing OPAEvaluation step for step type [%s]", request.getType()), e);
    }

    OPAEvaluationStepInfo opaEvaluationStepInfo = opaEvaluationStepNode.getOpaEvaluationStepInfo();

    // Extract container-related fields from OPAEvaluationStepInfo
    ContainerResource resources = opaEvaluationStepInfo.getResources();
    ParameterField<String> image = opaEvaluationStepInfo.getImage();
    ParameterField<String> connectorRef = opaEvaluationStepInfo.getConnectorRef();
    ParameterField<Map<String, String>> envVariables = opaEvaluationStepInfo.getEnvVariables();
    // Privileged mode is not needed for OPA evaluation (read-only policy checks)
    ParameterField<Integer> runAsUser = opaEvaluationStepInfo.getRunAsUser();
    ParameterField<ImagePullPolicy> imagePullPolicy = opaEvaluationStepInfo.getImagePullPolicy();

    // Build ImageDetails
    ImageDetails.Builder imageDetailsBuilder = ImageDetails.newBuilder();

    // Build ImageInformation from image string
    if (ParameterField.isNotNull(image)) {
      String imageValue = getParameterFieldValue(image);
      StringValue imagePullPolicyStr;
      ImagePullPolicy imagePullPolicyValue = getParameterFieldValue(imagePullPolicy);
      if (imagePullPolicyValue == null || isEmpty(imagePullPolicyValue.toString())) {
        imagePullPolicyStr = StringValue.of(ImagePullPolicy.ALWAYS.toString());
      } else {
        imagePullPolicyStr = StringValue.of(imagePullPolicyValue.toString());
      }

      ImageInformation imageInformation = ImageInformation.newBuilder()
                                              .setImageName(StringValue.of(imageValue))
                                              .setImagePullPolicy(imagePullPolicyStr)
                                              .build();
      imageDetailsBuilder.setImageInformation(imageInformation);
    }

    // Build ConnectorDetails
    if (ParameterField.isNotNull(connectorRef)) {
      String connectorRefValue = getParameterFieldValue(connectorRef);
      imageDetailsBuilder.setConnectorDetails(
          ConnectorDetails.newBuilder().setConnectorRef(emptyIfNull(connectorRefValue)).build());
    }

    // Build PluginDetails
    Map<String, String> envVars = getParameterFieldValue(envVariables);
    if (envVars == null) {
      envVars = Collections.emptyMap();
    }

    // Assign at least one port (required by container initialization, even if OPA doesn't use it)
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();
    Integer assignedPort = portFinder.getNextPort();
    Set<Integer> allUsedPorts = new HashSet<>(usedPorts);
    allUsedPorts.add(assignedPort);
    log.info("OPAEvaluationPluginInfoProvider.getPluginInfo: Assigned port. stepIdentifier={}, assignedPort={}, "
            + "usedPorts={}, planExecutionId={}",
        opaEvaluationStepNode.getIdentifier(), assignedPort, allUsedPorts, planExecutionId);

    // Extract secrets from rego policies using ContainerSecretEvaluator (same approach as RunStep)
    List<SecretVariableDetails> secretVariableDetails = getSecretVariableDetails(ambiance, opaEvaluationStepInfo);

    List<SecretVariable> secretVariables =
        secretVariableDetails.stream()
            .map(detail
                -> SecretNgVariableUtils.getSecretVariable(
                    SecretNGVariable.builder()
                        .name(detail.getSecretVariableDTO().getName())
                        .value(ParameterField.createValueField(detail.getSecretVariableDTO().getSecret()))
                        .build()))
            .collect(Collectors.toList());

    PluginDetails.Builder pluginDetailsBuilder =
        PluginDetails.newBuilder()
            .putAllEnvVariables(envVars)
            .setImageDetails(imageDetailsBuilder.build())
            .setIsHarnessManaged(BoolValue.of(false))
            .setPrivileged(false)
            .addPortUsed(assignedPort) // Required by container init, even if unused
            .setTotalPortUsedDetails(PortDetails.newBuilder().addAllUsedPorts(allUsedPorts).build())
            .addAllSecretVariable(secretVariables)
            .putAllEnvVariablesWithPlainTextSecret(Collections.emptyMap());

    // Set resources - always required, use defaults if not provided
    // Note: OPA evaluation steps are lightweight (policy evaluation only) compared to CI container steps
    // which run builds/tests. CI container defaults (Container.java) are 2000m CPU / 9000Mi memory,
    // but OPA steps need much less resources.
    PluginContainerResources.Builder resourcesBuilder = PluginContainerResources.newBuilder();
    int defaultCpu = 500; // 500 millicores default (0.5 CPU) - optimized for OPA evaluation workload
    int defaultMemory = 500; // 500 MiB default - optimized for OPA evaluation workload

    if (resources != null && resources.getLimits() != null) {
      ContainerResource.Limits limits = resources.getLimits();
      if (limits.getCpu() != null && getParameterFieldValue(limits.getCpu()) != null) {
        String cpuValue = getParameterFieldValue(limits.getCpu());
        try {
          // CPU is typically in millicores (e.g., "1000m" or "1"), convert to integer
          if (cpuValue.endsWith("m")) {
            resourcesBuilder.setCpu(Integer.parseInt(cpuValue.substring(0, cpuValue.length() - 1)));
          } else {
            resourcesBuilder.setCpu((int) (Double.parseDouble(cpuValue) * 1000));
          }
        } catch (NumberFormatException e) {
          log.warn("Failed to parse CPU value: {}, using default", cpuValue, e);
          resourcesBuilder.setCpu(defaultCpu);
        }
      } else {
        resourcesBuilder.setCpu(defaultCpu);
      }

      if (limits.getMemory() != null && getParameterFieldValue(limits.getMemory()) != null) {
        String memoryValue = getParameterFieldValue(limits.getMemory());
        try {
          // Memory is typically in MiB (e.g., "512Mi" or "512"), convert to integer
          if (memoryValue.endsWith("Mi")) {
            resourcesBuilder.setMemory(Integer.parseInt(memoryValue.substring(0, memoryValue.length() - 2)));
          } else if (memoryValue.endsWith("Gi")) {
            resourcesBuilder.setMemory(
                (int) (Double.parseDouble(memoryValue.substring(0, memoryValue.length() - 2)) * 1024));
          } else {
            resourcesBuilder.setMemory(Integer.parseInt(memoryValue));
          }
        } catch (NumberFormatException e) {
          log.warn("Failed to parse memory value: {}, using default", memoryValue, e);
          resourcesBuilder.setMemory(defaultMemory);
        }
      } else {
        resourcesBuilder.setMemory(defaultMemory);
      }
    } else {
      // No resources specified, use defaults
      resourcesBuilder.setCpu(defaultCpu);
      resourcesBuilder.setMemory(defaultMemory);
    }
    pluginDetailsBuilder.setResource(resourcesBuilder.build());

    if (getParameterFieldValue(runAsUser) != null) {
      pluginDetailsBuilder.setRunAsUser(getParameterFieldValue(runAsUser));
      pluginDetailsBuilder.setRunAsUserV1(Int32Value.of(getParameterFieldValue(runAsUser)));
    }

    // Privileged mode not needed for OPA evaluation - always set to false
    pluginDetailsBuilder.setPrivilegedV1(BoolValue.of(false));

    PluginDetails pluginDetails = pluginDetailsBuilder.build();
    PluginCreationResponse response = PluginCreationResponse.newBuilder().setPluginDetails(pluginDetails).build();
    StepInfoProto stepInfoProto = StepInfoProto.newBuilder()
                                      .setIdentifier(opaEvaluationStepNode.getIdentifier())
                                      .setName(opaEvaluationStepNode.getName())
                                      .setUuid(opaEvaluationStepNode.getUuid())
                                      .build();

    log.info("OPAEvaluationPluginInfoProvider.getPluginInfo: Successfully created plugin info. "
            + "stepIdentifier={}, image={}, port={}, cpu={}m, memory={}Mi, envVarsCount={}, "
            + "runAsUser={}, planExecutionId={}",
        opaEvaluationStepNode.getIdentifier(),
        pluginDetails.getImageDetails().getImageInformation().getImageName().getValue(), assignedPort,
        pluginDetails.getResource().getCpu(), pluginDetails.getResource().getMemory(),
        pluginDetails.getEnvVariablesMap().size(),
        pluginDetails.hasRunAsUserV1() ? pluginDetails.getRunAsUserV1().getValue() : null, planExecutionId);

    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  @Override
  public boolean isSupported(String stepType) {
    return StepSpecTypeConstants.OPA_EVALUATION.equals(stepType);
  }

  /**
   * Extracts secrets from rego policies using ContainerSecretEvaluator (same approach as RunStep).
   * This method processes PolicySetData to find all ngSecretManager.obtain() expressions in rego code
   * and creates SecretVariableDetails for delegate-side secret resolution.
   *
   * @param ambiance Pipeline execution ambiance
   * @param opaEvaluationStepInfo OPA evaluation step info containing policy set details
   * @return List of SecretVariableDetails for secrets found in rego policies
   */
  private List<SecretVariableDetails> getSecretVariableDetails(
      Ambiance ambiance, OPAEvaluationStepInfo opaEvaluationStepInfo) {
    List<SecretVariableDetails> secretVariableDetails = new ArrayList<>();
    try {
      String policySetId = opaEvaluationStepInfo.getPolicySetId().getValue();
      String policySetOrgId = opaEvaluationStepInfo.getPolicySetOrgId() != null
          ? opaEvaluationStepInfo.getPolicySetOrgId().getValue()
          : null;
      String policySetProjectId = opaEvaluationStepInfo.getPolicySetProjectId() != null
          ? opaEvaluationStepInfo.getPolicySetProjectId().getValue()
          : null;

      PolicySetData policySetData =
          opaEvaluationStepHelper.fetchPolicySet(ambiance, policySetId, policySetOrgId, policySetProjectId);

      if (policySetData == null || isEmpty(policySetData.getPolicies())) {
        log.debug("OPAEvaluationPluginInfoProvider: No policies found in policy set {}, skipping secret extraction",
            policySetId);
        return secretVariableDetails;
      }

      // Use ContainerSecretEvaluator to extract secrets from rego (same as RunStep)
      ContainerSecretEvaluator containerSecretEvaluator =
          ContainerSecretEvaluator.builder()
              .secretUtils(secretUtils)
              .withSingleQuotes(AmbianceUtils.checkIfFeatureFlagEnabled(
                  ambiance, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR.name()))
              .build();

      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      long expressionFunctorToken = ambiance.getExpressionFunctorToken();

      for (PolicyData policy : policySetData.getPolicies()) {
        if (policy == null || isEmpty(policy.getRego())) {
          continue;
        }

        String resolvedRego;
        try {
          Object resolved = engineExpressionService.resolve(
              ambiance, policy.getRego(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED, new HashMap<>());
          resolvedRego = resolved != null ? resolved.toString() : policy.getRego();
        } catch (Exception ex) {
          log.warn("Failed to resolve expressions in rego for policy {}, using original rego. Error: {}",
              policy.getIdentifier(), ex.getMessage());
          resolvedRego = policy.getRego();
        }

        List<SecretVariableDetails> policySecrets =
            containerSecretEvaluator.resolve(resolvedRego, ngAccess, expressionFunctorToken);
        secretVariableDetails.addAll(policySecrets);
      }

      log.info("OPAEvaluationPluginInfoProvider: Extracted {} secrets from rego policies for policy set {}",
          secretVariableDetails.size(), policySetId);
    } catch (Exception ex) {
      log.warn("OPAEvaluationPluginInfoProvider: Failed to extract secrets from rego policies. Error: {}",
          ex.getMessage(), ex);
      String errorMessage = ex.getMessage();
      if (ex.getMessage().equals(EXPRESSION_EVALUATION_FAILED.toString()) && ex.getCause() != null
          && ex.getCause().getMessage() != null) {
        // This is done because in ex.getMessage is only returning EXPRESSION_EVALUATION_FAILED as message but not the
        // secret which was not found But the cause is giving the secret which was not found
        errorMessage = ex.getCause().getMessage();
      }
      throw new InternalServerErrorException("Failed to resolve secrets in OPA policy. Error: " + errorMessage);
      // Don't fail the step if secret extraction fails - secrets might not be present
    }
    return secretVariableDetails;
  }

  private <T> T getParameterFieldValue(ParameterField<T> parameterField) {
    if (parameterField == null || parameterField.isExpression() || parameterField.getValue() == null) {
      return null;
    }
    return parameterField.getValue();
  }
}
