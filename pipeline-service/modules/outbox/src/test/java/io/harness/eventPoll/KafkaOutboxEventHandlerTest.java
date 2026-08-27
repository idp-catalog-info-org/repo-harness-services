/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eventPoll;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.KafkaOutboxEvent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class KafkaOutboxEventHandlerTest extends CategoryTest {
  @Mock private OutboxEvent mockOutboxEvent;

  private TestKafkaOutboxEventHandler kafkaOutboxEventHandler;

  private static final String TOPIC = "test-topic";
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  private KafkaOutboxEvent testEvent;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    kafkaOutboxEventHandler = new TestKafkaOutboxEventHandler();

    ProjectScope scope = new ProjectScope(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    Resource resource = Resource.builder().identifier("testResource").type("TEST").build();

    testEvent = KafkaOutboxEvent.builder()
                    .topic(TOPIC)
                    .retryCount(0)
                    .blocked(false)
                    .eventType("TEST_EVENT")
                    .eventData("{\"test\": \"data\"}")
                    .resourceScope(scope)
                    .resource(resource)
                    .build();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testHandleKafkaOutboxEvent() {
    boolean result = kafkaOutboxEventHandler.handle(testEvent);

    assertThat(result).isTrue();
    assertThat(kafkaOutboxEventHandler.processEventCalled).isTrue();
    assertThat(kafkaOutboxEventHandler.processedEvent).isEqualTo(testEvent);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testHandleNonKafkaOutboxEvent() {
    boolean result = kafkaOutboxEventHandler.handle(mockOutboxEvent);

    assertThat(result).isFalse();
    assertThat(kafkaOutboxEventHandler.processEventCalled).isFalse();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testProcessEventReturnsFalse() {
    TestKafkaOutboxEventHandler failingHandler = new TestKafkaOutboxEventHandler(false);

    boolean result = failingHandler.handle(testEvent);

    assertThat(result).isFalse();
    assertThat(failingHandler.processEventCalled).isTrue();
  }

  // Test implementation of KafkaOutboxEventHandler for testing purposes
  private static class TestKafkaOutboxEventHandler implements KafkaOutboxEventHandler {
    private boolean processEventCalled = false;
    private KafkaOutboxEvent processedEvent;
    private final boolean shouldReturnTrue;

    public TestKafkaOutboxEventHandler() {
      this.shouldReturnTrue = true;
    }

    public TestKafkaOutboxEventHandler(boolean shouldReturnTrue) {
      this.shouldReturnTrue = shouldReturnTrue;
    }

    @Override
    public boolean processEvent(KafkaOutboxEvent kafkaOutboxEvent) {
      this.processEventCalled = true;
      this.processedEvent = kafkaOutboxEvent;
      return shouldReturnTrue;
    }
  }
}
