/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.OM;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.stepDetail.NodeExecutionDetailsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo.NodeExecutionsInfoKeys;
import io.harness.category.element.UnitTests;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.observers.StepDetailsUpdateInfo;
import io.harness.engine.observers.StepDetailsUpdateObserver;
import io.harness.execution.NodeExecution;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.observer.Subject;
import io.harness.plancreator.strategy.StrategyConstants;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.repositories.stepDetail.NodeExecutionsInfoRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionInfoServiceImplTest extends OrchestrationTestBase {
  @Mock private NodeExecutionsInfoRepository nodeExecutionsInfoRepositoryMock;
  @Mock private PersistentLocker persistentLocker;
  @Mock private NodeExecutionService nodeExecutionService;
  @Inject private NodeExecutionsInfoRepository nodeExecutionsInfoRepository;

  @Mock private Subject<StepDetailsUpdateObserver> stepDetailsUpdateObserverSubject;
  @Inject private MongoTemplate mongoTemplate;

  @Inject @InjectMocks private NodeExecutionInfoServiceImpl pmsGraphStepDetailsService;
  private static final String ACCOUNT_ID = "accountId";

  @Before
  public void setUp() {
    Reflect.on(pmsGraphStepDetailsService).set("stepDetailsUpdateObserverSubject", stepDetailsUpdateObserverSubject);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void addStepDetail() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    PmsStepDetails pmsStepDetails = new PmsStepDetails(new HashMap<>());
    String name = "name";
    when(nodeExecutionsInfoRepositoryMock.save(any())).thenReturn(null);
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    pmsGraphStepDetailsService.addStepDetail(nodeExecutionId, planExecutionId, pmsStepDetails, name);

    verify(stepDetailsUpdateObserverSubject).fireInform(any(), any());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void addStepDetailWithAccountId() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    String accountIdentifier = generateUuid();
    PmsStepDetails pmsStepDetails = new PmsStepDetails(new HashMap<>());
    String name = "name";
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .planExecutionId(planExecutionId)
                                                .accountIdentifier(accountIdentifier)
                                                .build();
    when(nodeExecutionsInfoRepositoryMock.save(any())).thenReturn(null);
    mongoTemplate.save(nodeExecutionsInfo);
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    StepDetailsUpdateInfo expectedStepDetailsInfo = StepDetailsUpdateInfo.builder()
                                                        .nodeExecutionId(nodeExecutionId)
                                                        .planExecutionId(planExecutionId)
                                                        .accountId(nodeExecutionsInfo.getAccountIdentifier())
                                                        .build();

    ArgumentCaptor<StepDetailsUpdateInfo> stepDetailsUpdateInfoArgumentCaptor =
        ArgumentCaptor.forClass(StepDetailsUpdateInfo.class);
    pmsGraphStepDetailsService.addStepDetail(nodeExecutionId, planExecutionId, pmsStepDetails, name);

    verify(stepDetailsUpdateObserverSubject).fireInform(any(), stepDetailsUpdateInfoArgumentCaptor.capture());

    assertEquals(expectedStepDetailsInfo, stepDetailsUpdateInfoArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void addStepInputs() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    pmsGraphStepDetailsService.saveNodeExecutionInfo(nodeExecutionId, planExecutionId, null, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testSaveNodeExecutionInfo() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    pmsGraphStepDetailsService.saveNodeExecutionInfo(
        nodeExecutionId, planExecutionId, StrategyMetadata.newBuilder().build(), ACCOUNT_ID);
    verify(nodeExecutionsInfoRepositoryMock, times(1)).save(any());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStepInputs() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    PmsStepParameters stepParams = new PmsStepParameters(Map.of("key", "value"));
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .planExecutionId(planExecutionId)
                                                .resolvedInputs(stepParams)
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    PmsStepParameters result = pmsGraphStepDetailsService.getStepInputs(planExecutionId, nodeExecutionId);

    assertThat(result).isNotNull();
    assertThat(result.get("key")).isEqualTo("value");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testAddStepInputsInternal_setsCurrentStatusOnInsert() {
    on(pmsGraphStepDetailsService).set("mongoTemplate", mongoTemplate);
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    PmsStepParameters stepParams = new PmsStepParameters(Map.of("key", "value"));
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    boolean wasInserted = pmsGraphStepDetailsService.addStepInputsInternal(
        nodeExecutionId, stepParams, planExecutionId, null, ACCOUNT_ID);

    assertThat(wasInserted).isTrue();
    NodeExecutionsInfo info =
        mongoTemplate.findOne(new Query(Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId)),
            NodeExecutionsInfo.class);
    assertThat(info).isNotNull();
    assertEquals(Status.SUCCEEDED, info.getCurrentStatus());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetStepInputsRecasterPruned() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    Map<String, Object> stepInputs = new LinkedHashMap<>();
    stepInputs.put("__recast", "a.b.c");
    stepInputs.put("uuid", generateUuid());
    stepInputs.put("a", "b");
    Map<String, Object> nestedMap = new LinkedHashMap<>();
    nestedMap.put("d", "e");
    stepInputs.put("c", nestedMap);

    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .planExecutionId(planExecutionId)
                                                .resolvedInputs(PmsStepParameters.parse(stepInputs))
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    PmsStepParameters stepInputsRecasterPruned =
        pmsGraphStepDetailsService.getStepInputsRecasterPruned(planExecutionId, nodeExecutionId);
    assertThat(stepInputsRecasterPruned).isNotNull();
    assertThat(stepInputsRecasterPruned.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetStepInputsRecasterPrunedV2() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    Map<String, Object> stepInputs = new LinkedHashMap<>();
    stepInputs.put("__recast", "a.b.c");
    stepInputs.put("uuid", generateUuid());
    stepInputs.put("a", "b");
    Map<String, Object> nestedMap = new LinkedHashMap<>();
    nestedMap.put("d", "e");
    stepInputs.put("c", nestedMap);

    PmsStepParameters stepInputsRecasterPruned =
        pmsGraphStepDetailsService.getStepInputsRecasterPruned(PmsStepParameters.parse(stepInputs));
    assertThat(stepInputsRecasterPruned).isNotNull();
    assertThat(stepInputsRecasterPruned.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStepInputsWithEmptyOptional() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    doNothing().when(stepDetailsUpdateObserverSubject).fireInform(any());

    PmsStepParameters result = pmsGraphStepDetailsService.getStepInputs(planExecutionId, nodeExecutionId);

    assertThat(result).isNotNull();
    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void getStepDetails() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();

    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .planExecutionId(planExecutionId)
                                                .stepDetails(NodeExecutionDetailsInfo.builder()
                                                                 .stepDetails(PmsStepDetails.parse(new HashMap<>()))
                                                                 .name("name")
                                                                 .build())
                                                .build();

    mongoTemplate.save(nodeExecutionsInfo);

    Map<String, PmsStepDetails> stepDetails =
        pmsGraphStepDetailsService.getStepDetails(planExecutionId, nodeExecutionId);

    assertThat(stepDetails).isNotNull();
    assertThat(stepDetails).isNotEmpty();
    assertThat(stepDetails.get("name")).isNotNull();
    PmsStepDetails pmsStepDetails = stepDetails.get("name");
    assertThat(pmsStepDetails).isEmpty();
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void getStepDetailsWithDuplicateNames() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();

    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder()
            .nodeExecutionId(nodeExecutionId)
            .planExecutionId(planExecutionId)
            .stepDetails(NodeExecutionDetailsInfo.builder()
                             .stepDetails(PmsStepDetails.parse(Map.of("taskId", "task-1")))
                             .name("K8s Diff Task : K8s Diff")
                             .build())
            .stepDetails(NodeExecutionDetailsInfo.builder()
                             .stepDetails(PmsStepDetails.parse(Map.of("taskId", "task-2")))
                             .name("K8s Diff Task : K8s Diff")
                             .build())
            .build();

    mongoTemplate.save(nodeExecutionsInfo);

    Map<String, PmsStepDetails> stepDetails =
        pmsGraphStepDetailsService.getStepDetails(planExecutionId, nodeExecutionId);

    assertThat(stepDetails).isNotNull();
    assertThat(stepDetails).hasSize(1);
    assertThat(stepDetails.get("K8s Diff Task : K8s Diff")).isNotNull();
    assertThat(stepDetails.get("K8s Diff Task : K8s Diff").get("taskId")).isEqualTo("task-2");
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void getStepDetailsFormNodeExecutionInfoWithDuplicateNames() {
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder()
            .stepDetails(NodeExecutionDetailsInfo.builder()
                             .stepDetails(PmsStepDetails.parse(Map.of("taskId", "task-1")))
                             .name("K8s Diff Task : K8s Diff")
                             .build())
            .stepDetails(NodeExecutionDetailsInfo.builder()
                             .stepDetails(PmsStepDetails.parse(Map.of("taskId", "task-2")))
                             .name("K8s Diff Task : K8s Diff")
                             .build())
            .build();

    Map<String, PmsStepDetails> stepDetails =
        pmsGraphStepDetailsService.getStepDetailsFormNodeExecutionInfo(nodeExecutionsInfo);

    assertThat(stepDetails).isNotNull();
    assertThat(stepDetails).hasSize(1);
    assertThat(stepDetails.get("K8s Diff Task : K8s Diff")).isNotNull();
    assertThat(stepDetails.get("K8s Diff Task : K8s Diff").get("taskId")).isEqualTo("task-2");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetNodeExecutionsInfo() {
    String nodeExecutionId = generateUuid();
    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(nodeExecutionId))
        .thenReturn(Optional.of(NodeExecutionsInfo.builder().nodeExecutionId(nodeExecutionId).build()));
    NodeExecutionsInfo nodeExecutionsInfo = pmsGraphStepDetailsService.getNodeExecutionsInfo(nodeExecutionId);
    assertEquals(nodeExecutionsInfo.getNodeExecutionId(), nodeExecutionId);
    verify(nodeExecutionsInfoRepositoryMock, times(1)).findByNodeExecutionId(nodeExecutionId);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetNodeExecutionsInfoWithEmptyOptional() {
    String nodeExecutionId = generateUuid();
    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(nodeExecutionId)).thenReturn(Optional.empty());
    NodeExecutionsInfo nodeExecutionsInfo = pmsGraphStepDetailsService.getNodeExecutionsInfo(nodeExecutionId);
    assertNull(nodeExecutionsInfo);
    verify(nodeExecutionsInfoRepositoryMock, times(1)).findByNodeExecutionId(nodeExecutionId);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testSaveNodeExecutionInfoForRetry() {
    String originalNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    String newNodeExecutionId = generateUuid();
    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(originalNodeExecutionId))
        .thenReturn(Optional.of(NodeExecutionsInfo.builder().build()));
    pmsGraphStepDetailsService.saveNodeExecutionInfoForRetry(
        planExecutionId, originalNodeExecutionId, newNodeExecutionId);
    verify(nodeExecutionsInfoRepositoryMock, times(1)).save(any(NodeExecutionsInfo.class));
    ArgumentCaptor<NodeExecutionsInfo> mCaptor = ArgumentCaptor.forClass(NodeExecutionsInfo.class);
    verify(nodeExecutionsInfoRepositoryMock).save(mCaptor.capture());
    NodeExecutionsInfo actualNodeExecutionsInfo = mCaptor.getValue();
    assertEquals(actualNodeExecutionsInfo.getNodeExecutionId(), newNodeExecutionId);
    assertEquals(actualNodeExecutionsInfo.getPlanExecutionId(), planExecutionId);
    assertNull(actualNodeExecutionsInfo.getUuid());
    assertEquals(actualNodeExecutionsInfo.getNodeExecutionDetailsInfoList().size(), 0);
    assertNull(actualNodeExecutionsInfo.getResolvedInputs());
    assertNull(actualNodeExecutionsInfo.getConcurrentChildInstance());
    verify(stepDetailsUpdateObserverSubject, times(1)).fireInform(any(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testSaveNodeExecutionInfoForRetryWithEmptyOptional() {
    String originalNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    String newNodeExecutionId = generateUuid();
    when(nodeExecutionsInfoRepositoryMock.findByNodeExecutionId(originalNodeExecutionId)).thenReturn(Optional.empty());
    pmsGraphStepDetailsService.saveNodeExecutionInfoForRetry(
        planExecutionId, originalNodeExecutionId, newNodeExecutionId);
    verify(nodeExecutionsInfoRepositoryMock, times(0)).save(any(NodeExecutionsInfo.class));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testAddConcurrentChildInformation() {
    String nodeExecutionId = generateUuid();
    List<String> childrenNodeExecutionIds = new ArrayList<>();
    childrenNodeExecutionIds.add("ID1");
    ConcurrentChildInstance concurrentChildInstance =
        ConcurrentChildInstance.builder().childrenNodeExecutionIds(childrenNodeExecutionIds).cursor(9).build();
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .uuid(generateUuid())
                                                .planExecutionId(generateUuid())
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);
    pmsGraphStepDetailsService.addConcurrentChildInformation(concurrentChildInstance, nodeExecutionId);
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    List<NodeExecutionsInfo> nodeExecutionsInfos = mongoTemplate.find(new Query(criteria), NodeExecutionsInfo.class);
    assertEquals(1, nodeExecutionsInfos.size());
    assertEquals(9, nodeExecutionsInfos.get(0).getConcurrentChildInstance().getCursor());
    assertEquals("ID1", nodeExecutionsInfos.get(0).getConcurrentChildInstance().getChildrenNodeExecutionIds().get(0));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testIncrementCursor() {
    String nodeExecutionId = generateUuid();
    List<String> childrenNodeExecutionIds = new ArrayList<>();
    childrenNodeExecutionIds.add("ID1");
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(), any()))
        .thenReturn(RedisAcquiredLock.builder().build());
    assertNull(pmsGraphStepDetailsService.incrementCursor(nodeExecutionId, Status.SUCCEEDED));
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder()
            .nodeExecutionId(nodeExecutionId)
            .uuid(generateUuid())
            .planExecutionId(generateUuid())
            .concurrentChildInstance(
                ConcurrentChildInstance.builder().cursor(4).childrenNodeExecutionIds(childrenNodeExecutionIds).build())
            .build();
    mongoTemplate.save(nodeExecutionsInfo);
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(), any()))
        .thenReturn(RedisAcquiredLock.builder().build());
    pmsGraphStepDetailsService.incrementCursor(nodeExecutionId, Status.SUCCEEDED);
    verify(persistentLocker, times(2)).waitToAcquireLockOptional(any(), any(), any());
    int cursor = mongoTemplate
                     .find(new Query(Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId)),
                         NodeExecutionsInfo.class)
                     .get(0)
                     .getConcurrentChildInstance()
                     .getCursor();
    assertEquals(5, cursor);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchConcurrentChildInstance() {
    String nodeExecutionId = generateUuid();
    List<String> childrenNodeExecutionIds = new ArrayList<>();
    childrenNodeExecutionIds.add("ID1");
    assertNull(pmsGraphStepDetailsService.fetchConcurrentChildInstance(nodeExecutionId));
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder()
            .nodeExecutionId(nodeExecutionId)
            .uuid(generateUuid())
            .planExecutionId(generateUuid())
            .concurrentChildInstance(
                ConcurrentChildInstance.builder().cursor(4).childrenNodeExecutionIds(childrenNodeExecutionIds).build())
            .build();
    mongoTemplate.save(nodeExecutionsInfo);
    ConcurrentChildInstance concurrentChildInstance =
        pmsGraphStepDetailsService.fetchConcurrentChildInstance(nodeExecutionId);
    assertEquals(concurrentChildInstance.getCursor(), 4);
    assertEquals(concurrentChildInstance.getChildrenNodeExecutionIds().get(0), "ID1");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteNodeExecutionInfoForGivenIds() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .uuid(generateUuid())
                                                .planExecutionId(generateUuid())
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);

    Optional<NodeExecutionsInfo> byNodeExecutionId =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isPresent();

    pmsGraphStepDetailsService.deleteNodeExecutionInfoForGivenIds(Set.of(nodeExecutionId));
    byNodeExecutionId = nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isNotPresent();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateTTLForNodeExecutionInfoForGivenPlanExecutionId() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .uuid(generateUuid())
                                                .planExecutionId(planExecutionId)
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);
    Optional<NodeExecutionsInfo> byNodeExecutionId =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isPresent();

    Date ttlExpiry = Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(30)).toInstant());
    pmsGraphStepDetailsService.updateTTLForNodesForGivenPlanExecutionId(planExecutionId, ttlExpiry);
    byNodeExecutionId = nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isPresent();
    assertThat(byNodeExecutionId.get().getValidUntil()).isEqualTo(ttlExpiry);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFetchStrategyMetadataWithNull() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .uuid(generateUuid())
                                                .planExecutionId(generateUuid())
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);

    Optional<NodeExecutionsInfo> byNodeExecutionId =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isPresent();

    Map<String, Object> result = pmsGraphStepDetailsService.fetchStrategyObjectMap(nodeExecutionId);
    assertThat(result.keySet().size()).isEqualTo(3);
    assertThat(result.get(StrategyConstants.TOTAL_ITERATIONS)).isEqualTo(1);
    assertThat(result.get(StrategyConstants.ITERATION)).isEqualTo(0);
    assertThat(result.get(StrategyConstants.ITERATIONS)).isEqualTo(1);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFetchStrategyMetadataWithMatrixStrategyMetadata() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder()
            .nodeExecutionId(nodeExecutionId)
            .uuid(generateUuid())
            .planExecutionId(generateUuid())
            .strategyMetadata(
                StrategyMetadata.newBuilder()
                    .setMatrixMetadata(
                        MatrixMetadata.newBuilder().putMatrixValues("a", "test").addMatrixCombination(0).build())
                    .setCurrentIteration(0)
                    .setTotalIterations(1)
                    .setIdentifierPostFix("_0")
                    .build())
            .build();
    mongoTemplate.save(nodeExecutionsInfo);

    Optional<NodeExecutionsInfo> byNodeExecutionId =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isPresent();

    Map<String, Object> result = pmsGraphStepDetailsService.fetchStrategyObjectMap(nodeExecutionId);
    assertThat(result.keySet().size()).isEqualTo(6);
    assertThat(result.get(StrategyConstants.TOTAL_ITERATIONS)).isEqualTo(1);
    assertThat(result.get(StrategyConstants.ITERATION)).isEqualTo(0);
    assertThat(result.get(StrategyConstants.ITERATIONS)).isEqualTo(1);
    assertThat(result.get(StrategyConstants.REPEAT)).isEqualTo(new HashMap<>());
    assertThat(result.get(StrategyConstants.MATRIX)).isEqualTo(Map.of("a", "test"));
    assertThat(result.get("identifierPostFix")).isEqualTo("_0");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFetchStrategyMetadataWithMatrixStrategyMetadataEnableMatrixName() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder()
            .nodeExecutionId(nodeExecutionId)
            .uuid(generateUuid())
            .planExecutionId(generateUuid())
            .strategyMetadata(
                StrategyMetadata.newBuilder()
                    .setMatrixMetadata(
                        MatrixMetadata.newBuilder().putMatrixValues("a", "test").addMatrixCombination(0).build())
                    .setCurrentIteration(0)
                    .setTotalIterations(1)
                    .setIdentifierPostFix("_test")
                    .build())
            .build();
    mongoTemplate.save(nodeExecutionsInfo);

    Optional<NodeExecutionsInfo> byNodeExecutionId =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isPresent();

    Map<String, Object> result = pmsGraphStepDetailsService.fetchStrategyObjectMap(nodeExecutionId);
    assertThat(result.keySet().size()).isEqualTo(6);
    assertThat(result.get(StrategyConstants.TOTAL_ITERATIONS)).isEqualTo(1);
    assertThat(result.get(StrategyConstants.ITERATION)).isEqualTo(0);
    assertThat(result.get(StrategyConstants.ITERATIONS)).isEqualTo(1);
    assertThat(result.get(StrategyConstants.REPEAT)).isEqualTo(new HashMap<>());
    assertThat(result.get(StrategyConstants.MATRIX)).isEqualTo(Map.of("a", "test"));
    assertThat(result.get("identifierPostFix")).isEqualTo("_test");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFetchStrategyMetadataWithMatrixStrategyMetadataMatrixnull() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo =
        NodeExecutionsInfo.builder()
            .nodeExecutionId(nodeExecutionId)
            .uuid(generateUuid())
            .planExecutionId(generateUuid())
            .strategyMetadata(StrategyMetadata.newBuilder()
                                  .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(0).build())
                                  .setCurrentIteration(0)
                                  .setTotalIterations(1)
                                  .setIdentifierPostFix("_")
                                  .build())
            .build();
    mongoTemplate.save(nodeExecutionsInfo);

    Optional<NodeExecutionsInfo> byNodeExecutionId =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    assertThat(byNodeExecutionId).isPresent();

    Map<String, Object> result = pmsGraphStepDetailsService.fetchStrategyObjectMap(nodeExecutionId);
    assertThat(result.keySet().size()).isEqualTo(6);
    assertThat(result.get(StrategyConstants.TOTAL_ITERATIONS)).isEqualTo(1);
    assertThat(result.get(StrategyConstants.ITERATION)).isEqualTo(0);
    assertThat(result.get(StrategyConstants.ITERATIONS)).isEqualTo(1);
    assertThat(result.get(StrategyConstants.REPEAT)).isEqualTo(new HashMap<>());
    assertThat(result.get(StrategyConstants.MATRIX)).isEqualTo(new HashMap<>());
    assertThat(result.get("identifierPostFix")).isEqualTo("_");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetCurrentStatus_WithCachedStatus() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .planExecutionId(generateUuid())
                                                .currentStatus(Status.FAILED)
                                                .failedChildIdChain("non-empty")
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);

    Optional<Status> result = pmsGraphStepDetailsService.getCurrentStatus(nodeExecutionId);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetCurrentStatus_WithoutCachedStatus() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();

    Optional<Status> result = pmsGraphStepDetailsService.getCurrentStatus(nodeExecutionId);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetCurrentStatus_NodeExecutionNotFound() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();

    Optional<Status> result = pmsGraphStepDetailsService.getCurrentStatus(nodeExecutionId);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateCalculatedStatusForParentNodes_FeatureFlagDisabled() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    // Build ambiance without feature flag
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build();

    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).status(Status.FAILED).ambiance(ambiance).build();

    on(pmsGraphStepDetailsService).set("nodeExecutionService", nodeExecutionService);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);

    // Should not update anything since FF is disabled
    pmsGraphStepDetailsService.updateCalculatedStatusForParentNodes(nodeExecution);

    // Method should return early without processing
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateCalculatedStatusForParentNodes_NonFinalStatus() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    // Build ambiance with feature flag
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(FeatureName.PIPE_CACHE_CURRENT_STATUS.name(), true)
                             .build())
            .build();

    // Status is RUNNING (not final), so should not update
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid(nodeExecutionId).status(Status.RUNNING).ambiance(ambiance).build();

    on(pmsGraphStepDetailsService).set("nodeExecutionService", nodeExecutionService);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);

    pmsGraphStepDetailsService.updateCalculatedStatusForParentNodes(nodeExecution);

    // Method should return early since status is not final

    // Status is IGNORE_FAILED (final), so should not update
    NodeExecution nodeExecution1 =
        NodeExecution.builder().uuid(nodeExecutionId).status(Status.IGNORE_FAILED).ambiance(ambiance).build();

    pmsGraphStepDetailsService.updateCalculatedStatusForParentNodes(nodeExecution);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateCalculatedStatusForParentNodes_IgnoreFailureAdvisorDoNotUpdate() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    on(pmsGraphStepDetailsService).set("nodeExecutionService", nodeExecutionService);
    on(pmsGraphStepDetailsService).set("mongoTemplate", mongoTemplate);

    String stepNodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    // Build levels: Pipeline -> Stage -> Step
    Level pipelineLevel = Level.newBuilder()
                              .setRuntimeId(generateUuid())
                              .setSetupId(generateUuid())
                              .setGroup("PIPELINE")
                              .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                              .build();

    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setSetupId(generateUuid())
                           .setGroup("STAGE")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build();

    Level stepLevel = Level.newBuilder()
                          .setRuntimeId(stepNodeExecutionId)
                          .setSetupId(generateUuid())
                          .setGroup("STEP")
                          .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                          .build();

    // Build ambiance with feature flags
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(FeatureName.PIPE_CACHE_CURRENT_STATUS.name(), true)
                             .build())
            .addLevels(pipelineLevel)
            .addLevels(stageLevel)
            .addLevels(stepLevel)
            .build();

    NodeExecution stepNodeExecution =
        NodeExecution.builder()
            .uuid(stepNodeExecutionId)
            .status(Status.FAILED)
            .mode(ExecutionMode.SYNC)
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
            .advisorsProcessed(true)
            .adviserResponse(AdviserResponse.newBuilder().setType(AdviseType.IGNORE_FAILURE).build())
            .ambiance(ambiance)
            .build();

    // Save stage nodeExecutionInfo
    NodeExecutionsInfo stageNodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                     .nodeExecutionId(stageNodeExecutionId)
                                                     .planExecutionId(planExecutionId)
                                                     .currentStatus(Status.SUCCEEDED)
                                                     .build();
    mongoTemplate.save(stageNodeExecutionsInfo);

    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);

    pmsGraphStepDetailsService.updateCalculatedStatusForParentNodes(stepNodeExecution);

    // Verify stage status was updated to FAILED
    NodeExecutionsInfo updatedStageInfo = mongoTemplate.findOne(
        new org.springframework.data.mongodb.core.query.Query(
            org.springframework.data.mongodb.core.query.Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId)
                .is(stageNodeExecutionId)),
        NodeExecutionsInfo.class);

    assertThat(updatedStageInfo).isNotNull();
    assertThat(updatedStageInfo.getCurrentStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateCalculatedStatusForParentNodes_MarkSuccessAdvisorDoNotUpdate() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    on(pmsGraphStepDetailsService).set("nodeExecutionService", nodeExecutionService);
    on(pmsGraphStepDetailsService).set("mongoTemplate", mongoTemplate);

    String stepNodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    // Build levels: Pipeline -> Stage -> Step
    Level pipelineLevel = Level.newBuilder()
                              .setRuntimeId(generateUuid())
                              .setSetupId(generateUuid())
                              .setGroup("PIPELINE")
                              .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                              .build();

    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setSetupId(generateUuid())
                           .setGroup("STAGE")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build();

    Level stepLevel = Level.newBuilder()
                          .setRuntimeId(stepNodeExecutionId)
                          .setSetupId(generateUuid())
                          .setGroup("STEP")
                          .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                          .build();

    // Build ambiance with feature flags
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(FeatureName.PIPE_CACHE_CURRENT_STATUS.name(), true)
                             .build())
            .addLevels(pipelineLevel)
            .addLevels(stageLevel)
            .addLevels(stepLevel)
            .build();

    NodeExecution stepNodeExecution =
        NodeExecution.builder()
            .uuid(stepNodeExecutionId)
            .status(Status.FAILED)
            .mode(ExecutionMode.SYNC)
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
            .advisorsProcessed(true)
            .adviserResponse(AdviserResponse.newBuilder().setType(AdviseType.MARK_SUCCESS).build())
            .ambiance(ambiance)
            .build();

    // Save stage nodeExecutionInfo
    NodeExecutionsInfo stageNodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                     .nodeExecutionId(stageNodeExecutionId)
                                                     .planExecutionId(planExecutionId)
                                                     .currentStatus(Status.SUCCEEDED)
                                                     .build();
    mongoTemplate.save(stageNodeExecutionsInfo);

    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);

    pmsGraphStepDetailsService.updateCalculatedStatusForParentNodes(stepNodeExecution);

    // Verify stage status was updated to FAILED
    NodeExecutionsInfo updatedStageInfo = mongoTemplate.findOne(
        new org.springframework.data.mongodb.core.query.Query(
            org.springframework.data.mongodb.core.query.Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId)
                .is(stageNodeExecutionId)),
        NodeExecutionsInfo.class);

    assertThat(updatedStageInfo).isNotNull();
    assertThat(updatedStageInfo.getCurrentStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateCalculatedStatusForParentNodes_ManualInterventionAdvisorDoNotUpdate() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    on(pmsGraphStepDetailsService).set("nodeExecutionService", nodeExecutionService);
    on(pmsGraphStepDetailsService).set("mongoTemplate", mongoTemplate);

    String stepNodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    // Build levels: Pipeline -> Stage -> Step
    Level pipelineLevel = Level.newBuilder()
                              .setRuntimeId(generateUuid())
                              .setSetupId(generateUuid())
                              .setGroup("PIPELINE")
                              .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                              .build();

    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setSetupId(generateUuid())
                           .setGroup("STAGE")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build();

    Level stepLevel = Level.newBuilder()
                          .setRuntimeId(stepNodeExecutionId)
                          .setSetupId(generateUuid())
                          .setGroup("STEP")
                          .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                          .build();

    // Build ambiance with feature flags
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(FeatureName.PIPE_CACHE_CURRENT_STATUS.name(), true)
                             .build())
            .addLevels(pipelineLevel)
            .addLevels(stageLevel)
            .addLevels(stepLevel)
            .build();

    NodeExecution stepNodeExecution =
        NodeExecution.builder()
            .uuid(stepNodeExecutionId)
            .status(Status.FAILED)
            .mode(ExecutionMode.SYNC)
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
            .advisorsProcessed(true)
            .adviserResponse(AdviserResponse.newBuilder().setType(AdviseType.INTERVENTION_WAIT).build())
            .ambiance(ambiance)
            .build();

    // Save stage nodeExecutionInfo
    NodeExecutionsInfo stageNodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                     .nodeExecutionId(stageNodeExecutionId)
                                                     .planExecutionId(planExecutionId)
                                                     .currentStatus(Status.SUCCEEDED)
                                                     .build();
    mongoTemplate.save(stageNodeExecutionsInfo);

    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);

    pmsGraphStepDetailsService.updateCalculatedStatusForParentNodes(stepNodeExecution);

    // Verify stage status was updated to FAILED
    NodeExecutionsInfo updatedStageInfo = mongoTemplate.findOne(
        new org.springframework.data.mongodb.core.query.Query(
            org.springframework.data.mongodb.core.query.Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId)
                .is(stageNodeExecutionId)),
        NodeExecutionsInfo.class);

    assertThat(updatedStageInfo).isNotNull();
    assertThat(updatedStageInfo.getCurrentStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdateCalculatedStatusForParentNodes_LeafNodeWithStage() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    on(pmsGraphStepDetailsService).set("nodeExecutionService", nodeExecutionService);
    on(pmsGraphStepDetailsService).set("mongoTemplate", mongoTemplate);

    String stepNodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    // Build levels: Pipeline -> Stage -> Step
    Level pipelineLevel = Level.newBuilder()
                              .setRuntimeId(generateUuid())
                              .setSetupId(generateUuid())
                              .setGroup("PIPELINE")
                              .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                              .build();

    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setSetupId(generateUuid())
                           .setGroup("STAGE")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build();

    Level stepLevel = Level.newBuilder()
                          .setRuntimeId(stepNodeExecutionId)
                          .setSetupId(generateUuid())
                          .setGroup("STEP")
                          .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                          .build();

    // Build ambiance with feature flags
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(FeatureName.PIPE_CACHE_CURRENT_STATUS.name(), true)
                             .build())
            .addLevels(pipelineLevel)
            .addLevels(stageLevel)
            .addLevels(stepLevel)
            .build();

    NodeExecution stepNodeExecution = NodeExecution.builder()
                                          .uuid(stepNodeExecutionId)
                                          .status(Status.FAILED)
                                          .mode(io.harness.pms.contracts.execution.ExecutionMode.SYNC)
                                          .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                          .advisorsProcessed(true)
                                          .ambiance(ambiance)
                                          .build();

    // Save stage nodeExecutionInfo
    NodeExecutionsInfo stageNodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                     .nodeExecutionId(stageNodeExecutionId)
                                                     .planExecutionId(planExecutionId)
                                                     .currentStatus(Status.SUCCEEDED)
                                                     .build();
    mongoTemplate.save(stageNodeExecutionsInfo);

    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);

    pmsGraphStepDetailsService.updateCalculatedStatusForParentNodes(stepNodeExecution);

    // Verify stage status was updated to FAILED
    NodeExecutionsInfo updatedStageInfo = mongoTemplate.findOne(
        new org.springframework.data.mongodb.core.query.Query(
            org.springframework.data.mongodb.core.query.Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId)
                .is(stageNodeExecutionId)),
        NodeExecutionsInfo.class);

    assertThat(updatedStageInfo).isNotNull();
    assertThat(updatedStageInfo.getCurrentStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testClearFirstUnsuccessfulRuntimeIdChain() {
    on(pmsGraphStepDetailsService).set("mongoTemplate", mongoTemplate);
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    // Save nodeExecutionInfo with firstUnsuccessfulRuntimeIdChain set
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .planExecutionId(planExecutionId)
                                                .currentStatus(Status.FAILED)
                                                .failedChildIdChain("step1.stepGroup1")
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);

    // Clear the field
    pmsGraphStepDetailsService.clearFirstUnsuccessfulRuntimeIdChain(nodeExecutionId);

    // Verify it was cleared
    NodeExecutionsInfo updatedInfo =
        mongoTemplate.findOne(new Query(Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId)),
            NodeExecutionsInfo.class);

    assertThat(updatedInfo).isNotNull();
    assertThat(updatedInfo.getFailedChildIdChain()).isNull();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetCurrentStatus_WithFirstUnsuccessfulRuntimeIdChain() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .planExecutionId(generateUuid())
                                                .currentStatus(Status.FAILED)
                                                .failedChildIdChain("step1.stepGroup1")
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);

    Optional<Status> result = pmsGraphStepDetailsService.getCurrentStatus(nodeExecutionId);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetCurrentStatus_WithNullFirstUnsuccessfulRuntimeIdChain_ReturnsSucceeded() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    String nodeExecutionId = generateUuid();
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .planExecutionId(generateUuid())
                                                .currentStatus(Status.FAILED)
                                                .failedChildIdChain(null)
                                                .build();
    mongoTemplate.save(nodeExecutionsInfo);

    Optional<Status> result = pmsGraphStepDetailsService.getCurrentStatus(nodeExecutionId);

    // When firstUnsuccessfulRuntimeIdChain is null, it returns SUCCEEDED regardless of currentStatus
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateCalculatedStatusForParentNodes_AdvisorsNotProcessed_ShouldNotUpdate() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    on(pmsGraphStepDetailsService).set("nodeExecutionService", nodeExecutionService);
    on(pmsGraphStepDetailsService).set("mongoTemplate", mongoTemplate);

    String stepNodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    Level pipelineLevel = Level.newBuilder()
                              .setRuntimeId(generateUuid())
                              .setSetupId(generateUuid())
                              .setGroup("PIPELINE")
                              .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                              .build();

    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setSetupId(generateUuid())
                           .setGroup("STAGE")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build();

    Level stepLevel = Level.newBuilder()
                          .setRuntimeId(stepNodeExecutionId)
                          .setSetupId(generateUuid())
                          .setGroup("STEP")
                          .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                          .build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(FeatureName.PIPE_CACHE_CURRENT_STATUS.name(), true)
                             .build())
            .addLevels(pipelineLevel)
            .addLevels(stageLevel)
            .addLevels(stepLevel)
            .build();

    // advisorsProcessed is false
    NodeExecution stepNodeExecution = NodeExecution.builder()
                                          .uuid(stepNodeExecutionId)
                                          .status(Status.FAILED)
                                          .mode(io.harness.pms.contracts.execution.ExecutionMode.SYNC)
                                          .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                          .advisorsProcessed(false)
                                          .ambiance(ambiance)
                                          .build();

    NodeExecutionsInfo stageNodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                     .nodeExecutionId(stageNodeExecutionId)
                                                     .planExecutionId(planExecutionId)
                                                     .currentStatus(Status.SUCCEEDED)
                                                     .build();
    mongoTemplate.save(stageNodeExecutionsInfo);

    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);

    pmsGraphStepDetailsService.updateCalculatedStatusForParentNodes(stepNodeExecution);

    // Verify stage status was NOT updated because advisors are not processed
    NodeExecutionsInfo updatedStageInfo = mongoTemplate.findOne(
        new Query(Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(stageNodeExecutionId)),
        NodeExecutionsInfo.class);

    assertThat(updatedStageInfo).isNotNull();
    assertThat(updatedStageInfo.getCurrentStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testUpdateCalculatedStatusForParentNodes_UpdatesWhenStageCurrentStatusIsNull() {
    on(pmsGraphStepDetailsService).set("nodeExecutionsInfoRepository", nodeExecutionsInfoRepository);
    on(pmsGraphStepDetailsService).set("nodeExecutionService", nodeExecutionService);
    on(pmsGraphStepDetailsService).set("mongoTemplate", mongoTemplate);

    String stepNodeExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    // Build levels: Pipeline -> Stage -> Step
    Level pipelineLevel = Level.newBuilder()
                              .setRuntimeId(generateUuid())
                              .setSetupId(generateUuid())
                              .setGroup("PIPELINE")
                              .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                              .build();

    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setSetupId(generateUuid())
                           .setGroup("STAGE")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build();

    Level stepLevel = Level.newBuilder()
                          .setRuntimeId(stepNodeExecutionId)
                          .setSetupId(generateUuid())
                          .setGroup("STEP")
                          .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                          .build();

    // Build ambiance with feature flags
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(FeatureName.PIPE_CACHE_CURRENT_STATUS.name(), true)
                             .build())
            .addLevels(pipelineLevel)
            .addLevels(stageLevel)
            .addLevels(stepLevel)
            .build();

    NodeExecution stepNodeExecution = NodeExecution.builder()
                                          .uuid(stepNodeExecutionId)
                                          .status(Status.FAILED)
                                          .mode(ExecutionMode.SYNC)
                                          .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                          .advisorsProcessed(true)
                                          .ambiance(ambiance)
                                          .build();

    // Save stage nodeExecutionInfo WITHOUT a currentStatus set (null).
    NodeExecutionsInfo stageNodeExecutionsInfo =
        NodeExecutionsInfo.builder().nodeExecutionId(stageNodeExecutionId).planExecutionId(planExecutionId).build();
    mongoTemplate.save(stageNodeExecutionsInfo);

    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);

    pmsGraphStepDetailsService.updateCalculatedStatusForParentNodes(stepNodeExecution);

    // Previously, a null currentStatus made fetchCalculatedStatus return empty and skip the update.
    // Now the update should proceed, defaulting the missing currentStatus to SUCCEEDED before recalculating.
    NodeExecutionsInfo updatedStageInfo = mongoTemplate.findOne(
        new Query(Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(stageNodeExecutionId)),
        NodeExecutionsInfo.class);

    assertThat(updatedStageInfo).isNotNull();
    assertThat(updatedStageInfo.getCurrentStatus()).isEqualTo(Status.FAILED);
  }
}
