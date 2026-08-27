/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.ContainerResource;
import io.harness.opaclient.model.InfraParams;
import io.harness.opaclient.model.KubernetesDirectInfraParams;
import io.harness.opaclient.model.PolicySetData;
import io.harness.opaclient.model.ResourceLimits;
import io.harness.opaclient.model.VMInfraParams;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.scope.ScopeHelper;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.execution.ExecutionModeUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class for creating and injecting OPA evaluation stage during plan creation.
 * This stage contains step groups for each delegate policy set with infrastructure config,
 * and an aggregator step to finalize the evaluation.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class OpaEvaluationStageHelper {
  @Inject private OpaServiceClient opaServiceClient;
  @Inject @Named("opaEvaluationPluginImage") private String opaEvaluationPluginImage;

  /**
   * Injects OPA evaluation stage into processedYaml at position [0] if delegate policy sets exist.
   * This method is called early in plan creation (before plan creators run) to modify the YAML
   * so that OPA stage appears as if it was part of the original YAML.
   *
   * @param accountId Account identifier
   * @param orgId Organization identifier
   * @param projectId Project identifier
   * @param executionUuid Plan execution UUID
   * @param pipelineId Pipeline identifier
   * @param executionMode Execution mode (to check if rollback)
   * @param processedYaml Original processed YAML string
   * @return Updated processedYaml with OPA stage injected at [0], or original YAML if no injection needed
   */
  public String injectOpaStageIntoProcessedYaml(String accountId, String orgId, String projectId, String executionUuid,
      String pipelineId, ExecutionMode executionMode, String processedYaml) {
    try {
      if (isEmpty(accountId)) {
        log.debug("Account ID is empty, skipping OPA evaluation stage injection");
        return processedYaml;
      }

      if (executionMode != null && ExecutionModeUtils.isRollbackMode(executionMode)) {
        log.debug("Skipping OPA evaluation stage injection in rollback mode");
        return processedYaml;
      }

      List<PolicySetData> delegatePolicySets = getDelegatePolicySets(accountId, orgId, projectId);
      if (isEmpty(delegatePolicySets)) {
        log.info("No delegate policy sets found, skipping OPA evaluation stage injection");
        return processedYaml;
      }

      // Build OPA stage YAML structure
      ObjectNode opaStageRootNode = buildOpaStageYaml(delegatePolicySets, null);
      if (opaStageRootNode == null) {
        log.error("Failed to build OPA stage YAML structure");
        return processedYaml;
      }

      try {
        YamlUtils.injectUuid(opaStageRootNode);
      } catch (Exception ex) {
        log.error("Failed to inject UUIDs into OPA stage node", ex);
        return processedYaml;
      }

      // Parse processedYaml and inject OPA stage at position [0]
      YamlField pipelineField = YamlUtils.readTree(processedYaml);
      YamlField stagesField = pipelineField.getNode()
                                  .getField(YAMLFieldNameConstants.PIPELINE)
                                  .getNode()
                                  .getField(YAMLFieldNameConstants.STAGES);

      if (stagesField == null || stagesField.getNode() == null) {
        log.error("Stages field not found in processedYaml");
        return processedYaml;
      }

      JsonNode existingStagesArray = stagesField.getNode().getCurrJsonNode();
      if (existingStagesArray == null || !existingStagesArray.isArray()) {
        log.error("Stages field is not an array");
        return processedYaml;
      }

      // Build new stages array with OPA stage first
      JsonNodeFactory factory = JsonNodeFactory.instance;
      ArrayNode newStagesArray = factory.arrayNode();

      newStagesArray.add(opaStageRootNode);

      // Add all existing stages after OPA stage
      for (JsonNode existingStage : existingStagesArray) {
        newStagesArray.add(existingStage.deepCopy());
      }

      log.info("Injecting OPA evaluation stage into processedYaml: policySetsCount={}, existingStagesCount={}, "
              + "newStagesCount={}",
          delegatePolicySets.size(), existingStagesArray.size(), newStagesArray.size());

      YamlNode stagesYamlNode = stagesField.getNode();
      if (stagesYamlNode == null) {
        log.error("Stages YamlNode is null");
        return processedYaml;
      }

      // Get pipeline YamlNode and replace stages field
      YamlNode pipelineYamlNode = pipelineField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode();
      if (pipelineYamlNode == null) {
        log.error("Pipeline YamlNode is null");
        return processedYaml;
      }

      JsonNode pipelineJsonNode = pipelineYamlNode.getCurrJsonNode();
      if (!(pipelineJsonNode instanceof ObjectNode)) {
        log.error("Pipeline JsonNode is not an ObjectNode");
        return processedYaml;
      }

      ((ObjectNode) pipelineJsonNode).set(YAMLFieldNameConstants.STAGES, newStagesArray);

      String updatedYaml = JsonPipelineUtils.getJsonString(pipelineField.getNode().getCurrJsonNode());
      log.info("Successfully injected OPA evaluation stage into processedYaml");
      return updatedYaml;

    } catch (Exception ex) {
      log.error("Error injecting OPA evaluation stage into processedYaml", ex);
      throw new RuntimeException("Error injecting OPA evaluation stage into processedYaml", ex);
    }
  }

  /**
   * Gets delegate policy sets from OPA service (without PlanCreationContext).
   * Fetches policy sets with type="pipeline" and action="onrun", then filters for those with infrastructure config.
   *
   * @param accountId Account identifier
   * @param orgId Organization identifier
   * @param projectId Project identifier
   * @return List of delegate policy sets with infrastructure config
   */
  private List<PolicySetData> getDelegatePolicySets(String accountId, String orgId, String projectId) {
    try (PipelineServiceOpaContextGuard ignore = new PipelineServiceOpaContextGuard()) {
      // Fetch policy sets from all scope levels: account, org, and project
      List<PolicySetData> accountLevelPolicySets = SafeHttpCall.executeWithErrorMessage(
          opaServiceClient.listOpaPolicySetsWithTypeAndAction(accountId, null, null, null, "pipeline", "onrun"));
      List<PolicySetData> orgLevelPolicySets = SafeHttpCall.executeWithErrorMessage(
          opaServiceClient.listOpaPolicySetsWithTypeAndAction(accountId, orgId, null, null, "pipeline", "onrun"));
      List<PolicySetData> projectLevelPolicySets = SafeHttpCall.executeWithErrorMessage(
          opaServiceClient.listOpaPolicySetsWithTypeAndAction(accountId, orgId, projectId, null, "pipeline", "onrun"));

      List<PolicySetData> allPolicySets = new ArrayList<>();
      if (isNotEmpty(accountLevelPolicySets)) {
        allPolicySets.addAll(accountLevelPolicySets);
      }
      if (isNotEmpty(orgLevelPolicySets)) {
        allPolicySets.addAll(orgLevelPolicySets);
      }
      if (isNotEmpty(projectLevelPolicySets)) {
        allPolicySets.addAll(projectLevelPolicySets);
      }

      if (isEmpty(allPolicySets)) {
        log.info("No policy sets found");
        return new ArrayList<>();
      }

      List<PolicySetData> delegatePolicySets = new ArrayList<>();
      for (PolicySetData policySet : allPolicySets) {
        if (policySet != null && Boolean.TRUE.equals(policySet.getEnabled()) && isNotEmpty(policySet.getInfra_type())) {
          delegatePolicySets.add(policySet);
          log.debug("Found delegate policy set: identifier={}, enabled={}, type={}, action={}, infra_type={}",
              policySet.getIdentifier(), policySet.getEnabled(), policySet.getType(), policySet.getAction(),
              policySet.getInfra_type());
        }
      }

      log.info("Found {} delegate policy sets (enabled=true, with infra) out of {} total policy sets (type=pipeline, "
              + "action=onrun)",
          delegatePolicySets.size(), allPolicySets.size());
      return delegatePolicySets;
    } catch (Exception ex) {
      log.error("Failed to fetch delegate policy sets from OPA service", ex);
      throw new RuntimeException("Failed to fetch delegate policy sets from OPA service", ex);
    }
  }

  private ObjectNode buildOpaStageYaml(List<PolicySetData> delegatePolicySets, PlanCreationContext ctx) {
    JsonNodeFactory factory = JsonNodeFactory.instance;

    ObjectNode stageNode = factory.objectNode();
    stageNode.put("name", "OPA Evaluation");
    stageNode.put("identifier", "Harness_OPA_Evaluation");
    stageNode.put("type", "Custom");

    ObjectNode specNode = factory.objectNode();
    ObjectNode executionNode = factory.objectNode();
    ArrayNode stepsArray = factory.arrayNode();

    ArrayNode parallelArray = factory.arrayNode();
    for (PolicySetData policySet : delegatePolicySets) {
      ObjectNode stepGroupNode = createStepGroupForPolicySet(policySet, ctx);
      ObjectNode wrappedStepGroup = factory.objectNode();
      wrappedStepGroup.set(YAMLFieldNameConstants.STEP_GROUP, stepGroupNode);
      parallelArray.add(wrappedStepGroup);
    }

    if (parallelArray.size() > 0) {
      ObjectNode parallelNode = factory.objectNode();
      parallelNode.set(YAMLFieldNameConstants.PARALLEL, parallelArray);
      stepsArray.add(parallelNode);
    }

    ObjectNode aggregatorStep = createAggregatorStep(ctx);
    ObjectNode wrappedAggregatorStep = factory.objectNode();
    wrappedAggregatorStep.set(YAMLFieldNameConstants.STEP, aggregatorStep);
    stepsArray.add(wrappedAggregatorStep);

    executionNode.set("steps", stepsArray);
    specNode.set("execution", executionNode);
    stageNode.set("spec", specNode);

    // Wrap in stage field
    ObjectNode rootNode = factory.objectNode();
    rootNode.set(YAMLFieldNameConstants.STAGE, stageNode);

    return rootNode;
  }

  /**
   * Creates a step group YAML node for a policy set.
   * Each step group has:
   * - Infrastructure configuration (from policy set infra_type and infra_params)
   * - One OPA evaluation step with resources and connector ref from infrastructure config
   *
   * @param policySet Policy set data with infrastructure config
   * @param ctx Plan creation context
   * @return ObjectNode representing the step group
   */
  private ObjectNode createStepGroupForPolicySet(PolicySetData policySet, PlanCreationContext ctx) {
    JsonNodeFactory factory = JsonNodeFactory.instance;
    ObjectNode stepGroupNode = factory.objectNode();

    String policySetId = policySet.getIdentifier();
    Scope scope = ScopeHelper.getScope(policySet.getAccount_id(), policySet.getOrg_id(), policySet.getProject_id());
    String scopePrefix = scope.getYamlRepresentation();
    String stepGroupName =
        scopePrefix + " Policy Set " + (isEmpty(policySet.getName()) ? policySetId : policySet.getName());
    String stepGroupIdentifier = scopePrefix + "_policy_set_" + policySetId;

    stepGroupNode.put("name", stepGroupName);
    stepGroupNode.put("identifier", stepGroupIdentifier);

    // Build infrastructure configuration
    ObjectNode infraNode = buildStepGroupInfrastructure(policySet, policySetId);
    stepGroupNode.set("stepGroupInfra", infraNode);

    // Build OPA evaluation step
    ObjectNode stepNode = buildOpaEvaluationStep(policySet, policySetId, scopePrefix);

    ArrayNode stepsArray = factory.arrayNode();
    ObjectNode wrappedStep = factory.objectNode();
    wrappedStep.set(YAMLFieldNameConstants.STEP, stepNode);
    stepsArray.add(wrappedStep);

    stepGroupNode.set("steps", stepsArray);

    return stepGroupNode;
  }

  /**
   * Builds the step group infrastructure configuration node.
   *
   * @param policySet Policy set data
   * @param policySetId Policy set identifier
   * @return ObjectNode representing the infrastructure configuration
   */
  private ObjectNode buildStepGroupInfrastructure(PolicySetData policySet, String policySetId) {
    JsonNodeFactory factory = JsonNodeFactory.instance;
    String infraType =
        isNotEmpty(policySet.getInfra_type()) ? policySet.getInfra_type() : InfrastructureKind.KUBERNETES_DIRECT;

    ObjectNode infraNode = factory.objectNode();
    infraNode.put("type", infraType);

    ObjectNode infraSpecNode = buildInfraSpec(policySet, policySetId, infraType);
    infraNode.set("spec", infraSpecNode);

    return infraNode;
  }

  /**
   * Builds the infrastructure spec node based on infrastructure type.
   *
   * @param policySet Policy set data
   * @param policySetId Policy set identifier
   * @param infraType Infrastructure type
   * @return ObjectNode representing the infrastructure spec
   */
  private ObjectNode buildInfraSpec(PolicySetData policySet, String policySetId, String infraType) {
    JsonNodeFactory factory = JsonNodeFactory.instance;
    ObjectNode infraSpecNode = factory.objectNode();

    InfraParams infraParams = policySet.getInfra_params();
    if (infraParams instanceof KubernetesDirectInfraParams k8sParams) {
      buildKubernetesInfraSpec(infraSpecNode, k8sParams, policySetId, factory);
    } else if (infraParams instanceof VMInfraParams vmParams) {
      buildVMInfraSpec(infraSpecNode, vmParams, policySetId);
    } else {
      throw new IllegalArgumentException(
          String.format("Invalid infrastructure configuration for policy set %s. Infrastructure type is '%s' but "
                  + "infrastructure parameters are missing or invalid. "
                  + "Policy set identifier: %s. Expected infrastructure parameters to match the infrastructure type.",
              policySetId, infraType, policySetId));
    }

    return infraSpecNode;
  }

  /**
   * Builds Kubernetes infrastructure spec node.
   *
   * @param infraSpecNode Infrastructure spec node to populate
   * @param k8sParams Kubernetes infrastructure parameters
   * @param policySetId Policy set identifier
   * @param factory JsonNodeFactory instance
   */
  private void buildKubernetesInfraSpec(
      ObjectNode infraSpecNode, KubernetesDirectInfraParams k8sParams, String policySetId, JsonNodeFactory factory) {
    if (isEmpty(k8sParams.getConnectorRef())) {
      throw new IllegalArgumentException(String.format("Infrastructure connector is required for policy set %s with "
              + "Kubernetes infrastructure. Policy set identifier: %s",
          policySetId, policySetId));
    }
    infraSpecNode.put("connectorRef", k8sParams.getConnectorRef());

    if (isNotEmpty(k8sParams.getNamespace())) {
      infraSpecNode.put("namespace", k8sParams.getNamespace());
    } else {
      infraSpecNode.put("namespace", "default");
    }

    if (isNotEmpty(k8sParams.getInitTimeout())) {
      infraSpecNode.put("initTimeout", k8sParams.getInitTimeout());
    }

    // Add labels if present
    if (k8sParams.getLabels() != null && !k8sParams.getLabels().isEmpty()) {
      ObjectNode labelsNode = factory.objectNode();
      k8sParams.getLabels().forEach(labelsNode::put);
      infraSpecNode.set("labels", labelsNode);
    }

    // Add annotations if present
    if (k8sParams.getAnnotations() != null && !k8sParams.getAnnotations().isEmpty()) {
      ObjectNode annotationsNode = factory.objectNode();
      k8sParams.getAnnotations().forEach(annotationsNode::put);
      infraSpecNode.set("annotations", annotationsNode);
    }

    // Add nodeSelector if present
    if (k8sParams.getNodeSelector() != null && !k8sParams.getNodeSelector().isEmpty()) {
      ObjectNode nodeSelectorNode = factory.objectNode();
      k8sParams.getNodeSelector().forEach(nodeSelectorNode::put);
      infraSpecNode.set("nodeSelector", nodeSelectorNode);
    }
  }

  /**
   * Builds VM infrastructure spec node.
   *
   * @param infraSpecNode Infrastructure spec node to populate
   * @param vmParams VM infrastructure parameters
   * @param policySetId Policy set identifier
   */
  private void buildVMInfraSpec(ObjectNode infraSpecNode, VMInfraParams vmParams, String policySetId) {
    JsonNodeFactory factory = JsonNodeFactory.instance;
    ObjectNode poolSpecNode = factory.objectNode();
    infraSpecNode.put("type", "Pool");

    if (isEmpty(vmParams.getPoolName())) {
      throw new IllegalArgumentException(
          String.format("VM pool name is required for policy set %s with VM infrastructure. Policy set identifier: %s",
              policySetId, policySetId));
    }
    poolSpecNode.put("poolName", vmParams.getPoolName());

    if (isEmpty(vmParams.getOs())) {
      throw new IllegalArgumentException(
          String.format("VM OS is required for policy set %s with VM infrastructure. Policy set identifier: %s",
              policySetId, policySetId));
    }
    poolSpecNode.put("os", vmParams.getOs());
    infraSpecNode.set("spec", poolSpecNode);
  }

  /**
   * Builds the OPA evaluation step node.
   *
   * @param policySet Policy set data
   * @param policySetId Policy set identifier
   * @param scopePrefix Scope prefix for step identifier
   * @return ObjectNode representing the OPA evaluation step
   */
  private ObjectNode buildOpaEvaluationStep(PolicySetData policySet, String policySetId, String scopePrefix) {
    JsonNodeFactory factory = JsonNodeFactory.instance;
    ObjectNode stepNode = factory.objectNode();

    String stepName = "Policy set Evaluation";
    String stepIdentifier = scopePrefix + "_opa_eval_" + policySetId;

    stepNode.put("name", stepName);
    stepNode.put("identifier", stepIdentifier);
    stepNode.put("type", StepSpecTypeConstants.OPA_EVALUATION);

    ObjectNode stepSpecNode = buildStepSpec(policySet, policySetId);
    stepNode.set("spec", stepSpecNode);

    // Set when condition to "All" so it runs regardless of stage status
    // The ON_SUCCESS adviser from init step will ensure it only runs when init succeeds
    ObjectNode whenSpecType = factory.objectNode();
    whenSpecType.put("stageStatus", "All");
    stepNode.set("when", whenSpecType);

    return stepNode;
  }

  /**
   * Builds the step spec node with policy set details, image, connector, and resources.
   *
   * @param policySet Policy set data
   * @param policySetId Policy set identifier
   * @return ObjectNode representing the step spec
   */
  private ObjectNode buildStepSpec(PolicySetData policySet, String policySetId) {
    JsonNodeFactory factory = JsonNodeFactory.instance;
    ObjectNode stepSpecNode = factory.objectNode();

    stepSpecNode.put("policySetId", policySetId);

    if (isNotEmpty(policySet.getOrg_id())) {
      stepSpecNode.put("policySetOrgId", policySet.getOrg_id());
    }
    if (isNotEmpty(policySet.getProject_id())) {
      stepSpecNode.put("policySetProjectId", policySet.getProject_id());
    }

    String pluginImage = getOpaEvaluationPluginImage();
    stepSpecNode.put("image", pluginImage);

    // Determine image connector ref and resources, then add to step spec
    String imageConnectorRef = determineImageConnectorRef(policySet);
    stepSpecNode.put("connectorRef", imageConnectorRef);
    stepSpecNode.put("timeout", "10m");

    // Add resources if present (only for K8s)
    ContainerResource resources = extractResourcesFromInfraParams(policySet);
    if (resources != null) {
      ObjectNode resourcesNode = buildResourcesNode(resources, factory);
      if (resourcesNode.size() > 0) {
        stepSpecNode.set("resources", resourcesNode);
      }
    }

    return stepSpecNode;
  }

  /**
   * Determines the image connector reference based on infrastructure type and policy set.
   *
   * @param policySet Policy set data
   * @return Image connector reference string
   */
  private String determineImageConnectorRef(PolicySetData policySet) {
    String imageConnectorRef = "account.harnessImage"; // Default

    InfraParams infraParams = policySet.getInfra_params();
    if (infraParams instanceof KubernetesDirectInfraParams k8sParams) {
      // Use harnessImageConnectorRef if available, otherwise use default image connector
      // Do not use connectorRef (infrastructure connector) as it's for cluster connection, not image pulling
      if (isNotEmpty(k8sParams.getHarnessImageConnectorRef())) {
        imageConnectorRef = k8sParams.getHarnessImageConnectorRef();
      }
      // If harnessImageConnectorRef is not available, keep default "account.harnessImage"
    } else if (infraParams instanceof VMInfraParams vmParams) {
      // Use harnessImageConnectorRef if available, otherwise use default image connector
      if (isNotEmpty(vmParams.getHarnessImageConnectorRef())) {
        imageConnectorRef = vmParams.getHarnessImageConnectorRef();
      }
      // If harnessImageConnectorRef is not available, keep default "account.harnessImage"
    }

    // Fallback to top-level harness_image_connector_ref if available
    if (isNotEmpty(policySet.getHarness_image_connector_ref())) {
      imageConnectorRef = policySet.getHarness_image_connector_ref();
    }

    return imageConnectorRef;
  }

  /**
   * Extracts container resources from infrastructure parameters.
   *
   * @param policySet Policy set data
   * @return ContainerResource if available (only for K8s), null otherwise
   */
  private ContainerResource extractResourcesFromInfraParams(PolicySetData policySet) {
    InfraParams infraParams = policySet.getInfra_params();
    if (infraParams instanceof KubernetesDirectInfraParams k8sParams) {
      return k8sParams.getResources();
    }
    // VM doesn't have resources in infra_params
    return null;
  }

  /**
   * Builds resources node from ContainerResource.
   *
   * @param resources Container resources
   * @param factory JsonNodeFactory instance
   * @return ObjectNode representing resources
   */
  private ObjectNode buildResourcesNode(ContainerResource resources, JsonNodeFactory factory) {
    ObjectNode resourcesNode = factory.objectNode();

    if (resources.getLimits() != null) {
      ObjectNode limitsNode = factory.objectNode();
      ResourceLimits limits = resources.getLimits();
      if (isNotEmpty(limits.getCpu())) {
        limitsNode.put("cpu", limits.getCpu());
      }
      if (isNotEmpty(limits.getMemory())) {
        limitsNode.put("memory", limits.getMemory());
      }
      resourcesNode.set("limits", limitsNode);
    }

    if (resources.getRequests() != null) {
      ObjectNode requestsNode = factory.objectNode();
      ResourceLimits requests = resources.getRequests();
      if (isNotEmpty(requests.getCpu())) {
        requestsNode.put("cpu", requests.getCpu());
      }
      if (isNotEmpty(requests.getMemory())) {
        requestsNode.put("memory", requests.getMemory());
      }
      resourcesNode.set("requests", requestsNode);
    }

    return resourcesNode;
  }

  /**
   * Creates the aggregator step YAML node.
   * This step:
   * - Calls OPA service to get evaluation_new records
   * - Checks status of all policy set evaluations
   * - Determines overall evaluation status
   * - Fails/passes the pipeline accordingly
   *
   *
   * @param ctx Plan creation context
   * @return ObjectNode representing the aggregator step
   */
  private ObjectNode createAggregatorStep(PlanCreationContext ctx) {
    JsonNodeFactory factory = JsonNodeFactory.instance;
    ObjectNode stepNode = factory.objectNode();

    stepNode.put("name", "OPA Evaluation Decision");
    stepNode.put("identifier", "opa_evaluation_decision");
    stepNode.put("type", StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR);

    ObjectNode stepSpecNode = factory.objectNode();
    stepSpecNode.put("timeout", "5m");

    stepNode.set("spec", stepSpecNode);

    ObjectNode whenSpecType = factory.objectNode();
    whenSpecType.put("stageStatus", "All");
    stepNode.set("when", whenSpecType);

    return stepNode;
  }

  /**
   * Gets OPA evaluation plugin image from injected configuration.
   *
   * @return Plugin image from config, or fallback default
   */
  private String getOpaEvaluationPluginImage() {
    if (isEmpty(opaEvaluationPluginImage)) {
      log.error("OPA evaluation plugin image is not configured");
      throw new RuntimeException("OPA evaluation plugin image is not configured");
    }
    log.info("Using OPA evaluation plugin image from config: {}", opaEvaluationPluginImage);
    return opaEvaluationPluginImage;
  }
}
