/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.executions.node.detection;

import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Query;

public class StuckExecutionDetectorTest extends OrchestrationTestBase {
  private static final String PLAN_EXECUTION_ID = "planExec123";
  private static final String ACCOUNT_ID = "account123";

  // Create detection service manually since it has no dependencies
  private StuckExecutionDetectionServiceImpl detectionService;

  @Mock private MongoTemplate secondaryMongoTemplate;
  @Mock private MetricService metricService;
  @Mock private MongoConverter mongoConverter;
  @Mock private PersistentLocker persistentLocker;
  @Mock private AcquiredLock acquiredLock;

  private StuckExecutionDetector stuckExecutionDetector;

  @Before
  public void setup() {
    // Create detection service manually (no dependencies needed)
    detectionService = new StuckExecutionDetectionServiceImpl();

    // Create detector with real detection service
    stuckExecutionDetector = new StuckExecutionDetector();
    setField(stuckExecutionDetector, "detectionService", detectionService);
    setField(stuckExecutionDetector, "secondaryMongoTemplate", secondaryMongoTemplate);
    setField(stuckExecutionDetector, "metricService", metricService);
    setField(stuckExecutionDetector, "persistentLocker", persistentLocker);

    when(secondaryMongoTemplate.getConverter()).thenReturn(mongoConverter);
    when(persistentLocker.acquireLock(anyString(), any(Duration.class))).thenReturn(acquiredLock);
  }

  /**
   * Test: PlanExecution with 3 nodes where Async Step has RECENTLY completed (SUCCEEDED).
   *
   * Hierarchical structure:
   * - Stage (CHILD mode, RUNNING status)
   *   └─ Step (CHILD mode, RUNNING status)
   *      └─ Async Step (ASYNC mode, SUCCEEDED status, completed 1 minute ago)
   *
   * Expected result: NOT_STUCK
   * Reason: Step has a child in final status (SUCCEEDED) that completed within the
   *         5-minute callback grace period, so it's completing normally.
   *         This tests the fix for false positive detection where containers with
   *         recently completed children were incorrectly flagged as stuck.
   *
   * Note: If the child completed MORE than 5 minutes ago and parent is still RUNNING,
   *       that would indicate a lost callback and the node would be correctly flagged as STUCK.
   */
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testDetectStuckExecution_AsyncStepSucceeded_ShouldNotBeStuck() {
    // Compute timestamps dynamically to avoid stale static values
    long currentTime = System.currentTimeMillis();
    long twoHoursAgo = currentTime - (2 * 60 * 60 * 1000); // 2h ago (within detection window)
    long oneMinuteAgo = currentTime - (60 * 1000); // 1 min ago (within callback grace period)

    // 1. Stage (parent container)
    NodeExecution stageNode = createNodeExecution("stage-uuid-1", PLAN_EXECUTION_ID, ACCOUNT_ID,
        null, // no parent (root)
        ExecutionMode.CHILD, Status.RUNNING, twoHoursAgo);

    // 2. Step (child of Stage)
    NodeExecution stepNode = createNodeExecution("step-uuid-1", PLAN_EXECUTION_ID, ACCOUNT_ID,
        "stage-uuid-1", // parent = stage
        ExecutionMode.CHILD, Status.RUNNING, twoHoursAgo);

    // 3. Async Step (child of Step) - SUCCEEDED recently, parent should complete normally
    // Use oneMinuteAgo so it's within the 5-minute callback grace period
    NodeExecution asyncStepNode = createNodeExecution("async-step-uuid-1", PLAN_EXECUTION_ID, ACCOUNT_ID,
        "step-uuid-1", // parent = step
        ExecutionMode.ASYNC, Status.SUCCEEDED, oneMinuteAgo);

    // ============================================================
    // MOCK: MongoDB findDistinct - returns the planExecutionId
    // ============================================================
    when(secondaryMongoTemplate.findDistinct(
             any(Query.class), eq(NodeExecutionKeys.planExecutionId), eq(NodeExecution.class), eq(String.class)))
        .thenReturn(Arrays.asList(PLAN_EXECUTION_ID));

    // ============================================================
    // MOCK: MongoDB aggregation with $graphLookup
    // Now returns ALL descendants (any status) so we can distinguish
    // "no children" from "all children completed"
    // ============================================================

    // Simulate the $graphLookup result for the Stage (parent node)
    // graphLookup returns the Stage with all descendants (Step and AsyncStep)
    Map<String, Object> stageDocument = createMongoDocument(stageNode);

    // Add descendants (Step and AsyncStep) to the Stage document
    // Both are included since graphLookup now fetches all descendants regardless of status
    List<Map<String, Object>> descendants =
        Arrays.asList(createMongoDocument(stepNode), createMongoDocument(asyncStepNode));
    stageDocument.put("descendants", descendants);

    AggregationResults<Map> aggregationResults = mockAggregationResults(Arrays.asList(stageDocument));

    when(secondaryMongoTemplate.aggregate(any(Aggregation.class), eq("nodeExecutions"), eq(Map.class)))
        .thenReturn(aggregationResults);

    // ============================================================
    // MOCK: MongoConverter to convert Map → NodeExecution
    // ============================================================

    // Convert the Stage document
    when(mongoConverter.read(eq(NodeExecution.class), any(Document.class))).thenAnswer(invocation -> {
      Document doc = invocation.getArgument(1);
      String uuid = doc.getString("uuid");

      if ("stage-uuid-1".equals(uuid)) {
        return stageNode;
      } else if ("step-uuid-1".equals(uuid)) {
        return stepNode;
      } else if ("async-step-uuid-1".equals(uuid)) {
        return asyncStepNode;
      }
      return null;
    });

    // ============================================================
    // EXECUTE: Run the detector (with real detection service!)
    // ============================================================
    stuckExecutionDetector.detectStuckExecutions();

    // Verify that NO stuck execution metric was published (node is completing normally)
    verify(metricService, times(0)).incCounter(eq("stuck_execution_detected"));

    // Verify that the detection duration metric was still published
    verify(metricService).recordDuration(eq("stuck_execution_detection_duration"), any(Duration.class));
  }

  /**
   * Creates a NodeExecution with the necessary fields for the test.
   */
  private NodeExecution createNodeExecution(String uuid, String planExecutionId, String accountId, String parentId,
      ExecutionMode mode, Status status, long createdAt) {
    return NodeExecution.builder()
        .uuid(uuid)
        .executionContext(ExecutionContext.newBuilder().putSetupAbstractions("accountId", accountId).build())
        .parentId(parentId)
        .mode(mode)
        .status(status)
        .createdAt(createdAt)
        .lastUpdatedAt(createdAt)
        .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
        .build();
  }

  /**
   * Creates a MongoDB document (Map) representing a NodeExecution.
   */
  private Map<String, Object> createMongoDocument(NodeExecution node) {
    Map<String, Object> doc = new HashMap<>();
    doc.put("uuid", node.getUuid());
    doc.put("planExecutionId", node.getPlanExecutionId());
    doc.put("accountId", node.getAccountId());
    doc.put("parentId", node.getParentId());
    doc.put("mode", node.getMode().name());
    doc.put("status", node.getStatus().name());
    doc.put("createdAt", node.getCreatedAt());
    doc.put("lastUpdatedAt", node.getLastUpdatedAt());
    return doc;
  }

  /**
   * Mocks the MongoDB AggregationResults.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private AggregationResults<Map> mockAggregationResults(List<Map<String, Object>> results) {
    return new AggregationResults(results, new Document());
  }

  /**
   * Helper to set private fields using reflection.
   */
  private void setField(Object target, String fieldName, Object value) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set field: " + fieldName, e);
    }
  }
}
