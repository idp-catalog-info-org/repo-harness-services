/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.setupusage;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.pms.helpers.ConnectorScopeHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class InputSetSetupUsageHelperTest extends PipelineServiceTestBase {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String INPUT_SET_ID = "inputSetId";
  private static final String INPUT_SET_NAME = "Input Set Name";
  private static final String CONNECTOR_REF = "gitConnector";
  private static final String BRANCH = "main";
  private static final String REPO = "test-repo";

  @Mock private Producer eventProducer;
  @Mock private ConnectorScopeHelper connectorScopeHelper;
  @InjectMocks private InputSetSetupUsageHelper inputSetSetupUsageHelper;

  private MockedStatic<IdentifierRefProtoDTOHelper> identifierRefProtoDTOHelperMockedStatic;
  private MockedStatic<GitAwareContextHelper> gitAwareContextHelperMockedStatic;
  private MockedStatic<GitContextHelper> gitContextHelperMockedStatic;

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
    identifierRefProtoDTOHelperMockedStatic = mockStatic(IdentifierRefProtoDTOHelper.class);
    gitAwareContextHelperMockedStatic = mockStatic(GitAwareContextHelper.class);
    gitContextHelperMockedStatic = mockStatic(GitContextHelper.class);
  }

  @After
  public void cleanup() {
    verifyNoMoreInteractions(eventProducer);
    if (identifierRefProtoDTOHelperMockedStatic != null) {
      identifierRefProtoDTOHelperMockedStatic.close();
    }
    if (gitAwareContextHelperMockedStatic != null) {
      gitAwareContextHelperMockedStatic.close();
    }
    if (gitContextHelperMockedStatic != null) {
      gitContextHelperMockedStatic.close();
    }
  }

  private InputSetEntity createRemoteInputSetEntity(String connectorRef) {
    return InputSetEntity.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .pipelineIdentifier(PIPELINE_ID)
        .identifier(INPUT_SET_ID)
        .name(INPUT_SET_NAME)
        .storeType(StoreType.REMOTE)
        .connectorRef(connectorRef)
        .branch(BRANCH)
        .repo(REPO)
        .build();
  }

  private InputSetEntity createInlineInputSetEntity() {
    return InputSetEntity.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .pipelineIdentifier(PIPELINE_ID)
        .identifier(INPUT_SET_ID)
        .name(INPUT_SET_NAME)
        .storeType(StoreType.INLINE)
        .build();
  }

  private ScopeInfo createScopeInfo(String accountId, String orgId, String projectId, String uniqueId) {
    return ScopeInfo.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .uniqueId(uniqueId)
        .build();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_ProjectScopedConnector() throws Exception {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity(CONNECTOR_REF);
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    // Mock ConnectorScopeHelper
    when(connectorScopeHelper.getConnectorScopeInfo(any(io.harness.beans.Scope.class), any())).thenReturn(scopeInfo);

    IdentifierRefProtoDTO connectorRefProtoDTO = IdentifierRefProtoDTO.newBuilder().build();
    identifierRefProtoDTOHelperMockedStatic.when(() -> IdentifierRefProtoDTOHelper.fromIdentifierRef(any()))
        .thenReturn(connectorRefProtoDTO);

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(CONNECTOR_REF))
        .thenReturn(false);

    // Execute
    inputSetSetupUsageHelper.publishSetupUsageEvent(inputSetEntity, scopeInfo, true, BRANCH, REPO);

    // Verify event was sent
    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageCaptor.capture());

    Message sentMessage = messageCaptor.getValue();
    assertThat(sentMessage.getMetadataMap()).containsEntry("accountId", ACCOUNT_ID);
    assertThat(sentMessage.getMetadataMap())
        .containsEntry(EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.CONNECTORS.name());
    assertThat(sentMessage.getMetadataMap())
        .containsEntry(EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);

    // Verify DTO structure
    EntitySetupUsageCreateV2DTO dto = EntitySetupUsageCreateV2DTO.parseFrom(sentMessage.getData());
    assertThat(dto.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(dto.getDeleteOldReferredByRecords()).isTrue();
    assertThat(dto.getReferredByEntity().getType()).isEqualTo(EntityTypeProtoEnum.INPUT_SETS);
    assertThat(dto.getReferredEntitiesCount()).isEqualTo(1);
    assertThat(dto.getReferredEntities(0).getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_AccountScopedConnector() throws Exception {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity("account.accountConnector");
    ScopeInfo projectScopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");
    ScopeInfo accountScopeInfo = createScopeInfo(ACCOUNT_ID, null, null, "account_unique_id");

    // Mock ConnectorScopeHelper to return account scope info
    when(connectorScopeHelper.getConnectorScopeInfo(any(io.harness.beans.Scope.class), any()))
        .thenReturn(accountScopeInfo);

    IdentifierRefProtoDTO connectorRefProtoDTO = IdentifierRefProtoDTO.newBuilder().build();
    identifierRefProtoDTOHelperMockedStatic.when(() -> IdentifierRefProtoDTOHelper.fromIdentifierRef(any()))
        .thenReturn(connectorRefProtoDTO);

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault("account.accountConnector"))
        .thenReturn(false);

    // Execute
    inputSetSetupUsageHelper.publishSetupUsageEvent(inputSetEntity, projectScopeInfo, true, BRANCH, REPO);

    // Verify event was sent with correct scope
    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageCaptor.capture());

    Message sentMessage = messageCaptor.getValue();
    EntitySetupUsageCreateV2DTO dto = EntitySetupUsageCreateV2DTO.parseFrom(sentMessage.getData());
    assertThat(dto.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(dto.getReferredEntitiesCount()).isEqualTo(1);

    // Verify ConnectorScopeHelper was called
    verify(connectorScopeHelper, times(1)).getConnectorScopeInfo(any(io.harness.beans.Scope.class), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_OrgScopedConnector() throws Exception {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity("org.orgConnector");
    ScopeInfo projectScopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");
    ScopeInfo orgScopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, null, "org_unique_id");

    // Mock ConnectorScopeHelper to return org scope info
    when(connectorScopeHelper.getConnectorScopeInfo(any(io.harness.beans.Scope.class), any())).thenReturn(orgScopeInfo);

    IdentifierRefProtoDTO connectorRefProtoDTO = IdentifierRefProtoDTO.newBuilder().build();
    identifierRefProtoDTOHelperMockedStatic.when(() -> IdentifierRefProtoDTOHelper.fromIdentifierRef(any()))
        .thenReturn(connectorRefProtoDTO);

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault("org.orgConnector"))
        .thenReturn(false);

    // Execute
    inputSetSetupUsageHelper.publishSetupUsageEvent(inputSetEntity, projectScopeInfo, true, BRANCH, REPO);

    // Verify event was sent
    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageCaptor.capture());

    // Verify ConnectorScopeHelper was called
    verify(connectorScopeHelper, times(1)).getConnectorScopeInfo(any(io.harness.beans.Scope.class), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_InlineInputSet_NoEventSent() throws Exception {
    InputSetEntity inputSetEntity = createInlineInputSetEntity();
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    // Execute
    inputSetSetupUsageHelper.publishSetupUsageEvent(inputSetEntity, scopeInfo, true, null, null);

    // Verify no event was sent for inline input sets (they return early)
    verifyNoMoreInteractions(eventProducer);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_NullConnectorRef_NoEventSent() throws Exception {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity(null);
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(any())).thenReturn(true);

    // Execute
    inputSetSetupUsageHelper.publishSetupUsageEvent(inputSetEntity, scopeInfo, true, BRANCH, REPO);

    // Verify no event was sent for null/default connector refs (they return early)
    verifyNoMoreInteractions(eventProducer);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_NullConnectorRefEntity_NoEventSent() throws Exception {
    // Input set with null connector ref should not publish any events
    InputSetEntity inputSetEntity = createRemoteInputSetEntity(null);
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(null)).thenReturn(true);

    // Execute
    inputSetSetupUsageHelper.publishSetupUsageEvent(inputSetEntity, scopeInfo, true, BRANCH, REPO);

    // Verify no event was sent (connector ref is null/default)
    verifyNoMoreInteractions(eventProducer);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_ParentIdQueryingDisabled() {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity(CONNECTOR_REF);
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(CONNECTOR_REF))
        .thenReturn(false);

    IdentifierRefProtoDTO connectorRefProtoDTO = IdentifierRefProtoDTO.newBuilder().build();
    identifierRefProtoDTOHelperMockedStatic.when(() -> IdentifierRefProtoDTOHelper.fromIdentifierRef(any()))
        .thenReturn(connectorRefProtoDTO);

    // Execute with isParentIdQueryingEnabled = false
    inputSetSetupUsageHelper.publishSetupUsageEvent(inputSetEntity, scopeInfo, false, BRANCH, REPO);

    // Verify event was sent and connectorScopeHelper was not called
    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageCaptor.capture());

    // Verify ConnectorScopeHelper was NOT called
    verifyNoMoreInteractions(connectorScopeHelper);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testDeleteExistingSetupUsages() throws Exception {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity(CONNECTOR_REF);
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    // Execute
    inputSetSetupUsageHelper.deleteExistingSetupUsages(inputSetEntity, scopeInfo, true);

    // Verify delete event was sent
    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageCaptor.capture());

    Message sentMessage = messageCaptor.getValue();
    assertThat(sentMessage.getMetadataMap()).containsEntry("accountId", ACCOUNT_ID);
    assertThat(sentMessage.getMetadataMap())
        .containsEntry(EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);

    EntitySetupUsageCreateV2DTO dto = EntitySetupUsageCreateV2DTO.parseFrom(sentMessage.getData());
    assertThat(dto.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(dto.getDeleteOldReferredByRecords()).isTrue();
    assertThat(dto.getReferredEntitiesCount()).isEqualTo(0); // No referred entities in delete
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetGitConnectorReference_RemoteInputSet() {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity(CONNECTOR_REF);
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(CONNECTOR_REF))
        .thenReturn(false);

    // Mock ConnectorScopeHelper
    when(connectorScopeHelper.getConnectorScopeInfo(any(io.harness.beans.Scope.class), any())).thenReturn(scopeInfo);

    IdentifierRefProtoDTO connectorRefProtoDTO = IdentifierRefProtoDTO.newBuilder().build();
    identifierRefProtoDTOHelperMockedStatic.when(() -> IdentifierRefProtoDTOHelper.fromIdentifierRef(any()))
        .thenReturn(connectorRefProtoDTO);

    // Execute
    Optional<EntityDetailProtoDTO> result =
        inputSetSetupUsageHelper.getGitConnectorReference(inputSetEntity, scopeInfo, true);

    // Verify
    assertThat(result).isPresent();
    assertThat(result.get().getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetGitConnectorReference_InlineInputSet_ReturnsEmpty() {
    InputSetEntity inputSetEntity = createInlineInputSetEntity();
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    // Execute
    Optional<EntityDetailProtoDTO> result =
        inputSetSetupUsageHelper.getGitConnectorReference(inputSetEntity, scopeInfo, true);

    // Verify
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetGitConnectorReference_NullConnectorRef_ReturnsEmpty() {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity(null);
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.initDefaultScmGitMetaData())
        .then(invocation -> null);
    gitContextHelperMockedStatic.when(() -> GitContextHelper.getGitEntityInfo()).thenReturn(null);
    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(any())).thenReturn(true);

    // Execute
    Optional<EntityDetailProtoDTO> result =
        inputSetSetupUsageHelper.getGitConnectorReference(inputSetEntity, scopeInfo, true);

    // Verify
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetGitConnectorReference_DefaultConnectorRef_ReturnsEmpty() {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity(CONNECTOR_REF);
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(CONNECTOR_REF)).thenReturn(true);

    // Execute
    Optional<EntityDetailProtoDTO> result =
        inputSetSetupUsageHelper.getGitConnectorReference(inputSetEntity, scopeInfo, true);

    // Verify
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_WithGitMetadata() throws Exception {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity(CONNECTOR_REF);
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    // Mock ConnectorScopeHelper
    when(connectorScopeHelper.getConnectorScopeInfo(any(io.harness.beans.Scope.class), any())).thenReturn(scopeInfo);

    IdentifierRefProtoDTO connectorRefProtoDTO = IdentifierRefProtoDTO.newBuilder().build();
    identifierRefProtoDTOHelperMockedStatic.when(() -> IdentifierRefProtoDTOHelper.fromIdentifierRef(any()))
        .thenReturn(connectorRefProtoDTO);

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(CONNECTOR_REF))
        .thenReturn(false);

    // Execute with branch and repo
    inputSetSetupUsageHelper.publishSetupUsageEvent(inputSetEntity, scopeInfo, true, "feature-branch", "my-repo");

    // Verify event was sent
    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageCaptor.capture());

    Message sentMessage = messageCaptor.getValue();
    EntitySetupUsageCreateV2DTO dto = EntitySetupUsageCreateV2DTO.parseFrom(sentMessage.getData());

    // Verify git metadata is present
    EntityDetailProtoDTO referredByEntity = dto.getReferredByEntity();
    assertThat(referredByEntity.hasEntityGitMetadata()).isTrue();
    assertThat(referredByEntity.getEntityGitMetadata().getBranch()).isEqualTo("feature-branch");
    assertThat(referredByEntity.getEntityGitMetadata().getRepo()).isEqualTo("my-repo");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent_WithoutGitMetadata() throws Exception {
    InputSetEntity inputSetEntity = createRemoteInputSetEntity(CONNECTOR_REF);
    ScopeInfo scopeInfo = createScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID, "project_unique_id");

    // Mock ConnectorScopeHelper
    when(connectorScopeHelper.getConnectorScopeInfo(any(io.harness.beans.Scope.class), any())).thenReturn(scopeInfo);

    IdentifierRefProtoDTO connectorRefProtoDTO = IdentifierRefProtoDTO.newBuilder().build();
    identifierRefProtoDTOHelperMockedStatic.when(() -> IdentifierRefProtoDTOHelper.fromIdentifierRef(any()))
        .thenReturn(connectorRefProtoDTO);

    gitAwareContextHelperMockedStatic.when(() -> GitAwareContextHelper.isNullOrDefault(CONNECTOR_REF))
        .thenReturn(false);

    // Execute without branch and repo
    inputSetSetupUsageHelper.publishSetupUsageEvent(inputSetEntity, scopeInfo, true, null, null);

    // Verify event was sent
    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(eventProducer, times(1)).send(messageCaptor.capture());

    Message sentMessage = messageCaptor.getValue();
    EntitySetupUsageCreateV2DTO dto = EntitySetupUsageCreateV2DTO.parseFrom(sentMessage.getData());

    // Verify git metadata is not present
    EntityDetailProtoDTO referredByEntity = dto.getReferredByEntity();
    assertThat(referredByEntity.hasEntityGitMetadata()).isFalse();
  }
}
