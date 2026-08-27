/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.delegate;

import static io.harness.NGCommonEntityConstants.APPLICATION_GZIP_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_OCTET_STREAM_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_TAR_GZ_STREAM_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.CONTENT_TYPE_HEADER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.common.CommonUtils.addAccountScopeInIdentifier;
import static io.harness.idp.common.CommonUtils.urlObject;
import static io.harness.idp.common.Constants.AUTH_ATLASSIAN_CLIENT_SECRET;
import static io.harness.idp.common.Constants.AUTH_GITHUB_CLIENT_SECRET;
import static io.harness.idp.common.Constants.AUTH_GOOGLE_CLIENT_SECRET;
import static io.harness.idp.common.Constants.HARNESS_PROXY;
import static io.harness.idp.common.Constants.IDP_PLUGIN_ORIGIN_HEADER;
import static io.harness.idp.common.Constants.JENKINS_PLUGIN;
import static io.harness.idp.common.Constants.SCAFFOLDER_ACTION;
import static io.harness.idp.common.Constants.SONARQUBE_PLUGIN;
import static io.harness.idp.common.YamlUtils.loadYamlStringAsMap;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.connector.SonarQubeConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.beans.connector.jira.JiraPATDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.idp.http.request.IdpHttpTaskParams;
import io.harness.encryption.SecretRefData;
import io.harness.exception.UnexpectedException;
import io.harness.expression.common.ExpressionMode;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.entities.PluginsProxyInfoEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.repositories.PluginsProxyInfoRepository;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.proxy.delegate.expression.IdpHttpExpressionEvaluator;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.TaskType;
import software.wings.service.intfc.FileService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class DelegateProxyRequestForwarder {
  private static final long EXECUTION_TIMEOUT_IN_SECONDS = 60;
  private static final int SOCKET_TIMEOUT_IN_MILLISECONDS = 20000;
  private static final List<String> TECH_DOCS_REQUEST_IDENTIFIER = List.of("/+/archive/", "/repository/archive?",
      "/tarball/", "/archive?format=tgz&at=", ".tar.gz", "?recursionLevel=full&download=true");
  private static final Pattern GITHUB_OAUTH_CALL_IDENTIFIER =
      Pattern.compile("https://([a-zA-Z0-9.-]+)/login/oauth/access_token");
  private static final Pattern GOOGLE_OAUTH_CALL_IDENTIFIER =
      Pattern.compile("https://([a-zA-Z0-9.-]+)/oauth2/v4/token");
  private static final Pattern GITHUB_OAUTH_USER_CALL_IDENTIFIER =
      Pattern.compile("https://([a-zA-Z0-9.-]+)(/api/v3)?/user$");
  private static final String ATLASSIAN_OAUTH_CALL_IDENTIFIER = "https://auth.atlassian.com/oauth/token";
  private static final Pattern SONARQUBE_PLUGIN_CALL_IDENTIFIER = Pattern.compile(
      "^(https?://)?[^/]+(/[^/]+)?/(api/metrics/search|api/components/show|api/measures/component)(\\?.*)?$");
  private static final Pattern JENKINS_PLUGIN_CALL_IDENTIFIER =
      Pattern.compile("(/[^/]+/api/json)|(/createItem)|(/[^/]+/createItem)|(/[^/]+/(doDelete|enable|disable))|(/[^/]+/"
          + "[^/]+/consoleText)");
  @Inject DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject @Named("PRIVILEGED") SecretManagerClientService secretManagerClientService;
  @Inject GitIntegrationServiceImpl gitIntegrationService;
  @Inject FileService fileService;
  @Inject BackstageEnvVariableService backstageEnvVariableService;
  @Inject AppConfigRepository appConfigRepository;
  @Inject PluginsProxyInfoRepository pluginsProxyInfoRepository;
  @Inject NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject @Named("gcsForTechDocsDelegate") boolean gcsForTechDocsDelegate;

  public List<HttpHeaderConfig> createHeaderConfig(Map<String, String> headers) {
    List<HttpHeaderConfig> headerList = new ArrayList<>();
    try {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        if (entry.getKey().equalsIgnoreCase("Content-Length") || entry.getKey().equalsIgnoreCase("host")
            || entry.getKey().equalsIgnoreCase("Connection")) {
          continue;
        }
        headerList.add(HttpHeaderConfig.builder().key(entry.getKey()).value(entry.getValue()).build());
      }
    } catch (Exception ex) {
      log.error("Error while mapping the headers", ex);
      throw ex;
    }

    return headerList;
  }

  public HttpStepResponse forwardRequestToDelegate(String accountIdentifier, String url,
      List<HttpHeaderConfig> headerList, String body, String methodType, Set<String> delegateSelectors, Object entity,
      Long executionTimeoutInSeconds) {
    Duration executionTimeout = executionTimeoutInSeconds == null ? Duration.ofSeconds(EXECUTION_TIMEOUT_IN_SECONDS)
                                                                  : Duration.ofSeconds(executionTimeoutInSeconds);
    int socketTimeoutInMilliseconds =
        executionTimeoutInSeconds == null ? SOCKET_TIMEOUT_IN_MILLISECONDS : (int) (executionTimeoutInSeconds * 1000);
    int integerHash = HashGenerator.generateIntegerHash();
    List<HttpHeaderConfig> resolvedHeadersList = resolveHeaders(headerList, integerHash);
    IdpHttpTaskParams.ResponseDataStore responseDataStore = null;
    if (TECH_DOCS_REQUEST_IDENTIFIER.stream().anyMatch(url::contains) && gcsForTechDocsDelegate) {
      responseDataStore = IdpHttpTaskParams.ResponseDataStore.GCS;
    }
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(accountIdentifier)
            .executionTimeout(executionTimeout)
            .taskType(TaskType.IDP_HTTP_TASK.name())
            .taskParameters(getTaskParams(accountIdentifier, url, methodType, resolvedHeadersList, body,
                responseDataStore, entity, socketTimeoutInMilliseconds))
            .expressionFunctorToken(integerHash)
            .taskSelectors(delegateSelectors)
            .taskDescription("IDP Proxy Http Task")
            .taskSetupAbstraction("ng", "true")
            .build();
    HttpStepResponse httpResponse = null;
    try {
      DelegateResponseData responseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
      if (responseData instanceof ErrorNotifyResponseData) {
        ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
        log.error("errorMessage: {}", errorNotifyResponseData.getErrorMessage());
      }
      if (responseData instanceof HttpStepResponse) {
        httpResponse = (HttpStepResponse) responseData;
        boolean responseBodyInBytes = (httpResponse.getHeader() != null)
            && (httpResponse.getHeader().equals(CONTENT_TYPE_HEADER + ":" + APPLICATION_GZIP_MEDIA_TYPE + ";")
                || httpResponse.getHeader().equals(
                    CONTENT_TYPE_HEADER + ":" + APPLICATION_OCTET_STREAM_MEDIA_TYPE + ";")
                || httpResponse.getHeader().equals(
                    CONTENT_TYPE_HEADER + ":" + APPLICATION_TAR_GZ_STREAM_MEDIA_TYPE + ";"));
        if ((httpResponse.getHttpResponseCode() == 302
                || (httpResponse.getHttpResponseCode() >= 200 && httpResponse.getHttpResponseCode() < 300))
            && (!isEmpty(httpResponse.getHttpResponseBody()) || !isEmpty(httpResponse.getHttpResponseBodyInBytes()))
            && IdpHttpTaskParams.ResponseDataStore.GCS.equals(responseDataStore)) {
          String fileId;
          if (responseBodyInBytes) {
            fileId = new String(httpResponse.getHttpResponseBodyInBytes(), StandardCharsets.UTF_8);
          } else {
            fileId = httpResponse.getHttpResponseBody();
          }
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          fileService.downloadToStream(fileId, outputStream, FileBucket.FILE_STORE);
          if (responseBodyInBytes) {
            httpResponse.setHttpResponseBodyInBytes(outputStream.toByteArray());
          } else {
            httpResponse.setHttpResponseBody(outputStream.toString(StandardCharsets.UTF_8));
          }
        }
        log.debug("httpResponse: {}", httpResponse);
        log.info("Delegate response status code: {}", httpResponse.getHttpResponseCode());
      }
    } catch (Exception ex) {
      log.error("Delegate error: ", ex);
      throw ex;
    }

    return httpResponse;
  }

  private List<HttpHeaderConfig> resolveHeaders(List<HttpHeaderConfig> headerList, int integerHash) {
    IdpHttpExpressionEvaluator evaluator = new IdpHttpExpressionEvaluator(integerHash);
    return (List) evaluator.resolve(headerList, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
  }

  private IdpHttpTaskParams getTaskParams(String accountIdentifier, String url, String methodType,
      List<HttpHeaderConfig> headers, String body, IdpHttpTaskParams.ResponseDataStore responseDataStore, Object entity,
      int socketTimeoutInMilliseconds) {
    DecryptableEntity authentication;
    if (checkIfProxyPlugin(headers) || checkScaffolderActionCall(headers)) {
      authentication = null;
    } else if (GITHUB_OAUTH_CALL_IDENTIFIER.matcher(url).find()) {
      authentication = githubOauthAuthentication(accountIdentifier);
      body = body.replaceAll("client_secret=[^&]*", "client_secret={IDP_HTTP_TASK_GITHUB_OAUTH_CLIENT_SECRET}");
    } else if (GOOGLE_OAUTH_CALL_IDENTIFIER.matcher(url).find()) {
      authentication = googleOauthAuthentication(accountIdentifier);
      body = body.replaceAll("client_secret=[^&]*", "client_secret={IDP_HTTP_TASK_GOOGLE_OAUTH_CLIENT_SECRET}");
    } else if (GITHUB_OAUTH_USER_CALL_IDENTIFIER.matcher(url).find()) {
      authentication = null;
    } else if (ATLASSIAN_OAUTH_CALL_IDENTIFIER.contains(url)) {
      authentication = atlassianOauthAuthentication(accountIdentifier);
      body = body.replaceAll("client_secret=[^&]*", "client_secret={IDP_HTTP_TASK_ATLASSIAN_OAUTH_CLIENT_SECRET}");
    } else if (SONARQUBE_PLUGIN_CALL_IDENTIFIER.matcher(url).find()) {
      authentication = sonarQubeAuthentication(accountIdentifier, url, headers);
    } else if (JENKINS_PLUGIN_CALL_IDENTIFIER.matcher(url).find()) {
      authentication = jenkinsAuthentication(accountIdentifier, url, headers);
    } else {
      authentication =
          gitIntegrationService.getAuthenticationDetailsForDelegateTask(accountIdentifier, url, headers, entity);
    }
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManagerClientService.getEncryptionDetails(ngAccess(accountIdentifier), authentication);
    String capabilityUrl =
        ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.IDP_ENABLE_HEALTHCHECK_FOR_PROXY)
        ? deriveCapabilityUrl(accountIdentifier, url)
        : null;
    return IdpHttpTaskParams.builder()
        .url(url)
        .method(methodType)
        .requestHeader(headers)
        .body(body)
        .socketTimeoutMillis(socketTimeoutInMilliseconds)
        .supportNonTextResponse(true)
        .isIgnoreResponseCode(isEmpty(capabilityUrl))
        .authentication(authentication)
        .encryptedDataDetails(encryptedDataDetails)
        .responseDataStore(responseDataStore)
        .capabilityUrl(capabilityUrl)
        .build();
  }

  @VisibleForTesting
  String deriveCapabilityUrl(String accountIdentifier, String url) {
    try {
      URL parsedUrl = urlObject(url);
      String normalizedPath = normalizeHealthCheckPath(getHealthCheckPathForHost(accountIdentifier, parsedUrl));
      if (normalizedPath == null) {
        return null;
      }
      StringBuilder base = new StringBuilder(parsedUrl.getProtocol()).append("://").append(parsedUrl.getHost());
      if (parsedUrl.getPort() != -1) {
        base.append(":").append(parsedUrl.getPort());
      }
      return base.append(normalizedPath).toString();
    } catch (Exception ex) {
      log.warn("Could not derive capability URL for proxy task; falling back to the task URL", ex);
      return null;
    }
  }

  private String getHealthCheckPathForHost(String accountIdentifier, URL parsedUrl) {
    String host = parsedUrl.getHost();
    if (host.startsWith("api.")) {
      host = host.replaceFirst("^api\\.", "");
    }
    List<PluginsProxyInfoEntity> proxies =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndHost(accountIdentifier, host);
    if (isEmpty(proxies)) {
      return null;
    }
    return proxies.stream()
        .filter(proxy -> Boolean.TRUE.equals(proxy.getProxy()))
        .map(PluginsProxyInfoEntity::getHealthCheckPath)
        .filter(healthCheckPath -> isNotEmpty(healthCheckPath))
        .findFirst()
        .orElse(null);
  }

  private String normalizeHealthCheckPath(String healthCheckPath) {
    if (isEmpty(healthCheckPath)) {
      return null;
    }
    String trimmed = healthCheckPath.trim();
    if (trimmed.contains("://") || trimmed.startsWith("//")) {
      log.warn("Ignoring invalid healthCheckPath '{}' (must be a host-relative path)", healthCheckPath);
      return null;
    }
    return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
  }

  private DecryptableEntity githubOauthAuthentication(String accountIdentifier) {
    Optional<BackstageEnvVariable> optionalBackstageEnvVariable =
        backstageEnvVariableService.findByEnvNameAndAccountIdentifier(AUTH_GITHUB_CLIENT_SECRET, accountIdentifier);
    if (optionalBackstageEnvVariable.isEmpty()
        || !optionalBackstageEnvVariable.get().getType().equals(BackstageEnvVariable.TypeEnum.SECRET)) {
      throw new UnexpectedException("Error in framing Github OAuth Secret details.");
    }
    BackstageEnvSecretVariable githubOauthSecret = (BackstageEnvSecretVariable) optionalBackstageEnvVariable.get();
    return GithubOauthDTO.builder()
        .tokenRef(new SecretRefData(addAccountScopeInIdentifier(githubOauthSecret.getHarnessSecretIdentifier())))
        .build();
  }

  private DecryptableEntity googleOauthAuthentication(String accountIdentifier) {
    Optional<BackstageEnvVariable> optionalBackstageEnvVariable =
        backstageEnvVariableService.findByEnvNameAndAccountIdentifier(AUTH_GOOGLE_CLIENT_SECRET, accountIdentifier);
    if (optionalBackstageEnvVariable.isEmpty()
        || !optionalBackstageEnvVariable.get().getType().equals(BackstageEnvVariable.TypeEnum.SECRET)) {
      throw new UnexpectedException("Error in framing Google OAuth Secret details.");
    }
    BackstageEnvSecretVariable googleOauthSecret = (BackstageEnvSecretVariable) optionalBackstageEnvVariable.get();
    return GcpManualDetailsDTO.builder()
        .secretKeyRef(new SecretRefData(addAccountScopeInIdentifier(googleOauthSecret.getHarnessSecretIdentifier())))
        .build();
  }

  private DecryptableEntity atlassianOauthAuthentication(String accountIdentifier) {
    Optional<BackstageEnvVariable> optionalBackstageEnvVariable =
        backstageEnvVariableService.findByEnvNameAndAccountIdentifier(AUTH_ATLASSIAN_CLIENT_SECRET, accountIdentifier);
    if (optionalBackstageEnvVariable.isEmpty()
        || !optionalBackstageEnvVariable.get().getType().equals(BackstageEnvVariable.TypeEnum.SECRET)) {
      throw new UnexpectedException("Error in framing Atlassian OAuth Secret details.");
    }
    BackstageEnvSecretVariable atlassianOauthSecret = (BackstageEnvSecretVariable) optionalBackstageEnvVariable.get();
    return JiraPATDTO.builder()
        .patRef(new SecretRefData(addAccountScopeInIdentifier(atlassianOauthSecret.getHarnessSecretIdentifier())))
        .build();
  }

  private DecryptableEntity sonarQubeAuthentication(
      String accountIdentifier, String url, List<HttpHeaderConfig> headers) {
    URL urlObject = urlObject(url);
    AppConfigEntity appConfigEntity =
        appConfigRepository.findByAccountIdentifierAndConfigId(accountIdentifier, SONARQUBE_PLUGIN);
    Map<String, Object> appConfig = loadYamlStringAsMap(appConfigEntity.getConfigs());
    List<Map<String, String>> instances =
        (List<Map<String, String>>) ((Map<String, Object>) appConfig.get("sonarqube")).get("instances");
    for (Map<String, String> instance : instances) {
      if (urlObject(instance.get("baseUrl")).getHost().equals(urlObject.getHost())) {
        String instanceApiKey = instance.get("apiKey");
        Optional<BackstageEnvVariable> optionalBackstageEnvVariable =
            backstageEnvVariableService.findByEnvNameAndAccountIdentifier(
                instanceApiKey.substring(2, instanceApiKey.length() - 1), accountIdentifier);
        if (optionalBackstageEnvVariable.isEmpty()
            || !optionalBackstageEnvVariable.get().getType().equals(BackstageEnvVariable.TypeEnum.SECRET)) {
          throw new UnexpectedException("Error in framing SonarQube Authentication details.");
        }
        BackstageEnvSecretVariable sonarQubeSecret = (BackstageEnvSecretVariable) optionalBackstageEnvVariable.get();
        removeHeadersForDelegateTask(headers);
        return SonarQubeConnectorDTO.builder()
            .apiTokenRef(new SecretRefData(addAccountScopeInIdentifier(sonarQubeSecret.getHarnessSecretIdentifier())))
            .build();
      }
    }
    return null;
  }

  private DecryptableEntity jenkinsAuthentication(
      String accountIdentifier, String url, List<HttpHeaderConfig> headers) {
    URL urlObject = urlObject(url);
    AppConfigEntity appConfigEntity =
        appConfigRepository.findByAccountIdentifierAndConfigId(accountIdentifier, JENKINS_PLUGIN);
    Map<String, Object> appConfig = loadYamlStringAsMap(appConfigEntity.getConfigs());
    List<Map<String, String>> instances =
        (List<Map<String, String>>) ((Map<String, Object>) appConfig.get("jenkins")).get("instances");
    for (Map<String, String> instance : instances) {
      if (urlObject(instance.get("baseUrl")).getHost().equals(urlObject.getHost())) {
        String instanceApiKey = instance.get("apiKey");
        Optional<BackstageEnvVariable> optionalBackstageEnvVariable =
            backstageEnvVariableService.findByEnvNameAndAccountIdentifier(
                instanceApiKey.substring(2, instanceApiKey.length() - 1), accountIdentifier);
        if (optionalBackstageEnvVariable.isEmpty()
            || !optionalBackstageEnvVariable.get().getType().equals(BackstageEnvVariable.TypeEnum.SECRET)) {
          throw new UnexpectedException("Error in framing Jenkins Authentication details.");
        }
        BackstageEnvSecretVariable jenkinsSecret = (BackstageEnvSecretVariable) optionalBackstageEnvVariable.get();
        removeHeadersForDelegateTask(headers);
        return JenkinsUserNamePasswordDTO.builder()
            .username(instance.get("username"))
            .passwordRef(new SecretRefData(addAccountScopeInIdentifier(jenkinsSecret.getHarnessSecretIdentifier())))
            .build();
      }
    }
    return null;
  }

  private boolean checkIfProxyPlugin(List<HttpHeaderConfig> headers) {
    for (HttpHeaderConfig header : headers) {
      if (header.getKey().equals(IDP_PLUGIN_ORIGIN_HEADER) && header.getValue().equals(HARNESS_PROXY)) {
        return true;
      }
    }
    return false;
  }

  private boolean checkScaffolderActionCall(List<HttpHeaderConfig> headers) {
    for (HttpHeaderConfig header : headers) {
      if (header.getKey().equals(IDP_PLUGIN_ORIGIN_HEADER) && header.getValue().equals(SCAFFOLDER_ACTION)) {
        return true;
      }
    }
    return false;
  }

  private void removeHeadersForDelegateTask(List<HttpHeaderConfig> headers) {
    List<HttpHeaderConfig> toBeRemovedHeaders;
    toBeRemovedHeaders = headers.stream()
                             .filter(httpHeaderConfig -> httpHeaderConfig.getKey().equalsIgnoreCase("Authorization"))
                             .collect(Collectors.toList());
    headers.removeAll(toBeRemovedHeaders);
  }

  private NGAccess ngAccess(String accountIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(null)
        .projectIdentifier(null)
        .build();
  }
}
