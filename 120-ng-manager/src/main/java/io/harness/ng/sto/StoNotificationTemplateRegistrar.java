/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.sto;

import static io.harness.annotations.dev.HarnessTeam.STO;

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

@OwnedBy(STO)
@Singleton
@Slf4j
public class StoNotificationTemplateRegistrar implements Runnable {
  @Inject NotificationClient notificationClient;

  @Override
  public void run() {
    try {
      int timeout = 2;
      List<PredefinedTemplate> templates = new ArrayList<>(Arrays.asList(
          PredefinedTemplate.STO_EXEMPTION_REQUESTED_EMAIL, PredefinedTemplate.STO_EXEMPTION_REQUESTED_MSTEAMS,
          PredefinedTemplate.STO_EXEMPTION_REQUESTED_MSTEAMS_WF, PredefinedTemplate.STO_EXEMPTION_REQUESTED_PAGERDUTY,
          PredefinedTemplate.STO_EXEMPTION_REQUESTED_SLACK, PredefinedTemplate.STO_EXEMPTION_REQUESTED_WEBHOOK,
          PredefinedTemplate.STO_EXEMPTION_STATUS_CHANGED_EMAIL,
          PredefinedTemplate.STO_EXEMPTION_STATUS_CHANGED_MSTEAMS,
          PredefinedTemplate.STO_EXEMPTION_STATUS_CHANGED_MSTEAMS_WF,
          PredefinedTemplate.STO_EXEMPTION_STATUS_CHANGED_PAGERDUTY,
          PredefinedTemplate.STO_EXEMPTION_STATUS_CHANGED_SLACK,
          PredefinedTemplate.STO_EXEMPTION_STATUS_CHANGED_WEBHOOK,
          PredefinedTemplate.STO_QWIET_TRIAL_ACTIVATION_CUSTOMER_EMAIL,
          PredefinedTemplate.STO_QWIET_TRIAL_EXPIRY_CUSTOMER_EMAIL, PredefinedTemplate.STO_QWIET_TRIAL_INTERNAL_EMAIL,
          PredefinedTemplate.STO_QWIET_TRIAL_EXPIRY_INTERNAL_EMAIL));

      while (true) {
        List<PredefinedTemplate> unprocessedTemplate = new ArrayList<>();
        for (PredefinedTemplate template : templates) {
          log.info("Registering {} with NotificationService", template);
          try {
            notificationClient.saveNotificationTemplate(Team.OTHER, template, true);
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
      }
    } catch (InterruptedException e) {
      log.error("STO template registration was interrupted", e);
    }
  }
}
