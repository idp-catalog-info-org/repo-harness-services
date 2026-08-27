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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.beans.cd.api.beans.ServiceRequestDTO;
import io.harness.beans.cd.api.beans.ServiceResponse;
import io.harness.category.element.UnitTests;
import io.harness.ci.cd.service.ServiceEntityService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.Collections;
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
public class ServiceResourceImplTest {
  private static final String ACCOUNT_ID = "testAccount";
  private static final String ORG_IDENTIFIER = "testOrg";
  private static final String PROJECT_IDENTIFIER = "testProject";
  private static final String SERVICE_IDENTIFIER = "testService";

  @Mock private Validator validator;
  @Mock private AccessControlClient accessControlClient;
  @Mock private ServiceEntityService serviceEntityService;
  @InjectMocks private ServiceResourceImpl serviceResource;

  @Before
  public void setUp() {
    openMocks(this);
    when(validator.validate(any())).thenReturn(Collections.emptySet());
  }

  private ServiceEntity buildServiceEntity() {
    return ServiceEntity.builder()
        .accountId(ACCOUNT_ID)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .identifier(SERVICE_IDENTIFIER)
        .name("Test Service")
        .yaml("service:\n  name: Test Service")
        .harnessVersion("V2")
        .build();
  }

  private ServiceRequestDTO buildServiceRequestDTO() {
    return ServiceRequestDTO.builder()
        .identifier(SERVICE_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .projectIdentifier(PROJECT_IDENTIFIER)
        .name("Test Service")
        .yaml("service:\n  name: Test Service")
        .harnessVersion("V2")
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreate() {
    ServiceRequestDTO requestDTO = buildServiceRequestDTO();
    ServiceEntity serviceEntity = buildServiceEntity();
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(serviceEntityService.create(any(ServiceEntity.class))).thenReturn(serviceEntity);

    ResponseDTO<ServiceResponse> response = serviceResource.create(ACCOUNT_ID, requestDTO, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getService()).isNotNull();
    assertThat(response.getData().getService().getIdentifier()).isEqualTo(SERVICE_IDENTIFIER);
    verify(serviceEntityService).create(any(ServiceEntity.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFound() {
    ServiceEntity serviceEntity = buildServiceEntity();
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(serviceEntityService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false))
        .thenReturn(Optional.of(serviceEntity));

    ResponseDTO<ServiceResponse> response =
        serviceResource.get(SERVICE_IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, false);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getService().getIdentifier()).isEqualTo(SERVICE_IDENTIFIER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(serviceEntityService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, false))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
        () -> serviceResource.get(SERVICE_IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, false))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdate() {
    ServiceRequestDTO requestDTO = buildServiceRequestDTO();
    ServiceEntity serviceEntity = buildServiceEntity();
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(serviceEntityService.update(any(ServiceEntity.class))).thenReturn(serviceEntity);

    ResponseDTO<ServiceResponse> response = serviceResource.update(ACCOUNT_ID, requestDTO, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getService().getIdentifier()).isEqualTo(SERVICE_IDENTIFIER);
    verify(serviceEntityService).update(any(ServiceEntity.class));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDelete() {
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), anyString());
    when(serviceEntityService.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER))
        .thenReturn(true);

    ResponseDTO<Boolean> response =
        serviceResource.delete(SERVICE_IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isTrue();
    verify(serviceEntityService).delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListServicesWithAccess() {
    ServiceEntity serviceEntity = buildServiceEntity();
    Page<ServiceEntity> page = new PageImpl<>(Collections.singletonList(serviceEntity));
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(true);
    when(serviceEntityService.list(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), anyString(),
             anyBoolean(), anyString(), anyInt(), anyInt()))
        .thenReturn(page);

    ResponseDTO<PageResponse<ServiceResponse>> response = serviceResource.listServices(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "", Collections.emptyList(), false, false);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getContent()).hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testListServicesAccessDenied() {
    when(accessControlClient.hasAccess(any(), any(), anyString())).thenReturn(false);

    assertThatThrownBy(()
                           -> serviceResource.listServices(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "",
                               Collections.emptyList(), false, false))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("Unauthorized to list services");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateScopesBlankAccountId() {
    assertThatThrownBy(()
                           -> serviceResource.listServices(0, 10, "", ORG_IDENTIFIER, PROJECT_IDENTIFIER, "",
                               Collections.emptyList(), false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("AccountID is mandatory");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateScopesBlankOrgWithProject() {
    assertThatThrownBy(()
                           -> serviceResource.listServices(
                               0, 10, ACCOUNT_ID, "", PROJECT_IDENTIFIER, "", Collections.emptyList(), false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Org Identifier is mandatory if projectIdentifier is given");
  }
}
