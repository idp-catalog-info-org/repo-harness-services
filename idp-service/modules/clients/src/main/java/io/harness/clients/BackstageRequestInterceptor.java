/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.BACKSTAGE_BASE_URL_LOCAL_VALUE;
import static io.harness.idp.common.Constants.LOCAL_ENV;

import io.harness.beans.DecryptedSecretValue;
import io.harness.idp.common.Constants;
import io.harness.idp.common.accountdetails.AccountsDetailsCache;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class BackstageRequestInterceptor implements Interceptor {
  private final SecretManagerClientService ngSecretService;
  private final String env;
  private static final int EXPIRATION_TIME = 3600;
  private final String backstageBaseUrl;
  private final AccountsDetailsCache accountsDetailsCache;

  public BackstageRequestInterceptor(SecretManagerClientService ngSecretService, String env, String backstageBaseUrl,
      AccountsDetailsCache accountsDetailsCache) {
    this.ngSecretService = ngSecretService;
    this.env = env;
    this.backstageBaseUrl = backstageBaseUrl;
    this.accountsDetailsCache = accountsDetailsCache;
  }

  @NotNull
  @Override
  public Response intercept(@NotNull Chain chain) throws IOException {
    Request request = chain.request();
    URL url = new URL(chain.request().url().toString());
    String urlStr = url.toString();
    urlStr = requestUrlModification(urlStr);
    String path = url.getPath();
    String accountIdentifier = path.split("/")[1];
    if (!env.equals(LOCAL_ENV) && backstageBaseUrl.equals(BACKSTAGE_BASE_URL_LOCAL_VALUE)) {
      // Above if condition is to differentiate infra evolution clusters from normal clusters

      if (!isEmpty(accountsDetailsCache.get(accountIdentifier).getSubdomainUrl())) {
        String subdomainUrl = accountsDetailsCache.get(accountIdentifier).getSubdomainUrl();
        if (!subdomainUrl.endsWith("/")) {
          subdomainUrl += "/";
        }
        urlStr = urlStr.replace(backstageBaseUrl, subdomainUrl);
        // Will remove this logger when things will be stable in infra evolution clusters
        log.info("Case of Vanity url - {} for account - {}", urlStr, accountIdentifier);
      } else {
        /*This is the case if there is bug in infra evolution cluster as every account in infra evolution cluster will
         * have subdomain url
         */
        log.error("Subdomain url does not exist for account id - {}, value of subdomain url -{}", accountIdentifier,
            accountsDetailsCache.get(accountIdentifier).getSubdomainUrl());
      }
    }
    String token = getBackstageBackendSecret(accountIdentifier);
    return chain.proceed(request.newBuilder().url(urlStr).header("Authorization", "Bearer " + token).build());
  }

  private String requestUrlModification(String urlStr) {
    if (StringUtils.isEmpty(env) || env.equals(LOCAL_ENV)) {
      urlStr = urlStr.replace("/idp/api/", "/api/");

      StringBuilder localUrl = new StringBuilder(urlStr);
      int start = urlStr.indexOf("/", 8);
      int end = urlStr.indexOf("/", 23);
      localUrl.replace(start, end, "");
      urlStr = localUrl.toString();
    }
    return urlStr;
  }

  private String getBackstageBackendSecret(String harnessAccount) {
    DecryptedSecretValue decryptedValue =
        ngSecretService.getDecryptedSecretValue(harnessAccount, null, null, Constants.IDP_BACKEND_SECRET);
    byte[] decodedSecret = Base64.getDecoder().decode(String.valueOf(decryptedValue.getDecryptedValue()));
    return generateToken(new String(decodedSecret));
  }

  private String generateToken(String secretKey) {
    String subject = "backstage-server";
    long expirationTime = Instant.now().plusSeconds(EXPIRATION_TIME).getEpochSecond();

    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", subject);
    claims.put("exp", expirationTime);

    // Get user principal from SourcePrincipalContext if available and add user claims
    Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (sourcePrincipal instanceof UserPrincipal userPrincipal) {
      claims.put("userId", userPrincipal.getName());
      claims.put("userEmail", userPrincipal.getEmail());
      claims.put("username", userPrincipal.getUsername());
      claims.put("accountId", userPrincipal.getAccountId());
      claims.put("userRole", userPrincipal.getRole());
      claims.put("uniqueId", userPrincipal.getUniqueId());
    } else {
      log.debug("No user principal found in context. Generating token without user claims. Principal type: {}",
          sourcePrincipal != null ? sourcePrincipal.getType() : "null");
    }

    Map<String, Object> headers = new HashMap<>();
    headers.put("alg", SignatureAlgorithm.HS256.getValue());
    headers.put("typ", Header.JWT_TYPE);

    JwtBuilder builder = Jwts.builder().setHeaderParams(headers).setClaims(claims).signWith(
        SignatureAlgorithm.HS256, secretKey.getBytes());
    String token = builder.compact();
    log.debug("Successfully generated backstage token with {} claims", claims.size());
    return token;
  }
}