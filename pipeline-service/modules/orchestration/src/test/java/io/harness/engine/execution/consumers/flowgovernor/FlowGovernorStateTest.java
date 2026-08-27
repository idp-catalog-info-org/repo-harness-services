/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution.consumers.flowgovernor;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class FlowGovernorStateTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void resolveRpsFor_perConsumerOverride_wins() {
    Map<String, Integer> overrides = new HashMap<>();
    overrides.put(FlowGovernorConsumerKeys.INITIATE_NODE, 10);
    overrides.put(FlowGovernorConsumerKeys.SDK_STEP_RESPONSE, 50);
    FlowGovernorState state = FlowGovernorState.builder()
                                  .mode(FlowGovernorState.Mode.THROTTLED)
                                  .targetRps(20)
                                  .targetRpsByConsumer(overrides)
                                  .build();

    assertThat(state.resolveRpsFor(FlowGovernorConsumerKeys.INITIATE_NODE)).isEqualTo(10);
    assertThat(state.resolveRpsFor(FlowGovernorConsumerKeys.SDK_STEP_RESPONSE)).isEqualTo(50);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void resolveRpsFor_noOverride_fallsBackToDefault() {
    Map<String, Integer> overrides = new HashMap<>();
    overrides.put(FlowGovernorConsumerKeys.INITIATE_NODE, 10);
    FlowGovernorState state = FlowGovernorState.builder()
                                  .mode(FlowGovernorState.Mode.THROTTLED)
                                  .targetRps(20)
                                  .targetRpsByConsumer(overrides)
                                  .build();

    // Every non-overridden governed consumer sees the default RPS.
    assertThat(state.resolveRpsFor(FlowGovernorConsumerKeys.SDK_STEP_RESPONSE)).isEqualTo(20);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void resolveRpsFor_nullOverrideMap_returnsDefault() {
    FlowGovernorState state = FlowGovernorState.builder().mode(FlowGovernorState.Mode.THROTTLED).targetRps(20).build();

    assertThat(state.resolveRpsFor(FlowGovernorConsumerKeys.INITIATE_NODE)).isEqualTo(20);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void resolveRpsFor_noDefaultAndNoOverride_returnsNull() {
    FlowGovernorState state = FlowGovernorState.builder().mode(FlowGovernorState.Mode.NORMAL).build();

    assertThat(state.resolveRpsFor(FlowGovernorConsumerKeys.INITIATE_NODE)).isNull();
  }

  // Documents the deliberate null-key tolerance: a null consumer key skips the override lookup
  // and falls back to the default RPS instead of throwing. This is safer than a hot-path
  // NullPointerException if a downstream caller ever forwards a null.
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void resolveRpsFor_nullKey_fallsBackToDefault() {
    Map<String, Integer> overrides = new HashMap<>();
    overrides.put(FlowGovernorConsumerKeys.INITIATE_NODE, 10);
    FlowGovernorState state = FlowGovernorState.builder()
                                  .mode(FlowGovernorState.Mode.THROTTLED)
                                  .targetRps(20)
                                  .targetRpsByConsumer(overrides)
                                  .build();

    assertThat(state.resolveRpsFor(null)).isEqualTo(20);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void normal_hasNormalModeAndNoRps() {
    FlowGovernorState state = FlowGovernorState.normal();

    assertThat(state.getMode()).isEqualTo(FlowGovernorState.Mode.NORMAL);
    assertThat(state.getTargetRps()).isNull();
    assertThat(state.getTargetRpsByConsumer()).isNull();
    assertThat(state.getVersion()).isZero();
  }
}
