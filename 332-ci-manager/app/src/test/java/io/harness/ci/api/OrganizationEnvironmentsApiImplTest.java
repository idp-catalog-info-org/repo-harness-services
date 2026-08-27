/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.category.element.UnitTests;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.rule.Owner;
import io.harness.spec.server.ci.v1.model.EnvironmentRequest;

import java.util.Optional;
import javax.validation.Validator;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(HarnessTeam.CI)
public class OrganizationEnvironmentsApiImplTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String ENV_ID = "envId";

  @Mock private EnvironmentEntityService environmentEntityService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private Validator validator;
  @InjectMocks private OrganizationEnvironmentsApiImpl organizationEnvironmentsApi;

  @Before
  public void setUp() {
    openMocks(this);
  }

  private EnvironmentEntity buildEnvironmentEntity() {
    return EnvironmentEntity.builder()
        .identifier(ENV_ID)
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .name("Test Environment")
        .type(EnvironmentType.Production)
        .build();
  }

  private EnvironmentRequest buildEnvironmentRequest() {
    EnvironmentRequest request = new EnvironmentRequest();
    request.setIdentifier(ENV_ID);
    request.setName("Test Environment");
    request.setType(io.harness.spec.server.ci.v1.model.EnvironmentType.PRODUCTION);
    return request;
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreateEnvironment() {
    EnvironmentRequest request = buildEnvironmentRequest();
    EnvironmentEntity entity = buildEnvironmentEntity();

    when(validator.validate(any(EnvironmentRequest.class))).thenReturn(emptySet());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(environmentEntityService.create(any(EnvironmentEntity.class))).thenReturn(entity);

    Response response = organizationEnvironmentsApi.createEnvironment(request, ACCOUNT_ID, ORG_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    verify(environmentEntityService).create(any(EnvironmentEntity.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentByIdentifierFound() {
    EnvironmentEntity entity = buildEnvironmentEntity();

    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString(), anyString());
    when(environmentEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), isNull(), eq(ENV_ID)))
        .thenReturn(Optional.of(entity));

    Response response = organizationEnvironmentsApi.getEnvironmentByIdentifier(ORG_ID, ENV_ID, ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    verify(environmentEntityService).get(ACCOUNT_ID, ORG_ID, null, ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentByIdentifierNotFound() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString(), anyString());
    when(environmentEntityService.get(eq(ACCOUNT_ID), eq(ORG_ID), isNull(), eq(ENV_ID))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> organizationEnvironmentsApi.getEnvironmentByIdentifier(ORG_ID, ENV_ID, ACCOUNT_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentByIdentifier() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString(), anyString());
    when(environmentEntityService.delete(eq(ACCOUNT_ID), eq(ORG_ID), isNull(), eq(ENV_ID))).thenReturn(true);

    Response response = organizationEnvironmentsApi.deleteEnvironmentByIdentifier(ORG_ID, ENV_ID, ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    verify(environmentEntityService).delete(ACCOUNT_ID, ORG_ID, null, ENV_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdateEnvironment() {
    EnvironmentRequest request = buildEnvironmentRequest();
    EnvironmentEntity entity = buildEnvironmentEntity();

    when(validator.validate(any(EnvironmentRequest.class))).thenReturn(emptySet());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(environmentEntityService.update(any(EnvironmentEntity.class))).thenReturn(entity);

    Response response = organizationEnvironmentsApi.updateEnvironment(request, ACCOUNT_ID, ORG_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    verify(environmentEntityService).update(any(EnvironmentEntity.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentsAccessGranted() {
    Page<EnvironmentEntity> page = new PageImpl<>(java.util.List.of(buildEnvironmentEntity()));

    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);
    when(environmentEntityService.list(any(Integer.class), any(Integer.class), eq(ACCOUNT_ID), eq(ORG_ID), isNull(),
             isNull(), eq(false), anyString()))
        .thenReturn(page);

    Response response =
        organizationEnvironmentsApi.getEnvironments(ORG_ID, ACCOUNT_ID, 0, 10, null, false, null, false);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetEnvironmentsAccessDenied() {
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(false);

    assertThatThrownBy(
        () -> organizationEnvironmentsApi.getEnvironments(ORG_ID, ACCOUNT_ID, 0, 10, null, false, null, false))
        .isInstanceOf(io.harness.accesscontrol.NGAccessDeniedException.class);
  }
}
