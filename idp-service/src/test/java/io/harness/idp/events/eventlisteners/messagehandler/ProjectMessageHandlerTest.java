/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.messagehandler;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.*;
import static io.harness.rule.OwnerRule.ANKUR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ProjectMessageHandlerTest extends CategoryTest {
  @Mock private IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;

  @InjectMocks ProjectMessageHandler projectMessageHandler;

  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_ORG_ID = "test-org-id";
  private static final String TEST_PROJECT_ID = "test-project-id";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  private Message buildMessage(String action) {
    ProjectEntityChangeDTO projectDto = ProjectEntityChangeDTO.newBuilder()
                                            .setAccountIdentifier(TEST_ACCOUNT_ID)
                                            .setOrgIdentifier(TEST_ORG_ID)
                                            .setIdentifier(TEST_PROJECT_ID)
                                            .build();
    return Message.newBuilder()
        .setMessage(
            io.harness.eventsframework.producer.Message.newBuilder()
                .putAllMetadata(ImmutableMap.of("accountId", TEST_ACCOUNT_ID,
                    EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.PROJECT_ENTITY,
                    EventsFrameworkMetadataConstants.ACTION, action))
                .setData(projectDto.toByteString())
                .build())
        .build();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleMessage() throws Exception {
    Message message = buildMessage(CREATE_ACTION);
    projectMessageHandler.handleMessage(message, null, CREATE_ACTION);
    verify(idpServiceMiscRedisProducer).publishProjectEventToRedis(any(), any());
  }
}
