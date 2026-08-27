/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.executions.node.detection;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.StatusUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GraphLookupOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Scheduled detector that identifies stuck pipeline executions.
 * Uses MongoDB $graphLookup for recursive children fetching and processes by planExecutionId batches.
 * Runs periodically to check for executions that are not making progress.
 */
@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class StuckExecutionDetector implements Managed {
  @Inject private StuckExecutionDetectionService detectionService;
  @Inject @Named("secondary-mongo") private MongoTemplate secondaryMongoTemplate;
  @Inject private MetricService metricService;
  @Inject private PersistentLocker persistentLocker;

  // Metrics
  private static final String STUCK_EXECUTION_DETECTED = "stuck_execution_detected";
  private static final String STUCK_EXECUTION_DETECTION_DURATION = "stuck_execution_detection_duration";

  // Configuration
  private static final Duration TRACKING_EXPIRATION = Duration.ofHours(1);
  private static final int MAX_TRACKED_EXECUTIONS = 1000;
  private static final int PLAN_EXECUTION_BATCH_SIZE = 20;
  private static final Duration DETECTION_INTERVAL = Duration.ofMinutes(30);
  private static final long INITIAL_DELAY_MINUTES = 5;
  private static final Duration TIME_WINDOW_START = Duration.ofHours(3);
  private static final Duration TIME_WINDOW_END = Duration.ofMinutes(30);

  private final ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                                                     .setNameFormat("stuck-execution-detector-%d")
                                                     .setPriority(Thread.NORM_PRIORITY)
                                                     .build());

  // Cache to track alerted executions (deduplication)
  private final Cache<String, Boolean> alertedExecutions =
      Caffeine.newBuilder().maximumSize(MAX_TRACKED_EXECUTIONS).expireAfterWrite(TRACKING_EXPIRATION).build();

  @Override
  public void start() throws Exception {
    log.info("Starting StuckExecutionDetector with {} minute interval and time window {} hours to {} minutes",
        DETECTION_INTERVAL.toMinutes(), TIME_WINDOW_START.toHours(), TIME_WINDOW_END.toMinutes());
    executorService.scheduleWithFixedDelay(
        this::detectStuckExecutions, INITIAL_DELAY_MINUTES, DETECTION_INTERVAL.toMinutes(), TimeUnit.MINUTES);
    log.info("StuckExecutionDetector started successfully");
  }

  @Override
  public void stop() throws Exception {
    log.info("Stopping StuckExecutionDetector");
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
    log.info("StuckExecutionDetector stopped successfully");
  }

  /**
   * Main detection cycle that runs periodically.
   * Algorithm:
   * 1. Get all distinct planExecutionIds from running nodes in time window
   * 2. Process in batches of 20 planExecutionIds
   * 3. For each batch, use $graphLookup to recursively fetch all children
   * 4. Apply stuck detection logic to each node
   */
  @VisibleForTesting
  public void detectStuckExecutions() {
    // Acquire distributed lock with 30-minute TTL (do NOT use try-with-resources to prevent auto-release)
    AcquiredLock lock = null;
    try {
      lock = persistentLocker.acquireLock("StuckExecutionDetector-Global", DETECTION_INTERVAL);

      if (lock == null) {
        log.info("Failed to acquire lock for StuckExecutionDetector. Another pod is running detection.");
        return;
      }

      log.info("Starting stuck execution detection cycle with planExecution batch size {}", PLAN_EXECUTION_BATCH_SIZE);
      long startTime = Instant.now().toEpochMilli();

      int totalProcessed = 0;
      int stuckCount = 0;
      int batchNumber = 0;

      // Step 1: Get all distinct planExecutionIds from running nodes in time window
      List<String> planExecutionIds = fetchDistinctPlanExecutionIds();
      log.info("Found {} distinct plan executions to check", planExecutionIds.size());

      if (planExecutionIds.isEmpty()) {
        log.info("No plan executions found in time window");
        return;
      }

      // Step 2: Process planExecutionIds in batches
      for (int i = 0; i < planExecutionIds.size(); i += PLAN_EXECUTION_BATCH_SIZE) {
        int endIndex = Math.min(i + PLAN_EXECUTION_BATCH_SIZE, planExecutionIds.size());
        List<String> batch = planExecutionIds.subList(i, endIndex);
        batchNumber++;

        BatchProcessingResult result = processPlanExecutionBatch(batch, batchNumber);

        totalProcessed += result.totalProcessed();
        stuckCount += result.stuckCount();
      }

      var duration = Duration.ofMillis(Instant.now().toEpochMilli() - startTime);
      log.info("Stuck execution detection cycle complete. Duration: {}ms, Batches: {}, PlanExecutions: {}, "
              + "NodesProcessed: {}, Stuck: {}",
          duration, batchNumber, planExecutionIds.size(), totalProcessed, stuckCount);

      // Publish cycle duration metric
      publishDurationMetric(duration);

    } catch (Exception e) {
      log.error("Error in stuck execution detection cycle", e);
    }
  }

  /**
   * Fetch distinct planExecutionIds from running nodes created in the time window.
   * Time window: nodes created between 3 hours and 30 minutes ago.
   */
  @VisibleForTesting
  List<String> fetchDistinctPlanExecutionIds() {
    Query query = new Query();
    long currentTime = System.currentTimeMillis();
    long timeWindowStart = currentTime - TIME_WINDOW_START.toMillis();
    long timeWindowEnd = currentTime - TIME_WINDOW_END.toMillis();

    query.addCriteria(where(NodeExecutionKeys.status)
                          .in(StatusUtils.resumableStatuses())
                          .and(NodeExecutionKeys.createdAt)
                          .gte(timeWindowStart)
                          .lte(timeWindowEnd));

    // Project only planExecutionId field
    query.fields().include(NodeExecutionKeys.planExecutionId);

    return secondaryMongoTemplate.findDistinct(
        query, NodeExecutionKeys.planExecutionId, NodeExecution.class, String.class);
  }

  /**
   * Process a batch of planExecutionIds.
   * For each batch, fetch all nodes with their recursive children using $graphLookup,
   * then apply stuck detection logic.
   */
  private BatchProcessingResult processPlanExecutionBatch(List<String> planExecutionIds, int batchNumber) {
    int totalProcessed = 0;
    int stuckCount = 0;

    // Fetch all nodes with their recursive children for this batch of planExecutionIds
    List<NodeExecutionWithChildren> nodesWithChildren =
        fetchNodesWithChildrenForPlanExecutions(planExecutionIds, batchNumber);

    // Filter to only process "root" parent nodes - nodes whose parent is NOT also in the result set.
    // This avoids redundant processing since children are handled via recursion.
    // Example: If A -> B -> C and both A and B are parent nodes, we only process A.
    // B will be analyzed recursively when we process A.
    Set<String> allNodeIds =
        nodesWithChildren.stream().map(nwc -> nwc.nodeExecution().getUuid()).collect(Collectors.toSet());

    List<NodeExecutionWithChildren> rootNodes =
        nodesWithChildren.stream()
            .filter(nwc -> !allNodeIds.contains(nwc.nodeExecution().getParentId()))
            .collect(Collectors.toList());

    log.debug("Batch {}: {} total parent nodes, {} root nodes to process", batchNumber, nodesWithChildren.size(),
        rootNodes.size());

  // Process only root nodes - children are handled via recursion
  outer:
    for (NodeExecutionWithChildren nodeWithChildren : rootNodes) {
      totalProcessed++;

      NodeExecution node = nodeWithChildren.nodeExecution();

      // Build children map for this node (contains ALL descendants grouped by parentId)
      Map<String, List<NodeExecution>> childrenByParentId = buildChildrenMap(nodeWithChildren);

      // Analyze the node execution
      // The childrenByParentId map contains ALL descendants (any status) so the service can
      // distinguish "no children" (stuck) from "all children completed" (not stuck)
      StuckExecutionResult nodeAnalysisResult = detectionService.analyzeNodeExecution(node, childrenByParentId);

      switch (nodeAnalysisResult.getCategory()) {
        case STUCK:
          handleStuck(nodeAnalysisResult);
          stuckCount++;
          break outer;
        case POSSIBLY_NOT_STUCK:
        case NOT_STUCK:
          // No action needed
          break;
        default:
          log.warn("Unhandled stuck execution category: {}", nodeAnalysisResult.getCategory());
      }
    }

    return new BatchProcessingResult(totalProcessed, stuckCount);
  }

  /**
   * Fetch all running nodes with their recursive children for a batch of planExecutionIds.
   * Uses MongoDB $graphLookup to recursively fetch all descendants in a single query.
   * The graphLookup fetches ALL descendants (any status) so the detection service can
   * distinguish between "no children at all" (stuck) vs "all children completed" (not stuck).
   */
  @VisibleForTesting
  List<NodeExecutionWithChildren> fetchNodesWithChildrenForPlanExecutions(
      List<String> planExecutionIds, int batchNumber) {
    // Match criteria: running nodes in the given planExecutionIds and parent modes
    Criteria matchCriteria = where(NodeExecutionKeys.planExecutionId)
                                 .in(planExecutionIds)
                                 .and(NodeExecutionKeys.status)
                                 .in(StatusUtils.resumableStatuses())
                                 .and(NodeExecutionKeys.mode)
                                 .in(ExecutionModeUtils.parentModes());

    // GraphLookup to recursively fetch ALL descendants (any status)
    // Searches from current node's _id, connecting to children's parentId field
    // Note: We use "_id" directly because MongoDB stores the ID as "_id", not "uuid"
    // No status restriction - we need to see all descendants to distinguish
    // "no children" (stuck) from "all children completed" (not stuck)
    GraphLookupOperation graphLookup = Aggregation.graphLookup("nodeExecutions")
                                           .startWith("$_id")
                                           .connectFrom("_id")
                                           .connectTo(NodeExecutionKeys.parentId)
                                           .as("descendants");

    // Build aggregation pipeline:
    // 1. Match parent nodes in resumable statuses
    // 2. GraphLookup to get ALL descendants (any status)
    Aggregation aggregation = Aggregation.newAggregation(Aggregation.match(matchCriteria), graphLookup);

    AggregationResults<Map> results = secondaryMongoTemplate.aggregate(aggregation, "nodeExecutions", Map.class);

    // Convert Map results to NodeExecutionWithChildren
    return results.getMappedResults().stream().map(this::mapToNodeExecutionWithChildren).collect(Collectors.toList());
  }

  /**
   * Convert MongoDB aggregation result (Map) to NodeExecutionWithChildren.
   * The Map contains all NodeExecution fields plus the descendants array.
   * Note: Spring Data MongoDB may auto-deserialize nested documents to NodeExecution objects,
   * so we handle both cases (List of NodeExecution and List of Map).
   */
  @SuppressWarnings("unchecked")
  private NodeExecutionWithChildren mapToNodeExecutionWithChildren(Map<String, Object> doc) {
    // Convert the document to NodeExecution (excluding descendants field)
    Map<String, Object> nodeDoc = new HashMap<>(doc);
    nodeDoc.remove("descendants");

    NodeExecution node = secondaryMongoTemplate.getConverter().read(NodeExecution.class, new Document(nodeDoc));

    // Extract descendants list (all children from graphLookup, any status)
    Object descendantsObj = doc.get("descendants");
    List<NodeExecution> descendants = Collections.emptyList();

    if (descendantsObj instanceof List) {
      List<?> list = (List<?>) descendantsObj;
      if (!list.isEmpty()) {
        Object firstElement = list.get(0);
        if (firstElement instanceof NodeExecution) {
          // Spring Data MongoDB already deserialized to NodeExecution objects
          descendants = (List<NodeExecution>) descendantsObj;
        } else if (firstElement instanceof Map) {
          // Need to convert from Map to NodeExecution
          descendants = list.stream()
                            .map(descDoc
                                -> secondaryMongoTemplate.getConverter().read(
                                    NodeExecution.class, new Document((Map<String, Object>) descDoc)))
                            .collect(Collectors.toList());
        }
      }
    }

    return new NodeExecutionWithChildren(node, descendants);
  }

  /**
   * Build a map of children by parentId from the NodeExecutionWithChildren structure.
   * This converts the flat descendants list into a hierarchical map for the detection service.
   */
  private Map<String, List<NodeExecution>> buildChildrenMap(NodeExecutionWithChildren nodeWithChildren) {
    if (nodeWithChildren.descendants().isEmpty()) {
      return Collections.emptyMap();
    }

    // Group descendants by their parentId
    return nodeWithChildren.descendants().stream().collect(Collectors.groupingBy(NodeExecution::getParentId));
  }

  /**
   * Handle a definitively stuck execution.
   * Logs at WARN level, publishes metrics, and sends alert if not recently alerted.
   */
  private void handleStuck(StuckExecutionResult result) {
    publishMetrics(result);
    sendAlertIfNeeded(result);
  }

  /**
   * Publish metrics for a detected stuck execution.
   */
  private void publishMetrics(StuckExecutionResult result) {
    try (var ignore = new PmsMetricContextGuard(buildMetricContext(result))) {
      metricService.incCounter(STUCK_EXECUTION_DETECTED);
    }
  }

  /**
   * Publish detection cycle duration metric.
   */
  private void publishDurationMetric(Duration durationMillis) {
    try (var ignore = new PmsMetricContextGuard(new HashMap<>())) {
      metricService.recordDuration(STUCK_EXECUTION_DETECTION_DURATION, durationMillis);
    }
  }

  /**
   * Build metric context with relevant dimensions.
   */
  private Map<String, String> buildMetricContext(StuckExecutionResult result) {
    Map<String, String> context = new HashMap<>();
    context.put("category", result.getCategory().name());
    return context;
  }

  /**
   * Send alert if this execution hasn't been alerted recently (deduplication).
   */
  private void sendAlertIfNeeded(StuckExecutionResult result) {
    String key = result.getPlanExecutionId();
    if (Boolean.TRUE.equals(alertedExecutions.getIfPresent(key))) {
      return;
    }

    alertedExecutions.put(key, true);

    log.warn("STUCK EXECUTION ALERT: execution detected for reason={}: "
            + "https://app.harness.io/ng/account/{}/all/orgs/{}/projects/{}/pipelines/{}/deployments/{}",
        result.getReason(), result.getAccountId(), result.getOrgIdentifier(), result.getProjectIdentifier(),
        result.getPipelineIdentifier(), result.getPlanExecutionId());
  }

  /**
   * Result of processing a single batch of planExecutionIds.
   * Used to accumulate metrics across batches.
   */
  private record BatchProcessingResult(int totalProcessed, int stuckCount) {}

  /**
   * Wrapper class for a node execution with its recursive descendants.
   * Used to combine a parent node with all its descendants from $graphLookup.
   * @param nodeExecution The parent node execution
   * @param descendants List of ALL descendants (any status) - the detection service
   *                    filters by status to distinguish "no children" from "all children completed"
   */
  public record NodeExecutionWithChildren(NodeExecution nodeExecution, List<NodeExecution> descendants) {
    public NodeExecutionWithChildren(NodeExecution nodeExecution, List<NodeExecution> descendants) {
      this.nodeExecution = nodeExecution;
      this.descendants = descendants != null ? descendants : Collections.emptyList();
    }
  }
}
