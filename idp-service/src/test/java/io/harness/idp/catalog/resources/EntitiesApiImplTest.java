/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.catalog.resources;

import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.ROUNAK;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.processor.api.ApiEndpointSyncFailedException;
import io.harness.idp.catalog.processor.api.ApiEndpointSyncInProgressException;
import io.harness.idp.catalog.service.ApiEndpointSyncService;
import io.harness.idp.catalog.service.BulkEntityFieldUpdateService;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.service.CatalogTableService;
import io.harness.idp.catalog.utils.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ApiEndpointSyncResponse;
import io.harness.spec.server.idp.v1.model.BulkEntityFieldUpdateRequest;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateOperationResponse;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateProperty;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateSubmitResponse;
import io.harness.spec.server.idp.v1.model.CatalogSyncRequest;
import io.harness.spec.server.idp.v1.model.EntitiesByRefsRequest;
import io.harness.spec.server.idp.v1.model.EntityConvertResponse;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityFiltersResponse;
import io.harness.spec.server.idp.v1.model.EntityKindsResponse;
import io.harness.spec.server.idp.v1.model.EntityRequest;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityTableCreateOrUpdateRequest;
import io.harness.spec.server.idp.v1.model.EntityTableResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import java.util.Collections;
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

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class EntitiesApiImplTest extends CategoryTest {
  @Mock IdpCommonService idpCommonService;

  @Mock CatalogService catalogService;
  @Mock CatalogTableService catalogTableService;
  @Mock IDPGitXHelper idpGitXHelper;
  @Mock ApiEndpointSyncService apiEndpointSyncService;
  @Mock BulkEntityFieldUpdateService bulkEntityFieldUpdateService;

  @InjectMocks EntitiesApiImpl entitiesApi;

  AutoCloseable openMocks;
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String OPTION = "backstage-to-harness";
  private static final String TEST_IDENTIFIER = "test-identifier";
  private static final String V1_DEFAULT_KIND_FILTER =
      Constants.SUPPORTED_KINDS.stream()
          .filter(k
              -> !k.equals("workflow") && !k.equals("environment") && !k.equals("environmentblueprint")
                  && !k.equals("group"))
          .collect(Collectors.joining(","));

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void syncCatalogEntities() {
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT_ID)).thenReturn(true);
    doNothing().when(idpCommonService).checkUserAuthorization();
    entitiesApi.syncCatalogEntities(OPTION, null, TEST_ACCOUNT_ID);
    verify(catalogService, times(1)).syncCatalogEntities(TEST_ACCOUNT_ID, OPTION, null);

    CatalogSyncRequest catalogSyncRequest = new CatalogSyncRequest();
    catalogSyncRequest.setIdentifier(TEST_IDENTIFIER);
    catalogSyncRequest.setAction(CatalogSyncRequest.ActionEnum.CREATE);

    entitiesApi.syncCatalogEntities(OPTION, catalogSyncRequest, TEST_ACCOUNT_ID);
    verify(catalogService, times(1)).syncCatalogEntities(TEST_ACCOUNT_ID, OPTION, catalogSyncRequest);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testConvertEntity() {
    EntityRequest entityRequest = new EntityRequest();
    EntityConvertResponse entityConvertResponse = new EntityConvertResponse();

    when(catalogService.convertEntity(TEST_ACCOUNT_ID, "option", entityRequest, null, false))
        .thenReturn(entityConvertResponse);
    Response response = entitiesApi.convertEntity(entityRequest, "option", TEST_ACCOUNT_ID, null, null, null, null);
    verify(catalogService, times(1)).convertEntity(TEST_ACCOUNT_ID, "option", entityRequest, null, false);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(entityConvertResponse);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCreateEntity() {
    EntityCreateRequest entityRequest = new EntityCreateRequest();
    EntityResponse entityResponse = new EntityResponse();

    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    when(catalogService.createEntity(eq(TEST_ACCOUNT_ID), eq(null), eq(null), eq(true), eq(false), eq(entityRequest)))
        .thenReturn(entityResponse);
    Response response = entitiesApi.createEntity(entityRequest, TEST_ACCOUNT_ID, null, null, true, false);
    verify(catalogService, times(1)).createEntity(TEST_ACCOUNT_ID, null, null, true, false, entityRequest);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(entityResponse);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCreateEntityWithIDPV2Disabled() {
    EntityCreateRequest entityRequest = new EntityCreateRequest();
    doThrow(new InvalidRequestException("Account not enabled for IDP 2.0"))
        .when(idpCommonService)
        .idpV2Check(TEST_ACCOUNT_ID);
    entitiesApi.createEntity(entityRequest, TEST_ACCOUNT_ID, null, null, true, false);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testDeleteEntity() {
    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    doNothing()
        .when(catalogService)
        .deleteEntity(eq(TEST_ACCOUNT_ID), anyString(), anyString(), anyString(), anyBoolean());
    Response response = entitiesApi.deleteEntity("scope", "component", "entity", null, null, TEST_ACCOUNT_ID, false);
    verify(catalogService, times(1)).deleteEntity(TEST_ACCOUNT_ID, null, null, "component:scope/entity", false);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testDeleteEntityWithIDPV2Disabled() {
    doThrow(new InvalidRequestException("Account not enabled for IDP 2.0"))
        .when(idpCommonService)
        .idpV2Check(TEST_ACCOUNT_ID);
    entitiesApi.deleteEntity("component", "scope", "entity", null, null, TEST_ACCOUNT_ID, false);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateEntity() {
    EntityUpdateRequest entityRequest = new EntityUpdateRequest();
    EntityResponse entityResponse = new EntityResponse();

    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    when(catalogService.updateEntity(
             eq(TEST_ACCOUNT_ID), eq(null), eq(null), eq("component:scope/entity"), eq(entityRequest)))
        .thenReturn(entityResponse);
    Response response =
        entitiesApi.updateEntity(entityRequest, "scope", "component", "entity", TEST_ACCOUNT_ID, null, null);
    verify(catalogService, times(1)).updateEntity(TEST_ACCOUNT_ID, null, null, "component:scope/entity", entityRequest);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(entityResponse);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateEntityWithIDPV2Disabled() {
    EntityUpdateRequest entityRequest = new EntityUpdateRequest();
    doThrow(new InvalidRequestException("Account not enabled for IDP 2.0"))
        .when(idpCommonService)
        .idpV2Check(TEST_ACCOUNT_ID);
    entitiesApi.updateEntity(entityRequest, "component", "scope", "entity", TEST_ACCOUNT_ID, null, null);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntity() {
    EntityResponse entityResponse = new EntityResponse();
    when(catalogService.getEntity(TEST_ACCOUNT_ID, null, null, "component:scope/entity", false, false, false))
        .thenReturn(entityResponse);
    Response response = entitiesApi.getEntity(
        "scope", "component", "entity", null, null, TEST_ACCOUNT_ID, null, null, null, null, null, null);
    verify(catalogService, times(1))
        .getEntity(TEST_ACCOUNT_ID, null, null, "component:scope/entity", false, false, false);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(entityResponse);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntitiesKinds() {
    List<EntityKindsResponse> entityKindsResponseList = List.of();
    when(catalogService.getEntitiesKinds(TEST_ACCOUNT_ID, null, null)).thenReturn(entityKindsResponseList);
    Response response = entitiesApi.getEntitiesKinds(TEST_ACCOUNT_ID, null, null, TEST_ACCOUNT_ID);
    verify(catalogService, times(1)).getEntitiesKinds(TEST_ACCOUNT_ID, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(entityKindsResponseList);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntitiesFilters() {
    List<EntityFiltersResponse> entityFiltersResponseList = List.of();
    when(catalogService.getEntitiesFilters(eq(TEST_ACCOUNT_ID), anyString(), anyString(), anyString()))
        .thenReturn(entityFiltersResponseList);
    Response response = entitiesApi.getEntitiesFilters(TEST_ACCOUNT_ID, "kind", null, TEST_ACCOUNT_ID, null);
    verify(catalogService, times(1)).getEntitiesFilters(eq(TEST_ACCOUNT_ID), isNull(), eq("kind"), eq(TEST_ACCOUNT_ID));
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(entityFiltersResponseList);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntitiesWithEmptyEntityRefsUsesV2() {
    GetEntitiesDTO getEntitiesDTO = GetEntitiesDTO.builder()
                                        .pageNumber(0)
                                        .totalElements(1)
                                        .totalOwned(0)
                                        .totalStarred(0)
                                        .entityResponses(List.of())
                                        .build();
    when(idpCommonService.idpEntityListOptimizedPathEnabled(TEST_ACCOUNT_ID)).thenReturn(true);
    when(catalogService.getEntitiesV2(eq(TEST_ACCOUNT_ID), eq(0), eq(10), eq(""), eq(""), eq(false), eq(null), eq(""),
             eq(false), eq(false), eq(V1_DEFAULT_KIND_FILTER), eq(""), eq(""), eq(""), eq(""), eq(null), eq(true),
             eq(false), isNull()))
        .thenReturn(getEntitiesDTO);

    Response response = entitiesApi.getEntities(
        TEST_ACCOUNT_ID, 0, 10, "", "", false, null, "", false, false, "", "", "", "", "", null);

    verify(catalogService)
        .getEntitiesV2(TEST_ACCOUNT_ID, 0, 10, "", "", false, null, "", false, false, V1_DEFAULT_KIND_FILTER, "", "",
            "", "", null, true, false, null);
    verifyNoMoreInteractions(catalogService);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(getEntitiesDTO.getEntityResponses());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntitiesWithNullEntityRefsUsesV2() {
    GetEntitiesDTO getEntitiesDTO =
        GetEntitiesDTO.builder().pageNumber(0).totalElements(0).entityResponses(List.of()).build();
    when(idpCommonService.idpEntityListOptimizedPathEnabled(TEST_ACCOUNT_ID)).thenReturn(true);
    when(catalogService.getEntitiesV2(eq(TEST_ACCOUNT_ID), eq(0), eq(10), isNull(), isNull(), eq(false), isNull(),
             isNull(), isNull(), isNull(), eq(V1_DEFAULT_KIND_FILTER), isNull(), isNull(), isNull(), isNull(), isNull(),
             eq(true), eq(false), isNull()))
        .thenReturn(getEntitiesDTO);

    Response response = entitiesApi.getEntities(
        TEST_ACCOUNT_ID, 0, 10, null, null, false, null, null, null, null, null, null, null, null, null, null);

    verify(catalogService)
        .getEntitiesV2(TEST_ACCOUNT_ID, 0, 10, null, null, false, null, null, null, null, V1_DEFAULT_KIND_FILTER, null,
            null, null, null, null, true, false, null);
    verifyNoMoreInteractions(catalogService);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntitiesWithEmptyEntityRefsAndFFDisabledUsesV1() {
    GetEntitiesDTO getEntitiesDTO =
        GetEntitiesDTO.builder().pageNumber(0).totalElements(1).entityResponses(List.of(new EntityResponse())).build();
    when(idpCommonService.idpEntityListOptimizedPathEnabled(TEST_ACCOUNT_ID)).thenReturn(false);
    when(catalogService.getEntities(eq(TEST_ACCOUNT_ID), eq(0), eq(10), isNull(), isNull(), eq(false), isNull(),
             isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true)))
        .thenReturn(getEntitiesDTO);

    Response response = entitiesApi.getEntities(
        TEST_ACCOUNT_ID, 0, 10, null, null, false, null, null, null, null, null, null, null, null, null, null);

    verify(catalogService)
        .getEntities(TEST_ACCOUNT_ID, 0, 10, null, null, false, null, null, null, null, null, null, null, null, null,
            null, true);
    verifyNoMoreInteractions(catalogService);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntitiesWithEntityRefsUsesV1() {
    String entityRefs = "component:account/service1";
    GetEntitiesDTO getEntitiesDTO =
        GetEntitiesDTO.builder().pageNumber(0).totalElements(1).entityResponses(List.of(new EntityResponse())).build();
    when(catalogService.getEntities(eq(TEST_ACCOUNT_ID), eq(0), eq(10), isNull(), isNull(), eq(false), isNull(),
             eq(entityRefs), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true)))
        .thenReturn(getEntitiesDTO);

    Response response = entitiesApi.getEntities(
        TEST_ACCOUNT_ID, 0, 10, null, null, false, null, entityRefs, null, null, null, null, null, null, null, null);

    verify(catalogService)
        .getEntities(TEST_ACCOUNT_ID, 0, 10, null, null, false, null, entityRefs, null, null, null, null, null, null,
            null, null, true);
    verifyNoMoreInteractions(catalogService);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(getEntitiesDTO.getEntityResponses());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntitiesByRefs() {
    GetEntitiesDTO getEntitiesDTO = GetEntitiesDTO.builder()
                                        .pageNumber(0)
                                        .totalElements(1)
                                        .totalOwned(0)
                                        .totalStarred(0)
                                        .entityResponses(List.of())
                                        .build();
    EntitiesByRefsRequest entitiesByRefsRequest = new EntitiesByRefsRequest();
    entitiesByRefsRequest.setEntityRefs(Collections.singletonList("test"));

    when(catalogService.getEntities(eq(TEST_ACCOUNT_ID), eq(0), eq(10), eq(""), eq(""), eq(false), eq(null), eq("test"),
             eq(false), eq(false), eq(""), eq(""), eq(""), eq(""), eq(""), eq(null), eq(true), eq(true)))
        .thenReturn(getEntitiesDTO);
    Response response = entitiesApi.getEntitiesByRefs(
        entitiesByRefsRequest, TEST_ACCOUNT_ID, 0, 10, "", "", null, false, false, "", "", "", "", "", null, false);
    verify(catalogService, times(1))
        .getEntities(
            TEST_ACCOUNT_ID, 0, 10, "", "", false, null, "test", false, false, "", "", "", "", "", null, true, true);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateOrUpdateEntityTable() {
    EntityTableCreateOrUpdateRequest request = new EntityTableCreateOrUpdateRequest();
    EntityTableResponse mockResponse = new EntityTableResponse();
    mockResponse.setIdentifier("service_table");
    mockResponse.setName("service table");

    String kind = "service";

    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    when(catalogTableService.createOrUpdateEntityTable(eq(request), eq(TEST_ACCOUNT_ID), eq(kind)))
        .thenReturn(mockResponse);

    Response response = entitiesApi.createOrUpdateEntityTable(request, TEST_ACCOUNT_ID, kind);

    verify(idpCommonService, times(1)).idpV2Check(TEST_ACCOUNT_ID);
    verify(catalogTableService, times(1)).createOrUpdateEntityTable(request, TEST_ACCOUNT_ID, kind);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(mockResponse);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetEntityTables() {
    String kind = "service";
    List<EntityTableResponse> mockResponses =
        List.of(createMockEntityTableResponse("service_table_1", "Service Table 1"),
            createMockEntityTableResponse("service_table_2", "Service Table 2"));

    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    when(catalogTableService.getEntityTables(TEST_ACCOUNT_ID, kind)).thenReturn(mockResponses);

    Response response = entitiesApi.getEntityTables(TEST_ACCOUNT_ID, kind);

    verify(idpCommonService, times(1)).idpV2Check(TEST_ACCOUNT_ID);
    verify(catalogTableService, times(1)).getEntityTables(TEST_ACCOUNT_ID, kind);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(mockResponses);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSyncApiEndpoints_success() {
    ApiEndpointSyncResponse syncResponse = new ApiEndpointSyncResponse();
    syncResponse.setChanged(true);
    syncResponse.setEndpointsExtracted(3);

    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    when(apiEndpointSyncService.sync(TEST_ACCOUNT_ID, null, null, "api", "entity")).thenReturn(syncResponse);

    Response response = entitiesApi.syncApiEndpoints("scope", "api", "entity", null, null, TEST_ACCOUNT_ID);

    verify(apiEndpointSyncService, times(1)).sync(TEST_ACCOUNT_ID, null, null, "api", "entity");
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(syncResponse);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSyncApiEndpoints_lockHeld_returnsConflict() {
    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    doThrow(new ApiEndpointSyncInProgressException("A sync is already in progress for this entity"))
        .when(apiEndpointSyncService)
        .sync(TEST_ACCOUNT_ID, null, null, "api", "entity");

    Response response = entitiesApi.syncApiEndpoints("scope", "api", "entity", null, null, TEST_ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSyncApiEndpoints_fetchOrParseFailure_returnsInternalServerError() {
    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    doThrow(new ApiEndpointSyncFailedException("Failed to fetch API spec from source: boom"))
        .when(apiEndpointSyncService)
        .sync(TEST_ACCOUNT_ID, null, null, "api", "entity");

    Response response = entitiesApi.syncApiEndpoints("scope", "api", "entity", null, null, TEST_ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSyncApiEndpoints_idpV2Disabled_propagatesAsBadRequest() {
    doThrow(new InvalidRequestException("Account not enabled for IDP 2.0"))
        .when(idpCommonService)
        .idpV2Check(TEST_ACCOUNT_ID);
    entitiesApi.syncApiEndpoints("scope", "api", "entity", null, null, TEST_ACCOUNT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSyncApiEndpoints_ffDisabledOrNonApiKind_propagatesAsBadRequest() {
    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    doThrow(new InvalidRequestException("sync-api-endpoints is only supported for API entities"))
        .when(apiEndpointSyncService)
        .sync(TEST_ACCOUNT_ID, null, null, "component", "entity");
    entitiesApi.syncApiEndpoints("scope", "component", "entity", null, null, TEST_ACCOUNT_ID);
  }

  @Test(expected = EntityNotFoundException.class)
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSyncApiEndpoints_entityNotFound_propagatesAsNotFound() {
    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    doThrow(new EntityNotFoundException("Entity with entityRef = api:entity not found"))
        .when(apiEndpointSyncService)
        .sync(TEST_ACCOUNT_ID, null, null, "api", "entity");
    entitiesApi.syncApiEndpoints("scope", "api", "entity", null, null, TEST_ACCOUNT_ID);
  }

  @Test(expected = NGAccessDeniedException.class)
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSyncApiEndpoints_missingEditPermission_propagatesAsForbidden() {
    doNothing().when(idpCommonService).idpV2Check(TEST_ACCOUNT_ID);
    doThrow(new NGAccessDeniedException("Missing permission", null, null))
        .when(apiEndpointSyncService)
        .sync(TEST_ACCOUNT_ID, null, null, "api", "entity");
    entitiesApi.syncApiEndpoints("scope", "api", "entity", null, null, TEST_ACCOUNT_ID);
  }

  private EntityTableResponse createMockEntityTableResponse(String identifier, String name) {
    EntityTableResponse response = new EntityTableResponse();
    response.setIdentifier(identifier);
    response.setName(name);
    return response;
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSubmitBulkUpdateEntityField() {
    BulkFieldUpdateProperty property = new BulkFieldUpdateProperty().key("owner").value("group:account/owners");
    BulkEntityFieldUpdateRequest request = new BulkEntityFieldUpdateRequest()
                                               .filter(new ScorecardFilter().kind("component"))
                                               .properties(List.of(property));

    BulkFieldUpdateSubmitResponse mockResponse =
        new BulkFieldUpdateSubmitResponse().operationId("op123").status("QUEUED").matched(10).permitted(8);

    when(bulkEntityFieldUpdateService.submit(request, TEST_ACCOUNT_ID)).thenReturn(mockResponse);

    Response response = entitiesApi.submitBulkUpdateEntityField(request, TEST_ACCOUNT_ID);

    verify(bulkEntityFieldUpdateService, times(1)).submit(request, TEST_ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(202);
    assertThat(response.getEntity()).isEqualTo(mockResponse);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetBulkUpdateEntityFieldOperation() {
    String operationId = "op123";
    BulkFieldUpdateOperationResponse mockResponse = new BulkFieldUpdateOperationResponse()
                                                        .operationId(operationId)
                                                        .status("SUCCESS")
                                                        .matched(10)
                                                        .permitted(8)
                                                        .updated(8);

    when(bulkEntityFieldUpdateService.getOperation(TEST_ACCOUNT_ID, operationId)).thenReturn(mockResponse);

    Response response = entitiesApi.getBulkUpdateEntityFieldOperation(operationId, TEST_ACCOUNT_ID);

    verify(bulkEntityFieldUpdateService, times(1)).getOperation(TEST_ACCOUNT_ID, operationId);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(mockResponse);
  }
}
