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
public class FlowGovernorConfigTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void disabled_returnsDisabledConfig() {
    FlowGovernorConfig config = FlowGovernorConfig.disabled();

    assertThat(config.isEnabled()).isFalse();
    assertThat(config.getNormalRps()).isEqualTo(FlowGovernorConfig.DEFAULT_NORMAL_RPS);
    assertThat(config.getNormalRpsByConsumer()).isNull();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void defaultConstructor_isDisabledWithDefaultNormalRps() {
    FlowGovernorConfig config = new FlowGovernorConfig();

    assertThat(config.isEnabled()).isFalse();
    assertThat(config.getNormalRps()).isEqualTo(FlowGovernorConfig.DEFAULT_NORMAL_RPS);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void resolveNormalRpsFor_perConsumerOverride_wins() {
    Map<String, Integer> overrides = new HashMap<>();
    overrides.put(FlowGovernorConsumerKeys.INITIATE_NODE, 500);
    FlowGovernorConfig config = new FlowGovernorConfig(true, 10_000, overrides, ThrottledConsumerConfig.defaults());

    assertThat(config.resolveNormalRpsFor(FlowGovernorConsumerKeys.INITIATE_NODE)).isEqualTo(500);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void resolveNormalRpsFor_noOverride_returnsDefault() {
    Map<String, Integer> overrides = new HashMap<>();
    overrides.put(FlowGovernorConsumerKeys.INITIATE_NODE, 500);
    FlowGovernorConfig config = new FlowGovernorConfig(true, 10_000, overrides, ThrottledConsumerConfig.defaults());

    assertThat(config.resolveNormalRpsFor(FlowGovernorConsumerKeys.SDK_STEP_RESPONSE)).isEqualTo(10_000);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void resolveNormalRpsFor_nullOverrideMap_returnsDefault() {
    FlowGovernorConfig config = new FlowGovernorConfig(true, 10_000, null, ThrottledConsumerConfig.defaults());

    assertThat(config.resolveNormalRpsFor(FlowGovernorConsumerKeys.INITIATE_NODE)).isEqualTo(10_000);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void resolveNormalRpsFor_nullKey_returnsDefault() {
    Map<String, Integer> overrides = new HashMap<>();
    overrides.put(FlowGovernorConsumerKeys.INITIATE_NODE, 500);
    FlowGovernorConfig config = new FlowGovernorConfig(true, 10_000, overrides, ThrottledConsumerConfig.defaults());

    assertThat(config.resolveNormalRpsFor(null)).isEqualTo(10_000);
  }
}
