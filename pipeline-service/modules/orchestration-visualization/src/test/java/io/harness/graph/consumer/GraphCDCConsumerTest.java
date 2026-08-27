/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.consumer;

import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.event.streams.model.ChangeDataEvent;
import io.harness.graph.service.GraphBatchUpdateDTOs.ModuleInfoUpdate;
import io.harness.graph.service.GraphBatchUpdateDTOs.OutcomeUpdate;
import io.harness.graph.service.GraphBatchUpdateDTOs.StepDetailsUpdate;
import io.harness.graph.service.GraphCDCService;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class GraphCDCConsumerTest extends OrchestrationVisualizationTestBase {
  @Mock private GraphCDCService graphService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private MetricService metricService;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;

  private GraphCDCConsumer consumer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    GraphCDCConsumerConfig config =
        GraphCDCConsumerConfig.builder().enabled(false).topics(List.of("test-topic")).build();
    consumer = new GraphCDCConsumer(config, graphService, kryoSerializer, pmsExecutionSummaryService, metricService);
  }

  // ===================== extractAccountIdentifier =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractAccountIdentifier_fromAmbiance() throws Exception {
    Map<String, Object> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "acct-123");
    Map<String, Object> ambiance = new HashMap<>();
    ambiance.put("setupAbstractions", setupAbstractions);
    Map<String, Object> doc = new HashMap<>();
    doc.put("ambiance", ambiance);

    Method method = GraphCDCConsumer.class.getDeclaredMethod("extractAccountIdentifier", Map.class);
    method.setAccessible(true);
    String result = (String) method.invoke(consumer, doc);

    assertThat(result).isEqualTo("acct-123");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractAccountIdentifier_nullDoc() throws Exception {
    Method method = GraphCDCConsumer.class.getDeclaredMethod("extractAccountIdentifier", Map.class);
    method.setAccessible(true);
    String result = (String) method.invoke(consumer, (Map<String, Object>) null);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExtractAccountIdentifier_noAmbiance() throws Exception {
    Map<String, Object> doc = new HashMap<>();
    doc.put("status", "RUNNING");

    Method method = GraphCDCConsumer.class.getDeclaredMethod("extractAccountIdentifier", Map.class);
    method.setAccessible(true);
    String result = (String) method.invoke(consumer, doc);
    assertThat(result).isNull();
  }

  // ===================== accumulateNodeExecutionCreate =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateNodeExecutionCreate_validEvent() throws Exception {
    Map<String, Object> ambiance = new HashMap<>();
    Map<String, Object> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "acct-1");
    ambiance.put("planExecutionId", "plan-1");
    ambiance.put("setupAbstractions", setupAbstractions);

    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("ambiance", ambiance);
    fullDoc.put("status", "RUNNING");

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .ns(new ChangeDataEvent.Namespace("db", "nodeExecutions"))
                                .documentKey(Map.of("_id", "ne-1"))
                                .fullDocument(fullDoc)
                                .build();

    Map<String, Object> vertexUpdates = new HashMap<>();
    List<String> barrierStepParentIds = new ArrayList<>();

    Method method = GraphCDCConsumer.class.getDeclaredMethod(
        "accumulateNodeExecutionCreate", ChangeDataEvent.class, Map.class, List.class);
    method.setAccessible(true);
    method.invoke(consumer, event, vertexUpdates, barrierStepParentIds);

    assertThat(vertexUpdates).containsKey("ne-1");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateNodeExecutionCreate_missingPlanExecutionId() throws Exception {
    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("status", "RUNNING");

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .documentKey(Map.of("_id", "ne-1"))
                                .fullDocument(fullDoc)
                                .build();

    Map<String, Object> vertexUpdates = new HashMap<>();
    List<String> barrierStepParentIds = new ArrayList<>();

    Method method = GraphCDCConsumer.class.getDeclaredMethod(
        "accumulateNodeExecutionCreate", ChangeDataEvent.class, Map.class, List.class);
    method.setAccessible(true);
    method.invoke(consumer, event, vertexUpdates, barrierStepParentIds);

    // No accumulation should happen without planExecutionId
    assertThat(vertexUpdates).isEmpty();
  }

  // ===================== accumulateNodeExecutionUpdate =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateNodeExecutionUpdate_validEvent() throws Exception {
    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("status", "SUCCEEDED");
    updatedFields.put("endTs", 12345L);

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("update")
                                .documentKey(Map.of("_id", "ne-1"))
                                .updateDescription(new ChangeDataEvent.UpdateDescription(updatedFields, null, null))
                                .build();

    Map<String, Object> vertexUpdates = new HashMap<>();

    Method method =
        GraphCDCConsumer.class.getDeclaredMethod("accumulateNodeExecutionUpdate", ChangeDataEvent.class, Map.class);
    method.setAccessible(true);
    method.invoke(consumer, event, vertexUpdates);

    assertThat(vertexUpdates).containsKey("ne-1");
  }

  // ===================== accumulateOutcomeInstanceCreate =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateOutcomeInstanceCreate_validEvent() throws Exception {
    Map<String, Object> producedBy = new HashMap<>();
    producedBy.put("runtimeId", "ne-1");

    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("planExecutionId", "plan-1");
    fullDoc.put("producedBy", producedBy);
    fullDoc.put("name", "outcome1");
    fullDoc.put("outcome", Map.of("key", "value"));

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .documentKey(Map.of("_id", "oc-1"))
                                .fullDocument(fullDoc)
                                .build();

    List<OutcomeUpdate> outcomeUpdates = new ArrayList<>();

    Method method =
        GraphCDCConsumer.class.getDeclaredMethod("accumulateOutcomeInstanceCreate", ChangeDataEvent.class, List.class);
    method.setAccessible(true);
    method.invoke(consumer, event, outcomeUpdates);

    assertThat(outcomeUpdates).hasSize(1);
    assertThat(outcomeUpdates.get(0).getNodeExecutionId()).isEqualTo("ne-1");
    assertThat(outcomeUpdates.get(0).getOutcomeName()).isEqualTo("outcome1");
    assertThat(outcomeUpdates.get(0).isCreate()).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateOutcomeInstanceCreate_noProducedBy() throws Exception {
    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("planExecutionId", "plan-1");
    fullDoc.put("name", "outcome1");

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .documentKey(Map.of("_id", "oc-1"))
                                .fullDocument(fullDoc)
                                .build();

    List<OutcomeUpdate> outcomeUpdates = new ArrayList<>();

    Method method =
        GraphCDCConsumer.class.getDeclaredMethod("accumulateOutcomeInstanceCreate", ChangeDataEvent.class, List.class);
    method.setAccessible(true);
    method.invoke(consumer, event, outcomeUpdates);

    assertThat(outcomeUpdates).isEmpty();
  }

  // ===================== accumulateGraphUpdateInfoCreate =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateGraphUpdateInfoCreate_stageLevel() throws Exception {
    Map<String, Object> moduleInfo = new HashMap<>();
    moduleInfo.put("cd", Map.of("serviceIdentifiers", List.of("svc1")));

    Map<String, Object> updateInfo = new HashMap<>();
    updateInfo.put("stepCategory", "STAGE");
    updateInfo.put("stageUuid", "stage-1");
    updateInfo.put("moduleInfo", moduleInfo);

    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("planExecutionId", "plan-1");
    fullDoc.put("executionSummaryUpdateInfo", updateInfo);

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .documentKey(Map.of("_id", "gui-1"))
                                .fullDocument(fullDoc)
                                .build();

    List<ModuleInfoUpdate> moduleInfoUpdates = new ArrayList<>();

    Method method =
        GraphCDCConsumer.class.getDeclaredMethod("accumulateGraphUpdateInfoCreate", ChangeDataEvent.class, List.class);
    method.setAccessible(true);
    method.invoke(consumer, event, moduleInfoUpdates);

    assertThat(moduleInfoUpdates).hasSize(1);
    assertThat(moduleInfoUpdates.get(0).getStageUuid()).isEqualTo("stage-1");
    assertThat(moduleInfoUpdates.get(0).isPipelineLevel()).isFalse();
    assertThat(moduleInfoUpdates.get(0).isCreate()).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateGraphUpdateInfoCreate_pipelineLevel() throws Exception {
    Map<String, Object> moduleInfo = new HashMap<>();
    moduleInfo.put("cd", Map.of("key", "value"));

    Map<String, Object> updateInfo = new HashMap<>();
    updateInfo.put("stepCategory", "PIPELINE");
    updateInfo.put("moduleInfo", moduleInfo);

    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("planExecutionId", "plan-1");
    fullDoc.put("executionSummaryUpdateInfo", updateInfo);

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .documentKey(Map.of("_id", "gui-2"))
                                .fullDocument(fullDoc)
                                .build();

    List<ModuleInfoUpdate> moduleInfoUpdates = new ArrayList<>();

    Method method =
        GraphCDCConsumer.class.getDeclaredMethod("accumulateGraphUpdateInfoCreate", ChangeDataEvent.class, List.class);
    method.setAccessible(true);
    method.invoke(consumer, event, moduleInfoUpdates);

    assertThat(moduleInfoUpdates).hasSize(1);
    assertThat(moduleInfoUpdates.get(0).isPipelineLevel()).isTrue();
  }

  // ===================== accumulateGraphUpdateInfoUpdate (dot-notation) =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateGraphUpdateInfoUpdate_dotNotation() throws Exception {
    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("executionSummaryUpdateInfo.moduleInfo.cd.envIdentifiers", List.of("env1", "env2"));
    updatedFields.put("executionSummaryUpdateInfo.moduleInfo.cd.serviceIdentifiers", List.of("svc1"));

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("update")
                                .documentKey(Map.of("_id", "gui-3"))
                                .updateDescription(new ChangeDataEvent.UpdateDescription(updatedFields, null, null))
                                .build();

    List<ModuleInfoUpdate> moduleInfoUpdates = new ArrayList<>();

    Method method =
        GraphCDCConsumer.class.getDeclaredMethod("accumulateGraphUpdateInfoUpdate", ChangeDataEvent.class, List.class);
    method.setAccessible(true);
    method.invoke(consumer, event, moduleInfoUpdates);

    assertThat(moduleInfoUpdates).hasSize(1);
    assertThat(moduleInfoUpdates.get(0).isCreate()).isFalse();
    Map<String, Object> moduleInfo = moduleInfoUpdates.get(0).getModuleInfo();
    assertThat(moduleInfo).containsKey("cd");
    @SuppressWarnings("unchecked") Map<String, Object> cdModule = (Map<String, Object>) moduleInfo.get("cd");
    assertThat(cdModule).containsKey("envIdentifiers");
    assertThat(cdModule).containsKey("serviceIdentifiers");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateGraphUpdateInfoUpdate_fullModuleInfoObject() throws Exception {
    Map<String, Object> moduleInfo = new HashMap<>();
    moduleInfo.put("cd", Map.of("key", "value"));

    Map<String, Object> updateInfo = new HashMap<>();
    updateInfo.put("moduleInfo", moduleInfo);

    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("executionSummaryUpdateInfo", updateInfo);

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("update")
                                .documentKey(Map.of("_id", "gui-4"))
                                .updateDescription(new ChangeDataEvent.UpdateDescription(updatedFields, null, null))
                                .build();

    List<ModuleInfoUpdate> moduleInfoUpdates = new ArrayList<>();

    Method method =
        GraphCDCConsumer.class.getDeclaredMethod("accumulateGraphUpdateInfoUpdate", ChangeDataEvent.class, List.class);
    method.setAccessible(true);
    method.invoke(consumer, event, moduleInfoUpdates);

    assertThat(moduleInfoUpdates).hasSize(1);
    assertThat(moduleInfoUpdates.get(0).getModuleInfo()).containsKey("cd");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateGraphUpdateInfoUpdate_noModuleInfo() throws Exception {
    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("someOtherField", "value");

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("update")
                                .documentKey(Map.of("_id", "gui-5"))
                                .updateDescription(new ChangeDataEvent.UpdateDescription(updatedFields, null, null))
                                .build();

    List<ModuleInfoUpdate> moduleInfoUpdates = new ArrayList<>();

    Method method =
        GraphCDCConsumer.class.getDeclaredMethod("accumulateGraphUpdateInfoUpdate", ChangeDataEvent.class, List.class);
    method.setAccessible(true);
    method.invoke(consumer, event, moduleInfoUpdates);

    assertThat(moduleInfoUpdates).isEmpty();
  }

  // ===================== accumulateNodeExecutionsInfoCreate =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testAccumulateNodeExecutionsInfoCreate_validEvent() throws Exception {
    Map<String, Object> detailItem = new HashMap<>();
    detailItem.put("name", "detail1");
    detailItem.put("stepDetails", Map.of("field1", "value1"));

    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("nodeExecutionId", "ne-1");
    fullDoc.put("nodeExecutionDetailsInfoList", List.of(detailItem));

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .documentKey(Map.of("_id", "nei-1"))
                                .fullDocument(fullDoc)
                                .build();

    List<StepDetailsUpdate> stepDetailsUpdates = new ArrayList<>();

    Method method = GraphCDCConsumer.class.getDeclaredMethod(
        "accumulateNodeExecutionsInfoCreate", ChangeDataEvent.class, List.class);
    method.setAccessible(true);
    method.invoke(consumer, event, stepDetailsUpdates);

    assertThat(stepDetailsUpdates).hasSize(1);
    assertThat(stepDetailsUpdates.get(0).getNodeExecutionId()).isEqualTo("ne-1");
    assertThat(stepDetailsUpdates.get(0).getDocumentId()).isEqualTo("nei-1");
    assertThat(stepDetailsUpdates.get(0).isCreate()).isTrue();
  }

  // ===================== processPlanExecution =====================

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessPlanExecution_createEvent() throws Exception {
    Map<String, Object> fullDoc = new HashMap<>();
    fullDoc.put("status", "SUCCEEDED");
    fullDoc.put("endTs", 99999L);
    fullDoc.put("_id", "plan-1");

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("insert")
                                .documentKey(Map.of("_id", "plan-1"))
                                .fullDocument(fullDoc)
                                .build();

    Method method = GraphCDCConsumer.class.getDeclaredMethod("processPlanExecution", ChangeDataEvent.class);
    method.setAccessible(true);
    boolean result = (boolean) method.invoke(consumer, event);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessPlanExecution_updateWithStatusChange() throws Exception {
    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("status", "FAILED");

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("update")
                                .documentKey(Map.of("_id", "plan-2"))
                                .updateDescription(new ChangeDataEvent.UpdateDescription(updatedFields, null, null))
                                .build();

    Method method = GraphCDCConsumer.class.getDeclaredMethod("processPlanExecution", ChangeDataEvent.class);
    method.setAccessible(true);
    boolean result = (boolean) method.invoke(consumer, event);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessPlanExecution_updateWithoutStatusChange() throws Exception {
    Map<String, Object> updatedFields = new HashMap<>();
    updatedFields.put("someOtherField", "value");

    ChangeDataEvent event = ChangeDataEvent.builder()
                                .operationType("update")
                                .documentKey(Map.of("_id", "plan-3"))
                                .updateDescription(new ChangeDataEvent.UpdateDescription(updatedFields, null, null))
                                .build();

    Method method = GraphCDCConsumer.class.getDeclaredMethod("processPlanExecution", ChangeDataEvent.class);
    method.setAccessible(true);
    boolean result = (boolean) method.invoke(consumer, event);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessPlanExecution_deleteEvent() throws Exception {
    ChangeDataEvent event =
        ChangeDataEvent.builder().operationType("delete").documentKey(Map.of("_id", "plan-4")).build();

    Method method = GraphCDCConsumer.class.getDeclaredMethod("processPlanExecution", ChangeDataEvent.class);
    method.setAccessible(true);
    boolean result = (boolean) method.invoke(consumer, event);

    assertThat(result).isFalse();
  }
}
