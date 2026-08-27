/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.EntityLinks;
import io.harness.idp.catalog.entities.EntityLinks.FieldMapping;
import io.harness.idp.catalog.entities.EntityLinks.LinkTarget;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.repositories.EntityLinkRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityLink;
import io.harness.spec.server.idp.v1.model.EntityLinkExistsResponse;
import io.harness.spec.server.idp.v1.model.EntityLinkRequest;
import io.harness.spec.server.idp.v1.model.EntityLinkResponse;
import io.harness.spec.server.idp.v1.model.ResolveFieldMappingsRequest;
import io.harness.spec.server.idp.v1.model.ResolveFieldMappingsResponse;
import io.harness.springdata.TransactionHelper;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class EntityLinkServiceImplTest extends CategoryTest {
  @Mock EntityLinkRepository entityLinkRepository;
  @Mock CatalogEntityRepository catalogEntityRepository;
  @Mock CatalogServiceHelper catalogServiceHelper;
  @Mock CatalogScopeResolver scopeResolver;
  @Mock OutboxService outboxService;
  @Mock TransactionHelper transactionHelper;

  @InjectMocks EntityLinkServiceImpl entityLinkService;

  AutoCloseable openMocks;

  private static final String ACCOUNT_ID = "test-account-id";
  private static final String ENTITY_REF = "workflow:account/my-workflow";
  private static final String ORG_ID = "default";
  private static final String PROJECT_ID = null;
  private static final String PARENT_UID = "parent-uid";
  private ScopeTopology mockTopology;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    when(transactionHelper.performTransaction(any())).thenAnswer(inv -> {
      TransactionHelper.TransactionFunction<?> fn = inv.getArgument(0);
      return fn.execute();
    });
    doNothing().when(catalogServiceHelper).checkCrudRbac(anyString(), anyString(), anyString());
    when(catalogServiceHelper.getOrgProjectFromEntityRef(ENTITY_REF)).thenReturn(Pair.of(ORG_ID, PROJECT_ID));
    when(catalogServiceHelper.getKindScopeIdentifier(anyString())).thenAnswer(inv -> {
      String ref = inv.getArgument(0);
      String[] parts = ref.split(":");
      String kind = parts[0];
      String scopeAndId = parts.length > 1 ? parts[1] : "";
      int slashIdx = scopeAndId.indexOf('/');
      String scope = slashIdx != -1 ? scopeAndId.substring(0, slashIdx) : "account";
      String identifier = slashIdx != -1 ? scopeAndId.substring(slashIdx + 1) : scopeAndId;
      return Triple.of(kind.toLowerCase(), scope, identifier);
    });
    mockTopology = org.mockito.Mockito.mock(ScopeTopology.class);
    when(scopeResolver.getOrBuildTopology(ACCOUNT_ID)).thenReturn(mockTopology);
    when(mockTopology.resolveParentUniqueIds(anyString())).thenReturn(List.of(PARENT_UID));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  // ── createLink ──────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateLink_success() {
    EntityLinkRequest request = buildRequest(ENTITY_REF);
    EntityLinks savedEntity = buildEntityLinks(ENTITY_REF);
    when(entityLinkRepository.save(any())).thenReturn(savedEntity);

    EntityLinkResponse response = entityLinkService.createLink(ACCOUNT_ID, request);

    assertThat(response).isNotNull();
    assertThat(response.getEntityLink().getEntityRef()).isEqualTo(ENTITY_REF);
    verify(outboxService).save(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateLink_invalidKind_throwsException() {
    EntityLinkRequest request = buildRequest("component:account/my-comp");

    assertThatThrownBy(() -> entityLinkService.createLink(ACCOUNT_ID, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("currently only supported for kind [workflow]");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateLink_duplicate_throwsInvalidRequestException() {
    EntityLinkRequest request = buildRequest(ENTITY_REF);
    when(entityLinkRepository.save(any())).thenThrow(new DuplicateKeyException("dup"));

    assertThatThrownBy(() -> entityLinkService.createLink(ACCOUNT_ID, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("already exists");
  }

  // ── updateLink ──────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateLink_success() {
    EntityLinks existing = buildEntityLinks(ENTITY_REF);
    EntityLinkRequest request = buildRequest(ENTITY_REF);
    EntityLinks updated = buildEntityLinks(ENTITY_REF);
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF))
        .thenReturn(Optional.of(existing));
    when(entityLinkRepository.save(any())).thenReturn(updated);

    EntityLinkResponse response = entityLinkService.updateLink(ACCOUNT_ID, ENTITY_REF, request);

    assertThat(response).isNotNull();
    assertThat(response.getEntityLink().getEntityRef()).isEqualTo(ENTITY_REF);
    verify(outboxService).save(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateLink_notFound_throwsException() {
    EntityLinkRequest request = buildRequest(ENTITY_REF);
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> entityLinkService.updateLink(ACCOUNT_ID, ENTITY_REF, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No entity link found");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateLink_invalidKind_throwsException() {
    EntityLinkRequest request = buildRequest("component:account/my-comp");

    assertThatThrownBy(() -> entityLinkService.updateLink(ACCOUNT_ID, "component:account/my-comp", request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("currently only supported for kind [workflow]");
  }

  // ── deleteLink ──────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteLink_withExistingLink_firesAuditEvent() {
    EntityLinks existing = buildEntityLinks(ENTITY_REF);
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF))
        .thenReturn(Optional.of(existing));
    doNothing().when(entityLinkRepository).deleteByAccountIdentifierAndEntityRef(anyString(), anyString());

    entityLinkService.deleteLink(ACCOUNT_ID, ENTITY_REF);

    verify(entityLinkRepository).deleteByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF);
    verify(outboxService).save(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteLink_withNoExistingLink_noAuditEvent() {
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF)).thenReturn(Optional.empty());
    doNothing().when(entityLinkRepository).deleteByAccountIdentifierAndEntityRef(anyString(), anyString());

    entityLinkService.deleteLink(ACCOUNT_ID, ENTITY_REF);

    verify(entityLinkRepository).deleteByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF);
    verify(outboxService, never()).save(any());
  }

  // ── getLink ─────────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLink_found() {
    EntityLinks entity = buildEntityLinks(ENTITY_REF);
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF))
        .thenReturn(Optional.of(entity));

    EntityLinkResponse response = entityLinkService.getLink(ACCOUNT_ID, ENTITY_REF);

    assertThat(response).isNotNull();
    assertThat(response.getEntityLink().getEntityRef()).isEqualTo(ENTITY_REF);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLink_notFound_returnsNull() {
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF)).thenReturn(Optional.empty());

    EntityLinkResponse response = entityLinkService.getLink(ACCOUNT_ID, ENTITY_REF);

    assertThat(response).isNull();
  }

  // ── linkExists ──────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testLinkExists_noLinkDoc_returnsFalse() {
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF)).thenReturn(Optional.empty());

    EntityLinkExistsResponse response = entityLinkService.linkExists(ACCOUNT_ID, ENTITY_REF);

    assertThat(response.isLinked()).isFalse();
    assertThat(response.getMatchingEntitiesCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testLinkExists_noTargets_returnsFalse() {
    EntityLinks link =
        EntityLinks.builder().accountIdentifier(ACCOUNT_ID).entityRef(ENTITY_REF).targets(List.of()).build();
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF))
        .thenReturn(Optional.of(link));

    EntityLinkExistsResponse response = entityLinkService.linkExists(ACCOUNT_ID, ENTITY_REF);

    assertThat(response.isLinked()).isFalse();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testLinkExists_withMatchingEntities_returnsLinkedTrue() {
    EntityLinks link = buildEntityLinks(ENTITY_REF);
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF))
        .thenReturn(Optional.of(link));
    when(catalogEntityRepository.countEntitiesByKindTypeAndScopes(eq(ACCOUNT_ID), any(), any())).thenReturn(3L);

    EntityLinkExistsResponse response = entityLinkService.linkExists(ACCOUNT_ID, ENTITY_REF);

    assertThat(response.isLinked()).isTrue();
    assertThat(response.getMatchingEntitiesCount()).isEqualTo(3);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testLinkExists_withScopeFilter_buildsTopology() {
    EntityLinks link = EntityLinks.builder()
                           .accountIdentifier(ACCOUNT_ID)
                           .entityRef(ENTITY_REF)
                           .scopes(List.of("account.default.myproject"))
                           .targets(List.of(LinkTarget.builder().entityKind("component").entityType("service").build()))
                           .build();
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF))
        .thenReturn(Optional.of(link));
    ScopeTopology topology = org.mockito.Mockito.mock(ScopeTopology.class);
    when(scopeResolver.getOrBuildTopology(ACCOUNT_ID)).thenReturn(topology);
    when(topology.resolveParentUniqueIds(anyString())).thenReturn(List.of("parent-uid-1"));
    when(catalogEntityRepository.countEntitiesByKindTypeAndScopes(eq(ACCOUNT_ID), any(), any())).thenReturn(1L);

    EntityLinkExistsResponse response = entityLinkService.linkExists(ACCOUNT_ID, ENTITY_REF);

    assertThat(response.isLinked()).isTrue();
    verify(scopeResolver).getOrBuildTopology(ACCOUNT_ID);
  }

  // ── getLinkedEntities ────────────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLinkedEntities_noLinks_returnsEmpty() {
    when(entityLinkRepository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service"))
        .thenReturn(List.of());

    List<String> result =
        entityLinkService.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:account/my-comp");

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLinkedEntities_entityNotFound_returnsEmpty() {
    EntityLinks link = buildEntityLinks(ENTITY_REF);
    when(entityLinkRepository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service"))
        .thenReturn(List.of(link));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.empty());

    List<String> result =
        entityLinkService.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:account/my-comp");

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLinkedEntities_noScopes_returnsApplicableLinks() {
    EntityLinks link = EntityLinks.builder()
                           .accountIdentifier(ACCOUNT_ID)
                           .entityRef(ENTITY_REF)
                           .scopes(null)
                           .targets(List.of(LinkTarget.builder().entityKind("component").entityType("service").build()))
                           .build();
    when(entityLinkRepository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service"))
        .thenReturn(List.of(link));
    CatalogEntity catalogEntity = InlineCatalogEntity.builder().parentUniqueId("parent-uid").build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.of(catalogEntity));
    ScopeTopology topology = org.mockito.Mockito.mock(ScopeTopology.class);
    when(topology.resolveParentUniqueIds(anyString())).thenReturn(List.of(PARENT_UID));
    when(scopeResolver.getOrBuildTopology(ACCOUNT_ID)).thenReturn(topology);

    List<String> result =
        entityLinkService.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:account/my-comp");

    assertThat(result).containsExactly(ENTITY_REF);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLinkedEntities_withScopeFilter_filtersOutOfScopeLinks() {
    EntityLinks link = EntityLinks.builder()
                           .accountIdentifier(ACCOUNT_ID)
                           .entityRef(ENTITY_REF)
                           .scopes(List.of("account.default.projectA"))
                           .targets(List.of(LinkTarget.builder().entityKind("component").entityType("service").build()))
                           .build();
    when(entityLinkRepository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service"))
        .thenReturn(List.of(link));
    CatalogEntity catalogEntity = InlineCatalogEntity.builder().parentUniqueId("different-parent").build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.of(catalogEntity));
    ScopeTopology topology = org.mockito.Mockito.mock(ScopeTopology.class);
    when(scopeResolver.getOrBuildTopology(ACCOUNT_ID)).thenReturn(topology);
    when(topology.resolveParentUniqueIds(anyString())).thenReturn(List.of("allowed-parent-uid"));

    List<String> result =
        entityLinkService.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:account/my-comp");

    assertThat(result).isEmpty();
  }

  // ── resolveFieldMappings ─────────────────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testResolveFieldMappings_entityNotFound_throwsException() {
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.empty());

    ResolveFieldMappingsRequest request = new ResolveFieldMappingsRequest();
    assertThatThrownBy(
        () -> entityLinkService.resolveFieldMappings(ACCOUNT_ID, "account", "component", "my-comp", request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Entity not found");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testResolveFieldMappings_noYaml_throwsException() {
    CatalogEntity entity = InlineCatalogEntity.builder().yaml(null).build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.of(entity));

    ResolveFieldMappingsRequest request = new ResolveFieldMappingsRequest();
    assertThatThrownBy(
        () -> entityLinkService.resolveFieldMappings(ACCOUNT_ID, "account", "component", "my-comp", request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("has no YAML content");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testResolveFieldMappings_emptyMappings_returnsEmptyList() {
    CatalogEntity entity = InlineCatalogEntity.builder().yaml("metadata:\n  name: my-comp\n").build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.of(entity));

    ResolveFieldMappingsRequest request = new ResolveFieldMappingsRequest();
    request.setFieldMappings(List.of());

    ResolveFieldMappingsResponse response =
        entityLinkService.resolveFieldMappings(ACCOUNT_ID, "account", "component", "my-comp", request);

    assertThat(response.getResolvedValues()).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testResolveFieldMappings_validPath_returnsValue() {
    CatalogEntity entity = InlineCatalogEntity.builder().yaml("metadata:\n  name: my-comp\n").build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.of(entity));

    io.harness.spec.server.idp.v1.model.FieldMapping mapping = new io.harness.spec.server.idp.v1.model.FieldMapping();
    mapping.setInput("myInput");
    mapping.setEntityFieldSource("metadata.name");
    ResolveFieldMappingsRequest request = new ResolveFieldMappingsRequest();
    request.setFieldMappings(List.of(mapping));

    ResolveFieldMappingsResponse response =
        entityLinkService.resolveFieldMappings(ACCOUNT_ID, "account", "component", "my-comp", request);

    assertThat(response.getResolvedValues()).hasSize(1);
    assertThat(response.getResolvedValues().get(0).getInput()).isEqualTo("myInput");
    assertThat(response.getResolvedValues().get(0).getValue()).isEqualTo("my-comp");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testResolveFieldMappings_missingPath_returnsNullValue() {
    CatalogEntity entity = InlineCatalogEntity.builder().yaml("metadata:\n  name: my-comp\n").build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.of(entity));

    io.harness.spec.server.idp.v1.model.FieldMapping mapping = new io.harness.spec.server.idp.v1.model.FieldMapping();
    mapping.setInput("myInput");
    mapping.setEntityFieldSource("spec.nonExistentField");
    ResolveFieldMappingsRequest request = new ResolveFieldMappingsRequest();
    request.setFieldMappings(List.of(mapping));

    ResolveFieldMappingsResponse response =
        entityLinkService.resolveFieldMappings(ACCOUNT_ID, "account", "component", "my-comp", request);

    assertThat(response.getResolvedValues()).hasSize(1);
    assertThat(response.getResolvedValues().get(0).getValue()).isNull();
  }

  // ── validateSupportedKind edge cases ────────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateLink_nullEntityRef_throwsException() {
    EntityLinkRequest request = buildRequest(null);

    assertThatThrownBy(() -> entityLinkService.createLink(ACCOUNT_ID, request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("currently only supported for kind [workflow]");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateLink_uppercaseWorkflowKind_isAccepted() {
    EntityLinkRequest request = buildRequest("WORKFLOW:account/my-workflow");
    EntityLinks savedEntity = buildEntityLinks("WORKFLOW:account/my-workflow");
    when(catalogServiceHelper.getOrgProjectFromEntityRef("WORKFLOW:account/my-workflow"))
        .thenReturn(Pair.of(ORG_ID, PROJECT_ID));
    when(entityLinkRepository.save(any())).thenReturn(savedEntity);

    EntityLinkResponse response = entityLinkService.createLink(ACCOUNT_ID, request);

    assertThat(response).isNotNull();
  }

  // ── linkExists: zero matching entities ─────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testLinkExists_zeroMatchingEntities_returnsFalse() {
    EntityLinks link = buildEntityLinks(ENTITY_REF);
    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF))
        .thenReturn(Optional.of(link));
    when(catalogEntityRepository.countEntitiesByKindTypeAndScopes(eq(ACCOUNT_ID), any(), any())).thenReturn(0L);

    EntityLinkExistsResponse response = entityLinkService.linkExists(ACCOUNT_ID, ENTITY_REF);

    assertThat(response.isLinked()).isFalse();
    assertThat(response.getMatchingEntitiesCount()).isEqualTo(0);
  }

  // ── isLinkApplicableForEntity: null targets ──────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLinkedEntities_linkWithNullTargets_notIncluded() {
    EntityLinks linkWithNullTargets =
        EntityLinks.builder().accountIdentifier(ACCOUNT_ID).entityRef(ENTITY_REF).targets(null).build();
    when(entityLinkRepository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service"))
        .thenReturn(List.of(linkWithNullTargets));
    CatalogEntity catalogEntity = InlineCatalogEntity.builder().parentUniqueId("parent-uid").build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.of(catalogEntity));
    ScopeTopology topology = org.mockito.Mockito.mock(ScopeTopology.class);
    when(topology.resolveParentUniqueIds(anyString())).thenReturn(List.of(PARENT_UID));
    when(scopeResolver.getOrBuildTopology(ACCOUNT_ID)).thenReturn(topology);

    List<String> result =
        entityLinkService.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:account/my-comp");

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLinkedEntities_targetKindMismatch_notIncluded() {
    EntityLinks link = EntityLinks.builder()
                           .accountIdentifier(ACCOUNT_ID)
                           .entityRef(ENTITY_REF)
                           .scopes(null)
                           .targets(List.of(LinkTarget.builder().entityKind("resource").entityType("database").build()))
                           .build();
    when(entityLinkRepository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service"))
        .thenReturn(List.of(link));
    CatalogEntity catalogEntity = InlineCatalogEntity.builder().parentUniqueId("parent-uid").build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.of(catalogEntity));
    ScopeTopology topology = org.mockito.Mockito.mock(ScopeTopology.class);
    when(topology.resolveParentUniqueIds(anyString())).thenReturn(List.of(PARENT_UID));
    when(scopeResolver.getOrBuildTopology(ACCOUNT_ID)).thenReturn(topology);

    List<String> result =
        entityLinkService.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:account/my-comp");

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLinkedEntities_scopeMatchesParent_included() {
    EntityLinks link = EntityLinks.builder()
                           .accountIdentifier(ACCOUNT_ID)
                           .entityRef(ENTITY_REF)
                           .scopes(List.of("account.default.projectA"))
                           .targets(List.of(LinkTarget.builder().entityKind("component").entityType("service").build()))
                           .build();
    when(entityLinkRepository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service"))
        .thenReturn(List.of(link));
    CatalogEntity catalogEntity = InlineCatalogEntity.builder().parentUniqueId("matching-parent").build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
             eq("matching-parent"), anyString(), anyString()))
        .thenReturn(Optional.of(catalogEntity));
    ScopeTopology topology = org.mockito.Mockito.mock(ScopeTopology.class);
    when(scopeResolver.getOrBuildTopology(ACCOUNT_ID)).thenReturn(topology);
    when(topology.resolveParentUniqueIds(anyString())).thenReturn(List.of("matching-parent", "other-parent"));

    List<String> result =
        entityLinkService.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:account/my-comp");

    assertThat(result).containsExactly(ENTITY_REF);
  }

  // ── resolveFieldMappings: multiple mappings ──────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testResolveFieldMappings_multipleMappings_allResolved() {
    CatalogEntity entity =
        InlineCatalogEntity.builder().yaml("metadata:\n  name: my-comp\n  description: a service\n").build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.of(entity));

    io.harness.spec.server.idp.v1.model.FieldMapping m1 = new io.harness.spec.server.idp.v1.model.FieldMapping();
    m1.setInput("inputName");
    m1.setEntityFieldSource("metadata.name");
    io.harness.spec.server.idp.v1.model.FieldMapping m2 = new io.harness.spec.server.idp.v1.model.FieldMapping();
    m2.setInput("inputDesc");
    m2.setEntityFieldSource("metadata.description");
    ResolveFieldMappingsRequest request = new ResolveFieldMappingsRequest();
    request.setFieldMappings(List.of(m1, m2));

    ResolveFieldMappingsResponse response =
        entityLinkService.resolveFieldMappings(ACCOUNT_ID, "account", "component", "my-comp", request);

    assertThat(response.getResolvedValues()).hasSize(2);
    assertThat(response.getResolvedValues().get(0).getValue()).isEqualTo("my-comp");
    assertThat(response.getResolvedValues().get(1).getValue()).isEqualTo("a service");
  }

  // ── updateLink: field values actually replaced ────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateLink_replacesTargetsAndFieldMappings() {
    EntityLinks existing = buildEntityLinks(ENTITY_REF);
    EntityLink newLink = new EntityLink();
    newLink.setEntityRef(ENTITY_REF);
    io.harness.spec.server.idp.v1.model.LinkTarget newTarget = new io.harness.spec.server.idp.v1.model.LinkTarget();
    newTarget.setEntityKind("resource");
    newTarget.setEntityType("database");
    newLink.setTargets(List.of(newTarget));
    EntityLinkRequest request = new EntityLinkRequest();
    request.setEntityLink(newLink);

    when(entityLinkRepository.findByAccountIdentifierAndEntityRef(ACCOUNT_ID, ENTITY_REF))
        .thenReturn(Optional.of(existing));
    when(entityLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    EntityLinkResponse response = entityLinkService.updateLink(ACCOUNT_ID, ENTITY_REF, request);

    assertThat(response.getEntityLink().getTargets()).hasSize(1);
    assertThat(response.getEntityLink().getTargets().get(0).getEntityKind()).isEqualTo("resource");
  }

  // ── lookupEntityByRef: unresolvable scope ────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLinkedEntities_unresolvableScope_returnsEmpty() {
    EntityLinks link = buildEntityLinks(ENTITY_REF);
    when(entityLinkRepository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service"))
        .thenReturn(List.of(link));
    when(mockTopology.resolveParentUniqueIds("unknown-scope")).thenReturn(List.of());

    List<String> result =
        entityLinkService.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:unknown-scope/my-comp");

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testResolveFieldMappings_unresolvableScope_throwsEntityNotFound() {
    when(mockTopology.resolveParentUniqueIds("unknown-scope")).thenReturn(List.of());

    ResolveFieldMappingsRequest request = new ResolveFieldMappingsRequest();
    assertThatThrownBy(
        () -> entityLinkService.resolveFieldMappings(ACCOUNT_ID, "unknown-scope", "component", "my-comp", request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Entity not found");
  }

  // ── lookupEntityByRef: wildcard scope rejected ──────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetLinkedEntities_wildcardScope_throwsException() {
    EntityLinks link = buildEntityLinks(ENTITY_REF);
    when(entityLinkRepository.findByAccountIdentifierAndTargetEntityKindAndType(ACCOUNT_ID, "component", "service"))
        .thenReturn(List.of(link));

    assertThatThrownBy(
        () -> entityLinkService.getLinkedEntities(ACCOUNT_ID, "component", "service", "component:account.*/my-comp"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("wildcards and comma-lists are not supported");
  }

  // ── extractValueFromPath: non-String value ────────────────────────────────────

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testResolveFieldMappings_numericYamlValue_returnedAsString() {
    CatalogEntity entity =
        InlineCatalogEntity.builder().yaml("metadata:\n  name: my-comp\nspec:\n  port: 8080\n").build();
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(eq(PARENT_UID), anyString(), anyString()))
        .thenReturn(Optional.of(entity));

    io.harness.spec.server.idp.v1.model.FieldMapping mapping = new io.harness.spec.server.idp.v1.model.FieldMapping();
    mapping.setInput("portInput");
    mapping.setEntityFieldSource("spec.port");
    ResolveFieldMappingsRequest request = new ResolveFieldMappingsRequest();
    request.setFieldMappings(List.of(mapping));

    ResolveFieldMappingsResponse response =
        entityLinkService.resolveFieldMappings(ACCOUNT_ID, "account", "component", "my-comp", request);

    assertThat(response.getResolvedValues()).hasSize(1);
    assertThat(response.getResolvedValues().get(0).getValue()).isEqualTo("8080");
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private EntityLinkRequest buildRequest(String entityRef) {
    EntityLink link = new EntityLink();
    link.setEntityRef(entityRef);
    link.setTargets(List.of(new io.harness.spec.server.idp.v1.model.LinkTarget() {
      {
        setEntityKind("component");
        setEntityType("service");
      }
    }));
    EntityLinkRequest request = new EntityLinkRequest();
    request.setEntityLink(link);
    return request;
  }

  private EntityLinks buildEntityLinks(String entityRef) {
    return EntityLinks.builder()
        .id("link-id")
        .accountIdentifier(ACCOUNT_ID)
        .entityRef(entityRef)
        .targets(List.of(LinkTarget.builder().entityKind("component").entityType("service").build()))
        .fieldMappings(List.of(FieldMapping.builder().input("myInput").entityFieldSource("metadata.name").build()))
        .build();
  }
}
