/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfchangeset.resources;

import static io.harness.rule.OwnerRule.HARSHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.sfchangeset.entity.SalesforceChangesetEntity;
import io.harness.ng.core.sfchangeset.services.SalesforceChangesetService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.SalesforceChangeset;
import io.harness.spec.server.ng.v1.model.SalesforceChangesetCreateRequest;
import io.harness.spec.server.ng.v1.model.SalesforceChangesetMetadata;
import io.harness.spec.server.ng.v1.model.SalesforceChangesetUpdateRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public class AbstractSalesforceChangesetsApiImplTest extends CategoryTest {
  @Mock private SalesforceChangesetService service;
  @Mock private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock private io.harness.ng.core.services.ScopeInfoService scopeInfoService;

  @InjectMocks private AbstractSalesforceChangesetsApiImpl abstractApi;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String IDENTIFIER = "changeset1";
  private static final String NAME = "Changeset 1";
  private static final String SOURCE1 = "deployable1";
  private static final String SOURCE2 = "deployable2";

  private ScopeInfo scopeInfo;
  private SalesforceChangesetEntity entity;
  private SalesforceChangesetCreateRequest createRequest;
  private SalesforceChangesetUpdateRequest updateRequest;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_ID)
                    .projectIdentifier(PROJECT_ID)
                    .uniqueId("uniqueId")
                    .build();

    entity = SalesforceChangesetEntity.builder()
                 .accountIdentifier(ACCOUNT_ID)
                 .orgIdentifier(ORG_ID)
                 .projectIdentifier(PROJECT_ID)
                 .identifier(IDENTIFIER)
                 .name(NAME)
                 .source1(SOURCE1)
                 .source2(SOURCE2)
                 .metadataTypes(Arrays.asList("ApexClass", "ApexTrigger"))
                 .build();

    createRequest = new SalesforceChangesetCreateRequest();
    createRequest.setIdentifier(IDENTIFIER);
    createRequest.setName(NAME);
    createRequest.setSource1(SOURCE1);
    createRequest.setSource2(SOURCE2);
    createRequest.setMetadataTypes(Arrays.asList("ApexClass", "ApexTrigger"));

    updateRequest = new SalesforceChangesetUpdateRequest();
    updateRequest.setName(NAME);
    updateRequest.setMetadataTypes(Arrays.asList("ApexClass"));
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testCreateChangeset_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.create(any(SalesforceChangesetEntity.class), eq(scopeInfo))).thenReturn(entity);

    Response response = abstractApi.createChangeset(createRequest, ORG_ID, PROJECT_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(SalesforceChangeset.class);
    verify(orgAndProjectValidationHelper).checkThatTheOrganizationAndProjectExists(ORG_ID, PROJECT_ID, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testCreateChangeset_NullRequest() {
    assertThatThrownBy(() -> abstractApi.createChangeset(null, ORG_ID, PROJECT_ID, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No request body sent in the API");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetChangeset_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.get(scopeInfo, IDENTIFIER)).thenReturn(Optional.of(entity));

    Response response = abstractApi.getChangeset(ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(SalesforceChangeset.class);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetChangeset_NotFound() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.get(scopeInfo, IDENTIFIER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> abstractApi.getChangeset(ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("SalesforceChangeset with identifier [" + IDENTIFIER + "] not found");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testUpdateChangeset_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.update(any(SalesforceChangesetEntity.class), eq(scopeInfo))).thenReturn(entity);

    Response response = abstractApi.updateChangeset(updateRequest, ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(SalesforceChangeset.class);
    verify(orgAndProjectValidationHelper).checkThatTheOrganizationAndProjectExists(ORG_ID, PROJECT_ID, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testUpdateChangeset_NullRequest() {
    assertThatThrownBy(() -> abstractApi.updateChangeset(null, ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No request body sent in the API");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testDeleteChangeset_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.delete(scopeInfo, IDENTIFIER)).thenReturn(true);

    Response response = abstractApi.deleteChangeset(ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testDeleteChangeset_Failure() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.delete(scopeInfo, IDENTIFIER)).thenReturn(false);

    assertThatThrownBy(() -> abstractApi.deleteChangeset(ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("could not be deleted");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetChangesets() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    Page<SalesforceChangesetEntity> page = new PageImpl<>(Arrays.asList(entity));
    when(service.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);

    Response response =
        abstractApi.getChangesets(ORG_ID, PROJECT_ID, 0, 10, "test", "deployable", "ApexClass", null, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    List<SalesforceChangesetMetadata> result = (List<SalesforceChangesetMetadata>) response.getEntity();
    assertThat(result).hasSize(1);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetChangesets_WithComparisonPairRefFilter() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    Page<SalesforceChangesetEntity> page = new PageImpl<>(Arrays.asList(entity));
    when(service.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);

    Response response =
        abstractApi.getChangesets(ORG_ID, PROJECT_ID, 0, 10, null, null, null, "comparisonPair1", ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    List<SalesforceChangesetMetadata> result = (List<SalesforceChangesetMetadata>) response.getEntity();
    assertThat(result).hasSize(1);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetChangesets_WithBothSearchTermAndSourceFilter() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    Page<SalesforceChangesetEntity> page = new PageImpl<>(Arrays.asList(entity));
    when(service.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);

    Response response =
        abstractApi.getChangesets(ORG_ID, PROJECT_ID, 0, 10, "searchTerm", "sourceFilter", null, null, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // Verify service.list() was called with criteria containing BOTH filters
    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(service).list(criteriaCaptor.capture(), any(Pageable.class));

    // The captured criteria should have both OR conditions ANDed together
    // This test validates that searchTerm isn't silently dropped
    List<SalesforceChangesetMetadata> result = (List<SalesforceChangesetMetadata>) response.getEntity();
    assertThat(result).hasSize(1);
  }
}
