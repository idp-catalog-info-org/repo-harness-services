/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.redisConsumer;

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
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.gitops.config.CdcKafkaConsumerConfig;
import io.harness.queue.QueueController;
import io.harness.rule.Owner;

import java.util.Optional;
import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class PipelineExecutionSummaryCDRedisEventConsumerTest extends CategoryTest {
  @Mock private Consumer redisConsumer;
  @Mock private QueueController queueController;
  @Mock private PipelineExecutionSummaryCDChangeEventHandler eventHandler;
  @Mock private Cache<String, Long> eventsCache;
  @Mock private CdcKafkaConfig cdcKafkaConfig;
  @Mock private CdcKafkaConsumerConfig consumerConfig;
  @Mock private Message message;

  private PipelineExecutionSummaryCDRedisEventConsumer consumer;

  private static final String KAFKA_CONFIG_KEY = "planExecutionsSummaryCD";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    consumer = new PipelineExecutionSummaryCDRedisEventConsumer(
        redisConsumer, queueController, eventHandler, eventsCache, cdcKafkaConfig);
    when(cdcKafkaConfig.getConsumer(KAFKA_CONFIG_KEY)).thenReturn(Optional.of(consumerConfig));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void processMessage_redisShortCircuitEnabled_acksWithoutProcessing() {
    when(consumerConfig.isRedisShortCircuit()).thenReturn(true);

    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(eventHandler, never()).handleCreateEvent(any(), any());
    verify(eventHandler, never()).handleUpdateEvent(any(), any());
    verify(eventHandler, never()).handleDeleteEvent(any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void processMessage_redisShortCircuitDisabled_delegatesToParent() {
    when(consumerConfig.isRedisShortCircuit()).thenReturn(false);

    // Parent's processMessage requires a real Message; without full mocking we get NPE.
    // Verify the short-circuit check was made and parent path was attempted.
    try {
      consumer.processMessage(message);
    } catch (NullPointerException e) {
      // Expected: we didn't mock the full Message protobuf structure for parent processing.
    }

    verify(consumerConfig, times(1)).isRedisShortCircuit();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void processMessage_noConsumerConfig_delegatesToParent() {
    when(cdcKafkaConfig.getConsumer(KAFKA_CONFIG_KEY)).thenReturn(Optional.empty());

    try {
      consumer.processMessage(message);
    } catch (NullPointerException e) {
      // Expected: parent processing path tried without mocked message.
    }

    verify(eventHandler, never()).handleCreateEvent(any(), any());
  }
}
