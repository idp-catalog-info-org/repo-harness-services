/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc_auth.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ds.remote.DSEventPublishHelper;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;
import io.harness.oidc_auth.entity.OidcProviderSettings;
import io.harness.oidc_auth.mapper.OidcProviderMapper;
import io.harness.oidc_auth.service.OidcProviderService;
import io.harness.repositories.OidcProviderRepository;
import io.harness.spec.server.ng.v1.model.OidcProviderDTO;

import software.wings.beans.sso.SSOType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class OidcProviderServiceImpl implements OidcProviderService {
  private OidcProviderRepository oidcProviderRepository;

  private EnforcementClientService enforcementClientService;
  private DSEventPublishHelper dsEventPublishHelper;
  private static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";
  private static final String TOKEN_ENDPOINT = "token_endpoint";
  private static final String USERINFO_ENDPOINT = "userinfo_endpoint";
  private static final String JWKS_URI = "jwks_uri";

  @Override
  public OidcProviderDTO createOidcProvider(String accountIdentifier, OidcProviderDTO oidcProviderDTO) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.OIDC_SUPPORT, accountIdentifier);
    validateOidcProvider(oidcProviderDTO);

    OidcProviderDTO existingOidcProvider = getOidcProvider(accountIdentifier, oidcProviderDTO.getIdentifier());
    if (existingOidcProvider != null) {
      throw new InvalidRequestException(
          String.format("Oidc Provider with identifier %s already exists.", oidcProviderDTO.getIdentifier()));
    }

    OidcProviderSettings oidcProviderSettings = OidcProviderMapper.getOidcProvider(accountIdentifier, oidcProviderDTO);

    if (Boolean.TRUE.equals(oidcProviderDTO.isDiscovery())) {
      populateClientConfig(oidcProviderDTO.getIssuer(), oidcProviderSettings);
    }

    try {
      OidcProviderSettings savedOidcProviderSettings = oidcProviderRepository.save(oidcProviderSettings);
      dsEventPublishHelper.publishAuthUpdateEventToDS(accountIdentifier);
      return OidcProviderMapper.getOidcProviderDTO(savedOidcProviderSettings);
    } catch (Exception ex) {
      log.error("Failed to save oidc provider with identifier {} for account- {}", oidcProviderDTO.getIdentifier(),
          accountIdentifier, ex);
      throw new InternalServerErrorException("Failed to create the OIDC provider, please try again.");
    }
  }

  @Override
  public OidcProviderDTO getOidcProvider(String accountIdentifier, String identifier) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.OIDC_SUPPORT, accountIdentifier);
    Optional<OidcProviderSettings> oidcProvider =
        oidcProviderRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    if (oidcProvider.isEmpty()) {
      return null;
    }
    return OidcProviderMapper.getOidcProviderDTO(oidcProvider.get());
  }

  @Override
  public List<OidcProviderDTO> getOidcProvidersForAccount(String accountIdentifier) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.OIDC_SUPPORT, accountIdentifier);
    List<OidcProviderSettings> oidcProviderSettings =
        oidcProviderRepository.findByAccountIdentifierAndType(accountIdentifier, SSOType.OIDC);
    if (isEmpty(oidcProviderSettings)) {
      return new ArrayList<>();
    }
    return oidcProviderSettings.stream()
        .map(oidcProvider -> OidcProviderMapper.getOidcProviderDTO(oidcProvider))
        .collect(Collectors.toList());
  }

  @Override
  public OidcProviderDTO updateOidcProvider(
      String accountIdentifier, String identifier, OidcProviderDTO oidcProviderDTO) {
    enforcementClientService.checkAvailability(FeatureRestrictionName.OIDC_SUPPORT, accountIdentifier);
    Optional<OidcProviderSettings> existingOidcProvider =
        oidcProviderRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    if (existingOidcProvider.isEmpty()) {
      throw new InvalidRequestException(String.format("OIDC provider with identifier %s does not exist for account %s",
          oidcProviderDTO.getIdentifier(), accountIdentifier));
    }

    validateOidcProvider(oidcProviderDTO);
    OidcProviderSettings oidcProviderSettings = OidcProviderMapper.getOidcProvider(accountIdentifier, oidcProviderDTO);

    if (Boolean.TRUE.equals(oidcProviderDTO.isDiscovery())) {
      populateClientConfig(oidcProviderDTO.getIssuer(), oidcProviderSettings);
    }

    oidcProviderSettings.setIdentifier(identifier);
    oidcProviderSettings.setId(existingOidcProvider.get().getId());
    try {
      OidcProviderSettings savedOidcProviderSettings = oidcProviderRepository.save(oidcProviderSettings);
      dsEventPublishHelper.publishAuthUpdateEventToDS(accountIdentifier);
      return OidcProviderMapper.getOidcProviderDTO(savedOidcProviderSettings);
    } catch (Exception ex) {
      log.error("Failed to save oidc provider with identifier {} for account- {}", oidcProviderDTO.getIdentifier(),
          accountIdentifier, ex);
      throw new InternalServerErrorException("Failed to update the OIDC provider, please try again.");
    }
  }

  @Override
  public boolean deleteOidcProvider(String accountIdentifier, String identifier) {
    try {
      oidcProviderRepository.deleteByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
      dsEventPublishHelper.publishAuthUpdateEventToDS(accountIdentifier);
    } catch (Exception ex) {
      log.error("Failed to delete oidc provider with identifier {} for account- {}", identifier, accountIdentifier, ex);
      throw new InternalServerErrorException("Failed to delete the OIDC provider, please try again.");
    }
    return oidcProviderRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier).isEmpty();
  }

  private void validateOidcProvider(OidcProviderDTO oidcProviderDTO) {}

  private JSONObject fetchOpenIdConfiguration(String issuer) {
    URI issuerUri = URI.create(issuer);
    String configURL = null;
    try {
      configURL = issuerUri.resolve(".well-known/openid-configuration").toURL().toString();
    } catch (MalformedURLException e) {
      throw new InvalidRequestException(
          "Failed to fetch openid configuration for the OIDC provider from %s due to malformed URL");
    }

    HttpGet request = new HttpGet(configURL);

    log.info("Requesting OIDC configuration URL {}", configURL);
    String content;

    try (CloseableHttpClient httpClient = getHttpClient(configURL);
         CloseableHttpResponse response = httpClient.execute(request)) {
      HttpEntity entity = response.getEntity();
      if (response.getStatusLine().getStatusCode() == 200) {
        content = EntityUtils.toString(entity);
      } else {
        throw new InvalidRequestException(
            String.format("Failed to fetch openid configuration for the OIDC provider from %s", configURL));
      }
    } catch (IOException e) {
      log.error(String.format("Failed to fetch openid configuration for the OIDC provider from %s. Reason: %s",
                    configURL, e.getMessage()),
          e);
      throw new InternalServerErrorException(String.format(
          "Failed to fetch openid configuration for the OIDC provider from %s. Reason: %s", configURL, e.getMessage()));
    }
    return new JSONObject(content);
  }

  private void populateClientConfig(String issuer, OidcProviderSettings oidcProviderSettings) {
    JSONObject openidConfigResponse = fetchOpenIdConfiguration(issuer);
    try {
      oidcProviderSettings.getClientIdConfiguration().setAuthorizationEndpoint(
          openidConfigResponse.get(AUTHORIZATION_ENDPOINT).toString());
      oidcProviderSettings.getClientIdConfiguration().setTokenEndpoint(
          openidConfigResponse.get(TOKEN_ENDPOINT).toString());
      oidcProviderSettings.getClientIdConfiguration().setJwksUri(openidConfigResponse.get(JWKS_URI).toString());
      oidcProviderSettings.getClientIdConfiguration().setUserInfoEndpoint(
          openidConfigResponse.get(USERINFO_ENDPOINT).toString());
    } catch (JSONException ex) {
      log.error("Failed to populate client config for OIDC provider {}", oidcProviderSettings.getIdentifier(), ex);
      throw new InvalidRequestException("Failed to populate client config during client discovery.");
    }
  }

  private CloseableHttpClient getHttpClient(String url) {
    RequestConfig requestConfig = RequestConfig.custom()
                                      .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(10))
                                      .setSocketTimeout((int) TimeUnit.SECONDS.toMillis(10))
                                      .build();
    HttpClientBuilder httpClientBuilder = HttpClients.custom().setDefaultRequestConfig(requestConfig);
    setProxyIfRequired(url, httpClientBuilder);
    return httpClientBuilder.build();
  }

  private void setProxyIfRequired(String url, HttpClientBuilder httpClientBuilder) {
    HttpHost proxyHost = Http.getHttpProxyHost();
    if (proxyHost != null && !Http.shouldUseNonProxy(url)) {
      if (isNotEmpty(Http.getProxyUserName())) {
        httpClientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(proxyHost),
            new UsernamePasswordCredentials(Http.getProxyUserName(), Http.getProxyPassword()));
        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
      }
      httpClientBuilder.setProxy(proxyHost);
    }
  }
}
