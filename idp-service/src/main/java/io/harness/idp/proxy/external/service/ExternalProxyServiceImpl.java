/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.external.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.UnexpectedException;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.DslClientConfig;
import io.harness.idp.common.OkHttpClientConnectionPoolConfig;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.harnessid.HarnessIdTokenService;
import io.harness.idp.proxy.delegate.DelegateProxyRequestForwarder;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.idp.proxy.external.beans.ExternalProxyEndpointConfig;
import io.harness.security.AllTrustingX509TrustManager;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ExternalProxyServiceImpl implements ExternalProxyService {
  private static final String PROXY_KEY = "proxy";
  private static final String ENDPOINTS_KEY = "endpoints";
  private static final String TARGET_KEY = "target";
  private static final String HEADERS_KEY = "headers";
  private static final String ALLOWED_METHODS_KEY = "allowedMethods";
  private static final String ALLOWED_HEADERS_KEY = "allowedHeaders";
  private static final String PATH_REWRITE_KEY = "pathRewrite";
  private static final String ENABLE_SIGNED_USER_KEY = "enableSignedUser";
  private static final String USER_TOKEN_HEADER = "X-Harness-IDP-User-Token";
  private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

  private final AppConfigRepository appConfigRepository;
  private final BackstageEnvVariableService backstageEnvVariableService;
  private final ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;
  private final DelegateProxyRequestForwarder delegateProxyRequestForwarder;
  private final DelegateSelectorsCache delegateSelectorsCache;
  private final HarnessIdTokenService harnessIdTokenService;
  private final Yaml yaml;

  private final OkHttpClientConnectionPoolConfig directDslClientHttpClientConnectionPoolConfig;
  private final DslClientConfig dslClientConfig;
  private final OkHttpClient httpClient;
  private static final ImmutableList<TrustManager> TRUST_ALL_CERTS =
      ImmutableList.of(new AllTrustingX509TrustManager());

  @Inject
  public ExternalProxyServiceImpl(AppConfigRepository appConfigRepository,
      BackstageEnvVariableService backstageEnvVariableService,
      ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper,
      DelegateProxyRequestForwarder delegateProxyRequestForwarder, DelegateSelectorsCache delegateSelectorsCache,
      HarnessIdTokenService harnessIdTokenService,
      @Named("directDslClientHttpClientConnectionPoolConfig")
      OkHttpClientConnectionPoolConfig directDslClientHttpClientConnectionPoolConfig,
      @Named("dslClientConfig") DslClientConfig dslClientConfig) {
    this.appConfigRepository = appConfigRepository;
    this.backstageEnvVariableService = backstageEnvVariableService;
    this.proxyEnvVariableServiceWrapper = proxyEnvVariableServiceWrapper;
    this.delegateProxyRequestForwarder = delegateProxyRequestForwarder;
    this.delegateSelectorsCache = delegateSelectorsCache;
    this.harnessIdTokenService = harnessIdTokenService;
    this.yaml = new Yaml();
    this.directDslClientHttpClientConnectionPoolConfig = directDslClientHttpClientConnectionPoolConfig;
    this.dslClientConfig = dslClientConfig;
    this.httpClient = buildOkHttpClient();
  }

  @Override
  public List<ExternalProxyEndpointConfig> getAllProxyEndpointConfigs(String accountIdentifier) {
    List<ExternalProxyEndpointConfig> allConfigs = new ArrayList<>();

    List<AppConfigEntity> enabledPlugins = appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
        accountIdentifier, ConfigType.PLUGIN, true);
    for (AppConfigEntity plugin : enabledPlugins) {
      if (isEmpty(plugin.getConfigs())) {
        continue;
      }
      List<ExternalProxyEndpointConfig> pluginProxyConfigs = parseProxyConfigsFromYaml(plugin.getConfigs());
      allConfigs.addAll(pluginProxyConfigs);
    }

    return allConfigs;
  }

  @Override
  public Optional<ExternalProxyEndpointConfig> getProxyEndpointConfig(String accountIdentifier, String endpointPath) {
    List<ExternalProxyEndpointConfig> allConfigs = getAllProxyEndpointConfigs(accountIdentifier);
    return allConfigs.stream().filter(config -> matchesEndpoint(endpointPath, config.getEndpoint())).findFirst();
  }

  @Override
  public boolean isMethodAllowed(ExternalProxyEndpointConfig config, String method) {
    if (config == null || isEmpty(config.getAllowedMethods())) {
      return true;
    }
    return config.getAllowedMethods().stream().anyMatch(allowedMethod -> allowedMethod.equalsIgnoreCase(method));
  }

  @Override
  public String resolveHeaderValue(String accountIdentifier, String headerValue) {
    if (isEmpty(headerValue)) {
      return headerValue;
    }

    Matcher matcher = ENV_VAR_PATTERN.matcher(headerValue);
    if (!matcher.find()) {
      return headerValue;
    }
    matcher.reset();

    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String envVarName = matcher.group(1);
      String resolvedValue = resolveEnvVariable(accountIdentifier, envVarName);
      matcher.appendReplacement(result, Matcher.quoteReplacement(resolvedValue != null ? resolvedValue : ""));
    }
    matcher.appendTail(result);

    return result.toString();
  }

  @Override
  public Response getResponse(String accountIdentifier, boolean enableSignedUser, String endpoint, String targetUrl,
      String method, String body, Map<String, List<String>> headers, String contentType) throws IOException {
    URL url;
    try {
      url = new URL(targetUrl);
    } catch (MalformedURLException e) {
      throw new RuntimeException("Error parsing the url", e);
    }
    String host = url.getHost();
    if (host.equals("api.github.com")) {
      host = "github.com";
    }

    if (enableSignedUser) {
      injectSignedUserToken(accountIdentifier, endpoint, host, headers);
    }

    boolean throughDelegate = throughDelegate(accountIdentifier, host);
    if (throughDelegate) {
      Set<String> delegateSelectors = delegateSelectors(accountIdentifier, host);
      return delegateResponse(accountIdentifier, targetUrl, method, body, headers, delegateSelectors);
    }
    Request.Builder requestBuilder = new Request.Builder().url(targetUrl);
    setMethodAndBody(requestBuilder, method, body, contentType);
    setHeaders(requestBuilder, headers);
    okhttp3.Response response = httpClient.newCall(requestBuilder.build()).execute();
    return buildResponse(response);
  }

  private void injectSignedUserToken(
      String accountIdentifier, String endpoint, String targetHost, Map<String, List<String>> headers) {
    try {
      Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
      if (!(sourcePrincipal instanceof UserPrincipal)) {
        log.warn("Signed user token requested but no user principal found in context for endpoint: {}. Skipping token "
                + "generation.",
            endpoint);
        return;
      }

      UserPrincipal userPrincipal = (UserPrincipal) sourcePrincipal;
      String signedUserToken = harnessIdTokenService.generateSignedUserToken(
          accountIdentifier, userPrincipal, endpoint, targetHost, targetHost, null);

      if (StringUtils.isNotBlank(signedUserToken)) {
        headers.put(USER_TOKEN_HEADER, Collections.singletonList(signedUserToken));
        log.debug("Injected signed user token for user: {}, endpoint: {}", userPrincipal.getEmail(), endpoint);
      }
    } catch (Exception e) {
      log.error("Failed to inject signed user token for endpoint: {}. Proceeding without token.", endpoint, e);
    }
  }

  private String resolveEnvVariable(String accountIdentifier, String envVarName) {
    try {
      List<BackstageEnvVariable> envVariables = backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(
          Collections.singletonList(envVarName), accountIdentifier);

      if (!isEmpty(envVariables)) {
        BackstageEnvVariable envVar = envVariables.get(0);
        if (envVar.getType() == BackstageEnvVariable.TypeEnum.SECRET) {
          BackstageEnvSecretVariable secretVar = (BackstageEnvSecretVariable) envVar;
          return backstageEnvVariableService
              .getDecryptedValueAndLastModifiedTime(
                  envVarName, secretVar.getHarnessSecretIdentifier(), accountIdentifier, null, null)
              .getFirst();
        } else {
          BackstageEnvConfigVariable configVar = (BackstageEnvConfigVariable) envVar;
          return configVar.getValue();
        }
      }
    } catch (Exception e) {
      log.warn("Failed to resolve environment variable {}: {}", envVarName, e.getMessage());
    }
    return "${" + envVarName + "}";
  }

  @SuppressWarnings("unchecked")
  private List<ExternalProxyEndpointConfig> parseProxyConfigsFromYaml(String configYaml) {
    List<ExternalProxyEndpointConfig> configs = new ArrayList<>();

    try {
      Map<String, Object> yamlMap = yaml.load(configYaml);
      if (yamlMap == null || !yamlMap.containsKey(PROXY_KEY)) {
        return configs;
      }

      Map<String, Object> proxyMap = (Map<String, Object>) yamlMap.get(PROXY_KEY);
      if (proxyMap == null || !proxyMap.containsKey(ENDPOINTS_KEY)) {
        return configs;
      }

      Map<String, Object> endpointsMap = (Map<String, Object>) proxyMap.get(ENDPOINTS_KEY);
      if (endpointsMap == null) {
        return configs;
      }

      for (Map.Entry<String, Object> entry : endpointsMap.entrySet()) {
        String endpoint = entry.getKey();
        Map<String, Object> endpointConfig = (Map<String, Object>) entry.getValue();

        if (endpointConfig != null) {
          ExternalProxyEndpointConfig config = parseEndpointConfig(endpoint, endpointConfig);
          configs.add(config);
        }
      }
    } catch (Exception e) {
      log.error("Failed to parse proxy config from YAML: {}", e.getMessage());
    }

    return configs;
  }

  @SuppressWarnings("unchecked")
  private ExternalProxyEndpointConfig parseEndpointConfig(String endpoint, Map<String, Object> configMap) {
    ExternalProxyEndpointConfig.ExternalProxyEndpointConfigBuilder builder = ExternalProxyEndpointConfig.builder();
    builder.endpoint(endpoint);

    if (configMap.containsKey(TARGET_KEY)) {
      builder.target((String) configMap.get(TARGET_KEY));
    }

    if (configMap.containsKey(HEADERS_KEY)) {
      Map<String, Object> headersObj = (Map<String, Object>) configMap.get(HEADERS_KEY);
      if (headersObj != null) {
        Map<String, String> headers = new HashMap<>();
        headersObj.forEach((key, value) -> headers.put(key, String.valueOf(value)));
        builder.headers(headers);
      }
    }

    if (configMap.containsKey(ALLOWED_METHODS_KEY)) {
      Object methods = configMap.get(ALLOWED_METHODS_KEY);
      if (methods instanceof List) {
        builder.allowedMethods(((List<?>) methods).stream().map(Object::toString).collect(Collectors.toList()));
      }
    }

    if (configMap.containsKey(ALLOWED_HEADERS_KEY)) {
      Object headers = configMap.get(ALLOWED_HEADERS_KEY);
      if (headers instanceof List) {
        builder.allowedHeaders(((List<?>) headers).stream().map(Object::toString).collect(Collectors.toList()));
      }
    }

    if (configMap.containsKey(PATH_REWRITE_KEY)) {
      Map<String, Object> pathRewriteObj = (Map<String, Object>) configMap.get(PATH_REWRITE_KEY);
      if (pathRewriteObj != null) {
        Map<String, String> pathRewrite = new HashMap<>();
        pathRewriteObj.forEach((key, value) -> pathRewrite.put(key, String.valueOf(value)));
        builder.pathRewrite(pathRewrite);
      }
    }

    if (configMap.containsKey(ENABLE_SIGNED_USER_KEY)) {
      Object enableSignedUser = configMap.get(ENABLE_SIGNED_USER_KEY);
      if (enableSignedUser instanceof Boolean) {
        builder.enableSignedUser((Boolean) enableSignedUser);
      }
    }

    return builder.build();
  }

  private boolean matchesEndpoint(String requestPath, String endpointPattern) {
    if (isEmpty(requestPath) || isEmpty(endpointPattern)) {
      return false;
    }
    String normalizedRequest = CommonUtils.removeTrailingAndLeadingSlash(requestPath);
    String normalizedEndpoint = CommonUtils.removeTrailingAndLeadingSlash(endpointPattern);
    return normalizedRequest.equals(normalizedEndpoint) || normalizedRequest.startsWith(normalizedEndpoint + "/");
  }

  private boolean throughDelegate(String accountIdentifier, String host) {
    JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);
    return host != null && hostProxyMap.optBoolean(host, false);
  }

  private Set<String> delegateSelectors(String accountIdentifier, String host) {
    return delegateSelectorsCache.get(accountIdentifier, host);
  }

  private Response delegateResponse(String accountIdentifier, String url, String method, String body,
      Map<String, List<String>> headers, Set<String> delegateSelectors) {
    List<HttpHeaderConfig> headerList = new ArrayList<>();
    headers.forEach((key, values) -> {
      for (String value : values) {
        headerList.add(HttpHeaderConfig.builder().key(key).value(value).build());
      }
    });
    HttpStepResponse httpResponse = delegateProxyRequestForwarder.forwardRequestToDelegate(
        accountIdentifier, url, headerList, body, method, delegateSelectors, null, null);
    if (httpResponse == null) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder()
                      .message("Did not receive response from Delegate")
                      .code(ErrorCode.INTERNAL_SERVER_ERROR)
                      .build())
          .build();
    }
    return Response.status(httpResponse.getHttpResponseCode()).entity(httpResponse.getHttpResponseBody()).build();
  }

  private void setMethodAndBody(Request.Builder requestBuilder, String method, String body, String contentType) {
    RequestBody requestBody = null;

    if (body != null && !body.isEmpty()) {
      requestBody = RequestBody.create(body, MediaType.parse(contentType != null ? contentType : "application/json"));
    }

    switch (method.toUpperCase()) {
      case "GET":
        requestBuilder.get();
        break;
      case "POST":
        requestBuilder.post(requestBody != null ? requestBody : RequestBody.create("", null));
        break;
      case "PUT":
        requestBuilder.put(requestBody != null ? requestBody : RequestBody.create("", null));
        break;
      case "PATCH":
        requestBuilder.patch(requestBody != null ? requestBody : RequestBody.create("", null));
        break;
      case "DELETE":
        if (requestBody != null) {
          requestBuilder.delete(requestBody);
        } else {
          requestBuilder.delete();
        }
        break;
      default:
        throw new IllegalArgumentException("Unsupported HTTP method: " + method);
    }
  }

  private void setHeaders(Request.Builder requestBuilder, Map<String, List<String>> headers) {
    headers.forEach((key, values) -> values.forEach(value -> requestBuilder.header(key, value)));
  }

  private OkHttpClient buildOkHttpClient() {
    try {
      final SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, TRUST_ALL_CERTS.toArray(new TrustManager[1]), new java.security.SecureRandom());
      final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
      return new OkHttpClient()
          .newBuilder()
          .connectionPool(new ConnectionPool(directDslClientHttpClientConnectionPoolConfig.getMaxIdleConnections(),
              directDslClientHttpClientConnectionPoolConfig.getKeepAliveDuration(),
              TimeUnit.valueOf(directDslClientHttpClientConnectionPoolConfig.getTimeUnit())))
          .connectTimeout(dslClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
          .readTimeout(dslClientConfig.getReadTimeOutSeconds(), TimeUnit.SECONDS)
          .writeTimeout(dslClientConfig.getWriteTimeOutSeconds(), TimeUnit.SECONDS)
          .retryOnConnectionFailure(true)
          .sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS.get(0))
          .build();
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new UnexpectedException(e.getMessage());
    }
  }

  private Response buildResponse(okhttp3.Response response) throws IOException {
    Object entity = null;
    if (response.body() != null) {
      entity = response.body().string();
    }
    return Response.status(response.code()).entity(entity).build();
  }
}
