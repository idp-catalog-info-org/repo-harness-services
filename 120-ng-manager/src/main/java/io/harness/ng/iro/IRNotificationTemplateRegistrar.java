/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import static io.harness.annotations.dev.HarnessTeam.IRO;

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

@OwnedBy(IRO)
@Singleton
@Slf4j

public class IRNotificationTemplateRegistrar implements Runnable {
  @Inject NotificationClient notificationClient;
  @Override
  public void run() {
    try {
      int timeout = 1;
      List<PredefinedTemplate> templates =
          new ArrayList<>(Arrays.asList(PredefinedTemplate.SLO_ERROR_BUDGET_BURN_RATE_EMAIL,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_MINUTES_EMAIL,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_PERCENTAGE_EMAIL,
              PredefinedTemplate.SLO_ERROR_BUDGET_BURN_RATE_MSTEAMS,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_MINUTES_MSTEAMS,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_PERCENTAGE_MSTEAMS,
              PredefinedTemplate.SLO_ERROR_BUDGET_BURN_RATE_MSTEAMS_WF,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_MINUTES_MSTEAMS_WF,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_PERCENTAGE_MSTEAMS_WF,
              PredefinedTemplate.SLO_ERROR_BUDGET_BURN_RATE_SLACK,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_MINUTES_SLACK,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_PERCENTAGE_SLACK,
              PredefinedTemplate.SLO_ERROR_BUDGET_BURN_RATE_PAGERDUTY,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_MINUTES_PAGERDUTY,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_PERCENTAGE_PAGERDUTY,
              PredefinedTemplate.SLO_ERROR_BUDGET_BURN_RATE_WEBHOOK,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_MINUTES_WEBHOOK,
              PredefinedTemplate.SLO_ERROR_BUDGET_REMAINING_PERCENTAGE_WEBHOOK));
      while (true) {
        List<PredefinedTemplate> unprocessedTemplate = new ArrayList<>();
        for (PredefinedTemplate template : templates) {
          log.info("Registering {} with NotificationService", template);
          try {
            notificationClient.saveNotificationTemplate(Team.OTHER, template, true);
          } catch (Exception ex) {
            log.error(String.format("Unable to register template with id: %s", template.getIdentifier()), ex);
            unprocessedTemplate.add(template);
          }
        }
        if (unprocessedTemplate.isEmpty()) {
          break;
        }

        Thread.sleep(timeout);

        timeout *= 10;
        templates = unprocessedTemplate;
      }
    } catch (InterruptedException e) {
      log.error("", e);
    }
  }
}
