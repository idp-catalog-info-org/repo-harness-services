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
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.ff.FeatureFlagService;
import io.harness.kafka.consumers.HKafkaConsumer;
import io.harness.ng.gitops.changestreams.GitOpsUtilizationSnapshotRedisEventHandler;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link GitOpsUtilizationSnapshotCdcMessageHandler}.
 *
 * <p>These tests validate the handler in isolation: feature-flag gating, id/optype/timestamp
 * extraction, synthetic-key formatting, Avro→plain-JSON conversion, and the bounded retry loop.
 * The downstream {@link GitOpsUtilizationSnapshotRedisEventHandler} is mocked.
 */
@OwnedBy(HarnessTeam.GITOPS)
@RunWith(MockitoJUnitRunner.class)
public class GitOpsUtilizationSnapshotCdcMessageHandlerTest extends CategoryTest {
  private static final String SNAPSHOT_ID = "snapshot-uuid-123";

  @Mock private GitOpsUtilizationSnapshotRedisEventHandler eventHandler;
  @Mock private FeatureFlagService featureFlagService;

  private GitOpsUtilizationSnapshotCdcMessageHandler handler;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new GitOpsUtilizationSnapshotCdcMessageHandler(eventHandler, featureFlagService);
    // Default: FF ON so tests exercise the full path unless they explicitly disable it.
    when(featureFlagService.isGlobalEnabled(FeatureName.CDS_GITOPS_ENABLE_KAFKA_CONNECT)).thenReturn(true);
  }

  // ---------------------------------------------------------------------------
  // Feature flag gating
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_ffOff_shortCircuitsWithoutInvokingHandler() {
    when(featureFlagService.isGlobalEnabled(FeatureName.CDS_GITOPS_ENABLE_KAFKA_CONNECT)).thenReturn(false);

    handler.onMessage(
        buildUtilizationSnapshotRecord(SNAPSHOT_ID, "account1", 5), createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleEvent(any(DebeziumChangeEvent.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_ffEvaluationThrows_treatsAsDisabled() {
    when(featureFlagService.isGlobalEnabled(FeatureName.CDS_GITOPS_ENABLE_KAFKA_CONNECT))
        .thenThrow(new RuntimeException("FF service unavailable"));

    handler.onMessage(
        buildUtilizationSnapshotRecord(SNAPSHOT_ID, "account1", 5), createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleEvent(any(DebeziumChangeEvent.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_nullMessage_skipsWithoutInvokingHandler() {
    handler.onMessage(null, createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleEvent(any(DebeziumChangeEvent.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_emptyId_skipsWithoutInvokingHandler() {
    GenericRecord recordWithEmptyId = buildUtilizationSnapshotRecord("", "account1", 5);

    handler.onMessage(recordWithEmptyId, createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleEvent(any(DebeziumChangeEvent.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_nullId_skipsWithoutInvokingHandler() {
    GenericRecord recordWithNullId = buildUtilizationSnapshotRecord(null, "account1", 5);

    handler.onMessage(recordWithNullId, createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleEvent(any(DebeziumChangeEvent.class));
  }

  // ---------------------------------------------------------------------------
  // End-to-end event translation (FF ON)
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_ffOn_createOp_delegatesToHandlerWithCorrectlyShapedEvent() throws Exception {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(true);
    Map<String, Object> metricInfo = new HashMap<>();
    metricInfo.put(HKafkaConsumer.EVENT_SEND_TS, 1_700_000_000_000L);

    handler.onMessage(buildUtilizationSnapshotRecord(SNAPSHOT_ID, "account1", 5), createOpHeaders("c"), metricInfo);

    ArgumentCaptor<DebeziumChangeEvent> captor = ArgumentCaptor.forClass(DebeziumChangeEvent.class);
    verify(eventHandler, times(1)).handleEvent(captor.capture());
    DebeziumChangeEvent event = captor.getValue();

    assertThat(event.getOptype()).isEqualTo("CREATE");
    assertThat(event.getTimestamp()).isEqualTo(1_700_000_000_000L);

    JsonNode key = new ObjectMapper().readTree(event.getKey());
    assertThat(key.get("id").asText()).isEqualTo(SNAPSHOT_ID);

    JsonNode value = new ObjectMapper().readTree(event.getValue());
    assertThat(value.get("_id").asText()).isEqualTo(SNAPSHOT_ID);
    assertThat(value.get("accountIdentifier").asText()).isEqualTo("account1");
    assertThat(value.get("instanceCount").asInt()).isEqualTo(5);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_ffOn_updateOp_mapsOptypeCorrectly() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(true);

    handler.onMessage(
        buildUtilizationSnapshotRecord(SNAPSHOT_ID, "account1", 10), createOpHeaders("u"), emptyMetricInfo());

    ArgumentCaptor<DebeziumChangeEvent> captor = ArgumentCaptor.forClass(DebeziumChangeEvent.class);
    verify(eventHandler).handleEvent(captor.capture());
    assertThat(captor.getValue().getOptype()).isEqualTo("UPDATE");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_ffOn_deleteOp_mapsOptypeCorrectly() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(true);

    handler.onMessage(
        buildUtilizationSnapshotRecord(SNAPSHOT_ID, "account1", 0), createOpHeaders("d"), emptyMetricInfo());

    ArgumentCaptor<DebeziumChangeEvent> captor = ArgumentCaptor.forClass(DebeziumChangeEvent.class);
    verify(eventHandler).handleEvent(captor.capture());
    assertThat(captor.getValue().getOptype()).isEqualTo("DELETE");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_ffOn_snapshotOp_mapsOptypeCorrectly() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(true);

    handler.onMessage(
        buildUtilizationSnapshotRecord(SNAPSHOT_ID, "account1", 3), createOpHeaders("r"), emptyMetricInfo());

    ArgumentCaptor<DebeziumChangeEvent> captor = ArgumentCaptor.forClass(DebeziumChangeEvent.class);
    verify(eventHandler).handleEvent(captor.capture());
    assertThat(captor.getValue().getOptype()).isEqualTo("SNAPSHOT");
  }

  // ---------------------------------------------------------------------------
  // Retry loop
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_transientFailureThenSuccess_retriesUntilSuccess() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class)))
        .thenThrow(new RuntimeException("transient 1"))
        .thenThrow(new RuntimeException("transient 2"))
        .thenReturn(true);

    handler.onMessage(
        buildUtilizationSnapshotRecord(SNAPSHOT_ID, "account1", 5), createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, times(3)).handleEvent(any(DebeziumChangeEvent.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_persistentFailure_swallowsAfterMaxRetries() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenThrow(new RuntimeException("permanent"));

    // Should return normally (not throw), so HKafkaConsumer commits the offset.
    handler.onMessage(
        buildUtilizationSnapshotRecord(SNAPSHOT_ID, "account1", 5), createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, times(AbstractGitopsCdcMessageHandler.MAX_RETRIES))
        .handleEvent(any(DebeziumChangeEvent.class));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void onMessage_handlerReturnsFalse_logsWarningButDoesNotRetry() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(false);

    handler.onMessage(
        buildUtilizationSnapshotRecord(SNAPSHOT_ID, "account1", 5), createOpHeaders("c"), emptyMetricInfo());

    // Handler returning false is treated as success (no retry), just logged
    verify(eventHandler, times(1)).handleEvent(any(DebeziumChangeEvent.class));
  }

  // ---------------------------------------------------------------------------
  // extractOptype (inherited from AbstractGitopsCdcMessageHandler)
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void extractOptype_mapsDebeziumOpCodes() {
    assertThat(AbstractGitopsCdcMessageHandler.extractOptype(createOpHeaders("c"))).isEqualTo("CREATE");
    assertThat(AbstractGitopsCdcMessageHandler.extractOptype(createOpHeaders("u"))).isEqualTo("UPDATE");
    assertThat(AbstractGitopsCdcMessageHandler.extractOptype(createOpHeaders("d"))).isEqualTo("DELETE");
    assertThat(AbstractGitopsCdcMessageHandler.extractOptype(createOpHeaders("r"))).isEqualTo("SNAPSHOT");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void extractOptype_missingOrUnknownHeader_returnsSafeDefault() {
    assertThat(AbstractGitopsCdcMessageHandler.extractOptype(null)).isEqualTo("UNKNOWN");
    assertThat(AbstractGitopsCdcMessageHandler.extractOptype(Collections.emptyMap())).isEqualTo("UNKNOWN");
    // Unknown op codes are upper-cased rather than dropped so operators can see what arrived.
    assertThat(AbstractGitopsCdcMessageHandler.extractOptype(createOpHeaders("x"))).isEqualTo("X");
  }

  // ---------------------------------------------------------------------------
  // extractId + syntheticKey (inherited from AbstractGitopsCdcMessageHandler)
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void extractId_withUtf8Value_returnsStringForm() {
    GenericRecord record = buildUtilizationSnapshotRecord("snapshot-1", "account1", 5);
    assertThat(AbstractGitopsCdcMessageHandler.extractId(record)).isEqualTo("snapshot-1");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void extractId_missingIdField_returnsEmptyString() {
    GenericRecord record = buildUtilizationSnapshotRecord(null, "account1", 5);
    assertThat(AbstractGitopsCdcMessageHandler.extractId(record)).isEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void syntheticKey_producesJsonParseableByRedisAbstractHandler() throws Exception {
    String key = AbstractGitopsCdcMessageHandler.syntheticKey("snapshot-abc");
    JsonNode parsed = new ObjectMapper().readTree(key);
    assertThat(parsed.get("id").asText()).isEqualTo("snapshot-abc");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void syntheticKey_escapesEmbeddedQuotesSafely() throws Exception {
    String weirdId = "snap\"shot\\abc";
    String key = AbstractGitopsCdcMessageHandler.syntheticKey(weirdId);
    // The key must parse as valid JSON and round-trip the id exactly.
    JsonNode parsed = new ObjectMapper().readTree(key);
    assertThat(parsed.get("id").asText()).isEqualTo(weirdId);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void syntheticKey_nullId_producesEmptyStringId() throws Exception {
    String key = AbstractGitopsCdcMessageHandler.syntheticKey(null);
    JsonNode parsed = new ObjectMapper().readTree(key);
    assertThat(parsed.get("id").asText()).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // convertValueToJson (inherited from AbstractGitopsCdcMessageHandler)
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void convertValueToJson_nullInput_returnsEmptyString() {
    assertThat(AbstractGitopsCdcMessageHandler.convertValueToJson(null)).isEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void convertValueToJson_unwrapsUtf8AndNestedRecords() throws Exception {
    Schema inner = SchemaBuilder.record("Inner").fields().requiredString("name").endRecord();
    Schema outer =
        SchemaBuilder.record("Outer").fields().requiredString("_id").name("nested").type(inner).noDefault().endRecord();

    GenericRecord innerRec = new GenericData.Record(inner);
    innerRec.put("name", new Utf8("test-name"));
    GenericRecord outerRec = new GenericData.Record(outer);
    outerRec.put("_id", new Utf8("outer-1"));
    outerRec.put("nested", innerRec);

    String json = AbstractGitopsCdcMessageHandler.convertValueToJson(outerRec);
    JsonNode parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.get("_id").asText()).isEqualTo("outer-1");
    assertThat(parsed.get("nested").get("name").asText()).isEqualTo("test-name");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void convertValueToJson_unwrapsCollectionsAndNulls() throws Exception {
    Schema schema = SchemaBuilder.record("Rec")
                        .fields()
                        .requiredString("_id")
                        .name("tags")
                        .type()
                        .array()
                        .items()
                        .stringType()
                        .noDefault()
                        .name("optional")
                        .type()
                        .unionOf()
                        .nullType()
                        .and()
                        .stringType()
                        .endUnion()
                        .noDefault()
                        .endRecord();

    GenericRecord record = new GenericData.Record(schema);
    record.put("_id", new Utf8("rec-1"));
    record.put("tags", Arrays.asList(new Utf8("tag1"), new Utf8("tag2")));
    record.put("optional", null);

    String json = AbstractGitopsCdcMessageHandler.convertValueToJson(record);
    JsonNode parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.get("_id").asText()).isEqualTo("rec-1");
    assertThat(parsed.get("tags").isArray()).isTrue();
    assertThat(parsed.get("tags").get(0).asText()).isEqualTo("tag1");
    assertThat(parsed.get("tags").get(1).asText()).isEqualTo("tag2");
    assertThat(parsed.get("optional").isNull()).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Builds a minimal flattened utilization_snapshot Avro record with fields our tests read:
   * {@code _id}, {@code accountIdentifier}, {@code instanceCount}.
   */
  private static GenericRecord buildUtilizationSnapshotRecord(String id, String accountId, int instanceCount) {
    Schema schema = SchemaBuilder.record("UtilizationSnapshot")
                        .fields()
                        .name("_id")
                        .type()
                        .unionOf()
                        .nullType()
                        .and()
                        .stringType()
                        .endUnion()
                        .noDefault()
                        .requiredString("accountIdentifier")
                        .requiredInt("instanceCount")
                        .endRecord();
    GenericRecord record = new GenericData.Record(schema);
    record.put("_id", id == null ? null : new Utf8(id));
    record.put("accountIdentifier", new Utf8(accountId));
    record.put("instanceCount", instanceCount);
    return record;
  }

  private static Map<String, String> createOpHeaders(String op) {
    Map<String, String> headers = new HashMap<>();
    headers.put(AbstractGitopsCdcMessageHandler.OP_HEADER, op);
    return headers;
  }

  private static Map<String, Object> emptyMetricInfo() {
    return new HashMap<>();
  }
}