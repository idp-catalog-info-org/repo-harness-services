/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SAKSHI;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.abort.AbortedBy;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.dto.FailureInfoDTO;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.exception.FailureType;
import io.harness.execution.PriorityType;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicyMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.QueuedType;
import io.harness.pms.plan.execution.StagesExecutionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.EdgeLayoutListDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.NodeExecutionOutlineDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionIdentifierSummaryDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineExecutionSummaryDtoMapperTest extends CategoryTest {
  String accountId = "acc";
  String orgId = "org";
  String projId = "proj";
  String pipelineId = "pipelineId";
  String planId = "plan-random";

  String branch = "branch";
  String repo = "repo";
  String objectId = "o";
  String rootFolder = "folder/.harness/";
  String file = "file.yaml";

  String nodeType = "CUSTOM";
  String nodeGroup = "STAGE";

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDtoForGitDetails() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .pipelineVersion("0")
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails()).isNull();

    EntityGitDetails entityGitDetails =
        EntityGitDetails.builder().branch(branch).repoIdentifier(repo).objectId(objectId).build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails, false, false, null, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails()).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails().getBranch()).isEqualTo(branch);
    assertThat(executionSummaryDTO.getGitDetails().getRepoIdentifier()).isEqualTo(repo);
    assertThat(executionSummaryDTO.getGitDetails().getObjectId()).isEqualTo(objectId);
    assertThat(executionSummaryDTO.getGitDetails().getFilePath()).isNull();
    assertThat(executionSummaryDTO.getGitDetails().getRootFolder()).isNull();
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();
    assertThat(executionSummaryDTO.getYamlVersion()).isEqualTo("0");

    entityGitDetails = EntityGitDetails.builder()
                           .branch(branch)
                           .repoIdentifier(repo)
                           .objectId(objectId)
                           .rootFolder("__default__")
                           .filePath("__default__")
                           .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails, false, false, null, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails()).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails().getBranch()).isEqualTo(branch);
    assertThat(executionSummaryDTO.getGitDetails().getRepoIdentifier()).isEqualTo(repo);
    assertThat(executionSummaryDTO.getGitDetails().getObjectId()).isEqualTo(objectId);
    assertThat(executionSummaryDTO.getGitDetails().getFilePath()).isNull();
    assertThat(executionSummaryDTO.getGitDetails().getRootFolder()).isNull();
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();

    entityGitDetails = EntityGitDetails.builder()
                           .branch(branch)
                           .repoIdentifier(repo)
                           .objectId(objectId)
                           .rootFolder(rootFolder)
                           .filePath(file)
                           .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails, false, false, null, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails()).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails().getBranch()).isEqualTo(branch);
    assertThat(executionSummaryDTO.getGitDetails().getRepoIdentifier()).isEqualTo(repo);
    assertThat(executionSummaryDTO.getGitDetails().getObjectId()).isEqualTo(objectId);
    assertThat(executionSummaryDTO.getGitDetails().getFilePath()).isEqualTo(file);
    assertThat(executionSummaryDTO.getGitDetails().getRootFolder()).isEqualTo(rootFolder);
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void toExecutionIdentifierDto() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .runSequence(32)
                                                                        .projectIdentifier(projId)
                                                                        .orgIdentifier(orgId)
                                                                        .pipelineIdentifier(pipelineId)
                                                                        .planExecutionId(planId)
                                                                        .status(ExecutionStatus.ABORTED)
                                                                        .build();
    PipelineExecutionIdentifierSummaryDTO pipelineExecutionIdentifierSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toExecutionIdentifierDto(pipelineExecutionSummaryEntity, null);

    assertThat(pipelineExecutionIdentifierSummaryDTO).isNotNull();
    assertThat(pipelineExecutionIdentifierSummaryDTO.getPipelineIdentifier()).isEqualTo(pipelineId);
    assertThat(pipelineExecutionIdentifierSummaryDTO.getPlanExecutionId()).isEqualTo(planId);
    assertThat(pipelineExecutionIdentifierSummaryDTO.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(pipelineExecutionIdentifierSummaryDTO.getProjectIdentifier()).isEqualTo(projId);
    assertThat(pipelineExecutionIdentifierSummaryDTO.getRunSequence()).isEqualTo(32);
    assertThat(pipelineExecutionIdentifierSummaryDTO.getStatus()).isEqualTo(ExecutionStatus.ABORTED);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDtoForInlinePipeline() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .executionInputConfigured(false)
                                                                .planExecutionId(planId)
                                                                .storeType(StoreType.INLINE)
                                                                .pipelineVersion("1")
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();
    assertThat(executionSummaryDTO.getExecutionInputConfigured()).isEqualTo(false);
    assertThat(executionSummaryDTO.getYamlVersion()).isEqualTo("1");

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .endTs(System.currentTimeMillis())
                                 .runSequence(1)
                                 .planExecutionId(planId)
                                 .storeType(StoreType.INLINE)
                                 .executionInputConfigured(true)
                                 .build();

    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO.getExecutionInputConfigured()).isEqualTo(true);
    assertThat(executionSummaryDTO.getYamlVersion()).isEqualTo("0");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDtoForRemotePipeline() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .storeType(StoreType.REMOTE)
                                                                .connectorRef("conn")
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(executionSummaryDTO.getConnectorRef()).isEqualTo("conn");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDtoForStagesExecutionMetadata() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO.isStagesExecution()).isFalse();
    assertThat(executionSummaryDTO.getStagesExecuted()).isNull();
    assertThat(executionSummaryDTO.isAllowStageExecutions()).isFalse();
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();

    PipelineExecutionSummaryEntity executionSummaryEntityWithStages =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .endTs(System.currentTimeMillis())
            .runSequence(1)
            .planExecutionId(planId)
            .stagesExecutionMetadata(StagesExecutionMetadata.builder()
                                         .isStagesExecution(true)
                                         .stageIdentifiers(Collections.singletonList("s1"))
                                         .fullPipelineYaml(getPipelineYaml())
                                         .stageIdentifierToNameMap(StagesExecutionHelper.getStageIdentifierToNameMap(
                                             getPipelineYaml(), Collections.singletonList("s1"), HarnessYamlVersion.V0))
                                         .build())
            .allowStagesExecution(true)
            .build();
    PipelineExecutionSummaryDTO executionSummaryDTOWithStages =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityWithStages, null, false, false, null, null);
    assertThat(executionSummaryDTOWithStages.isStagesExecution()).isTrue();
    assertThat(executionSummaryDTOWithStages.getStagesExecuted()).hasSize(1);
    assertThat(executionSummaryDTOWithStages.getStagesExecuted().contains("s1")).isTrue();
    assertThat(executionSummaryDTOWithStages.getStagesExecutedNames()).hasSize(1);
    assertThat(executionSummaryDTOWithStages.getStagesExecutedNames().get("s1")).isEqualTo("s one");
    assertThat(executionSummaryDTOWithStages.isAllowStageExecutions()).isTrue();
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();

    executionSummaryEntityWithStages =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .endTs(System.currentTimeMillis())
            .runSequence(1)
            .planExecutionId(planId)
            .stagesExecutionMetadata(StagesExecutionMetadata.builder()
                                         .isStagesExecution(true)
                                         .stageIdentifiers(Collections.singletonList("s1"))
                                         .stageIdentifierToNameMap(StagesExecutionHelper.getStageIdentifierToNameMap(
                                             getPipelineYaml(), Collections.singletonList("s1"), HarnessYamlVersion.V0))
                                         .build())
            .allowStagesExecution(true)
            .build();
    executionSummaryDTOWithStages =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityWithStages, null, false, false, null, null);
    assertThat(executionSummaryDTOWithStages.isStagesExecution()).isTrue();
    assertThat(executionSummaryDTOWithStages.getStagesExecuted()).hasSize(1);
    assertThat(executionSummaryDTOWithStages.getStagesExecuted().contains("s1")).isTrue();
    assertThat(executionSummaryDTOWithStages.getStagesExecutedNames()).hasSize(1);
    assertThat(executionSummaryDTOWithStages.getStagesExecutedNames().get("s1")).isEqualTo("s one");
    assertThat(executionSummaryDTOWithStages.isAllowStageExecutions()).isTrue();
    assertThat(executionSummaryDTO.getStoreType()).isNull();
    assertThat(executionSummaryDTO.getConnectorRef()).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testToDtoForStagesExecutionMetadataForRollbackExecution() {
    PipelineExecutionSummaryEntity executionSummaryEntityWithStages =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .endTs(System.currentTimeMillis())
            .runSequence(1)
            .planExecutionId(planId)
            .stagesExecutionMetadata(StagesExecutionMetadata.builder()
                                         .isStagesExecution(true)
                                         .stageIdentifiers(Collections.singletonList("s1"))
                                         .fullPipelineYaml(getPipelineYaml())
                                         .build())
            .allowStagesExecution(true)
            .executionMode(ExecutionMode.PIPELINE_ROLLBACK)
            .build();
    PipelineExecutionSummaryDTO executionSummaryDTOWithStages =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityWithStages, null, false, false, null, null);
    assertThat(executionSummaryDTOWithStages.isStagesExecution()).isTrue();
    assertThat(executionSummaryDTOWithStages.getStagesExecuted()).isNull();
    assertThat(executionSummaryDTOWithStages.isAllowStageExecutions()).isTrue();
  }

  private String getPipelineYaml() {
    return "pipeline:\n"
        + "  identifier: p1\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      name: s one\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      name: s two";
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testToDtoForRetryHistory() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .endTs(System.currentTimeMillis())
                                                                .pipelineIdentifier(pipelineId)
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .build();

    // when isLatest is notSet (default value is true)
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, true, null, null);
    assertThat(executionSummaryDTO.getCanRetry()).isEqualTo(true);
    assertThat(executionSummaryDTO.isShowRetryHistory()).isEqualTo(false);

    // added rootParentId and setting isLatest false
    PipelineExecutionSummaryEntity executionSummaryEntityWithRootParentId =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .endTs(System.currentTimeMillis())
            .runSequence(1)
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("rootParentId").build())
            .isLatestExecution(false)
            .planExecutionId(planId)
            .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityWithRootParentId, null, true, false, null, null);
    assertThat(executionSummaryDTO.getCanRetry()).isEqualTo(false);
    assertThat(executionSummaryDTO.isShowRetryHistory()).isEqualTo(true);

    // isLatestTrue
    PipelineExecutionSummaryEntity executionSummaryEntityWithIsLatest =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .endTs(System.currentTimeMillis())
            .runSequence(1)
            .isLatestExecution(true)
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("rootParentId").build())
            .planExecutionId(planId)
            .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityWithIsLatest, null, true, true, null, null);
    assertThat(executionSummaryDTO.getCanRetry()).isEqualTo(true);
    assertThat(executionSummaryDTO.isShowRetryHistory()).isEqualTo(true);

    // isLatestNull
    PipelineExecutionSummaryEntity executionSummaryEntityWithIsLatestNull =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .endTs(System.currentTimeMillis())
            .runSequence(1)
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("rootParentId").build())
            .planExecutionId(planId)
            .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityWithIsLatestNull, null, true, null, null, null);
    assertThat(executionSummaryDTO.getCanRetry()).isNull();
    assertThat(executionSummaryDTO.isShowRetryHistory()).isEqualTo(true);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testToDtoForIsRetriedExecution() {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().planExecutionId(planId).build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO.isRetriedExecution()).isFalse();

    // added rootParentId same as current plan id
    executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId(planId).build())
            .planExecutionId(planId)
            .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO.isRetriedExecution()).isFalse();

    // added rootParentId
    PipelineExecutionSummaryEntity executionSummaryEntityWithRootParentId =
        PipelineExecutionSummaryEntity.builder()
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("rootParentId").build())
            .planExecutionId(planId)
            .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityWithRootParentId, null, false, false, null, null);
    assertThat(executionSummaryDTO.isRetriedExecution()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStagesCount() {
    String startingNodeId = "SQBPoxJCTi6k_gxILqb7SA";
    String otherNodeId = "MQ5AFrizSeesedKbw-lZeQ";

    GraphLayoutNodeDTO startingNode =
        GraphLayoutNodeDTO.builder()
            .nodeType("Approval")
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(Collections.singletonList(otherNodeId)).build())
            .build();
    GraphLayoutNodeDTO otherNode =
        GraphLayoutNodeDTO.builder()
            .nodeType("Approval")
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(Collections.emptyList()).build())
            .build();
    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, startingNode);
    layoutNodeDTOMap.put(otherNodeId, otherNode);
    int stagesCount = PipelineExecutionSummaryDtoMapper.getStagesCount(layoutNodeDTOMap, startingNodeId);
    assertThat(stagesCount).isEqualTo(2);
    assertEquals(0, PipelineExecutionSummaryDtoMapper.getStagesCount(layoutNodeDTOMap, "startingNodeId"));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStagesCountWithExecutionStatus() {
    String startingNodeId = "SQBPoxJCTi6k_gxILqb7SA";
    String otherNodeId = "MQ5AFrizSeesedKbw-lZeQ";

    GraphLayoutNodeDTO startingNode =
        GraphLayoutNodeDTO.builder()
            .nodeType("Approval")
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(List.of(otherNodeId)).build())
            .status(ExecutionStatus.SUCCESS)
            .build();
    GraphLayoutNodeDTO otherNode =
        GraphLayoutNodeDTO.builder()
            .nodeType("Approval")
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(Collections.emptyList()).build())
            .status(ExecutionStatus.ABORTED)
            .build();
    GraphLayoutNodeDTO nullNodeStatusForSomeWeirdReason =
        GraphLayoutNodeDTO.builder()
            .nodeType("Approval")
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(Collections.emptyList()).build())
            .build();
    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, startingNode);
    layoutNodeDTOMap.put(otherNodeId, otherNode);
    layoutNodeDTOMap.put("nullNodeStatus", nullNodeStatusForSomeWeirdReason);
    // TODO:  Change this test to test the toDto.  These aren't great tests.  You're testing the internal logic of tree
    // recursion vs. how the dto mapper operates.  IDEALLY should test that vs. "getStagesCountByStatus" method
    // directly.
    assertThat((int) PipelineExecutionSummaryDtoMapper
                   .getStagesCountByStatus(layoutNodeDTOMap, startingNodeId, new HashMap<>())
                   .get(ExecutionStatus.ABORTED))
        .isEqualTo(1);
    assertEquals(PipelineExecutionSummaryDtoMapper
                     .getStagesCountByStatus(layoutNodeDTOMap, "invalidStartNodeIdWithNoNodeData", new HashMap<>())
                     .getOrDefault(ExecutionStatus.ABORTED, 0),
        Integer.valueOf(0));
    // Hashmaps handle null as a special entry so not really a risk - but good to test
    assertEquals(
        PipelineExecutionSummaryDtoMapper.getStagesCountByStatus(layoutNodeDTOMap, "nullNodeStatus", new HashMap<>())
            .getOrDefault(ExecutionStatus.ABORTED, 0),
        Integer.valueOf(0));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSuccessfulStagesCountIncludesPassedWithWarning() {
    String startingNodeId = "stage-with-warning";
    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId,
        GraphLayoutNodeDTO.builder()
            .nodeType("Custom")
            .status(ExecutionStatus.PASSED_WITH_WARNING)
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(Collections.emptyList()).build())
            .build());

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .planExecutionId(planId)
                                                                .startingNodeId(startingNodeId)
                                                                .layoutNodeMap(layoutNodeDTOMap)
                                                                .status(ExecutionStatus.PASSED_WITH_WARNING)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null);

    assertThat(executionSummaryDTO.getSuccessfulStagesCount()).isEqualTo(1);
    assertThat(executionSummaryDTO.getFailedStagesCount()).isEqualTo(0);
    assertThat(executionSummaryDTO.getTotalStagesCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToDtoForParentStageInfo() {
    AbortedBy abortedBy =
        AbortedBy.builder().userName("user1").email("email").createdAt(System.currentTimeMillis()).build();
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .abortedBy(abortedBy)
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getParentStageInfo()).isNull();
    assertEquals(executionSummaryDTO.getAbortedBy(), abortedBy);
    PipelineStageInfo pipelineStageInfo = PipelineStageInfo.newBuilder()
                                              .setHasParentPipeline(true)
                                              .setExecutionId("executionId")
                                              .setIdentifier("id")
                                              .setStageNodeId("stageNodeId")
                                              .setOrgId("orgId")
                                              .setProjectId("projectId")
                                              .setRunSequence(4556)
                                              .build();
    PipelineExecutionSummaryEntity executionSummaryWithParentStage = PipelineExecutionSummaryEntity.builder()
                                                                         .accountId(accountId)
                                                                         .orgIdentifier(orgId)
                                                                         .projectIdentifier(projId)
                                                                         .pipelineIdentifier(pipelineId)
                                                                         .endTs(System.currentTimeMillis())
                                                                         .runSequence(1)
                                                                         .planExecutionId(planId)
                                                                         .parentStageInfo(pipelineStageInfo)
                                                                         .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryWithParentStage, null, false, false, null, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getParentStageInfo()).isNotNull();
    assertEquals(executionSummaryDTO.getParentStageInfo(), pipelineStageInfo);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testToDtoForNotesExist() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notesExistForPlanExecutionId(true)
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails()).isNull();
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isTrue();
    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .endTs(System.currentTimeMillis())
                                 .runSequence(1)
                                 .planExecutionId(planId)
                                 .notesExistForPlanExecutionId(false)
                                 .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isFalse();
    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .endTs(System.currentTimeMillis())
                                 .pipelineIdentifier(pipelineId)
                                 .runSequence(1)
                                 .planExecutionId(planId)
                                 .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isFalse();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testToDtoForFailureInfo() {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .endTs(System.currentTimeMillis())
            .runSequence(1)
            .planExecutionId(planId)
            .failureInfo(FailureInfoDTO.builder().message("Test Failed").build())
            .notesExistForPlanExecutionId(true)
            .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO.getFailureInfo()).isNotNull();
    assertThat(executionSummaryDTO.getFailureInfo().getMessage()).isEqualTo("Test Failed");
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testToOutlineDto() {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .name(pipelineId)
            .startTs(System.currentTimeMillis())
            .endTs(System.currentTimeMillis())
            .planExecutionId(planId)
            .failureInfo(FailureInfoDTO.builder().message("Test Failed").build())
            .resolvedUserInputSetYaml("key: value1")
            .status(ExecutionStatus.FAILED)
            .modules(Arrays.asList("cd", "pms"))
            .createdAt(System.currentTimeMillis())
            .lastUpdatedAt(System.currentTimeMillis())
            .layoutNodeMap(getLayoutNodeMap())
            .build();
    PipelineExecutionOutlineDTO outlineDto =
        PipelineExecutionSummaryDtoMapper.toOutlineDto(executionSummaryEntity, null);
    assertThat(outlineDto.getFailureInfo()).isNotNull();
    assertThat(outlineDto.getFailureInfo()).isEqualTo("Test Failed");
    assertEquals(ExecutionStatus.FAILED, outlineDto.getStatus());
    assertEquals(accountId, outlineDto.getAccountIdentifier());
    assertEquals(planId, outlineDto.getPlanExecutionId());
    assertEquals(orgId, outlineDto.getOrgIdentifier());
    assertEquals(projId, outlineDto.getProjectIdentifier());
    assertEquals(pipelineId, outlineDto.getPipelineIdentifier());
    assertEquals("key: value1", outlineDto.getRuntimeInputYaml());
    assertEquals(pipelineId, outlineDto.getName());
    assertThat(outlineDto.getStartTs()).isNotNull();
    assertThat(outlineDto.getEndTs()).isNotNull();
    assertThat(outlineDto.getCreatedAt()).isNotNull();
    assertThat(outlineDto.getLastUpdatedAt()).isNotNull();
    assertThat(outlineDto.getStagesMap()).isNotNull();
    assertThat(outlineDto.getStagesMap().get("nodeId1")).isNotNull();
    assertThat(outlineDto.getStagesMap().get("nodeId2")).isNotNull();
    assertThat(outlineDto.getStagesMap().get("nodeId3")).isNull();

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .name(pipelineId)
                                 .startTs(System.currentTimeMillis())
                                 .endTs(System.currentTimeMillis())
                                 .planExecutionId(planId)
                                 .resolvedUserInputSetYaml("key: value1")
                                 .status(ExecutionStatus.SUCCESS)
                                 .modules(Arrays.asList("cd", "pms"))
                                 .createdAt(System.currentTimeMillis())
                                 .lastUpdatedAt(System.currentTimeMillis())
                                 .build();
    outlineDto = PipelineExecutionSummaryDtoMapper.toOutlineDto(executionSummaryEntity, null);
    assertThat(outlineDto.getFailureInfo()).isNull();
  }

  private Map<String, GraphLayoutNodeDTO> getLayoutNodeMap() {
    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put("nodeId1",
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId("nodeExecutionId1")
            .nodeGroup(nodeGroup)
            .nodeIdentifier("nodeIdentifier1")
            .name("nodeName1")
            .startTs(System.currentTimeMillis())
            .endTs(System.currentTimeMillis())
            .status(ExecutionStatus.SUCCESS)
            .nodeUuid("nodeUuid1")
            .strategyMetadata(StrategyMetadata.newBuilder().build())
            .nodeType(nodeType)
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(List.of("nodeId2")).build())
            .build());
    layoutNodeDTOMap.put("nodeId2",
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId("nodeExecutionId2")
            .nodeGroup(nodeGroup)
            .nodeIdentifier("nodeIdentifier2")
            .name("nodeName2")
            .startTs(System.currentTimeMillis())
            .endTs(System.currentTimeMillis())
            .status(ExecutionStatus.FAILED)
            .nodeUuid("nodeUuid2")
            .failureInfoDTO(
                FailureInfoDTO.builder().failureTypeList(FailureType.TIMEOUT).message("Time out failure").build())
            .nodeType(nodeType)
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(List.of("nodeId3")).build())
            .build());
    layoutNodeDTOMap.put("nodeId3",
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId("nodeExecutionId3")
            .nodeGroup(nodeGroup)
            .nodeIdentifier("nodeIdentifier3")
            .name("nodeName3")
            .startTs(System.currentTimeMillis())
            .endTs(System.currentTimeMillis())
            .status(ExecutionStatus.NOT_STARTED)
            .hidden(true)
            .nodeUuid("nodeUuid3")
            .nodeType(nodeType)
            .build());
    return layoutNodeDTOMap;
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testToNodeExecutionOutlineDto() {
    String nodeExecutionId = "nodeExecutionId";
    String nodeIdentifier = "customerDefinedStageId";
    String nodeName = "customerDefinedStageName";
    String nodeUuid = "nodeUuid";
    GraphLayoutNodeDTO graphLayoutNodeDTO =
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId(nodeExecutionId)
            .nodeGroup(nodeGroup)
            .nodeIdentifier(nodeIdentifier)
            .name(nodeName)
            .startTs(System.currentTimeMillis())
            .endTs(System.currentTimeMillis())
            .status(ExecutionStatus.SUCCESS)
            .nodeUuid(nodeUuid)
            .strategyMetadata(StrategyMetadata.newBuilder().build())
            .nodeType(nodeType)
            .edgeLayoutList(EdgeLayoutListDTO.builder().nextIds(Arrays.asList("next1", "next2")).build())
            .build();
    NodeExecutionOutlineDTO outlineDto =
        PipelineExecutionSummaryDtoMapper.toNodeExecutionOutlineDto(graphLayoutNodeDTO);
    assertThat(outlineDto.getFailureInfo()).isNull();
    assertEquals(ExecutionStatus.SUCCESS, outlineDto.getStatus());
    assertEquals(nodeExecutionId, outlineDto.getNodeExecutionId());
    assertEquals(nodeGroup, outlineDto.getNodeGroup());
    assertEquals(nodeIdentifier, outlineDto.getNodeIdentifier());
    assertEquals(nodeName, outlineDto.getName());
    assertEquals(ExecutionStatus.SUCCESS, outlineDto.getStatus());
    assertEquals(nodeUuid, outlineDto.getNodeUuid());
    assertEquals(nodeType, outlineDto.getNodeType());
    assertThat(outlineDto.getStartTs()).isNotNull();
    assertThat(outlineDto.getEndTs()).isNotNull();
    assertThat(outlineDto.getEdgeLayoutList()).isNotNull();
    assertThat(outlineDto.getEdgeLayoutList().getNextIds()).isNotNull();

    graphLayoutNodeDTO =
        GraphLayoutNodeDTO.builder()
            .nodeExecutionId(nodeExecutionId)
            .nodeGroup(nodeGroup)
            .nodeIdentifier(nodeIdentifier)
            .name(nodeName)
            .status(ExecutionStatus.FAILED)
            .failureInfoDTO(
                FailureInfoDTO.builder().failureTypeList(FailureType.TIMEOUT).message("Time out failure").build())
            .build();
    outlineDto = PipelineExecutionSummaryDtoMapper.toNodeExecutionOutlineDto(graphLayoutNodeDTO);
    assertThat(outlineDto.getFailureInfo()).isNotNull();
    assertEquals(ExecutionStatus.FAILED, outlineDto.getStatus());
    assertEquals(outlineDto.getFailureInfo(), "Time out failure");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToDto_WithGovernanceMetadata() {
    GovernanceMetadata metadata = GovernanceMetadata.newBuilder()
                                      .setId("governanceUuid")
                                      .setDeny(false)
                                      .setTimestamp(System.currentTimeMillis())
                                      .addAllDetails(new ArrayList<>())
                                      .setStatus("success")
                                      .setAccountId(accountId)
                                      .setOrgId(orgId)
                                      .setProjectId(projId)
                                      .setEntity("pipeline")
                                      .setType("testTyoe")
                                      .setAction("on_run")
                                      .setCreated(System.currentTimeMillis())
                                      .build();

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .pipelineVersion("0")
                                                                .governanceMetadata(metadata)
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGitDetails()).isNull();
    assertThat(executionSummaryDTO.getGovernanceMetadata()).isNotNull();
    assertThat(executionSummaryDTO.getGovernanceMetadata().getAccountId()).isEqualTo(metadata.getAccountId());
    assertThat(executionSummaryDTO.getGovernanceMetadata().getOrgId()).isEqualTo(metadata.getOrgId());
    assertThat(executionSummaryDTO.getGovernanceMetadata().getProjectId()).isEqualTo(metadata.getProjectId());
    assertThat(executionSummaryDTO.getGovernanceMetadata().getDeny()).isEqualTo(metadata.getDeny());
    assertThat(executionSummaryDTO.getGovernanceMetadata().getStatus()).isEqualTo(metadata.getStatus());
    assertThat(executionSummaryDTO.getGovernanceMetadata().getEntity()).isEqualTo(metadata.getEntity());
    assertThat(executionSummaryDTO.getGovernanceMetadata().getType()).isEqualTo(metadata.getType());
    assertThat(executionSummaryDTO.getGovernanceMetadata().getAction()).isEqualTo(metadata.getAction());
    assertThat(executionSummaryDTO.getClass().getDeclaredFields().length).isEqualTo(55);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testDynamicExecutionData() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .pipelineVersion("0")
                                                                .isDynamicExecution(false)
                                                                .build();
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, true, null, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.isDynamicExecution()).isFalse();
    assertThat(executionSummaryDTO.getCanRetry()).isTrue();
    assertThat(executionSummaryDTO.isCanReExecute()).isTrue();

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .endTs(System.currentTimeMillis())
                                 .runSequence(1)
                                 .planExecutionId(planId)
                                 .pipelineVersion("0")
                                 .isDynamicExecution(true)
                                 .build();
    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, true, null, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.isDynamicExecution()).isTrue();
    assertThat(executionSummaryDTO.getCanRetry()).isFalse();
    assertThat(executionSummaryDTO.isCanReExecute()).isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testQueuedReason() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .pipelineVersion("0")
                                                                .isDynamicExecution(false)
                                                                .priorityType(PriorityType.LOW)
                                                                .build();

    // Test MAX_CONCURRENCY_REACHED queue type
    PipelineExecutionSummaryDTO executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(
        executionSummaryEntity, null, false, true, QueuedType.MAX_CONCURRENCY_REACHED, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.isDynamicExecution()).isFalse();
    assertThat(executionSummaryDTO.getCanRetry()).isTrue();
    assertThat(executionSummaryDTO.isCanReExecute()).isTrue();
    assertThat(executionSummaryDTO.getQueuedReason())
        .isEqualTo("Max number of concurrent executions reached for the account");

    // Test PRIORITY_CONCURRENCY_REACHED queue type
    executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(
        executionSummaryEntity, null, false, true, QueuedType.PRIORITY_CONCURRENCY_REACHED, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getQueuedReason())
        .isEqualTo("Max number of concurrent executions reached for LOW priority type");

    // Test MAX_CONCURRENCY_NOT_REACHED
    executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(
        executionSummaryEntity, null, false, true, QueuedType.MAX_CONCURRENCY_NOT_REACHED, null);
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getQueuedReason()).isEqualTo("Execution Loading ...");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoForOriginalYamlUsedOnRerun() {
    // Create entity with isOriginalYamlUsedOnRerun set to true
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .planExecutionId(planId)
                                                                .isOriginalYamlUsedOnRerun(true)
                                                                .build();

    // Map to DTO
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    // Verify the flag is properly mapped
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.isOriginalYamlUsedOnRerun()).isTrue();

    // Test with flag set to false
    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .planExecutionId(planId)
                                 .isOriginalYamlUsedOnRerun(false)
                                 .build();

    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.isOriginalYamlUsedOnRerun()).isFalse();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoForInputSetIdentifiers() {
    List<String> inputSetIds = Arrays.asList("input1", "input2", "input3");

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .planExecutionId(planId)
                                                                .inputSetIdentifiers(inputSetIds)
                                                                .build();

    // Map to DTO
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).hasSize(3);
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).containsExactlyElementsOf(inputSetIds);

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .planExecutionId(planId)
                                 .inputSetIdentifiers(Collections.emptyList())
                                 .build();

    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isEmpty();

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .planExecutionId(planId)
                                 .build();

    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isEmpty();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoForInputSetReferencesInTriggerExecution() {
    List<String> inputSetIds = Arrays.asList("input1", "input2", "input3");
    ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).build();

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .planExecutionId(planId)
                                                                .inputSetIdentifiers(inputSetIds)
                                                                .executionTriggerInfo(triggerInfo)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).hasSize(3);
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).containsExactlyElementsOf(inputSetIds);

    Map<String, String> extraInfo = new HashMap<>();
    extraInfo.put("inputSetReferences", String.join(",", inputSetIds));

    TriggeredBy triggeredBy = TriggeredBy.newBuilder().putAllExtraInfo(extraInfo).build();

    triggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.WEBHOOK).setTriggeredBy(triggeredBy).build();

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .planExecutionId(planId)
                                 .executionTriggerInfo(triggerInfo)
                                 .build();

    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isEmpty();

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .planExecutionId(planId)
                                 .inputSetIdentifiers(inputSetIds)
                                 .executionTriggerInfo(triggerInfo)
                                 .build();

    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).hasSize(3);
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).containsExactlyElementsOf(inputSetIds);

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .orgIdentifier(orgId)
                                 .projectIdentifier(projId)
                                 .pipelineIdentifier(pipelineId)
                                 .planExecutionId(planId)
                                 .build();

    executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isNotNull();
    assertThat(executionSummaryDTO.getInputSetIdentifiers()).isEmpty();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoWithNotes() {
    String testNotes = "This is a test note for the execution";

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notes(testNotes)
                                                                .notesExistForPlanExecutionId(true)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isEqualTo(testNotes);
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoWithNullNotes() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notes(null)
                                                                .notesExistForPlanExecutionId(false)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isNull();
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isFalse();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoWithEmptyNotes() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notes("")
                                                                .notesExistForPlanExecutionId(false)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isEqualTo("");
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isFalse();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoWithMultilineNotes() {
    String multilineNotes = "This is line 1\nThis is line 2\nThis is line 3";
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notes(multilineNotes)
                                                                .notesExistForPlanExecutionId(true)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isEqualTo(multilineNotes);
    assertThat(executionSummaryDTO.getNotes()).contains("\n");
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoWithNotesAndOtherFields() {
    String testNotes = "Execution notes with context";
    AbortedBy abortedBy =
        AbortedBy.builder().userName("user1").email("email@test.com").createdAt(System.currentTimeMillis()).build();

    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .endTs(System.currentTimeMillis())
            .runSequence(1)
            .planExecutionId(planId)
            .notes(testNotes)
            .notesExistForPlanExecutionId(true)
            .abortedBy(abortedBy)
            .failureInfo(FailureInfoDTO.builder().message("Test Failed").build())
            .storeType(StoreType.INLINE)
            .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isEqualTo(testNotes);
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isTrue();
    assertThat(executionSummaryDTO.getAbortedBy()).isEqualTo(abortedBy);
    assertThat(executionSummaryDTO.getFailureInfo()).isNotNull();
    assertThat(executionSummaryDTO.getStoreType()).isEqualTo(StoreType.INLINE);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoWithLongNotesTruncation() {
    StringBuilder longNotesBuilder = new StringBuilder();
    for (int i = 0; i < 600; i++) {
      longNotesBuilder.append("a");
    }
    String longNotes = longNotesBuilder.toString();

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notes(longNotes)
                                                                .notesExistForPlanExecutionId(true)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null, true);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isNotNull();
    assertThat(executionSummaryDTO.getNotes().length()).isEqualTo(500);
    assertThat(executionSummaryDTO.getNotes()).isEqualTo(longNotes.substring(0, 500));
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoWithNotesExactly500Chars() {
    StringBuilder notesBuilder = new StringBuilder();
    for (int i = 0; i < 500; i++) {
      notesBuilder.append("b");
    }
    String notes500 = notesBuilder.toString();

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notes(notes500)
                                                                .notesExistForPlanExecutionId(true)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null, true);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isNotNull();
    assertThat(executionSummaryDTO.getNotes().length()).isEqualTo(500);
    assertThat(executionSummaryDTO.getNotes()).isEqualTo(notes500);
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoWithShortNotes() {
    String shortNotes = "This is a short note with less than 500 characters";

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notes(shortNotes)
                                                                .notesExistForPlanExecutionId(true)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null, true);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isEqualTo(shortNotes);
    assertThat(executionSummaryDTO.getNotes().length()).isLessThan(500);
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoWithNotesDisabledByFeatureFlag() {
    String testNotes = "These notes should not be included when feature flag is enabled";

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notes(testNotes)
                                                                .notesExistForPlanExecutionId(true)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null, false);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isNull();
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoWithNotesEnabledByDefault() {
    String testNotes = "These notes should be included by default";

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notes(testNotes)
                                                                .notesExistForPlanExecutionId(true)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null, true);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isEqualTo(testNotes);
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToDtoLegacyMethodsStillPopulateNotes() {
    String testNotes = "Testing backward compatibility";

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .notes(testNotes)
                                                                .notesExistForPlanExecutionId(true)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isEqualTo(testNotes);
    assertThat(executionSummaryDTO.isNotesExistForPlanExecutionId()).isTrue();

    executionSummaryDTO = PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isNotNull();
    assertThat(executionSummaryDTO.getNotes()).isEqualTo(testNotes);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testToDto_UpdatesOrgIdForProjectLevelPoliciesAfterProjectMovement() {
    String oldOrgId = "oldOrg";
    String newOrgId = "newOrg";

    // Create project-level policy metadata with OLD org
    PolicyMetadata projectPolicy = PolicyMetadata.newBuilder()
                                       .setIdentifier("policy1")
                                       .setPolicyName("Test Policy")
                                       .setAccountId(accountId)
                                       .setOrgId(oldOrgId)
                                       .setProjectId(projId)
                                       .setStatus("pass")
                                       .build();

    PolicySetMetadata projectPolicySet = PolicySetMetadata.newBuilder()
                                             .setIdentifier("policyset1")
                                             .setPolicySetName("Test PolicySet")
                                             .setAccountId(accountId)
                                             .setOrgId(oldOrgId)
                                             .setProjectId(projId)
                                             .setStatus("pass")
                                             .addPolicyMetadata(projectPolicy)
                                             .build();

    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder()
                                                .setId("eval1")
                                                .setAccountId(accountId)
                                                .setOrgId(oldOrgId)
                                                .setProjectId(projId)
                                                .setStatus("pass")
                                                .addDetails(projectPolicySet)
                                                .build();

    // Create execution summary with governance metadata
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(oldOrgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .planExecutionId(planId)
                                                                .pipelineVersion("0")
                                                                .governanceMetadata(governanceMetadata)
                                                                .build();

    // Create ScopeInfo with NEW org (after project movement)
    ScopeInfo newScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(accountId)
                                 .orgIdentifier(newOrgId)
                                 .projectIdentifier(projId)
                                 .uniqueId("uniqueId123")
                                 .scopeType(ScopeLevel.PROJECT)
                                 .build();

    // Act: Convert to DTO with new ScopeInfo
    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, newScopeInfo);

    // Assert: Governance metadata should have UPDATED orgId
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGovernanceMetadata()).isNotNull();
    assertThat(executionSummaryDTO.getGovernanceMetadata().getDetailsList()).hasSize(1);

    PolicySetMetadata updatedPolicySet = executionSummaryDTO.getGovernanceMetadata().getDetails(0);
    assertThat(updatedPolicySet.getOrgId()).isEqualTo(newOrgId); // Updated!
    assertThat(updatedPolicySet.getProjectId()).isEqualTo(projId);

    PolicyMetadata updatedPolicy = updatedPolicySet.getPolicyMetadata(0);
    assertThat(updatedPolicy.getOrgId()).isEqualTo(newOrgId); // Updated!
    assertThat(updatedPolicy.getProjectId()).isEqualTo(projId);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testToDto_OrgLevelPoliciesNotUpdatedAfterProjectMovement() {
    String oldOrgId = "oldOrg";
    String newOrgId = "newOrg";

    // Create org-level policy metadata (no projectId)
    PolicyMetadata orgPolicy = PolicyMetadata.newBuilder()
                                   .setIdentifier("policy1")
                                   .setPolicyName("Org Policy")
                                   .setAccountId(accountId)
                                   .setOrgId(oldOrgId)
                                   .setStatus("pass")
                                   .build();

    PolicySetMetadata orgPolicySet = PolicySetMetadata.newBuilder()
                                         .setIdentifier("policyset1")
                                         .setPolicySetName("Org PolicySet")
                                         .setAccountId(accountId)
                                         .setOrgId(oldOrgId)
                                         .setStatus("pass")
                                         .addPolicyMetadata(orgPolicy)
                                         .build();

    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder()
                                                .setId("eval1")
                                                .setAccountId(accountId)
                                                .setOrgId(oldOrgId)
                                                .setStatus("pass")
                                                .addDetails(orgPolicySet)
                                                .build();

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(oldOrgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .planExecutionId(planId)
                                                                .pipelineVersion("0")
                                                                .governanceMetadata(governanceMetadata)
                                                                .build();

    ScopeInfo newScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(accountId)
                                 .orgIdentifier(newOrgId)
                                 .projectIdentifier(projId)
                                 .uniqueId("uniqueId123")
                                 .scopeType(ScopeLevel.PROJECT)
                                 .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, newScopeInfo);

    // Assert: Org-level policy should NOT be updated
    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getGovernanceMetadata()).isNotNull();
    assertThat(executionSummaryDTO.getGovernanceMetadata().getDetailsList()).hasSize(1);

    PolicySetMetadata updatedPolicySet = executionSummaryDTO.getGovernanceMetadata().getDetails(0);
    assertThat(updatedPolicySet.getOrgId()).isEqualTo(oldOrgId); // NOT updated!
    assertThat(updatedPolicySet.getProjectId()).isEmpty();

    PolicyMetadata updatedPolicy = updatedPolicySet.getPolicyMetadata(0);
    assertThat(updatedPolicy.getOrgId()).isEqualTo(oldOrgId); // NOT updated!
    assertThat(updatedPolicy.getProjectId()).isEmpty();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testToDto_mapsDagFields() {
    List<String> startingNodeIds = Arrays.asList("stage1-uuid", "stage2-uuid");
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("stage1-uuid", Collections.emptyList());
    dependencyGraph.put("stage2-uuid", Collections.emptyList());
    dependencyGraph.put("stage3-uuid", Arrays.asList("stage1-uuid", "stage2-uuid"));

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .endTs(System.currentTimeMillis())
                                                                .runSequence(1)
                                                                .planExecutionId(planId)
                                                                .startingNodeId("stage1-uuid")
                                                                .startingNodeIds(startingNodeIds)
                                                                .isDagEnabled(true)
                                                                .dependencyGraph(dependencyGraph)
                                                                .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getStartingNodeId()).isEqualTo("stage1-uuid");
    assertThat(executionSummaryDTO.getStartingNodeIds()).containsExactlyInAnyOrder("stage1-uuid", "stage2-uuid");
    assertThat(executionSummaryDTO.getIsDagEnabled()).isTrue();
    assertThat(executionSummaryDTO.getDependencyGraph()).isNotNull();
    assertThat(executionSummaryDTO.getDependencyGraph()).hasSize(3);
    assertThat(executionSummaryDTO.getDependencyGraph().get("stage3-uuid"))
        .containsExactlyInAnyOrder("stage1-uuid", "stage2-uuid");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testToDto_handlesNullDagFields_forBackwardCompatibility() {
    // Test backward compatibility - entity without DAG fields should map to null/default values
    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .endTs(System.currentTimeMillis())
            .runSequence(1)
            .planExecutionId(planId)
            .startingNodeId("stage1-uuid")
            // DAG fields intentionally not set (simulating old data)
            .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getStartingNodeId()).isEqualTo("stage1-uuid");
    // DAG fields should be null for old data
    assertThat(executionSummaryDTO.getStartingNodeIds()).isNull();
    assertThat(executionSummaryDTO.getIsDagEnabled()).isNull();
    assertThat(executionSummaryDTO.getDependencyGraph()).isNull();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testToOutlineDto_mapsDagFields() {
    List<String> startingNodeIds = Arrays.asList("stage1-uuid", "stage2-uuid");
    Map<String, List<String>> dependencyGraph = new HashMap<>();
    dependencyGraph.put("stage1-uuid", Collections.emptyList());
    dependencyGraph.put("stage2-uuid", Collections.emptyList());

    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .orgIdentifier(orgId)
                                                                .projectIdentifier(projId)
                                                                .pipelineIdentifier(pipelineId)
                                                                .name(pipelineId)
                                                                .startTs(System.currentTimeMillis())
                                                                .endTs(System.currentTimeMillis())
                                                                .planExecutionId(planId)
                                                                .status(ExecutionStatus.SUCCESS)
                                                                .startingNodeId("stage1-uuid")
                                                                .startingNodeIds(startingNodeIds)
                                                                .isDagEnabled(true)
                                                                .dependencyGraph(dependencyGraph)
                                                                .build();

    PipelineExecutionOutlineDTO outlineDto =
        PipelineExecutionSummaryDtoMapper.toOutlineDto(executionSummaryEntity, null);

    assertThat(outlineDto).isNotNull();
    assertThat(outlineDto.getStartingNodeId()).isEqualTo("stage1-uuid");
    assertThat(outlineDto.getStartingNodeIds()).containsExactlyInAnyOrder("stage1-uuid", "stage2-uuid");
    assertThat(outlineDto.getIsDagEnabled()).isTrue();
    assertThat(outlineDto.getDependencyGraph()).isNotNull();
    assertThat(outlineDto.getDependencyGraph()).hasSize(2);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testToDto_sequentialPipeline_dagFieldsCorrect() {
    // Test sequential pipeline where isDagEnabled is false
    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projId)
            .pipelineIdentifier(pipelineId)
            .endTs(System.currentTimeMillis())
            .runSequence(1)
            .planExecutionId(planId)
            .startingNodeId("stage1-uuid")
            .startingNodeIds(Collections.singletonList("stage1-uuid"))
            .isDagEnabled(false)
            .dependencyGraph(null)
            .build();

    PipelineExecutionSummaryDTO executionSummaryDTO =
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, null, false, false, null, null);

    assertThat(executionSummaryDTO).isNotNull();
    assertThat(executionSummaryDTO.getStartingNodeId()).isEqualTo("stage1-uuid");
    assertThat(executionSummaryDTO.getStartingNodeIds()).containsExactly("stage1-uuid");
    assertThat(executionSummaryDTO.getIsDagEnabled()).isFalse();
    assertThat(executionSummaryDTO.getDependencyGraph()).isNull();
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testToDtoPopulatesPipelineTimeoutTs() {
    long timeoutTs = 1_700_000_000_000L;
    PipelineExecutionSummaryEntity entityWithTimeout = PipelineExecutionSummaryEntity.builder()
                                                           .accountId(accountId)
                                                           .orgIdentifier(orgId)
                                                           .projectIdentifier(projId)
                                                           .pipelineIdentifier(pipelineId)
                                                           .planExecutionId(planId)
                                                           .runSequence(1)
                                                           .pipelineVersion("0")
                                                           .endTs(System.currentTimeMillis())
                                                           .pipelineTimeoutTs(timeoutTs)
                                                           .build();

    PipelineExecutionSummaryDTO dto =
        PipelineExecutionSummaryDtoMapper.toDto(entityWithTimeout, null, false, false, null, null);
    assertThat(dto.getPipelineTimeoutTs()).isEqualTo(timeoutTs);
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testToDtoNullPipelineTimeoutTsWhenNotSet() {
    PipelineExecutionSummaryEntity entityWithoutTimeout = PipelineExecutionSummaryEntity.builder()
                                                              .accountId(accountId)
                                                              .orgIdentifier(orgId)
                                                              .projectIdentifier(projId)
                                                              .pipelineIdentifier(pipelineId)
                                                              .planExecutionId(planId)
                                                              .runSequence(1)
                                                              .pipelineVersion("0")
                                                              .endTs(System.currentTimeMillis())
                                                              .build();

    PipelineExecutionSummaryDTO dto =
        PipelineExecutionSummaryDtoMapper.toDto(entityWithoutTimeout, null, false, false, null, null);
    assertThat(dto.getPipelineTimeoutTs()).isNull();
  }
}