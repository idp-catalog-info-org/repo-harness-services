/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ObserverEventConsumerTest extends CIExecutionTestBase {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private Consumer redisConsumer;
  @Mock private MessageListener delegateTaskEventListener;
  @Mock private QueueController queueController;

  private ObserverEventConsumer observerEventConsumer;

  @Before
  public void setUp() {
    observerEventConsumer = new ObserverEventConsumer(redisConsumer, delegateTaskEventListener, queueController);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testRun_whenPrimaryAndMessagesAvailable_shouldProcessAndAcknowledge() throws Exception {
    String messageId = "msg-001";
    Message message = mock(Message.class);
    when(message.getId()).thenReturn(messageId);
    when(queueController.isNotPrimary()).thenReturn(false);
    when(redisConsumer.read(any(Duration.class)))
        .thenReturn(Collections.singletonList(message))
        .thenReturn(Collections.emptyList());
    when(delegateTaskEventListener.handleMessage(message)).thenReturn(true);

    CountDownLatch latch = new CountDownLatch(1);
    doAnswer(invocation -> {
      latch.countDown();
      return null;
    })
        .when(redisConsumer)
        .acknowledge(messageId);

    Thread thread = new Thread(observerEventConsumer);
    thread.start();

    boolean processed = latch.await(5, TimeUnit.SECONDS);
    thread.interrupt();
    thread.join(2000);

    assertThat(processed).as("Message should be processed and acknowledged within timeout").isTrue();
    verify(redisConsumer).acknowledge(messageId);
    verify(delegateTaskEventListener).handleMessage(message);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testRun_whenNotPrimary_shouldNotProcessMessages() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    when(queueController.isNotPrimary()).thenAnswer(invocation -> {
      latch.countDown();
      return true;
    });

    Thread thread = new Thread(observerEventConsumer);
    thread.start();
    latch.await(5, TimeUnit.SECONDS);
    thread.interrupt();
    thread.join(2000);

    verify(redisConsumer, never()).read(any(Duration.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testRun_whenListenerReturnsFalse_shouldNotAcknowledge() throws Exception {
    String messageId = "msg-002";
    Message message = mock(Message.class);
    when(message.getId()).thenReturn(messageId);
    when(queueController.isNotPrimary()).thenReturn(false);

    CountDownLatch readLatch = new CountDownLatch(1);
    CountDownLatch processedLatch = new CountDownLatch(1);
    when(redisConsumer.read(any(Duration.class))).thenAnswer(invocation -> {
      readLatch.countDown();
      return Collections.singletonList(message);
    });
    when(delegateTaskEventListener.handleMessage(message)).thenAnswer(invocation -> {
      processedLatch.countDown();
      return false;
    });

    Thread thread = new Thread(observerEventConsumer);
    thread.start();

    boolean reached = readLatch.await(5, TimeUnit.SECONDS);
    processedLatch.await(5, TimeUnit.SECONDS);
    thread.interrupt();
    thread.join(2000);

    assertThat(reached).as("Should have attempted to read messages").isTrue();
    verify(redisConsumer, never()).acknowledge(messageId);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testRun_whenEventsFrameworkDown_shouldHandleGracefully() throws Exception {
    when(queueController.isNotPrimary()).thenReturn(false);

    CountDownLatch latch = new CountDownLatch(1);
    when(redisConsumer.read(any(Duration.class))).thenAnswer(invocation -> {
      latch.countDown();
      throw new EventsFrameworkDownException("Redis is down");
    });

    Thread thread = new Thread(observerEventConsumer);
    thread.start();

    boolean reached = latch.await(5, TimeUnit.SECONDS);
    thread.interrupt();
    thread.join(2000);

    assertThat(reached).as("Should have attempted to read and caught EventsFrameworkDownException").isTrue();
    verify(redisConsumer, never()).acknowledge(any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testRun_whenListenerThrowsException_shouldReturnFalseAndNotAcknowledge() throws Exception {
    String messageId = "msg-003";
    Message message = mock(Message.class);
    when(message.getId()).thenReturn(messageId);
    when(queueController.isNotPrimary()).thenReturn(false);

    CountDownLatch readLatch = new CountDownLatch(1);
    CountDownLatch handledLatch = new CountDownLatch(1);
    when(redisConsumer.read(any(Duration.class))).thenAnswer(invocation -> {
      readLatch.countDown();
      return Collections.singletonList(message);
    });
    when(delegateTaskEventListener.handleMessage(message)).thenAnswer(invocation -> {
      handledLatch.countDown();
      throw new RuntimeException("Listener error");
    });

    Thread thread = new Thread(observerEventConsumer);
    thread.start();

    boolean reached = readLatch.await(5, TimeUnit.SECONDS);
    handledLatch.await(5, TimeUnit.SECONDS);
    thread.interrupt();
    thread.join(2000);

    assertThat(reached).as("Should have attempted to read messages").isTrue();
    verify(redisConsumer, never()).acknowledge(messageId);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testRun_whenMultipleMessages_shouldProcessEachMessage() throws Exception {
    String messageId1 = "msg-010";
    String messageId2 = "msg-011";
    Message message1 = mock(Message.class);
    Message message2 = mock(Message.class);
    when(message1.getId()).thenReturn(messageId1);
    when(message2.getId()).thenReturn(messageId2);
    when(queueController.isNotPrimary()).thenReturn(false);

    CountDownLatch ackLatch = new CountDownLatch(1);
    when(redisConsumer.read(any(Duration.class)))
        .thenReturn(Arrays.asList(message1, message2))
        .thenReturn(Collections.emptyList());
    when(delegateTaskEventListener.handleMessage(message1)).thenReturn(true);
    when(delegateTaskEventListener.handleMessage(message2)).thenReturn(false);
    doAnswer(invocation -> {
      ackLatch.countDown();
      return null;
    })
        .when(redisConsumer)
        .acknowledge(messageId1);

    Thread thread = new Thread(observerEventConsumer);
    thread.start();

    boolean reached = ackLatch.await(5, TimeUnit.SECONDS);
    thread.interrupt();
    thread.join(2000);

    assertThat(reached).as("Should have processed multiple messages").isTrue();
    verify(redisConsumer).acknowledge(messageId1);
    verify(redisConsumer, never()).acknowledge(messageId2);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testRun_whenInterrupted_shouldExitGracefully() throws Exception {
    when(queueController.isNotPrimary()).thenReturn(false);

    CountDownLatch latch = new CountDownLatch(1);
    when(redisConsumer.read(any(Duration.class))).thenAnswer(invocation -> {
      latch.countDown();
      return Collections.emptyList();
    });

    Thread thread = new Thread(observerEventConsumer);
    thread.start();
    latch.await(5, TimeUnit.SECONDS);
    thread.interrupt();
    thread.join(2000);

    assertThat(thread.isAlive()).as("Thread should have exited after interrupt").isFalse();
  }
}
