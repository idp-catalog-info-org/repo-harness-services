/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.ng.webhook.entities.WebhookEvent;

import javax.ws.rs.core.MultivaluedMap;

@OwnedBy(PIPELINE)
public interface WebhookService {
  WebhookEvent createWebhookEvent(
      Scope scope, GitXWebhook webhook, MultivaluedMap<String, String> headers, String payload);

  WebhookEvent addEventToQueue(WebhookEvent webhookEvent);
}
