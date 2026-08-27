/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.catalog.resources;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.service.CatalogTableService;
import io.harness.idp.catalog.utils.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.EntityResponse;

import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Parity tests asserting that GET /v1/entities (EntitiesApiImpl.getEntities) produces
 * an identical response whether it goes through the old V1 path (entityRefs non-empty,
 * handled by CatalogServiceImpl.getEntities) or the new V2 path (entityRefs empty/null,
 * handled by CatalogServiceV2Impl.getEntitiesV2 with the V1_DEFAULT_KIND_FILTER applied).
 *
 * Each test scenario creates the same GetEntitiesDTO payload, stubs it on both service
 * methods under their respective call conditions, and asserts that the HTTP response
 * (status, entity list, pagination metadata) is identical.
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GetEntitiesV1ParityTest extends CategoryTest {
  private static final String ACCOUNT = "test-account";

  // V1_DEFAULT_KIND_FILTER — must stay in sync with EntitiesApiImpl.V1_DEFAULT_KIND_FILTER
  private static final String V1_DEFAULT_KIND_FILTER =
      Constants.SUPPORTED_KINDS.stream()
          .filter(k
              -> !k.equals("workflow") && !k.equals("environment") && !k.equals("environmentblueprint")
                  && !k.equals("group"))
          .collect(Collectors.joining(","));

  @Mock CatalogService catalogService;
  @Mock IdpCommonService idpCommonService;
  @Mock IDPGitXHelper idpGitXHelper;
  @Mock CatalogTableService catalogTableService;

  @InjectMocks EntitiesApiImpl entitiesApi;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private static EntityResponse entityResponse(String identifier) {
    EntityResponse r = new EntityResponse();
    r.setIdentifier(identifier);
    return r;
  }

  private static GetEntitiesDTO dto(List<EntityResponse> entities, long total, long owned, long starred) {
    return GetEntitiesDTO.builder()
        .pageNumber(0)
        .totalElements(total)
        .entityResponses(entities)
        .totalOwned(owned)
        .totalStarred(starred)
        .build();
  }

  /**
   * Stubs the V1 path (CatalogServiceImpl.getEntities) for a call with the given entityRefs.
   * entityRefs must be non-empty to route through V1.
   */
  private void stubV1(String entityRefs, String kind, GetEntitiesDTO result) {
    when(catalogService.getEntities(eq(ACCOUNT), eq(0), eq(10), isNull(), isNull(), eq(false), isNull(), eq(entityRefs),
             isNull(), isNull(), eq(kind), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true)))
        .thenReturn(result);
  }

  /**
   * Stubs the V2 path (CatalogServiceV2Impl.getEntitiesV2) for a call with null entityRefs.
   * The kind passed to V2 is V1_DEFAULT_KIND_FILTER when the caller provides no kind,
   * or the caller-supplied kind when one is given — matching EntitiesApiImpl.V1_DEFAULT_KIND_FILTER logic.
   * Also enables the IDP_ENTITY_LIST_OPTIMIZED_PATH feature flag so the redirect fires.
   */
  private void stubV2(String effectiveKind, GetEntitiesDTO result) {
    when(idpCommonService.idpEntityListOptimizedPathEnabled(ACCOUNT)).thenReturn(true);
    when(catalogService.getEntitiesV2(eq(ACCOUNT), eq(0), eq(10), isNull(), isNull(), eq(false), isNull(), isNull(),
             isNull(), isNull(), eq(effectiveKind), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true),
             eq(false), isNull()))
        .thenReturn(result);
  }

  // ─── tests ────────────────────────────────────────────────────────────────

  /**
   * Scenario 1: Basic entity list — no filters, no kind.
   * V1: entityRefs = "component:account/comp1" → goes through CatalogServiceImpl.getEntities
   * V2: entityRefs = null                       → goes through CatalogServiceV2Impl.getEntitiesV2
   * Both should return the same entity list, pagination counts, and status.
   */
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testParity_BasicEntityList_NoFilters() {
    List<EntityResponse> entities = List.of(entityResponse("comp1"), entityResponse("comp2"));
    GetEntitiesDTO expected = dto(entities, 2, 0, 0);

    // V1 path: called when entityRefs is non-empty
    stubV1("component:account/comp1", null, expected);
    Response v1Response = entitiesApi.getEntities(ACCOUNT, 0, 10, null, null, false, null, "component:account/comp1",
        null, null, null, null, null, null, null, null);

    // V2 path: called when entityRefs is null (post-refactoring default)
    stubV2(V1_DEFAULT_KIND_FILTER, expected);
    Response v2Response = entitiesApi.getEntities(
        ACCOUNT, 0, 10, null, null, false, null, null, null, null, null, null, null, null, null, null);

    assertThat(v1Response.getStatus()).isEqualTo(v2Response.getStatus());
    assertThat(v1Response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(v1Response.getEntity()).isEqualTo(v2Response.getEntity());
    assertThat(((List<?>) v1Response.getEntity())).hasSize(2);
  }

  /**
   * Scenario 2: Pagination metadata — totalElements, totalOwned, totalStarred must be identical.
   */
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testParity_PaginationMetadata_Consistent() {
    List<EntityResponse> entities = List.of(entityResponse("svc1"));
    GetEntitiesDTO expected = dto(entities, 42, 5, 3);

    stubV1("component:account/svc1", null, expected);
    Response v1Response = entitiesApi.getEntities(ACCOUNT, 0, 10, null, null, false, null, "component:account/svc1",
        null, null, null, null, null, null, null, null);

    stubV2(V1_DEFAULT_KIND_FILTER, expected);
    Response v2Response = entitiesApi.getEntities(
        ACCOUNT, 0, 10, null, null, false, null, null, null, null, null, null, null, null, null, null);

    // Both responses should carry the same pagination header values
    assertThat(v1Response.getHeaderString("Total-Owned")).isEqualTo(v2Response.getHeaderString("Total-Owned"));
    assertThat(v1Response.getHeaderString("Total-Starred")).isEqualTo(v2Response.getHeaderString("Total-Starred"));
    assertThat(v1Response.getEntity()).isEqualTo(v2Response.getEntity());
  }

  /**
   * Scenario 3: Empty result set — both paths must return 200 with an empty list (not 404 or error).
   */
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testParity_EmptyResults_BothReturn200WithEmptyList() {
    GetEntitiesDTO expected = dto(List.of(), 0, 0, 0);

    stubV1("component:account/missing", null, expected);
    Response v1Response = entitiesApi.getEntities(ACCOUNT, 0, 10, null, null, false, null, "component:account/missing",
        null, null, null, null, null, null, null, null);

    stubV2(V1_DEFAULT_KIND_FILTER, expected);
    Response v2Response = entitiesApi.getEntities(
        ACCOUNT, 0, 10, null, null, false, null, null, null, null, null, null, null, null, null, null);

    assertThat(v1Response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(v2Response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat((List<?>) v1Response.getEntity()).isEmpty();
    assertThat((List<?>) v2Response.getEntity()).isEmpty();
  }

  /**
   * Scenario 4: Explicit kind provided by caller — kind is passed through unchanged to both paths.
   * When kind is non-null, V2 must receive the exact caller-supplied kind (not V1_DEFAULT_KIND_FILTER).
   */
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testParity_ExplicitKindProvided_PassedThroughUnchanged() {
    String explicitKind = "component";
    List<EntityResponse> entities = List.of(entityResponse("comp1"));
    GetEntitiesDTO expected = dto(entities, 1, 0, 0);

    // V1: entityRefs non-empty, kind = "component"
    stubV1("component:account/comp1", explicitKind, expected);
    Response v1Response = entitiesApi.getEntities(ACCOUNT, 0, 10, null, null, false, null, "component:account/comp1",
        null, null, explicitKind, null, null, null, null, null);

    // V2: entityRefs null, kind = "component" (non-null so V1_DEFAULT_KIND_FILTER is NOT applied)
    when(idpCommonService.idpEntityListOptimizedPathEnabled(ACCOUNT)).thenReturn(true);
    when(catalogService.getEntitiesV2(eq(ACCOUNT), eq(0), eq(10), isNull(), isNull(), eq(false), isNull(), isNull(),
             isNull(), isNull(), eq(explicitKind), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true),
             eq(false), isNull()))
        .thenReturn(expected);
    Response v2Response = entitiesApi.getEntities(
        ACCOUNT, 0, 10, null, null, false, null, null, null, null, explicitKind, null, null, null, null, null);

    assertThat(v1Response.getStatus()).isEqualTo(v2Response.getStatus());
    assertThat(v1Response.getEntity()).isEqualTo(v2Response.getEntity());
    assertThat(((List<?>) v1Response.getEntity())).hasSize(1);
  }

  /**
   * Scenario 5: favorites=true filter — both paths must honor the favorites filter equally.
   * When favorites=true and there are starred entities, both return only the starred set.
   */
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testParity_FavoritesFilter_OnlyStarredEntitiesReturned() {
    List<EntityResponse> starredEntities = List.of(entityResponse("starred1"));
    GetEntitiesDTO expected = dto(starredEntities, 1, 0, 1);

    // V1 with favorites=true and entityRefs non-empty
    when(catalogService.getEntities(eq(ACCOUNT), eq(0), eq(10), isNull(), isNull(), eq(false), isNull(),
             eq("component:account/comp1"), isNull(), eq(true), isNull(), isNull(), isNull(), isNull(), isNull(),
             isNull(), eq(true)))
        .thenReturn(expected);
    Response v1Response = entitiesApi.getEntities(ACCOUNT, 0, 10, null, null, false, null, "component:account/comp1",
        null, true, null, null, null, null, null, null);

    // V2 with favorites=true and entityRefs null
    when(idpCommonService.idpEntityListOptimizedPathEnabled(ACCOUNT)).thenReturn(true);
    when(catalogService.getEntitiesV2(eq(ACCOUNT), eq(0), eq(10), isNull(), isNull(), eq(false), isNull(), isNull(),
             isNull(), eq(true), eq(V1_DEFAULT_KIND_FILTER), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true),
             eq(false), isNull()))
        .thenReturn(expected);
    Response v2Response = entitiesApi.getEntities(
        ACCOUNT, 0, 10, null, null, false, null, null, null, true, null, null, null, null, null, null);

    assertThat(v1Response.getStatus()).isEqualTo(v2Response.getStatus());
    assertThat(v1Response.getEntity()).isEqualTo(v2Response.getEntity());
    assertThat(v1Response.getHeaderString("Total-Starred")).isEqualTo(v2Response.getHeaderString("Total-Starred"));
  }

  /**
   * Scenario 6: ownedByMe=true filter — both paths must apply the owned filter identically.
   */
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testParity_OwnedByMeFilter_SameEntitiesReturned() {
    List<EntityResponse> ownedEntities = List.of(entityResponse("owned1"), entityResponse("owned2"));
    GetEntitiesDTO expected = dto(ownedEntities, 2, 2, 0);

    // V1 with ownedByMe=true and entityRefs non-empty
    when(catalogService.getEntities(eq(ACCOUNT), eq(0), eq(10), isNull(), isNull(), eq(false), isNull(),
             eq("component:account/owned1"), eq(true), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
             isNull(), eq(true)))
        .thenReturn(expected);
    Response v1Response = entitiesApi.getEntities(ACCOUNT, 0, 10, null, null, false, null, "component:account/owned1",
        true, null, null, null, null, null, null, null);

    // V2 with ownedByMe=true and entityRefs null
    when(idpCommonService.idpEntityListOptimizedPathEnabled(ACCOUNT)).thenReturn(true);
    when(catalogService.getEntitiesV2(eq(ACCOUNT), eq(0), eq(10), isNull(), isNull(), eq(false), isNull(), isNull(),
             eq(true), isNull(), eq(V1_DEFAULT_KIND_FILTER), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true),
             eq(false), isNull()))
        .thenReturn(expected);
    Response v2Response = entitiesApi.getEntities(
        ACCOUNT, 0, 10, null, null, false, null, null, true, null, null, null, null, null, null, null);

    assertThat(v1Response.getStatus()).isEqualTo(v2Response.getStatus());
    assertThat(v1Response.getEntity()).isEqualTo(v2Response.getEntity());
    assertThat(v1Response.getHeaderString("Total-Owned")).isEqualTo(v2Response.getHeaderString("Total-Owned"));
  }

  /**
   * Scenario 7: Default page size applied consistently — limit=null must default to 10 in both paths.
   */
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testParity_DefaultPageSize_AppliedToBothPaths() {
    List<EntityResponse> entities = List.of(entityResponse("comp1"));
    GetEntitiesDTO expected = dto(entities, 1, 0, 0);

    // V1: limit null → pageLimit=10
    when(catalogService.getEntities(eq(ACCOUNT), isNull(), eq(10), isNull(), isNull(), eq(false), isNull(),
             eq("component:account/comp1"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
             isNull(), eq(true)))
        .thenReturn(expected);
    Response v1Response = entitiesApi.getEntities(ACCOUNT, null, null, null, null, false, null,
        "component:account/comp1", null, null, null, null, null, null, null, null);

    // V2: limit null → pageLimit=10
    when(idpCommonService.idpEntityListOptimizedPathEnabled(ACCOUNT)).thenReturn(true);
    when(catalogService.getEntitiesV2(eq(ACCOUNT), isNull(), eq(10), isNull(), isNull(), eq(false), isNull(), isNull(),
             isNull(), isNull(), eq(V1_DEFAULT_KIND_FILTER), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true),
             eq(false), isNull()))
        .thenReturn(expected);
    Response v2Response = entitiesApi.getEntities(
        ACCOUNT, null, null, null, null, false, null, null, null, null, null, null, null, null, null, null);

    assertThat(v1Response.getStatus()).isEqualTo(v2Response.getStatus());
    assertThat(v1Response.getEntity()).isEqualTo(v2Response.getEntity());
  }
}
