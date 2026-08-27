/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.junit.Assert.assertTrue;
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
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.IDP)
public class UserMembershipStreamConsumerTest extends CategoryTest {
  @Mock private Consumer redisConsumer;

  @Mock private MessageListener crudListener;

  @Mock private QueueController queueController;

  @InjectMocks @Spy private UserMembershipStreamConsumer userMembershipStreamConsumer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    assertTrue(true);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testRunShouldExitIfShouldStopIsTrue() {
    when(queueController.isNotPrimary()).thenReturn(false);
    userMembershipStreamConsumer.shutDown();

    userMembershipStreamConsumer.run();

    verify(redisConsumer, never()).read(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testPollAndProcessMessagesShouldProcessMessagesAndAcknowledge() {
    List<Message> messages = new ArrayList<>();
    Message message1 = Message.newBuilder().setId("1").build();
    Message message2 = Message.newBuilder().setId("2").build();
    messages.add(message1);
    messages.add(message2);
    when(redisConsumer.read(any())).thenReturn(messages);
    when(crudListener.handleMessage(message1)).thenReturn(true);
    when(crudListener.handleMessage(message2)).thenReturn(false);

    userMembershipStreamConsumer.pollAndProcessMessages();

    verify(redisConsumer, times(1)).acknowledge("1");
    verify(redisConsumer, never()).acknowledge("2");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessMessageShouldCallHandleMessageOnAllListeners() {
    Message message = Message.newBuilder().build();
    when(crudListener.handleMessage(message)).thenReturn(true);

    boolean result = userMembershipStreamConsumer.processMessage(message);

    verify(crudListener, times(1)).handleMessage(message);
    assert result;
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProcessMessageShouldReturnFalseIfAnyListenerReturnsFalse() {
    Message message = Message.newBuilder().build();
    when(crudListener.handleMessage(message)).thenReturn(false);

    boolean result = userMembershipStreamConsumer.processMessage(message);

    verify(crudListener, times(1)).handleMessage(message);
    assert !result;
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testShutDownShouldSetShouldStopToTrue() {
    userMembershipStreamConsumer.shutDown();

    assert userMembershipStreamConsumer.shouldStop.get();
  }
}
