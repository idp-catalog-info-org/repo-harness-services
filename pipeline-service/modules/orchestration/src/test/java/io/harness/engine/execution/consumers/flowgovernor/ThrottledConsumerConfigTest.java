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

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ThrottledConsumerConfigTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void defaults_matchSpecConstants() {
    ThrottledConsumerConfig config = ThrottledConsumerConfig.defaults();

    assertThat(config.getWorkers()).isEqualTo(20);
    assertThat(config.getQueueCapacity()).isEqualTo(200);
    assertThat(config.getOfferTimeoutMs()).isEqualTo(100L);
    assertThat(config.getHighWatermarkPercent()).isEqualTo(80);
    assertThat(config.getLowWatermarkPercent()).isEqualTo(30);
    assertThat(config.getModePollIntervalMs()).isEqualTo(5000L);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void watermarkThresholds_derivedFromCapacityAndPercent() {
    ThrottledConsumerConfig config = ThrottledConsumerConfig.defaults();

    // 200 * 80% = 160, 200 * 30% = 60
    assertThat(config.highWatermarkThreshold()).isEqualTo(160);
    assertThat(config.lowWatermarkThreshold()).isEqualTo(60);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void watermarkThresholds_scaleWithCustomCapacity() {
    ThrottledConsumerConfig config = ThrottledConsumerConfig.defaults();
    config.setQueueCapacity(100);
    config.setHighWatermarkPercent(90);
    config.setLowWatermarkPercent(20);

    assertThat(config.highWatermarkThreshold()).isEqualTo(90);
    assertThat(config.lowWatermarkThreshold()).isEqualTo(20);
  }
}
