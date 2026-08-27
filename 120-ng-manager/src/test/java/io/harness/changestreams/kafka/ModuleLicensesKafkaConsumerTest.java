/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreams.kafka;

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

@OwnedBy(HarnessTeam.GTM)
@RunWith(MockitoJUnitRunner.class)
public class ModuleLicensesKafkaConsumerTest extends CategoryTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  // ── buildChangeEvent ────────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void buildChangeEvent_createOp_setsKeyValueOptypeTimestampCorrectly() throws Exception {
    GenericRecord record = buildModuleLicenseRecord("lic-123", "ACC1");
    ConsumerRecord<String, Object> kafkaRecord =
        buildKafkaRecord("{\"id\":\"lic-123\"}", record, "c", 1_700_000_000_000L);

    DebeziumChangeEvent event = ModuleLicensesKafkaConsumer.buildChangeEvent(kafkaRecord);

    assertThat(event.getOptype()).isEqualTo("CREATE");
    assertThat(event.getTimestamp()).isEqualTo(1_700_000_000_000L);
    JsonNode key = MAPPER.readTree(event.getKey());
    assertThat(key.get("id").asText()).isEqualTo("lic-123");
    JsonNode value = MAPPER.readTree(event.getValue());
    assertThat(value.get("_id").asText()).isEqualTo("lic-123");
    assertThat(value.get("accountIdentifier").asText()).isEqualTo("ACC1");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void buildChangeEvent_nullKafkaKey_setsEmptyKey() throws Exception {
    GenericRecord record = buildModuleLicenseRecord("lic-456", "ACC2");
    ConsumerRecord<String, Object> kafkaRecord = buildKafkaRecord(null, record, "u", 0L);

    DebeziumChangeEvent event = ModuleLicensesKafkaConsumer.buildChangeEvent(kafkaRecord);

    assertThat(event.getKey()).isEmpty();
    assertThat(event.getOptype()).isEqualTo("UPDATE");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void buildChangeEvent_deleteOp_mapsOptypeCorrectly() {
    ConsumerRecord<String, Object> kafkaRecord =
        buildKafkaRecord("{\"id\":\"x\"}", buildModuleLicenseRecord("x", "A"), "d", 0L);
    assertThat(ModuleLicensesKafkaConsumer.buildChangeEvent(kafkaRecord).getOptype()).isEqualTo("DELETE");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void buildChangeEvent_snapshotOp_mapsOptypeCorrectly() {
    ConsumerRecord<String, Object> kafkaRecord =
        buildKafkaRecord("{\"id\":\"x\"}", buildModuleLicenseRecord("x", "A"), "r", 0L);
    assertThat(ModuleLicensesKafkaConsumer.buildChangeEvent(kafkaRecord).getOptype()).isEqualTo("SNAPSHOT");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void buildChangeEvent_missingOpHeader_returnsUnknown() {
    RecordHeaders headers = new RecordHeaders();
    ConsumerRecord<String, Object> kafkaRecord = new ConsumerRecord<>("topic", 0, 0L, 0L, TimestampType.CREATE_TIME, 0,
        0, "{\"id\":\"x\"}", buildModuleLicenseRecord("x", "A"), headers, Optional.empty());
    assertThat(ModuleLicensesKafkaConsumer.buildChangeEvent(kafkaRecord).getOptype()).isEqualTo("UNKNOWN");
  }

  // ── convertValueToJson ──────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void convertValueToJson_nullValue_returnsEmpty() {
    assertThat(ModuleLicensesKafkaConsumer.convertValueToJson(null)).isEmpty();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void convertValueToJson_genericRecord_producesValidJson() throws Exception {
    GenericRecord record = buildModuleLicenseRecord("lic-1", "ACCOUNT");
    String json = ModuleLicensesKafkaConsumer.convertValueToJson(record);

    JsonNode parsed = MAPPER.readTree(json);
    assertThat(parsed.get("_id").asText()).isEqualTo("lic-1");
    assertThat(parsed.get("accountIdentifier").asText()).isEqualTo("ACCOUNT");
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

    String json = ModuleLicensesKafkaConsumer.convertValueToJson(outerRec);
    JsonNode parsed = MAPPER.readTree(json);
    assertThat(parsed.get("nested").get("name").asText()).isEqualTo("test");
    assertThat(parsed.get("tags").get(0).asText()).isEqualTo("a");
  }

  // ── constants ───────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void consumerConfigKey_matchesConfigYmlEntryName() {
    assertThat(ModuleLicensesKafkaConsumer.CONSUMER_CONFIG_KEY).isEqualTo("moduleLicenses");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void consumerGroup_isStableIdentifier() {
    assertThat(ModuleLicensesKafkaConsumer.CONSUMER_GROUP).isEqualTo("ng-manager-module-licenses-cdc");
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private static GenericRecord buildModuleLicenseRecord(String id, String accountIdentifier) {
    Schema schema = SchemaBuilder.record("ModuleLicenses")
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
                        .endRecord();
    GenericRecord record = new GenericData.Record(schema);
    record.put("_id", id == null ? null : new Utf8(id));
    record.put("accountIdentifier", new Utf8(accountIdentifier));
    return record;
  }

  private static ConsumerRecord<String, Object> buildKafkaRecord(String key, Object value, String op, long timestamp) {
    RecordHeaders headers = new RecordHeaders();
    if (op != null) {
      headers.add("__op", op.getBytes(StandardCharsets.UTF_8));
    }
    return new ConsumerRecord<>("ngMongo.ng-harness.moduleLicenses", 0, 0L, timestamp, TimestampType.CREATE_TIME, 0, 0,
        key, value, headers, Optional.empty());
  }
}
