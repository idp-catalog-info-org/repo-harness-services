/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.FailureDataAvro;
import io.harness.pms.contracts.execution.FailureInfoAvro;
import io.harness.pms.contracts.execution.PipelineEventAvro;
import io.harness.pms.contracts.execution.StageEventAvro;
import io.harness.pms.contracts.execution.StepEndEventAvro;
import io.harness.pms.execution.cdc.MongoDBCDCEvent;
import io.harness.pms.execution.cdc.PipelineExecutionCDCEnrichment;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.connect.avro.ConnectDefault;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionCDCWrapperTest extends CategoryTest {
  private static final ObjectMapper OBJECT_MAPPER = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testWrapStepEventInCDCFormat() throws Exception {
    // Given
    StepEndEventAvro stepEvent = createSampleStepEvent();
    String database = "harness-pms";
    String collection = "nodeExecutionsStep";
    String documentId = "step-exec-123";
    String operationType = "insert";

    // When
    ConnectDefault result = NodeExecutionCDCWrapper.wrapInNodeExecutionCDCFormat(
        stepEvent, database, collection, documentId, operationType);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getPayload()).isNotNull();
    assertThat(result.getDb()).isEqualTo(database);
    assertThat(result.getSourceType()).isEqualTo("MONGODB");

    // Parse payload and verify structure
    MongoDBCDCEvent cdcEvent = OBJECT_MAPPER.readValue(result.getPayload().toString(), MongoDBCDCEvent.class);
    assertThat(cdcEvent.getOperationType()).isEqualTo(operationType);
    assertThat(cdcEvent.getNs().getDb()).isEqualTo(database);
    assertThat(cdcEvent.getNs().getColl()).isEqualTo(collection);
    assertThat(cdcEvent.getDocumentKey().getId()).isEqualTo(documentId);
    assertThat(cdcEvent.getWallTime().getDate()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}Z");
    assertThat(cdcEvent.getFullDocument()).isNotNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testWrapStageEventInCDCFormat() throws Exception {
    // Given
    StageEventAvro stageEvent = createSampleStageEvent();
    String database = "harness-pms";
    String collection = "nodeExecutionsStage";
    String documentId = "stage-exec-456";
    String operationType = "update";

    // When
    ConnectDefault result = NodeExecutionCDCWrapper.wrapInNodeExecutionCDCFormat(
        stageEvent, database, collection, documentId, operationType);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getPayload()).isNotNull();
    assertThat(result.getDb()).isEqualTo(database);
    assertThat(result.getSourceType()).isEqualTo("MONGODB");

    // Parse payload
    MongoDBCDCEvent cdcEvent = OBJECT_MAPPER.readValue(result.getPayload().toString(), MongoDBCDCEvent.class);
    assertThat(cdcEvent.getOperationType()).isEqualTo(operationType);
    assertThat(cdcEvent.getNs().getColl()).isEqualTo(collection);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testWrapPipelineEventInCDCFormat() throws Exception {
    // Given
    PipelineEventAvro pipelineEvent = createSamplePipelineEvent();
    String database = "harness-pms";
    String collection = "nodeExecutionsPipeline";
    String documentId = "pipeline-exec-789";
    String operationType = "update";

    // When
    ConnectDefault result = NodeExecutionCDCWrapper.wrapInNodeExecutionCDCFormat(
        pipelineEvent, database, collection, documentId, operationType);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getPayload()).isNotNull();
    assertThat(result.getDb()).isEqualTo(database);
    assertThat(result.getSourceType()).isEqualTo("MONGODB");

    // Parse payload
    MongoDBCDCEvent cdcEvent = OBJECT_MAPPER.readValue(result.getPayload().toString(), MongoDBCDCEvent.class);
    assertThat(cdcEvent.getOperationType()).isEqualTo(operationType);
    assertThat(cdcEvent.getNs().getColl()).isEqualTo(collection);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testWallTimeIsISO8601Format() throws Exception {
    // Given
    StepEndEventAvro stepEvent = createSampleStepEvent();

    // When
    ConnectDefault result = NodeExecutionCDCWrapper.wrapInNodeExecutionCDCFormat(
        stepEvent, "harness-pms", "nodeExecutionsStep", "exec-123", "insert");

    // Then
    MongoDBCDCEvent cdcEvent = OBJECT_MAPPER.readValue(result.getPayload().toString(), MongoDBCDCEvent.class);
    String wallTimeDate = cdcEvent.getWallTime().getDate();

    // Verify ISO-8601 format: YYYY-MM-DDTHH:MM:SS.sssZ (accepts 1-9 fractional seconds)
    assertThat(wallTimeDate).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}Z");
    assertThat(wallTimeDate).endsWith("Z"); // UTC timezone
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineNodeExecutionCollectionName_Step() {
    // When
    String result = NodeExecutionCDCWrapper.determineNodeExecutionCollectionName("step");

    // Then
    assertThat(result).isEqualTo("nodeExecutionsStep");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineNodeExecutionCollectionName_Stage() {
    // When
    String result = NodeExecutionCDCWrapper.determineNodeExecutionCollectionName("stage");

    // Then
    assertThat(result).isEqualTo("nodeExecutionsStage");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineNodeExecutionCollectionName_Pipeline() {
    // When
    String result = NodeExecutionCDCWrapper.determineNodeExecutionCollectionName("pipeline");

    // Then
    assertThat(result).isEqualTo("nodeExecutionsPipeline");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineNodeExecutionCollectionName_DefaultToPipeline() {
    // When
    String result = NodeExecutionCDCWrapper.determineNodeExecutionCollectionName(null);

    // Then
    assertThat(result).isEqualTo("nodeExecutionsPipeline");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineNodeExecutionCDCOperationType_NodeStart() {
    // When
    String result = NodeExecutionCDCWrapper.determineNodeExecutionCDCOperationType("nodeStart");

    // Then
    assertThat(result).isEqualTo("insert");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineNodeExecutionCDCOperationType_NodeStatusUpdate() {
    // When
    String result = NodeExecutionCDCWrapper.determineNodeExecutionCDCOperationType("nodeStatusUpdate");

    // Then
    assertThat(result).isEqualTo("update");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineNodeExecutionCDCOperationType_NodeEnd() {
    // When
    String result = NodeExecutionCDCWrapper.determineNodeExecutionCDCOperationType("nodeEnd");

    // Then
    assertThat(result).isEqualTo("update");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineNodeExecutionCDCOperationType_DefaultToUpdate() {
    // When
    String result = NodeExecutionCDCWrapper.determineNodeExecutionCDCOperationType(null);

    // Then
    assertThat(result).isEqualTo("update");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testWrapWithFailureInfo() throws Exception {
    // Given
    StepEndEventAvro stepEvent = createStepEventWithFailure();

    // When
    ConnectDefault result = NodeExecutionCDCWrapper.wrapInNodeExecutionCDCFormat(
        stepEvent, "harness-pms", "nodeExecutionsStep", "exec-123", "update");

    // Then
    MongoDBCDCEvent cdcEvent = OBJECT_MAPPER.readValue(result.getPayload().toString(), MongoDBCDCEvent.class);
    assertThat(cdcEvent.getFullDocument().getFailureInfo()).isNotNull();
    assertThat(cdcEvent.getFullDocument().getFailureInfo()).hasSize(1);
    assertThat(cdcEvent.getFullDocument().getFailureInfo().get(0).getMessage()).isEqualTo("Test failure");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testWrapWithNullFailureInfo() throws Exception {
    // Given
    StepEndEventAvro stepEvent = createSampleStepEvent(); // No failure info

    // When
    ConnectDefault result = NodeExecutionCDCWrapper.wrapInNodeExecutionCDCFormat(
        stepEvent, "harness-pms", "nodeExecutionsStep", "exec-123", "insert");

    // Then
    MongoDBCDCEvent cdcEvent = OBJECT_MAPPER.readValue(result.getPayload().toString(), MongoDBCDCEvent.class);
    assertThat(cdcEvent.getFullDocument().getFailureInfo()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testWrapWithUnsupportedAvroType() {
    // Given
    org.apache.avro.specific.SpecificRecordBase unsupportedEvent =
        org.mockito.Mockito.mock(org.apache.avro.specific.SpecificRecordBase.class);

    // When & Then
    assertThatThrownBy(()
                           -> NodeExecutionCDCWrapper.wrapInNodeExecutionCDCFormat(
                               unsupportedEvent, "harness-pms", "collection", "doc-123", "insert"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to wrap NodeExecution event in CDC format");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreatePlainJsonCDCEnvelope() throws Exception {
    // Given
    StepEndEventAvro stepEvent = createSampleStepEvent();
    String database = "harness-pms";
    String collection = "nodeExecutionsStep";
    String documentId = "step-exec-123";
    String operationType = "insert";

    // When
    String jsonResult =
        NodeExecutionCDCWrapper.createPlainJsonCDCEnvelope(stepEvent, database, collection, documentId, operationType);

    // Then
    assertThat(jsonResult).isNotNull();
    assertThat(jsonResult).isNotEmpty();

    // Parse the JSON string and verify top-level envelope structure
    com.fasterxml.jackson.databind.JsonNode rootNode = OBJECT_MAPPER.readTree(jsonResult);
    assertThat(rootNode.has("version")).isTrue();
    assertThat(rootNode.get("version").asText()).isEqualTo("1.0");
    assertThat(rootNode.has("db")).isTrue();
    assertThat(rootNode.get("db").asText()).isEqualTo(database);
    assertThat(rootNode.has("source_type")).isTrue();
    assertThat(rootNode.get("source_type").asText()).isEqualTo("MONGODB");
    assertThat(rootNode.has("payload")).isTrue();

    // Verify payload is an object (not a string)
    com.fasterxml.jackson.databind.JsonNode payloadNode = rootNode.get("payload");
    assertThat(payloadNode.isObject()).isTrue();
    assertThat(payloadNode.has("operationType")).isTrue();
    assertThat(payloadNode.get("operationType").asText()).isEqualTo(operationType);
    assertThat(payloadNode.has("ns")).isTrue();
    assertThat(payloadNode.get("ns").get("db").asText()).isEqualTo(database);
    assertThat(payloadNode.get("ns").get("coll").asText()).isEqualTo(collection);
    assertThat(payloadNode.has("documentKey")).isTrue();
    assertThat(payloadNode.get("documentKey").get("_id").asText()).isEqualTo(documentId);
    assertThat(payloadNode.has("wallTime")).isTrue();
    assertThat(payloadNode.get("wallTime").get("$date").asText())
        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}Z");
    assertThat(payloadNode.has("fullDocument")).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreatePlainJsonCDCEnvelopeWithEnrichment_allFieldsPopulated() throws Exception {
    // Given: a pipeline event and a fully populated enrichment DTO
    PipelineEventAvro pipelineEvent = createSamplePipelineEvent();
    String database = "harness-pms";
    String collection = "nodeExecutionsPipeline";
    String documentId = "pipeline-exec-789";
    String operationType = "update";

    PipelineExecutionCDCEnrichment enrichment = PipelineExecutionCDCEnrichment.builder()
                                                    .runSequence(42)
                                                    .triggerType("MANUAL")
                                                    .triggeredById("user-uuid-123")
                                                    .triggeredByIdentifier("admin")
                                                    .executedModules(java.util.Arrays.asList("CD", "CI"))
                                                    .deleted(false)
                                                    .build();

    // When
    String jsonResult = NodeExecutionCDCWrapper.createPlainJsonCDCEnvelope(
        pipelineEvent, database, collection, documentId, operationType, enrichment);

    // Then: all enrichment fields are present in fullDocument
    assertThat(jsonResult).isNotNull();
    com.fasterxml.jackson.databind.JsonNode fullDocument =
        OBJECT_MAPPER.readTree(jsonResult).get("payload").get("fullDocument");
    assertThat(fullDocument.has("runSequence")).isTrue();
    assertThat(fullDocument.get("runSequence").asInt()).isEqualTo(42);
    assertThat(fullDocument.has("triggerType")).isTrue();
    assertThat(fullDocument.get("triggerType").asText()).isEqualTo("MANUAL");
    assertThat(fullDocument.has("triggeredById")).isTrue();
    assertThat(fullDocument.get("triggeredById").asText()).isEqualTo("user-uuid-123");
    assertThat(fullDocument.has("triggeredByIdentifier")).isTrue();
    assertThat(fullDocument.get("triggeredByIdentifier").asText()).isEqualTo("admin");
    assertThat(fullDocument.has("executedModules")).isTrue();
    assertThat(fullDocument.get("executedModules").isArray()).isTrue();
    assertThat(fullDocument.get("executedModules").size()).isEqualTo(2);
    assertThat(fullDocument.has("deleted")).isTrue();
    assertThat(fullDocument.get("deleted").asBoolean()).isFalse();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreatePlainJsonCDCEnvelopeWithEnrichment_nullEnrichment() throws Exception {
    // Given: pipeline event, enrichment is null (e.g., summary unavailable)
    PipelineEventAvro pipelineEvent = createSamplePipelineEvent();
    String database = "harness-pms";
    String collection = "nodeExecutionsPipeline";
    String documentId = "pipeline-exec-789";
    String operationType = "update";

    // When
    String jsonResult = NodeExecutionCDCWrapper.createPlainJsonCDCEnvelope(
        pipelineEvent, database, collection, documentId, operationType, (PipelineExecutionCDCEnrichment) null);

    // Then: enrichment fields should be absent (null fields are not serialised)
    com.fasterxml.jackson.databind.JsonNode fullDocument =
        OBJECT_MAPPER.readTree(jsonResult).get("payload").get("fullDocument");
    assertThat(fullDocument.has("runSequence")).isFalse();
    assertThat(fullDocument.has("triggerType")).isFalse();
    assertThat(fullDocument.has("triggeredById")).isFalse();
    assertThat(fullDocument.has("triggeredByIdentifier")).isFalse();
    assertThat(fullDocument.has("executedModules")).isFalse();
    assertThat(fullDocument.has("deleted")).isFalse();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreatePlainJsonCDCEnvelopeWithEnrichment_partialEnrichment() throws Exception {
    // Given: enrichment with only runSequence set; all other fields null
    PipelineEventAvro pipelineEvent = createSamplePipelineEvent();
    PipelineExecutionCDCEnrichment enrichment = PipelineExecutionCDCEnrichment.builder().runSequence(7).build();

    // When
    String jsonResult = NodeExecutionCDCWrapper.createPlainJsonCDCEnvelope(
        pipelineEvent, "harness-pms", "nodeExecutionsPipeline", "pipeline-exec-789", "update", enrichment);

    // Then: only runSequence present; other optional fields absent
    com.fasterxml.jackson.databind.JsonNode fullDocument =
        OBJECT_MAPPER.readTree(jsonResult).get("payload").get("fullDocument");
    assertThat(fullDocument.has("runSequence")).isTrue();
    assertThat(fullDocument.get("runSequence").asInt()).isEqualTo(7);
    assertThat(fullDocument.has("triggerType")).isFalse();
    assertThat(fullDocument.has("triggeredById")).isFalse();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreatePlainJsonCDCEnvelopeWithEnrichment_stepEventNotAffected() throws Exception {
    // Given: step event with enrichment (enrichment only applies to pipeline events)
    StepEndEventAvro stepEvent = createSampleStepEvent();
    PipelineExecutionCDCEnrichment enrichment =
        PipelineExecutionCDCEnrichment.builder().runSequence(5).triggerType("WEBHOOK").build();

    // When
    String jsonResult = NodeExecutionCDCWrapper.createPlainJsonCDCEnvelope(
        stepEvent, "harness-pms", "nodeExecutionsStep", "step-exec-123", "update", enrichment);

    // Then: step-level fields present; enrichment fields not injected (not a PipelineEventAvro)
    com.fasterxml.jackson.databind.JsonNode fullDocument =
        OBJECT_MAPPER.readTree(jsonResult).get("payload").get("fullDocument");
    assertThat(fullDocument.has("runSequence")).isFalse();
    assertThat(fullDocument.has("triggerType")).isFalse();
    assertThat(fullDocument.get("stepIdentifier").asText()).isEqualTo("step1");
  }

  // Helper methods to create sample events

  private StepEndEventAvro createSampleStepEvent() {
    return StepEndEventAvro.newBuilder()
        .setLevel("step")
        .setAccountIdentifier("acc123")
        .setOrgIdentifier("org123")
        .setProjectIdentifier("proj123")
        .setPipelineIdentifier("pipe123")
        .setPipelineName("Test Pipeline")
        .setPlanExecutionId("exec123")
        .setExecutionUrl("https://app.harness.io/executions/exec123")
        .setStageExecutionId("stage-exec-123")
        .setStageIdentifier("stage1")
        .setStepExecutionId("step-exec-123")
        .setStepIdentifier("step1")
        .setStepName("Test Step")
        .setStepType("Http")
        .setStepInputs("")
        .setIsRetried(false)
        .setStatus("Success")
        .setEventType("nodeEnd")
        .setCreatedAt("1738425318000")
        .setStartTs("1738425318000")
        .setLastModifiedAt("1738425320000")
        .setEndTs("1738425320000")
        .setDuration("2000")
        .setStepOutputs(new ArrayList<>())
        .build();
  }

  private StepEndEventAvro createStepEventWithFailure() {
    List<FailureDataAvro> failureDataList = new ArrayList<>();
    failureDataList.add(FailureDataAvro.newBuilder()
                            .setCode("500")
                            .setLevel("ERROR")
                            .setMessage("Test failure")
                            .setFailureType("APPLICATION_FAILURE")
                            .build());

    FailureInfoAvro failureInfo = FailureInfoAvro.newBuilder().setFailureData(failureDataList).build();

    return StepEndEventAvro.newBuilder()
        .setLevel("step")
        .setAccountIdentifier("acc123")
        .setOrgIdentifier("org123")
        .setProjectIdentifier("proj123")
        .setPipelineIdentifier("pipe123")
        .setPipelineName("Test Pipeline")
        .setPlanExecutionId("exec123")
        .setExecutionUrl("https://app.harness.io/executions/exec123")
        .setStageExecutionId("stage-exec-123")
        .setStageIdentifier("stage1")
        .setStepExecutionId("step-exec-123")
        .setStepIdentifier("step1")
        .setStepName("Test Step")
        .setStepType("Http")
        .setStepInputs("")
        .setIsRetried(false)
        .setStatus("Failed")
        .setEventType("nodeEnd")
        .setCreatedAt("1738425318000")
        .setStartTs("1738425318000")
        .setLastModifiedAt("1738425320000")
        .setEndTs("1738425320000")
        .setDuration("2000")
        .setStepOutputs(new ArrayList<>())
        .setFailureInfo(failureInfo)
        .build();
  }

  private StageEventAvro createSampleStageEvent() {
    return StageEventAvro.newBuilder()
        .setLevel("stage")
        .setAccountIdentifier("acc123")
        .setOrgIdentifier("org123")
        .setProjectIdentifier("proj123")
        .setPipelineIdentifier("pipe123")
        .setPipelineName("Test Pipeline")
        .setPlanExecutionId("exec456")
        .setExecutionUrl("https://app.harness.io/executions/exec456")
        .setStageExecutionId("stage-exec-456")
        .setStageIdentifier("stage1")
        .setStageName("Test Stage")
        .setStageType("Deployment")
        .setStatus("Running")
        .setEventType("nodeStart")
        .setCreatedAt("1738425318000")
        .setStartTs("1738425318000")
        .setLastModifiedAt("1738425318000")
        .setEndTs("1738425320000")
        .setDuration("2000")
        .build();
  }

  private PipelineEventAvro createSamplePipelineEvent() {
    return PipelineEventAvro.newBuilder()
        .setLevel("pipeline")
        .setAccountIdentifier("acc123")
        .setOrgIdentifier("org123")
        .setProjectIdentifier("proj123")
        .setPipelineIdentifier("pipe123")
        .setPipelineName("Test Pipeline")
        .setPlanExecutionId("exec789")
        .setExecutionUrl("https://app.harness.io/executions/exec789")
        .setStatus("Success")
        .setEventType("nodeEnd")
        .setCreatedAt("1738425318000")
        .setStartTs("1738425318000")
        .setLastModifiedAt("1738425350000")
        .setEndTs("1738425350000")
        .setDuration("32000")
        .build();
  }
}
