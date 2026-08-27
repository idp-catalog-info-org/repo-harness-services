/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.ait;

import static io.harness.annotations.dev.HarnessTeam.AIT;

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

@OwnedBy(AIT)
@Singleton
@Slf4j
public class AitNotificationTemplateRegistrar implements Runnable {
  @Inject NotificationClient notificationClient;

  @Override
  public void run() {
    try {
      int timeout = 2000;
      List<PredefinedTemplate> templates = new ArrayList<>(Arrays.asList(
          PredefinedTemplate.AIT_PLAYWRIGHT_RUN_COMPLETED_EMAIL, PredefinedTemplate.AIT_PLAYWRIGHT_RUN_COMPLETED_SLACK,
          PredefinedTemplate.AIT_PLAYWRIGHT_RUN_FAILED_EMAIL, PredefinedTemplate.AIT_PLAYWRIGHT_RUN_FAILED_SLACK,
          PredefinedTemplate.AIT_PLAYWRIGHT_RUN_ABORTED_EMAIL, PredefinedTemplate.AIT_PLAYWRIGHT_RUN_ABORTED_SLACK));

      int maxRetries = 10;
      int retryCount = 0;
      while (retryCount < maxRetries) {
        List<PredefinedTemplate> unprocessedTemplate = new ArrayList<>();
        for (PredefinedTemplate template : templates) {
          log.info("Registering {} with NotificationService", template);
          try {
            notificationClient.saveNotificationTemplate(Team.AIT, template, true);
          } catch (Exception ex) {
            log.error("Unable to register template {}", template.getIdentifier(), ex);
            unprocessedTemplate.add(template);
          }
        }

        if (unprocessedTemplate.isEmpty()) {
          break;
        }

        Thread.sleep(timeout);

        timeout *= 2;
        templates = unprocessedTemplate;
        retryCount++;
      }
    } catch (InterruptedException e) {
      log.error("AIT template registration was interrupted", e);
    }
  }
}
