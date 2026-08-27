/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.ng.core.environment.resources.EnvironmentResourceConstants.UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_DELETE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO.AccessControlDTOBuilder;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.beans.StoreType;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentGovernanceDataResponse;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.helpers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.service.resources.ServiceResourceApiUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.EnvironmentCreateRequest;
import io.harness.spec.server.ng.v1.model.EnvironmentResponse;
import io.harness.spec.server.ng.v1.model.EnvironmentUpdateRequest;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.After;
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

public class ProjectEnvironmentApiImplTest extends CategoryTest {
  @Inject @InjectMocks ProjectEnvironmentsApiImpl projectEnvironmentsApi;
  @Mock private EnvironmentEntityYamlSchemaHelper entityYamlSchemaHelper;
  @Mock private AccessControlClient accessControlClient;
  @Mock private EnvironmentService environmentService;
  @Mock private EnvironmentRbacHelper environmentRbacHelper;
  @Mock private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock private EnvironmentFilterHelper environmentFilterHelper;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private ServiceResourceApiUtils serviceResourceApiUtils;

  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String IDENTIFIER = "identifier";
  private static final String NAME = "name";
  private static final String ENV_IDENTIFIER = "envId";

  private final String envYamlV1 = "version: 1\n"
      + "kind: environment";

  private final String envYamlV0 = "environment:\n"
      + "  name: envId\n"
      + "  identifier: envId\n"
      + "  type: Production\n"
      + "  orgIdentifier: orgId\n"
      + "  projectIdentifier: projId\n"
      + "  variables:\n"
      + "    - name: stringvar\n"
      + "      type: String\n"
      + "      value: envvalue\n"
      + "    - name: numbervar\n"
      + "      type: Number\n"
      + "      value: 5";

  private static final Environment entity = Environment.builder()
                                                .identifier("id")
                                                .projectIdentifier("projectId")
                                                .orgIdentifier("orgId")
                                                .accountId("accountId")
                                                .type(EnvironmentType.PreProduction)
                                                .build();

  private static final EnvironmentGovernanceDataResponse ENVIRONMENT_GOVERNANCE_DATA_RESPONSE =
      EnvironmentGovernanceDataResponse.builder().environment(entity).build();

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void unSet() {
    entity.setHarnessVersion(null);
    entity.setYaml(null);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testEnvironmentV0CreateEmptyYaml() {
    EnvironmentCreateRequest request = new EnvironmentCreateRequest();
    request.setIdentifier(ENV_IDENTIFIER);
    request.setName(ENV_IDENTIFIER);
    request.setType(io.harness.spec.server.ng.v1.model.EnvironmentType.PRODUCTION);
    doReturn(ENVIRONMENT_GOVERNANCE_DATA_RESPONSE).when(environmentService).create(any(Environment.class), any());
    when(scopeInfoService.getScopeInfo(any(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .uniqueId(PROJ_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());

    Response response =
        projectEnvironmentsApi.createEnvironmentEntity(request, ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);

    ArgumentCaptor<Environment> envCaptor = ArgumentCaptor.forClass(Environment.class);
    verify(environmentService, times(1)).create(envCaptor.capture(), any(ScopeInfo.class));
    Environment envEntityRequest = envCaptor.getValue();
    assertThat(envEntityRequest).isNotNull();
    assertThat(envEntityRequest.getIdentifier()).isEqualTo(ENV_IDENTIFIER);
    assertThat(envEntityRequest.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(envEntityRequest.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(response).isNotNull();
    assertThat(response.getEntity()).isNotNull();

    EnvironmentResponse responseEntity = (EnvironmentResponse) response.getEntity();
    assertThat(responseEntity).isNotNull();
    io.harness.spec.server.ng.v1.model.Environment environmentCreated = responseEntity.getEnvironment();

    assertThat(environmentCreated.getIdentifier()).isEqualTo("id");
    assertThat(environmentCreated.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testEnvironmentV0CreateYamlV0() {
    EnvironmentCreateRequest request = new EnvironmentCreateRequest();
    request.setIdentifier(ENV_IDENTIFIER);
    request.setName(ENV_IDENTIFIER);
    request.setType(io.harness.spec.server.ng.v1.model.EnvironmentType.PRODUCTION);
    request.setYaml(envYamlV0);
    entity.setYaml(envYamlV0);
    doReturn(ENVIRONMENT_GOVERNANCE_DATA_RESPONSE)
        .when(environmentService)
        .create(any(Environment.class), any(ScopeInfo.class));

    when(scopeInfoService.getScopeInfo(any(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .uniqueId(PROJ_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());
    Response response =
        projectEnvironmentsApi.createEnvironmentEntity(request, ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);

    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(entityYamlSchemaHelper, times(1)).validateSchema(eq(ACCOUNT_ID), eq(envYamlV0));
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(ACCOUNT_ID));

    ArgumentCaptor<Environment> envCaptor = ArgumentCaptor.forClass(Environment.class);
    verify(environmentService, times(1)).create(envCaptor.capture(), any(ScopeInfo.class));
    Environment envEntityRequest = envCaptor.getValue();
    assertThat(envEntityRequest).isNotNull();
    assertThat(envEntityRequest.getIdentifier()).isEqualTo(ENV_IDENTIFIER);
    assertThat(envEntityRequest.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(envEntityRequest.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(envEntityRequest.getYaml(envEntityRequest.getHarnessVersion())).isEqualTo(envYamlV0);

    assertThat(response).isNotNull();
    assertThat(response.getEntity()).isNotNull();

    EnvironmentResponse responseEntity = (EnvironmentResponse) response.getEntity();
    assertThat(responseEntity).isNotNull();
    io.harness.spec.server.ng.v1.model.Environment environmentCreated = responseEntity.getEnvironment();

    assertThat(environmentCreated.getIdentifier()).isEqualTo("id");
    assertThat(environmentCreated.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(environmentCreated.getYaml()).isEqualTo(envYamlV0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testEnvironmentV0CreateYamlV1() {
    EnvironmentCreateRequest request = new EnvironmentCreateRequest();
    request.setIdentifier(ENV_IDENTIFIER);
    request.setName(ENV_IDENTIFIER);
    request.setType(io.harness.spec.server.ng.v1.model.EnvironmentType.PRODUCTION);
    request.setYaml(envYamlV1);

    entity.setYaml(envYamlV1);
    entity.setHarnessVersion(HarnessYamlVersion.V1);
    doReturn(ENVIRONMENT_GOVERNANCE_DATA_RESPONSE)
        .when(environmentService)
        .create(any(Environment.class), any(ScopeInfo.class));
    when(scopeInfoService.getScopeInfo(any(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .uniqueId(PROJ_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());

    Response response =
        projectEnvironmentsApi.createEnvironmentEntity(request, ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);

    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(entityYamlSchemaHelper, never()).validateSchema(any(), any());
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(ACCOUNT_ID));

    ArgumentCaptor<Environment> envCaptor = ArgumentCaptor.forClass(Environment.class);
    verify(environmentService, times(1)).create(envCaptor.capture(), any(ScopeInfo.class));
    Environment envEntityRequest = envCaptor.getValue();
    assertThat(envEntityRequest).isNotNull();
    assertThat(envEntityRequest.getIdentifier()).isEqualTo(ENV_IDENTIFIER);
    assertThat(envEntityRequest.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(envEntityRequest.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
    assertThat(envEntityRequest.getYaml(envEntityRequest.getHarnessVersion())).isEqualTo(envYamlV1);

    assertThat(response).isNotNull();
    assertThat(response.getEntity()).isNotNull();

    EnvironmentResponse responseEntity = (EnvironmentResponse) response.getEntity();
    assertThat(responseEntity).isNotNull();
    io.harness.spec.server.ng.v1.model.Environment environmentCreated = responseEntity.getEnvironment();

    assertThat(environmentCreated.getIdentifier()).isEqualTo("id");
    assertThat(environmentCreated.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
    assertThat(environmentCreated.getYaml()).isEqualTo(envYamlV1);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testEnvironmentUpdateEmptyYamlV0() {
    EnvironmentUpdateRequest request = new EnvironmentUpdateRequest();
    request.setIdentifier(ENV_IDENTIFIER);
    request.setName(ENV_IDENTIFIER);
    request.setType(io.harness.spec.server.ng.v1.model.EnvironmentType.PRODUCTION);
    doReturn(ENVIRONMENT_GOVERNANCE_DATA_RESPONSE).when(environmentService).update(any(Environment.class), any());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER)))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .uniqueId(PROJ_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());

    Response response = projectEnvironmentsApi.updateEnvironmentEntity(
        request, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER, ACCOUNT_ID);

    ArgumentCaptor<Environment> envCaptor = ArgumentCaptor.forClass(Environment.class);
    verify(environmentService, times(1)).update(envCaptor.capture(), any());
    Environment envEntityRequest = envCaptor.getValue();
    assertThat(envEntityRequest).isNotNull();
    assertThat(envEntityRequest.getIdentifier()).isEqualTo(ENV_IDENTIFIER);
    assertThat(envEntityRequest.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(envEntityRequest.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(response).isNotNull();
    assertThat(response.getEntity()).isNotNull();

    EnvironmentResponse responseEntity = (EnvironmentResponse) response.getEntity();
    assertThat(responseEntity).isNotNull();
    io.harness.spec.server.ng.v1.model.Environment environmentCreated = responseEntity.getEnvironment();

    assertThat(environmentCreated.getIdentifier()).isEqualTo("id");
    assertThat(environmentCreated.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testEnvironmentUpdateYamlV0() {
    EnvironmentUpdateRequest request = new EnvironmentUpdateRequest();
    request.setIdentifier(ENV_IDENTIFIER);
    request.setName(ENV_IDENTIFIER);
    request.setType(io.harness.spec.server.ng.v1.model.EnvironmentType.PRODUCTION);
    request.setYaml(envYamlV0);
    entity.setYaml(envYamlV0);
    doReturn(ENVIRONMENT_GOVERNANCE_DATA_RESPONSE).when(environmentService).update(any(Environment.class), any());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER)))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .uniqueId(PROJ_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());

    Response response = projectEnvironmentsApi.updateEnvironmentEntity(
        request, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER, ACCOUNT_ID);

    verify(environmentRbacHelper, times(1))
        .checkForAccessOrThrow(anyMap(), any(ResourceScope.class), anyString(), anyString());
    verify(entityYamlSchemaHelper, times(1)).validateSchema(eq(ACCOUNT_ID), eq(envYamlV0));

    ArgumentCaptor<Environment> envCaptor = ArgumentCaptor.forClass(Environment.class);
    verify(environmentService, times(1)).update(envCaptor.capture(), any());
    Environment envEntityRequest = envCaptor.getValue();
    assertThat(envEntityRequest).isNotNull();
    assertThat(envEntityRequest.getIdentifier()).isEqualTo(ENV_IDENTIFIER);
    assertThat(envEntityRequest.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(envEntityRequest.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(envEntityRequest.getYaml(envEntityRequest.getHarnessVersion())).isEqualTo(envYamlV0);

    assertThat(response).isNotNull();
    assertThat(response.getEntity()).isNotNull();

    EnvironmentResponse responseEntity = (EnvironmentResponse) response.getEntity();
    assertThat(responseEntity).isNotNull();
    io.harness.spec.server.ng.v1.model.Environment environmentCreated = responseEntity.getEnvironment();

    assertThat(environmentCreated.getIdentifier()).isEqualTo("id");
    assertThat(environmentCreated.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);
    assertThat(environmentCreated.getYaml()).isEqualTo(envYamlV0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testEnvironmentUpdateYamlV1() {
    EnvironmentUpdateRequest request = new EnvironmentUpdateRequest();
    request.setIdentifier(ENV_IDENTIFIER);
    request.setName(ENV_IDENTIFIER);
    request.setType(io.harness.spec.server.ng.v1.model.EnvironmentType.PRODUCTION);
    request.setYaml(envYamlV1);

    entity.setYaml(envYamlV1);
    entity.setHarnessVersion(HarnessYamlVersion.V1);
    doReturn(ENVIRONMENT_GOVERNANCE_DATA_RESPONSE).when(environmentService).update(any(Environment.class), any());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER)))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .uniqueId(PROJ_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());

    Response response = projectEnvironmentsApi.updateEnvironmentEntity(
        request, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER, ACCOUNT_ID);

    verify(environmentRbacHelper, times(1))
        .checkForAccessOrThrow(anyMap(), any(ResourceScope.class), anyString(), anyString());
    verify(entityYamlSchemaHelper, never()).validateSchema(any(), any());

    ArgumentCaptor<Environment> envCaptor = ArgumentCaptor.forClass(Environment.class);
    verify(environmentService, times(1)).update(envCaptor.capture(), any());
    Environment envEntityRequest = envCaptor.getValue();
    assertThat(envEntityRequest).isNotNull();
    assertThat(envEntityRequest.getIdentifier()).isEqualTo(ENV_IDENTIFIER);
    assertThat(envEntityRequest.getType()).isEqualTo(EnvironmentType.Production);
    assertThat(envEntityRequest.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
    assertThat(envEntityRequest.getYaml(envEntityRequest.getHarnessVersion())).isEqualTo(envYamlV1);

    assertThat(response).isNotNull();
    assertThat(response.getEntity()).isNotNull();

    EnvironmentResponse responseEntity = (EnvironmentResponse) response.getEntity();
    assertThat(responseEntity).isNotNull();
    io.harness.spec.server.ng.v1.model.Environment environmentCreated = responseEntity.getEnvironment();

    assertThat(environmentCreated.getIdentifier()).isEqualTo("id");
    assertThat(environmentCreated.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
    assertThat(environmentCreated.getYaml()).isEqualTo(envYamlV1);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetEnvironment() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PROJ_IDENTIFIER)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    Environment environment = Environment.builder()
                                  .identifier(IDENTIFIER)
                                  .name(NAME)
                                  .projectIdentifier(PROJ_IDENTIFIER)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .accountId(ACCOUNT_ID)
                                  .parentUniqueId(scopeInfo.getUniqueId())
                                  .type(EnvironmentType.PreProduction)
                                  .storeType(StoreType.REMOTE)
                                  .yaml(envYamlV0)
                                  .build();

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER))).thenReturn(scopeInfo);
    doReturn(Optional.of(environment))
        .when(environmentService)
        .get(any(ScopeInfo.class), anyString(), anyBoolean(), anyBoolean(), anyBoolean());

    Response apiResponse =
        projectEnvironmentsApi.getEnvironmentEntity(ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER, ACCOUNT_ID);

    verify(environmentRbacHelper, times(1))
        .checkForAccessOrThrow(anyMap(), any(ResourceScope.class), anyString(), anyString());
    assertThat(apiResponse).isNotNull();
    assertThat(apiResponse.getStatus()).isEqualTo(200);
    EnvironmentResponse envResponse = (EnvironmentResponse) apiResponse.getEntity();
    assertThat(envResponse.getEnvironment().getIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(envResponse.getEnvironment().getYaml()).isEqualTo(envYamlV0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteEnvironment() {
    Environment environment = Environment.builder()
                                  .identifier(IDENTIFIER)
                                  .name(NAME)
                                  .projectIdentifier(PROJ_IDENTIFIER)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .accountId(ACCOUNT_ID)
                                  .type(EnvironmentType.PreProduction)
                                  .build();

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER)))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .uniqueId(PROJ_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());
    doReturn(Optional.of(environment))
        .when(environmentService)
        .getMetadata(any(ScopeInfo.class), anyString(), anyBoolean());

    Response apiResponse = projectEnvironmentsApi.deleteEnvironmentEntity(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER, ACCOUNT_ID, true);

    verify(environmentRbacHelper, times(1))
        .checkForAccessOrThrow(
            anyMap(), any(ResourceScope.class), eq(ENV_IDENTIFIER), eq(ENVIRONMENT_DELETE_PERMISSION));
    assertThat(apiResponse).isNotNull();
    assertThat(apiResponse.getStatus()).isEqualTo(204);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteEnvironmentNotExist() {
    assertThatThrownBy(()
                           -> projectEnvironmentsApi.deleteEnvironmentEntity(
                               ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER, ACCOUNT_ID, true))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testListEnvironmentAccessList() {
    Environment environment0 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .type(EnvironmentType.PreProduction)
                                   .yaml(envYamlV0)
                                   .build();
    Environment environment1 = Environment.builder()
                                   .identifier("identifier2")
                                   .name(NAME)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .type(EnvironmentType.PreProduction)
                                   .yaml(envYamlV0)
                                   .build();

    doReturn(List.of(environment0, environment1)).when(environmentService).listAccess(any());
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER)))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .uniqueId(PROJ_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());
    when(environmentFilterHelper.createCriteriaForGetList(any(ScopeInfo.class), anyBoolean(), any(), any()))
        .thenReturn(new Criteria()
                        .and("accountId")
                        .is(ACCOUNT_ID)
                        .and("orgIdentifier")
                        .is(ORG_IDENTIFIER)
                        .and("projectIdentifier")
                        .is(PROJ_IDENTIFIER)
                        .and("deleted")
                        .is(false));

    mockAccessCheckResponseDTO();

    Response apiResponse = projectEnvironmentsApi.getEnvironments(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, 1, 1, null, List.of(IDENTIFIER), null, true, ACCOUNT_ID, null);

    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(ENVIRONMENT_VIEW_PERMISSION),
            eq(UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE));

    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(environmentService, times(1)).listAccess(criteriaCaptor.capture());
    Criteria criteria = criteriaCaptor.getValue();
    assertThat(criteria.getCriteriaObject().toJson())
        .isEqualTo("{\"accountId\": \"account_id\", \"orgIdentifier\": \"orgId\", \"projectIdentifier\": \"projId\", "
            + "\"deleted\": false, \"identifier\": {\"$in\": [\"identifier\"]}}");
    assertThat(apiResponse).isNotNull();
    assertThat(apiResponse.getStatus()).isEqualTo(200);

    List<EnvironmentResponse> envResponseList = (List<EnvironmentResponse>) apiResponse.getEntity();
    assertThat(envResponseList.size()).isEqualTo(1);
    assertThat(envResponseList.get(0).getEnvironment().getIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(envResponseList.get(0).getEnvironment().getYaml()).isEqualTo(envYamlV0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testListEnvironmentOnlyView() {
    Environment environment0 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .type(EnvironmentType.PreProduction)
                                   .yaml(envYamlV0)
                                   .build();
    Environment environment1 = Environment.builder()
                                   .identifier("identifier2")
                                   .name(NAME)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .type(EnvironmentType.PreProduction)
                                   .build();

    final String envYamlV0Assert = "environment:\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projId\n"
        + "  identifier: identifier2\n"
        + "  tags: {}\n"
        + "  name: name\n"
        + "  type: PreProduction\n";

    doReturn(new PageImpl<>(List.of(environment0, environment1)))
        .when(environmentService)
        .list(any(Criteria.class), any(Pageable.class));

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER)))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .uniqueId(PROJ_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());
    when(environmentFilterHelper.createCriteriaForGetList(any(ScopeInfo.class), anyBoolean(), any(), any()))
        .thenReturn(new Criteria()
                        .and("accountId")
                        .is(ACCOUNT_ID)
                        .and("orgIdentifier")
                        .is(ORG_IDENTIFIER)
                        .and("projectIdentifier")
                        .is(PROJ_IDENTIFIER)
                        .and("deleted")
                        .is(false));

    Response apiResponse = projectEnvironmentsApi.getEnvironments(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, 1, 1, null, List.of(IDENTIFIER), null, false, ACCOUNT_ID, null);

    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(ENVIRONMENT_VIEW_PERMISSION),
            eq(UNAUTHORIZED_TO_LIST_ENVIRONMENTS_MESSAGE));

    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(environmentService, times(1)).list(criteriaCaptor.capture(), pageCaptor.capture());
    Criteria criteria = criteriaCaptor.getValue();
    assertThat(criteria.getCriteriaObject().toJson())
        .isEqualTo("{\"accountId\": \"account_id\", \"orgIdentifier\": \"orgId\", \"projectIdentifier\": \"projId\", "
            + "\"deleted\": false, \"identifier\": {\"$in\": [\"identifier\"]}}");

    Pageable pageable = pageCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(1);
    assertThat(pageable.getPageSize()).isEqualTo(1);

    assertThat(apiResponse).isNotNull();
    assertThat(apiResponse.getStatus()).isEqualTo(200);

    List<EnvironmentResponse> envResponseList = (List<EnvironmentResponse>) apiResponse.getEntity();
    assertThat(envResponseList.size()).isEqualTo(2);
    assertThat(envResponseList.stream()
                   .map(EnvironmentResponse::getEnvironment)
                   .map(io.harness.spec.server.ng.v1.model.Environment::getIdentifier)
                   .toList())
        .containsExactlyInAnyOrder(IDENTIFIER, "identifier2");
    assertThat(envResponseList.get(0).getEnvironment().getYaml()).isEqualTo(envYamlV0);
    assertThat(envResponseList.get(1).getEnvironment().getYaml()).isEqualTo(envYamlV0Assert);
  }

  private void mockAccessCheckResponseDTO() {
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(NGResourceType.ENVIRONMENT)
                                                          .permission(ENVIRONMENT_VIEW_PERMISSION)
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACCOUNT_ID)
                                                                             .orgIdentifier(ORG_IDENTIFIER)
                                                                             .projectIdentifier(PROJ_IDENTIFIER)
                                                                             .build());

    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier(IDENTIFIER).build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier("identifier2").build());

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(accessCheckResponseDTO).when(accessControlClient).checkForAccess(anyList());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSearchEnvironmentsFiltered_WithScopeInfo() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PROJ_IDENTIFIER)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();

    Environment environment1 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(PROJ_IDENTIFIER)
                                   .type(EnvironmentType.Production)
                                   .yaml(envYamlV0)
                                   .build();

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER))).thenReturn(scopeInfo);

    Criteria mockCriteria = new Criteria();
    when(environmentFilterHelper.createCriteriaForGetList(
             eq(scopeInfo), eq(false), eq("search"), eq("filter1"), any(), eq(false), eq("repo1")))
        .thenReturn(mockCriteria);

    when(environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(ENVIRONMENT_VIEW_PERMISSION)))
        .thenReturn(true);

    doReturn(new PageImpl<>(List.of(environment1)))
        .when(environmentService)
        .list(any(Criteria.class), any(Pageable.class));

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any(Set.class)))
        .thenReturn(Collections.singletonMap(PROJ_IDENTIFIER, Optional.of(scopeInfo)));

    Response response = projectEnvironmentsApi.searchEnvironmentsFiltered(ORG_IDENTIFIER, PROJ_IDENTIFIER, 0, 10,
        "search", null, null, null, null, null, "filter1", false, "repo1", null, null, ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    List<EnvironmentResponse> envResponseList = (List<EnvironmentResponse>) response.getEntity();
    assertThat(envResponseList.size()).isEqualTo(1);
    assertThat(envResponseList.get(0).getEnvironment().getIdentifier()).isEqualTo(IDENTIFIER);

    verify(scopeInfoService, times(1)).getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER));
    verify(environmentFilterHelper, times(1))
        .createCriteriaForGetList(eq(scopeInfo), eq(false), eq("search"), eq("filter1"), any(), eq(false), eq("repo1"));
    verify(scopeInfoService, times(1)).getScopeInfo(eq(ACCOUNT_ID), any(Set.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSearchEnvironmentsFiltered_WithRBACFiltering() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PROJ_IDENTIFIER)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    Environment environment1 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(PROJ_IDENTIFIER)
                                   .type(EnvironmentType.Production)
                                   .yaml(envYamlV0)
                                   .build();
    Environment environment2 = Environment.builder()
                                   .identifier("identifier2")
                                   .name("name2")
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(PROJ_IDENTIFIER)
                                   .type(EnvironmentType.PreProduction)
                                   .yaml(envYamlV0)
                                   .build();

    Criteria mockCriteria = new Criteria();
    when(environmentFilterHelper.createCriteriaForGetList(any(), eq(false), any(), any(), any(), eq(false), any()))
        .thenReturn(mockCriteria);
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any(Set.class)))
        .thenReturn(Collections.singletonMap(PROJ_IDENTIFIER, Optional.of(scopeInfo)));

    // User doesn't have permission for all environments
    when(environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(ENVIRONMENT_VIEW_PERMISSION)))
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

    Response response = projectEnvironmentsApi.searchEnvironmentsFiltered(ORG_IDENTIFIER, PROJ_IDENTIFIER, 0, 10, null,
        null, null, null, null, null, null, false, null, null, null, ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    List<EnvironmentResponse> envResponseList = (List<EnvironmentResponse>) response.getEntity();
    assertThat(envResponseList.size()).isEqualTo(1);
    assertThat(envResponseList.get(0).getEnvironment().getIdentifier()).isEqualTo(IDENTIFIER);

    verify(environmentRbacHelper, times(1))
        .hasRequiredPermissionForAllEnvironments(
            eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(ENVIRONMENT_VIEW_PERMISSION));
    verify(environmentRbacHelper, times(1)).getPermittedEnvironmentsList(anyList());
    verify(environmentService, times(1)).list(any(Criteria.class), eq(Pageable.unpaged()));
    verify(environmentService, times(2)).list(any(Criteria.class), any(Pageable.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSearchEnvironmentsFiltered_WithAllFilters() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PROJ_IDENTIFIER)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    Environment environment1 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(PROJ_IDENTIFIER)
                                   .type(EnvironmentType.Production)
                                   .yaml(envYamlV0)
                                   .build();

    Criteria mockCriteria = new Criteria();
    when(environmentFilterHelper.createCriteriaForGetList(
             any(), eq(false), eq("testSearch"), eq("filter1"), any(), eq(true), eq("testRepo")))
        .thenReturn(mockCriteria);
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any(Set.class)))
        .thenReturn(Collections.singletonMap(PROJ_IDENTIFIER, Optional.of(scopeInfo)));

    when(environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(ENVIRONMENT_VIEW_PERMISSION)))
        .thenReturn(true);

    when(serviceResourceApiUtils.mapSort(eq("name"), eq("ASC"))).thenReturn("name,ASC");

    doReturn(new PageImpl<>(List.of(environment1)))
        .when(environmentService)
        .list(any(Criteria.class), any(Pageable.class));

    Response response = projectEnvironmentsApi.searchEnvironmentsFiltered(ORG_IDENTIFIER, PROJ_IDENTIFIER, 0, 50,
        "testSearch", List.of(IDENTIFIER, "env2"), "name", "ASC", List.of(NAME, "name2"), "test description", "filter1",
        true, "testRepo", List.of("tag1:value1", "tag2:value2"), "Production", ACCOUNT_ID);

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
  public void testSearchEnvironmentsFiltered_WithPagination() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PROJ_IDENTIFIER)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    Environment environment1 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(PROJ_IDENTIFIER)
                                   .type(EnvironmentType.Production)
                                   .yaml(envYamlV0)
                                   .build();

    Criteria mockCriteria = new Criteria();
    when(environmentFilterHelper.createCriteriaForGetList(any(), eq(false), any(), any(), any(), eq(false), any()))
        .thenReturn(mockCriteria);
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any(Set.class)))
        .thenReturn(Collections.singletonMap(PROJ_IDENTIFIER, Optional.of(scopeInfo)));

    when(environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(ENVIRONMENT_VIEW_PERMISSION)))
        .thenReturn(true);

    PageImpl<Environment> pagedResult = new PageImpl<>(List.of(environment1), PageRequest.of(2, 5), 15);
    doReturn(pagedResult).when(environmentService).list(any(Criteria.class), any(Pageable.class));

    Response response = projectEnvironmentsApi.searchEnvironmentsFiltered(ORG_IDENTIFIER, PROJ_IDENTIFIER, 2, 5, null,
        null, null, null, null, null, null, false, null, null, null, ACCOUNT_ID);

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
  public void testSearchEnvironmentsFiltered_WithIncludeAllAccessibleAtScope() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PROJ_IDENTIFIER)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();

    Environment environment1 = Environment.builder()
                                   .identifier(IDENTIFIER)
                                   .name(NAME)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountId(ACCOUNT_ID)
                                   .parentUniqueId(PROJ_IDENTIFIER)
                                   .type(EnvironmentType.Production)
                                   .yaml(envYamlV0)
                                   .build();

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER))).thenReturn(scopeInfo);

    Criteria mockCriteria = new Criteria();
    when(environmentFilterHelper.createCriteriaForGetList(
             eq(scopeInfo), eq(false), any(), any(), any(), eq(true), any()))
        .thenReturn(mockCriteria);

    when(environmentRbacHelper.hasRequiredPermissionForAllEnvironments(
             eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(ENVIRONMENT_VIEW_PERMISSION)))
        .thenReturn(true);

    doReturn(new PageImpl<>(List.of(environment1)))
        .when(environmentService)
        .list(any(Criteria.class), any(Pageable.class));

    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any(Set.class)))
        .thenReturn(Collections.singletonMap(PROJ_IDENTIFIER, Optional.of(scopeInfo)));

    Response response = projectEnvironmentsApi.searchEnvironmentsFiltered(ORG_IDENTIFIER, PROJ_IDENTIFIER, 0, 10, null,
        null, null, null, null, null, null, true, null, null, null, ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    verify(environmentFilterHelper, times(1))
        .createCriteriaForGetList(eq(scopeInfo), eq(false), any(), any(), any(), eq(true), any());
  }
}
