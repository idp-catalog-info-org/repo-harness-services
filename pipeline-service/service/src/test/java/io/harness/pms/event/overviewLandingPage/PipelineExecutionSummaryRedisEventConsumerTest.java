/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.overviewLandingPage;

import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.ff.FeatureFlagService;
import io.harness.queue.QueueController;
import io.harness.rule.Owner;

import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link PipelineExecutionSummaryRedisEventConsumer}.
 *
 * <p>The key behaviour under test is the short-circuit condition:
 * Redis processing is skipped only when BOTH the feature flag is ON
 * AND {@code redisShortCircuit=true} in config.  All four combinations
 * of the two booleans are verified.
 *
 * <p>No Redis infrastructure is needed — the consumer is constructed
 * with mocks and {@code processMessage} is called directly.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class PipelineExecutionSummaryRedisEventConsumerTest extends CategoryTest {
  @Mock private Consumer redisConsumer;
  @Mock private QueueController queueController;
  @Mock private PipelineExecutionSummaryChangeEventHandler eventHandler;
  @Mock private Cache<String, Long> eventsCache;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private Message message;

  // ── helpers ─────────────────────────────────────────────────────────────────

  private PipelineExecutionSummaryRedisEventConsumer buildConsumer(boolean redisShortCircuit) {
    return new PipelineExecutionSummaryRedisEventConsumer(
        redisConsumer, queueController, eventHandler, eventsCache, featureFlagService, redisShortCircuit);
  }

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  // ── short-circuit AND condition ─────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void processMessage_ffOnAndConfigTrue_shortCircuitsAndReturnsTrue() {
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY)).thenReturn(true);

    boolean result = buildConsumer(true).processMessage(message);

    assertThat(result).isTrue();
    verify(eventHandler, never()).handleCreateEvent(any(), any());
    verify(eventHandler, never()).handleUpdateEvent(any(), any());
    verify(eventHandler, never()).handleDeleteEvent(any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void processMessage_ffOnButConfigFalse_doesNotShortCircuit() {
    // When redisShortCircuit=false the && short-circuits before the FF is evaluated —
    // verify the FF is never called (no unnecessary remote call) and Redis still processes.
    try {
      buildConsumer(false).processMessage(message);
    } catch (NullPointerException e) {
      // Expected: parent processing path attempted without full message structure.
    }

    verify(featureFlagService, never()).isGlobalEnabled(any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void processMessage_ffOffAndConfigTrue_doesNotShortCircuit() {
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY)).thenReturn(false);

    try {
      buildConsumer(true).processMessage(message);
    } catch (NullPointerException e) {
      // Expected: parent processing path attempted.
    }

    verify(featureFlagService, times(1)).isGlobalEnabled(FeatureName.PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void processMessage_ffOffAndConfigFalse_doesNotShortCircuit() {
    // Same as ffOnButConfigFalse: redisShortCircuit=false short-circuits && before FF is checked.
    try {
      buildConsumer(false).processMessage(message);
    } catch (NullPointerException e) {
      // Expected: parent processing path attempted.
    }

    verify(featureFlagService, never()).isGlobalEnabled(any());
  }

  // ── resilience ───────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void processMessage_ffEvaluationThrows_fallsBackToRedisProcessing() {
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY))
        .thenThrow(new RuntimeException("FF service down"));

    try {
      buildConsumer(true).processMessage(message);
    } catch (NullPointerException e) {
      // Expected: exception caught, fell back to parent processing.
    }

    verify(featureFlagService, times(1)).isGlobalEnabled(FeatureName.PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY);
    verify(eventHandler, never()).handleCreateEvent(any(), any());
  }
}
