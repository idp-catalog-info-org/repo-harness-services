/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import static io.harness.data.ExecutionSweepingOutputInstance.TTL;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.ExecutionSweepingOutputInstance;
import io.harness.data.ExecutionSweepingOutputInstance.ExecutionSweepingOutputKeys;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.output.PmsSweepingOutput;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.serializer.spring.converters.outputs.PmsSweepingOutputWriteConverter;
import io.harness.utils.DummySweepingOutput;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Date;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSweepingOutputServiceImplV2Test extends OrchestrationTestBase {
  private static final String PLAN_EXE_ID = "plan_execution_id";
  private static final String KEY = "key_id";
  private static final String UUID = "id";
  private static final String ACC_ID = "acc_id";
  private static final String ORG_ID = "org_id";
  private static final String PROJ_ID = "proj_id";
  private static final String STAGE_EXE_ID = "stage_exe_id";
  private static final Binary DUMMY_BINARY_VALUE = new Binary(new byte[5]);
  @Mock private MongoTemplate mongoTemplate;
  @Mock private PmsSweepingOutputWriteConverter pmsSweepingOutputWriteConverter;
  @Inject @InjectMocks private PmsSweepingOutputService pmsSweepingOutputService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(mongoTemplate.insert(any(ExecutionSweepingOutputInstance.class)))
        .thenReturn(buildInstance("pipelineId|stageId|stepId"));
    when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(ExecutionSweepingOutputInstance.class)))
        .thenReturn(buildInstance("pipelineId|stageId|stepId"));
    when(pmsSweepingOutputWriteConverter.convert(any(PmsSweepingOutput.class))).thenReturn(DUMMY_BINARY_VALUE);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testConsumeUpsertWithEmptyGroupName() {
    Ambiance ambiance = buildAmbiance();
    ExecutionSweepingOutput value = DummySweepingOutput.builder().test(KEY).build();

    ArgumentCaptor<ExecutionSweepingOutputInstance> captor =
        ArgumentCaptor.forClass(ExecutionSweepingOutputInstance.class);
    assertThat(pmsSweepingOutputService.consumeUpsert(ambiance, "name", RecastOrchestrationUtils.toJson(value), ""))
        .isEqualTo(RawSweepingOutputConsumeUpsert.builder().id(UUID).isUpsert(false).build());
    verify(mongoTemplate, times(1)).insert(captor.capture());
    validateInstance(captor.getValue(), "stepId", "pipelineId|stageId|stepId", "", "pipelineId.stageId.stepId.name");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testConsumeUpsertWithGlobalGroupName() {
    Ambiance ambiance = buildAmbiance();
    ExecutionSweepingOutput value = DummySweepingOutput.builder().test(KEY).build();

    ArgumentCaptor<ExecutionSweepingOutputInstance> captor =
        ArgumentCaptor.forClass(ExecutionSweepingOutputInstance.class);
    assertThat(pmsSweepingOutputService.consumeUpsert(
                   ambiance, "name", RecastOrchestrationUtils.toJson(value), "__GLOBAL_GROUP_SCOPE__"))
        .isEqualTo(RawSweepingOutputConsumeUpsert.builder().id(UUID).isUpsert(false).build());
    verify(mongoTemplate, times(1)).insert(captor.capture());
    validateInstance(captor.getValue(), "stepId", "", "__GLOBAL_GROUP_SCOPE__", "name");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testConsumeUpsertWithNormalGroupNameWithAmbianceEmpty() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(PLAN_EXE_ID).build();
    ExecutionSweepingOutput value = DummySweepingOutput.builder().test(KEY).build();

    assertThatThrownBy(
        () -> pmsSweepingOutputService.consumeUpsert(ambiance, "name", RecastOrchestrationUtils.toJson(value), "step"))
        .isInstanceOf(GroupNotFoundException.class)
        .hasMessage("step");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testConsumeWithNormalGroupName() {
    Ambiance ambiance = buildAmbiance();
    ExecutionSweepingOutput value = DummySweepingOutput.builder().test(KEY).build();

    ArgumentCaptor<ExecutionSweepingOutputInstance> captor =
        ArgumentCaptor.forClass(ExecutionSweepingOutputInstance.class);

    assertThat(pmsSweepingOutputService.consumeUpsert(
                   ambiance, "name", RecastOrchestrationUtils.toJson(value), StepCategory.PIPELINE.name()))
        .isEqualTo(RawSweepingOutputConsumeUpsert.builder().id(UUID).isUpsert(false).build());
    verify(mongoTemplate, times(1)).insert(captor.capture());
    validateInstance(captor.getValue(), "stepId", "pipelineId", "PIPELINE", "pipelineId.name");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testConsumeUpsertWithNormalGroupName() {
    Ambiance ambiance = buildAmbiance();
    ExecutionSweepingOutput value = DummySweepingOutput.builder().test(KEY).build();
    when(mongoTemplate.insert(any(ExecutionSweepingOutputInstance.class)))
        .thenThrow(new DuplicateKeyException("dummy"));

    ArgumentCaptor<ExecutionSweepingOutputInstance> captor =
        ArgumentCaptor.forClass(ExecutionSweepingOutputInstance.class);
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    assertThat(pmsSweepingOutputService.consumeUpsert(
                   ambiance, "name", RecastOrchestrationUtils.toJson(value), StepCategory.PIPELINE.name()))
        .isEqualTo(RawSweepingOutputConsumeUpsert.builder().id(UUID).isUpsert(true).build());
    verify(mongoTemplate, times(1)).insert(captor.capture());
    validateInstance(captor.getValue(), "stepId", "pipelineId", "PIPELINE", "pipelineId.name");
    verify(mongoTemplate, times(1))
        .findAndModify(queryCaptor.capture(), updateCaptor.capture(), eq(ExecutionSweepingOutputInstance.class));
    assertThat(queryCaptor.getValue().toString())
        .startsWith(
            "Query: { \"planExecutionId\" : \"plan_execution_id\", \"levelRuntimeIdIdx\" : \"pipelineId\", \"name\" : \"name\"}, Fields: { ");
    validateUpdateSet(updateCaptor.getValue(), "PIPELINE", "pipelineId.name", "stepId");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testConsumeUpsertFailWithNormalGroupName() {
    Ambiance ambiance = buildAmbiance();
    ExecutionSweepingOutput value = DummySweepingOutput.builder().test(KEY).build();
    when(mongoTemplate.insert(any(ExecutionSweepingOutputInstance.class)))
        .thenThrow(new DuplicateKeyException("dummy"));
    when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), eq(ExecutionSweepingOutputInstance.class)))
        .thenReturn(null);
    assertThatThrownBy(()
                           -> pmsSweepingOutputService.consumeUpsert(
                               ambiance, "name", RecastOrchestrationUtils.toJson(value), StepCategory.PIPELINE.name()))
        .isInstanceOf(SweepingOutputException.class)
        .hasMessageContaining("Couldn't find sweeping output to update, also insert failed");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testConsumeUpsertWithInvalidGroupNameWithNesting() {
    Ambiance ambiance = buildAmbiance();
    ExecutionSweepingOutput value = DummySweepingOutput.builder().test(KEY).build();
    assertThatThrownBy(()
                           -> pmsSweepingOutputService.consumeUpsert(
                               ambiance, "name", RecastOrchestrationUtils.toJson(value), "random"))
        .isInstanceOf(GroupNotFoundException.class)
        .hasMessage("random");
  }

  private void validateUpdateSet(Update update, String groupName, String fqn, String producedBy) {
    Document document = update.getUpdateObject().get("$set", Document.class);
    assertThat(document.get(ExecutionSweepingOutputKeys.producedBy, Level.class).getRuntimeId()).isEqualTo(producedBy);
    assertThat(document.get(ExecutionSweepingOutputKeys.valueOutput, Binary.class)).isEqualTo(DUMMY_BINARY_VALUE);
    assertThat(document.get(ExecutionSweepingOutputKeys.groupName, String.class)).isEqualTo(groupName);
    assertThat(document.get(ExecutionSweepingOutputKeys.fullyQualifiedName, String.class)).isEqualTo(fqn);
    assertThat(document.get(ExecutionSweepingOutputKeys.validUntil, Date.class))
        .isEqualToIgnoringMinutes(Date.from(OffsetDateTime.now().plus(TTL).toInstant()));
  }

  private void validateInstance(ExecutionSweepingOutputInstance executionSweepingOutputInstance, String producedBy,
      String levelRuntimeIdIdx, String groupName, String fqn) {
    assertThat(executionSweepingOutputInstance.getAccountIdentifier()).isEqualTo(ACC_ID);
    assertThat(executionSweepingOutputInstance.getPlanExecutionId()).isEqualTo(PLAN_EXE_ID);
    assertThat(executionSweepingOutputInstance.getProducedBy().getRuntimeId()).isEqualTo(producedBy);
    assertThat(executionSweepingOutputInstance.getName()).isEqualTo("name");
    assertThat(executionSweepingOutputInstance.getValueOutput())
        .isEqualTo(
            PmsSweepingOutput.parse(RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test(KEY).build())));
    assertThat(executionSweepingOutputInstance.getLevelRuntimeIdIdx()).isEqualTo(levelRuntimeIdIdx);
    assertThat(executionSweepingOutputInstance.getGroupName()).isEqualTo(groupName);
    assertThat(executionSweepingOutputInstance.getFullyQualifiedName()).isEqualTo(fqn);
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .addAllLevels(ImmutableList.of(Level.newBuilder()
                                           .setIdentifier("pipelineId")
                                           .setRuntimeId("pipelineId")
                                           .setGroup(StepCategory.PIPELINE.name())
                                           .setStepType(StepType.newBuilder().build())
                                           .build(),
            Level.newBuilder()
                .setIdentifier("stageId")
                .setRuntimeId("stageId")
                .setGroup(StepCategory.STAGE.name())
                .setStepType(StepType.newBuilder().build())
                .build(),
            Level.newBuilder()
                .setIdentifier("stepId")
                .setRuntimeId("stepId")
                .setGroup(StepCategory.STEP.name())
                .setStepType(StepType.newBuilder().build())
                .build()))
        .setPlanExecutionId(PLAN_EXE_ID)
        .putAllSetupAbstractions(ImmutableMap.<String, String>builder()
                                     .put(SetupAbstractionKeys.accountId, ACC_ID)
                                     .put(SetupAbstractionKeys.orgIdentifier, ORG_ID)
                                     .put(SetupAbstractionKeys.projectIdentifier, PROJ_ID)
                                     .build())
        .setStageExecutionId(STAGE_EXE_ID)
        .build();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void shouldTestConsumeUpsertForNull() {
    Ambiance ambiance = buildAmbiance();
    String outputName = "outcomeName";
    assertThat(pmsSweepingOutputService.consumeUpsert(ambiance, outputName, null, null).getId()).isEqualTo(UUID);
  }

  private ExecutionSweepingOutputInstance buildInstance(String levelRuntimeIdIdx) {
    DummySweepingOutput dummySweepingOutput = DummySweepingOutput.builder().test(KEY).build();
    return ExecutionSweepingOutputInstance.builder()
        .valueOutput(PmsSweepingOutput.parse(RecastOrchestrationUtils.toJson(dummySweepingOutput)))
        .levelRuntimeIdIdx(levelRuntimeIdIdx)
        .planExecutionId(PLAN_EXE_ID)
        .uuid(UUID)
        .name("key")
        .build();
  }
}
