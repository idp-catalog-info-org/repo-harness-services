/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node.helper;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.NodeExecution;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.monitoring.ExecutionCountWithAccountResult;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

public class NodeExecutionReadHelperTest extends CategoryTest {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private SecondaryMongoTemplateHolder secondaryMongoTemplateHolder;
  @Mock private MongoTemplate secondaryMongoTemplate;

  private NodeExecutionReadHelper nodeExecutionReadHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(secondaryMongoTemplateHolder.getSecondaryMongoTemplate()).thenReturn(secondaryMongoTemplate);
    nodeExecutionReadHelper = new NodeExecutionReadHelper(mongoTemplate, secondaryMongoTemplateHolder);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void aggregateLeafStepCountByAccountReturnsMappedResults() {
    List<ExecutionCountWithAccountResult> expected =
        List.of(ExecutionCountWithAccountResult.builder().accountId("acc1").count(3).build(),
            ExecutionCountWithAccountResult.builder().accountId("acc2").count(5).build());
    AggregationResults<ExecutionCountWithAccountResult> aggregationResults =
        new AggregationResults<>(expected, new Document());
    when(secondaryMongoTemplate.aggregate(
             any(Aggregation.class), eq(NodeExecution.class), eq(ExecutionCountWithAccountResult.class)))
        .thenReturn(aggregationResults);

    Set<Status> statuses = Set.of(Status.RUNNING, Status.QUEUED);
    List<ExecutionCountWithAccountResult> result = nodeExecutionReadHelper.aggregateLeafStepCountByAccount(statuses);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void aggregateLeafStepCountByAccountWithNoMatchesReturnsEmptyList() {
    AggregationResults<ExecutionCountWithAccountResult> aggregationResults =
        new AggregationResults<>(Collections.emptyList(), new Document());
    when(secondaryMongoTemplate.aggregate(
             any(Aggregation.class), eq(NodeExecution.class), eq(ExecutionCountWithAccountResult.class)))
        .thenReturn(aggregationResults);

    List<ExecutionCountWithAccountResult> result =
        nodeExecutionReadHelper.aggregateLeafStepCountByAccount(Set.of(Status.RUNNING));

    assertThat(result).isEmpty();
  }
}
