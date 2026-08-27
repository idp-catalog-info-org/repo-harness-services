/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.aisre;

import static io.harness.rule.OwnerRule.CAMERON;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.aisre.AiSrePipelineContextData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.manage.GlobalContextManager;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CHAOS)
@RunWith(MockitoJUnitRunner.class)
public class AisrePipelineContextFormatterTest extends CategoryTest {
  @InjectMocks private AisrePipelineContextFormatter formatter;
  @Mock private PipelineExpressionHelper pipelineExpressionHelper;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "deploy_checkout";
  private static final String EXECUTION_ID = "exec-123";
  private static final String STAGE_ID = "smoke_test";
  private static final String EXECUTION_URL = "https://app.harness.io/account/accountId/cd/orgs/orgId/projects/"
      + "projectId/pipelines/deploy_checkout/executions/exec-123/pipeline";

  private Ambiance ambiance;

  @Before
  public void setup() {
    GlobalContextManager.set(new GlobalContext());
    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                   .putSetupAbstractions("orgIdentifier", ORG_ID)
                   .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                   .setPlanExecutionId(EXECUTION_ID)
                   .addLevels(Level.newBuilder()
                                  .setIdentifier(STAGE_ID)
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                  .build())
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setPipelineIdentifier(PIPELINE_ID)
                                    .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                        .setTriggerType(TriggerType.MANUAL)
                                                        .setTriggeredBy(TriggeredBy.newBuilder()
                                                                            .setIdentifier("raj")
                                                                            .putExtraInfo("email", "raj@harness.io")
                                                                            .build())
                                                        .build())
                                    .build())
                   .build();
    AiSrePipelineContextData.setFromAmbiance(ambiance);
  }

  @After
  public void tearDown() {
    AiSrePipelineContextData.clear();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testSkipsPipelineContextWhenAttachDisabled() {
    String summary = formatter.formatIncidentSummary("Deploy failed", ambiance, false);
    assertThat(summary).isEqualTo("Deploy failed");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testFormatsRichPipelineContextInSummary() {
    PipelineExecutionSummaryEntity executionSummary = buildExecutionSummary(List.of("checkout:1.2.3"));
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(eq(ACCOUNT_ID), eq(EXECUTION_ID), any()))
        .thenReturn(executionSummary);
    when(pipelineExpressionHelper.generateUrl(ambiance, executionSummary)).thenReturn(EXECUTION_URL);

    String summary = formatter.formatIncidentSummary("Deploy failed", ambiance, true);

    assertThat(summary).startsWith("Deploy failed");
    assertThat(summary).contains("Created from Harness pipeline execution.");
    assertThat(summary).contains("Triggered by: raj@harness.io (MANUAL)");
    assertThat(summary).contains("Pipeline: deploy_checkout");
    assertThat(summary).contains("Stage: " + STAGE_ID);
    assertThat(summary).contains("Execution URL: " + EXECUTION_URL);
    assertThat(summary).contains("Artifact: checkout:1.2.3");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testFormatPipelineContextBlockWithoutLeadingSummary() {
    PipelineExecutionSummaryEntity executionSummary = buildExecutionSummary(List.of("checkout:1.2.3"));
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(eq(ACCOUNT_ID), eq(EXECUTION_ID), any()))
        .thenReturn(executionSummary);
    when(pipelineExpressionHelper.generateUrl(ambiance, executionSummary)).thenReturn(EXECUTION_URL);

    String block = formatter.formatPipelineContextBlock(ambiance);

    assertThat(block).startsWith("Created from Harness pipeline execution.");
    assertThat(block).contains("Triggered by: raj@harness.io (MANUAL)");
    assertThat(block).contains("Pipeline: deploy_checkout");
    assertThat(block).contains("Stage: " + STAGE_ID);
    assertThat(block).contains("Execution URL: " + EXECUTION_URL);
    assertThat(block).contains("Artifact: checkout:1.2.3");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testFallsBackToExecutionIdWhenUrlGenerationFails() {
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(eq(ACCOUNT_ID), eq(EXECUTION_ID), any()))
        .thenReturn(null);
    when(pipelineExpressionHelper.generateUrl(ambiance, null)).thenThrow(new RuntimeException("no url"));

    String summary = formatter.formatIncidentSummary(null, ambiance, true);

    assertThat(summary).contains("Execution: exec-123");
    assertThat(summary).doesNotContain("Execution URL:");
  }

  private PipelineExecutionSummaryEntity buildExecutionSummary(List<String> artifactDisplayNames) {
    Document cdModule = new Document("artifactDisplayNames", artifactDisplayNames);
    Map<String, Document> moduleInfo = new HashMap<>();
    moduleInfo.put("cd", cdModule);
    return PipelineExecutionSummaryEntity.builder().moduleInfo(moduleInfo).build();
  }
}
