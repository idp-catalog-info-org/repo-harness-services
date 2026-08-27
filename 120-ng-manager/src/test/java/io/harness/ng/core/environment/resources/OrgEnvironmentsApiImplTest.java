/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.helpers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.resources.ServiceResourceApiUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.EnvironmentResponse;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public class OrgEnvironmentsApiImplTest extends CategoryTest {
  @Inject @InjectMocks OrgEnvironmentsApiImpl orgEnvironmentsApi;
  @Mock private EnvironmentService environmentService;
  @Mock private EnvironmentRbacHelper environmentRbacHelper;
  @Mock private EnvironmentFilterHelper environmentFilterHelper;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private ServiceResourceApiUtils serviceResourceApiUtils;

  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String IDENTIFIER = "identifier";
  private static final String NAME = "name";

  private final String envYamlV0 = "environment:\n"
      + "  name: envId\n"
      + "  identifier: envId\n"
      + "  type: Production\n"
      + "  orgIdentifier: orgId\n"
      + "  variables:\n"
      + "    - name: stringvar\n"
      + "      type: String\n"
      + "      value: envvalue\n"
      + "    - name: numbervar\n"
      + "      type: Number\n"
      + "      value: 5";

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSearchOrgScopedEnvironmentsFiltered_WithScopeInfo() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .uniqueId(ORG_IDENTIFIER)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();

    Environment environment1 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(ORG_IDENTIFIER)
                                   .type(EnvironmentType.Production)
                                   .yaml(envYamlV0)
                                   .build();

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null))).thenReturn(scopeInfo);

    Criteria mockCriteria = new Criteria();
    when(environmentFilterHelper.createCriteriaForGetList(
             eq(scopeInfo), eq(false), eq("search"), eq("filter1"), any(), eq(false), eq("repo1")))
        .thenReturn(mockCriteria);

    when(environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null), eq(ENVIRONMENT_VIEW_PERMISSION)))
        .thenReturn(true);

    doReturn(new PageImpl<>(List.of(environment1)))
        .when(environmentService)
        .list(any(Criteria.class), any(Pageable.class));

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any(Set.class)))
        .thenReturn(Collections.singletonMap(ORG_IDENTIFIER, Optional.of(scopeInfo)));

    Response response = orgEnvironmentsApi.searchOrgScopedEnvironmentsFiltered(ORG_IDENTIFIER, 0, 10, "search", null,
        null, null, null, null, "filter1", false, "repo1", null, null, ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    List<EnvironmentResponse> envResponseList = (List<EnvironmentResponse>) response.getEntity();
    assertThat(envResponseList.size()).isEqualTo(1);
    assertThat(envResponseList.get(0).getEnvironment().getIdentifier()).isEqualTo(IDENTIFIER);

    verify(scopeInfoService, times(1)).getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null));
    verify(environmentFilterHelper, times(1))
        .createCriteriaForGetList(eq(scopeInfo), eq(false), eq("search"), eq("filter1"), any(), eq(false), eq("repo1"));
    verify(scopeInfoService, times(1)).getScopeInfo(eq(ACCOUNT_ID), any(Set.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSearchOrgScopedEnvironmentsFiltered_WithRBACFiltering() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .uniqueId(ORG_IDENTIFIER)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();

    Environment environment1 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(ORG_IDENTIFIER)
                                   .type(EnvironmentType.Production)
                                   .yaml(envYamlV0)
                                   .build();
    Environment environment2 = Environment.builder()
                                   .identifier("identifier2")
                                   .name("name2")
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(ORG_IDENTIFIER)
                                   .type(EnvironmentType.PreProduction)
                                   .yaml(envYamlV0)
                                   .build();

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null))).thenReturn(scopeInfo);

    Criteria mockCriteria = new Criteria();
    when(environmentFilterHelper.createCriteriaForGetList(any(), eq(false), any(), any(), any(), eq(false), any()))
        .thenReturn(mockCriteria);
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any(Set.class)))
        .thenReturn(Collections.singletonMap(ORG_IDENTIFIER, Optional.of(scopeInfo)));

    // User doesn't have permission for all environments
    when(environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null), eq(ENVIRONMENT_VIEW_PERMISSION)))
        .thenReturn(false);

    // First call for unpaged list
    doReturn(new PageImpl<>(List.of(environment1, environment2)))
        .when(environmentService)
        .list(any(Criteria.class), eq(Pageable.unpaged()));

    // Only environment1 is permitted
    when(environmentRbacHelper.getPermittedEnvironmentsList(anyList())).thenReturn(List.of(environment1));

    // Second call for paged list after RBAC filtering
    doReturn(new PageImpl<>(List.of(environment1)))
        .when(environmentService)
        .list(any(Criteria.class), any(Pageable.class));

    Response response = orgEnvironmentsApi.searchOrgScopedEnvironmentsFiltered(
        ORG_IDENTIFIER, 0, 10, null, null, null, null, null, null, null, false, null, null, null, ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    List<EnvironmentResponse> envResponseList = (List<EnvironmentResponse>) response.getEntity();
    assertThat(envResponseList.size()).isEqualTo(1);
    assertThat(envResponseList.get(0).getEnvironment().getIdentifier()).isEqualTo(IDENTIFIER);

    verify(environmentRbacHelper, times(1))
        .hasRequiredPermissionForAllEnvironments(
            eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null), eq(ENVIRONMENT_VIEW_PERMISSION));
    verify(environmentRbacHelper, times(1)).getPermittedEnvironmentsList(anyList());
    verify(environmentService, times(1)).list(any(Criteria.class), eq(Pageable.unpaged()));
    verify(environmentService, times(2)).list(any(Criteria.class), any(Pageable.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSearchOrgScopedEnvironmentsFiltered_WithAllFilters() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .uniqueId(ORG_IDENTIFIER)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();

    Environment environment1 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(ORG_IDENTIFIER)
                                   .type(EnvironmentType.Production)
                                   .yaml(envYamlV0)
                                   .build();

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null))).thenReturn(scopeInfo);

    Criteria mockCriteria = new Criteria();
    when(environmentFilterHelper.createCriteriaForGetList(
             any(), eq(false), eq("testSearch"), eq("filter1"), any(), eq(true), eq("testRepo")))
        .thenReturn(mockCriteria);
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any(Set.class)))
        .thenReturn(Collections.singletonMap(ORG_IDENTIFIER, Optional.of(scopeInfo)));

    when(environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null), eq(ENVIRONMENT_VIEW_PERMISSION)))
        .thenReturn(true);

    when(serviceResourceApiUtils.mapSort(eq("name"), eq("ASC"))).thenReturn("name,ASC");

    doReturn(new PageImpl<>(List.of(environment1)))
        .when(environmentService)
        .list(any(Criteria.class), any(Pageable.class));

    Response response = orgEnvironmentsApi.searchOrgScopedEnvironmentsFiltered(ORG_IDENTIFIER, 0, 50, "testSearch",
        List.of(IDENTIFIER, "env2"), "name", "ASC", List.of(NAME, "name2"), "test description", "filter1", true,
        "testRepo", List.of("tag1:value1", "tag2:value2"), "Production", ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    List<EnvironmentResponse> envResponseList = (List<EnvironmentResponse>) response.getEntity();
    assertThat(envResponseList.size()).isEqualTo(1);
    assertThat(envResponseList.get(0).getEnvironment().getIdentifier()).isEqualTo(IDENTIFIER);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(environmentService, times(1)).list(any(Criteria.class), pageableCaptor.capture());

    Pageable capturedPageable = pageableCaptor.getValue();
    assertThat(capturedPageable.getPageNumber()).isEqualTo(0);
    assertThat(capturedPageable.getPageSize()).isEqualTo(50);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSearchOrgScopedEnvironmentsFiltered_WithPagination() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .uniqueId(ORG_IDENTIFIER)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();

    Environment environment1 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(ORG_IDENTIFIER)
                                   .type(EnvironmentType.Production)
                                   .yaml(envYamlV0)
                                   .build();

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null))).thenReturn(scopeInfo);

    Criteria mockCriteria = new Criteria();
    when(environmentFilterHelper.createCriteriaForGetList(any(), eq(false), any(), any(), any(), eq(false), any()))
        .thenReturn(mockCriteria);
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any(Set.class)))
        .thenReturn(Collections.singletonMap(ORG_IDENTIFIER, Optional.of(scopeInfo)));

    when(environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null), eq(ENVIRONMENT_VIEW_PERMISSION)))
        .thenReturn(true);

    PageImpl<Environment> pagedResult = new PageImpl<>(List.of(environment1), PageRequest.of(2, 5), 15);
    doReturn(pagedResult).when(environmentService).list(any(Criteria.class), any(Pageable.class));

    Response response = orgEnvironmentsApi.searchOrgScopedEnvironmentsFiltered(
        ORG_IDENTIFIER, 2, 5, null, null, null, null, null, null, null, false, null, null, null, ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    List<EnvironmentResponse> envResponseList = (List<EnvironmentResponse>) response.getEntity();
    assertThat(envResponseList.size()).isEqualTo(1);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(environmentService, times(1)).list(any(Criteria.class), pageableCaptor.capture());

    Pageable capturedPageable = pageableCaptor.getValue();
    assertThat(capturedPageable.getPageNumber()).isEqualTo(2);
    assertThat(capturedPageable.getPageSize()).isEqualTo(5);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSearchOrgScopedEnvironmentsFiltered_WithIncludeAllAccessibleAtScope() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .uniqueId(ORG_IDENTIFIER)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();

    Environment environment1 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(ORG_IDENTIFIER)
                                   .type(EnvironmentType.Production)
                                   .yaml(envYamlV0)
                                   .build();

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null))).thenReturn(scopeInfo);

    Criteria mockCriteria = new Criteria();
    when(environmentFilterHelper.createCriteriaForGetList(
             eq(scopeInfo), eq(false), any(), any(), any(), eq(true), any()))
        .thenReturn(mockCriteria);

    when(environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(null), eq(ENVIRONMENT_VIEW_PERMISSION)))
        .thenReturn(true);

    doReturn(new PageImpl<>(List.of(environment1)))
        .when(environmentService)
        .list(any(Criteria.class), any(Pageable.class));

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any(Set.class)))
        .thenReturn(Collections.singletonMap(ORG_IDENTIFIER, Optional.of(scopeInfo)));

    Response response = orgEnvironmentsApi.searchOrgScopedEnvironmentsFiltered(
        ORG_IDENTIFIER, 0, 10, null, null, null, null, null, null, null, true, null, null, null, ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    verify(environmentFilterHelper, times(1))
        .createCriteriaForGetList(eq(scopeInfo), eq(false), any(), any(), any(), eq(true), any());
  }
}
