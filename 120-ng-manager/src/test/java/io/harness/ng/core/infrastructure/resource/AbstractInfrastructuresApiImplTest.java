/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.ssh.SshEntityHelper;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureGovernanceDataResponse;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureEntityVersionAwareFacade;
import io.harness.ng.core.service.resources.ServiceResourceApiUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.Infrastructure;
import io.harness.spec.server.ng.v1.model.InfrastructureCreateRequest;
import io.harness.spec.server.ng.v1.model.InfrastructureResponse;
import io.harness.spec.server.ng.v1.model.InfrastructureType;
import io.harness.spec.server.ng.v1.model.InfrastructureUpdateRequest;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
public class AbstractInfrastructuresApiImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Inject @InjectMocks AbstractInfrastructuresApiImpl abstractInfrastructuresApi;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock EnvironmentValidationHelper environmentValidationHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock CustomDeploymentYamlHelper customDeploymentYamlHelper;
  @Mock CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  @Mock SshEntityHelper sshEntityHelper;
  @Mock CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock ServiceResourceApiUtils serviceResourceApiUtils;
  @Mock InfrastructureEntityVersionAwareFacade infraVersionAwareFacade;
  @Mock ScopeInfoService scopeInfoService;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;

  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String IDENTIFIER = "identifier";
  private static final String ENV_IDENTIFIER_0 = "env_identifier";
  private static final String ENV_IDENTIFIER_1 = "env_identifier_1";
  private static final String PROJ_UNIQUE_ID = "projUniqueId";
  private static final ScopeInfo SCOPE_INFO = ScopeInfo.builder()
                                                  .accountIdentifier(ACCOUNT_ID)
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .uniqueId(PROJ_UNIQUE_ID)
                                                  .build();

  private static final String NAME = "name";
  private static final String DESCRIPTION = "infra description";

  private static final String INFRA_YAML_V0 = "infrastructureDefinition:\n"
      + "  name: name\n"
      + "  identifier: identifier\n"
      + "  orgIdentifier: orgId\n"
      + "  projectIdentifier: projId\n"
      + "  environmentRef: env_identifier\n"
      + "  deploymentType: Kubernetes\n"
      + "  type: KubernetesDirect\n"
      + "  spec:\n"
      + "    connectorRef: stringvar\n"
      + "    namespace: default\n"
      + "    releaseName: release-<+INFRA_KEY_SHORT_ID>";

  private static final String INFRA_YAML_V1 = "version: 1\n"
      + "kind: infra-def\n"
      + "spec:\n"
      + "  type: k8s-gcp\n"
      + "  spec:\n"
      + "    connector: account.testgcrpl205701\n"
      + "    cluster: prod2\n"
      + "    namespace: default\n"
      + "    release: release-<+INFRA_KEY_SHORT_ID>\n"
      + "  variables:\n"
      + "    tag:\n"
      + "      type: string\n"
      + "      value: 1.9.0";

  @Before
  public void setUp() throws Exception {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateInfraV0() {
    InfrastructureCreateRequest createRequest = new InfrastructureCreateRequest();
    createRequest.setIdentifier(IDENTIFIER);
    createRequest.name(NAME);
    createRequest.setType(InfrastructureType.KUBERNETES_DIRECT);
    createRequest.setDescription(DESCRIPTION);

    createRequest.setYaml(INFRA_YAML_V0);

    doReturn(
        InfrastructureGovernanceDataResponse.builder().infrastructureEntity(getInfraV0Entity(ENV_IDENTIFIER_0)).build())
        .when(infrastructureEntityService)
        .create(any());

    Response createResponse = abstractInfrastructuresApi.createInfrastructureEntity(
        createRequest, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER_0, ACCOUNT_ID);

    verifyInternalMethodInvocation(ENVIRONMENT_UPDATE_PERMISSION);
    ArgumentCaptor<InfrastructureEntity> entityCaptor = ArgumentCaptor.forClass(InfrastructureEntity.class);
    verify(infrastructureEntityService, times(1)).create(entityCaptor.capture());

    assertResponseAndArgument(entityCaptor, INFRA_YAML_V0, createResponse, 201, HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateInfraV1() {
    InfrastructureCreateRequest createRequest = new InfrastructureCreateRequest();
    createRequest.setIdentifier(IDENTIFIER);
    createRequest.name(NAME);
    createRequest.setType(InfrastructureType.KUBERNETES_DIRECT);
    createRequest.setDescription(DESCRIPTION);
    createRequest.setYaml(INFRA_YAML_V1);

    doReturn(InfrastructureGovernanceDataResponse.builder().infrastructureEntity(getInfraV1Entity()).build())
        .when(infrastructureEntityService)
        .create(any());

    Response createResponse = abstractInfrastructuresApi.createInfrastructureEntity(
        createRequest, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER_0, ACCOUNT_ID);

    verifyInternalMethodInvocation(ENVIRONMENT_UPDATE_PERMISSION);
    ArgumentCaptor<InfrastructureEntity> entityCaptor = ArgumentCaptor.forClass(InfrastructureEntity.class);
    verify(infrastructureEntityService, times(1)).create(entityCaptor.capture());
    assertResponseAndArgument(entityCaptor, INFRA_YAML_V1, createResponse, 201, HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpdateInfraV0() {
    InfrastructureUpdateRequest createRequest = new InfrastructureUpdateRequest();
    createRequest.setIdentifier(IDENTIFIER);
    createRequest.name(NAME);
    createRequest.setType(InfrastructureType.KUBERNETES_DIRECT);
    createRequest.setDescription(DESCRIPTION);

    createRequest.setYaml(INFRA_YAML_V0);

    doReturn(
        InfrastructureGovernanceDataResponse.builder().infrastructureEntity(getInfraV0Entity(ENV_IDENTIFIER_0)).build())
        .when(infrastructureEntityService)
        .update(any());

    Response updateResponse = abstractInfrastructuresApi.updateInfrastructureEntity(
        createRequest, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER_0, IDENTIFIER, ACCOUNT_ID);

    verifyInternalMethodInvocation(ENVIRONMENT_UPDATE_PERMISSION);
    ArgumentCaptor<InfrastructureEntity> entityCaptor = ArgumentCaptor.forClass(InfrastructureEntity.class);
    verify(infrastructureEntityService, times(1)).update(entityCaptor.capture());
    assertResponseAndArgument(entityCaptor, INFRA_YAML_V0, updateResponse, 200, HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpdateInfraV1() {
    InfrastructureUpdateRequest createRequest = new InfrastructureUpdateRequest();
    createRequest.setIdentifier(IDENTIFIER);
    createRequest.name(NAME);
    createRequest.setType(InfrastructureType.KUBERNETES_DIRECT);
    createRequest.setDescription(DESCRIPTION);

    createRequest.setYaml(INFRA_YAML_V1);

    doReturn(InfrastructureGovernanceDataResponse.builder().infrastructureEntity(getInfraV1Entity()).build())
        .when(infrastructureEntityService)
        .update(any());

    Response updateResponse = abstractInfrastructuresApi.updateInfrastructureEntity(
        createRequest, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER_0, IDENTIFIER, ACCOUNT_ID);

    verifyInternalMethodInvocation(ENVIRONMENT_UPDATE_PERMISSION);
    ArgumentCaptor<InfrastructureEntity> entityCaptor = ArgumentCaptor.forClass(InfrastructureEntity.class);
    verify(infrastructureEntityService, times(1)).update(entityCaptor.capture());
    assertResponseAndArgument(entityCaptor, INFRA_YAML_V1, updateResponse, 200, HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDeleteInfra() {
    Response response = abstractInfrastructuresApi.deleteInfrastructureEntity(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER_0, IDENTIFIER, ACCOUNT_ID, true);
    verifyInternalMethodInvocation(ENVIRONMENT_UPDATE_PERMISSION);
    verify(infrastructureEntityService, times(1))
        .delete(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(null), eq(ENV_IDENTIFIER_0), eq(IDENTIFIER),
            eq(true));
    assertThat(response.getStatus()).isEqualTo(204);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetInfra() {
    doReturn(Optional.of(getInfraV0Entity(ENV_IDENTIFIER_0)))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyBoolean(), anyBoolean());
    Response response = abstractInfrastructuresApi.getInfrastructureEntity(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER_0, IDENTIFIER, ACCOUNT_ID);
    verifyInternalMethodInvocation(ENVIRONMENT_VIEW_PERMISSION);
    verify(infrastructureEntityService, times(1))
        .get(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(null), eq(ENV_IDENTIFIER_0), eq(IDENTIFIER),
            anyBoolean(), anyBoolean());
    assertApiResponse(INFRA_YAML_V0, response, 200, HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testListInfraRuntimeAccess() {
    Page<InfrastructureEntity> infrastructureEntities =
        new PageImpl<>(List.of(getInfraV1Entity(), getInfraV0Entity(ENV_IDENTIFIER_1)));
    doReturn(infrastructureEntities)
        .when(infrastructureEntityService)
        .list(any(Criteria.class), any(Pageable.class), anyBoolean());

    doReturn(infrastructureEntities)
        .when(infrastructureEntityService)
        .getScopedInfrastructures(infrastructureEntities, null);

    when(serviceResourceApiUtils.mapSort(anyString(), anyString())).thenCallRealMethod();
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(SCOPE_INFO);
    mockAccessCheckResponseDTO();
    Response response =
        abstractInfrastructuresApi.getInfrastructureEntities(ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER_0,
            ACCOUNT_ID, 2, 5, "searchTerm", List.of(IDENTIFIER), "identifier", true, null, null, null, null, "DESC");
    verifyInternalMethodInvocation(ENVIRONMENT_VIEW_PERMISSION);
    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);

    verify(infrastructureEntityService, times(1)).list(criteriaCaptor.capture(), pageCaptor.capture(), anyBoolean());
    assertThat(criteriaCaptor.getValue()).isNotNull();
    assertThat(criteriaCaptor.getValue().getCriteriaObject().toJson())
        .isEqualTo("{\"accountId\": \"account_id\", \"parentUniqueId\": \"projUniqueId\", "
            + "\"envIdentifier\": \"env_identifier\", \"$and\": [{\"$or\": [{\"name\": {\"$regularExpression\": "
            + "{\"pattern\": \"searchTerm\", \"options\": \"i\"}}}, {\"identifier\": {\"$regularExpression\": "
            + "{\"pattern\": \"searchTerm\", \"options\": \"i\"}}}]}], \"identifier\": {\"$in\": [\"identifier\"]}}");

    Pageable pageable = pageCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(2);
    assertThat(pageable.getPageSize()).isEqualTo(5);

    assertThat(response.getStatus()).isEqualTo(200);
    List<InfrastructureResponse> infraResponses = (List<InfrastructureResponse>) response.getEntity();
    assertThat(infraResponses).hasSize(1);
    InfrastructureResponse createdInfraResponse = infraResponses.get(0);
    assertInfraResponse(createdInfraResponse.getInfrastructure());
    assertThat(createdInfraResponse.getInfrastructure().getYaml()).isEqualTo(INFRA_YAML_V1);
    assertThat(createdInfraResponse.getInfrastructure().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testListInfraViewAccess() {
    Page<InfrastructureEntity> infrastructureEntities =
        new PageImpl<>(List.of(getInfraV0Entity(ENV_IDENTIFIER_0), getInfraV1Entity()));
    doReturn(infrastructureEntities)
        .when(infrastructureEntityService)
        .list(any(Criteria.class), any(Pageable.class), anyBoolean());
    doReturn(infrastructureEntities)
        .when(infrastructureEntityService)
        .getScopedInfrastructures(infrastructureEntities, null);

    when(serviceResourceApiUtils.mapSort(anyString(), anyString())).thenCallRealMethod();
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)).thenReturn(SCOPE_INFO);
    Response response =
        abstractInfrastructuresApi.getInfrastructureEntities(ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER_0,
            ACCOUNT_ID, 2, 5, "searchTerm", List.of(IDENTIFIER), "identifier", false, null, null, null, null, "DESC");
    verifyInternalMethodInvocation(ENVIRONMENT_VIEW_PERMISSION);
    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);

    verify(infrastructureEntityService, times(1)).list(criteriaCaptor.capture(), pageCaptor.capture(), anyBoolean());
    assertThat(criteriaCaptor.getValue()).isNotNull();
    assertThat(criteriaCaptor.getValue().getCriteriaObject().toJson())
        .isEqualTo("{\"accountId\": \"account_id\", \"parentUniqueId\": \"projUniqueId\", "
            + "\"envIdentifier\": \"env_identifier\", \"$and\": [{\"$or\": [{\"name\": {\"$regularExpression\": "
            + "{\"pattern\": \"searchTerm\", \"options\": \"i\"}}}, {\"identifier\": {\"$regularExpression\": "
            + "{\"pattern\": \"searchTerm\", \"options\": \"i\"}}}]}], \"identifier\": {\"$in\": [\"identifier\"]}}");

    Pageable pageable = pageCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(2);
    assertThat(pageable.getPageSize()).isEqualTo(5);

    assertThat(response.getStatus()).isEqualTo(200);
    List<InfrastructureResponse> infraResponses = (List<InfrastructureResponse>) response.getEntity();
    assertThat(infraResponses).hasSize(2);

    InfrastructureResponse createdInfraResponse0 = infraResponses.get(0);
    assertInfraResponse(createdInfraResponse0.getInfrastructure());
    assertThat(createdInfraResponse0.getInfrastructure().getYaml()).isEqualTo(INFRA_YAML_V0);
    assertThat(createdInfraResponse0.getInfrastructure().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V0);

    InfrastructureResponse createdInfraResponse1 = infraResponses.get(1);
    assertInfraResponse(createdInfraResponse1.getInfrastructure());
    assertThat(createdInfraResponse1.getInfrastructure().getYaml()).isEqualTo(INFRA_YAML_V1);
    assertThat(createdInfraResponse1.getInfrastructure().getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
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

    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier(ENV_IDENTIFIER_0).build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(false).resourceIdentifier(ENV_IDENTIFIER_1).build());

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(accessCheckResponseDTO).when(accessControlClient).checkForAccess(anyList());
  }

  private void assertResponseAndArgument(ArgumentCaptor<InfrastructureEntity> entityCaptor, String entityYaml,
      Response apiResponse, int status, String yamlVersion) {
    InfrastructureEntity entityForCreateRequest = entityCaptor.getValue();
    assertThat(entityForCreateRequest).isNotNull();
    assertThat(entityForCreateRequest.getYaml(entityForCreateRequest.getHarnessVersion())).isEqualTo(entityYaml);
    assertApiResponse(entityYaml, apiResponse, status, yamlVersion);
  }

  private void assertApiResponse(String entityYaml, Response apiResponse, int status, String yamlVersion) {
    assertThat(apiResponse).isNotNull();
    assertThat(apiResponse.getStatus()).isEqualTo(status);
    assertThat(apiResponse.getEntity()).isNotNull();
    InfrastructureResponse entityResponse = (InfrastructureResponse) apiResponse.getEntity();
    Infrastructure createdInfra = entityResponse.getInfrastructure();
    assertThat(createdInfra.getHarnessVersion()).isEqualTo(yamlVersion);
    assertInfraResponse(createdInfra);
    assertThat(createdInfra.getYaml()).isEqualTo(entityYaml);
  }

  private void assertInfraResponse(Infrastructure createdInfra) {
    assertThat(createdInfra.getDescription()).isEqualTo(DESCRIPTION);
    assertThat(createdInfra.getType()).isEqualTo(InfrastructureType.KUBERNETES_DIRECT);
    assertThat(createdInfra.getAccount()).isEqualTo(ACCOUNT_ID);
    assertThat(createdInfra.getProject()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(createdInfra.getOrg()).isEqualTo(ORG_IDENTIFIER);
    assertThat(createdInfra.getEnvironment()).isEqualTo(ENV_IDENTIFIER_0);
  }

  private void verifyInternalMethodInvocation(String permission) {
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(ACCOUNT_ID));
    verify(environmentValidationHelper, times(1))
        .checkThatEnvExists(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(ENV_IDENTIFIER_0));
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(eq(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)),
            eq(Resource.of(NGResourceType.ENVIRONMENT, ENV_IDENTIFIER_0)), eq(permission), anyString());
  }

  private InfrastructureEntity getInfraV1Entity() {
    return InfrastructureEntity.builder()
        .identifier(IDENTIFIER)
        .accountId(ACCOUNT_ID)
        .projectIdentifier(PROJ_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .envIdentifier(ENV_IDENTIFIER_0)
        .description(DESCRIPTION)
        .type(io.harness.ng.core.infrastructure.InfrastructureType.KUBERNETES_DIRECT)
        .harnessVersion(HarnessYamlVersion.V1)
        .deploymentType(ServiceDefinitionType.KUBERNETES)
        .yaml(INFRA_YAML_V1)
        .build();
  }

  private InfrastructureEntity getInfraV0Entity(String envIdentifier) {
    return InfrastructureEntity.builder()
        .identifier(IDENTIFIER)
        .accountId(ACCOUNT_ID)
        .projectIdentifier(PROJ_IDENTIFIER)
        .orgIdentifier(ORG_IDENTIFIER)
        .envIdentifier(envIdentifier)
        .description(DESCRIPTION)
        .type(io.harness.ng.core.infrastructure.InfrastructureType.KUBERNETES_DIRECT)
        .deploymentType(ServiceDefinitionType.KUBERNETES)
        .harnessVersion(HarnessYamlVersion.V0)
        .yaml(INFRA_YAML_V0)
        .build();
  }
}
