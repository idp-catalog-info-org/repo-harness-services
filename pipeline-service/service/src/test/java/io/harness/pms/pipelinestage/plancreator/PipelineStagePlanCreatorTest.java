/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.plancreator;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanExecutionContext;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineServiceImpl;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.plan.execution.helper.PipelineStageHelper;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.utils.GitxBranchContextUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.steps.pipelinestage.PipelineStageNode;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.core.failurestrategy.action.NGFailureActionTypeConstants;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStagePlanCreatorTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock PipelineStageHelper pipelineStageHelper;
  @Mock KryoSerializer kryoSerializer;
  @Mock PMSPipelineServiceImpl pmsPipelineService;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PipelineEnforcementService pipelineEnforcementService;
  @Mock BarrierService barrierService;
  @Mock PipelineBarrierExtractor pipelineBarrierExtractor;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @InjectMocks PipelineStagePlanCreator pipelineStagePlanCreator;

  private String ORG = "org";
  private String PROJ = "proj";
  private String PIPELINE = "pipeline";
  private String ACC = "acc";
  private String PIP = "childPipeline";
  ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId("unique-id").build();

  @Before
  public void setup() {
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    GitEntityInfo parent = GitEntityInfo.builder().branch("main").connectorRef("conn").repoName("repo").build();
    GitAwareContextHelper.updateGitEntityContext(parent);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(pipelineStagePlanCreator.getFieldClass()).isEqualTo(PipelineStageNode.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(pipelineStagePlanCreator.getSupportedTypes().get(YAMLFieldNameConstants.STAGE))
        .contains(StepSpecTypeConstants.PIPELINE_STAGE);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStepParameter() throws IOException {
    String pipelineInputs = "---\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "type: \"Pipeline\"\n"
        + "stages:\n"
        + "   - stage:\n"
        + "       spec:\n"
        + "         pipeline: \"childPipeline\"\n"
        + "         org: \"org\"\n";

    YamlField yamlField = YamlUtils.readTree(pipelineInputs);
    PipelineStageConfig config = PipelineStageConfig.builder()
                                     .pipeline(PIPELINE)
                                     .org(ORG)
                                     .project(PROJ)
                                     .inputSetReferences(Collections.singletonList("ref"))
                                     .build();
    doReturn(null).when(pipelineStageHelper).getInputSetJsonNode(yamlField, HarnessYamlVersion.V0);

    Map<String, String> tags = new HashMap<>();
    tags.put("env", "prod");
    PipelineStageNode stageNode = new PipelineStageNode();
    stageNode.setName("My Pipeline Stage");
    stageNode.setIdentifier("s1");
    stageNode.setDescription(io.harness.pms.yaml.ParameterField.createValueField("stage description"));
    stageNode.setTags(tags);

    PipelineStageStepParameters stepParameters =
        pipelineStagePlanCreator.getStepParameter(config, yamlField, "planNodeId", HarnessYamlVersion.V0, stageNode);
    assertThat(stepParameters.getPipeline()).isEqualTo(PIPELINE);
    assertThat(stepParameters.getOrg()).isEqualTo(ORG);
    assertThat(stepParameters.getProject()).isEqualTo(PROJ);
    assertThat(stepParameters.getStageNodeId()).isEqualTo("planNodeId");
    assertThat(stepParameters.getPipelineInputsJsonNode()).isEqualTo(null);
    assertThat(stepParameters.getInputSetReferences().size()).isEqualTo(1);
    assertThat(stepParameters.getInputSetReferences().get(0)).isEqualTo("ref");
    assertThat(stepParameters.getName()).isEqualTo("My Pipeline Stage");
    assertThat(stepParameters.getIdentifier()).isEqualTo("s1");
    assertThat(stepParameters.getDescription()).isEqualTo("stage description");
    assertThat(stepParameters.getTags()).containsEntry("env", "prod");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForField() throws IOException {
    String yamlField = "stage:\n"
        + "  name: \"parent pipeline\"\n"
        + "  identifier: parent_pipeline\n"
        + "  timeout: \"1w\"\n"
        + "  type: \"Pipeline\"\n"
        + "  __uuid: uuid\n"
        + "  spec:\n"
        + "    pipeline: \"childPipeline\"\n"
        + "    org: \"org\"\n"
        + "    project: \"project\"\n";

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: parent_pipeline\n"
        + "          project: project\n"
        + "          inputSetReferences: []\n"
        + "          outputs: []\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("branch").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);
    MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());
    PlanCreationResponse response = pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);
    mockSettings.close();
    assertThat(SecurityContextBuilder.getPrincipal()).isNotNull();
    assertThat(response.getPlanNode()).isNotNull();
    PlanNode planNode = response.getPlanNode();
    assertThat(planNode.getName()).isEqualTo(pipelineStageNode.getName());
    assertThat(planNode.getIdentifier()).isEqualTo(pipelineStageNode.getIdentifier());
    assertThat(planNode.getGroup()).isEqualTo(StepCategory.STAGE.name());
    assertThat(planNode.getFacilitatorObtainments().get(0).getType().getType())
        .isEqualTo(OrchestrationFacilitatorType.ASYNC);
    assertThat(planNode.getExpressionMode()).isEqualTo(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

    StepParameters stepParameters = planNode.getStepParameters();
    assertThat(stepParameters).isInstanceOf(PipelineStageStepParameters.class);
    PipelineStageStepParameters pipelineStageStepParameters = (PipelineStageStepParameters) stepParameters;
    assertThat(pipelineStageStepParameters.getName()).isEqualTo(pipelineStageNode.getName());
    assertThat(pipelineStageStepParameters.getIdentifier()).isEqualTo(pipelineStageNode.getIdentifier());

    // Verifying the new Git Context For ChildPipeline
    verify(pmsGitSyncHelper, times(1)).getEntityGitDetailsFromBytes(any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithTimeoutObtainment() throws IOException {
    String yamlField = "stage:\n"
        + "  name: \"parent pipeline\"\n"
        + "  identifier: parent_pipeline\n"
        + "  timeout: \"30s\"\n"
        + "  type: \"Pipeline\"\n"
        + "  __uuid: uuid\n"
        + "  spec:\n"
        + "    pipeline: \"childPipeline\"\n"
        + "    org: \"org\"\n"
        + "    project: \"project\"\n";

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: parent_pipeline\n"
        + "          project: project\n"
        + "          inputSetReferences: []\n"
        + "          outputs: []\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repoName").branch("branch").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);

    try (MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS)) {
      when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());
      PlanCreationResponse response = pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);

      assertThat(response.getPlanNode()).isNotNull();
      PlanNode planNode = response.getPlanNode();

      // Verify timeout obtainment is set
      assertThat(planNode.getTimeoutObtainments()).isNotNull();
      assertThat(planNode.getTimeoutObtainments()).isNotEmpty();
      assertThat(planNode.getTimeoutObtainments().size()).isEqualTo(1);

      SdkTimeoutObtainment timeoutObtainment = planNode.getTimeoutObtainments().get(0);
      assertThat(timeoutObtainment.getDimension()).isEqualTo(AbsoluteTimeoutTrackerFactory.DIMENSION);
      assertThat(timeoutObtainment.getParameters()).isNotNull();
      // Verify the timeout is 30 seconds (30000 milliseconds)
      assertThat(timeoutObtainment.getParameters().prepareTimeoutParameters().getTimeoutMillis()).isEqualTo(30000L);
    }
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithDevopsEssentials() throws IOException {
    String yamlField = "stage:\n"
        + "  name: \"parent pipeline\"\n"
        + "  identifier: parent_pipeline\n"
        + "  timeout: \"1w\"\n"
        + "  type: \"Pipeline\"\n"
        + "  __uuid: uuid\n"
        + "  spec:\n"
        + "    pipeline: \"childPipeline\"\n"
        + "    org: \"org\"\n"
        + "    project: \"project\"\n";

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: parent_pipeline\n"
        + "          project: project\n"
        + "          inputSetReferences: []\n"
        + "          outputs: []\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("branch").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
    MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());
    pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);
    mockSettings.close();

    verify(pipelineEnforcementService, times(1)).validatePipelineChainingEnforcement("acc");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithFailureStrategy() throws IOException {
    String ignoreFailureYamlField = getFailureYamlField(NGFailureActionTypeConstants.IGNORE);

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(ignoreFailureYamlField);

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: parent_pipeline\n"
        + "          project: project\n"
        + "          inputSetReferences: []\n"
        + "          outputs: []\n";

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(ignoreFailureYamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("branch").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    doReturn(new byte[9]).when(kryoSerializer).asBytes(any());
    PipelineStageNode stageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);

    MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());
    pipelineStagePlanCreator.createPlanForField(ctx, stageNode);
    mockSettings.close();

    verify(pipelineStageHelper, times(1)).validateFailureStrategy(stageNode.getFailureStrategies());
  }

  @NotNull
  private String getFailureYamlField(String action) {
    String yamlField = "stage:\n"
        + "  name: \"parent pipeline\"\n"
        + "  identifier: parent_pipeline\n"
        + "  timeout: \"1w\"\n"
        + "  type: \"Pipeline\"\n"
        + "  __uuid: uuid\n"
        + "  failureStrategies:\n"
        + "    - onFailure:\n"
        + "        errors:\n"
        + "           - AllErrors\n"
        + "        action:\n"
        + "           type: " + action + "\n"
        + "  spec:\n"
        + "    pipeline: \"childPipeline\"\n"
        + "    org: \"org\"\n"
        + "    project: \"project\"\n";
    return yamlField;
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions() {
    assertThat(pipelineStagePlanCreator.getSupportedYamlVersions()).isEqualTo(Set.of(HarnessYamlVersion.V0));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithBarriers() throws IOException {
    // Setup YAML structure
    String yamlField = "stage:\n"
        + "  name: \"parent pipeline\"\n"
        + "  identifier: parent_pipeline\n"
        + "  timeout: \"1w\"\n"
        + "  type: \"Pipeline\"\n"
        + "  __uuid: uuid\n"
        + "  spec:\n"
        + "    pipeline: \"childPipeline\"\n"
        + "    org: \"org\"\n"
        + "    project: \"project\"\n";

    // Create YAML fields
    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    YamlField inputsField = YamlUtils.readTree("inputs:").getNode().getField(YAMLFieldNameConstants.INPUTS);

    // Create context with proper execution UUID in metadata
    String executionUuid = generateUuid();
    PlanCreationContextValue value =
        PlanCreationContextValue.newBuilder()
            .setAccountIdentifier(ACC)
            .setExecutionContext(PlanExecutionContext.newBuilder().setExecutionUuid(executionUuid))
            .build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .build();

    // Setup mocks for pipeline service
    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline(ACC, ORG, "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("branch").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    // Setup barrier extraction mocks
    List<String> barrierRefs = Collections.singletonList("parent.barrier1");
    when(pipelineBarrierExtractor.getChildPipelineInputsField(ctx)).thenReturn(inputsField);
    when(pipelineBarrierExtractor.getAllBarriersUsedInChildPipeline(inputsField)).thenReturn(barrierRefs);

    // Get pipeline stage node
    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);

    // Setup additional mocks for YamlUtils
    MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());

    // Execute the method
    pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);
    mockSettings.close();

    // Verify barrier service interaction
    verify(barrierService)
        .upsertBarrierExecutionInstance(isNull(),
            eq("barrier1"), // Should strip "parent." prefix
            eq("barrier1"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true), eq(executionUuid),
            anyString());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithchildBranchOverridesForFetchAndRestores() throws IOException {
    String ignoreFailureYamlField = getFailureYamlField(NGFailureActionTypeConstants.IGNORE);

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(ignoreFailureYamlField);

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: childPipeline\n"
        + "          project: project\n"
        + "          gitBranch: devtest\n";

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(ignoreFailureYamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("main").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    doReturn(new byte[9]).when(kryoSerializer).asBytes(any());
    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);
    pipelineStageNode.getPipelineStageConfig().setGitBranch(
        io.harness.pms.yaml.ParameterField.createValueField("devtest")); // add devtest child branch

    MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());
    PipelineEntity child =
        PipelineEntity.builder().identifier(PIP).storeType(StoreType.REMOTE).harnessVersion("v1").build();

    try (MockedStatic<GitxBranchContextUtils> mocked = mockStatic(GitxBranchContextUtils.class)) {
      // Let the supplier execute and return the mocked child
      mocked.when(() -> GitxBranchContextUtils.withBranch(any(), eq("devtest"), any()))
          .thenAnswer(inv -> Optional.of(child));

      PlanCreationResponse resp = pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);
      assertThat(resp).isNotNull();
      StepParameters stepParams = resp.getPlanNode().getStepParameters();
      assertThat(stepParams).isInstanceOf(PipelineStageStepParameters.class);

      PipelineStageStepParameters p = (PipelineStageStepParameters) stepParams;
      assertThat(p.getGitBranch()).isEqualTo("devtest");

      // Verify we invoked the child-branch guard with the expected branch
      mocked.verify(() -> GitxBranchContextUtils.withBranch(any(), eq("devtest"), any()), times(2));

      mockSettings.close();
      // After fetch, branch restored to parent
      assertThat(GitAwareContextHelper.getGitRequestParamsInfo().getBranch()).isEqualTo("main");
    }
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithoutBranchUsesParentBranch() throws IOException {
    String ignoreFailureYamlField = getFailureYamlField(NGFailureActionTypeConstants.IGNORE);

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(ignoreFailureYamlField);

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: childPipeline\n"
        + "          project: project\n";

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(ignoreFailureYamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("main").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    doReturn(new byte[9]).when(kryoSerializer).asBytes(any());
    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);

    MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());
    PipelineEntity child =
        PipelineEntity.builder().identifier(PIP).storeType(StoreType.REMOTE).harnessVersion("v1").build();

    try (MockedStatic<GitxBranchContextUtils> mocked = mockStatic(GitxBranchContextUtils.class)) {
      mocked.when(() -> GitxBranchContextUtils.withBranch(any(), any(), any())).thenAnswer(inv -> Optional.of(child));

      PlanCreationResponse resp = pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);
      assertThat(resp).isNotNull();
      StepParameters stepParams = resp.getPlanNode().getStepParameters();
      assertThat(stepParams).isInstanceOf(PipelineStageStepParameters.class);

      PipelineStageStepParameters p = (PipelineStageStepParameters) stepParams;
      assertThat(p.getGitBranch()).isNull();

      // Verify we invoked the child-branch guard with the expected branch
      mocked.verify(() -> GitxBranchContextUtils.withBranch(any(), eq(null), any()), times(2));

      mockSettings.close();
      // After fetch, branch restored to parent
      assertThat(GitAwareContextHelper.getGitRequestParamsInfo().getBranch()).isEqualTo("main");
    }
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithoutRemotePipelineChildParameterBranchIsNull() throws IOException {
    String ignoreFailureYamlField = getFailureYamlField(NGFailureActionTypeConstants.IGNORE);

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(ignoreFailureYamlField);

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: parent_pipeline\n"
        + "          project: project\n";

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(ignoreFailureYamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("main").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    doReturn(new byte[9]).when(kryoSerializer).asBytes(any());
    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);

    MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());
    PipelineEntity child =
        PipelineEntity.builder().identifier(PIP).storeType(StoreType.INLINE).harnessVersion("v1").build();

    try (MockedStatic<GitxBranchContextUtils> mocked = mockStatic(GitxBranchContextUtils.class)) {
      mocked.when(() -> GitxBranchContextUtils.withBranch(any(), any(), any())).thenAnswer(inv -> Optional.of(child));

      PlanCreationResponse resp = pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);
      assertThat(resp).isNotNull();
      StepParameters stepParams = resp.getPlanNode().getStepParameters();
      assertThat(stepParams).isInstanceOf(PipelineStageStepParameters.class);

      PipelineStageStepParameters p = (PipelineStageStepParameters) stepParams;
      assertThat(p.getGitBranch()).isNull();

      // Verify we invoked the child-branch guard with the expected branch
      mocked.verify(() -> GitxBranchContextUtils.withBranch(any(), eq(null), any()), times(2));

      mockSettings.close();
      // After fetch, branch restored to parent
      assertThat(GitAwareContextHelper.getGitRequestParamsInfo().getBranch()).isEqualTo("main");
    }
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithoutRemotePipelineChildParameterBranchIsBlank() throws IOException {
    String ignoreFailureYamlField = getFailureYamlField(NGFailureActionTypeConstants.IGNORE);

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(ignoreFailureYamlField);

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: parent_pipeline\n"
        + "          project: project\n"
        + "          gitBranch: \"\"\n";

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(ignoreFailureYamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("main").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    doReturn(new byte[9]).when(kryoSerializer).asBytes(any());
    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);

    MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());
    PipelineEntity child =
        PipelineEntity.builder().identifier(PIP).storeType(StoreType.INLINE).harnessVersion("v1").build();

    try (MockedStatic<GitxBranchContextUtils> mocked = mockStatic(GitxBranchContextUtils.class)) {
      mocked.when(() -> GitxBranchContextUtils.withBranch(any(), any(), any())).thenAnswer(inv -> Optional.of(child));

      PlanCreationResponse resp = pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);
      assertThat(resp).isNotNull();
      StepParameters stepParams = resp.getPlanNode().getStepParameters();
      assertThat(stepParams).isInstanceOf(PipelineStageStepParameters.class);

      PipelineStageStepParameters p = (PipelineStageStepParameters) stepParams;
      assertThat(p.getGitBranch()).isNull();

      // Verify we invoked the child-branch guard with the expected branch
      mocked.verify(() -> GitxBranchContextUtils.withBranch(any(), eq(null), any()), times(2));

      mockSettings.close();
      // After fetch, branch restored to parent
      assertThat(GitAwareContextHelper.getGitRequestParamsInfo().getBranch()).isEqualTo("main");
    }
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithStrategyWhenFFDisabled() throws IOException {
    String yamlField = "stage:\n"
        + "  name: \"parent pipeline\"\n"
        + "  identifier: parent_pipeline\n"
        + "  timeout: \"1w\"\n"
        + "  type: \"Pipeline\"\n"
        + "  __uuid: uuid\n"
        + "  strategy:\n"
        + "    repeat:\n"
        + "      times: 3\n"
        + "  spec:\n"
        + "    pipeline: \"childPipeline\"\n"
        + "    org: \"org\"\n"
        + "    project: \"project\"\n";

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        strategy:\n"
        + "          repeat:\n"
        + "            times: 3\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: childPipeline\n"
        + "          project: project\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("branch").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    // FF disabled
    when(pmsFeatureFlagService.isEnabled(eq("acc"), eq(FeatureName.PIPE_ENABLE_STRATEGY_FOR_CHAINED_PIPELINES)))
        .thenReturn(false);

    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);

    try (MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS)) {
      when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());

      // Should throw InvalidRequestException when FF is disabled and strategy is present
      try {
        pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);
        org.junit.Assert.fail("Expected InvalidRequestException to be thrown");
      } catch (InvalidRequestException e) {
        assertThat(e.getMessage()).contains("Strategy is not supported for Pipeline stage");
        assertThat(e.getMessage()).contains("parent_pipeline");
      }
    }
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithStrategyWhenFFEnabled() throws IOException {
    String yamlField = "stage:\n"
        + "  name: \"parent pipeline\"\n"
        + "  identifier: parent_pipeline\n"
        + "  timeout: \"1w\"\n"
        + "  type: \"Pipeline\"\n"
        + "  __uuid: stageUuid\n"
        + "  strategy:\n"
        + "    repeat:\n"
        + "      times: 3\n"
        + "      maxConcurrency: 2\n"
        + "    __uuid: strategyUuid\n"
        + "  spec:\n"
        + "    pipeline: \"childPipeline\"\n"
        + "    org: \"org\"\n"
        + "    project: \"project\"\n";

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        strategy:\n"
        + "          repeat:\n"
        + "            times: 3\n"
        + "            maxConcurrency: 2\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: childPipeline\n"
        + "          project: project\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    PlanCreationContextValue value =
        PlanCreationContextValue.newBuilder()
            .setAccountIdentifier("acc")
            .setExecutionContext(
                PlanExecutionContext.newBuilder()
                    .putFeatureFlagToValueMap(FeatureName.PIPE_ENABLE_STRATEGY_FOR_CHAINED_PIPELINES.name(), true)
                    .build())
            .build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("branch").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    // Mock KryoSerializer for strategy metadata serialization
    doReturn(new byte[9]).when(kryoSerializer).asDeflatedBytes(any());

    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);

    try (MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS)) {
      when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());

      // Should not throw exception when FF is enabled
      PlanCreationResponse response = pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);

      assertThat(response).isNotNull();
      assertThat(response.getPlanNode()).isNotNull();

      // Verify the node was created successfully
      PlanNode planNode = response.getPlanNode();
      assertThat(planNode).isNotNull();
    }
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreatePlanForFieldWithoutStrategyWhenFFDisabled() throws IOException {
    String yamlField = "stage:\n"
        + "  name: \"parent pipeline\"\n"
        + "  identifier: parent_pipeline\n"
        + "  timeout: \"1w\"\n"
        + "  type: \"Pipeline\"\n"
        + "  __uuid: uuid\n"
        + "  spec:\n"
        + "    pipeline: \"childPipeline\"\n"
        + "    org: \"org\"\n"
        + "    project: \"project\"\n";

    String pipelineYaml = "pipeline:\n"
        + "  name: parent\n"
        + "  identifier: parent\n"
        + "  projectIdentifier: project\n"
        + "  orgIdentifier: org\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: s1\n"
        + "        identifier: s1\n"
        + "        description: \"\"\n"
        + "        type: Pipeline\n"
        + "        spec:\n"
        + "          org: org\n"
        + "          pipeline: childPipeline\n"
        + "          project: project\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    PlanCreationContextValue value = PlanCreationContextValue.newBuilder().setAccountIdentifier("acc").build();
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Collections.singletonMap("metadata", value))
                                  .currentField(pipelineStageYamlField.getNode().getField("stage"))
                                  .yaml(pipelineYaml)
                                  .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    doReturn(EntityGitDetails.builder().repoName("repo").repoName("repoName").branch("branch").build())
        .when(pmsGitSyncHelper)
        .getEntityGitDetailsFromBytes(any());

    // FF disabled but no strategy defined - should work fine
    when(pmsFeatureFlagService.isEnabled(eq("acc"), eq(FeatureName.PIPE_ENABLE_STRATEGY_FOR_CHAINED_PIPELINES)))
        .thenReturn(false);

    PipelineStageNode pipelineStageNode = YamlUtils.read(
        pipelineStageYamlField.getNode().getField("stage").getNode().toString(), PipelineStageNode.class);

    try (MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS)) {
      when(YamlUtils.getGivenYamlNodeFromParentPath(any(), anyString())).thenReturn(pipelineStageYamlField.getNode());

      // Should NOT throw exception when no strategy is defined, regardless of FF state
      PlanCreationResponse response = pipelineStagePlanCreator.createPlanForField(ctx, pipelineStageNode);

      assertThat(response).isNotNull();
      assertThat(response.getPlanNode()).isNotNull();
    } finally {
      // Clean up security context to avoid affecting other tests
      SecurityContextBuilder.unsetCompleteContext();
    }
  }
}
