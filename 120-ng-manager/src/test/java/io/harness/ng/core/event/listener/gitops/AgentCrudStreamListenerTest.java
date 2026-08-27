/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener.gitops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.service.instancesync.GitopsInstanceSyncService;

import com.google.protobuf.StringValue;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AgentCrudStreamListenerTest {
  @Mock private GitopsInstanceSyncService gitopsInstanceSyncService;
  @InjectMocks private AgentCrudStreamListener listener;
  private AutoCloseable closeable;

  @Before
  public void openMocks() throws Exception {
    closeable = MockitoAnnotations.openMocks(this);
  }

  @After
  public void closeMocks() throws Exception {
    closeable.close();
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void handleMessageNoAction() {
    boolean b = listener.handleMessage(messageFor(EntityChangeDTO.newBuilder().build()));

    assertThat(b).isTrue();
    Mockito.verifyNoInteractions(gitopsInstanceSyncService);
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void handleMessageNoOpActions() {
    Set.of(EventsFrameworkMetadataConstants.CREATE_ACTION, EventsFrameworkMetadataConstants.UPDATE_ACTION,
           EventsFrameworkMetadataConstants.UPSERT_ACTION)
        .forEach(action
            -> assertThat(listener.handleMessage(messageFor(action, EntityChangeDTO.newBuilder().build()))).isTrue());

    Mockito.verifyNoInteractions(gitopsInstanceSyncService);
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void handleMessageForProjectLevelAgentDeletion() {
    boolean b = listener.handleMessage(messageFor(EventsFrameworkMetadataConstants.DELETE_ACTION,
        EntityChangeDTO.newBuilder()
            .setAccountIdentifier(StringValue.newBuilder().setValue("accountId").build())
            .setOrgIdentifier(StringValue.newBuilder().setValue("orgId").build())
            .setProjectIdentifier(StringValue.newBuilder().setValue("projectId").build())
            .setIdentifier(StringValue.newBuilder().setValue("agentId").build())
            .build()));

    assertThat(b).isTrue();
    verify(gitopsInstanceSyncService, times(1)).deleteInstancesForAgent("accountId", "orgId", "projectId", "agentId");
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void handleMessageForOrgLevelAgentDeletion() {
    boolean b = listener.handleMessage(messageFor(EventsFrameworkMetadataConstants.DELETE_ACTION,
        EntityChangeDTO.newBuilder()
            .setAccountIdentifier(StringValue.newBuilder().setValue("accountId").build())
            .setOrgIdentifier(StringValue.newBuilder().setValue("orgId").build())
            .setIdentifier(StringValue.newBuilder().setValue("agentId").build())
            .build()));

    assertThat(b).isTrue();
    verify(gitopsInstanceSyncService, times(1)).deleteInstancesForAgent("accountId", "orgId", "", "org.agentId");
  }

  @Test
  @Owner(developers = OwnerRule.MANISH)
  @Category(UnitTests.class)
  public void handleMessageForAccountLevelClusterDeletion() {
    boolean b = listener.handleMessage(messageFor(EventsFrameworkMetadataConstants.DELETE_ACTION,
        EntityChangeDTO.newBuilder()
            .setAccountIdentifier(StringValue.newBuilder().setValue("accountId").build())
            .setIdentifier(StringValue.newBuilder().setValue("agentId").build())
            .build()));

    assertThat(b).isTrue();
    verify(gitopsInstanceSyncService, times(1)).deleteInstancesForAgent("accountId", "", "", "account.agentId");
  }

  private Message messageFor(String action, EntityChangeDTO entityChangeDTO) {
    return Message.newBuilder()
        .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                        .putMetadata(EventsFrameworkMetadataConstants.ENTITY_TYPE,
                            EventsFrameworkMetadataConstants.GITOPS_AGENT_ENTITY)
                        .putMetadata(EventsFrameworkMetadataConstants.ACTION, action)
                        .setData(entityChangeDTO.toByteString())
                        .build())
        .build();
  }

  private Message messageFor(EntityChangeDTO entityChangeDTO) {
    return Message.newBuilder()
        .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                        .putMetadata(EventsFrameworkMetadataConstants.ENTITY_TYPE,
                            EventsFrameworkMetadataConstants.GITOPS_AGENT_ENTITY)
                        .setData(entityChangeDTO.toByteString())
                        .build())
        .build();
  }
}
