/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.unified;

import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.beans.unified.UnifiedApprovalType;
import io.harness.steps.approval.step.beans.unified.UnifiedManualApprovalApproverInfo;
import io.harness.steps.approval.step.beans.unified.UnifiedManualApprovalApproverInfoWrapper;
import io.harness.steps.approval.step.beans.unified.UnifiedManualApprovalStepSpec;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class UnifiedManualApprovalStepPlanCreatorTest extends CategoryTest {
  private UnifiedManualApprovalStepPlanCreator unifiedManualApprovalStepPlanCreator;

  private String approvalStepYaml;

  @Before
  public void setUp() {
    approvalStepYaml = getApprovalStepYaml();
    unifiedManualApprovalStepPlanCreator = new UnifiedManualApprovalStepPlanCreator();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes = unifiedManualApprovalStepPlanCreator.getSupportedStepTypes();
    assertThat(supportedStepTypes).hasSize(1);
    assertThat(supportedStepTypes).contains(YAMLFieldNameConstants.UNIFIED_MANUAL_APPROVAL);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(unifiedManualApprovalStepPlanCreator.getFieldClass()).isEqualTo(UnifiedManualApprovalStepNode.class);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetStepType() {
    StepType stepType = unifiedManualApprovalStepPlanCreator.getStepType();
    assertThat(stepType.getType()).isEqualTo(StepSpecTypeConstants.HARNESS_APPROVAL);
    assertThat(stepType.getStepCategory()).isEqualTo(StepCategory.STEP);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetFieldObject() throws IOException {
    YamlField yamlField = YamlUtils.readTree(approvalStepYaml);
    UnifiedManualApprovalStepNode stepNode = unifiedManualApprovalStepPlanCreator.getFieldObject(yamlField);

    // Verify node structure
    assertThat(stepNode).isNotNull();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.HARNESS_APPROVAL);
    assertThat(stepNode.getFacilitatorType()).isEqualTo(StepSpecTypeConstants.APPROVAL_FACILITATOR);

    // Verify approval info
    assertThat(stepNode.getUnifiedApprovalStepInfo()).isNotNull();
    assertThat(stepNode.getUnifiedApprovalStepInfo().getType()).isEqualTo(UnifiedApprovalType.UNIFIED_MANUAL_APPROVAL);

    // Verify spec
    UnifiedManualApprovalStepSpec spec =
        (UnifiedManualApprovalStepSpec) stepNode.getUnifiedApprovalStepInfo().getSpec();
    assertThat(spec).isNotNull();
    assertThat(spec.getMessage().obtainValue()).isEqualTo("Test message");
    assertThat(spec.getUserGroups().obtainValue()).contains("_project_all_users");
    assertThat(spec.getServiceAccounts().obtainValue()).contains("dummyToken");
    assertThat(spec.getApproverMinCount().obtainValue()).isEqualTo(1);
    assertThat(spec.getBlockExecutor().obtainValue()).isFalse();
    assertThat(spec.getExecutionDetails().obtainValue()).isTrue();
    assertThat(spec.getAutoReject().obtainValue()).isTrue();
    assertThat(spec.getAutoApprove()).isTrue();
    assertThat(spec.getDeadline().obtainValue()).isEqualTo("2025-04-18 01:55 AM");
    assertThat(spec.getTimezone().obtainValue()).isEqualTo("Asia/Calcutta");
    assertThat(spec.getCallbackId().obtainValue()).isEqualTo("approval-Id");

    // Verifying inputs
    UnifiedManualApprovalApproverInfoWrapper inputsWrapper = spec.getInputs();
    assertThat(inputsWrapper).isNotNull();
    Map<String, UnifiedManualApprovalApproverInfo> inputs = inputsWrapper.getMap();
    assertThat(inputs).hasSize(2);
    assertThat(inputs).containsKeys("demo", "feat");

    UnifiedManualApprovalApproverInfo demoInput = inputs.get("demo");
    assertThat(demoInput.getDescription()).isEqualTo("Demo input");
    assertThat(demoInput.getRegex().obtainValue()).isEqualTo(".*");
    assertThat(demoInput.isMultiSelect()).isFalse();
    assertThat(demoInput.isRequired()).isTrue();
    assertThat(demoInput.getEnumList().obtainValue()).containsExactly("Rishi", "Bobby");
    assertThat(demoInput.getDefaultValue().obtainValue()).isEqualTo("Rishi");

    UnifiedManualApprovalApproverInfo featInput = inputs.get("feat");
    assertThat(featInput.getDescription()).isEqualTo("Feature input");
    assertThat(featInput.isMultiSelect()).isTrue();
    assertThat(featInput.isRequired()).isFalse();
    assertThat(featInput.getEnumList().obtainValue()).containsExactly("feat1", "feat2", "feat3");
    assertThat(featInput.getDefaultValue().getValue()).isNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInvalidYaml() {
    String invalidYaml = "invalid: yaml: format";
    YamlField mockField = mock(YamlField.class);
    YamlNode mockNode = mock(YamlNode.class);
    when(mockField.getNode()).thenReturn(mockNode);
    when(mockNode.toString()).thenReturn(invalidYaml);

    assertThatThrownBy(() -> unifiedManualApprovalStepPlanCreator.getFieldObject(mockField))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Unable to parse approval step yaml");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetFieldObjectIgnoresInjectedUuidInInputsMap() throws IOException {
    // Simulate the production plan-creation path: __uuid is injected into every object
    // node (including the inputs map and each entry) BEFORE deserialization happens.
    YamlField yamlField = YamlUtils.injectUuidInYamlField(approvalStepYaml);

    UnifiedManualApprovalStepNode stepNode = unifiedManualApprovalStepPlanCreator.getFieldObject(yamlField);

    UnifiedManualApprovalStepSpec spec =
        (UnifiedManualApprovalStepSpec) stepNode.getUnifiedApprovalStepInfo().getSpec();
    UnifiedManualApprovalApproverInfoWrapper inputsWrapper = spec.getInputs();
    assertThat(inputsWrapper).isNotNull();
    Map<String, UnifiedManualApprovalApproverInfo> inputs = inputsWrapper.getMap();
    // The injected "__uuid" key must NOT leak into the map.
    assertThat(inputs).doesNotContainKey("__uuid");
    assertThat(inputs).hasSize(2);
    assertThat(inputs).containsKeys("demo", "feat");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetApproverInputInfoWhenInputsIsNull() {
    UnifiedManualApprovalStepSpec spec = UnifiedManualApprovalStepSpec.builder().autoApprove(false).build();
    // getSpecParameters() should not throw NPE when inputs is null and should produce zero
    // approver inputs.
    assertThat(spec.getSpecParameters()).isNotNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testDuplicateInputKeysAreRejected() {
    String yamlWithDuplicateInputKeys = "approval:\n"
        + "  uses: harness\n"
        + "  with:\n"
        + "    message: Test message\n"
        + "    inputs:\n"
        + "      demo:\n"
        + "        description: \"first\"\n"
        + "        required: true\n"
        + "      demo:\n"
        + "        description: \"second\"\n"
        + "        required: false\n";

    assertThatThrownBy(() -> YamlUtils.readTree(yamlWithDuplicateInputKeys))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Duplicate field 'demo'");
  }

  private String getApprovalStepYaml() {
    String approvalStepYaml = "approval:\n"
        + "  uses: harness\n"
        + "  with:\n"
        + "    message: Test message\n"
        + "    execution-details: true\n"
        + "    approvers-min-count: 1\n"
        + "    block-executor: false\n"
        + "    user-groups:\n"
        + "      - _project_all_users\n"
        + "    service-accounts: dummyToken\n"
        + "    auto-reject: true\n"
        + "    inputs:\n"
        + "      demo:\n"
        + "        description: \"Demo input\"\n"
        + "        pattern: \".*\"\n"
        + "        required: true\n"
        + "        multi-select: false\n"
        + "        default: \"Rishi\"\n"
        + "        enum:\n"
        + "          - Rishi\n"
        + "          - Bobby\n"
        + "      feat:\n"
        + "        description: \"Feature input\"\n"
        + "        required: false\n"
        + "        multi-select: true\n"
        + "        enum:\n"
        + "          - feat1\n"
        + "          - feat2\n"
        + "          - feat3\n"
        + "    auto-approve: true\n"
        + "    deadline: 2025-04-18 01:55 AM\n"
        + "    timezone: Asia/Calcutta\n"
        + "    comments: Auto approved by Harness via Harness Approval step\n"
        + "    callback: approval-Id\n"
        + "timeout: 10m\n"
        + "strategy:\n"
        + "  matrix:\n"
        + "    version:\n"
        + "      - \"1.0\"\n"
        + "      - \"1.1\"\n"
        + "on-failure:\n"
        + "  errors: all\n"
        + "  action: abort";
    return approvalStepYaml;
  }
}
