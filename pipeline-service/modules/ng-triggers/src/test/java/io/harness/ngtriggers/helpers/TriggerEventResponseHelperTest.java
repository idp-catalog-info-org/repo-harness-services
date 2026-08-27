/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_RUNTIME_INPUT_YAML;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SKIPPED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_CONFIRMATION_SUCCESSFUL;
import static io.harness.rule.OwnerRule.ABHIPRANAV;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.OM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.exception.CriticalExpressionEvaluationException;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.artifact.AMIRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.GCEImageRegistrySpec;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.dtos.NGPipelineExecutionResponseDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class TriggerEventResponseHelperTest extends CategoryTest {
  @InjectMocks TriggerEventResponseHelper triggerEventResponseHelper;
  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "pipelineId";
  String triggerIdentifier = "triggerIdentifier";
  String pollingDocId = "pollingDocId";
  String message = "message";
  String uuid = "uuid";
  String payload = "payload";
  String build = "build";
  Long createdAt = 12L;
  List<HeaderConfig> headers = List.of(HeaderConfig.builder().key("testKey").values(List.of("testValue")).build());
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testToResponseWithPollingInfo() {
    TriggerEventResponse.FinalStatus status = TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId(accountId)
                                                  .uuid(uuid)
                                                  .createdAt(createdAt)
                                                  .payload(payload)
                                                  .headers(headers)
                                                  .build();
    NGPipelineExecutionResponseDTO ngPipelineExecutionResponseDTO = NGPipelineExecutionResponseDTO.builder().build();
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .orgIdentifier(orgId)
                                          .projectIdentifier(projectId)
                                          .identifier(triggerIdentifier)
                                          .targetIdentifier(pipelineId)
                                          .type(NGTriggerType.ARTIFACT)
                                          .build();
    NGTriggerConfigV2 ngTriggerConfigV2 = NGTriggerConfigV2.builder()
                                              .source(NGTriggerSourceV2.builder()
                                                          .spec(ArtifactTriggerConfig.builder()
                                                                    .spec(AMIRegistrySpec.builder().build())
                                                                    .type(ArtifactType.AMI)
                                                                    .build())
                                                          .build())
                                              .build();
    TargetExecutionSummary targetExecutionSummary = TargetExecutionSummary.builder().targetId(pipelineId).build();

    TriggerEventResponse response = TriggerEventResponse.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .targetIdentifier(pipelineId)
                                        .eventCorrelationId(uuid)
                                        .headers(headers)
                                        .payload(payload)
                                        .createdAt(createdAt)
                                        .finalStatus(status)
                                        .triggerIdentifier(triggerIdentifier)
                                        .message(message)
                                        .buildSourceType(ArtifactType.AMI.getValue())
                                        .build(build)
                                        .ngTriggerType(NGTriggerType.ARTIFACT)
                                        .targetExecutionSummary(targetExecutionSummary)
                                        .pollingDocId(pollingDocId)
                                        .build();
    TriggerEventResponse triggerEventResponse = TriggerEventResponseHelper.toResponseWithPollingInfo(status,
        triggerWebhookEvent, ngPipelineExecutionResponseDTO, ngTriggerEntity, ngTriggerConfigV2, message,
        targetExecutionSummary, pollingDocId, build);
    assertThat(triggerEventResponse).isEqualTo(response);

    // Without pipeline execution details
    TriggerEventResponse triggerEventResponse1 = TriggerEventResponseHelper.toResponseWithPollingInfo(status,
        triggerWebhookEvent, ngTriggerEntity, ngTriggerConfigV2, message, targetExecutionSummary, pollingDocId, build);
    assertThat(triggerEventResponse1).isEqualTo(response);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testToEntity() {
    TargetExecutionSummary targetExecutionSummary = TargetExecutionSummary.builder().targetId(pipelineId).build();

    TriggerEventResponse response = TriggerEventResponse.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .targetIdentifier(pipelineId)
                                        .createdAt(1234L)
                                        .eventCorrelationId(uuid)
                                        .headers(headers)
                                        .payload(payload)
                                        .createdAt(createdAt)
                                        .finalStatus(SKIPPED)
                                        .triggerIdentifier(triggerIdentifier)
                                        .message(message)
                                        .buildSourceType(ArtifactType.AMI.getValue())
                                        .build(build)
                                        .targetExecutionSummary(targetExecutionSummary)
                                        .pollingDocId(pollingDocId)
                                        .build();

    TriggerEventHistory result = triggerEventResponseHelper.toEntity(response);
    assertThat(result.getExecutionNotAttempted()).isEqualTo(true);

    response = TriggerEventResponse.builder()
                   .accountId(accountId)
                   .orgIdentifier(orgId)
                   .projectIdentifier(projectId)
                   .targetIdentifier(pipelineId)
                   .createdAt(1234L)
                   .eventCorrelationId(uuid)
                   .headers(headers)
                   .payload(payload)
                   .createdAt(createdAt)
                   .finalStatus(TRIGGER_CONFIRMATION_SUCCESSFUL)
                   .triggerIdentifier(triggerIdentifier)
                   .message(message)
                   .buildSourceType(ArtifactType.AMI.getValue())
                   .build(build)
                   .targetExecutionSummary(targetExecutionSummary)
                   .pollingDocId(pollingDocId)
                   .build();

    result = triggerEventResponseHelper.toEntity(response);
    assertThat(result.getExecutionNotAttempted()).isEqualTo(false);
  }

  @Test
  @Owner(developers = ABHIPRANAV)
  @Category(UnitTests.class)
  public void testToResponseWithPollingInfo_GCEImage() {
    TriggerEventResponse.FinalStatus status = TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId(accountId)
                                                  .uuid(uuid)
                                                  .createdAt(createdAt)
                                                  .payload(payload)
                                                  .headers(headers)
                                                  .build();
    NGPipelineExecutionResponseDTO ngPipelineExecutionResponseDTO = NGPipelineExecutionResponseDTO.builder().build();
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .orgIdentifier(orgId)
                                          .projectIdentifier(projectId)
                                          .identifier(triggerIdentifier)
                                          .targetIdentifier(pipelineId)
                                          .type(NGTriggerType.ARTIFACT)
                                          .build();
    NGTriggerConfigV2 ngTriggerConfigV2 = NGTriggerConfigV2.builder()
                                              .source(NGTriggerSourceV2.builder()
                                                          .spec(ArtifactTriggerConfig.builder()
                                                                    .spec(GCEImageRegistrySpec.builder().build())
                                                                    .type(ArtifactType.GCE_IMAGE)
                                                                    .build())
                                                          .build())
                                              .build();
    TargetExecutionSummary targetExecutionSummary = TargetExecutionSummary.builder().targetId(pipelineId).build();

    TriggerEventResponse response = TriggerEventResponse.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .targetIdentifier(pipelineId)
                                        .eventCorrelationId(uuid)
                                        .headers(headers)
                                        .payload(payload)
                                        .createdAt(createdAt)
                                        .finalStatus(status)
                                        .triggerIdentifier(triggerIdentifier)
                                        .message(message)
                                        .buildSourceType(ArtifactType.GCE_IMAGE.getValue())
                                        .build(build)
                                        .ngTriggerType(NGTriggerType.ARTIFACT)
                                        .targetExecutionSummary(targetExecutionSummary)
                                        .pollingDocId(pollingDocId)
                                        .build();
    TriggerEventResponse triggerEventResponse = TriggerEventResponseHelper.toResponseWithPollingInfo(status,
        triggerWebhookEvent, ngPipelineExecutionResponseDTO, ngTriggerEntity, ngTriggerConfigV2, message,
        targetExecutionSummary, pollingDocId, build);
    assertThat(triggerEventResponse).isEqualTo(response);

    // Without pipeline execution details
    TriggerEventResponse triggerEventResponse1 = TriggerEventResponseHelper.toResponseWithPollingInfo(status,
        triggerWebhookEvent, ngTriggerEntity, ngTriggerConfigV2, message, targetExecutionSummary, pollingDocId, build);
    assertThat(triggerEventResponse1).isEqualTo(response);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testExtractErrorMessage_returnsDefaultWhenExceptionIsNull() {
    assertThat(TriggerEventResponseHelper.extractErrorMessage(null))
        .isEqualTo(TriggerEventResponseHelper.DEFAULT_TRIGGER_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testExtractErrorMessage_returnsExceptionMessageWhenPresent() {
    Exception e = new RuntimeException("boom");
    assertThat(TriggerEventResponseHelper.extractErrorMessage(e)).isEqualTo("boom");
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testExtractErrorMessage_fallsBackToClassNameWhenMessageIsNull() {
    Exception e = new NullPointerException();
    String extracted = TriggerEventResponseHelper.extractErrorMessage(e);
    assertThat(extracted).isNotBlank();
    assertThat(extracted).contains("NullPointerException");
    assertThat(extracted).startsWith(TriggerEventResponseHelper.DEFAULT_TRIGGER_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testExtractErrorMessage_fallsBackToClassNameWhenMessageIsBlank() {
    Exception e = new RuntimeException("   ");
    String extracted = TriggerEventResponseHelper.extractErrorMessage(e);
    assertThat(extracted).contains("RuntimeException");
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testExtractErrorMessage_returnsCauseToStringForCriticalExpressionEvaluationExceptionWithCause() {
    IOException rootCause = new IOException("scm unreachable");
    Exception e = new CriticalExpressionEvaluationException("evaluation failed", "<+pipeline.foo>", rootCause);
    String extracted = TriggerEventResponseHelper.extractErrorMessage(e);
    assertThat(extracted).contains("IOException");
    assertThat(extracted).contains("scm unreachable");
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testExtractErrorMessage_doesNotThrowWhenCriticalExpressionEvaluationExceptionHasNullCause() {
    Exception e = new CriticalExpressionEvaluationException("evaluation failed", "<+pipeline.foo>");
    String extracted = TriggerEventResponseHelper.extractErrorMessage(e);
    assertThat(extracted).isNotBlank();
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testToEntity_neverPersistsNullMessage_fallsBackToFinalStatusDescription() {
    TriggerEventResponse response = TriggerEventResponse.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .targetIdentifier(pipelineId)
                                        .eventCorrelationId(uuid)
                                        .createdAt(createdAt)
                                        .finalStatus(INVALID_RUNTIME_INPUT_YAML)
                                        .triggerIdentifier(triggerIdentifier)
                                        .message(null) // simulates a caller that forgot or intentionally passed null
                                        .build();

    TriggerEventHistory entity = triggerEventResponseHelper.toEntity(response);

    assertThat(entity.getMessage()).isNotBlank();
    assertThat(entity.getMessage()).isEqualTo(INVALID_RUNTIME_INPUT_YAML.getMessage());
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testToEntity_neverPersistsNullMessage_fallsBackForSkippedResponse() {
    TriggerEventResponse response = TriggerEventResponse.builder().finalStatus(SKIPPED).build();

    TriggerEventHistory entity = triggerEventResponseHelper.toEntity(response);

    assertThat(entity.getMessage()).isNotBlank();
    assertThat(entity.getMessage()).isEqualTo(SKIPPED.getMessage());
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testToEntity_preservesExplicitMessageWhenPresent() {
    TriggerEventResponse response = TriggerEventResponse.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .targetIdentifier(pipelineId)
                                        .eventCorrelationId(uuid)
                                        .createdAt(createdAt)
                                        .finalStatus(TARGET_EXECUTION_REQUESTED)
                                        .triggerIdentifier(triggerIdentifier)
                                        .message("Pipeline execution was requested successfully")
                                        .build();

    TriggerEventHistory entity = triggerEventResponseHelper.toEntity(response);

    assertThat(entity.getMessage()).isEqualTo("Pipeline execution was requested successfully");
  }
}
