/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.filter.service.FilterService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.service.dto.ServiceDashboardResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceFilterPropertiesDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class CDDashboardApiImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String REPO_NAME = "repoName";
  private static final String SEARCH_TERM = "search";
  private static final String FILTER_ID = "filterId";

  @Mock private CDOverviewDashboardService cdOverviewDashboardService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private FilterService filterService;

  private ProjectCDDashboardApiImpl projectApi;
  private OrgCDDashboardApiImpl orgApi;
  private AccountCDDashboardApiImpl accountApi;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    projectApi = new ProjectCDDashboardApiImpl(
        cdOverviewDashboardService, ngFeatureFlagHelperService, scopeInfoService, filterService);
    orgApi = new OrgCDDashboardApiImpl(
        cdOverviewDashboardService, ngFeatureFlagHelperService, scopeInfoService, filterService);
    accountApi = new AccountCDDashboardApiImpl(
        cdOverviewDashboardService, ngFeatureFlagHelperService, scopeInfoService, filterService);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testProjectApiGetDashboardServices_WithAllFilters() throws Exception {
    List<String> serviceIdentifiers = Arrays.asList("svc1", "svc2");
    List<String> serviceNames = Arrays.asList("Service 1", "Service 2");
    List<String> tags = Arrays.asList("env:prod", "team:backend");
    List<String> serviceTypes = Arrays.asList("Kubernetes", "ECS");

    PageResponse<ServiceDashboardResponseDTO> pageResponse = createMockPageResponse();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(),
             eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), any(), any(ServiceFilterPropertiesDTO.class)))
        .thenReturn(pageResponse);

    Response response = projectApi.getDashboardServices(ORG_ID, PROJECT_ID, ACCOUNT_ID, 0, 10, SEARCH_TERM, "createdAt",
        "DESC", null, serviceIdentifiers, serviceNames, tags, serviceTypes, REPO_NAME);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    ArgumentCaptor<ServiceFilterPropertiesDTO> filterCaptor = ArgumentCaptor.forClass(ServiceFilterPropertiesDTO.class);
    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), filterCaptor.capture());

    ServiceFilterPropertiesDTO capturedFilter = filterCaptor.getValue();
    assertThat(capturedFilter).isNotNull();
    assertThat(capturedFilter.getServiceIdentifiers()).containsExactlyElementsOf(serviceIdentifiers);
    assertThat(capturedFilter.getServiceNames()).containsExactlyElementsOf(serviceNames);
    assertThat(capturedFilter.getServiceTypes()).containsExactlyElementsOf(serviceTypes);
    assertThat(capturedFilter.getTags()).hasSize(2);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testProjectApiGetDashboardServices_WithNoFilters() throws Exception {
    PageResponse<ServiceDashboardResponseDTO> pageResponse = createMockPageResponse();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(),
             eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), any(), isNull()))
        .thenReturn(pageResponse);

    Response response = projectApi.getDashboardServices(
        ORG_ID, PROJECT_ID, ACCOUNT_ID, 0, 10, SEARCH_TERM, null, null, null, null, null, null, null, REPO_NAME);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), isNull());
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testProjectApiGetDashboardServices_WithOnlyServiceIdentifiers() throws Exception {
    List<String> serviceIdentifiers = Arrays.asList("svc1", "svc2");
    PageResponse<ServiceDashboardResponseDTO> pageResponse = createMockPageResponse();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(),
             eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), any(), any(ServiceFilterPropertiesDTO.class)))
        .thenReturn(pageResponse);

    Response response = projectApi.getDashboardServices(ORG_ID, PROJECT_ID, ACCOUNT_ID, 0, 10, SEARCH_TERM, null, null,
        null, serviceIdentifiers, null, null, null, REPO_NAME);

    assertThat(response).isNotNull();

    ArgumentCaptor<ServiceFilterPropertiesDTO> filterCaptor = ArgumentCaptor.forClass(ServiceFilterPropertiesDTO.class);
    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), filterCaptor.capture());

    ServiceFilterPropertiesDTO capturedFilter = filterCaptor.getValue();
    assertThat(capturedFilter).isNotNull();
    assertThat(capturedFilter.getServiceIdentifiers()).containsExactlyElementsOf(serviceIdentifiers);
    assertThat(capturedFilter.getServiceNames()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testOrgApiGetOrgScopedDashboardServices_WithAllFilters() throws Exception {
    List<String> serviceIdentifiers = Arrays.asList("svc1");
    List<String> serviceNames = Arrays.asList("Service 1");
    List<String> tags = Arrays.asList("env:prod");
    List<String> serviceTypes = Arrays.asList("Kubernetes");

    PageResponse<ServiceDashboardResponseDTO> pageResponse = createMockPageResponse();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), isNull(), anyList(), eq(REPO_NAME),
             anyInt(), anyInt(), eq(SEARCH_TERM), any(), any(ServiceFilterPropertiesDTO.class)))
        .thenReturn(pageResponse);

    Response response = orgApi.getOrgScopedDashboardServices(ORG_ID, ACCOUNT_ID, 0, 10, SEARCH_TERM, "createdAt",
        "DESC", null, serviceIdentifiers, serviceNames, tags, serviceTypes, REPO_NAME);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    ArgumentCaptor<ServiceFilterPropertiesDTO> filterCaptor = ArgumentCaptor.forClass(ServiceFilterPropertiesDTO.class);
    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), isNull(), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), filterCaptor.capture());

    ServiceFilterPropertiesDTO capturedFilter = filterCaptor.getValue();
    assertThat(capturedFilter).isNotNull();
    assertThat(capturedFilter.getServiceIdentifiers()).containsExactlyElementsOf(serviceIdentifiers);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testOrgApiGetOrgScopedDashboardServices_WithNoFilters() throws Exception {
    PageResponse<ServiceDashboardResponseDTO> pageResponse = createMockPageResponse();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), isNull(), anyList(), eq(REPO_NAME),
             anyInt(), anyInt(), eq(SEARCH_TERM), any(), isNull()))
        .thenReturn(pageResponse);

    Response response = orgApi.getOrgScopedDashboardServices(
        ORG_ID, ACCOUNT_ID, 0, 10, SEARCH_TERM, null, null, null, null, null, null, null, REPO_NAME);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), isNull(), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), isNull());
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testAccountApiGetAccountScopedDashboardServices_WithAllFilters() throws Exception {
    List<String> serviceIdentifiers = Arrays.asList("svc1");
    List<String> tags = Arrays.asList("env:prod");
    List<String> serviceTypes = Arrays.asList("Kubernetes");
    List<String> serviceNames = Arrays.asList("Service 1");

    PageResponse<ServiceDashboardResponseDTO> pageResponse = createMockPageResponse();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), isNull(), isNull(), anyList(), eq(REPO_NAME),
             anyInt(), anyInt(), eq(SEARCH_TERM), any(), any(ServiceFilterPropertiesDTO.class)))
        .thenReturn(pageResponse);

    Response response = accountApi.getAccountScopedDashboardServices(ACCOUNT_ID, 0, 10, SEARCH_TERM, "createdAt",
        "DESC", null, serviceIdentifiers, tags, serviceTypes, serviceNames, REPO_NAME);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    ArgumentCaptor<ServiceFilterPropertiesDTO> filterCaptor = ArgumentCaptor.forClass(ServiceFilterPropertiesDTO.class);
    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), isNull(), isNull(), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), filterCaptor.capture());

    ServiceFilterPropertiesDTO capturedFilter = filterCaptor.getValue();
    assertThat(capturedFilter).isNotNull();
    assertThat(capturedFilter.getServiceIdentifiers()).containsExactlyElementsOf(serviceIdentifiers);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testAccountApiGetAccountScopedDashboardServices_WithNoFilters() throws Exception {
    PageResponse<ServiceDashboardResponseDTO> pageResponse = createMockPageResponse();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), isNull(), isNull(), anyList(), eq(REPO_NAME),
             anyInt(), anyInt(), eq(SEARCH_TERM), any(), isNull()))
        .thenReturn(pageResponse);

    Response response = accountApi.getAccountScopedDashboardServices(
        ACCOUNT_ID, 0, 10, SEARCH_TERM, null, null, null, null, null, null, null, REPO_NAME);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), isNull(), isNull(), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), isNull());
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testProjectApiGetDashboardServices_WithSortParameter() throws Exception {
    PageResponse<ServiceDashboardResponseDTO> pageResponse = createMockPageResponse();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(Arrays.asList("createdAt")), eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), any(), isNull()))
        .thenReturn(pageResponse);

    Response response = projectApi.getDashboardServices(ORG_ID, PROJECT_ID, ACCOUNT_ID, 0, 10, SEARCH_TERM, "createdAt",
        "DESC", null, null, null, null, null, REPO_NAME);

    assertThat(response).isNotNull();

    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Arrays.asList("createdAt")), eq(REPO_NAME),
            anyInt(), anyInt(), eq(SEARCH_TERM), any(), isNull());
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testProjectApiGetDashboardServices_WithNullSort() throws Exception {
    PageResponse<ServiceDashboardResponseDTO> pageResponse = createMockPageResponse();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(Collections.emptyList()), eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), any(), isNull()))
        .thenReturn(pageResponse);

    Response response = projectApi.getDashboardServices(
        ORG_ID, PROJECT_ID, ACCOUNT_ID, 0, 10, SEARCH_TERM, null, null, null, null, null, null, null, REPO_NAME);

    assertThat(response).isNotNull();

    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.emptyList()), eq(REPO_NAME),
            anyInt(), anyInt(), eq(SEARCH_TERM), any(), isNull());
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testProjectApiGetDashboardServices_WithEmptyLists() throws Exception {
    PageResponse<ServiceDashboardResponseDTO> pageResponse = createMockPageResponse();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(),
             eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), any(), isNull()))
        .thenReturn(pageResponse);

    Response response = projectApi.getDashboardServices(ORG_ID, PROJECT_ID, ACCOUNT_ID, 0, 10, SEARCH_TERM, null, null,
        null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        REPO_NAME);

    assertThat(response).isNotNull();

    // Empty lists should result in null filter
    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), isNull());
  }

  private PageResponse<ServiceDashboardResponseDTO> createMockPageResponse() {
    ServiceResponseDTO serviceResponseDTO =
        ServiceResponseDTO.builder().accountId(ACCOUNT_ID).identifier("svc1").name("Service 1").build();

    ServiceDashboardResponseDTO dashboardResponseDTO = ServiceDashboardResponseDTO.builder()
                                                           .service(serviceResponseDTO)
                                                           .createdAt(1000L)
                                                           .lastModifiedAt(2000L)
                                                           .deploymentTypeList(Set.of("Kubernetes"))
                                                           .build();

    return PageResponse.<ServiceDashboardResponseDTO>builder()
        .content(Arrays.asList(dashboardResponseDTO))
        .totalItems(1)
        .pageIndex(0)
        .pageSize(10)
        .build();
  }
}
