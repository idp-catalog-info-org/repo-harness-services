/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.beans.cd.api.beans.EnvironmentRequestDTO;
import io.harness.beans.cd.api.beans.EnvironmentResponse;
import io.harness.category.element.UnitTests;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
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
public class EnvironmentResourceImplTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String ENV_ID = "envId";

  @Mock private Validator validator;
  @Mock private AccessControlClient accessControlClient;
  @Mock private EnvironmentEntityService environmentEntityService;
  @InjectMocks private EnvironmentResourceImpl environmentResource;

  @Before
  public void setUp() {
    openMocks(this);
  }

  private EnvironmentEntity buildEnvironmentEntity() {
    return EnvironmentEntity.builder()
        .identifier(ENV_ID)
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .name("Test Environment")
        .type(EnvironmentType.Production)
        .build();
  }

  private EnvironmentRequestDTO buildEnvironmentRequestDTO() {
    return EnvironmentRequestDTO.builder()
        .identifier(ENV_ID)
        .name("Test Environment")
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .type(EnvironmentType.Production)
        .harnessVersion("V2")
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate() {
    EnvironmentRequestDTO requestDTO = buildEnvironmentRequestDTO();
    EnvironmentEntity entity = buildEnvironmentEntity();

    when(validator.validate(any(EnvironmentRequestDTO.class))).thenReturn(emptySet());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(environmentEntityService.create(any(EnvironmentEntity.class))).thenReturn(entity);

    ResponseDTO<EnvironmentResponse> response = environmentResource.create(ACCOUNT_ID, requestDTO);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getEnvironment().getIdentifier()).isEqualTo(ENV_ID);
    verify(environmentEntityService).create(any(EnvironmentEntity.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFound() {
    EnvironmentEntity entity = buildEnvironmentEntity();

    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString(), anyString());
    when(environmentEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID)).thenReturn(Optional.of(entity));

    ResponseDTO<EnvironmentResponse> response = environmentResource.get(ENV_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getEnvironment().getIdentifier()).isEqualTo(ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString(), anyString());
    when(environmentEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> environmentResource.get(ENV_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate() {
    EnvironmentRequestDTO requestDTO = buildEnvironmentRequestDTO();
    EnvironmentEntity entity = buildEnvironmentEntity();

    when(validator.validate(any(EnvironmentRequestDTO.class))).thenReturn(emptySet());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(environmentEntityService.update(any(EnvironmentEntity.class))).thenReturn(entity);

    ResponseDTO<EnvironmentResponse> response = environmentResource.update(ACCOUNT_ID, requestDTO, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getEnvironment().getIdentifier()).isEqualTo(ENV_ID);
    verify(environmentEntityService).update(any(EnvironmentEntity.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString(), anyString());
    when(environmentEntityService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID)).thenReturn(true);

    ResponseDTO<Boolean> response = environmentResource.delete(ENV_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isTrue();
    verify(environmentEntityService).delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListEnvironmentsAccessGranted() {
    Page<EnvironmentEntity> page = new PageImpl<>(List.of(buildEnvironmentEntity()));

    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);
    when(environmentEntityService.list(
             eq(0), eq(10), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("search"), eq(false), anyString()))
        .thenReturn(page);

    var response = environmentResource.listEnvironments(
        0, 10, ACCOUNT_ID, ORG_ID, PROJECT_ID, "search", emptyList(), false, false);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListEnvironmentsAccessDenied() {
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(false);

    assertThatThrownBy(()
                           -> environmentResource.listEnvironments(
                               0, 10, ACCOUNT_ID, ORG_ID, PROJECT_ID, "search", emptyList(), false, false))
        .isInstanceOf(NGAccessDeniedException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateScopesBlankAccountId() {
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);

    assertThatThrownBy(
        () -> environmentResource.listEnvironments(0, 10, "", ORG_ID, PROJECT_ID, null, emptyList(), false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("AccountID is mandatory");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateScopesBlankOrgWithProject() {
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);

    assertThatThrownBy(
        () -> environmentResource.listEnvironments(0, 10, ACCOUNT_ID, "", PROJECT_ID, null, emptyList(), false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Org Identifier is mandatory if projectIdentifier is given");
  }
}
