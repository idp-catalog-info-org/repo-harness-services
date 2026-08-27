/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate;

import static io.harness.NGCommonEntityConstants.APPLICATION_GZIP_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.CONTENT_TYPE_HEADER;
import static io.harness.idp.common.Constants.AUTH_GITHUB_CLIENT_SECRET;
import static io.harness.idp.common.Constants.AUTH_GOOGLE_CLIENT_SECRET;
import static io.harness.idp.common.Constants.IDP_PLUGIN_ORIGIN_HEADER;
import static io.harness.idp.common.Constants.SCAFFOLDER_ACTION;
import static io.harness.rule.OwnerRule.ARYA;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.idp.http.request.IdpHttpTaskParams;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.configmanager.entities.PluginsProxyInfoEntity;
import io.harness.idp.configmanager.repositories.PluginsProxyInfoRepository;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.service.intfc.FileService;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DelegateProxyRequestForwarderTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String REQUEST_URL =
      "https://api.github.com/repos/harness/harness-core/contents/idp-service/.sample-catalog-entities/three.yaml";
  private static final String GITHUB_REQUEST_URL_TECH_DOCS =
      "https://api.github.com/repos/backstage/backstage/tarball/a4cfe9d6f4d063bdb8d4e96a3470cf6dcae3e82b";
  private static final String REQUEST_BODY =
      "{\"url\":\"https://github.com/harness/harness-core/blob/develop/idp-service/.sample-catalog-entities/"
      + "three.yaml\",\"method\":\"GET\",\"headers\":{\"Accept\":\"application/"
      + "vnd.github.v3.raw\",\"User-Agent\":\"node-fetch/1.0 "
      + "(+https://github.com/bitinn/node-fetch)\",\"Accept-Encoding\":\"gzip,deflate\",\"Connection\":\"close\"}}";
  private static final String REQUEST_METHOD = "GET";
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock GitIntegrationServiceImpl gitIntegrationService;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock BackstageEnvVariableService backstageEnvVariableService;
  @Mock FileService fileService;
  @Mock PluginsProxyInfoRepository pluginsProxyInfoRepository;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @InjectMocks DelegateProxyRequestForwarder delegateProxyRequestForwarder;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateHeaderConfig() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    headers.put("Authorization", "Bearer token123");

    List<HttpHeaderConfig> headerList = delegateProxyRequestForwarder.createHeaderConfig(headers);

    assertEquals(3, headerList.size());
    assertEquals("Authorization", headerList.get(0).getKey());
    assertEquals("Bearer token123", headerList.get(0).getValue());
    assertEquals("Accept", headerList.get(1).getKey());
    assertEquals("application/json", headerList.get(1).getValue());
    assertEquals("Content-Type", headerList.get(2).getKey());
    assertEquals("application/json", headerList.get(2).getValue());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateHeaderConfigWithInvalidHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Length", "100");
    headers.put("Host", "example.com");
    headers.put("Connection", "keep-alive");

    List<HttpHeaderConfig> headerList = delegateProxyRequestForwarder.createHeaderConfig(headers);

    assertEquals(0, headerList.size());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegate() {
    String testResponse = "Test Response";
    DelegateResponseData expectedResponse = HttpStepResponse.builder().httpResponseBody(testResponse).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(expectedResponse);
    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(
        ACCOUNT_IDENTIFIER, REQUEST_URL, new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(), null, null);

    assertEquals(expectedResponse, actualResponse);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateGithubTechDocsGcsResponseStore() {
    String fileId = "fileId";
    DelegateResponseData delegateResponseData =
        HttpStepResponse.builder()
            .header(CONTENT_TYPE_HEADER + ":" + APPLICATION_GZIP_MEDIA_TYPE + ";")
            .httpResponseBodyInBytes(fileId.getBytes(StandardCharsets.UTF_8))
            .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(delegateResponseData);
    doNothing().when(fileService).downloadToStream(fileId, new ByteArrayOutputStream(), FileBucket.FILE_STORE);
    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(ACCOUNT_IDENTIFIER,
        GITHUB_REQUEST_URL_TECH_DOCS, new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(), null, null);

    assertEquals(delegateResponseData, actualResponse);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateGithubOAuth() {
    String testResponse = "Test Response";
    DelegateResponseData expectedResponse = HttpStepResponse.builder().httpResponseBody(testResponse).build();
    when(backstageEnvVariableService.findByEnvNameAndAccountIdentifier(AUTH_GITHUB_CLIENT_SECRET, ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(new BackstageEnvSecretVariable().type(BackstageEnvVariable.TypeEnum.SECRET)));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(expectedResponse);
    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(ACCOUNT_IDENTIFIER,
        "https://github.com/login/oauth/access_token", new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(),
        null, null);

    assertEquals(expectedResponse, actualResponse);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateGoogleOAuth() {
    String testResponse = "Test Response";
    DelegateResponseData expectedResponse = HttpStepResponse.builder().httpResponseBody(testResponse).build();
    when(backstageEnvVariableService.findByEnvNameAndAccountIdentifier(AUTH_GOOGLE_CLIENT_SECRET, ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(new BackstageEnvSecretVariable().type(BackstageEnvVariable.TypeEnum.SECRET)));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(expectedResponse);
    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(ACCOUNT_IDENTIFIER,
        "https://googleapis.com/oauth2/v4/token", new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(),
        null, null);

    assertEquals(expectedResponse, actualResponse);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateGithubOAuthUserCallSkipsGitAuth() {
    // github.com's flat profile URL: https://api.github.com/user
    String testResponse = "Test Response";
    DelegateResponseData expectedResponse = HttpStepResponse.builder().httpResponseBody(testResponse).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(expectedResponse);

    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(ACCOUNT_IDENTIFIER,
        "https://api.github.com/user", new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(), null, null);

    assertEquals(expectedResponse, actualResponse);
    verify(gitIntegrationService, never()).getAuthenticationDetailsForDelegateTask(any(), any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(captor.capture());
    IdpHttpTaskParams params = (IdpHttpTaskParams) captor.getValue().getTaskParameters();
    assertNull(params.getAuthentication());
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateGhesOAuthUserCallSkipsGitAuth() {
    // GitHub Enterprise Server's /api/v3-prefixed profile URL. Regression test for IDP-10755: this call used to
    // fall through to gitIntegrationService.getAuthenticationDetailsForDelegateTask and get the account's Git
    // Integration credentials substituted in place of the caller's real user token.
    String testResponse = "Test Response";
    DelegateResponseData expectedResponse = HttpStepResponse.builder().httpResponseBody(testResponse).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(expectedResponse);

    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(ACCOUNT_IDENTIFIER,
        "https://ghes.example.com/api/v3/user", new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(), null,
        null);

    assertEquals(expectedResponse, actualResponse);
    verify(gitIntegrationService, never()).getAuthenticationDetailsForDelegateTask(any(), any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(captor.capture());
    IdpHttpTaskParams params = (IdpHttpTaskParams) captor.getValue().getTaskParameters();
    assertNull(params.getAuthentication());
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateGhesNonUserCallStillUsesGitAuth() {
    // Sanity control: a GHES call that isn't the profile fetch (e.g. content/PR access) must still go through
    // the normal Git Integration credential resolution. The widened regex must not become overly permissive.
    String testResponse = "Test Response";
    DelegateResponseData expectedResponse = HttpStepResponse.builder().httpResponseBody(testResponse).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(expectedResponse);

    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(ACCOUNT_IDENTIFIER,
        "https://ghes.example.com/api/v3/repos/org/repo/contents/file.yaml", new ArrayList<>(), REQUEST_BODY,
        REQUEST_METHOD, new HashSet<>(), null, null);

    assertEquals(expectedResponse, actualResponse);
    verify(gitIntegrationService)
        .getAuthenticationDetailsForDelegateTask(eq(ACCOUNT_IDENTIFIER),
            eq("https://ghes.example.com/api/v3/repos/org/repo/contents/file.yaml"), any(), any());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateScaffolderActionSkipsGitAuth() {
    String ngManagerDecryptUrl =
        "https://qa.harness.io/gateway/ng-manager/v2/secrets/nonhsmidpapp/decrypt?accountIdentifier=accountId";
    List<HttpHeaderConfig> headers = new ArrayList<>();
    headers.add(HttpHeaderConfig.builder().key(IDP_PLUGIN_ORIGIN_HEADER).value(SCAFFOLDER_ACTION).build());
    DelegateResponseData expectedResponse = HttpStepResponse.builder().httpResponseBody("ok").build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(expectedResponse);

    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(
        ACCOUNT_IDENTIFIER, ngManagerDecryptUrl, headers, REQUEST_BODY, REQUEST_METHOD, new HashSet<>(), null, null);

    assertEquals(expectedResponse, actualResponse);
    verify(gitIntegrationService, never()).getAuthenticationDetailsForDelegateTask(any(), any(), any(), any());

    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(captor.capture());
    IdpHttpTaskParams params = (IdpHttpTaskParams) captor.getValue().getTaskParameters();
    assertNull(params.getAuthentication());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateWithError() {
    String testResponse = "Could not get response";
    DelegateResponseData expectedResponse = ErrorNotifyResponseData.builder().errorMessage(testResponse).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(expectedResponse);
    HttpStepResponse actualResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(
        ACCOUNT_IDENTIFIER, REQUEST_URL, new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(), null, null);

    assertNull(actualResponse);
  }

  @Test(expected = Exception.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testForwardRequestToDelegateWhenException() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenThrow(new RuntimeException());
    delegateProxyRequestForwarder.forwardRequestToDelegate(
        ACCOUNT_IDENTIFIER, REQUEST_URL, new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(), null, null);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testDeriveCapabilityUrlDropsPathAndQuery() {
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndHost(ACCOUNT_IDENTIFIER, "svc.example.com"))
        .thenReturn(List.of(proxyEntity("svc.example.com", true, "/health")));

    String capabilityUrl = delegateProxyRequestForwarder.deriveCapabilityUrl(
        ACCOUNT_IDENTIFIER, "https://svc.example.com/api/vm/get_power_state?VM=vm-1&vCenterServer=vc-1");

    assertEquals("https://svc.example.com/health", capabilityUrl);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testDeriveCapabilityUrlAddsLeadingSlash() {
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndHost(ACCOUNT_IDENTIFIER, "svc.example.com"))
        .thenReturn(List.of(proxyEntity("svc.example.com", true, "health")));

    String capabilityUrl =
        delegateProxyRequestForwarder.deriveCapabilityUrl(ACCOUNT_IDENTIFIER, "https://svc.example.com/api/op");

    assertEquals("https://svc.example.com/health", capabilityUrl);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testDeriveCapabilityUrlPreservesPort() {
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndHost(ACCOUNT_IDENTIFIER, "svc.example.com"))
        .thenReturn(List.of(proxyEntity("svc.example.com", true, "/health")));

    String capabilityUrl =
        delegateProxyRequestForwarder.deriveCapabilityUrl(ACCOUNT_IDENTIFIER, "https://svc.example.com:8443/api/op");

    assertEquals("https://svc.example.com:8443/health", capabilityUrl);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testDeriveCapabilityUrlNormalizesApiHostForLookupButKeepsRealHost() {
    // Lookup strips the api. prefix to match the configured host, but the capability still targets the real host.
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndHost(ACCOUNT_IDENTIFIER, "github.com"))
        .thenReturn(List.of(proxyEntity("github.com", true, "/health")));

    String capabilityUrl =
        delegateProxyRequestForwarder.deriveCapabilityUrl(ACCOUNT_IDENTIFIER, "https://api.github.com/repos/x");

    assertEquals("https://api.github.com/health", capabilityUrl);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testDeriveCapabilityUrlNullWhenNoConfigForHost() {
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndHost(ACCOUNT_IDENTIFIER, "svc.example.com"))
        .thenReturn(List.of());

    String capabilityUrl =
        delegateProxyRequestForwarder.deriveCapabilityUrl(ACCOUNT_IDENTIFIER, "https://svc.example.com/api/op");

    assertNull(capabilityUrl);
    verify(pluginsProxyInfoRepository).findAllByAccountIdentifierAndHost(ACCOUNT_IDENTIFIER, "svc.example.com");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testDeriveCapabilityUrlNullWhenHealthPathNotConfigured() {
    // Most common case: host is a proxy host but has no healthCheckPath -> fall back to the task URL (today's
    // behavior).
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndHost(ACCOUNT_IDENTIFIER, "svc.example.com"))
        .thenReturn(List.of(proxyEntity("svc.example.com", true, null)));

    String capabilityUrl =
        delegateProxyRequestForwarder.deriveCapabilityUrl(ACCOUNT_IDENTIFIER, "https://svc.example.com/api/op");

    assertNull(capabilityUrl);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testDeriveCapabilityUrlNullWhenProxyDisabled() {
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndHost(ACCOUNT_IDENTIFIER, "svc.example.com"))
        .thenReturn(List.of(proxyEntity("svc.example.com", false, "/health")));

    String capabilityUrl =
        delegateProxyRequestForwarder.deriveCapabilityUrl(ACCOUNT_IDENTIFIER, "https://svc.example.com/api/op");

    assertNull(capabilityUrl);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testDeriveCapabilityUrlNullWhenHealthPathCarriesScheme() {
    // A healthCheckPath that looks like a full URL must be rejected so it cannot repoint the host.
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndHost(ACCOUNT_IDENTIFIER, "svc.example.com"))
        .thenReturn(List.of(proxyEntity("svc.example.com", true, "http://evil.example.com/health")));

    String capabilityUrl =
        delegateProxyRequestForwarder.deriveCapabilityUrl(ACCOUNT_IDENTIFIER, "https://svc.example.com/api/op");

    assertNull(capabilityUrl);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testProxyTaskEnforcesResponseCodeWhenHealthCheckPathConfigured() {
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_IDENTIFIER, FeatureName.IDP_ENABLE_HEALTHCHECK_FOR_PROXY))
        .thenReturn(true);
    when(pluginsProxyInfoRepository.findAllByAccountIdentifierAndHost(ACCOUNT_IDENTIFIER, "svc.example.com"))
        .thenReturn(List.of(proxyEntity("svc.example.com", true, "/health")));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(HttpStepResponse.builder().build());

    delegateProxyRequestForwarder.forwardRequestToDelegate(ACCOUNT_IDENTIFIER, "https://svc.example.com/api/op",
        new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(), null, null);

    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(captor.capture());
    IdpHttpTaskParams params = (IdpHttpTaskParams) captor.getValue().getTaskParameters();
    assertEquals("https://svc.example.com/health", params.getCapabilityUrl());
    // healthCheckPath is a real liveness endpoint -> the capability check enforces its response code.
    assertFalse(params.isIgnoreResponseCode());
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testProxyTaskIgnoresResponseCodeWhenNoHealthCheckPath() {
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_IDENTIFIER, FeatureName.IDP_ENABLE_HEALTHCHECK_FOR_PROXY))
        .thenReturn(true);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(HttpStepResponse.builder().build());

    delegateProxyRequestForwarder.forwardRequestToDelegate(ACCOUNT_IDENTIFIER, "https://svc.example.com/api/op",
        new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(), null, null);

    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(captor.capture());
    IdpHttpTaskParams params = (IdpHttpTaskParams) captor.getValue().getTaskParameters();
    // FF on but no healthCheckPath -> capability falls back to the task URL and keeps today's ignore behavior.
    assertNull(params.getCapabilityUrl());
    assertTrue(params.isIgnoreResponseCode());
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void testProxyTaskFeatureFlagDisabledKeepsTodayBehavior() {
    // FF off: even with a configured healthCheckPath, the capability stays on the task URL with
    // isIgnoreResponseCode=true (develop behavior). The proxy config is never even consulted.
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(HttpStepResponse.builder().build());

    delegateProxyRequestForwarder.forwardRequestToDelegate(ACCOUNT_IDENTIFIER, "https://svc.example.com/api/op",
        new ArrayList<>(), REQUEST_BODY, REQUEST_METHOD, new HashSet<>(), null, null);

    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(captor.capture());
    IdpHttpTaskParams params = (IdpHttpTaskParams) captor.getValue().getTaskParameters();
    assertNull(params.getCapabilityUrl());
    assertTrue(params.isIgnoreResponseCode());
    verify(pluginsProxyInfoRepository, never()).findAllByAccountIdentifierAndHost(any(), any());
  }

  private PluginsProxyInfoEntity proxyEntity(String host, boolean proxy, String healthCheckPath) {
    return PluginsProxyInfoEntity.builder().host(host).proxy(proxy).healthCheckPath(healthCheckPath).build();
  }
}
