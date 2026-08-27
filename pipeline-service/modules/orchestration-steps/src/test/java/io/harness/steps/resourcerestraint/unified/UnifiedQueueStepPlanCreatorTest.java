/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.unified;

import static io.harness.rule.OwnerRule.RISHIKESH;

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
import io.harness.steps.resourcerestraint.QueueHoldingScope;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class UnifiedQueueStepPlanCreatorTest extends CategoryTest {
  private UnifiedQueueStepPlanCreator unifiedQueueStepPlanCreator;

  private String queueStepYaml;

  @Before
  public void setUp() {
    queueStepYaml = getQueueStepYaml();
    unifiedQueueStepPlanCreator = new UnifiedQueueStepPlanCreator();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    assertThat(unifiedQueueStepPlanCreator.getSupportedStepTypes()).hasSize(1);
    assertThat(unifiedQueueStepPlanCreator.getSupportedStepTypes()).contains(YAMLFieldNameConstants.QUEUE_V1);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(unifiedQueueStepPlanCreator.getFieldClass()).isEqualTo(UnifiedQueueStepNode.class);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetStepType() {
    StepType stepType = unifiedQueueStepPlanCreator.getStepType();
    assertThat(stepType.getType()).isEqualTo(StepSpecTypeConstants.QUEUE);
    assertThat(stepType.getStepCategory()).isEqualTo(StepCategory.STEP);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetFieldObject() throws IOException {
    YamlField yamlField = YamlUtils.readTree(queueStepYaml);
    UnifiedQueueStepNode stepNode = unifiedQueueStepPlanCreator.getFieldObject(yamlField);

    // Verify node structure
    assertThat(stepNode).isNotNull();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.QUEUE);
    assertThat(stepNode.getFacilitatorType()).isEqualTo(StepSpecTypeConstants.RESOURCE_RESTRAINT_FACILITATOR_TYPE);

    // Verify queue step info
    assertThat(stepNode.getUnifiedQueueStepInfo()).isNotNull();

    // Verify key and scope
    assertThat(stepNode.getUnifiedQueueStepInfo().getKey().obtainValue()).isEqualTo("test-queue-key");
    assertThat(stepNode.getUnifiedQueueStepInfo().getScope()).isEqualTo(QueueHoldingScope.PIPELINE);
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

    assertThatThrownBy(() -> unifiedQueueStepPlanCreator.getFieldObject(mockField))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Unable to parse queue step yaml");
  }

  private String getQueueStepYaml() {
    String queueStepYaml = "queue:\n"
        + "  key: test-queue-key\n"
        + "  scope: pipeline\n"
        + "timeout: 10m\n"
        + "strategy:\n"
        + "  matrix:\n"
        + "    version:\n"
        + "      - \"1.0\"\n"
        + "      - \"1.1\"\n"
        + "on-failure:\n"
        + "  errors: all\n"
        + "  action: abort";
    return queueStepYaml;
  }
}
