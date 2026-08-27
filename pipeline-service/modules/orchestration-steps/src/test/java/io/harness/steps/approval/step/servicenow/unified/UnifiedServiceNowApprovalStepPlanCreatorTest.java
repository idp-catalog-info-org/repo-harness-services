/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.steps.approval.step.servicenow.unified;

import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.steps.v1.Download;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.beans.CriteriaSpecType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapper;
import io.harness.steps.approval.step.beans.JexlCriteriaSpec;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpec;
import io.harness.steps.approval.step.beans.unified.UnifiedApprovalType;
import io.harness.steps.approval.step.beans.unified.UnifiedCriteriaMapper;
import io.harness.steps.approval.step.beans.unified.UnifiedServiceNowApprovalStepSpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
public class UnifiedServiceNowApprovalStepPlanCreatorTest extends CategoryTest {
  private UnifiedServiceNowApprovalStepPlanCreator unifiedServiceNowApprovalStepPlanCreator;
  @Before
  public void setUp() {
    unifiedServiceNowApprovalStepPlanCreator = new UnifiedServiceNowApprovalStepPlanCreator();
  }
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes = unifiedServiceNowApprovalStepPlanCreator.getSupportedStepTypes();
    assertThat(supportedStepTypes).hasSize(1);
    assertThat(supportedStepTypes).contains(YAMLFieldNameConstants.UNIFIED_SERVICENOW_APPROVAL);
  }
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(unifiedServiceNowApprovalStepPlanCreator.getFieldClass())
        .isEqualTo(UnifiedServiceNowApprovalStepNode.class);
  }
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetStepType() {
    StepType stepType = unifiedServiceNowApprovalStepPlanCreator.getStepType();
    assertThat(stepType.getType()).isEqualTo(StepSpecTypeConstants.SERVICENOW_APPROVAL);
    assertThat(stepType.getStepCategory()).isEqualTo(StepCategory.STEP);
  }
  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetStepExpressionMode() {
    // Approval criteria reference the fetched ticket fields, available only after polling. The plan node must keep
    // unresolved expressions as-is instead of collapsing them to null during step-parameter resolution.
    assertThat(unifiedServiceNowApprovalStepPlanCreator.getStepExpressionMode())
        .isEqualTo(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
  }
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testServieNowApprovalStep() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL serviceNowApprovalYAMLFile = classLoader.getResource("servicenow-approval-unified.yaml");
    assertThat(serviceNowApprovalYAMLFile).isNotNull();
    String serviceNowApprovalYAML = Resources.toString(serviceNowApprovalYAMLFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(serviceNowApprovalYAML);
    UnifiedServiceNowApprovalStepNode stepNode = unifiedServiceNowApprovalStepPlanCreator.getFieldObject(yamlField);
    // Verify node structure
    assertThat(stepNode).isNotNull();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.SERVICENOW_APPROVAL);
    assertThat(stepNode.getFacilitatorType()).isEqualTo(StepSpecTypeConstants.APPROVAL_FACILITATOR);
    // Verify approval info
    assertThat(stepNode.getUnifiedApprovalStepInfo()).isNotNull();
    assertThat(stepNode.getUnifiedApprovalStepInfo().getType())
        .isEqualTo(UnifiedApprovalType.UNIFIED_SERVICENOW_APPROVAL);
    // Verify spec
    UnifiedServiceNowApprovalStepSpec spec =
        (UnifiedServiceNowApprovalStepSpec) stepNode.getUnifiedApprovalStepInfo().getSpec();
    assertThat(spec).isNotNull();
    // Verify main properties
    assertThat(spec.getRetry()).isEqualTo("10m");
    // Verify change window
    assertThat(spec.getChangeWindow().getStart().obtainValue()).isEqualTo("Opened");
    assertThat(spec.getChangeWindow().getEnd().obtainValue()).isEqualTo("Closed");
    // Verify approve criteria
    assertThat(spec.getApprove()).isNotNull();
    CriteriaSpecWrapper approveWrapper = UnifiedCriteriaMapper.toCriteriaSpecWrapper(spec.getApprove());
    assertThat(approveWrapper).isNotNull();
    assertThat(approveWrapper.getType()).isEqualTo(CriteriaSpecType.KEY_VALUES);

    KeyValuesCriteriaSpec keyValueSpec = (KeyValuesCriteriaSpec) approveWrapper.getCriteriaSpec();
    assertThat(keyValueSpec).isNotNull();
    assertThat(keyValueSpec.getMatchAnyCondition().getValue()).isEqualTo(true);
    assertThat(keyValueSpec.getConditions()).hasSize(4);

    assertThat(keyValueSpec.getConditions().get(0).getKey()).isEqualTo("state");
    assertThat(keyValueSpec.getConditions().get(0).getValue().obtainValue()).isEqualTo("Opened");
    assertThat(keyValueSpec.getConditions().get(0).getOperator().getDisplayName()).isEqualTo("equals");

    assertThat(keyValueSpec.getConditions().get(1).getKey()).isEqualTo("state");
    assertThat(keyValueSpec.getConditions().get(1).getValue().obtainValue()).isEqualTo("Opened,Closed");
    assertThat(keyValueSpec.getConditions().get(1).getOperator().getDisplayName()).isEqualTo("in");

    assertThat(keyValueSpec.getConditions().get(2).getKey()).isEqualTo("state");
    assertThat(keyValueSpec.getConditions().get(2).getValue().obtainValue()).isEqualTo("Resolved,In Progress");
    assertThat(keyValueSpec.getConditions().get(2).getOperator().getDisplayName()).isEqualTo("not in");

    assertThat(keyValueSpec.getConditions().get(3).getKey()).isEqualTo("state");
    assertThat(keyValueSpec.getConditions().get(3).getValue().obtainValue()).isEqualTo("Resolved");
    assertThat(keyValueSpec.getConditions().get(3).getOperator().getDisplayName()).isEqualTo("not equals");

    // Verify reject criteria
    assertThat(spec.getReject()).isNotNull();
    CriteriaSpecWrapper rejectWrapper = UnifiedCriteriaMapper.toCriteriaSpecWrapper(spec.getReject());
    assertThat(rejectWrapper).isNotNull();
    assertThat(rejectWrapper.getType()).isEqualTo(CriteriaSpecType.JEXL);

    JexlCriteriaSpec jexlSpec = (JexlCriteriaSpec) rejectWrapper.getCriteriaSpec();
    assertThat(jexlSpec).isNotNull();
    assertThat(jexlSpec.getExpression().getValue()).isEqualTo("<+<+pipeline.name> == \"ServiceNowApproval\">");

    // Verify RunStep
    Map<String, JsonNode> env = spec.getRunStep().getEnv().obtainValue();
    assertThat(env).hasSize(7);
    assertThat(env.get("PLUGIN_LOG_LEVEL").textValue()).isEqualTo("info");
    assertThat(env.get("PLUGIN_TICKET_TYPE").textValue()).isEqualTo("incident");
    assertThat(env.get("PLUGIN_TICKET_NUMBER").textValue()).isEqualTo("IncidentNumber");
    assertThat(env.get("PLUGIN_SERVICENOW_NAME").textValue()).isEqualTo("ServiceNowConnector");
    assertThat(env.get("PLUGIN_SERVICENOW_URL").textValue()).isEqualTo("ServiceNowURL");
    assertThat(env.get("PLUGIN_SERVICENOW_USERNAME").textValue()).isEqualTo("ServiceNow_Username");
    assertThat(env.get("PLUGIN_SERVICENOW_PASSWORD").textValue()).isEqualTo("ServiceNow_Pwd");

    // Verify Script
    String script = spec.getRunStep().getScript().obtainValue();
    assertThat(script).isEqualTo("pwd && $PLUGIN_PATH");

    // Verify Download
    Download download = spec.getRunStep().getDownload().obtainValue();
    assertThat(download.getSource()).isEqualTo("/binary/location/ServiceNow");
    assertThat(download.getTarget()).isEqualTo("/target/path");
    assertThat(download.getChecksum()).isEqualTo("df6a4178aec9fbdc1d6d7e3634d1bc33");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testInvalidYaml() {
    String invalidYaml = "invalid: yaml: format";
    YamlField mockField = mock(YamlField.class);
    YamlNode mockNode = mock(YamlNode.class);
    when(mockField.getNode()).thenReturn(mockNode);
    when(mockNode.toString()).thenReturn(invalidYaml);
    assertThatThrownBy(() -> unifiedServiceNowApprovalStepPlanCreator.getFieldObject(mockField))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Unable to parse ServiceNow approval step yaml");
  }
}
