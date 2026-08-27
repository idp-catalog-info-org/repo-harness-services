/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SOUMYO_PURKAYASTHA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionFunctorTest extends CategoryTest {
  @Mock private PMSExecutionService pmsExecutionService;
  @Mock PipelineExpressionHelper pipelineExpressionHelper;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @InjectMocks private PipelineExecutionFunctor triggeredByFunctor;

  String sampleYaml = "pipeline:\n"
      + "  identifier: \"trialselective\"\n"
      + "  name: \"trialselective\"\n"
      + "  projectIdentifier: \"test\"\n"
      + "  orgIdentifier: \"default\"\n"
      + "  tags: {}\n"
      + "  stages:\n"
      + "  - stage:\n"
      + "      identifier: \"Test1\"\n"
      + "      type: \"Custom\"\n"
      + "      name: \"Test1\"\n"
      + "      description: \"\"\n"
      + "      spec:\n"
      + "        execution:\n"
      + "          steps:\n"
      + "          - step:\n"
      + "              identifier: \"Wait_1\"\n"
      + "              type: \"Wait\"\n"
      + "              name: \"Wait_1\"\n"
      + "              spec:\n"
      + "                duration: \"1m\"\n"
      + "          - step:\n"
      + "              identifier: \"ShellScript_1\"\n"
      + "              type: \"ShellScript\"\n"
      + "              name: \"ShellScript_1\"\n"
      + "              spec:\n"
      + "                shell: \"Bash\"\n"
      + "                onDelegate: true\n"
      + "                source:\n"
      + "                  type: \"Inline\"\n"
      + "                  spec:\n"
      + "                    script: \"echo \\\"hi\\\"\\necho <+pipeline.pipeline.triggeredBy.email>\\n\\\n"
      + "                      \\necho <+pipeline.selectedStages>\\n\\necho <+inputSet>\"\n"
      + "                environmentVariables: []\n"
      + "                outputVariables:\n"
      + "                - name: \"selectedStages\"\n"
      + "                  type: \"String\"\n"
      + "                  value: \"<+pipeline.selectedStages>\"\n"
      + "                delegateSelectors:\n"
      + "                - \"localdelegate\"\n"
      + "              timeout: \"10m\"\n"
      + "              failureStrategies: []\n"
      + "      tags: {}\n"
      + "  - parallel:\n"
      + "    - stage:\n"
      + "        identifier: \"test2\"\n"
      + "        type: \"Custom\"\n"
      + "        name: \"test2\"\n"
      + "        description: \"\"\n"
      + "        spec:\n"
      + "          execution:\n"
      + "            steps:\n"
      + "            - step:\n"
      + "                identifier: \"Wait_1\"\n"
      + "                type: \"Wait\"\n"
      + "                name: \"Wait_1\"\n"
      + "                spec:\n"
      + "                  duration: \"1m\"\n"
      + "        tags: {}\n"
      + "    - stage:\n"
      + "        identifier: \"test3\"\n"
      + "        type: \"Custom\"\n"
      + "        name: \"test3\"\n"
      + "        description: \"\"\n"
      + "        spec:\n"
      + "          execution:\n"
      + "            steps:\n"
      + "            - step:\n"
      + "                identifier: \"Wait_1_3\"\n"
      + "                type: \"Wait\"\n"
      + "                name: \"Wait_1_3\"\n"
      + "                spec:\n"
      + "                  duration: \"1m\"\n"
      + "        tags: {}\n"
      + "  - stage:\n"
      + "      identifier: \"Test4\"\n"
      + "      type: \"Custom\"\n"
      + "      name: \"Test4\"\n"
      + "      description: \"\"\n"
      + "      spec:\n"
      + "        execution:\n"
      + "          steps:\n"
      + "          - step:\n"
      + "              identifier: \"Wait_1_4\"\n"
      + "              type: \"Wait\"\n"
      + "              name: \"Wait_1_4\"\n"
      + "              spec:\n"
      + "                duration: \"10m\"\n"
      + "      tags: {}\n"
      + "      strategy:\n"
      + "        parallelism: 2\n"
      + "  allowStageExecutions: true\n";

  // V1 parallel stages nested under `parallel.stages`, flat stage objects keyed by `id`.
  String v1ParallelStagesYaml = "pipeline:\n"
      + "  stages:\n"
      + "    - name: parallelStages\n"
      + "      parallel:\n"
      + "        stages:\n"
      + "          - name: custom\n"
      + "            id: st1\n"
      + "          - name: custom2\n"
      + "            id: st2\n"
      + "    - name: stage1\n"
      + "      id: stage1_1\n";

  // V1 parallel stages as a bare array under `parallel`, without a `stages` key.
  String v1ParallelStagesAsArrayYaml = "pipeline:\n"
      + "  stages:\n"
      + "    - id: stage_1\n"
      + "      name: stage_1\n"
      + "    - parallel:\n"
      + "        - id: stage_1_parallel\n"
      + "          name: stage_1_parallel\n"
      + "        - id: stage_2_parallel\n"
      + "          name: stage_2_parallel\n";

  Ambiance ambiance = Ambiance.newBuilder()
                          .putSetupAbstractions("accountId", "accountId")
                          .putSetupAbstractions("projectIdentifier", "projectId")
                          .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                          .build();
  Ambiance ambiance2 =
      Ambiance.newBuilder()
          .putSetupAbstractions("accountId", "accountId")
          .putSetupAbstractions("projectIdentifier", "projectId")
          .putSetupAbstractions("orgIdentifier", "orgIdentifier")
          .setMetadata(ExecutionMetadata.newBuilder().setOriginalPlanExecutionIdForRollbackMode("executionId").build())
          .build();

  Ambiance ambianceV1 =
      Ambiance.newBuilder()
          .putSetupAbstractions("accountId", "accountId")
          .putSetupAbstractions("projectIdentifier", "projectId")
          .putSetupAbstractions("orgIdentifier", "orgIdentifier")
          .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
          .build();

  String executionUrl = "http:127.0.0.1:8080/account/dummyAccount/cd/orgs/dummyOrg/projects/dummyProject/pipelines/"
      + "dummyPipeline/executions/dummyPlanExecutionId/pipeline";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testBind() {
    on(triggeredByFunctor).set("ambiance", ambiance);
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(generateUuid())
            .allowStagesExecution(false)
            .runSequence(32)
            .storeType(StoreType.REMOTE)
            .entityGitDetails(EntityGitDetails.builder().branch("main").repoName("test").build())
            .executionMode(ExecutionMode.NORMAL)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.WEBHOOK)
                                      .setTriggeredBy(TriggeredBy.newBuilder()
                                                          .setIdentifier("system")
                                                          .setTriggerIdentifier("triggerIdentifier")
                                                          .build())
                                      .build())
            .name("test pipeline")
            .pipelineIdentifier("test_pipeline")
            .tags(List.of(NGTag.builder().key("k1").value("v1").build()))
            .build();

    Optional<PlanExecutionMetadata> planExecutionMetadataOptional =
        Optional.of(PlanExecutionMetadata.builder().planExecutionId("123234345").yaml(sampleYaml).build());

    doReturn(planExecutionMetadataOptional)
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());

    doReturn(pipelineExecutionSummaryEntity).when(pmsExecutionService).getPipelineExecutionSummaryEntity(any(), any());

    Map<String, Object> response = (Map<String, Object>) triggeredByFunctor.bind();
    assertEquals(response.get("name"), "test pipeline");
    assertEquals(response.get("identifier"), "test_pipeline");
    assertEquals(response.get("tags"), List.of(NGTag.builder().key("k1").value("v1").build()));
    assertEquals(response.get("triggerType"), TriggerType.WEBHOOK.toString());
    Map<String, String> triggeredByMap = (Map<String, String>) response.get("triggeredBy");
    assertNull(triggeredByMap.get("email"));
    assertEquals(triggeredByMap.get("name"), "system");
    assertEquals(triggeredByMap.get("triggerIdentifier"), "triggerIdentifier");
    assertEquals(response.get("resumedExecutionId"),
        pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId());
    assertThat(response.get("executionMode")).isEqualTo(pipelineExecutionSummaryEntity.getExecutionMode().toString());
    assertEquals(response.get("storeType"), StoreType.REMOTE);
    assertEquals(response.get("branch"), "main");
    assertEquals(response.get("repo"), "test");
    pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .executionMode(ExecutionMode.NORMAL)
            .allowStagesExecution(false)
            .planExecutionId(generateUuid())
            .retryExecutionMetadata(RetryExecutionMetadata.builder().rootExecutionId(generateUuid()).build())
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder()
                                                          .setIdentifier("Admin")
                                                          .putExtraInfo("email", "admin@harness.io")
                                                          .build())
                                      .build())
            .build();

    doReturn(executionUrl).when(pipelineExpressionHelper).generateUrl(ambiance, null);
    doReturn(pipelineExecutionSummaryEntity).when(pmsExecutionService).getPipelineExecutionSummaryEntity(any(), any());

    response = (Map<String, Object>) triggeredByFunctor.bind();
    assertEquals(response.get("resumedExecutionId"),
        pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId());
    assertThat(response.get("executionMode")).isEqualTo(pipelineExecutionSummaryEntity.getExecutionMode().toString());
    assertEquals(response.get("triggerType"), TriggerType.MANUAL.toString());
    triggeredByMap = (Map<String, String>) response.get("triggeredBy");
    assertEquals(triggeredByMap.get("email"), "admin@harness.io");
    assertEquals(triggeredByMap.get("name"), "Admin");
    assertNull(triggeredByMap.get("triggerIdentifier"));
    Map<String, String> executionMap = (Map<String, String>) response.get("execution");
    assertEquals(executionMap.size(), 1);
    assertEquals(executionMap.get("url"), executionUrl);

    ArrayList<String> selectedStages = (ArrayList<String>) response.get("selectedStages");
    assertEquals(selectedStages.size(), 4);
    assertEquals(selectedStages.get(0), "Test1");

    assertEquals(response.get("sequenceId"), pipelineExecutionSummaryEntity.getRunSequence());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testBindWithOriginalExecutionId() {
    on(triggeredByFunctor).set("ambiance", ambiance2);
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(generateUuid())
            .allowStagesExecution(false)
            .runSequence(32)
            .storeType(StoreType.REMOTE)
            .entityGitDetails(EntityGitDetails.builder().branch("main").repoName("test").build())
            .executionMode(ExecutionMode.PIPELINE_ROLLBACK)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.WEBHOOK)
                                      .setTriggeredBy(TriggeredBy.newBuilder()
                                                          .setIdentifier("system")
                                                          .setTriggerIdentifier("triggerIdentifier")
                                                          .build())
                                      .build())
            .name("test pipeline")
            .pipelineIdentifier("test_pipeline")
            .tags(List.of(NGTag.builder().key("k1").value("v1").build()))
            .status(ExecutionStatus.RUNNING)
            .startTs(1740736014L)
            .build();

    Optional<PlanExecutionMetadata> planExecutionMetadataOptional =
        Optional.of(PlanExecutionMetadata.builder().planExecutionId("123234345").yaml(sampleYaml).build());

    doReturn(planExecutionMetadataOptional)
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambiance2), ambiance2.getPlanExecutionId());

    doReturn(pipelineExecutionSummaryEntity).when(pmsExecutionService).getPipelineExecutionSummaryEntity(any(), any());

    Map<String, Object> response = (Map<String, Object>) triggeredByFunctor.bind();
    assertEquals(response.get("name"), "test pipeline");
    assertEquals(response.get("identifier"), "test_pipeline");
    assertEquals(response.get("tags"), List.of(NGTag.builder().key("k1").value("v1").build()));
    assertEquals(response.get("triggerType"), TriggerType.WEBHOOK.toString());
    Map<String, String> triggeredByMap = (Map<String, String>) response.get("triggeredBy");
    assertNull(triggeredByMap.get("email"));
    assertEquals(triggeredByMap.get("name"), "system");
    assertEquals(triggeredByMap.get("triggerIdentifier"), "triggerIdentifier");
    assertEquals(response.get("resumedExecutionId"),
        pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId());
    assertThat(response.get("executionMode")).isEqualTo(pipelineExecutionSummaryEntity.getExecutionMode().toString());
    assertEquals(response.get("storeType"), StoreType.REMOTE);
    assertEquals(response.get("branch"), "main");
    assertEquals(response.get("repo"), "test");

    // Check original execution details
    Map<String, Object> result = (Map<String, Object>) response.get("originalExecution");
    assertEquals(result.get("executionId"), ambiance2.getMetadata().getOriginalPlanExecutionIdForRollbackMode());
    assertThat(result).isNotNull();
    assertEquals("test_pipeline", result.get("identifier"));
    assertNull(((Map<String, Object>) result.get("execution")).get("url"));
    assertEquals(StoreType.REMOTE, result.get("storeType"));
    assertNull(result.get("executionUrl"));
    assertEquals(pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId(),
        result.get("resumedExecutionId"));
    assertEquals("test", result.get("repo"));
    assertEquals("PIPELINE_ROLLBACK", result.get("executionMode"));
    assertEquals(1740736014L, result.get("startTs"));
    assertEquals(32, result.get("sequenceId"));
    assertEquals("main", result.get("branch"));

    // Tags Assertion
    List<NGTag> tags = (List<NGTag>) result.get("tags");
    assertThat(tags).isNotNull();
    assertEquals(1, tags.size());
    assertEquals("k1", tags.get(0).getKey());
    assertEquals("v1", tags.get(0).getValue());

    // Selected Stages Assertion
    List<String> selectedStages = (List<String>) result.get("selectedStages");
    assertThat(selectedStages).isNotNull();
    assertEquals(4, selectedStages.size());
    assertThat(selectedStages).isNotNull().hasSize(4).containsExactlyInAnyOrder("Test1", "test2", "test3", "Test4");

    assertEquals("test pipeline", result.get("name"));
    assertThat(result.get("endTs")).isNull();
    assertEquals("WEBHOOK", result.get("triggerType"));
    assertEquals(ExecutionStatus.RUNNING, result.get("status"));

    // Triggered By Assertion
    Map<String, String> triggeredBy = (Map<String, String>) result.get("triggeredBy");
    assertThat(triggeredBy).isNotNull();
    assertEquals("system", triggeredBy.get("name"));
    assertEquals("triggerIdentifier", triggeredBy.get("triggerIdentifier"));
    assertThat(triggeredBy.get("triggerDisplayName")).isNull();
    assertThat(triggeredBy.get("email")).isNull();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testBindWithParentPipeline() {
    Ambiance ambianceWithParent = Ambiance.newBuilder()
                                      .putSetupAbstractions("accountId", "accountId")
                                      .putSetupAbstractions("projectIdentifier", "projectId")
                                      .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                                      .setMetadata(ExecutionMetadata.newBuilder()
                                                       .setPipelineStageInfo(PipelineStageInfo.newBuilder()
                                                                                 .setHasParentPipeline(true)
                                                                                 .setIdentifier("parent_pipeline")
                                                                                 .setPipelineName("Parent Pipeline")
                                                                                 .setExecutionId("parentExecId123")
                                                                                 .setStageNodeId("stageNode1")
                                                                                 .setProjectId("parentProjectId")
                                                                                 .setOrgId("parentOrgId")
                                                                                 .setRunSequence(5)
                                                                                 .build())
                                                       .build())
                                      .build();
    on(triggeredByFunctor).set("ambiance", ambianceWithParent);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(generateUuid())
            .allowStagesExecution(false)
            .runSequence(10)
            .executionMode(ExecutionMode.NORMAL)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("Admin").build())
                                      .build())
            .name("child pipeline")
            .pipelineIdentifier("child_pipeline")
            .build();

    Optional<PlanExecutionMetadata> planExecutionMetadataOptional =
        Optional.of(PlanExecutionMetadata.builder().planExecutionId("123234345").yaml(sampleYaml).build());

    doReturn(planExecutionMetadataOptional)
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambianceWithParent), ambianceWithParent.getPlanExecutionId());
    doReturn(pipelineExecutionSummaryEntity).when(pmsExecutionService).getPipelineExecutionSummaryEntity(any(), any());

    Map<String, Object> response = (Map<String, Object>) triggeredByFunctor.bind();

    // Verify parent pipeline expressions are present
    Map<String, Object> parentPipelineMap = (Map<String, Object>) response.get("parentPipeline");
    assertThat(parentPipelineMap).isNotNull();
    assertEquals("parent_pipeline", parentPipelineMap.get("identifier"));
    assertEquals("Parent Pipeline", parentPipelineMap.get("name"));
    assertEquals("parentExecId123", parentPipelineMap.get("executionId"));
    assertEquals("stageNode1", parentPipelineMap.get("stageNodeId"));
    assertEquals("parentProjectId", parentPipelineMap.get("projectId"));
    assertEquals("parentOrgId", parentPipelineMap.get("orgId"));
    assertEquals(5, parentPipelineMap.get("runSequence"));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testBindWithoutParentPipeline() {
    on(triggeredByFunctor).set("ambiance", ambiance);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(generateUuid())
            .allowStagesExecution(false)
            .runSequence(10)
            .executionMode(ExecutionMode.NORMAL)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("Admin").build())
                                      .build())
            .name("standalone pipeline")
            .pipelineIdentifier("standalone_pipeline")
            .build();

    Optional<PlanExecutionMetadata> planExecutionMetadataOptional =
        Optional.of(PlanExecutionMetadata.builder().planExecutionId("123234345").yaml(sampleYaml).build());

    doReturn(planExecutionMetadataOptional)
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
    doReturn(pipelineExecutionSummaryEntity).when(pmsExecutionService).getPipelineExecutionSummaryEntity(any(), any());

    Map<String, Object> response = (Map<String, Object>) triggeredByFunctor.bind();

    // parentPipeline should not be present when not triggered via chaining
    assertNull(response.get("parentPipeline"));
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testBindWithV1ParallelStages() {
    on(triggeredByFunctor).set("ambiance", ambianceV1);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(generateUuid())
            .allowStagesExecution(false)
            .runSequence(42)
            .executionMode(ExecutionMode.NORMAL)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("Admin").build())
                                      .build())
            .name("v1 parallel pipeline")
            .pipelineIdentifier("v1_parallel_pipeline")
            .build();

    Optional<PlanExecutionMetadata> planExecutionMetadataOptional =
        Optional.of(PlanExecutionMetadata.builder().planExecutionId("123234345").yaml(v1ParallelStagesYaml).build());

    doReturn(planExecutionMetadataOptional)
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambianceV1), ambianceV1.getPlanExecutionId());
    doReturn(pipelineExecutionSummaryEntity).when(pmsExecutionService).getPipelineExecutionSummaryEntity(any(), any());

    Map<String, Object> response = (Map<String, Object>) triggeredByFunctor.bind();

    // sequenceId must resolve even though selectedStages spans a V1 parallel block (PIPE-36526).
    assertEquals(response.get("sequenceId"), pipelineExecutionSummaryEntity.getRunSequence());
    List<String> selectedStages = (List<String>) response.get("selectedStages");
    assertThat(selectedStages).containsExactlyInAnyOrder("st1", "st2", "stage1_1");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void testBindWithV1ParallelStagesAsArray() {
    on(triggeredByFunctor).set("ambiance", ambianceV1);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .planExecutionId(generateUuid())
            .allowStagesExecution(false)
            .runSequence(43)
            .executionMode(ExecutionMode.NORMAL)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("Admin").build())
                                      .build())
            .name("v1 parallel array pipeline")
            .pipelineIdentifier("v1_parallel_array_pipeline")
            .build();

    Optional<PlanExecutionMetadata> planExecutionMetadataOptional = Optional.of(
        PlanExecutionMetadata.builder().planExecutionId("123234345").yaml(v1ParallelStagesAsArrayYaml).build());

    doReturn(planExecutionMetadataOptional)
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambianceV1), ambianceV1.getPlanExecutionId());
    doReturn(pipelineExecutionSummaryEntity).when(pmsExecutionService).getPipelineExecutionSummaryEntity(any(), any());

    Map<String, Object> response = (Map<String, Object>) triggeredByFunctor.bind();

    assertEquals(response.get("sequenceId"), pipelineExecutionSummaryEntity.getRunSequence());
    List<String> selectedStages = (List<String>) response.get("selectedStages");
    assertThat(selectedStages).containsExactlyInAnyOrder("stage_1", "stage_1_parallel", "stage_2_parallel");
  }
}
