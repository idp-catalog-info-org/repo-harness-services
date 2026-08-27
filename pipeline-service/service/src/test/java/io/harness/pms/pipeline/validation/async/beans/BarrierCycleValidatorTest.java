/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.beans;

import static io.harness.rule.OwnerRule.EDGAR_GARCIA;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierCycleValidatorTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "testAccount";

  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  private BarrierCycleValidator validator;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(PmsFeatureFlagService.class).toInstance(pmsFeatureFlagService);
      }
    });

    validator = injector.getInstance(BarrierCycleValidator.class);

    // Enable feature flag by default
    when(pmsFeatureFlagService.isEnabled(eq(TEST_ACCOUNT_ID), eq(FeatureName.PIPE_DETECT_BARRIER_CYCLES)))
        .thenReturn(true);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testSequentialBarriersSameRef_ShouldDetectCycle() {
    // Example: [B1, B2] with same ref = CYCLE
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: Barrier1\n"
        + "                  identifier: barrier1\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: shared_barrier\n"
        + "              - step:\n"
        + "                  name: Barrier2\n"
        + "                  identifier: barrier2\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: shared_barrier\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected")
        .hasMessageContaining("shared_barrier");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testParallelBarriersSameBlock_ShouldNotDetectCycle() {
    // Example: [B1 || B2] in same parallel block = NO CYCLE
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - parallel:\n"
        + "                  - step:\n"
        + "                      name: Barrier1\n"
        + "                      identifier: barrier1\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n"
        + "                  - step:\n"
        + "                      name: Barrier2\n"
        + "                      identifier: barrier2\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testMixedParallelSequential_ShouldDetectCycle() {
    // Example: [B1 || B2, B3] with same ref = CYCLE
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - parallel:\n"
        + "                  - step:\n"
        + "                      name: Barrier1\n"
        + "                      identifier: barrier1\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n"
        + "                  - step:\n"
        + "                      name: Barrier2\n"
        + "                      identifier: barrier2\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n"
        + "              - step:\n"
        + "                  name: Barrier3\n"
        + "                  identifier: barrier3\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: shared_barrier\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testAllParallelInSameBlock_ShouldNotDetectCycle() {
    // Example: [B1 || B2 || B3] all in same parallel = NO CYCLE
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - parallel:\n"
        + "                  - step:\n"
        + "                      name: Barrier1\n"
        + "                      identifier: barrier1\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n"
        + "                  - step:\n"
        + "                      name: Barrier2\n"
        + "                      identifier: barrier2\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n"
        + "                  - step:\n"
        + "                      name: Barrier3\n"
        + "                      identifier: barrier3\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testDifferentParallelBlocks_ShouldDetectCycle() {
    // Example: [B1 || B2], [B3 || B4] with same ref = CYCLE
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - parallel:\n"
        + "                  - step:\n"
        + "                      name: Barrier1\n"
        + "                      identifier: barrier1\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n"
        + "                  - step:\n"
        + "                      name: Barrier2\n"
        + "                      identifier: barrier2\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n"
        + "              - parallel:\n"
        + "                  - step:\n"
        + "                      name: Barrier3\n"
        + "                      identifier: barrier3\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n"
        + "                  - step:\n"
        + "                      name: Barrier4\n"
        + "                      identifier: barrier4\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  // ========== MULTIPLE REFERENCE EXAMPLES ==========

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testSequentialDifferentRefs_ShouldNotDetectCycle() {
    // Example: [BA1, BB1] with different refs = NO CYCLE
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: BarrierA1\n"
        + "                  identifier: barrierA1\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: barrier_a\n"
        + "              - step:\n"
        + "                  name: BarrierB1\n"
        + "                  identifier: barrierB1\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: barrier_b\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testSequentialSameRefWithGap_ShouldDetectCycle() {
    // Example: [BA1, BB1, BA2] = CYCLE for barrier_a
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: BarrierA1\n"
        + "                  identifier: barrierA1\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: barrier_a\n"
        + "              - step:\n"
        + "                  name: BarrierB1\n"
        + "                  identifier: barrierB1\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: barrier_b\n"
        + "              - step:\n"
        + "                  name: BarrierA2\n"
        + "                  identifier: barrierA2\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: barrier_a\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("barrier_a");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testDifferentParallelBlocksDifferentRefs_ShouldNotDetectCycle() {
    // Example: [BA1 || BA2], [BB1, BB2] = NO CYCLE (each ref completes independently)
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - parallel:\n"
        + "                  - step:\n"
        + "                      name: BarrierA1\n"
        + "                      identifier: barrierA1\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: barrier_a\n"
        + "                  - step:\n"
        + "                      name: BarrierA2\n"
        + "                      identifier: barrierA2\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: barrier_a\n"
        + "              - parallel:\n"
        + "                  - step:\n"
        + "                      name: BarrierB1\n"
        + "                      identifier: barrierB1\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: barrier_b\n"
        + "                  - step:\n"
        + "                      name: BarrierB2\n"
        + "                      identifier: barrierB2\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: barrier_b\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testMixedRefsInDifferentBlocks_ShouldDetectCycle() {
    // Example: [BA1 || BB1], [BA2, BB2] = CYCLE (both refs have issues)
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - parallel:\n"
        + "                  - step:\n"
        + "                      name: BarrierA1\n"
        + "                      identifier: barrierA1\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: barrier_a\n"
        + "                  - step:\n"
        + "                      name: BarrierB1\n"
        + "                      identifier: barrierB1\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: barrier_b\n"
        + "              - parallel:\n"
        + "                  - step:\n"
        + "                      name: BarrierA2\n"
        + "                      identifier: barrierA2\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: barrier_a\n"
        + "                  - step:\n"
        + "                      name: BarrierB2\n"
        + "                      identifier: barrierB2\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: barrier_b\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testNestedParallelSameLevel_ShouldNotDetectCycle() {
    // Example: [BA1 || [BA2] || [BA3, [BB1]]] = NO CYCLE (BA barriers at same level)
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - parallel:\n"
        + "                  - step:\n"
        + "                      name: BarrierA1\n"
        + "                      identifier: barrierA1\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: barrier_a\n"
        + "                  - stepGroup:\n"
        + "                      name: Group1\n"
        + "                      identifier: group1\n"
        + "                      steps:\n"
        + "                        - step:\n"
        + "                            name: BarrierA2\n"
        + "                            identifier: barrierA2\n"
        + "                            type: Barrier\n"
        + "                            spec:\n"
        + "                              barrierRef: barrier_a\n"
        + "                  - stepGroup:\n"
        + "                      name: Group2\n"
        + "                      identifier: group2\n"
        + "                      steps:\n"
        + "                        - step:\n"
        + "                            name: BarrierA3\n"
        + "                            identifier: barrierA3\n"
        + "                            type: Barrier\n"
        + "                            spec:\n"
        + "                              barrierRef: barrier_a\n"
        + "                        - stepGroup:\n"
        + "                            name: NestedGroup\n"
        + "                            identifier: nestedGroup\n"
        + "                            steps:\n"
        + "                              - step:\n"
        + "                                  name: BarrierB1\n"
        + "                                  identifier: barrierB1\n"
        + "                                  type: Barrier\n"
        + "                                  spec:\n"
        + "                                    barrierRef: barrier_b\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testParallelStagesWithSameRef_ShouldNotDetectCycle() {
    // Example: S1{[B1]} || S2{[B2]} with same ref = NO CYCLE (stages run in parallel)
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            name: Stage1\n"
        + "            identifier: stage1\n"
        + "            type: Custom\n"
        + "            spec:\n"
        + "              execution:\n"
        + "                steps:\n"
        + "                  - step:\n"
        + "                      name: Barrier1\n"
        + "                      identifier: barrier1\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n"
        + "        - stage:\n"
        + "            name: Stage2\n"
        + "            identifier: stage2\n"
        + "            type: Custom\n"
        + "            spec:\n"
        + "              execution:\n"
        + "                steps:\n"
        + "                  - step:\n"
        + "                      name: Barrier2\n"
        + "                      identifier: barrier2\n"
        + "                      type: Barrier\n"
        + "                      spec:\n"
        + "                        barrierRef: shared_barrier\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testSequentialStagesWithSameRef_ShouldDetectCycle() {
    // Example: S1{B1}, S2{B2} with same ref = CYCLE (stages run sequentially)
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: Barrier1\n"
        + "                  identifier: barrier1\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: shared_barrier\n"
        + "    - stage:\n"
        + "        name: Stage2\n"
        + "        identifier: stage2\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: Barrier2\n"
        + "                  identifier: barrier2\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: shared_barrier\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testSequentialStagesWithExpression_ShouldNoDetectCycle() {
    // Example: S1{B1}, S2{<+input>} with input ref = NO CYCLE (cannot detect cycle if ref are not final)
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: Barrier1\n"
        + "                  identifier: barrier1\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: shared_barrier\n"
        + "    - stage:\n"
        + "        name: Stage2\n"
        + "        identifier: stage2\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: Barrier2\n"
        + "                  identifier: barrier2\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: <+input>\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testStageWithSameBarrierRefInNormalAndRollbackFlow_ShouldDetectCycle() {
    // Example: S1{normalFlow{B1}, rollbackFlow{B2]} with same ref in normal and rollback = CYCLE (rollback steps are
    // executed only on failure from normal flow. Need to use different barrier ref)
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: test\n"
        + "        identifier: test\n"
        + "        description: \"\"\n"
        + "        type: Deployment\n"
        + "        spec:\n"
        + "          deploymentType: Kubernetes\n"
        + "          service:\n"
        + "            serviceRef: emptySvc\n"
        + "          environment:\n"
        + "            environmentRef: edgarenv\n"
        + "            deployToAll: false\n"
        + "            infrastructureDefinitions:\n"
        + "              - identifier: edgarinfra\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_1\n"
        + "                  identifier: Barrier_1\n"
        + "                  spec:\n"
        + "                    barrierRef: bar1\n"
        + "                  timeout: 10m\n"
        + "            rollbackSteps:\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_1\n"
        + "                  identifier: Barrier_1\n"
        + "                  spec:\n"
        + "                    barrierRef: bar1\n"
        + "                  timeout: 10m\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testFeatureFlagDisabled_ShouldNotValidate() {
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.PIPE_DETECT_BARRIER_CYCLES))).thenReturn(false);

    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: Barrier1\n"
        + "                  identifier: barrier1\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: shared_barrier\n"
        + "              - step:\n"
        + "                  name: Barrier2\n"
        + "                  identifier: barrier2\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: shared_barrier\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testSingleBarrier_ShouldNotDetectCycle() {
    String yaml = "pipeline:\n"
        + "  name: test\n"
        + "  identifier: test\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Stage1\n"
        + "        identifier: stage1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: Barrier1\n"
        + "                  identifier: barrier1\n"
        + "                  type: Barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: shared_barrier\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void V1_testSequentialBarriersSameRef_ShouldDetectCycle() {
    // Example: [B1, B2] with same ref = CYCLE
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - name: Stage\n"
        + "      id: stage\n"
        + "      steps:\n"
        + "        - name: barrier1\n"
        + "          barrier:\n"
        + "            name: shared_barrier_v1\n"
        + "          id: barrier1\n"
        + "        - name: barrier2\n"
        + "          barrier:\n"
        + "            name: shared_barrier_v1\n"
        + "          id: barrier2\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected")
        .hasMessageContaining("shared_barrier_v1");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void V1_testBarriersSameRefInRollback_ShouldDetectCycle() {
    // Example: S1{normalFlow{B1}, rollbackFlow{B2]} with same ref in normal and rollback = CYCLE (rollback steps are
    // executed only on failure from normal flow. Need to use different barrier ref)
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - name: stage1\n"
        + "      id: stage1\n"
        + "      steps:\n"
        + "        - name: barrier1\n"
        + "          barrier:\n"
        + "            name: bar1\n"
        + "          id: barrier1\n"
        + "      rollback:\n"
        + "        - name: Rollback Stage1\n"
        + "          id: rollback_stage1\n"
        + "          run:\n"
        + "            shell: bash\n"
        + "            script: echo \"rolling back stage\"\n"
        + "        - name: barrierRollback1\n"
        + "          barrier:\n"
        + "            name: bar1\n"
        + "          id: barrierrollback1\n"
        + "      on-failure:\n"
        + "        - errors:\n"
        + "            - all\n"
        + "          action: stage-rollback\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void V1_testSequentialStagesWithExpression_ShouldNoDetectCycle() {
    // Example: S1{B1}, S2{<+input>} with input ref = NO CYCLE (cannot detect cycle if ref are not final)
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - name: parallel_stage_group_fecc\n"
        + "      id: parallel_stage_group_fecc\n"
        + "      parallel:\n"
        + "        stages:\n"
        + "          - name: stage1\n"
        + "            id: stage1\n"
        + "            steps:\n"
        + "              - name: barrier1\n"
        + "                barrier:\n"
        + "                  name: bar1\n"
        + "                id: barrier1\n"
        + "          - name: stage1-1\n"
        + "            id: stage1_1\n"
        + "            steps:\n"
        + "              - name: barrier2\n"
        + "                barrier:\n"
        + "                  name: <+input>\n"
        + "                id: barrier2\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void V1_testParallelStageGroup_ShouldNoDetectCycle() {
    // Example: PSG1{S1{B1}, S2{B1}} with v1 parallel stage group = NO CYCLE
    String yaml = "pipeline:\n"
        + "  barriers:\n"
        + "    - bar1\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        stages:\n"
        + "          - id: childStage1\n"
        + "            name: childStage1\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_1\n"
        + "                name: Barrier_1\n"
        + "                timeout: 10m\n"
        + "          - id: childStage2\n"
        + "            name: childStage2\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_2\n"
        + "                name: Barrier_2\n"
        + "                timeout: 10m\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void V1_testParallelStageGroupWithSequential_ShouldDetectCycle() {
    // Example: S1, PSG1{S2{B1}, S3{B1}}, PSG2{S3{B1}, S4{B1}} with v1 parallel stage group = CYCLE (Two sequential
    // Parallel Stage Group use same ref)
    String yaml = "pipeline:\n"
        + "  barriers:\n"
        + "    - bar1\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - name: Stage 1\n"
        + "      id: stage1\n"
        + "      steps:\n"
        + "        - name: Step 1\n"
        + "          id: step1\n"
        + "          run: \n"
        + "            script: echo \"hello world\"\n"
        + "    - parallel:\n"
        + "        stages:\n"
        + "          - id: childStage1\n"
        + "            name: childStage1\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_1\n"
        + "                name: Barrier_1\n"
        + "                timeout: 10m\n"
        + "          - id: childStage2\n"
        + "            name: childStage2\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_2\n"
        + "                name: Barrier_2\n"
        + "                timeout: 10m\n"
        + "    - parallel:\n"
        + "        stages:\n"
        + "          - id: childStage1\n"
        + "            name: childStage1\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_1\n"
        + "                name: Barrier_1\n"
        + "                timeout: 10m\n"
        + "          - id: childStage2\n"
        + "            name: childStage2\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_2\n"
        + "                name: Barrier_2\n"
        + "                timeout: 10m\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void V1_testParallelStageGroupWithSequentialStep_ShouldDetectCycle() {
    // Example: S1, PSG1{S2{B1}, S3{[B1, B1]}} with v1 parallel stage group = CYCLE (Parallel Stage Group with
    // sequential step use same ref)
    String yaml = "pipeline:\n"
        + "  barriers:\n"
        + "    - bar1\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        stages:\n"
        + "          - id: childStage1\n"
        + "            name: childStage1\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_1\n"
        + "                name: Barrier_1\n"
        + "                timeout: 10m\n"
        + "          - id: childStage2\n"
        + "            name: childStage2\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_2\n"
        + "                name: Barrier_2\n"
        + "                timeout: 10m\n"
        + "              - name: Parallel1\n"
        + "                parallel:\n"
        + "                  steps:\n"
        + "                    - barrier:\n"
        + "                        name: bar1\n"
        + "                      id: Barrier_4\n"
        + "                      name: Barrier_4\n"
        + "                      timeout: 10m\n"
        + "                    - barrier:\n"
        + "                        name: bar1\n"
        + "                      id: Barrier_5\n"
        + "                      name: Barrier_5\n"
        + "                      timeout: 10m\n"
        + "                timeout: 10m\n"
        + "                id: parallel1\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void V1_testParallelStageGroupWithParallelStep_ShouldNoDetectCycle() {
    // Example: S1, PSG1{S2{B1}, S3{B1{B1,B1}}} with v1 parallel stage group and parallel step group = NO CYCLE
    // (Parallel Stage Group with Parallel Step Group use same ref concurrently)
    String yaml = "pipeline:\n"
        + "  barriers:\n"
        + "    - bar1\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        stages:\n"
        + "          - id: childStage1\n"
        + "            name: childStage1\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_1\n"
        + "                name: Barrier_1\n"
        + "                timeout: 10m\n"
        + "          - id: childStage2\n"
        + "            name: childStage2\n"
        + "            steps:\n"
        + "              - name: Parallel1\n"
        + "                parallel:\n"
        + "                  steps:\n"
        + "                    - barrier:\n"
        + "                        name: bar1\n"
        + "                      id: Barrier_4\n"
        + "                      name: Barrier_4\n"
        + "                      timeout: 10m\n"
        + "                    - barrier:\n"
        + "                        name: bar1\n"
        + "                      id: Barrier_5\n"
        + "                      name: Barrier_5\n"
        + "                      timeout: 10m\n"
        + "                timeout: 10m\n"
        + "                id: parallel1\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void V1_testParallelStageGroupWithParallelStepGroupAndGroup_ShouldNoDetectCycle() {
    // Example: S1, PSG1{S2{B1}, S3{[B1,B1,GROUP{B1}]} with v1 parallel stage group, parallel step group and single
    // group = NO CYCLE (Parallel Stage Group with parallel step group and group use same ref but run all concurrently)
    String yaml = "pipeline:\n"
        + "  barriers:\n"
        + "    - bar1\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        stages:\n"
        + "          - id: childStage1\n"
        + "            name: childStage1\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_1\n"
        + "                name: Barrier_1\n"
        + "                timeout: 10m\n"
        + "          - id: childStage2\n"
        + "            name: childStage2\n"
        + "            steps:\n"
        + "              - name: Parallel1\n"
        + "                parallel:\n"
        + "                  steps:\n"
        + "                    - barrier:\n"
        + "                        name: bar1\n"
        + "                      id: Barrier_4\n"
        + "                      name: Barrier_4\n"
        + "                      timeout: 10m\n"
        + "                    - barrier:\n"
        + "                        name: bar1\n"
        + "                      id: Barrier_5\n"
        + "                      name: Barrier_5\n"
        + "                      timeout: 10m\n"
        + "                    - name: Group1\n"
        + "                      group:\n"
        + "                        steps:\n"
        + "                          - barrier:\n"
        + "                               name: bar1\n"
        + "                            id: Barrier_6\n"
        + "                            name: Barrier_6\n"
        + "                            timeout: 10m\n"
        + "                      timeout: 10m\n"
        + "                      id: group1\n"
        + "                timeout: 10m\n"
        + "                id: parallel1\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void V1_testParallelStageGroupWithParallelStepGroupAndSequentialGroup_ShouldDetectCycle() {
    // Example: S1, PSG1{S2{B1}, S3{[B1, B1, Group{B1, B1}]}} with v1 parallel stage group, parallel step group and
    // group with sequential steps = CYCLE (Parallel Stage Group with parallel step group and group with sequential
    // barrier use same ref)
    String yaml = "pipeline:\n"
        + "  barriers:\n"
        + "    - bar1\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        stages:\n"
        + "          - id: childStage1\n"
        + "            name: childStage1\n"
        + "            steps:\n"
        + "              - barrier:\n"
        + "                  name: bar1\n"
        + "                id: Barrier_1\n"
        + "                name: Barrier_1\n"
        + "                timeout: 10m\n"
        + "          - id: childStage2\n"
        + "            name: childStage2\n"
        + "            steps:\n"
        + "              - name: Parallel1\n"
        + "                parallel:\n"
        + "                  steps:\n"
        + "                    - barrier:\n"
        + "                        name: bar1\n"
        + "                      id: Barrier_4\n"
        + "                      name: Barrier_4\n"
        + "                      timeout: 10m\n"
        + "                    - barrier:\n"
        + "                        name: bar1\n"
        + "                      id: Barrier_5\n"
        + "                      name: Barrier_5\n"
        + "                      timeout: 10m\n"
        + "                    - name: Group1\n"
        + "                      group:\n"
        + "                        steps:\n"
        + "                          - barrier:\n"
        + "                               name: bar1\n"
        + "                            id: Barrier_6\n"
        + "                            name: Barrier_6\n"
        + "                            timeout: 10m\n"
        + "                          - barrier:\n"
        + "                               name: bar1\n"
        + "                            id: Barrier_7\n"
        + "                            name: Barrier_7\n"
        + "                            timeout: 10m\n"
        + "                      timeout: 10m\n"
        + "                      id: group1\n"
        + "                timeout: 10m\n"
        + "                id: parallel1\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testStageAndInsertedWithSameBarrierRefInNormalFlow_ShouldDetectCycle() {
    // Example: S1{B1}, Inserted2{B2} with same ref in normal = CYCLE
    // even with inserted stages in a resolved template can detect cycle
    String yaml = "pipeline:\n"
        + "  identifier: testinsertbar1\n"
        + "  name: test-insert-bar1\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: fixed1\n"
        + "        type: Custom\n"
        + "        name: fixed1\n"
        + "        description: \"\"\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: Barrier_1\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_1\n"
        + "                  spec:\n"
        + "                    barrierRef: bar1\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "    - insert:\n"
        + "        identifier: insert1\n"
        + "        name: insert1\n"
        + "        stages:\n"
        + "          - stage:\n"
        + "              identifier: inserted1\n"
        + "              type: Custom\n"
        + "              name: inserted1\n"
        + "              description: \"\"\n"
        + "              spec:\n"
        + "                execution:\n"
        + "                  steps:\n"
        + "                    - step:\n"
        + "                        identifier: Barrier_1\n"
        + "                        type: Barrier\n"
        + "                        name: Barrier_1\n"
        + "                        spec:\n"
        + "                          barrierRef: bar1\n"
        + "                        timeout: 10m\n"
        + "              tags: {}\n"
        + "  flowControl:\n"
        + "    barriers:\n"
        + "      - identifier: bar1\n"
        + "        name: bar1\n"
        + "  projectIdentifier: edgartest\n"
        + "  orgIdentifier: default\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testMultipleInsertedWithSameBarrierRefInNormalFlow_ShouldDetectCycle() {
    // Example: Inserted1{B1} || Inserted2{B2}, Inserted3{B3} with same ref in normal = CYCLE
    // inserted3 is not configured at the same parallel block
    String yaml = "pipeline:\n"
        + "  name: test-insert-bar1\n"
        + "  identifier: testinsertbar1\n"
        + "  tags: {}\n"
        + "  template:\n"
        + "    templateRef: flexibletemplatebarrier1\n"
        + "    versionLabel: v1\n"
        + "    templateInputs:\n"
        + "      stages:\n"
        + "        - insert:\n"
        + "            identifier: insert1\n"
        + "            stages:\n"
        + "              - parallel:\n"
        + "                  - stage:\n"
        + "                      name: inserted1\n"
        + "                      identifier: inserted1\n"
        + "                      description: \"\"\n"
        + "                      type: Custom\n"
        + "                      spec:\n"
        + "                        execution:\n"
        + "                          steps:\n"
        + "                            - step:\n"
        + "                                type: Barrier\n"
        + "                                name: Barrier_1\n"
        + "                                identifier: Barrier_1\n"
        + "                                spec:\n"
        + "                                  barrierRef: bar1\n"
        + "                                timeout: 10m\n"
        + "                      tags: {}\n"
        + "                  - stage:\n"
        + "                      name: inserted2\n"
        + "                      identifier: inserted2\n"
        + "                      description: \"\"\n"
        + "                      type: Custom\n"
        + "                      spec:\n"
        + "                        execution:\n"
        + "                          steps:\n"
        + "                            - step:\n"
        + "                                type: Barrier\n"
        + "                                name: Barrier_1\n"
        + "                                identifier: Barrier_1\n"
        + "                                spec:\n"
        + "                                  barrierRef: bar1\n"
        + "                                timeout: 10m\n"
        + "                      tags: {}\n"
        + "        - insert:\n"
        + "            identifier: insert2\n"
        + "            stages:\n"
        + "              - stage:\n"
        + "                  name: inserted3\n"
        + "                  identifier: inserted3\n"
        + "                  description: \"\"\n"
        + "                  type: Custom\n"
        + "                  spec:\n"
        + "                    execution:\n"
        + "                      steps:\n"
        + "                        - step:\n"
        + "                            type: Barrier\n"
        + "                            name: Barrier_1\n"
        + "                            identifier: Barrier_1\n"
        + "                            spec:\n"
        + "                              barrierRef: bar1\n"
        + "                            timeout: 10m\n"
        + "                  tags: {}\n"
        + "  projectIdentifier: edgartest\n"
        + "  orgIdentifier: default\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testDagPipelineWithBasicDependsOn_ShouldNotDetectCycle() {
    String yaml = "pipeline:\n"
        + "  name: DAGBArrier\n"
        + "  identifier: DAGBArrier\n"
        + "  tags: {}\n"
        + "  projectIdentifier: NGPipeAuto_dag_DagApprovalBarrierDHKsJEKBf3\n"
        + "  orgIdentifier: Pipelines_NonK8s_Org_NG\n"
        + "  flowControl:\n"
        + "    barriers:\n"
        + "      - name: syncPoint\n"
        + "        identifier: syncPoint\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Setup\n"
        + "        identifier: Setup\n"
        + "        description: Root stage so BranchA and BranchB explicitly depend on it (makes the DAG edges visible "
        + "in YAML view).\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: Setup_step\n"
        + "                  identifier: Setup_step\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo setup complete\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 5m\n"
        + "        tags: {}\n"
        + "        dependsOn: []\n"
        + "    - stage:\n"
        + "        name: BranchA\n"
        + "        identifier: BranchA\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: BranchA_pre\n"
        + "                  identifier: BranchA_pre\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo A pre-barrier\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 5m\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: BranchA_barrier\n"
        + "                  identifier: BranchA_barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: syncPoint\n"
        + "                  timeout: 5m\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: BranchA_post\n"
        + "                  identifier: BranchA_post\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo A post-barrier\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 5m\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - Setup\n"
        + "    - stage:\n"
        + "        name: BranchB\n"
        + "        identifier: BranchB\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: BranchB_pre\n"
        + "                  identifier: BranchB_pre\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo B pre-barrier\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 5m\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: BranchB_barrier\n"
        + "                  identifier: BranchB_barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: syncPoint\n"
        + "                  timeout: 5m\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: BranchB_post\n"
        + "                  identifier: BranchB_post\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo B post-barrier\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 5m\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - Setup\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testDagPipelineWithMoreComplexDependsOn_ShouldNotDetectCycle() {
    String yaml = "pipeline:\n"
        + "  name: test-dag-barrier1\n"
        + "  identifier: testdagbarrier1\n"
        + "  tags: {}\n"
        + "  projectIdentifier: edgarproject\n"
        + "  orgIdentifier: DoNotDelete_MigratorOrg\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: test1\n"
        + "        identifier: test1\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  identifier: ShellScript_1\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    executionTarget: {}\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: exit 0\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn: []\n"
        + "    - stage:\n"
        + "        name: test2\n"
        + "        identifier: test2\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_1\n"
        + "                  identifier: Barrier_1\n"
        + "                  spec:\n"
        + "                    barrierRef: bar1\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - test1\n"
        + "    - stage:\n"
        + "        name: test3\n"
        + "        identifier: test3\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_1\n"
        + "                  identifier: Barrier_1\n"
        + "                  spec:\n"
        + "                    barrierRef: bar1\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - test1\n"
        + "    - stage:\n"
        + "        name: test4\n"
        + "        identifier: test4\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  identifier: ShellScript_1\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    executionTarget: {}\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: exit 0\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn: []\n"
        + "    - stage:\n"
        + "        name: test5\n"
        + "        identifier: test5\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_1\n"
        + "                  identifier: Barrier_1\n"
        + "                  spec:\n"
        + "                    barrierRef: bar1\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - test4\n"
        + "    - stage:\n"
        + "        name: test6\n"
        + "        identifier: test6\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Wait\n"
        + "                  name: Wait_1\n"
        + "                  identifier: Wait_1\n"
        + "                  spec:\n"
        + "                    duration: 10s\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - test5\n"
        + "          - test2\n"
        + "    - stage:\n"
        + "        name: test7\n"
        + "        identifier: test7\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Wait\n"
        + "                  name: Wait_1\n"
        + "                  identifier: Wait_1\n"
        + "                  spec:\n"
        + "                    duration: 10s\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - test2\n"
        + "  flowControl:\n"
        + "    barriers:\n"
        + "      - name: bar1\n"
        + "        identifier: bar1\n";

    assertThatCode(() -> validator.validate(TEST_ACCOUNT_ID, yaml)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testDagPipelineWithBarrierRefUsedAsAncestor_ShouldDetectCycle() {
    String yaml = "pipeline:\n"
        + "  name: test-dag-barrier1\n"
        + "  identifier: testdagbarrier1\n"
        + "  tags: {}\n"
        + "  projectIdentifier: edgarproject\n"
        + "  orgIdentifier: DoNotDelete_MigratorOrg\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: test1\n"
        + "        identifier: test1\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  identifier: ShellScript_1\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    executionTarget: {}\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: exit 0\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn: []\n"
        + "    - stage:\n"
        + "        name: test2\n"
        + "        identifier: test2\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_1\n"
        + "                  identifier: Barrier_1\n"
        + "                  spec:\n"
        + "                    barrierRef: bar1\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - test1\n"
        + "    - stage:\n"
        + "        name: test3\n"
        + "        identifier: test3\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_1\n"
        + "                  identifier: Barrier_1\n"
        + "                  spec:\n"
        + "                    barrierRef: bar1\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - test1\n"
        + "    - stage:\n"
        + "        name: test4\n"
        + "        identifier: test4\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: ShellScript_1\n"
        + "                  identifier: ShellScript_1\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    executionTarget: {}\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: exit 0\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn: []\n"
        + "    - stage:\n"
        + "        name: test5\n"
        + "        identifier: test5\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_1\n"
        + "                  identifier: Barrier_1\n"
        + "                  spec:\n"
        + "                    barrierRef: bar1\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - test4\n"
        + "    - stage:\n"
        + "        name: test6\n"
        + "        identifier: test6\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Wait\n"
        + "                  name: Wait_1\n"
        + "                  identifier: Wait_1\n"
        + "                  spec:\n"
        + "                    duration: 10s\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - test5\n"
        + "          - test2\n"
        + "    - stage:\n"
        + "        name: test7\n"
        + "        identifier: test7\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_1\n"
        + "                  identifier: Barrier_1\n"
        + "                  spec:\n"
        + "                    barrierRef: bar1\n"
        + "                  timeout: 10m\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - test2\n"
        + "  flowControl:\n"
        + "    barriers:\n"
        + "      - name: bar1\n"
        + "        identifier: bar1\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testDagPipelineWithSameSequentialBarrierInSteps_ShouldDetectCycle() {
    String yaml = "pipeline:\n"
        + "  name: test-dag-barrier1\n"
        + "  identifier: testdagbarrier1\n"
        + "  tags: {}\n"
        + "  projectIdentifier: edgarproject\n"
        + "  orgIdentifier: DoNotDelete_MigratorOrg\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: Setup\n"
        + "        identifier: Setup\n"
        + "        description: Root stage so BranchA and BranchB explicitly depend on it (makes the DAG edges visible "
        + "in YAML view).\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: Setup_step\n"
        + "                  identifier: Setup_step\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo setup complete\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 5m\n"
        + "        tags: {}\n"
        + "        dependsOn: []\n"
        + "    - stage:\n"
        + "        name: BranchA\n"
        + "        identifier: BranchA\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: BranchA_pre\n"
        + "                  identifier: BranchA_pre\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo A pre-barrier\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 5m\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: BranchA_barrier\n"
        + "                  identifier: BranchA_barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: syncPoint\n"
        + "                  timeout: 5m\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: BranchA_post\n"
        + "                  identifier: BranchA_post\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo A post-barrier\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 5m\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - Setup\n"
        + "    - stage:\n"
        + "        name: BranchB\n"
        + "        identifier: BranchB\n"
        + "        description: \"\"\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: BranchB_pre\n"
        + "                  identifier: BranchB_pre\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo B pre-barrier\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 5m\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: BranchB_barrier\n"
        + "                  identifier: BranchB_barrier\n"
        + "                  spec:\n"
        + "                    barrierRef: syncPoint\n"
        + "                  timeout: 5m\n"
        + "              - step:\n"
        + "                  type: ShellScript\n"
        + "                  name: BranchB_post\n"
        + "                  identifier: BranchB_post\n"
        + "                  spec:\n"
        + "                    shell: Bash\n"
        + "                    onDelegate: true\n"
        + "                    source:\n"
        + "                      type: Inline\n"
        + "                      spec:\n"
        + "                        script: echo B post-barrier\n"
        + "                    environmentVariables: []\n"
        + "                    outputVariables: []\n"
        + "                  timeout: 5m\n"
        + "              - step:\n"
        + "                  type: Barrier\n"
        + "                  name: Barrier_2\n"
        + "                  identifier: Barrier_2\n"
        + "                  spec:\n"
        + "                    barrierRef: syncPoint\n"
        + "        tags: {}\n"
        + "        dependsOn:\n"
        + "          - Setup\n"
        + "  flowControl:\n"
        + "    barriers:\n"
        + "      - name: syncPoint\n"
        + "        identifier: syncPoint\n";

    assertThatThrownBy(() -> validator.validate(TEST_ACCOUNT_ID, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Barrier Deadlock Detected");
  }
}
