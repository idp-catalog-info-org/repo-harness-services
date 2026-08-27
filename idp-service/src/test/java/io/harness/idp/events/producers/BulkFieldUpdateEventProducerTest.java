/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.producers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_BULK_FIELD_UPDATE_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class BulkFieldUpdateEventProducerTest extends CategoryTest {
  private static final String OPERATION_ID = "op123";
  private static final String ACCOUNT_ID = "acc1";

  @Mock private Producer eventProducer;

  private BulkFieldUpdateEventProducer bulkFieldUpdateEventProducer;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    bulkFieldUpdateEventProducer = new BulkFieldUpdateEventProducer(eventProducer);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPublishSuccess() {
    when(eventProducer.send(any())).thenReturn("eventId123");

    boolean result = bulkFieldUpdateEventProducer.publish(OPERATION_ID, ACCOUNT_ID);

    assertThat(result).isTrue();
    verify(eventProducer)
        .send(argThat(message
            -> message.getMetadataMap().get("accountId").equals(ACCOUNT_ID)
                && message.getMetadataMap().get(ENTITY_TYPE).equals(IDP_BULK_FIELD_UPDATE_EVENT)
                && message.getMetadataMap().get("action").equals(CREATE_ACTION)
                && message.getData().toStringUtf8().contains(OPERATION_ID)
                && message.getData().toStringUtf8().contains(ACCOUNT_ID)));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPublishEventsFrameworkDown() {
    when(eventProducer.send(any())).thenThrow(new EventsFrameworkDownException("Events framework down"));

    boolean result = bulkFieldUpdateEventProducer.publish(OPERATION_ID, ACCOUNT_ID);

    assertThat(result).isFalse();
  }
}
