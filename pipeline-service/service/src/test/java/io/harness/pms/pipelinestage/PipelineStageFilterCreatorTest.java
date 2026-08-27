/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.pipeline.service.PMSPipelineServiceImpl;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipelinestage.creator.PipelineStageFilterCreator;
import io.harness.pms.plan.execution.helper.PipelineStageHelper;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.steps.pipelinestage.PipelineStageNode;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStageFilterCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock PipelineStageHelper pipelineStageHelper;
  @Mock PMSPipelineServiceImpl pmsPipelineService;
  @Mock PipelineEnforcementService pipelineEnforcementService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @InjectMocks PipelineStageFilterCreator pipelineStageFilterCreator;
  ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId("unique-id").build();

  private static final String ACCOUNT = "acct";
  private static final String ORG = "org";
  private static final String PROJ = "proj";
  private static final String PIP = "child";

  @Before
  public void setup() {
    when(pmsFeatureFlagHelper.isEnabled(any(), anyString())).thenReturn(false);
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldValidatePipelineStageFilterCreator() {
    Set<String> stageTypes = pipelineStageFilterCreator.getSupportedStageTypes();
    assertThat(stageTypes).isNotEmpty();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldValidatePipelineStageGetFilter() {
    PipelineStageNode customStageNode = new PipelineStageNode();
    PipelineFilter filter =
        pipelineStageFilterCreator.getFilter(FilterCreationContext.builder().build(), customStageNode);
    assertThat(filter).isNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldValidatePipelineStageFieldClass() {
    assertThat(pipelineStageFilterCreator.getFieldClass()).isEqualTo(PipelineStageNode.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldHandleNode() throws IOException {
    String yamlField = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  project: \"project\"\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    FilterCreationContext filterCreationContext =
        FilterCreationContext.builder()
            .setupMetadata(
                SetupMetadata.newBuilder().setAccountId("acc").setOrgId("org").setProjectId("project").build())
            .currentField(pipelineStageYamlField)
            .build();

    doReturn(Optional.of(PipelineEntity.builder().yaml(yamlField).build()))
        .when(pmsPipelineService)
        .getPipeline("acc", "org", "project", "childPipeline", false, false, false, true, scopeInfo, true);

    GitEntityInfo gitRequestParamsInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitRequestParamsInfo.getParentEntityAccountIdentifier()).isNull();
    assertThat(gitRequestParamsInfo.getParentEntityOrgIdentifier()).isNull();
    assertThat(gitRequestParamsInfo.getParentEntityProjectIdentifier()).isNull();

    FilterCreationResponse filterCreationResponse = pipelineStageFilterCreator.handleNode(
        filterCreationContext, YamlUtils.read(yamlField, PipelineStageNode.class));

    verify(pipelineEnforcementService, times(1)).validatePipelineChainingEnforcement("acc");

    gitRequestParamsInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    assertThat(gitRequestParamsInfo.getParentEntityAccountIdentifier()).isEqualTo("acc");
    assertThat(gitRequestParamsInfo.getParentEntityOrgIdentifier()).isEqualTo("org");
    assertThat(gitRequestParamsInfo.getParentEntityProjectIdentifier()).isEqualTo("project");

    assertThat(filterCreationResponse.getReferredEntities().size()).isEqualTo(1);
    EntityDetailProtoDTO entityDetailProtoDTO = filterCreationResponse.getReferredEntities().get(0);
    assertThat(entityDetailProtoDTO.getType()).isEqualTo(EntityTypeProtoEnum.PIPELINES);
    assertThat(entityDetailProtoDTO.getIdentifierRef())
        .isEqualTo(IdentifierRefProtoDTO.newBuilder()
                       .setAccountIdentifier(StringValue.of("acc"))
                       .setOrgIdentifier(StringValue.of("org"))
                       .setProjectIdentifier(StringValue.of("project"))
                       .setIdentifier(StringValue.of("childPipeline"))
                       .build());

    // case2: pipeline stage config as null
    String yamlFieldWithoutSpec = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  inputs: \n"
        + "     dummy: dummy\n"
        + "  inputSetReferences: \n"
        + "     - ref1\n"
        + "  project: \"project\"\n";

    pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlFieldWithoutSpec);
    FilterCreationContext filterCreationContextCase2 =
        FilterCreationContext.builder()
            .setupMetadata(SetupMetadata.newBuilder().setAccountId("acc").setOrgId("org").setProjectId("org").build())
            .currentField(pipelineStageYamlField)
            .build();

    assertThatThrownBy(()
                           -> pipelineStageFilterCreator.handleNode(filterCreationContextCase2,
                               YamlUtils.read(yamlFieldWithoutSpec, PipelineStageNode.class)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void handleNodeWithChildBranchOverridesContextAndRestores() throws IOException {
    String yamlField = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  project: \"project\"\n"
        + "  gitBranch: \"devtest\"\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    FilterCreationContext filterCreationContext =
        FilterCreationContext.builder()
            .setupMetadata(SetupMetadata.newBuilder().setAccountId(ACCOUNT).setOrgId(ORG).setProjectId(PROJ).build())
            .currentField(pipelineStageYamlField)
            .build();
    // Parent context branch = main
    GitEntityInfo parentInfo = GitEntityInfo.builder().branch("main").connectorRef("conn").repoName("repo").build();
    GitAwareContextHelper.updateGitEntityContext(parentInfo);

    // Mock child fetch: assert branch == devtest inside getPipeline call
    Mockito.lenient()
        .when(pmsPipelineService.getPipeline(eq(ACCOUNT), eq(ORG), eq(PROJ), eq(PIP), anyBoolean(), anyBoolean(),
            anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenAnswer(invocation -> {
          String transientBranch = GitAwareContextHelper.getGitRequestParamsInfo().getTransientBranch();
          String activeBranch = GitAwareContextHelper.getGitRequestParamsInfo().getBranch();
          assertThat(activeBranch).isEqualTo("main");
          assertThat(transientBranch).isEqualTo("devtest");
          return Optional.of(PipelineEntity.builder().identifier(PIP).build());
        });

    FilterCreationResponse resp =
        pipelineStageFilterCreator.handleNode(filterCreationContext, makeNodeWithBranch("devtest"));
    assertThat(resp).isNotNull();
    assertThat(resp.getReferredEntities()).hasSize(1);
    assertThat(resp.getReferredEntities().get(0)).isInstanceOf(EntityDetailProtoDTO.class);

    // After handleNode returns, ensure branch restored to parent
    String after = GitAwareContextHelper.getGitRequestParamsInfo().getBranch();
    String afterTransient = GitAwareContextHelper.getGitRequestParamsInfo().getTransientBranch();
    assertThat(after).isEqualTo("main");
    assertThat(afterTransient).isEqualTo(null);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void handleNodeNoBranchUsesParentContext() throws IOException {
    String yamlField = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  project: \"project\"\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    FilterCreationContext filterCreationContext =
        FilterCreationContext.builder()
            .setupMetadata(SetupMetadata.newBuilder().setAccountId(ACCOUNT).setOrgId(ORG).setProjectId(PROJ).build())
            .currentField(pipelineStageYamlField)
            .build();
    // Parent context branch = main
    GitEntityInfo parentInfo = GitEntityInfo.builder().branch("main").connectorRef("conn").repoName("repo").build();
    GitAwareContextHelper.updateGitEntityContext(parentInfo);

    Mockito.lenient()
        .when(pmsPipelineService.getPipeline(eq(ACCOUNT), eq(ORG), eq(PROJ), eq(PIP), anyBoolean(), anyBoolean(),
            anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenAnswer(invocation -> {
          String transientBranch = GitAwareContextHelper.getGitRequestParamsInfo().getTransientBranch();
          String activeBranch = GitAwareContextHelper.getGitRequestParamsInfo().getBranch();
          assertThat(activeBranch).isEqualTo("main");
          assertThat(transientBranch).isEqualTo(null);
          return Optional.of(PipelineEntity.builder().identifier(PIP).build());
        });

    FilterCreationResponse resp =
        pipelineStageFilterCreator.handleNode(filterCreationContext, makeNodeWithBranch(null));
    assertThat(resp).isNotNull();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void handleNodeBlankBranchUsesParentContext() throws IOException {
    String yamlField = "---\n"
        + "name: \"parent pipeline\"\n"
        + "identifier: \"rc-" + generateUuid() + "\"\n"
        + "timeout: \"1w\"\n"
        + "type: \"Pipeline\"\n"
        + "spec:\n"
        + "  pipeline: \"childPipeline\"\n"
        + "  org: \"org\"\n"
        + "  project: \"project\"\n"
        + "  gitBranch: \"\"\n";

    YamlField pipelineStageYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    FilterCreationContext filterCreationContext =
        FilterCreationContext.builder()
            .setupMetadata(SetupMetadata.newBuilder().setAccountId(ACCOUNT).setOrgId(ORG).setProjectId(PROJ).build())
            .currentField(pipelineStageYamlField)
            .build();
    // Parent context branch = main
    GitEntityInfo parentInfo = GitEntityInfo.builder().branch("main").connectorRef("conn").repoName("repo").build();
    GitAwareContextHelper.updateGitEntityContext(parentInfo);

    Mockito.lenient()
        .when(pmsPipelineService.getPipeline(eq(ACCOUNT), eq(ORG), eq(PROJ), eq(PIP), anyBoolean(), anyBoolean(),
            anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenAnswer(invocation -> {
          String transientBranch = GitAwareContextHelper.getGitRequestParamsInfo().getTransientBranch();
          String activeBranch = GitAwareContextHelper.getGitRequestParamsInfo().getBranch();
          assertThat(activeBranch).isEqualTo("main");
          assertThat(transientBranch).isEqualTo(null);
          return Optional.of(PipelineEntity.builder().identifier(PIP).build());
        });

    FilterCreationResponse resp =
        pipelineStageFilterCreator.handleNode(filterCreationContext, makeNodeWithBranch(null));
    assertThat(resp).isNotNull();
  }

  private PipelineStageNode makeNodeWithBranch(String branch) {
    PipelineStageConfig cfg =
        PipelineStageConfig.builder()
            .org(ORG)
            .project(PROJ)
            .pipeline(PIP)
            .gitBranch(branch != null ? io.harness.pms.yaml.ParameterField.createValueField(branch) : null)
            .build();
    PipelineStageNode node = new PipelineStageNode();
    node.setPipelineStageConfig(cfg);
    node.setIdentifier("stg");
    node.setName("stg");
    return node;
  }
}
