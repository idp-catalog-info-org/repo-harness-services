/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHALINI;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceApplication;
import io.harness.PipelineServiceTestBase;
import io.harness.PipelineServiceTestHelper;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.encoding.EncodingUtils;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.expressions.constants.OrchestrationConstants;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.secrets.ExpressionsObserverFactory;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.EngineJexlContext;
import io.harness.expression.VariableResolverTracker;
import io.harness.expression.common.ExpressionMode;
import io.harness.expression.field.dummy.DummyOrchestrationField;
import io.harness.filestore.remote.FileStoreClient;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.expression.ExpressionResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.expressions.functors.FileStoreFunctorV2;
import io.harness.pms.expressions.functors.RemoteExpressionFunctor;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSExpressionEvaluatorTest extends PipelineServiceTestBase {
  @Mock private PlanExecutionService planExecutionService;

  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsOutcomeService pmsOutcomeService;
  @Mock PmsSdkInstanceService pmsSdkInstanceService;
  @Mock RemoteExpressionFunctor remoteExpressionFunctor;
  FileStoreFunctorV2 fileStoreFunctorV2;
  @Mock FileStoreClient fileStoreClient;
  @Mock PlanExpansionService planExpansionService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock ExpressionsObserverFactory expressionsObserverFactory;
  @Mock PipelineSettingsService pipelineSettingsService;
  @Mock PipelineRetentionService pipelineRetentionService;

  private final String planExecutionId = generateUuid();
  NodeExecution nodeExecution1;
  PlanNode planNode1;

  NodeExecution nodeExecution2;
  PlanNode planNode2;

  NodeExecution nodeExecution3;
  PlanNode planNode3;

  NodeExecution nodeExecution4;
  PlanNode planNode4;

  NodeExecution nodeExecution5;
  PlanNode planNode5;

  @Before
  public void setup() {
    String nodeExecution1Id = generateUuid();
    String nodeExecution2Id = generateUuid();
    String nodeExecution3Id = generateUuid();
    String nodeExecution4Id = generateUuid();
    String nodeExecution5Id = generateUuid();

    Ambiance.Builder ambianceBuilder = Ambiance.newBuilder().setPlanExecutionId(planExecutionId);
    planNode1 = preparePlanNode(false, "pipeline", "pipelineValue", "PIPELINE");

    nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecution1Id)
            .identifier("pipeline")
            .group(StepOutcomeGroup.PIPELINE.name())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1)).build())
            .nodeType(NodeType.PLAN_NODE.name())
            .resolvedStepParameters(prepareStepParameters("pipelineResolvedValue"))
            .build();

    planNode2 = preparePlanNode(false, "stages", "stagesValue", null);
    nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecution2Id)
            .identifier("stages")
            .group(StepOutcomeGroup.STAGES.name())
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2Id, planNode2))
                          .build())
            .resolvedStepParameters(prepareStepParameters("stagesResolvedValue"))
            .nodeType(NodeType.PLAN_NODE.name())
            .parentId(nodeExecution1Id)
            .build();

    planNode3 = preparePlanNode(false, "stage", "stageValue", "STAGE");
    nodeExecution3 =
        NodeExecution.builder()
            .identifier("stage")
            .group(StepOutcomeGroup.STAGE.name())
            .uuid(nodeExecution3Id)
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2Id, planNode2))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, planNode3))
                          .build())
            .resolvedStepParameters(prepareStepParameters("stageResolvedValue"))
            .nodeType(NodeType.PLAN_NODE.name())
            .parentId(nodeExecution2Id)
            .build();

    planNode4 = preparePlanNode(false, "d", "di1", null);
    nodeExecution4 =
        NodeExecution.builder()
            .uuid(nodeExecution4Id)
            .identifier("d")
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2Id, planNode2))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, planNode3))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution4Id, planNode4))
                          .build())
            .resolvedStepParameters(prepareStepParameters("dResolvedValue"))
            .parentId(nodeExecution3Id)
            .nextId(nodeExecution5Id)
            .nodeType(NodeType.PLAN_NODE.name())
            .build();

    planNode5 = preparePlanNode(false, "e", "ei1", null);
    nodeExecution5 =
        NodeExecution.builder()
            .uuid(nodeExecution5Id)
            .identifier("e")
            .ambiance(ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1Id, planNode1))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2Id, planNode2))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3Id, planNode3))
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5Id, planNode5))
                          .build())
            .resolvedStepParameters(prepareStepParameters("eResolvedValue"))
            .previousId(nodeExecution4Id)
            .parentId(nodeExecution3Id)
            .build();

    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution1.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution1);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution2.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution2);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution3.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution3);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution4.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution4);
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution5.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution5);
    when(pmsFeatureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);

    List<NodeExecution> nodeExecutionsList1 = Collections.singletonList(nodeExecution1);
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, null, NodeProjectionUtils.fieldsForExpressionEngine))
        .thenAnswer((Answer<Stream<NodeExecution>>) invocation
            -> PipelineServiceTestHelper.createCloseableIterator(nodeExecutionsList1.iterator()).stream());

    List<NodeExecution> nodeExecutionsList2 = Collections.singletonList(nodeExecution2);
    Stream<NodeExecution> iterator2 =
        PipelineServiceTestHelper.createCloseableIterator(nodeExecutionsList2.iterator()).stream();
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution1.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator2);

    List<NodeExecution> nodeExecutionList3 = Collections.singletonList(nodeExecution3);
    Stream<NodeExecution> iterator3 =
        PipelineServiceTestHelper.createCloseableIterator(nodeExecutionList3.iterator()).stream();
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution2.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator3);

    List<NodeExecution> nodeExecutionList4 = asList(nodeExecution4, nodeExecution5);
    Stream<NodeExecution> iterator4 =
        PipelineServiceTestHelper.createCloseableIterator(nodeExecutionList4.iterator()).stream();
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution3.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator4);

    List<NodeExecution> emptyList = new ArrayList<>();
    Stream<NodeExecution> emptyIterator =
        PipelineServiceTestHelper.createCloseableIterator(emptyList.iterator()).stream();
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution4.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(emptyIterator);
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, nodeExecution5.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(emptyIterator);
    when(planExecutionService.getPlanExecutionMetadata(planExecutionId)).thenReturn(PlanExecution.builder().build());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testNodeExecutionCurrentStatusWhenIgnoredFailure() {
    Ambiance newAmbiance = Ambiance.newBuilder()
                               .setPlanExecutionId(planExecutionId)
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1.getUuid(), planNode1))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2.getUuid(), planNode2))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3.getUuid(), planNode3))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5.getUuid(), planNode5))
                               .build();

    Reflect.on(nodeExecution5).set("status", Status.IGNORE_FAILED);
    Reflect.on(nodeExecution4).set("status", Status.SUCCEEDED);
    Reflect.on(nodeExecution4).set("advisorsProcessed", true);
    Reflect.on(nodeExecution5).set("advisorsProcessed", true);

    // pipeline children
    when(nodeExecutionService.findAllChildrenWithStatusInAndWithoutOldRetriesV2(
             planExecutionId, nodeExecution1.getUuid(), StatusUtils.finalStatuses(), false))
        .thenReturn(Arrays.asList(nodeExecution4, nodeExecution5));

    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder().staticAliases(new PipelineServiceApplication().getStaticAliases()).build();
    doReturn(ImmutableMap.of("cd", pmsSdkInstance)).when(pmsSdkInstanceService).getSdkInstanceCacheValue();
    Object pipelineCurrentStatus = engineExpressionEvaluator.evaluateExpression("<+pipeline.currentStatus>");
    assertThat((String) pipelineCurrentStatus).isEqualTo("IGNORE_FAILED");
    Object stageCurrentStatus = engineExpressionEvaluator.evaluateExpression("<+pipeline.currentStatus>");
    assertThat((String) stageCurrentStatus).isEqualTo("IGNORE_FAILED");
    Object pipelineSuccess =
        engineExpressionEvaluator.evaluateExpression("<+" + OrchestrationConstants.PIPELINE_SUCCESS + ">");
    assertThat(pipelineSuccess).isInstanceOf(Boolean.class);
    assertThat((Boolean) pipelineSuccess).isEqualTo(true);

    Object stageSuccess =
        engineExpressionEvaluator.evaluateExpression("<+" + OrchestrationConstants.STAGE_SUCCESS + ">");
    assertThat(stageSuccess).isInstanceOf(Boolean.class);
    assertThat((Boolean) stageSuccess).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testNodeExecutionLiveStatusWhenIgnoredFailure() {
    Ambiance newAmbiance = Ambiance.newBuilder()
                               .setPlanExecutionId(planExecutionId)
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1.getUuid(), planNode1))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2.getUuid(), planNode2))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3.getUuid(), planNode3))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5.getUuid(), planNode5))
                               .build();

    Reflect.on(nodeExecution5).set("status", Status.IGNORE_FAILED);
    Reflect.on(nodeExecution4).set("status", Status.SUCCEEDED);

    // pipeline children
    when(nodeExecutionService.findAllChildrenWithStatusInAndWithoutOldRetriesV2(
             planExecutionId, nodeExecution1.getUuid(), StatusUtils.finalStatuses(), true))
        .thenReturn(Arrays.asList(nodeExecution4, nodeExecution5));

    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder().staticAliases(new PipelineServiceApplication().getStaticAliases()).build();
    doReturn(ImmutableMap.of("cd", pmsSdkInstance)).when(pmsSdkInstanceService).getSdkInstanceCacheValue();
    Object pipelineCurrentStatus = engineExpressionEvaluator.evaluateExpression("<+pipeline.liveStatus>");
    assertThat((String) pipelineCurrentStatus).isEqualTo("IGNORE_FAILED");
    Object stageCurrentStatus = engineExpressionEvaluator.evaluateExpression("<+pipeline.liveStatus>");
    assertThat((String) stageCurrentStatus).isEqualTo("IGNORE_FAILED");
    Object pipelineSuccess =
        engineExpressionEvaluator.evaluateExpression("<+" + OrchestrationConstants.PIPELINE_SUCCESS + ">");
    assertThat(pipelineSuccess).isInstanceOf(Boolean.class);
    assertThat((Boolean) pipelineSuccess).isEqualTo(true);

    Object stageSuccess =
        engineExpressionEvaluator.evaluateExpression("<+" + OrchestrationConstants.STAGE_SUCCESS + ">");
    assertThat(stageSuccess).isInstanceOf(Boolean.class);
    assertThat((Boolean) stageSuccess).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRetryCountExpressionWithoutRetryIds() {
    String uuid = generateUuid();
    PlanNode planNode = preparePlanNode(false, "step", "stepValue", "STEP");
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .identifier("step")
            .ambiance(Ambiance.newBuilder().addLevels(PmsLevelUtils.buildLevelFromNode(uuid, planNode)).build())
            .uuid(uuid)
            .build();
    Ambiance newAmbiance = Ambiance.newBuilder()
                               .setPlanExecutionId(planExecutionId)
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution.getUuid(), planNode))
                               .build();
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution);
    List<NodeExecution> nodeExecutionsList = Collections.singletonList(nodeExecution);
    Stream<NodeExecution> iterator =
        PipelineServiceTestHelper.createCloseableIterator(nodeExecutionsList.iterator()).stream();
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, uuid, NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator);
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder().staticAliases(new PipelineServiceApplication().getStaticAliases()).build();
    doReturn(ImmutableMap.of("cd", pmsSdkInstance)).when(pmsSdkInstanceService).getSdkInstanceCacheValue();
    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    Object retryCount =
        engineExpressionEvaluator.evaluateExpression("<+step.retryCount>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    assertThat((Integer) retryCount).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRetryCountExpressionWithRetryIds() {
    String uuid = generateUuid();
    PlanNode planNode = preparePlanNode(false, "step", "stepValue", "STEP");
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .identifier("step")
            .ambiance(Ambiance.newBuilder().addLevels(PmsLevelUtils.buildLevelFromNode(uuid, planNode)).build())
            .uuid(uuid)
            .retryIds(List.of("id1", "id2", "id3"))
            .build();
    Ambiance newAmbiance = Ambiance.newBuilder()
                               .setPlanExecutionId(planExecutionId)
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution.getUuid(), planNode))
                               .build();
    when(nodeExecutionService.getWithFieldsIncluded(
             nodeExecution.getUuid(), NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(nodeExecution);
    List<NodeExecution> nodeExecutionsList = Collections.singletonList(nodeExecution);
    Stream<NodeExecution> iterator =
        PipelineServiceTestHelper.createCloseableIterator(nodeExecutionsList.iterator()).stream();
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, uuid, NodeProjectionUtils.fieldsForExpressionEngine))
        .thenReturn(iterator);
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder().staticAliases(new PipelineServiceApplication().getStaticAliases()).build();
    doReturn(ImmutableMap.of("cd", pmsSdkInstance)).when(pmsSdkInstanceService).getSdkInstanceCacheValue();
    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    Object retryCount =
        engineExpressionEvaluator.evaluateExpression("<+step.retryCount>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    assertThat((Integer) retryCount).isEqualTo(3);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRemoteFunctor() {
    Ambiance newAmbiance = Ambiance.newBuilder()
                               .setPlanExecutionId(planExecutionId)
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1.getUuid(), planNode1))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2.getUuid(), planNode2))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3.getUuid(), planNode3))
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5.getUuid(), planNode5))
                               .build();
    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    ExpressionResponse expressionResponse = ExpressionResponse.newBuilder().build();
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(expressionResponse).when(remoteExpressionFunctor).get(any());

    // testing that remoteFunctor is registered correctly
    assertTrue(engineExpressionEvaluator.evaluateExpression("<+dummy>") instanceof RemoteExpressionFunctor);

    // testing simple string argument
    // The previous stream would have been closed
    List<NodeExecution> nodeExecutionsList1 = new ArrayList<>();
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             anyString(), anyString(), eq(NodeProjectionUtils.fieldsForExpressionEngine)))
        .thenAnswer((Answer<Stream<NodeExecution>>) invocation
            -> PipelineServiceTestHelper.createCloseableIterator(nodeExecutionsList1.iterator()).stream());
    assertEquals(engineExpressionEvaluator.evaluateExpression("<+dummy.abc>"), expressionResponse);
    verify(remoteExpressionFunctor, times(1)).get(argumentCaptor.capture());
    assertEquals(argumentCaptor.getValue(), "abc");
    // The previous stream would have been closed
    List<NodeExecution> nodeExecutionsList2 = new ArrayList<>();
    Stream<NodeExecution> iterator2 =
        PipelineServiceTestHelper.createCloseableIterator(nodeExecutionsList2.iterator()).stream();
    when(nodeExecutionService.fetchChildrenNodeExecutionsIterator(anyString(), anyString(), any()))
        .thenReturn(iterator2);
    assertEquals(engineExpressionEvaluator.evaluateExpression("<+dummy.get(\"arg1\")>"), expressionResponse);
    verify(remoteExpressionFunctor, times(2)).get(argumentCaptor.capture());
    assertEquals(argumentCaptor.getValue(), "arg1");

    // testing array of strings as argument
    assertEquals(engineExpressionEvaluator.evaluateExpression("<+dummy.get([\"arg1\",\"arg2\"])>"), expressionResponse);
    ArgumentCaptor<String[]> arrayArgumentCaptor = ArgumentCaptor.forClass(String[].class);
    verify(remoteExpressionFunctor, times(1)).get(arrayArgumentCaptor.capture());
    String[] argsArray = arrayArgumentCaptor.getValue();
    assertEquals(argsArray.length, 2);
    assertEquals(argsArray[0], "arg1");
    assertEquals(argsArray[1], "arg2");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFileStoreFunctorResolution() {
    Map<String, String> abstrtactionsMap = new HashMap<>();
    abstrtactionsMap.put("accountId", "accountIdentifier");
    Ambiance newAmbiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .putFeatureFlagToValueMap(FeatureName.PIPE_MOVE_FILE_STORE_FUNCTOR_TO_PIPELINE_SERVICE.name(), true)
                    .build())
            .setPlanExecutionId(planExecutionId)
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1.getUuid(), planNode1))
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2.getUuid(), planNode2))
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3.getUuid(), planNode3))
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5.getUuid(), planNode5))
            .putAllSetupAbstractions(abstrtactionsMap)
            .build();
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);
    aStatic.when(() -> SafeHttpCall.executeWithExceptions(any())).thenReturn(ResponseDTO.newResponse("<+dummy.abc>"));
    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    doReturn("test").when(remoteExpressionFunctor).get(any());
    doReturn(true).when(pipelineSettingsService).isFileSizeWithinLimit("accountIdentifier", 4);
    // testing simple string argument
    assertEquals(engineExpressionEvaluator.evaluateExpression("<+fileStore.getAsString('/abc')>"), "test");
    verify(remoteExpressionFunctor, times(1)).get(argumentCaptor.capture());
    assertEquals(argumentCaptor.getValue(), "abc");

    assertEquals(engineExpressionEvaluator.evaluateExpression("<+fileStore.getAsBase64('/abc')>"),
        EncodingUtils.encodeBase64("test"));
    verify(remoteExpressionFunctor, times(2)).get(argumentCaptor.capture());
    assertEquals(argumentCaptor.getValue(), "abc");
    aStatic.close();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFileStoreLoopResolution() {
    Ambiance newAmbiance =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .putFeatureFlagToValueMap(FeatureName.PIPE_MOVE_FILE_STORE_FUNCTOR_TO_PIPELINE_SERVICE.name(), true)
                    .build())
            .setPlanExecutionId(planExecutionId)
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1.getUuid(), planNode1))
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution2.getUuid(), planNode2))
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution3.getUuid(), planNode3))
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution5.getUuid(), planNode5))
            .build();
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);
    aStatic.when(() -> SafeHttpCall.executeWithExceptions(any()))
        .thenReturn(ResponseDTO.newResponse("<+fileStore.getAsString('/abc')>"));

    EngineExpressionEvaluator engineExpressionEvaluator = prepareEngineExpressionEvaluator(newAmbiance);
    fileStoreFunctorV2 = spy(new FileStoreFunctorV2(fileStoreClient, newAmbiance, pipelineRetentionService,
        pipelineSettingsService, engineExpressionEvaluator, 10));

    doReturn("test").when(remoteExpressionFunctor).get(any());

    // testing simple string argument
    assertThatThrownBy(() -> engineExpressionEvaluator.evaluateExpression("<+fileStore.getAsBase64('/abc')>"))
        .hasMessage("Infinite loop or too deep indirection in expression evaluation");
    aStatic.close();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testStringFunctor() {
    Ambiance newAmbiance = Ambiance.newBuilder()
                               .setPlanExecutionId(planExecutionId)
                               .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecution1.getUuid(), planNode1))
                               .build();

    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder().staticAliases(new PipelineServiceApplication().getStaticAliases()).build();
    doReturn(ImmutableMap.of("cd", pmsSdkInstance)).when(pmsSdkInstanceService).getSdkInstanceCacheValue();

    EngineExpressionEvaluator evaluator = prepareEngineExpressionEvaluator(newAmbiance);

    Object v1 = evaluator.evaluateExpression("<+string.escapeJson('x\"y')>");
    assertThat(v1).isEqualTo("x\\\"y");

    Object v2 = evaluator.evaluateExpression("<+string.escapeJson('8E92hr20Sb{{{}}}}///a\"/asd/as6O3-bd0kCQsw\"')>");
    assertThat(v2).isEqualTo("8E92hr20Sb{{{}}}}///a\\\"/asd/as6O3-bd0kCQsw\\\"");

    Object v3 = evaluator.evaluateExpression("<+string.escapeJson('{\"k\":\"v\"}')>");
    assertThat(v3).isEqualTo("{\\\"k\\\":\\\"v\\\"}");
  }

  private PlanNode preparePlanNode(
      boolean skipExpressionChain, String identifier, String paramValue, String groupName) {
    return PlanNode.builder()
        .uuid(generateUuid())
        .name(identifier)
        .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
        .identifier(identifier)
        .skipExpressionChain(skipExpressionChain)
        .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(prepareStepParameters(paramValue))))
        .group(groupName)
        .build();
  }

  private Map<String, Object> prepareStepParameters(String paramValue) {
    return RecastOrchestrationUtils.toMap(TestStepParameters.builder().param(paramValue).build());
  }

  private EngineExpressionEvaluator prepareEngineExpressionEvaluator(Ambiance ambiance) {
    SampleEngineExpressionEvaluator evaluator = new SampleEngineExpressionEvaluator(ambiance, pmsSdkInstanceService);
    on(evaluator).set("planExecutionService", planExecutionService);
    on(evaluator).set("nodeExecutionService", nodeExecutionService);
    on(evaluator).set("planExpansionService", planExpansionService);
    on(evaluator).set("pmsFeatureFlagService", pmsFeatureFlagService);
    on(evaluator).set("expressionsObserverFactory", expressionsObserverFactory);
    on(evaluator).set("fileStoreClient", fileStoreClient);
    on(evaluator).set("pipelineSettingsService", pipelineSettingsService);

    evaluator.addToContextMap("dummy", remoteExpressionFunctor);

    return evaluator;
  }

  public static class SampleEngineExpressionEvaluator extends PMSExpressionEvaluator {
    public SampleEngineExpressionEvaluator(Ambiance ambiance, PmsSdkInstanceService pmsSdkInstanceService) {
      super((VariableResolverTracker) null, ambiance, null, false, null, false);
      this.pmsSdkInstanceService = pmsSdkInstanceService;
    }

    @Override
    protected void initialize() {
      super.initialize();
    }

    public void addToContextMap(String a, Object b) {
      super.addToContext(a, b);
    }

    @Override
    protected Object evaluateInternal(String expression, EngineJexlContext ctx) {
      Object value = super.evaluateInternal(expression, ctx);
      if (value instanceof DummyOrchestrationField) {
        return ((DummyOrchestrationField) value).fetchFinalValue();
      }
      return value;
    }
  }

  @Data
  @Builder
  @RecasterAlias("io.harness.pms.expressions.PMSExpressionEvaluatorTest$TestStepParameters")
  public static class TestStepParameters implements StepParameters {
    String param;
  }
}
