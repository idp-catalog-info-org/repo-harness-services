/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DISABLE_TRIGGERS;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_ENTITY;
import static io.harness.rule.OwnerRule.RISHABH;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.service.FilterService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class ProjectEntityCrudStreamListenerTest extends CategoryTest {
  @Mock private PMSPipelineService pmsPipelineService;
  @Mock private NGTriggerService ngTriggerService;
  @Mock private FilterService filterService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;

  private ProjectEntityCrudStreamListener projectEntityCrudStreamListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    projectEntityCrudStreamListener =
        new ProjectEntityCrudStreamListener(pmsPipelineService, ngTriggerService, filterService, pmsFeatureFlagService);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testEmptyHandleMessage() {
    Message message = Message.newBuilder().build();
    assertTrue(projectEntityCrudStreamListener.handleMessage(message));
    verifyNoInteractions(pmsPipelineService, ngTriggerService, filterService);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNonProjectEntityEventHandleMessage() {
    // Action type is not related to project entity
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, USER_ENTITY)
                                          .putMetadata(ACTION, CREATE_ACTION)
                                          .setData(ByteString.copyFromUtf8("Dummy"))
                                          .build())
                          .build();
    assertTrue(projectEntityCrudStreamListener.handleMessage(message));
    verifyNoInteractions(pmsPipelineService, ngTriggerService, filterService);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testInvalidMessageData() {
    // Message with invalid data that cannot be parsed as ProjectEntityChangeDTO
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PROJECT_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .setData(ByteString.copyFromUtf8("Invalid data"))
                                          .build())
                          .build();

    try {
      projectEntityCrudStreamListener.handleMessage(message);
      org.junit.Assert.fail("Expected InvalidRequestException was not thrown");
    } catch (InvalidRequestException e) {
      // Expected exception
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testProcessDeleteEventWithFilterDeletionEnabled() {
    // Create a valid ProjectEntityChangeDTO for delete action
    ProjectEntityChangeDTO entityChangeDTO = ProjectEntityChangeDTO.newBuilder()
                                                 .setAccountIdentifier("account123")
                                                 .setOrgIdentifier("org123")
                                                 .setIdentifier("project123")
                                                 .setUniqueId("uniqueId123")
                                                 .build();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PROJECT_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .setData(entityChangeDTO.toByteString())
                                          .build())
                          .build();

    // Mock feature flag to be enabled
    when(pmsFeatureFlagService.isEnabled(
             "account123", FeatureName.PIPE_SUPPORT_FILTER_DELETION_ON_ORG_OR_PROJECT_DELETION))
        .thenReturn(true);

    assertTrue(projectEntityCrudStreamListener.handleMessage(message));

    // Verify pipeline service was called to delete all pipelines in the project
    verify(pmsPipelineService, times(1))
        .deleteAllPipelinesInAProject("account123", "org123", "project123",
            ScopeInfo.builder()
                .accountIdentifier("account123")
                .orgIdentifier("org123")
                .projectIdentifier("project123")
                .uniqueId("uniqueId123")
                .scopeType(ScopeLevel.PROJECT)
                .build());

    // Verify filter service was called to delete filters by scope
    verify(filterService, times(1))
        .deleteByScope(eq(ScopeInfo.builder()
                              .accountIdentifier("account123")
                              .orgIdentifier("org123")
                              .projectIdentifier("project123")
                              .uniqueId("uniqueId123")
                              .scopeType(ScopeLevel.PROJECT)
                              .build()));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testProcessDeleteEventWithFilterDeletionDisabled() {
    // Create a valid ProjectEntityChangeDTO for delete action
    ProjectEntityChangeDTO entityChangeDTO = ProjectEntityChangeDTO.newBuilder()
                                                 .setAccountIdentifier("account123")
                                                 .setOrgIdentifier("org123")
                                                 .setIdentifier("project123")
                                                 .setUniqueId("uniqueId123")
                                                 .build();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PROJECT_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .setData(entityChangeDTO.toByteString())
                                          .build())
                          .build();

    // Mock feature flag to be disabled
    when(pmsFeatureFlagService.isEnabled(
             "account123", FeatureName.PIPE_SUPPORT_FILTER_DELETION_ON_ORG_OR_PROJECT_DELETION))
        .thenReturn(false);

    assertTrue(projectEntityCrudStreamListener.handleMessage(message));

    // Verify pipeline service was called to delete all pipelines in the project
    verify(pmsPipelineService, times(1))
        .deleteAllPipelinesInAProject("account123", "org123", "project123",
            ScopeInfo.builder()
                .accountIdentifier("account123")
                .orgIdentifier("org123")
                .projectIdentifier("project123")
                .uniqueId("uniqueId123")
                .scopeType(ScopeLevel.PROJECT)
                .build());

    // Verify filter service was NOT called to delete filters
    verify(filterService, never()).deleteByScope(any(ScopeInfo.class));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testProcessTriggerDisableEvent() {
    // Create a valid ProjectEntityChangeDTO for disable triggers action
    ProjectEntityChangeDTO entityChangeDTO = ProjectEntityChangeDTO.newBuilder()
                                                 .setAccountIdentifier("account123")
                                                 .setOrgIdentifier("org123")
                                                 .setIdentifier("project123")
                                                 .setUniqueId("uniqueId123")
                                                 .build();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PROJECT_ENTITY)
                                          .putMetadata(ACTION, DISABLE_TRIGGERS)
                                          .setData(entityChangeDTO.toByteString())
                                          .build())
                          .build();

    assertTrue(projectEntityCrudStreamListener.handleMessage(message));

    // Verify trigger service was called to toggle triggers to disabled state
    verify(ngTriggerService, times(1))
        .toggleTriggers(eq(false), eq("account123"), eq("org123"), eq("project123"), eq(null), eq(null),
            eq(ScopeInfo.builder()
                    .accountIdentifier("account123")
                    .orgIdentifier("org123")
                    .projectIdentifier("project123")
                    .uniqueId("uniqueId123")
                    .scopeType(ScopeLevel.PROJECT)
                    .build()));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleMessageWithUnknownAction() {
    // Create a message with an action that is not handled specifically
    ProjectEntityChangeDTO entityChangeDTO = ProjectEntityChangeDTO.newBuilder()
                                                 .setAccountIdentifier("account123")
                                                 .setOrgIdentifier("org123")
                                                 .setIdentifier("project123")
                                                 .build();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PROJECT_ENTITY)
                                          .putMetadata(ACTION, "UNKNOWN_ACTION")
                                          .setData(entityChangeDTO.toByteString())
                                          .build())
                          .build();

    assertTrue(projectEntityCrudStreamListener.handleMessage(message));

    // Verify that no service methods were called
    verifyNoInteractions(pmsPipelineService, ngTriggerService, filterService);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testNullActionInMessage() {
    // Create a message with no action specified
    ProjectEntityChangeDTO entityChangeDTO = ProjectEntityChangeDTO.newBuilder()
                                                 .setAccountIdentifier("account123")
                                                 .setOrgIdentifier("org123")
                                                 .setIdentifier("project123")
                                                 .build();

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PROJECT_ENTITY)
                                          .setData(entityChangeDTO.toByteString())
                                          .build())
                          .build();

    assertTrue(projectEntityCrudStreamListener.handleMessage(message));

    // Verify that no service methods were called
    verifyNoInteractions(pmsPipelineService, ngTriggerService, filterService);
  }
}
