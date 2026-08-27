/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2025/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.channelDetails;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.notification.channeldetails.DatadogChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class PmsDatadogChannelTest extends CategoryTest {
  Map<String, String> headers = Map.of("key", "val");
  ParameterField<String> apiKey;
  ParameterField<String> url;
  PmsDatadogChannel pmsDatadogChannel;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testToNotificationChannel() {
    url = ParameterField.createValueField("datadog_url");
    apiKey = ParameterField.createValueField("api_key");
    pmsDatadogChannel = spy(new PmsDatadogChannel(apiKey, url, headers, null, false));

    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    templateData.put("someKey", "someVal");
    NotificationChannel notificationChannel = pmsDatadogChannel.toNotificationChannel(accountId, orgId, projectId,
        templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());
    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((DatadogChannel) notificationChannel).getDatadogURLs().size(), 1);
    assertEquals(((DatadogChannel) notificationChannel).getDatadogURLs().get(0), url.obtainValue());
    assertEquals(((DatadogChannel) notificationChannel).getApiKey(), apiKey.obtainValue());
    assertEquals(((DatadogChannel) notificationChannel).getHeaders(), headers);
    assertEquals(notificationChannel.getTemplateId(), templateId);
    assertEquals(notificationChannel.getTemplateData(), templateData);
    assertFalse(((DatadogChannel) notificationChannel).isExecuteOnDelegate());
    assertEquals(((DatadogChannel) notificationChannel).getDelegateSelectors(), Collections.emptySet());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testToNotificationChannelWithExecuteOnDelegate() {
    url = ParameterField.createValueField("datadog_url");
    apiKey = ParameterField.createValueField("api_key");
    List<String> delegateSelectors = new ArrayList<>();
    delegateSelectors.add("delegate1");
    pmsDatadogChannel = spy(new PmsDatadogChannel(
        apiKey, url, headers, ParameterField.<List<String>>builder().value(delegateSelectors).build(), true));

    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    templateData.put("someKey", "someVal");
    NotificationChannel notificationChannel = pmsDatadogChannel.toNotificationChannel(accountId, orgId, projectId,
        templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());
    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((DatadogChannel) notificationChannel).getDatadogURLs().size(), 1);
    assertEquals(((DatadogChannel) notificationChannel).getDatadogURLs().get(0), url.obtainValue());
    assertEquals(((DatadogChannel) notificationChannel).getApiKey(), apiKey.obtainValue());
    assertEquals(((DatadogChannel) notificationChannel).getHeaders(), headers);
    assertEquals(notificationChannel.getTemplateId(), templateId);
    assertEquals(notificationChannel.getTemplateData(), templateData);

    assertEquals(((DatadogChannel) notificationChannel).isExecuteOnDelegate(), true);
    assertEquals(((DatadogChannel) notificationChannel).getDelegateSelectors().stream().toList(), delegateSelectors);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToNotificationChannelWithDelegateExpression() {
    url = ParameterField.createValueField("datadog_url");
    apiKey = ParameterField.createValueField("api_key");
    // Unresolved Expression
    String delegateExpression = "<+pipeline.variables.delegateSelector>";
    pmsDatadogChannel = spy(new PmsDatadogChannel(apiKey, url, headers,
        ParameterField.<List<String>>builder().expression(true).expressionValue(delegateExpression).build(), true));

    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    templateData.put("someKey", "someVal");
    NotificationChannel notificationChannel = pmsDatadogChannel.toNotificationChannel(accountId, orgId, projectId,
        templateId, templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build());
    assertEquals(notificationChannel.getAccountId(), accountId);
    assertEquals(((DatadogChannel) notificationChannel).getDatadogURLs().size(), 1);
    assertEquals(((DatadogChannel) notificationChannel).getDatadogURLs().get(0), url.obtainValue());
    assertEquals(((DatadogChannel) notificationChannel).getApiKey(), apiKey.obtainValue());
    assertEquals(((DatadogChannel) notificationChannel).getHeaders(), headers);
    assertEquals(notificationChannel.getTemplateId(), templateId);
    assertEquals(notificationChannel.getTemplateData(), templateData);

    assertEquals(((DatadogChannel) notificationChannel).isExecuteOnDelegate(), true);
    assertEquals(((DatadogChannel) notificationChannel).getDelegateSelectors().size(), 0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testToNotificationChannelWithNoURL() {
    url = ParameterField.createValueField("");
    apiKey = ParameterField.createValueField("api_key");
    pmsDatadogChannel = spy(new PmsDatadogChannel(apiKey, url, headers, null, false));

    String accountId = "accountId";
    String orgId = "orgId";
    String projectId = "projectId";
    String templateId = "templateId";
    Map<String, String> templateData = new HashMap<>();
    templateData.put("someKey", "someVal");
    assertThatThrownBy(()
                           -> pmsDatadogChannel.toNotificationChannel(accountId, orgId, projectId, templateId,
                               templateData, Ambiance.newBuilder().setExpressionFunctorToken(123L).build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Datadog URL is not provided for sending the notification to datadog channel");
  }
}
