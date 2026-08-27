/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.gitxwebhooks.entity.GenericWebhookSpec;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.HmacSpec;
import io.harness.gitsync.gitxwebhooks.entity.SlackWebhookSpec;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDC)
public class WebhookInstrumentationHelper extends InstrumentationHelper {
  @Inject TelemetryReporter telemetryReporter;

  String ACCOUNT_ID = "account_id";
  String WEBHOOK_ID = "webhook_id";
  String SCOPE = "scope";
  String WEBHOOK_TYPE = "webhook_type";
  String AUTH_TYPE = "auth_type";
  String HEADER = "http_request_header";

  public CompletableFuture sendNonGitWebhookEvent(String accountId, GitXWebhook webhook) {
    if (EmptyPredicate.isEmpty(accountId)) {
      log.warn(
          "Missing accountId. Cannot send telemetry for the received event for webhook [{}]", webhook.getIdentifier());
      return null;
    }
    try {
      HashMap<String, Object> map = new HashMap<>();
      map.put(ACCOUNT_ID, accountId);
      map.put(WEBHOOK_ID, webhook.getIdentifier());
      map.put(SCOPE, getWebhookScope(webhook));
      map.put(WEBHOOK_TYPE, webhook.getWebhookType());
      map.put(AUTH_TYPE, getAuthType(webhook));
      map.put(HEADER, getHeader(webhook));
      return CompletableFuture.runAsync(
          ()
              -> telemetryReporter.sendTrackEvent("process_webhook_event", null, accountId, map,
                  Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
                  TelemetryOption.builder().sendForCommunity(false).build()));
    } catch (Exception e) {
      log.warn("Failed to send telemetry event for the received event for account [{}] and webhook [{}]", accountId,
          webhook.getIdentifier(), e);
    }
    return null;
  }

  private String getHeader(GitXWebhook webhook) {
    if ((NGCommonEntityConstants.GENERIC_WEBHOOK_TYPE).equals(webhook.getWebhookType())) {
      GenericWebhookSpec genericWebhookSpec = (GenericWebhookSpec) webhook.getSpec();
      if ((NGCommonEntityConstants.HMAC_AUTH_TYPE_WEBHOOK).equals(genericWebhookSpec.getAuthType())) {
        HmacSpec hmacSpec = (HmacSpec) genericWebhookSpec.getAuthSpec();
        return hmacSpec.getHeader();
      } else {
        return null;
      }
    } else if ((NGCommonEntityConstants.SLACK_WEBHOOK_TYPE).equals(webhook.getWebhookType())) {
      SlackWebhookSpec slackWebhookSpec = (SlackWebhookSpec) webhook.getSpec();
      if ((NGCommonEntityConstants.HMAC_AUTH_TYPE_WEBHOOK).equals(slackWebhookSpec.getAuthType())) {
        HmacSpec hmacSpec = (HmacSpec) slackWebhookSpec.getAuthSpec();
        return hmacSpec.getHeader();
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  private String getAuthType(GitXWebhook webhook) {
    if ((NGCommonEntityConstants.GENERIC_WEBHOOK_TYPE).equals(webhook.getWebhookType())) {
      GenericWebhookSpec genericWebhookSpec = (GenericWebhookSpec) webhook.getSpec();
      return genericWebhookSpec.getAuthType();
    } else if ((NGCommonEntityConstants.SLACK_WEBHOOK_TYPE).equals(webhook.getWebhookType())) {
      SlackWebhookSpec slackWebhookSpec = (SlackWebhookSpec) webhook.getSpec();
      return slackWebhookSpec.getAuthType();
    } else {
      return null;
    }
  }

  private String getWebhookScope(GitXWebhook webhook) {
    if (EmptyPredicate.isEmpty(webhook.getOrgIdentifier()) && EmptyPredicate.isEmpty(webhook.getProjectIdentifier())) {
      return "ACCOUNT";
    } else if (EmptyPredicate.isNotEmpty(webhook.getOrgIdentifier())
        && EmptyPredicate.isEmpty(webhook.getProjectIdentifier())) {
      return "ORG";
    } else if (EmptyPredicate.isNotEmpty(webhook.getOrgIdentifier())
        && EmptyPredicate.isNotEmpty(webhook.getProjectIdentifier())) {
      return "PROJECT";
    }
    return null;
  }
}
