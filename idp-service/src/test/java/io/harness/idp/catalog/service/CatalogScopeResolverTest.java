/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.cache.CatalogOrgCache;
import io.harness.idp.catalog.cache.CatalogProjectCache;
import io.harness.idp.catalog.cache.CatalogScopeTopologyCache;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@OwnedBy(HarnessTeam.IDP)
@SuppressWarnings("unchecked")
public class CatalogScopeResolverTest extends CategoryTest {
  private static final String TEST_ACCOUNT = "testAccount";
  private static final String ORG_1 = "org1";
  private static final String ORG_2 = "org2";
  private static final String PROJECT_1 = "project1";
  private static final String PROJECT_2 = "project2";
  private static final String PROJECT_3 = "project3";
  private static final String UNIQUE_ID_ACCOUNT = "testAccount";
  private static final String UNIQUE_ID_ORG_1 = "testAccount/org1";
  private static final String UNIQUE_ID_ORG_2 = "testAccount/org2";
  private static final String UNIQUE_ID_PROJECT_1 = "testAccount/org1/project1";
  private static final String UNIQUE_ID_PROJECT_2 = "testAccount/org1/project2";
  private static final String UNIQUE_ID_PROJECT_3 = "testAccount/org1/project3";

  @Mock private CatalogScopeTopologyCache scopeTopologyCache;
  @Mock private CatalogOrgCache orgCache;
  @Mock private CatalogProjectCache projectCache;
  @Mock private OrganizationClient organizationClient;
  @Mock private ProjectClient projectClient;
  @Mock private ScopeInfoClient scopeInfoClient;

  @Mock private Call listOrgsCall;
  @Mock private Call orgScopeInfoCall;
  @Mock private Call listProjectsOrg1Call;
  @Mock private Call listProjectsOrg2Call;
  @Mock private Call projectScopeInfoOrg1Call;
  @Mock private Call projectScopeInfoOrg2Call;

  private final ExecutorService executorService = MoreExecutors.newDirectExecutorService();

  private CatalogScopeResolver scopeResolver;
  private MockedStatic<NGRestUtils> ngRestUtilsMock;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    scopeResolver = new CatalogScopeResolver(scopeTopologyCache, orgCache, projectCache, organizationClient,
        projectClient, scopeInfoClient, executorService);
    ngRestUtilsMock = mockStatic(NGRestUtils.class);
  }

  @After
  public void tearDown() {
    ngRestUtilsMock.close();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_AccountWildcard_BuildsFullTopology() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);
    mockPlatformDrivenTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.*");

    assertThat(result.getScopeInfos()).hasSize(5);
    assertThat(result.getScopeInfos())
        .extracting(ScopeInfo::getUniqueId)
        .containsExactlyInAnyOrder(
            UNIQUE_ID_ACCOUNT, UNIQUE_ID_ORG_1, UNIQUE_ID_ORG_2, UNIQUE_ID_PROJECT_1, UNIQUE_ID_PROJECT_2);
    assertThat(result.getTopology()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_OrgWildcard() {
    mockBuildTestTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.org1.*");

    assertThat(result.getScopeInfos()).hasSize(3);
    assertThat(result.getScopeInfos())
        .extracting(ScopeInfo::getUniqueId)
        .containsExactlyInAnyOrder(UNIQUE_ID_ORG_1, UNIQUE_ID_PROJECT_1, UNIQUE_ID_PROJECT_2);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_SpecificProject() {
    mockBuildTestTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.org1.project1");

    assertThat(result.getScopeInfos()).hasSize(1);
    assertThat(result.getScopeInfos().get(0).getUniqueId()).isEqualTo(UNIQUE_ID_PROJECT_1);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_MultipleScopePatterns() {
    mockBuildTestTopology();

    CatalogScopeResolver.ScopeResolveResult result =
        scopeResolver.resolve(TEST_ACCOUNT, "account.org1.project1,account.org2.*");

    assertThat(result.getScopeInfos()).hasSize(2);
    assertThat(result.getScopeInfos())
        .extracting(ScopeInfo::getUniqueId)
        .containsExactlyInAnyOrder(UNIQUE_ID_PROJECT_1, UNIQUE_ID_ORG_2);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_NonExistentOrg_FallsBackToAccount() {
    mockBuildTestTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.nonexistent.*");

    assertThat(result.getScopeInfos()).hasSize(1);
    assertThat(result.getScopeInfos().get(0).getUniqueId()).isEqualTo(UNIQUE_ID_ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_EmptyScope_FallsBackToAccount() {
    mockBuildTestTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "");

    assertThat(result.getScopeInfos()).hasSize(1);
    assertThat(result.getScopeInfos().get(0).getUniqueId()).isEqualTo(UNIQUE_ID_ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_NullScope_FallsBackToAccount() {
    mockBuildTestTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, null);

    assertThat(result.getScopeInfos()).hasSize(1);
    assertThat(result.getScopeInfos().get(0).getUniqueId()).isEqualTo(UNIQUE_ID_ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_CacheMiss_BuildsTopology() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);
    mockPlatformDrivenTopologyWithSingleOrg();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.*");

    assertThat(result.getTopology()).isNotNull();
    verify(scopeTopologyCache, times(1)).put(eq(TEST_ACCOUNT), any(ScopeTopology.class));
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_CacheMiss_ListsOrgsAndProjects() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);
    mockPlatformDrivenTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.*");

    assertThat(result.getTopology()).isNotNull();
    assertThat(result.getTopology().getOrgs()).isNotEmpty();
    assertThat(result.getTopology().getOrgs()).containsKey(ORG_1);
    assertThat(result.getTopology().getOrgs()).containsKey(ORG_2);
    assertThat(result.getTopology().getOrgs().get(ORG_1).getProjects()).containsKey(PROJECT_1);
    assertThat(result.getTopology().getOrgs().get(ORG_1).getProjects()).containsKey(PROJECT_2);
    verify(orgCache).putAll(eq(TEST_ACCOUNT), any());
    verify(scopeTopologyCache).put(eq(TEST_ACCOUNT), any(ScopeTopology.class));
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_CacheMiss_OrgListingFails_ReturnsEmpty() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);
    when(organizationClient.listAllOrganizations(eq(TEST_ACCOUNT), any(), isNull())).thenReturn(listOrgsCall);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(listOrgsCall)))
        .thenThrow(new RuntimeException("Organization service unavailable"));

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.*");

    assertThat(result.getTopology()).isNotNull();
    assertThat(result.getTopology().getOrgs()).isEmpty();
    assertThat(result.getScopeInfos()).hasSize(1);
    assertThat(result.getScopeInfos().get(0).getScopeType()).isEqualTo(ScopeLevel.ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_CacheMiss_OrgScopeInfoFails_SkipsOrg() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);

    stubListOrgs(ORG_1);
    when(scopeInfoClient.getScopeInfoList(eq(TEST_ACCOUNT), anySet())).thenReturn(orgScopeInfoCall);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(orgScopeInfoCall)))
        .thenThrow(new RuntimeException("ScopeInfo service unavailable"));

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.*");

    assertThat(result.getTopology()).isNotNull();
    assertThat(result.getTopology().getOrgs()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_CacheMiss_ProjectListingFails_OrgPresentWithEmptyProjects() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);

    stubListOrgs(ORG_1);
    stubOrgScopeInfos(Set.of(ORG_1));

    when(projectClient.listWithMultiOrg(eq(TEST_ACCOUNT), eq(Set.of(ORG_1)), anyBoolean(), isNull(), isNull(), isNull(),
             anyInt(), anyInt(), isNull(), anyBoolean()))
        .thenReturn(listProjectsOrg1Call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(listProjectsOrg1Call)))
        .thenThrow(new RuntimeException("Project service unavailable"));

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.*");

    assertThat(result.getTopology()).isNotNull();
    assertThat(result.getTopology().getOrgs()).containsKey(ORG_1);
    assertThat(result.getTopology().getOrgs().get(ORG_1).getProjects()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_CacheMiss_NoOrgs_ReturnsAccountScope() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);

    when(organizationClient.listAllOrganizations(eq(TEST_ACCOUNT), any(), isNull())).thenReturn(listOrgsCall);
    PageResponse<OrganizationResponse> emptyOrgResponse =
        PageResponse.<OrganizationResponse>builder().content(Collections.emptyList()).build();
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(listOrgsCall))).thenReturn(emptyOrgResponse);

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.*");

    assertThat(result.getTopology()).isNotNull();
    assertThat(result.getTopology().getOrgs()).isEmpty();
    assertThat(result.getScopeInfos()).hasSize(1);
    assertThat(result.getScopeInfos().get(0).getScopeType()).isEqualTo(ScopeLevel.ACCOUNT);
    assertThat(result.getScopeInfos().get(0).getUniqueId()).isEqualTo(TEST_ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_CacheMiss_OrgWithNoProjects() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);

    stubListOrgs(ORG_1);
    stubOrgScopeInfos(Set.of(ORG_1));

    when(projectClient.listWithMultiOrg(eq(TEST_ACCOUNT), eq(Set.of(ORG_1)), anyBoolean(), isNull(), isNull(), isNull(),
             anyInt(), anyInt(), isNull(), anyBoolean()))
        .thenReturn(listProjectsOrg1Call);
    PageResponse<ProjectResponse> emptyProjectResponse =
        PageResponse.<ProjectResponse>builder().content(Collections.emptyList()).build();
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(listProjectsOrg1Call))).thenReturn(emptyProjectResponse);

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.*");

    assertThat(result.getTopology()).isNotNull();
    assertThat(result.getTopology().getOrgs()).containsKey(ORG_1);
    assertThat(result.getTopology().getOrgs().get(ORG_1).getUniqueId()).isEqualTo(UNIQUE_ID_ORG_1);
    assertThat(result.getTopology().getOrgs().get(ORG_1).getProjects()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testBuildScopeTopology_AllPlatformScopesIncluded() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);
    mockPlatformDrivenTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.*");

    assertThat(result.getTopology()).isNotNull();
    ScopeTopology.OrgNode orgNode = result.getTopology().getOrgs().get(ORG_1);
    assertThat(orgNode.getProjects()).hasSize(2);
    assertThat(orgNode.getProjects()).containsKey(PROJECT_1);
    assertThat(orgNode.getProjects()).containsKey(PROJECT_2);
    assertThat(orgNode.getProjects().get(PROJECT_1)).isEqualTo(UNIQUE_ID_PROJECT_1);
    assertThat(orgNode.getProjects().get(PROJECT_2)).isEqualTo(UNIQUE_ID_PROJECT_2);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_SpecificOrg() {
    mockBuildTestTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.org1");

    assertThat(result.getScopeInfos()).hasSize(1);
    assertThat(result.getScopeInfos().get(0).getUniqueId()).isEqualTo(UNIQUE_ID_ORG_1);
    assertThat(result.getScopeInfos().get(0).getScopeType()).isEqualTo(ScopeLevel.ORGANIZATION);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_AccountOrgToken() {
    mockBuildTestTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.org");

    assertThat(result.getScopeInfos()).hasSize(2);
    assertThat(result.getScopeInfos())
        .extracting(ScopeInfo::getUniqueId)
        .containsExactlyInAnyOrder(UNIQUE_ID_ORG_1, UNIQUE_ID_ORG_2);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_AccountOrgProjectToken() {
    mockBuildTestTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.org.project");

    assertThat(result.getScopeInfos()).hasSize(2);
    assertThat(result.getScopeInfos())
        .extracting(ScopeInfo::getUniqueId)
        .containsExactlyInAnyOrder(UNIQUE_ID_PROJECT_1, UNIQUE_ID_PROJECT_2);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_AccountToken() {
    mockBuildTestTopology();

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account");

    assertThat(result.getScopeInfos()).hasSize(1);
    assertThat(result.getScopeInfos().get(0).getUniqueId()).isEqualTo(UNIQUE_ID_ACCOUNT);
    assertThat(result.getScopeInfos().get(0).getScopeType()).isEqualTo(ScopeLevel.ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_CacheMiss_ProjectScopeInfoPartialFailure() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);

    stubListOrgs(ORG_1);
    stubOrgScopeInfos(Set.of(ORG_1));

    ProjectDTO proj1Dto = ProjectDTO.builder().identifier(PROJECT_1).orgIdentifier(ORG_1).name("Project One").build();
    ProjectDTO proj2Dto = ProjectDTO.builder().identifier(PROJECT_2).orgIdentifier(ORG_1).name("Project Two").build();
    ProjectResponse projResp1 = ProjectResponse.builder().project(proj1Dto).build();
    ProjectResponse projResp2 = ProjectResponse.builder().project(proj2Dto).build();
    PageResponse<ProjectResponse> projPageResponse =
        PageResponse.<ProjectResponse>builder().content(Arrays.asList(projResp1, projResp2)).build();

    when(projectClient.listWithMultiOrg(eq(TEST_ACCOUNT), eq(Set.of(ORG_1)), anyBoolean(), isNull(), isNull(), isNull(),
             anyInt(), anyInt(), isNull(), anyBoolean()))
        .thenReturn(listProjectsOrg1Call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(listProjectsOrg1Call))).thenReturn(projPageResponse);

    List<ScopeInfo> projectScopeInfos = Collections.singletonList(ScopeInfo.builder()
                                                                      .orgIdentifier(ORG_1)
                                                                      .projectIdentifier(PROJECT_1)
                                                                      .uniqueId(UNIQUE_ID_PROJECT_1)
                                                                      .scopeType(ScopeLevel.PROJECT)
                                                                      .build());
    when(scopeInfoClient.getScopeInfoList(eq(TEST_ACCOUNT), eq(ORG_1), anySet())).thenReturn(projectScopeInfoOrg1Call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(projectScopeInfoOrg1Call))).thenReturn(projectScopeInfos);

    CatalogScopeResolver.ScopeResolveResult result = scopeResolver.resolve(TEST_ACCOUNT, "account.*");

    assertThat(result.getTopology()).isNotNull();
    ScopeTopology.OrgNode orgNode = result.getTopology().getOrgs().get(ORG_1);
    assertThat(orgNode.getProjects()).hasSize(1);
    assertThat(orgNode.getProjects()).containsKey(PROJECT_1);
    assertThat(orgNode.getProjects()).doesNotContainKey(PROJECT_2);
  }

  private void stubListOrgs(String... orgIds) {
    PageResponse<OrganizationResponse> orgPageResponse = mockOrgListResponse(orgIds);
    when(organizationClient.listAllOrganizations(eq(TEST_ACCOUNT), any(), isNull())).thenReturn(listOrgsCall);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(listOrgsCall))).thenReturn(orgPageResponse);
  }

  private void stubOrgScopeInfos(Set<String> orgIds) {
    List<ScopeInfo> orgScopeInfos = new java.util.ArrayList<>();
    for (String orgId : orgIds) {
      String uniqueId = TEST_ACCOUNT + "/" + orgId;
      orgScopeInfos.add(
          ScopeInfo.builder().orgIdentifier(orgId).uniqueId(uniqueId).scopeType(ScopeLevel.ORGANIZATION).build());
    }
    when(scopeInfoClient.getScopeInfoList(eq(TEST_ACCOUNT), anySet())).thenReturn(orgScopeInfoCall);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(orgScopeInfoCall))).thenReturn(orgScopeInfos);
  }

  private PageResponse<OrganizationResponse> mockOrgListResponse(String... orgIds) {
    List<OrganizationResponse> responses = new java.util.ArrayList<>();
    for (String orgId : orgIds) {
      OrganizationDTO dto = OrganizationDTO.builder().identifier(orgId).name(orgId + " Name").build();
      responses.add(OrganizationResponse.builder().organization(dto).build());
    }
    return PageResponse.<OrganizationResponse>builder().content(responses).build();
  }

  private void mockPlatformDrivenTopology() {
    stubListOrgs(ORG_1, ORG_2);
    stubOrgScopeInfos(Set.of(ORG_1, ORG_2));

    ProjectDTO proj1Dto = ProjectDTO.builder().identifier(PROJECT_1).orgIdentifier(ORG_1).name("Project One").build();
    ProjectDTO proj2Dto = ProjectDTO.builder().identifier(PROJECT_2).orgIdentifier(ORG_1).name("Project Two").build();
    ProjectResponse projResp1 = ProjectResponse.builder().project(proj1Dto).build();
    ProjectResponse projResp2 = ProjectResponse.builder().project(proj2Dto).build();
    PageResponse<ProjectResponse> projPageResponseOrg1 =
        PageResponse.<ProjectResponse>builder().content(Arrays.asList(projResp1, projResp2)).build();

    PageResponse<ProjectResponse> emptyProjectResponse =
        PageResponse.<ProjectResponse>builder().content(Collections.emptyList()).build();

    when(projectClient.listWithMultiOrg(eq(TEST_ACCOUNT), eq(Set.of(ORG_1)), anyBoolean(), isNull(), isNull(), isNull(),
             anyInt(), anyInt(), isNull(), anyBoolean()))
        .thenReturn(listProjectsOrg1Call);
    when(projectClient.listWithMultiOrg(eq(TEST_ACCOUNT), eq(Set.of(ORG_2)), anyBoolean(), isNull(), isNull(), isNull(),
             anyInt(), anyInt(), isNull(), anyBoolean()))
        .thenReturn(listProjectsOrg2Call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(listProjectsOrg1Call))).thenReturn(projPageResponseOrg1);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(listProjectsOrg2Call))).thenReturn(emptyProjectResponse);

    List<ScopeInfo> projectScopeInfosOrg1 = Arrays.asList(ScopeInfo.builder()
                                                              .orgIdentifier(ORG_1)
                                                              .projectIdentifier(PROJECT_1)
                                                              .uniqueId(UNIQUE_ID_PROJECT_1)
                                                              .scopeType(ScopeLevel.PROJECT)
                                                              .build(),
        ScopeInfo.builder()
            .orgIdentifier(ORG_1)
            .projectIdentifier(PROJECT_2)
            .uniqueId(UNIQUE_ID_PROJECT_2)
            .scopeType(ScopeLevel.PROJECT)
            .build());

    when(scopeInfoClient.getScopeInfoList(eq(TEST_ACCOUNT), eq(ORG_1), anySet())).thenReturn(projectScopeInfoOrg1Call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(projectScopeInfoOrg1Call))).thenReturn(projectScopeInfosOrg1);
  }

  private void mockPlatformDrivenTopologyWithSingleOrg() {
    stubListOrgs(ORG_1);
    stubOrgScopeInfos(Set.of(ORG_1));

    ProjectDTO proj1Dto = ProjectDTO.builder().identifier(PROJECT_1).orgIdentifier(ORG_1).name("Project One").build();
    ProjectResponse projResp1 = ProjectResponse.builder().project(proj1Dto).build();
    PageResponse<ProjectResponse> projPageResponse =
        PageResponse.<ProjectResponse>builder().content(Collections.singletonList(projResp1)).build();

    when(projectClient.listWithMultiOrg(eq(TEST_ACCOUNT), eq(Set.of(ORG_1)), anyBoolean(), isNull(), isNull(), isNull(),
             anyInt(), anyInt(), isNull(), anyBoolean()))
        .thenReturn(listProjectsOrg1Call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(listProjectsOrg1Call))).thenReturn(projPageResponse);

    List<ScopeInfo> projectScopeInfos = Collections.singletonList(ScopeInfo.builder()
                                                                      .orgIdentifier(ORG_1)
                                                                      .projectIdentifier(PROJECT_1)
                                                                      .uniqueId(UNIQUE_ID_PROJECT_1)
                                                                      .scopeType(ScopeLevel.PROJECT)
                                                                      .build());
    when(scopeInfoClient.getScopeInfoList(eq(TEST_ACCOUNT), eq(ORG_1), anySet())).thenReturn(projectScopeInfoOrg1Call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(projectScopeInfoOrg1Call))).thenReturn(projectScopeInfos);
  }

  private void mockBuildTestTopology() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);
    mockPlatformDrivenTopology();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_CacheHit_AccountLevel() {
    ScopeTopology topology = buildTestTopology();
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(topology);

    String result = scopeResolver.resolveNamespaceToUniqueId(TEST_ACCOUNT, "account");

    assertThat(result).isEqualTo(UNIQUE_ID_ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_CacheHit_OrgLevel() {
    ScopeTopology topology = buildTestTopology();
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(topology);

    String result = scopeResolver.resolveNamespaceToUniqueId(TEST_ACCOUNT, "account.org1");

    assertThat(result).isEqualTo(UNIQUE_ID_ORG_1);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_CacheHit_ProjectLevel() {
    ScopeTopology topology = buildTestTopology();
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(topology);

    String result = scopeResolver.resolveNamespaceToUniqueId(TEST_ACCOUNT, "account.org1.project1");

    assertThat(result).isEqualTo(UNIQUE_ID_PROJECT_1);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_CacheMiss_FallsBackToScopeInfoClient() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);

    Call scopeInfoCall = org.mockito.Mockito.mock(Call.class);
    when(scopeInfoClient.getScopeInfo(eq(TEST_ACCOUNT), eq("org1"), eq("project1"))).thenReturn(scopeInfoCall);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(TEST_ACCOUNT)
                              .orgIdentifier(ORG_1)
                              .projectIdentifier(PROJECT_1)
                              .uniqueId(UNIQUE_ID_PROJECT_1)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(scopeInfoCall))).thenReturn(scopeInfo);

    String result = scopeResolver.resolveNamespaceToUniqueId(TEST_ACCOUNT, "account.org1.project1");

    assertThat(result).isEqualTo(UNIQUE_ID_PROJECT_1);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_CacheMiss_ScopeInfoClientFails_ReturnsNull() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);

    Call scopeInfoCall = org.mockito.Mockito.mock(Call.class);
    when(scopeInfoClient.getScopeInfo(eq(TEST_ACCOUNT), eq("unknownOrg"), isNull())).thenReturn(scopeInfoCall);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(scopeInfoCall)))
        .thenThrow(new RuntimeException("ScopeInfo service unavailable"));

    String result = scopeResolver.resolveNamespaceToUniqueId(TEST_ACCOUNT, "account.unknownOrg");

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_NullNamespace_ReturnsNull() {
    String result = scopeResolver.resolveNamespaceToUniqueId(TEST_ACCOUNT, null);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_EmptyNamespace_ReturnsNull() {
    String result = scopeResolver.resolveNamespaceToUniqueId(TEST_ACCOUNT, "");
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_TopologyCacheHitButNamespaceUnresolvable_FallsBack() {
    ScopeTopology topology = buildTestTopology();
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(topology);

    Call scopeInfoCall = org.mockito.Mockito.mock(Call.class);
    when(scopeInfoClient.getScopeInfo(eq(TEST_ACCOUNT), eq("unknownOrg"), isNull())).thenReturn(scopeInfoCall);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(TEST_ACCOUNT)
                              .orgIdentifier("unknownOrg")
                              .uniqueId("resolvedUniqueId")
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(scopeInfoCall))).thenReturn(scopeInfo);

    String result = scopeResolver.resolveNamespaceToUniqueId(TEST_ACCOUNT, "account.unknownOrg");

    assertThat(result).isEqualTo("resolvedUniqueId");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrBuildTopology_CacheHit() {
    ScopeTopology topology = buildTestTopology();
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(topology);

    ScopeTopology result = scopeResolver.getOrBuildTopology(TEST_ACCOUNT);

    assertThat(result).isSameAs(topology);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrBuildTopology_CacheMiss_BuildsTopology() {
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(null);
    mockPlatformDrivenTopologyWithSingleOrg();

    ScopeTopology result = scopeResolver.getOrBuildTopology(TEST_ACCOUNT);

    assertThat(result).isNotNull();
    assertThat(result.getOrgs()).containsKey(ORG_1);
    verify(scopeTopologyCache).put(eq(TEST_ACCOUNT), any(ScopeTopology.class));
  }

  @Test
  @Owner(developers = OwnerRule.VIGNESWARA)
  public void testResolveSingleScopeInfoAccountLevel() {
    ScopeTopology topology = buildTestTopology();
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(topology);
    ScopeInfo scopeInfo = scopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT, "account");
    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getScopeType()).isEqualTo(ScopeLevel.ACCOUNT);
    assertThat(scopeInfo.getUniqueId()).isEqualTo(UNIQUE_ID_ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.VIGNESWARA)
  public void testResolveSingleScopeInfoOrgLevel() {
    ScopeTopology topology = buildTestTopology();
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(topology);
    ScopeInfo scopeInfo = scopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT, "account.org1");
    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getScopeType()).isEqualTo(ScopeLevel.ORGANIZATION);
    assertThat(scopeInfo.getUniqueId()).isEqualTo(UNIQUE_ID_ORG_1);
  }

  @Test
  @Owner(developers = OwnerRule.VIGNESWARA)
  public void testResolveSingleScopeInfoProjectLevel() {
    ScopeTopology topology = buildTestTopology();
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(topology);
    ScopeInfo scopeInfo = scopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT, "account.org1.project1");
    assertThat(scopeInfo).isNotNull();
    assertThat(scopeInfo.getScopeType()).isEqualTo(ScopeLevel.PROJECT);
    assertThat(scopeInfo.getUniqueId()).isEqualTo(UNIQUE_ID_PROJECT_1);
  }

  @Test
  @Owner(developers = OwnerRule.VIGNESWARA)
  public void testResolveSingleScopeInfoProjectLevelCacheMiss() {
    ScopeTopology topology = buildTestTopology();
    when(scopeTopologyCache.get(TEST_ACCOUNT)).thenReturn(topology);
    Call scopeInfoCall = org.mockito.Mockito.mock(Call.class);
    when(scopeInfoClient.getScopeInfo(eq(TEST_ACCOUNT), eq("org1"), eq("project3"))).thenReturn(scopeInfoCall);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(TEST_ACCOUNT)
                              .orgIdentifier(ORG_1)
                              .projectIdentifier(PROJECT_3)
                              .uniqueId(UNIQUE_ID_PROJECT_3)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(eq(scopeInfoCall))).thenReturn(scopeInfo);
    ScopeInfo result = scopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT, "account.org1.project3");
    assertThat(result).isNotNull();
    assertThat(result.getScopeType()).isEqualTo(ScopeLevel.PROJECT);
    assertThat(result.getUniqueId()).isEqualTo(UNIQUE_ID_PROJECT_3);
  }

  private ScopeTopology buildTestTopology() {
    Map<String, String> org1Projects = new HashMap<>();
    org1Projects.put(PROJECT_1, UNIQUE_ID_PROJECT_1);
    org1Projects.put(PROJECT_2, UNIQUE_ID_PROJECT_2);

    ScopeTopology.OrgNode org1Node =
        ScopeTopology.OrgNode.builder().uniqueId(UNIQUE_ID_ORG_1).projects(org1Projects).build();
    ScopeTopology.OrgNode org2Node =
        ScopeTopology.OrgNode.builder().uniqueId(UNIQUE_ID_ORG_2).projects(new HashMap<>()).build();

    Map<String, ScopeTopology.OrgNode> orgs = new HashMap<>();
    orgs.put(ORG_1, org1Node);
    orgs.put(ORG_2, org2Node);

    return ScopeTopology.builder().accountUniqueId(TEST_ACCOUNT).orgs(orgs).build();
  }
}
