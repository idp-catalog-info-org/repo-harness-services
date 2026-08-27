/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.channelDetails;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.WebhookChannel;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsWebhookChannelTest extends CategoryTest {
  Map<String, String> headers = new HashMap<>();
  ParameterField<String> webhookUrl = ParameterField.<String>builder().expressionValue("url").build();
  PmsWebhookChannel pmsWebhookChannel;
  PmsWebhookChannel pmsWebhookChannelWithDelegateSelector;
  PmsWebhookChannel pmsWebhookChannelWithDelegateSelectorExpression;
  List<String> delegateSelectors;
  // Unresolved Expression
  String delegateExpression = "<+pipeline.variables.delegateSelector>";
  @Before
  public void setUp() {
    headers.put("key", "val");
    delegateSelectors = new ArrayList<>();
    delegateSelectors.add("delegate1");
    pmsWebhookChannel = spy(new PmsWebhookChannel(webhookUrl, headers, null, false));
    pmsWebhookChannelWithDelegateSelector = spy(new PmsWebhookChannel(
        webhookUrl, headers, ParameterField.<List<String>>builder().value(delegateSelectors).build(), true));
    pmsWebhookChannelWithDelegateSelectorExpression = spy(new PmsWebhookChannel(webhookUrl, headers,
        ParameterField.<List<String>>builder().expression(true).expressionValue(delegateExpression).build(), true));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToNotificationChannel() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    templateData.put("key1", "value1");
    templateData.put("key2", "value2");

    NotificationChannel notificationChannel = pmsWebhookChannel.toNotificationChannel(accountId, orgId, projectId,
        templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());

    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((WebhookChannel) notificationChannel).getOrgIdentifier(), orgId);
    assertEquals(((WebhookChannel) notificationChannel).getProjectIdentifier(), projectId);
    assertEquals(notificationChannel.getTemplateId(), templateId);
    assertEquals(notificationChannel.getTemplateData(), templateData);
    assertEquals(((WebhookChannel) notificationChannel).getHeaders(), headers);
    assertEquals(((WebhookChannel) notificationChannel).getWebhookUrls().get(0), webhookUrl.getExpressionValue());

    assertEquals(notificationChannel.isExecuteOnDelegate(), false);
    assertEquals(notificationChannel.getDelegateSelectors().isEmpty(), true);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToNotificationChannelWithDelegateSelectors() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    templateData.put("key1", "value1");
    templateData.put("key2", "value2");

    NotificationChannel notificationChannel = pmsWebhookChannelWithDelegateSelector.toNotificationChannel(accountId,
        orgId, projectId, templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());

    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((WebhookChannel) notificationChannel).getOrgIdentifier(), orgId);
    assertEquals(((WebhookChannel) notificationChannel).getProjectIdentifier(), projectId);
    assertEquals(notificationChannel.getTemplateId(), templateId);
    assertEquals(notificationChannel.getTemplateData(), templateData);
    assertEquals(((WebhookChannel) notificationChannel).getHeaders(), headers);
    assertEquals(((WebhookChannel) notificationChannel).getWebhookUrls().get(0), webhookUrl.getExpressionValue());

    assertEquals(notificationChannel.isExecuteOnDelegate(), true);
    assertEquals(notificationChannel.getDelegateSelectors().stream().toList(), delegateSelectors);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToNotificationChannelWithDelegateExpression() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    templateData.put("key1", "value1");
    templateData.put("key2", "value2");

    NotificationChannel notificationChannel =
        pmsWebhookChannelWithDelegateSelectorExpression.toNotificationChannel(accountId, orgId, projectId, templateId,
            templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());

    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((WebhookChannel) notificationChannel).getOrgIdentifier(), orgId);
    assertEquals(((WebhookChannel) notificationChannel).getProjectIdentifier(), projectId);
    assertEquals(notificationChannel.getTemplateId(), templateId);
    assertEquals(notificationChannel.getTemplateData(), templateData);
    assertEquals(((WebhookChannel) notificationChannel).getHeaders(), headers);
    assertEquals(((WebhookChannel) notificationChannel).getWebhookUrls().get(0), webhookUrl.getExpressionValue());

    assertEquals(notificationChannel.isExecuteOnDelegate(), true);
    assertEquals(notificationChannel.getDelegateSelectors().size(), 0);
  }
}
