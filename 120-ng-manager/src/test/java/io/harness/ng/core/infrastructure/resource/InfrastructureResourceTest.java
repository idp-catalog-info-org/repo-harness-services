
/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.HARSHIT;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.THRISHANK;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.vivekveman;

import static java.util.List.of;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.ff.FeatureFlagService;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.dto.InfrastructureGitUpdateResponseDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureImportResponseDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponse;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponseDTO;
import io.harness.ng.core.infrastructure.dto.RemoteInfrastructuresResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.entity.InfrastructureGovernanceDataResponse;
import io.harness.ng.core.infrastructure.entity.InfrastructureRemoteRepoInfo;
import io.harness.ng.core.infrastructure.entity.InfrastructureRemoteRepoListResponse;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureYamlSchemaHelper;
import io.harness.ng.core.opa.OpaOnSaveEvaluationStatus;
import io.harness.ng.core.opa.OpaOnSaveStatusResponseDTO;
import io.harness.ng.core.opa.gitx.InfrastructureOpaStatusHandler;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ng.opa.gitx.CdOpaOnSaveStatusApiHelper;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.beans.GitYamlValidationRequestParams;
import io.harness.yaml.validator.beans.YamlValidationRequestBody;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.CDC)
public class InfrastructureResourceTest extends CategoryTest {
  @InjectMocks InfrastructureResource infrastructureResource;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock InfrastructureYamlSchemaHelper entityYamlSchemaHelper;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock EnvironmentValidationHelper environmentValidationHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock ScopeAccessHelper scopeAccessHelper;
  @Mock FeatureFlagService featureFlagService;
  @Mock InfrastructureOpaStatusHandler infrastructureOpaStatusHandler;
  @Mock CdOpaOnSaveStatusApiHelper cdOpaOnSaveStatusApiHelper;
  @Spy @InjectMocks InfrastructureHelper infrastructureHelper;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "identifier";
  private final String ENV_IDENTIFIER = "env_identifier";

  private final String NAME = "name";
  private final ClassLoader classLoader = this.getClass().getClassLoader();
  private final ScopeInfo scopeInfo = ScopeInfo.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .uniqueId(PROJ_IDENTIFIER)
                                          .build();
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(infrastructureHelper).set("accessControlClient", accessControlClient);
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testValidateInfrastructureYamlMapsCommitIdIntoDto() {
    GitYamlValidationRequestParams gitParams = GitYamlValidationRequestParams.builder()
                                                   .repoName("repo")
                                                   .filePath(".harness/infra.yaml")
                                                   .branch("main")
                                                   .isDefaultBranch(false)
                                                   .commitId("commit-123")
                                                   .build();
    YamlValidationRequestBody requestBody =
        YamlValidationRequestBody.builder().yaml("infra: {}").gitYamlValidationRequestParams(gitParams).build();
    when(infrastructureEntityService.validateInfrastructureYaml(eq(ACCOUNT_ID), any()))
        .thenReturn(Collections.emptyList());

    infrastructureResource.validateInfrastructureYaml(ACCOUNT_ID, requestBody);

    ArgumentCaptor<YamlValidationRequestDTO> captor = ArgumentCaptor.forClass(YamlValidationRequestDTO.class);
    verify(infrastructureEntityService, times(1)).validateInfrastructureYaml(eq(ACCOUNT_ID), captor.capture());
    assertThat(captor.getValue().getCommitId()).isEqualTo("commit-123");
    assertThat(captor.getValue().getBranch()).isEqualTo("main");
    assertThat(captor.getValue().getFilePath()).isEqualTo(".harness/infra.yaml");
    assertThat(captor.getValue().getRepoName()).isEqualTo("repo");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testCreateServiceWithSchemaValidation() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    String yaml = readFile("InfraYamlWithIncorrectDeploymentType.yaml");

    InfrastructureRequestDTO environmentRequestDTO = InfrastructureRequestDTO.builder()
                                                         .identifier(IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJ_IDENTIFIER)
                                                         .name(NAME)
                                                         .yaml(yaml)
                                                         .build();

    assertThatThrownBy(() -> infrastructureResource.create(ACCOUNT_ID, environmentRequestDTO, null))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, environmentRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpdateServiceWithSchemaValidation() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    String yaml = readFile("InfraYamlWithIncorrectDeploymentType.yaml");

    InfrastructureRequestDTO environmentRequestDTO = InfrastructureRequestDTO.builder()
                                                         .identifier(IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .projectIdentifier(PROJ_IDENTIFIER)
                                                         .name(NAME)
                                                         .yaml(yaml)
                                                         .build();

    assertThatThrownBy(() -> infrastructureResource.update(ACCOUNT_ID, environmentRequestDTO, null))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, environmentRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpsertServiceWithSchemaValidation() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    String yaml = readFile("InfraYamlWithIncorrectDeploymentType.yaml");

    InfrastructureRequestDTO infrastructureRequestDTO = InfrastructureRequestDTO.builder()
                                                            .identifier(IDENTIFIER)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJ_IDENTIFIER)
                                                            .name(NAME)
                                                            .yaml(yaml)
                                                            .build();

    assertThatThrownBy(() -> infrastructureResource.upsert(ACCOUNT_ID, infrastructureRequestDTO))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, infrastructureRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testListAccess() throws IOException {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    InfrastructureEntity infra = InfrastructureEntity.builder()
                                     .identifier(IDENTIFIER)
                                     .accountId(ACCOUNT_ID)
                                     .projectIdentifier(PROJ_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .envIdentifier(ENV_IDENTIFIER)
                                     .yaml("yaml")
                                     .build();
    Page<InfrastructureEntity> infraEntities = new PageImpl<>(Arrays.asList(infra), pageable, 1);

    when(infrastructureEntityService.getScopedInfrastructures(infraEntities, null)).thenReturn(infraEntities);
    doReturn(
        PermissionCheckDTO.builder()
            .permission(VIEW_PROJECT_PERMISSION)
            .resourceIdentifier(PROJ_IDENTIFIER)
            .resourceScope(ResourceScope.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_IDENTIFIER).build())
            .resourceType(PROJECT)
            .build())
        .when(scopeAccessHelper)
        .getPermissionCheckDtoForViewAccessForScope(any());
    when(infrastructureEntityService.list(any(), any(), anyBoolean())).thenReturn(infraEntities);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(AccessCheckResponseDTO.builder()
                 .accessControlList(of(AccessControlDTO.builder()
                                           .permitted(true)
                                           .resourceIdentifier(ENV_IDENTIFIER)
                                           .resourceScope(ResourceScope.builder()
                                                              .accountIdentifier(ACCOUNT_ID)
                                                              .orgIdentifier(ORG_IDENTIFIER)
                                                              .projectIdentifier(PROJ_IDENTIFIER)
                                                              .build())
                                           .build()))
                 .build())
        .when(accessControlClient)
        .checkForAccess(any());

    ResponseDTO<List<InfrastructureResponse>> responseDTO = infrastructureResource.listAccessInfrastructures(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, null, null, null, scopeInfo);

    InfrastructureResponseDTO infrastructure = responseDTO.getData().get(0).getInfrastructure();
    assertThat(infrastructure.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(infrastructure.getEnvironmentRef()).isEqualTo(ENV_IDENTIFIER);
    assertThat(infrastructure.getIdentifier()).isEqualTo(IDENTIFIER);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testListAccessWithMultipleInfrasWithAccessControlCheck() throws IOException {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    InfrastructureEntity infra_project = InfrastructureEntity.builder()
                                             .identifier("infra1")
                                             .accountId(ACCOUNT_ID)
                                             .orgIdentifier(ORG_IDENTIFIER)
                                             .projectIdentifier(PROJ_IDENTIFIER)
                                             .envIdentifier(ENV_IDENTIFIER)
                                             .yaml("yaml")
                                             .build();

    InfrastructureEntity infra_account = InfrastructureEntity.builder()
                                             .identifier("infra2")
                                             .accountId(ACCOUNT_ID)
                                             .envIdentifier("env")
                                             .yaml("yaml")
                                             .build();

    InfrastructureEntity infra_org = InfrastructureEntity.builder()
                                         .identifier("infra3")
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_IDENTIFIER)
                                         .envIdentifier("env")
                                         .yaml("yaml")
                                         .build();

    Page<InfrastructureEntity> infraEntities =
        new PageImpl<>(Arrays.asList(infra_project, infra_account, infra_org), pageable, 1);

    when(infrastructureEntityService.getScopedInfrastructures(infraEntities, null)).thenReturn(infraEntities);
    when(infrastructureEntityService.list(any(), any(), anyBoolean())).thenReturn(infraEntities);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(
        AccessCheckResponseDTO.builder()
            .accessControlList(Arrays.asList(AccessControlDTO.builder()
                                                 .permitted(false)
                                                 .resourceIdentifier(ENV_IDENTIFIER)
                                                 .resourceScope(ResourceScope.builder()
                                                                    .accountIdentifier(ACCOUNT_ID)
                                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                                    .projectIdentifier(PROJ_IDENTIFIER)
                                                                    .build())
                                                 .build(),
                AccessControlDTO.builder()
                    .permitted(true)
                    .resourceIdentifier("env")
                    .resourceScope(
                        ResourceScope.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_IDENTIFIER).build())
                    .build(),
                AccessControlDTO.builder()
                    .permitted(true)
                    .resourceIdentifier("env")
                    .resourceScope(ResourceScope.builder().accountIdentifier(ACCOUNT_ID).build())
                    .build()))
            .build())
        .when(accessControlClient)
        .checkForAccess(any());

    doReturn(
        PermissionCheckDTO.builder()
            .permission(VIEW_PROJECT_PERMISSION)
            .resourceIdentifier(PROJ_IDENTIFIER)
            .resourceScope(ResourceScope.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_IDENTIFIER).build())
            .resourceType(PROJECT)
            .build())
        .when(scopeAccessHelper)
        .getPermissionCheckDtoForViewAccessForScope(any());

    ResponseDTO<List<InfrastructureResponse>> responseDTO = infrastructureResource.listAccessInfrastructures(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, null, null, null, scopeInfo);

    assertThat(responseDTO.getData()).hasSize(2);
    InfrastructureResponseDTO infrastructure1 = responseDTO.getData().get(0).getInfrastructure();
    InfrastructureResponseDTO infrastructure2 = responseDTO.getData().get(1).getInfrastructure();

    assertThat(infrastructure1.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(infrastructure1.getOrgIdentifier()).isEqualTo(null);
    assertThat(infrastructure1.getEnvironmentRef()).isEqualTo("env");
    assertThat(infrastructure1.getIdentifier()).isEqualTo("infra2");

    assertThat(infrastructure2.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(infrastructure2.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(infrastructure2.getEnvironmentRef()).isEqualTo("env");
    assertThat(infrastructure2.getIdentifier()).isEqualTo("infra3");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testListAccessThrowsException() {
    doReturn(
        PermissionCheckDTO.builder()
            .permission(VIEW_PROJECT_PERMISSION)
            .resourceIdentifier(PROJ_IDENTIFIER)
            .resourceScope(ResourceScope.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_IDENTIFIER).build())
            .resourceType(PROJECT)
            .build())
        .when(scopeAccessHelper)
        .getPermissionCheckDtoForViewAccessForScope(any());

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    InfrastructureEntity infra = InfrastructureEntity.builder()
                                     .identifier(IDENTIFIER)
                                     .accountId(ACCOUNT_ID)
                                     .projectIdentifier(PROJ_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .envIdentifier(ENV_IDENTIFIER)
                                     .yaml("yaml")
                                     .build();

    Page<InfrastructureEntity> infraEntities = new PageImpl<>(Arrays.asList(infra), pageable, 1);
    when(infrastructureEntityService.list(any(), any(), anyBoolean())).thenReturn(infraEntities);

    doReturn(AccessCheckResponseDTO.builder()
                 .accessControlList(of(AccessControlDTO.builder()
                                           .permitted(false)
                                           .resourceIdentifier(ENV_IDENTIFIER)
                                           .resourceScope(ResourceScope.builder()
                                                              .accountIdentifier(ACCOUNT_ID)
                                                              .orgIdentifier(ORG_IDENTIFIER)
                                                              .projectIdentifier(PROJ_IDENTIFIER)
                                                              .build())
                                           .build()))
                 .build())
        .when(accessControlClient)
        .checkForAccess(any());

    when(infrastructureEntityService.getScopedInfrastructures(infraEntities, null)).thenReturn(infraEntities);

    infrastructureResource.listAccessInfrastructures(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, null, null, null, scopeInfo);
    verify(scopeAccessHelper, times(1))
        .getPermissionCheckDtoForViewAccessForScope(eq(Scope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)));
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(),
            eq("Unable to list infrastructures because the user is not having view access for the corresponding "
                + "scope"));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testListInfrastructuresV2() throws IOException {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    InfrastructureEntity infra_project = InfrastructureEntity.builder()
                                             .identifier("infra1")
                                             .accountId(ACCOUNT_ID)
                                             .orgIdentifier(ORG_IDENTIFIER)
                                             .projectIdentifier(PROJ_IDENTIFIER)
                                             .envIdentifier(ENV_IDENTIFIER)
                                             .yaml("yaml")
                                             .build();

    InfrastructureEntity infra_account = InfrastructureEntity.builder()
                                             .identifier("infra2")
                                             .accountId(ACCOUNT_ID)
                                             .envIdentifier("env")
                                             .yaml("yaml")
                                             .build();

    InfrastructureEntity infra_org = InfrastructureEntity.builder()
                                         .identifier("infra3")
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_IDENTIFIER)
                                         .envIdentifier("env")
                                         .yaml("yaml")
                                         .build();

    Page<InfrastructureEntity> infraEntities =
        new PageImpl<>(Arrays.asList(infra_project, infra_account, infra_org), pageable, 1);

    when(infrastructureEntityService.getScopedInfrastructures(infraEntities, null)).thenReturn(infraEntities);
    when(infrastructureEntityService.list(any(), any(), anyBoolean())).thenReturn(infraEntities);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(
        AccessCheckResponseDTO.builder()
            .accessControlList(Arrays.asList(AccessControlDTO.builder()
                                                 .permitted(false)
                                                 .resourceIdentifier(ENV_IDENTIFIER)
                                                 .resourceScope(ResourceScope.builder()
                                                                    .accountIdentifier(ACCOUNT_ID)
                                                                    .orgIdentifier(ORG_IDENTIFIER)
                                                                    .projectIdentifier(PROJ_IDENTIFIER)
                                                                    .build())
                                                 .build(),
                AccessControlDTO.builder()
                    .permitted(true)
                    .resourceIdentifier("env")
                    .resourceScope(
                        ResourceScope.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_IDENTIFIER).build())
                    .build(),
                AccessControlDTO.builder()
                    .permitted(true)
                    .resourceIdentifier("env")
                    .resourceScope(ResourceScope.builder().accountIdentifier(ACCOUNT_ID).build())
                    .build()))
            .build())
        .when(accessControlClient)
        .checkForAccess(any());

    ResponseDTO<List<InfrastructureResponse>> responseDTO =
        infrastructureResource.listInfrastructuresV2(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
            null, null, null, null, null, null, null, false, scopeInfo);

    assertThat(responseDTO.getData()).hasSize(2);
    InfrastructureResponseDTO infrastructure1 = responseDTO.getData().get(0).getInfrastructure();
    InfrastructureResponseDTO infrastructure2 = responseDTO.getData().get(1).getInfrastructure();

    assertThat(infrastructure1.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(infrastructure1.getOrgIdentifier()).isEqualTo(null);
    assertThat(infrastructure1.getEnvironmentRef()).isEqualTo("env");
    assertThat(infrastructure1.getIdentifier()).isEqualTo("infra2");

    assertThat(infrastructure2.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(infrastructure2.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(infrastructure2.getEnvironmentRef()).isEqualTo("env");
    assertThat(infrastructure2.getIdentifier()).isEqualTo("infra3");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataForInfrastructure() {
    GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo = GitMetadataUpdateRequestInfoDTO.builder()
                                                                       .connectorRef("newConnectorRef")
                                                                       .filePath("newFilePath")
                                                                       .repoName("repoName")
                                                                       .build();
    doReturn(IDENTIFIER)
        .when(infrastructureEntityService)
        .updateGitMetadata(any(), any(), any(), any(), any(), any(), any());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    ResponseDTO<InfrastructureGitUpdateResponseDTO> response =
        infrastructureResource.updateGitMetadataForInfrastructure(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            ENV_IDENTIFIER, IDENTIFIER, gitMetadataUpdateRequestInfo, null);
    assertEquals(IDENTIFIER, response.getData().getIdentifier());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetWithSchemaValidation() throws IOException {
    String yaml = readFile("InfraYamlWithIncorrectDeploymentType.yaml");
    InfrastructureEntity infra = InfrastructureEntity.builder()
                                     .identifier(IDENTIFIER)
                                     .accountId(ACCOUNT_ID)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJ_IDENTIFIER)
                                     .envIdentifier(ENV_IDENTIFIER)
                                     .type(InfrastructureType.KUBERNETES_AWS)
                                     .storeType(StoreType.REMOTE)
                                     .yaml(yaml)
                                     .build();

    doReturn(Optional.of(infra))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyBoolean(), anyBoolean());

    doThrow(new InvalidYamlException("invalid yaml", YamlSchemaErrorWrapperDTO.builder().build(), yaml))
        .when(entityYamlSchemaHelper)
        .validateSchema(anyString(), anyString());

    InfrastructureResponse response = infrastructureResource
                                          .get(IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER,
                                              false, null, "false", false, null)
                                          .getData();
    assertThat(response.getEntityValidityDetails()).isNotNull();
    assertThat(response.getEntityValidityDetails().isValid()).isFalse();
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testGetSetsOpaOnSaveStatus() throws IOException {
    String yaml = readFile("InfraYamlWithIncorrectDeploymentType.yaml");
    InfrastructureEntity infra = InfrastructureEntity.builder()
                                     .identifier(IDENTIFIER)
                                     .accountId(ACCOUNT_ID)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJ_IDENTIFIER)
                                     .envIdentifier(ENV_IDENTIFIER)
                                     .type(InfrastructureType.KUBERNETES_AWS)
                                     .storeType(StoreType.REMOTE)
                                     .yaml(yaml)
                                     .build();
    doReturn(Optional.of(infra))
        .when(infrastructureEntityService)
        .get(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyBoolean(), anyBoolean());
    OpaOnSaveStatusResponseDTO opaStatus =
        OpaOnSaveStatusResponseDTO.builder().status(OpaOnSaveEvaluationStatus.SUCCESS).build();
    when(cdOpaOnSaveStatusApiHelper.resolveGetOpaOnSaveStatus(any(), any(), any(), any()))
        .thenReturn(Optional.of(opaStatus));

    InfrastructureResponse response = infrastructureResource
                                          .get(IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER,
                                              false, null, "false", false, null)
                                          .getData();

    assertThat(response.getOpaOnSaveStatus()).isEqualTo(opaStatus);
    verify(cdOpaOnSaveStatusApiHelper).resolveGetOpaOnSaveStatus(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testImport() {
    InfrastructureEntity infrastructure = InfrastructureEntity.builder()
                                              .identifier(IDENTIFIER)
                                              .accountId(ACCOUNT_ID)
                                              .orgIdentifier(ORG_IDENTIFIER)
                                              .projectIdentifier(PROJ_IDENTIFIER)
                                              .name(IDENTIFIER)
                                              .envIdentifier(ENV_IDENTIFIER)
                                              .connectorRef("github_con")
                                              .repo("test-repo")
                                              .filePath(".harness/envId.yaml")
                                              .storeType(StoreType.REMOTE)
                                              .build();

    InfrastructureGovernanceDataResponse infrastructureGovernanceDataResponse =
        InfrastructureGovernanceDataResponse.builder().infrastructureEntity(infrastructure).build();

    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(true).when(orgAndProjectValidationHelper).checkThatTheOrganizationAndProjectExists(any(), any(), any());
    ArgumentCaptor<String> identifierCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(infrastructureGovernanceDataResponse)
        .when(infrastructureEntityService)
        .importInfrastructureFromRemote(any(), any(), any(), any(), any(), identifierCaptor.capture(), any());

    ResponseDTO<InfrastructureImportResponseDTO> responseDTO =
        infrastructureResource.importInfrastructureFromGit(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER,
            IDENTIFIER, GitImportInfoDTO.builder().build(), null);

    assertThat(identifierCaptor.getValue()).isEqualTo(IDENTIFIER);
    assertThat(responseDTO.getData().getIdentifier()).isEqualTo(IDENTIFIER);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testCorrectOrgEnvRefWhenCheckAccessAndThrow() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    InfrastructureEntity infra = InfrastructureEntity.builder()
                                     .identifier(IDENTIFIER)
                                     .accountId(ACCOUNT_ID)
                                     .projectIdentifier(PROJ_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .envIdentifier(ENV_IDENTIFIER)
                                     .yaml("yaml")
                                     .build();
    Page<InfrastructureEntity> infraEntities = new PageImpl<>(Arrays.asList(infra), pageable, 1);

    when(infrastructureEntityService.getScopedInfrastructures(infraEntities, null)).thenReturn(infraEntities);
    ArgumentCaptor<Resource> argumentCaptor = ArgumentCaptor.forClass(Resource.class);
    ArgumentCaptor<ResourceScope> argumentCaptor1 = ArgumentCaptor.forClass(ResourceScope.class);
    doReturn(infraEntities).when(infrastructureEntityService).list(any(), any(), anyBoolean());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any());
    infrastructureResource.listInfrastructures(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        "org." + ENV_IDENTIFIER, "", null, ServiceDefinitionType.ASG, "", "", null, null, "", scopeInfo);
    verify(accessControlClient)
        .checkForAccessOrThrow(argumentCaptor1.capture(), argumentCaptor.capture(), any(), any());
    assertThat(argumentCaptor.getValue().getResourceIdentifier()).isEqualTo(ENV_IDENTIFIER);
    assertThat(argumentCaptor1.getValue().getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(argumentCaptor1.getValue().getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(argumentCaptor1.getValue().getProjectIdentifier()).isEqualTo(null);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testCorrectProjectEnvRefWhenCheckAccessAndThrow() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, InfrastructureEntityKeys.createdAt));
    InfrastructureEntity infra = InfrastructureEntity.builder()
                                     .identifier(IDENTIFIER)
                                     .accountId(ACCOUNT_ID)
                                     .projectIdentifier(PROJ_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .envIdentifier(ENV_IDENTIFIER)
                                     .yaml("yaml")
                                     .build();
    Page<InfrastructureEntity> infraEntities = new PageImpl<>(Arrays.asList(infra), pageable, 1);

    when(infrastructureEntityService.getScopedInfrastructures(infraEntities, null)).thenReturn(infraEntities);
    ArgumentCaptor<Resource> argumentCaptor = ArgumentCaptor.forClass(Resource.class);
    ArgumentCaptor<ResourceScope> argumentCaptor1 = ArgumentCaptor.forClass(ResourceScope.class);
    doReturn(infraEntities).when(infrastructureEntityService).list(any(), any(), anyBoolean());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any());
    infrastructureResource.listInfrastructures(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER, "",
        null, ServiceDefinitionType.ASG, "", "", null, null, "", scopeInfo);
    verify(accessControlClient)
        .checkForAccessOrThrow(argumentCaptor1.capture(), argumentCaptor.capture(), any(), any());
    assertThat(argumentCaptor.getValue().getResourceIdentifier()).isEqualTo(ENV_IDENTIFIER);
    assertThat(argumentCaptor1.getValue().getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(argumentCaptor1.getValue().getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(argumentCaptor1.getValue().getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
  }

  private String readFile(String fileName) throws IOException {
    final URL testFile = classLoader.getResource(fileName);
    return Resources.toString(testFile, Charsets.UTF_8);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteInfrastructuresMetadata_emptyResponseWhenServiceReturnsNoRepos() {
    when(infrastructureEntityService.getRemoteRepoListForAGivenScope(
             eq(ACCOUNT_ID), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(InfrastructureRemoteRepoListResponse.builder().repositories(null).build());

    ResponseDTO<RemoteInfrastructuresResponseDTO> response =
        infrastructureResource.getRemoteInfrastructuresMetadata(ACCOUNT_ID, null, null, null, 0, 20, null);

    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getTotalInfrastructures()).isZero();
    assertThat(response.getData().getRepositories()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteInfrastructuresMetadata_mapsServiceLayerInfoAndSumsCount() {
    InfrastructureRemoteRepoInfo repoA = InfrastructureRemoteRepoInfo.builder()
                                             .repoName("repoA")
                                             .repoURL("urlA")
                                             .count(4L)
                                             .filePathsByOwningScope(Collections.emptyMap())
                                             .connectorRefs(Collections.emptySet())
                                             .build();
    InfrastructureRemoteRepoInfo repoB = InfrastructureRemoteRepoInfo.builder()
                                             .repoName("repoB")
                                             .repoURL("urlB")
                                             .count(1L)
                                             .filePathsByOwningScope(Collections.emptyMap())
                                             .connectorRefs(Collections.emptySet())
                                             .build();
    when(infrastructureEntityService.getRemoteRepoListForAGivenScope(
             any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(InfrastructureRemoteRepoListResponse.builder().repositories(List.of(repoA, repoB)).build());

    ResponseDTO<RemoteInfrastructuresResponseDTO> response = infrastructureResource.getRemoteInfrastructuresMetadata(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "repoA", 0, 20, null);

    assertThat(response.getData().getTotalInfrastructures()).isEqualTo(5L);
    assertThat(response.getData().getRepositories()).hasSize(2);
    assertThat(response.getData().getRepositories().get(0).getRepoName()).isEqualTo("repoA");
    assertThat(response.getData().getRepositories().get(0).getCount()).isEqualTo(4L);
    verify(infrastructureEntityService, times(1))
        .getRemoteRepoListForAGivenScope(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "repoA", null, 0, 20);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteInfrastructuresMetadata_propagatesServiceLayerException() {
    when(infrastructureEntityService.getRemoteRepoListForAGivenScope(
             any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenThrow(new InvalidRequestException("boom"));

    assertThatThrownBy(
        () -> infrastructureResource.getRemoteInfrastructuresMetadata(ACCOUNT_ID, null, null, null, 0, 20, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("boom");
  }
}
