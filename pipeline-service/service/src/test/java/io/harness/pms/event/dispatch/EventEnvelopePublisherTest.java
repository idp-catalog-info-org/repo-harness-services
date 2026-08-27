/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.dispatch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.PmsFeatureFlagService;

import com.google.protobuf.Message;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class EventEnvelopePublisherTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccount";
  private static final String ORG_ID = "testOrg";
  private static final String PROJECT_ID = "testProject";
  private static final String PIPELINE_ID = "testPipeline";
  private static final String PLAN_EXECUTION_ID = "testExecution123";

  @Mock private HKafkaProtoProducer kafkaProducer;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;

  private EventEnvelopePublisher eventEnvelopePublisher;
  private Ambiance ambiance;

  @Before
  public void setUp() {
    eventEnvelopePublisher = new EventEnvelopePublisher(Optional.of(kafkaProducer), pmsFeatureFlagService);
    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                   .putSetupAbstractions("orgIdentifier", ORG_ID)
                   .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                   .putSetupAbstractions("pipelineIdentifier", PIPELINE_ID)
                   .addLevels(Level.newBuilder().setIdentifier("pipeline").build())
                   .build();
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testPublishPipelineEvent_Success() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.UDP_EVENT_DISPATCH_ENABLED)).thenReturn(true);
    eventEnvelopePublisher.publishPipelineEvent(ambiance, Status.SUCCEEDED);
    verify(kafkaProducer)
        .send(eq("harness-event-dispatch-normalized-event"), any(Message.class), anyMap(), eq(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testPublishPipelineEvent_FeatureFlagDisabled() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.UDP_EVENT_DISPATCH_ENABLED)).thenReturn(false);
    eventEnvelopePublisher.publishPipelineEvent(ambiance, Status.SUCCEEDED);
    verify(kafkaProducer, never()).send(anyString(), any(Message.class), anyMap(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testPublishPipelineEvent_NoKafkaProducer() {
    eventEnvelopePublisher = new EventEnvelopePublisher(Optional.empty(), pmsFeatureFlagService);
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.UDP_EVENT_DISPATCH_ENABLED)).thenReturn(true);
    eventEnvelopePublisher.publishPipelineEvent(ambiance, Status.SUCCEEDED);
    verify(kafkaProducer, never()).send(anyString(), any(Message.class), anyMap(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testPublishPipelineEvent_Failed() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.UDP_EVENT_DISPATCH_ENABLED)).thenReturn(true);
    eventEnvelopePublisher.publishPipelineEvent(ambiance, Status.FAILED);
    verify(kafkaProducer)
        .send(eq("harness-event-dispatch-normalized-event"), any(Message.class), anyMap(), eq(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = OwnerRule.SHALINI)
  @Category(UnitTests.class)
  public void testPublishPipelineEvent_KafkaException_DoesNotThrow() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.UDP_EVENT_DISPATCH_ENABLED)).thenReturn(true);
    doThrow(new RuntimeException("Kafka send failed"))
        .when(kafkaProducer)
        .send(anyString(), any(Message.class), anyMap(), anyString());
    eventEnvelopePublisher.publishPipelineEvent(ambiance, Status.SUCCEEDED);
    verify(kafkaProducer).send(anyString(), any(Message.class), anyMap(), anyString());
  }
}
