/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.kafkaconsumer;

import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class PipelineExecutionSummaryCDKafkaConsumerStaticTest extends CategoryTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  // ── buildChangeEvent ─────────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void buildChangeEvent_createOp_setsKeyValueOptypeTimestampCorrectly() throws Exception {
    GenericRecord record = buildPlanExecSummaryRecord("exec-123", "ACC1");
    ConsumerRecord<String, Object> kafkaRecord =
        buildKafkaRecord("{\"id\":\"exec-123\"}", record, "c", 1_700_000_000_000L);

    DebeziumChangeEvent event = PipelineExecutionSummaryCDKafkaConsumer.buildChangeEvent(kafkaRecord);

    assertThat(event.getOptype()).isEqualTo("CREATE");
    assertThat(event.getTimestamp()).isEqualTo(1_700_000_000_000L);
    JsonNode value = MAPPER.readTree(event.getValue());
    assertThat(value.get("_id").asText()).isEqualTo("exec-123");
    assertThat(value.get("accountId").asText()).isEqualTo("ACC1");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void buildChangeEvent_nullKafkaKey_setsEmptyKey() {
    GenericRecord record = buildPlanExecSummaryRecord("exec-456", "ACC2");
    ConsumerRecord<String, Object> kafkaRecord = buildKafkaRecord(null, record, "u", 0L);

    DebeziumChangeEvent event = PipelineExecutionSummaryCDKafkaConsumer.buildChangeEvent(kafkaRecord);

    assertThat(event.getKey()).isEmpty();
    assertThat(event.getOptype()).isEqualTo("UPDATE");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void buildChangeEvent_deleteOp_mapsOptypeCorrectly() {
    ConsumerRecord<String, Object> kafkaRecord =
        buildKafkaRecord("{\"id\":\"x\"}", buildPlanExecSummaryRecord("x", "A"), "d", 0L);
    assertThat(PipelineExecutionSummaryCDKafkaConsumer.buildChangeEvent(kafkaRecord).getOptype()).isEqualTo("DELETE");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void buildChangeEvent_snapshotOp_mapsOptypeCorrectly() {
    ConsumerRecord<String, Object> kafkaRecord =
        buildKafkaRecord("{\"id\":\"x\"}", buildPlanExecSummaryRecord("x", "A"), "r", 0L);
    assertThat(PipelineExecutionSummaryCDKafkaConsumer.buildChangeEvent(kafkaRecord).getOptype()).isEqualTo("SNAPSHOT");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void buildChangeEvent_missingOpHeader_returnsUnknown() {
    RecordHeaders headers = new RecordHeaders();
    ConsumerRecord<String, Object> kafkaRecord =
        new ConsumerRecord<>("pmsMongo.pms-harness.planExecutionsSummary", 0, 0L, 0L, TimestampType.CREATE_TIME, 0, 0,
            "{\"id\":\"x\"}", buildPlanExecSummaryRecord("x", "A"), headers, Optional.empty());
    assertThat(PipelineExecutionSummaryCDKafkaConsumer.buildChangeEvent(kafkaRecord).getOptype()).isEqualTo("UNKNOWN");
  }

  // ── convertValueToJson ──────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void convertValueToJson_nullValue_returnsEmpty() {
    assertThat(PipelineExecutionSummaryCDKafkaConsumer.convertValueToJson(null)).isEmpty();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void convertValueToJson_genericRecord_producesValidJson() throws Exception {
    GenericRecord record = buildPlanExecSummaryRecord("exec-1", "ACCOUNT");
    String json = PipelineExecutionSummaryCDKafkaConsumer.convertValueToJson(record);

    JsonNode parsed = MAPPER.readTree(json);
    assertThat(parsed.get("_id").asText()).isEqualTo("exec-1");
    assertThat(parsed.get("accountId").asText()).isEqualTo("ACCOUNT");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void convertValueToJson_nestedRecordAndCollection_unwrapsCorrectly() throws Exception {
    Schema inner = SchemaBuilder.record("Inner").fields().requiredString("name").endRecord();
    Schema outer = SchemaBuilder.record("Outer")
                       .fields()
                       .requiredString("_id")
                       .name("nested")
                       .type(inner)
                       .noDefault()
                       .name("tags")
                       .type()
                       .array()
                       .items()
                       .stringType()
                       .noDefault()
                       .endRecord();
    GenericRecord innerRec = new GenericData.Record(inner);
    innerRec.put("name", new Utf8("test"));
    GenericRecord outerRec = new GenericData.Record(outer);
    outerRec.put("_id", new Utf8("outer-1"));
    outerRec.put("nested", innerRec);
    outerRec.put("tags", Arrays.asList(new Utf8("a"), new Utf8("b")));

    String json = PipelineExecutionSummaryCDKafkaConsumer.convertValueToJson(outerRec);
    JsonNode parsed = MAPPER.readTree(json);
    assertThat(parsed.get("nested").get("name").asText()).isEqualTo("test");
    assertThat(parsed.get("tags").get(0).asText()).isEqualTo("a");
  }

  // ── constants ────────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void consumerConfigKey_matchesConfigYmlEntryName() {
    assertThat(PipelineExecutionSummaryCDKafkaConsumer.CONSUMER_CONFIG_KEY).isEqualTo("planExecutionsSummaryCD");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void consumerGroup_isStableIdentifier() {
    assertThat(PipelineExecutionSummaryCDKafkaConsumer.CONSUMER_GROUP)
        .isEqualTo("ng-manager-pipeline-execution-summary-cd-cdc");
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private static GenericRecord buildPlanExecSummaryRecord(String id, String accountId) {
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
                        .requiredString("accountId")
                        .endRecord();
    GenericRecord record = new GenericData.Record(schema);
    record.put("_id", id == null ? null : new Utf8(id));
    record.put("accountId", new Utf8(accountId));
    return record;
  }

  private static ConsumerRecord<String, Object> buildKafkaRecord(String key, Object value, String op, long timestamp) {
    RecordHeaders headers = new RecordHeaders();
    if (op != null) {
      headers.add("__op", op.getBytes(StandardCharsets.UTF_8));
    }
    return new ConsumerRecord<>("pmsMongo.pms-harness.planExecutionsSummary", 0, 0L, timestamp,
        TimestampType.CREATE_TIME, 0, 0, key, value, headers, Optional.empty());
  }
}
