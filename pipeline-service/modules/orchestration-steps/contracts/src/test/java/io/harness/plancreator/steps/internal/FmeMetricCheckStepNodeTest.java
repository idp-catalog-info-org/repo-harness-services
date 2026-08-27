/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeFailureCriteria;
import io.harness.steps.fme.FmeFailureCriteriaSpec;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeMetricCheckStepNodeTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.GONZALO)
  @Category(UnitTests.class)
  public void testGetType() {
    FmeMetricCheckStepNode node = new FmeMetricCheckStepNode();
    assertThat(node.getType()).isEqualTo(StepSpecTypeConstants.FME_METRIC_CHECK_STEP_TYPE.getType());
  }

  @Test
  @Owner(developers = OwnerRule.GONZALO)
  @Category(UnitTests.class)
  public void testGetStepSpecType() {
    FmeMetricCheckStepNode node = new FmeMetricCheckStepNode();
    FmeMetricCheckStepInfo info =
        FmeMetricCheckStepInfo.builder()
            .flagName(ParameterField.createValueField("flag"))
            .environment(ParameterField.createValueField("env"))
            .lookbackWindow(ParameterField.createValueField("7d"))
            .failureCriteria(
                FmeFailureCriteria.builder()
                    .type("Jexl")
                    .spec(FmeFailureCriteriaSpec.builder().condition(ParameterField.createValueField("true")).build())
                    .build())
            .build();
    node.setFmeMetricCheckStepInfo(info);

    assertThat(node.getStepSpecType()).isEqualTo(info);
  }
}
