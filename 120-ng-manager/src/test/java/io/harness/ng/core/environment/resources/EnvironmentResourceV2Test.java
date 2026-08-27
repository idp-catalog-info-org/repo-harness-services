/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;
import static io.harness.ng.core.environment.beans.EnvironmentType.PreProduction;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.rule.OwnerRule.ABHISHEK_ARYAN;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.AYUSHMAN;
import static io.harness.rule.OwnerRule.HIMANSHU;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.THRISHANK;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.vivekveman;

import static java.util.List.of;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO.AccessControlDTOBuilder;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceEntityValidationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.infrastructure.unified.UnifiedEnvListConverterResponse;
import io.harness.infrastructure.unified.UnifiedEnvListRequestDTO;
import io.harness.ng.accesscontrol.PlatformPermissions;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.beans.EntityWithGitInfo;
import io.harness.ng.core.beans.EnvironmentAndServiceOverridesMetadataInput;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentCloneResponse;
import io.harness.ng.core.environment.beans.EnvironmentGitUpdateResponseDTO;
import io.harness.ng.core.environment.beans.EnvironmentGovernanceDataResponse;
import io.harness.ng.core.environment.beans.EnvironmentInputSetYamlAndServiceOverridesMetadataDTO;
import io.harness.ng.core.environment.beans.EnvironmentRemoteRepoInfo;
import io.harness.ng.core.environment.beans.EnvironmentRemoteRepoListResponse;
import io.harness.ng.core.environment.beans.ForceImportEnvironmentYamlOperationDTO;
import io.harness.ng.core.environment.dto.DestinationEnvironmentConfig;
import io.harness.ng.core.environment.dto.EnvironmentBatchResponse;
import io.harness.ng.core.environment.dto.EnvironmentCloneRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentCloneResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentFailureDTO;
import io.harness.ng.core.environment.dto.EnvironmentImportResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.environment.dto.ForceImportEnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.ForceImportEnvironmentResponse;
import io.harness.ng.core.environment.dto.RemoteEnvironmentsResponseDTO;
import io.harness.ng.core.environment.dto.SourceEnvironmentConfig;
import io.harness.ng.core.environment.helpers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.opa.OpaOnSaveEvaluationStatus;
import io.harness.ng.core.opa.OpaOnSaveStatusResponseDTO;
import io.harness.ng.core.opa.gitx.EnvironmentOpaStatusHandler;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverrides.resources.ServiceOverridesResource;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideRequestDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesResponseDTOV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ng.opa.gitx.CdOpaOnSaveStatusApiHelper;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.beans.GitYamlValidationRequestParams;
import io.harness.yaml.validator.beans.YamlValidationRequestBody;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;

import software.wings.beans.ServiceKeys;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentResourceV2Test extends CategoryTest {
  @InjectMocks EnvironmentResourceV2 environmentResourceV2;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock EnvironmentEntityYamlSchemaHelper entityYamlSchemaHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock EnvironmentService environmentService;
  @Mock ServiceOverrideService serviceOverrideService;
  @Mock ServiceEntityValidationHelper serviceEntityValidationHelper;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock EnvironmentValidationHelper environmentValidationHelper;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Mock private NGSettingsClient ngSettingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock private ServiceOverridesResource serviceOverridesResource;
  @Mock EnvironmentRbacHelper environmentRbacHelper;
  @Mock EnvironmentFilterHelper environmentFilterHelper;
  @Mock ScopeAccessHelper scopeAccessHelper;
  @Mock EnvironmentCloneHelper environmentCloneHelper;
  @Mock ScopeInfoService scopeInfoService;
  @Mock CDOverviewDashboardService cdOverviewDashboardService;
  @Mock EnvironmentOpaStatusHandler environmentOpaStatusHandler;
  @Mock CdOpaOnSaveStatusApiHelper cdOpaOnSaveStatusApiHelper;

  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String IDENTIFIER = "identifier";
  private static final String NAME = "name";

  private static final String ENV_IDENTIFIER = "envId";
  private static final String SVC_IDENTIFIER = "svcId";

  private final String OVERRIDE_YAML = "serviceOverrides:\n  environmentRef: envId\n  serviceRef: svcId\n  "
      + "variables:\n    - name: var1\n      type: String\n      value: val1\n";

  private static final Environment entity = Environment.builder()
                                                .identifier("id")
                                                .projectIdentifier(PROJ_IDENTIFIER)
                                                .orgIdentifier(ORG_IDENTIFIER)
                                                .accountId(ACCOUNT_ID)
                                                .type(PreProduction)
                                                .build();
  private static final ServiceOverridesResponseDTOV2 OVERRIDE_RESPONSE =
      ServiceOverridesResponseDTOV2.builder()
          .identifier("OverrideId")
          .environmentRef(ENV_IDENTIFIER)
          .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
          .spec(ServiceOverridesSpec.builder().build())
          .build();

  private final ClassLoader classLoader = this.getClass().getClassLoader();

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    doReturn(ResponseDTO.newResponse(OVERRIDE_RESPONSE))
        .when(serviceOverridesResource)
        .create(anyString(), any(ServiceOverrideRequestDTOV2.class), any());
    doReturn(ResponseDTO.newResponse(OVERRIDE_RESPONSE))
        .when(serviceOverridesResource)
        .update(anyString(), any(ServiceOverrideRequestDTOV2.class), any());
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testValidateEnvironmentYamlMapsCommitIdIntoDto() {
    GitYamlValidationRequestParams gitParams = GitYamlValidationRequestParams.builder()
                                                   .repoName("repo")
                                                   .filePath(".harness/env.yaml")
                                                   .branch("main")
                                                   .isDefaultBranch(false)
                                                   .commitId("commit-123")
                                                   .build();
    YamlValidationRequestBody requestBody =
        YamlValidationRequestBody.builder().yaml("environment: {}").gitYamlValidationRequestParams(gitParams).build();
    when(environmentService.validateEnvironmentYaml(eq(ACCOUNT_ID), any())).thenReturn(Collections.emptyList());

    environmentResourceV2.validateEnvironmentYaml(ACCOUNT_ID, requestBody);

    // Webhook MODIFIED plumbing: the commitId from the request must be mapped into the validation DTO.
    ArgumentCaptor<YamlValidationRequestDTO> captor = ArgumentCaptor.forClass(YamlValidationRequestDTO.class);
    verify(environmentService, times(1)).validateEnvironmentYaml(eq(ACCOUNT_ID), captor.capture());
    assertThat(captor.getValue().getCommitId()).isEqualTo("commit-123");
    assertThat(captor.getValue().getBranch()).isEqualTo("main");
    assertThat(captor.getValue().getFilePath()).isEqualTo(".harness/env.yaml");
    assertThat(captor.getValue().getRepoName()).isEqualTo("repo");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testCreateEnvironmentWithSchemaValidation() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    String yaml = readFile("ManifestYamlWithoutSpec.yaml");

    EnvironmentRequestDTO environmentRequestDTO = EnvironmentRequestDTO.builder()
                                                      .identifier(IDENTIFIER)
                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                      .projectIdentifier(PROJ_IDENTIFIER)
                                                      .name(NAME)
                                                      .yaml(yaml)
                                                      .type(PreProduction)
                                                      .build();

    assertThatThrownBy(
        () -> environmentResourceV2.create(ACCOUNT_ID, environmentRequestDTO, GitEntityCreateInfoDTO.builder().build()))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, environmentRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpdateEnvironmentWithSchemaValidation() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PROJ_IDENTIFIER)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    doReturn(scopeInfo).when(scopeInfoService).getScopeInfo(anyString(), anyString(), anyString());

    String yaml = readFile("ManifestYamlWithoutSpec.yaml");

    EnvironmentRequestDTO environmentRequestDTO = EnvironmentRequestDTO.builder()
                                                      .identifier(IDENTIFIER)
                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                      .projectIdentifier(PROJ_IDENTIFIER)
                                                      .name(NAME)
                                                      .yaml(yaml)
                                                      .type(PreProduction)
                                                      .build();
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(NGResourceType.ENVIRONMENT)
                                                          .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACCOUNT_ID)
                                                                             .orgIdentifier(ORG_IDENTIFIER)
                                                                             .projectIdentifier(PROJ_IDENTIFIER)
                                                                             .build());

    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("identifier").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("identifier").build());

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(accessCheckResponseDTO).when(accessControlClient).checkForAccessOrThrow(anyList());
    assertThatThrownBy(()
                           -> environmentResourceV2.update(
                               IF_MATCH, ACCOUNT_ID, environmentRequestDTO, GitEntityUpdateInfoDTO.builder().build()))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, environmentRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpsertServiceWithSchemaValidation() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    String yaml = readFile("ManifestYamlWithoutSpec.yaml");

    EnvironmentRequestDTO environmentRequestDTO = EnvironmentRequestDTO.builder()
                                                      .identifier(IDENTIFIER)
                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                      .projectIdentifier(PROJ_IDENTIFIER)
                                                      .name(NAME)
                                                      .yaml(yaml)
                                                      .type(PreProduction)
                                                      .build();
    List<AccessControlDTO> accessControlDTOS = new ArrayList<>();

    AccessControlDTOBuilder accessControlDTOBuilder = AccessControlDTO.builder()
                                                          .resourceType(NGResourceType.ENVIRONMENT)
                                                          .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                                          .resourceScope(ResourceScope.builder()
                                                                             .accountIdentifier(ACCOUNT_ID)
                                                                             .orgIdentifier(ORG_IDENTIFIER)
                                                                             .projectIdentifier(PROJ_IDENTIFIER)
                                                                             .build());

    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("identifier").build());
    accessControlDTOS.add(accessControlDTOBuilder.permitted(true).resourceIdentifier("identifier").build());

    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .principal(Principal.builder().principalIdentifier("id").principalType(USER).build())
            .accessControlList(accessControlDTOS)
            .build();

    doReturn(accessCheckResponseDTO).when(accessControlClient).checkForAccessOrThrow(anyList());

    assertThatThrownBy(() -> environmentResourceV2.upsert(IF_MATCH, ACCOUNT_ID, environmentRequestDTO))
        .isInstanceOf(InvalidRequestException.class);

    verify(entityYamlSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, environmentRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGet() {
    doReturn(Optional.of(entity))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());
    when(accessControlClient.checkForAccessOrThrow(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Arrays.asList(AccessControlDTO.builder().permitted(true).build()))
                        .build());
    ResponseDTO<EnvironmentResponse> environmentResponseResponseDTO = environmentResourceV2.get(IDENTIFIER, ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, false, GitEntityFindInfoDTO.builder().build(), "false", false, null);
    assertThat(environmentResponseResponseDTO.getEntityTag()).isNull();
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testGetSetsOpaOnSaveStatus() {
    doReturn(Optional.of(entity))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());
    when(accessControlClient.checkForAccessOrThrow(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Arrays.asList(AccessControlDTO.builder().permitted(true).build()))
                        .build());
    OpaOnSaveStatusResponseDTO opaStatus =
        OpaOnSaveStatusResponseDTO.builder().status(OpaOnSaveEvaluationStatus.SUCCESS).build();
    when(cdOpaOnSaveStatusApiHelper.resolveGetOpaOnSaveStatus(any(), any(), any(), any()))
        .thenReturn(Optional.of(opaStatus));

    EnvironmentResponse response = environmentResourceV2
                                       .get(IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false,
                                           GitEntityFindInfoDTO.builder().build(), "false", false, null)
                                       .getData();

    assertThat(response.getOpaOnSaveStatus()).isEqualTo(opaStatus);
    verify(cdOpaOnSaveStatusApiHelper).resolveGetOpaOnSaveStatus(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpsertServiceOverrideCreate() throws IOException {
    ServiceOverrideRequestDTO requestDTO = ServiceOverrideRequestDTO.builder()
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .environmentIdentifier(ENV_IDENTIFIER)
                                               .serviceIdentifier(SVC_IDENTIFIER)
                                               .yaml(OVERRIDE_YAML)
                                               .build();
    mockedReturnOverrideV2EnabledTrue();

    ResponseDTO<ServiceOverrideResponseDTO> serviceOverrideResponseDTOResponseDTO =
        environmentResourceV2.upsertServiceOverride(ACCOUNT_ID, requestDTO);

    ServiceOverrideResponseDTO serviceOverrideResponseDTO = serviceOverrideResponseDTOResponseDTO.getData();
    assertThat(serviceOverrideResponseDTO).isNotNull();
    assertThat(serviceOverrideResponseDTO.getYaml()).isEqualTo(OVERRIDE_YAML);

    ArgumentCaptor<ServiceOverrideRequestDTOV2> requestDTOV2Captor =
        ArgumentCaptor.forClass(ServiceOverrideRequestDTOV2.class);
    verify(serviceOverridesResource, times(1)).create(eq(ACCOUNT_ID), requestDTOV2Captor.capture(), any());

    assertRequestDTOV2(requestDTOV2Captor.getValue());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testUpsertServiceOverrideUpdate() throws IOException {
    ServiceOverrideRequestDTO requestDTO = ServiceOverrideRequestDTO.builder()
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .environmentIdentifier(ENV_IDENTIFIER)
                                               .serviceIdentifier(SVC_IDENTIFIER)
                                               .yaml(OVERRIDE_YAML)
                                               .build();
    mockedReturnOverrideV2EnabledTrue();
    doReturn(Optional.of(NGServiceOverridesEntity.builder().build()))
        .when(serviceOverrideService)
        .getForV1AndV2(any(), any(), any(), any(), any());

    ResponseDTO<ServiceOverrideResponseDTO> serviceOverrideResponseDTOResponseDTO =
        environmentResourceV2.upsertServiceOverride(ACCOUNT_ID, requestDTO);

    ServiceOverrideResponseDTO serviceOverrideResponseDTO = serviceOverrideResponseDTOResponseDTO.getData();
    assertThat(serviceOverrideResponseDTO).isNotNull();
    assertThat(serviceOverrideResponseDTO.getYaml()).isEqualTo(OVERRIDE_YAML);

    ArgumentCaptor<ServiceOverrideRequestDTOV2> requestDTOV2Captor =
        ArgumentCaptor.forClass(ServiceOverrideRequestDTOV2.class);
    verify(serviceOverridesResource, times(1)).update(eq(ACCOUNT_ID), requestDTOV2Captor.capture(), any());

    assertRequestDTOV2(requestDTOV2Captor.getValue());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataForEnvironment() {
    GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo = GitMetadataUpdateRequestInfoDTO.builder()
                                                                       .connectorRef("newConnectorRef")
                                                                       .filePath("newFilePath")
                                                                       .repoName("repoName")
                                                                       .build();
    doReturn(ENV_IDENTIFIER).when(environmentService).updateGitMetadata(any(), any(), any(), any(), any(), any());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any());

    ResponseDTO<EnvironmentGitUpdateResponseDTO> response = environmentResourceV2.updateGitMetadataForEnvironment(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER, gitMetadataUpdateRequestInfo, null);
    assertEquals(ENV_IDENTIFIER, response.getData().getIdentifier());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetWithSchemaValidation() throws IOException {
    // yaml that needs to be invalidated
    String yaml = readFile("ManifestYamlWithoutSpec.yaml");
    Environment environment = Environment.builder()
                                  .identifier(IDENTIFIER)
                                  .name(NAME)
                                  .projectIdentifier(PROJ_IDENTIFIER)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .accountId(ACCOUNT_ID)
                                  .type(PreProduction)
                                  .storeType(StoreType.REMOTE)
                                  .yaml(yaml)
                                  .build();

    doReturn(Optional.of(environment))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());
    when(accessControlClient.checkForAccessOrThrow(anyList()))
        .thenReturn(AccessCheckResponseDTO.builder()
                        .accessControlList(Arrays.asList(AccessControlDTO.builder().permitted(true).build()))
                        .build());

    doThrow(new InvalidYamlException("invalid yaml", YamlSchemaErrorWrapperDTO.builder().build(), yaml))
        .when(entityYamlSchemaHelper)
        .validateSchema(anyString(), anyString());

    // call to get environment
    EnvironmentResponse response =
        environmentResourceV2
            .get(IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false, null, "false", false, null)
            .getData();
    assertThat(response.getEntityValidityDetails()).isNotNull();
    assertThat(response.getEntityValidityDetails().isValid()).isFalse();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testImport() throws Exception {
    Environment environment = Environment.builder()
                                  .identifier(ENV_IDENTIFIER)
                                  .accountId(ACCOUNT_ID)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .projectIdentifier(PROJ_IDENTIFIER)
                                  .name(ENV_IDENTIFIER)
                                  .connectorRef("github_con")
                                  .repo("test-repo")
                                  .filePath(".harness/envId.yaml")
                                  .storeType(StoreType.REMOTE)
                                  .build();

    EnvironmentGovernanceDataResponse environmentGovernanceDataResponse =
        EnvironmentGovernanceDataResponse.builder().environment(environment).build();

    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    doReturn(true).when(orgAndProjectValidationHelper).checkThatTheOrganizationAndProjectExists(any(), any(), any());
    doReturn(environmentGovernanceDataResponse)
        .when(environmentService)
        .importEnvironmentFromRemote(any(), any(), any(), any(), any(), any());
    doReturn(false).when(featureFlagHelperService).isEnabled(any(), any());

    ResponseDTO<EnvironmentImportResponseDTO> responseDTO = environmentResourceV2.importEnvironmentFromGit(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, GitImportInfoDTO.builder().build(), null);

    assertThat(responseDTO.getData().getEnvIdentifier()).isEqualTo(ENV_IDENTIFIER);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetEnvOverridesMetadataWithEmptyServiceIdList() throws IOException {
    EnvironmentAndServiceOverridesMetadataInput metadataInput =
        EnvironmentAndServiceOverridesMetadataInput.builder()
            .entityWithGitInfoList(Collections.singletonList(EntityWithGitInfo.builder().ref("env").build()))
            .serviceIdentifiers(List.of(""))
            .build();
    doReturn(EnvironmentInputSetYamlAndServiceOverridesMetadataDTO.builder().build())
        .when(environmentService)
        .getEnvironmentsInputYamlAndServiceOverridesMetadata(
            anyString(), anyString(), anyString(), anyList(), anyMap(), anyMap(), anyBoolean(), anyBoolean());

    ResponseDTO<EnvironmentInputSetYamlAndServiceOverridesMetadataDTO> responseDTO =
        environmentResourceV2.getEnvironmentsInputYamlAndServiceOverridesV2(
            metadataInput, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "false", null);

    assertThat(responseDTO.getData()).isNotNull();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testlistAccessEnvironmentThrowsException() {
    mockScopeInfoServiceForAnyParentUniqueId();
    Environment environment = Environment.builder().identifier("i1").build();
    List<Environment> list = new ArrayList<>();
    list.add(environment);
    doReturn(list).when(environmentService).listAccess(any());

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

    doReturn(
        PermissionCheckDTO.builder()
            .permission(PlatformPermissions.VIEW_PROJECT_PERMISSION)
            .resourceIdentifier(PROJ_IDENTIFIER)
            .resourceScope(ResourceScope.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_IDENTIFIER).build())
            .resourceType(PROJECT)
            .build())
        .when(scopeAccessHelper)
        .getPermissionCheckDtoForViewAccessForScope(any());

    environmentResourceV2.listAccessEnvironmentsV2(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, false, null);
    verify(scopeAccessHelper, times(1))
        .getPermissionCheckDtoForViewAccessForScope(eq(Scope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER)));
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(),
            eq("Unable to list environments because the user is not having view access for the corresponding scope"));
  }

  private void assertRequestDTOV2(ServiceOverrideRequestDTOV2 requestDTOV2) {
    assertThat(requestDTOV2.isV1Api()).isTrue();
    assertThat(requestDTOV2.getYamlInternal())
        .isEqualTo("serviceOverrides:\n  environmentRef: envId\n  serviceRef: svcId\n  variables:\n    - name: var1\n  "
            + "    type: String\n      value: val1\n");
    assertThat(requestDTOV2.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(requestDTOV2.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(requestDTOV2.getEnvironmentRef()).isEqualTo(ENV_IDENTIFIER);
    assertThat(requestDTOV2.getServiceRef()).isEqualTo(SVC_IDENTIFIER);

    assertThat(requestDTOV2.getType()).isEqualTo(ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    assertThat(requestDTOV2.getSpec()).isNotNull();

    assertThat(requestDTOV2.getSpec().getVariables()).hasSize(1);
    assertThat(requestDTOV2.getSpec().getVariables().stream().map(NGVariable::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("var1");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testListEnvironmentsV3() {
    mockScopeInfoServiceForAnyParentUniqueId();
    Environment environment = Environment.builder().identifier("i1").build();
    List<Environment> list = new ArrayList<>();
    list.add(environment);
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<Environment> page = new PageImpl<>(Collections.singletonList(environment), pageable, 1);
    doReturn(page).when(environmentService).list(any(), any());
    doReturn(list).when(environmentRbacHelper).getPermittedEnvironmentsListV2(any(), any());
    Criteria criteria = new Criteria();
    doReturn(criteria)
        .when(environmentFilterHelper)
        .createCriteriaForGetList(any(), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean(), any());
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.listEnvironmentsV3(
        0, 500, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, false, null, false, null);

    verify(environmentRbacHelper, times(1)).getPermittedEnvironmentsListV2(any(), any());
    verify(environmentService, times(2)).list(criteriaArgumentCaptor.capture(), any());

    Criteria criteria2 = criteriaArgumentCaptor.getValue();

    Assertions.assertThat(criteria2.getCriteriaObject().get("identifier").toString()).contains("i1");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testListAccessEnvironment() {
    mockScopeInfoServiceForAnyParentUniqueId();
    Environment env1 = Environment.builder().identifier("env1").name("Test Env 1").build();
    Environment env2 = Environment.builder().identifier("env2").name("Test Env 2").build();
    List<Environment> environments = Arrays.asList(env1, env2);
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(List.of(AccessControlDTO.builder().permitted(true).resourceIdentifier("env1").build(),
                AccessControlDTO.builder().permitted(true).resourceIdentifier("env2").build()))
            .build();

    PermissionCheckDTO permissionCheckDTO = PermissionCheckDTO.builder().build();
    doReturn(permissionCheckDTO).when(scopeAccessHelper).getPermissionCheckDtoForViewAccessForScope(any());

    doReturn(null).when(accessControlClient).checkForAccess(anyList());

    Criteria criteria = new Criteria();
    doReturn(criteria)
        .when(environmentFilterHelper)
        .createCriteriaForGetList(any(), any(), any(), anyBoolean(), any(), any());
    doReturn(environments).when(environmentService).listAccess(any());
    when(accessControlClient.checkForAccess(any())).thenReturn(accessCheckResponseDTO);

    doCallRealMethod().when(environmentRbacHelper).filterEnvironmentResponseByPermissionAndId(any(), any());
    ResponseDTO<List<EnvironmentResponse>> response = environmentResourceV2.listAccessEnvironment(
        0, 100, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, PreProduction, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).hasSize(2);
    assertThat(response.getData().get(0).getEnvironment().getIdentifier()).isEqualTo("env1");
    assertThat(response.getData().get(1).getEnvironment().getIdentifier()).isEqualTo("env2");

    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any());
    verify(environmentService, times(1)).listAccess(any());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCloneEnvironment() {
    InfrastructureEntity infrastructure = InfrastructureEntity.builder()
                                              .identifier("id1")
                                              .envIdentifier("id")
                                              .projectIdentifier(PROJ_IDENTIFIER)
                                              .orgIdentifier(ORG_IDENTIFIER)
                                              .accountId(ACCOUNT_ID)
                                              .build();
    // The cloned environment must carry a parentUniqueId, matching what any real (GA'd, migrated)
    // environment record has, and matching the ScopeInfo resolved for the destination scope below -
    // cloneEnvironment() now unconditionally dereferences scopeInfo.getUniqueId().
    Environment clonedEnvironment = Environment.builder()
                                        .identifier("id")
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .accountId(ACCOUNT_ID)
                                        .type(PreProduction)
                                        .parentUniqueId(PROJ_IDENTIFIER)
                                        .build();
    EnvironmentCloneResponse environmentCloneResponse =
        EnvironmentCloneResponse.builder()
            .infrastructureEntities(Collections.singletonList(infrastructure))
            .cloneFailedInfrastructures(new ArrayList<>())
            .environment(clonedEnvironment)
            .build();
    when(environmentCloneHelper.cloneEnvironment(any(), any(), any(), anyBoolean()))
        .thenReturn(environmentCloneResponse);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PROJ_IDENTIFIER)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    doReturn(scopeInfo).when(scopeInfoService).getScopeInfo(any(), any(), any());

    EnvironmentCloneRequestDTO environmentCloneRequestDTO =
        EnvironmentCloneRequestDTO.builder()
            .destinationConfig(DestinationEnvironmentConfig.builder().envIdentifier("e1").build())
            .sourceConfig(SourceEnvironmentConfig.builder().build())
            .build();

    ResponseDTO<EnvironmentCloneResponseDTO> responseDTO =
        environmentResourceV2.cloneEnvironment(ACCOUNT_ID, environmentCloneRequestDTO);
    assertThat(responseDTO.getData().getEnvironment().getType()).isEqualTo(PreProduction);
    assertThat(responseDTO.getData().getInfrastructureResponseList().get(0).getInfrastructure().getEnvironmentRef())
        .isEqualTo("id");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldConvertToUnifiedEnvironmentsListUseRefsWhenCrossScopedFFOn() {
    String accountId = "accountId";
    String orgIdentifier = "orgId";
    String projectIdentifier = "projectId";
    List<String> envRefs = List.of("account.env1", "org.env2", "env3");
    Environment environment =
        Environment.builder().identifier("env1").accountId(accountId).orgIdentifier(orgIdentifier).build();
    List<Environment> environments = List.of(environment);
    when(featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_CROSS_SCOPED_ENV_GROUPS)).thenReturn(true);
    when(environmentService.fetchesEnvFromListOfRefs(accountId, orgIdentifier, projectIdentifier, envRefs))
        .thenReturn(environments);
    UnifiedEnvListRequestDTO requestDTO =
        UnifiedEnvListRequestDTO.builder().envRefs(envRefs).fetchAllEnvs(false).build();
    ResponseDTO<UnifiedEnvListConverterResponse> response = environmentResourceV2.convertToUnifiedEnvironmentsList(
        accountId, orgIdentifier, projectIdentifier, requestDTO, null);
    assertThat(response.getData().getEnvironments()).hasSize(1);
    assertThat(response.getData().getEnvironments().get(0).getIdentifier()).isEqualTo("env1");
    verify(environmentService, times(1)).fetchesEnvFromListOfRefs(accountId, orgIdentifier, projectIdentifier, envRefs);
    verify(environmentService, never())
        .fetchesEnvFromListOfIdentifiers(accountId, orgIdentifier, projectIdentifier, envRefs);
    verify(featureFlagHelperService, times(1)).isEnabled(accountId, FeatureName.CDS_CROSS_SCOPED_ENV_GROUPS);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testForceImportEnvironment() {
    ForceImportEnvironmentRequestDTO requestDTO = ForceImportEnvironmentRequestDTO.builder()
                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                      .projectIdentifier(PROJ_IDENTIFIER)
                                                      .identifier(IDENTIFIER)
                                                      .type("PreProduction")
                                                      .connectorRef("connectorRef")
                                                      .filePath(".harness/t1_v1.yaml")
                                                      .repoName("test-repo")
                                                      .build();

    doReturn(ForceImportEnvironmentResponse.builder().build())
        .when(environmentService)
        .forceImportEnvironment(any(), any());

    ArgumentCaptor<ForceImportEnvironmentYamlOperationDTO> operationDTOCaptor =
        ArgumentCaptor.forClass(ForceImportEnvironmentYamlOperationDTO.class);
    environmentResourceV2.forceImportEnvironment(ACCOUNT_ID, requestDTO);

    verify(environmentService).forceImportEnvironment(any(), operationDTOCaptor.capture());
    assertThat(operationDTOCaptor.getValue().getConnectorRef()).isEqualTo("connectorRef");
    assertThat(operationDTOCaptor.getValue().getFilePath()).isEqualTo(".harness/t1_v1.yaml");
    assertThat(operationDTOCaptor.getValue().getRepoName()).isEqualTo("test-repo");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testInvalidManifestOverrideTypeWithFFEnabled() {
    String ECS_TASK_DEFINITION = "serviceOverrides:\n"
        + "  environmentRef: cloudFormationEnv\n"
        + "  serviceRef: ecsService\n"
        + "  manifests:\n"
        + "    - manifest:\n"
        + "        identifier: m1\n"
        + "        type: EcsTaskDefinition\n"
        + "        spec:\n"
        + "          store:\n"
        + "            type: Harness\n"
        + "            spec:\n"
        + "              files:\n"
        + "                - /ecsCanary\n";

    NGServiceOverridesEntity ecsTaskDefinitionServiceOverridesEntity = NGServiceOverridesEntity.builder()
                                                                           .identifier("OVERRIDE_IDENTIFIER")
                                                                           .accountId(ACCOUNT_ID)
                                                                           .projectIdentifier("projectIdentifier")
                                                                           .orgIdentifier("orgIdentifier")
                                                                           .environmentRef("environmentRef")
                                                                           .serviceRef("serviceRef")
                                                                           .yaml(ECS_TASK_DEFINITION)
                                                                           .yamlV2(null)
                                                                           .isV2(false)
                                                                           .build();
    environmentResourceV2.validateServiceOverrides(
        ecsTaskDefinitionServiceOverridesEntity, "orgIdentifier", "projectIdentifier");

    String AWS_LAMBDA_TASK_DEFINITION = "serviceOverrides:\n"
        + "  environmentRef: cloudFormationEnv\n"
        + "  serviceRef: lambdaService\n"
        + "  manifests:\n"
        + "    - manifest:\n"
        + "        identifier: m1\n"
        + "        type: AwsLambdaFunctionDefinition\n"
        + "        spec:\n"
        + "          store:\n"
        + "            type: Harness\n"
        + "            spec:\n"
        + "              files:\n"
        + "                - /ecsCanary\n";

    NGServiceOverridesEntity awsLambdaDefinitionServiceOverridesEntity = NGServiceOverridesEntity.builder()
                                                                             .identifier("OVERRIDE_IDENTIFIER")
                                                                             .accountId(ACCOUNT_ID)
                                                                             .projectIdentifier("projectIdentifier")
                                                                             .orgIdentifier("orgIdentifier")
                                                                             .environmentRef("environmentRef")
                                                                             .serviceRef("serviceRef")
                                                                             .yaml(AWS_LAMBDA_TASK_DEFINITION)
                                                                             .yamlV2(null)
                                                                             .isV2(false)
                                                                             .build();
    assertThatThrownBy(()
                           -> environmentResourceV2.validateServiceOverrides(
                               awsLambdaDefinitionServiceOverridesEntity, "orgIdentifier", "projectIdentifier"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported Manifest Types: [AwsLambdaFunctionDefinition] found for service overrides");
  }

  private void mockedReturnOverrideV2EnabledTrue() throws IOException {
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACCOUNT_ID, FeatureName.CDS_SERVICE_OVERRIDES_2_0);
  }

  // The ScopeInfo-based resolution path is now unconditional (PL_USE_SCOPE_INFO_FOR_ENV_ENTITY removed), so
  // scopeInfoService.getScopeInfo(accountId, Set<parentUniqueId>) must always be stubbed, otherwise
  // EnvironmentMapper.writeDTO NPEs on a null ScopeInfo. Resolves every requested uniqueId (including null,
  // for fixtures that don't set parentUniqueId) to a valid ScopeInfo.
  private void mockScopeInfoServiceForAnyParentUniqueId() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PROJ_IDENTIFIER)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    doAnswer(invocation -> {
      Set<String> uniqueIds = invocation.getArgument(1);
      Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
      for (String uniqueId : uniqueIds) {
        scopeInfoMap.put(uniqueId, Optional.of(scopeInfo));
      }
      return scopeInfoMap;
    })
        .when(scopeInfoService)
        .getScopeInfo(anyString(), any());
  }

  private String readFile(String fileName) throws IOException {
    final URL testFile = classLoader.getResource(fileName);
    return Resources.toString(testFile, Charsets.UTF_8);
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testListEnvironmentsV2_SkipsNullEnvironments_WithLimitedPermissions() {
    mockScopeInfoServiceForAnyParentUniqueId();
    Environment validEnv = Environment.builder()
                               .identifier("validEnv")
                               .accountId(ACCOUNT_ID)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJ_IDENTIFIER)
                               .yaml("")
                               .build();

    List<Environment> environmentsWithNull = Arrays.asList(validEnv, null, validEnv);
    Page<Environment> mockPage = new PageImpl<>(environmentsWithNull, PageRequest.of(0, 10), 3);

    Criteria criteria = new Criteria();
    doReturn(criteria)
        .when(environmentFilterHelper)
        .createCriteriaForGetList(any(), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean(), any());

    doReturn(false).when(environmentRbacHelper).hasRequiredPermissionForAllEnvironments(any(), any(), any(), any());

    doReturn(mockPage).when(environmentService).list(any(Criteria.class), eq(Pageable.unpaged()));
    doReturn(environmentsWithNull).when(environmentRbacHelper).getPermittedEnvironmentsList(any());

    doReturn(mockPage).when(environmentService).list(any(Criteria.class), any(PageRequest.class));

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.listEnvironmentsV2(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, false, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testListEnvironmentsV2_SkipsNullEnvironments_WithFullPermissions() {
    mockScopeInfoServiceForAnyParentUniqueId();
    Environment validEnv = Environment.builder()
                               .identifier("validEnv")
                               .accountId(ACCOUNT_ID)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJ_IDENTIFIER)
                               .yaml("")
                               .build();

    List<Environment> environmentsWithNull = Arrays.asList(validEnv, null, validEnv);
    Page<Environment> mockPage = new PageImpl<>(environmentsWithNull, PageRequest.of(0, 10), 3);

    Criteria criteria = new Criteria();
    doReturn(criteria)
        .when(environmentFilterHelper)
        .createCriteriaForGetList(any(), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean(), any());

    doReturn(true).when(environmentRbacHelper).hasRequiredPermissionForAllEnvironments(any(), any(), any(), any());

    doReturn(mockPage).when(environmentService).list(any(Criteria.class), any(Pageable.class));

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.listEnvironmentsV2(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, false, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testListEnvironmentsV3_SkipsNullEnvironments_WithLimitedPermissions() {
    mockScopeInfoServiceForAnyParentUniqueId();
    Environment validEnv = Environment.builder()
                               .identifier("validEnv")
                               .accountId(ACCOUNT_ID)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJ_IDENTIFIER)
                               .yaml("")
                               .build();

    List<Environment> environmentsWithNull = Arrays.asList(validEnv, null, validEnv);
    Page<Environment> mockPage = new PageImpl<>(environmentsWithNull, PageRequest.of(0, 10), 3);

    Criteria criteria = new Criteria();
    doReturn(criteria)
        .when(environmentFilterHelper)
        .createCriteriaForGetList(any(), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean(), any());

    doReturn(mockPage).when(environmentService).list(any(Criteria.class), any(Pageable.class));
    doReturn(environmentsWithNull).when(environmentRbacHelper).getPermittedEnvironmentsListV2(any(), any());

    doReturn(mockPage).when(environmentService).list(any(Criteria.class), any(PageRequest.class));

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.listEnvironmentsV3(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, false, null, false, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testListEnvironmentsV3_SkipsNullEnvironments_WithFullPermissions() {
    mockScopeInfoServiceForAnyParentUniqueId();
    Environment validEnv = Environment.builder()
                               .identifier("validEnv")
                               .accountId(ACCOUNT_ID)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJ_IDENTIFIER)
                               .yaml("")
                               .build();

    List<Environment> environmentsWithNull = Arrays.asList(validEnv, null, validEnv);
    Page<Environment> mockPage = new PageImpl<>(environmentsWithNull, PageRequest.of(0, 10), 3);

    Criteria criteria = new Criteria();
    doReturn(criteria)
        .when(environmentFilterHelper)
        .createCriteriaForGetList(any(), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean(), any());

    doReturn(mockPage).when(environmentService).list(any(Criteria.class), any(Pageable.class));
    doReturn(environmentsWithNull).when(environmentRbacHelper).getPermittedEnvironmentsListV2(any(), any());

    doReturn(mockPage).when(environmentService).list(any(Criteria.class), any(PageRequest.class));

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.listEnvironmentsV3(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, false, null, true, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateEnvironmentsBatch_AllValid_AllSucceed() throws IOException {
    // Given: 3 valid environment request DTOs
    List<EnvironmentRequestDTO> environmentRequestDTOs = new ArrayList<>();
    for (int i = 1; i <= 3; i++) {
      environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                     .identifier("env" + i)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJ_IDENTIFIER)
                                     .name("Environment " + i)
                                     .type(PreProduction)
                                     .yaml("environment:\n  identifier: env" + i + "\n  name: Environment " + i
                                         + "\n  type: PreProduction\n  orgIdentifier: " + ORG_IDENTIFIER
                                         + "\n  projectIdentifier: " + PROJ_IDENTIFIER)
                                     .build());
    }

    // Mock RBAC for all environments
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());

    // Mock YAML schema validation
    doNothing().when(entityYamlSchemaHelper).validateSchema(anyString(), anyString());

    // Mock org/project validation
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(any(), any(), any())).thenReturn(true);

    // Mock scopeInfoService
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId("test-unique-id")
                              .build();
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER))).thenReturn(scopeInfo);

    // Mock service layer bulk create - all succeed
    List<EnvironmentResponse> successfulResponses = new ArrayList<>();
    for (int i = 1; i <= 3; i++) {
      successfulResponses.add(
          EnvironmentResponse.builder()
              .environment(EnvironmentResponseDTO.builder().identifier("env" + i).name("Environment " + i).build())
              .build());
    }

    io.harness.ng.core.environment.dto.EnvironmentBatchResponse batchResponse =
        io.harness.ng.core.environment.dto.EnvironmentBatchResponse.builder()
            .successful(successfulResponses)
            .failed(Collections.emptyList())
            .totalSuccessful(3)
            .totalFailed(0)
            .build();

    when(environmentService.bulkCreate(eq(ACCOUNT_ID), anyList())).thenReturn(batchResponse);

    // When: Call batch create endpoint
    ResponseDTO<io.harness.ng.core.environment.dto.EnvironmentBatchResponse> response =
        environmentResourceV2.createEnvironmentsBatch(ACCOUNT_ID, environmentRequestDTOs);

    // Then: All succeed
    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getTotalRequested()).isEqualTo(3);
    assertThat(response.getData().getTotalSuccessful()).isEqualTo(3);
    assertThat(response.getData().getTotalFailed()).isEqualTo(0);
    assertThat(response.getData().getSuccessful()).hasSize(3);
    assertThat(response.getData().getFailed()).isEmpty();

    // Verify RBAC checked for all
    verify(accessControlClient, times(3)).checkForAccessOrThrow(any(), any(), any());

    // Verify YAML validation for all
    verify(entityYamlSchemaHelper, times(3)).validateSchema(eq(ACCOUNT_ID), anyString());

    // Verify service layer called
    verify(environmentService, times(1)).bulkCreate(eq(ACCOUNT_ID), anyList());
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateEnvironmentsBatch_YAMLMismatch_PartialSuccess() throws IOException {
    // Given: 3 environments - first has YAML identifier mismatch
    List<EnvironmentRequestDTO> environmentRequestDTOs = new ArrayList<>();

    // First one has YAML mismatch
    environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                   .identifier("env1")
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .name("Environment 1")
                                   .type(PreProduction)
                                   .yaml("environment:\n  identifier: wrongId\n  name: Environment 1")
                                   .build());

    // Second and third are valid
    for (int i = 2; i <= 3; i++) {
      environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                     .identifier("env" + i)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJ_IDENTIFIER)
                                     .name("Environment " + i)
                                     .type(PreProduction)
                                     .yaml("environment:\n  identifier: env" + i + "\n  name: Environment " + i
                                         + "\n  type: PreProduction\n  orgIdentifier: " + ORG_IDENTIFIER
                                         + "\n  projectIdentifier: " + PROJ_IDENTIFIER)
                                     .build());
    }

    // Mock RBAC
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());

    // Mock YAML validation - passes for all
    doNothing().when(entityYamlSchemaHelper).validateSchema(anyString(), anyString());

    // Mock org/project validation
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(any(), any(), any())).thenReturn(true);

    // Mock scopeInfoService
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId("test-unique-id")
                              .build();
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER))).thenReturn(scopeInfo);

    // Note: EnvironmentMapper.toEnvironmentEntity will throw InvalidRequestException for first one
    // This is handled by try-catch in the endpoint

    // Mock service layer - receives only 2 valid environments
    List<EnvironmentResponse> successfulResponses = new ArrayList<>();
    for (int i = 2; i <= 3; i++) {
      successfulResponses.add(
          EnvironmentResponse.builder()
              .environment(EnvironmentResponseDTO.builder().identifier("env" + i).name("Environment " + i).build())
              .build());
    }

    io.harness.ng.core.environment.dto.EnvironmentBatchResponse batchResponse =
        io.harness.ng.core.environment.dto.EnvironmentBatchResponse.builder()
            .successful(successfulResponses)
            .failed(Collections.emptyList())
            .totalSuccessful(2)
            .totalFailed(0)
            .build();

    when(environmentService.bulkCreate(eq(ACCOUNT_ID), anyList())).thenReturn(batchResponse);

    // When: Call batch create endpoint
    ResponseDTO<io.harness.ng.core.environment.dto.EnvironmentBatchResponse> response =
        environmentResourceV2.createEnvironmentsBatch(ACCOUNT_ID, environmentRequestDTOs);

    // Then: Partial success - 2 succeed, 1 fails at conversion
    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getTotalRequested()).isEqualTo(3);
    assertThat(response.getData().getTotalSuccessful()).isEqualTo(2);
    assertThat(response.getData().getTotalFailed()).isEqualTo(1);
    assertThat(response.getData().getSuccessful()).hasSize(2);
    assertThat(response.getData().getFailed()).hasSize(1);

    // Verify failure is due to YAML mismatch
    assertThat(response.getData().getFailed().get(0).getErrorMessage()).contains("env1");
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateEnvironmentsBatch_EmptyList_ReturnsBadRequest() {
    // Given: Empty list
    List<EnvironmentRequestDTO> environmentRequestDTOs = Collections.emptyList();

    // When/Then: Should throw InvalidRequestException
    assertThatThrownBy(() -> environmentResourceV2.createEnvironmentsBatch(ACCOUNT_ID, environmentRequestDTOs))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Environment request list cannot be empty");
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateEnvironmentsBatch_RBACFailure_ThrowsException() throws IOException {
    // Given: Valid environment request DTO
    List<EnvironmentRequestDTO> environmentRequestDTOs = new ArrayList<>();
    environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                   .identifier("env1")
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .name("Environment 1")
                                   .type(PreProduction)
                                   .yaml("environment:\n  identifier: env1\n  name: Environment 1")
                                   .build());

    // Mock RBAC - throws exception
    doThrow(new InvalidRequestException("User does not have permission"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());

    // When: Call batch API with RBAC failure
    ResponseDTO<EnvironmentBatchResponse> response =
        environmentResourceV2.createEnvironmentsBatch(ACCOUNT_ID, environmentRequestDTOs);

    // Then: Verify RBAC failure is collected in failed list (partial success)
    EnvironmentBatchResponse batchResponse = response.getData();
    assertThat(batchResponse.getFailed()).hasSize(1);
    assertThat(batchResponse.getFailed().get(0).getErrorMessage()).contains("User does not have permission");
    assertThat(batchResponse.getFailed().get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(batchResponse.getFailed().get(0).getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(batchResponse.getFailed().get(0).getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(batchResponse.getFailed().get(0).getIdentifier()).isEqualTo("env1");
    assertThat(batchResponse.getTotalFailed()).isEqualTo(1);
    assertThat(batchResponse.getTotalSuccessful()).isEqualTo(0);
    assertThat(batchResponse.getSuccessful()).isEmpty();

    // Verify service layer was never called (conversion failed)
    verify(environmentService, times(0)).bulkCreate(anyString(), anyList());
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateEnvironmentsBatch_ServiceLayerFailures_MergedWithConversionFailures() throws IOException {
    // Given: 3 environments - all pass conversion, but service layer has failures
    List<EnvironmentRequestDTO> environmentRequestDTOs = new ArrayList<>();
    for (int i = 1; i <= 3; i++) {
      environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                     .identifier("env" + i)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJ_IDENTIFIER)
                                     .name("Environment " + i)
                                     .type(PreProduction)
                                     .yaml("environment:\n  identifier: env" + i + "\n  name: Environment " + i
                                         + "\n  type: PreProduction\n  orgIdentifier: " + ORG_IDENTIFIER
                                         + "\n  projectIdentifier: " + PROJ_IDENTIFIER)
                                     .build());
    }

    // Mock RBAC
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any());

    // Mock YAML validation
    doNothing().when(entityYamlSchemaHelper).validateSchema(anyString(), anyString());

    // Mock org/project validation
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(any(), any(), any())).thenReturn(true);

    // Mock scopeInfoService
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId("test-unique-id")
                              .build();
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER))).thenReturn(scopeInfo);

    // Mock service layer - 1 succeeds, 2 fail (duplicates)
    List<EnvironmentResponse> successfulResponses = new ArrayList<>();
    successfulResponses.add(
        EnvironmentResponse.builder()
            .environment(EnvironmentResponseDTO.builder().identifier("env1").name("Environment 1").build())
            .build());

    List<EnvironmentFailureDTO> serviceFailed = new ArrayList<>();
    serviceFailed.add(EnvironmentFailureDTO.builder()
                          .accountId(ACCOUNT_ID)
                          .orgIdentifier(ORG_IDENTIFIER)
                          .projectIdentifier(PROJ_IDENTIFIER)
                          .identifier("env2")
                          .status(io.harness.ng.core.Status.FAILURE)
                          .errorMessage("Environment [env2] validation failed: Duplicate")
                          .build());
    serviceFailed.add(EnvironmentFailureDTO.builder()
                          .accountId(ACCOUNT_ID)
                          .orgIdentifier(ORG_IDENTIFIER)
                          .projectIdentifier(PROJ_IDENTIFIER)
                          .identifier("env3")
                          .status(io.harness.ng.core.Status.FAILURE)
                          .errorMessage("Environment [env3] validation failed: Duplicate")
                          .build());

    io.harness.ng.core.environment.dto.EnvironmentBatchResponse batchResponse =
        io.harness.ng.core.environment.dto.EnvironmentBatchResponse.builder()
            .successful(successfulResponses)
            .failed(serviceFailed)
            .totalSuccessful(1)
            .totalFailed(2)
            .build();

    when(environmentService.bulkCreate(eq(ACCOUNT_ID), anyList())).thenReturn(batchResponse);

    // When: Call batch create endpoint
    ResponseDTO<io.harness.ng.core.environment.dto.EnvironmentBatchResponse> response =
        environmentResourceV2.createEnvironmentsBatch(ACCOUNT_ID, environmentRequestDTOs);

    // Then: Should merge service layer failures with scope information
    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getTotalRequested()).isEqualTo(3);
    assertThat(response.getData().getTotalSuccessful()).isEqualTo(1);
    assertThat(response.getData().getTotalFailed()).isEqualTo(2);
    assertThat(response.getData().getFailed()).hasSize(2);

    // Verify first failure
    assertThat(response.getData().getFailed().get(0).getErrorMessage()).contains("env2");
    assertThat(response.getData().getFailed().get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(response.getData().getFailed().get(0).getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(response.getData().getFailed().get(0).getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(response.getData().getFailed().get(0).getIdentifier()).isEqualTo("env2");

    // Verify second failure
    assertThat(response.getData().getFailed().get(1).getErrorMessage()).contains("env3");
    assertThat(response.getData().getFailed().get(1).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(response.getData().getFailed().get(1).getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(response.getData().getFailed().get(1).getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(response.getData().getFailed().get(1).getIdentifier()).isEqualTo("env3");
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateEnvironmentsBatch_ManualValidationFailures_Comprehensive() throws IOException {
    List<EnvironmentRequestDTO> environmentRequestDTOs = new ArrayList<>();

    // 1. Invalid Identifier (special chars)
    environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                   .identifier("invalid identifier!")
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .name(NAME)
                                   .type(PreProduction)
                                   .yaml("yaml")
                                   .build());

    // 2. Empty Identifier
    environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                   .identifier("")
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .name(NAME)
                                   .type(PreProduction)
                                   .yaml("yaml")
                                   .build());

    // 3. Invalid Name (special chars)
    environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                   .identifier("validId")
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .name("Invalid@Name")
                                   .type(PreProduction)
                                   .yaml("yaml")
                                   .build());

    // 4. Null Type
    environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                   .identifier("validId2")
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .name(NAME)
                                   .type(null) // Null type
                                   .yaml("yaml")
                                   .build());

    // 5. Reserved Keyword
    environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                   .identifier("true") // "true" is a reserved keyword
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .name(NAME)
                                   .type(PreProduction)
                                   .yaml("yaml")
                                   .build());

    // When: Call batch API
    ResponseDTO<EnvironmentBatchResponse> response =
        environmentResourceV2.createEnvironmentsBatch(ACCOUNT_ID, environmentRequestDTOs);

    // Then: Verify all fail
    EnvironmentBatchResponse batchResponse = response.getData();
    assertThat(batchResponse.getTotalRequested()).isEqualTo(5);
    assertThat(batchResponse.getTotalFailed()).isEqualTo(5);
    assertThat(batchResponse.getTotalSuccessful()).isEqualTo(0);

    // Check specific error messages
    assertThat(batchResponse.getFailed().stream().anyMatch(
                   f -> f.getIdentifier().equals("invalid identifier!") && f.getErrorMessage().contains("identifier")))
        .isTrue();
    assertThat(batchResponse.getFailed().stream().anyMatch(
                   f -> f.getIdentifier().equals("") && f.getErrorMessage().contains("identifier")))
        .isTrue();
    assertThat(batchResponse.getFailed().stream().anyMatch(
                   f -> f.getIdentifier().equals("validId") && f.getErrorMessage().contains("name")))
        .isTrue();
    assertThat(batchResponse.getFailed().stream().anyMatch(
                   f -> f.getIdentifier().equals("validId2") && f.getErrorMessage().contains("type")))
        .isTrue();
    assertThat(batchResponse.getFailed().stream().anyMatch(
                   f -> f.getIdentifier().equals("true") && f.getErrorMessage().contains("keyword")))
        .isTrue();

    // Verify service layer not called
    verify(environmentService, times(0)).bulkCreate(anyString(), anyList());
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateEnvironmentsBatch_PartialSuccess_MixedValidationAndRBAC() throws IOException {
    List<EnvironmentRequestDTO> environmentRequestDTOs = new ArrayList<>();

    // 1. Fails Validation
    environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                   .identifier("invalid!")
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .name("Env 1")
                                   .type(PreProduction)
                                   .yaml("yaml")
                                   .build());

    // 2. Fails RBAC (valid DTO)
    environmentRequestDTOs.add(EnvironmentRequestDTO.builder()
                                   .identifier("rbacFail")
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .name("Env 2")
                                   .type(PreProduction)
                                   .yaml("yaml")
                                   .build());

    // 3. Success
    environmentRequestDTOs.add(
        EnvironmentRequestDTO.builder()
            .identifier("success")
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .name("Env 3")
            .type(PreProduction)
            .yaml("environment:\n  identifier: success\n  name: Env 3\n  type: PreProduction\n  orgIdentifier: "
                + ORG_IDENTIFIER + "\n  projectIdentifier: " + PROJ_IDENTIFIER)
            .build());

    // Mock RBAC
    // Throw for "rbacFail" (first RBAC call), pass for "success" (second RBAC call)
    // Note: "invalid!" fails validation before RBAC, so it doesn't trigger a call.
    doAnswer(invocation -> { throw new InvalidRequestException("User does not have permission"); })
        .doAnswer(invocation -> {
          return null; // Success
        })
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());

    // Mock other services
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(any(), any(), any())).thenReturn(true);
    doNothing().when(entityYamlSchemaHelper).validateSchema(anyString(), anyString());

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId("uniqueId")
                              .build();
    when(scopeInfoService.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);

    // Mock service layer - expects only the "success" entity
    EnvironmentBatchResponse batchResponse =
        EnvironmentBatchResponse.builder()
            .successful(Collections.singletonList(
                EnvironmentResponse.builder()
                    .environment(EnvironmentResponseDTO.builder().identifier("success").build())
                    .build()))
            .failed(Collections.emptyList())
            .totalSuccessful(1)
            .totalFailed(0)
            .build();

    when(environmentService.bulkCreate(eq(ACCOUNT_ID), anyList())).thenReturn(batchResponse);

    // When
    ResponseDTO<EnvironmentBatchResponse> response =
        environmentResourceV2.createEnvironmentsBatch(ACCOUNT_ID, environmentRequestDTOs);

    // Then
    EnvironmentBatchResponse result = response.getData();
    assertThat(result.getTotalRequested()).isEqualTo(3);
    assertThat(result.getTotalSuccessful()).isEqualTo(1);
    assertThat(result.getTotalFailed()).isEqualTo(2);

    // Verify successful
    assertThat(result.getSuccessful().get(0).getEnvironment().getIdentifier()).isEqualTo("success");

    // Verify failures
    // 1. Validation failure
    assertThat(result.getFailed().stream().anyMatch(
                   f -> f.getIdentifier().equals("invalid!") && f.getErrorMessage().contains("identifier")))
        .isTrue();
    // 2. RBAC failure
    assertThat(
        result.getFailed().stream().anyMatch(
            f -> f.getIdentifier().equals("rbacFail") && f.getErrorMessage().contains("User does not have permission")))
        .isTrue();

    // Verify service layer received only 1 item
    ArgumentCaptor<List<Environment>> captor = ArgumentCaptor.forClass(List.class);
    // When using doAnswer, parameters are same, verify sees it called twice (once failed, once success)
    // We capture all values.
    verify(environmentService, times(1)).bulkCreate(eq(ACCOUNT_ID), captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getIdentifier()).isEqualTo("success");
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testListEnvironmentPost_HandlesLargeIdentifierList() {
    List<String> largeEnvIdentifierList = new ArrayList<>();
    for (int i = 0; i < 250; i++) {
      largeEnvIdentifierList.add("env_id_" + i);
    }

    Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    Page<Environment> environmentList = new PageImpl<>(Collections.singletonList(entity), pageable, 1);

    when(environmentFilterHelper.createCriteriaForGetList(
             anyString(), anyString(), anyString(), anyList(), anyBoolean(), any()))
        .thenReturn(new Criteria());
    when(environmentService.list(any(Criteria.class), any(Pageable.class))).thenReturn(environmentList);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.getEnvironmentsFilteredByRefsPost(
        0, 100, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, largeEnvIdentifierList, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getContent()).hasSize(1);
    assertThat(response.getData().getContent().get(0).getEnvironment().getIdentifier()).isEqualTo("id");

    verify(environmentService, times(1)).list(any(Criteria.class), any(Pageable.class));
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testListEnvironmentPost_WithEmptyIdentifierList() {
    Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    Page<Environment> environmentList = new PageImpl<>(Collections.singletonList(entity), pageable, 1);

    when(environmentFilterHelper.createCriteriaForGetList(
             anyString(), anyString(), anyString(), anyList(), anyBoolean(), any()))
        .thenReturn(new Criteria());
    when(environmentService.list(any(Criteria.class), any(Pageable.class))).thenReturn(environmentList);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.getEnvironmentsFilteredByRefsPost(
        0, 100, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, Collections.emptyList(), null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testListEnvironmentPost_WithNullIdentifierList() {
    Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    Page<Environment> environmentList = new PageImpl<>(Collections.singletonList(entity), pageable, 1);

    when(environmentFilterHelper.createCriteriaForGetList(
             anyString(), anyString(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(new Criteria());
    when(environmentService.list(any(Criteria.class), any(Pageable.class))).thenReturn(environmentList);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.getEnvironmentsFilteredByRefsPost(
        0, 100, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testListEnvironmentPost_ReturnsEmptyList() {
    List<String> envIdentifiers = List.of("env1", "env2");
    Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    Page<Environment> emptyEnvironmentList = new PageImpl<>(Collections.emptyList(), pageable, 0);

    when(environmentFilterHelper.createCriteriaForGetList(
             anyString(), anyString(), anyString(), anyList(), anyBoolean(), any()))
        .thenReturn(new Criteria());
    when(environmentService.list(any(Criteria.class), any(Pageable.class))).thenReturn(emptyEnvironmentList);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.getEnvironmentsFilteredByRefsPost(
        0, 100, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, envIdentifiers, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getContent()).isEmpty();
    assertThat(response.getData().getTotalItems()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testListEnvironmentPost_WithPagination() {
    List<String> envIdentifiers = List.of("env1", "env2", "env3");
    Environment environment2 = Environment.builder()
                                   .accountId(ACCOUNT_ID)
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .projectIdentifier(PROJ_IDENTIFIER)
                                   .identifier("env2")
                                   .type(PreProduction)
                                   .build();

    Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    Page<Environment> environmentList = new PageImpl<>(List.of(entity, environment2), pageable, 2);

    when(environmentFilterHelper.createCriteriaForGetList(
             anyString(), anyString(), anyString(), anyList(), anyBoolean(), any()))
        .thenReturn(new Criteria());
    when(environmentService.list(any(Criteria.class), any(Pageable.class))).thenReturn(environmentList);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.getEnvironmentsFilteredByRefsPost(
        1, 2, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, envIdentifiers, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getContent()).hasSize(2);
    assertThat(response.getData().getPageIndex()).isEqualTo(1);
    assertThat(response.getData().getPageSize()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testListEnvironment_WithRbacImprovementFF_UsesPerEntityFiltering() {
    doReturn(true).when(featureFlagHelperService).isEnabled(ACCOUNT_ID, FeatureName.CDS_ENV_LISTING_RBAC_IMPROVEMENT);

    Criteria criteria = new Criteria();
    doReturn(criteria)
        .when(environmentFilterHelper)
        .createCriteriaForGetList(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(false), any(), any());

    Environment env = Environment.builder()
                          .identifier("env1")
                          .accountId(ACCOUNT_ID)
                          .orgIdentifier(ORG_IDENTIFIER)
                          .projectIdentifier(PROJ_IDENTIFIER)
                          .type(PreProduction)
                          .yaml("")
                          .build();
    Page<Environment> mockPage = new PageImpl<>(List.of(env), PageRequest.of(0, 10), 1);
    doReturn(mockPage).when(environmentService).list(any(Criteria.class), any(Pageable.class));

    doReturn(false)
        .when(environmentRbacHelper)
        .hasRequiredPermissionForAllEnvironments(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), any());
    doReturn(List.of(env)).when(environmentRbacHelper).getPermittedEnvironmentsList(anyList());

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.listEnvironment(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getContent()).hasSize(1);
    assertThat(response.getData().getContent().get(0).getEnvironment().getIdentifier()).isEqualTo("env1");

    verify(environmentRbacHelper, times(1)).getPermittedEnvironmentsList(anyList());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testListEnvironment_WithRbacImprovementFF_ReturnsEmptyWhenNoPermissions() {
    doReturn(true).when(featureFlagHelperService).isEnabled(ACCOUNT_ID, FeatureName.CDS_ENV_LISTING_RBAC_IMPROVEMENT);

    Criteria criteria = new Criteria();
    doReturn(criteria)
        .when(environmentFilterHelper)
        .createCriteriaForGetList(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(false), any(), any());

    Environment env = Environment.builder()
                          .identifier("env1")
                          .accountId(ACCOUNT_ID)
                          .orgIdentifier(ORG_IDENTIFIER)
                          .projectIdentifier(PROJ_IDENTIFIER)
                          .type(PreProduction)
                          .yaml("")
                          .build();
    Page<Environment> mockPage = new PageImpl<>(List.of(env), PageRequest.of(0, 10), 1);
    doReturn(mockPage).when(environmentService).list(any(Criteria.class), any(Pageable.class));

    doReturn(false)
        .when(environmentRbacHelper)
        .hasRequiredPermissionForAllEnvironments(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), any());
    doReturn(Collections.emptyList()).when(environmentRbacHelper).getPermittedEnvironmentsList(anyList());

    ResponseDTO<PageResponse<EnvironmentResponse>> response = environmentResourceV2.listEnvironment(
        0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getContent()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteEnvironmentsMetadata_emptyResponseWhenServiceReturnsNoRepos() {
    when(environmentService.getRemoteRepoListForAGivenScope(
             eq(ACCOUNT_ID), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(EnvironmentRemoteRepoListResponse.builder().repositories(null).build());

    ResponseDTO<RemoteEnvironmentsResponseDTO> response =
        environmentResourceV2.getRemoteEnvironmentsMetadata(ACCOUNT_ID, null, null, null, 0, 20, null);

    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getTotalEnvironments()).isZero();
    assertThat(response.getData().getRepositories()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteEnvironmentsMetadata_mapsServiceLayerInfoAndSumsCount() {
    EnvironmentRemoteRepoInfo repoA = EnvironmentRemoteRepoInfo.builder()
                                          .repoName("repoA")
                                          .repoURL("urlA")
                                          .count(3L)
                                          .filePathsByOwningScope(Collections.emptyMap())
                                          .connectorRefs(Collections.emptySet())
                                          .build();
    EnvironmentRemoteRepoInfo repoB = EnvironmentRemoteRepoInfo.builder()
                                          .repoName("repoB")
                                          .repoURL("urlB")
                                          .count(2L)
                                          .filePathsByOwningScope(Collections.emptyMap())
                                          .connectorRefs(Collections.emptySet())
                                          .build();
    when(environmentService.getRemoteRepoListForAGivenScope(any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(EnvironmentRemoteRepoListResponse.builder().repositories(of(repoA, repoB)).build());

    ResponseDTO<RemoteEnvironmentsResponseDTO> response = environmentResourceV2.getRemoteEnvironmentsMetadata(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "repoA", 0, 20, null);

    assertThat(response.getData().getTotalEnvironments()).isEqualTo(5L);
    assertThat(response.getData().getRepositories()).hasSize(2);
    assertThat(response.getData().getRepositories().get(0).getRepoName()).isEqualTo("repoA");
    assertThat(response.getData().getRepositories().get(0).getCount()).isEqualTo(3L);
    verify(environmentService, times(1))
        .getRemoteRepoListForAGivenScope(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "repoA", null, 0, 20);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteEnvironmentsMetadata_propagatesServiceLayerException() {
    when(environmentService.getRemoteRepoListForAGivenScope(any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenThrow(new InvalidRequestException("boom"));

    assertThatThrownBy(
        () -> environmentResourceV2.getRemoteEnvironmentsMetadata(ACCOUNT_ID, null, null, null, 0, 20, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("boom");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetActiveServiceInstancesForEnvironmentChecksEnvViewAccess() {
    environmentResourceV2.getActiveServiceInstancesForEnvironment(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER, SVC_IDENTIFIER, null);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(NGResourceType.ENVIRONMENT, ENV_IDENTIFIER), ENVIRONMENT_VIEW_PERMISSION);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetActiveServiceInstancesForEnvironmentThrowsWhenNoAccess() {
    doThrow(new InvalidRequestException("User does not have permission"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());
    assertThatThrownBy(()
                           -> environmentResourceV2.getActiveServiceInstancesForEnvironment(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, ENV_IDENTIFIER, SVC_IDENTIFIER, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testMergeEnvironmentInputsIsGuardedByNGAccessControlCheck() {
    Method method = null;
    for (Method m : EnvironmentResourceV2.class.getDeclaredMethods()) {
      if (m.getName().equals("mergeEnvironmentInputs")) {
        method = m;
        break;
      }
    }
    assertThat(method).isNotNull();
    NGAccessControlCheck annotation = method.getAnnotation(NGAccessControlCheck.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.resourceType()).isEqualTo(NGResourceType.ENVIRONMENT);
    assertThat(annotation.permission()).isEqualTo("core_environment_view");
  }
}
