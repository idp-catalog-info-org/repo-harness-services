/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.provision.service;

import static io.harness.idp.provision.service.ProvisionServiceImpl.ERROR_MESSAGE;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.PipelineTriggerUtils;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.mappers.NamespaceMapper;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.licensing.beans.summary.dto.CodeLicenseSummaryDTO;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ngmanager.NgConnectorManagerClient;
import io.harness.reflection.ReflectionUtils;
import io.harness.remote.client.CGRestUtils;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import okhttp3.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.IDP)
public class ProvisionServiceImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Spy @InjectMocks private ProvisionServiceImpl provisionServiceImpl;
  @Spy @InjectMocks private IdpCommonService idpCommonService;
  @Mock NgConnectorManagerClient ngConnectorManagerClient;
  @Mock BackstagePermissionsService backstagePermissionsService;
  @Mock SecretManagerClientService ngSecretService;
  @Mock BackstageEnvVariableService backstageEnvVariableService;
  @Mock ConfigManagerService configManagerService;
  @Mock private ProvisionModuleConfig provisionModuleConfig;
  @Mock private NamespaceService namespaceService;
  @Mock private OkHttpClient client;
  @Mock private Call call;
  @Mock NgLicenseHttpClient ngLicenseHttpClient;
  @Mock GitIntegrationServiceImpl gitIntegrationService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) AccountClient accountClient;
  private static final String ADMIN_USER_ID = "lv0euRhKRCyiXWzS7pOg6g";
  private static final String DEFAULT_USER_ID = "0osgWsTZRsSZ8RWfjLRkEg";
  private static final String ACCOUNT_ID = "123";
  private static final String NAMESPACE = "8982311";
  static final List<String> TEST_USERGROUP = List.of(" ");
  static final List<String> TEST_PERMISSIONS =
      List.of("user_read", "user_update", "user_delete", "owner_read", "owner_update", "owner_delete", "all_create");

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckUserAuthorization() {
    MockedStatic<SecurityContextBuilder> mockSecurityContext = Mockito.mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", ACCOUNT_ID));
    MockedStatic<CGRestUtils> mockRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);
    idpCommonService.checkUserAuthorization();
    verify(ngConnectorManagerClient, times(1)).isHarnessSupportUser(ADMIN_USER_ID);
    mockSecurityContext.close();
    mockRestUtils.close();
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = SARTHAK_KASAT)
  public void testCreateDefaultBackstagePermissions() {
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissions.setUserGroups(TEST_USERGROUP);
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    backstagePermissions.setUserGroup(TEST_USERGROUP.get(0));
    backstagePermissions.setUserGroups(Collections.emptyList());
    provisionServiceImpl.createDefaultPermissions(ACCOUNT_ID);
    verify(backstagePermissionsService).createPermissions(backstagePermissions, ACCOUNT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Category(UnitTests.class)
  @Owner(developers = VIGNESWARA)
  public void testCreateDefaultBackstagePermissionsThrowsException() {
    when(backstagePermissionsService.createPermissions(any(), any()))
        .thenThrow(new InvalidRequestException("Creating permission failed"));
    provisionServiceImpl.createDefaultPermissions(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCheckUserAuthorizationThrowsException() {
    MockedStatic<SecurityContextBuilder> mockSecurityContext = Mockito.mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(DEFAULT_USER_ID, "default@harness.io", "default", ACCOUNT_ID));
    MockedStatic<CGRestUtils> mockRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);
    try {
      idpCommonService.checkUserAuthorization();
    } catch (Exception e) {
      String expectedMessage = String.format("User : %s not allowed to do action on IDP module", DEFAULT_USER_ID);
      Assert.assertEquals(expectedMessage, e.getMessage());
    }
    verify(ngConnectorManagerClient, times(1)).isHarnessSupportUser(DEFAULT_USER_ID);
    mockSecurityContext.close();
    mockRestUtils.close();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateBackstageBackendSecret() {
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any())).thenReturn(dto);
    provisionServiceImpl.createBackstageBackendSecret(ACCOUNT_ID);
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(Constants.BACKEND_SECRET);
    backstageEnvSecretVariable.setHarnessSecretIdentifier(dto.getSecret().getIdentifier());
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    verify(backstageEnvVariableService).create(backstageEnvSecretVariable, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testUpdateBackstageBackendSecret() {
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any()))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE));
    when(ngSecretService.updateSecret(eq(Constants.IDP_BACKEND_SECRET), eq(ACCOUNT_ID), eq(null), eq(null), any()))
        .thenReturn(dto);
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(Constants.BACKEND_SECRET);
    backstageEnvSecretVariable.setHarnessSecretIdentifier(dto.getSecret().getIdentifier());
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    when(backstageEnvVariableService.create(backstageEnvSecretVariable, ACCOUNT_ID))
        .thenThrow(new DuplicateKeyException(""));
    provisionServiceImpl.createBackstageBackendSecret(ACCOUNT_ID);
    verify(backstageEnvVariableService).update(backstageEnvSecretVariable, ACCOUNT_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateBackstageBackendSecretThrowsException() {
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any())).thenReturn(dto);
    when(backstageEnvVariableService.create(any(), any()))
        .thenThrow(new InvalidRequestException("Unexpected Error occurred"));
    provisionServiceImpl.createBackstageBackendSecret(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProvision() throws Exception {
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    Request request = new Request.Builder().url("https://harness.trigger.com").method("GET", null).build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .message("success")
            .request(request)
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create(MediaType.parse("application/json"), "Response from Trigger"))
            .build();
    retrofit2.Call<ResponseDTO<LicensesWithSummaryDTO>> booleanResult = mock(retrofit2.Call.class);
    when(booleanResult.execute())
        .thenReturn(retrofit2.Response.success(
            ResponseDTO.newResponse(CodeLicenseSummaryDTO.builder().maxExpiryTime(Long.MAX_VALUE).build())));
    when(ngLicenseHttpClient.getLicenseSummary(ACCOUNT_ID, ModuleType.CODE.name())).thenReturn(booleanResult);
    doNothing()
        .when(gitIntegrationService)
        .setupDefaultConnectorLessManagedHarnessCodeRepoIntegrationIfNotAlready(ACCOUNT_ID);
    NamespaceEntity namespaceEntity = NamespaceEntity.builder().accountIdentifier(ACCOUNT_ID).id(NAMESPACE).build();
    when(namespaceService.saveAccountIdNamespace(ACCOUNT_ID)).thenReturn(namespaceEntity);
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any())).thenReturn(dto);
    when(backstageEnvVariableService.create(any(), any())).thenReturn(new BackstageEnvVariable());
    when(backstagePermissionsService.createPermissions(any(), any())).thenReturn(new BackstagePermissions());
    doNothing().when(configManagerService).mergeAndUpdateConfigInNamespace(any(), any());
    when(provisionModuleConfig.getTriggerPipelineUrl()).thenReturn("https://harness.trigger.com");
    AccountDTO accountDTO = AccountDTO.builder().identifier(ACCOUNT_ID).subdomainURL("https://test.vanit.com").build();
    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(retrofit2.Response.success(new RestResponse(accountDTO)));
    when(idpCommonService.getAccountDTO(ACCOUNT_ID)).thenReturn(accountDTO);
    MockedStatic<PipelineTriggerUtils> mockRestUtils = Mockito.mockStatic(PipelineTriggerUtils.class);
    provisionServiceImpl.provision(ACCOUNT_ID);
    verify(backstagePermissionsService, times(1)).createPermissions(any(), any());
    mockRestUtils.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProvisionWithNamespaceExists() throws Exception {
    NamespaceEntity namespaceEntity = NamespaceEntity.builder().accountIdentifier(ACCOUNT_ID).id(NAMESPACE).build();
    when(namespaceService.saveAccountIdNamespace(ACCOUNT_ID)).thenThrow(DuplicateKeyException.class);
    when(namespaceService.getNamespaceForAccountIdentifier(ACCOUNT_ID))
        .thenReturn(NamespaceMapper.toDTO(namespaceEntity));
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any())).thenReturn(dto);
    retrofit2.Call<ResponseDTO<LicensesWithSummaryDTO>> booleanResult = mock(retrofit2.Call.class);
    when(booleanResult.execute())
        .thenReturn(retrofit2.Response.success(
            ResponseDTO.newResponse(CodeLicenseSummaryDTO.builder().maxExpiryTime(Long.MAX_VALUE).build())));
    when(ngLicenseHttpClient.getLicenseSummary(ACCOUNT_ID, ModuleType.CODE.name())).thenReturn(booleanResult);
    doNothing()
        .when(gitIntegrationService)
        .setupDefaultConnectorLessManagedHarnessCodeRepoIntegrationIfNotAlready(ACCOUNT_ID);
    when(backstageEnvVariableService.create(any(), any())).thenReturn(new BackstageEnvVariable());
    when(backstagePermissionsService.createPermissions(any(), any())).thenReturn(new BackstagePermissions());
    doNothing().when(configManagerService).mergeAndUpdateConfigInNamespace(any(), any());
    when(provisionModuleConfig.getTriggerPipelineUrl()).thenReturn("https://harness.trigger.com");
    MockedStatic<PipelineTriggerUtils> mockRestUtils = Mockito.mockStatic(PipelineTriggerUtils.class);
    AccountDTO accountDTO = AccountDTO.builder().identifier(ACCOUNT_ID).subdomainURL("https://test.vanit.com").build();
    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(retrofit2.Response.success(new RestResponse(accountDTO)));
    when(idpCommonService.getAccountDTO(ACCOUNT_ID)).thenReturn(accountDTO);
    provisionServiceImpl.provision(ACCOUNT_ID);
    verify(backstagePermissionsService, times(1)).createPermissions(any(), any());
    mockRestUtils.close();
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testTriggerPipelineThrowsIOException() throws Exception {
    Field base = ReflectionUtils.getFieldByName(provisionServiceImpl.getClass(), "xApiKey");
    base.setAccessible(true);
    base.set(provisionServiceImpl, "token");
    SecretResponseWrapper dto = SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().identifier(Constants.IDP_BACKEND_SECRET).build())
                                    .build();
    when(ngSecretService.create(eq(ACCOUNT_ID), eq(null), eq(null), eq(true), any())).thenReturn(dto);
    when(backstageEnvVariableService.create(any(), any())).thenReturn(new BackstageEnvVariable());
    when(backstagePermissionsService.createPermissions(any(), any())).thenThrow(new DuplicateKeyException(""));
    doNothing().when(configManagerService).mergeAndUpdateConfigInNamespace(any(), any());
    when(provisionModuleConfig.getTriggerPipelineUrl()).thenReturn("https://harness.trigger.com");
    AccountDTO accountDTO = AccountDTO.builder().identifier(ACCOUNT_ID).subdomainURL("https://test.vanit.com").build();
    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(retrofit2.Response.success(new RestResponse(accountDTO)));
    when(idpCommonService.getAccountDTO(ACCOUNT_ID)).thenReturn(accountDTO);
    when(client.newCall(any())).thenReturn(call);
    when(call.execute()).thenThrow(IOException.class);
    provisionServiceImpl.triggerPipelineAndCreatePermissions(ACCOUNT_ID, NAMESPACE);
  }
}
