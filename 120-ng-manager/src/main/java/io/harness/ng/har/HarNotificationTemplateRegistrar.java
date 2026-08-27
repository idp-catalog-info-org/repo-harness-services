/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.har;

import static io.harness.annotations.dev.HarnessTeam.HAR;

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

@OwnedBy(HAR)
@Singleton
@Slf4j
public class HarNotificationTemplateRegistrar implements Runnable {
  @Inject NotificationClient notificationClient;

  @Override
  public void run() {
    try {
      int timeout = 2000;
      List<PredefinedTemplate> templates =
          new ArrayList<>(Arrays.asList(PredefinedTemplate.HAR_FIREWALL_EXCEPTION_CREATED_EMAIL,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_CREATED_SLACK,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_CREATED_MSTEAMS,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_CREATED_MSTEAMS_WF,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_CREATED_PAGERDUTY,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_CREATED_WEBHOOK,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_CREATED_DATADOG,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_STATUS_CHANGED_EMAIL,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_STATUS_CHANGED_SLACK,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_STATUS_CHANGED_MSTEAMS,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_STATUS_CHANGED_MSTEAMS_WF,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_STATUS_CHANGED_PAGERDUTY,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_STATUS_CHANGED_WEBHOOK,
              PredefinedTemplate.HAR_FIREWALL_EXCEPTION_STATUS_CHANGED_DATADOG,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_EXECUTION_COMPLETED_EMAIL,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_EXECUTION_COMPLETED_SLACK,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_EXECUTION_COMPLETED_MSTEAMS,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_EXECUTION_COMPLETED_MSTEAMS_WF,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_EXECUTION_COMPLETED_PAGERDUTY,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_EXECUTION_COMPLETED_WEBHOOK,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_EXECUTION_COMPLETED_DATADOG,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_DRY_RUN_EXECUTION_COMPLETED_EMAIL,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_DRY_RUN_EXECUTION_COMPLETED_SLACK,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_DRY_RUN_EXECUTION_COMPLETED_MSTEAMS,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_DRY_RUN_EXECUTION_COMPLETED_MSTEAMS_WF,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_DRY_RUN_EXECUTION_COMPLETED_PAGERDUTY,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_DRY_RUN_EXECUTION_COMPLETED_WEBHOOK,
              PredefinedTemplate.HAR_LIFECYCLE_POLICY_DRY_RUN_EXECUTION_COMPLETED_DATADOG));

      int maxRetries = 10;
      int retryCount = 0;
      while (retryCount < maxRetries) {
        List<PredefinedTemplate> unprocessedTemplate = new ArrayList<>();
        for (PredefinedTemplate template : templates) {
          log.info("Registering {} with NotificationService", template);
          try {
            notificationClient.saveNotificationTemplate(Team.HAR, template, true);
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
      log.error("HAR template registration was interrupted", e);
    }
  }
}
