/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.resources;

import static io.harness.ng.core.variable.VariablePermissions.VARIABLE_EDIT_PERMISSION;
import static io.harness.ng.core.variable.VariablePermissions.VARIABLE_RESOURCE_TYPE;
import static io.harness.ng.core.variable.VariablePermissions.VARIABLE_VIEW_PERMISSION;
import static io.harness.rule.OwnerRule.NIKETAN;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.SAHIBA;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.engine.expressions.VariableFunctorProcessor;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.variable.dto.VariableDTO;
import io.harness.ng.core.variable.dto.VariableListRequestDTO;
import io.harness.ng.core.variable.dto.VariableRequestDTO;
import io.harness.ng.core.variable.dto.VariableResponseDTO;
import io.harness.ng.core.variable.entity.Variable;
import io.harness.ng.core.variable.mappers.VariableMapper;
import io.harness.ng.core.variable.services.VariableService;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;

public class VariableResourceImplTest extends CategoryTest {
  @Mock private VariableService variableService;
  @Mock private VariableMapper variableMapper;
  @Mock private AccessControlClient accessControlClient;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private VariableFunctorProcessor variableFunctorProcessor;
  private VariableResource variableResource;

  @Captor ArgumentCaptor<String> stringArgumentCaptor;
  @Captor ArgumentCaptor<VariableDTO> variableDTOArgumentCaptor;
  @Captor ArgumentCaptor<Variable> variableArgumentCaptor;
  @Captor ArgumentCaptor<String> permissionArgumentCaptor;
  @Captor ArgumentCaptor<ScopeInfo> scopeInfoCaptor;
  @Rule public ExpectedException expectedExceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    variableResource = new VariableResourceImpl(
        variableService, variableMapper, accessControlClient, scopeInfoService, variableFunctorProcessor);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testCreate() {
    String accountIdentifier = randomAlphabetic(10);
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build();
    VariableDTO variableDTO = VariableDTO.builder().build();
    VariableRequestDTO variableRequestDTO = VariableRequestDTO.builder().variable(variableDTO).build();
    when(variableService.create(any(), any())).thenReturn(variableDTO);
    when(variableMapper.toResponseWrapper(any(), any())).thenReturn(VariableResponseDTO.builder().build());
    when(scopeInfoService.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    variableResource.create(accountIdentifier, variableRequestDTO);

    verify(variableService, times(1)).create(scopeInfoCaptor.capture(), variableDTOArgumentCaptor.capture());
    assertThat(scopeInfoCaptor.getValue().getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(variableDTOArgumentCaptor.getValue()).isEqualTo(variableDTO);

    verify(variableMapper, times(1)).toResponseWrapper(variableDTO);

    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), permissionArgumentCaptor.capture());
    assertThat(permissionArgumentCaptor.getValue()).isEqualTo(VARIABLE_EDIT_PERMISSION);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGet() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    when(variableService.get(any(), anyString()))
        .thenReturn(Optional.ofNullable(VariableResponseDTO.builder().build()));
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    ResponseDTO<VariableResponseDTO> returnVal =
        variableResource.get(identifier, accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo);
    assertThat(returnVal).isNotNull();
    verify(variableService, times(1)).get(scopeInfo, identifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGet_notFoundException() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    when(variableService.get(any(), anyString())).thenReturn(Optional.empty());
    expectedExceptionRule.expect(NotFoundException.class);
    expectedExceptionRule.expectMessage(
        String.format("Variable with identifier [%s] in project [%s] and org [%s] not found", identifier,
            projectIdentifier, orgIdentifier));
    variableResource.get(identifier, accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo);
    verify(variableService, times(1)).get(scopeInfo, identifier);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testCreateWithoutOrg() {
    String accountIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    VariableDTO variableDTO = VariableDTO.builder().projectIdentifier(projectIdentifier).build();
    VariableRequestDTO variableRequestDTO = VariableRequestDTO.builder().variable(variableDTO).build();
    expectedExceptionRule.expect(InvalidRequestException.class);
    expectedExceptionRule.expectMessage(String.format(
        "Project %s specified without the org Identifier", variableRequestDTO.getVariable().getProjectIdentifier()));
    variableResource.create(accountIdentifier, variableRequestDTO);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testUpdateWithoutOrg() {
    String accountIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    VariableDTO variableDTO = VariableDTO.builder().projectIdentifier(projectIdentifier).build();
    VariableRequestDTO variableRequestDTO = VariableRequestDTO.builder().variable(variableDTO).build();
    expectedExceptionRule.expect(InvalidRequestException.class);
    expectedExceptionRule.expectMessage(String.format(
        "Project %s specified without the org Identifier", variableRequestDTO.getVariable().getProjectIdentifier()));
    variableResource.update(accountIdentifier, variableRequestDTO);
  }

  @Test
  @Owner(developers = NIKETAN)
  @Category(UnitTests.class)
  public void testGet_NoAccess() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    doThrow(new NGAccessDeniedException("Access Denied", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    assertThrows(NGAccessDeniedException.class,
        () -> { variableResource.get(identifier, accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo); });
  }

  @Test
  @Owner(developers = NIKETAN)
  @Category(UnitTests.class)
  public void testUpdate_NoAccess() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    doThrow(new NGAccessDeniedException("Access Denied", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());
    VariableDTO variableDTO = VariableDTO.builder()
                                  .identifier(identifier)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .build();
    assertThrows(NGAccessDeniedException.class, () -> {
      variableResource.update(accountIdentifier, VariableRequestDTO.builder().variable(variableDTO).build());
    });
  }

  @Test
  @Owner(developers = NIKETAN)
  @Category(UnitTests.class)
  public void testDelete_NoAccess() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    doThrow(new NGAccessDeniedException("Access Denied", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    assertThrows(NGAccessDeniedException.class,
        () -> { variableResource.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, scopeInfo); });
  }

  @Test
  @Owner(developers = NIKETAN)
  @Category(UnitTests.class)
  public void testList_AllAccess_IncludeEverySubscopeFalse() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    PageRequest pageRequest =
        PageRequest.builder()
            .pageIndex(0)
            .pageSize(10)
            .sortOrders(
                List.of(SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build()))
            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    List<VariableResponseDTO> variableResponseDTOList = getVariableResponseList();
    when(accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
             Resource.of(VARIABLE_RESOURCE_TYPE, null), VARIABLE_VIEW_PERMISSION))
        .thenReturn(true);
    when(variableService.list(scopeInfo, null, null, false, getPageRequest(pageRequest)))
        .thenReturn(PageResponse.<VariableResponseDTO>builder().content(variableResponseDTOList).build());
    ResponseDTO<PageResponse<VariableResponseDTO>> list =
        variableResource.list(accountIdentifier, orgIdentifier, projectIdentifier, null, false, pageRequest, scopeInfo);
    assertThat(list.getData().getContent().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = NIKETAN)
  @Category(UnitTests.class)
  public void testList_AllAccess_IncludeEverySubscopeTrue() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    PageRequest pageRequest =
        PageRequest.builder()
            .pageIndex(0)
            .pageSize(10)
            .sortOrders(
                List.of(SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build()))
            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    List<VariableResponseDTO> variableResponseDTOList = getVariableResponseList();
    when(accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
             Resource.of(VARIABLE_RESOURCE_TYPE, null), VARIABLE_VIEW_PERMISSION))
        .thenReturn(true);
    when(variableService.list(scopeInfo, null, null, true, Pageable.ofSize(90000)))
        .thenReturn(PageResponse.<VariableResponseDTO>builder().content(variableResponseDTOList).build());
    when(variableService.getPermitted(variableResponseDTOList, scopeInfo)).thenReturn(variableResponseDTOList);
    when(variableService.list(scopeInfo,
             VariableListRequestDTO.builder().identifiers(Arrays.asList("id1", "id2")).build(), null, true,
             getPageRequest(pageRequest)))
        .thenReturn(PageResponse.<VariableResponseDTO>builder().content(variableResponseDTOList).build());
    ResponseDTO<PageResponse<VariableResponseDTO>> list =
        variableResource.list(accountIdentifier, orgIdentifier, projectIdentifier, null, true, pageRequest, scopeInfo);
    assertThat(list.getData().getContent().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = NIKETAN)
  @Category(UnitTests.class)
  public void testList_SpecificAccess_IncludeEverySubscopeFalse() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    PageRequest pageRequest =
        PageRequest.builder()
            .pageIndex(0)
            .pageSize(10)
            .sortOrders(
                List.of(SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build()))
            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    VariableResponseDTO variableResponseDTO1 =
        VariableResponseDTO.builder().variable(VariableDTO.builder().identifier("id1").build()).build();
    VariableResponseDTO variableResponseDTO2 =
        VariableResponseDTO.builder().variable(VariableDTO.builder().identifier("id2").build()).build();
    List<VariableResponseDTO> variableResponseList = Arrays.asList(variableResponseDTO1, variableResponseDTO2);
    when(accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
             Resource.of(VARIABLE_RESOURCE_TYPE, null), VARIABLE_VIEW_PERMISSION))
        .thenReturn(false);
    when(variableService.list(scopeInfo, null, null, false, Pageable.ofSize(90000)))
        .thenReturn(PageResponse.<VariableResponseDTO>builder().content(variableResponseList).build());
    when(variableService.getPermitted(variableResponseList, scopeInfo))
        .thenReturn(Collections.singletonList(variableResponseDTO2));
    when(variableService.list(scopeInfo, VariableListRequestDTO.builder().identifiers(List.of("id2")).build(), null,
             false, getPageRequest(pageRequest)))
        .thenReturn(PageResponse.<VariableResponseDTO>builder()
                        .content(Collections.singletonList(variableResponseDTO2))
                        .build());
    ResponseDTO<PageResponse<VariableResponseDTO>> list =
        variableResource.list(accountIdentifier, orgIdentifier, projectIdentifier, null, false, pageRequest, scopeInfo);
    assertThat(list.getData().getContent().size()).isEqualTo(1);
    assertThat(list.getData().getContent().get(0).getVariable().getIdentifier()).isEqualTo("id2");
  }

  @Test
  @Owner(developers = NIKETAN)
  @Category(UnitTests.class)
  public void testList_SpecificAccess_IncludeEverySubscopeTrue() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String uniqueId = randomAlphabetic(10);
    PageRequest pageRequest =
        PageRequest.builder()
            .pageIndex(0)
            .pageSize(10)
            .sortOrders(
                List.of(SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build()))
            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .uniqueId(uniqueId)
                              .build();
    VariableResponseDTO variableResponseDTO1 =
        VariableResponseDTO.builder().variable(VariableDTO.builder().identifier("id1").build()).build();
    VariableResponseDTO variableResponseDTO2 =
        VariableResponseDTO.builder().variable(VariableDTO.builder().identifier("id2").build()).build();
    List<VariableResponseDTO> variableResponseList = Arrays.asList(variableResponseDTO1, variableResponseDTO2);
    when(accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
             Resource.of(VARIABLE_RESOURCE_TYPE, null), VARIABLE_VIEW_PERMISSION))
        .thenReturn(false);
    when(variableService.list(scopeInfo, null, null, true, Pageable.ofSize(90000)))
        .thenReturn(PageResponse.<VariableResponseDTO>builder().content(variableResponseList).build());
    when(variableService.getPermitted(variableResponseList, scopeInfo))
        .thenReturn(Collections.singletonList(variableResponseDTO2));
    when(variableService.list(scopeInfo, VariableListRequestDTO.builder().identifiers(List.of("id2")).build(), null,
             true, getPageRequest(pageRequest)))
        .thenReturn(PageResponse.<VariableResponseDTO>builder()
                        .content(Collections.singletonList(variableResponseDTO2))
                        .build());
    ResponseDTO<PageResponse<VariableResponseDTO>> list =
        variableResource.list(accountIdentifier, orgIdentifier, projectIdentifier, null, true, pageRequest, scopeInfo);
    assertThat(list.getData().getContent().size()).isEqualTo(1);
    assertThat(list.getData().getContent().get(0).getVariable().getIdentifier()).isEqualTo("id2");
  }

  private List<VariableResponseDTO> getVariableResponseList() {
    VariableResponseDTO variableResponseDTO1 =
        VariableResponseDTO.builder().variable(VariableDTO.builder().identifier("id1").build()).build();
    VariableResponseDTO variableResponseDTO2 =
        VariableResponseDTO.builder().variable(VariableDTO.builder().identifier("id2").build()).build();
    return Arrays.asList(variableResponseDTO1, variableResponseDTO2);
  }
}
