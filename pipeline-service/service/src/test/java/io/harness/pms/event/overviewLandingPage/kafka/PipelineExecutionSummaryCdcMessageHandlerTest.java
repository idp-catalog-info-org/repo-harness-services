/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.overviewLandingPage.kafka;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

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
import io.harness.pms.event.overviewLandingPage.PipelineExecutionSummaryChangeEventHandler;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link PipelineExecutionSummaryCdcMessageHandler}.
 *
 * <p>These tests validate the handler in isolation: feature-flag gating, id/optype/timestamp
 * extraction, synthetic-key formatting, Avro→plain-JSON conversion, and the bounded retry loop.
 * The downstream {@link PipelineExecutionSummaryChangeEventHandler} is mocked.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionSummaryCdcMessageHandlerTest extends CategoryTest {
  private static final String PLAN_ID = "plan-exec-uuid-123";

  @Mock private PipelineExecutionSummaryChangeEventHandler eventHandler;
  @Mock private FeatureFlagService featureFlagService;

  private PipelineExecutionSummaryCdcMessageHandler handler;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new PipelineExecutionSummaryCdcMessageHandler(eventHandler, featureFlagService);
    // Default: FF ON so tests exercise the full path unless they explicitly disable it.
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY)).thenReturn(true);
  }

  // ---------------------------------------------------------------------------
  // Feature flag gating
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void onMessage_ffOff_shortCircuitsWithoutInvokingHandler() {
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY)).thenReturn(false);

    handler.onMessage(buildPlanExecutionRecord(PLAN_ID, "RUNNING"), createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleEvent(any(DebeziumChangeEvent.class));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void onMessage_ffEvaluationThrows_treatsAsDisabled() {
    when(featureFlagService.isGlobalEnabled(FeatureName.PIPE_CDC_KAFKA_PLAN_EXECUTIONS_SUMMARY))
        .thenThrow(new RuntimeException("FF service unavailable"));

    handler.onMessage(buildPlanExecutionRecord(PLAN_ID, "RUNNING"), createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleEvent(any(DebeziumChangeEvent.class));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void onMessage_nullMessage_skipsWithoutInvokingHandler() {
    handler.onMessage(null, createOpHeaders("c"), emptyMetricInfo());

    verify(eventHandler, never()).handleEvent(any(DebeziumChangeEvent.class));
  }

  // ---------------------------------------------------------------------------
  // End-to-end event translation (FF ON)
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void onMessage_ffOn_createOp_delegatesToHandlerWithCorrectlyShapedEvent() throws Exception {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(true);
    Map<String, Object> metricInfo = new HashMap<>();
    metricInfo.put(HKafkaConsumer.EVENT_SEND_TS, 1_700_000_000_000L);

    handler.onMessage(buildPlanExecutionRecord(PLAN_ID, "RUNNING"), createOpHeaders("c"), metricInfo);

    ArgumentCaptor<DebeziumChangeEvent> captor = ArgumentCaptor.forClass(DebeziumChangeEvent.class);
    verify(eventHandler, times(1)).handleEvent(captor.capture());
    DebeziumChangeEvent event = captor.getValue();

    assertThat(event.getOptype()).isEqualTo("CREATE");
    assertThat(event.getTimestamp()).isEqualTo(1_700_000_000_000L);

    JsonNode key = new ObjectMapper().readTree(event.getKey());
    assertThat(key.get("id").asText()).isEqualTo(PLAN_ID);

    JsonNode value = new ObjectMapper().readTree(event.getValue());
    assertThat(value.get("_id").asText()).isEqualTo(PLAN_ID);
    assertThat(value.get("status").asText()).isEqualTo("RUNNING");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void onMessage_ffOn_deleteOp_mapsOptypeCorrectly() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(true);

    handler.onMessage(buildPlanExecutionRecord(PLAN_ID, "ABORTED"), createOpHeaders("d"), emptyMetricInfo());

    ArgumentCaptor<DebeziumChangeEvent> captor = ArgumentCaptor.forClass(DebeziumChangeEvent.class);
    verify(eventHandler).handleEvent(captor.capture());
    assertThat(captor.getValue().getOptype()).isEqualTo("DELETE");
  }

  // ---------------------------------------------------------------------------
  // Retry loop
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void onMessage_transientFailureThenSuccess_retriesUntilSuccess() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class)))
        .thenThrow(new RuntimeException("transient 1"))
        .thenThrow(new RuntimeException("transient 2"))
        .thenReturn(true);

    handler.onMessage(buildPlanExecutionRecord(PLAN_ID, "RUNNING"), createOpHeaders("u"), emptyMetricInfo());

    verify(eventHandler, times(3)).handleEvent(any(DebeziumChangeEvent.class));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void onMessage_persistentFailure_swallowsAfterMaxRetries() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenThrow(new RuntimeException("permanent"));

    // Should return normally (not throw), so HKafkaConsumer commits the offset.
    handler.onMessage(buildPlanExecutionRecord(PLAN_ID, "RUNNING"), createOpHeaders("u"), emptyMetricInfo());

    verify(eventHandler, times(PipelineExecutionSummaryCdcMessageHandler.MAX_RETRIES))
        .handleEvent(any(DebeziumChangeEvent.class));
  }

  // ---------------------------------------------------------------------------
  // extractOptype
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void extractOptype_mapsDebeziumOpCodes() {
    assertThat(PipelineExecutionSummaryCdcMessageHandler.extractOptype(createOpHeaders("c"))).isEqualTo("CREATE");
    assertThat(PipelineExecutionSummaryCdcMessageHandler.extractOptype(createOpHeaders("u"))).isEqualTo("UPDATE");
    assertThat(PipelineExecutionSummaryCdcMessageHandler.extractOptype(createOpHeaders("d"))).isEqualTo("DELETE");
    assertThat(PipelineExecutionSummaryCdcMessageHandler.extractOptype(createOpHeaders("r"))).isEqualTo("SNAPSHOT");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void extractOptype_missingOrUnknownHeader_returnsSafeDefault() {
    assertThat(PipelineExecutionSummaryCdcMessageHandler.extractOptype(null)).isEqualTo("UNKNOWN");
    assertThat(PipelineExecutionSummaryCdcMessageHandler.extractOptype(Collections.emptyMap())).isEqualTo("UNKNOWN");
    // Unknown op codes are upper-cased rather than dropped so operators can see what arrived.
    assertThat(PipelineExecutionSummaryCdcMessageHandler.extractOptype(createOpHeaders("t"))).isEqualTo("T");
  }

  // ---------------------------------------------------------------------------
  // extractId + syntheticKey
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void extractId_withUtf8Value_returnsStringForm() {
    GenericRecord record = buildPlanExecutionRecord("plan-1", "RUNNING");
    assertThat(PipelineExecutionSummaryCdcMessageHandler.extractId(record)).isEqualTo("plan-1");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void extractId_missingIdField_returnsEmptyString() {
    GenericRecord record = buildPlanExecutionRecord(null, "RUNNING");
    assertThat(PipelineExecutionSummaryCdcMessageHandler.extractId(record)).isEmpty();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void syntheticKey_producesJsonParseableByRedisAbstractHandler() throws Exception {
    String key = PipelineExecutionSummaryCdcMessageHandler.syntheticKey("plan-abc");
    JsonNode parsed = new ObjectMapper().readTree(key);
    assertThat(parsed.get("id").asText()).isEqualTo("plan-abc");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void syntheticKey_escapesEmbeddedQuotesSafely() throws Exception {
    String weirdId = "pla\"n\\abc";
    String key = PipelineExecutionSummaryCdcMessageHandler.syntheticKey(weirdId);
    // The key must parse as valid JSON and round-trip the id exactly.
    JsonNode parsed = new ObjectMapper().readTree(key);
    assertThat(parsed.get("id").asText()).isEqualTo(weirdId);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void syntheticKey_nullId_producesEmptyStringId() throws Exception {
    String key = PipelineExecutionSummaryCdcMessageHandler.syntheticKey(null);
    JsonNode parsed = new ObjectMapper().readTree(key);
    assertThat(parsed.get("id").asText()).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // convertValueToJson
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void convertValueToJson_nullInput_returnsEmptyString() {
    assertThat(PipelineExecutionSummaryCdcMessageHandler.convertValueToJson(null)).isEmpty();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void convertValueToJson_unwrapsUtf8AndNestedRecords() throws Exception {
    Schema inner = SchemaBuilder.record("Inner").fields().requiredString("name").endRecord();
    Schema outer =
        SchemaBuilder.record("Outer").fields().requiredString("_id").name("nested").type(inner).noDefault().endRecord();

    GenericRecord innerRec = new GenericData.Record(inner);
    innerRec.put("name", new Utf8("abc"));
    GenericRecord outerRec = new GenericData.Record(outer);
    outerRec.put("_id", new Utf8("outer-1"));
    outerRec.put("nested", innerRec);

    String json = PipelineExecutionSummaryCdcMessageHandler.convertValueToJson(outerRec);
    JsonNode parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.get("_id").asText()).isEqualTo("outer-1");
    assertThat(parsed.get("nested").get("name").asText()).isEqualTo("abc");
  }

  @Test
  @Owner(developers = ARCHIT)
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
    record.put("tags", Arrays.asList(new Utf8("a"), new Utf8("b")));
    record.put("optional", null);

    String json = PipelineExecutionSummaryCdcMessageHandler.convertValueToJson(record);
    JsonNode parsed = new ObjectMapper().readTree(json);
    assertThat(parsed.get("_id").asText()).isEqualTo("rec-1");
    assertThat(parsed.get("tags").isArray()).isTrue();
    assertThat(parsed.get("tags").get(0).asText()).isEqualTo("a");
    assertThat(parsed.get("tags").get(1).asText()).isEqualTo("b");
    assertThat(parsed.get("optional").isNull()).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Delete event handling — two Kafka records per MongoDB delete
  // (connector changed from delete.handling.mode=drop → rewrite, drop.tombstones=false)
  // ---------------------------------------------------------------------------

  /**
   * When the connector switches to {@code delete.handling.mode=rewrite}, MongoDB DELETE events
   * arrive as a NON-NULL Avro record carrying only {@code _id} (from {@code documentKey._id})
   * plus a {@code __op=d} header.  All other fields (accountId, status, moduleInfo, …) are null
   * because {@code fullDocument} is absent in the MongoDB change stream for deletions.
   *
   * <p>The handler must extract the id, build a DELETE {@link DebeziumChangeEvent}, and delegate
   * to {@link PipelineExecutionSummaryChangeEventHandler#handleDeleteEvent(String)} which only
   * needs the {@code id} — it does NOT call {@code createRecord()} (which guards on moduleInfo).
   */
  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void onMessage_rewriteDelete_sparseRecord_invokesHandlerWithDeleteOptype() throws Exception {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(true);

    // Sparse record: only _id is populated; all other projected fields are null unions.
    GenericRecord sparseRecord = buildSparseDeleteRecord(PLAN_ID);
    handler.onMessage(sparseRecord, createOpHeaders("d"), emptyMetricInfo());

    ArgumentCaptor<DebeziumChangeEvent> captor = ArgumentCaptor.forClass(DebeziumChangeEvent.class);
    verify(eventHandler, times(1)).handleEvent(captor.capture());
    DebeziumChangeEvent event = captor.getValue();
    assertThat(event.getOptype()).isEqualTo("DELETE");
    // The synthetic key must still carry the id so RedisAbstractHandler.getId() can parse it.
    assertThat(new ObjectMapper().readTree(event.getKey()).get("id").asText()).isEqualTo(PLAN_ID);
  }

  /**
   * When {@code drop.tombstones=false} is set, a null-value (tombstone) record follows each
   * rewrite-delete record.  The tombstone has NO {@code __op} header and a null value — it must
   * be silently skipped so the consumer stays current and never gets stuck.
   */
  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void onMessage_tombstoneAfterDelete_nullMessage_silentlySkipped() {
    // null value → HKafkaConsumer passes null to onMessage (KafkaAvroDeserializer returns null
    // for null-value bytes).  No __op header → metadata will be empty map.
    handler.onMessage(null, Collections.emptyMap(), emptyMetricInfo());

    verify(eventHandler, never()).handleEvent(any(DebeziumChangeEvent.class));
  }

  /**
   * End-to-end scenario: one MongoDB delete produces two sequential Kafka records.
   * Both must be handled without exception and without leaving the consumer stuck.
   */
  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void onMessage_twoRecordsPerDelete_bothHandledCorrectly() {
    when(eventHandler.handleEvent(any(DebeziumChangeEvent.class))).thenReturn(true);

    // Record 1: rewrite delete (non-null Avro, __op=d)
    handler.onMessage(buildSparseDeleteRecord(PLAN_ID), createOpHeaders("d"), emptyMetricInfo());

    // Record 2: tombstone (null value, no __op header)
    handler.onMessage(null, Collections.emptyMap(), emptyMetricInfo());

    // The eventHandler is invoked exactly once — for the rewrite delete only.
    // The tombstone is a silent no-op.
    verify(eventHandler, times(1)).handleEvent(any(DebeziumChangeEvent.class));
  }

  /**
   * Safety guard: if the rewrite-delete record somehow arrives with an empty _id (should not
   * happen in practice, but defensive), the handler skips the event rather than writing a
   * garbage-id row to TimescaleDB.
   */
  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void onMessage_rewriteDelete_emptyId_skipsWithoutInvokingHandler() {
    GenericRecord recordWithNullId = buildSparseDeleteRecord(null);

    handler.onMessage(recordWithNullId, createOpHeaders("d"), emptyMetricInfo());

    verify(eventHandler, never()).handleEvent(any(DebeziumChangeEvent.class));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Simulates the sparse Avro record produced by Debezium's rewrite-delete mode:
   * only {@code _id} is populated (from {@code documentKey._id}); all other fields are null.
   */
  private static GenericRecord buildSparseDeleteRecord(String id) {
    Schema schema = SchemaBuilder.record("PlanExecutionSummary")
                        .fields()
                        .name("_id")
                        .type()
                        .unionOf()
                        .nullType()
                        .and()
                        .stringType()
                        .endUnion()
                        .noDefault()
                        .name("accountId")
                        .type()
                        .unionOf()
                        .nullType()
                        .and()
                        .stringType()
                        .endUnion()
                        .noDefault()
                        .name("status")
                        .type()
                        .unionOf()
                        .nullType()
                        .and()
                        .stringType()
                        .endUnion()
                        .noDefault()
                        .name("moduleInfo")
                        .type()
                        .unionOf()
                        .nullType()
                        .and()
                        .stringType()
                        .endUnion()
                        .noDefault()
                        .endRecord();
    GenericRecord record = new GenericData.Record(schema);
    record.put("_id", id == null ? null : new Utf8(id));
    record.put("accountId", null);
    record.put("status", null);
    record.put("moduleInfo", null);
    return record;
  }

  /**
   * Builds a minimal flattened planExecutionsSummary Avro record with only the two fields
   * our tests read: {@code _id} and {@code status}.
   */
  private static GenericRecord buildPlanExecutionRecord(String id, String status) {
    Schema schema = SchemaBuilder.record("PlanExecutionSummary")
                        .fields()
                        .name("_id")
                        .type()
                        .unionOf()
                        .nullType()
                        .and()
                        .stringType()
                        .endUnion()
                        .noDefault()
                        .requiredString("status")
                        .endRecord();
    GenericRecord record = new GenericData.Record(schema);
    record.put("_id", id == null ? null : new Utf8(id));
    record.put("status", new Utf8(status));
    return record;
  }

  private static Map<String, String> createOpHeaders(String op) {
    Map<String, String> headers = new HashMap<>();
    headers.put(PipelineExecutionSummaryCdcMessageHandler.OP_HEADER, op);
    return headers;
  }

  private static Map<String, Object> emptyMetricInfo() {
    return new HashMap<>();
  }
}
