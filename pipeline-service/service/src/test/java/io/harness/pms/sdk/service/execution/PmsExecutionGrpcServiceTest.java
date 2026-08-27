/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.service.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.DANIEL;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.pms.contracts.service.ExecutionSummaryResponse;
import io.harness.pms.contracts.service.ExecutionSummaryUpdateRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.beans.ExecutionSummaryUpdateInfo;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.repositories.executions.GraphUpdateInfoRepository;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;

import io.grpc.stub.StreamObserver;
import java.util.Optional;
import lombok.Data;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(PIPELINE)
public class PmsExecutionGrpcServiceTest extends CategoryTest {
  @Mock PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock GraphUpdateInfoRepository graphUpdateInfoRepository;
  @Mock OrchestrationLogPublisher orchestrationLogPublisher;
  @Spy @InjectMocks PmsExecutionGrpcService pmsExecutionGrpcService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doReturn(PipelineExecutionSummaryEntity.builder().accountId("accountId").build())
        .when(pmsExecutionSummaryRepository)
        .getPipelineExecutionSummaryWithProjections(any(), any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdateExecutionSummary() {
    ExecutionSummaryUpdateRequest executionSummaryUpdateRequest = ExecutionSummaryUpdateRequest.newBuilder().build();
    DummyStreamObserver<ExecutionSummaryResponse> responseObserver = new DummyStreamObserver<>();
    pmsExecutionGrpcService.updateExecutionSummary(executionSummaryUpdateRequest, responseObserver);
    verify(pmsExecutionGrpcService, times(1)).updatePipelineInfoJson(any());
    verify(pmsExecutionGrpcService, times(1)).updateStageModuleInfo(any());
    assertThat(responseObserver.executionSummaryResponse).isNotNull();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfoJson() {
    ExecutionSummaryUpdateRequest executionSummaryUpdateRequest =
        ExecutionSummaryUpdateRequest.newBuilder()
            .setModuleName("pms")
            .setPlanExecutionId("planExecutionId")
            .setPipelineModuleInfoJson("{\"moduleInfo\" : {\n"
                + "        \"pms\" : {\n"
                + "            \"__recast\" : \"io.harness.pms.plan.execution.PmsPipelineModuleInfo\",\n"
                + "            \"approvalStageNames\" : [\n"
                + "                \"s1\"\n"
                + "            ],\n"
                + "            \"hasApprovalStage\" : true\n"
                + "        }\n"
                + "    }}")
            .build();
    pmsExecutionGrpcService.updatePipelineInfoJson(executionSummaryUpdateRequest);
    verify(graphUpdateInfoRepository, times(1)).upsert(any(), any());
    verify(orchestrationLogPublisher, times(1)).onPipelineInfoUpdate(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfoJson_1() {
    ExecutionSummaryUpdateRequest executionSummaryUpdateRequest =
        ExecutionSummaryUpdateRequest.newBuilder()
            .setModuleName("pms")
            .setPlanExecutionId("planExecutionId")
            .setPipelineModuleInfoJson("{\"moduleInfo\" : {\n"
                + "        \"pms\" : {\n"
                + "            \"__recast\" : \"io.harness.pms.plan.execution.PmsPipelineModuleInfo\",\n"
                + "            \"approvalStageNames\" : [\n"
                + "                \"s1\"\n"
                + "            ],\n"
                + "            \"hasApprovalStage\" : true\n"
                + "        }\n"
                + "    }}")
            .build();
    Optional<GraphUpdateInfo> graphUpdateInfoOptional =
        Optional.ofNullable(GraphUpdateInfo.builder()
                                .planExecutionId("planExecutionId")
                                .executionSummaryUpdateInfo(
                                    ExecutionSummaryUpdateInfo.builder().stepCategory(StepCategory.PIPELINE).build())
                                .build());
    pmsExecutionGrpcService.updatePipelineInfoJson(executionSummaryUpdateRequest);
    verify(graphUpdateInfoRepository, times(1)).upsert(any(), any());
    verify(orchestrationLogPublisher, times(1)).onPipelineInfoUpdate(any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdateStageModuleInfo() {
    ExecutionSummaryUpdateRequest executionSummaryUpdateRequest =
        ExecutionSummaryUpdateRequest.newBuilder()
            .setModuleName("pms")
            .setNodeUuid("123456")
            .setPlanExecutionId("planExecutionId")
            .setNodeModuleInfoJson("{\n"
                + "    \"lomivDmmQN6EWfXfUlt_Qw\" : {\n"
                + "        \"nodeType\" : \"Approval\",\n"
                + "        \"nodeGroup\" : \"STAGE\",\n"
                + "        \"nodeIdentifier\" : \"s1\",\n"
                + "        \"name\" : \"s1\",\n"
                + "        \"nodeUuid\" : \"lomivDmmQN6EWfXfUlt_Qw\",\n"
                + "        \"status\" : \"ABORTED\",\n"
                + "        \"module\" : \"pms\",\n"
                + "        \"moduleInfo\" : {\n"
                + "            \"pms\" : {\n"
                + "                \"__recast\" : "
                + "\"io.harness.pms.plan.execution.PmsExecutionServiceInfoProvider$PmsNoopModuleInfo\"\n"
                + "            }\n"
                + "        }\n"
                + "}\n"
                + "}")
            .build();
    pmsExecutionGrpcService.updateStageModuleInfo(executionSummaryUpdateRequest);
    verify(graphUpdateInfoRepository, times(1)).upsert(any(), any());
    verify(orchestrationLogPublisher, times(1)).onStageInfoUpdate(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testUpdateStageModuleInfo_1() {
    ExecutionSummaryUpdateRequest executionSummaryUpdateRequest =
        ExecutionSummaryUpdateRequest.newBuilder()
            .setModuleName("pms")
            .setNodeUuid("123456")
            .setPlanExecutionId("planExecutionId")
            .setNodeModuleInfoJson("{\n"
                + "    \"lomivDmmQN6EWfXfUlt_Qw\" : {\n"
                + "        \"nodeType\" : \"Approval\",\n"
                + "        \"nodeGroup\" : \"STAGE\",\n"
                + "        \"nodeIdentifier\" : \"s1\",\n"
                + "        \"name\" : \"s1\",\n"
                + "        \"nodeUuid\" : \"lomivDmmQN6EWfXfUlt_Qw\",\n"
                + "        \"status\" : \"ABORTED\",\n"
                + "        \"module\" : \"pms\",\n"
                + "        \"moduleInfo\" : {\n"
                + "            \"pms\" : {\n"
                + "                \"__recast\" : "
                + "\"io.harness.pms.plan.execution.PmsExecutionServiceInfoProvider$PmsNoopModuleInfo\"\n"
                + "            }\n"
                + "        }\n"
                + "}\n"
                + "}")
            .build();
    Optional<GraphUpdateInfo> graphUpdateInfoOptional = Optional.ofNullable(
        GraphUpdateInfo.builder()
            .planExecutionId("planExecutionId")
            .executionSummaryUpdateInfo(ExecutionSummaryUpdateInfo.builder().stepCategory(StepCategory.STAGE).build())
            .nodeExecutionId("nodeExecutionId")
            .build());
    pmsExecutionGrpcService.updateStageModuleInfo(executionSummaryUpdateRequest);
    verify(graphUpdateInfoRepository, times(1)).upsert(any(), any());
    verify(orchestrationLogPublisher, times(1)).onStageInfoUpdate(any(), any());
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfoJson_V1PreservesNestedRecasterTypeMetadata() {
    ExecutionSummaryUpdateRequest request =
        ExecutionSummaryUpdateRequest.newBuilder()
            .setModuleName("ci")
            .setVersion("1")
            .setPlanExecutionId("planExecutionId")
            .setPipelineModuleInfoJson("{"
                + "\"__recast\":\"io.harness.ci.plan.creator.execution.CIPipelineModuleInfo\","
                + "\"ciPipelineStageModuleInfo\":{"
                + "\"__recast\":\"io.harness.ci.plan.creator.execution.CIPipelineStageModuleInfo\","
                + "\"stageExecutionId\":\"stageExecutionId\","
                + "\"status\":\"SUCCEEDED\""
                + "}"
                + "}")
            .build();

    pmsExecutionGrpcService.updatePipelineInfoJson(request);

    ArgumentCaptor<org.springframework.data.mongodb.core.query.Update> updateCaptor =
        ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
    verify(graphUpdateInfoRepository, times(1)).upsert(any(), updateCaptor.capture());
    verify(orchestrationLogPublisher, times(1)).onPipelineInfoUpdate(any());

    Document setDoc = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
    assertThat(setDoc).containsEntry("executionSummaryUpdateInfo.moduleInfo.ci.ciPipelineStageModuleInfo.__recast",
        "io.harness.ci.plan.creator.execution.CIPipelineStageModuleInfo");
  }

  @Data
  private static class DummyStreamObserver<T> implements StreamObserver<T> {
    ExecutionSummaryResponse executionSummaryResponse;
    @Override
    public void onNext(Object o) {
      executionSummaryResponse = (ExecutionSummaryResponse) o;
    }
    @Override
    public void onError(Throwable throwable) {}
    @Override
    public void onCompleted() {}
  }
}
