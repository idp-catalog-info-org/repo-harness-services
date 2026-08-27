/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.BulkFieldUpdateOperation;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.entities.OperationStatus;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.repositories.BulkFieldUpdateOperationRepository;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.events.producers.BulkFieldUpdateEventProducer;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BulkEntityFieldUpdateRequest;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateOperationResponse;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateProperty;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateSubmitResponse;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@OwnedBy(HarnessTeam.IDP)
public class BulkEntityFieldUpdateServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccount";
  private static final String ORG_ID = "testOrg";
  private static final String PROJECT_ID = "testProject";
  private static final String PARENT_UNIQUE_ID = "account.testOrg.testProject";
  private static final String ENTITY_IDENTIFIER = "entity1";
  private static final String NORMALIZED_OWNER = "user:account/owner1";
  private static final String ENTITY_REF = "component:account.testOrg.testProject/entity1";
  private static final String ENTITY_YAML =
      "apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: entity1\nspec: {}";

  @Mock private CatalogEntityRepository catalogEntityRepository;
  @Mock private CatalogServiceHelper catalogServiceHelper;
  @Mock private KindServiceHelper kindServiceHelper;
  @Mock private CatalogService catalogService;
  @Mock private BulkFieldUpdateOperationRepository operationRepository;
  @Mock private BulkFieldUpdateEventProducer eventProducer;
  @Mock private IDPGitXHelper idpGitXHelper;

  private BulkEntityFieldUpdateServiceImpl bulkUpdateService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    bulkUpdateService = new BulkEntityFieldUpdateServiceImpl(catalogEntityRepository, catalogServiceHelper,
        kindServiceHelper, catalogService, operationRepository, eventProducer, idpGitXHelper);
  }

  // ========== submit() tests ==========

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitFilterPathHappy() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("user:account/owner1");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    InlineCatalogEntity entity = buildInlineEntity(ENTITY_IDENTIFIER, "component");
    setupMocksForFilterPath(filter, List.of(entity), Set.of(ENTITY_REF), "component");

    BulkFieldUpdateOperation savedOp = BulkFieldUpdateOperation.builder()
                                           .id("op123")
                                           .accountIdentifier(ACCOUNT_ID)
                                           .status(OperationStatus.QUEUED)
                                           .matched(1)
                                           .permitted(1)
                                           .updated(0)
                                           .build();
    when(operationRepository.save(any(BulkFieldUpdateOperation.class))).thenReturn(savedOp);
    when(eventProducer.publish(anyString(), anyString())).thenReturn(true);

    BulkFieldUpdateSubmitResponse response = bulkUpdateService.submit(request, ACCOUNT_ID);

    assertThat(response.getOperationId()).isEqualTo("op123");
    assertThat(response.getStatus()).isEqualTo(OperationStatus.QUEUED.name());
    assertThat(response.getMatched()).isEqualTo(1);
    assertThat(response.getPermitted()).isEqualTo(1);

    verify(kindServiceHelper).validateKindIfExist(ACCOUNT_ID, "component");
    verify(catalogServiceHelper).checkEntityRefsPermission(ACCOUNT_ID, Set.of(ENTITY_REF), "edit");
    verify(catalogServiceHelper).resolveOwner(ACCOUNT_ID, "user:account/owner1");
    verify(catalogServiceHelper).validateOwnerScope(anyString(), eq(NORMALIZED_OWNER));
    verify(operationRepository).save(any(BulkFieldUpdateOperation.class));
    verify(eventProducer).publish("op123", ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitEntityRefsPathHappy() {
    List<String> entityRefs = List.of(ENTITY_REF);
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("user:account/owner1");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().entityRefs(entityRefs).properties(List.of(property));

    InlineCatalogEntity entity = buildInlineEntity(ENTITY_IDENTIFIER, "component");
    setupMocksForEntityRefsPath(entityRefs, List.of(entity), Set.of(ENTITY_REF));

    BulkFieldUpdateOperation savedOp = BulkFieldUpdateOperation.builder()
                                           .id("op123")
                                           .accountIdentifier(ACCOUNT_ID)
                                           .status(OperationStatus.QUEUED)
                                           .matched(1)
                                           .permitted(1)
                                           .build();
    when(operationRepository.save(any(BulkFieldUpdateOperation.class))).thenReturn(savedOp);
    when(eventProducer.publish(anyString(), anyString())).thenReturn(true);

    BulkFieldUpdateSubmitResponse response = bulkUpdateService.submit(request, ACCOUNT_ID);

    assertThat(response.getOperationId()).isEqualTo("op123");
    assertThat(response.getMatched()).isEqualTo(1);
    assertThat(response.getPermitted()).isEqualTo(1);
    verify(operationRepository).save(any(BulkFieldUpdateOperation.class));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitValidationBothFilterAndEntityRefs() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    List<String> entityRefs = List.of(ENTITY_REF);
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("value");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).entityRefs(entityRefs).properties(List.of(property));

    assertThatThrownBy(() -> bulkUpdateService.submit(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Exactly one of 'filter' or 'entityRefs' must be provided");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitValidationNeitherFilterNorEntityRefs() {
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("value");
    BulkEntityFieldUpdateRequest request = new BulkEntityFieldUpdateRequest().properties(List.of(property));

    assertThatThrownBy(() -> bulkUpdateService.submit(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Exactly one of 'filter' or 'entityRefs' must be provided");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitValidationEmptyProperties() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(Collections.emptyList());

    assertThatThrownBy(() -> bulkUpdateService.submit(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("properties' is required and must not be empty");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitValidationUnknownKey() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("unknownKey").value("value");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    assertThatThrownBy(() -> bulkUpdateService.submit(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unknown field key");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitValidationAppendModeNotSupported() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property =
        new BulkFieldUpdateProperty().key("owner").value("value").mode(BulkFieldUpdateProperty.ModeEnum.APPEND);
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    assertThatThrownBy(() -> bulkUpdateService.submit(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("APPEND mode is not yet supported");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitMatchedZeroReturnsSuccessImmediately() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("user:account/owner1");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    setupMocksForFilterPath(filter, Collections.emptyList(), Collections.emptySet(), "component");

    BulkFieldUpdateOperation savedOp = BulkFieldUpdateOperation.builder()
                                           .id("op123")
                                           .accountIdentifier(ACCOUNT_ID)
                                           .status(OperationStatus.SUCCESS)
                                           .matched(0)
                                           .permitted(0)
                                           .build();
    when(operationRepository.save(any(BulkFieldUpdateOperation.class))).thenReturn(savedOp);

    BulkFieldUpdateSubmitResponse response = bulkUpdateService.submit(request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS.name());
    assertThat(response.getMatched()).isEqualTo(0);
    assertThat(response.getPermitted()).isEqualTo(0);
    verify(eventProducer, never()).publish(anyString(), anyString());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitOwnerScopeValidationSkipsInvalidEntities() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("user:account.testOrg/owner1");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    InlineCatalogEntity validEntity = buildInlineEntity("entity1", "component");
    InlineCatalogEntity invalidEntity = InlineCatalogEntity.builder()
                                            .accountIdentifier(ACCOUNT_ID)
                                            .orgIdentifier("otherOrg")
                                            .projectIdentifier("otherProj")
                                            .kind("component")
                                            .identifier("entity2")
                                            .name("entity2")
                                            .referenceType(ReferenceType.INLINE)
                                            .yaml(ENTITY_YAML)
                                            .spec(new HashMap<>())
                                            .metadata(new HashMap<>())
                                            .build();

    setupMocksForFilterPath(
        filter, List.of(validEntity, invalidEntity), Set.of(buildEntityRef("component", "entity1")), "component");

    doNothing().when(catalogServiceHelper).validateOwnerScope("account.testOrg.testProject", NORMALIZED_OWNER);
    doThrow(new InvalidRequestException("Owner scope mismatch"))
        .when(catalogServiceHelper)
        .validateOwnerScope("account.otherOrg.otherProj", NORMALIZED_OWNER);

    BulkFieldUpdateOperation savedOp = BulkFieldUpdateOperation.builder()
                                           .id("op123")
                                           .accountIdentifier(ACCOUNT_ID)
                                           .status(OperationStatus.QUEUED)
                                           .matched(2)
                                           .permitted(1)
                                           .build();
    when(operationRepository.save(any(BulkFieldUpdateOperation.class))).thenReturn(savedOp);
    when(eventProducer.publish(anyString(), anyString())).thenReturn(true);

    BulkFieldUpdateSubmitResponse response = bulkUpdateService.submit(request, ACCOUNT_ID);

    assertThat(response.getMatched()).isEqualTo(2);
    assertThat(response.getPermitted()).isEqualTo(1);

    ArgumentCaptor<BulkFieldUpdateOperation> captor = ArgumentCaptor.forClass(BulkFieldUpdateOperation.class);
    verify(operationRepository).save(captor.capture());
    BulkFieldUpdateOperation op = captor.getValue();
    assertThat(op.getSkipped()).hasSize(1);
    assertThat(op.getSkipped().get(0).getReason()).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitOwnerScopeValidationAllInvalidThrows() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("user:account.testOrg/owner1");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    InlineCatalogEntity invalidEntity = InlineCatalogEntity.builder()
                                            .accountIdentifier(ACCOUNT_ID)
                                            .orgIdentifier("otherOrg")
                                            .projectIdentifier("otherProj")
                                            .kind("component")
                                            .identifier("entity1")
                                            .name("entity1")
                                            .referenceType(ReferenceType.INLINE)
                                            .yaml(ENTITY_YAML)
                                            .spec(new HashMap<>())
                                            .metadata(new HashMap<>())
                                            .build();

    setupMocksForFilterPath(filter, List.of(invalidEntity), Collections.emptySet(), "component");

    doThrow(new InvalidRequestException("Owner scope mismatch"))
        .when(catalogServiceHelper)
        .validateOwnerScope(anyString(), eq(NORMALIZED_OWNER));

    assertThatThrownBy(() -> bulkUpdateService.submit(request, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No matched entities are within a valid scope");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitRbacShortcutSingleOwnerPropertyGranted() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("group:account/owners");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    InlineCatalogEntity entity = buildInlineEntity(ENTITY_IDENTIFIER, "component");
    setupMocksForFilterPath(filter, List.of(entity), Set.of(ENTITY_REF), "component");

    // Owner resolves to a fully-qualified group ref so the team-edit shortcut applies.
    String ownerGroup = "group:account/owners";
    when(catalogServiceHelper.resolveOwner(eq(ACCOUNT_ID), anyString())).thenReturn(ownerGroup);
    doNothing().when(catalogServiceHelper).validateOwnerScope(anyString(), eq(ownerGroup));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_ID), eq(Set.of(ownerGroup)), eq("edit")))
        .thenReturn(Set.of(ownerGroup));

    BulkFieldUpdateOperation savedOp = BulkFieldUpdateOperation.builder()
                                           .id("op123")
                                           .accountIdentifier(ACCOUNT_ID)
                                           .status(OperationStatus.QUEUED)
                                           .matched(1)
                                           .permitted(1)
                                           .build();
    when(operationRepository.save(any(BulkFieldUpdateOperation.class))).thenReturn(savedOp);
    when(eventProducer.publish(anyString(), anyString())).thenReturn(true);

    BulkFieldUpdateSubmitResponse response = bulkUpdateService.submit(request, ACCOUNT_ID);

    assertThat(response.getPermitted()).isEqualTo(1);
    verify(catalogServiceHelper).checkEntityRefsPermission(ACCOUNT_ID, Set.of(ownerGroup), "edit");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitRbacShortcutDeniedFallsBackToPerEntityCheck() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("group:account/owners");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    InlineCatalogEntity entity = buildInlineEntity(ENTITY_IDENTIFIER, "component");
    // Per-entity permission grants ENTITY_REF (the intersection stub in the helper handles the entity-ref call).
    setupMocksForFilterPath(filter, List.of(entity), Set.of(ENTITY_REF), "component");

    // Owner resolves to a group ref so the shortcut is attempted; team-edit on the group is DENIED, so the flow
    // falls back to the regular per-entity edit check (which permits ENTITY_REF) instead of denying all.
    String ownerGroup = "group:account/owners";
    when(catalogServiceHelper.resolveOwner(eq(ACCOUNT_ID), anyString())).thenReturn(ownerGroup);
    doNothing().when(catalogServiceHelper).validateOwnerScope(anyString(), eq(ownerGroup));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_ID), eq(Set.of(ownerGroup)), eq("edit")))
        .thenReturn(Collections.emptySet());

    BulkFieldUpdateOperation savedOp = BulkFieldUpdateOperation.builder()
                                           .id("op123")
                                           .accountIdentifier(ACCOUNT_ID)
                                           .status(OperationStatus.QUEUED)
                                           .matched(1)
                                           .permitted(1)
                                           .build();
    when(operationRepository.save(any(BulkFieldUpdateOperation.class))).thenReturn(savedOp);
    when(eventProducer.publish(anyString(), anyString())).thenReturn(true);

    BulkFieldUpdateSubmitResponse response = bulkUpdateService.submit(request, ACCOUNT_ID);

    // Team-edit denied -> per-entity fallback permits the entity -> operation is created (no deny-all).
    assertThat(response.getOperationId()).isEqualTo("op123");
    assertThat(response.getPermitted()).isEqualTo(1);
    verify(catalogServiceHelper).checkEntityRefsPermission(ACCOUNT_ID, Set.of(ownerGroup), "edit");
    verify(catalogServiceHelper).checkEntityRefsPermission(ACCOUNT_ID, Set.of(ENTITY_REF), "edit");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitRbacShortcutDeniedAndNoPerEntityPermissionThrows() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("group:account/owners");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    InlineCatalogEntity entity = buildInlineEntity(ENTITY_IDENTIFIER, "component");
    // Per-entity permission grants NOTHING -> fallback denies all.
    setupMocksForFilterPath(filter, List.of(entity), Collections.emptySet(), "component");

    String ownerGroup = "group:account/owners";
    when(catalogServiceHelper.resolveOwner(eq(ACCOUNT_ID), anyString())).thenReturn(ownerGroup);
    doNothing().when(catalogServiceHelper).validateOwnerScope(anyString(), eq(ownerGroup));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_ID), eq(Set.of(ownerGroup)), eq("edit")))
        .thenReturn(Collections.emptySet());

    assertThatThrownBy(() -> bulkUpdateService.submit(request, ACCOUNT_ID))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("Missing Catalog Edit Permission");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitRbacPerEntityNoPermissionThrows() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("user:account/owner1");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    InlineCatalogEntity entity = buildInlineEntity(ENTITY_IDENTIFIER, "component");
    setupMocksForFilterPath(filter, List.of(entity), Collections.emptySet(), "component");

    assertThatThrownBy(() -> bulkUpdateService.submit(request, ACCOUNT_ID))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("Missing Catalog Edit Permission");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitRbacPerEntityPartialPermission() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("user:account/owner1");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(property));

    InlineCatalogEntity entity1 = buildInlineEntity("entity1", "component");
    InlineCatalogEntity entity2 = buildInlineEntity("entity2", "component");
    String entity1Ref = buildEntityRef("component", "entity1");
    String entity2Ref = buildEntityRef("component", "entity2");

    setupMocksForFilterPath(filter, List.of(entity1, entity2), Set.of(entity1Ref), "component");

    BulkFieldUpdateOperation savedOp = BulkFieldUpdateOperation.builder()
                                           .id("op123")
                                           .accountIdentifier(ACCOUNT_ID)
                                           .status(OperationStatus.QUEUED)
                                           .matched(2)
                                           .permitted(1)
                                           .build();
    when(operationRepository.save(any(BulkFieldUpdateOperation.class))).thenReturn(savedOp);
    when(eventProducer.publish(anyString(), anyString())).thenReturn(true);

    BulkFieldUpdateSubmitResponse response = bulkUpdateService.submit(request, ACCOUNT_ID);

    assertThat(response.getMatched()).isEqualTo(2);
    assertThat(response.getPermitted()).isEqualTo(1);

    ArgumentCaptor<BulkFieldUpdateOperation> captor = ArgumentCaptor.forClass(BulkFieldUpdateOperation.class);
    verify(operationRepository).save(captor.capture());
    BulkFieldUpdateOperation op = captor.getValue();
    assertThat(op.getSkipped()).hasSize(1);
    assertThat(op.getSkipped().get(0).getEntityRef()).isEqualTo(entity2Ref);
    assertThat(op.getSkipped().get(0).getReason()).isEqualTo("NO_PERMISSION");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitMultiPropertyDoesNotUseShortcut() {
    ScorecardFilter filter = new ScorecardFilter().kind("component");
    BulkFieldUpdateProperty prop1 = new BulkFieldUpdateProperty().key("owner").value("group:account/owners");
    BulkFieldUpdateProperty prop2 = new BulkFieldUpdateProperty().key("owner").value("group:account/team");
    BulkEntityFieldUpdateRequest request =
        new BulkEntityFieldUpdateRequest().filter(filter).properties(List.of(prop1, prop2));

    InlineCatalogEntity entity = buildInlineEntity(ENTITY_IDENTIFIER, "component");
    setupMocksForFilterPath(filter, List.of(entity), Set.of(ENTITY_REF), "component");

    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_ID), any(Set.class), eq("edit")))
        .thenReturn(Set.of(ENTITY_REF));

    BulkFieldUpdateOperation savedOp = BulkFieldUpdateOperation.builder()
                                           .id("op123")
                                           .accountIdentifier(ACCOUNT_ID)
                                           .status(OperationStatus.QUEUED)
                                           .matched(1)
                                           .permitted(1)
                                           .build();
    when(operationRepository.save(any(BulkFieldUpdateOperation.class))).thenReturn(savedOp);
    when(eventProducer.publish(anyString(), anyString())).thenReturn(true);

    BulkFieldUpdateSubmitResponse response = bulkUpdateService.submit(request, ACCOUNT_ID);

    assertThat(response.getPermitted()).isEqualTo(1);
    verify(catalogServiceHelper).checkEntityRefsPermission(eq(ACCOUNT_ID), eq(Set.of(ENTITY_REF)), eq("edit"));
  }

  // ========== getOperation() tests ==========

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetOperationHappy() {
    String operationId = "op123";
    BulkFieldUpdateOperation operation =
        BulkFieldUpdateOperation.builder()
            .id(operationId)
            .accountIdentifier(ACCOUNT_ID)
            .status(OperationStatus.SUCCESS)
            .matched(10)
            .permitted(8)
            .updated(7)
            .skipped(List.of(
                BulkFieldUpdateOperation.SkippedItem.builder().entityRef("ref1").reason("NO_PERMISSION").build()))
            .errors(
                List.of(BulkFieldUpdateOperation.ErrorItem.builder().entityRef("ref2").errorMessage("error").build()))
            .errorMessage("Some error")
            .build();
    when(operationRepository.findByIdAndAccountIdentifier(operationId, ACCOUNT_ID)).thenReturn(Optional.of(operation));

    BulkFieldUpdateOperationResponse response = bulkUpdateService.getOperation(ACCOUNT_ID, operationId);

    assertThat(response.getOperationId()).isEqualTo(operationId);
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS.name());
    assertThat(response.getMatched()).isEqualTo(10);
    assertThat(response.getPermitted()).isEqualTo(8);
    assertThat(response.getUpdated()).isEqualTo(7);
    assertThat(response.getSkipped()).hasSize(1);
    assertThat(response.getErrors()).hasSize(1);
    assertThat(response.getErrorMessage()).isEqualTo("Some error");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetOperationNotFound() {
    String operationId = "op123";
    when(operationRepository.findByIdAndAccountIdentifier(operationId, ACCOUNT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> bulkUpdateService.getOperation(ACCOUNT_ID, operationId))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("operation not found");
  }

  // ========== execute() tests ==========

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testExecuteHappyPathAllSuccess() {
    String operationId = "op123";
    BulkFieldUpdateOperation.PropertyUpdate propUpdate =
        BulkFieldUpdateOperation.PropertyUpdate.builder().key("owner").value(NORMALIZED_OWNER).mode("REPLACE").build();
    BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                             .id(operationId)
                                             .accountIdentifier(ACCOUNT_ID)
                                             .status(OperationStatus.PROCESSING)
                                             .matched(1)
                                             .permitted(1)
                                             .updated(0)
                                             .permittedEntityRefs(List.of(ENTITY_REF))
                                             .properties(List.of(propUpdate))
                                             .errors(new ArrayList<>())
                                             .build();
    when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));

    when(catalogServiceHelper.getKindScopeIdentifier(ENTITY_REF))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("component", "account.testOrg.testProject", "entity1"));

    EntityResponse entityResponse =
        new EntityResponse().yaml(ENTITY_YAML).orgIdentifier(ORG_ID).projectIdentifier(PROJECT_ID).gitDetails(null);
    when(catalogService.getEntity(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENTITY_REF), eq(false), eq(false), eq(true), eq(false)))
        .thenReturn(entityResponse);

    bulkUpdateService.execute(operationId);

    ArgumentCaptor<BulkFieldUpdateOperation> captor = ArgumentCaptor.forClass(BulkFieldUpdateOperation.class);
    verify(operationRepository).save(captor.capture());
    BulkFieldUpdateOperation saved = captor.getValue();
    assertThat(saved.getUpdated()).isEqualTo(1);
    assertThat(saved.getErrors()).isEmpty();
    assertThat(saved.getStatus()).isEqualTo(OperationStatus.SUCCESS);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testExecutePartialSuccessWithErrors() {
    String operationId = "op123";
    String entityRef1 = "component:account.testOrg.testProject/entity1";
    String entityRef2 = "component:account.testOrg.testProject/entity2";

    BulkFieldUpdateOperation.PropertyUpdate propUpdate =
        BulkFieldUpdateOperation.PropertyUpdate.builder().key("owner").value(NORMALIZED_OWNER).mode("REPLACE").build();
    BulkFieldUpdateOperation operation = BulkFieldUpdateOperation.builder()
                                             .id(operationId)
                                             .accountIdentifier(ACCOUNT_ID)
                                             .status(OperationStatus.PROCESSING)
                                             .matched(2)
                                             .permitted(2)
                                             .updated(0)
                                             .permittedEntityRefs(List.of(entityRef1, entityRef2))
                                             .properties(List.of(propUpdate))
                                             .errors(new ArrayList<>())
                                             .build();
    when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));

    when(catalogServiceHelper.getKindScopeIdentifier(entityRef1))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("component", "account.testOrg.testProject", "entity1"));
    when(catalogServiceHelper.getKindScopeIdentifier(entityRef2))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("component", "account.testOrg.testProject", "entity2"));

    EntityResponse entityResponse1 =
        new EntityResponse().yaml(ENTITY_YAML).orgIdentifier(ORG_ID).projectIdentifier(PROJECT_ID).gitDetails(null);
    when(catalogService.getEntity(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(entityRef1), eq(false), eq(false), eq(true), eq(false)))
        .thenReturn(entityResponse1);

    when(catalogService.getEntity(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(entityRef2), eq(false), eq(false), eq(true), eq(false)))
        .thenThrow(new RuntimeException("Entity not found"));

    bulkUpdateService.execute(operationId);

    ArgumentCaptor<BulkFieldUpdateOperation> captor = ArgumentCaptor.forClass(BulkFieldUpdateOperation.class);
    verify(operationRepository).save(captor.capture());
    BulkFieldUpdateOperation saved = captor.getValue();
    assertThat(saved.getUpdated()).isEqualTo(1);
    assertThat(saved.getErrors()).hasSize(1);
    assertThat(saved.getErrors().get(0).getEntityRef()).isEqualTo(entityRef2);
    assertThat(saved.getStatus()).isEqualTo(OperationStatus.PARTIAL_SUCCESS);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testExecuteOperationNotFound() {
    String operationId = "op123";
    when(operationRepository.findById(operationId)).thenReturn(Optional.empty());

    bulkUpdateService.execute(operationId);

    verify(catalogService, never())
        .getEntity(anyString(), anyString(), anyString(), anyString(), eq(false), eq(false), eq(true), eq(false));
  }

  // ========== Helper methods ==========

  private InlineCatalogEntity buildInlineEntity(String identifier, String kind) {
    return InlineCatalogEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .parentUniqueId(PARENT_UNIQUE_ID)
        .kind(kind)
        .identifier(identifier)
        .name(identifier)
        .referenceType(ReferenceType.INLINE)
        .yaml(ENTITY_YAML)
        .spec(new HashMap<>())
        .metadata(new HashMap<>())
        .build();
  }

  private String buildEntityRef(String kind, String identifier) {
    return kind + ":account." + ORG_ID + "." + PROJECT_ID + "/" + identifier;
  }

  private ScopeInfo buildScopeInfo() {
    return ScopeInfo.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .uniqueId(PARENT_UNIQUE_ID)
        .build();
  }

  private void setupMocksForFilterPath(
      ScorecardFilter filter, List<InlineCatalogEntity> entities, Set<String> permittedRefs, String remappedKind) {
    doNothing().when(kindServiceHelper).validateKindIfExist(eq(ACCOUNT_ID), eq(remappedKind));

    ScopeInfo scopeInfo = buildScopeInfo();
    when(catalogServiceHelper.getAllScopes()).thenReturn("account,account/testOrg,account/testOrg/testProject");
    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(eq(ACCOUNT_ID), anyString(), eq(null)))
        .thenReturn(Pair.of(List.of(scopeInfo), Collections.emptyMap()));

    Page<InlineCatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());
    when(catalogEntityRepository.getEntities(eq(ACCOUNT_ID), any(), any(), eq(1000), eq(null), eq(null), eq(null),
             eq(null), eq(remappedKind), any(), any(), any(), any(), eq(null), eq(null)))
        .thenReturn((Page) page);

    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_ID), any(), eq("edit"))).thenAnswer(invocation -> {
      Set<String> requested = invocation.getArgument(1);
      Set<String> result = new HashSet<>(requested);
      result.retainAll(permittedRefs);
      return result;
    });

    when(catalogServiceHelper.resolveOwner(eq(ACCOUNT_ID), anyString())).thenReturn(NORMALIZED_OWNER);
    doNothing().when(catalogServiceHelper).validateOwnerScope(anyString(), eq(NORMALIZED_OWNER));
  }

  private void setupMocksForEntityRefsPath(
      List<String> entityRefs, List<InlineCatalogEntity> entities, Set<String> permittedRefs) {
    ScopeInfo scopeInfo = buildScopeInfo();
    when(catalogServiceHelper.getAllScopes()).thenReturn("account,account/testOrg,account/testOrg/testProject");
    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(
             eq(ACCOUNT_ID), anyString(), eq(String.join(",", entityRefs))))
        .thenReturn(Pair.of(List.of(scopeInfo), Collections.emptyMap()));

    Page<InlineCatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 10), entities.size());
    when(catalogEntityRepository.getEntities(eq(ACCOUNT_ID), any(), eq(null), eq(-1), eq(null), eq(null), eq(null),
             eq(String.join(",", entityRefs)), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null)))
        .thenReturn((Page) page);

    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_ID), any(), eq("edit"))).thenAnswer(invocation -> {
      Set<String> requested = invocation.getArgument(1);
      Set<String> result = new HashSet<>(requested);
      result.retainAll(permittedRefs);
      return result;
    });

    when(catalogServiceHelper.resolveOwner(eq(ACCOUNT_ID), anyString())).thenReturn(NORMALIZED_OWNER);
    doNothing().when(catalogServiceHelper).validateOwnerScope(anyString(), eq(NORMALIZED_OWNER));
  }
}
