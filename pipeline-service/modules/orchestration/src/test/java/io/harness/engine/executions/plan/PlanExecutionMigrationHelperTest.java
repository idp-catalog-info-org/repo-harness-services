/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.YAGYANSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PlanExecutionMigrationHelperTest extends OrchestrationTestBase {
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = YAGYANSH)
  @Category(UnitTests.class)
  public void testReaders() throws ExecutionException {
    String accountId = "accountId";
    String planExecutionId = generateUuid();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();

    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .accountIdentifier(accountId)
                                                      .planExecutionId(planExecutionId)
                                                      .triggerJsonPayload("triggerJsonPayload1")
                                                      .build();
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .triggerPayload(triggerPayload)
                                      .triggerJsonPayload("triggerJsonPayload2")
                                      .build();

    assertValues(planExecutionMetadata, planExecution, "triggerJsonPayload1", null);

    assertValues(planExecutionMetadata, planExecution, "triggerJsonPayload2", triggerPayload);

    // Test fallback
    planExecution = PlanExecution.builder().uuid(planExecutionId).build();
    assertValues(planExecutionMetadata, planExecution, "triggerJsonPayload1", null);
  }

  private void assertValues(PlanExecutionMetadata planExecutionMetadata, PlanExecution planExecution,
      String triggerJsonPayload, TriggerPayload triggerPayload) {
    assertThat(
        PlanExecutionMigrationHelper.readTriggerJsonPayloadWithFallBackOnMetadata(planExecutionMetadata, planExecution)
            .equals(triggerJsonPayload));
    assertThat(
        PlanExecutionMigrationHelper.readTriggerPayloadWithFallBackOnMetadata(planExecutionMetadata, planExecution)
        == triggerPayload);
  }
}
