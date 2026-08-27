/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.resource;

import static io.harness.NGCommonEntityConstants.GENERIC_WEBHOOK_TYPE;
import static io.harness.NGCommonEntityConstants.SLACK_WEBHOOK_TYPE;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.WebhookPayloadService;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.resources.NgWebhookResource;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.rule.Owner;
import io.harness.telemetry.helpers.WebhookInstrumentationHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NgWebhookResourceTest extends CategoryTest {
  @InjectMocks NgWebhookResource ngWebhookResource;
  @Mock ContainerRequest containerRequest;
  @Mock WebhookHelper webhookHelper;
  @Mock WebhookService webhookService;
  @Mock WebhookPayloadService webhookPayloadService;
  @Mock GitXWebhookService gitXWebhookService;
  String accountId = "accountId";
  String eventPayload = "eventPayload";
  @Mock WebhookInstrumentationHelper webhookInstrumentationHelper;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testProcessWebhookEvent() {
    MultivaluedMap<String, String> httpHeaders = new MultivaluedHashMap<>();
    httpHeaders.add("key", "value");
    when(containerRequest.getRequestHeaders()).thenReturn(httpHeaders);
    when(gitXWebhookService.getWebhookByIdentifier(any(), any(), any(), eq("unknown"))).thenReturn(Optional.empty());
    assertThatThrownBy(
        () -> ngWebhookResource.processWebhookEvent(accountId, null, null, "unknown", eventPayload, containerRequest))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("Webhook with identifier unknown not found");

    // Test generic event
    WebhookEvent newEvent = WebhookEvent.builder().uuid("generic_uuid").build();
    var webhook = GitXWebhook.builder().webhookType(GENERIC_WEBHOOK_TYPE).accountIdentifier(accountId).build();
    when(gitXWebhookService.getWebhookByIdentifier(eq(accountId), isNull(), isNull(), eq("generic")))
        .thenReturn(Optional.of(webhook));
    when(webhookService.createWebhookEvent(any(), eq(webhook), eq(httpHeaders), any())).thenReturn(newEvent);
    assertThat(((ResponseDTO<String>) ngWebhookResource.processWebhookEvent(
                    accountId, null, null, "generic", eventPayload, containerRequest))
                   .getData())
        .isEqualTo("generic_uuid");
    verify(webhookInstrumentationHelper, times(1)).sendNonGitWebhookEvent(accountId, webhook);

    // Test generic slack event without url verification
    newEvent = WebhookEvent.builder().uuid("slack_uuid").build();
    webhook = GitXWebhook.builder().webhookType(SLACK_WEBHOOK_TYPE).accountIdentifier(accountId).build();
    when(gitXWebhookService.getWebhookByIdentifier(eq(accountId), isNull(), isNull(), eq("slack")))
        .thenReturn(Optional.of(webhook));
    when(webhookService.createWebhookEvent(any(), eq(webhook), eq(httpHeaders), any())).thenReturn(newEvent);
    assertThat(((ResponseDTO<String>) ngWebhookResource.processWebhookEvent(accountId, null, null, "slack",
                    "{\n"
                        + "\t\"type\": \"bot_added\",\n"
                        + "\t\"bot\": {\n"
                        + "\t\t\"id\": \"B024BE7LH\",\n"
                        + "\t\t\"app_id\": \"A4H1JB4AZ\",\n"
                        + "\t\t\"name\": \"hugbot\",\n"
                        + "\t\t\"icons\": {\n"
                        + "\t\t\t\"image_48\": \"https:\\/\\/slack.com\\/path\\/to\\/hugbot_48.png\"\n"
                        + "\t\t}\n"
                        + "\t}\n"
                        + "}",
                    containerRequest))
                   .getData())
        .isEqualTo("slack_uuid");
    verify(webhookInstrumentationHelper, times(1)).sendNonGitWebhookEvent(accountId, webhook);

    // Test slack url verification
    newEvent = WebhookEvent.builder().uuid("slack_uuid").build();
    webhook = GitXWebhook.builder().webhookType(SLACK_WEBHOOK_TYPE).accountIdentifier(accountId).build();
    when(gitXWebhookService.getWebhookByIdentifier(eq(accountId), isNull(), isNull(), eq("slack")))
        .thenReturn(Optional.of(webhook));
    when(webhookService.createWebhookEvent(any(), eq(webhook), eq(httpHeaders), any())).thenReturn(newEvent);
    var event =
        "{\"token\": \"Jhj5dZrVaK7ZwHHjRyZWjbDl\", \"challenge\": \"3eZbrw1aBm2rZgRNFdxV2595E9CY3gmdALWMmHkvFXO7tYXAYM8P\", \"type\": \"url_verification\"}";
    assertThat((ngWebhookResource.processWebhookEvent(accountId, null, null, "slack", event, containerRequest)))
        .isEqualTo(event);
    verify(webhookInstrumentationHelper, times(1)).sendNonGitWebhookEvent(accountId, webhook);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetWebhookAllPayloadData() throws IOException {
    String uuid = "uuid";
    when(webhookPayloadService.readWebhookAllPayloadDataToStreamingOutput(uuid)).thenReturn(outputStream -> {
      outputStream.write("abc".getBytes(StandardCharsets.UTF_8));
      outputStream.close();
    });
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ngWebhookResource.getWebhookPayload("accountId", uuid).write(outputStream);
    assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("abc");
    verify(webhookPayloadService, times(1)).readWebhookAllPayloadDataToStreamingOutput(uuid);
  }
}
