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
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsEmailChannelTest extends CategoryTest {
  List<String> userGroups = new ArrayList<>();
  List<String> recipients = Collections.singletonList("reciepient@gmail.com");

  @Before
  public void setUp() {
    userGroups.add("user");
    userGroups.add("org.user");
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
    PmsEmailChannel pmsEmailChannel = spy(new PmsEmailChannel(userGroups, recipients, null, false));
    NotificationChannel notificationChannel = pmsEmailChannel.toNotificationChannel(accountId, orgId, projectId,
        templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());
    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((EmailChannel) notificationChannel).getRecipients(), recipients);
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
  public void testToNotificationChannelWithExecuteOnDelegate() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    List<String> delegateSelectors = new ArrayList<>();
    delegateSelectors.add("delegate1");
    PmsEmailChannel pmsEmailChannelWithDelegate = spy(new PmsEmailChannel(
        userGroups, recipients, ParameterField.<List<String>>builder().value(delegateSelectors).build(), true));
    NotificationChannel notificationChannelWithDelegate = pmsEmailChannelWithDelegate.toNotificationChannel(accountId,
        orgId, projectId, templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(125L).build());
    assertEquals(notificationChannelWithDelegate.getAccountId(), accountId);
    assertEquals(((EmailChannel) notificationChannelWithDelegate).getRecipients(), recipients);
    assertEquals(notificationChannelWithDelegate.getUserGroups().get(0).getOrgIdentifier(), orgId);
    assertEquals(notificationChannelWithDelegate.getUserGroups().get(0).getProjectIdentifier(), projectId);

    assertEquals(notificationChannelWithDelegate.getUserGroups().get(1).getOrgIdentifier(), orgId);
    assertEquals(notificationChannelWithDelegate.getUserGroups().get(1).getProjectIdentifier(), "");

    assertEquals(notificationChannelWithDelegate.isExecuteOnDelegate(), true);
    assertEquals(notificationChannelWithDelegate.getDelegateSelectors().stream().toList(), delegateSelectors);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToNotificationChannelWithDelegateExpressions() {
    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    // Unresolved Expression
    String delegateExpression = "<+pipeline.variables.delegateSelector>";
    PmsEmailChannel pmsEmailChannelWithDelegate = spy(new PmsEmailChannel(userGroups, recipients,
        ParameterField.<List<String>>builder().expression(true).expressionValue(delegateExpression).build(), true));
    NotificationChannel notificationChannelWithDelegate = pmsEmailChannelWithDelegate.toNotificationChannel(accountId,
        orgId, projectId, templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(125L).build());
    assertEquals(notificationChannelWithDelegate.getAccountId(), accountId);
    assertEquals(((EmailChannel) notificationChannelWithDelegate).getRecipients(), recipients);
    assertEquals(notificationChannelWithDelegate.getUserGroups().get(0).getOrgIdentifier(), orgId);
    assertEquals(notificationChannelWithDelegate.getUserGroups().get(0).getProjectIdentifier(), projectId);

    assertEquals(notificationChannelWithDelegate.getUserGroups().get(1).getOrgIdentifier(), orgId);
    assertEquals(notificationChannelWithDelegate.getUserGroups().get(1).getProjectIdentifier(), "");

    assertEquals(notificationChannelWithDelegate.isExecuteOnDelegate(), true);
    assertEquals(notificationChannelWithDelegate.getDelegateSelectors().size(), 0);
  }
}
