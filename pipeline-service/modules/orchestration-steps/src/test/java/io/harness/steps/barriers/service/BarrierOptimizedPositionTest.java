/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.barriers.service;

import static io.harness.rule.OwnerRule.EDGAR_GARCIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rule.Owner;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;

import com.mongodb.client.result.UpdateResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

public class BarrierOptimizedPositionTest {
  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private BarrierServiceImpl barrierService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldBuildCorrectOrConditionWith5Conditions_whenDummyPositionFixEnabled() {
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));

    String positionSetupId = "step1";
    String positionExecutionId = "exec1";
    String stageExecutionId = "stageExec1";
    String stepGroupExecutionId = "sgExec1";

    BarrierExecutionInstance instance = createTestBarrierInstance();

    // Execute - disableDummyPositionFix = false (5th condition enabled)
    barrierService.updatePosition(BarrierPositionInfo.BarrierPosition.BarrierPositionType.STEP, positionSetupId,
        positionExecutionId, stageExecutionId, stepGroupExecutionId, Collections.singletonList(instance),
        true, // optimizationFFEnabled
        false);

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).updateFirst(any(Query.class), updateCaptor.capture(), eq(BarrierExecutionInstance.class));

    Update update = updateCaptor.getValue();
    List<UpdateDefinition.ArrayFilter> arrayFilters = update.getArrayFilters();

    assertThat(arrayFilters).isNotEmpty();
    UpdateDefinition.ArrayFilter orFilter = arrayFilters.get(0);
    List<Document> orConditions = (List<Document>) orFilter.asDocument().get("$or");

    // Should have 5 conditions when dummy position fix is enabled
    assertThat(orConditions).hasSize(5);

    // Condition 1: stepSetupId=X, stageRuntimeId=Y, stepGroupRuntimeId=Z
    Document condition1 = orConditions.get(0);
    assertThat(condition1.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition1.get("position.stageRuntimeId")).isEqualTo(stageExecutionId);
    assertThat(condition1.get("position.stepGroupRuntimeId")).isEqualTo(stepGroupExecutionId);

    // Condition 2: stepSetupId=X, stageRuntimeId=Y, stepGroupRuntimeId=null
    Document condition2 = orConditions.get(1);
    assertThat(condition2.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition2.get("position.stageRuntimeId")).isEqualTo(stageExecutionId);
    assertThat(condition2.get("position.stepGroupRuntimeId")).isNull();

    // Condition 3: stepSetupId=X, stepGroupRuntimeId=Z, stageRuntimeId=null
    Document condition3 = orConditions.get(2);
    assertThat(condition3.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition3.get("position.stepGroupRuntimeId")).isEqualTo(stepGroupExecutionId);
    assertThat(condition3.get("position.stageRuntimeId")).isNull();

    // Condition 4: stepSetupId=X, stageRuntimeId=null, stepGroupRuntimeId=null
    Document condition4 = orConditions.get(3);
    assertThat(condition4.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition4.get("position.stageRuntimeId")).isNull();
    assertThat(condition4.get("position.stepGroupRuntimeId")).isNull();

    // Condition 5: stepSetupId=X, stepRuntimeId=null, stageRuntimeId=Y, stepGroupRuntimeId=Z
    Document condition5 = orConditions.get(4);
    assertThat(condition5.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition5.get("position.stepRuntimeId")).isNull();
    assertThat(condition5.get("position.stageRuntimeId")).isEqualTo(stageExecutionId);
    assertThat(condition5.get("position.stepGroupRuntimeId")).isEqualTo(stepGroupExecutionId);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldBuildCondition5_dummyPositionWithStageContextOnly() {
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));

    String positionSetupId = "step1";
    String positionExecutionId = "exec1";
    String stageExecutionId = "stageExec1";
    String stepGroupExecutionId = null; // No step group context

    barrierService.updatePosition(
        BarrierPositionInfo.BarrierPosition.BarrierPositionType.STEP, positionSetupId, positionExecutionId,
        stageExecutionId, stepGroupExecutionId, Collections.singletonList(createTestBarrierInstance()), true,
        false // 5th condition enabled
    );

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).updateFirst(any(Query.class), updateCaptor.capture(), eq(BarrierExecutionInstance.class));

    List<Document> orConditions = extractOrConditions(updateCaptor.getValue());

    // Condition 5: stepSetupId=X, stepRuntimeId=null, stageRuntimeId=Y, stepGroupRuntimeId=null
    Document condition5 = orConditions.get(4);
    assertThat(condition5.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition5.get("position.stepRuntimeId")).isNull();
    assertThat(condition5.get("position.stageRuntimeId")).isEqualTo(stageExecutionId);
    assertThat(condition5.get("position.stepGroupRuntimeId")).isNull();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldBuildCondition5_dummyPositionWithStepGroupContextOnly() {
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));

    String positionSetupId = "step1";
    String positionExecutionId = "exec1";
    String stageExecutionId = null; // No stage context
    String stepGroupExecutionId = "sgExec1";

    barrierService.updatePosition(
        BarrierPositionInfo.BarrierPosition.BarrierPositionType.STEP, positionSetupId, positionExecutionId,
        stageExecutionId, stepGroupExecutionId, Collections.singletonList(createTestBarrierInstance()), true,
        false // 5th condition enabled
    );

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).updateFirst(any(Query.class), updateCaptor.capture(), eq(BarrierExecutionInstance.class));

    List<Document> orConditions = extractOrConditions(updateCaptor.getValue());

    // Condition 5: stepSetupId=X, stepRuntimeId=null, stageRuntimeId=null, stepGroupRuntimeId=Z
    Document condition5 = orConditions.get(4);
    assertThat(condition5.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition5.get("position.stepRuntimeId")).isNull();
    assertThat(condition5.get("position.stageRuntimeId")).isNull();
    assertThat(condition5.get("position.stepGroupRuntimeId")).isEqualTo(stepGroupExecutionId);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldBuildCondition5_dummyPositionWithNoContext() {
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));

    String positionSetupId = "step1";
    String positionExecutionId = "exec1";
    String stageExecutionId = null; // No stage context
    String stepGroupExecutionId = null; // No step group context

    barrierService.updatePosition(
        BarrierPositionInfo.BarrierPosition.BarrierPositionType.STEP, positionSetupId, positionExecutionId,
        stageExecutionId, stepGroupExecutionId, Collections.singletonList(createTestBarrierInstance()), true,
        false // 5th condition enabled
    );

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).updateFirst(any(Query.class), updateCaptor.capture(), eq(BarrierExecutionInstance.class));

    List<Document> orConditions = extractOrConditions(updateCaptor.getValue());

    // Condition 5: stepSetupId=X, stepRuntimeId=null, stageRuntimeId=null, stepGroupRuntimeId=null
    Document condition5 = orConditions.get(4);
    assertThat(condition5.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition5.get("position.stepRuntimeId")).isNull();
    assertThat(condition5.get("position.stageRuntimeId")).isNull();
    assertThat(condition5.get("position.stepGroupRuntimeId")).isNull();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldBuildCorrectOrConditionWith4Conditions_whenDummyPositionFixDisabled() {
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), any(Class.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));

    String positionSetupId = "step1";
    String positionExecutionId = "exec1";
    String stageExecutionId = "stageExec1";
    String stepGroupExecutionId = "sgExec1";

    BarrierExecutionInstance instance = createTestBarrierInstance();

    // Execute - disableDummyPositionFix = true (5th condition disabled)
    barrierService.updatePosition(BarrierPositionInfo.BarrierPosition.BarrierPositionType.STEP, positionSetupId,
        positionExecutionId, stageExecutionId, stepGroupExecutionId, Collections.singletonList(instance),
        true, // optimizationFFEnabled
        true // disableDummyPositionFix = true (5th condition DISABLED)
    );

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).updateFirst(any(Query.class), updateCaptor.capture(), eq(BarrierExecutionInstance.class));

    Update update = updateCaptor.getValue();
    List<UpdateDefinition.ArrayFilter> arrayFilters = update.getArrayFilters();

    assertThat(arrayFilters).isNotEmpty();
    UpdateDefinition.ArrayFilter orFilter = arrayFilters.get(0);
    List<Document> orConditions = (List<Document>) orFilter.asDocument().get("$or");

    // Should have only 4 conditions when dummy position fix is disabled
    assertThat(orConditions).hasSize(4);

    // Condition 1: stepSetupId=X, stageRuntimeId=Y, stepGroupRuntimeId=Z
    Document condition1 = orConditions.get(0);
    assertThat(condition1.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition1.get("position.stageRuntimeId")).isEqualTo(stageExecutionId);
    assertThat(condition1.get("position.stepGroupRuntimeId")).isEqualTo(stepGroupExecutionId);

    // Condition 2: stepSetupId=X, stageRuntimeId=Y, stepGroupRuntimeId=null
    Document condition2 = orConditions.get(1);
    assertThat(condition2.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition2.get("position.stageRuntimeId")).isEqualTo(stageExecutionId);
    assertThat(condition2.get("position.stepGroupRuntimeId")).isNull();

    // Condition 3: stepSetupId=X, stepGroupRuntimeId=Z, stageRuntimeId=null
    Document condition3 = orConditions.get(2);
    assertThat(condition3.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition3.get("position.stepGroupRuntimeId")).isEqualTo(stepGroupExecutionId);
    assertThat(condition3.get("position.stageRuntimeId")).isNull();

    // Condition 4: stepSetupId=X, stageRuntimeId=null, stepGroupRuntimeId=null
    Document condition4 = orConditions.get(3);
    assertThat(condition4.get("position.stepSetupId")).isEqualTo(positionSetupId);
    assertThat(condition4.get("position.stageRuntimeId")).isNull();
    assertThat(condition4.get("position.stepGroupRuntimeId")).isNull();
  }

  private List<Document> extractOrConditions(Update update) {
    List<UpdateDefinition.ArrayFilter> arrayFilters = update.getArrayFilters();
    assertThat(arrayFilters).isNotEmpty();
    UpdateDefinition.ArrayFilter orFilter = arrayFilters.get(0);
    return (List<Document>) orFilter.asDocument().get("$or");
  }

  private BarrierExecutionInstance createTestBarrierInstance() {
    return BarrierExecutionInstance.builder()
        .uuid(UUIDGenerator.generateUuid())
        .identifier("barrier1")
        .planExecutionId(UUIDGenerator.generateUuid())
        .positionInfo(BarrierPositionInfo.builder()
                          .barrierPositionList(Arrays.asList(BarrierPositionInfo.BarrierPosition.builder()
                                                                 .stepSetupId("step1")
                                                                 .stageSetupId("stage1")
                                                                 .stepGroupSetupId("stepGroup1")
                                                                 .build()))
                          .build())
        .build();
  }
}
