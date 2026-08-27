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
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeFailureCriteria;
import io.harness.steps.fme.FmeFailureCriteriaSpec;
import io.harness.steps.fme.FmeMetricCheckStepParameters;
import io.harness.steps.fme.FmeMetricRef;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeMetricCheckStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.GONZALO)
  @Category(UnitTests.class)
  public void testFacilitatorTypeIsAsync() {
    FmeMetricCheckStepInfo info = new FmeMetricCheckStepInfo();
    assertThat(info.getFacilitatorType()).isEqualTo(OrchestrationFacilitatorType.ASYNC);
    assertThat(info.getStepType()).isEqualTo(StepSpecTypeConstants.FME_METRIC_CHECK_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.GONZALO)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    FmeMetricCheckStepInfo info = new FmeMetricCheckStepInfo();
    String facilitatorType = info.getFacilitatorType();
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.ASYNC);
  }

  @Test
  @Owner(developers = OwnerRule.GONZALO)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    FmeFailureCriteria failureCriteria =
        FmeFailureCriteria.builder()
            .type("Jexl")
            .spec(FmeFailureCriteriaSpec.builder()
                      .condition(ParameterField.createValueField("monitor.metric(\"m1\").mean > 0.05"))
                      .build())
            .build();

    FmeMetricCheckStepInfo info =
        FmeMetricCheckStepInfo.builder()
            .flagName(ParameterField.createValueField("test_flag"))
            .environment(ParameterField.createValueField("production"))
            .metrics(Arrays.asList(new FmeMetricRef("metric_1"), new FmeMetricRef("metric_2")))
            .lookbackWindow(ParameterField.createValueField("7d"))
            .failureCriteria(failureCriteria)
            .build();

    FmeMetricCheckStepParameters params = (FmeMetricCheckStepParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getFlagName().getValue()).isEqualTo("test_flag");
    assertThat(params.getEnvironment().getValue()).isEqualTo("production");
    assertThat(params.getMetrics()).hasSize(2);
    assertThat(params.getMetrics().get(0).getRef()).isEqualTo("metric_1");
    assertThat(params.getMetrics().get(1).getRef()).isEqualTo("metric_2");
    assertThat(params.getLookbackWindow().getValue()).isEqualTo("7d");
    assertThat(params.getFailureCriteria().getType()).isEqualTo("Jexl");
    assertThat(params.getFailureCriteria().getSpec().getCondition().getValue())
        .isEqualTo("monitor.metric(\"m1\").mean > 0.05");
  }

  @Test
  @Owner(developers = OwnerRule.GONZALO)
  @Category(UnitTests.class)
  public void testGetSpecParameters_nullMetrics_handledGracefully() {
    FmeFailureCriteria failureCriteria =
        FmeFailureCriteria.builder()
            .type("Jexl")
            .spec(FmeFailureCriteriaSpec.builder().condition(ParameterField.createValueField("true")).build())
            .build();

    FmeMetricCheckStepInfo info = FmeMetricCheckStepInfo.builder()
                                      .flagName(ParameterField.createValueField("my_flag"))
                                      .environment(ParameterField.createValueField("env1"))
                                      .lookbackWindow(ParameterField.createValueField("24h"))
                                      .failureCriteria(failureCriteria)
                                      .build();

    FmeMetricCheckStepParameters params = (FmeMetricCheckStepParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getFlagName().getValue()).isEqualTo("my_flag");
    assertThat(params.getMetrics()).isNull();
    assertThat(params.getLookbackWindow().getValue()).isEqualTo("24h");
  }
}
