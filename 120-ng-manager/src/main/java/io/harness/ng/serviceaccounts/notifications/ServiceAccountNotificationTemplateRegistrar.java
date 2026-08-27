/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.notifications;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.Team;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.templates.PredefinedTemplate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Singleton
@Slf4j
public class ServiceAccountNotificationTemplateRegistrar implements Runnable {
  private final NotificationClient notificationClient;

  @Inject
  public ServiceAccountNotificationTemplateRegistrar(NotificationClient notificationClient) {
    this.notificationClient = notificationClient;
  }

  @Override
  public void run() {
    try {
      int timeout = 100;
      int maxTimeout = 1_200_000;
      List<PredefinedTemplate> templates = new ArrayList<>(Arrays.asList(
          // Email
          PredefinedTemplate.TOKEN_CREATED_EMAIL, PredefinedTemplate.TOKEN_EDITED_EMAIL,
          PredefinedTemplate.TOKEN_ROTATED_EMAIL, PredefinedTemplate.TOKEN_EXPIRED_EMAIL,
          PredefinedTemplate.TOKEN_DELETED_EMAIL, PredefinedTemplate.TOKEN_ABOUT_TO_EXPIRE_EMAIL,
          // Slack
          PredefinedTemplate.TOKEN_CREATED_SLACK, PredefinedTemplate.TOKEN_EDITED_SLACK,
          PredefinedTemplate.TOKEN_ROTATED_SLACK, PredefinedTemplate.TOKEN_EXPIRED_SLACK,
          PredefinedTemplate.TOKEN_DELETED_SLACK, PredefinedTemplate.TOKEN_ABOUT_TO_EXPIRE_SLACK,
          // MS Teams
          PredefinedTemplate.TOKEN_CREATED_MSTEAMS, PredefinedTemplate.TOKEN_EDITED_MSTEAMS,
          PredefinedTemplate.TOKEN_ROTATED_MSTEAMS, PredefinedTemplate.TOKEN_EXPIRED_MSTEAMS,
          PredefinedTemplate.TOKEN_DELETED_MSTEAMS, PredefinedTemplate.TOKEN_ABOUT_TO_EXPIRE_MSTEAMS,
          // MS Teams Workflow
          PredefinedTemplate.TOKEN_CREATED_MSTEAMS_WF, PredefinedTemplate.TOKEN_EDITED_MSTEAMS_WF,
          PredefinedTemplate.TOKEN_ROTATED_MSTEAMS_WF, PredefinedTemplate.TOKEN_EXPIRED_MSTEAMS_WF,
          PredefinedTemplate.TOKEN_DELETED_MSTEAMS_WF, PredefinedTemplate.TOKEN_ABOUT_TO_EXPIRE_MSTEAMS_WF,
          // PagerDuty
          PredefinedTemplate.TOKEN_CREATED_PAGERDUTY, PredefinedTemplate.TOKEN_EDITED_PAGERDUTY,
          PredefinedTemplate.TOKEN_ROTATED_PAGERDUTY, PredefinedTemplate.TOKEN_EXPIRED_PAGERDUTY,
          PredefinedTemplate.TOKEN_DELETED_PAGERDUTY, PredefinedTemplate.TOKEN_ABOUT_TO_EXPIRE_PAGERDUTY,
          // Datadog
          PredefinedTemplate.TOKEN_CREATED_DATADOG, PredefinedTemplate.TOKEN_EDITED_DATADOG,
          PredefinedTemplate.TOKEN_ROTATED_DATADOG, PredefinedTemplate.TOKEN_EXPIRED_DATADOG,
          PredefinedTemplate.TOKEN_DELETED_DATADOG, PredefinedTemplate.TOKEN_ABOUT_TO_EXPIRE_DATADOG,
          // Webhook
          PredefinedTemplate.TOKEN_CREATED_WEBHOOK, PredefinedTemplate.TOKEN_EDITED_WEBHOOK,
          PredefinedTemplate.TOKEN_ROTATED_WEBHOOK, PredefinedTemplate.TOKEN_EXPIRED_WEBHOOK,
          PredefinedTemplate.TOKEN_DELETED_WEBHOOK, PredefinedTemplate.TOKEN_ABOUT_TO_EXPIRE_WEBHOOK));

      while (true) {
        List<PredefinedTemplate> unprocessedTemplates = new ArrayList<>();
        for (PredefinedTemplate template : templates) {
          log.info("Registering {} with the NotificationService", template);
          try {
            notificationClient.saveNotificationTemplate(Team.OTHER, template, true);
          } catch (Exception e) {
            log.error(String.format("Could not register template with id: %s", template.getIdentifier()), e);
            unprocessedTemplates.add(template);
          }
        }

        if (unprocessedTemplates.isEmpty()) {
          break;
        }

        Thread.sleep(timeout);

        timeout = Math.min(timeout * 2, maxTimeout);
        templates = unprocessedTemplates;
      }
    } catch (InterruptedException e) {
      log.error("ServiceAccount Template Registration was interrupted", e);
      Thread.currentThread().interrupt();
    }
  }
}
