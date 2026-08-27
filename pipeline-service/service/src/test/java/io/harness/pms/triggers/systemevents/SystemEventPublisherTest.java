/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.systemevents;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.ngtriggers.beans.source.systemevents.SystemEventType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class SystemEventPublisherTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccount";
  private static final String PLAN_EXECUTION_ID = "execId123";

  @Mock private Producer eventProducer;
  @Mock private PmsFeatureFlagService featureFlagService;

  private SystemEventPublisher publisher;
  private Ambiance ambiance;

  @Before
  public void setUp() {
    publisher = new SystemEventPublisher(eventProducer, featureFlagService);
    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                   .putSetupAbstractions("orgIdentifier", "testOrg")
                   .putSetupAbstractions("projectIdentifier", "testProject")
                   .build();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testPublish_FeatureFlagEnabled_PublishesToStream() {
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.SYSTEM_EVENTS_TRIGGERS)).thenReturn(true);
    when(eventProducer.send(any())).thenReturn("msgId1");

    publisher.publish(ambiance, SystemEventType.PIPELINE_SUCCESS);

    verify(eventProducer).send(any());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testPublish_FeatureFlagDisabled_DoesNotPublish() {
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.SYSTEM_EVENTS_TRIGGERS)).thenReturn(false);

    publisher.publish(ambiance, SystemEventType.PIPELINE_SUCCESS);

    verify(eventProducer, never()).send(any());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testPublish_ProducerThrows_DoesNotPropagateException() {
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.SYSTEM_EVENTS_TRIGGERS)).thenReturn(true);
    when(eventProducer.send(any())).thenThrow(new RuntimeException("Redis down"));

    // Should not throw
    publisher.publish(ambiance, SystemEventType.PIPELINE_FAILURE);

    verify(eventProducer).send(any());
  }
}
