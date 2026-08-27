/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.dynamic.DynamicExecutionService;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceRequestDTO;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.plancreator.stages.dynamic.DynamicStageStepParameters;
import io.harness.plancreator.stages.dynamic.GitConfig;
import io.harness.plancreator.stages.dynamic.GitSourceConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.PIPELINE)
@PrepareForTest({AmbianceUtils.class, PmsSecurityContextGuardUtils.class, SourcePrincipalContextBuilder.class})
public class DynamicStageStepTest extends CategoryTest {
  @Mock private PlanCreationQueueRequestHelper planCreationQueueRequestHelper;
  @Mock private DynamicExecutionService dynamicExecutionService;
  @Mock private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  @Mock private GitAwareEntityHelper gitAwareEntityHelper;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @InjectMocks private DynamicStageStep dynamicStageStep;

  private final String ACCOUNT_ID = "accountId";
  String PLAN_EXECUTION_ID = "planExecutionId";
  private final Ambiance ambiance =
      Ambiance.newBuilder()
          .setPlanExecutionId(PLAN_EXECUTION_ID)
          .putSetupAbstractions("accountId", ACCOUNT_ID)
          .addLevels(Level.newBuilder().setIdentifier("step_1").setRuntimeId("parentNodeExecutionId").build())
          .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    // Mock PmsEngineExpressionService to return the input GitConfig as-is (for tests with fixed values)
    when(pmsEngineExpressionService.resolve(any(Ambiance.class), any(GitConfig.class), any(ExpressionMode.class)))
        .thenAnswer(invocation -> invocation.getArgument(1));

    // Mock Principal-related static methods
    Mockito.mockStatic(PmsSecurityContextGuardUtils.class);
    when(PmsSecurityContextGuardUtils.getPrincipalFromAmbiance(any(Ambiance.class)))
        .thenReturn(new UserPrincipal("testUser", "test@example.com", "testUser", ACCOUNT_ID));
    Mockito.mockStatic(SourcePrincipalContextBuilder.class);
    // No need to mock setSourcePrincipal behavior if it's just setting a static context
  }

  private String getYaml(String filename) throws IOException {
    URL url = this.getClass().getClassLoader().getResource(filename);
    return Resources.toString(url, Charsets.UTF_8);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testObtainChildWithInvalidYaml() {
    DynamicStageStepParameters stepParameters = DynamicStageStepParameters.builder().source("invalid-yaml").build();
    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, null))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Kindly provide valid YAML for dynamic execution.");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testObtainChild() throws IOException {
    String nodeExecutionId = "nodeExecutionId";

    String yaml = getYaml("pipeline.yml");

    String encodedYaml = Base64.getEncoder().encodeToString(yaml.getBytes());
    DynamicStageStepParameters stepParameters = DynamicStageStepParameters.builder().source(encodedYaml).build();
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    when(pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), any(), any(), any(), any()))
        .thenReturn(templateMergeResponseDTO);
    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.obtainCurrentRuntimeId(any())).thenReturn(nodeExecutionId);
    doReturn(Plan.builder().planNodes(List.of(PlanNode.builder().build())).build())
        .when(planCreationQueueRequestHelper)
        .createAndAppendToExistingPlan(any(), any(), any());

    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());

    ArgumentCaptor<YamlField> yamlFieldArgumentCaptor = ArgumentCaptor.forClass(YamlField.class);
    verify(planCreationQueueRequestHelper, times(1))
        .createAndAppendToExistingPlan(eq(ambiance), any(), yamlFieldArgumentCaptor.capture());

    YamlField passedYamlField = yamlFieldArgumentCaptor.getValue();
    YamlUtils.removeUuid(passedYamlField.getNode().getCurrJsonNode());
    assertThat(YamlUtils.readTree(yaml).getNode().getField("pipeline").getNode().toString())
        .endsWith(passedYamlField.getNode().toString());
    ArgumentCaptor<DynamicExecutionInstanceRequestDTO> argumentCaptor =
        ArgumentCaptor.forClass(DynamicExecutionInstanceRequestDTO.class);
    verify(dynamicExecutionService, times(1)).create(argumentCaptor.capture());
    DynamicExecutionInstanceRequestDTO requestDTO = argumentCaptor.getValue();

    assertThat(requestDTO.getNodeExecutionId()).isEqualTo(nodeExecutionId);
    assertThat(requestDTO.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(requestDTO.getYaml()).isEqualTo(yaml);
    assertThat(response).isNotNull();

    doReturn(Plan.builder().planNodes(List.of(PlanNode.builder().executionInputTemplate("someValue").build())).build())
        .when(planCreationQueueRequestHelper)
        .createAndAppendToExistingPlan(any(), any(), any());

    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Execution Time Input is not supported with the dynamic-stage execution.");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testObtainChildWithChildNodeProvided() throws IOException {
    String nodeExecutionId = "nodeExecutionId";
    String parentExecutionId = "parentExecutionId";

    String yaml = getYaml("pipeline.yml");
    String encodedYaml = Base64.getEncoder().encodeToString(yaml.getBytes());
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().childNodeId("childNodeId").source(encodedYaml).build();
    doReturn(
        Optional.of(PlanExecutionMetadata.builder()
                        .retryExecutionInfo(RetryExecutionInfo.newBuilder().setParentRetryId(parentExecutionId).build())
                        .build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambiance), PLAN_EXECUTION_ID);

    DynamicExecutionInstanceResponseDTO dynamicExecutionInstanceResponseDTO =
        DynamicExecutionInstanceResponseDTO.builder()
            .processedYaml("processedYaml")
            .nodeExecutionId(nodeExecutionId)
            .planExecutionId(parentExecutionId)
            .yaml("yaml")
            .build();
    doReturn(Optional.of(dynamicExecutionInstanceResponseDTO))
        .when(dynamicExecutionService)
        .getByPlanExecutionIdAndIdentifier(parentExecutionId, "step_1");
    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response.getChildNodeId()).isEqualTo("childNodeId");
    ArgumentCaptor<DynamicExecutionInstanceRequestDTO> argumentCaptor =
        ArgumentCaptor.forClass(DynamicExecutionInstanceRequestDTO.class);
    verify(dynamicExecutionService, times(1)).create(argumentCaptor.capture());
    DynamicExecutionInstanceRequestDTO createRequest = argumentCaptor.getValue();
    assertThat(createRequest.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(createRequest.getYaml()).isEqualTo(dynamicExecutionInstanceResponseDTO.getYaml());
    assertThat(createRequest.getProcessedYaml()).isEqualTo(dynamicExecutionInstanceResponseDTO.getProcessedYaml());
    assertThat(createRequest.getIdentifier()).isEqualTo(AmbianceUtils.obtainStepIdentifier(ambiance));
    assertThat(createRequest.getNodeExecutionId()).isEqualTo(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testObtainChildWithMultilineBase64() throws IOException {
    String nodeExecutionId = "nodeExecutionId";
    String yaml = getYaml("pipeline.yml");
    String singleLineEncoded = Base64.getEncoder().encodeToString(yaml.getBytes());
    StringBuilder multilineEncoded = new StringBuilder();
    for (int i = 0; i < singleLineEncoded.length(); i += 64) {
      multilineEncoded.append(singleLineEncoded, i, Math.min(i + 64, singleLineEncoded.length()));
      if (i + 64 < singleLineEncoded.length()) {
        multilineEncoded.append("\n");
      }
    }

    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().source(multilineEncoded.toString()).build();
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    when(pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), any(), any(), any(), any()))
        .thenReturn(templateMergeResponseDTO);
    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.obtainCurrentRuntimeId(any())).thenReturn(nodeExecutionId);
    doReturn(Plan.builder().planNodes(List.of(PlanNode.builder().build())).build())
        .when(planCreationQueueRequestHelper)
        .createAndAppendToExistingPlan(any(), any(), any());

    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());

    ArgumentCaptor<DynamicExecutionInstanceRequestDTO> argumentCaptor =
        ArgumentCaptor.forClass(DynamicExecutionInstanceRequestDTO.class);
    verify(dynamicExecutionService, times(1)).create(argumentCaptor.capture());
    DynamicExecutionInstanceRequestDTO requestDTO = argumentCaptor.getValue();

    assertThat(requestDTO.getNodeExecutionId()).isEqualTo(nodeExecutionId);
    assertThat(requestDTO.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(requestDTO.getYaml()).isEqualTo(yaml);
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testObtainChildWithGitStore() throws IOException {
    String nodeExecutionId = "nodeExecutionId";
    String yaml = getYaml("pipeline.yml");

    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(ParameterField.createValueField("git-conn"))
                              .filePath(ParameterField.createValueField(".harness/pipeline.yaml"))
                              .branchName(ParameterField.createValueField("feature/test"))
                              .repoName(ParameterField.createValueField("repo"))
                              .build();
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    when(pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), any(), any(), any(), any()))
        .thenReturn(templateMergeResponseDTO);

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);
    when(AmbianceUtils.obtainCurrentRuntimeId(any())).thenReturn(nodeExecutionId);
    when(AmbianceUtils.obtainStepIdentifier(any())).thenReturn("step_1");
    when(AmbianceUtils.getOrgIdentifier(any())).thenReturn("orgId");
    when(AmbianceUtils.getProjectIdentifier(any())).thenReturn("projId");
    when(AmbianceUtils.getParentUniqueIdentifier(any())).thenReturn("parentUniqueId");

    doReturn(Plan.builder().planNodes(List.of(PlanNode.builder().build())).build())
        .when(planCreationQueueRequestHelper)
        .createAndAppendToExistingPlan(any(), any(), any());

    when(gitAwareEntityHelper.fetchYAMLFromRemote(any(Scope.class), any(GitContextRequestParams.class), any()))
        .thenReturn(yaml);

    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response).isNotNull();
    ArgumentCaptor<DynamicExecutionInstanceRequestDTO> argumentCaptor =
        ArgumentCaptor.forClass(DynamicExecutionInstanceRequestDTO.class);
    verify(dynamicExecutionService, times(1)).create(argumentCaptor.capture());
    DynamicExecutionInstanceRequestDTO requestDTO = argumentCaptor.getValue();

    assertThat(requestDTO.getNodeExecutionId()).isEqualTo(nodeExecutionId);
    assertThat(requestDTO.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(requestDTO.getYaml()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGitStorePassesEmptyBranchWhenNotProvided() throws IOException {
    String nodeExecutionId = "nodeExecutionId";
    String yaml = getYaml("pipeline.yml");

    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(ParameterField.createValueField("git-conn"))
                              .filePath(ParameterField.createValueField(".harness/pipeline.yaml"))
                              .repoName(ParameterField.createValueField("repo"))
                              .build();
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    when(pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), any(), any(), any(), any()))
        .thenReturn(templateMergeResponseDTO);

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);
    when(AmbianceUtils.obtainCurrentRuntimeId(any())).thenReturn(nodeExecutionId);
    when(AmbianceUtils.obtainStepIdentifier(any())).thenReturn("step_1");
    when(AmbianceUtils.getOrgIdentifier(any())).thenReturn("orgId");
    when(AmbianceUtils.getProjectIdentifier(any())).thenReturn("projId");
    when(AmbianceUtils.getParentUniqueIdentifier(any())).thenReturn("parentUniqueId");

    doReturn(Plan.builder().planNodes(List.of(PlanNode.builder().build())).build())
        .when(planCreationQueueRequestHelper)
        .createAndAppendToExistingPlan(any(), any(), any());

    ArgumentCaptor<GitContextRequestParams> gitContextCaptor = ArgumentCaptor.forClass(GitContextRequestParams.class);
    when(gitAwareEntityHelper.fetchYAMLFromRemote(any(Scope.class), gitContextCaptor.capture(), any()))
        .thenReturn(yaml);

    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response).isNotNull();
    GitContextRequestParams params = gitContextCaptor.getValue();
    // When branch is not provided, empty string is passed to let Git SDK determine the default branch
    assertThat(params.getBranchName()).isEmpty();
    assertThat(params.getCommitId()).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testObtainChildThrowsExceptionWhenBothSourceAndSourceConfigAreEmpty() {
    DynamicStageStepParameters stepParameters = DynamicStageStepParameters.builder().build();

    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Kindly provide valid YAML for dynamic execution")
        .hasCauseInstanceOf(InvalidYamlException.class)
        .satisfies(throwable -> {
          assertThat(throwable.getCause().getMessage())
              .isEqualTo("Either 'source' (inline YAML) or 'sourceConfig' (Git store configuration) must be provided.");
        });
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testIsGitStoreProvidedReturnsFalseWhenGitConfigSpecIsNull() {
    // GitSourceConfig with null spec should make isGitStoreProvided return false
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(null).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);

    // Should fall back to inline source validation and fail
    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Kindly provide valid YAML for dynamic execution")
        .hasCauseInstanceOf(InvalidYamlException.class)
        .satisfies(throwable -> {
          assertThat(throwable.getCause().getMessage())
              .contains("Either 'source' (inline YAML) or 'sourceConfig' (Git store configuration) must be provided");
        });
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testFetchYamlFromGitThrowsExceptionWhenFilePathIsEmpty() throws IOException {
    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(ParameterField.createValueField("git-conn"))
                              .filePath(ParameterField.createValueField("")) // Empty filePath
                              .repoName(ParameterField.createValueField("repo"))
                              .build();
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);
    when(AmbianceUtils.getOrgIdentifier(any())).thenReturn("orgId");
    when(AmbianceUtils.getProjectIdentifier(any())).thenReturn("projId");
    when(AmbianceUtils.getParentUniqueIdentifier(any())).thenReturn("parentUniqueId");
    when(scopeResolutionHelper.getScopeInfo(any(), any())).thenReturn(null);

    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Kindly provide valid YAML for dynamic execution")
        .hasCauseInstanceOf(InvalidRequestException.class)
        .satisfies(throwable -> {
          assertThat(throwable.getCause().getMessage())
              .contains("filePath is required when using Git store for dynamic stage");
        });
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testFetchYamlFromGitThrowsExceptionWhenRepoNameIsEmpty() throws IOException {
    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(ParameterField.createValueField("git-conn"))
                              .filePath(ParameterField.createValueField(".harness/pipeline.yaml"))
                              .repoName(ParameterField.createValueField("")) // Empty repoName
                              .build();
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);
    when(AmbianceUtils.getOrgIdentifier(any())).thenReturn("orgId");
    when(AmbianceUtils.getProjectIdentifier(any())).thenReturn("projId");
    when(AmbianceUtils.getParentUniqueIdentifier(any())).thenReturn("parentUniqueId");
    when(scopeResolutionHelper.getScopeInfo(any(), any())).thenReturn(null);

    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Kindly provide valid YAML for dynamic execution")
        .hasCauseInstanceOf(InvalidRequestException.class)
        .satisfies(throwable -> {
          assertThat(throwable.getCause().getMessage())
              .contains("repoName is required when using Git store for dynamic stage");
        });
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testFetchYamlFromGitWorksWithNullConnectorRef() throws IOException {
    String nodeExecutionId = "nodeExecutionId";
    String yaml = getYaml("pipeline.yml");

    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(null) // Null connectorRef should be allowed
                              .filePath(ParameterField.createValueField(".harness/pipeline.yaml"))
                              .branchName(ParameterField.createValueField("feature/test"))
                              .repoName(ParameterField.createValueField("repo"))
                              .build();
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    when(pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), any(), any(), any(), any()))
        .thenReturn(templateMergeResponseDTO);

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);
    when(AmbianceUtils.obtainCurrentRuntimeId(any())).thenReturn(nodeExecutionId);
    when(AmbianceUtils.obtainStepIdentifier(any())).thenReturn("step_1");
    when(AmbianceUtils.getOrgIdentifier(any())).thenReturn("orgId");
    when(AmbianceUtils.getProjectIdentifier(any())).thenReturn("projId");
    when(AmbianceUtils.getParentUniqueIdentifier(any())).thenReturn("parentUniqueId");

    doReturn(Plan.builder().planNodes(List.of(PlanNode.builder().build())).build())
        .when(planCreationQueueRequestHelper)
        .createAndAppendToExistingPlan(any(), any(), any());

    when(gitAwareEntityHelper.fetchYAMLFromRemote(any(Scope.class), any(GitContextRequestParams.class), any()))
        .thenReturn(yaml);

    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response).isNotNull();
    ArgumentCaptor<GitContextRequestParams> gitContextCaptor = ArgumentCaptor.forClass(GitContextRequestParams.class);
    verify(gitAwareEntityHelper, times(1)).fetchYAMLFromRemote(any(Scope.class), gitContextCaptor.capture(), any());
    GitContextRequestParams params = gitContextCaptor.getValue();
    assertThat(params.getConnectorRef()).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testFetchYamlFromGitWorksWithCommitId() throws IOException {
    String nodeExecutionId = "nodeExecutionId";
    String yaml = getYaml("pipeline.yml");

    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(ParameterField.createValueField("git-conn"))
                              .filePath(ParameterField.createValueField(".harness/pipeline.yaml"))
                              .branchName(ParameterField.createValueField("feature/test"))
                              .commitId(ParameterField.createValueField("abc123def456"))
                              .repoName(ParameterField.createValueField("repo"))
                              .build();
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    when(pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), any(), any(), any(), any()))
        .thenReturn(templateMergeResponseDTO);

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);
    when(AmbianceUtils.obtainCurrentRuntimeId(any())).thenReturn(nodeExecutionId);
    when(AmbianceUtils.obtainStepIdentifier(any())).thenReturn("step_1");
    when(AmbianceUtils.getOrgIdentifier(any())).thenReturn("orgId");
    when(AmbianceUtils.getProjectIdentifier(any())).thenReturn("projId");
    when(AmbianceUtils.getParentUniqueIdentifier(any())).thenReturn("parentUniqueId");

    doReturn(Plan.builder().planNodes(List.of(PlanNode.builder().build())).build())
        .when(planCreationQueueRequestHelper)
        .createAndAppendToExistingPlan(any(), any(), any());

    ArgumentCaptor<GitContextRequestParams> gitContextCaptor = ArgumentCaptor.forClass(GitContextRequestParams.class);
    when(gitAwareEntityHelper.fetchYAMLFromRemote(any(Scope.class), gitContextCaptor.capture(), any()))
        .thenReturn(yaml);

    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response).isNotNull();
    GitContextRequestParams params = gitContextCaptor.getValue();
    assertThat(params.getCommitId()).isEqualTo("abc123def456");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testFetchYamlFromGitUsesScopeInfoWhenAvailable() throws IOException {
    String nodeExecutionId = "nodeExecutionId";
    String yaml = getYaml("pipeline.yml");

    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(ParameterField.createValueField("git-conn"))
                              .filePath(ParameterField.createValueField(".harness/pipeline.yaml"))
                              .branchName(ParameterField.createValueField("feature/test"))
                              .repoName(ParameterField.createValueField("repo"))
                              .build();
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    when(pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), any(), any(), any(), any()))
        .thenReturn(templateMergeResponseDTO);

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);
    when(AmbianceUtils.obtainCurrentRuntimeId(any())).thenReturn(nodeExecutionId);
    when(AmbianceUtils.obtainStepIdentifier(any())).thenReturn("step_1");
    when(AmbianceUtils.getParentUniqueIdentifier(any())).thenReturn("parentUniqueId");

    // Mock scopeInfo to be non-null
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("accountId")
                              .orgIdentifier("orgId")
                              .projectIdentifier("projId")
                              .uniqueId("accountId/orgId/projId")
                              .build();
    when(scopeResolutionHelper.getScopeInfo(any(), any())).thenReturn(scopeInfo);

    doReturn(Plan.builder().planNodes(List.of(PlanNode.builder().build())).build())
        .when(planCreationQueueRequestHelper)
        .createAndAppendToExistingPlan(any(), any(), any());

    ArgumentCaptor<Scope> scopeCaptor = ArgumentCaptor.forClass(Scope.class);
    when(gitAwareEntityHelper.fetchYAMLFromRemote(scopeCaptor.capture(), any(GitContextRequestParams.class), any()))
        .thenReturn(yaml);

    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response).isNotNull();
    Scope capturedScope = scopeCaptor.getValue();
    assertThat(capturedScope.getAccountIdentifier()).isEqualTo("accountId");
    assertThat(capturedScope.getOrgIdentifier()).isEqualTo("orgId");
    assertThat(capturedScope.getProjectIdentifier()).isEqualTo("projId");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testFetchYamlFromGitThrowsExceptionWhenGitReturnsEmptyYaml() throws IOException {
    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(ParameterField.createValueField("git-conn"))
                              .filePath(ParameterField.createValueField(".harness/pipeline.yaml"))
                              .branchName(ParameterField.createValueField("feature/test"))
                              .repoName(ParameterField.createValueField("repo"))
                              .build();
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);
    when(AmbianceUtils.getOrgIdentifier(any())).thenReturn("orgId");
    when(AmbianceUtils.getProjectIdentifier(any())).thenReturn("projId");
    when(AmbianceUtils.getParentUniqueIdentifier(any())).thenReturn("parentUniqueId");
    when(scopeResolutionHelper.getScopeInfo(any(), any())).thenReturn(null);

    // Mock gitAwareEntityHelper to return empty string
    when(gitAwareEntityHelper.fetchYAMLFromRemote(any(Scope.class), any(GitContextRequestParams.class), any()))
        .thenReturn("");

    // The exception is wrapped in a try-catch, so check the cause message
    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Kindly provide valid YAML for dynamic execution")
        .hasCauseInstanceOf(InvalidYamlException.class)
        .satisfies(
            throwable -> { assertThat(throwable.getCause().getMessage()).contains("Failed to fetch YAML from Git"); });
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testFetchYamlFromGitThrowsExceptionWhenGitThrowsException() throws IOException {
    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(ParameterField.createValueField("git-conn"))
                              .filePath(ParameterField.createValueField(".harness/pipeline.yaml"))
                              .branchName(ParameterField.createValueField("feature/test"))
                              .repoName(ParameterField.createValueField("repo"))
                              .build();
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);
    when(AmbianceUtils.getOrgIdentifier(any())).thenReturn("orgId");
    when(AmbianceUtils.getProjectIdentifier(any())).thenReturn("projId");
    when(AmbianceUtils.getParentUniqueIdentifier(any())).thenReturn("parentUniqueId");
    when(scopeResolutionHelper.getScopeInfo(any(), any())).thenReturn(null);

    // Mock gitAwareEntityHelper to throw exception
    when(gitAwareEntityHelper.fetchYAMLFromRemote(any(Scope.class), any(GitContextRequestParams.class), any()))
        .thenThrow(new RuntimeException("Git connection failed"));

    // The exception is wrapped in a try-catch, so check the cause message
    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Kindly provide valid YAML for dynamic execution")
        .hasCauseInstanceOf(InvalidYamlException.class)
        .satisfies(throwable -> {
          assertThat(throwable.getCause().getMessage()).contains("Error fetching YAML from Git: Git connection failed");
        });
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetResolvedValueThrowsExceptionWhenFetchFinalValueFails() {
    // Create a ParameterField that will throw exception on fetchFinalValue
    ParameterField<String> problematicField = ParameterField.createValueField("test");
    // Mock the resolved GitConfig to have a field that throws exception
    GitConfig gitConfig = GitConfig.builder()
                              .connectorRef(ParameterField.createValueField("git-conn"))
                              .filePath(problematicField)
                              .branchName(ParameterField.createValueField("feature/test"))
                              .repoName(ParameterField.createValueField("repo"))
                              .build();
    GitSourceConfig gitSourceConfig = GitSourceConfig.builder().spec(gitConfig).build();
    DynamicStageStepParameters stepParameters =
        DynamicStageStepParameters.builder().sourceConfig(gitSourceConfig).build();

    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);
    when(AmbianceUtils.getOrgIdentifier(any())).thenReturn("orgId");
    when(AmbianceUtils.getProjectIdentifier(any())).thenReturn("projId");
    when(AmbianceUtils.getParentUniqueIdentifier(any())).thenReturn("parentUniqueId");
    when(scopeResolutionHelper.getScopeInfo(any(), any())).thenReturn(null);

    // Mock expression resolution to return a GitConfig with a field that throws exception
    when(pmsEngineExpressionService.resolve(any(Ambiance.class), any(GitConfig.class), any(ExpressionMode.class)))
        .thenAnswer(invocation -> {
          GitConfig original = invocation.getArgument(1);
          // Create a new GitConfig with a problematic filePath that will throw exception
          ParameterField<String> badField = ParameterField.createExpressionField(true, "<+invalid>", null, true);
          // We can't easily make fetchFinalValue throw, so let's just return a config that will fail validation
          return GitConfig.builder()
              .connectorRef(original.getConnectorRef())
              .filePath(badField)
              .branchName(original.getBranchName())
              .repoName(original.getRepoName())
              .build();
        });

    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidYamlException.class);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testIsGitStoreProvidedReturnsFalseWhenSourceConfigIsNull() {
    DynamicStageStepParameters stepParameters = DynamicStageStepParameters.builder().sourceConfig(null).build();
    // We can't directly test isGitStoreProvided as it's private, but we can test through obtainChild
    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.getAccountId(any())).thenReturn(ACCOUNT_ID);

    // Should fall back to inline source validation and fail
    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Kindly provide valid YAML for dynamic execution")
        .hasCauseInstanceOf(InvalidYamlException.class)
        .satisfies(throwable -> {
          assertThat(throwable.getCause().getMessage())
              .contains("Either 'source' (inline YAML) or 'sourceConfig' (Git store configuration) must be provided");
        });
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testObtainChildWithParallelAsFirstStage() throws IOException {
    String nodeExecutionId = "nodeExecutionId";
    String yaml = getYaml("pipeline-with-parallel-first.yml");
    String encodedYaml = Base64.getEncoder().encodeToString(yaml.getBytes());
    DynamicStageStepParameters stepParameters = DynamicStageStepParameters.builder().source(encodedYaml).build();

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    when(pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), any(), any(), any(), any()))
        .thenReturn(templateMergeResponseDTO);
    Mockito.mockStatic(AmbianceUtils.class);
    when(AmbianceUtils.obtainCurrentRuntimeId(any())).thenReturn(nodeExecutionId);
    doReturn(Plan.builder().planNodes(List.of(PlanNode.builder().build())).build())
        .when(planCreationQueueRequestHelper)
        .createAndAppendToExistingPlan(any(), any(), any());
    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());
    assertThat(response).isNotNull();
    assertThat(response.getChildNodeId()).isNotNull();
    verify(planCreationQueueRequestHelper, times(1)).createAndAppendToExistingPlan(any(), any(), any());
    ArgumentCaptor<DynamicExecutionInstanceRequestDTO> argumentCaptor =
        ArgumentCaptor.forClass(DynamicExecutionInstanceRequestDTO.class);
    verify(dynamicExecutionService, times(1)).create(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getYaml()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testObtainChildThrowsWhenNoStages() throws IOException {
    String yaml = getYaml("pipeline-empty-stages.yml");
    String encodedYaml = Base64.getEncoder().encodeToString(yaml.getBytes());
    DynamicStageStepParameters stepParameters = DynamicStageStepParameters.builder().source(encodedYaml).build();
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    when(pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), any(), any(), any(), any()))
        .thenReturn(templateMergeResponseDTO);
    doReturn(Plan.builder().planNodes(List.of()).build())
        .when(planCreationQueueRequestHelper)
        .createAndAppendToExistingPlan(any(), any(), any());
    assertThatThrownBy(() -> dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Kindly make sure that YAML is correct and it has at least one stage.");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testObtainChildWithChildNodeId_RollbackMode_CopiesDynamicInstanceWhenFound() {
    String originalPlanExecutionId = "originalPlanExecutionId";
    Ambiance rollbackAmbiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(PLAN_EXECUTION_ID)
            .putSetupAbstractions("accountId", ACCOUNT_ID)
            .addLevels(Level.newBuilder().setIdentifier("step_1").setRuntimeId("parentNodeExecutionId").build())
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                             .setOriginalPlanExecutionIdForRollbackMode(originalPlanExecutionId)
                             .build())
            .build();

    DynamicStageStepParameters stepParameters = DynamicStageStepParameters.builder().childNodeId("childNodeId").build();
    doReturn(Optional.empty()).when(planExecutionMetadataService).findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID);

    DynamicExecutionInstanceResponseDTO storedInstance = DynamicExecutionInstanceResponseDTO.builder()
                                                             .yaml("storedYaml")
                                                             .processedYaml("storedProcessedYaml")
                                                             .planExecutionId(originalPlanExecutionId)
                                                             .build();
    doReturn(Optional.of(storedInstance))
        .when(dynamicExecutionService)
        .getByPlanExecutionIdAndIdentifier(originalPlanExecutionId, "step_1");

    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(rollbackAmbiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response.getChildNodeId()).isEqualTo("childNodeId");
    ArgumentCaptor<DynamicExecutionInstanceRequestDTO> captor =
        ArgumentCaptor.forClass(DynamicExecutionInstanceRequestDTO.class);
    verify(dynamicExecutionService, times(1)).create(captor.capture());
    DynamicExecutionInstanceRequestDTO createRequest = captor.getValue();
    assertThat(createRequest.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(createRequest.getYaml()).isEqualTo("storedYaml");
    assertThat(createRequest.getProcessedYaml()).isEqualTo("storedProcessedYaml");
    assertThat(createRequest.getIdentifier()).isEqualTo("step_1");
    assertThat(createRequest.getNodeExecutionId()).isEqualTo("parentNodeExecutionId");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testObtainChildWithChildNodeId_WhenNoDynamicInstanceFound_DoesNotCreate() {
    String originalPlanExecutionId = "originalPlanExecutionId";
    Ambiance rollbackAmbiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(PLAN_EXECUTION_ID)
            .putSetupAbstractions("accountId", ACCOUNT_ID)
            .addLevels(Level.newBuilder().setIdentifier("step_1").setRuntimeId("parentNodeExecutionId").build())
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                             .setOriginalPlanExecutionIdForRollbackMode(originalPlanExecutionId)
                             .build())
            .build();

    DynamicStageStepParameters stepParameters = DynamicStageStepParameters.builder().childNodeId("childNodeId").build();
    doReturn(Optional.empty()).when(planExecutionMetadataService).findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID);
    doReturn(Optional.empty())
        .when(dynamicExecutionService)
        .getByPlanExecutionIdAndIdentifier(originalPlanExecutionId, "step_1");

    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(rollbackAmbiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response.getChildNodeId()).isEqualTo("childNodeId");
    verify(dynamicExecutionService, never()).create(any(DynamicExecutionInstanceRequestDTO.class));
    verify(dynamicExecutionService, times(1)).getByPlanExecutionIdAndIdentifier(originalPlanExecutionId, "step_1");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testObtainChildWithChildNodeId_WhenNotRetryOrRollback_DoesNotCreate() {
    DynamicStageStepParameters stepParameters = DynamicStageStepParameters.builder().childNodeId("childNodeId").build();
    doReturn(Optional.empty()).when(planExecutionMetadataService).findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID);
    doReturn(Optional.empty()).when(dynamicExecutionService).getByPlanExecutionIdAndIdentifier("", "step_1");

    ChildExecutableResponse response =
        dynamicStageStep.obtainChild(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response.getChildNodeId()).isEqualTo("childNodeId");
    verify(dynamicExecutionService, times(1)).getByPlanExecutionIdAndIdentifier("", "step_1");
    verify(dynamicExecutionService, never()).create(any(DynamicExecutionInstanceRequestDTO.class));
  }
}
