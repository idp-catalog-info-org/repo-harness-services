/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.gitxwebhooks.entity.GenericWebhookSpec;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.HmacSpec;
import io.harness.gitsync.gitxwebhooks.entity.SlackWebhookSpec;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.utils.HmacUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class WebhookHmacHelper {
  private static final String X_SLACK_SIGNATURE = "X-Slack-Signature";
  private static final String X_SLACK_REQUEST_TIMESTAMP = "X-Slack-Request-Timestamp";
  @Inject private NGEncryptedDataService encryptedDataService;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  public void verifyHMACSignature(GitXWebhook webhook, String eventPayload, List<HeaderConfig> httpHeaders) {
    GenericWebhookSpec genericWebhookSpec = (GenericWebhookSpec) webhook.getSpec();
    HmacSpec hmacSpec = (HmacSpec) genericWebhookSpec.getAuthSpec();

    if (!NGCommonEntityConstants.HMAC_AUTH_TYPE_WEBHOOK.equals(genericWebhookSpec.getAuthType())) {
      return;
    }

    String header = hmacSpec.getHeader();
    String secretKey = hmacSpec.getSecretKey();
    String algorithm = hmacSpec.getHashAlgorithm();
    String secretValue = getSecretValue(webhook, secretKey);
    verifySignature(eventPayload, httpHeaders, header, secretValue, algorithm);
  }

  private static HeaderConfig getHeaderConfigForTheHeader(List<HeaderConfig> httpHeaders, String header) {
    if (header == null || httpHeaders == null) {
      return null;
    }
    return httpHeaders.stream()
        .filter(headerConfig -> StringUtils.equalsIgnoreCase(header, headerConfig.getKey()))
        .findFirst()
        .orElse(null);
  }

  public static void verifySignature(
      String eventPayload, List<HeaderConfig> httpHeaders, String header, String secretValue, String algorithm) {
    HeaderConfig headerConfig = getHeaderConfigForTheHeader(httpHeaders, header);

    if (headerConfig == null) {
      log.warn(
          "Configured header [{}] is not present in the http request headers. Please configure the header correctly.",
          header);
      throw new InvalidRequestException("Configured header [" + header
          + "] is not present in the http request headers. Please configure the header correctly.");
    }

    if (EmptyPredicate.isEmpty(headerConfig.getValues())) {
      log.warn(
          "Configured header [{}] is not present in the http request headers. Please configure the header correctly.",
          header);
      throw new InvalidRequestException("Configured header [" + header
          + "] is not present in the http request headers. Please configure the header correctly.");
    }
    String signature = headerConfig.getValues().get(0);
    HmacUtils.verifyHmacSignature(secretValue, algorithm, eventPayload, signature);
  }

  public void verifyHMACSignatureForSlack(GitXWebhook webhook, String eventPayload, List<HeaderConfig> httpHeaders) {
    SlackWebhookSpec slackWebhookSpec = (SlackWebhookSpec) webhook.getSpec();
    HmacSpec hmacSpec = (HmacSpec) slackWebhookSpec.getAuthSpec();

    if (!(NGCommonEntityConstants.HMAC_AUTH_TYPE_WEBHOOK).equals(slackWebhookSpec.getAuthType())) {
      return;
    }

    String secretKey = hmacSpec.getSecretKey();

    HeaderConfig signatureHeader = getHeaderConfigForTheHeader(httpHeaders, X_SLACK_SIGNATURE);
    HeaderConfig timeStampHeader = getHeaderConfigForTheHeader(httpHeaders, X_SLACK_REQUEST_TIMESTAMP);

    if (signatureHeader == null) {
      log.warn("Slack signature header [{}] is not present in the http request headers.", X_SLACK_SIGNATURE);
      throw new InvalidRequestException(
          "Slack signature header [" + X_SLACK_SIGNATURE + "] is not present in the http request headers.");
    }

    if (timeStampHeader == null) {
      log.warn("Slack timestamp header [{}] is not present in the http request headers.", X_SLACK_REQUEST_TIMESTAMP);
      throw new InvalidRequestException(
          "Slack timestamp header [" + X_SLACK_REQUEST_TIMESTAMP + "] is not present in the http request headers.");
    }

    String secretValue = getSecretValue(webhook, secretKey);

    String baseString = "v0:" + timeStampHeader.getValues().get(0) + ":" + eventPayload;

    HmacUtils.verifySlackHMACSignature(secretValue, baseString, signatureHeader.getValues().get(0));
  }

  private String getSecretValue(GitXWebhook webhook, String secretKey) {
    ScopeInfo webhookScopeInfo =
        scopeResolutionHelper.getScopeInfo(webhook.getAccountIdentifier(), webhook.getParentUniqueId());
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(secretKey, webhook.getAccountIdentifier(),
        webhookScopeInfo.getOrgIdentifier(), webhookScopeInfo.getProjectIdentifier());
    ScopeInfo secretScopeInfo = scopeResolutionHelper.getScopeInfo(
        identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier());
    return encryptedDataService.decryptSecret(secretScopeInfo, identifierRef.getIdentifier()).getDecryptedValue();
  }
}
