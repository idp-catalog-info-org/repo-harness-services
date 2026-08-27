/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.dto.FilterPropertiesDTO;
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
import java.util.Set;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class AbstractCDDashboardApiTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String FILTER_ID = "filterId";
  private static final String REPO_NAME = "repoName";
  private static final String SEARCH_TERM = "search";
  private static final String UNIQUE_ID = "uniqueId";

  @Mock private CDOverviewDashboardService cdOverviewDashboardService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private FilterService filterService;

  private TestCDDashboardApi testApi;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    testApi =
        new TestCDDashboardApi(cdOverviewDashboardService, ngFeatureFlagHelperService, scopeInfoService, filterService);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetApiServices_Success() throws Exception {
    ServiceResponseDTO serviceResponseDTO =
        ServiceResponseDTO.builder().accountId(ACCOUNT_ID).identifier("svc1").name("Service 1").build();

    ServiceDashboardResponseDTO dashboardResponseDTO = ServiceDashboardResponseDTO.builder()
                                                           .service(serviceResponseDTO)
                                                           .createdAt(1000L)
                                                           .lastModifiedAt(2000L)
                                                           .deploymentTypeList(Set.of("Kubernetes"))
                                                           .build();

    PageResponse<ServiceDashboardResponseDTO> pageResponse = PageResponse.<ServiceDashboardResponseDTO>builder()
                                                                 .content(Arrays.asList(dashboardResponseDTO))
                                                                 .totalItems(1)
                                                                 .pageIndex(0)
                                                                 .pageSize(10)
                                                                 .build();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(),
             eq(REPO_NAME), eq(10), eq(0), eq(SEARCH_TERM), any(), isNull()))
        .thenReturn(pageResponse);

    Response response = testApi.getApiServices(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyList(), REPO_NAME, 0, 10, SEARCH_TERM, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isNotNull();

    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(), eq(REPO_NAME), eq(10), eq(0),
            eq(SEARCH_TERM), any(), isNull());
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetApiServices_WithServiceFilter() throws Exception {
    ServiceFilterPropertiesDTO filterProperties = ServiceFilterPropertiesDTO.builder()
                                                      .serviceIdentifiers(Arrays.asList("svc1", "svc2"))
                                                      .serviceTypes(Arrays.asList("Kubernetes"))
                                                      .build();

    PageResponse<ServiceDashboardResponseDTO> pageResponse =
        PageResponse.<ServiceDashboardResponseDTO>builder().content(Collections.emptyList()).totalItems(0).build();

    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(),
             eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), any(), eq(filterProperties)))
        .thenReturn(pageResponse);

    Response response = testApi.getApiServices(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyList(), REPO_NAME, 0, 10, SEARCH_TERM, null, filterProperties);

    assertThat(response).isNotNull();
    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), eq(filterProperties));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetApiServices_WithFilterId() throws Exception {
    ServiceFilterPropertiesDTO filterProperties =
        ServiceFilterPropertiesDTO.builder().serviceIdentifiers(Arrays.asList("svc1")).build();

    FilterDTO filterDTO = FilterDTO.builder()
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .identifier(FILTER_ID)
                              .filterProperties(filterProperties)
                              .build();

    PageResponse<ServiceDashboardResponseDTO> pageResponse =
        PageResponse.<ServiceDashboardResponseDTO>builder().content(Collections.emptyList()).totalItems(0).build();

    when(filterService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, FILTER_ID, FilterType.SERVICE)).thenReturn(filterDTO);
    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(),
             eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), any(), eq(filterProperties)))
        .thenReturn(pageResponse);

    Response response = testApi.getApiServices(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyList(), REPO_NAME, 0, 10, SEARCH_TERM, FILTER_ID, null);

    assertThat(response).isNotNull();
    verify(filterService, times(1)).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, FILTER_ID, FilterType.SERVICE);
    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), eq(filterProperties));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetApiServices_WithFeatureFlagEnabled() throws Exception {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .uniqueId(UNIQUE_ID)
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();

    PageResponse<ServiceDashboardResponseDTO> pageResponse =
        PageResponse.<ServiceDashboardResponseDTO>builder().content(Collections.emptyList()).totalItems(0).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(),
             eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), eq(scopeInfo), isNull()))
        .thenReturn(pageResponse);

    Response response = testApi.getApiServices(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyList(), REPO_NAME, 0, 10, SEARCH_TERM, null, null);

    assertThat(response).isNotNull();
    verify(scopeInfoService, times(1)).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), eq(scopeInfo), isNull());
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetApiServices_WithException() throws Exception {
    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(),
             eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), any(), isNull()))
        .thenThrow(new RuntimeException("Database error"));

    assertThatThrownBy(()
                           -> testApi.getApiServices(ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyList(), REPO_NAME,
                               0, 10, SEARCH_TERM, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Database error");
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetFilter_WithScopeInfo() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .uniqueId(UNIQUE_ID)
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();

    ServiceFilterPropertiesDTO filterProperties =
        ServiceFilterPropertiesDTO.builder().serviceIdentifiers(Arrays.asList("svc1")).build();

    FilterDTO filterDTO =
        FilterDTO.builder().orgIdentifier(ORG_ID).identifier(FILTER_ID).filterProperties(filterProperties).build();

    when(filterService.get(scopeInfo, FILTER_ID, FilterType.SERVICE)).thenReturn(filterDTO);

    ServiceFilterPropertiesDTO result = testApi.getFilter(ACCOUNT_ID, ORG_ID, PROJECT_ID, scopeInfo, FILTER_ID);

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(filterProperties);
    verify(filterService, times(1)).get(scopeInfo, FILTER_ID, FilterType.SERVICE);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetFilter_WithoutScopeInfo() {
    ServiceFilterPropertiesDTO filterProperties =
        ServiceFilterPropertiesDTO.builder().serviceIdentifiers(Arrays.asList("svc1")).build();

    FilterDTO filterDTO = FilterDTO.builder()
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .identifier(FILTER_ID)
                              .filterProperties(filterProperties)
                              .build();

    when(filterService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, FILTER_ID, FilterType.SERVICE)).thenReturn(filterDTO);

    ServiceFilterPropertiesDTO result = testApi.getFilter(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, FILTER_ID);

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(filterProperties);
    verify(filterService, times(1)).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, FILTER_ID, FilterType.SERVICE);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetFilter_WithInvalidFilterType() {
    FilterPropertiesDTO invalidFilterProperties = mock(FilterPropertiesDTO.class);

    FilterDTO filterDTO = FilterDTO.builder()
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .identifier(FILTER_ID)
                              .filterProperties(invalidFilterProperties)
                              .build();

    when(filterService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, FILTER_ID, FilterType.SERVICE)).thenReturn(filterDTO);

    assertThatThrownBy(() -> testApi.getFilter(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, FILTER_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unexpected filter properties type");
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetServices_WithFilterIdAndFilterProperties() throws Exception {
    // Filter ID should take precedence over filterPropertiesDTO parameter
    ServiceFilterPropertiesDTO filterIdProperties =
        ServiceFilterPropertiesDTO.builder().serviceIdentifiers(Arrays.asList("svc1")).build();

    ServiceFilterPropertiesDTO filterPropertiesParam =
        ServiceFilterPropertiesDTO.builder().serviceIdentifiers(Arrays.asList("svc2")).build();

    FilterDTO filterDTO = FilterDTO.builder()
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .identifier(FILTER_ID)
                              .filterProperties(filterIdProperties)
                              .build();

    PageResponse<ServiceDashboardResponseDTO> pageResponse =
        PageResponse.<ServiceDashboardResponseDTO>builder().content(Collections.emptyList()).totalItems(0).build();

    when(filterService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, FILTER_ID, FilterType.SERVICE)).thenReturn(filterDTO);
    when(cdOverviewDashboardService.getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(),
             eq(REPO_NAME), anyInt(), anyInt(), eq(SEARCH_TERM), any(), eq(filterIdProperties)))
        .thenReturn(pageResponse);

    Response response = testApi.getApiServices(ACCOUNT_ID, ORG_ID, PROJECT_ID, Collections.emptyList(), REPO_NAME, 0,
        10, SEARCH_TERM, FILTER_ID, filterPropertiesParam);

    assertThat(response).isNotNull();
    // Should use filter from filterId, not filterPropertiesParam
    verify(cdOverviewDashboardService, times(1))
        .getServicesList(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), anyList(), eq(REPO_NAME), anyInt(), anyInt(),
            eq(SEARCH_TERM), any(), eq(filterIdProperties));
  }

  // Test implementation class for testing AbstractCDDashboardApi
  private static class TestCDDashboardApi extends AbstractCDDashboardApi {
    TestCDDashboardApi(CDOverviewDashboardService cdOverviewDashboardService,
        NGFeatureFlagHelperService ngFeatureFlagHelperService, ScopeInfoService scopeResolverService,
        FilterService filterService) {
      super(cdOverviewDashboardService, ngFeatureFlagHelperService, scopeResolverService, filterService);
    }
  }
}
