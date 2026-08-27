/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.cache.CachedOrgInfo;
import io.harness.idp.catalog.cache.CachedProjectInfo;
import io.harness.idp.catalog.cache.CatalogOrgCache;
import io.harness.idp.catalog.cache.CatalogProjectCache;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class CatalogOrgProjectServiceTest extends CategoryTest {
  private static final String TEST_ACCOUNT = "testAccount";
  private static final String ORG_1 = "org1";
  private static final String ORG_2 = "org2";
  private static final String ORG_1_NAME = "Organization One";
  private static final String ORG_2_NAME = "Organization Two";
  private static final String PROJ_1 = "proj1";
  private static final String PROJ_2 = "proj2";
  private static final String PROJ_3 = "proj3";
  private static final String PROJ_1_NAME = "Project One";
  private static final String PROJ_2_NAME = "Project Two";
  private static final String PROJ_3_NAME = "Project Three";

  @Mock private CatalogOrgCache orgCache;
  @Mock private CatalogProjectCache projectCache;
  @Mock private OrganizationClient organizationClient;
  @Mock private ProjectClient projectClient;

  private CatalogOrgProjectService catalogOrgProjectService;
  private MockedStatic<NGRestUtils> ngRestUtilsMock;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    catalogOrgProjectService = new CatalogOrgProjectService(orgCache, projectCache, organizationClient, projectClient);
    ngRestUtilsMock = mockStatic(NGRestUtils.class);
  }

  @After
  public void tearDown() {
    ngRestUtilsMock.close();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgName_CacheHit() {
    CachedOrgInfo cachedOrgInfo = CachedOrgInfo.builder().identifier(ORG_1).name(ORG_1_NAME).build();
    when(orgCache.get(TEST_ACCOUNT, ORG_1)).thenReturn(cachedOrgInfo);

    String result = catalogOrgProjectService.getOrgName(TEST_ACCOUNT, ORG_1);

    assertThat(result).isEqualTo(ORG_1_NAME);
    verify(orgCache).get(TEST_ACCOUNT, ORG_1);
    verifyNoInteractions(organizationClient);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgName_CacheMiss_FetchesFromClient() {
    when(orgCache.get(TEST_ACCOUNT, ORG_1)).thenReturn(null);

    OrganizationDTO orgDTO = OrganizationDTO.builder().identifier(ORG_1).name(ORG_1_NAME).build();
    OrganizationResponse orgResponse = OrganizationResponse.builder().organization(orgDTO).build();
    when(organizationClient.getOrganization(ORG_1, TEST_ACCOUNT)).thenReturn(null);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.of(orgResponse));

    String result = catalogOrgProjectService.getOrgName(TEST_ACCOUNT, ORG_1);

    assertThat(result).isEqualTo(ORG_1_NAME);
    verify(orgCache).put(
        eq(TEST_ACCOUNT), eq(ORG_1), eq(CachedOrgInfo.builder().identifier(ORG_1).name(ORG_1_NAME).build()));
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgName_CacheMiss_ClientFailure() {
    when(orgCache.get(TEST_ACCOUNT, ORG_1)).thenReturn(null);

    when(organizationClient.getOrganization(ORG_1, TEST_ACCOUNT)).thenReturn(null);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenThrow(new RuntimeException("Connection refused"));

    String result = catalogOrgProjectService.getOrgName(TEST_ACCOUNT, ORG_1);

    assertThat(result).isNull();
    verify(orgCache, never()).put(any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgName_NullOrgId() {
    String result = catalogOrgProjectService.getOrgName(TEST_ACCOUNT, null);

    assertThat(result).isNull();
    verifyNoInteractions(orgCache, organizationClient);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgName_EmptyOrgId() {
    String result = catalogOrgProjectService.getOrgName(TEST_ACCOUNT, "");

    assertThat(result).isNull();
    verifyNoInteractions(orgCache, organizationClient);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectName_CacheHit() {
    CachedProjectInfo cachedProjectInfo =
        CachedProjectInfo.builder().identifier(PROJ_1).orgIdentifier(ORG_1).name(PROJ_1_NAME).build();
    when(projectCache.get(TEST_ACCOUNT, ORG_1, PROJ_1)).thenReturn(cachedProjectInfo);

    String result = catalogOrgProjectService.getProjectName(TEST_ACCOUNT, ORG_1, PROJ_1);

    assertThat(result).isEqualTo(PROJ_1_NAME);
    verify(projectCache).get(TEST_ACCOUNT, ORG_1, PROJ_1);
    verifyNoInteractions(projectClient);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectName_CacheMiss_FetchesFromClient() {
    when(projectCache.get(TEST_ACCOUNT, ORG_1, PROJ_1)).thenReturn(null);

    ProjectDTO projectDTO = ProjectDTO.builder().identifier(PROJ_1).orgIdentifier(ORG_1).name(PROJ_1_NAME).build();
    ProjectResponse projectResponse = ProjectResponse.builder().project(projectDTO).build();
    when(projectClient.getProject(PROJ_1, TEST_ACCOUNT, ORG_1)).thenReturn(null);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.of(projectResponse));

    String result = catalogOrgProjectService.getProjectName(TEST_ACCOUNT, ORG_1, PROJ_1);

    assertThat(result).isEqualTo(PROJ_1_NAME);
    verify(projectCache)
        .put(eq(TEST_ACCOUNT), eq(ORG_1), eq(PROJ_1),
            eq(CachedProjectInfo.builder().identifier(PROJ_1).orgIdentifier(ORG_1).name(PROJ_1_NAME).build()));
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectName_NullOrgOrProject() {
    String result = catalogOrgProjectService.getProjectName(TEST_ACCOUNT, null, PROJ_1);

    assertThat(result).isNull();
    verifyNoInteractions(projectCache, projectClient);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgNames_AllCacheHits() {
    Set<String> orgIds = new HashSet<>(Arrays.asList(ORG_1, ORG_2));
    Map<String, CachedOrgInfo> cachedOrgs = new HashMap<>();
    cachedOrgs.put(ORG_1, CachedOrgInfo.builder().identifier(ORG_1).name(ORG_1_NAME).build());
    cachedOrgs.put(ORG_2, CachedOrgInfo.builder().identifier(ORG_2).name(ORG_2_NAME).build());
    when(orgCache.getAll(TEST_ACCOUNT, orgIds)).thenReturn(cachedOrgs);

    Map<String, String> result = catalogOrgProjectService.getOrgNames(TEST_ACCOUNT, orgIds);

    assertThat(result).hasSize(2);
    assertThat(result).containsEntry(ORG_1, ORG_1_NAME);
    assertThat(result).containsEntry(ORG_2, ORG_2_NAME);
    verifyNoInteractions(organizationClient);
    verify(orgCache, never()).putAll(any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgNames_PartialCacheMiss() {
    Set<String> orgIds = new HashSet<>(Arrays.asList(ORG_1, ORG_2));
    Map<String, CachedOrgInfo> cachedOrgs = new HashMap<>();
    cachedOrgs.put(ORG_1, CachedOrgInfo.builder().identifier(ORG_1).name(ORG_1_NAME).build());
    when(orgCache.getAll(TEST_ACCOUNT, orgIds)).thenReturn(cachedOrgs);

    OrganizationDTO org2DTO = OrganizationDTO.builder().identifier(ORG_2).name(ORG_2_NAME).build();
    OrganizationResponse org2Response = OrganizationResponse.builder().organization(org2DTO).build();
    PageResponse<OrganizationResponse> pageResponse =
        PageResponse.<OrganizationResponse>builder().content(Collections.singletonList(org2Response)).build();

    when(organizationClient.listAllOrganizations(eq(TEST_ACCOUNT), any(), eq(null))).thenReturn(null);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(pageResponse);

    Map<String, String> result = catalogOrgProjectService.getOrgNames(TEST_ACCOUNT, orgIds);

    assertThat(result).hasSize(2);
    assertThat(result).containsEntry(ORG_1, ORG_1_NAME);
    assertThat(result).containsEntry(ORG_2, ORG_2_NAME);
    verify(orgCache).putAll(eq(TEST_ACCOUNT), any());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgNames_FullCacheMiss() {
    Set<String> orgIds = new HashSet<>(Arrays.asList(ORG_1, ORG_2));
    when(orgCache.getAll(TEST_ACCOUNT, orgIds)).thenReturn(Collections.emptyMap());

    OrganizationDTO org1DTO = OrganizationDTO.builder().identifier(ORG_1).name(ORG_1_NAME).build();
    OrganizationDTO org2DTO = OrganizationDTO.builder().identifier(ORG_2).name(ORG_2_NAME).build();
    OrganizationResponse org1Response = OrganizationResponse.builder().organization(org1DTO).build();
    OrganizationResponse org2Response = OrganizationResponse.builder().organization(org2DTO).build();
    PageResponse<OrganizationResponse> pageResponse =
        PageResponse.<OrganizationResponse>builder().content(Arrays.asList(org1Response, org2Response)).build();

    when(organizationClient.listAllOrganizations(eq(TEST_ACCOUNT), any(), eq(null))).thenReturn(null);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(pageResponse);

    Map<String, String> result = catalogOrgProjectService.getOrgNames(TEST_ACCOUNT, orgIds);

    assertThat(result).hasSize(2);
    assertThat(result).containsEntry(ORG_1, ORG_1_NAME);
    assertThat(result).containsEntry(ORG_2, ORG_2_NAME);
    verify(orgCache).putAll(eq(TEST_ACCOUNT), any());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgNames_ClientFailure() {
    Set<String> orgIds = new HashSet<>(Arrays.asList(ORG_1, ORG_2));
    when(orgCache.getAll(TEST_ACCOUNT, orgIds)).thenReturn(Collections.emptyMap());

    when(organizationClient.listAllOrganizations(eq(TEST_ACCOUNT), any(), eq(null))).thenReturn(null);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenThrow(new RuntimeException("Service unavailable"));

    Map<String, String> result = catalogOrgProjectService.getOrgNames(TEST_ACCOUNT, orgIds);

    assertThat(result).isEmpty();
    verify(orgCache, never()).putAll(any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgNames_EmptyOrgIds() {
    Map<String, String> result = catalogOrgProjectService.getOrgNames(TEST_ACCOUNT, Collections.emptySet());

    assertThat(result).isEmpty();
    verifyNoInteractions(orgCache, organizationClient);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectNames_AllCacheHits() {
    Map<String, Set<String>> projectsByOrg = new HashMap<>();
    projectsByOrg.put(ORG_1, new HashSet<>(Arrays.asList(PROJ_1, PROJ_2)));

    String key1 = CatalogProjectCache.buildProjectKey(ORG_1, PROJ_1);
    String key2 = CatalogProjectCache.buildProjectKey(ORG_1, PROJ_2);
    Map<String, CachedProjectInfo> cachedProjects = new HashMap<>();
    cachedProjects.put(
        key1, CachedProjectInfo.builder().identifier(PROJ_1).orgIdentifier(ORG_1).name(PROJ_1_NAME).build());
    cachedProjects.put(
        key2, CachedProjectInfo.builder().identifier(PROJ_2).orgIdentifier(ORG_1).name(PROJ_2_NAME).build());
    when(projectCache.getAll(TEST_ACCOUNT, new HashSet<>(Arrays.asList(key1, key2)))).thenReturn(cachedProjects);

    Set<String> orgIds = Collections.singleton(ORG_1);
    Map<String, String> result = catalogOrgProjectService.getProjectNames(TEST_ACCOUNT, orgIds, projectsByOrg);

    assertThat(result).hasSize(2);
    assertThat(result).containsEntry(key1, PROJ_1_NAME);
    assertThat(result).containsEntry(key2, PROJ_2_NAME);
    verifyNoInteractions(projectClient);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectNames_PartialCacheMiss_WithPagination() {
    Map<String, Set<String>> projectsByOrg = new HashMap<>();
    Set<String> projects = new HashSet<>();
    projects.add(PROJ_1);
    for (int i = 2; i <= 101; i++) {
      projects.add("proj" + i);
    }
    projectsByOrg.put(ORG_1, projects);

    String key1 = CatalogProjectCache.buildProjectKey(ORG_1, PROJ_1);
    Map<String, CachedProjectInfo> cachedProjects = new HashMap<>();
    cachedProjects.put(
        key1, CachedProjectInfo.builder().identifier(PROJ_1).orgIdentifier(ORG_1).name(PROJ_1_NAME).build());
    when(projectCache.getAll(eq(TEST_ACCOUNT), any())).thenReturn(cachedProjects);

    List<ProjectResponse> page1Content = new ArrayList<>();
    for (int i = 2; i <= 101; i++) {
      String projId = "proj" + i;
      ProjectDTO projDTO = ProjectDTO.builder().identifier(projId).orgIdentifier(ORG_1).name("Project " + i).build();
      page1Content.add(ProjectResponse.builder().project(projDTO).build());
    }
    PageResponse<ProjectResponse> page1 = PageResponse.<ProjectResponse>builder().content(page1Content).build();

    when(projectClient.listWithMultiOrg(
             eq(TEST_ACCOUNT), any(), eq(false), any(), eq(null), eq(null), eq(0), eq(100), eq(null), eq(false)))
        .thenReturn(null);
    when(projectClient.listWithMultiOrg(
             eq(TEST_ACCOUNT), any(), eq(false), any(), eq(null), eq(null), eq(1), eq(100), eq(null), eq(false)))
        .thenReturn(null);

    PageResponse<ProjectResponse> page2 =
        PageResponse.<ProjectResponse>builder().content(Collections.emptyList()).build();

    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(page1, page2);

    Set<String> orgIds = Collections.singleton(ORG_1);
    Map<String, String> result = catalogOrgProjectService.getProjectNames(TEST_ACCOUNT, orgIds, projectsByOrg);

    assertThat(result).containsEntry(key1, PROJ_1_NAME);
    for (int i = 2; i <= 101; i++) {
      String key = CatalogProjectCache.buildProjectKey(ORG_1, "proj" + i);
      assertThat(result).containsEntry(key, "Project " + i);
    }
    // putAll called once for fetched results
    verify(projectCache).putAll(eq(TEST_ACCOUNT), any());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectNames_DoesNotCacheMissingRemoteProjects() {
    Map<String, Set<String>> projectsByOrg = new HashMap<>();
    projectsByOrg.put(ORG_1, new HashSet<>(Arrays.asList(PROJ_1, PROJ_2)));

    when(projectCache.getAll(eq(TEST_ACCOUNT), any())).thenReturn(Collections.emptyMap());

    ProjectDTO proj1DTO = ProjectDTO.builder().identifier(PROJ_1).orgIdentifier(ORG_1).name(PROJ_1_NAME).build();
    ProjectResponse proj1Response = ProjectResponse.builder().project(proj1DTO).build();
    PageResponse<ProjectResponse> pageResponse =
        PageResponse.<ProjectResponse>builder().content(Collections.singletonList(proj1Response)).build();

    when(projectClient.listWithMultiOrg(
             eq(TEST_ACCOUNT), any(), eq(false), any(), eq(null), eq(null), eq(0), eq(100), eq(null), eq(false)))
        .thenReturn(null);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(pageResponse);

    Set<String> orgIds = Collections.singleton(ORG_1);
    Map<String, String> result = catalogOrgProjectService.getProjectNames(TEST_ACCOUNT, orgIds, projectsByOrg);

    String key1 = CatalogProjectCache.buildProjectKey(ORG_1, PROJ_1);
    String key2 = CatalogProjectCache.buildProjectKey(ORG_1, PROJ_2);
    assertThat(result).containsEntry(key1, PROJ_1_NAME);
    assertThat(result).doesNotContainKey(key2);

    verify(projectCache)
        .putAll(eq(TEST_ACCOUNT),
            argThat(cacheEntries -> cacheEntries.containsKey(key1) && !cacheEntries.containsKey(key2)));
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectNames_ClientFailure() {
    Map<String, Set<String>> projectsByOrg = new HashMap<>();
    projectsByOrg.put(ORG_1, new HashSet<>(Collections.singletonList(PROJ_1)));

    when(projectCache.getAll(eq(TEST_ACCOUNT), any())).thenReturn(Collections.emptyMap());

    when(projectClient.listWithMultiOrg(
             eq(TEST_ACCOUNT), any(), eq(false), any(), eq(null), eq(null), eq(0), eq(100), eq(null), eq(false)))
        .thenReturn(null);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenThrow(new RuntimeException("Timeout"));

    Set<String> orgIds = Collections.singleton(ORG_1);
    Map<String, String> result = catalogOrgProjectService.getProjectNames(TEST_ACCOUNT, orgIds, projectsByOrg);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectNames_EmptyProjectsByOrg() {
    Map<String, String> result =
        catalogOrgProjectService.getProjectNames(TEST_ACCOUNT, Collections.emptySet(), Collections.emptyMap());

    assertThat(result).isEmpty();
    verifyNoInteractions(projectCache, projectClient);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectNames_MultipleOrgsMultipleProjects() {
    Map<String, Set<String>> projectsByOrg = new HashMap<>();
    projectsByOrg.put(ORG_1, new HashSet<>(Arrays.asList(PROJ_1, PROJ_2)));
    projectsByOrg.put(ORG_2, new HashSet<>(Collections.singletonList(PROJ_3)));

    when(projectCache.getAll(eq(TEST_ACCOUNT), any())).thenReturn(Collections.emptyMap());

    ProjectDTO proj1DTO = ProjectDTO.builder().identifier(PROJ_1).orgIdentifier(ORG_1).name(PROJ_1_NAME).build();
    ProjectDTO proj2DTO = ProjectDTO.builder().identifier(PROJ_2).orgIdentifier(ORG_1).name(PROJ_2_NAME).build();
    ProjectDTO proj3DTO = ProjectDTO.builder().identifier(PROJ_3).orgIdentifier(ORG_2).name(PROJ_3_NAME).build();
    PageResponse<ProjectResponse> pageResponse =
        PageResponse.<ProjectResponse>builder()
            .content(Arrays.asList(ProjectResponse.builder().project(proj1DTO).build(),
                ProjectResponse.builder().project(proj2DTO).build(),
                ProjectResponse.builder().project(proj3DTO).build()))
            .build();

    when(projectClient.listWithMultiOrg(
             eq(TEST_ACCOUNT), any(), eq(false), any(), eq(null), eq(null), eq(0), eq(100), eq(null), eq(false)))
        .thenReturn(null);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(pageResponse);

    Set<String> orgIds = new HashSet<>(Arrays.asList(ORG_1, ORG_2));
    Map<String, String> result = catalogOrgProjectService.getProjectNames(TEST_ACCOUNT, orgIds, projectsByOrg);

    assertThat(result).hasSize(3);
    assertThat(result).containsEntry(CatalogProjectCache.buildProjectKey(ORG_1, PROJ_1), PROJ_1_NAME);
    assertThat(result).containsEntry(CatalogProjectCache.buildProjectKey(ORG_1, PROJ_2), PROJ_2_NAME);
    assertThat(result).containsEntry(CatalogProjectCache.buildProjectKey(ORG_2, PROJ_3), PROJ_3_NAME);
    verify(projectCache).putAll(eq(TEST_ACCOUNT), any());
  }
}
