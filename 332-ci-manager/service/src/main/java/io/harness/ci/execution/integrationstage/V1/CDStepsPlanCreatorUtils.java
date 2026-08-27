/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage.V1;

import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.ENV_BRANCH_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.GROUP;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_ENVIRONMENT_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_ENV_BRANCH_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_INFRA_ID;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_INFRA_INPUTS;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_SERVICE_INPUTS;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_SERVICE_REF;
import static io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils.MATRIX_SVC_BRANCH_REF;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.ENV_OVERRIDES_INPUTS;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.INFRA_INPUTS;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MULTI_ENVIRONMENT;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.MULTI_SERVICE;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.SERVICE_INPUTS;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.SERVICE_TYPE;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.SVC_BRANCH_REF;
import static io.harness.ci.commonconstants.CdStepParametersInfoConstants.SVC_OVERRIDES_INPUTS;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.BRANCH;

import io.harness.StageChildrenEntitiesType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.multi.deploy.UnifiedDeploymentItemsResolver;
import io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils;
import io.harness.cd.multi.deploy.UnifiedServiceTypeValidatorUtils;
import io.harness.ci.commonconstants.CdStepParametersInfoConstants;
import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.ListValue;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.TemplateType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CI)
@UtilityClass
public class CDStepsPlanCreatorUtils {
  public static final String DEPLOY_TO = "deploy-to";
  public static final String ID = "id";
  public static final String WITH = "with";
  public static final String INPUTS = "inputs";
  public static final String OVERLAY = "overlay";

  static Map<String, Object> getDeployNodesInfo(YamlField stageYamlField, UnifiedStageNodeV1 stageNode) {
    Map<String, Object> deployEntitiesInfoMap = new HashMap<>();
    if (ParameterField.isNotNull(stageNode.getService()) || ParameterField.isNotNull(stageNode.getEnvironment())) {
      boolean isMultiService = UnifiedMultiDeploymentUtils.isMultiService(stageNode.getService());
      boolean isMultiInfra = UnifiedMultiDeploymentUtils.isMultiEnvironment(stageNode.getEnvironment());
      deployEntitiesInfoMap.put(MULTI_SERVICE, String.valueOf(isMultiService));
      deployEntitiesInfoMap.put(MULTI_ENVIRONMENT, String.valueOf(isMultiInfra));

      YamlField serviceField = stageYamlField.getNode().getField(YAMLFieldNameConstants.SERVICE);
      setServiceNodeInfo(stageNode, deployEntitiesInfoMap, serviceField, isMultiService);
      setServiceTypeInfo(stageNode, deployEntitiesInfoMap);

      UnifiedMultiDeploymentUtils.validateEnvironmentItems(stageNode.getEnvironment());
      YamlField envField = stageYamlField.getNode().getField(YAMLFieldNameConstants.ENVIRONMENT);
      setEnvAndInfraNodeInfo(stageNode, deployEntitiesInfoMap, envField, isMultiInfra);
    }
    return deployEntitiesInfoMap;
  }

  static Map<String, Object> getTestNodesInfo(YamlField stageYamlField, UnifiedStageNodeV1 stageNode) {
    // TODO: Implement test-specific node info gathering
    return new HashMap<>();
  }

  static Map<String, Object> getChaosNodesInfo(YamlField stageYamlField, UnifiedStageNodeV1 stageNode) {
    // TODO: Implement chaos-specific node info gathering
    return new HashMap<>();
  }

  static Map<String, Object> getIdpNodesInfo(YamlField stageYamlField, UnifiedStageNodeV1 stageNode) {
    // TODO: Implement idp-specific node info gathering
    return new HashMap<>();
  }

  static Map<String, Object> getStoNodesInfo(YamlField stageYamlField, UnifiedStageNodeV1 stageNode) {
    // TODO: Implement sto-specific node info gathering
    return new HashMap<>();
  }

  private static void setEnvAndInfraNodeInfo(UnifiedStageNodeV1 stageNode, Map<String, Object> entitiesInfoMap,
      YamlField envField, boolean isMultiEnvironment) {
    if (isMultiEnvironment) {
      setMultiEnvAndInfraNodeInfo(stageNode, entitiesInfoMap);
    } else {
      setSingleEnvAndInfraNodeInfo(stageNode, entitiesInfoMap, envField);
    }
  }

  private static void setSingleEnvAndInfraNodeInfo(
      UnifiedStageNodeV1 stageNode, Map<String, Object> entitiesIDMap, YamlField envField) {
    if (envField == null) {
      return;
    }
    // A length-1 `items` list is a single environment; unwrap to the sole item so `id`/`deploy-to` (which live on the
    // item, not the wrapper) are read from the same place as the bare id-map shape (see
    // UnifiedDeploymentItemsResolver for the count resolution).
    Map<String, Object> environment = getSingleEnvironmentValue(stageNode);
    if (isNotEmpty(environment)) {
      addEnvironmentToMap(environment, entitiesIDMap);
      addInfrastructureToMap(environment, entitiesIDMap);
      addEnvGroupToMap(environment, entitiesIDMap);
      addOverridesToMap(environment, entitiesIDMap);
    }
  }

  private static void addOverridesToMap(Map<String, Object> environment, Map<String, Object> entitiesIDMap) {
    Object overrides = environment.get("overrides");
    if (!(overrides instanceof Map)) {
      return;
    }

    Map<String, Object> overridesMap = (Map<String, Object>) overrides;

    Object globalOverrides = overridesMap.get("global");
    if (globalOverrides instanceof Map) {
      Map<String, Object> globalOverridesMap = (Map<String, Object>) globalOverrides;
      Object withNode = globalOverridesMap.get(WITH);
      if (withNode instanceof Map) {
        Map<String, Object> withMap = (Map<String, Object>) withNode;
        Object overlayNode = withMap.get(OVERLAY);
        if (overlayNode != null) {
          entitiesIDMap.put(ENV_OVERRIDES_INPUTS, overlayNode);
        }
      }
    }

    Object serviceOverrides = overridesMap.get("service");
    if (serviceOverrides instanceof Map) {
      Map<String, Object> serviceOverridesMap = (Map<String, Object>) serviceOverrides;
      Object withNode = serviceOverridesMap.get(WITH);
      if (withNode instanceof Map) {
        Map<String, Object> withMap = (Map<String, Object>) withNode;
        Object overlayNode = withMap.get(OVERLAY);
        if (overlayNode != null) {
          entitiesIDMap.put(SVC_OVERRIDES_INPUTS, overlayNode);
        }
      }
    }
  }

  private static Map<String, Object> getEnvironmentValue(UnifiedStageNodeV1 stageNode) {
    Object environmentValue = stageNode.getEnvironment().getValue();
    if (!(environmentValue instanceof Map<?, ?>) ) {
      return new HashMap<>();
    }
    return (Map<String, Object>) environmentValue;
  }

  /**
   * Same as {@link #getEnvironmentValue}, but additionally unwraps a length-1 {@code items} list to its sole element
   * map. Used by the single-environment path so a length-1 {@code items} environment is read the same way as the
   * bare id-map shape (both end up as a plain {@code {id, deploy-to, ...}} map).
   */
  private static Map<String, Object> getSingleEnvironmentValue(UnifiedStageNodeV1 stageNode) {
    Object soleEnvironmentItem = UnifiedDeploymentItemsResolver.getSoleEnvironmentItem(stageNode.getEnvironment());
    if (!(soleEnvironmentItem instanceof Map<?, ?>) ) {
      return new HashMap<>();
    }
    return (Map<String, Object>) soleEnvironmentItem;
  }

  private static void addEnvironmentToMap(Map<String, Object> environment, Map<String, Object> entitiesIDMap) {
    String environmentRef = getEnvironmentRef(environment);
    if (environmentRef != null) {
      entitiesIDMap.put(YAMLFieldNameConstants.ENVIRONMENT, environmentRef);
    }

    String envBranchRef = getEnvironmentBranch(environment);
    if (envBranchRef != null) {
      entitiesIDMap.put(ENV_BRANCH_REF, envBranchRef);
    }
  }

  private static void addEnvGroupToMap(Map<String, Object> environment, Map<String, Object> entitiesIDMap) {
    Object envGroupNode = environment.get(GROUP);
    if (envGroupNode != null) {
      if (envGroupNode instanceof String) {
        entitiesIDMap.put(YAMLFieldNameConstants.ENVIRONMENT_GROUP, envGroupNode);
      } else if (envGroupNode instanceof Map<?, ?>) {
        Map<String, Object> envGroupNodeMap = (Map<String, Object>) envGroupNode;
        Object envGroupId = envGroupNodeMap.get(ID);
        if (envGroupId instanceof String) {
          entitiesIDMap.put(YAMLFieldNameConstants.ENVIRONMENT_GROUP, envGroupId);
        } else {
          throw new InvalidYamlException("Environment group is defined in stage, But id is not provided for same. "
              + "Please add Environment Group Id.");
        }
      }
    }
  }

  private static void addInfrastructureToMap(Map<String, Object> environment, Map<String, Object> entitiesIDMap) {
    Object infraNode = environment.get(DEPLOY_TO);
    // A single-infra "equivalent list form" (deploy-to: [i1]) is unwrapped to its sole element here; this method is
    // only reached on the single-env path, so a genuinely multi-infra list never reaches here (see
    // UnifiedMultiDeploymentUtils#isMultiEnvironment / UnifiedDeploymentItemsResolver).
    Object soleInfraNode = UnifiedDeploymentItemsResolver.getSoleDeployToItem(infraNode);
    if (soleInfraNode instanceof String) {
      entitiesIDMap.put(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE, soleInfraNode);
    } else if (soleInfraNode instanceof Map<?, ?>) {
      Map<String, Object> infraNodeMap = (Map<String, Object>) soleInfraNode;
      addInfrastructureDetails(infraNodeMap, entitiesIDMap);
    }
  }

  private static void addInfrastructureDetails(Map<String, Object> infraNodeMap, Map<String, Object> entitiesIDMap) {
    Object infraId = infraNodeMap.get(ID);
    if (infraId != null) {
      entitiesIDMap.put(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE, infraId);
    }
    Object infraInputs = infraNodeMap.get(WITH);
    if (infraInputs != null) {
      entitiesIDMap.put(INFRA_INPUTS, infraInputs);
    }
  }

  private static void setMultiEnvAndInfraNodeInfo(UnifiedStageNodeV1 stageNode, Map<String, Object> entitiesIDMap) {
    entitiesIDMap.put(YAMLFieldNameConstants.ENVIRONMENT, MATRIX_ENVIRONMENT_REF);
    entitiesIDMap.put(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE, MATRIX_INFRA_ID);
    entitiesIDMap.put(INFRA_INPUTS, MATRIX_INFRA_INPUTS);
    entitiesIDMap.put(ENV_BRANCH_REF, MATRIX_ENV_BRANCH_REF);

    Map<String, Object> environment = getEnvironmentValue(stageNode);
    addEnvGroupToMap(environment, entitiesIDMap);
  }

  private static void setServiceNodeInfo(UnifiedStageNodeV1 stageNode, Map<String, Object> entitiesInfoMap,
      YamlField serviceField, boolean isMultiService) {
    if (isMultiService) {
      setMultiServiceNodeInfo(entitiesInfoMap);
    } else {
      setSingleServiceNodeInfo(stageNode, entitiesInfoMap, serviceField);
    }
  }

  /**
   * Validates the optional group-level service {@code type} at plan creation and, when declared, threads the normalized
   * value into the deploy module info so it can be validated against the resolved service at runtime. Runtime services
   * must declare a {@code type} (see {@link UnifiedServiceTypeValidatorUtils#validateServiceType}).
   */
  private static void setServiceTypeInfo(UnifiedStageNodeV1 stageNode, Map<String, Object> entitiesInfoMap) {
    ParameterField<Object> service = stageNode.getService();
    if (ParameterField.isNull(service)) {
      return;
    }
    UnifiedServiceTypeValidatorUtils.validateServiceType(service);
    String declaredType = UnifiedServiceTypeValidatorUtils.extractDeclaredServiceType(service);
    if (isNotEmpty(declaredType)) {
      entitiesInfoMap.put(
          SERVICE_TYPE, UnifiedServiceTypeValidatorUtils.parseServiceTypeOrThrow(declaredType).getDisplayName());
    }
  }

  private static void setMultiServiceNodeInfo(Map<String, Object> entitiesIDMap) {
    entitiesIDMap.put(YAMLFieldNameConstants.SERVICE, MATRIX_SERVICE_REF);
    entitiesIDMap.put(SERVICE_INPUTS, MATRIX_SERVICE_INPUTS);
    // Wire each child's branch to its per-item ref so remote services in a multi-service group resolve from their own
    // branch (mirrors the multi-env envBranchRef wiring). Resolves to "" for items without a ref -> pipeline context.
    entitiesIDMap.put(SVC_BRANCH_REF, MATRIX_SVC_BRANCH_REF);
  }

  private static void setSingleServiceNodeInfo(
      UnifiedStageNodeV1 stageNode, Map<String, Object> entitiesIDMap, YamlField serviceField) {
    if (serviceField == null) {
      return;
    }
    JsonNode currentNode = serviceField.getNode().getCurrJsonNode();
    if (currentNode instanceof TextNode) {
      entitiesIDMap.put(YAMLFieldNameConstants.SERVICE, currentNode.asText());
      return;
    }
    ParameterField<Object> service = stageNode.getService();
    if (ParameterField.isNull(service)) {
      return;
    }

    // A length-1 `items` list is a single service; unwrap to the sole item (bare string, expression, or id-map) so
    // it is read the same way as the non-`items` shapes below (see UnifiedDeploymentItemsResolver for the count
    // resolution).
    Object serviceValue = UnifiedDeploymentItemsResolver.getSoleServiceItem(service);
    if (serviceValue instanceof String serviceIdOrExpression) {
      entitiesIDMap.put(YAMLFieldNameConstants.SERVICE, serviceIdOrExpression);
      return;
    }

    if (serviceValue instanceof Map<?, ?>) {
      Map<String, Object> serviceValueMap = (Map<String, Object>) serviceValue;
      Object serviceId = serviceValueMap.get(ID);
      if (serviceId == null) {
        throw new InvalidYamlException("Service id is missing in stage yaml");
      }
      Object branch = serviceValueMap.get(YAMLFieldNameConstants.BRANCH);
      if (branch != null) {
        entitiesIDMap.put(CdStepParametersInfoConstants.SVC_BRANCH_REF, branch);
      }
      entitiesIDMap.put(YAMLFieldNameConstants.SERVICE, serviceId);
      Object serviceInputs = serviceValueMap.get(WITH);
      if (serviceInputs != null) {
        entitiesIDMap.put(SERVICE_INPUTS, serviceInputs);
      }
      return;
    }

    // Throw exception in case service node doesn't match any of above collections
    throw new InvalidYamlException("Please check the service defined in stage");
  }

  private static String getEnvironmentBranch(Map<String, Object> environment) {
    if (environment.get(BRANCH) != null) {
      return (String) environment.get(BRANCH);
    }
    return null;
  }

  private static String getEnvironmentRef(Map<String, Object> environment) {
    if (environment.get(ID) == null) {
      throw new InvalidYamlException(
          "Environment node yaml is invalid. Please check if required field [id] field is provided.");
    }
    return (String) environment.get(ID);
  }

  public static void getDeployStageChildrenEntitiesInfo(
      Map<String, Object> modulesImplicitNodesInfo, ListValue.Builder stageChildren) {
    Object deployModuleValue = modulesImplicitNodesInfo.get(TemplateType.DEPLOY.getName());
    if (deployModuleValue instanceof Map<?, ?>) {
      Map<String, Object> deployModuleInfo = (Map<String, Object>) deployModuleValue;
      if (deployModuleInfo.containsKey(YAMLFieldNameConstants.SERVICE)) {
        stageChildren.addValues(
            HarnessValue.newBuilder().setStringValue(StageChildrenEntitiesType.SERVICE.getDisplayName()).build());
      }
      if (deployModuleInfo.containsKey(YAMLFieldNameConstants.ENVIRONMENT)) {
        stageChildren.addValues(
            HarnessValue.newBuilder().setStringValue(StageChildrenEntitiesType.ENVIRONMENT.getDisplayName()).build());
      }
    }
  }
}
