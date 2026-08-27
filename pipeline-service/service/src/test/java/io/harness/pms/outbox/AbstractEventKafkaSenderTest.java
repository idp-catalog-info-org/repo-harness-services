/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.execution.NodeExecution;
import io.harness.kafka.producers.HKafkaStringProducer;
import io.harness.kafka.producers.avro.HKafkaAvroProducer;
import io.harness.metrics.service.api.MetricService;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.FailureDataAvro;
import io.harness.pms.contracts.execution.FailureInfoAvro;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureSubType;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.failure.FailureTypeInfo;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.util.Optional;
import org.apache.kafka.clients.producer.Callback;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class AbstractEventKafkaSenderTest extends CategoryTest {
  @Mock private HKafkaAvroProducer hKafkaAvroProducer;
  @Mock private HKafkaStringProducer hKafkaGeneralStringProducer;
  @Mock private PipelineServiceConfiguration configuration;
  @Mock private PipelineExpressionHelper pipelineExpressionHelper;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock private PmsFeatureFlagService featureFlagService;
  @Mock private OutboxService executionOutboxService;
  @Mock private OutboxService kafkaOutboxService;
  @Mock private MetricService metricService;

  private TestAbstractEventKafkaSender testSender;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    testSender = new TestAbstractEventKafkaSender();
    testSender.hKafkaAvroProducerOptional = Optional.of(hKafkaAvroProducer);
    // PIPE-33718: General Avro producer is empty by default; tests that exercise the General path
    // should populate this field explicitly.
    testSender.hKafkaAvroProducerGeneralOptional = Optional.empty();
    testSender.hKafkaGeneralStringProducerOptional = Optional.of(hKafkaGeneralStringProducer);
    testSender.configuration = configuration;
    testSender.pipelineExpressionHelper = pipelineExpressionHelper;
    testSender.pmsExecutionSummaryService = pmsExecutionSummaryService;
    testSender.featureFlagService = featureFlagService;
    testSender.metricService = metricService;
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testMapFailureInfoProtoToAvro_WithFailureDataList() {
    // Given
    FailureTypeInfo failureTypeInfo = FailureTypeInfo.newBuilder()
                                          .setFailureType(FailureType.APPLICATION_FAILURE)
                                          .setFailureSubType(FailureSubType.AUTHORIZATION)
                                          .build();

    FailureData failureData = FailureData.newBuilder()
                                  .setCode("500")
                                  .setLevel("ERROR")
                                  .setMessage("Test failure message")
                                  .addFailureTypeInfos(failureTypeInfo)
                                  .build();

    FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(failureData).build();

    // When
    FailureInfoAvro result = testSender.mapFailureInfoProtoToAvro(failureInfo);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getFailureData()).hasSize(1);

    FailureDataAvro failureDataAvro = result.getFailureData().get(0);
    assertThat(failureDataAvro.getCode()).isEqualTo("500");
    assertThat(failureDataAvro.getLevel()).isEqualTo("ERROR");
    assertThat(failureDataAvro.getMessage()).isEqualTo("Test failure message");
    assertThat(failureDataAvro.getFailureType()).isEqualTo("APPLICATION_FAILURE");
    assertThat(failureDataAvro.getFailureSubType()).isEqualTo("AUTHORIZATION");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testMapFailureInfoProtoToAvro_WithMultipleFailureData() {
    // Given
    FailureData failureData1 =
        FailureData.newBuilder().setCode("400").setLevel("WARN").setMessage("First failure").build();

    FailureData failureData2 =
        FailureData.newBuilder().setCode("500").setLevel("ERROR").setMessage("Second failure").build();

    FailureInfo failureInfo =
        FailureInfo.newBuilder().addFailureData(failureData1).addFailureData(failureData2).build();

    // When
    FailureInfoAvro result = testSender.mapFailureInfoProtoToAvro(failureInfo);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getFailureData()).hasSize(2);
    assertThat(result.getFailureData().get(0).getMessage()).isEqualTo("First failure");
    assertThat(result.getFailureData().get(0).getCode()).isEqualTo("400");
    assertThat(result.getFailureData().get(1).getMessage()).isEqualTo("Second failure");
    assertThat(result.getFailureData().get(1).getCode()).isEqualTo("500");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testMapFailureInfoProtoToAvro_WithErrorMessageFallback() {
    // Given
    FailureInfo failureInfo = FailureInfo.newBuilder().setErrorMessage("Fallback error message").build();

    // When
    FailureInfoAvro result = testSender.mapFailureInfoProtoToAvro(failureInfo);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getFailureData()).hasSize(1);

    FailureDataAvro failureDataAvro = result.getFailureData().get(0);
    assertThat(failureDataAvro.getCode()).isNull();
    assertThat(failureDataAvro.getLevel()).isNull();
    assertThat(failureDataAvro.getMessage()).isEqualTo("Fallback error message");
    assertThat(failureDataAvro.getFailureType()).isNull();
    assertThat(failureDataAvro.getFailureSubType()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testMapFailureInfoProtoToAvro_WithEmptyFailureTypeInfos() {
    // Given
    FailureData failureData = FailureData.newBuilder().setCode("404").setLevel("INFO").setMessage("Not found").build();

    FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(failureData).build();

    // When
    FailureInfoAvro result = testSender.mapFailureInfoProtoToAvro(failureInfo);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getFailureData()).hasSize(1);

    FailureDataAvro failureDataAvro = result.getFailureData().get(0);
    assertThat(failureDataAvro.getCode()).isEqualTo("404");
    assertThat(failureDataAvro.getLevel()).isEqualTo("INFO");
    assertThat(failureDataAvro.getMessage()).isEqualTo("Not found");
    assertThat(failureDataAvro.getFailureType()).isNull();
    assertThat(failureDataAvro.getFailureSubType()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testMapFailureInfoProtoToAvro_WithNullFailureInfo() {
    // When
    FailureInfoAvro result = testSender.mapFailureInfoProtoToAvro(null);

    // Then
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testMapFailureInfoProtoToAvro_WithEmptyFailureInfo() {
    // Given
    FailureInfo failureInfo = FailureInfo.newBuilder().build();

    // When
    FailureInfoAvro result = testSender.mapFailureInfoProtoToAvro(failureInfo);

    // Then
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testIsKafkaProducerInitialized_WhenPresent() {
    // Given - Confluent producer initialized, FF off (default)
    testSender.hKafkaAvroProducerOptional = Optional.of(hKafkaAvroProducer);

    // When
    boolean result = testSender.isKafkaProducerInitialized("acc123");

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testIsKafkaProducerInitialized_WhenEmpty() {
    // Given - Confluent producer empty, FF off so Confluent is the active producer
    testSender.hKafkaAvroProducerOptional = Optional.empty();

    // When
    boolean result = testSender.isKafkaProducerInitialized("acc123");

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetActiveAvroProducer_FfOff_ReturnsConfluentProducer() {
    // Given
    testSender.hKafkaAvroProducerOptional = Optional.of(hKafkaAvroProducer);
    testSender.hKafkaAvroProducerGeneralOptional = Optional.empty();
    when(featureFlagService.isEnabled("acc123", FeatureName.PIPE_PUBLISH_DATA_INGESTION_EVENTS_TO_GENERAL_KAFKA))
        .thenReturn(false);

    // When
    Optional<HKafkaAvroProducer> result = testSender.getActiveAvroProducer("acc123");

    // Then
    assertThat(result).isPresent();
    assertThat(result.get()).isSameAs(hKafkaAvroProducer);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetActiveAvroProducer_FfOn_ReturnsGeneralProducer() {
    // Given
    HKafkaAvroProducer hKafkaAvroProducerGeneral = org.mockito.Mockito.mock(HKafkaAvroProducer.class);
    testSender.hKafkaAvroProducerOptional = Optional.of(hKafkaAvroProducer);
    testSender.hKafkaAvroProducerGeneralOptional = Optional.of(hKafkaAvroProducerGeneral);
    when(featureFlagService.isEnabled("acc123", FeatureName.PIPE_PUBLISH_DATA_INGESTION_EVENTS_TO_GENERAL_KAFKA))
        .thenReturn(true);

    // When
    Optional<HKafkaAvroProducer> result = testSender.getActiveAvroProducer("acc123");

    // Then
    assertThat(result).isPresent();
    assertThat(result.get()).isSameAs(hKafkaAvroProducerGeneral);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetActiveAvroProducer_FfOn_GeneralProducerEmpty_ReturnsEmpty() {
    // Given - FF on but General producer not initialized; should return empty (caller must guard)
    testSender.hKafkaAvroProducerOptional = Optional.of(hKafkaAvroProducer);
    testSender.hKafkaAvroProducerGeneralOptional = Optional.empty();
    when(featureFlagService.isEnabled("acc123", FeatureName.PIPE_PUBLISH_DATA_INGESTION_EVENTS_TO_GENERAL_KAFKA))
        .thenReturn(true);

    // When
    Optional<HKafkaAvroProducer> result = testSender.getActiveAvroProducer("acc123");

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testIsGeneralKafkaStringProducerInitialized_WhenPresent() {
    // Given
    testSender.hKafkaGeneralStringProducerOptional = Optional.of(hKafkaGeneralStringProducer);

    // When
    boolean result = testSender.isGeneralKafkaStringProducerInitialized();

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testIsGeneralKafkaStringProducerInitialized_WhenEmpty() {
    // Given
    testSender.hKafkaGeneralStringProducerOptional = Optional.empty();

    // When
    boolean result = testSender.isGeneralKafkaStringProducerInitialized();

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummary() {
    // Given
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId("exec123")
                            .putSetupAbstractions("accountId", "acc123")
                            .putSetupAbstractions("orgIdentifier", "org123")
                            .putSetupAbstractions("projectIdentifier", "proj123")
                            .build();
    PipelineExecutionSummaryEntity expectedSummary = PipelineExecutionSummaryEntity.builder().build();

    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(eq("acc123"), eq("exec123"), any()))
        .thenReturn(expectedSummary);

    // When
    PipelineExecutionSummaryEntity result = testSender.getPipelineExecutionSummary(ambiance);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedSummary);
    verify(pmsExecutionSummaryService).getPipelineExecutionSummaryWithProjections(eq("acc123"), eq("exec123"), any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGenerateExecutionUrl() {
    // Given
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId("exec123")
            .putSetupAbstractions("accountId", "acc123")
            .putSetupAbstractions("orgIdentifier", "org123")
            .putSetupAbstractions("projectIdentifier", "proj123")
            .setMetadata(
                io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder().setPipelineIdentifier("pipe123").build())
            .build();
    String expectedUrl = "https://app.harness.io/executions/exec123";

    when(pipelineExpressionHelper.generateUrl(
             eq("acc123"), eq("org123"), eq("proj123"), eq("pipe123"), eq("exec123"), any()))
        .thenReturn(expectedUrl);

    // When
    String result = testSender.generateExecutionUrl(ambiance);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedUrl);
    verify(pipelineExpressionHelper)
        .generateUrl(eq("acc123"), eq("org123"), eq("proj123"), eq("pipe123"), eq("exec123"), any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testProcessOutboxEvent_WhenFeatureEnabled() {
    // Given
    String accountId = "acc123";
    String topicName = "test-topic";
    Object avroObject = org.mockito.Mockito.mock(org.apache.avro.generic.GenericRecord.class);

    when(featureFlagService.isEnabled(accountId, FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA)).thenReturn(true);
    testSender.hKafkaAvroProducerOptional = Optional.of(hKafkaAvroProducer);

    // When
    boolean result = testSender.processOutboxEvent(
        accountId, FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA, topicName, avroObject, "key123", "STEP_END");

    // Then - method completes without exception (actual result depends on async callback)
    verify(featureFlagService).isEnabled(accountId, FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA);
    verify(hKafkaAvroProducer).send(eq(topicName), any(), any(), eq("key123"), any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testProcessOutboxEvent_WhenFeatureDisabled() {
    // Given
    String accountId = "acc123";
    String topicName = "test-topic";
    Object avroObject = org.mockito.Mockito.mock(org.apache.avro.generic.GenericRecord.class);

    when(featureFlagService.isEnabled(accountId, FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA)).thenReturn(false);

    // When
    boolean result = testSender.processOutboxEvent(
        accountId, FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA, topicName, avroObject, "key123", "STEP_END");

    // Then
    assertThat(result).isFalse();
    verify(featureFlagService).isEnabled(accountId, FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testProcessOutboxEvent_WhenKafkaProducerNotInitialized() {
    // Given
    String accountId = "acc123";
    String topicName = "test-topic";
    Object avroObject = org.mockito.Mockito.mock(org.apache.avro.generic.GenericRecord.class);

    when(featureFlagService.isEnabled(accountId, FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA)).thenReturn(true);
    testSender.hKafkaAvroProducerOptional = Optional.empty();

    // When
    boolean result = testSender.processOutboxEvent(
        accountId, FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA, topicName, avroObject, "key123", "STEP_END");

    // Then
    assertThat(result).isFalse();
    verify(featureFlagService).isEnabled(accountId, FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testEmitKafkaEventMetric_Success() {
    testSender.emitKafkaEventMetric("pipeline", "pipeline-topic", "nodeStart", "success");

    verify(metricService).incCounter("kafka_producer_event_sent_count");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testEmitKafkaEventMetric_Failure() {
    testSender.emitKafkaEventMetric("step", "step-topic", "nodeEnd", "failure");

    verify(metricService).incCounter("kafka_producer_event_sent_count");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testEmitKafkaEventMetric_NullMetricService_NoException() {
    testSender.metricService = null;

    testSender.emitKafkaEventMetric("stage", "stage-topic", "nodeEnd", "success");

    verify(metricService, never()).incCounter("kafka_producer_event_sent_count");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreateFailureHandlingCallback_OnSuccess_EmitsSuccessMetric() {
    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .nodeExecution(NodeExecution.builder().uuid("test-id").build())
                                        .type("NODE_END")
                                        .build();
    Callback callback = testSender.createFailureHandlingCallback(
        nodeOutboxInfo, Ambiance.newBuilder().build(), "TEST_EVENT", nodeInfo -> null, "test-topic", "step", "nodeEnd");

    callback.onCompletion(null, null);

    verify(metricService).incCounter("kafka_producer_event_sent_count");
  }

  // Concrete implementation for testing abstract class
  private static class TestAbstractEventKafkaSender extends AbstractEventKafkaSender {
    @Override
    public void sendEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance, Callback callback, String eventType) {
      // Test implementation
    }
  }
}
