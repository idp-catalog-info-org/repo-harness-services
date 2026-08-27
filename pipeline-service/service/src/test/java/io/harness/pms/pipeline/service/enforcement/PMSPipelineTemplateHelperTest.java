/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.enforcement;

import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BHUMIJ;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.OM;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.GlobalTemplateConstants;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.engine.governance.OpaOnSaveStatusErrorDTO;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.HarnessRemoteServiceException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.manage.GlobalContextManager;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.RefreshRequestDTO;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceRequestDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.refresh.ErrorNodeSummary;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.preprocess.YamlPreProcessorFactory;
import io.harness.rule.Owner;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSPipelineTemplateHelperTest extends CategoryTest {
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private TemplateResourceClient templateResourceClient;
  @Mock private PipelineEnforcementService pipelineEnforcementService;
  @Mock private YamlPreProcessorFactory yamlPreProcessorFactory;
  @Mock private MetricService metricService;
  @InjectMocks private PMSPipelineTemplateHelper pipelineTemplateHelper;

  private static final String ACCOUNT_ID = "accountId";
  private static final String PROJECT_ID = "projectId";
  private static final String ORG_ID = "orgId";
  private static final String GIVEN_YAML = "yaml";
  private static final PipelineEntity pipelineEntity =
      PipelineEntity.builder().harnessVersion(HarnessYamlVersion.V0).build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    pipelineTemplateHelper = new PMSPipelineTemplateHelper(pmsFeatureFlagHelper, templateResourceClient,
        pipelineEnforcementService, yamlPreProcessorFactory, metricService);
    doReturn(true).when(pipelineEnforcementService).isFeatureRestricted(any(), anyString());
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidTemplateInPipelineHasTemplateRef() throws IOException {
    String fileName = "pipeline-with-template-ref.yaml";
    String givenYaml = readFile(fileName);
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(TemplateApplyRequestDTO.class), any());
    when(callRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateMergeResponseDTO.builder().mergedPipelineYaml(givenYaml).build())));
    String resolveTemplateRefsInPipeline = pipelineTemplateHelper
                                               .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, givenYaml,
                                                   BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0)
                                               .getMergedPipelineYaml();
    verify(metricService, times(1)).incCounter(any());
    verify(metricService, times(1)).recordMetric(any(), anyDouble());
    assertThat(resolveTemplateRefsInPipeline).isEqualTo(givenYaml);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testInValidTemplateInPipelineWhenDoesNotContainTemplateRef() throws IOException {
    String fileName = "pipeline-with-template-ref.yaml";
    String givenYaml = readFile(fileName);
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, null, null, null, null, null,
            "false", TemplateApplyRequestDTO.builder().originalEntityYaml(givenYaml).build(), false);
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().build();
    when(callRequest.execute())
        .thenThrow(new InvalidRequestException("Exception in resolving template refs in given yaml."));
    assertThatThrownBy(()
                           -> pipelineTemplateHelper.resolveTemplateRefsInPipeline(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, givenYaml, BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Exception in resolving template refs in given yaml.");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetTemplateReferencesForGivenYamlWhenFFIsOnAndGitSyncNotEnabled() throws IOException {
    Call<ResponseDTO<List<EntityDetailProtoDTO>>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .getTemplateReferenceForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null,
            TemplateReferenceRequestDTO.builder().yaml(GIVEN_YAML).build());
    List<EntityDetailProtoDTO> expected =
        Collections.singletonList(EntityDetailProtoDTO.newBuilder().setType(EntityTypeProtoEnum.TEMPLATE).build());
    when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(expected)));

    List<EntityDetailProtoDTO> finalList =
        pipelineTemplateHelper.getTemplateReferencesForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, GIVEN_YAML);
    assertThat(finalList).isEqualTo(expected);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetRefreshedYamlWhenGitSyncNotEnabled() throws IOException {
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(GIVEN_YAML).build();
    RefreshResponseDTO refreshResponseDTO = RefreshResponseDTO.builder().refreshedYaml("refreshed yaml").build();
    Call<ResponseDTO<RefreshResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .getRefreshedYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, null, null, null, null, null, "0", "false",
            refreshRequest);
    when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(refreshResponseDTO)));

    RefreshResponseDTO refreshedResponse =
        pipelineTemplateHelper.getRefreshedYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, GIVEN_YAML, pipelineEntity, "false");
    assertThat(refreshedResponse).isEqualTo(refreshResponseDTO);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsForGivenYamlWhenGitSyncNotEnabled() throws IOException {
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(GIVEN_YAML).build();
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder()
            .validYaml(false)
            .errorNodeSummary(ErrorNodeSummary.builder().build())
            .build();
    Call<ResponseDTO<ValidateTemplateInputsResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .validateTemplateInputsForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, null, null, null, null,
            null, "0", "false", refreshRequest);
    when(callRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(validateTemplateInputsResponseDTO)));

    ValidateTemplateInputsResponseDTO responseDTO = pipelineTemplateHelper.validateTemplateInputsForGivenYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, GIVEN_YAML, pipelineEntity, "false");
    assertThat(responseDTO).isEqualTo(validateTemplateInputsResponseDTO);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsForGivenYamlUsesV2GitEntityInfoForBranchResolution() throws IOException {
    GitEntityInfo branchInfo = GitEntityInfo.builder().branch("non-default").build();
    setupGitContext(branchInfo);
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIPE_DISABLE_GIT_ENTITY_INFO_V2_FLOW);

    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(GIVEN_YAML).build();
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
    Call<ResponseDTO<ValidateTemplateInputsResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .validateTemplateInputsForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, "non-default", null, true, null,
            "testRepo2", ACCOUNT_ID, ORG_ID, PROJECT_ID, "0", "false", refreshRequest);
    when(callRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(validateTemplateInputsResponseDTO)));

    PipelineEntity inlinePipelineWithRemoteTemplate =
        PipelineEntity.builder().harnessVersion(HarnessYamlVersion.V0).repo("testRepo2").build();
    try {
      ValidateTemplateInputsResponseDTO responseDTO = pipelineTemplateHelper.validateTemplateInputsForGivenYaml(
          ACCOUNT_ID, ORG_ID, PROJECT_ID, GIVEN_YAML, inlinePipelineWithRemoteTemplate, "false");
      assertThat(responseDTO).isEqualTo(validateTemplateInputsResponseDTO);
    } finally {
      GlobalContextManager.unset();
    }
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsForGivenYamlUsesLegacyGitEntityInfoWhenFlagEnabled() throws IOException {
    GitEntityInfo branchInfo = GitEntityInfo.builder().branch("non-default").build();
    setupGitContext(branchInfo);
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIPE_DISABLE_GIT_ENTITY_INFO_V2_FLOW);

    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(GIVEN_YAML).build();
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
    Call<ResponseDTO<ValidateTemplateInputsResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .validateTemplateInputsForGivenYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, null, null, null, null,
            null, "0", "false", refreshRequest);
    when(callRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(validateTemplateInputsResponseDTO)));

    PipelineEntity inlinePipelineWithRemoteTemplate =
        PipelineEntity.builder().harnessVersion(HarnessYamlVersion.V0).repo("testRepo2").build();
    try {
      ValidateTemplateInputsResponseDTO responseDTO = pipelineTemplateHelper.validateTemplateInputsForGivenYaml(
          ACCOUNT_ID, ORG_ID, PROJECT_ID, GIVEN_YAML, inlinePipelineWithRemoteTemplate, "false");
      assertThat(responseDTO).isEqualTo(validateTemplateInputsResponseDTO);
    } finally {
      GlobalContextManager.unset();
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshAllTemplatesForYamlWhenGitSyncNotEnabled() throws IOException {
    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(GIVEN_YAML).build();
    YamlFullRefreshResponseDTO refreshResponseDTO =
        YamlFullRefreshResponseDTO.builder().shouldRefreshYaml(true).refreshedYaml("refreshed yaml").build();
    Call<ResponseDTO<YamlFullRefreshResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .refreshAllTemplatesForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, null, null, null, null, null, "0",
            "false", refreshRequest);
    when(callRequest.execute()).thenReturn(Response.success(ResponseDTO.newResponse(refreshResponseDTO)));

    YamlFullRefreshResponseDTO refreshedResponse = pipelineTemplateHelper.refreshAllTemplatesForYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, GIVEN_YAML, pipelineEntity, "false");
    assertThat(refreshedResponse).isEqualTo(refreshResponseDTO);
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }

  private void runResolveTemplateRefsForRepoNamePopulationTest(
      String contextRepoName, boolean ffDisableEnabled, String expectedParentEntityRepoName) throws IOException {
    GitEntityInfo branchInfo =
        GitEntityInfo.builder().branch("pipelineBranch").repoName(contextRepoName).connectorRef("conn").build();
    setupGitContext(branchInfo);
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIPE_DISABLE_GIT_ENTITY_INFO_V2_FLOW);
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIE_ERROR_ENHANCEMENTS);
    doReturn(ffDisableEnabled)
        .when(pmsFeatureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.PIPE_DISABLE_PARENT_REPO_NAME_CONTEXT_POPULATION_FOR_TEMPLATE);

    String fileName = "pipeline-with-template-ref.yaml";
    String givenYaml = readFile(fileName);
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2(anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(TemplateApplyRequestDTO.class), any());
    when(callRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateMergeResponseDTO.builder().mergedPipelineYaml(givenYaml).build())));

    PipelineEntity remotePipelineEntity = PipelineEntity.builder()
                                              .accountId(ACCOUNT_ID)
                                              .orgIdentifier(ORG_ID)
                                              .projectIdentifier(PROJECT_ID)
                                              .yaml(givenYaml)
                                              .repo("pipelineRepo")
                                              .harnessVersion(HarnessYamlVersion.V0)
                                              .build();
    try {
      pipelineTemplateHelper.resolveTemplateRefsInPipeline(remotePipelineEntity, BOOLEAN_FALSE_VALUE);

      verify(templateResourceClient)
          .applyTemplatesOnGivenYamlV2(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq("pipelineBranch"), any(),
              eq(true), any(), eq(expectedParentEntityRepoName), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
              eq("false"), any(TemplateApplyRequestDTO.class), eq(false));

      assertThat(GitAwareContextHelper.getGitRequestParamsInfo().getRepoName()).isEqualTo(contextRepoName);
    } finally {
      GlobalContextManager.unset();
    }
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testResolveTemplateRefsPopulatesRepoNameWhenContextRepoIsDefault() throws IOException {
    runResolveTemplateRefsForRepoNamePopulationTest(GitSyncConstants.DEFAULT, false, "pipelineRepo");
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testResolveTemplateRefsPopulatesRepoNameWhenContextRepoIsNull() throws IOException {
    runResolveTemplateRefsForRepoNamePopulationTest(null, false, "pipelineRepo");
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testResolveTemplateRefsPopulatesRepoNameWhenContextRepoIsEmpty() throws IOException {
    runResolveTemplateRefsForRepoNamePopulationTest("", false, "pipelineRepo");
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testResolveTemplateRefsAlwaysUsesPipelineRepoRegardlessOfContext() throws IOException {
    runResolveTemplateRefsForRepoNamePopulationTest("callerRepo", false, "pipelineRepo");
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testResolveTemplateRefsSkipsPopulateWhenFFDisableEnabled() throws IOException {
    runResolveTemplateRefsForRepoNamePopulationTest(GitSyncConstants.DEFAULT, true, GitSyncConstants.DEFAULT);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testResolveOnlyPipelineTemplateRefAndMerge() throws IOException {
    String pipelineYamlWithPipelineTemplate = readFile("test-pipeline-with-pipeline-template.yaml");
    String pipelineTemplateYaml = readFile("test-pipeline-template.yaml");
    BasicPipeline resolvedBasicPipeline = BasicPipeline.builder()
                                              .name("test-pipeline-with-pipeline-template")
                                              .identifier("testpipelinewithpipelinetemplate")
                                              .orgIdentifier("default")
                                              .projectIdentifier("RishiTest")
                                              .allowStageExecutions(true)
                                              .fixedInputsOnRerun(true)
                                              .tags(new HashMap<>())
                                              .build();
    Call<ResponseDTO<TemplateResponseDTO>> templateGetCallRequest = mock(Call.class);
    doReturn(templateGetCallRequest)
        .when(templateResourceClient)
        .get("testpipelinetemplate", "accountId", "orgId", "projectId", null, "v1", false, null, null, null, null, null,
            null, null, null, "false");
    when(templateGetCallRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateResponseDTO.builder().yaml(pipelineTemplateYaml).build())));
    String resolvedPipelineYaml = pipelineTemplateHelper.resolveOnlyPipelineTemplateRefAndMerge(
        pipelineYamlWithPipelineTemplate, "false", ACCOUNT_ID, ORG_ID, PROJECT_ID, "0");
    assertThat(resolvedBasicPipeline).isEqualTo(YamlUtils.read(resolvedPipelineYaml, BasicPipeline.class));
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolvePipelineWithAllTemplatesRuntimeInputsWithRuntimeInputsPresent() throws IOException {
    String pipelineYAMLFileName = "v1-pipeline-with-template-ref-with-runtime-inputs.yaml";
    String stepTemplateYAMLFileName = "v1-step-template.yaml";
    String stageTemplateYAMLFileName = "v1-stage-template.yaml";

    String pipelineYAML = readFile(pipelineYAMLFileName);
    String stepTemplateYAML = readFile(stepTemplateYAMLFileName);
    String stageTemplateYAML = readFile(stageTemplateYAMLFileName);

    PipelineEntity pipelineEntity =
        PipelineEntity.builder().yaml(pipelineYAML).harnessVersion(HarnessYamlVersion.V1).build();
    Call<ResponseDTO<TemplateResponseDTO>> stepTemplateCallRequest = mock(Call.class);
    doReturn(stepTemplateCallRequest)
        .when(templateResourceClient)
        .get("TestStepTemplate", ACCOUNT_ID, ORG_ID, PROJECT_ID, null, "", false, null, null, null, null, null, null,
            null, null, "false");
    when(stepTemplateCallRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplateResponseDTO.builder().yaml(stepTemplateYAML).yamlVersion(HarnessYamlVersion.V1).build())));

    Call<ResponseDTO<TemplateResponseDTO>> stageTemplateCallRequest = mock(Call.class);
    doReturn(stageTemplateCallRequest)
        .when(templateResourceClient)
        .get("TestStageTemplate", ACCOUNT_ID, ORG_ID, PROJECT_ID, null, "", false, null, null, null, null, null, null,
            null, null, "false");
    when(stageTemplateCallRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplateResponseDTO.builder().yaml(stageTemplateYAML).yamlVersion(HarnessYamlVersion.V1).build())));

    String result = pipelineTemplateHelper.resolvePipelineWithAllTemplatesRuntimeInputs(
        Optional.of(pipelineEntity).get().getYaml(), ACCOUNT_ID, ORG_ID, PROJECT_ID, "false");
    JsonNode resultNode = YamlUtils.readAsJsonNode(result);
    JsonNode pipelineNode = resultNode.get(YAMLFieldNameConstants.PIPELINE);
    assertThat(pipelineNode.has(YAMLFieldNameConstants.INPUTS)).isTrue();
    JsonNode inputsNode = pipelineNode.get(YAMLFieldNameConstants.INPUTS);
    assertThat(inputsNode.size()).isEqualTo(5);

    int inputsWithTypeString = 0;
    int inputsWithTypeBoolean = 0;
    int inputsWithTypeNumber = 0;
    int inputsWithTypeArray = 0;
    Iterator<Map.Entry<String, JsonNode>> fields = inputsNode.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode value = entry.getValue();
      if (value.get("type").asText().equals("string")) {
        inputsWithTypeString++;
      }
      if (value.get("type").asText().equals("boolean")) {
        inputsWithTypeBoolean++;
      }
      if (value.get("type").asText().equals("number")) {
        inputsWithTypeNumber++;
      }
      if (value.get("type").asText().equals("array")) {
        inputsWithTypeArray++;
      }
    }
    assertThat(inputsWithTypeString).isEqualTo(2);
    assertThat(inputsWithTypeNumber).isEqualTo(2);
    assertThat(inputsWithTypeBoolean).isEqualTo(1);
    assertThat(inputsWithTypeArray).isEqualTo(0);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolvePipelineWithAllTemplatesRuntimeInputsWithNoRuntimeInputsPresent() throws IOException {
    String pipelineYAMLFileName = "v1-pipeline-with-template-ref-with-no-runtime-inputs.yaml";
    String stepTemplateYAMLFileName = "v1-step-template.yaml";
    String stageTemplateYAMLFileName = "v1-stage-template.yaml";

    String pipelineYAML = readFile(pipelineYAMLFileName);
    String stepTemplateYAML = readFile(stepTemplateYAMLFileName);
    String stageTemplateYAML = readFile(stageTemplateYAMLFileName);

    PipelineEntity pipelineEntity =
        PipelineEntity.builder().yaml(pipelineYAML).harnessVersion(HarnessYamlVersion.V1).build();
    Call<ResponseDTO<TemplateResponseDTO>> stepTemplateCallRequest = mock(Call.class);
    doReturn(stepTemplateCallRequest)
        .when(templateResourceClient)
        .get("TestStepTemplate", ACCOUNT_ID, ORG_ID, PROJECT_ID, null, "", false, null, null, null, null, null, null,
            null, null, "false");
    when(stepTemplateCallRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplateResponseDTO.builder().yaml(stepTemplateYAML).yamlVersion(HarnessYamlVersion.V1).build())));

    Call<ResponseDTO<TemplateResponseDTO>> stageTemplateCallRequest = mock(Call.class);
    doReturn(stageTemplateCallRequest)
        .when(templateResourceClient)
        .get("TestStageTemplate", ACCOUNT_ID, ORG_ID, PROJECT_ID, null, "", false, null, null, null, null, null, null,
            null, null, "false");
    when(stageTemplateCallRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplateResponseDTO.builder().yaml(stageTemplateYAML).yamlVersion(HarnessYamlVersion.V1).build())));
    String result = pipelineTemplateHelper.resolvePipelineWithAllTemplatesRuntimeInputs(
        Optional.of(pipelineEntity).get().getYaml(), ACCOUNT_ID, ORG_ID, PROJECT_ID, "false");
    JsonNode resultNode = YamlUtils.readAsJsonNode(result);
    JsonNode pipelineNode = resultNode.get(YAMLFieldNameConstants.PIPELINE);
    assertThat(pipelineNode.has(YAMLFieldNameConstants.INPUTS)).isFalse();
    assertThat(result).isEqualTo(pipelineYAML);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolvePipelineWithAllTemplatesRuntimeInputsResolvesScopedTemplateRefs() throws IOException {
    String pipelineYAML = readFile("v1-pipeline-with-scoped-template-refs.yaml");
    String stepTemplateYAML = readFile("v1-step-template.yaml");

    // The scope prefix on the ref must drive the query scope: org.<id> -> org scope, account.<id> -> account scope,
    // and a bare <id> -> project scope. Fallback (non-global) gets are stubbed with the scope-adjusted params.
    mockScopedTemplateGet("OrgStepTemplate", ACCOUNT_ID, ORG_ID, null, stepTemplateYAML);
    mockScopedTemplateGet("AccountStepTemplate", ACCOUNT_ID, null, null, stepTemplateYAML);
    mockScopedTemplateGet("ProjectStepTemplate", ACCOUNT_ID, ORG_ID, PROJECT_ID, stepTemplateYAML);

    String result = pipelineTemplateHelper.resolvePipelineWithAllTemplatesRuntimeInputs(
        pipelineYAML, ACCOUNT_ID, ORG_ID, PROJECT_ID, "false");

    // org ref queried at org scope (projectId null) with the unscoped identifier, not "org.OrgStepTemplate" at project
    verify(templateResourceClient)
        .get("OrgStepTemplate", ACCOUNT_ID, ORG_ID, null, null, "", false, null, null, null, null, null, null, null,
            null, "false");
    // account ref queried at account scope (orgId and projectId null)
    verify(templateResourceClient)
        .get("AccountStepTemplate", ACCOUNT_ID, null, null, null, "", false, null, null, null, null, null, null, null,
            null, "false");
    // project ref keeps the full scope
    verify(templateResourceClient)
        .get("ProjectStepTemplate", ACCOUNT_ID, ORG_ID, PROJECT_ID, null, "", false, null, null, null, null, null, null,
            null, null, "false");
    // global-priority lookup uses the unscoped identifier, not the scoped ref
    verify(templateResourceClient)
        .get("OrgStepTemplate", GlobalTemplateConstants.GLOBAL_TEMPLATES_ACCOUNT_ID, null, null, null, "", false, null,
            null, null, null, null, null, null, null, "false");

    JsonNode pipelineNode = YamlUtils.readAsJsonNode(result).get(YAMLFieldNameConstants.PIPELINE);
    assertThat(pipelineNode.has(YAMLFieldNameConstants.INPUTS)).isTrue();
    assertThat(pipelineNode.get(YAMLFieldNameConstants.INPUTS).size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testResolvePipelineWithAllTemplatesRuntimeInputsResolvesScopeForNonTemplateKeywords() throws IOException {
    // A V1 template can be linked via any template-type keyword (action/build/deploy/...), not just `template`.
    // Scope resolution must be keyword-agnostic since the ref is always the `uses:` value.
    String pipelineYAML = readFile("v1-pipeline-with-scoped-template-refs-multiple-keywords.yaml");
    String stepTemplateYAML = readFile("v1-step-template.yaml");

    mockScopedTemplateGet("OrgActionTemplate", ACCOUNT_ID, ORG_ID, null, stepTemplateYAML);
    mockScopedTemplateGet("AccountBuildTemplate", ACCOUNT_ID, null, null, stepTemplateYAML);
    mockScopedTemplateGet("ProjectDeployTemplate", ACCOUNT_ID, ORG_ID, PROJECT_ID, stepTemplateYAML);

    String result = pipelineTemplateHelper.resolvePipelineWithAllTemplatesRuntimeInputs(
        pipelineYAML, ACCOUNT_ID, ORG_ID, PROJECT_ID, "false");

    // org.<id> via `action` -> org scope (projectId null)
    verify(templateResourceClient)
        .get("OrgActionTemplate", ACCOUNT_ID, ORG_ID, null, null, "", false, null, null, null, null, null, null, null,
            null, "false");
    // account.<id> via `build` -> account scope (orgId and projectId null)
    verify(templateResourceClient)
        .get("AccountBuildTemplate", ACCOUNT_ID, null, null, null, "", false, null, null, null, null, null, null, null,
            null, "false");
    // bare <id> via `deploy` -> project scope (full scope)
    verify(templateResourceClient)
        .get("ProjectDeployTemplate", ACCOUNT_ID, ORG_ID, PROJECT_ID, null, "", false, null, null, null, null, null,
            null, null, null, "false");

    JsonNode pipelineNode = YamlUtils.readAsJsonNode(result).get(YAMLFieldNameConstants.PIPELINE);
    assertThat(pipelineNode.has(YAMLFieldNameConstants.INPUTS)).isTrue();
    assertThat(pipelineNode.get(YAMLFieldNameConstants.INPUTS).size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveTemplateRefsThrowsPolicyEvaluationExceptionWhenHarnessRemoteServiceExceptionHasOpaMetadata()
      throws IOException {
    String givenYaml = readFile("pipeline-with-template-ref.yaml");
    OpaOnSaveStatusDTO opaStatus = OpaOnSaveStatusDTO.builder().build();
    OpaOnSaveStatusErrorDTO opaMetadata = OpaOnSaveStatusErrorDTO.builder().opaOnSaveStatusDTO(opaStatus).build();
    HarnessRemoteServiceException remoteEx =
        new HarnessRemoteServiceException("OPA policy violated", opaMetadata, Collections.emptyList());
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(TemplateApplyRequestDTO.class), any());
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIE_ERROR_ENHANCEMENTS);
    when(callRequest.execute()).thenThrow(remoteEx);

    assertThatThrownBy(()
                           -> pipelineTemplateHelper.resolveTemplateRefsInPipeline(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, givenYaml, BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0))
        .isInstanceOf(PolicyEvaluationFailureException.class)
        .hasMessageContaining("OPA policy violated");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveTemplateRefsThrowsNGTemplateExceptionWhenHarnessRemoteServiceExceptionHasNoOpaMetadata()
      throws IOException {
    String givenYaml = readFile("pipeline-with-template-ref.yaml");
    HarnessRemoteServiceException remoteEx =
        new HarnessRemoteServiceException("template error", null, Collections.emptyList());
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(TemplateApplyRequestDTO.class), any());
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(ACCOUNT_ID, FeatureName.PIE_ERROR_ENHANCEMENTS);
    when(callRequest.execute()).thenThrow(remoteEx);

    assertThatThrownBy(()
                           -> pipelineTemplateHelper.resolveTemplateRefsInPipeline(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, givenYaml, BOOLEAN_FALSE_VALUE, HarnessYamlVersion.V0))
        .isInstanceOf(NGTemplateException.class)
        .hasMessageContaining("Failed to apply templates on pipeline");
  }

  private void mockScopedTemplateGet(
      String templateId, String accountId, String orgId, String projectId, String templateYaml) throws IOException {
    Call<ResponseDTO<TemplateResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .get(templateId, accountId, orgId, projectId, null, "", false, null, null, null, null, null, null, null, null,
            "false");
    when(callRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplateResponseDTO.builder().yaml(templateYaml).yamlVersion(HarnessYamlVersion.V1).build())));
  }
}
