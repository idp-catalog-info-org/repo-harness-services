/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.NGConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.getManagedAdminRole;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.KAPIL_GARG;
import static io.harness.rule.OwnerRule.SAHIBA;

import static java.util.Optional.of;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.AccessControlAdminClientConfiguration;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.beans.FeatureName;
import io.harness.beans.PageResponse;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.code.CodeRepoResponseDTO;
import io.harness.ff.FeatureFlagService;
import io.harness.licensing.Edition;
import io.harness.licensing.PricingType;
import io.harness.licensing.services.LicenseService;
import io.harness.ng.code.services.HarnessCodeService;
import io.harness.ng.config.AutoProvisionLicenseConfig;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.event.manager.CIDefaultEntityManager;
import io.harness.ng.core.event.manager.HarnessLLMConnectorService;
import io.harness.ng.core.event.manager.HarnessSMManager;
import io.harness.ng.core.event.service.NGAccountSetupService;
import io.harness.ng.core.manifests.SampleManifestFileService;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.user.remote.UserClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class NGAccountSetupServiceTest extends CategoryTest {
  public static final String ACCOUNT_ID = "account-id";
  public static final String FIRST_ADMIN_USER = "lv0euRhKRCyiXWzS7pOg6f";
  public static final String SECOND_ADMIN_USER = "lv0euRhKRCyiXWzS7pOg6g";
  public static final String NON_ADMIN_USER = "lv0euRhKRCyiXWzS7pOg6h";
  @Mock private NGAccountSetupService ngAccountSetupService;
  @Mock private OrganizationService organizationService;
  @Mock private ProjectService projectService;
  @Mock private AccessControlAdminClient accessControlAdminClient;
  @Mock private NgUserService ngUserService;
  @Mock private UserClient userClient;
  @Mock private HarnessSMManager harnessSMManager;
  @Mock private CIDefaultEntityManager ciDefaultEntityManager;
  @Mock private NextGenConfiguration nextGenConfiguration;
  @Mock private AccessControlAdminClientConfiguration accessControlAdminClientConfiguration;
  @Mock private NGAccountSettingService accountSettingService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DefaultUserGroupService defaultUserGroupService;
  @Mock private SampleManifestFileService sampleManifestFileService;
  @Mock private ScopeInfoService scopeResolverService;
  @Mock private HarnessCodeService harnessCodeService;
  @Mock private LicenseService licenseService;
  @Mock private HarnessLLMConnectorService harnessLLMConnectorService;
  String orgUniqueId = randomAlphabetic(10);
  String projUniqueId = randomAlphabetic(10);

  @Before
  public void setup() throws IOException {
    when(nextGenConfiguration.getAccessControlAdminClientConfiguration())
        .thenReturn(accessControlAdminClientConfiguration);
    when(accessControlAdminClientConfiguration.getMockAccessControlService()).thenReturn(true);

    when(organizationService.get(any(ScopeInfo.class), any()))
        .thenReturn(of(Organization.builder()
                           .accountIdentifier(ACCOUNT_ID)
                           .identifier(DEFAULT_ORG_IDENTIFIER)
                           .name(DEFAULT_ORG_IDENTIFIER)
                           .uniqueId(orgUniqueId)
                           .build()));
    when(projectService.get(any(ScopeInfo.class), anyString()))
        .thenReturn(of(Project.builder()
                           .accountIdentifier(ACCOUNT_ID)
                           .orgIdentifier(DEFAULT_ORG_IDENTIFIER)
                           .identifier(DEFAULT_PROJECT_IDENTIFIER)
                           .uniqueId(projUniqueId)
                           .name(DEFAULT_PROJECT_IDENTIFIER)
                           .build()));

    ScopeInfo accountScopeInfo =
        ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).uniqueId(ACCOUNT_ID).build();
    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_ID)
                                 .orgIdentifier(DEFAULT_ORG_IDENTIFIER)
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .uniqueId(orgUniqueId)
                                 .build();
    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(DEFAULT_ORG_IDENTIFIER)
                                     .projectIdentifier(DEFAULT_PROJECT_IDENTIFIER)
                                     .scopeType(ScopeLevel.PROJECT)
                                     .uniqueId(projUniqueId)
                                     .build();
    when(scopeResolverService.getScopeInfo(ACCOUNT_ID, DEFAULT_ORG_IDENTIFIER, DEFAULT_PROJECT_IDENTIFIER))
        .thenReturn(projectScopeInfo);
    when(scopeResolverService.getScopeInfo(ACCOUNT_ID, DEFAULT_ORG_IDENTIFIER, null)).thenReturn(orgScopeInfo);
    when(scopeResolverService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(accountScopeInfo);

    PageResponse<UserInfo> pageResponse = new PageResponse<>();
    List<UserInfo> users = new ArrayList<>();
    users.add(UserInfo.builder().uuid(FIRST_ADMIN_USER).admin(true).build());
    users.add(UserInfo.builder().uuid(SECOND_ADMIN_USER).admin(true).build());
    users.add(UserInfo.builder().uuid(NON_ADMIN_USER).admin(false).build());
    pageResponse.setResponse(users);

    Call<RestResponse<PageResponse<UserInfo>>> userListRequest = mock(Call.class);
    when(userListRequest.execute())
        .thenReturn(Response.success(new RestResponse<>(pageResponse)))
        .thenReturn(Response.success(new RestResponse<>(new PageResponse<>())));
    when(userClient.list(any(), any(), any(), any(), anyBoolean())).thenReturn(userListRequest);

    Call<ResponseDTO<List<RoleAssignmentResponseDTO>>> roleAssignmentRequest = mock(Call.class);
    when(roleAssignmentRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse()));
    when(accessControlAdminClient.createMultiRoleAssignment(any(), any(), any(), any(), any()))
        .thenReturn(roleAssignmentRequest);

    when(ngUserService.listUsersHavingRole(orgScopeInfo, getManagedAdminRole(orgScopeInfo),
             List.of(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER,
                 DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER)))
        .thenReturn(List.of(UserMetadataDTO.builder().build()));

    when(ngUserService.listUsersHavingRole(projectScopeInfo, getManagedAdminRole(projectScopeInfo),
             List.of(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER, DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER)))
        .thenReturn(List.of(UserMetadataDTO.builder().build()));

    SampleManifestFileService.SampleManifestFileCreateResponse response =
        mock(SampleManifestFileService.SampleManifestFileCreateResponse.class);
    when(response.isCreated()).thenReturn(true);
    when(sampleManifestFileService.createDefaultFilesInFileStore(ACCOUNT_ID)).thenReturn(response);

    when(harnessCodeService.createHarnessDefaultRepository(ACCOUNT_ID))
        .thenReturn(CodeRepoResponseDTO.builder().build());

    AutoProvisionLicenseConfig autoProvisionLicenseConfig = new AutoProvisionLicenseConfig(false, null);
    ngAccountSetupService = new NGAccountSetupService(organizationService, accessControlAdminClient, ngUserService,
        userClient, harnessSMManager, ciDefaultEntityManager, nextGenConfiguration, accountSettingService,
        projectService, featureFlagService, sampleManifestFileService, defaultUserGroupService, scopeResolverService,
        harnessCodeService, licenseService, autoProvisionLicenseConfig, harnessLLMConnectorService);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDefaultProjectIsEnabledAndAllCGUsersMigratedToNG() {
    when(featureFlagService.isGlobalEnabled(FeatureName.CREATE_DEFAULT_PROJECT)).thenReturn(true);
    when(featureFlagService.isNotEnabled(FeatureName.PL_DO_NOT_MIGRATE_NON_ADMIN_CG_USERS_TO_NG, ACCOUNT_ID))
        .thenReturn(true);
    ngAccountSetupService.setupAccountForNG(ACCOUNT_ID, Edition.FREE, PricingType.CLASSIC);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(ngUserService, times(3)).addUserToScope(captor.capture(), any(), any(), any(), any(), any());

    List<String> users = captor.getAllValues();
    assertThat(users.size()).isEqualTo(3);
    assertThat(users.contains(FIRST_ADMIN_USER)).isTrue();
    assertThat(users.contains(SECOND_ADMIN_USER)).isTrue();
    assertThat(users.contains(NON_ADMIN_USER)).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDefaultProjectIsEnabledAndOnlyCGAdminsMigratedToNG() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .orgIdentifier(DEFAULT_ORG_IDENTIFIER)
                              .uniqueId(orgUniqueId)
                              .build();
    when(scopeResolverService.getScopeInfo(ACCOUNT_ID, DEFAULT_ORG_IDENTIFIER, null)).thenReturn(scopeInfo);
    when(featureFlagService.isGlobalEnabled(FeatureName.CREATE_DEFAULT_PROJECT)).thenReturn(true);
    when(featureFlagService.isNotEnabled(FeatureName.PL_DO_NOT_MIGRATE_NON_ADMIN_CG_USERS_TO_NG, ACCOUNT_ID))
        .thenReturn(false);
    ngAccountSetupService.setupAccountForNG(ACCOUNT_ID, Edition.FREE, PricingType.CLASSIC);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(ngUserService, times(2)).addUserToScope(captor.capture(), any(), any(), any(), any(), any());

    List<String> users = captor.getAllValues();
    assertThat(users.size()).isEqualTo(2);
    assertThat(users.contains(FIRST_ADMIN_USER)).isTrue();
    assertThat(users.contains(SECOND_ADMIN_USER)).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDefaultProjectIsDisabledAndAllCGUsersMigratedToNG() {
    when(featureFlagService.isGlobalEnabled(FeatureName.CREATE_DEFAULT_PROJECT)).thenReturn(false);
    when(featureFlagService.isNotEnabled(FeatureName.PL_DO_NOT_MIGRATE_NON_ADMIN_CG_USERS_TO_NG, ACCOUNT_ID))
        .thenReturn(true);
    ngAccountSetupService.setupAccountForNG(ACCOUNT_ID, Edition.FREE, PricingType.CLASSIC);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(ngUserService, times(3)).addUserToScope(captor.capture(), any(), any(), any(), any(), any());

    List<String> users = captor.getAllValues();
    assertThat(users.size()).isEqualTo(3);
    assertThat(users.contains(FIRST_ADMIN_USER)).isTrue();
    assertThat(users.contains(SECOND_ADMIN_USER)).isTrue();
    assertThat(users.contains(NON_ADMIN_USER)).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDefaultProjectIsDisabledAndOnlyCGAdminsMigratedToNG() {
    when(featureFlagService.isGlobalEnabled(FeatureName.CREATE_DEFAULT_PROJECT)).thenReturn(false);
    when(featureFlagService.isNotEnabled(FeatureName.PL_DO_NOT_MIGRATE_NON_ADMIN_CG_USERS_TO_NG, ACCOUNT_ID))
        .thenReturn(false);
    ngAccountSetupService.setupAccountForNG(ACCOUNT_ID, Edition.FREE, PricingType.CLASSIC);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(ngUserService, times(2)).addUserToScope(captor.capture(), any(), any(), any(), any(), any());

    List<String> users = captor.getAllValues();
    assertThat(users.size()).isEqualTo(2);
    assertThat(users.contains(FIRST_ADMIN_USER)).isTrue();
    assertThat(users.contains(SECOND_ADMIN_USER)).isTrue();
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testCleanUpForNG() {
    when(featureFlagService.isGlobalEnabled(FeatureName.CREATE_DEFAULT_PROJECT)).thenReturn(false);
    when(featureFlagService.isNotEnabled(FeatureName.PL_DO_NOT_MIGRATE_NON_ADMIN_CG_USERS_TO_NG, ACCOUNT_ID))
        .thenReturn(true);
    ngAccountSetupService.setupAccountForNG(ACCOUNT_ID, Edition.FREE, PricingType.CLASSIC);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(ngUserService, times(3)).addUserToScope(captor.capture(), any(), any(), any(), any(), any());
    List<String> users = captor.getAllValues();
    assertThat(users.size()).isEqualTo(3);
    ngAccountSetupService.cleanUsersFromAccountForNg(ACCOUNT_ID);
    verify(ngUserService, times(1)).listUserIds(any(), any());
    verify(ngUserService, times(1)).listUsersHavingRole(any(), any(), any());
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testSyncWithCG() {
    when(featureFlagService.isNotEnabled(FeatureName.PL_DO_NOT_MIGRATE_NON_ADMIN_CG_USERS_TO_NG, ACCOUNT_ID))
        .thenReturn(true);
    ngAccountSetupService.setupAccountForNG(ACCOUNT_ID, Edition.FREE, PricingType.CLASSIC);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(ngUserService, times(3)).addUserToScope(captor.capture(), any(), any(), any(), any(), any());
    List<String> users = captor.getAllValues();
    assertThat(users.size()).isEqualTo(3);
    assertThat(users.contains(FIRST_ADMIN_USER)).isTrue();
    assertThat(users.contains(SECOND_ADMIN_USER)).isTrue();
    assertThat(users.contains(NON_ADMIN_USER)).isTrue();
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void testSyncWithCGAdminOnly() {
    when(featureFlagService.isNotEnabled(FeatureName.PL_DO_NOT_MIGRATE_NON_ADMIN_CG_USERS_TO_NG, ACCOUNT_ID))
        .thenReturn(false);
    ngAccountSetupService.setupAccountForNG(ACCOUNT_ID, Edition.FREE, PricingType.CLASSIC);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(ngUserService, times(2)).addUserToScope(captor.capture(), any(), any(), any(), any(), any());
    List<String> users = captor.getAllValues();
    assertThat(users.size()).isEqualTo(2);
    assertThat(users.contains(FIRST_ADMIN_USER)).isTrue();
    assertThat(users.contains(SECOND_ADMIN_USER)).isTrue();
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testStartFlexLicense_enabledWithModules() {
    Map<Edition, String> editionModuleConfig = new HashMap<>();
    editionModuleConfig.put(Edition.ENTERPRISE, "CD,CI");
    AutoProvisionLicenseConfig config = new AutoProvisionLicenseConfig(true, editionModuleConfig);
    NGAccountSetupService service = new NGAccountSetupService(organizationService, accessControlAdminClient,
        ngUserService, userClient, harnessSMManager, ciDefaultEntityManager, nextGenConfiguration,
        accountSettingService, projectService, featureFlagService, sampleManifestFileService, defaultUserGroupService,
        scopeResolverService, harnessCodeService, licenseService, config, harnessLLMConnectorService);

    service.setupAccountForNG(ACCOUNT_ID, Edition.ENTERPRISE, PricingType.FLEX);

    verify(licenseService)
        .startFlexLicense(eq(ACCOUNT_ID), eq(Edition.ENTERPRISE), eq(List.of(ModuleType.CD, ModuleType.CI)));
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testStartFlexLicense_enabledButNoModulesForEdition() {
    Map<Edition, String> editionModuleConfig = new HashMap<>();
    editionModuleConfig.put(Edition.FREE, "CD,CI");
    AutoProvisionLicenseConfig config = new AutoProvisionLicenseConfig(true, editionModuleConfig);
    NGAccountSetupService service = new NGAccountSetupService(organizationService, accessControlAdminClient,
        ngUserService, userClient, harnessSMManager, ciDefaultEntityManager, nextGenConfiguration,
        accountSettingService, projectService, featureFlagService, sampleManifestFileService, defaultUserGroupService,
        scopeResolverService, harnessCodeService, licenseService, config, harnessLLMConnectorService);

    service.setupAccountForNG(ACCOUNT_ID, Edition.ENTERPRISE, PricingType.FLEX);

    verify(licenseService, never()).startFlexLicense(any(), any(), any());
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testStartFlexLicense_disabled() {
    Map<Edition, String> editionModuleConfig = new HashMap<>();
    editionModuleConfig.put(Edition.ENTERPRISE, "CD,CI");
    AutoProvisionLicenseConfig config = new AutoProvisionLicenseConfig(false, editionModuleConfig);
    NGAccountSetupService service = new NGAccountSetupService(organizationService, accessControlAdminClient,
        ngUserService, userClient, harnessSMManager, ciDefaultEntityManager, nextGenConfiguration,
        accountSettingService, projectService, featureFlagService, sampleManifestFileService, defaultUserGroupService,
        scopeResolverService, harnessCodeService, licenseService, config, harnessLLMConnectorService);

    service.setupAccountForNG(ACCOUNT_ID, Edition.ENTERPRISE, PricingType.FLEX);

    verify(licenseService, never()).startFlexLicense(any(), any(), any());
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testStartFlexLicense_nonFlexPricingType() {
    Map<Edition, String> editionModuleConfig = new HashMap<>();
    editionModuleConfig.put(Edition.ENTERPRISE, "CD,CI");
    AutoProvisionLicenseConfig config = new AutoProvisionLicenseConfig(true, editionModuleConfig);
    NGAccountSetupService service = new NGAccountSetupService(organizationService, accessControlAdminClient,
        ngUserService, userClient, harnessSMManager, ciDefaultEntityManager, nextGenConfiguration,
        accountSettingService, projectService, featureFlagService, sampleManifestFileService, defaultUserGroupService,
        scopeResolverService, harnessCodeService, licenseService, config, harnessLLMConnectorService);

    service.setupAccountForNG(ACCOUNT_ID, Edition.ENTERPRISE, PricingType.CLASSIC);

    verify(licenseService, never()).startFlexLicense(any(), any(), any());
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testStartFlexLicense_nullConfig() {
    NGAccountSetupService service = new NGAccountSetupService(organizationService, accessControlAdminClient,
        ngUserService, userClient, harnessSMManager, ciDefaultEntityManager, nextGenConfiguration,
        accountSettingService, projectService, featureFlagService, sampleManifestFileService, defaultUserGroupService,
        scopeResolverService, harnessCodeService, licenseService, null, harnessLLMConnectorService);

    service.setupAccountForNG(ACCOUNT_ID, Edition.ENTERPRISE, PricingType.FLEX);

    verify(licenseService, never()).startFlexLicense(any(), any(), any());
  }
}
