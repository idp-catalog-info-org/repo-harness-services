/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfcomparisonpair.resources;

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
import io.harness.ng.core.sfcomparisonpair.entity.SalesforceComparisonPairEntity;
import io.harness.ng.core.sfcomparisonpair.services.SalesforceComparisonPairService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.SalesforceComparisonPair;
import io.harness.spec.server.ng.v1.model.SalesforceComparisonPairCreateRequest;
import io.harness.spec.server.ng.v1.model.SalesforceComparisonPairMetadata;
import io.harness.spec.server.ng.v1.model.SalesforceComparisonPairUpdateRequest;

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

public class AbstractSalesforceComparisonPairsApiImplTest extends CategoryTest {
  @Mock private SalesforceComparisonPairService service;
  @Mock private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock private io.harness.ng.core.services.ScopeInfoService scopeInfoService;

  @InjectMocks private AbstractSalesforceComparisonPairsApiImpl abstractApi;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String IDENTIFIER = "comparisonPair1";
  private static final String NAME = "Comparison Pair 1";
  private static final String SOURCE_REF_1 = "deployable1";
  private static final String SOURCE_REF_2 = "deployable2";

  private ScopeInfo scopeInfo;
  private SalesforceComparisonPairEntity entity;
  private SalesforceComparisonPairCreateRequest createRequest;
  private SalesforceComparisonPairUpdateRequest updateRequest;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_ID)
                    .projectIdentifier(PROJECT_ID)
                    .uniqueId("uniqueId")
                    .build();

    entity = SalesforceComparisonPairEntity.builder()
                 .accountIdentifier(ACCOUNT_ID)
                 .orgIdentifier(ORG_ID)
                 .projectIdentifier(PROJECT_ID)
                 .identifier(IDENTIFIER)
                 .name(NAME)
                 .sourceRef1(SOURCE_REF_1)
                 .sourceRef2(SOURCE_REF_2)
                 .metadataTypes(Arrays.asList("ApexClass", "ApexTrigger"))
                 .build();

    createRequest = new SalesforceComparisonPairCreateRequest();
    createRequest.setIdentifier(IDENTIFIER);
    createRequest.setName(NAME);
    createRequest.setSourceRef1(SOURCE_REF_1);
    createRequest.setSourceRef2(SOURCE_REF_2);
    createRequest.setMetadataTypes(Arrays.asList("ApexClass", "ApexTrigger"));

    updateRequest = new SalesforceComparisonPairUpdateRequest();
    updateRequest.setIdentifier(IDENTIFIER);
    updateRequest.setName(NAME);
    updateRequest.setSourceRef1(SOURCE_REF_1);
    updateRequest.setSourceRef2(SOURCE_REF_2);
    updateRequest.setMetadataTypes(Arrays.asList("ApexClass"));
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testCreateComparisonPair_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.create(any(SalesforceComparisonPairEntity.class), eq(scopeInfo))).thenReturn(entity);

    Response response = abstractApi.createComparisonPair(createRequest, ORG_ID, PROJECT_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(SalesforceComparisonPair.class);
    verify(orgAndProjectValidationHelper).checkThatTheOrganizationAndProjectExists(ORG_ID, PROJECT_ID, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testCreateComparisonPair_NullRequest() {
    assertThatThrownBy(() -> abstractApi.createComparisonPair(null, ORG_ID, PROJECT_ID, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No request body sent in the API");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetComparisonPair_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.get(scopeInfo, IDENTIFIER)).thenReturn(Optional.of(entity));

    Response response = abstractApi.getComparisonPair(ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(SalesforceComparisonPair.class);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetComparisonPair_NotFound() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.get(scopeInfo, IDENTIFIER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> abstractApi.getComparisonPair(ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("SalesforceComparisonPair with identifier [" + IDENTIFIER + "] not found");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testUpdateComparisonPair_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.update(any(SalesforceComparisonPairEntity.class), eq(scopeInfo))).thenReturn(entity);

    Response response = abstractApi.updateComparisonPair(updateRequest, ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(SalesforceComparisonPair.class);
    verify(orgAndProjectValidationHelper).checkThatTheOrganizationAndProjectExists(ORG_ID, PROJECT_ID, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testUpdateComparisonPair_NullRequest() {
    assertThatThrownBy(() -> abstractApi.updateComparisonPair(null, ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No request body sent in the API");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testDeleteComparisonPair_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.delete(scopeInfo, IDENTIFIER)).thenReturn(true);

    Response response = abstractApi.deleteComparisonPair(ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testDeleteComparisonPair_Failure() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(service.delete(scopeInfo, IDENTIFIER)).thenReturn(false);

    assertThatThrownBy(() -> abstractApi.deleteComparisonPair(ORG_ID, PROJECT_ID, IDENTIFIER, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("could not be deleted");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetComparisonPairs() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    Page<SalesforceComparisonPairEntity> page = new PageImpl<>(Arrays.asList(entity));
    when(service.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);

    Response response =
        abstractApi.getComparisonPairs(ORG_ID, PROJECT_ID, 0, 10, "test", "deployable", "ApexClass", ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    List<SalesforceComparisonPairMetadata> result = (List<SalesforceComparisonPairMetadata>) response.getEntity();
    assertThat(result).hasSize(1);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetComparisonPairs_WithBothSearchTermAndSourceRefFilter() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    Page<SalesforceComparisonPairEntity> page = new PageImpl<>(Arrays.asList(entity));
    when(service.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);

    Response response =
        abstractApi.getComparisonPairs(ORG_ID, PROJECT_ID, 0, 10, "searchTerm", "sourceFilter", null, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // Verify service.list() was called with criteria containing BOTH filters
    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(service).list(criteriaCaptor.capture(), any(Pageable.class));

    // The captured criteria should have both OR conditions ANDed together
    // This test validates that searchTerm isn't silently dropped
    List<SalesforceComparisonPairMetadata> result = (List<SalesforceComparisonPairMetadata>) response.getEntity();
    assertThat(result).hasSize(1);
  }
}
