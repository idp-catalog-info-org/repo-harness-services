/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.kafka;

import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.gitops.changestreams.GitopsApplicationsRedisEventHandler;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link GitopsApplicationsCdcMessageHandler}.
 */
@OwnedBy(HarnessTeam.GITOPS)
@RunWith(MockitoJUnitRunner.class)
public class GitopsApplicationsCdcMessageHandlerTest extends CategoryTest {
  private static final String APP_ID = "app-uuid-123";

  @Mock private GitopsApplicationsRedisEventHandler eventHandler;
  @Mock private FeatureFlagService featureFlagService;

  private GitopsApplicationsCdcMessageHandler handler;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new GitopsApplicationsCdcMessageHandler(eventHandler, featureFlagService);
    when(featureFlagService.isGlobalEnabled(FeatureName.CDS_GITOPS_ENABLE_KAFKA_CONNECT)).thenReturn(true);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_ffOff_shortCircuitsWithoutInvokingHandler() {
    when(featureFlagService.isGlobalEnabled(FeatureName.CDS_GITOPS_ENABLE_KAFKA_CONNECT)).thenReturn(false);

    handler.onMessage(buildApplicationJson(APP_ID, "account1", "app1"), createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleCreateEvent(any(), any());
    verify(eventHandler, never()).handleUpdateEvent(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_nullMessage_skipsWithoutInvokingHandler() {
    handler.onMessage(null, createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleCreateEvent(any(), any());
    verify(eventHandler, never()).handleUpdateEvent(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_emptyId_skipsWithoutInvokingHandler() {
    String jsonWithEmptyId = buildApplicationJson("", "account1", "app1");

    handler.onMessage(jsonWithEmptyId, createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleCreateEvent(any(), any());
    verify(eventHandler, never()).handleUpdateEvent(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_ffOn_createOp_delegatesToHandlerWithCorrectlyShapedEvent() throws Exception {
    when(eventHandler.handleCreateEvent(any(), any())).thenReturn(true);

    handler.onMessage(buildApplicationJson(APP_ID, "account1", "app1"), createOpHeaders("c"), emptyMetricInfo());

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
    verify(eventHandler, times(1)).handleCreateEvent(keyCaptor.capture(), valueCaptor.capture());

    JsonNode key = new ObjectMapper().readTree(keyCaptor.getValue());
    assertThat(key.get("id").asText()).isEqualTo(APP_ID);

    JsonNode value = new ObjectMapper().readTree(valueCaptor.getValue());
    assertThat(value.get("_id").asText()).isEqualTo(APP_ID);
    assertThat(value.get("accountIdentifier").asText()).isEqualTo("account1");
    assertThat(value.get("name").asText()).isEqualTo("app1");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_ffOn_updateOp_callsUpdateHandler() {
    when(eventHandler.handleUpdateEvent(any(), any())).thenReturn(true);

    handler.onMessage(buildApplicationJson(APP_ID, "account1", "app1"), createOpHeaders("u"), emptyMetricInfo());

    verify(eventHandler, times(1)).handleUpdateEvent(any(), any());
    verify(eventHandler, never()).handleCreateEvent(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_transientFailureThenSuccess_retriesUntilSuccess() {
    when(eventHandler.handleCreateEvent(any(), any()))
        .thenThrow(new RuntimeException("transient 1"))
        .thenThrow(new RuntimeException("transient 2"))
        .thenReturn(true);

    handler.onMessage(buildApplicationJson(APP_ID, "account1", "app1"), createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, times(3)).handleCreateEvent(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_persistentFailure_swallowsAfterMaxRetries() {
    when(eventHandler.handleCreateEvent(any(), any())).thenThrow(new RuntimeException("permanent"));

    handler.onMessage(buildApplicationJson(APP_ID, "account1", "app1"), createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, times(AbstractGitopsJsonCdcMessageHandler.MAX_RETRIES)).handleCreateEvent(any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void extractOptype_mapsDebeziumOpCodes() {
    assertThat(AbstractGitopsJsonCdcMessageHandler.extractOptype(createOpHeaders("c"))).isEqualTo("CREATE");
    assertThat(AbstractGitopsJsonCdcMessageHandler.extractOptype(createOpHeaders("u"))).isEqualTo("UPDATE");
    assertThat(AbstractGitopsJsonCdcMessageHandler.extractOptype(createOpHeaders("d"))).isEqualTo("DELETE");
    assertThat(AbstractGitopsJsonCdcMessageHandler.extractOptype(createOpHeaders("r"))).isEqualTo("CREATE");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void extractOptype_missingOrUnknownHeader_returnsSafeDefault() {
    assertThat(AbstractGitopsJsonCdcMessageHandler.extractOptype(null)).isEqualTo("UNKNOWN");
    assertThat(AbstractGitopsJsonCdcMessageHandler.extractOptype(Collections.emptyMap())).isEqualTo("UNKNOWN");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void extractId_parsesJsonAndExtractsIdField() {
    String json = buildApplicationJson("app-abc", "acc1", "myapp");
    assertThat(AbstractGitopsJsonCdcMessageHandler.extractId(json)).isEqualTo("app-abc");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void normalizeMongoLabelKeys_replaceTildeWithDot() throws Exception {
    String jsonWithTilde = buildApplicationJsonWithLabels(
        APP_ID, "acc1", "app1", Map.of("harness~io/serviceRef", "my-service", "app~kubernetes~io/name", "myapp"));

    String normalized = AbstractGitopsJsonCdcMessageHandler.normalizeMongoLabelKeys(jsonWithTilde);

    JsonNode root = new ObjectMapper().readTree(normalized);
    JsonNode labels = root.at("/app/objectmeta/labels");
    assertThat(labels.has("harness.io/serviceRef")).isTrue();
    assertThat(labels.get("harness.io/serviceRef").asText()).isEqualTo("my-service");
    assertThat(labels.has("app.kubernetes.io/name")).isTrue();
    assertThat(labels.get("app.kubernetes.io/name").asText()).isEqualTo("myapp");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void normalizeMongoLabelKeys_noLabels_returnsOriginal() {
    String jsonWithoutLabels = buildApplicationJson(APP_ID, "acc1", "app1");
    String result = AbstractGitopsJsonCdcMessageHandler.normalizeMongoLabelKeys(jsonWithoutLabels);
    assertThat(result).isEqualTo(jsonWithoutLabels);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void getTopicName_returnsApplications() {
    assertThat(handler.getTopicName()).isEqualTo("applications");
  }

  private static String buildApplicationJson(String id, String accountId, String appName) {
    return String.format("{\"_id\":\"%s\",\"accountIdentifier\":\"%s\",\"name\":\"%s\",\"createdAt\":1700000000000}",
        id, accountId, appName);
  }

  private static String buildApplicationJsonWithLabels(
      String id, String accountId, String appName, Map<String, String> labels) {
    StringBuilder labelsJson = new StringBuilder("{");
    int i = 0;
    for (Map.Entry<String, String> entry : labels.entrySet()) {
      if (i++ > 0) {
        labelsJson.append(",");
      }
      labelsJson.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
    }
    labelsJson.append("}");

    return String.format(
        "{\"_id\":\"%s\",\"accountIdentifier\":\"%s\",\"name\":\"%s\",\"app\":{\"status\":{\"operationstate\":{"
            + "\"startedat\":{\"time\":1700000000000}}},\"objectmeta\":{\"labels\":%s}},\"createdAt\":1700000000000}",
        id, accountId, appName, labelsJson);
  }

  private static Map<String, String> createOpHeaders(String op) {
    Map<String, String> headers = new HashMap<>();
    headers.put(AbstractGitopsJsonCdcMessageHandler.OP_HEADER, op);
    return headers;
  }

  private static Map<String, Object> emptyMetricInfo() {
    return new HashMap<>();
  }
}