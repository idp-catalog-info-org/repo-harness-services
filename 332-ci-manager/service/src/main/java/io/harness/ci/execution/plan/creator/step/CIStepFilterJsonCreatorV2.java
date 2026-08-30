/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.step;

import static io.harness.beans.FeatureName.CDS_AI_VERIFY_DEMO;
import static io.harness.beans.FeatureName.CI_ENABLE_GENERIC_CACHE_STEPS;
import static io.harness.beans.FeatureName.CI_SECRET_EXPRESSION_REFERENCES;
import static io.harness.beans.FeatureName.ML_ENABLE_AI_AGENTS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.AgentStepNode;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.beans.steps.nodes.RunTestStepV2Node;
import io.harness.beans.steps.stepinfo.AgentStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestStepV2Info;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.execution.plan.creator.filter.CISecretExpressionExtractor;
import io.harness.ci.execution.plan.creator.utils.CICreatorUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.exception.InvalidYamlException;
import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.util.Strings;

@OwnedBy(HarnessTeam.CI)
public class CIStepFilterJsonCreatorV2 extends GenericStepPMSFilterJsonCreatorV2 {
  @Inject private CIFeatureFlagService ciFeatureFlagService;

  @Override
  public Set<String> getSupportedStepTypes() {
    return CICreatorUtils.getSupportedStepsV2();
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    validateStep(filterCreationContext, yamlField);
    FilterCreationResponse response = super.handleNode(filterCreationContext, yamlField);
    if (ciFeatureFlagService.isEnabled(
            CI_SECRET_EXPRESSION_REFERENCES, filterCreationContext.getSetupMetadata().getAccountId())) {
      response.addReferredEntities(CISecretExpressionExtractor.extract(filterCreationContext));
    }
    return response;
  }

  public void validateStep(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    String infra = getStageInfra(filterCreationContext);
    String k = CIStepInfoType.RUN.getDisplayName();
    String stepType = yamlField.getType();
    switch (stepType) {
      case "Run":
        validateRunStep(infra, (RunStepNode) yamlField);
        break;
      case "RunTests":
        validateRunTestsStep(infra, (RunTestStepNode) yamlField);
        break;
      case "Test":
        validateRunTestStepV2(infra, (RunTestStepV2Node) yamlField, filterCreationContext);
        break;
      case "AiVerify":
        validateAIVerifyStep(filterCreationContext);
        break;
      case "SaveCache":
      case "RestoreCache":
        validateGenericCacheStep(filterCreationContext);
        break;
      case "Agent":
        validateAgentStep(filterCreationContext, yamlField);
        break;
      default:
        break;
    }
  }

  private void validateAIVerifyStep(FilterCreationContext filterCreationContext) {
    if (!ciFeatureFlagService.isEnabled(CDS_AI_VERIFY_DEMO, filterCreationContext.getSetupMetadata().getAccountId())) {
      throw new InvalidYamlException("AI Verify Step is not enabled for your account");
    }
  }

  private void validateAgentStep(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    if (!ciFeatureFlagService.isEnabled(ML_ENABLE_AI_AGENTS, filterCreationContext.getSetupMetadata().getAccountId())) {
      throw new InvalidYamlException("Agent step is not enabled. Please enable the ML_ENABLE_AI_AGENTS feature flag.");
    }
    AgentStepNode agentStepNode = (AgentStepNode) yamlField;
    AgentStepInfo agentStepInfo = agentStepNode.getAgentStepInfo();
    if (agentStepInfo.getAgentName() == null || ParameterField.isBlank(agentStepInfo.getAgentName())) {
      throw new InvalidYamlException("agentName is required for Agent step");
    }
  }

  private void validateGenericCacheStep(FilterCreationContext filterCreationContext) {
    if (!ciFeatureFlagService.isEnabled(
            CI_ENABLE_GENERIC_CACHE_STEPS, filterCreationContext.getSetupMetadata().getAccountId())) {
      throw new InvalidYamlException("Generic Cache Steps are not enabled for your account");
    }
  }
  private void validateRunTestStepV2(
      String infra, RunTestStepV2Node runTestStepV2Node, FilterCreationContext filterCreationContext) {
    RunTestStepV2Info runTestStepV2Info = runTestStepV2Node.getRunTestStepV2Info();
    if (Infrastructure.Type.KUBERNETES_DIRECT.getYamlName().equals(infra)) {
      String connectorRef = runTestStepV2Info.getConnectorRef().getValue();
      String connectorExpression = runTestStepV2Info.getConnectorRef().getExpressionValue();

      String image = runTestStepV2Info.getImage().getValue();
      String imageExpression = runTestStepV2Info.getImage().getExpressionValue();

      if (Strings.isBlank(connectorRef) && Strings.isBlank(connectorExpression)
          && ParameterField.isBlank(runTestStepV2Info.getRegistryRef())) {
        throw new InvalidYamlException("Test step with Kubernetes infra can't have empty connector field");
      }
      if (Strings.isBlank(image) && Strings.isBlank(imageExpression)) {
        throw new InvalidYamlException("Test step with Kubernetes infra can't have empty image field");
      }
    }
  }
  private void validateRunStep(String infra, RunStepNode runStepNode) {
    RunStepInfo runStep = runStepNode.getRunStepInfo();
    if (Infrastructure.Type.KUBERNETES_DIRECT.getYamlName().equals(infra)) {
      String connectorRef = runStep.getConnectorRef().getValue();
      String connectorExpression = runStep.getConnectorRef().getExpressionValue();

      String image = runStep.getImage().getValue();
      String imageExpression = runStep.getImage().getExpressionValue();

      if (Strings.isBlank(connectorRef) && Strings.isBlank(connectorExpression)
          && ParameterField.isBlank(runStep.getRegistryRef())) {
        throw new InvalidYamlException("Run step with Kubernetes infra can't have empty connector field");
      }
      if (Strings.isBlank(image) && Strings.isBlank(imageExpression)) {
        throw new InvalidYamlException("Run step with Kubernetes infra can't have empty image field");
      }
    }
  }

  private void validateRunTestsStep(String infra, RunTestStepNode runStepNode) {
    RunTestsStepInfo runTestsStep = runStepNode.getRunTestsStepInfo();
    if (Infrastructure.Type.KUBERNETES_DIRECT.getYamlName().equals(infra)) {
      String connectorRef = runTestsStep.getConnectorRef().getValue();
      String connectorExpression = runTestsStep.getConnectorRef().getExpressionValue();

      String image = runTestsStep.getImage().getValue();
      String imageExpression = runTestsStep.getImage().getExpressionValue();

      if (Strings.isBlank(connectorRef) && Strings.isBlank(connectorExpression)) {
        throw new InvalidYamlException("RunTests step with Kubernetes infra can't have empty connector field");
      }
      if (Strings.isBlank(image) && Strings.isBlank(imageExpression)) {
        throw new InvalidYamlException("RunTests step with Kubernetes infra can't have empty image field");
      }
    }
  }

  private String getStageInfra(FilterCreationContext filterCreationContext) {
    YamlNode currNode = filterCreationContext.getCurrentField().getNode();
    while (!Objects.isNull(currNode)) {
      if (!Objects.isNull(currNode.getField("infrastructure"))) {
        break;
      }
      currNode = currNode.getParentNode();
    }
    if (!Objects.isNull(currNode)) {
      return currNode.getField("infrastructure").getType();
    }
    return null;
  }
}
