/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.producers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITY;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.PROJECT_EVENT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.MOVE_ACTION;
import static io.harness.rule.OwnerRule.NITESH_GAHLOT;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.idp.IdpCatalogEntitiesSyncCaptureEvent;
import io.harness.eventsframework.schemas.idp.IdpIntegrationCatalogProcessorEvent;
import io.harness.eventsframework.schemas.idp.IdpLicenseUsageCaptureEvent;
import io.harness.manage.GlobalContextManager;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.BackstageHarnessSyncRequest;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequestIntegrationEntities;
import io.harness.spec.server.idp.v1.model.User;

import com.google.protobuf.ByteString;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IdpServiceMiscRedisProducerTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_USER_EMAIL = "testEmail123";
  static final String TEST_USER_NAME = "testName123";
  static final long TEST_LAST_ACCESSED_AT = 1698294600000L;
  static final String TEST_ENTITY_UID = "testEntityUid";
  static final String TEST_PROJECT_ID = "testProject123";
  static final String TEST_ORG_ID = "testOrg123";
  static final String TEST_OLD_ORG_ID = "oldOrg456";

  @Mock Producer idpModuleLicenseUsageCaptureEventProducer;
  @Mock Producer idpCatalogEntitiesSyncCaptureEventProducer;
  @Mock Producer idpCatalogEntitiesSyncEventProducer;
  @Mock Producer idpCatalogCustomPropertyCaptureEventProducer;
  @Mock Producer idpIntegrationCatalogProcessorEventProducer;
  @Mock Producer idpKindProcessorEventProducer;
  @Mock Producer projectEventsStreamProducer;
  IdpServiceMiscRedisProducer streamProducer;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    streamProducer = new IdpServiceMiscRedisProducer(idpModuleLicenseUsageCaptureEventProducer,
        idpCatalogEntitiesSyncCaptureEventProducer, idpCatalogEntitiesSyncEventProducer,
        idpCatalogCustomPropertyCaptureEventProducer, idpIntegrationCatalogProcessorEventProducer,
        idpKindProcessorEventProducer, projectEventsStreamProducer);
  }

  @After
  public void cleanup() {
    GlobalContextManager.unset();
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPublishIDPLicenseUsageUserCaptureDTOToRedisSuccessful() throws EventsFrameworkDownException {
    String eventId = "test-event-id";
    when(idpModuleLicenseUsageCaptureEventProducer.send(any(Message.class))).thenReturn(eventId);

    streamProducer.publishIDPLicenseUsageUserCaptureDTOToRedis(
        TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER, TEST_USER_EMAIL, TEST_USER_NAME, TEST_LAST_ACCESSED_AT);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(idpModuleLicenseUsageCaptureEventProducer).send(messageCaptor.capture());
    Message sentMessage = messageCaptor.getValue();
    assertEquals(TEST_ACCOUNT_IDENTIFIER, sentMessage.getMetadataOrDefault("accountIdentifier", ""));
    assertEquals(IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT,
        sentMessage.getMetadataOrDefault(EventsFrameworkMetadataConstants.ENTITY_TYPE, ""));
    assertEquals(CREATE_ACTION, sentMessage.getMetadataOrDefault(EventsFrameworkMetadataConstants.ACTION, ""));
    ByteString expectedPayload = IdpLicenseUsageCaptureEvent.newBuilder()
                                     .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .setUserIdentifier(TEST_USER_IDENTIFIER)
                                     .setEmail(TEST_USER_EMAIL)
                                     .setUserName(TEST_USER_NAME)
                                     .setAccessedAt(TEST_LAST_ACCESSED_AT)
                                     .build()
                                     .toByteString();
    assertEquals(expectedPayload, sentMessage.getData());
  }

  @Test(expected = Exception.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPublishIDPLicenseUsageUserCaptureDTOToRedisFailure() {
    given(idpModuleLicenseUsageCaptureEventProducer.send(any(Message.class))).willAnswer(invocation -> {
      throw new Exception("Exception Throw");
    });

    streamProducer.publishIDPLicenseUsageUserCaptureDTOToRedis(
        TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER, TEST_USER_EMAIL, TEST_USER_NAME, TEST_LAST_ACCESSED_AT);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPublishIDPCatalogEntitiesSyncCaptureToRedisSuccessful() throws EventsFrameworkDownException {
    String eventId = "test-event-id";
    when(idpCatalogEntitiesSyncCaptureEventProducer.send(any(Message.class))).thenReturn(eventId);

    streamProducer.publishIDPCatalogEntitiesSyncCaptureToRedis(TEST_ACCOUNT_IDENTIFIER, TEST_ENTITY_UID, "create",
        new User().uuid("uuid").email("email").name("name"), BackstageHarnessSyncRequest.TypeEnum.ENTITY);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(idpCatalogEntitiesSyncCaptureEventProducer).send(messageCaptor.capture());
    Message sentMessage = messageCaptor.getValue();
    assertEquals(TEST_ACCOUNT_IDENTIFIER, sentMessage.getMetadataOrDefault("accountIdentifier", ""));
    assertEquals(
        IDP_CATALOG_ENTITY, sentMessage.getMetadataOrDefault(EventsFrameworkMetadataConstants.ENTITY_TYPE, ""));
    assertEquals(CREATE_ACTION, sentMessage.getMetadataOrDefault(EventsFrameworkMetadataConstants.ACTION, ""));
    ByteString expectedPayload = IdpCatalogEntitiesSyncCaptureEvent.newBuilder()
                                     .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .setIdentifier(TEST_ENTITY_UID)
                                     .setAction("create")
                                     .setSyncMode("sync")
                                     .setType(BackstageHarnessSyncRequest.TypeEnum.ENTITY.value())
                                     .setUserUuid("uuid")
                                     .setUserEmail("email")
                                     .setUserName("name")
                                     .build()
                                     .toByteString();
    assertEquals(expectedPayload, sentMessage.getData());
  }

  @Test(expected = Exception.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPublishIDPCatalogEntitiesSyncCaptureToRedisFailure() {
    given(idpCatalogEntitiesSyncCaptureEventProducer.send(any(Message.class))).willAnswer(invocation -> {
      throw new Exception("Exception Throw");
    });

    streamProducer.publishIDPCatalogEntitiesSyncCaptureToRedis(TEST_ACCOUNT_IDENTIFIER, TEST_ENTITY_UID, "create",
        new User().uuid("uuid").email("email").name("name"), BackstageHarnessSyncRequest.TypeEnum.ENTITY);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPublishProjectEventToRedisSuccessful() throws EventsFrameworkDownException {
    String eventId = "test-event-id";
    ProjectEntityChangeDTO projectDTO = ProjectEntityChangeDTO.newBuilder()
                                            .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                            .setIdentifier(TEST_PROJECT_ID)
                                            .setOrgIdentifier(TEST_ORG_ID)
                                            .setOldOrgIdentifier(TEST_OLD_ORG_ID)
                                            .build();

    when(projectEventsStreamProducer.send(any(Message.class))).thenReturn(eventId);

    streamProducer.publishProjectEventToRedis(projectDTO, MOVE_ACTION);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(projectEventsStreamProducer).send(messageCaptor.capture());
    Message sentMessage = messageCaptor.getValue();

    assertEquals(TEST_ACCOUNT_IDENTIFIER, sentMessage.getMetadataOrDefault("accountIdentifier", ""));
    assertEquals(
        PROJECT_EVENT_ENTITY, sentMessage.getMetadataOrDefault(EventsFrameworkMetadataConstants.ENTITY_TYPE, ""));
    assertEquals(MOVE_ACTION, sentMessage.getMetadataOrDefault(EventsFrameworkMetadataConstants.ACTION, ""));
    assertEquals(projectDTO.toByteString(), sentMessage.getData());
  }

  @Test(expected = Exception.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPublishProjectEventToRedisFailureThrowsException() {
    ProjectEntityChangeDTO projectDTO = ProjectEntityChangeDTO.newBuilder()
                                            .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                            .setIdentifier(TEST_PROJECT_ID)
                                            .setOrgIdentifier(TEST_ORG_ID)
                                            .build();

    given(projectEventsStreamProducer.send(any(Message.class))).willAnswer(invocation -> {
      throw new Exception("Producer failed");
    });

    streamProducer.publishProjectEventToRedis(projectDTO, CREATE_ACTION);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPublishProjectEventToRedis_AllActions() throws EventsFrameworkDownException {
    String[] actions = {CREATE_ACTION, MOVE_ACTION, EventsFrameworkMetadataConstants.UPDATE_ACTION,
        EventsFrameworkMetadataConstants.DELETE_ACTION};
    ProjectEntityChangeDTO projectDTO = ProjectEntityChangeDTO.newBuilder()
                                            .setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                            .setIdentifier(TEST_PROJECT_ID)
                                            .setOrgIdentifier(TEST_ORG_ID)
                                            .build();

    when(projectEventsStreamProducer.send(any(Message.class))).thenReturn("event-id");

    for (String action : actions) {
      streamProducer.publishProjectEventToRedis(projectDTO, action);
    }

    verify(projectEventsStreamProducer, org.mockito.Mockito.times(4)).send(any(Message.class));
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testPublishIntegrationCatalogEventAllowsOptionalFieldsToBeNull() throws Exception {
    when(idpIntegrationCatalogProcessorEventProducer.send(any(Message.class))).thenReturn("event-id");
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new UserPrincipal("Test User", "test@harness.io", "user-uuid", TEST_ACCOUNT_IDENTIFIER));

    SaveDiscoverEntitiesRequestIntegrationEntities integrationEntity =
        new SaveDiscoverEntitiesRequestIntegrationEntities()
            .integrationEntityId("entity-uuid")
            .action(SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.REGISTER);
    SaveDiscoverEntitiesRequest request = new SaveDiscoverEntitiesRequest()
                                              .selectionFilter(SaveDiscoverEntitiesRequest.SelectionFilterEnum.MANUAL)
                                              .integrationEntities(List.of(integrationEntity));

    streamProducer.publishIDPIntegrationCatalogProcessorEventToRedis(
        TEST_ACCOUNT_IDENTIFIER, null, null, "harness-ci", request);

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(idpIntegrationCatalogProcessorEventProducer).send(messageCaptor.capture());
    IdpIntegrationCatalogProcessorEvent event =
        IdpIntegrationCatalogProcessorEvent.parseFrom(messageCaptor.getValue().getData());
    assertEquals(false, event.getAutoDiscover());
    assertEquals("", event.getIntegrationEntities(0).getActionDestination());
  }
}
