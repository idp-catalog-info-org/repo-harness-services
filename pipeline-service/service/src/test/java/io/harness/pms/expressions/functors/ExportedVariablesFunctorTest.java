/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.expressions.metadata.ExecutionSweepingOutputMetadata;
import io.harness.engine.pms.data.SweepingOutputException;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.steps.shellscript.OutputAliasSweepingOutput;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExportedVariablesFunctorTest extends CategoryTest {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private PmsSweepingOutputService pmsSweepingOutputService;
  private Ambiance ambiance;
  private ExportedVariablesFunctor functor;
  private static final String ACCOUNT_ID = "accountId";
  private static final String UUID = "1f30de2c";
  private static final String PIPELINE_SCOPE = "PIPELINE";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ambiance =
        Ambiance.newBuilder().putSetupAbstractions("accountId", ACCOUNT_ID).setExpressionFunctorToken(1234L).build();
    functor = new ExportedVariablesFunctor(ambiance, executionSweepingOutputService, null);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetExportedVariablesWhenBlankExpression() {
    assertThat(functor.getValue("   ")).isNull();
    verifyNoMoreInteractions(executionSweepingOutputService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetExportedVariablesWhenInvalidExpression() {
    assertThat(functor.getValue("a")).isNull();
    assertThat(functor.getValue("a.b.c.d")).isNull();
    assertThat(functor.getValue("a.  .c")).isNull();
    assertThat(functor.getValue("dfg.s.c")).isNull();
    verifyNoMoreInteractions(executionSweepingOutputService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetExportedVariablesExpressionWhenExceptionThrows() {
    when(executionSweepingOutputService.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE)))
        .thenThrow(new SweepingOutputException("random"));
    assertThat(functor.getValue("pipeline.alias.varName")).isNull();
    verify(executionSweepingOutputService, times(1))
        .resolve(ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetExportedVariablesExpressionWhenSweepingOutputNull() {
    when(executionSweepingOutputService.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE)))
        .thenReturn(null);
    assertThat(functor.getValue("pipeline.alias.varName")).isNull();
    verify(executionSweepingOutputService, times(1))
        .resolve(ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetExportedVariablesExpressionWhenSweepingOutputInvalid() {
    when(executionSweepingOutputService.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE)))
        .thenReturn(new ExecutionSweepingOutput() {});
    assertThat(functor.getValue("pipeline.alias.varName")).isNull();
    verify(executionSweepingOutputService, times(1))
        .resolve(ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetExportedVariablesExpressionWhenSweepingOutputWithoutOutputVariables() {
    when(executionSweepingOutputService.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE)))
        .thenReturn(OutputAliasSweepingOutput.builder().build());
    assertThat(functor.getValue("pipeline.alias.varName")).isNull();
    verify(executionSweepingOutputService, times(1))
        .resolve(ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetExportedVariablesExpressionWhenSweepingOutputWhenLeafAndNonLeafExpression() {
    Map<String, String> outVariables = new HashMap<>();
    outVariables.put("key", "value");
    when(executionSweepingOutputService.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE)))
        .thenReturn(OutputAliasSweepingOutput.builder().outputVariables(outVariables).build());
    assertThat(functor.getValue("pipeline.alias.key")).isEqualTo("value");
    assertThat(functor.getValue("pipeline.alias.random")).isNull();
    assertThat(functor.getValue("pipeline.alias")).isEqualTo(outVariables);
    verify(executionSweepingOutputService, times(3))
        .resolve(ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetExportedVariablesExpressionOptimised() {
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .putFeatureFlagToValueMap("PIPE_OPTIMISE_EXPORTED_VARIABLES_FUNCTOR", true)
                                    .build())
                   .setExpressionFunctorToken(1234L)
                   .build();
    functor = new ExportedVariablesFunctor(ambiance, executionSweepingOutputService,
        new ExecutionSweepingOutputMetadata(pmsSweepingOutputService, "planExecutionId", null));
    Map<String, String> outVariables = new HashMap<>();
    outVariables.put("key", "value");
    when(pmsSweepingOutputService.fetchNameOfOutcomesInPlanExecutionId("planExecutionId"))
        .thenReturn(Collections.singletonList(UUID));
    when(executionSweepingOutputService.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE)))
        .thenReturn(OutputAliasSweepingOutput.builder().outputVariables(outVariables).build());
    assertThat(functor.getValue("pipeline.alias.key")).isEqualTo("value");
    assertThat(functor.getValue("pipeline.alias.random")).isNull();
    assertThat(functor.getValue("pipeline.alias")).isEqualTo(outVariables);
    assertThat(functor.getValue("pipeline.alisa")).isNull();
    verify(executionSweepingOutputService, times(3))
        .resolve(ambiance, RefObjectUtils.getSweepingOutputRefObjectUsingGroup(UUID, PIPELINE_SCOPE));
  }
}
