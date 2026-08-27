/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.beans.FeatureName.PIPE_VALIDATE_RETRY_FROM_PREVIOUS_EXECUTION_ID;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NAVNEET_KHANDELWAL;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.THRISHANK;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.PipelineServiceTestHelper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.impl.NodeExecutionServiceImpl;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.engine.executions.retry.RetryGroup;
import io.harness.engine.executions.retry.RetryHistoryResponseDto;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.executions.retry.RetryLatestExecutionResponseDto;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.execution.dynamic.DynamicExecutionService;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.PipelineExecutionSummaryEntityProjectionConstants;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.RollbackExecutionInfo;
import io.harness.pms.plan.execution.beans.dto.RetryExecutionInfoDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.steps.matrix.StrategyStep;
import io.harness.utils.PmsFeatureFlagService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class RetryExecuteHelperTest extends CategoryTest {
  @InjectMocks private RetryExecutionHelper retryExecuteHelper;
  @Mock private NodeExecutionServiceImpl nodeExecutionService;
  @Mock private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock private PMSPipelineService pipelineService;
  @Mock private PMSExecutionService executionService;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock DynamicExecutionService dynamicExecutionService;

  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";
  String pipelineId = "pipeline";
  String planExecId = "plan";

  String branch = "branch";

  String repoName = "repoName";

  String filepath = "filepath";

  private final long HR_IN_MS = 60 * 60 * 1000;
  private final long DAY_IN_MS = 24 * HR_IN_MS;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountId, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE);
    // Mock OPA_RUN_ON_CUSTOMER_INFRA to return false (so the OPA retry logic is enabled)
    doReturn(false).when(pmsFeatureFlagService).isEnabled(accountId, FeatureName.OPA_RUN_ON_CUSTOMER_INFRA);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  List<RetryStageInfo> getFirstStageFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(100L)
                         .build());
    return stageDetails;
  }

  private List<RetryStageInfo> getlastStageFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .nextId("stage2")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .nextId("stage3")
                         .parentId("parent2")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent3")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(300L)
                         .build());
    return stageDetails;
  }

  private List<RetryStageInfo> getlastStageFailedWithParallel() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .nextId("stage2")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .nextId("stage3")
                         .parentId("parent2")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent3")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(300L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage4")
                         .identifier("stage4")
                         .parentId("parent3")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(300L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage5")
                         .identifier("stage5")
                         .parentId("parent4")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(300L)
                         .build());
    return stageDetails;
  }

  private List<RetryStageInfo> getFirstStageParallelAndFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .parentId("parent1")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent1")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(300L)
                         .build());
    return stageDetails;
  }

  private List<RetryStageInfo> getlastStageParallelAndFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .nextId("stage4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .parentId("parent1")
                         .nextId("stage4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent1")
                         .nextId("stage4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(300L)
                         .build());

    stageDetails.add(RetryStageInfo.builder()
                         .name("stage4")
                         .identifier("stage4")
                         .parentId("parent2")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(400L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage5")
                         .identifier("stage5")
                         .parentId("parent2")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(500L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage6")
                         .identifier("stage6")
                         .parentId("parent2")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(600L)
                         .build());

    stageDetails.add(RetryStageInfo.builder()
                         .name("stage7")
                         .identifier("stage7")
                         .parentId("parent3")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(700L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage8")
                         .identifier("stage8")
                         .parentId("parent3")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(800L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage9")
                         .identifier("stage9")
                         .parentId("parent3")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(900L)
                         .build());

    return stageDetails;
  }

  private List<RetryStageInfo> getMixTypeStagesWithParallelFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .nextId("stage2")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .parentId("parent2")
                         .nextId("stage3")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent3")
                         .nextId("stage4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(300L)
                         .build());

    stageDetails.add(RetryStageInfo.builder()
                         .name("stage4")
                         .identifier("stage4")
                         .parentId("parent4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(400L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage5")
                         .identifier("stage5")
                         .parentId("parent4")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(500L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage6")
                         .identifier("stage6")
                         .parentId("parent4")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(600L)
                         .build());

    return stageDetails;
  }

  private List<RetryStageInfo> getMixTypeStagesWithSeriesStageFailed() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage1")
                         .identifier("stage1")
                         .parentId("parent1")
                         .nextId("stage2")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(100L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage2")
                         .identifier("stage2")
                         .parentId("parent2")
                         .nextId("stage3")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(200L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage3")
                         .identifier("stage3")
                         .parentId("parent3")
                         .nextId("stage4")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(300L)
                         .build());

    stageDetails.add(RetryStageInfo.builder()
                         .name("stage4")
                         .identifier("stage4")
                         .parentId("parent4")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(400L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage5")
                         .identifier("stage5")
                         .parentId("parent4")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(500L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage6")
                         .identifier("stage6")
                         .parentId("parent4")
                         .nextId("stage7")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(600L)
                         .build());

    stageDetails.add(RetryStageInfo.builder()
                         .name("stage7")
                         .identifier("stage7")
                         .parentId("parent7")
                         .nextId("stage8")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(400L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage8")
                         .identifier("stage8")
                         .parentId("parent8")
                         .nextId("stage9")
                         .status(ExecutionStatus.SUCCESS)
                         .createdAt(500L)
                         .build());
    stageDetails.add(RetryStageInfo.builder()
                         .name("stage9")
                         .identifier("stage9")
                         .parentId("parent9")
                         .status(ExecutionStatus.FAILED)
                         .createdAt(600L)
                         .build());
    return stageDetails;
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStagesSeries() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();

    // passing empty stageDetails
    RetryInfo retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.getGroups().size()).isEqualTo(0);

    // making first stage as empty
    stageDetails = getFirstStageFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.getGroups().get(0).getInfo()).isEqualTo(stageDetails);

    // making the last stageFailed
    stageDetails = getlastStageFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.getGroups().size()).isEqualTo(3);
    assertThat(retryInfo.getGroups().get(0).getInfo().get(0)).isEqualTo(stageDetails.get(0));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStagesSeriesV1() {
    List<RetryStageInfo> stageDetails = new ArrayList<>();

    // passing empty stageDetails
    RetryInfo retryInfo = retryExecuteHelper.getRetryInfoV1(stageDetails);
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.getGroups().size()).isEqualTo(0);

    // making first stage as empty
    stageDetails = getFirstStageFailed();
    retryInfo = retryExecuteHelper.getRetryInfoV1(stageDetails);
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.getGroups().get(0).getInfo()).isEqualTo(stageDetails);

    // making the last stageFailed
    stageDetails = getlastStageFailedWithParallel();
    retryInfo = retryExecuteHelper.getRetryInfoV1(stageDetails);
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.getGroups().size()).isEqualTo(4);
    // stage3 and 4 have same parentIds and nextIds so, they belong to same group
    // stage5 has same nextIds as them but different parentId so, it is in a different group
    assertThat(retryInfo.getGroups().get(0).getInfo().get(0)).isEqualTo(stageDetails.get(0));
    assertThat(retryInfo.getGroups().get(1).getInfo().get(0)).isEqualTo(stageDetails.get(1));
    assertThat(retryInfo.getGroups().get(2).getInfo().get(0)).isEqualTo(stageDetails.get(2));
    assertThat(retryInfo.getGroups().get(2).getInfo().get(1)).isEqualTo(stageDetails.get(3));
    assertThat(retryInfo.getGroups().get(3).getInfo().get(0)).isEqualTo(stageDetails.get(4));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetRetryInfoForDAG_EmptyStageDetails() {
    // Test with empty stage details - should return empty groups
    List<RetryStageInfo> stageDetails = new ArrayList<>();
    RetryInfo retryInfo = retryExecuteHelper.getRetryInfoForDAG(stageDetails);

    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.isResumable()).isTrue();
    assertThat(retryInfo.getGroups()).isEmpty();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetRetryInfoForDAG_SingleStage() {
    // Test with single stage - should return one group with one stage
    List<RetryStageInfo> stageDetails = Arrays.asList(
        RetryStageInfo.builder().identifier("stage1").name("Stage 1").status(ExecutionStatus.FAILED).build());

    RetryInfo retryInfo = retryExecuteHelper.getRetryInfoForDAG(stageDetails);

    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.isResumable()).isTrue();
    assertThat(retryInfo.getGroups()).hasSize(1);
    assertThat(retryInfo.getGroups().get(0).getInfo()).hasSize(1);
    assertThat(retryInfo.getGroups().get(0).getInfo().get(0).getIdentifier()).isEqualTo("stage1");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetRetryInfoForDAG_MultipleStages_EachInOwnGroup() {
    // Test with multiple stages - each stage should be in its own separate group
    // This is the key behavior for DAG pipelines where there's no parallel grouping
    List<RetryStageInfo> stageDetails = Arrays.asList(
        RetryStageInfo.builder().identifier("stage1").name("Stage 1").status(ExecutionStatus.SUCCESS).build(),
        RetryStageInfo.builder().identifier("stage2").name("Stage 2").status(ExecutionStatus.FAILED).build(),
        RetryStageInfo.builder().identifier("stage3").name("Stage 3").status(ExecutionStatus.SUCCESS).build(),
        RetryStageInfo.builder().identifier("stage4").name("Stage 4").status(ExecutionStatus.FAILED).build());

    RetryInfo retryInfo = retryExecuteHelper.getRetryInfoForDAG(stageDetails);

    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.isResumable()).isTrue();
    // Each stage should be in its own group
    assertThat(retryInfo.getGroups()).hasSize(4);

    // Verify each group contains exactly one stage
    for (int i = 0; i < stageDetails.size(); i++) {
      assertThat(retryInfo.getGroups().get(i).getInfo()).hasSize(1);
      assertThat(retryInfo.getGroups().get(i).getInfo().get(0)).isEqualTo(stageDetails.get(i));
    }
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetRetryInfoForDAG_StagesWithNullNextId() {
    // Test with stages that have null nextId (typical for DAG pipelines)
    // In DAG, stages don't have nextId - this is why regular getRetryInfo groups them all together
    // But getRetryInfoForDAG should put each in its own group
    List<RetryStageInfo> stageDetails = Arrays.asList(RetryStageInfo.builder()
                                                          .identifier("stage1")
                                                          .name("Stage 1")
                                                          .status(ExecutionStatus.SUCCESS)
                                                          .nextId(null)
                                                          .build(),
        RetryStageInfo.builder()
            .identifier("stage2")
            .name("Stage 2")
            .status(ExecutionStatus.FAILED)
            .nextId(null)
            .build(),
        RetryStageInfo.builder()
            .identifier("stage3")
            .name("Stage 3")
            .status(ExecutionStatus.FAILED)
            .nextId(null)
            .build());

    RetryInfo retryInfo = retryExecuteHelper.getRetryInfoForDAG(stageDetails);

    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.isResumable()).isTrue();
    // Each stage should be in its own group regardless of null nextId
    assertThat(retryInfo.getGroups()).hasSize(3);
    assertThat(retryInfo.getGroups().get(0).getInfo().get(0).getIdentifier()).isEqualTo("stage1");
    assertThat(retryInfo.getGroups().get(1).getInfo().get(0).getIdentifier()).isEqualTo("stage2");
    assertThat(retryInfo.getGroups().get(2).getInfo().get(0).getIdentifier()).isEqualTo("stage3");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetRetryInfoForDAG_CompareWithRegularGetRetryInfo() {
    // This test demonstrates the difference between DAG and sequential grouping
    // When stages have same nextId (null), regular getRetryInfo groups them together
    // But getRetryInfoForDAG puts each in its own group
    List<RetryStageInfo> stageDetails = Arrays.asList(
        RetryStageInfo.builder().identifier("stage1").status(ExecutionStatus.SUCCESS).nextId(null).build(),
        RetryStageInfo.builder().identifier("stage2").status(ExecutionStatus.FAILED).nextId(null).build(),
        RetryStageInfo.builder().identifier("stage3").status(ExecutionStatus.FAILED).nextId(null).build());

    // Regular getRetryInfo - groups by nextId, so all null nextId stages go to one group
    RetryInfo regularRetryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(regularRetryInfo.getGroups()).hasSize(1); // All in one group due to same nextId (null -> LAST_STAGE)
    assertThat(regularRetryInfo.getGroups().get(0).getInfo()).hasSize(3);

    // DAG getRetryInfoForDAG - each stage in its own group
    RetryInfo dagRetryInfo = retryExecuteHelper.getRetryInfoForDAG(stageDetails);
    assertThat(dagRetryInfo.getGroups()).hasSize(3); // Each stage in its own group
    assertThat(dagRetryInfo.getGroups().get(0).getInfo()).hasSize(1);
    assertThat(dagRetryInfo.getGroups().get(1).getInfo()).hasSize(1);
    assertThat(dagRetryInfo.getGroups().get(2).getInfo()).hasSize(1);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetRetryStages_DagEnabled_DelegatesToGetRetryInfoForDAG() {
    // This test verifies the integration path through getRetryStages with isDagEnabled=true
    // It ensures that getRetryStages properly delegates to getRetryInfoForDAG for DAG pipelines

    // Simple pipeline YAML - using same YAML for both params passes validateRetry
    String pipelineYaml = "pipeline:\n"
        + "  identifier: testPipeline\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stage1\n"
        + "        name: Stage 1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps: []\n"
        + "    - stage:\n"
        + "        identifier: stage2\n"
        + "        name: Stage 2\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps: []\n";

    String testPlanExecutionId = "testPlanExecId";

    // Mock the stage details returned from node execution service
    // These stages have null nextId (typical for DAG pipelines)
    List<RetryStageInfo> stageDetails = Arrays.asList(RetryStageInfo.builder()
                                                          .identifier("stage1")
                                                          .name("Stage 1")
                                                          .status(ExecutionStatus.FAILED)
                                                          .nextId(null)
                                                          .build(),
        RetryStageInfo.builder()
            .identifier("stage2")
            .name("Stage 2")
            .status(ExecutionStatus.SUCCESS)
            .nextId(null)
            .build());

    doReturn(stageDetails)
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionId(eq(testPlanExecutionId), eq(HarnessYamlVersion.V0));

    // Call getRetryStages with isDagEnabled = true
    RetryInfo retryInfo =
        retryExecuteHelper.getRetryStages(pipelineYaml, pipelineYaml, testPlanExecutionId, HarnessYamlVersion.V0,
            false, // storeTemplateRefEnabled
            true // isDagEnabled = true - this triggers DAG behavior
        );

    // Verify DAG behavior: each stage should be in its own separate group
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.isResumable()).isTrue();
    assertThat(retryInfo.getGroups()).hasSize(2); // 2 stages = 2 groups (DAG behavior)
    assertThat(retryInfo.getGroups().get(0).getInfo()).hasSize(1); // First group has 1 stage
    assertThat(retryInfo.getGroups().get(1).getInfo()).hasSize(1); // Second group has 1 stage
    assertThat(retryInfo.getGroups().get(0).getInfo().get(0).getIdentifier()).isEqualTo("stage1");
    assertThat(retryInfo.getGroups().get(1).getInfo().get(0).getIdentifier()).isEqualTo("stage2");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRetryProcessedYamlForDAG_RetryMiddleStage_SkipsAncestorsAndRerunsDownstream() throws IOException {
    String previousYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stageA\n"
        + "        \"__uuid\": \"oldUuidA\"\n"
        + "    - stage:\n"
        + "        identifier: stageB\n"
        + "        dependsOn:\n"
        + "          - stageA\n"
        + "        \"__uuid\": \"oldUuidB\"\n"
        + "    - stage:\n"
        + "        identifier: stageC\n"
        + "        dependsOn:\n"
        + "          - stageB\n"
        + "        \"__uuid\": \"oldUuidC\"\n";

    String currentYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stageA\n"
        + "        \"__uuid\": \"newUuidA\"\n"
        + "    - stage:\n"
        + "        identifier: stageB\n"
        + "        dependsOn:\n"
        + "          - stageA\n"
        + "        \"__uuid\": \"newUuidB\"\n"
        + "    - stage:\n"
        + "        identifier: stageC\n"
        + "        dependsOn:\n"
        + "          - stageB\n"
        + "        \"__uuid\": \"newUuidC\"\n";

    List<String> identifierOfSkipStages = new ArrayList<>();
    String resultYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("stageB"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId, true);

    assertThat(identifierOfSkipStages).containsExactly("stageA");
    assertThat(resultYaml).contains("oldUuidA");
    assertThat(resultYaml).contains("newUuidB");
    assertThat(resultYaml).contains("newUuidC");
    assertThat(resultYaml).doesNotContain("oldUuidB");
    assertThat(resultYaml).doesNotContain("oldUuidC");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRetryProcessedYamlForDAG_Fork_RetrySingleBranchOnly() throws IOException {
    String previousYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stageA\n"
        + "        \"__uuid\": \"oldUuidA\"\n"
        + "    - stage:\n"
        + "        identifier: stageB\n"
        + "        dependsOn:\n"
        + "          - stageA\n"
        + "        \"__uuid\": \"oldUuidB\"\n"
        + "    - stage:\n"
        + "        identifier: stageC\n"
        + "        dependsOn:\n"
        + "          - stageA\n"
        + "        \"__uuid\": \"oldUuidC\"\n";

    String currentYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stageA\n"
        + "        \"__uuid\": \"newUuidA\"\n"
        + "    - stage:\n"
        + "        identifier: stageB\n"
        + "        dependsOn:\n"
        + "          - stageA\n"
        + "        \"__uuid\": \"newUuidB\"\n"
        + "    - stage:\n"
        + "        identifier: stageC\n"
        + "        dependsOn:\n"
        + "          - stageA\n"
        + "        \"__uuid\": \"newUuidC\"\n";

    List<String> identifierOfSkipStages = new ArrayList<>();
    String resultYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("stageB"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId, true);

    assertThat(identifierOfSkipStages).containsExactlyInAnyOrder("stageA", "stageC");
    assertThat(resultYaml).contains("oldUuidA");
    assertThat(resultYaml).contains("oldUuidC");
    assertThat(resultYaml).contains("newUuidB");
    assertThat(resultYaml).doesNotContain("oldUuidB");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRetryProcessedYamlForDAG_InjectStageInSkipSet() throws IOException {
    String previousYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - insert:\n"
        + "        stages:\n"
        + "          - stage:\n"
        + "              identifier: injectStage1\n"
        + "              \"__uuid\": \"oldInjectUuid1\"\n"
        + "    - stage:\n"
        + "        identifier: stageB\n"
        + "        dependsOn:\n"
        + "          - injectStage1\n"
        + "        \"__uuid\": \"oldUuidB\"\n";

    String currentYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - insert:\n"
        + "        stages:\n"
        + "          - stage:\n"
        + "              identifier: injectStage1\n"
        + "              \"__uuid\": \"newInjectUuid1\"\n"
        + "    - stage:\n"
        + "        identifier: stageB\n"
        + "        dependsOn:\n"
        + "          - injectStage1\n"
        + "        \"__uuid\": \"newUuidB\"\n";

    List<String> identifierOfSkipStages = new ArrayList<>();
    String resultYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("stageB"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId, true);

    assertThat(identifierOfSkipStages).containsExactly("injectStage1");
    assertThat(resultYaml).contains("oldInjectUuid1");
    assertThat(resultYaml).contains("newUuidB");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRetryProcessedYamlForDAG_MatrixStageCopiesNestedUuidsWhenSkipped() throws IOException {
    String previousYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: matrixStage\n"
        + "        strategy:\n"
        + "          matrix:\n"
        + "            axes:\n"
        + "              - axis:\n"
        + "                  identifier: region\n"
        + "                  values: [\"us\", \"eu\"]\n"
        + "        \"__uuid\": \"oldMatrixUuid\"\n"
        + "    - stage:\n"
        + "        identifier: downstream\n"
        + "        dependsOn:\n"
        + "          - matrixStage\n"
        + "        \"__uuid\": \"oldDownstreamUuid\"\n";

    String currentYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: matrixStage\n"
        + "        strategy:\n"
        + "          matrix:\n"
        + "            axes:\n"
        + "              - axis:\n"
        + "                  identifier: region\n"
        + "                  values: [\"us\", \"eu\"]\n"
        + "        \"__uuid\": \"newMatrixUuid\"\n"
        + "    - stage:\n"
        + "        identifier: downstream\n"
        + "        dependsOn:\n"
        + "          - matrixStage\n"
        + "        \"__uuid\": \"newDownstreamUuid\"\n";

    List<String> identifierOfSkipStages = new ArrayList<>();
    String resultYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("downstream"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId, true);

    assertThat(identifierOfSkipStages).containsExactly("matrixStage");
    assertThat(resultYaml).contains("oldMatrixUuid");
    assertThat(resultYaml).contains("newDownstreamUuid");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testValidateRetryStagesIdentifiersAndGetRetryGroup_DagEnabled_AllowsIndependentStages() {
    String planExecutionId = "dagPlanExecutionId";
    List<RetryStageInfo> stageDetails =
        Arrays.asList(RetryStageInfo.builder().identifier("stageA").status(ExecutionStatus.FAILED).build(),
            RetryStageInfo.builder().identifier("stageB").status(ExecutionStatus.FAILED).build(),
            RetryStageInfo.builder().identifier("stageC").status(ExecutionStatus.SUCCESS).build());
    doReturn(stageDetails).when(nodeExecutionService).getStageDetailFromPlanExecutionId(planExecutionId, "0");

    RetryGroup retryGroup = retryExecuteHelper.validateRetryStagesIdentifiersAndGetRetryGroup(
        planExecutionId, List.of("stageA", "stageB"), HarnessYamlVersion.V0, true);

    assertThat(retryGroup.getInfo()).hasSize(2);
    assertThat(retryGroup.getInfo().stream().map(RetryStageInfo::getIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("stageA", "stageB");

    List<String> onlyFailed =
        retryExecuteHelper.fetchOnlyFailedStages(retryGroup.getInfo(), List.of("stageA", "stageB"));
    assertThat(onlyFailed).containsExactlyInAnyOrder("stageA", "stageB");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRetryProcessedYamlForDAG_ChainedPipelineStage() throws IOException {
    String previousYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: deployStage\n"
        + "        type: Deployment\n"
        + "        \"__uuid\": \"oldDeployUuid\"\n"
        + "    - stage:\n"
        + "        identifier: chainedPipelineStage\n"
        + "        type: Pipeline\n"
        + "        dependsOn:\n"
        + "          - deployStage\n"
        + "        \"__uuid\": \"oldChainedUuid\"\n"
        + "    - stage:\n"
        + "        identifier: verifyStage\n"
        + "        type: Custom\n"
        + "        dependsOn:\n"
        + "          - chainedPipelineStage\n"
        + "        \"__uuid\": \"oldVerifyUuid\"\n";

    String currentYaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: deployStage\n"
        + "        type: Deployment\n"
        + "        \"__uuid\": \"newDeployUuid\"\n"
        + "    - stage:\n"
        + "        identifier: chainedPipelineStage\n"
        + "        type: Pipeline\n"
        + "        dependsOn:\n"
        + "          - deployStage\n"
        + "        \"__uuid\": \"newChainedUuid\"\n"
        + "    - stage:\n"
        + "        identifier: verifyStage\n"
        + "        type: Custom\n"
        + "        dependsOn:\n"
        + "          - chainedPipelineStage\n"
        + "        \"__uuid\": \"newVerifyUuid\"\n";

    List<String> identifierOfSkipStages = new ArrayList<>();
    String resultYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("chainedPipelineStage"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId,
        true);

    assertThat(identifierOfSkipStages).containsExactly("deployStage");
    assertThat(resultYaml).contains("oldDeployUuid");
    assertThat(resultYaml).contains("newChainedUuid");
    assertThat(resultYaml).contains("newVerifyUuid");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRetryProcessedYamlForDAG_DynamicStage() throws IOException {
    String previousYaml = readFile("retry-processedYamlPreviousWithDynamicStage.yaml");
    String currentYaml = readFile("retry-processedYamlCurrentWithDynamicStage.yaml");
    String dynamicStageProcessedYaml = readFile("retry-dynamicStageProcessedYaml.yaml");

    doReturn(
        Optional.of(DynamicExecutionInstanceResponseDTO.builder().processedYaml(dynamicStageProcessedYaml).build()))
        .when(dynamicExecutionService)
        .getByPlanExecutionIdAndIdentifier(any(), anyString());

    List<String> identifierOfSkipStages = new ArrayList<>();
    retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("CustomStage"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId, true);

    assertThat(identifierOfSkipStages).contains("stage_1");
    assertThat(identifierOfSkipStages).doesNotContain("CustomStage");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetRetryStages_DagDisabled_UsesRegularGetRetryInfo() {
    // This test verifies that when isDagEnabled=false, getRetryStages uses regular getRetryInfo
    // which groups stages by nextId (all null nextId stages go into one group)

    String pipelineYaml = "pipeline:\n"
        + "  identifier: testPipeline\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: stage1\n"
        + "        name: Stage 1\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps: []\n"
        + "    - stage:\n"
        + "        identifier: stage2\n"
        + "        name: Stage 2\n"
        + "        type: Custom\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps: []\n";

    String testPlanExecutionId = "testPlanExecId2";

    // Same stage details with null nextId
    List<RetryStageInfo> stageDetails = Arrays.asList(RetryStageInfo.builder()
                                                          .identifier("stage1")
                                                          .name("Stage 1")
                                                          .status(ExecutionStatus.FAILED)
                                                          .nextId(null)
                                                          .build(),
        RetryStageInfo.builder()
            .identifier("stage2")
            .name("Stage 2")
            .status(ExecutionStatus.SUCCESS)
            .nextId(null)
            .build());

    doReturn(stageDetails)
        .when(nodeExecutionService)
        .getStageDetailFromPlanExecutionId(eq(testPlanExecutionId), eq(HarnessYamlVersion.V0));

    // Call getRetryStages with isDagEnabled = false
    RetryInfo retryInfo =
        retryExecuteHelper.getRetryStages(pipelineYaml, pipelineYaml, testPlanExecutionId, HarnessYamlVersion.V0,
            false, // storeTemplateRefEnabled
            false // isDagEnabled = false - uses regular grouping
        );

    // Verify regular behavior: stages with same nextId (null -> LAST_STAGE) are grouped together
    assertThat(retryInfo).isNotNull();
    assertThat(retryInfo.isResumable()).isTrue();
    assertThat(retryInfo.getGroups()).hasSize(1); // All stages in one group (non-DAG behavior)
    assertThat(retryInfo.getGroups().get(0).getInfo()).hasSize(2); // Both stages in same group
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStagesParallel() {
    List<RetryStageInfo> stageDetails;
    RetryInfo retryInfo;

    // making first stage as parallel and failed
    stageDetails = getFirstStageParallelAndFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    List<RetryGroup> retryGroupList = retryInfo.getGroups();
    assertThat(retryGroupList.get(0).getInfo()).isEqualTo(stageDetails);

    // having more than once parallel stages. All stages in parallel
    stageDetails = getlastStageParallelAndFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    retryGroupList = retryInfo.getGroups();
    assertThat(retryGroupList.size()).isEqualTo(3);
    assertThat(retryGroupList.get(0).getInfo().size()).isEqualTo(3);
    assertThat(retryGroupList.get(0).getInfo().get(0).getIdentifier()).isEqualTo("stage1");
    assertThat(retryGroupList.get(1).getInfo().get(0).getIdentifier()).isEqualTo("stage4");
    assertThat(retryGroupList.get(2).getInfo().get(0).getIdentifier()).isEqualTo("stage7");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStagesSeriesAndParallel() {
    List<RetryStageInfo> stageDetails;
    RetryInfo retryInfo;

    // parallel step failed after getting success for stages in series
    stageDetails = getMixTypeStagesWithParallelFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    List<RetryGroup> retryGroupList = retryInfo.getGroups();
    assertThat(retryGroupList.size()).isEqualTo(4);
    assertThat(retryGroupList.get(0).getInfo().get(0).getIdentifier()).isEqualTo("stage1");
    assertThat(retryGroupList.get(2).getInfo().get(0).getIdentifier()).isEqualTo("stage3");
    assertThat(retryGroupList.get(3).getInfo().size()).isEqualTo(3);

    // series stage failed having few stages in parallel before
    stageDetails = getMixTypeStagesWithSeriesStageFailed();
    retryInfo = retryExecuteHelper.getRetryInfo(stageDetails);
    assertThat(retryInfo).isNotNull();
    retryGroupList = retryInfo.getGroups();
    assertThat(retryGroupList.size()).isEqualTo(7);
    assertThat(retryGroupList.get(0).getInfo().get(0).getIdentifier()).isEqualTo("stage1");
    assertThat(retryGroupList.get(2).getInfo().get(0).getIdentifier()).isEqualTo("stage3");
    assertThat(retryGroupList.get(3).getInfo().size()).isEqualTo(3);
    assertThat(retryGroupList.get(6).getInfo().get(0).getIdentifier()).isEqualTo("stage9");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateRetry() {
    // empty and null yaml values
    assertThat(retryExecuteHelper.validateRetry("updatedYaml", "", false, "0")).isEqualTo(false);
    assertThat(retryExecuteHelper.validateRetry(null, "originalYaml", false, "0")).isEqualTo(false);

    // same yaml
    String updatedYamlFile = "retry-updated1.yaml";
    String updatedYaml = readFile(updatedYamlFile);

    String originalYamlFile = "retry-original1.yaml";
    String originalYaml = readFile(originalYamlFile);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml, originalYaml, false, "0")).isEqualTo(true);

    // updated the yaml - adding a stage
    // same yaml
    String updatedYamlFile2 = "retry-updated2.yaml";
    String updatedYaml2 = readFile(updatedYamlFile2);

    String originalYamlFile2 = "retry-original2.yaml";
    String originalYaml2 = readFile(originalYamlFile2);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml2, originalYaml2, false, "0")).isEqualTo(false);

    // added step in on of the stage and changed the name of the stage
    String updatedYamlFile3 = "retry-updated3.yaml";
    String updatedYaml3 = readFile(updatedYamlFile3);

    String originalYamlFile3 = "retry-original3.yaml";
    String originalYaml3 = readFile(originalYamlFile3);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml3, originalYaml3, false, "0")).isEqualTo(true);

    // updated the identifier
    String updatedYamlFile4 = "retry-updated4.yaml";
    String updatedYaml4 = readFile(updatedYamlFile4);

    String originalYamlFile4 = "retry-original4.yaml";
    String originalYaml4 = readFile(originalYamlFile4);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml4, originalYaml4, false, "0")).isEqualTo(false);

    // shuffling of stages
    String updatedYamlFile5 = "retry-updated5.yaml";
    String updatedYaml5 = readFile(updatedYamlFile5);

    String originalYamlFile5 = "retry-original5.yaml";
    String originalYaml5 = readFile(originalYamlFile5);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml5, originalYaml5, false, "0")).isEqualTo(false);

    // adding the stage in parallel
    String updatedYamlFile6 = "retry-updated6.yaml";
    String updatedYaml6 = readFile(updatedYamlFile6);

    String originalYamlFile6 = "retry-original6.yaml";
    String originalYaml6 = readFile(originalYamlFile6);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml6, originalYaml6, false, "0")).isEqualTo(false);

    // shuffling of parallel stages
    String updatedYamlFile7 = "retry-updated7.yaml";
    String updatedYaml7 = readFile(updatedYamlFile7);

    String originalYamlFile7 = "retry-original7.yaml";
    String originalYaml7 = readFile(originalYamlFile7);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml7, originalYaml7, false, "0")).isEqualTo(false);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateRetryWithStoreTemplateRefAsTrue() {
    // empty and null yaml values
    assertThat(retryExecuteHelper.validateRetry("updatedYaml", "", true, "0")).isEqualTo(false);
    assertThat(retryExecuteHelper.validateRetry(null, "originalYaml", true, "0")).isEqualTo(false);

    // same yaml
    String updatedYamlFile = "retry-updated1.yaml";
    String updatedYaml = readFile(updatedYamlFile);

    String originalYamlFile = "retry-original1.yaml";
    String originalYaml = readFile(originalYamlFile);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml, originalYaml, true, "0")).isEqualTo(true);

    // updated the yaml - adding a stage
    // same yaml
    String updatedYamlFile2 = "retry-updated2.yaml";
    String updatedYaml2 = readFile(updatedYamlFile2);

    String originalYamlFile2 = "retry-original2.yaml";
    String originalYaml2 = readFile(originalYamlFile2);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml2, originalYaml2, true, "0")).isEqualTo(false);

    // added step in on of the stage and changed the name of the stage
    String updatedYamlFile3 = "retry-updated3.yaml";
    String updatedYaml3 = readFile(updatedYamlFile3);

    String originalYamlFile3 = "retry-original3.yaml";
    String originalYaml3 = readFile(originalYamlFile3);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml3, originalYaml3, true, "0")).isEqualTo(true);

    // updated the identifier
    String updatedYamlFile4 = "retry-updated4.yaml";
    String updatedYaml4 = readFile(updatedYamlFile4);

    String originalYamlFile4 = "retry-original4.yaml";
    String originalYaml4 = readFile(originalYamlFile4);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml4, originalYaml4, true, "0")).isEqualTo(false);

    // shuffling of stages
    String updatedYamlFile5 = "retry-updated5.yaml";
    String updatedYaml5 = readFile(updatedYamlFile5);

    String originalYamlFile5 = "retry-original5.yaml";
    String originalYaml5 = readFile(originalYamlFile5);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml5, originalYaml5, true, "0")).isEqualTo(false);

    // adding the stage in parallel
    String updatedYamlFile6 = "retry-updated6.yaml";
    String updatedYaml6 = readFile(updatedYamlFile6);

    String originalYamlFile6 = "retry-original6.yaml";
    String originalYaml6 = readFile(originalYamlFile6);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml6, originalYaml6, true, "0")).isEqualTo(false);

    // shuffling of parallel stages
    String updatedYamlFile7 = "retry-updated7.yaml";
    String updatedYaml7 = readFile(updatedYamlFile7);

    String originalYamlFile7 = "retry-original7.yaml";
    String originalYaml7 = readFile(originalYamlFile7);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml7, originalYaml7, true, "0")).isEqualTo(false);

    // with template block
    String updatedYamlFile8 = "retry-updated8.yaml";
    String updatedYaml8 = readFile(updatedYamlFile8);

    String originalYamlFile8 = "retry-original8.yaml";
    String originalYaml8 = readFile(originalYamlFile8);

    assertThat(retryExecuteHelper.validateRetry(updatedYaml8, originalYaml8, true, "0")).isEqualTo(true);
    assertThat(retryExecuteHelper.validateRetry(updatedYaml8, originalYaml8, false, "0")).isEqualTo(false);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testRetryProcessedYaml() throws IOException {
    String previousYamlFile = "retry-processedYamlPrevious1.yaml";
    String previousYaml = readFile(previousYamlFile);
    String currentYamlFile = "retry-processedYamlCurrent1.yaml";
    String currentYaml = readFile(currentYamlFile);
    String resultYamlFile = "retry-processedYamlResult1.yaml";
    String resultYaml = readFile(resultYamlFile);
    List<String> identifierOfSkipStages = new ArrayList<>();
    String replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml,
        currentYaml, Collections.singletonList("stage2"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // resuming from the first stage
    resultYamlFile = "retry-processedYamlResultFirstStageFailed1.yaml";
    resultYaml = readFile(resultYamlFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("stage1"), new ArrayList<>(), HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // failing a single stage which is ahead of some parallel stages
    String previousGoldenYamlFile = "retry-processedYamlPreviousGolden.yaml";
    String previousGoldenYaml = readFile(previousGoldenYamlFile);
    String currentGoldenYamlFile = "retry-processedYamlCurrentGolden.yaml";
    String currentGoldenYaml = readFile(currentGoldenYamlFile);
    String resultProcessedFile = "retry-processedYamlResultGolden1.yaml";
    String resultProcessedYaml = readFile(resultProcessedFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousGoldenYaml,
        currentGoldenYaml, Collections.singletonList("stage7"), new ArrayList<>(), HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(yamlToJsonString(resultProcessedYaml));

    // failing single stages from parallel groups
    resultProcessedFile = "retry-processedYamlResultSingleStageFailedInParallelStages.yaml";
    resultProcessedYaml = readFile(resultProcessedFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousGoldenYaml,
        currentGoldenYaml, Collections.singletonList("stage9"), new ArrayList<>(), HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(yamlToJsonString(resultProcessedYaml));

    // failing multiple stage failure in parallel group
    resultProcessedFile = "retry-processedYamlResultMultipleStageFailedInParallelStages.yaml";
    resultProcessedYaml = readFile(resultProcessedFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousGoldenYaml,
        currentGoldenYaml, Arrays.asList("stage3", "stage5"), new ArrayList<>(), HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(yamlToJsonString(resultProcessedYaml));

    // selecting all stages in parallel group
    resultProcessedFile = "retry-processedYamlResultAllStageFailedInParallelStages.yaml";
    resultProcessedYaml = readFile(resultProcessedFile);
    replacedProcessedYaml =
        retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousGoldenYaml, currentGoldenYaml,
            Arrays.asList("stage3", "stage4", "stage5"), new ArrayList<>(), HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(yamlToJsonString(resultProcessedYaml));

    // testing the matrix scenarios
    // Resuming from the stage that has strategy in it.
    previousYaml = readFile("retry/previous-retry-processed-yaml-with-matrix.yaml");
    currentYaml = readFile("retry/current-processed-yaml-with-matrix.yaml");
    resultProcessedYaml = readFile("retry/result-processed-yaml-with-matrix.yaml");
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("approval"), Collections.emptyList(), HarnessYamlVersion.V0, accountId);
    assertEquals(replacedProcessedYaml, resultProcessedYaml);

    // Resuming from the next stage of the stage that has strategy.
    previousYaml = readFile("retry/previous-retry-processed-yaml-with-matrix-1.yaml");
    currentYaml = readFile("retry/current-processed-yaml-with-matrix-1.yaml");
    resultProcessedYaml = readFile("retry/result-processed-yaml-with-matrix-1.yaml");
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("sssss"), new ArrayList<>(), HarnessYamlVersion.V0, accountId);
    assertEquals(replacedProcessedYaml, resultProcessedYaml);

    // Retry failed deployment stage. First run was missing infrastructureDefinitions input. Retry execution is fixed
    // to provide infrastructureDefinitions.
    previousYaml = readFile("retry/previous-retry-processed-yaml-without-infraDef.yaml");
    currentYaml = readFile("retry/current-retry-processed-yaml-without-infraDef.yaml");
    resultProcessedYaml = readFile("retry/result-retry-processed-yaml-without-infraDef.yaml");
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("stage1"), new ArrayList<>(), HarnessYamlVersion.V0, accountId);
    assertEquals(replacedProcessedYaml, resultProcessedYaml);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRetryProcessedYamlForInject() throws IOException {
    String previousYamlFile = "retry-processedYamlPrevious1WithInject.yaml";
    String previousYaml = readFile(previousYamlFile);
    String currentYamlFile = "retry-processedYamlCurrent1WithInject.yaml";
    String currentYaml = readFile(currentYamlFile);

    // Converting YamlString to JsonString
    YAMLMapper yamlMapper = new YAMLMapper();
    JsonNode jsonNode1 = yamlMapper.readTree(previousYaml);
    JsonNode jsonNode2 = yamlMapper.readTree(currentYaml);

    ObjectMapper jsonMapper = new ObjectMapper();
    previousYaml = jsonMapper.writeValueAsString(jsonNode1);
    currentYaml = jsonMapper.writeValueAsString(jsonNode2);

    String resultYamlFile = "retry-processedYamlResult1WithInject.yaml";
    String resultYaml = readFile(resultYamlFile);
    List<String> identifierOfSkipStages = new ArrayList<>();
    String replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml,
        currentYaml, Collections.singletonList("dsa"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // resuming from the first stage before inject having matrix
    resultYamlFile = "retry-processedYamlResultWithInjectFirstStageFailed.yaml";
    resultYaml = readFile(resultYamlFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("wqw"), new ArrayList<>(), HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // failing multiple stages in parallel inside matrix within inject
    String resultProcessedFile = "retry-processedYamlResultWithInjectSecondStageFailed.yaml";
    String resultProcessedYaml = readFile(resultProcessedFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Arrays.asList("dsa", "dsadsa"), new ArrayList<>(), HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(yamlToJsonString(resultProcessedYaml));

    // Resuming from the next stage of the stage that has strategy.
    resultProcessedFile = "retry-processedYamlResultWithInjectNextStageAfterStrategy.yaml";
    resultProcessedYaml = readFile(resultProcessedFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        List.of("fdsf1sd"), new ArrayList<>(), HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(yamlToJsonString(resultProcessedYaml));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRetryProcessedYamlForEmptyInject() throws IOException {
    String previousYamlFile = "retry-processedYamlPreviousWithEmptyInject.yaml";
    String previousYaml = readFile(previousYamlFile);
    String currentYamlFile = "retry-processedYamlCurrent1WithEmptyInject.yaml";
    String currentYaml = readFile(currentYamlFile);

    // Converting YamlString to JsonString
    YAMLMapper yamlMapper = new YAMLMapper();
    JsonNode jsonNode1 = yamlMapper.readTree(previousYaml);
    JsonNode jsonNode2 = yamlMapper.readTree(currentYaml);

    ObjectMapper jsonMapper = new ObjectMapper();
    previousYaml = jsonMapper.writeValueAsString(jsonNode1);
    currentYaml = jsonMapper.writeValueAsString(jsonNode2);

    String resultYamlFile = "retry-processedYamlResult1WithEmptyInject.yaml";
    String resultYaml = readFile(resultYamlFile);
    List<String> identifierOfSkipStages = new ArrayList<>();
    String replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml,
        currentYaml, Collections.singletonList("dasddsa"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRetryProcessedYamlForV1() throws IOException {
    String previousYamlFile = "retry-processedYamlPreviousV1.yaml";
    String previousYaml = readFile(previousYamlFile);
    String currentYamlFile = "retry-processedYamlCurrentV1.yaml";
    String currentYaml = readFile(currentYamlFile);
    List<String> identifierOfSkipStages = new ArrayList<>();

    // Retrying from stage1 that was passed in previous execution.
    String resultYamlFile = "retry-processedYamlResultV1.yaml";
    String resultYaml = readFile(resultYamlFile);
    String replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml,
        currentYaml, List.of("stage1"), identifierOfSkipStages, HarnessYamlVersion.V1, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // Retrying from parallel stages stage2_1 and stage2_2. Only one of these stages were failed. But in retry both the
    // stages will run.
    resultYamlFile = "retry-processedYamlResult1V1.yaml";
    resultYaml = readFile(resultYamlFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        List.of("stage2_1", "stage2_2"), identifierOfSkipStages, HarnessYamlVersion.V1, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // Retrying from parallel stages stage2_1 only. Only one of these stages were failed. And one will run while retry.
    resultYamlFile = "retry-processedYamlResult2V1.yaml";
    resultYaml = readFile(resultYamlFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        List.of("stage2_2"), identifierOfSkipStages, HarnessYamlVersion.V1, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);

    // Retrying from parallel stages stage1_1 and stage1_2. Both were success in previous execuiton.
    resultYamlFile = "retry-processedYamlResult3V1.yaml";
    resultYaml = readFile(resultYamlFile);
    replacedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        List.of("stage1_1", "stage1_2"), identifierOfSkipStages, HarnessYamlVersion.V1, accountId);
    assertThat(replacedProcessedYaml).isEqualTo(resultYaml);
  }

  private String yamlToJsonString(String resultProcessedYaml) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    return mapper.readTree(resultProcessedYaml).toString();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testIsFailedStatus() {
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.EXPIRED)).isEqualTo(true);
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.ABORTED)).isEqualTo(true);
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.FAILED)).isEqualTo(true);
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.APPROVAL_REJECTED)).isEqualTo(true);
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.APPROVALREJECTED)).isEqualTo(true);
    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.ABORTEDBYFREEZE)).isEqualTo(true);

    assertThat(retryExecuteHelper.isFailedStatus(ExecutionStatus.SUCCESS)).isEqualTo(false);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFetchOnlyFailedStages() {
    List<RetryStageInfo> retryStageInfos = new ArrayList<>();
    List<String> stageIdentifier = new ArrayList<>();
    assertThatThrownBy(() -> retryExecuteHelper.fetchOnlyFailedStages(retryStageInfos, stageIdentifier))
        .isInstanceOf(InvalidRequestException.class);

    // testing caching of exception
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage1").build());
    stageIdentifier.add("stage2");
    assertThatThrownBy(() -> retryExecuteHelper.fetchOnlyFailedStages(retryStageInfos, stageIdentifier))
        .isInstanceOf(InvalidRequestException.class);

    stageIdentifier.clear();
    retryStageInfos.clear();

    // testing whole valid status
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage1").status(ExecutionStatus.SUCCESS).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage2").status(ExecutionStatus.ABORTED).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage3").status(ExecutionStatus.IGNOREFAILED).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage4").status(ExecutionStatus.FAILED).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage5").status(ExecutionStatus.EXPIRED).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage6").status(ExecutionStatus.APPROVALREJECTED).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage7").status(ExecutionStatus.APPROVALREJECTED).build());

    stageIdentifier.add("stage1");
    stageIdentifier.add("stage2");
    stageIdentifier.add("stage3");
    stageIdentifier.add("stage4");
    stageIdentifier.add("stage5");
    stageIdentifier.add("stage6");
    stageIdentifier.add("stage7");

    List<String> onlyFailedStageIdentifier = retryExecuteHelper.fetchOnlyFailedStages(retryStageInfos, stageIdentifier);
    assertThat(onlyFailedStageIdentifier.size()).isEqualTo(5);
    assertThat(onlyFailedStageIdentifier).contains("stage2");
    assertThat(onlyFailedStageIdentifier).contains("stage4");
    assertThat(onlyFailedStageIdentifier).contains("stage5");
    assertThat(onlyFailedStageIdentifier).contains("stage6");
    assertThat(onlyFailedStageIdentifier).contains("stage7");
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testFetchOnlyFailedStagesReExecutesFreezeAbortedStage() {
    List<RetryStageInfo> retryStageInfos = new ArrayList<>();
    retryStageInfos.add(RetryStageInfo.builder().identifier("deploy_dev").status(ExecutionStatus.FAILED).build());
    retryStageInfos.add(
        RetryStageInfo.builder().identifier("deploy_prod").status(ExecutionStatus.ABORTEDBYFREEZE).build());
    List<String> retryStagesIdentifier = List.of("deploy_dev", "deploy_prod");

    List<String> onlyFailedStages = retryExecuteHelper.fetchOnlyFailedStages(retryStageInfos, retryStagesIdentifier);

    assertThat(onlyFailedStages.size()).isEqualTo(2);
    assertThat(onlyFailedStages).contains("deploy_dev");
    assertThat(onlyFailedStages).contains("deploy_prod");
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testFetchOnlyFailedStagesAllFreezeAborted() {
    List<RetryStageInfo> retryStageInfos = new ArrayList<>();
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage_a").status(ExecutionStatus.ABORTEDBYFREEZE).build());
    retryStageInfos.add(RetryStageInfo.builder().identifier("stage_b").status(ExecutionStatus.ABORTEDBYFREEZE).build());
    List<String> retryStagesIdentifier = List.of("stage_a", "stage_b");

    List<String> onlyFailedStages = retryExecuteHelper.fetchOnlyFailedStages(retryStageInfos, retryStagesIdentifier);

    assertThat(onlyFailedStages.size()).isEqualTo(2);
    assertThat(onlyFailedStages).contains("stage_a");
    assertThat(onlyFailedStages).contains("stage_b");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFetchUuidOfNonRetryStages() throws IOException {
    String previousYamlFile = "retry-processedYamlPrevious1.yaml";
    String previousYaml = readFile(previousYamlFile);
    String currentYamlFile = "retry-processedYamlCurrent1.yaml";
    String currentYaml = readFile(currentYamlFile);
    List<String> identifierOfSkipStages = new ArrayList<>();
    retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("stage2"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);

    // resuming from the first stage
    identifierOfSkipStages.clear();
    retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("stage1"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);
    assertThat(identifierOfSkipStages.size()).isEqualTo(0);

    // failing a single stage which is ahead of some parallel stages
    identifierOfSkipStages.clear();
    String previousGoldenYamlFile = "retry-processedYamlPreviousGolden.yaml";
    String previousGoldenYaml = readFile(previousGoldenYamlFile);
    String currentGoldenYamlFile = "retry-processedYamlCurrentGolden.yaml";
    String currentGoldenYaml = readFile(currentGoldenYamlFile);
    retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousGoldenYaml, currentGoldenYaml,
        Collections.singletonList("stage7"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);
    assertThat(identifierOfSkipStages.size()).isEqualTo(6);
    assertThat(identifierOfSkipStages.get(0)).isEqualTo("stage1");
    assertThat(identifierOfSkipStages.get(1)).isEqualTo("stage2");
    assertThat(identifierOfSkipStages.get(2)).isEqualTo("stage3");
    assertThat(identifierOfSkipStages.get(3)).isEqualTo("stage4");
    assertThat(identifierOfSkipStages.get(4)).isEqualTo("stage5");
    assertThat(identifierOfSkipStages.get(5)).isEqualTo("stage6");

    // failing single stages from parallel groups
    identifierOfSkipStages.clear();
    retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousGoldenYaml, currentGoldenYaml,
        Collections.singletonList("stage9"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);
    assertThat(identifierOfSkipStages.size()).isEqualTo(8);
    assertThat(identifierOfSkipStages.get(0)).isEqualTo("stage1");
    assertThat(identifierOfSkipStages.get(1)).isEqualTo("stage2");
    assertThat(identifierOfSkipStages.get(2)).isEqualTo("stage3");
    assertThat(identifierOfSkipStages.get(3)).isEqualTo("stage4");
    assertThat(identifierOfSkipStages.get(4)).isEqualTo("stage5");
    assertThat(identifierOfSkipStages.get(5)).isEqualTo("stage6");
    assertThat(identifierOfSkipStages.get(6)).isEqualTo("stage7");
    assertThat(identifierOfSkipStages.get(7)).isEqualTo("stage8");

    // failing multiple stage failure in parallel group
    identifierOfSkipStages.clear();
    retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousGoldenYaml, currentGoldenYaml,
        Arrays.asList("stage3", "stage5"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);
    assertThat(identifierOfSkipStages.size()).isEqualTo(3);
    assertThat(identifierOfSkipStages.get(0)).isEqualTo("stage1");
    assertThat(identifierOfSkipStages.get(1)).isEqualTo("stage2");
    assertThat(identifierOfSkipStages.get(2)).isEqualTo("stage4");

    // selecting all stages in parallel group
    identifierOfSkipStages.clear();
    retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousGoldenYaml, currentGoldenYaml,
        Arrays.asList("stage3", "stage4", "stage5"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);
    assertThat(identifierOfSkipStages.size()).isEqualTo(2);
    assertThat(identifierOfSkipStages.get(0)).isEqualTo("stage1");
    assertThat(identifierOfSkipStages.get(1)).isEqualTo("stage2");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void tesRetryProcessedYamlWithDynamicStage() throws IOException {
    String previousYamlFile = "retry-processedYamlPreviousWithDynamicStage.yaml";
    String previousYaml = readFile(previousYamlFile);
    String currentYamlFile = "retry-processedYamlCurrentWithDynamicStage.yaml";
    String currentYaml = readFile(currentYamlFile);
    String dynamicStageProcessedYaml = readFile("retry-dynamicStageProcessedYaml.yaml");

    doReturn(
        Optional.of(DynamicExecutionInstanceResponseDTO.builder().processedYaml(dynamicStageProcessedYaml).build()))
        .when(dynamicExecutionService)
        .getByPlanExecutionIdAndIdentifier(any(), anyString());
    List<String> identifierOfSkipStages = new ArrayList<>();
    String updatedProcessedYaml = retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml,
        currentYaml, Collections.singletonList("cdsadsa"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);

    assertThat(updatedProcessedYaml).isNotEmpty();
    assertThat(identifierOfSkipStages.size()).isEqualTo(3);
    assertThat(identifierOfSkipStages.get(0)).isEqualTo("stage_1");
    assertThat(identifierOfSkipStages.get(1)).isEqualTo("CustomStage");
    assertThat(identifierOfSkipStages.get(2)).isEqualTo("cs2333");
    assertThat(updatedProcessedYaml).isEqualTo(readFile("retry-updatedProcessedYamlWithDynamicStage.yaml"));

    identifierOfSkipStages.clear();
    retryExecuteHelper.retryProcessedYaml("originalExecutionId", previousYaml, currentYaml,
        Collections.singletonList("CustomStage"), identifierOfSkipStages, HarnessYamlVersion.V0, accountId);
    assertThat(identifierOfSkipStages.size()).isEqualTo(1);
    assertThat(identifierOfSkipStages.get(0)).isEqualTo("stage_1");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testTransformPlan() {
    StepType TEST_STEP_TYPE =
        StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build();
    String uuid = "uuid1";
    List<String> identifierOfSkipStages = Collections.singletonList(uuid);
    List<String> stageIdentifierToRetryWith = Collections.singletonList("stage3");

    when(nodeExecutionService.fetchStageFqnFromStageIdentifiers(any(), eq(identifierOfSkipStages)))
        .thenReturn(Collections.singletonList("pipeline.stages.pip1"));

    PlanNode planNode1 =
        PlanNode.builder()
            .name("Test Node")
            .uuid(uuid)
            .identifier("test")
            .stageFqn("pipeline.stages.pip1")
            .stepType(TEST_STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .build();

    Map<String, Node> uuidMapper = new HashMap<>();
    uuidMapper.put("nodeUuid", planNode1);
    when(nodeExecutionService.mapNodeExecutionIdWithPlanNodeForGivenStageFQN(any(), any())).thenReturn(uuidMapper);

    // Returning emptyList. So strategy node should not get converted to IdentityNode.
    doReturn(Collections.emptyList()).when(nodeExecutionService).fetchStrategyNodeExecutions(any(), any());
    PlanNode planNode2 =
        PlanNode.builder()
            .name("Test Node2")
            .uuid("uuid2")
            .identifier("test2")
            .stepType(TEST_STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .build();

    PlanNode planNode3 =
        PlanNode.builder()
            .name("Test Node3")
            .uuid("uuid3")
            .identifier("test3")
            .stageFqn("pipeline.stages.stage3")
            .stepType(StrategyStep.STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .build();

    Plan newPlan = retryExecuteHelper.transformPlan(
        Plan.builder().planNodes(Arrays.asList(planNode1, planNode2, planNode3)).build(), identifierOfSkipStages, "abc",
        stageIdentifierToRetryWith, true);

    List<Node> updatedNodes = newPlan.getPlanNodes();
    List<Node> identityPlanNodes =
        updatedNodes.stream().filter(o -> o instanceof IdentityPlanNode).collect(Collectors.toList());
    assertThat(updatedNodes.size()).isEqualTo(3);
    assertThat(updatedNodes.get(0).getNodeType()).isEqualTo(NodeType.PLAN_NODE);
    assertThat(updatedNodes.get(2).getNodeType()).isEqualTo(NodeType.PLAN_NODE);
    assertEquals(identityPlanNodes.size(), 1);
    assertThat(((IdentityPlanNode) identityPlanNodes.get(0)).getOriginalNodeExecutionId()).isEqualTo("nodeUuid");
    assertThat(identityPlanNodes.get(0).getIdentifier()).isEqualTo("test");
    assertThat(identityPlanNodes.get(0).getName()).isEqualTo("Test Node");
    assertThat(identityPlanNodes.get(0).getUuid()).isEqualTo(uuid);

    List<Node> strategyNodes =
        updatedNodes.stream().filter(o -> o.getStepType().equals(StrategyStep.STEP_TYPE)).collect(Collectors.toList());
    assertEquals(strategyNodes.size(), 1);
    // This would be PlanNode because previous noExecutions did not have strategy node for provided stageFqn.
    assertEquals(strategyNodes.get(0).getNodeType(), NodeType.PLAN_NODE);

    doReturn(Collections.singletonList("pipeline.stages.stage3"))
        .when(nodeExecutionService)
        .fetchStageFqnFromStageIdentifiers(any(), eq(stageIdentifierToRetryWith));
    // This StrategyNode does not have any executableResponse, so it will not be converted to identityNode.
    doReturn(Collections.singletonList(NodeExecution.builder()
                                           .ambiance(Ambiance.newBuilder()
                                                         .addLevels(Level.newBuilder().setGroup("STAGES").build())
                                                         .addLevels(Level.newBuilder().build())
                                                         .build())
                                           .stageFqn("pipeline.stages.stage3")
                                           .nodeId(planNode3.getUuid())
                                           .build()))
        .when(nodeExecutionService)
        .fetchStrategyNodeExecutions(any(), any());

    // Testing with runAllStages as true
    newPlan = retryExecuteHelper.transformPlan(
        Plan.builder().planNodes(Arrays.asList(planNode1, planNode2, planNode3)).build(), identifierOfSkipStages, "abc",
        stageIdentifierToRetryWith, true);

    updatedNodes = newPlan.getPlanNodes();
    identityPlanNodes = updatedNodes.stream().filter(o -> o instanceof IdentityPlanNode).collect(Collectors.toList());

    assertEquals(identityPlanNodes.size(), 1);

    newPlan = retryExecuteHelper.transformPlan(
        Plan.builder().planNodes(Arrays.asList(planNode1, planNode2, planNode3)).build(), identifierOfSkipStages, "abc",
        stageIdentifierToRetryWith, false);

    updatedNodes = newPlan.getPlanNodes();
    identityPlanNodes = updatedNodes.stream().filter(o -> o instanceof IdentityPlanNode).collect(Collectors.toList());

    assertEquals(identityPlanNodes.size(), 1);

    // This StrategyNode have at least one executableResponse so this will be converted into IdentityNode
    doReturn(Collections.singletonList(NodeExecution.builder()
                                           .ambiance(Ambiance.newBuilder()
                                                         .addLevels(Level.newBuilder().setGroup("STAGES").build())
                                                         .addLevels(Level.newBuilder().build())
                                                         .build())
                                           .stageFqn("pipeline.stages.stage3")
                                           .nodeId(planNode3.getUuid())
                                           .executableResponse(ExecutableResponse.newBuilder().build())
                                           .build()))
        .when(nodeExecutionService)
        .fetchStrategyNodeExecutions(any(), any());

    newPlan = retryExecuteHelper.transformPlan(
        Plan.builder().planNodes(Arrays.asList(planNode1, planNode2, planNode3)).build(), identifierOfSkipStages, "abc",
        stageIdentifierToRetryWith, false);

    updatedNodes = newPlan.getPlanNodes();
    identityPlanNodes = updatedNodes.stream().filter(o -> o instanceof IdentityPlanNode).collect(Collectors.toList());

    assertEquals(identityPlanNodes.size(), 2);

    assertThat(((IdentityPlanNode) identityPlanNodes.get(0)).getOriginalNodeExecutionId()).isEqualTo("nodeUuid");
    assertThat(identityPlanNodes.get(0).getIdentifier()).isEqualTo("test");
    assertThat(identityPlanNodes.get(0).getName()).isEqualTo("Test Node");
    assertThat(identityPlanNodes.get(0).getUuid()).isEqualTo(uuid);

    strategyNodes = identityPlanNodes.stream()
                        .filter(o -> o.getStepType().equals(StrategyStep.STEP_TYPE))
                        .collect(Collectors.toList());
    assertEquals(strategyNodes.size(), 1);
    // This would be of IdentityPlanNode type. Previous nodeExecutions has strategyNode with provided stageFqn.
    assertEquals(strategyNodes.get(0).getNodeType(), NodeType.IDENTITY_PLAN_NODE);
    assertThat(strategyNodes.get(0).getIdentifier()).isEqualTo(planNode3.getIdentifier());
    assertThat(strategyNodes.get(0).getName()).isEqualTo(planNode3.getName());
    assertThat(strategyNodes.get(0).getUuid()).isEqualTo(planNode3.getUuid());
    assertThat(((IdentityPlanNode) strategyNodes.get(0)).getUseAdviserObtainments()).isTrue();
    assertThat(strategyNodes.get(0).getAdviserObtainments()).isEqualTo(planNode3.getAdviserObtainments());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testTransformPlanWithSameStrategyNodeIdentifierAtStageAndStep() {
    StepType TEST_STEP_TYPE =
        StepType.newBuilder().setType("TEST_STEP_PLAN").setStepCategory(StepCategory.STEP).build();
    String uuid = "uuid1";
    List<String> identifierOfSkipStages = Collections.singletonList(uuid);
    List<String> stageIdentifierToRetryWith = Collections.singletonList("stage3");

    PlanNode planNode1 =
        PlanNode.builder()
            .name("Test Node")
            .uuid(uuid)
            .identifier("test")
            .stageFqn("pipeline.stages.pip1")
            .stepType(TEST_STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .build();

    Map<String, Node> uuidMapper = new HashMap<>();
    uuidMapper.put("nodeUuid", planNode1);
    when(nodeExecutionService.mapNodeExecutionIdWithPlanNodeForGivenStageFQN(any(), any())).thenReturn(uuidMapper);

    // Returning emptyList. So strategy node should not get converted to IdentityNode.
    doReturn(Collections.emptyList()).when(nodeExecutionService).fetchStrategyNodeExecutions(any(), any());
    PlanNode planNode2 =
        PlanNode.builder()
            .name("Test Node2")
            .uuid("uuid2")
            .identifier("test2")
            .stepType(TEST_STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .build();

    PlanNode planNode3 =
        PlanNode.builder()
            .name("Test Node3")
            .uuid("uuid3")
            .identifier("test3")
            .stageFqn("pipeline.stages.stage3")
            .stepType(StrategyStep.STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .build();

    // PlanNode.identifier is same. But UUID will be different. Only the strategy node of stage level should be
    // converted into the IdentityPlanNode.
    PlanNode planNode4 =
        PlanNode.builder()
            .name("Test Node3")
            .uuid("uuid4")
            .identifier("test3")
            .stageFqn("pipeline.stages.stage3")
            .stepType(StrategyStep.STEP_TYPE)
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("NEXT_STEP").build()).build())
            .build();

    doReturn(Collections.singletonList("pipeline.stages.stage3"))
        .when(nodeExecutionService)
        .fetchStageFqnFromStageIdentifiers(any(), eq(stageIdentifierToRetryWith));
    // StrategyNode should not be converted to IdentityNode now. As it does not have any executable response.
    doReturn(Arrays.asList(NodeExecution.builder()
                               .uuid("stageStrategyNodeExecutionUUID")
                               .ambiance(Ambiance.newBuilder()
                                             .addLevels(Level.newBuilder().setGroup("STAGES").build())
                                             .addLevels(Level.newBuilder().build())
                                             .build())
                               .stageFqn("pipeline.stages.stage3")
                               .nodeId(planNode3.getUuid())
                               .build(),
                 NodeExecution.builder()
                     .uuid("stepStrategyNodeExecutionUUID")
                     .ambiance(Ambiance.newBuilder()
                                   .addLevels(Level.newBuilder().setGroup("STAGES").build())
                                   .addLevels(Level.newBuilder().setGroup("STRATEGY").build())
                                   .addLevels(Level.newBuilder().setGroup("STAGE").build())
                                   .addLevels(Level.newBuilder().setGroup("STRATEGY").build())
                                   .build())
                     .stageFqn("pipeline.stages.stage3")
                     .nodeId(planNode4.getUuid())
                     .build()))
        .when(nodeExecutionService)
        .fetchStrategyNodeExecutions(any(), any());
    doReturn(Collections.singletonList("pipeline.stages.pip1"))
        .when(nodeExecutionService)
        .fetchStageFqnFromStageIdentifiers("abc", identifierOfSkipStages);

    Plan newPlan = retryExecuteHelper.transformPlan(
        Plan.builder().planNodes(Arrays.asList(planNode1, planNode2, planNode3, planNode4)).build(),
        identifierOfSkipStages, "abc", stageIdentifierToRetryWith, false);

    List<Node> updatedNodes = newPlan.getPlanNodes();
    List<Node> identityPlanNodes =
        updatedNodes.stream().filter(o -> o instanceof IdentityPlanNode).collect(Collectors.toList());

    assertEquals(identityPlanNodes.size(), 1);
    List<Node> strategyIdentityNodes = identityPlanNodes.stream()
                                           .filter(o -> o.getStepType().equals(StrategyStep.STEP_TYPE))
                                           .collect(Collectors.toList());
    assertEquals(strategyIdentityNodes.size(), 0);

    // StrategyNode should get converted to IdentityNode now.
    doReturn(Arrays.asList(NodeExecution.builder()
                               .uuid("stageStrategyNodeExecutionUUID")
                               .ambiance(Ambiance.newBuilder()
                                             .addLevels(Level.newBuilder().setGroup("STAGES").build())
                                             .addLevels(Level.newBuilder().build())
                                             .build())
                               .executableResponse(ExecutableResponse.newBuilder().build())
                               .stageFqn("pipeline.stages.stage3")
                               .nodeId(planNode3.getUuid())
                               .build(),
                 NodeExecution.builder()
                     .uuid("stepStrategyNodeExecutionUUID")
                     .ambiance(Ambiance.newBuilder()
                                   .addLevels(Level.newBuilder().setGroup("STAGES").build())
                                   .addLevels(Level.newBuilder().setGroup("STRATEGY").build())
                                   .addLevels(Level.newBuilder().setGroup("STAGE").build())
                                   .addLevels(Level.newBuilder().setGroup("STRATEGY").build())
                                   .build())
                     .stageFqn("pipeline.stages.stage3")
                     .nodeId(planNode4.getUuid())
                     .build()))
        .when(nodeExecutionService)
        .fetchStrategyNodeExecutions(any(), any());

    newPlan = retryExecuteHelper.transformPlan(
        Plan.builder().planNodes(Arrays.asList(planNode1, planNode2, planNode3, planNode4)).build(),
        identifierOfSkipStages, "abc", stageIdentifierToRetryWith, false);

    updatedNodes = newPlan.getPlanNodes();
    identityPlanNodes = updatedNodes.stream().filter(o -> o instanceof IdentityPlanNode).collect(Collectors.toList());

    assertEquals(identityPlanNodes.size(), 2);
    strategyIdentityNodes = identityPlanNodes.stream()
                                .filter(o -> o.getStepType().equals(StrategyStep.STEP_TYPE))
                                .collect(Collectors.toList());
    assertEquals(strategyIdentityNodes.size(), 1);

    assertEquals(strategyIdentityNodes.get(0).getNodeType(), NodeType.IDENTITY_PLAN_NODE);
    IdentityPlanNode strategyIdentityNode = (IdentityPlanNode) strategyIdentityNodes.get(0);
    assertThat(strategyIdentityNode.getOriginalNodeExecutionId()).isEqualTo("stageStrategyNodeExecutionUUID");
    assertThat(strategyIdentityNode.getIdentifier()).isEqualTo(planNode3.getIdentifier());
    assertThat(strategyIdentityNode.getName()).isEqualTo(planNode3.getName());
    assertThat(strategyIdentityNode.getUuid()).isEqualTo(planNode3.getUuid());
    assertThat(strategyIdentityNode.getUseAdviserObtainments()).isTrue();
    assertThat(strategyIdentityNode.getAdviserObtainments()).isEqualTo(planNode3.getAdviserObtainments());

    List<Node> strategyPlanNodes =
        updatedNodes.stream()
            .filter(o -> o.getStepType().equals(StrategyStep.STEP_TYPE) && o.getNodeType() == NodeType.PLAN_NODE)
            .collect(Collectors.toList());
    assertEquals(strategyIdentityNodes.size(), 1);
    // The identifier was same. But this will not be converted into the IdentityNode becaue now we check the
    // nodeExecution.nodeId(Equivalent to nodeExecution.planNode.uid) so the exact node will match even though same
    // identifier for multiple planNodes.
    assertEquals(strategyPlanNodes.get(0).getIdentifier(), strategyIdentityNode.getIdentifier());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetHistory() {
    String rootExecutionId = "rootExecutionId";
    List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        Collections.singletonList(PipelineExecutionSummaryEntity.builder().planExecutionId(rootExecutionId).build());

    // entities are <=1. Checking error message
    when(pmsExecutionSummaryRepository.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(rootExecutionId))
        .thenReturn(
            PipelineServiceTestHelper.createCloseableIterator(pipelineExecutionSummaryEntities.iterator()).stream());
    RetryHistoryResponseDto retryHistory =
        retryExecuteHelper.getRetryHistory(accountId, rootExecutionId, "planExecutionId");
    assertThat(retryHistory.getErrorMessage()).isNotNull();

    pipelineExecutionSummaryEntities = Arrays.asList(PipelineExecutionSummaryEntity.builder()
                                                         .planExecutionId("uuid1")
                                                         .startTs(10L)
                                                         .endTs(11L)
                                                         .status(ExecutionStatus.FAILED)
                                                         .runSequence(1)
                                                         .build(),
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("uuid2")
            .startTs(20L)
            .endTs(21L)
            .status(ExecutionStatus.FAILED)
            .runSequence(2)
            .build(),
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId("uuid3")
            .startTs(30L)
            .endTs(31L)
            .status(ExecutionStatus.ABORTED)
            .build());

    when(pmsExecutionSummaryRepository.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(rootExecutionId))
        .thenReturn(
            PipelineServiceTestHelper.createCloseableIterator(pipelineExecutionSummaryEntities.iterator()).stream());
    doReturn(Optional.of(PlanExecutionMetadata.builder().build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(accountId, "planExecutionId");
    retryHistory = retryExecuteHelper.getRetryHistory(accountId, rootExecutionId, "planExecutionId");
    assertThat(retryHistory.getErrorMessage()).isNull();
    assertThat(retryHistory.getLatestExecutionId()).isEqualTo("uuid1");
    assertThat(retryHistory.getExecutionInfos().size()).isEqualTo(3);
    assertThat(retryHistory.getExecutionInfos().get(0).getRunSequence()).isEqualTo(1);
    assertThat(retryHistory.getExecutionInfos().get(1).getRunSequence()).isEqualTo(2);
    assertThat(retryHistory.getExecutionInfos().get(2).getRunSequence()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetHistoryWithoutRetryExecutionMetadata() {
    String rootExecutionId = "rootExecutionId";
    List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
        Arrays.asList(PipelineExecutionSummaryEntity.builder()
                          .planExecutionId("uuid2")
                          .startTs(30L)
                          .endTs(31L)
                          .status(ExecutionStatus.FAILED)
                          .runSequence(3)
                          .build(),
            PipelineExecutionSummaryEntity.builder()
                .planExecutionId("uuid1")
                .startTs(20L)
                .endTs(21L)
                .status(ExecutionStatus.FAILED)
                .runSequence(2)
                .build());
    PipelineExecutionSummaryEntity rootExecution = PipelineExecutionSummaryEntity.builder()
                                                       .planExecutionId(rootExecutionId)
                                                       .startTs(10L)
                                                       .endTs(11L)
                                                       .status(ExecutionStatus.ABORTED)
                                                       .runSequence(1)
                                                       .build();
    when(pmsExecutionSummaryRepository.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(rootExecutionId))
        .thenReturn(
            PipelineServiceTestHelper.createCloseableIterator(pipelineExecutionSummaryEntities.iterator()).stream());
    when(pmsExecutionSummaryService.fetchFromSecondaryWithProjections(
             accountId, rootExecutionId, PipelineExecutionSummaryEntityProjectionConstants.fieldsForRetryHistory))
        .thenReturn(rootExecution);
    doReturn(Optional.of(PlanExecutionMetadata.builder().build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(accountId, "planExecutionId");

    RetryHistoryResponseDto retryHistory =
        retryExecuteHelper.getRetryHistory(accountId, rootExecutionId, "planExecutionId");
    assertThat(retryHistory.getErrorMessage()).isNull();
    assertThat(retryHistory.getLatestExecutionId()).isEqualTo("uuid2");
    assertThat(retryHistory.getExecutionInfos().size()).isEqualTo(3);
    assertThat(retryHistory.getExecutionInfos().get(0).getRunSequence()).isEqualTo(3);
    assertThat(retryHistory.getExecutionInfos().get(1).getRunSequence()).isEqualTo(2);
    assertThat(retryHistory.getExecutionInfos().get(2).getRunSequence()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetLatestExecutionId() {
    String rootExecutionId = "rootExecutionId";
    String accountIdentifier = "accountId";
    // entities are <=1. Checking error message
    when(pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(accountIdentifier, rootExecutionId))
        .thenReturn(RetryExecutionInfoDTO.builder().planExecutionId(rootExecutionId).build());
    RetryLatestExecutionResponseDto retryLatestExecutionResponse =
        retryExecuteHelper.getRetryLatestExecutionId(accountIdentifier, rootExecutionId);
    assertThat(retryLatestExecutionResponse.getErrorMessage()).isNotNull();

    when(pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(accountIdentifier, rootExecutionId))
        .thenReturn(RetryExecutionInfoDTO.builder()
                        .planExecutionId("uuid1")
                        .startTs(10L)
                        .endTs(11L)
                        .status(ExecutionStatus.FAILED)
                        .build());
    retryLatestExecutionResponse = retryExecuteHelper.getRetryLatestExecutionId(accountIdentifier, rootExecutionId);
    assertThat(retryLatestExecutionResponse.getErrorMessage()).isNull();
    assertThat(retryLatestExecutionResponse.getLatestExecutionId()).isEqualTo("uuid1");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryWithPipelineDeleted() {
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .entityGitDetails(buildEntityGitDetails())
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecId, false);
    doReturn(Optional.empty())
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, true);
    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, null, null);
    assertThat(retryInfo.isResumable()).isFalse();
    assertThat(retryInfo.getErrorMessage())
        .isEqualTo("Pipeline with the given ID: pipeline does not exist or has been deleted");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryWhenNotTheLatestExecution() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .planExecutionId(planExecId)
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("someRootId").build())
            .build();

    doReturn(Optional.of(PipelineEntity.builder().build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, true);
    doReturn(pipelineExecutionSummaryEntity)
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecId, false);

    PipelineExecutionSummaryEntity parentExecution =
        PipelineExecutionSummaryEntity.builder()
            .isLatestExecution(false)
            .endTs(System.currentTimeMillis() - 10 * DAY_IN_MS)
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId("parentExecutionId").build())
            .build();

    doReturn(parentExecution)
        .when(pmsExecutionSummaryService)
        .getFromSecondaryWithProjections(
            accountId, orgId, projectId, "someRootId", false, List.of(PipelineExecutionSummaryKeys.endTs), null);

    RetryExecutionInfoDTO latestRetryExecution =
        RetryExecutionInfoDTO.builder().planExecutionId("rootExecutionId").build();

    doReturn(latestRetryExecution)
        .when(pmsExecutionSummaryService)
        .fetchLatestRetryExecutionInfoDTO(pipelineExecutionSummaryEntity.getAccountId(),
            pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId());

    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, null, null);
    assertThat(retryInfo.isResumable()).isFalse();
    assertThat(retryInfo.getErrorMessage())
        .isEqualTo(
            "This execution is not the latest of all retried execution. You can only retry the latest execution.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryForExecutionsThatHaveUndergonePRB() {
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .rollbackExecutionInfo(RollbackExecutionInfo.builder().rollbackModeExecutionId("something").build())
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecId, false);
    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, null, null);
    assertThat(retryInfo.isResumable()).isFalse();
    assertThat(retryInfo.getErrorMessage())
        .isEqualTo("This execution has undergone Pipeline Rollback, and hence cannot be retried.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryWhenThirtyDaysHavePassed() {
    doReturn(Optional.of(PipelineEntity.builder().build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, true);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - 60 * DAY_IN_MS)
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecId, false);
    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false", null);
    assertThat(retryInfo.isResumable()).isFalse();
    assertThat(retryInfo.getErrorMessage()).isEqualTo("Execution is more than 30 days old. Cannot retry");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryWhenPlanExecutionDoesNotExist() {
    doReturn(Optional.of(PipelineEntity.builder().build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, true);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecId, false);
    doReturn(Optional.empty()).when(planExecutionMetadataService).findByPlanExecutionId(accountId, planExecId);

    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false", null);
    assertThat(retryInfo.isResumable()).isFalse();
    assertThat(retryInfo.getErrorMessage()).isEqualTo("No Plan Execution exists for id plan");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryWhenNoChangeInPipeline() {
    String originalYamlFile = "retry-original1.yaml";
    String originalYaml = readFile(originalYamlFile);

    doReturn(Optional.of(PipelineEntity.builder().yaml(originalYaml).build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, true);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecId, false);
    doReturn(Optional.of(PlanExecutionMetadata.builder().yaml(originalYaml).build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(accountId, planExecId);
    doReturn(
        Optional.of(PlanExecution.builder()
                        .stagesExecutionMetadata(StagesExecutionMetadata.builder().isStagesExecution(false).build())
                        .build()))
        .when(planExecutionService)
        .getWithFieldsIncludedOptional(planExecId, Set.of(PlanExecutionKeys.stagesExecutionMetadata));
    doReturn(originalYaml)
        .when(pipelineTemplateHelper)
        .resolveOnlyPipelineTemplateRefAndMerge(accountId, orgId, projectId, originalYaml, null, "false", "0");

    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false", null);
    assertThat(retryInfo.isResumable()).isTrue();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testValidateRetryForOriginalExecutionOver30DaysOld() {
    doReturn(Optional.of(PlanExecutionMetadata.builder().yaml("yaml").build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(accountId, planExecId);
    doReturn(
        Optional.of(PlanExecution.builder()
                        .stagesExecutionMetadata(StagesExecutionMetadata.builder().isStagesExecution(false).build())
                        .build()))
        .when(planExecutionService)
        .getWithFieldsIncludedOptional(planExecId, Set.of(PlanExecutionKeys.stagesExecutionMetadata));
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .retryExecutionMetadata(RetryExecutionMetadata.builder()
                                             .rootExecutionId("rootExecId")
                                             .parentExecutionId("parentExecId")
                                             .build())
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecId, false);

    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(1705038230L)
                 .endTs(1706593430L)
                 .build())
        .when(pmsExecutionSummaryService)
        .getFromSecondaryWithProjections(
            accountId, orgId, projectId, "rootExecId", false, List.of(PipelineExecutionSummaryKeys.endTs), null);
    assertThatThrownBy(
        () -> retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false", null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "The pipeline execution cannot be retried because the first original execution is more than 30 days old");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryForTriggerExecution() {
    String originalYamlFile = "retry-original1.yaml";
    String originalYaml = readFile(originalYamlFile);

    doReturn(Optional.of(PipelineEntity.builder().yaml(originalYaml).build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, true);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecId, false);
    doReturn(Optional.of(PlanExecutionMetadata.builder().yaml(originalYaml).build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(accountId, planExecId);
    doReturn(originalYaml)
        .when(pipelineTemplateHelper)
        .resolveOnlyPipelineTemplateRefAndMerge(accountId, orgId, projectId, originalYaml, null, "false", "0");

    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false", null);
    assertThat(retryInfo.isResumable()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidateRetryForSelectiveStageExecution() {
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "      description: desc>\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "      description: desc\n";

    String s2StageYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n"
        + "      description: \"desc\"\n";

    doReturn(Optional.of(PipelineEntity.builder().yaml(pipelineYaml).build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, true);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(true)
                 .createdAt(System.currentTimeMillis() - DAY_IN_MS)
                 .entityGitDetails(buildEntityGitDetails())
                 .build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecId, false);
    doReturn(Optional.of(PlanExecutionMetadata.builder().yaml(s2StageYaml).build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(accountId, planExecId);
    doReturn(Optional.of(PlanExecution.builder()
                             .stagesExecutionMetadata(
                                 StagesExecutionMetadata.builder()
                                     .isStagesExecution(true)
                                     .stageIdentifiers(Collections.singletonList("s2"))
                                     .fullPipelineYaml(pipelineYaml)
                                     .stageIdentifierToNameMap(StagesExecutionHelper.getStageIdentifierToNameMap(
                                         pipelineYaml, Collections.singletonList("s2"), HarnessYamlVersion.V0))
                                     .build())
                             .build()))
        .when(planExecutionService)
        .getWithFieldsIncludedOptional(planExecId, Set.of(PlanExecutionKeys.stagesExecutionMetadata));
    doReturn(pipelineYaml)
        .when(pipelineTemplateHelper)
        .resolveOnlyPipelineTemplateRefAndMerge(accountId, orgId, projectId, pipelineYaml, null, "false", "0");

    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, "false", null);
    assertThat(retryInfo.isResumable()).isTrue();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFetchOnlyFailedStagesFromPlanExecutionId() {
    String planExecutionId = generateUuid();
    List<RetryStageInfo> retryStageInfoList =
        List.of(RetryStageInfo.builder().identifier("stage1").status(ExecutionStatus.FAILED).nextId("nextId1").build(),
            RetryStageInfo.builder().identifier("stage2").status(ExecutionStatus.SUCCESS).nextId("nextId1").build(),
            RetryStageInfo.builder().identifier("stage3").status(ExecutionStatus.FAILED).nextId("nextId2").build(),
            RetryStageInfo.builder().identifier("stage4").status(ExecutionStatus.FAILED).nextId("nextId2").build(),
            RetryStageInfo.builder().identifier("stage5").status(ExecutionStatus.SUCCESS).nextId("nextId2").build(),
            RetryStageInfo.builder().identifier("stage6").status(ExecutionStatus.SUCCESS).build(),
            RetryStageInfo.builder().identifier("stage7").status(ExecutionStatus.SUCCESS).build());

    doReturn(retryStageInfoList).when(nodeExecutionService).getStageDetailFromPlanExecutionId(planExecutionId, "0");

    RetryGroup retryGroup = retryExecuteHelper.validateRetryStagesIdentifiersAndGetRetryGroup(
        planExecutionId, List.of("stage1", "stage2"), HarnessYamlVersion.V0);
    List<String> failedStagesResponses =
        retryExecuteHelper.fetchOnlyFailedStages(retryGroup.getInfo(), List.of("stage1", "stage2"));
    assertThat(failedStagesResponses.size()).isEqualTo(1);
    assertThat(failedStagesResponses.get(0)).isEqualTo("stage1");

    assertThatThrownBy(() -> retryExecuteHelper.fetchOnlyFailedStages(retryGroup.getInfo(), List.of("stage1")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Run only failed stages is applicable only for failed parallel group stages");

    RetryGroup retryGroup2 = retryExecuteHelper.validateRetryStagesIdentifiersAndGetRetryGroup(
        planExecutionId, List.of("stage3", "stage4", "stage5"), HarnessYamlVersion.V0);
    failedStagesResponses =
        retryExecuteHelper.fetchOnlyFailedStages(retryGroup2.getInfo(), List.of("stage3", "stage4", "stage5"));
    assertThat(failedStagesResponses.size()).isEqualTo(2);
    assertThat(failedStagesResponses.contains("stage3")).isTrue();
    assertThat(failedStagesResponses.contains("stage4")).isTrue();

    RetryGroup retryGroup3 = retryExecuteHelper.validateRetryStagesIdentifiersAndGetRetryGroup(
        planExecutionId, List.of("stage6", "stage7"), HarnessYamlVersion.V0);
    assertThatThrownBy(
        () -> retryExecuteHelper.fetchOnlyFailedStages(retryGroup3.getInfo(), List.of("stage6", "stage7")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No failed stage found in parallel group");

    assertThatThrownBy(()
                           -> retryExecuteHelper.validateRetryStagesIdentifiersAndGetRetryGroup(
                               planExecutionId, List.of("stage8", "stage7"), HarnessYamlVersion.V0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The execution can not be retried because the retryStagesIdentifier could not be found in any "
            + "stage Groups. Please provide the correct list of retryStagesIdentifier");
  }
  private EntityGitDetails buildEntityGitDetails() {
    return EntityGitDetails.builder().branch(branch).repoName(repoName).filePath(filepath).build();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testShouldShowRetryHistoryForRootExecutionWithNoRetry() {
    String planExecutionId = "planExecutionId";
    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(planExecutionId)
            .retryExecutionMetadata(RetryExecutionMetadata.builder()
                                        .parentExecutionId(planExecutionId)
                                        .rootExecutionId(planExecutionId)
                                        .build())
            .build();
    RetryExecutionInfoDTO retryExecutionInfoDTO =
        RetryExecutionInfoDTO.builder().planExecutionId(planExecutionId).build();
    when(pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(accountId, planExecutionId))
        .thenReturn(retryExecutionInfoDTO);

    boolean showRetryHistory = retryExecuteHelper.shouldShowRetryHistory(executionSummaryEntity);
    assertThat(showRetryHistory).isFalse();
    verify(pmsExecutionSummaryService, times(1)).fetchLatestRetryExecutionInfoDTO(any(), any());

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder().planExecutionId(planExecutionId).build();
    when(pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(accountId, planExecutionId))
        .thenReturn(retryExecutionInfoDTO);

    showRetryHistory = retryExecuteHelper.shouldShowRetryHistory(executionSummaryEntity);
    assertThat(showRetryHistory).isFalse();
    verify(pmsExecutionSummaryService, times(2)).fetchLatestRetryExecutionInfoDTO(any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testShouldShowRetryHistoryForRootExecutionWithRetries() {
    String planExecutionId = "planExecutionId";
    String retryExecution1 = "retryExecution1";
    String retryExecution2 = "retryExecution2";
    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .planExecutionId(planExecutionId)
            .retryExecutionMetadata(RetryExecutionMetadata.builder()
                                        .parentExecutionId(planExecutionId)
                                        .rootExecutionId(planExecutionId)
                                        .build())
            .build();
    PipelineExecutionSummaryEntity retry1ExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(retryExecution1)
            .retryExecutionMetadata(RetryExecutionMetadata.builder()
                                        .parentExecutionId(planExecutionId)
                                        .rootExecutionId(planExecutionId)
                                        .build())
            .build();
    PipelineExecutionSummaryEntity retry2ExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(retryExecution2)
            .retryExecutionMetadata(RetryExecutionMetadata.builder()
                                        .parentExecutionId(retryExecution1)
                                        .rootExecutionId(planExecutionId)
                                        .build())
            .build();

    RetryExecutionInfoDTO retryExecutionInfoDTO =
        RetryExecutionInfoDTO.builder().planExecutionId(retryExecution2).build();
    when(pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(accountId, planExecutionId))
        .thenReturn(retryExecutionInfoDTO);

    // For 1st retry execution
    boolean showRetryHistory = retryExecuteHelper.shouldShowRetryHistory(retry1ExecutionSummaryEntity);
    assertThat(showRetryHistory).isTrue();
    verify(pmsExecutionSummaryService, times(0)).fetchLatestRetryExecutionInfoDTO(any(), any());

    // For 2nd retry execution
    showRetryHistory = retryExecuteHelper.shouldShowRetryHistory(retry2ExecutionSummaryEntity);
    assertThat(showRetryHistory).isTrue();
    verify(pmsExecutionSummaryService, times(0)).fetchLatestRetryExecutionInfoDTO(any(), any());

    // For root execution
    retryExecutionInfoDTO = RetryExecutionInfoDTO.builder().planExecutionId(retryExecution2).build();
    when(pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(accountId, planExecutionId))
        .thenReturn(retryExecutionInfoDTO);
    showRetryHistory = retryExecuteHelper.shouldShowRetryHistory(executionSummaryEntity);
    assertThat(showRetryHistory).isTrue();
    verify(pmsExecutionSummaryService, times(1)).fetchLatestRetryExecutionInfoDTO(any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testIsLatestExecutionWithNoRetry() {
    String planExecutionId = "planExecutionId";
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId(accountId)
                                                                .isLatestExecution(false)
                                                                .planExecutionId(planExecutionId)
                                                                .build();
    RetryExecutionInfoDTO retryExecutionInfoDTO =
        RetryExecutionInfoDTO.builder().planExecutionId(planExecutionId).build();
    when(pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(accountId, planExecutionId))
        .thenReturn(retryExecutionInfoDTO);

    boolean isLatestExecution = retryExecuteHelper.isLatestExecution(executionSummaryEntity);
    assertThat(isLatestExecution).isTrue();
    verify(pmsExecutionSummaryService, times(1)).fetchLatestRetryExecutionInfoDTO(any(), any());

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(accountId)
                                 .isLatestExecution(false)
                                 .planExecutionId(planExecutionId)
                                 .retryExecutionMetadata(RetryExecutionMetadata.builder()
                                                             .parentExecutionId(planExecutionId)
                                                             .rootExecutionId(planExecutionId)
                                                             .build())
                                 .build();
    retryExecutionInfoDTO = RetryExecutionInfoDTO.builder().planExecutionId(planExecutionId).build();
    when(pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(accountId, planExecutionId))
        .thenReturn(retryExecutionInfoDTO);

    isLatestExecution = retryExecuteHelper.isLatestExecution(executionSummaryEntity);
    assertThat(isLatestExecution).isTrue();
    verify(pmsExecutionSummaryService, times(2)).fetchLatestRetryExecutionInfoDTO(any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testIsLatestExecutionWithRetry() {
    String planExecutionId = "planExecutionId";
    String retryExecution1 = "retryExecution1";
    String retryExecution2 = "retryExecution2";

    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().accountId(accountId).planExecutionId(planExecutionId).build();
    PipelineExecutionSummaryEntity retry1ExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .planExecutionId(retryExecution1)
            .retryExecutionMetadata(RetryExecutionMetadata.builder()
                                        .parentExecutionId(planExecutionId)
                                        .rootExecutionId(planExecutionId)
                                        .build())
            .build();
    PipelineExecutionSummaryEntity retry2ExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .accountId(accountId)
            .planExecutionId(retryExecution2)
            .retryExecutionMetadata(RetryExecutionMetadata.builder()
                                        .parentExecutionId(retryExecution1)
                                        .rootExecutionId(planExecutionId)
                                        .build())
            .build();
    RetryExecutionInfoDTO retryExecutionInfoDTO =
        RetryExecutionInfoDTO.builder().planExecutionId(retryExecution2).build();
    when(pmsExecutionSummaryService.fetchLatestRetryExecutionInfoDTO(accountId, planExecutionId))
        .thenReturn(retryExecutionInfoDTO);

    boolean isLatestExecution = retryExecuteHelper.isLatestExecution(executionSummaryEntity);
    assertThat(isLatestExecution).isFalse();
    verify(pmsExecutionSummaryService, times(1)).fetchLatestRetryExecutionInfoDTO(any(), any());

    isLatestExecution = retryExecuteHelper.isLatestExecution(retry1ExecutionSummaryEntity);
    assertThat(isLatestExecution).isFalse();
    verify(pmsExecutionSummaryService, times(2)).fetchLatestRetryExecutionInfoDTO(any(), any());

    isLatestExecution = retryExecuteHelper.isLatestExecution(retry2ExecutionSummaryEntity);
    assertThat(isLatestExecution).isTrue();
    verify(pmsExecutionSummaryService, times(3)).fetchLatestRetryExecutionInfoDTO(any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testValidateRetryFromPrevExecutionId() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountId, PIPE_VALIDATE_RETRY_FROM_PREVIOUS_EXECUTION_ID);
    doReturn(Optional.of(PipelineEntity.builder().build()))
        .when(pipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false, false, false, null, true);
    doReturn(PipelineExecutionSummaryEntity.builder().isLatestExecution(false).build())
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, planExecId, false);
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .isLatestExecution(false)
                 .createdAt(1705038230L)
                 .endTs(1706593430L)
                 .build())
        .when(pmsExecutionSummaryService)
        .getFromSecondaryWithProjections(
            accountId, orgId, projectId, planExecId, false, List.of(PipelineExecutionSummaryKeys.endTs), null);
    assertThatThrownBy(
        () -> retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The pipeline execution cannot be retried because the previous execution is more than 30 days old");
  }

  @Test
  @Owner(developers = NAVNEET_KHANDELWAL)
  @Category(UnitTests.class)
  public void testValidateRetryPreservesBranchFromOriginalExecution() {
    // Setup: Create execution summary with non-default branch
    String originalBranch = "feature/test-branch";
    String filePath = ".harness/pipeline.yaml";

    EntityGitDetails entityGitDetails =
        EntityGitDetails.builder().branch(originalBranch).repoName(repoName).filePath(filePath).build();

    PipelineExecutionSummaryEntity summaryEntity = PipelineExecutionSummaryEntity.builder()
                                                       .accountId(accountId)
                                                       .orgIdentifier(orgId)
                                                       .projectIdentifier(projectId)
                                                       .pipelineIdentifier(pipelineId)
                                                       .planExecutionId(planExecId)
                                                       .entityGitDetails(entityGitDetails)
                                                       .status(ExecutionStatus.FAILED)
                                                       .startTs(System.currentTimeMillis() - 1000000L)
                                                       .endTs(System.currentTimeMillis())
                                                       .build();

    when(executionService.getPipelineExecutionSummaryEntity(accountId, planExecId, false)).thenReturn(summaryEntity);

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml("pipeline:\n  name: test")
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .build();

    when(pipelineService.getPipeline(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), anyBoolean(),
             anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    when(pmsFeatureFlagService.isEnabled(accountId, PIPE_VALIDATE_RETRY_FROM_PREVIOUS_EXECUTION_ID)).thenReturn(false);

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountId)
                              .orgIdentifier(orgId)
                              .projectIdentifier(projectId)
                              .uniqueId(generateUuid())
                              .build();

    when(pmsExecutionSummaryService.getFromSecondaryWithProjections(
             eq(accountId), eq(orgId), eq(projectId), eq(planExecId), anyBoolean(), any(), any()))
        .thenReturn(summaryEntity);

    // Execute: Call validateRetry which should set up Git context
    RetryInfo retryInfo =
        retryExecuteHelper.validateRetry(accountId, orgId, projectId, pipelineId, planExecId, null, scopeInfo);

    // Verify: The method was called and completed successfully
    assertThat(retryInfo).isNotNull();

    // Verify: Git context was set correctly with the branch from original execution
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitEntityInfo).isNotNull();
    assertThat(gitEntityInfo.getBranch()).isEqualTo(originalBranch);
    assertThat(gitEntityInfo.getRepoName()).isEqualTo(repoName);
    assertThat(gitEntityInfo.getFilePath()).isEqualTo(filePath);

    // Verify: getPipelineExecutionSummaryEntity was called to fetch branch info
    verify(executionService).getPipelineExecutionSummaryEntity(accountId, planExecId, false);

    // Verify: getPipeline was called (which uses the Git context set by setupEntityDetails)
    verify(pipelineService)
        .getPipeline(eq(accountId), eq(orgId), eq(projectId), eq(pipelineId), anyBoolean(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean());
  }
}
