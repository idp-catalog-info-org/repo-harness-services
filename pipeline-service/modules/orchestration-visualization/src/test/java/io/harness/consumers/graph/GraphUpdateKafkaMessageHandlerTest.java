/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.consumers.graph;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GraphUpdateKafkaMessageHandlerTest extends CategoryTest {
  @Mock private GraphGenerationService graphGenerationService;

  private GraphUpdateKafkaMessageHandler messageHandler;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    messageHandler = new GraphUpdateKafkaMessageHandler(graphGenerationService);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleEventWithValidPlanExecutionId() {
    String planExecutionId = "plan123";
    OrchestrationLogEvent event = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();

    when(graphGenerationService.updateGraph(planExecutionId)).thenReturn(true);

    messageHandler.handleEvent(event, Collections.emptyMap(), Collections.emptyMap());

    verify(graphGenerationService, times(1)).updateGraph(planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleNullEvent() {
    messageHandler.handleEvent(null, Collections.emptyMap(), Collections.emptyMap());

    verify(graphGenerationService, never()).updateGraph(anyString());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleEventWithEmptyPlanExecutionId() {
    OrchestrationLogEvent event = OrchestrationLogEvent.newBuilder().setPlanExecutionId("").build();

    messageHandler.handleEvent(event, Collections.emptyMap(), Collections.emptyMap());

    verify(graphGenerationService, never()).updateGraph(anyString());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleEventWithException() {
    String planExecutionId = "plan-with-error";
    OrchestrationLogEvent event = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();

    when(graphGenerationService.updateGraph(planExecutionId)).thenThrow(new RuntimeException("Graph update failed"));

    // Should not throw exception, should log and handle gracefully
    assertThatCode(() -> messageHandler.handleEvent(event, Collections.emptyMap(), Collections.emptyMap()))
        .doesNotThrowAnyException();

    verify(graphGenerationService, times(1)).updateGraph(planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleEventWithMetadata() {
    String planExecutionId = "plan456";
    Map<String, String> metadata = ImmutableMap.of("key1", "value1", "key2", "value2");
    Map<String, Object> metricInfo = ImmutableMap.of("metric1", 100L, "metric2", "info");

    OrchestrationLogEvent event = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();

    when(graphGenerationService.updateGraph(planExecutionId)).thenReturn(true);

    messageHandler.handleEvent(event, metadata, metricInfo);

    verify(graphGenerationService, times(1)).updateGraph(planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleEventSuccessfulGraphUpdate() {
    String planExecutionId = "successful-plan";
    OrchestrationLogEvent event = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();

    when(graphGenerationService.updateGraph(planExecutionId)).thenReturn(true);

    messageHandler.handleEvent(event, Collections.emptyMap(), Collections.emptyMap());

    verify(graphGenerationService, times(1)).updateGraph(eq(planExecutionId));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleEventFailedGraphUpdate() {
    String planExecutionId = "failed-plan";
    OrchestrationLogEvent event = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();

    when(graphGenerationService.updateGraph(planExecutionId)).thenReturn(false);

    // Should complete without throwing exception even if update returns false
    assertThatCode(() -> messageHandler.handleEvent(event, Collections.emptyMap(), Collections.emptyMap()))
        .doesNotThrowAnyException();

    verify(graphGenerationService, times(1)).updateGraph(planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleEventWithNullMetadata() {
    String planExecutionId = "plan-null-metadata";
    OrchestrationLogEvent event = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();

    when(graphGenerationService.updateGraph(planExecutionId)).thenReturn(true);

    // Should handle null metadata gracefully
    assertThatCode(() -> messageHandler.handleEvent(event, null, null)).doesNotThrowAnyException();

    verify(graphGenerationService, times(1)).updateGraph(planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleMultipleEvents() {
    String planExecutionId1 = "plan001";
    String planExecutionId2 = "plan002";
    String planExecutionId3 = "plan003";

    OrchestrationLogEvent event1 = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId1).build();
    OrchestrationLogEvent event2 = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId2).build();
    OrchestrationLogEvent event3 = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId3).build();

    when(graphGenerationService.updateGraph(anyString())).thenReturn(true);

    messageHandler.handleEvent(event1, Collections.emptyMap(), Collections.emptyMap());
    messageHandler.handleEvent(event2, Collections.emptyMap(), Collections.emptyMap());
    messageHandler.handleEvent(event3, Collections.emptyMap(), Collections.emptyMap());

    verify(graphGenerationService, times(1)).updateGraph(planExecutionId1);
    verify(graphGenerationService, times(1)).updateGraph(planExecutionId2);
    verify(graphGenerationService, times(1)).updateGraph(planExecutionId3);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleEventWithSpecialCharactersInPlanExecutionId() {
    String planExecutionId = "plan-123_ABC!@#$%";
    OrchestrationLogEvent event = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();

    when(graphGenerationService.updateGraph(planExecutionId)).thenReturn(true);

    messageHandler.handleEvent(event, Collections.emptyMap(), Collections.emptyMap());

    verify(graphGenerationService, times(1)).updateGraph(planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleEventExceptionDoesNotAffectSubsequentCalls() {
    String planExecutionId1 = "plan-error";
    String planExecutionId2 = "plan-success";

    OrchestrationLogEvent event1 = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId1).build();
    OrchestrationLogEvent event2 = OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId2).build();

    when(graphGenerationService.updateGraph(planExecutionId1)).thenThrow(new RuntimeException("First call failed"));
    when(graphGenerationService.updateGraph(planExecutionId2)).thenReturn(true);

    // First call should handle exception gracefully
    assertThatCode(() -> messageHandler.handleEvent(event1, Collections.emptyMap(), Collections.emptyMap()))
        .doesNotThrowAnyException();

    // Second call should still work
    assertThatCode(() -> messageHandler.handleEvent(event2, Collections.emptyMap(), Collections.emptyMap()))
        .doesNotThrowAnyException();

    verify(graphGenerationService, times(1)).updateGraph(planExecutionId1);
    verify(graphGenerationService, times(1)).updateGraph(planExecutionId2);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testHandleEventWithNullPlanExecutionIdInEvent() {
    OrchestrationLogEvent event = OrchestrationLogEvent.newBuilder().build();

    messageHandler.handleEvent(event, Collections.emptyMap(), Collections.emptyMap());

    verify(graphGenerationService, never()).updateGraph(anyString());
  }
}
