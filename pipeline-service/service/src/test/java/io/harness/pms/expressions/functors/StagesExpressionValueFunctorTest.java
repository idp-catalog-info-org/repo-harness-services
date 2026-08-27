/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class StagesExpressionValueFunctorTest extends CategoryTest {
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private PlanExecutionService planExecutionService;
  @InjectMocks private StagesExpressionValuesFunctor stagesExpressionValuesFunctor;
  String accountIdentifier = "accountIdentifier";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.checkIfFeatureFlagEnabled(any(), eq(FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name())))
        .thenReturn(true);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testBind() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build();
    on(stagesExpressionValuesFunctor).set("ambiance", ambiance);
    Map<String, Object> stagesExpressionValueMap = Map.of(
        "pipeline", Map.of("stages", Map.of("stage1", Map.of("variables", Map.of("var1", "val1", "var2", "val2")))));
    doReturn(PlanExecutionMetadata.builder().build())
        .when(planExecutionMetadataService)
        .findByPlanExecutionIdWithFieldsIncluded(accountIdentifier, ambiance.getPlanExecutionId(),
            Set.of(PlanExecutionMetadataKeys.stageExpressionValuesMap));
    Optional<PlanExecution> planExecutionOptional =
        Optional.of(PlanExecution.builder().stageExpressionValuesMap(stagesExpressionValueMap).build());
    when(planExecutionService.getWithFieldsIncludedOptional(
             ambiance.getPlanExecutionId(), Set.of(PlanExecutionKeys.stageExpressionValuesMap)))
        .thenReturn(planExecutionOptional);
    Map<String, Object> responseMap = (Map<String, Object>) stagesExpressionValuesFunctor.bind();
    assertEquals(responseMap, stagesExpressionValueMap);
  }
}
