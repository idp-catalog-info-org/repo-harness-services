/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.k8s.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4PresignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

/**
 * Generates short-lived bearer tokens for EKS Kubernetes API authentication
 * using STS GetCallerIdentity presigned URLs. Compatible with EKS Pod Identity
 * and IRSA - credentials are resolved via DefaultCredentialsProvider (SDK v2).
 *
 * Token lifecycle: presigned URL is valid for 60s, but the resulting K8s token
 * is accepted by EKS for ~15 minutes. We cache and refresh at 10 min.
 */
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class EksTokenSupplier implements Supplier<String> {
  private static final String STS_ENDPOINT = "https://sts.amazonaws.com/";
  private static final String TOKEN_PREFIX = "k8s-aws-v1.";
  private static final String CLUSTER_ID_HEADER = "x-k8s-aws-id";
  private static final int PRESIGN_EXPIRY_SECONDS = 60;
  private static final long CACHE_DURATION_SECONDS = 600; // 10 minutes

  private final String clusterName;
  private final AwsCredentialsProvider credentialsProvider;

  private volatile String cachedToken;
  private volatile Instant tokenGeneratedAt = Instant.EPOCH;

  public EksTokenSupplier(String clusterName) {
    this.clusterName = clusterName;
    this.credentialsProvider = DefaultCredentialsProvider.create();
  }

  EksTokenSupplier(String clusterName, AwsCredentialsProvider credentialsProvider) {
    this.clusterName = clusterName;
    this.credentialsProvider = credentialsProvider;
  }

  @Override
  public String get() {
    if (isCacheValid()) {
      return cachedToken;
    }
    synchronized (this) {
      if (isCacheValid()) {
        return cachedToken;
      }
      cachedToken = generateToken();
      tokenGeneratedAt = Instant.now();
      log.info("Generated new EKS token for cluster {}", clusterName);
      return cachedToken;
    }
  }

  private boolean isCacheValid() {
    return cachedToken != null && Instant.now().isBefore(tokenGeneratedAt.plusSeconds(CACHE_DURATION_SECONDS));
  }

  private String generateToken() {
    AwsCredentials credentials = credentialsProvider.resolveCredentials();

    SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                                     .method(SdkHttpMethod.GET)
                                     .uri(URI.create(STS_ENDPOINT))
                                     .putRawQueryParameter("Action", "GetCallerIdentity")
                                     .putRawQueryParameter("Version", "2011-06-15")
                                     .putHeader(CLUSTER_ID_HEADER, clusterName)
                                     .build();

    Aws4PresignerParams presignerParams = Aws4PresignerParams.builder()
                                              .signingName("sts")
                                              .signingRegion(Region.US_EAST_1)
                                              .awsCredentials(credentials)
                                              .expirationTime(Instant.now().plusSeconds(PRESIGN_EXPIRY_SECONDS))
                                              .build();

    SdkHttpFullRequest signedRequest = Aws4Signer.create().presign(request, presignerParams);

    String presignedUrl = signedRequest.getUri().toString();
    return TOKEN_PREFIX
        + Base64.getUrlEncoder().withoutPadding().encodeToString(presignedUrl.getBytes(StandardCharsets.UTF_8));
  }
}
