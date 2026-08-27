/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.rule.OwnerRule.RISHIKESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.executions.GraphUpdateInfoRepository;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionModuleInfoUpdateEventHandlerTest {
  @Mock GraphUpdateInfoRepository graphUpdateInfoRepository;
  @InjectMocks PlanExecutionModuleInfoUpdateEventHandler planExecutionModuleInfoUpdateEventHandler;
  private String planExecutionId = "planExecutionId";
  private String nodeExecutionId = "nodeExecutionId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testHandlePipelineInfoUpdate() throws IOException {
    Optional<GraphUpdateInfo> pipelineGraphUpdateInfoOptional = getPipelineGraphUpdateInfo();
    when(graphUpdateInfoRepository.findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategory(
             any(), eq(StepCategory.PIPELINE)))
        .thenReturn(pipelineGraphUpdateInfoOptional);
    Update update = new Update();
    planExecutionModuleInfoUpdateEventHandler.handlePipelineInfoUpdate(planExecutionId, update);
    Update expectedUpdate = new Update();
    expectedUpdate.addToSet("moduleInfo.cd.envIdentifiers").each(List.of("devEnv2", "devenv"));
    expectedUpdate.addToSet("moduleInfo.cd.environmentTypes").each(List.of("PreProduction"));
    expectedUpdate.addToSet("moduleInfo.cd.infrastructureTypes").each(List.of("KubernetesDirect"));
    expectedUpdate.addToSet("moduleInfo.cd.infrastructureIdentifiers").each(List.of("k8Infra2", "k8sinfra"));
    expectedUpdate.addToSet("moduleInfo.cd.infrastructureNames").each(List.of("k8Infra2", "k8sinfra"));
    assertEquals(update, expectedUpdate);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testHandleStageInfoUpdate() throws IOException {
    Optional<GraphUpdateInfo> stageGraphUpdateInfoOptional = getStageGraphUpdateInfo();
    when(graphUpdateInfoRepository.findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategoryAndNodeExecutionId(
             any(), eq(StepCategory.STAGE), any()))
        .thenReturn(stageGraphUpdateInfoOptional);
    Update update = new Update();
    planExecutionModuleInfoUpdateEventHandler.handleStageInfoUpdate(planExecutionId, nodeExecutionId, update);
    Update expectedUpdate = new Update();
    expectedUpdate.set("layoutNodeMap.stageUuid.moduleInfo.cd.infraExecutionSummary.identifier", "devenv");
    expectedUpdate.set("layoutNodeMap.stageUuid.moduleInfo.cd.infraExecutionSummary.name", "devenv");
    expectedUpdate.set("layoutNodeMap.stageUuid.moduleInfo.cd.infraExecutionSummary.type", "PreProduction");
    expectedUpdate.set(
        "layoutNodeMap.stageUuid.moduleInfo.cd.infraExecutionSummary.infrastructureIdentifier", "k8sinfra");
    expectedUpdate.set("layoutNodeMap.stageUuid.moduleInfo.cd.infraExecutionSummary.infrastructureName", "k8sinfra");
    assertEquals(update, expectedUpdate);
  }

  private Optional<GraphUpdateInfo> getPipelineGraphUpdateInfo() throws IOException {
    String pipelineGraphUpdateInfoYaml = "planExecutionId: planExecutionId\n"
        + "executionSummaryUpdateInfo:\n"
        + "  stepCategory: PIPELINE\n"
        + "  moduleInfo:\n"
        + "    cd:\n"
        + "      envIdentifiers:\n"
        + "        - devEnv2\n"
        + "        - devenv\n"
        + "      environmentTypes:\n"
        + "        - PreProduction\n"
        + "      infrastructureTypes:\n"
        + "        - KubernetesDirect\n"
        + "      infrastructureIdentifiers:\n"
        + "        - k8Infra2\n"
        + "        - k8sinfra\n"
        + "      infrastructureNames:\n"
        + "        - k8Infra2\n"
        + "        - k8sinfra\n";
    GraphUpdateInfo pipelineGraphUpdateInfo = YamlUtils.read(pipelineGraphUpdateInfoYaml, GraphUpdateInfo.class);
    return Optional.of(pipelineGraphUpdateInfo);
  }

  private Optional<GraphUpdateInfo> getStageGraphUpdateInfo() throws IOException {
    String stageGraphUpdateInfoYaml = "planExecutionId: planExecutionId\n"
        + "nodeExecutionId: nodeExecutionId\n"
        + "executionSummaryUpdateInfo:\n"
        + "  stageUuid: stageUuid\n"
        + "  stepCategory: STAGE\n"
        + "  moduleInfo:\n"
        + "    cd:\n"
        + "      infraExecutionSummary:\n"
        + "        identifier: devenv\n"
        + "        name: devenv\n"
        + "        type: PreProduction\n"
        + "        infrastructureIdentifier: k8sinfra\n"
        + "        infrastructureName: k8sinfra\n";
    GraphUpdateInfo stageGraphUpdateInfo = YamlUtils.read(stageGraphUpdateInfoYaml, GraphUpdateInfo.class);
    return Optional.of(stageGraphUpdateInfo);
  }
}
