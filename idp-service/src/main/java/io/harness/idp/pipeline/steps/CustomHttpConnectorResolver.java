/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.pipeline.steps;

import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.CustomHttpConnectorDTO;
import io.harness.delegate.beans.connector.customhttp.ApiTokenAuthDTO;
import io.harness.delegate.beans.connector.customhttp.BasicAuthDTO;
import io.harness.delegate.beans.connector.customhttp.CustomHeaderAuthDTO;
import io.harness.delegate.beans.connector.customhttp.CustomHttpAuthCredentialsDTO;
import io.harness.delegate.beans.connector.customhttp.CustomHttpAuthType;
import io.harness.delegate.beans.connector.customhttp.CustomHttpAuthenticationDTO;
import io.harness.delegate.beans.connector.customhttp.CustomHttpHeaderKeyAndValue;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Singleton
@Slf4j
public class CustomHttpConnectorResolver {
  private final ConnectorResourceClient connectorResourceClient;
  private final SecretManagerClientService ngSecretService;
  private final DecryptionHelper decryptionHelper;

  @Inject
  public CustomHttpConnectorResolver(ConnectorResourceClient connectorResourceClient,
      @Named("PRIVILEGED") SecretManagerClientService ngSecretService, DecryptionHelper decryptionHelper) {
    this.connectorResourceClient = connectorResourceClient;
    this.ngSecretService = ngSecretService;
    this.decryptionHelper = decryptionHelper;
  }

  public ActionStepHelper.ResolvedConnector resolve(
      String connectorRef, String accountId, String orgId, String projectId, NGLogCallback logCallback) {
    if (connectorRef == null || connectorRef.isEmpty()) {
      throw new InvalidRequestException("Action definition is missing connectorRef");
    }

    IdentifierRef ref = IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgId, projectId);
    Optional<ConnectorDTO> connectorDTO = getResponse(connectorResourceClient.get(
        ref.getIdentifier(), ref.getAccountIdentifier(), ref.getOrgIdentifier(), ref.getProjectIdentifier()));
    if (connectorDTO.isEmpty()) {
      throw new InvalidRequestException(
          String.format("CustomHttp connector [%s] not found for scope account=[%s] org=[%s] project=[%s]",
              connectorRef, ref.getAccountIdentifier(), ref.getOrgIdentifier(), ref.getProjectIdentifier()));
    }
    ConnectorInfoDTO info = connectorDTO.get().getConnectorInfo();
    if (!(info.getConnectorConfig() instanceof CustomHttpConnectorDTO)) {
      throw new InvalidRequestException(
          String.format("Connector [%s] is type=[%s]; IdpAction requires a CustomHttp connector", connectorRef,
              info.getConnectorType()));
    }
    CustomHttpConnectorDTO custom = (CustomHttpConnectorDTO) info.getConnectorConfig();
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(ref.getAccountIdentifier())
                            .orgIdentifier(ref.getOrgIdentifier())
                            .projectIdentifier(ref.getProjectIdentifier())
                            .build();

    decryptAuthentication(custom.getAuthentication(), ngAccess);
    decryptAdditionalHeaders(custom.getAdditionalHeaders(), ngAccess);

    Map<String, String> defaultHeaders = buildDefaultHeaders(custom.getAdditionalHeaders());
    Map<String, String> authHeaders = buildAuthHeaders(custom.getAuthentication());
    Set<String> selectors = custom.getDelegateSelectors() == null ? new HashSet<>() : custom.getDelegateSelectors();

    logCallback.saveExecutionLog(String.format(
        "Resolved connector [%s] baseUrl=[%s], delegateSelectors=%s", connectorRef, custom.getBaseUrl(), selectors));

    return new ActionStepHelper.ResolvedConnector(custom.getBaseUrl(), defaultHeaders, authHeaders, selectors);
  }

  private void decryptAuthentication(CustomHttpAuthenticationDTO authentication, NGAccess ngAccess) {
    if (authentication == null || authentication.getCredentials() == null) {
      return;
    }
    CustomHttpAuthCredentialsDTO credentials = authentication.getCredentials();
    decryptIfDecryptable(credentials, ngAccess);
  }

  private void decryptAdditionalHeaders(List<CustomHttpHeaderKeyAndValue> headers, NGAccess ngAccess) {
    if (headers == null || headers.isEmpty()) {
      return;
    }
    for (CustomHttpHeaderKeyAndValue header : headers) {
      if (header != null && header.isValueEncrypted() && header.getEncryptedValueRef() != null) {
        decryptIfDecryptable(header, ngAccess);
      }
    }
  }

  private void decryptIfDecryptable(DecryptableEntity entity, NGAccess ngAccess) {
    if (entity == null) {
      return;
    }
    try {
      List<EncryptedDataDetail> details = ngSecretService.getEncryptionDetails(ngAccess, entity);
      decryptionHelper.decrypt(entity, details);
    } catch (Exception e) {
      throw new InvalidRequestException(
          String.format("Failed to decrypt CustomHttp connector credentials: %s", e.getMessage()), e);
    }
  }

  private Map<String, String> buildDefaultHeaders(List<CustomHttpHeaderKeyAndValue> headers) {
    Map<String, String> out = new LinkedHashMap<>();
    if (headers == null) {
      return out;
    }
    for (CustomHttpHeaderKeyAndValue header : headers) {
      if (header == null || header.getKey() == null || header.getKey().isEmpty()) {
        continue;
      }
      String value;
      if (header.isValueEncrypted()) {
        if (header.getEncryptedValueRef() == null || header.getEncryptedValueRef().getDecryptedValue() == null) {
          log.warn("Skipping additional header [{}] - secret could not be decrypted", header.getKey());
          continue;
        }
        value = String.valueOf(header.getEncryptedValueRef().getDecryptedValue());
      } else {
        value = header.getValue() == null ? "" : header.getValue();
      }
      out.put(header.getKey(), value);
    }
    return out;
  }

  private Map<String, String> buildAuthHeaders(CustomHttpAuthenticationDTO authentication) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (authentication == null || authentication.getAuthType() == null) {
      return headers;
    }
    CustomHttpAuthCredentialsDTO credentials = authentication.getCredentials();
    if (credentials == null) {
      return headers;
    }
    switch (authentication.getAuthType()) {
      case API_TOKEN:
        if (credentials instanceof ApiTokenAuthDTO) {
          addApiTokenHeader(headers, (ApiTokenAuthDTO) credentials);
        } else {
          log.warn("authType=API_TOKEN but credentials is {}; skipping", credentials.getClass().getSimpleName());
        }
        break;
      case BASIC_AUTH:
        if (credentials instanceof BasicAuthDTO) {
          addBasicAuthHeader(headers, (BasicAuthDTO) credentials);
        } else {
          log.warn("authType=BASIC_AUTH but credentials is {}; skipping", credentials.getClass().getSimpleName());
        }
        break;
      case CUSTOM_HEADER:
        if (credentials instanceof CustomHeaderAuthDTO) {
          addCustomHeaderAuthHeader(headers, (CustomHeaderAuthDTO) credentials);
        } else {
          log.warn("authType=CUSTOM_HEADER but credentials is {}; skipping", credentials.getClass().getSimpleName());
        }
        break;
      case NO_AUTH:
      default:
    }
    return headers;
  }

  private void addApiTokenHeader(Map<String, String> headers, ApiTokenAuthDTO apiToken) {
    if (apiToken == null || apiToken.getTokenRef() == null || apiToken.getTokenRef().getDecryptedValue() == null) {
      log.warn("API token secret missing or not decrypted; skipping");
      return;
    }
    String headerName = apiToken.getHeaderName() == null || apiToken.getHeaderName().isEmpty()
        ? "Authorization"
        : apiToken.getHeaderName();
    String prefix = apiToken.getHeaderPrefix() == null ? "" : apiToken.getHeaderPrefix();
    if (!prefix.isEmpty() && !prefix.endsWith(" ")) {
      prefix = prefix + " ";
    }
    headers.put(headerName, prefix + String.valueOf(apiToken.getTokenRef().getDecryptedValue()));
  }

  private void addBasicAuthHeader(Map<String, String> headers, BasicAuthDTO basic) {
    if (basic == null || basic.getPasswordRef() == null || basic.getPasswordRef().getDecryptedValue() == null) {
      log.warn("Basic auth password secret missing or not decrypted; skipping");
      return;
    }
    String username = basic.getUsername() == null ? "" : basic.getUsername();
    String password = String.valueOf(basic.getPasswordRef().getDecryptedValue());
    String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    headers.put("Authorization", "Basic " + encoded);
  }

  private void addCustomHeaderAuthHeader(Map<String, String> headers, CustomHeaderAuthDTO custom) {
    if (custom == null || custom.getHeaderValueRef() == null
        || custom.getHeaderValueRef().getDecryptedValue() == null) {
      log.warn("Custom header auth secret missing or not decrypted; skipping");
      return;
    }
    if (custom.getHeaderName() == null || custom.getHeaderName().isEmpty()) {
      log.warn("Custom header auth has no header name; skipping");
      return;
    }
    headers.put(custom.getHeaderName(), String.valueOf(custom.getHeaderValueRef().getDecryptedValue()));
  }
}
