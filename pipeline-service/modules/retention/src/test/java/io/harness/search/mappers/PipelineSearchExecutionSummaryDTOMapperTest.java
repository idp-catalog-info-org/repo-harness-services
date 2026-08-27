/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.abort.AbortedBy;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;
import io.harness.search.entity.beans.PipelineGitDetails;
import io.harness.search.entity.beans.PipelineRetryExecutionMetadata;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO;
import io.harness.search.entity.beans.PipelineTriggeredBy;
import io.harness.search.entity.beans.cd.CDPipelineSearchModuleInfo;
import io.harness.search.entity.beans.ci.CIPipelineSearchModuleInfo;
import io.harness.search.entity.beans.ci.ExecutionInfoDTO;
import io.harness.search.entity.beans.ci.PullRequestDTO;
import io.harness.yaml.core.NGLabel;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineSearchExecutionSummaryDTOMapperTest extends CategoryTest {
  private final String ACCOUNT_ID = "accountID";
  private final String ORG_ID = "orgIdentifier";
  private final String PROJECT_ID = "projectIdentifier";
  private final String PIPELINE_ID = "pipelineId";
  private final String PLAN_EXECUTION_ID = "planExecutionId";
  private final String CONNECTOR_ID = "connectorId";
  private final String PIPELINE_NAME = "pipelineName";
  private final String EMAIL = "admin@harness.io";
  private final String GIT_USER = "gitUser";
  private final String BRANCH = "branch";
  private final String REPO = "repo";
  private final String USERNAME = "Admin";

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testConvertSucess() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .uuid("1234")
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .pipelineIdentifier(PIPELINE_ID)
            .planExecutionId(PLAN_EXECUTION_ID)
            .runSequence(1)
            .name(PIPELINE_NAME)
            .status(ExecutionStatus.SUCCESS)
            .connectorRef(CONNECTOR_ID)
            .storeType(StoreType.INLINE)
            .tags(getTags())
            .labels(getNGLabels())
            .moduleInfo(getCDModuleInfo())
            .createdAt(1000L)
            .startTs(1000L)
            .endTs(1100L)
            .modules(Arrays.asList("cd", "pms"))
            .notesExistForPlanExecutionId(false)
            .executionMode(ExecutionMode.NORMAL)
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.MANUAL)
                                      .setTriggeredBy(TriggeredBy.newBuilder()
                                                          .setIdentifier(USERNAME)
                                                          .putExtraInfo("email", EMAIL)
                                                          .putExtraInfo("gitUser", GIT_USER)
                                                          .build())
                                      .build())
            .entityGitDetails(EntityGitDetails.builder().branch(BRANCH).repoName(REPO).build())
            .parentStageInfo(getParentStageInfo())
            .build();
    PipelineSearchExecutionSummaryDTO expectedPmsElasticExecutionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder()
            .uuid("1234")
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .pipelineIdentifier(PIPELINE_ID)
            .planExecutionId(PLAN_EXECUTION_ID)
            .runSequence(1)
            .name(PIPELINE_NAME)
            .status("SUCCESS")
            .tags(getTags())
            .labels(getNGLabels())
            .createdAt(1000L)
            .startTs(1000L)
            .endTs(1100L)
            .modules(Arrays.asList("cd", "pms"))
            .executionMode("NORMAL")
            .triggerType("MANUAL")
            .isChildPipeline(true)
            .retryExecutionMetadata(PipelineRetryExecutionMetadata.builder().rootExecutionId(PLAN_EXECUTION_ID).build())
            .cdModuleInfo(CDPipelineSearchModuleInfo.builder()
                              .artifactDisplayNames(Arrays.asList("nginx:stable", "nginx:latest"))
                              .serviceDefinitionTypes(Arrays.asList("Kubernetes", "Kubernetes"))
                              .serviceIdentifiers(Arrays.asList("service1", "service2"))
                              .envIdentifiers(Arrays.asList("env1", "env2"))
                              .gitOpsAppIdentifiers(Arrays.asList("gitOps1", "gitOps2"))
                              .build())
            .triggeredBy(PipelineTriggeredBy.builder().email(EMAIL).triggerIdentifier("").gitUser(GIT_USER).build())
            .entityGitDetails(PipelineGitDetails.builder().branch(BRANCH).repoName(REPO).build())
            .build();
    PipelineSearchExecutionSummaryDTO gotExecutionSummaryDTO =
        PipelineSearchExecutionSummaryDTOMapper.toSearchEntity(pipelineExecutionSummaryEntity, false);
    assertThat(gotExecutionSummaryDTO).isEqualTo(expectedPmsElasticExecutionSummaryDTO);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testConvertAborted() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .uuid("1234")
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .pipelineIdentifier(PIPELINE_ID)
            .planExecutionId(PLAN_EXECUTION_ID)
            .runSequence(1)
            .name(PIPELINE_NAME)
            .status(ExecutionStatus.ABORTED)
            .connectorRef(CONNECTOR_ID)
            .storeType(StoreType.INLINE)
            .tags(getTags())
            .labels(getNGLabels())
            .createdAt(1000L)
            .startTs(1000L)
            .endTs(1100L)
            .modules(Arrays.asList("pms"))
            .moduleInfo(getCIModuleInfo())
            .notesExistForPlanExecutionId(false)
            .executionMode(ExecutionMode.NORMAL)
            .abortedBy(AbortedBy.builder().userName(USERNAME).email(EMAIL).createdAt(1100L).build())
            .retryExecutionMetadata(
                RetryExecutionMetadata.builder().rootExecutionId("root").parentExecutionId("parent").build())
            .executionTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                      .setTriggerType(TriggerType.WEBHOOK_CUSTOM)
                                      .setTriggeredBy(TriggeredBy.newBuilder()
                                                          .setUuid("1234")
                                                          .setTriggerIdentifier("triggerId")
                                                          .setTriggerName("triggerName"))
                                      .build())
            .parentStageInfo(getParentStageInfo())
            .build();
    PipelineSearchExecutionSummaryDTO expectedPmsElasticExecutionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder()
            .uuid("1234")
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .pipelineIdentifier(PIPELINE_ID)
            .planExecutionId(PLAN_EXECUTION_ID)
            .runSequence(1)
            .name(PIPELINE_NAME)
            .status("ABORTED")
            .tags(getTags())
            .labels(getNGLabels())
            .createdAt(1000L)
            .startTs(1000L)
            .endTs(1100L)
            .modules(Arrays.asList("pms", "common"))
            .executionMode("NORMAL")
            .triggerType("WEBHOOK_CUSTOM")
            .isChildPipeline(true)
            .retryExecutionMetadata(PipelineRetryExecutionMetadata.builder().rootExecutionId("root").build())
            .isDeleted(false)
            .ciModuleInfo(CIPipelineSearchModuleInfo.builder()
                              .branch("main")
                              .tag("tag")
                              .buildType("branch")
                              .repoName("repo")
                              .ciExecutionInfoDTO(ExecutionInfoDTO.builder()
                                                      .event("pullRequest")
                                                      .pullRequest(PullRequestDTO.builder()
                                                                       .targetBranch("targetBranch")
                                                                       .sourceBranch("sourceBranch")
                                                                       .build())
                                                      .build())
                              .build())
            .triggeredBy(PipelineTriggeredBy.builder().email("").gitUser("").triggerIdentifier("triggerId").build())
            .build();
    PipelineSearchExecutionSummaryDTO gotExecutionSummaryDTO =
        PipelineSearchExecutionSummaryDTOMapper.toSearchEntity(pipelineExecutionSummaryEntity, true);
    assertThat(gotExecutionSummaryDTO).isEqualTo(expectedPmsElasticExecutionSummaryDTO);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testConvertModulesEmpty() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .uuid("1234")
                                                                        .planExecutionId(PLAN_EXECUTION_ID)
                                                                        .status(ExecutionStatus.ABORTED)
                                                                        .createdAt(1000L)
                                                                        .startTs(1000L)
                                                                        .endTs(1100L)
                                                                        .tags(getTags())
                                                                        .labels(getNGLabels())
                                                                        .modules(Collections.emptyList())
                                                                        .executionMode(ExecutionMode.NORMAL)
                                                                        .build();
    PipelineSearchExecutionSummaryDTO expectedPmsElasticExecutionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder()
            .uuid("1234")
            .planExecutionId(PLAN_EXECUTION_ID)
            .status("ABORTED")
            .tags(getTags())
            .labels(getNGLabels())
            .createdAt(1000L)
            .startTs(1000L)
            .endTs(1100L)
            .modules(Collections.emptyList())
            .executionMode("NORMAL")
            .isChildPipeline(false)
            .retryExecutionMetadata(PipelineRetryExecutionMetadata.builder().rootExecutionId(PLAN_EXECUTION_ID).build())
            .isDeleted(false)
            .runSequence(0)
            .build();
    PipelineSearchExecutionSummaryDTO gotExecutionSummaryDTO =
        PipelineSearchExecutionSummaryDTOMapper.toSearchEntity(pipelineExecutionSummaryEntity, true);
    assertThat(gotExecutionSummaryDTO).isEqualTo(expectedPmsElasticExecutionSummaryDTO);

    pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                         .uuid("1234")
                                         .planExecutionId(PLAN_EXECUTION_ID)
                                         .status(ExecutionStatus.ABORTED)
                                         .createdAt(1000L)
                                         .startTs(1000L)
                                         .endTs(1100L)
                                         .tags(getTags())
                                         .labels(getNGLabels())
                                         .executionMode(ExecutionMode.NORMAL)
                                         .build();
    expectedPmsElasticExecutionSummaryDTO =
        PipelineSearchExecutionSummaryDTO.builder()
            .uuid("1234")
            .planExecutionId(PLAN_EXECUTION_ID)
            .status("ABORTED")
            .tags(getTags())
            .labels(getNGLabels())
            .createdAt(1000L)
            .startTs(1000L)
            .endTs(1100L)
            .executionMode("NORMAL")
            .isChildPipeline(false)
            .runSequence(0)
            .retryExecutionMetadata(PipelineRetryExecutionMetadata.builder().rootExecutionId(PLAN_EXECUTION_ID).build())
            .build();
    gotExecutionSummaryDTO =
        PipelineSearchExecutionSummaryDTOMapper.toSearchEntity(pipelineExecutionSummaryEntity, false);
    assertThat(gotExecutionSummaryDTO).isEqualTo(expectedPmsElasticExecutionSummaryDTO);
  }

  private List<NGTag> getTags() {
    return Arrays.asList(NGTag.builder().key("abc").build(), NGTag.builder().key("hello").value("hello").build());
  }

  private List<NGLabel> getNGLabels() {
    return Arrays.asList(NGLabel.builder().key("abc").build(), NGLabel.builder().key("hello").value("hello").build());
  }

  private PipelineStageInfo getParentStageInfo() {
    return PipelineStageInfo.newBuilder().setHasParentPipeline(true).build();
  }

  private Map<String, Document> getCDModuleInfo() {
    Map<String, Document> moduleInfo = new HashMap<>();
    Document documentMap = new Document();
    moduleInfo.put("cd", documentMap);

    documentMap.put("artifactDisplayNames", Arrays.asList("nginx:stable", "nginx:latest"));
    documentMap.put("serviceDefinitionTypes", Arrays.asList("Kubernetes", "Kubernetes"));
    documentMap.put("serviceIdentifiers", Arrays.asList("service1", "service2"));
    documentMap.put("envIdentifiers", Arrays.asList("env1", "env2"));
    documentMap.put("gitOpsAppIdentifiers", Arrays.asList("gitOps1", "gitOps2"));

    return moduleInfo;
  }

  private Map<String, Document> getCIModuleInfo() {
    Map<String, Document> moduleInfo = new HashMap<>();
    Document documentMap = new Document();
    moduleInfo.put("ci", documentMap);

    Document pullRequest = new Document();
    Document ciExecutionInfoDTO = new Document();
    pullRequest.put("sourceBranch", "sourceBranch");
    pullRequest.put("targetBranch", "targetBranch");
    ciExecutionInfoDTO.put("event", "pullRequest");
    ciExecutionInfoDTO.put("pullRequest", pullRequest);

    documentMap.put("ciExecutionInfoDTO", ciExecutionInfoDTO);
    documentMap.put("branch", "main");
    documentMap.put("tag", "tag");
    documentMap.put("repoName", "repo");
    documentMap.put("buildType", "branch");

    return moduleInfo;
  }
}
