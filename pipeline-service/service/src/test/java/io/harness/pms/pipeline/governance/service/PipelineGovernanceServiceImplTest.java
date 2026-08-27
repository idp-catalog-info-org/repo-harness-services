/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.governance.service;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHASHANK_JAIN;

import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.engine.GovernanceService;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.engine.utils.OpaPolicyEvaluationHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.scm.beans.ScmGitMetaDataContext;
import io.harness.governance.GovernanceMetadata;
import io.harness.manage.GlobalContextManager;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.PipelineGovernanceGitConfig;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.contracts.governance.ExpansionResponseBatch;
import io.harness.pms.contracts.governance.ExpansionResponseProto;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.governance.ExpansionRequest;
import io.harness.pms.governance.ExpansionRequestsExtractor;
import io.harness.pms.governance.JsonExpander;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineGovernanceServiceImplTest extends CategoryTest {
  String accountIdentifier = "account";
  String orgIdentifier = "org";
  String projectIdentifier = "project";
  String branch = "branch";
  String yaml = "yaml";

  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PmsGitSyncHelper gitSyncHelper;
  @Mock private ExpansionRequestsExtractor expansionRequestsExtractor;
  @Mock private GovernanceService governanceService;
  @Mock private JsonExpander jsonExpander;
  @Mock private OpaPolicyEvaluationHelper opaPolicyEvaluationHelper;
  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks PipelineGovernanceServiceImpl pipelineGovernanceService;
  PipelineEntity pipelineEntity;
  PipelineGovernanceServiceImpl pipelineGovernanceService1;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    pipelineEntity = PipelineEntity.builder()
                         .accountId(accountIdentifier)
                         .orgIdentifier(orgIdentifier)
                         .projectIdentifier(projectIdentifier)
                         .identifier("pipeline")
                         .build();
    pipelineGovernanceService1 = spy(pipelineGovernanceService);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchExpandedPipelineJSONFromYaml() {
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    String dummyYaml = "\"don't really need a proper yaml cuz only testing the flow\"";
    ByteString randomByteString = ByteString.copyFromUtf8("sss");
    ExpansionRequestMetadata expansionRequestMetadata = ExpansionRequestMetadata.newBuilder()
                                                            .setAccountId(accountIdentifier)
                                                            .setOrgId(orgIdentifier)
                                                            .setProjectId(projectIdentifier)
                                                            .setGitSyncBranchContext(randomByteString)
                                                            .setYaml(ByteString.copyFromUtf8(dummyYaml))
                                                            .build();
    ExpansionRequest dummyRequest = ExpansionRequest.builder().fqn("fqn").build();
    Set<ExpansionRequest> dummyRequestSet = Collections.singleton(dummyRequest);
    doReturn(randomByteString).when(gitSyncHelper).getGitSyncBranchContextBytesThreadLocal();
    doReturn(dummyRequestSet).when(expansionRequestsExtractor).fetchExpansionRequests(dummyYaml, "account", false);
    ExpansionResponseProto dummyResponse =
        ExpansionResponseProto.newBuilder().setSuccess(false).setErrorMessage("just because").build();
    ExpansionResponseBatch dummyResponseBatch =
        ExpansionResponseBatch.newBuilder().addExpansionResponseProto(dummyResponse).build();
    Set<ExpansionResponseBatch> dummyResponseSet = Collections.singleton(dummyResponseBatch);
    doReturn(dummyResponseSet).when(jsonExpander).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);
    doReturn(true)
        .when(opaPolicyEvaluationHelper)
        .shouldEvaluatePolicy(accountIdentifier, orgIdentifier, projectIdentifier,
            OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, OpaConstants.OPA_EVALUATION_ACTION_SAVE, "0");
    pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
        pipelineEntity, dummyYaml, null, OpaConstants.OPA_EVALUATION_ACTION_SAVE);
    verify(gitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal();
    verify(expansionRequestsExtractor, times(1)).fetchExpansionRequests(dummyYaml, "account", false);
    verify(jsonExpander, times(1)).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);

    doReturn(false).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    String noExp = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
        pipelineEntity, dummyYaml, null, OpaConstants.OPA_EVALUATION_ACTION_SAVE);
    assertNull(noExp);
    verify(gitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal();
    verify(expansionRequestsExtractor, times(1)).fetchExpansionRequests(dummyYaml, "account", false);
    verify(jsonExpander, times(1)).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testFetchExpandedPipelineJSONForV1Yaml() {
    String dummyYaml = "\"version: 1\"";
    String noExp = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
        pipelineEntity, dummyYaml, null, OpaConstants.OPA_EVALUATION_ACTION_SAVE);
    assertNull(noExp);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFetchExpandedPipelineJSONFromYamlWithPipelineEntity() {
    String pipelineYaml = "pipeline:\n"
        + "    identifier: cipipeline2GDdkmQLfb\n"
        + "    name: run pipeline with output variable success\n"
        + "    stages:\n"
        + "        - stage:\n"
        + "              identifier: outputvar\n"
        + "              name: output variable\n"
        + "              type: CI\n"
        + "              spec:\n"
        + "                  execution:\n"
        + "                      steps:\n"
        + "                          - step:\n"
        + "                                identifier: two\n"
        + "                                name: two\n"
        + "                                type: Run\n"
        + "                                spec:\n"
        + "                                    command: <+input>\n"
        + "                                    shell: Powershell\n"
        + "                  infrastructure:\n"
        + "                      type: VM\n"
        + "                      spec:\n"
        + "                          type: Pool\n"
        + "                          spec:\n"
        + "                              identifier: windows\n"
        + "                  cloneCodebase: false\n"
        + "    projectIdentifier: Plain_Old_Project\n"
        + "    orgIdentifier: default\n";
    ByteString randomByteString = ByteString.copyFromUtf8("sss");
    ExpansionRequestMetadata expansionRequestMetadata = ExpansionRequestMetadata.newBuilder()
                                                            .setAccountId(accountIdentifier)
                                                            .setOrgId(orgIdentifier)
                                                            .setProjectId(projectIdentifier)
                                                            .setGitSyncBranchContext(randomByteString)
                                                            .setYaml(ByteString.copyFromUtf8(pipelineYaml))
                                                            .build();
    ExpansionRequest dummyRequest = ExpansionRequest.builder().fqn("fqn").build();
    Set<ExpansionRequest> dummyRequestSet = Collections.singleton(dummyRequest);
    doReturn(randomByteString).when(gitSyncHelper).getGitSyncBranchContextBytesThreadLocal();
    doReturn(dummyRequestSet).when(expansionRequestsExtractor).fetchExpansionRequests(pipelineYaml, "account", false);
    ExpansionResponseProto dummyResponse =
        ExpansionResponseProto.newBuilder().setSuccess(false).setErrorMessage("just because").build();
    ExpansionResponseBatch dummyResponseBatch =
        ExpansionResponseBatch.newBuilder().addExpansionResponseProto(dummyResponse).build();
    Set<ExpansionResponseBatch> dummyResponseSet = Sets.newHashSet(dummyResponseBatch);
    doReturn(dummyResponseSet).when(jsonExpander).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);

    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .filePath("filePath")
                                        .repo("repo")
                                        .storeType(StoreType.REMOTE)
                                        .build();
    doReturn(true).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(true)
        .when(opaPolicyEvaluationHelper)
        .shouldEvaluatePolicy(accountIdentifier, orgIdentifier, projectIdentifier,
            OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, OpaConstants.OPA_EVALUATION_ACTION_SAVE, "0");
    String noExp = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
        pipelineEntity, pipelineYaml, "branch", OpaConstants.OPA_EVALUATION_ACTION_SAVE);
    assertThat(noExp).isEqualTo(
        "{\"pipeline\":{\"identifier\":\"cipipeline2GDdkmQLfb\",\"name\":\"run pipeline with output variable "
        + "success\",\"stages\":[{\"stage\":{\"identifier\":\"outputvar\",\"name\":\"output "
        + "variable\",\"type\":\"CI\",\"spec\":{\"execution\":{\"steps\":[{\"step\":{\"identifier\":\"two\",\"name\":"
        + "\"two\",\"type\":\"Run\",\"spec\":{\"command\":\"<+input>\",\"shell\":\"Powershell\"}}}]},"
        + "\"infrastructure\":{\"type\":\"VM\",\"spec\":{\"type\":\"Pool\",\"spec\":{\"identifier\":\"windows\"}}},"
        + "\"cloneCodebase\":false}}}],\"projectIdentifier\":\"Plain_Old_Project\",\"orgIdentifier\":\"default\","
        + "\"gitConfig\":{\"branch\":\"branch\",\"repoName\":\"repo\",\"filePath\":\"filePath\"}}}");
    verify(gitSyncHelper, times(1)).getGitSyncBranchContextBytesThreadLocal();
    verify(expansionRequestsExtractor, times(1)).fetchExpansionRequests(pipelineYaml, "account", false);
    verify(jsonExpander, times(1)).fetchExpansionResponses(dummyRequestSet, expansionRequestMetadata);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testPolicyFailureException() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(true).build();
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled(accountIdentifier, FeatureName.CDS_SAVE_PIPELINE_OPA_RESPONSE_CODE_CHANGE);
    doReturn(governanceMetadata)
        .when(governanceService)
        .evaluateGovernancePolicies(
            any(), eq(accountIdentifier), eq(orgIdentifier), eq(projectIdentifier), any(), any(), any(), any());
    doReturn(governanceMetadata)
        .when(pipelineGovernanceService1)
        .validateGovernanceRules(accountIdentifier, orgIdentifier, projectIdentifier, branch, pipelineEntity, yaml);

    assertThatExceptionOfType(PolicyEvaluationFailureException.class)
        .isThrownBy(()
                        -> pipelineGovernanceService.validateGovernanceRulesAndThrowExceptionIfDenied(
                            accountIdentifier, orgIdentifier, projectIdentifier, branch, pipelineEntity, yaml));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGovernanceRulesAndThrowExceptionWhenNotDenied() {
    GovernanceMetadata governanceMetadata = GovernanceMetadata.newBuilder().setDeny(false).setId("someID").build();
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled(accountIdentifier, FeatureName.CDS_SAVE_PIPELINE_OPA_RESPONSE_CODE_CHANGE);
    doReturn(governanceMetadata)
        .when(governanceService)
        .evaluateGovernancePolicies(
            any(), eq(accountIdentifier), eq(orgIdentifier), eq(projectIdentifier), any(), any(), any(), any());
    doReturn(governanceMetadata)
        .when(pipelineGovernanceService1)
        .validateGovernanceRules(accountIdentifier, orgIdentifier, projectIdentifier, branch, pipelineEntity, yaml);

    when(pipelineGovernanceService.validateGovernanceRulesAndThrowExceptionIfDenied(
             accountIdentifier, orgIdentifier, projectIdentifier, branch, pipelineEntity, yaml))
        .thenReturn(governanceMetadata);

    assertThat(governanceMetadata.getDeny()).isFalse();
    assertThat(governanceMetadata.getId()).isEqualTo("someID");
  }

  @Test
  @Owner(developers = SHASHANK_JAIN)
  @Category(UnitTests.class)
  public void testGetGitDetailsAsExecutionResponseFromGitEntityInfo() {
    String testBranch = "main";
    String testFilePath = "path/to/pipeline.yaml";
    String testRepoName = "test-repo";

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().filePath(testFilePath).repoName(testRepoName).build();

    PipelineGovernanceGitConfig expectedConfig =
        PipelineGovernanceGitConfig.builder().branch(testBranch).filePath(testFilePath).repoName(testRepoName).build();

    String gitDetailsJson = new Gson().toJson(expectedConfig);
    ExpansionResponseProto gitConfig = ExpansionResponseProto.newBuilder()
                                           .setFqn("pipeline")
                                           .setKey("gitConfig")
                                           .setValue(gitDetailsJson)
                                           .setSuccess(true)
                                           .setPlacement(ExpansionPlacementStrategy.APPEND)
                                           .build();

    ExpansionResponseBatch expectedResponse =
        ExpansionResponseBatch.newBuilder().addAllExpansionResponseProto(Collections.singletonList(gitConfig)).build();

    PipelineGovernanceServiceImpl spyService = spy(pipelineGovernanceService);
    doReturn(expectedResponse).when(spyService).getExpansionResponseBatch(any(PipelineGovernanceGitConfig.class));

    ExpansionResponseBatch actualResponse =
        spyService.getGitDetailsAsExecutionResponseFromGitEntityInfo(gitEntityInfo, testBranch);

    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(spyService).getPipelineGovernanceGitConfigInfo(testBranch, testFilePath, testRepoName, null);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetGitDetailsAsExecutionResponseWithScmGitMetaData() {
    String testBranch = "main";
    String testRepoUrl = "https://github.com/harness/test-repo";
    String testCommitId = "abc123def456";

    PipelineEntity remotePipeline = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .filePath("path/to/pipeline.yaml")
                                        .repo("test-repo")
                                        .connectorRef("connectorRef")
                                        .storeType(StoreType.REMOTE)
                                        .build();

    GlobalContextManager.set(new GlobalContext());
    GlobalContextManager.upsertGlobalContextRecord(
        ScmGitMetaDataContext.builder()
            .scmGitMetaData(ScmGitMetaData.builder().repoUrl(testRepoUrl).commitId(testCommitId).build())
            .build());

    try {
      ExpansionResponseBatch response =
          pipelineGovernanceService.getGitDetailsAsExecutionResponse(remotePipeline, testBranch);

      assertThat(response).isNotNull();
      assertThat(response.getExpansionResponseProtoList()).hasSize(1);
      String value = response.getExpansionResponseProto(0).getValue();
      assertThat(value).contains("\"repoUrl\":\"" + testRepoUrl + "\"");
      assertThat(value).contains("\"commitId\":\"" + testCommitId + "\"");
      assertThat(value).contains("\"branch\":\"" + testBranch + "\"");
      assertThat(value).contains("\"connectorRef\":\"connectorRef\"");
    } finally {
      GlobalContextManager.unset();
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetGitDetailsAsExecutionResponseWithoutScmGitMetaData() {
    String testBranch = "main";

    PipelineEntity remotePipeline = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .filePath("path/to/pipeline.yaml")
                                        .repo("test-repo")
                                        .connectorRef("connectorRef")
                                        .storeType(StoreType.REMOTE)
                                        .build();

    ExpansionResponseBatch response =
        pipelineGovernanceService.getGitDetailsAsExecutionResponse(remotePipeline, testBranch);

    assertThat(response).isNotNull();
    String value = response.getExpansionResponseProto(0).getValue();
    assertThat(value).contains("\"branch\":\"" + testBranch + "\"");
    assertThat(value).contains("\"repoName\":\"test-repo\"");
    assertThat(value).doesNotContain("\"repoUrl\"");
    assertThat(value).doesNotContain("\"commitId\"");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetGitDetailsFromGitEntityInfoWithScmGitMetaData() {
    String testBranch = "develop";
    String testRepoUrl = "https://github.com/harness/another-repo";
    String testCommitId = "xyz789";

    GitEntityInfo gitEntityInfo =
        GitEntityInfo.builder().filePath("pipeline.yaml").repoName("another-repo").connectorRef("connector1").build();

    GlobalContextManager.set(new GlobalContext());
    GlobalContextManager.upsertGlobalContextRecord(
        ScmGitMetaDataContext.builder()
            .scmGitMetaData(ScmGitMetaData.builder().repoUrl(testRepoUrl).commitId(testCommitId).build())
            .build());

    try {
      ExpansionResponseBatch response =
          pipelineGovernanceService.getGitDetailsAsExecutionResponseFromGitEntityInfo(gitEntityInfo, testBranch);

      assertThat(response).isNotNull();
      String value = response.getExpansionResponseProto(0).getValue();
      assertThat(value).contains("\"repoUrl\":\"" + testRepoUrl + "\"");
      assertThat(value).contains("\"commitId\":\"" + testCommitId + "\"");
      assertThat(value).contains("\"branch\":\"" + testBranch + "\"");
      assertThat(value).contains("\"repoName\":\"another-repo\"");
      assertThat(value).contains("\"connectorRef\":\"connector1\"");
    } finally {
      GlobalContextManager.unset();
    }
  }
}
