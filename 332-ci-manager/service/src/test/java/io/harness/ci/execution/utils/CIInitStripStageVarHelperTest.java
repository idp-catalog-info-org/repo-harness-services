/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.utils.ci.CIInitStripStageVarHelper;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CIInitStripStageVarHelperTest {
  private static final String ACCOUNT_ID = "accountId";

  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @InjectMocks private CIInitStripStageVarHelper ciInitStripStageVarHelper;

  private final Ambiance ambiance = Ambiance.newBuilder().build();

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void ffEnabledShortCircuitsWithoutResolvingSweepingOutput() {
    when(featureFlagService.isEnabled(FeatureName.CI_INIT_REQUIRED_FIELDS_ONLY, ACCOUNT_ID)).thenReturn(true);

    assertThat(ciInitStripStageVarHelper.isRequiredFieldsOnlyInitStripEnabled(ambiance, ACCOUNT_ID)).isTrue();
    verify(executionSweepingOutputResolver, never()).resolveOptional(any(), any());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void ffDisabledButK8StageVarTrueEnables() {
    when(featureFlagService.isEnabled(FeatureName.CI_INIT_REQUIRED_FIELDS_ONLY, ACCOUNT_ID)).thenReturn(false);
    K8StageInfraDetails infra = K8StageInfraDetails.builder().variables(stageVar("true")).build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infra).build());

    assertThat(ciInitStripStageVarHelper.isRequiredFieldsOnlyInitStripEnabled(ambiance, ACCOUNT_ID)).isTrue();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void ffDisabledAndK8StageVarFalseDisables() {
    when(featureFlagService.isEnabled(FeatureName.CI_INIT_REQUIRED_FIELDS_ONLY, ACCOUNT_ID)).thenReturn(false);
    K8StageInfraDetails infra = K8StageInfraDetails.builder().variables(stageVar("false")).build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infra).build());

    assertThat(ciInitStripStageVarHelper.isRequiredFieldsOnlyInitStripEnabled(ambiance, ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void ffDisabledAndSweepingOutputNotFoundDisables() {
    when(featureFlagService.isEnabled(FeatureName.CI_INIT_REQUIRED_FIELDS_ONLY, ACCOUNT_ID)).thenReturn(false);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    assertThat(ciInitStripStageVarHelper.isRequiredFieldsOnlyInitStripEnabled(ambiance, ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void ffDisabledAndNonK8StageInfraDisables() {
    when(featureFlagService.isEnabled(FeatureName.CI_INIT_REQUIRED_FIELDS_ONLY, ACCOUNT_ID)).thenReturn(false);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(VmStageInfraDetails.builder().build()).build());

    assertThat(ciInitStripStageVarHelper.isRequiredFieldsOnlyInitStripEnabled(ambiance, ACCOUNT_ID)).isFalse();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void stripDecisionCachedPerStageExecutionId() {
    Ambiance stageAmbiance = Ambiance.newBuilder().setStageExecutionId("stage-exec-1").build();
    when(featureFlagService.isEnabled(FeatureName.CI_INIT_REQUIRED_FIELDS_ONLY, ACCOUNT_ID)).thenReturn(false);
    K8StageInfraDetails infra = K8StageInfraDetails.builder().variables(stageVar("true")).build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infra).build());

    assertThat(ciInitStripStageVarHelper.isRequiredFieldsOnlyInitStripEnabled(stageAmbiance, ACCOUNT_ID)).isTrue();
    assertThat(ciInitStripStageVarHelper.isRequiredFieldsOnlyInitStripEnabled(stageAmbiance, ACCOUNT_ID)).isTrue();
    assertThat(ciInitStripStageVarHelper.isRequiredFieldsOnlyInitStripEnabled(stageAmbiance, ACCOUNT_ID)).isTrue();
    verify(executionSweepingOutputResolver, times(1)).resolveOptional(any(), any());
  }

  private List<NGVariable> stageVar(String value) {
    return List.of(StringNGVariable.builder()
                       .name(CIStepInfoUtils.CI_INIT_REQUIRED_FIELDS_ONLY)
                       .type(NGVariableType.STRING)
                       .value(ParameterField.createValueField(value))
                       .build());
  }
}
