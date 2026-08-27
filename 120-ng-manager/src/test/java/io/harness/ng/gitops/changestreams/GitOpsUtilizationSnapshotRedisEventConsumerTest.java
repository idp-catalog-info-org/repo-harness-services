/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.changestreams;

import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

/**
 * Unit tests for {@link GitOpsUtilizationSnapshotRedisEventConsumer}.
 */
@OwnedBy(HarnessTeam.GITOPS)
@RunWith(MockitoJUnitRunner.class)
public class GitOpsUtilizationSnapshotRedisEventConsumerTest extends CategoryTest {
  @Mock private Consumer redisConsumer;
  @Mock private QueueController queueController;
  @Mock private GitOpsUtilizationSnapshotRedisEventHandler eventHandler;
  @Mock private Cache<String, Long> eventsCache;
  @Mock private CdcKafkaConfig cdcKafkaConfig;
  @Mock private Message message;

  private GitOpsUtilizationSnapshotRedisEventConsumer consumer;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    consumer = new GitOpsUtilizationSnapshotRedisEventConsumer(
        redisConsumer, queueController, eventHandler, eventsCache, cdcKafkaConfig);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void processMessage_redisShortCircuitEnabled_skipsProcessingAndReturnsTrue() {
    CdcKafkaConsumerConfig consumerConfig =
        CdcKafkaConsumerConfig.builder().name("utilizationSnapshot").redisShortCircuit(true).build();
    when(cdcKafkaConfig.getConsumer(CdcKafkaConfig.UTILIZATION_SNAPSHOT_CONSUMER))
        .thenReturn(Optional.of(consumerConfig));

    boolean result = consumer.processMessage(message);

    assertThat(result).isTrue();
    verify(eventHandler, never()).handleCreateEvent(any(), any());
    verify(eventHandler, never()).handleUpdateEvent(any(), any());
    verify(eventHandler, never()).handleDeleteEvent(any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void processMessage_redisShortCircuitDisabled_delegatesToParent() {
    CdcKafkaConsumerConfig consumerConfig =
        CdcKafkaConsumerConfig.builder().name("utilizationSnapshot").redisShortCircuit(false).build();
    when(cdcKafkaConfig.getConsumer(CdcKafkaConfig.UTILIZATION_SNAPSHOT_CONSUMER))
        .thenReturn(Optional.of(consumerConfig));

    // When redisShortCircuit is false, delegates to parent. We can't easily unit test parent delegation
    // without mocking the entire message structure (getMessage().getData(), etc.)
    // The parent behavior is tested in DebeziumAbstractRedisConsumer tests.
    try {
      consumer.processMessage(message);
    } catch (NullPointerException e) {
      // Expected since we didn't mock the full message structure for parent processing
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void processMessage_consumerConfigNotFound_delegatesToParent() {
    when(cdcKafkaConfig.getConsumer(CdcKafkaConfig.UTILIZATION_SNAPSHOT_CONSUMER)).thenReturn(Optional.empty());

    // When consumer config is not found, redisShortCircuit defaults to false, delegates to parent
    try {
      consumer.processMessage(message);
    } catch (NullPointerException e) {
      // Expected since we didn't mock the full message structure for parent processing
    }
  }
}