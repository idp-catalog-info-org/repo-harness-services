/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.channelDetails;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.SlackChannel;
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
public class PmsSlackChannelTest extends CategoryTest {
  List<String> userGroups = new ArrayList<>();
  ParameterField<String> webhookUrl = ParameterField.createValueField("url");
  PmsSlackChannel pmsSlackChannel;
  PmsSlackChannel pmsSlackChannelWithDelegate;
  PmsSlackChannel pmsSlackChannelWithDelegateExpression;
  // Unresolved Expression
  String delegateExpression = "<+pipeline.variables.delegateSelector>";
  List<String> delegateSelectors;
  @Before
  public void setUp() {
    userGroups.add("user");
    userGroups.add("org.user");
    delegateSelectors = new ArrayList<>();
    delegateSelectors.add("test");
    pmsSlackChannel = spy(new PmsSlackChannel(userGroups, webhookUrl, null, false));
    pmsSlackChannelWithDelegate = spy(new PmsSlackChannel(
        userGroups, webhookUrl, ParameterField.<List<String>>builder().value(delegateSelectors).build(), true));
    pmsSlackChannelWithDelegateExpression = spy(new PmsSlackChannel(userGroups, webhookUrl,
        ParameterField.<List<String>>builder().expression(true).expressionValue(delegateExpression).build(), true));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testToNotificationChannel() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    NotificationChannel notificationChannel = pmsSlackChannel.toNotificationChannel(accountId, orgId, projectId,
        templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());
    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((SlackChannel) notificationChannel).getWebhookUrls().get(0), webhookUrl.getValue());
    assertEquals(notificationChannel.getUserGroups().get(0).getOrgIdentifier(), orgId);
    assertEquals(notificationChannel.getUserGroups().get(0).getProjectIdentifier(), projectId);

    assertEquals(notificationChannel.getUserGroups().get(1).getOrgIdentifier(), orgId);
    assertEquals(notificationChannel.getUserGroups().get(1).getProjectIdentifier(), "");

    assertEquals(notificationChannel.isExecuteOnDelegate(), false);
    assertEquals(notificationChannel.getDelegateSelectors().isEmpty(), true);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToNotificationChannelWithDelegate() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    NotificationChannel notificationChannel = pmsSlackChannelWithDelegate.toNotificationChannel(accountId, orgId,
        projectId, templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());
    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((SlackChannel) notificationChannel).getWebhookUrls().get(0), webhookUrl.getValue());
    assertEquals(notificationChannel.getUserGroups().get(0).getOrgIdentifier(), orgId);
    assertEquals(notificationChannel.getUserGroups().get(0).getProjectIdentifier(), projectId);

    assertEquals(notificationChannel.getUserGroups().get(1).getOrgIdentifier(), orgId);
    assertEquals(notificationChannel.getUserGroups().get(1).getProjectIdentifier(), "");

    assertEquals(notificationChannel.isExecuteOnDelegate(), true);
    assertEquals(notificationChannel.getDelegateSelectors().stream().toList(), delegateSelectors);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToNotificationChannelWithDelegateSelector() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    NotificationChannel notificationChannel = pmsSlackChannelWithDelegateExpression.toNotificationChannel(accountId,
        orgId, projectId, templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());
    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((SlackChannel) notificationChannel).getWebhookUrls().get(0), webhookUrl.getValue());
    assertEquals(notificationChannel.getUserGroups().get(0).getOrgIdentifier(), orgId);
    assertEquals(notificationChannel.getUserGroups().get(0).getProjectIdentifier(), projectId);

    assertEquals(notificationChannel.getUserGroups().get(1).getOrgIdentifier(), orgId);
    assertEquals(notificationChannel.getUserGroups().get(1).getProjectIdentifier(), "");

    assertEquals(notificationChannel.isExecuteOnDelegate(), true);
    assertEquals(notificationChannel.getDelegateSelectors().size(), 0);
  }
}
