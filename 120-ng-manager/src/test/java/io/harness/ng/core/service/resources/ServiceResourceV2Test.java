/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
import static io.harness.rule.OwnerRule.ABHISHEK_ARYAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.HIMANSHU;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.PARTH_SHARMA;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.THRISHANK;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.vivekveman;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.cdng.hooks.ServiceHookAction;
import io.harness.cdng.manifest.yaml.K8sCommandFlagType;
import io.harness.cdng.manifest.yaml.KustomizeCommandFlagType;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.common.EntityYamlRootNames;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.beans.StoreType;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.beans.EntityWithGitInfo;
import io.harness.ng.core.beans.ServicesYamlMetadataApiInput;
import io.harness.ng.core.beans.ServicesYamlMetadataApiInputV2;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.opa.OpaOnSaveEvaluationStatus;
import io.harness.ng.core.opa.OpaOnSaveStatusResponseDTO;
import io.harness.ng.core.opa.gitx.ServiceOpaStatusHandler;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.service.ServiceGitUpdateResponseDTO;
import io.harness.ng.core.service.dto.DestinationServiceConfig;
import io.harness.ng.core.service.dto.ForceImportServiceRequestDTO;
import io.harness.ng.core.service.dto.ForceImportServiceResponse;
import io.harness.ng.core.service.dto.RemoteServicesResponseDTO;
import io.harness.ng.core.service.dto.ServiceBatchResponseDTO;
import io.harness.ng.core.service.dto.ServiceCloneRequestDTO;
import io.harness.ng.core.service.dto.ServiceFailureResponse;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.dto.SourceServiceConfig;
import io.harness.ng.core.service.entity.ForceImportServiceYamlOperationDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceGovernanceDataResponse;
import io.harness.ng.core.service.entity.ServiceRemoteRepoInfo;
import io.harness.ng.core.service.entity.ServiceRemoteRepoListResponse;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityYamlSchemaHelper;
import io.harness.ng.core.service.services.impl.ServiceRbacHelper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ng.opa.gitx.CdOpaOnSaveStatusApiHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.repositories.UpsertOptions;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.beans.GitYamlValidationRequestParams;
import io.harness.yaml.validator.beans.YamlValidationRequestBody;
import io.harness.yaml.validator.beans.YamlValidationRequestDTO;

import software.wings.beans.ServiceKeys;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDC)
public class ServiceResourceV2Test extends CategoryTest {
  @Mock ServiceEntityService serviceEntityService;
  @InjectMocks ServiceResourceV2 serviceResourceV2;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock ServiceEntityYamlSchemaHelper serviceSchemaHelper;
  @Mock ServiceRbacHelper serviceRbacHelper;
  @Mock ServiceCloneHelper serviceCloneHelper;
  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock ScopeAccessHelper scopeAccessHelper;
  @Mock ServiceHelper serviceHelper;
  @Mock ServiceOpaStatusHandler serviceOpaStatusHandler;
  @Mock CdOpaOnSaveStatusApiHelper cdOpaOnSaveStatusApiHelper;
  @Mock ScopeInfoService scopeInfoService;

  private static final String UNIQUE_ID = "uniqueId";

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "identifier";
  private final String OTHER_IDENTIFIER = "otherIdentifier";
  private final String NAME = "name";
  ServiceEntity entity;
  ScopeInfo scopeInfo;
  ServiceGovernanceDataResponse serviceGovernanceDataResponse;
  ServiceRequestDTO serviceRequestDTO;
  ServiceResponseDTO serviceResponseDTO;

  private AutoCloseable mocks;
  @Before
  public void setup() {
    mocks = MockitoAnnotations.openMocks(this);
    entity = ServiceEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(IDENTIFIER)
                 .parentUniqueId(UNIQUE_ID)
                 .version(1L)
                 .type(ServiceDefinitionType.GOOGLE_CLOUD_RUN)
                 .description("")
                 .build();

    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .build();
    serviceResponseDTO = ServiceResponseDTO.builder()
                             .accountId(ACCOUNT_ID)
                             .identifier(IDENTIFIER)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJ_IDENTIFIER)
                             .version(1L)
                             .description("")
                             .tags(new HashMap<>())
                             .build();
    serviceGovernanceDataResponse = ServiceGovernanceDataResponse.builder().service(entity).build();

    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_IDENTIFIER)
                    .projectIdentifier(PROJ_IDENTIFIER)
                    .uniqueId(UNIQUE_ID)
                    .build();

    // After CDS_MOVE_PROJECT_ACROSS_ORGS_SERVICE_ENTITY FF cleanup, ServiceResourceV2 unconditionally resolves scope
    // through ScopeInfoService. Stub the three overloads it consumes so the flows have a valid scope to work with.
    lenient()
        .when(scopeInfoService.getScopeInfo(anyString(), any(), any()))
        .thenAnswer(invocation
            -> ScopeInfo.builder()
                   .accountIdentifier(invocation.getArgument(0))
                   .orgIdentifier(invocation.getArgument(1))
                   .projectIdentifier(invocation.getArgument(2))
                   .uniqueId(UNIQUE_ID)
                   .build());
    lenient().when(scopeInfoService.getScopeInfo(anyString(), any(Set.class))).thenAnswer(invocation -> {
      Set<String> parentUniqueIds = invocation.getArgument(1);
      Map<String, Optional<ScopeInfo>> scopeInfos = new HashMap<>();
      ScopeInfo scopeInfo = ScopeInfo.builder()
                                .accountIdentifier(invocation.getArgument(0))
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .uniqueId(UNIQUE_ID)
                                .build();
      for (String parentUniqueId : parentUniqueIds) {
        scopeInfos.put(parentUniqueId, Optional.of(scopeInfo));
      }
      return scopeInfos;
    });
    lenient()
        .when(scopeInfoService.getUniqueIdsIncludingParentScopes(any()))
        .thenReturn(Map.of(ScopeLevel.ACCOUNT, UNIQUE_ID));
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreateService() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.create(any(), any())).thenReturn(serviceGovernanceDataResponse);
    serviceResourceV2.create(ACCOUNT_ID, serviceRequestDTO, null);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testValidateServiceYamlMapsCommitIdIntoDto() {
    GitYamlValidationRequestParams gitParams = GitYamlValidationRequestParams.builder()
                                                   .repoName("repo")
                                                   .filePath(".harness/service.yaml")
                                                   .branch("main")
                                                   .isDefaultBranch(false)
                                                   .commitId("commit-123")
                                                   .build();
    YamlValidationRequestBody requestBody =
        YamlValidationRequestBody.builder().yaml("service: {}").gitYamlValidationRequestParams(gitParams).build();
    when(serviceEntityService.validateServiceYaml(eq(ACCOUNT_ID), any())).thenReturn(Collections.emptyList());

    serviceResourceV2.validateServiceYaml(ACCOUNT_ID, requestBody);

    // Webhook MODIFIED plumbing: the commitId from the request must be mapped into the validation DTO.
    ArgumentCaptor<YamlValidationRequestDTO> captor = ArgumentCaptor.forClass(YamlValidationRequestDTO.class);
    verify(serviceEntityService, times(1)).validateServiceYaml(eq(ACCOUNT_ID), captor.capture());
    assertThat(captor.getValue().getCommitId()).isEqualTo("commit-123");
    assertThat(captor.getValue().getBranch()).isEqualTo("main");
    assertThat(captor.getValue().getFilePath()).isEqualTo(".harness/service.yaml");
    assertThat(captor.getValue().getRepoName()).isEqualTo("repo");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreateServiceWithSchemaValidation() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    String yaml = "service:\n"
        + "  name: das\n"
        + "  identifier: das\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes";
    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .yaml(yaml)
                            .build();
    assertThatThrownBy(() -> serviceResourceV2.create(ACCOUNT_ID, serviceRequestDTO, null))
        .isInstanceOf(InvalidRequestException.class);
    verify(serviceSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, serviceRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreateServices() {
    List<ServiceRequestDTO> serviceRequestDTOList = new ArrayList<>();
    List<ServiceEntity> serviceEntityList = new ArrayList<>();
    List<ServiceEntity> outputServiceEntitiesList = new ArrayList<>();
    outputServiceEntitiesList.add(entity);

    serviceEntityList.add(entity);
    serviceRequestDTOList.add(serviceRequestDTO);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.bulkCreate(eq(ACCOUNT_ID), any())).thenReturn(new PageImpl<>(outputServiceEntitiesList));
    serviceResourceV2.createServices(ACCOUNT_ID, serviceRequestDTOList);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateServicesPartialBatch_Success() {
    List<ServiceRequestDTO> requestDTOs = Collections.singletonList(serviceRequestDTO);
    List<ServiceResponse> successfulServices =
        Collections.singletonList(ServiceResponse.builder().service(serviceResponseDTO).build());
    ServiceBatchResponseDTO batchResponse = ServiceBatchResponseDTO.builder()
                                                .successfulServices(successfulServices)
                                                .failedServices(Collections.emptyList())
                                                .totalSuccess(1)
                                                .totalFailed(0)
                                                .build();

    when(featureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any());
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(any(), any(), any())).thenReturn(true);
    when(serviceEntityService.bulkCreatePartial(eq(ACCOUNT_ID), anyList())).thenReturn(batchResponse);

    ResponseDTO<ServiceBatchResponseDTO> response =
        serviceResourceV2.createServicesPartialBatch(ACCOUNT_ID, requestDTOs);

    assertThat(response.getData().getTotalSuccess()).isEqualTo(1);
    assertThat(response.getData().getTotalFailed()).isEqualTo(0);
    verify(serviceEntityService).bulkCreatePartial(eq(ACCOUNT_ID), anyList());
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateServicesPartialBatch_ManualValidationFailure() {
    ServiceRequestDTO invalidDto = ServiceRequestDTO.builder()
                                       .identifier("invalid identifier!") // Invalid char space and !
                                       .orgIdentifier(ORG_IDENTIFIER)
                                       .projectIdentifier(PROJ_IDENTIFIER)
                                       .name(NAME)
                                       .build();

    List<ServiceRequestDTO> requestDTOs = new ArrayList<>();
    requestDTOs.add(serviceRequestDTO); // Valid
    requestDTOs.add(invalidDto); // Invalid

    // Service layer only returns success for the valid one
    List<ServiceResponse> successfulServices =
        Collections.singletonList(ServiceResponse.builder().service(serviceResponseDTO).build());
    ServiceBatchResponseDTO batchResponse =
        ServiceBatchResponseDTO.builder()
            .successfulServices(successfulServices)
            .failedServices(Collections.emptyList()) // Service layer sees no failures because it only gets valid ones
            .totalSuccess(1)
            .totalFailed(0)
            .build();

    when(featureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any());
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(any(), any(), any())).thenReturn(true);

    // We expect bulkCreatePartial to be called with a list of size 1 (only the valid one)
    when(serviceEntityService.bulkCreatePartial(eq(ACCOUNT_ID), anyList())).thenReturn(batchResponse);

    ResponseDTO<ServiceBatchResponseDTO> response =
        serviceResourceV2.createServicesPartialBatch(ACCOUNT_ID, requestDTOs);

    assertThat(response.getData().getTotalRequested()).isEqualTo(2);
    assertThat(response.getData().getTotalSuccess()).isEqualTo(1);
    assertThat(response.getData().getTotalFailed()).isEqualTo(1);
    assertThat(response.getData().getFailedServices().get(0).getIdentifier()).isEqualTo("invalid identifier!");
    assertThat(response.getData().getFailedServices().get(0).getErrorMessage()).contains("identifier");

    // Verify bulkCreatePartial was called with only 1 entity
    ArgumentCaptor<List<ServiceEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(serviceEntityService).bulkCreatePartial(eq(ACCOUNT_ID), captor.capture());
    assertThat(captor.getValue()).hasSize(1);
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateServicesPartialBatch_RBACFailure() {
    List<ServiceRequestDTO> requestDTOs = Collections.singletonList(serviceRequestDTO);

    when(featureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    doThrow(new InvalidRequestException("User does not have permission"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());

    // We expect bulkCreatePartial to be called with an empty list (since the only item failed RBAC)
    ServiceBatchResponseDTO batchResponse = ServiceBatchResponseDTO.builder()
                                                .successfulServices(Collections.emptyList())
                                                .failedServices(Collections.emptyList())
                                                .totalSuccess(0)
                                                .totalFailed(0)
                                                .build();
    when(serviceEntityService.bulkCreatePartial(eq(ACCOUNT_ID), anyList())).thenReturn(batchResponse);

    ResponseDTO<ServiceBatchResponseDTO> response =
        serviceResourceV2.createServicesPartialBatch(ACCOUNT_ID, requestDTOs);

    assertThat(response.getData().getTotalRequested()).isEqualTo(1);
    assertThat(response.getData().getTotalSuccess()).isEqualTo(0);
    assertThat(response.getData().getTotalFailed()).isEqualTo(1);
    assertThat(response.getData().getFailedServices().get(0).getIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(response.getData().getFailedServices().get(0).getErrorMessage())
        .contains("User does not have permission");

    // Verify service layer called with empty list
    verify(serviceEntityService, times(0)).bulkCreatePartial(eq(ACCOUNT_ID), anyList());
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateServicesPartialBatch_AllFailures_MixedReasons() {
    // 1. Invalid Identifier
    ServiceRequestDTO invalidDto = ServiceRequestDTO.builder()
                                       .identifier("invalid identifier!")
                                       .orgIdentifier(ORG_IDENTIFIER)
                                       .projectIdentifier(PROJ_IDENTIFIER)
                                       .name(NAME)
                                       .build();

    // 2. RBAC Failure
    ServiceRequestDTO rbacFailDto = ServiceRequestDTO.builder()
                                        .identifier("rbacFail")
                                        .orgIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJ_IDENTIFIER)
                                        .name(NAME)
                                        .build();

    List<ServiceRequestDTO> requestDTOs = new ArrayList<>();
    requestDTOs.add(invalidDto);
    requestDTOs.add(rbacFailDto);

    when(featureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);

    // Mock RBAC to throw only for the second item
    doThrow(new InvalidRequestException("User does not have permission"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());

    // Expect empty list to service layer
    ServiceBatchResponseDTO batchResponse = ServiceBatchResponseDTO.builder()
                                                .successfulServices(Collections.emptyList())
                                                .failedServices(Collections.emptyList())
                                                .totalSuccess(0)
                                                .totalFailed(0)
                                                .build();
    when(serviceEntityService.bulkCreatePartial(eq(ACCOUNT_ID), anyList())).thenReturn(batchResponse);

    ResponseDTO<ServiceBatchResponseDTO> response =
        serviceResourceV2.createServicesPartialBatch(ACCOUNT_ID, requestDTOs);

    assertThat(response.getData().getTotalRequested()).isEqualTo(2);
    assertThat(response.getData().getTotalSuccess()).isEqualTo(0);
    assertThat(response.getData().getTotalFailed()).isEqualTo(2);

    // Order matters: invalidDto failed first, rbacFailDto failed second
    assertThat(response.getData().getFailedServices()).hasSize(2);

    // Check first failure (Manual Validation)
    ServiceFailureResponse fail1 = response.getData()
                                       .getFailedServices()
                                       .stream()
                                       .filter(f -> f.getIdentifier().equals("invalid identifier!"))
                                       .findFirst()
                                       .orElseThrow();
    assertThat(fail1.getErrorMessage()).contains("identifier");

    // Check second failure (RBAC)
    ServiceFailureResponse fail2 = response.getData()
                                       .getFailedServices()
                                       .stream()
                                       .filter(f -> f.getIdentifier().equals("rbacFail"))
                                       .findFirst()
                                       .orElseThrow();
    assertThat(fail2.getErrorMessage()).contains("User does not have permission");

    verify(serviceEntityService, times(0)).bulkCreatePartial(eq(ACCOUNT_ID), anyList());
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateServicesPartialBatch_AllFailures_ServiceLayer() {
    // Both pass pre-checks, but service layer fails them (e.g. duplicates)
    List<ServiceRequestDTO> requestDTOs = new ArrayList<>();
    requestDTOs.add(serviceRequestDTO);
    requestDTOs.add(ServiceRequestDTO.builder()
                        .identifier("otherId")
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .name("Other")
                        .build());

    when(featureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any());
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(any(), any(), any())).thenReturn(true);

    // Mock service layer to return failures
    List<ServiceFailureResponse> failures = new ArrayList<>();
    failures.add(ServiceFailureResponse.builder().identifier(IDENTIFIER).errorMessage("Duplicate").build());
    failures.add(ServiceFailureResponse.builder().identifier("otherId").errorMessage("Something else").build());

    ServiceBatchResponseDTO batchResponse = ServiceBatchResponseDTO.builder()
                                                .successfulServices(Collections.emptyList())
                                                .failedServices(failures)
                                                .totalSuccess(0)
                                                .totalFailed(2)
                                                .build();

    when(serviceEntityService.bulkCreatePartial(eq(ACCOUNT_ID), anyList())).thenReturn(batchResponse);

    ResponseDTO<ServiceBatchResponseDTO> response =
        serviceResourceV2.createServicesPartialBatch(ACCOUNT_ID, requestDTOs);

    assertThat(response.getData().getTotalRequested()).isEqualTo(2);
    assertThat(response.getData().getTotalSuccess()).isEqualTo(0);
    assertThat(response.getData().getTotalFailed()).isEqualTo(2);
    assertThat(response.getData().getFailedServices()).hasSize(2);
  }

  @Test
  @Owner(developers = HIMANSHU)
  @Category(UnitTests.class)
  public void testCreateServicesPartialBatch_EmptyList() {
    List<ServiceRequestDTO> requestDTOs = Collections.emptyList();

    assertThatThrownBy(() -> serviceResourceV2.createServicesPartialBatch(ACCOUNT_ID, requestDTOs))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreateServicesWithSchemaValidation() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    String yaml = "service:\n"
        + "  name: das\n"
        + "  identifier: das\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes";
    List<ServiceRequestDTO> serviceRequestDTOList = new ArrayList<>();
    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .yaml("")
                            .build();
    ServiceRequestDTO serviceRequestDTO1 = ServiceRequestDTO.builder()
                                               .identifier(IDENTIFIER)
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .name(NAME)
                                               .yaml(yaml)
                                               .build();
    serviceRequestDTOList.add(serviceRequestDTO);
    serviceRequestDTOList.add(serviceRequestDTO1);

    assertThatThrownBy(() -> serviceResourceV2.createServices(ACCOUNT_ID, serviceRequestDTOList))
        .isInstanceOf(InvalidRequestException.class);
    verify(serviceSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, serviceRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testListTemplate() {
    when(serviceEntityService.get(any(), any(), eq(false), eq(false), eq(false))).thenReturn(Optional.of(entity));
    ResponseDTO<ServiceResponse> serviceResponseResponseDTO = serviceResourceV2.get(
        IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false, false, null, "false", false, scopeInfo);
    assertThat(serviceResponseResponseDTO.getEntityTag()).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testListTemplateForNotFoundException() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.empty());
    assertThatThrownBy(()
                           -> serviceResourceV2.get(IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false,
                               false, null, "false", false, null))
        .hasMessage("Service with identifier [identifier] in project [projId], org [orgId] not found");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testUpdateService() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.update(any(), any())).thenReturn(serviceGovernanceDataResponse);
    serviceResourceV2.update("IF_MATCH", ACCOUNT_ID, serviceRequestDTO, null);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateServiceWithSchemaValidation() {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.update(any(), any())).thenReturn(serviceGovernanceDataResponse);
    serviceResourceV2.update("IF_MATCH", ACCOUNT_ID, serviceRequestDTO, null);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    verify(serviceSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, serviceRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testUpsertService() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.upsert(any(), eq(UpsertOptions.DEFAULT), any()))
        .thenReturn(serviceGovernanceDataResponse);
    serviceResourceV2.upsert("IF_MATCH", ACCOUNT_ID, serviceRequestDTO);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpsertServiceWithSchemaValidationFlagOn() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.upsert(any(), eq(UpsertOptions.DEFAULT), any()))
        .thenReturn(serviceGovernanceDataResponse);
    serviceResourceV2.upsert("IF_MATCH", ACCOUNT_ID, serviceRequestDTO);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
    verify(serviceSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, serviceRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testCreateServiceWithEmptyYaml() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    when(serviceEntityService.create(any(), any())).thenReturn(serviceGovernanceDataResponse);

    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .yaml("")
                            .build();

    serviceResourceV2.create(ACCOUNT_ID, serviceRequestDTO, null);
    verify(serviceSchemaHelper, times(2)).validateSchema(any(), any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpsertServiceWithEmptyYaml() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);

    when(serviceEntityService.upsert(any(), eq(UpsertOptions.DEFAULT), any()))
        .thenReturn(serviceGovernanceDataResponse);

    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .yaml("")
                            .build();

    serviceResourceV2.upsert("IF_MATCH", ACCOUNT_ID, serviceRequestDTO);

    verify(serviceSchemaHelper, times(2)).validateSchema(any(), any());
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testCreateServicesWithEmptyYaml() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    List<ServiceRequestDTO> serviceRequestDTOList = new ArrayList<>();
    List<ServiceEntity> outputServiceEntitiesList = new ArrayList<>();
    outputServiceEntitiesList.add(entity);
    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .build();
    ServiceRequestDTO serviceRequestDTO1 = ServiceRequestDTO.builder()
                                               .identifier(IDENTIFIER)
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .name(NAME)
                                               .build();
    serviceRequestDTOList.add(serviceRequestDTO);
    serviceRequestDTOList.add(serviceRequestDTO1);
    when(serviceEntityService.bulkCreate(eq(ACCOUNT_ID), any())).thenReturn(new PageImpl<>(outputServiceEntitiesList));
    serviceResourceV2.createServices(ACCOUNT_ID, serviceRequestDTOList);
    verify(serviceSchemaHelper, times(4)).validateSchema(any(), any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpdateServiceWithEmptyYaml() {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.update(any(), any())).thenReturn(serviceGovernanceDataResponse);
    ServiceRequestDTO serviceRequestDTO1 = ServiceRequestDTO.builder()
                                               .identifier(IDENTIFIER)
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .name(NAME)
                                               .build();
    serviceResourceV2.update("IF_MATCH", ACCOUNT_ID, serviceRequestDTO1, null);
    verify(serviceSchemaHelper, times(2)).validateSchema(any(), any());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testListServicesWithScopeInfoAtProjectScope() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId("projUniqueId")
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    entity.setParentUniqueId("projUniqueId");

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, Set.of("projUniqueId")))
        .thenReturn(Map.of("projUniqueId", Optional.of(scopeInfo)));

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> serviceList = new PageImpl<>(Collections.singletonList(entity), pageable, 1);
    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    when(serviceEntityService.list(criteriaCaptor.capture(), any())).thenReturn(serviceList);

    List<ServiceResponse> content =
        serviceResourceV2.getAllServicesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, 0, 10, null, scopeInfo)
            .getData()
            .getContent();

    assertThat(content).hasSize(1);
    assertThat(criteriaCaptor.getValue().getCriteriaObject().toJson())
        .isEqualTo("{\"accountId\": \"account_id\", \"parentUniqueId\": \"projUniqueId\", \"deleted\": false}");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListServices() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> serviceList = new PageImpl<>(Collections.singletonList(entity), pageable, 1);
    when(serviceEntityService.list(any(), any())).thenReturn(serviceList);
    List<ServiceResponse> content =
        serviceResourceV2.getAllServicesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "services", 0, 10, null, null)
            .getData()
            .getContent();

    assertThat(content).isNotNull();
    assertThat(content.size()).isEqualTo(1);
    assertThat(content.get(0).getService()).isEqualTo(serviceResponseDTO);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetListAccessServices() {
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(
                List.of(AccessControlDTO.builder().permitted(true).resourceIdentifier(IDENTIFIER).build()))
            .build();
    List<ServiceEntity> serviceList = List.of(entity);
    List<ServiceResponse> expectedServiceResponses =
        List.of(ServiceResponse.builder().service(serviceResponseDTO).build());

    when(scopeAccessHelper.getPermissionCheckDtoForViewAccessForScope(any()))
        .thenReturn(PermissionCheckDTO.builder().build());
    when(accessControlClient.checkForAccessOrThrow(anyList(), anyString()))
        .thenReturn(AccessCheckResponseDTO.builder().build());
    when(serviceEntityService.listRunTimePermission(any(), anyList(), eq(false))).thenReturn(serviceList);
    when(infrastructureEntityService.filterServicesByScopedInfrastructures(any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(IDENTIFIER));
    when(accessControlClient.checkForAccess(any())).thenReturn(accessCheckResponseDTO);
    when(serviceHelper.filterByPermissionAndId(any(), any())).thenReturn(expectedServiceResponses);
    ResponseDTO<List<ServiceResponse>> response =
        serviceResourceV2.listAccessServices(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "service",
            new ArrayList<>(), null, null, true, null, "v1", null, false, scopeInfo);

    assertThat(response.getData().size()).isEqualTo(1);
    assertThat(response.getData().get(0).getService().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(response.getData().get(0).getService().getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(response.getData().get(0).getService().getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(response.getData().get(0).getService().getIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(response.getData().get(0).getService().getVersion()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListServicesWithInvalidAccountIdentifier() {
    when(serviceEntityService.list(any(), any()))
        .thenThrow(new InvalidRequestException(format("Invalid account identifier, %s", ACCOUNT_ID)));

    assertThatThrownBy(()
                           -> serviceResourceV2.getAllServicesList(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "services", 0, 10, null, null))
        .hasMessage(format("Invalid account identifier, %s", ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testKustomizeCommandFlags() {
    assertThat(serviceResourceV2.getKustomizeCommandFlags().getData())
        .containsExactlyInAnyOrder(KustomizeCommandFlagType.BUILD);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testK8sCommandFlags() {
    assertThat(serviceResourceV2.getK8sCommandFlags(ServiceSpecType.NATIVE_HELM, null).getData())
        .containsExactlyInAnyOrder(K8sCommandFlagType.Apply, K8sCommandFlagType.Rollout);
    assertThat(
        serviceResourceV2.getK8sCommandFlags(ServiceSpecType.KUBERNETES, EntityYamlRootNames.K8S_PATCH).getData())
        .containsExactlyInAnyOrder(K8sCommandFlagType.Patch);
    assertThat(serviceResourceV2.getK8sCommandFlags(ServiceSpecType.KUBERNETES, null).getData())
        .containsExactlyInAnyOrder(K8sCommandFlagType.Apply, K8sCommandFlagType.Patch, K8sCommandFlagType.Rollout,
            K8sCommandFlagType.Delete, K8sCommandFlagType.Diff);
    assertThat(
        serviceResourceV2.getK8sCommandFlags(ServiceSpecType.KUBERNETES, EntityYamlRootNames.K8S_ROLLOUT).getData())
        .containsExactlyInAnyOrder(K8sCommandFlagType.Rollout);
    assertThat(
        serviceResourceV2.getK8sCommandFlags(ServiceSpecType.NATIVE_HELM, EntityYamlRootNames.K8S_ROLLOUT).getData())
        .containsExactlyInAnyOrder(K8sCommandFlagType.Rollout);
    assertThat(
        serviceResourceV2.getK8sCommandFlags(ServiceSpecType.KUBERNETES, EntityYamlRootNames.K8S_APPLY).getData())
        .containsExactlyInAnyOrder(K8sCommandFlagType.Apply);
    assertThat(serviceResourceV2.getK8sCommandFlags(ServiceSpecType.KUBERNETES, EntityYamlRootNames.K8S_DIFF).getData())
        .containsExactlyInAnyOrder(K8sCommandFlagType.Diff);
    assertThat(
        serviceResourceV2.getK8sCommandFlags(ServiceSpecType.KUBERNETES, EntityYamlRootNames.K8S_DRY_RUN_MANIFEST)
            .getData())
        .containsExactlyInAnyOrder(K8sCommandFlagType.Apply);
    assertThat(
        serviceResourceV2.getK8sCommandFlags(ServiceSpecType.NATIVE_HELM, EntityYamlRootNames.K8S_DRY_RUN_MANIFEST)
            .getData())
        .containsExactlyInAnyOrder(K8sCommandFlagType.Apply);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testK8sCommandFlagsWithIncorretStepType() {
    assertThat(serviceResourceV2.getK8sCommandFlags(ServiceSpecType.ECS, null).getData()).isEmpty();
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetServiceHookActions() {
    assertThat(serviceResourceV2.getServiceHookActions(ServiceSpecType.NATIVE_HELM).getData())
        .containsExactlyInAnyOrder(
            ServiceHookAction.FETCH_FILES, ServiceHookAction.TEMPLATE_MANIFEST, ServiceHookAction.STEADY_STATE_CHECK);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetServiceHookActionsWithIncorrectStepType() {
    assertThatThrownBy(() -> serviceResourceV2.getServiceHookActions(ServiceSpecType.ECS).getData())
        .hasMessage("Service with type: [ECS] does not support service hooks")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataForService() {
    GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo = GitMetadataUpdateRequestInfoDTO.builder()
                                                                       .connectorRef("newConnectorRef")
                                                                       .filePath("newFilePath")
                                                                       .repoName("repoName")
                                                                       .build();
    doReturn(IDENTIFIER).when(serviceEntityService).updateGitMetadata(any(), any(), any());
    doNothing().when(accessControlClient).checkForAccessOrThrow(any(), any(), any(), any());
    ResponseDTO<ServiceGitUpdateResponseDTO> response = serviceResourceV2.updateGitMetadataForService(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, gitMetadataUpdateRequestInfo, null);
    assertEquals(IDENTIFIER, response.getData().getIdentifier());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testListServicesIdentifiersInQuery() {
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());
    doReturn(Page.empty()).when(serviceEntityService).list(any(), any(), anyBoolean());

    ScopeInfo sampleScope = ScopeInfo.builder()
                                .accountIdentifier("sample_account")
                                .orgIdentifier("sample_org")
                                .projectIdentifier("sample_project")
                                .uniqueId("sample_unique_id")
                                .build();
    ResponseDTO<PageResponse<ServiceResponse>> pageResponseResponseDTO = serviceResourceV2.listServices(0, 10,
        "sample_account", "sample_org", "sample_project", null, List.of("id1", "id2"), null, null, null, null, null,
        null, null, null, false, false, null, false, sampleScope);

    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(serviceEntityService).list(criteriaCaptor.capture(), any(), anyBoolean());
    assertThat(pageResponseResponseDTO).isNotNull();
    assertThat(criteriaCaptor.getValue()).isNotNull();
    assertThat(criteriaCaptor.getValue().getCriteriaObject().toJson())
        .isEqualTo("{\"accountId\": \"sample_account\", \"parentUniqueId\": \"sample_unique_id\", "
            + "\"deleted\": false, \"identifier\": {\"$in\": [\"id1\", \"id2\"]}}");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testListServicesIdentifiersInQueryScopedAccess() {
    doReturn(false).when(accessControlClient).hasAccess(any(), any(), any());
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> servicePage = new PageImpl<>(Collections.singletonList(entity), pageable, 1);
    doReturn(servicePage)
        .when(serviceEntityService)
        .getRBACFilteredServices(any(), any(), anyList(), eq(SERVICE_VIEW_PERMISSION), anyBoolean());

    ScopeInfo sampleScope = ScopeInfo.builder()
                                .accountIdentifier("sample_account")
                                .orgIdentifier("sample_org")
                                .projectIdentifier("sample_project")
                                .uniqueId("sample_unique_id")
                                .build();
    List<String> serviceIdentifiers = List.of("id1", "id2", IDENTIFIER);
    ResponseDTO<PageResponse<ServiceResponse>> pageResponseResponseDTO =
        serviceResourceV2.listServices(0, 10, "sample_account", "sample_org", "sample_project", null,
            serviceIdentifiers, null, null, null, null, null, null, null, null, false, false, null, false, sampleScope);
    assertThat(pageResponseResponseDTO).isNotNull();
    assertThat(pageResponseResponseDTO.getData().getContent()).hasSize(1);

    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    ArgumentCaptor<List<String>> identifiersCaptor = ArgumentCaptor.forClass(List.class);
    verify(serviceEntityService)
        .getRBACFilteredServices(
            criteriaCaptor.capture(), any(), identifiersCaptor.capture(), eq(SERVICE_VIEW_PERMISSION), anyBoolean());
    assertThat(identifiersCaptor.getValue()).isEqualTo(serviceIdentifiers);
    assertThat(criteriaCaptor.getValue().getCriteriaObject().toJson())
        .isEqualTo("{\"accountId\": \"sample_account\", \"parentUniqueId\": \"sample_unique_id\", \"deleted\": false}");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetWithSchemaValidation() throws IOException {
    String yaml = "service:\n"
        + "  name: das\n"
        + "  identifier: das\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes";

    ServiceEntity service = ServiceEntity.builder()
                                .identifier(IDENTIFIER)
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .type(ServiceDefinitionType.KUBERNETES)
                                .yaml(yaml)
                                .storeType(StoreType.REMOTE)
                                .type(ServiceDefinitionType.KUBERNETES)
                                .build();

    doReturn(Optional.of(service))
        .when(serviceEntityService)
        .get(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());

    doThrow(new InvalidYamlException("invalid yaml", YamlSchemaErrorWrapperDTO.builder().build(), yaml))
        .when(serviceSchemaHelper)
        .validateSchema(anyString(), anyString());

    ServiceResponse response =
        serviceResourceV2
            .get(IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false, false, null, "false", false, scopeInfo)
            .getData();
    assertThat(response.getEntityValidityDetails()).isNotNull();
    assertThat(response.getEntityValidityDetails().isValid()).isFalse();
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testGetSetsOpaOnSaveStatus() {
    String yaml = "service:\n"
        + "  name: das\n"
        + "  identifier: das\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes";
    ServiceEntity service = ServiceEntity.builder()
                                .identifier(IDENTIFIER)
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .type(ServiceDefinitionType.KUBERNETES)
                                .yaml(yaml)
                                .storeType(StoreType.REMOTE)
                                .build();
    doReturn(Optional.of(service))
        .when(serviceEntityService)
        .get(any(), anyString(), anyBoolean(), anyBoolean(), anyBoolean());
    OpaOnSaveStatusResponseDTO opaStatus =
        OpaOnSaveStatusResponseDTO.builder().status(OpaOnSaveEvaluationStatus.SUCCESS).build();
    when(cdOpaOnSaveStatusApiHelper.resolveGetOpaOnSaveStatus(any(), any(), any(), any()))
        .thenReturn(Optional.of(opaStatus));

    ServiceResponse response =
        serviceResourceV2
            .get(IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false, false, null, "false", false, scopeInfo)
            .getData();

    assertThat(response.getOpaOnSaveStatus()).isEqualTo(opaStatus);
    verify(cdOpaOnSaveStatusApiHelper).resolveGetOpaOnSaveStatus(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCloneService() {
    when(serviceCloneHelper.cloneService(any(), any(), any(), any())).thenReturn(serviceGovernanceDataResponse);

    ServiceCloneRequestDTO serviceCloneRequestDTO =
        ServiceCloneRequestDTO.builder()
            .destinationConfig(DestinationServiceConfig.builder().serviceIdentifier("s11").build())
            .sourceConfig(SourceServiceConfig.builder().build())
            .build();

    ResponseDTO<ServiceResponse> responseDTO = serviceResourceV2.cloneService(ACCOUNT_ID, serviceCloneRequestDTO);

    assertThat(responseDTO.getData().getService().getIdentifier()).isEqualTo(IDENTIFIER);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testForceImportService() {
    ForceImportServiceRequestDTO requestDTO = ForceImportServiceRequestDTO.builder()
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .identifier(IDENTIFIER)
                                                  .connectorRef("connectorRef")
                                                  .filePath(".harness/t1_v1.yaml")
                                                  .repoName("test-repo")
                                                  .build();

    doReturn(ForceImportServiceResponse.builder().build()).when(serviceEntityService).forceImportService(any(), any());

    ArgumentCaptor<ForceImportServiceYamlOperationDTO> operationDTOCaptor =
        ArgumentCaptor.forClass(ForceImportServiceYamlOperationDTO.class);
    serviceResourceV2.forceImportService(ACCOUNT_ID, requestDTO);

    verify(serviceEntityService).forceImportService(any(), operationDTOCaptor.capture());
    assertThat(operationDTOCaptor.getValue().getConnectorRef()).isEqualTo("connectorRef");
    assertThat(operationDTOCaptor.getValue().getFilePath()).isEqualTo(".harness/t1_v1.yaml");
    assertThat(operationDTOCaptor.getValue().getRepoName()).isEqualTo("test-repo");
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testListServicePost_HandlesLargeIdentifierList() {
    List<String> largeServiceIdentifierList = new ArrayList<>();
    for (int i = 0; i < 600; i++) {
      largeServiceIdentifierList.add("service_id_" + i);
    }

    Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> serviceList = new PageImpl<>(Collections.singletonList(entity), pageable, 1);

    when(serviceEntityService.list(any(Criteria.class), any(Pageable.class))).thenReturn(serviceList);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());

    ResponseDTO<PageResponse<ServiceResponse>> response = serviceResourceV2.getServicesFilteredByRefsPost(
        0, 100, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, largeServiceIdentifierList, scopeInfo);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getContent()).hasSize(1);
    assertThat(response.getData().getContent().get(0).getService().getIdentifier()).isEqualTo(IDENTIFIER);

    verify(serviceEntityService, times(1)).list(any(Criteria.class), any(Pageable.class));
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testListServicePost_WithEmptyIdentifierList() {
    Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> serviceList = new PageImpl<>(Collections.singletonList(entity), pageable, 1);

    when(serviceEntityService.list(any(Criteria.class), any(Pageable.class))).thenReturn(serviceList);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());

    ResponseDTO<PageResponse<ServiceResponse>> response = serviceResourceV2.getServicesFilteredByRefsPost(
        0, 100, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, Collections.emptyList(), scopeInfo);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testListServicePost_WithNullIdentifierList() {
    Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> serviceList = new PageImpl<>(Collections.singletonList(entity), pageable, 1);

    when(serviceEntityService.list(any(Criteria.class), any(Pageable.class))).thenReturn(serviceList);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());

    ResponseDTO<PageResponse<ServiceResponse>> response = serviceResourceV2.getServicesFilteredByRefsPost(
        0, 100, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, scopeInfo);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testListServicePost_ReturnsEmptyList() {
    List<String> serviceIdentifiers = List.of("service1", "service2");
    Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> emptyServiceList = new PageImpl<>(Collections.emptyList(), pageable, 0);

    when(serviceEntityService.list(any(Criteria.class), any(Pageable.class))).thenReturn(emptyServiceList);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());

    ResponseDTO<PageResponse<ServiceResponse>> response = serviceResourceV2.getServicesFilteredByRefsPost(
        0, 100, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, serviceIdentifiers, scopeInfo);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getContent()).isEmpty();
    assertThat(response.getData().getTotalItems()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ABHISHEK_ARYAN)
  @Category(UnitTests.class)
  public void testListServicePost_WithPagination() {
    List<String> serviceIdentifiers = List.of("service1", "service2", "service3");
    ServiceEntity entity2 = ServiceEntity.builder()
                                .accountId(ACCOUNT_ID)
                                .orgIdentifier(ORG_IDENTIFIER)
                                .projectIdentifier(PROJ_IDENTIFIER)
                                .identifier("service2")
                                .version(1L)
                                .type(ServiceDefinitionType.KUBERNETES)
                                .build();

    Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> serviceList = new PageImpl<>(List.of(entity, entity2), pageable, 2);

    when(serviceEntityService.list(any(Criteria.class), any(Pageable.class))).thenReturn(serviceList);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), any(), anyString());

    ResponseDTO<PageResponse<ServiceResponse>> response = serviceResourceV2.getServicesFilteredByRefsPost(
        1, 2, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, serviceIdentifiers, scopeInfo);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getContent()).hasSize(2);
    assertThat(response.getData().getPageIndex()).isEqualTo(1);
    assertThat(response.getData().getPageSize()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testListServices_GitOpsMergeFF_NullifiesGitOpsEnabledFilter() {
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());
    doReturn(Page.empty()).when(serviceEntityService).list(any(), any(), anyBoolean());

    // Enable CDS_GITOPS_MERGE_K8S_SERVICES FF
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_MERGE_K8S_SERVICES)).thenReturn(true);

    // Call listServices with gitOpsEnabled=true (11th param)
    serviceResourceV2.listServices(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, List.of("id1"), null, null,
        null, Boolean.TRUE, null, null, null, null, false, false, null, false, scopeInfo);

    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(serviceEntityService).list(criteriaCaptor.capture(), any(), anyBoolean());

    // When FF is on, effectiveGitOpsEnabled becomes null, so gitOpsEnabled should NOT appear in criteria
    String criteriaJson = criteriaCaptor.getValue().getCriteriaObject().toJson();
    assertThat(criteriaJson)
        .doesNotContain("gitOpsEnabled")
        .describedAs("CDS_GITOPS_MERGE_K8S_SERVICES FF should nullify gitOpsEnabled filter");
  }

  @Test
  @Owner(developers = PARTH_SHARMA)
  @Category(UnitTests.class)
  public void testListServices_GitOpsMergeFF_Off_KeepsGitOpsEnabledFilter() {
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());
    doReturn(Page.empty()).when(serviceEntityService).list(any(), any(), anyBoolean());

    // Disable CDS_GITOPS_MERGE_K8S_SERVICES FF
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_MERGE_K8S_SERVICES)).thenReturn(false);

    // Call listServices with gitOpsEnabled=true (11th param)
    serviceResourceV2.listServices(0, 10, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, List.of("id1"), null, null,
        null, Boolean.TRUE, null, null, null, null, false, false, null, false, scopeInfo);

    ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
    verify(serviceEntityService).list(criteriaCaptor.capture(), any(), anyBoolean());

    // When FF is off, gitOpsEnabled=true should be preserved in criteria
    String criteriaJson = criteriaCaptor.getValue().getCriteriaObject().toJson();
    assertThat(criteriaJson)
        .contains("gitOpsEnabled")
        .describedAs("Without FF, gitOpsEnabled filter should be present");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteServicesMetadata_emptyResponseWhenServiceReturnsNoRepos() {
    when(serviceEntityService.getRemoteRepoListForAGivenScope(
             eq(ACCOUNT_ID), eq(null), eq(null), eq(null), eq(null), eq(0), eq(20)))
        .thenReturn(ServiceRemoteRepoListResponse.builder().repositories(null).build());

    ResponseDTO<RemoteServicesResponseDTO> response =
        serviceResourceV2.getRemoteServicesMetadata(ACCOUNT_ID, null, null, null, 0, 20, null);

    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getTotalServices()).isZero();
    assertThat(response.getData().getRepositories()).isEmpty();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteServicesMetadata_mapsServiceLayerInfoAndSumsCount() {
    ServiceRemoteRepoInfo repoA = ServiceRemoteRepoInfo.builder()
                                      .repoName("repoA")
                                      .repoURL("urlA")
                                      .count(3L)
                                      .filePathsByOwningScope(Collections.emptyMap())
                                      .connectorRefs(Collections.emptySet())
                                      .build();
    ServiceRemoteRepoInfo repoB = ServiceRemoteRepoInfo.builder()
                                      .repoName("repoB")
                                      .repoURL("urlB")
                                      .count(2L)
                                      .filePathsByOwningScope(Collections.emptyMap())
                                      .connectorRefs(Collections.emptySet())
                                      .build();
    when(serviceEntityService.getRemoteRepoListForAGivenScope(any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(ServiceRemoteRepoListResponse.builder().repositories(List.of(repoA, repoB)).build());

    ResponseDTO<RemoteServicesResponseDTO> response =
        serviceResourceV2.getRemoteServicesMetadata(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "repoA", 0, 20, null);

    assertThat(response.getData().getTotalServices()).isEqualTo(5L);
    assertThat(response.getData().getRepositories()).hasSize(2);
    assertThat(response.getData().getRepositories().get(0).getRepoName()).isEqualTo("repoA");
    assertThat(response.getData().getRepositories().get(0).getCount()).isEqualTo(3L);
    verify(serviceEntityService, times(1))
        .getRemoteRepoListForAGivenScope(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "repoA", null, 0, 20);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRemoteServicesMetadata_propagatesServiceLayerException() {
    when(serviceEntityService.getRemoteRepoListForAGivenScope(any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenThrow(new InvalidRequestException("boom"));

    assertThatThrownBy(() -> serviceResourceV2.getRemoteServicesMetadata(ACCOUNT_ID, null, null, null, 0, 20, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("boom");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testForceImportServiceChecksServiceCreateAccess() {
    ForceImportServiceRequestDTO requestDTO = ForceImportServiceRequestDTO.builder()
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .identifier(IDENTIFIER)
                                                  .build();
    when(serviceEntityService.forceImportService(eq(ACCOUNT_ID), any()))
        .thenReturn(ForceImportServiceResponse.builder().identifier(IDENTIFIER).build());

    serviceResourceV2.forceImportService(ACCOUNT_ID, requestDTO);

    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testForceImportServiceThrowsWhenNoAccess() {
    ForceImportServiceRequestDTO requestDTO = ForceImportServiceRequestDTO.builder()
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .identifier(IDENTIFIER)
                                                  .build();
    doThrow(new InvalidRequestException("User does not have permission"))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(), any(), any());

    assertThatThrownBy(() -> serviceResourceV2.forceImportService(ACCOUNT_ID, requestDTO))
        .isInstanceOf(InvalidRequestException.class);
    verify(serviceEntityService, times(0)).forceImportService(any(), any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetServicesYamlAndRuntimeInputsChecksViewAccess() {
    when(featureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());
    ServicesYamlMetadataApiInput input =
        ServicesYamlMetadataApiInput.builder().serviceIdentifiers(List.of(IDENTIFIER)).build();

    serviceResourceV2.getServicesYamlAndRuntimeInputs(input, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null);

    verify(accessControlClient, times(1))
        .hasAccess(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION);
    verify(serviceEntityService, times(1)).getServicesYamlMetadata(any(), eq(List.of(IDENTIFIER)), any(), eq(false));
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetServicesYamlAndRuntimeInputsThrowsWhenNoAccess() {
    ServicesYamlMetadataApiInput input =
        ServicesYamlMetadataApiInput.builder().serviceIdentifiers(List.of(IDENTIFIER)).build();
    doReturn(false).when(accessControlClient).hasAccess(any(), any(), any());
    doThrow(new InvalidRequestException("User does not have permission"))
        .when(accessControlClient)
        .checkForAccessOrThrow(anyList(), anyString());

    assertThatThrownBy(()
                           -> serviceResourceV2.getServicesYamlAndRuntimeInputs(
                               input, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null))
        .isInstanceOf(InvalidRequestException.class);
    verify(serviceEntityService, never()).getServicesYamlMetadata(any(), anyList(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetServicesYamlAndRuntimeInputsFiltersServicesWithoutViewAccess() {
    doReturn(false).when(accessControlClient).hasAccess(any(), any(), any());
    doReturn(accessCheckResponse(permitted(IDENTIFIER), denied(OTHER_IDENTIFIER)))
        .when(accessControlClient)
        .checkForAccessOrThrow(anyList(), anyString());
    ServicesYamlMetadataApiInput input =
        ServicesYamlMetadataApiInput.builder().serviceIdentifiers(List.of(IDENTIFIER, OTHER_IDENTIFIER)).build();
    serviceResourceV2.getServicesYamlAndRuntimeInputs(input, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null);
    verify(serviceEntityService, times(1)).getServicesYamlMetadata(any(), eq(List.of(IDENTIFIER)), any(), eq(false));
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetServicesYamlAndRuntimeInputsV2ChecksViewAccess() {
    when(featureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    ServicesYamlMetadataApiInputV2 input = ServicesYamlMetadataApiInputV2.builder().build();

    serviceResourceV2.getServicesYamlAndRuntimeInputsV2(
        input, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "false", null);

    // with no services requested there is nothing to evaluate per resource, so the scope gate still applies
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(NGResourceType.SERVICE, null), SERVICE_VIEW_PERMISSION, "Unauthorized to view services");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetServicesYamlAndRuntimeInputsV2SkipsPerServiceChecksWhenScopeLevelAccessGranted() {
    doReturn(true).when(accessControlClient).hasAccess(any(), any(), any());
    ServicesYamlMetadataApiInputV2 input = yamlMetadataInputV2(List.of(IDENTIFIER, OTHER_IDENTIFIER));
    serviceResourceV2.getServicesYamlAndRuntimeInputsV2(
        input, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "false", null);
    verify(accessControlClient, never()).checkForAccessOrThrow(anyList(), anyString());
    assertThat(captureServiceRefsPassedToMetadataFetch()).containsExactlyInAnyOrder(IDENTIFIER, OTHER_IDENTIFIER);
  }
  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetServicesYamlAndRuntimeInputsV2FiltersServicesWithoutViewAccess() {
    doReturn(false).when(accessControlClient).hasAccess(any(), any(), any());
    doReturn(accessCheckResponse(permitted(IDENTIFIER), denied(OTHER_IDENTIFIER)))
        .when(accessControlClient)
        .checkForAccessOrThrow(anyList(), anyString());
    ServicesYamlMetadataApiInputV2 input = yamlMetadataInputV2(List.of(IDENTIFIER, OTHER_IDENTIFIER));
    serviceResourceV2.getServicesYamlAndRuntimeInputsV2(
        input, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "false", null);
    assertThat(captureServiceRefsPassedToMetadataFetch()).containsExactly(IDENTIFIER);
  }
  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetServicesYamlAndRuntimeInputsV2ChecksScopedRefsAgainstTheirOwnScope() {
    doReturn(false).when(accessControlClient).hasAccess(any(), any(), any());
    doReturn(AccessCheckResponseDTO.builder()
                 .accessControlList(List.of(AccessControlDTO.builder()
                                                .permitted(true)
                                                .resourceIdentifier(IDENTIFIER)
                                                .resourceScope(ResourceScope.of(ACCOUNT_ID, null, null))
                                                .build()))
                 .build())
        .when(accessControlClient)
        .checkForAccessOrThrow(anyList(), anyString());
    ServicesYamlMetadataApiInputV2 input =
        ServicesYamlMetadataApiInputV2.builder()
            .serviceWithGitInfoList(List.of(EntityWithGitInfo.builder().ref("account." + IDENTIFIER).build()))
            .build();
    serviceResourceV2.getServicesYamlAndRuntimeInputsV2(
        input, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, "false", null);
    ArgumentCaptor<List<PermissionCheckDTO>> checksCaptor = ArgumentCaptor.forClass(List.class);
    verify(accessControlClient, times(1)).checkForAccessOrThrow(checksCaptor.capture(), anyString());
    assertThat(checksCaptor.getValue()).hasSize(1);
    PermissionCheckDTO check = checksCaptor.getValue().get(0);
    assertThat(check.getResourceIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(check.getResourceType()).isEqualTo(NGResourceType.SERVICE);
    assertThat(check.getPermission()).isEqualTo(SERVICE_VIEW_PERMISSION);
    assertThat(check.getResourceScope().getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(check.getResourceScope().getOrgIdentifier()).isNull();
    assertThat(check.getResourceScope().getProjectIdentifier()).isNull();
    verify(serviceEntityService, times(1))
        .getServicesYamlMetadata(any(), eq(List.of("account." + IDENTIFIER)), any(), eq(false));
  }
  private ServicesYamlMetadataApiInputV2 yamlMetadataInputV2(List<String> refs) {
    List<EntityWithGitInfo> serviceWithGitInfoList = new ArrayList<>();
    for (String ref : refs) {
      serviceWithGitInfoList.add(EntityWithGitInfo.builder().ref(ref).build());
    }
    return ServicesYamlMetadataApiInputV2.builder().serviceWithGitInfoList(serviceWithGitInfoList).build();
  }
  private List<String> captureServiceRefsPassedToMetadataFetch() {
    ArgumentCaptor<List<String>> refsCaptor = ArgumentCaptor.forClass(List.class);
    verify(serviceEntityService, times(1)).getServicesYamlMetadata(any(), refsCaptor.capture(), any(), anyBoolean());
    return refsCaptor.getValue();
  }
  private AccessCheckResponseDTO accessCheckResponse(AccessControlDTO... accessControlDTOs) {
    return AccessCheckResponseDTO.builder().accessControlList(List.of(accessControlDTOs)).build();
  }
  private AccessControlDTO permitted(String identifier) {
    return accessControlDTO(identifier, true);
  }
  private AccessControlDTO denied(String identifier) {
    return accessControlDTO(identifier, false);
  }
  private AccessControlDTO accessControlDTO(String identifier, boolean permitted) {
    return AccessControlDTO.builder()
        .permitted(permitted)
        .resourceIdentifier(identifier)
        .resourceScope(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .build();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHiddenReadEndpointsAreGuardedByNGAccessControlCheck() {
    assertServiceViewAccessControlCheck("getServiceRuntimeInputs");
    assertServiceViewAccessControlCheck("getArtifactSourceInputs");
    assertServiceViewAccessControlCheck("mergeServiceInputs");
  }

  private void assertServiceViewAccessControlCheck(String methodName) {
    Method method = null;
    for (Method m : ServiceResourceV2.class.getDeclaredMethods()) {
      if (m.getName().equals(methodName)) {
        method = m;
        break;
      }
    }
    assertThat(method).as("method %s should exist", methodName).isNotNull();
    NGAccessControlCheck annotation = method.getAnnotation(NGAccessControlCheck.class);
    assertThat(annotation).as("method %s should be guarded by @NGAccessControlCheck", methodName).isNotNull();
    assertThat(annotation.resourceType()).isEqualTo(NGResourceType.SERVICE);
    assertThat(annotation.permission()).isEqualTo("core_service_view");
  }
}