/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.notification;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.approval.notification.summary.ApprovalSummary;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ApprovalStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ApprovalSummaryTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void toParams() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId("accountId")
                                                                .orgIdentifier("orgIdentifier")
                                                                .projectIdentifier("projectIdentifier")
                                                                .pipelineIdentifier("pipelineIdentifier")
                                                                .build();
    long currentTimeMillis = System.currentTimeMillis();
    ApprovalSummary approvalSummary = ApprovalSummary.builder()
                                          .pipelineName("p1")
                                          .orgName("default")
                                          .projectName("dev")
                                          .approvalMessage("approved")
                                          .startedAt(String.valueOf(currentTimeMillis))
                                          .expiresAt(String.valueOf(2L * currentTimeMillis))
                                          .triggeredBy("admin")
                                          .pipelineExecutionSummary(executionSummaryEntity.toString())
                                          .finishedStages(new LinkedHashSet<>(Arrays.asList("a1", "a2", "a3")))
                                          .runningStages(Collections.singleton("a4"))
                                          .upcomingStages(Collections.emptySet())
                                          .pipelineExecutionLink("this.link.executes.io")
                                          .timeRemainingForApproval("6d")
                                          .status(ApprovalStatus.WAITING)
                                          .currentStageName("currentStageName")
                                          .build();
    Map<String, String> params = approvalSummary.toParams(ApprovalSummary.DEFAULT_STAGE_DELIMITER);
    assertThat(params).hasSize(15);
    assertThat(params.get("pipelineName")).isEqualTo("p1");
    assertThat(params.get("orgName")).isEqualTo("default");
    assertThat(params.get("projectName")).isEqualTo("dev");
    assertThat(params.get("approvalMessage")).isEqualTo("approved");
    assertThat(params.get("startedAt")).isEqualTo(String.valueOf(currentTimeMillis));
    assertThat(params.get("expiresAt")).isEqualTo(String.valueOf(2L * currentTimeMillis));
    assertThat(params.get("triggeredBy")).isEqualTo("admin");
    assertThat(params.get("finishedStages")).isEqualTo("a1, a2, a3");
    assertThat(params.get("runningStages")).isEqualTo("a4");
    assertThat(params.get("upcomingStages")).isEqualTo("N/A");
    assertThat(params.get("pipelineExecutionLink")).isEqualTo("this.link.executes.io");
    assertThat(params.get("status")).isEqualTo("waiting");
    assertThat(params.get("timeRemainingForApproval")).isEqualTo("6d");
    assertThat(params.get("upcomingStages")).isEqualTo("N/A");
    assertThat(params.get("currentStageName")).isEqualTo("currentStageName");

    params = approvalSummary.toParams(ApprovalSummary.NEWLINE_STAGE_DELIMITER);
    assertThat(params.get("finishedStages")).isEqualTo("a1\n a2\n a3");
    assertThat(params.get("runningStages")).isEqualTo("a4");
    assertThat(params.get("upcomingStages")).isEqualTo("N/A");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void toParamsWhenApprovalMessageAndStatusAreNull() {
    PipelineExecutionSummaryEntity executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                .accountId("accountId")
                                                                .orgIdentifier("orgIdentifier")
                                                                .projectIdentifier("projectIdentifier")
                                                                .pipelineIdentifier("pipelineIdentifier")
                                                                .build();
    ApprovalSummary approvalSummary = ApprovalSummary.builder()
                                          .pipelineName("p1")
                                          .orgName("default")
                                          .projectName("dev")
                                          .approvalMessage(null)
                                          .status(null)
                                          .build();
    Map<String, String> params = approvalSummary.toParams(ApprovalSummary.DEFAULT_STAGE_DELIMITER);
    assertThat(params).hasSize(15);
    assertThat(params.get("pipelineName")).isEqualTo("p1");
    assertThat(params.get("orgName")).isEqualTo("default");
    assertThat(params.get("projectName")).isEqualTo("dev");
    assertThat(params.get("approvalMessage")).isEqualTo("");
    assertThat(params.get("status")).isEqualTo("");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStaticTextFields() {
    assertThat(ApprovalSummary.TEXT_FIELDS_IN_APPROVAL_SUMMARY)
        .isEqualTo(List.of("approvalMessage", "action", "finishedStages", "runningStages", "upcomingStages"));
  }
}
