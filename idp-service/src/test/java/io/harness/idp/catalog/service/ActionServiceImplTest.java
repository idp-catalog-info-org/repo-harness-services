/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.rule.OwnerRule.ARYA;
import static io.harness.rule.OwnerRule.DIPENDRA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.event.Event;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.ActionHttpConfig;
import io.harness.idp.catalog.entities.ActionStatus;
import io.harness.idp.catalog.entities.ActionType;
import io.harness.idp.catalog.repositories.ActionRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ActionUpdateRequest;
import io.harness.springdata.TransactionHelper;
import io.harness.springdata.TransactionHelper.TransactionFunction;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ActionServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account";
  private static final String ORG_ID = "test-org";
  private static final String PROJECT_ID = "test-project";
  private static final String PARENT_UNIQUE_ID = "parent-unique-id";
  private static final String IDENTIFIER = "my-action";
  private static final String VERSION = "1.0.0";

  @Mock private ActionRepository actionRepository;
  @Mock private OutboxService outboxService;
  @Mock private TransactionHelper transactionHelper;
  @Mock private CatalogScopeResolver catalogScopeResolver;

  private ActionServiceImpl actionService;
  private AutoCloseable openMocks;
  private ScopeInfo scopeInfo;

  @Before
  public void setUp() throws Exception {
    openMocks = MockitoAnnotations.openMocks(this);
    scopeInfo = ScopeInfo.builder()
                    .uniqueId(PARENT_UNIQUE_ID)
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_ID)
                    .projectIdentifier(PROJECT_ID)
                    .scopeType(ScopeLevel.PROJECT)
                    .build();
    when(transactionHelper.performTransaction(any(TransactionFunction.class))).thenAnswer(invocation -> {
      TransactionFunction<?> fn = invocation.getArgument(0);
      return fn.execute();
    });

    actionService = new ActionServiceImpl(actionRepository, outboxService, transactionHelper, catalogScopeResolver);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  // --- createAction ---

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void createAction_success() {
    Action action = buildDraftHttpAction();
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.empty());
    when(actionRepository.save(any(Action.class))).thenAnswer(i -> i.getArgument(0));

    Action result = actionService.createAction(scopeInfo, action);

    assertThat(result.getParentUniqueId()).isEqualTo(PARENT_UNIQUE_ID);
    assertThat(result.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    verify(actionRepository).save(any(Action.class));
    verify(outboxService).save(any(Event.class));
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void createAction_duplicateThrows() {
    Action action = buildDraftHttpAction();
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(action));

    assertThatThrownBy(() -> actionService.createAction(scopeInfo, action)).isInstanceOf(DuplicateFieldException.class);
    verify(actionRepository, never()).save(any(Action.class));
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void createAction_invalidInputSchema_throws() {
    Action action = buildDraftHttpAction();
    action.setInputSchema(Map.of("type", "array"));
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> actionService.createAction(scopeInfo, action)).isInstanceOf(InvalidRequestException.class);
    verify(actionRepository, never()).save(any(Action.class));
  }

  // --- getAction ---

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void getAction_found() {
    Action action = buildDraftHttpAction();
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(action));

    Action result = actionService.getAction(scopeInfo, IDENTIFIER, VERSION);

    assertThat(result).isEqualTo(action);
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void getAction_notFound_throws() {
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> actionService.getAction(scopeInfo, IDENTIFIER, VERSION))
        .isInstanceOf(InvalidRequestException.class);
  }

  // --- getPublishedAction ---

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void getPublishedAction_found() {
    Action published = buildPublishedHttpAction();
    when(actionRepository.findPublishedVersion(PARENT_UNIQUE_ID, IDENTIFIER)).thenReturn(Optional.of(published));

    Action result = actionService.getPublishedAction(scopeInfo, IDENTIFIER);

    assertThat(result.getStatus()).isEqualTo(ActionStatus.PUBLISHED);
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void getPublishedAction_nonePublished_throws() {
    when(actionRepository.findPublishedVersion(PARENT_UNIQUE_ID, IDENTIFIER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> actionService.getPublishedAction(scopeInfo, IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class);
  }

  // --- updateAction ---

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void updateAction_deprecatedCannotBeModified() {
    Action deprecated = buildDraftHttpAction();
    deprecated.setStatus(ActionStatus.DEPRECATED);
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(deprecated));

    ActionUpdateRequest request = new ActionUpdateRequest();
    request.setName("New Name");

    assertThatThrownBy(() -> actionService.updateAction(scopeInfo, IDENTIFIER, VERSION, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("DEPRECATED");
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void updateAction_putReplacesAllFields() {
    Action existing = buildDraftHttpAction();
    existing.setDescription("Old Description");
    existing.setCategory("old-cat");
    existing.setParentUniqueId(PARENT_UNIQUE_ID);
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(existing));
    when(actionRepository.save(any(Action.class))).thenAnswer(i -> i.getArgument(0));

    ActionUpdateRequest request = new ActionUpdateRequest();
    request.setName("New Name");

    Action result = actionService.updateAction(scopeInfo, IDENTIFIER, VERSION, request);

    assertThat(result.getName()).isEqualTo("New Name");
    assertThat(result.getDescription()).isNull();
    assertThat(result.getCategory()).isNull();
  }

  // --- changeStatus ---

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void changeStatus_draftToPublished_succeeds() {
    Action existing = buildDraftHttpAction();
    existing.setParentUniqueId(PARENT_UNIQUE_ID);
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(existing));
    when(actionRepository.save(any(Action.class))).thenAnswer(i -> i.getArgument(0));

    Action result = actionService.changeStatus(scopeInfo, IDENTIFIER, VERSION, ActionStatus.PUBLISHED);

    assertThat(result.getStatus()).isEqualTo(ActionStatus.PUBLISHED);
    verify(actionRepository).deprecateCurrentlyPublished(PARENT_UNIQUE_ID, IDENTIFIER);
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void changeStatus_publishedToDeprecated_succeeds() {
    Action existing = buildPublishedHttpAction();
    existing.setParentUniqueId(PARENT_UNIQUE_ID);
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(existing));
    when(actionRepository.save(any(Action.class))).thenAnswer(i -> i.getArgument(0));

    Action result = actionService.changeStatus(scopeInfo, IDENTIFIER, VERSION, ActionStatus.DEPRECATED);

    assertThat(result.getStatus()).isEqualTo(ActionStatus.DEPRECATED);
    assertThat(result.getDeprecatedAt()).isNotNull();
    verify(actionRepository, never()).deprecateCurrentlyPublished(anyString(), anyString());
  }

  // --- deleteAction ---

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void deleteAction_draftSucceeds() {
    Action draft = buildDraftHttpAction();
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(draft));

    actionService.deleteAction(scopeInfo, IDENTIFIER, VERSION);

    verify(actionRepository).delete(draft);
    verify(outboxService).save(any(Event.class));
  }

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void deleteAction_publishedThrows() {
    Action published = buildPublishedHttpAction();
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(published));

    assertThatThrownBy(() -> actionService.deleteAction(scopeInfo, IDENTIFIER, VERSION))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("PUBLISHED");
    verify(actionRepository, never()).delete(any(Action.class));
  }

  // --- listActionVersions ---

  @Test
  @Owner(developers = DIPENDRA)
  @Category(UnitTests.class)
  public void listActionVersions_returnsList() {
    Action v1 = buildDraftHttpAction();
    Action v2 = buildDraftHttpAction();
    v2.setVersion("2.0.0");
    when(actionRepository.findByParentUniqueIdAndIdentifier(PARENT_UNIQUE_ID, IDENTIFIER)).thenReturn(List.of(v1, v2));

    List<Action> versions = actionService.listActionVersions(scopeInfo, IDENTIFIER);

    assertThat(versions).hasSize(2);
    verify(actionRepository).findByParentUniqueIdAndIdentifier(PARENT_UNIQUE_ID, IDENTIFIER);
  }

  // --- OOTB / GLOBAL fallback ---

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void getAction_fallsBackToGlobalWhenCallerScopeMisses() {
    Action ootb = buildOotbAction();
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.empty());
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(
             Action.GLOBAL_PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(ootb));

    Action result = actionService.getAction(scopeInfo, IDENTIFIER, VERSION);

    assertThat(result).isEqualTo(ootb);
    assertThat(result.getParentUniqueId()).isEqualTo(Action.GLOBAL_PARENT_UNIQUE_ID);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void getAction_callerScopeWinsOverGlobal() {
    Action tenant = buildDraftHttpAction();
    Action ootb = buildOotbAction();
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(tenant));

    Action result = actionService.getAction(scopeInfo, IDENTIFIER, VERSION);

    assertThat(result).isEqualTo(tenant);
    verify(actionRepository, never())
        .findByParentUniqueIdAndIdentifierAndVersion(Action.GLOBAL_PARENT_UNIQUE_ID, IDENTIFIER, VERSION);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void getPublishedAction_fallsBackToGlobalWhenCallerScopeHasNoPublishedVersion() {
    Action ootb = buildOotbAction();
    when(actionRepository.findPublishedVersion(PARENT_UNIQUE_ID, IDENTIFIER)).thenReturn(Optional.empty());
    when(actionRepository.findPublishedVersion(Action.GLOBAL_PARENT_UNIQUE_ID, IDENTIFIER))
        .thenReturn(Optional.of(ootb));

    Action result = actionService.getPublishedAction(scopeInfo, IDENTIFIER);

    assertThat(result).isEqualTo(ootb);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void createAction_rejectsReservedGlobalScope() {
    ScopeInfo globalScope = ScopeInfo.builder()
                                .uniqueId(Action.GLOBAL_PARENT_UNIQUE_ID)
                                .accountIdentifier(Action.GLOBAL_ACCOUNT_IDENTIFIER)
                                .scopeType(ScopeLevel.ACCOUNT)
                                .build();

    assertThatThrownBy(() -> actionService.createAction(globalScope, buildDraftHttpAction()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("reserved global scope");
    verify(actionRepository, never()).save(any(Action.class));
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void createAction_rejectsIdentifierThatCollidesWithOotb() {
    Action ootb = buildOotbAction();
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.empty());
    when(actionRepository.findByParentUniqueIdAndIdentifierAndVersion(
             Action.GLOBAL_PARENT_UNIQUE_ID, IDENTIFIER, VERSION))
        .thenReturn(Optional.of(ootb));

    assertThatThrownBy(() -> actionService.createAction(scopeInfo, buildDraftHttpAction()))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessageContaining("OOTB");
    verify(actionRepository, never()).save(any(Action.class));
  }

  // --- helpers ---

  private Action buildDraftHttpAction() {
    return Action.builder()
        .identifier(IDENTIFIER)
        .name("My Action")
        .version(VERSION)
        .accountIdentifier(ACCOUNT_ID)
        .parentUniqueId(PARENT_UNIQUE_ID)
        .status(ActionStatus.DRAFT)
        .type(ActionType.HTTP)
        .httpConfig(ActionHttpConfig.builder().method("GET").path("/ping").build())
        .build();
  }

  private Action buildPublishedHttpAction() {
    Action action = buildDraftHttpAction();
    action.setStatus(ActionStatus.PUBLISHED);
    return action;
  }

  private Action buildOotbAction() {
    return Action.builder()
        .identifier(IDENTIFIER)
        .name("OOTB Action")
        .version(VERSION)
        .accountIdentifier(Action.GLOBAL_ACCOUNT_IDENTIFIER)
        .parentUniqueId(Action.GLOBAL_PARENT_UNIQUE_ID)
        .status(ActionStatus.PUBLISHED)
        .type(ActionType.HTTP)
        .httpConfig(ActionHttpConfig.builder().method("GET").path("/ping").build())
        .build();
  }
}
