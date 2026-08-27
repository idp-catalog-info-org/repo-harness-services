/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.events.dispatch.v1.EventEnvelope;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class PipelineEventEnvelopeBuilderTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccount";
  private static final String ORG_ID = "testOrg";
  private static final String PROJECT_ID = "testProject";
  private static final String PIPELINE_ID = "testPipeline";
  private static final String PLAN_EXECUTION_ID = "testExecution123";

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .putSetupAbstractions("accountId", ACCOUNT_ID)
        .putSetupAbstractions("orgIdentifier", ORG_ID)
        .putSetupAbstractions("projectIdentifier", PROJECT_ID)
        .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
        .addLevels(Level.newBuilder().setIdentifier("pipeline").build())
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testBuild_Success_ProducesCompletedEvent() {
    EventEnvelope envelope = PipelineEventEnvelopeBuilder.build(buildAmbiance(), Status.SUCCEEDED);
    assertThat(envelope.getType()).isEqualTo("harness.pipeline.completed");
    assertThat(envelope.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(envelope.getOrgId()).isEqualTo(ORG_ID);
    assertThat(envelope.getProjectId()).isEqualTo(PROJECT_ID);
    assertThat(envelope.getSource()).isEqualTo("harness");
    assertThat(envelope.getCorrelationId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(envelope.getPayload().getFieldsMap()).containsKey("pipeline_identifier");
    assertThat(envelope.getPayload().getFieldsMap().get("pipeline_identifier").getStringValue()).isEqualTo(PIPELINE_ID);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testBuild_Failed_ProducesFailedEvent() {
    EventEnvelope envelope = PipelineEventEnvelopeBuilder.build(buildAmbiance(), Status.FAILED);
    assertThat(envelope.getType()).isEqualTo("harness.pipeline.failed");
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testBuild_IgnoreFailed_ProducesCompletedEvent() {
    EventEnvelope envelope = PipelineEventEnvelopeBuilder.build(buildAmbiance(), Status.IGNORE_FAILED);
    assertThat(envelope.getType()).isEqualTo("harness.pipeline.completed");
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testBuild_Running_ProducesStatusUpdateEvent() {
    EventEnvelope envelope = PipelineEventEnvelopeBuilder.build(buildAmbiance(), Status.RUNNING);
    assertThat(envelope.getType()).isEqualTo("harness.pipeline.status_update");
    assertThat(envelope.getSchemaVersion()).isEqualTo("v1");
    assertThat(envelope.getHopCount()).isEqualTo(0);
    assertThat(envelope.getSource()).isEqualTo("harness");
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testBuild_Errored_ProducesFailedEvent() {
    EventEnvelope envelope = PipelineEventEnvelopeBuilder.build(buildAmbiance(), Status.ERRORED);
    assertThat(envelope.getType()).isEqualTo("harness.pipeline.failed");
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testBuild_PayloadContainsAllFields() {
    EventEnvelope envelope = PipelineEventEnvelopeBuilder.build(buildAmbiance(), Status.SUCCEEDED);
    assertThat(envelope.getPayload().getFieldsMap()).containsKey("execution_id");
    assertThat(envelope.getPayload().getFieldsMap().get("execution_id").getStringValue()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(envelope.getPayload().getFieldsMap()).containsKey("status");
    assertThat(envelope.getPayload().getFieldsMap().get("status").getStringValue()).isEqualTo("SUCCEEDED");
    assertThat(envelope.getId()).isNotEmpty();
    assertThat(envelope.getTime().getSeconds()).isGreaterThan(0);
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testBuild_Aborted_ProducesStatusUpdateEvent() {
    EventEnvelope envelope = PipelineEventEnvelopeBuilder.build(buildAmbiance(), Status.ABORTED);
    assertThat(envelope.getType()).isEqualTo("harness.pipeline.status_update");
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testBuild_EmptyPipelineIdentifier_HandledGracefully() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(PLAN_EXECUTION_ID)
                            .putSetupAbstractions("accountId", ACCOUNT_ID)
                            .putSetupAbstractions("orgIdentifier", ORG_ID)
                            .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .addLevels(Level.newBuilder().setIdentifier("pipeline").build())
                            .build();
    EventEnvelope envelope = PipelineEventEnvelopeBuilder.build(ambiance, Status.SUCCEEDED);
    assertThat(envelope.getPayload().getFieldsMap().get("pipeline_identifier").getStringValue()).isEmpty();
  }
}
