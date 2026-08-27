/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.exception.WingsException.USER;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_GROUP_DELETE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_GROUP_VIEW_PERMISSION;
import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.CHIRAG_S;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.EnvironmentGroupEntity;
import io.harness.beans.cd.api.beans.EnvironmentGroupRequestDTO;
import io.harness.beans.cd.api.beans.EnvironmentGroupResponse;
import io.harness.category.element.UnitTests;
import io.harness.ci.cd.service.EnvironmentGroupService;
import io.harness.ci.environment.utils.EnvironmentGroupEntityRbacHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Optional;
import javax.validation.Validator;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(HarnessTeam.CI)
public class EnvironmentGroupResourceImplTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String ENV_GROUP_ID = "envGroupId";

  @Mock private Validator validator;
  @Mock private AccessControlClient accessControlClient;
  @Mock private EnvironmentGroupService environmentGroupService;
  @Mock private EnvironmentGroupEntityRbacHelper envGroupEntityRbacHelper;
  @InjectMocks private EnvironmentGroupResourceImpl environmentGroupResource;

  @Before
  public void setUp() {
    openMocks(this);
  }

  private EnvironmentGroupEntity buildEnvironmentGroupEntity() {
    return EnvironmentGroupEntity.builder()
        .identifier(ENV_GROUP_ID)
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .name("Test Environment Group")
        .environments(List.of("env1", "env2"))
        .build();
  }

  private EnvironmentGroupRequestDTO buildEnvironmentGroupRequestDTO() {
    return EnvironmentGroupRequestDTO.builder()
        .identifier(ENV_GROUP_ID)
        .name("Test Environment Group")
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .environments(List.of("env1", "env2"))
        .harnessVersion("V2")
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate() {
    EnvironmentGroupRequestDTO requestDTO = buildEnvironmentGroupRequestDTO();
    EnvironmentGroupEntity entity = buildEnvironmentGroupEntity();

    when(validator.validate(any(EnvironmentGroupRequestDTO.class))).thenReturn(emptySet());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(environmentGroupService.create(any(EnvironmentGroupEntity.class))).thenReturn(entity);

    ResponseDTO<EnvironmentGroupResponse> response = environmentGroupResource.create(ACCOUNT_ID, requestDTO);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getEnvironmentGroup().getIdentifier()).isEqualTo(ENV_GROUP_ID);
    verify(environmentGroupService).create(any(EnvironmentGroupEntity.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFound() {
    EnvironmentGroupEntity entity = buildEnvironmentGroupEntity();

    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(environmentGroupService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID)).thenReturn(Optional.of(entity));

    ResponseDTO<EnvironmentGroupResponse> response =
        environmentGroupResource.get(ENV_GROUP_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getEnvironmentGroup().getIdentifier()).isEqualTo(ENV_GROUP_ID);
    verify(accessControlClient).checkForAccessOrThrow(any(), any(), eq(ENVIRONMENT_GROUP_VIEW_PERMISSION));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    when(environmentGroupService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> environmentGroupResource.get(ENV_GROUP_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate() {
    EnvironmentGroupRequestDTO requestDTO = buildEnvironmentGroupRequestDTO();
    EnvironmentGroupEntity entity = buildEnvironmentGroupEntity();

    when(validator.validate(any(EnvironmentGroupRequestDTO.class))).thenReturn(emptySet());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(environmentGroupService.update(any(EnvironmentGroupEntity.class))).thenReturn(entity);

    ResponseDTO<EnvironmentGroupResponse> response = environmentGroupResource.update(ACCOUNT_ID, requestDTO);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getEnvironmentGroup().getIdentifier()).isEqualTo(ENV_GROUP_ID);
    verify(environmentGroupService).update(any(EnvironmentGroupEntity.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(environmentGroupService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID)).thenReturn(true);

    ResponseDTO<Boolean> response = environmentGroupResource.delete(ENV_GROUP_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isTrue();
    verify(accessControlClient).checkForAccessOrThrow(any(), any(), eq(ENVIRONMENT_GROUP_DELETE_PERMISSION));
    verify(environmentGroupService).delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_GROUP_ID);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testGetAccessDenied() {
    doThrow(new NGAccessDeniedException("Access Denied", USER, emptyList()))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), eq(ENVIRONMENT_GROUP_VIEW_PERMISSION));

    assertThatThrownBy(() -> environmentGroupResource.get(ENV_GROUP_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .isInstanceOf(NGAccessDeniedException.class);

    verify(environmentGroupService, never()).get(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testDeleteAccessDenied() {
    doThrow(new NGAccessDeniedException("Access Denied", USER, emptyList()))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), eq(ENVIRONMENT_GROUP_DELETE_PERMISSION));

    assertThatThrownBy(() -> environmentGroupResource.delete(ENV_GROUP_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .isInstanceOf(NGAccessDeniedException.class);

    verify(environmentGroupService, never()).delete(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListEnvironmentGroupsAccessGranted() {
    Page<EnvironmentGroupEntity> page = new PageImpl<>(List.of(buildEnvironmentGroupEntity()));

    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);
    when(environmentGroupService.list(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("search"), eq(false), anyString(), eq(0), eq(10)))
        .thenReturn(page);

    var response = environmentGroupResource.listEnvironmentGroups(
        0, 10, ACCOUNT_ID, ORG_ID, PROJECT_ID, "search", emptyList(), false, false);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListEnvironmentGroupsAccessDenied() {
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(false);

    assertThatThrownBy(()
                           -> environmentGroupResource.listEnvironmentGroups(
                               0, 10, ACCOUNT_ID, ORG_ID, PROJECT_ID, "search", emptyList(), false, false))
        .isInstanceOf(NGAccessDeniedException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateScopesBlankAccountId() {
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);

    assertThatThrownBy(()
                           -> environmentGroupResource.listEnvironmentGroups(
                               0, 10, "", ORG_ID, PROJECT_ID, null, emptyList(), false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("AccountID is mandatory");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateScopesBlankOrgWithProject() {
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);

    assertThatThrownBy(()
                           -> environmentGroupResource.listEnvironmentGroups(
                               0, 10, ACCOUNT_ID, "", PROJECT_ID, null, emptyList(), false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Org Identifier is mandatory if projectIdentifier is given");
  }
}
