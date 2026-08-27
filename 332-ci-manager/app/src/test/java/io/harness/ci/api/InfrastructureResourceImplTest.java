/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.beans.cd.api.beans.InfrastructureRequestDTO;
import io.harness.beans.cd.api.beans.InfrastructureResponse;
import io.harness.beans.cd.api.beans.InfrastructureResponseDTO;
import io.harness.category.element.UnitTests;
import io.harness.cd.mappers.InfrastructureEntityMapper;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.ci.cd.service.InfrastructureEntityService;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Optional;
import javax.validation.Validator;
import javax.ws.rs.NotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class InfrastructureResourceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccountId";
  private static final String ORG_ID = "testOrg";
  private static final String PROJECT_ID = "testProject";
  private static final String ENV_ID = "testEnv";
  private static final String INFRA_ID = "testInfra";
  private static final String INFRA_NAME = "Test Infrastructure";
  private static final String YAML = "infrastructure:\n  identifier: testInfra";

  @Mock private Validator validator;
  @Mock private AccessControlClient accessControlClient;
  @Mock private EnvironmentEntityService environmentEntityService;
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @InjectMocks private InfrastructureResourceImpl infrastructureResource;

  private MockedStatic<InfrastructureEntityMapper> mapperMockedStatic;
  private MockedStatic<GitAwareContextHelper> gitAwareContextHelperMockedStatic;

  private InfrastructureRequestDTO requestDTO;
  private InfrastructureEntity infrastructureEntity;
  private InfrastructureResponse infrastructureResponse;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    mapperMockedStatic = mockStatic(InfrastructureEntityMapper.class);
    gitAwareContextHelperMockedStatic = mockStatic(GitAwareContextHelper.class);

    requestDTO = InfrastructureRequestDTO.builder()
                     .identifier(INFRA_ID)
                     .name(INFRA_NAME)
                     .orgIdentifier(ORG_ID)
                     .projectIdentifier(PROJECT_ID)
                     .envIdentifier(ENV_ID)
                     .yaml(YAML)
                     .harnessVersion("V1")
                     .build();

    infrastructureEntity = InfrastructureEntity.builder()
                               .identifier(INFRA_ID)
                               .name(INFRA_NAME)
                               .accountId(ACCOUNT_ID)
                               .orgIdentifier(ORG_ID)
                               .projectIdentifier(PROJECT_ID)
                               .envIdentifier(ENV_ID)
                               .yaml(YAML)
                               .harnessVersion("V1")
                               .build();

    infrastructureResponse = InfrastructureResponse.builder()
                                 .infrastructure(InfrastructureResponseDTO.builder()
                                                     .identifier(INFRA_ID)
                                                     .name(INFRA_NAME)
                                                     .accountId(ACCOUNT_ID)
                                                     .orgIdentifier(ORG_ID)
                                                     .projectIdentifier(PROJECT_ID)
                                                     .envIdentifier(ENV_ID)
                                                     .yaml(YAML)
                                                     .build())
                                 .build();

    when(validator.validate(any())).thenReturn(Collections.emptySet());
    when(environmentEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID))
        .thenReturn(Optional.of(EnvironmentEntity.builder()
                                    .accountId(ACCOUNT_ID)
                                    .orgIdentifier(ORG_ID)
                                    .projectIdentifier(PROJECT_ID)
                                    .identifier(ENV_ID)
                                    .build()));

    mapperMockedStatic.when(() -> InfrastructureEntityMapper.toInfrastructureEntity(eq(ACCOUNT_ID), any()))
        .thenReturn(infrastructureEntity);
    mapperMockedStatic.when(() -> InfrastructureEntityMapper.toResponse(any())).thenReturn(infrastructureResponse);
  }

  @After
  public void tearDown() {
    mapperMockedStatic.close();
    gitAwareContextHelperMockedStatic.close();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate() {
    when(infrastructureEntityService.create(any())).thenReturn(infrastructureEntity);

    ResponseDTO<InfrastructureResponse> response = infrastructureResource.create(ACCOUNT_ID, requestDTO, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getInfrastructure().getIdentifier()).isEqualTo(INFRA_ID);
    verify(infrastructureEntityService).create(any());
    verify(environmentEntityService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGet() {
    when(infrastructureEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.of(infrastructureEntity));

    ResponseDTO<InfrastructureResponse> response =
        infrastructureResource.get(INFRA_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getInfrastructure().getIdentifier()).isEqualTo(INFRA_ID);
    verify(infrastructureEntityService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    when(infrastructureEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.empty());
    mapperMockedStatic
        .when(() -> InfrastructureEntityMapper.getInfraNotFoundError(ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn("Infrastructure not found");

    assertThatThrownBy(() -> infrastructureResource.get(INFRA_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, null))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate() {
    when(infrastructureEntityService.update(any())).thenReturn(infrastructureEntity);

    ResponseDTO<InfrastructureResponse> response = infrastructureResource.update(ACCOUNT_ID, requestDTO, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getInfrastructure().getIdentifier()).isEqualTo(INFRA_ID);
    verify(infrastructureEntityService).update(any());
    verify(environmentEntityService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete() {
    when(infrastructureEntityService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID)).thenReturn(true);

    ResponseDTO<Boolean> response = infrastructureResource.delete(INFRA_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isTrue();
    verify(infrastructureEntityService).delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID);
    verify(environmentEntityService).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateNullRequest() {
    assertThatThrownBy(() -> infrastructureResource.create(ACCOUNT_ID, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No request body sent in the API");
  }
}
