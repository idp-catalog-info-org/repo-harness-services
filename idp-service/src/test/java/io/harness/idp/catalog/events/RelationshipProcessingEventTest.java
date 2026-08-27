/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.events;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.RelationshipEventType;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class RelationshipProcessingEventTest extends CategoryTest {
  ObjectMapper objectMapper;

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testEstablishEventSerialization() throws Exception {
    RelationshipProcessingEvent event = RelationshipProcessingEvent.builder()
                                            .entityId("entity123")
                                            .accountIdentifier("acc1")
                                            .eventType(RelationshipEventType.ESTABLISH)
                                            .timestamp(System.currentTimeMillis())
                                            .build();

    String json = objectMapper.writeValueAsString(event);
    RelationshipProcessingEvent deserialized = objectMapper.readValue(json, RelationshipProcessingEvent.class);

    assertThat(deserialized.getEntityId()).isEqualTo("entity123");
    assertThat(deserialized.getAccountIdentifier()).isEqualTo("acc1");
    assertThat(deserialized.getEventType()).isEqualTo(RelationshipEventType.ESTABLISH);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateEventWithSnapshot() throws Exception {
    String existingSnapshot = "{\"id\":\"123\",\"name\":\"old-service\"}";

    RelationshipProcessingEvent event = RelationshipProcessingEvent.builder()
                                            .entityId("entity456")
                                            .accountIdentifier("acc1")
                                            .eventType(RelationshipEventType.UPDATE)
                                            .timestamp(System.currentTimeMillis())
                                            .existingEntitySnapshot(existingSnapshot)
                                            .build();

    String json = objectMapper.writeValueAsString(event);
    RelationshipProcessingEvent deserialized = objectMapper.readValue(json, RelationshipProcessingEvent.class);

    assertThat(deserialized.getEventType()).isEqualTo(RelationshipEventType.UPDATE);
    assertThat(deserialized.getExistingEntitySnapshot()).isEqualTo(existingSnapshot);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDisbandEventWithDeletedSnapshot() throws Exception {
    String deletedSnapshot = "{\"id\":\"789\",\"name\":\"deleted-service\"}";

    RelationshipProcessingEvent event = RelationshipProcessingEvent.builder()
                                            .entityId("entity789")
                                            .accountIdentifier("acc1")
                                            .eventType(RelationshipEventType.DISBAND)
                                            .timestamp(System.currentTimeMillis())
                                            .deletedEntitySnapshot(deletedSnapshot)
                                            .build();

    String json = objectMapper.writeValueAsString(event);
    RelationshipProcessingEvent deserialized = objectMapper.readValue(json, RelationshipProcessingEvent.class);

    assertThat(deserialized.getEventType()).isEqualTo(RelationshipEventType.DISBAND);
    assertThat(deserialized.getDeletedEntitySnapshot()).isEqualTo(deletedSnapshot);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMoveEventWithNewScope() throws Exception {
    ScopeInfo newScope = ScopeInfo.builder()
                             .uniqueId("acc2/org2/proj2")
                             .accountIdentifier("acc2")
                             .orgIdentifier("org2")
                             .projectIdentifier("proj2")
                             .build();

    RelationshipProcessingEvent event = RelationshipProcessingEvent.builder()
                                            .entityId("entity999")
                                            .accountIdentifier("acc1")
                                            .eventType(RelationshipEventType.MOVE)
                                            .timestamp(System.currentTimeMillis())
                                            .newScope(newScope)
                                            .build();

    String json = objectMapper.writeValueAsString(event);
    RelationshipProcessingEvent deserialized = objectMapper.readValue(json, RelationshipProcessingEvent.class);

    assertThat(deserialized.getEventType()).isEqualTo(RelationshipEventType.MOVE);
    assertThat(deserialized.getNewScope()).isNotNull();
    assertThat(deserialized.getNewScope().getAccountIdentifier()).isEqualTo("acc2");
    assertThat(deserialized.getNewScope().getOrgIdentifier()).isEqualTo("org2");
    assertThat(deserialized.getNewScope().getProjectIdentifier()).isEqualTo("proj2");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testJsonIgnoreUnknownProperties() throws Exception {
    String jsonWithExtraFields = "{\"entityId\":\"entity1\",\"accountIdentifier\":\"acc1\","
        + "\"eventType\":\"ESTABLISH\",\"timestamp\":123456,\"unknownField\":\"should be ignored\"}";

    RelationshipProcessingEvent event = objectMapper.readValue(jsonWithExtraFields, RelationshipProcessingEvent.class);

    assertThat(event.getEntityId()).isEqualTo("entity1");
    assertThat(event.getAccountIdentifier()).isEqualTo("acc1");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAllEventTypes() {
    for (RelationshipEventType eventType : RelationshipEventType.values()) {
      RelationshipProcessingEvent event = RelationshipProcessingEvent.builder()
                                              .entityId("entity1")
                                              .accountIdentifier("acc1")
                                              .eventType(eventType)
                                              .timestamp(System.currentTimeMillis())
                                              .build();

      assertThat(event.getEventType()).isEqualTo(eventType);
    }
  }
}
