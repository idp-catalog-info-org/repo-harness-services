/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.executions.node.detection;

import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Unit tests for StuckExecutionDetectionServiceImpl.
 * Tests the detection logic for different node scenarios.
 */

public class StuckExecutionDetectionServiceImplTest extends CategoryTest {
  private static final String PLAN_EXECUTION_ID = "plan-123";
  private static final String ACCOUNT_ID = "account-123";
  private static final String ORG_ID = "org-123";
  private static final String PROJECT_ID = "project-123";
  private static final String PIPELINE_ID = "pipeline-123";

  private StuckExecutionDetectionServiceImpl detectionService;

  @Before
  public void setup() {
    detectionService = new StuckExecutionDetectionServiceImpl();
  }

  /**
   * Test Case: Leaf node with executable responses and advisorsProcessed = false
   *
   * Scenario:
   * - Node is a leaf (TASK mode)
   * - Node has executable responses
   * - Advisors have NOT been processed yet (advisorsProcessed = false)
   *
   * Expected: POSSIBLY_NOT_STUCK
   * Reason: The node might be waiting for advisors to process the responses
   */
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testLeafNodeWithExecutableResponses_AdvisorsNotProcessed_ShouldBePossiblyNotStuck() {
    // Given: A leaf node with executable responses and advisorsProcessed = false
    NodeExecution leafNode =
        NodeExecution.builder()
            .uuid("leaf-uuid-1")
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(PLAN_EXECUTION_ID)
                          .putSetupAbstractions("accountId", ACCOUNT_ID)
                          .putSetupAbstractions("orgIdentifier", ORG_ID)
                          .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                          .putSetupAbstractions("pipelineIdentifier", PIPELINE_ID)
                          .build())
            .mode(ExecutionMode.TASK) // Leaf mode
            .status(Status.RUNNING)
            .executableResponses(Collections.singletonList(
                ExecutableResponse.newBuilder().setAsync(AsyncExecutableResponse.newBuilder().build()).build()))
            .advisorsProcessed(false) // Advisors NOT processed yet
            .build();

    // When: Analyze the node
    StuckExecutionResult result = detectionService.analyzeNodeExecution(leafNode, new HashMap<>());

    // Then: Should be POSSIBLY_NOT_STUCK
    assertThat(result.getCategory()).isEqualTo(StuckExecutionCategory.POSSIBLY_NOT_STUCK);
    assertThat(result.getReason()).contains("advisors not yet processed");
    assertThat(result.getNodeExecutionId()).isEqualTo("leaf-uuid-1");
    assertThat(result.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
  }

  /**
   * Test Case: Leaf node with executable responses and advisorsProcessed = true
   *
   * Scenario:
   * - Node is a leaf (TASK mode)
   * - Node has executable responses
   * - Advisors have ALREADY been processed (advisorsProcessed = true)
   *
   * Expected: STUCK
   * Reason: Advisors were processed but node is still RUNNING with no progress
   */
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testLeafNodeWithExecutableResponses_AdvisorsProcessed_ShouldBeStuck() {
    // Given: A leaf node with executable responses and advisorsProcessed = true
    NodeExecution leafNode =
        NodeExecution.builder()
            .uuid("leaf-uuid-2")
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(PLAN_EXECUTION_ID)
                          .putSetupAbstractions("accountId", ACCOUNT_ID)
                          .putSetupAbstractions("orgIdentifier", ORG_ID)
                          .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                          .putSetupAbstractions("pipelineIdentifier", PIPELINE_ID)
                          .build())
            .mode(ExecutionMode.TASK) // Leaf mode
            .status(Status.RUNNING)
            .executableResponses(Collections.singletonList(
                ExecutableResponse.newBuilder().setAsync(AsyncExecutableResponse.newBuilder().build()).build()))
            .advisorsProcessed(true) // Advisors ALREADY processed
            .build();

    // When: Analyze the node
    StuckExecutionResult result = detectionService.analyzeNodeExecution(leafNode, new HashMap<>());

    // Then: Should be STUCK
    assertThat(result.getCategory()).isEqualTo(StuckExecutionCategory.STUCK);
    assertThat(result.getReason()).contains("advisors already processed");
    assertThat(result.getNodeExecutionId()).isEqualTo("leaf-uuid-2");
    assertThat(result.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
  }

  /**
   * Test Case: Leaf node with NO executable responses
   *
   * Scenario:
   * - Node is a leaf (TASK mode)
   * - Node has NO executable responses
   *
   * Expected: STUCK
   * Reason: No responses means the node is stuck waiting for something
   */
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testLeafNodeWithoutExecutableResponses_ShouldBeStuck() {
    // Given: A leaf node with NO executable responses
    NodeExecution leafNode = NodeExecution.builder()
                                 .uuid("leaf-uuid-3")
                                 .ambiance(Ambiance.newBuilder()
                                               .setPlanExecutionId(PLAN_EXECUTION_ID)
                                               .putSetupAbstractions("accountId", ACCOUNT_ID)
                                               .putSetupAbstractions("orgIdentifier", ORG_ID)
                                               .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                                               .putSetupAbstractions("pipelineIdentifier", PIPELINE_ID)
                                               .build())
                                 .mode(ExecutionMode.TASK) // Leaf mode
                                 .status(Status.RUNNING)
                                 .build();

    // When: Analyze the node
    StuckExecutionResult result = detectionService.analyzeNodeExecution(leafNode, new HashMap<>());

    // Then: Should be STUCK
    assertThat(result.getCategory()).isEqualTo(StuckExecutionCategory.STUCK);
    assertThat(result.getReason()).contains("no executable responses");
    assertThat(result.getNodeExecutionId()).isEqualTo("leaf-uuid-3");
    assertThat(result.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
  }

  /**
   * Test Case: Leaf node with empty executable responses list
   *
   * Scenario:
   * - Node is a leaf (TASK mode)
   * - Node has an empty list of executable responses
   *
   * Expected: STUCK
   * Reason: Empty list is treated same as no responses
   */
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testLeafNodeWithEmptyExecutableResponses_ShouldBeStuck() {
    // Given: A leaf node with empty executable responses list
    NodeExecution leafNode = NodeExecution.builder()
                                 .uuid("leaf-uuid-4")
                                 .ambiance(Ambiance.newBuilder()
                                               .setPlanExecutionId(PLAN_EXECUTION_ID)
                                               .putSetupAbstractions("accountId", ACCOUNT_ID)
                                               .putSetupAbstractions("orgIdentifier", ORG_ID)
                                               .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                                               .putSetupAbstractions("pipelineIdentifier", PIPELINE_ID)
                                               .build())
                                 .mode(ExecutionMode.TASK) // Leaf mode
                                 .status(Status.RUNNING)
                                 .build();

    // When: Analyze the node
    StuckExecutionResult result = detectionService.analyzeNodeExecution(leafNode, new HashMap<>());

    // Then: Should be STUCK
    assertThat(result.getCategory()).isEqualTo(StuckExecutionCategory.STUCK);
    assertThat(result.getReason()).contains("no executable responses");
    assertThat(result.getNodeExecutionId()).isEqualTo("leaf-uuid-4");
    assertThat(result.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
  }

  /**
   * Test Case: Container node with no children at all
   *
   * Scenario:
   * - Node is a container (CHILD mode)
   * - Node has NO children whatsoever
   *
   * Expected: STUCK
   * Reason: Container with no children at all is truly stuck
   */
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testContainerNodeWithNoChildren_ShouldBeStuck() {
    // Given: A container node with no children at all
    NodeExecution containerNode = NodeExecution.builder()
                                      .uuid("container-uuid-1")
                                      .ambiance(Ambiance.newBuilder()
                                                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                                                    .putSetupAbstractions("accountId", ACCOUNT_ID)
                                                    .putSetupAbstractions("orgIdentifier", ORG_ID)
                                                    .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                                                    .putSetupAbstractions("pipelineIdentifier", PIPELINE_ID)
                                                    .build())
                                      .mode(ExecutionMode.CHILD) // Container mode
                                      .status(Status.RUNNING)
                                      .build();

    // When: Analyze the node with an empty children map (no children at all)
    StuckExecutionResult result = detectionService.analyzeNodeExecution(containerNode, new HashMap<>());

    // Then: Should be STUCK
    assertThat(result.getCategory()).isEqualTo(StuckExecutionCategory.STUCK);
    assertThat(result.getReason()).contains("no children");
    assertThat(result.getNodeExecutionId()).isEqualTo("container-uuid-1");
    assertThat(result.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
  }

  /**
   * Test Case: Container node with no running children but HAS children in final status
   *
   * Scenario:
   * - Node is a container (CHILD mode)
   * - Node has NO running children
   * - Node HAS children in final status (SUCCEEDED, FAILED, etc.)
   *
   * Expected: NOT_STUCK
   * Reason: All children completed, parent is transitioning to final state - this is normal completion,
   *         not a stuck execution. This test verifies the fix for false positive detection where
   *         containers with completed children were incorrectly flagged as stuck.
   */
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testContainerNodeWithChildrenInFinalStatus_ShouldNotBeStuck() {
    // Given: A container node with no running children but has children in final status
    NodeExecution containerNode = NodeExecution.builder()
                                      .uuid("container-uuid-2")
                                      .ambiance(Ambiance.newBuilder()
                                                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                                                    .putSetupAbstractions("accountId", ACCOUNT_ID)
                                                    .putSetupAbstractions("orgIdentifier", ORG_ID)
                                                    .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                                                    .putSetupAbstractions("pipelineIdentifier", PIPELINE_ID)
                                                    .build())
                                      .mode(ExecutionMode.CHILD) // Container mode
                                      .status(Status.RUNNING)
                                      .build();

    // Create a child in SUCCEEDED status (final status)
    NodeExecution childNode = NodeExecution.builder()
                                  .uuid("child-uuid-1")
                                  .parentId("container-uuid-2")
                                  .ambiance(Ambiance.newBuilder()
                                                .setPlanExecutionId(PLAN_EXECUTION_ID)
                                                .putSetupAbstractions("accountId", ACCOUNT_ID)
                                                .build())
                                  .mode(ExecutionMode.TASK)
                                  .status(Status.SUCCEEDED) // Final status
                                  .build();

    // Build childrenByParentId map with the completed child
    Map<String, List<NodeExecution>> childrenByParentId = new HashMap<>();
    childrenByParentId.put("container-uuid-2", Collections.singletonList(childNode));

    // When: Analyze the container node with children in final status only
    StuckExecutionResult result = detectionService.analyzeNodeExecution(containerNode, childrenByParentId);

    // Then: Should be NOT_STUCK (children completed, parent is transitioning)
    assertThat(result.getCategory()).isEqualTo(StuckExecutionCategory.NOT_STUCK);
    assertThat(result.getReason()).contains("children in final status");
    assertThat(result.getReason()).contains("completing normally");
    assertThat(result.getNodeExecutionId()).isEqualTo("container-uuid-2");
    assertThat(result.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
  }
}
