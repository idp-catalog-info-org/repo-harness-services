/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.ait;

import static io.harness.rule.OwnerRule.SHUBHAM_AGARWAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.notification.Team;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.remote.dto.TemplateDTO;
import io.harness.notification.templates.PredefinedTemplate;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.AIT)
public class AitNotificationTemplateRegistrarTest extends CategoryTest {
  @Mock private NotificationClient notificationClient;

  private AitNotificationTemplateRegistrar registrar;

  @Before
  public void setup() {
    registrar = new AitNotificationTemplateRegistrar();
    registrar.notificationClient = notificationClient;
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testAllTemplatesRegisteredSuccessfully() {
    doReturn(TemplateDTO.builder().build())
        .when(notificationClient)
        .saveNotificationTemplate(any(), any(), anyBoolean());

    registrar.run();

    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.AIT, PredefinedTemplate.AIT_PLAYWRIGHT_RUN_COMPLETED_EMAIL, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.AIT, PredefinedTemplate.AIT_PLAYWRIGHT_RUN_COMPLETED_SLACK, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.AIT, PredefinedTemplate.AIT_PLAYWRIGHT_RUN_FAILED_EMAIL, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.AIT, PredefinedTemplate.AIT_PLAYWRIGHT_RUN_FAILED_SLACK, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.AIT, PredefinedTemplate.AIT_PLAYWRIGHT_RUN_ABORTED_EMAIL, true);
    verify(notificationClient, times(1))
        .saveNotificationTemplate(Team.AIT, PredefinedTemplate.AIT_PLAYWRIGHT_RUN_ABORTED_SLACK, true);
    verify(notificationClient, times(6)).saveNotificationTemplate(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testRetriesOnFailure() {
    doThrow(new RuntimeException("Connection refused"))
        .doReturn(TemplateDTO.builder().build())
        .when(notificationClient)
        .saveNotificationTemplate(any(), any(), anyBoolean());

    registrar.run();

    // First template fails (1 call), remaining 5 succeed, then retry succeeds (1 call) = 7 total
    verify(notificationClient, times(7)).saveNotificationTemplate(any(), any(), anyBoolean());
  }
}
