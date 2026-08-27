/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.step;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.step.HarnessApprovalStepExecutionDetails;
import io.harness.execution.step.StepExecutionEntity;
import io.harness.execution.step.StepExecutionEntity.StepExecutionEntityKeys;
import io.harness.execution.step.StepExecutionEntityUpdateDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.repositories.stepexecution.StepExecutionEntityRepository;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.collect.ImmutableMap;
import com.mongodb.client.result.UpdateResult;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class StepExecutionEntityServiceTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String STAGE_EXECUTION_ID = "stageExecutionId";
  private static final String RUNTIME_ID = "runtimeId";
  private static final String SETUP_ID = "setupId";
  private static final String STEP_ID = "stepId";
  private static final String STEP_NAME = "stepName";
  private static final String PARENT_UNIQUE_ID = "parentUniqueId";
  private static final Scope scope = Scope.builder()
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .orgIdentifier(ORG_IDENTIFIER)
                                         .parentUniqueId(PARENT_UNIQUE_ID)
                                         .build();
  private final Ambiance ambiance =
      Ambiance.newBuilder()
          .setPlanExecutionId(PLAN_EXECUTION_ID)
          .putAllSetupAbstractions(ImmutableMap.of("accountId", ACCOUNT_IDENTIFIER, "orgIdentifier", ORG_IDENTIFIER,
              "projectIdentifier", PROJECT_IDENTIFIER, "parentUniqueId", PARENT_UNIQUE_ID))
          .setStageExecutionId(STAGE_EXECUTION_ID)
          .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
          .addLevels(Level.newBuilder()
                         .setIdentifier(STEP_ID)
                         .setStepType(StepType.newBuilder()
                                          .setType(StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE.getType())
                                          .setStepCategory(StepCategory.STEP)
                                          .build())
                         .setRuntimeId(RUNTIME_ID)
                         .setStartTs(2)
                         .setSetupId(SETUP_ID)
                         .build())
          .build();

  StepExecutionEntity stepExecutionEntity = StepExecutionEntity.builder()
                                                .stageExecutionId(STAGE_EXECUTION_ID)
                                                .planExecutionId(PLAN_EXECUTION_ID)
                                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                .orgIdentifier(ORG_IDENTIFIER)
                                                .projectIdentifier(PROJECT_IDENTIFIER)
                                                .pipelineIdentifier(PIPELINE_ID)
                                                .stepIdentifier(STEP_ID)
                                                .stepName(STEP_NAME)
                                                .stepExecutionId(RUNTIME_ID)
                                                .stepType(StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE.getType())
                                                .startts(2L)
                                                .status(Status.RUNNING)
                                                .build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().name(STEP_NAME).identifier(STEP_ID).build();
  @Mock private StepExecutionEntityRepository stepExecutionEntityRepository;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @InjectMocks private StepExecutionEntityServiceImpl stepExecutionEntityService;

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFindStepExecutionEntity() {
    stepExecutionEntityService.findStepExecutionEntity(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, RUNTIME_ID, PARENT_UNIQUE_ID);
    verify(stepExecutionEntityRepository).findByStepExecutionId(eq(RUNTIME_ID), eq(scope), eq(true));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testCreateStepExecutionEntity() {
    when(stepExecutionEntityRepository.save(any())).thenReturn(null);
    when(scopeResolutionHelper.getScopeInfoOptional(any(), any(), any()))
        .thenReturn(Optional.of(ScopeInfo.builder().uniqueId(PARENT_UNIQUE_ID).build()));
    stepExecutionEntityService.createStepExecutionEntity(ambiance, Status.RUNNING);
    ArgumentCaptor<StepExecutionEntity> argumentCaptor = ArgumentCaptor.forClass(StepExecutionEntity.class);
    verify(stepExecutionEntityRepository).save(argumentCaptor.capture());
    StepExecutionEntity stepExecution = argumentCaptor.getValue();
    assertThat(stepExecution.getStageExecutionId()).isEqualTo(STAGE_EXECUTION_ID);
    assertThat(stepExecution.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(stepExecution.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(stepExecution.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(stepExecution.getProjectIdentifier()).isEqualTo(PROJECT_IDENTIFIER);
    assertThat(stepExecution.getPipelineIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(stepExecution.getStepIdentifier()).isEqualTo(STEP_ID);
    assertThat(stepExecution.getStepExecutionId()).isEqualTo(RUNTIME_ID);
    assertThat(stepExecution.getStepType()).isEqualTo(StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE.getType());
    assertThat(stepExecution.getStartts()).isEqualTo(2L);
    assertThat(stepExecution.getStatus()).isEqualTo(Status.RUNNING);
    assertThat(stepExecution.getParentUniqueId()).isEqualTo(PARENT_UNIQUE_ID);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateStepExecutionEntity() {
    when(stepExecutionEntityRepository.findByStepExecutionId(eq(RUNTIME_ID), eq(scope), eq(true))).thenReturn(null);
    StepExecutionEntityUpdateDTO stepExecutionEntityUpdateDTO = StepExecutionEntityUpdateDTO.builder().build();
    when(scopeResolutionHelper.getScopeInfoOptional(any(), any(), any()))
        .thenReturn(Optional.of(ScopeInfo.builder().uniqueId(PARENT_UNIQUE_ID).build()));
    stepExecutionEntityService.updateStepExecutionEntity(
        ambiance, stepExecutionEntityUpdateDTO, Status.APPROVAL_WAITING);
    ArgumentCaptor<StepExecutionEntity> argumentCaptor = ArgumentCaptor.forClass(StepExecutionEntity.class);
    verify(stepExecutionEntityRepository, times(1)).save(argumentCaptor.capture());
    StepExecutionEntity stepExecution = argumentCaptor.getValue();
    assertThat(stepExecution.getStageExecutionId()).isEqualTo(STAGE_EXECUTION_ID);
    assertThat(stepExecution.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(stepExecution.getAccountIdentifier()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(stepExecution.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(stepExecution.getProjectIdentifier()).isEqualTo(PROJECT_IDENTIFIER);
    assertThat(stepExecution.getPipelineIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(stepExecution.getStepIdentifier()).isEqualTo(STEP_ID);
    assertThat(stepExecution.getStepExecutionId()).isEqualTo(RUNTIME_ID);
    assertThat(stepExecution.getStepType()).isEqualTo(StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE.getType());
    assertThat(stepExecution.getStartts()).isEqualTo(2L);
    assertThat(stepExecution.getStatus()).isEqualTo(Status.APPROVAL_WAITING);
    assertThat(stepExecution.getParentUniqueId()).isEqualTo(PARENT_UNIQUE_ID);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateStatusWithUnacknowledged() {
    when(stepExecutionEntityRepository.findByStepExecutionId(eq(RUNTIME_ID), eq(scope), eq(true)))
        .thenReturn(stepExecutionEntity);
    when(stepExecutionEntityRepository.update(scope, RUNTIME_ID, new HashMap<>(), true))
        .thenReturn(UpdateResult.unacknowledged());
    StepExecutionEntityUpdateDTO stepExecutionEntityUpdateDTO = StepExecutionEntityUpdateDTO.builder().build();
    assertThatThrownBy(()
                           -> stepExecutionEntityService.updateStepExecutionEntity(
                               ambiance, stepExecutionEntityUpdateDTO, Status.APPROVAL_WAITING))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unable to update StepExecutionEntity, accountIdentifier: accountIdentifier, orgIdentifier: "
            + "orgIdentifier, projectIdentifier: projectIdentifier, executionId: runtimeId");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpdateStepExecutionEntityFoundRecord() {
    when(stepExecutionEntityRepository.findByStepExecutionId(eq(RUNTIME_ID), eq(scope), eq(true)))
        .thenReturn(stepExecutionEntity);
    StepExecutionEntityUpdateDTO stepExecutionEntityUpdateDTO =
        StepExecutionEntityUpdateDTO.builder()
            .status(Status.FAILED)
            .stepExecutionDetails(HarnessApprovalStepExecutionDetails.builder().build())
            .endTs(3L)
            .failureInfo(FailureInfo.newBuilder().build())
            .build();
    Map<String, Object> updates = new HashMap<>();
    updates.put(StepExecutionEntityKeys.executionDetails, stepExecutionEntityUpdateDTO.getStepExecutionDetails());
    updates.put(StepExecutionEntityKeys.status, stepExecutionEntityUpdateDTO.getStatus());
    updates.put(StepExecutionEntityKeys.endts, stepExecutionEntityUpdateDTO.getEndTs());
    updates.put(StepExecutionEntityKeys.failureInfo, stepExecutionEntityUpdateDTO.getFailureInfo());
    when(stepExecutionEntityRepository.update(scope, RUNTIME_ID, updates, true))
        .thenReturn(UpdateResult.acknowledged(1, null, null));

    stepExecutionEntityService.updateStepExecutionEntity(
        ambiance, stepExecutionEntityUpdateDTO, Status.APPROVAL_WAITING);
    verify(stepExecutionEntityRepository, times(1)).update(scope, RUNTIME_ID, updates, true);
  }
}
