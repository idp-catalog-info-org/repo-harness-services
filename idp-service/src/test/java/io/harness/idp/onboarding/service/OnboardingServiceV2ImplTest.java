/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.service;

import static io.harness.idp.common.Constants.GITHUB;
import static io.harness.idp.common.Constants.HARNESS;
import static io.harness.idp.integrations.utils.Constants.HCR_CONNECTOR_IDENTIFIER;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.onboarding.client.FakeOrganizationClient;
import io.harness.idp.onboarding.client.FakeProjectClient;
import io.harness.idp.onboarding.client.FakeServiceResourceClient;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.idp.onboarding.config.OnboardingModuleV2Config;
import io.harness.idp.onboarding.entities.OnboardingFlowEntity;
import io.harness.idp.onboarding.mappers.HarnessOrgToBackstageDomain;
import io.harness.idp.onboarding.mappers.HarnessProjectToBackstageSystem;
import io.harness.idp.onboarding.mappers.HarnessServiceToBackstageComponent;
import io.harness.idp.onboarding.repositories.OnboardingFlowEntityRepository;
import io.harness.idp.onboarding.service.impl.OnboardingServiceV2Impl;
import io.harness.idp.status.enums.StatusType;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationResponse;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesCountResponse;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchRequest;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchRequestFilterOptions;
import io.harness.spec.server.idp.v1.model.OnboardingCdEntitiesFetchResponse;
import io.harness.spec.server.idp.v1.model.OnboardingGenerateYamlDefRequest;
import io.harness.spec.server.idp.v1.model.OnboardingGenerateYamlDefResponse;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesRequest;
import io.harness.spec.server.idp.v1.model.OnboardingImportCdEntitiesResponse;
import io.harness.spec.server.idp.v1.model.OnboardingSkipRequest;
import io.harness.spec.server.idp.v1.model.OnboardingSkipResponse;
import io.harness.spec.server.idp.v1.model.OnboardingStatusResponse;
import io.harness.spec.server.idp.v1.model.StatusInfo;
import io.harness.spec.server.idp.v1.model.StatusInfoV2;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import retrofit2.Call;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class OnboardingServiceV2ImplTest extends CategoryTest {
  AutoCloseable openMocks;
  static final String TEST_ACCOUNT_IDENTIFIER = "123";
  static final String TEST_CONNECTOR_IDENTIFIER = "connectorIdentifier";
  static final String TEST_REPOSITORY = "https://github.com/harness/harness-core.git";
  static final String TEST_BRANCH = "branch";
  static final String TEST_PATH = "path";
  final OnboardingModuleConfig onboardingModuleConfig =
      OnboardingModuleConfig.builder()
          .descriptionForSampleEntity(
              "This is an example of how the corresponding service definition YAML files will be created.")
          .descriptionForEntitySelected("A YAML file will be created for each service inside your GitHub repository. "
              + "An example of what the files will look like is shown below")
          .tmpPathForCatalogInfoYamlStore("/tmp")
          .harnessCiCdAnnotations(Map.of("projectUrl",
              "https://localhost:8181/ng/account/accountIdentifier/home/orgs/orgIdentifier/projects/projectIdentifier/"
                  + "details",
              "serviceUrl",
              "https://localhost:8181/ng/account/accountIdentifier/cd/orgs/orgIdentifier/projects/projectIdentifier/"
                  + "services/serviceIdentifier"))
          .build();
  final OnboardingModuleV2Config onboardingModuleV2Config =
      OnboardingModuleV2Config.builder()
          .descriptionForSampleCatalogInfoDef(
              "Because you didn't choose any Harness services to import in the previous step, an example service "
              + "sample-catalog-info.yaml will be created inside your Git repository as shown below")
          .descriptionForActualCatalogInfoDef(
              "A YAML file will be created for each service inside your Git repository. An example of what the files "
              + "will look like is shown below")
          .build();
  Call<Object> call;

  @InjectMocks private OnboardingServiceV2Impl onboardingServiceV2;

  @InjectMocks HarnessOrgToBackstageDomain harnessOrgToBackstageDomain;
  @InjectMocks HarnessProjectToBackstageSystem harnessProjectToBackstageSystem;
  @Mock OnboardingFlowEntityRepository onboardingFlowEntityRepository;
  @Mock GitIntegrationServiceImpl gitIntegrationService;
  @Mock StatusInfoService statusInfoService;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock IdpCommonService idpCommonService;

  @Before
  public void setUp() throws IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);

    FieldUtils.writeField(onboardingServiceV2, "harnessOrgToBackstageDomain", harnessOrgToBackstageDomain, true);
    FieldUtils.writeField(
        onboardingServiceV2, "harnessProjectToBackstageSystem", harnessProjectToBackstageSystem, true);

    HarnessServiceToBackstageComponent harnessServiceToBackstageComponent =
        new HarnessServiceToBackstageComponent(onboardingModuleConfig, "local");
    FieldUtils.writeField(
        onboardingServiceV2, "harnessServiceToBackstageComponent", harnessServiceToBackstageComponent, true);

    FakeServiceResourceClient serviceResourceClient = new FakeServiceResourceClient();
    FakeOrganizationClient organizationClient = new FakeOrganizationClient();
    FakeProjectClient projectClient = new FakeProjectClient();

    FieldUtils.writeField(onboardingServiceV2, "serviceResourceClient", serviceResourceClient, true);
    FieldUtils.writeField(onboardingServiceV2, "organizationClient", organizationClient, true);
    FieldUtils.writeField(onboardingServiceV2, "projectClient", projectClient, true);

    FieldUtils.writeField(onboardingServiceV2, "onboardingModuleV2Config", onboardingModuleV2Config, true);

    call = mock(Call.class);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCdEntitiesCount() {
    when(idpCommonService.idpCatalogCDAutoDiscoveryEnabled(TEST_ACCOUNT_IDENTIFIER)).thenReturn(false);

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));

    OnboardingCdEntitiesCountResponse onboardingCdEntitiesCountResponse =
        onboardingServiceV2.cdEntitiesCount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(1, onboardingCdEntitiesCountResponse.getCdEntitiesCount().intValue());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCdEntitiesCountConsiderAlreadyImported() {
    when(idpCommonService.idpCatalogCDAutoDiscoveryEnabled(TEST_ACCOUNT_IDENTIFIER)).thenReturn(false);

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setImportedCDEntities(Set.of("test-test-test"));

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));

    OnboardingCdEntitiesCountResponse onboardingCdEntitiesCountResponse =
        onboardingServiceV2.cdEntitiesCount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(0, onboardingCdEntitiesCountResponse.getCdEntitiesCount().intValue());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCdEntitiesFetch() {
    OnboardingCdEntitiesFetchRequest onboardingCdEntitiesFetchRequest = new OnboardingCdEntitiesFetchRequest();
    OnboardingCdEntitiesFetchRequestFilterOptions onboardingCdEntitiesFetchRequestFilterOptions =
        new OnboardingCdEntitiesFetchRequestFilterOptions();
    onboardingCdEntitiesFetchRequest.setFilterOptions(onboardingCdEntitiesFetchRequestFilterOptions);

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));

    OnboardingCdEntitiesFetchResponse onboardingCdEntitiesFetchResponse = onboardingServiceV2.cdEntitiesFetch(
        TEST_ACCOUNT_IDENTIFIER, onboardingCdEntitiesFetchRequest, PageRequest.of(0, 10), "");
    assertEquals(1, onboardingCdEntitiesFetchResponse.getOrganizationsCount().intValue());
    assertEquals(1, onboardingCdEntitiesFetchResponse.getProjectsCount().intValue());
    assertEquals(1, onboardingCdEntitiesFetchResponse.getServicesCount().intValue());
    assertEquals(1, onboardingCdEntitiesFetchResponse.getEntities().size());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCdEntitiesFetchConsiderAlreadyImported() {
    OnboardingCdEntitiesFetchRequest onboardingCdEntitiesFetchRequest = new OnboardingCdEntitiesFetchRequest();
    OnboardingCdEntitiesFetchRequestFilterOptions onboardingCdEntitiesFetchRequestFilterOptions =
        new OnboardingCdEntitiesFetchRequestFilterOptions();
    onboardingCdEntitiesFetchRequest.setFilterOptions(onboardingCdEntitiesFetchRequestFilterOptions);

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setImportedCDEntities(Set.of("orgId-projectId-serviceId"));

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));

    OnboardingCdEntitiesFetchResponse onboardingCdEntitiesFetchResponse = onboardingServiceV2.cdEntitiesFetch(
        TEST_ACCOUNT_IDENTIFIER, onboardingCdEntitiesFetchRequest, PageRequest.of(0, 10), "");
    assertEquals(0, onboardingCdEntitiesFetchResponse.getOrganizationsCount().intValue());
    assertEquals(0, onboardingCdEntitiesFetchResponse.getProjectsCount().intValue());
    assertEquals(0, onboardingCdEntitiesFetchResponse.getServicesCount().intValue());
    assertEquals(0, onboardingCdEntitiesFetchResponse.getEntities().size());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGenerateYamlDefSample() {
    OnboardingGenerateYamlDefRequest onboardingGenerateYamlDefRequest = new OnboardingGenerateYamlDefRequest();
    onboardingGenerateYamlDefRequest.setType(OnboardingGenerateYamlDefRequest.TypeEnum.SAMPLE);

    OnboardingGenerateYamlDefResponse onboardingGenerateYamlDefResponse =
        onboardingServiceV2.generateYamlDef(TEST_ACCOUNT_IDENTIFIER, onboardingGenerateYamlDefRequest);

    assertEquals(onboardingModuleV2Config.getDescriptionForSampleCatalogInfoDef(),
        onboardingGenerateYamlDefResponse.getYamlDefDesc());
    assertEquals("apiVersion: backstage.io/v1alpha1\n"
            + "kind: Component\n"
            + "metadata:\n"
            + "  name: my-sample-service\n"
            + "  description: My Sample service.\n"
            + "  tags: [my-sample]\n"
            + "  links:\n"
            + "    - title: Website\n"
            + "      url: http://my-sample-website.com\n"
            + "  annotations:\n"
            + "    github.com/project-slug: my-sample-org/my-sample-repo\n"
            + "    backstage.io/techdocs-ref: dir:../../\n"
            + "spec:\n"
            + "  type: Service\n"
            + "  owner: my-sample-team\n"
            + "  lifecycle: experimental\n"
            + "  system: my-sample-system",
        onboardingGenerateYamlDefResponse.getYamlDef());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGenerateYamlDefActual() {
    OnboardingGenerateYamlDefRequest onboardingGenerateYamlDefRequest = new OnboardingGenerateYamlDefRequest();
    onboardingGenerateYamlDefRequest.setType(OnboardingGenerateYamlDefRequest.TypeEnum.ACTUAL);
    onboardingGenerateYamlDefRequest.setEntityIdentifier("orgId|projectId|serviceId");

    OnboardingGenerateYamlDefResponse onboardingGenerateYamlDefResponse =
        onboardingServiceV2.generateYamlDef(TEST_ACCOUNT_IDENTIFIER, onboardingGenerateYamlDefRequest);

    assertEquals(onboardingModuleV2Config.getDescriptionForActualCatalogInfoDef(),
        onboardingGenerateYamlDefResponse.getYamlDefDesc());
    assertEquals("kind: Component\n"
            + "apiVersion: backstage.io/v1alpha1\n"
            + "metadata:\n"
            + "  name: serviceId\n"
            + "  description: serviceDesc\n"
            + "  annotations:\n"
            + "    harness.io/project-url: "
            + "https://localhost:8181/ng/account/123/home/orgs/orgId/projects/projectId/details\n"
            + "    harness.io/cd-serviceId: serviceId\n"
            + "    harness.io/services: |\n"
            + "      serviceId: "
            + "https://localhost:8181/ng/account/123/cd/orgs/orgId/projects/projectId/services/serviceId\n"
            + "  title: serviceName\n"
            + "  tags: []\n"
            + "spec:\n"
            + "  type: Service\n"
            + "  lifecycle: Unknown\n"
            + "  owner: Unknown\n"
            + "  domain: orgId\n"
            + "  system:\n"
            + "    - projectId\n",
        onboardingGenerateYamlDefResponse.getYamlDef());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOnboardingStatus() {
    List<IntegrationEntity> integrationEntities = new ArrayList<>();
    integrationEntities.add(new GithubIntegrationEntity());

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    when(gitIntegrationService.fetchNonManagedGitIntegrations(TEST_ACCOUNT_IDENTIFIER)).thenReturn(integrationEntities);

    OnboardingStatusResponse onboardingStatusResponse =
        onboardingServiceV2.getOnboardingStatus(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(OnboardingStatusResponse.StatusEnum.WITH_INTEGRATION_NO_IMPORT, onboardingStatusResponse.getStatus());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOnboardingStatusOnboardingFlowEntityPresent() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setCurrentStatus("ONBOARDING_COMPLETED_ALLOW_FURTHER");

    List<GitIntegrationResponse> gitIntegrationResponses = new ArrayList<>();
    gitIntegrationResponses.add(new GitIntegrationResponse());

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));
    when(gitIntegrationService.get(TEST_ACCOUNT_IDENTIFIER, Pageable.ofSize(1), null))
        .thenReturn(gitIntegrationResponses);

    OnboardingStatusResponse onboardingStatusResponse =
        onboardingServiceV2.getOnboardingStatus(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(
        OnboardingStatusResponse.StatusEnum.ONBOARDING_COMPLETED_ALLOW_FURTHER, onboardingStatusResponse.getStatus());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOnboardingStatusOnboardingFlowEntityPresentGetStarted() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setCurrentStatus("GET_STARTED");

    List<IntegrationEntity> integrationEntities = new ArrayList<>();
    integrationEntities.add(new GithubIntegrationEntity());

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));
    when(gitIntegrationService.fetchNonManagedGitIntegrations(TEST_ACCOUNT_IDENTIFIER)).thenReturn(integrationEntities);

    OnboardingStatusResponse onboardingStatusResponse =
        onboardingServiceV2.getOnboardingStatus(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(OnboardingStatusResponse.StatusEnum.WITH_INTEGRATION_NO_IMPORT, onboardingStatusResponse.getStatus());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testImportCdEntitiesSample() {
    OnboardingImportCdEntitiesRequest onboardingImportCdEntitiesRequest = new OnboardingImportCdEntitiesRequest();
    onboardingImportCdEntitiesRequest.setType(OnboardingImportCdEntitiesRequest.TypeEnum.SAMPLE);
    onboardingImportCdEntitiesRequest.setWriteTo(gitIntegrationRequest());

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    StatusInfoV2 statusInfoV2 = new StatusInfoV2();
    statusInfoV2.put(StatusType.ONBOARDING.name().toLowerCase(),
        new StatusInfo().currentStatus(StatusInfo.CurrentStatusEnum.NOT_FOUND));

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));
    when(gitIntegrationService.getConnectorInfo(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_CONNECTOR_IDENTIFIER))
        .thenReturn(null);
    when(gitIntegrationService.getGitIntegrationType(any())).thenReturn(GITHUB);
    doNothing()
        .when(gitIntegrationService)
        .writeThroughAPI(eq(TEST_ACCOUNT_IDENTIFIER), eq(gitIntegrationRequest()), any());
    when(onboardingFlowEntityRepository.save(any())).thenReturn(onboardingFlowEntity);
    when(statusInfoService.findByAccountIdentifierAndTypeV2(TEST_ACCOUNT_IDENTIFIER, StatusType.ONBOARDING.name()))
        .thenReturn(statusInfoV2);
    when(statusInfoService.save(any(), eq(TEST_ACCOUNT_IDENTIFIER), eq(StatusType.ONBOARDING.name())))
        .thenReturn(new StatusInfo());

    OnboardingImportCdEntitiesResponse onboardingImportCdEntitiesResponse =
        onboardingServiceV2.importCdEntities(TEST_ACCOUNT_IDENTIFIER, onboardingImportCdEntitiesRequest);
    assertEquals("SUCCESS", onboardingImportCdEntitiesResponse.getStatus());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testImportCdEntitiesSampleHarnessCodeRepo() {
    OnboardingImportCdEntitiesRequest onboardingImportCdEntitiesRequest = new OnboardingImportCdEntitiesRequest();
    onboardingImportCdEntitiesRequest.setType(OnboardingImportCdEntitiesRequest.TypeEnum.SAMPLE);
    GitIntegrationRequest gitIntegrationRequest = gitIntegrationRequest();
    gitIntegrationRequest.setConnectorIdentifier(HCR_CONNECTOR_IDENTIFIER);
    WriteValidationDetails writeValidationDetails = gitIntegrationRequest.getWriteValidationDetails();
    writeValidationDetails.setRepository(
        "https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/HarnessHCRInternalUAT/Harness_Code/harness-core.git");
    gitIntegrationRequest.setWriteValidationDetails(writeValidationDetails);
    onboardingImportCdEntitiesRequest.setWriteTo(gitIntegrationRequest);

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    StatusInfoV2 statusInfoV2 = new StatusInfoV2();
    statusInfoV2.put(StatusType.ONBOARDING.name().toLowerCase(),
        new StatusInfo().currentStatus(StatusInfo.CurrentStatusEnum.NOT_FOUND));

    when(gitIntegrationService.getAccountBaseUrl(TEST_ACCOUNT_IDENTIFIER)).thenReturn("vanity.harness.io");
    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));
    when(gitIntegrationService.connectorInfoDTO(
             TEST_ACCOUNT_IDENTIFIER, HCR_CONNECTOR_IDENTIFIER, gitIntegrationRequest))
        .thenReturn(ConnectorInfoDTO.builder().build());
    when(gitIntegrationService.getGitIntegrationType(any())).thenReturn(HARNESS);
    doNothing()
        .when(gitIntegrationService)
        .writeThroughAPI(eq(TEST_ACCOUNT_IDENTIFIER), eq(gitIntegrationRequest()), any());
    when(onboardingFlowEntityRepository.save(any())).thenReturn(onboardingFlowEntity);
    when(statusInfoService.findByAccountIdentifierAndTypeV2(TEST_ACCOUNT_IDENTIFIER, StatusType.ONBOARDING.name()))
        .thenReturn(statusInfoV2);
    when(statusInfoService.save(any(), eq(TEST_ACCOUNT_IDENTIFIER), eq(StatusType.ONBOARDING.name())))
        .thenReturn(new StatusInfo());

    OnboardingImportCdEntitiesResponse onboardingImportCdEntitiesResponse =
        onboardingServiceV2.importCdEntities(TEST_ACCOUNT_IDENTIFIER, onboardingImportCdEntitiesRequest);
    assertEquals("SUCCESS", onboardingImportCdEntitiesResponse.getStatus());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testImportCdEntitiesAll() {
    OnboardingImportCdEntitiesRequest onboardingImportCdEntitiesRequest = new OnboardingImportCdEntitiesRequest();
    onboardingImportCdEntitiesRequest.setType(OnboardingImportCdEntitiesRequest.TypeEnum.ALL);
    onboardingImportCdEntitiesRequest.setWriteTo(gitIntegrationRequest());

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    StatusInfoV2 statusInfoV2 = new StatusInfoV2();
    statusInfoV2.put(StatusType.ONBOARDING.name().toLowerCase(),
        new StatusInfo().currentStatus(StatusInfo.CurrentStatusEnum.NOT_FOUND));

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));
    when(gitIntegrationService.getConnectorInfo(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_CONNECTOR_IDENTIFIER))
        .thenReturn(null);
    when(gitIntegrationService.getGitIntegrationType(any())).thenReturn(GITHUB);
    doNothing()
        .when(gitIntegrationService)
        .writeThroughAPI(eq(TEST_ACCOUNT_IDENTIFIER), eq(gitIntegrationRequest()), any());
    when(onboardingFlowEntityRepository.save(any())).thenReturn(onboardingFlowEntity);
    when(statusInfoService.findByAccountIdentifierAndTypeV2(TEST_ACCOUNT_IDENTIFIER, StatusType.ONBOARDING.name()))
        .thenReturn(statusInfoV2);
    when(statusInfoService.save(any(), eq(TEST_ACCOUNT_IDENTIFIER), eq(StatusType.ONBOARDING.name())))
        .thenReturn(new StatusInfo());

    OnboardingImportCdEntitiesResponse onboardingImportCdEntitiesResponse =
        onboardingServiceV2.importCdEntities(TEST_ACCOUNT_IDENTIFIER, onboardingImportCdEntitiesRequest);
    assertEquals("SUCCESS", onboardingImportCdEntitiesResponse.getStatus());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testAsyncImport() {
    List<OnboardingFlowEntity> onboardingFlowEntities = new ArrayList<>();
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    onboardingFlowEntity.setEntitiesToImport(ImmutableTriple.of(Set.of("orgId"), Map.of("orgId", Set.of("projectId")),
        Map.of("orgId", Map.of("projectId", Set.of("serviceId")))));
    onboardingFlowEntity.setWriteDetails(Set.of(OnboardingFlowEntity.WriteDetails.builder()
                                                    .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                    .repositoryUrl(TEST_REPOSITORY)
                                                    .branch(TEST_BRANCH)
                                                    .path(TEST_PATH)
                                                    .build()));
    onboardingFlowEntity.setLastUpdatedBy(EmbeddedUser.builder().uuid("uuid").name("name").email("email").build());
    onboardingFlowEntities.add(onboardingFlowEntity);

    when(onboardingFlowEntityRepository.findByRegisterEntitiesOnIdpAtNot(Long.MAX_VALUE))
        .thenReturn(onboardingFlowEntities);
    when(gitIntegrationService.getConnectorInfo(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_CONNECTOR_IDENTIFIER))
        .thenReturn(null);
    when(gitIntegrationService.getGitIntegrationType(any())).thenReturn(GITHUB);
    doNothing()
        .when(gitIntegrationService)
        .writeThroughAPI(eq(TEST_ACCOUNT_IDENTIFIER), eq(gitIntegrationRequest()), any());
    when(backstageResourceClient.createCatalogLocation(any(), any())).thenReturn(call);
    when(onboardingFlowEntityRepository.save(any())).thenReturn(onboardingFlowEntity);

    onboardingServiceV2.asyncImport();
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPostOnboardingSkipInitial() {
    OnboardingSkipRequest onboardingSkipRequest = new OnboardingSkipRequest();
    onboardingSkipRequest.setSkippedAt(OnboardingSkipRequest.SkippedAtEnum.WITH_INTEGRATION_NO_IMPORT);

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    onboardingFlowEntity.setSkippedAt(
        OnboardingFlowEntity.SkippedAt.valueOf(onboardingSkipRequest.getSkippedAt().name()));
    onboardingFlowEntity.setCurrentStatus(onboardingSkipRequest.getSkippedAt().name());

    StatusInfoV2 statusInfoV2 = new StatusInfoV2();
    statusInfoV2.put(StatusType.ONBOARDING.name().toLowerCase(),
        new StatusInfo().currentStatus(StatusInfo.CurrentStatusEnum.NOT_FOUND));

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());
    when(onboardingFlowEntityRepository.save(onboardingFlowEntity)).thenReturn(onboardingFlowEntity);
    when(statusInfoService.findByAccountIdentifierAndTypeV2(TEST_ACCOUNT_IDENTIFIER, StatusType.ONBOARDING.name()))
        .thenReturn(statusInfoV2);
    when(statusInfoService.save(any(), eq(TEST_ACCOUNT_IDENTIFIER), eq(StatusType.ONBOARDING.name())))
        .thenReturn(new StatusInfo());

    OnboardingSkipResponse onboardingSkipResponse =
        onboardingServiceV2.postOnboardingSkip(TEST_ACCOUNT_IDENTIFIER, onboardingSkipRequest);
    assertEquals("SUCCESS", onboardingSkipResponse.getStatus());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPostOnboardingSkipExistingOnboardingFlowEntity() {
    OnboardingSkipRequest onboardingSkipRequest = new OnboardingSkipRequest();
    onboardingSkipRequest.setSkippedAt(OnboardingSkipRequest.SkippedAtEnum.WITH_INTEGRATION_NO_IMPORT);

    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    onboardingFlowEntity.setSkippedAt(
        OnboardingFlowEntity.SkippedAt.valueOf(onboardingSkipRequest.getSkippedAt().name()));
    onboardingFlowEntity.setCurrentStatus(onboardingSkipRequest.getSkippedAt().name());

    StatusInfoV2 statusInfoV2 = new StatusInfoV2();
    statusInfoV2.put(StatusType.ONBOARDING.name().toLowerCase(),
        new StatusInfo().currentStatus(StatusInfo.CurrentStatusEnum.NOT_FOUND));

    when(onboardingFlowEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(onboardingFlowEntity));
    when(onboardingFlowEntityRepository.save(onboardingFlowEntity)).thenReturn(onboardingFlowEntity);
    when(statusInfoService.findByAccountIdentifierAndTypeV2(TEST_ACCOUNT_IDENTIFIER, StatusType.ONBOARDING.name()))
        .thenReturn(statusInfoV2);
    when(statusInfoService.save(any(), eq(TEST_ACCOUNT_IDENTIFIER), eq(StatusType.ONBOARDING.name())))
        .thenReturn(new StatusInfo());

    OnboardingSkipResponse onboardingSkipResponse =
        onboardingServiceV2.postOnboardingSkip(TEST_ACCOUNT_IDENTIFIER, onboardingSkipRequest);
    assertEquals("SUCCESS", onboardingSkipResponse.getStatus());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private GitIntegrationRequest gitIntegrationRequest() {
    GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
    gitIntegrationRequest.setConnectorIdentifier(TEST_CONNECTOR_IDENTIFIER);

    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository(TEST_REPOSITORY);
    writeValidationDetails.setBranch(TEST_BRANCH);
    writeValidationDetails.setPath(TEST_PATH);

    gitIntegrationRequest.setWriteValidationDetails(writeValidationDetails);

    return gitIntegrationRequest;
  }
}
