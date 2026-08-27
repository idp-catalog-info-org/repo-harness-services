/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.ngtriggers.expressions.functors.EventPayloadFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class EventPayloadFunctorTest extends CategoryTest {
  @InjectMocks EventPayloadFunctor eventPayloadFunctor;
  @Mock PlanExecutionMetadataService metadataService;
  @Mock PlanExecutionService planExecutionService;
  @Mock Ambiance ambiance;

  private String bigPayload = "{\n"
      + " \"Type\" : \"Notification\",\n"
      + " \"Timestamp\" : \"2021-03-23T20:42:23.163Z\",\n"
      + " \"SignatureVersion\" : \"1\",\n"
      + " \"UnsubscribeURL\" : "
      + "\"https://sns.eu-central-1.amazonaws.com/"
      + "?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-central-1:448640225317:aws_cc_push_trigger:bf6ca40a-eb7c-"
      + "43a8-b452-ec5869813da4\"\n"
      + "}";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testBind() throws IOException {
    when(ambiance.getPlanExecutionId()).thenReturn("");
    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.checkIfFeatureFlagEnabled(
             eq(ambiance), eq(FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name())))
        .thenReturn(false);
    testEventPayloadFunctorBind();
    when(AmbianceUtils.checkIfFeatureFlagEnabled(
             eq(ambiance), eq(FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name())))
        .thenReturn(true);
    testEventPayloadFunctorBind();
  }

  private void testEventPayloadFunctorBind() {
    when(metadataService.findByPlanExecutionId(any(), any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().triggerJsonPayload(bigPayload).build()));
    when(planExecutionService.getWithFieldsIncludedOptional(
             ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.triggerJsonPayload)))
        .thenReturn(Optional.of(PlanExecution.builder().triggerJsonPayload(bigPayload).build()));

    Object object = eventPayloadFunctor.bind();
    assertThat(((HashMap) object).get("Timestamp")).isEqualTo("2021-03-23T20:42:23.163Z");
    assertThat(((HashMap) object).get("Type")).isEqualTo("Notification");

    // triggerJsonPayload null case
    when(metadataService.findByPlanExecutionId(any(), any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));
    when(planExecutionService.getWithFieldsIncludedOptional(
             ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.triggerJsonPayload)))
        .thenReturn(Optional.of(PlanExecution.builder().build()));
    assertThat(eventPayloadFunctor.bind()).isNull();

    // IOException case
    String triggerJsonPayload = ",";
    when(metadataService.findByPlanExecutionId(any(), any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().triggerJsonPayload(triggerJsonPayload).build()));
    when(planExecutionService.getWithFieldsIncludedOptional(
             ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.triggerJsonPayload)))
        .thenReturn(Optional.of(PlanExecution.builder().triggerJsonPayload(triggerJsonPayload).build()));
    assertThat(eventPayloadFunctor.bind()).isEqualTo(",");

    when(metadataService.findByPlanExecutionId(any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> eventPayloadFunctor.bind())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("PlanExecution metadata null for planExecutionId ");
  }
}
