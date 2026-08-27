/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.remote.ProjectMapper.toProject;
import static io.harness.rule.OwnerRule.ARYA;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.SAHIBA;
import static io.harness.utils.PageTestUtils.getPage;

import static java.lang.Long.parseLong;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.favorites.services.FavoritesScopeService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ActiveProjectsCountDTO;
import io.harness.ng.core.dto.MoveProjectRequest;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.dto.ProjectRequest;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.utils.UserHelperService;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class ProjectResourceTest extends CategoryTest {
  private ProjectService projectService;
  private OrganizationService organizationService;
  private AccessControlClient accessControlClient;
  private ProjectResource projectResource;

  private FavoritesScopeService favoritesService;
  private UserHelperService userHelperService;
  private ScopeInfoService scopeResolverService;

  String accountIdentifier = randomAlphabetic(10);
  String orgIdentifier = randomAlphabetic(10);
  String orgUniqueIdentifier = randomAlphabetic(10);
  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);

  @Before
  public void setup() {
    projectService = mock(ProjectService.class);
    organizationService = mock(OrganizationService.class);
    accessControlClient = mock(AccessControlClient.class);
    favoritesService = mock(FavoritesScopeService.class);
    userHelperService = mock(UserHelperService.class);
    scopeResolverService = mock(ScopeInfoService.class);
    projectResource = new ProjectResource(projectService, organizationService, favoritesService, userHelperService,
        accessControlClient, scopeResolverService);
  }

  private ProjectDTO getProjectDTO(String orgIdentifier, String identifier, String name) {
    return ProjectDTO.builder().orgIdentifier(orgIdentifier).identifier(identifier).name(name).build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() {
    String parentUniqueId = randomAlphabetic(10);
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    ProjectRequest projectRequestWrapper = ProjectRequest.builder().project(projectDTO).build();
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);
    project.setParentUniqueId(parentUniqueId);

    when(projectService.create(any(), eq(projectDTO))).thenReturn(project);
    when(favoritesService.getFavorites(any(ScopeInfo.class), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(orgUniqueIdentifier)
                              .build();
    when(scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, null)).thenReturn(scopeInfo);

    ResponseDTO<ProjectResponse> responseDTO =
        projectResource.create(accountIdentifier, orgIdentifier, projectRequestWrapper);

    ArgumentCaptor<ScopeInfo> captor = ArgumentCaptor.forClass(ScopeInfo.class);
    verify(projectService, times(1)).create(captor.capture(), eq(projectDTO));
    ScopeInfo actualScopeInfo = captor.getValue();
    assertEquals(scopeInfo.getScopeType(), actualScopeInfo.getScopeType());
    assertEquals(scopeInfo.getAccountIdentifier(), actualScopeInfo.getAccountIdentifier());
    assertEquals(scopeInfo.getOrgIdentifier(), actualScopeInfo.getOrgIdentifier());
    assertEquals(scopeInfo.getUniqueId(), actualScopeInfo.getUniqueId());

    assertEquals(project.getVersion().toString(), responseDTO.getEntityTag());
    assertEquals(orgIdentifier, responseDTO.getData().getProject().getOrgIdentifier());
    assertEquals(identifier, responseDTO.getData().getProject().getIdentifier());
    assertEquals(Boolean.FALSE, responseDTO.getData().getIsFavorite());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(orgUniqueIdentifier)
                              .build();
    when(scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, null)).thenReturn(scopeInfo);
    when(projectService.get(scopeInfo, identifier)).thenReturn(Optional.of(project));
    when(favoritesService.getFavorites(any(ScopeInfo.class), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    ResponseDTO<ProjectResponse> responseDTO = projectResource.get(identifier, accountIdentifier, orgIdentifier);

    assertEquals(project.getVersion().toString(), responseDTO.getEntityTag());
    assertEquals(orgIdentifier, responseDTO.getData().getProject().getOrgIdentifier());
    assertEquals(identifier, responseDTO.getData().getProject().getIdentifier());

    when(projectService.get(scopeInfo, identifier)).thenReturn(Optional.empty());

    boolean exceptionThrown = false;
    try {
      projectResource.get(identifier, accountIdentifier, orgIdentifier);
    } catch (EntityNotFoundException exception) {
      exceptionThrown = true;
    }

    assertTrue(exceptionThrown);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    String searchTerm = randomAlphabetic(10);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);
    ArgumentCaptor<ProjectFilterDTO> argumentCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);
    ArgumentCaptor<ScopeInfo> scopeInfoCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    when(favoritesService.getFavorites(any(ScopeInfo.class), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    Set<String> permittedOrgIds = new HashSet<>();
    permittedOrgIds.add(orgIdentifier);
    when(organizationService.getPermittedOrganizations(any(ScopeInfo.class), eq(orgIdentifier)))
        .thenReturn(permittedOrgIds);

    when(projectService.listPermittedProjects(eq(accountIdentifier), any(), any(), any()))
        .thenReturn(getPage(singletonList(project), 1));

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Collections.singletonList(
                            AccessControlDTO.builder()
                                .resourceIdentifier(null)
                                .resourceScope(ResourceScope.of(accountIdentifier, orgIdentifier, null))
                                .permitted(true)
                                .build()))
                        .build());

    ResponseDTO<PageResponse<ProjectResponse>> response =
        projectResource.list(accountIdentifier, orgIdentifier, true, Collections.EMPTY_LIST, ModuleType.CD, searchTerm,
            Boolean.FALSE, pageRequest, ScopeInfo.builder().uniqueId("unique-id").build());

    verify(projectService, times(1))
        .listPermittedProjects(eq(accountIdentifier), any(), argumentCaptor.capture(), any());
    ProjectFilterDTO projectFilterDTO = argumentCaptor.getValue();

    assertEquals(searchTerm, projectFilterDTO.getSearchTerm());
    assertEquals(ModuleType.CD, projectFilterDTO.getModuleType());
    assertEquals(1, response.getData().getPageItemCount());
    assertEquals(orgIdentifier, response.getData().getContent().get(0).getProject().getOrgIdentifier());
    assertEquals(identifier, response.getData().getContent().get(0).getProject().getIdentifier());
    assertEquals(Boolean.FALSE, response.getData().getContent().get(0).getIsFavorite());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testListWithFavorites() {
    String searchTerm = randomAlphabetic(10);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);
    ArgumentCaptor<ProjectFilterDTO> argumentCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);
    when(favoritesService.getFavorites(any(ScopeInfo.class), any(), any()))
        .thenReturn(Collections.singletonList(
            Favorite.builder().resourceIdentifier(project.getIdentifier()).resourceType(ResourceType.PROJECT).build()));

    Set<String> permittedOrgIds = new HashSet<>();
    permittedOrgIds.add(orgIdentifier);
    when(organizationService.getPermittedOrganizations(any(ScopeInfo.class), eq(orgIdentifier)))
        .thenReturn(permittedOrgIds);

    when(projectService.listPermittedProjects(eq(accountIdentifier), any(), any(), any()))
        .thenReturn(getPage(singletonList(project), 1));

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Collections.singletonList(
                            AccessControlDTO.builder()
                                .resourceIdentifier(null)
                                .resourceScope(ResourceScope.of(accountIdentifier, orgIdentifier, null))
                                .permitted(true)
                                .build()))
                        .build());

    ResponseDTO<PageResponse<ProjectResponse>> response =
        projectResource.list(accountIdentifier, orgIdentifier, true, Collections.EMPTY_LIST, ModuleType.CD, searchTerm,
            Boolean.FALSE, pageRequest, ScopeInfo.builder().uniqueId("unique-id").build());

    verify(projectService, times(1))
        .listPermittedProjects(eq(accountIdentifier), any(), argumentCaptor.capture(), any());
    ProjectFilterDTO projectFilterDTO = argumentCaptor.getValue();

    assertEquals(searchTerm, projectFilterDTO.getSearchTerm());
    assertEquals(ModuleType.CD, projectFilterDTO.getModuleType());
    assertThat(projectFilterDTO.getOrgIdentifiers()).containsExactlyInAnyOrder(orgIdentifier);
    assertEquals(1, response.getData().getPageItemCount());
    assertEquals(orgIdentifier, response.getData().getContent().get(0).getProject().getOrgIdentifier());
    assertEquals(identifier, response.getData().getContent().get(0).getProject().getIdentifier());
    assertEquals(Boolean.TRUE, response.getData().getContent().get(0).getIsFavorite());
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testListWithMultiplePermittedOrgs() {
    String searchTerm = randomAlphabetic(10);
    String orgIdentifier2 = randomAlphabetic(10);
    String orgIdentifier3 = randomAlphabetic(10);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);
    ArgumentCaptor<ProjectFilterDTO> argumentCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);
    when(favoritesService.getFavorites(any(ScopeInfo.class), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    Set<String> permittedOrgIds = new HashSet<>();
    permittedOrgIds.add(orgIdentifier);
    permittedOrgIds.add(orgIdentifier2);
    permittedOrgIds.add(orgIdentifier3);
    when(organizationService.getPermittedOrganizations(any(ScopeInfo.class), eq(orgIdentifier)))
        .thenReturn(permittedOrgIds);

    when(projectService.listPermittedProjects(eq(accountIdentifier), any(), any(), any()))
        .thenReturn(getPage(singletonList(project), 1));

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Collections.singletonList(
                            AccessControlDTO.builder()
                                .resourceIdentifier(null)
                                .resourceScope(ResourceScope.of(accountIdentifier, orgIdentifier, null))
                                .permitted(true)
                                .build()))
                        .build());

    ResponseDTO<PageResponse<ProjectResponse>> response =
        projectResource.list(accountIdentifier, orgIdentifier, true, Collections.EMPTY_LIST, ModuleType.CD, searchTerm,
            Boolean.FALSE, pageRequest, ScopeInfo.builder().uniqueId("unique-id").build());

    verify(projectService, times(1))
        .listPermittedProjects(eq(accountIdentifier), any(), argumentCaptor.capture(), any());
    ProjectFilterDTO projectFilterDTO = argumentCaptor.getValue();

    assertEquals(searchTerm, projectFilterDTO.getSearchTerm());
    assertEquals(ModuleType.CD, projectFilterDTO.getModuleType());
    assertThat(projectFilterDTO.getOrgIdentifiers())
        .containsExactlyInAnyOrder(orgIdentifier, orgIdentifier2, orgIdentifier3);
    assertEquals(1, response.getData().getPageItemCount());
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testListWithNullOrgIdentifier() {
    String searchTerm = randomAlphabetic(10);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    Project project = toProject(projectDTO);
    project.setVersion((long) 0);
    ArgumentCaptor<ProjectFilterDTO> argumentCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);
    when(favoritesService.getFavorites(any(ScopeInfo.class), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    Set<String> permittedOrgIds = new HashSet<>();
    permittedOrgIds.add(orgIdentifier);
    permittedOrgIds.add(randomAlphabetic(10));
    when(organizationService.getPermittedOrganizations(any(ScopeInfo.class), eq(null))).thenReturn(permittedOrgIds);

    when(projectService.listPermittedProjects(eq(accountIdentifier), any(), any(), any()))
        .thenReturn(getPage(singletonList(project), 1));

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Collections.singletonList(
                            AccessControlDTO.builder()
                                .resourceIdentifier(null)
                                .resourceScope(ResourceScope.of(accountIdentifier, null, null))
                                .permitted(true)
                                .build()))
                        .build());

    ResponseDTO<PageResponse<ProjectResponse>> response =
        projectResource.list(accountIdentifier, null, true, Collections.EMPTY_LIST, ModuleType.CD, searchTerm,
            Boolean.FALSE, pageRequest, ScopeInfo.builder().uniqueId("unique-id").build());

    verify(organizationService, times(1)).getPermittedOrganizations(any(ScopeInfo.class), eq(null));
    verify(projectService, times(1))
        .listPermittedProjects(eq(accountIdentifier), any(), argumentCaptor.capture(), any());
    ProjectFilterDTO projectFilterDTO = argumentCaptor.getValue();

    assertEquals(searchTerm, projectFilterDTO.getSearchTerm());
    assertEquals(ModuleType.CD, projectFilterDTO.getModuleType());
    assertThat(projectFilterDTO.getOrgIdentifiers()).hasSize(2);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testListWithEmptyPermittedOrgs() {
    String searchTerm = randomAlphabetic(10);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    ArgumentCaptor<ProjectFilterDTO> argumentCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);
    when(favoritesService.getFavorites(any(ScopeInfo.class), anyString(), anyString()))
        .thenReturn(Collections.emptyList());

    Set<String> permittedOrgIds = new HashSet<>();
    when(organizationService.getPermittedOrganizations(any(ScopeInfo.class), eq(orgIdentifier)))
        .thenReturn(permittedOrgIds);

    when(projectService.listPermittedProjects(eq(accountIdentifier), any(), any(), any()))
        .thenReturn(getPage(Collections.emptyList(), 0));

    when(accessControlClient.checkForAccess(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Collections.singletonList(
                            AccessControlDTO.builder()
                                .resourceIdentifier(null)
                                .resourceScope(ResourceScope.of(accountIdentifier, orgIdentifier, null))
                                .permitted(true)
                                .build()))
                        .build());

    ResponseDTO<PageResponse<ProjectResponse>> response =
        projectResource.list(accountIdentifier, orgIdentifier, true, Collections.EMPTY_LIST, ModuleType.CD, searchTerm,
            Boolean.FALSE, pageRequest, ScopeInfo.builder().uniqueId("unique-id").build());

    verify(projectService, times(1))
        .listPermittedProjects(eq(accountIdentifier), any(), argumentCaptor.capture(), any());
    ProjectFilterDTO projectFilterDTO = argumentCaptor.getValue();

    assertEquals(searchTerm, projectFilterDTO.getSearchTerm());
    assertEquals(ModuleType.CD, projectFilterDTO.getModuleType());
    assertThat(projectFilterDTO.getOrgIdentifiers()).isEmpty();
    assertEquals(0, response.getData().getPageItemCount());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    String ifMatch = "0";
    ProjectDTO projectDTO = getProjectDTO(orgIdentifier, identifier, name);
    ProjectRequest projectRequestWrapper = ProjectRequest.builder().project(projectDTO).build();
    Project project = toProject(projectDTO);
    project.setVersion(parseLong(ifMatch) + 1);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(orgUniqueIdentifier)
                              .build();
    when(scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, null)).thenReturn(scopeInfo);
    when(projectService.update(scopeInfo, identifier, projectDTO)).thenReturn(project);

    ResponseDTO<ProjectResponse> response =
        projectResource.update(ifMatch, identifier, accountIdentifier, orgIdentifier, projectRequestWrapper);

    ArgumentCaptor<ScopeInfo> captor = ArgumentCaptor.forClass(ScopeInfo.class);
    verify(projectService, times(1)).update(captor.capture(), eq(identifier), eq(projectDTO));
    ScopeInfo actualScopeInfo = captor.getValue();
    assertEquals(scopeInfo.getScopeType(), actualScopeInfo.getScopeType());
    assertEquals(scopeInfo.getAccountIdentifier(), actualScopeInfo.getAccountIdentifier());
    assertEquals(scopeInfo.getOrgIdentifier(), actualScopeInfo.getOrgIdentifier());
    assertEquals(scopeInfo.getUniqueId(), actualScopeInfo.getUniqueId());

    assertEquals("1", response.getEntityTag());
    assertEquals(orgIdentifier, response.getData().getProject().getOrgIdentifier());
    assertEquals(identifier, response.getData().getProject().getIdentifier());
    when(favoritesService.getFavorites(any(ScopeInfo.class), anyString(), anyString()))
        .thenReturn(Collections.emptyList());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String ifMatch = "0";
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(orgUniqueIdentifier)
                              .build();
    when(scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, null)).thenReturn(scopeInfo);

    when(projectService.delete(scopeInfo, identifier, Long.valueOf(ifMatch))).thenReturn(true);

    ResponseDTO<Boolean> response = projectResource.delete(ifMatch, identifier, accountIdentifier, orgIdentifier);

    verify(projectService, times(1)).delete(scopeInfo, identifier, Long.valueOf(ifMatch));
    assertNull(response.getEntityTag());
    assertTrue(response.getData());
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testMoveProject() {
    String destinationOrgIdentifier = randomAlphabetic(10);
    String destinationOrgUniqueId = randomAlphabetic(10);

    MoveProjectRequest moveProjectRequest = MoveProjectRequest.builder()
                                                .accountIdentifier(accountIdentifier)
                                                .projectIdentifier(identifier)
                                                .sourceOrgIdentifier(orgIdentifier)
                                                .destinationOrgIdentifier(destinationOrgIdentifier)
                                                .build();

    ScopeInfo sourceScopeInfo = ScopeInfo.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .scopeType(ScopeLevel.ORGANIZATION)
                                    .orgIdentifier(orgIdentifier)
                                    .uniqueId(orgUniqueIdentifier)
                                    .build();

    when(scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, null)).thenReturn(sourceScopeInfo);
    when(projectService.moveProject(sourceScopeInfo, identifier, destinationOrgIdentifier)).thenReturn(true);

    ResponseDTO<Boolean> response = projectResource.moveProject(moveProjectRequest);

    ArgumentCaptor<ResourceScope> deleteScopeCaptor = ArgumentCaptor.forClass(ResourceScope.class);
    ArgumentCaptor<ResourceScope> createScopeCaptor = ArgumentCaptor.forClass(ResourceScope.class);

    verify(accessControlClient, times(2)).checkForAccessOrThrow(any(ResourceScope.class), any(), any());

    ArgumentCaptor<ScopeInfo> scopeInfoCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    verify(projectService, times(1))
        .moveProject(scopeInfoCaptor.capture(), eq(identifier), eq(destinationOrgIdentifier));

    ScopeInfo actualScopeInfo = scopeInfoCaptor.getValue();
    assertEquals(sourceScopeInfo.getAccountIdentifier(), actualScopeInfo.getAccountIdentifier());
    assertEquals(sourceScopeInfo.getOrgIdentifier(), actualScopeInfo.getOrgIdentifier());
    assertEquals(sourceScopeInfo.getUniqueId(), actualScopeInfo.getUniqueId());

    assertTrue(response.getData());
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testMoveProjectWithNullRequest() {
    boolean exceptionThrown = false;
    try {
      projectResource.moveProject(null);
    } catch (Exception exception) {
      exceptionThrown = true;
    }
    assertTrue(exceptionThrown);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testMoveProjectServiceFailure() {
    String destinationOrgIdentifier = randomAlphabetic(10);

    MoveProjectRequest moveProjectRequest = MoveProjectRequest.builder()
                                                .accountIdentifier(accountIdentifier)
                                                .projectIdentifier(identifier)
                                                .sourceOrgIdentifier(orgIdentifier)
                                                .destinationOrgIdentifier(destinationOrgIdentifier)
                                                .build();

    ScopeInfo sourceScopeInfo = ScopeInfo.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .scopeType(ScopeLevel.ORGANIZATION)
                                    .orgIdentifier(orgIdentifier)
                                    .uniqueId(orgUniqueIdentifier)
                                    .build();

    when(scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, null)).thenReturn(sourceScopeInfo);
    when(projectService.moveProject(sourceScopeInfo, identifier, destinationOrgIdentifier)).thenReturn(false);

    ResponseDTO<Boolean> response = projectResource.moveProject(moveProjectRequest);

    verify(projectService, times(1)).moveProject(sourceScopeInfo, identifier, destinationOrgIdentifier);

    assertEquals(Boolean.FALSE, response.getData());
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testGetAccessibleProjectsCount() {
    long startInterval = System.currentTimeMillis() - 1000000;
    long endInterval = System.currentTimeMillis();
    String searchTerm = randomAlphabetic(10);
    boolean hasModule = true;
    ModuleType moduleType = ModuleType.CD;

    Set<String> permittedOrgIds = new HashSet<>();
    permittedOrgIds.add(orgIdentifier);
    permittedOrgIds.add(randomAlphabetic(10));

    ActiveProjectsCountDTO activeProjectsCountDTO = ActiveProjectsCountDTO.builder().count(5).build();

    when(organizationService.getPermittedOrganizations(any(ScopeInfo.class), eq(orgIdentifier)))
        .thenReturn(permittedOrgIds);
    when(projectService.permittedProjectsCount(
             eq(accountIdentifier), any(ProjectFilterDTO.class), eq(startInterval), eq(endInterval)))
        .thenReturn(activeProjectsCountDTO);

    ResponseDTO<ActiveProjectsCountDTO> response = projectResource.getAccessibleProjectsCount(accountIdentifier,
        orgIdentifier, hasModule, moduleType, searchTerm, startInterval, endInterval,
        ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build());

    assertEquals(5, response.getData().getCount());
    ArgumentCaptor<ScopeInfo> scopeInfoCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    verify(organizationService, times(1)).getPermittedOrganizations(scopeInfoCaptor.capture(), eq(orgIdentifier));
    ScopeInfo capturedScopeInfo = scopeInfoCaptor.getValue();
    assertEquals(accountIdentifier, capturedScopeInfo.getAccountIdentifier());
    assertEquals(ScopeLevel.ACCOUNT, capturedScopeInfo.getScopeType());
    assertEquals(accountIdentifier, capturedScopeInfo.getUniqueId());

    ArgumentCaptor<ProjectFilterDTO> filterCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);
    verify(projectService, times(1))
        .permittedProjectsCount(eq(accountIdentifier), filterCaptor.capture(), eq(startInterval), eq(endInterval));
    ProjectFilterDTO capturedFilter = filterCaptor.getValue();
    assertEquals(searchTerm, capturedFilter.getSearchTerm());
    assertThat(capturedFilter.getOrgIdentifiers()).containsExactlyInAnyOrderElementsOf(permittedOrgIds);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testGetAccessibleProjectsCountWithEmptyPermittedOrgs() {
    long startInterval = System.currentTimeMillis() - 1000000;
    long endInterval = System.currentTimeMillis();

    Set<String> permittedOrgIds = new HashSet<>();
    when(organizationService.getPermittedOrganizations(any(ScopeInfo.class), eq(orgIdentifier)))
        .thenReturn(permittedOrgIds);

    ResponseDTO<ActiveProjectsCountDTO> response = projectResource.getAccessibleProjectsCount(accountIdentifier,
        orgIdentifier, true, ModuleType.CD, null, startInterval, endInterval,
        ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build());

    assertEquals(0, response.getData().getCount());
    verify(organizationService, times(1)).getPermittedOrganizations(any(ScopeInfo.class), eq(orgIdentifier));
    verify(projectService, times(0)).permittedProjectsCount(anyString(), any(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testGetAccessibleProjectsCountWithNullOrgIdentifier() {
    long startInterval = System.currentTimeMillis() - 1000000;
    long endInterval = System.currentTimeMillis();
    String searchTerm = randomAlphabetic(10);

    Set<String> permittedOrgIds = new HashSet<>();
    permittedOrgIds.add(randomAlphabetic(10));
    permittedOrgIds.add(randomAlphabetic(10));

    ActiveProjectsCountDTO activeProjectsCountDTO = ActiveProjectsCountDTO.builder().count(3).build();

    when(organizationService.getPermittedOrganizations(any(ScopeInfo.class), eq(null))).thenReturn(permittedOrgIds);
    when(projectService.permittedProjectsCount(
             eq(accountIdentifier), any(ProjectFilterDTO.class), eq(startInterval), eq(endInterval)))
        .thenReturn(activeProjectsCountDTO);

    ResponseDTO<ActiveProjectsCountDTO> response = projectResource.getAccessibleProjectsCount(accountIdentifier, null,
        true, ModuleType.CD, searchTerm, startInterval, endInterval,
        ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build());

    assertEquals(3, response.getData().getCount());
    verify(organizationService, times(1)).getPermittedOrganizations(any(ScopeInfo.class), eq(null));
    ArgumentCaptor<ProjectFilterDTO> filterCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);
    verify(projectService, times(1))
        .permittedProjectsCount(eq(accountIdentifier), filterCaptor.capture(), eq(startInterval), eq(endInterval));
    ProjectFilterDTO capturedFilter = filterCaptor.getValue();
    assertEquals(searchTerm, capturedFilter.getSearchTerm());
    assertThat(capturedFilter.getOrgIdentifiers()).containsExactlyInAnyOrderElementsOf(permittedOrgIds);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testGetAccessibleProjectsCountWithAllFilterParameters() {
    long startInterval = System.currentTimeMillis() - 1000000;
    long endInterval = System.currentTimeMillis();
    String searchTerm = randomAlphabetic(10);
    boolean hasModule = false;
    ModuleType moduleType = ModuleType.CI;

    Set<String> permittedOrgIds = new HashSet<>();
    permittedOrgIds.add(orgIdentifier);

    ActiveProjectsCountDTO activeProjectsCountDTO = ActiveProjectsCountDTO.builder().count(2).build();

    when(organizationService.getPermittedOrganizations(any(ScopeInfo.class), eq(orgIdentifier)))
        .thenReturn(permittedOrgIds);
    when(projectService.permittedProjectsCount(
             eq(accountIdentifier), any(ProjectFilterDTO.class), eq(startInterval), eq(endInterval)))
        .thenReturn(activeProjectsCountDTO);

    ResponseDTO<ActiveProjectsCountDTO> response = projectResource.getAccessibleProjectsCount(accountIdentifier,
        orgIdentifier, hasModule, moduleType, searchTerm, startInterval, endInterval,
        ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build());

    assertEquals(2, response.getData().getCount());
    ArgumentCaptor<ProjectFilterDTO> filterCaptor = ArgumentCaptor.forClass(ProjectFilterDTO.class);
    verify(projectService, times(1))
        .permittedProjectsCount(eq(accountIdentifier), filterCaptor.capture(), eq(startInterval), eq(endInterval));
    ProjectFilterDTO capturedFilter = filterCaptor.getValue();
    assertEquals(searchTerm, capturedFilter.getSearchTerm());
    assertEquals(moduleType, capturedFilter.getModuleType());
    assertThat(capturedFilter.getOrgIdentifiers()).containsExactly(orgIdentifier);
  }
}
