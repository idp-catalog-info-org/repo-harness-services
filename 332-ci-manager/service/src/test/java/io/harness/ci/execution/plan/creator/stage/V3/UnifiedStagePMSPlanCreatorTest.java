/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.stage.V3;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.GARGI;
import static io.harness.rule.OwnerRule.SATYAKOTA;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.strategy.StrategyValidationUtils.STRATEGY_IDENTIFIER_POSTFIX;
import static io.harness.yaml.extended.ci.codebase.BuildType.BRANCH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.steps.StepSpecTypeConstants;
import io.harness.beans.steps.stepinfo.GitCloneStepInfoV1;
import io.harness.beans.steps.stepinfo.Strategy;
import io.harness.beans.steps.v1.BuildIntelligenceV1;
import io.harness.beans.steps.v1.CloneRef;
import io.harness.beans.steps.v1.CloneType;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.cd.multi.deploy.UnifiedMultiDeploymentUtils;
import io.harness.ci.execution.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.states.V1.cd.UnifiedMultiDeploymentSpawnerStep;
import io.harness.ci.states.codebase.CodeBaseTaskStepParameters;
import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.stages.v1.AbstractStagePlanCreator;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanExecutionContext;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.SubmoduleStrategy;
import io.harness.yaml.options.Options;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.powermock.reflect.Whitebox;

public class UnifiedStagePMSPlanCreatorTest extends CategoryTest {
  private final UnifiedStagePMSPlanCreator unifiedStagePMSPlanCreator = new UnifiedStagePMSPlanCreator();

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = unifiedStagePMSPlanCreator.getSupportedTypes();
    assertThat(supportedTypes).isNotNull();
    assertThat(supportedTypes).containsKey(YAMLFieldNameConstants.STAGE);
    assertThat(supportedTypes.get(YAMLFieldNameConstants.STAGE)).contains(StepSpecTypeConstants.UNIFIED_STAGE);
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetSupportedTypesReturnsCorrectSize() {
    Map<String, Set<String>> supportedTypes = unifiedStagePMSPlanCreator.getSupportedTypes();
    assertThat(supportedTypes).hasSize(1);
    assertThat(supportedTypes.get(YAMLFieldNameConstants.STAGE)).hasSize(1);
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testStaticConstants() {
    assertThat(UnifiedStagePMSPlanCreator.TYPE).isEqualTo("type");
    assertThat(UnifiedStagePMSPlanCreator.STAGE_NODE).isEqualTo("stageNode");
    assertThat(UnifiedStagePMSPlanCreator.INFRASTRUCTURE).isEqualTo("infrastructure");
    assertThat(UnifiedStagePMSPlanCreator.CODEBASE).isEqualTo("codebase");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBuildIntelligenceV1BuilderWithAllFields() {
    BuildIntelligenceV1 buildIntelligence = BuildIntelligenceV1.builder()
                                                .enabled(ParameterField.createValueField(true))
                                                .port(ParameterField.createValueField("8080"))
                                                .mavenUrl(ParameterField.createValueField("http://maven.example.com"))
                                                .build();
    assertThat(buildIntelligence).isNotNull();
    assertThat(buildIntelligence.getEnabled().getValue()).isTrue();
    assertThat(buildIntelligence.getPort().getValue()).isEqualTo("8080");
    assertThat(buildIntelligence.getMavenUrl().getValue()).isEqualTo("http://maven.example.com");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBuildIntelligenceV1BuilderWithEnabledFalse() {
    BuildIntelligenceV1 buildIntelligence =
        BuildIntelligenceV1.builder().enabled(ParameterField.createValueField(false)).build();
    assertThat(buildIntelligence).isNotNull();
    assertThat(buildIntelligence.getEnabled().getValue()).isFalse();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBuildIntelligenceV1BuilderWithEnabledTrue() {
    BuildIntelligenceV1 buildIntelligence =
        BuildIntelligenceV1.builder().enabled(ParameterField.createValueField(true)).build();
    assertThat(buildIntelligence).isNotNull();
    assertThat(buildIntelligence.getEnabled().getValue()).isTrue();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBuildIntelligenceV1WithNullValues() {
    BuildIntelligenceV1 buildIntelligence =
        BuildIntelligenceV1.builder().enabled(null).port(null).mavenUrl(null).build();
    assertThat(buildIntelligence).isNotNull();
    assertThat(buildIntelligence.getEnabled()).isNull();
    assertThat(buildIntelligence.getPort()).isNull();
    assertThat(buildIntelligence.getMavenUrl()).isNull();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBuildIntelligenceV1WithPortOnly() {
    BuildIntelligenceV1 buildIntelligence =
        BuildIntelligenceV1.builder().port(ParameterField.createValueField("9090")).build();
    assertThat(buildIntelligence).isNotNull();
    assertThat(buildIntelligence.getPort().getValue()).isEqualTo("9090");
    assertThat(buildIntelligence.getEnabled()).isNull();
    assertThat(buildIntelligence.getMavenUrl()).isNull();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBuildIntelligenceV1WithMavenUrlOnly() {
    BuildIntelligenceV1 buildIntelligence =
        BuildIntelligenceV1.builder().mavenUrl(ParameterField.createValueField("http://custom-maven.com")).build();
    assertThat(buildIntelligence).isNotNull();
    assertThat(buildIntelligence.getMavenUrl().getValue()).isEqualTo("http://custom-maven.com");
    assertThat(buildIntelligence.getEnabled()).isNull();
    assertThat(buildIntelligence.getPort()).isNull();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBuildIntelligenceV1WithCustomPort() {
    BuildIntelligenceV1 buildIntelligence = BuildIntelligenceV1.builder()
                                                .enabled(ParameterField.createValueField(true))
                                                .port(ParameterField.createValueField("8082"))
                                                .build();
    assertThat(buildIntelligence).isNotNull();
    assertThat(buildIntelligence.getPort().getValue()).isEqualTo("8082");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testBuildIntelligenceV1EmptyBuilder() {
    BuildIntelligenceV1 buildIntelligence = BuildIntelligenceV1.builder().build();
    assertThat(buildIntelligence).isNotNull();
    assertThat(buildIntelligence.getEnabled()).isNull();
    assertThat(buildIntelligence.getPort()).isNull();
    assertThat(buildIntelligence.getMavenUrl()).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetGitClone_OnlyPipelineClonePresent() throws Exception {
    // Setup mocks
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    // Arrange: Pipeline level clone with all fields
    GitCloneStepInfoV1 pipelineClone =
        GitCloneStepInfoV1.builder()
            .connector(ParameterField.createValueField("testRepo1"))
            .repo(ParameterField.createValueField("test-repo-1"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("main")).build()))
            .strategy(Strategy.MERGE)
            .depth(ParameterField.createValueField(50))
            .insecure(ParameterField.createValueField(false))
            .lfs(ParameterField.createValueField(true))
            .clonedir(ParameterField.createValueField("/harness"))
            .build();

    // Stage has no clone defined
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(null);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));

    // Act: Invoke private method
    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    // Assert: Should return pipeline clone as-is
    assertThat(result).isNotNull();
    assertThat(result.getConnector().getValue()).isEqualTo("testRepo1");
    assertThat(result.getRepo().getValue()).isEqualTo("test-repo-1");
    assertThat(result.getRef().getValue().getName().getValue()).isEqualTo("main");
    assertThat(result.getStrategy()).isEqualTo(Strategy.MERGE);
    assertThat(result.getDepth().getValue()).isEqualTo(50);
    assertThat(result.getInsecure().getValue()).isFalse();
    assertThat(result.getLfs().getValue()).isTrue();
    assertThat(result.getClonedir().getValue()).isEqualTo("/harness");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetGitClone_OnlyStageClonePresent() throws Exception {
    // Setup mocks
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    // Arrange: Stage level clone with all fields
    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .connector(ParameterField.createValueField("TIJavaRepo"))
            .repo(ParameterField.createValueField("ti-java-repo"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("master")).build()))
            .strategy(Strategy.MERGE)
            .depth(ParameterField.createValueField(100))
            .insecure(ParameterField.createValueField(true))
            .build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    // No pipeline clone
    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.empty());

    // Act: Invoke private method
    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    // Assert: Should return stage clone as-is
    assertThat(result).isNotNull();
    assertThat(result.getConnector().getValue()).isEqualTo("TIJavaRepo");
    assertThat(result.getRepo().getValue()).isEqualTo("ti-java-repo");
    assertThat(result.getRef().getValue().getName().getValue()).isEqualTo("master");
    assertThat(result.getStrategy()).isEqualTo(Strategy.MERGE);
    assertThat(result.getDepth().getValue()).isEqualTo(100);
    assertThat(result.getInsecure().getValue()).isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetGitClone_BothPipelineAndStageClonePresent() throws Exception {
    // Setup mocks
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    // Arrange: Pipeline level clone with all fields
    GitCloneStepInfoV1 pipelineClone =
        GitCloneStepInfoV1.builder()
            .connector(ParameterField.createValueField("testRepo1"))
            .repo(ParameterField.createValueField("test-repo-1"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("main")).build()))
            .strategy(Strategy.SOURCE_BRANCH)
            .depth(ParameterField.createValueField(100))
            .insecure(ParameterField.createValueField(true))
            .lfs(ParameterField.createValueField(false))
            .clonedir(ParameterField.createValueField("/tmp"))
            .build();

    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .connector(ParameterField.createValueField("TIJavaRepo"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("master")).build()))
            .strategy(Strategy.MERGE)
            .depth(ParameterField.createValueField(50))
            .insecure(ParameterField.createValueField(false))
            .lfs(ParameterField.createValueField(true))
            .clonedir(ParameterField.createValueField("/harness"))
            .build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));

    // Act: Invoke private method
    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    // Assert: Stage values take priority, but null values inherited from pipeline
    assertThat(result).isNotNull();
    // Stage values (override)
    assertThat(result.getConnector().getValue()).isEqualTo("TIJavaRepo");
    assertThat(result.getRef().getValue().getName().getValue()).isEqualTo("master");
    assertThat(result.getRepo().getValue()).isEqualTo("test-repo-1");
    assertThat(result.getStrategy()).isEqualTo(Strategy.MERGE);
    assertThat(result.getDepth().getValue()).isEqualTo(50);
    assertThat(result.getInsecure().getValue()).isFalse();
    assertThat(result.getLfs().getValue()).isTrue();
    assertThat(result.getClonedir().getValue()).isEqualTo("/harness");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetGitClone_NeitherClonePresent() throws Exception {
    // Setup mocks
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    // Arrange: No clone at either level
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(null);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.empty());

    // Act
    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    // Assert: Should return null (no clone means no clone)
    assertThat(result).isNull();
  }

  // Unit tests for partial inheritance from pipeline clone to stage clone if few values from stage clone is absent.

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetGitClone_StageInheritsRepoAndConnectorFromPipeline() throws Exception {
    // Setup mocks
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    // Pipeline clone with repo
    GitCloneStepInfoV1 pipelineClone = GitCloneStepInfoV1.builder()
                                           .connector(ParameterField.createValueField("pipelineConnector"))
                                           .repo(ParameterField.createValueField("pipeline-repo"))
                                           .build();

    // Stage clone WITHOUT repo
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .clonedir(ParameterField.createValueField("abcd"))
                                        .tags(ParameterField.createValueField(false))
                                        .depth(ParameterField.createValueField(100))
                                        // repo is NOT set
                                        .build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));

    // Act
    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getConnector().getValue()).isEqualTo("pipelineConnector"); // Inherited from pipeline
    assertThat(result.getRepo().getValue()).isEqualTo("pipeline-repo"); // Inherited from pipeline
    assertThat(result.getDepth().getValue()).isEqualTo(100); // Stage clone Value
    assertThat(result.getClonedir().getValue()).isEqualTo("abcd"); // Stage clone value
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetGitClone_StageInheritsDepthStrategyInsecureFromPipeline() throws Exception {
    // Setup mocks
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    // Pipeline clone with depth, strategy, insecure
    GitCloneStepInfoV1 pipelineClone =
        GitCloneStepInfoV1.builder()
            .depth(ParameterField.createValueField(75))
            .strategy(Strategy.MERGE)
            .insecure(ParameterField.createValueField(false))
            .clonedir(ParameterField.createValueField("/addon"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("main")).build()))
            .build();

    // Stage clone with only ref (different branch)
    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("develop")).build()))
            .connector(ParameterField.createValueField("stageConnector"))
            .repo(ParameterField.createValueField("stage-repo"))
            // depth, strategy, insecure are NOT set
            .build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));
    // Act
    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getRef().getValue().getName().getValue()).isEqualTo("develop"); // Stage value
    assertThat(result.getDepth().getValue()).isEqualTo(75); // Inherited from pipeline
    assertThat(result.getStrategy()).isEqualTo(Strategy.MERGE); // Inherited from pipeline
    assertThat(result.getInsecure().getValue()).isFalse(); // Inherited from pipeline
    assertThat(result.getConnector().getValue()).isEqualTo("stageConnector"); // Stage value
    assertThat(result.getRepo().getValue()).isEqualTo("stage-repo"); // Stage value
    assertThat(result.getClonedir().getValue()).isEqualTo("/addon"); // Inherited from pipeline
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetGitClone_StageDisablesCloneWithEnabledFalse() throws Exception {
    // Setup mocks
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    // Pipeline clone with all fields
    GitCloneStepInfoV1 pipelineClone =
        GitCloneStepInfoV1.builder()
            .connector(ParameterField.createValueField("testRepo1"))
            .repo(ParameterField.createValueField("test-repo-1"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("main")).build()))
            .strategy(Strategy.MERGE)
            .depth(ParameterField.createValueField(50))
            .build();

    // Stage clone with enabled=false (disables clone for this stage)
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(false)) // Explicitly disable
                                        .build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));

    // Act
    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    // Assert: Should return null because stage explicitly disabled clone
    assertThat(result).isNull();
  }

  // Regression tests for pipeline-level inheritance of advanced clone fields
  // (clonedir + tags + submodules + sparseCheckout + preFetchCommand + persistCredentials + trace).
  // Stage having any clone block (even just enabled:true) must not drop pipeline-level values that
  // stage did not explicitly override.

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetGitClone_StageInheritsAdvancedFieldsFromPipeline() throws Exception {
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    // Pipeline sets every advanced field
    GitCloneStepInfoV1 pipelineClone =
        GitCloneStepInfoV1.builder()
            .connector(ParameterField.createValueField("pipelineConnector"))
            .repo(ParameterField.createValueField("pipeline-repo"))
            .clonedir(ParameterField.createValueField("specifiedDir"))
            .tags(ParameterField.createValueField(true))
            .submodules(ParameterField.createValueField(SubmoduleStrategy.RECURSIVE))
            .sparseCheckout(ParameterField.createValueField(List.of("folder2", "folder with space")))
            .preFetchCommand(ParameterField.createValueField("mkdir preFetchDir"))
            .persistCredentials(ParameterField.createValueField(true))
            .trace(ParameterField.createValueField(true))
            .build();

    // Stage only enables clone; mirrors the V0->V1-rendered sanity pipeline failure mode
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder().enabled(ParameterField.createValueField(true)).build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));

    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    assertThat(result).isNotNull();
    assertThat(result.getClonedir().getValue()).isEqualTo("specifiedDir");
    assertThat(result.getTags().getValue()).isTrue();
    assertThat(result.getSubmodules().getValue()).isEqualTo(SubmoduleStrategy.RECURSIVE);
    assertThat(result.getSparseCheckout().getValue()).containsExactly("folder2", "folder with space");
    assertThat(result.getPreFetchCommand().getValue()).isEqualTo("mkdir preFetchDir");
    assertThat(result.getPersistCredentials().getValue()).isTrue();
    assertThat(result.getTrace().getValue()).isTrue();
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetGitClone_StageOverridesAdvancedFieldsFromPipeline() throws Exception {
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    // Pipeline has values
    GitCloneStepInfoV1 pipelineClone = GitCloneStepInfoV1.builder()
                                           .clonedir(ParameterField.createValueField("pipelineDir"))
                                           .tags(ParameterField.createValueField(true))
                                           .submodules(ParameterField.createValueField(SubmoduleStrategy.RECURSIVE))
                                           .sparseCheckout(ParameterField.createValueField(List.of("pipelineFolder")))
                                           .preFetchCommand(ParameterField.createValueField("pipeline-pre"))
                                           .persistCredentials(ParameterField.createValueField(true))
                                           .trace(ParameterField.createValueField(true))
                                           .build();

    // Stage overrides every advanced field (including "false" / empty values which must be honoured)
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .clonedir(ParameterField.createValueField("stageDir"))
                                        .tags(ParameterField.createValueField(false))
                                        .submodules(ParameterField.createValueField(SubmoduleStrategy.FALSE))
                                        .sparseCheckout(ParameterField.createValueField(List.of("stageFolder")))
                                        .preFetchCommand(ParameterField.createValueField("stage-pre"))
                                        .persistCredentials(ParameterField.createValueField(false))
                                        .trace(ParameterField.createValueField(false))
                                        .build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));

    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    assertThat(result).isNotNull();
    assertThat(result.getClonedir().getValue()).isEqualTo("stageDir");
    assertThat(result.getTags().getValue()).isFalse();
    assertThat(result.getSubmodules().getValue()).isEqualTo(SubmoduleStrategy.FALSE);
    assertThat(result.getSparseCheckout().getValue()).containsExactly("stageFolder");
    assertThat(result.getPreFetchCommand().getValue()).isEqualTo("stage-pre");
    assertThat(result.getPersistCredentials().getValue()).isFalse();
    assertThat(result.getTrace().getValue()).isFalse();
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetGitClone_ClonedirInheritedWhenStageFieldIsParameterFieldOfNull() throws Exception {
    // Production deserializer returns ParameterField.ofNull() (not Java null) for missing YAML fields.
    // Bug A was that getGitClone used `== null` for clonedir, which is false for ofNull(),
    // so the pipeline value was silently dropped. This test guards that the fix uses isBlank.
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    GitCloneStepInfoV1 pipelineClone =
        GitCloneStepInfoV1.builder().clonedir(ParameterField.createValueField("specifiedDir")).build();

    // Simulate the deserialized stage clone: enabled=true, every other ParameterField is ofNull()
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(true))
                                        .clonedir(ParameterField.ofNull())
                                        .tags(ParameterField.ofNull())
                                        .submodules(ParameterField.ofNull())
                                        .sparseCheckout(ParameterField.ofNull())
                                        .preFetchCommand(ParameterField.ofNull())
                                        .persistCredentials(ParameterField.ofNull())
                                        .trace(ParameterField.ofNull())
                                        .build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));

    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    assertThat(result).isNotNull();
    assertThat(result.getClonedir().getValue()).isEqualTo("specifiedDir");
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetGitClone_UserInheritedFromPipelineWhenStageDoesNotSpecify() throws Exception {
    // A stage-level clone override that does not set user must inherit runAsUser (user) from the pipeline clone.
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    GitCloneStepInfoV1 pipelineClone = GitCloneStepInfoV1.builder().user(ParameterField.createValueField(1001)).build();

    // Stage override sets its own connector but leaves user as ofNull() (field absent in YAML).
    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(true))
                                        .connector(ParameterField.createValueField("stageConnector"))
                                        .user(ParameterField.ofNull())
                                        .build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));

    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    assertThat(result).isNotNull();
    assertThat(result.getUser().getValue()).isEqualTo(1001);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetGitClone_StageUserOverridesPipelineUser() throws Exception {
    // When the stage-level clone explicitly sets user, it must win over the pipeline clone user.
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    GitCloneStepInfoV1 pipelineClone = GitCloneStepInfoV1.builder().user(ParameterField.createValueField(1001)).build();

    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(true))
                                        .connector(ParameterField.createValueField("stageConnector"))
                                        .user(ParameterField.createValueField(1002))
                                        .build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));

    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    assertThat(result).isNotNull();
    assertThat(result.getUser().getValue()).isEqualTo(1002);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testGetGitClone_StageUserZeroIsNotOverriddenByPipelineUser() throws Exception {
    // runAsUser: 0 (root) is an explicit, meaningful value. It must NOT be treated as blank and
    // must NOT be silently overridden by the pipeline clone user.
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    PlanCreationContext planCreationContext = mock(PlanCreationContext.class);
    Dependency dependency = mock(Dependency.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    when(planCreationContext.getDependency()).thenReturn(dependency);

    GitCloneStepInfoV1 pipelineClone = GitCloneStepInfoV1.builder().user(ParameterField.createValueField(1001)).build();

    GitCloneStepInfoV1 stageClone = GitCloneStepInfoV1.builder()
                                        .enabled(ParameterField.createValueField(true))
                                        .connector(ParameterField.createValueField("stageConnector"))
                                        .user(ParameterField.createValueField(0))
                                        .build();

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setClone(stageClone);

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.of(pipelineClone));

    GitCloneStepInfoV1 result = Whitebox.invokeMethod(creator, "getGitClone", planCreationContext, stageNode);

    assertThat(result).isNotNull();
    assertThat(result.getUser().getValue()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testGetStageParameters_WithNullCacheIntelligence() throws Exception {
    // Setup mocks
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    String yaml = "runtime: shell\n"
        + "id: s1\n"
        + "name: s1\n"
        + "clone:\n"
        + "  enabled: false\n"
        + "steps:\n"
        + "  - id: ShellScript_1\n"
        + "    name: ShellScript_1\n"
        + "    run:\n"
        + "      script: echo <+stage.name>\n"
        + "      shell: bash\n"
        + "    timeout: 10m\n"
        + "strategy:\n"
        + "  matrix:\n"
        + "    service:\n"
        + "      - svc1\n"
        + "      - svc2\n";
    Dependency dependency = Dependency.newBuilder().build();
    YamlField yamlField = null;
    try {
      yamlField = YamlUtils.readTree(yaml);
    } catch (IOException ioException) {
      throw new InvalidYamlException(
          String.format("Invalid yaml passed. Error due to - %s", ioException.getMessage()), ioException);
    }
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(yamlField)
                                  .dependency(dependency)
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();
    YamlField currentField = mock(YamlField.class);
    YamlNode yamlNode = mock(YamlNode.class);
    YamlNode parentNode = mock(YamlNode.class);
    YamlField stepsField = mock(YamlField.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    // Arrange
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setId("testStage");
    stageNode.setName("Test Stage");
    stageNode.setCacheIntelligence(null);

    List<String> childrenNodeIds = java.util.Collections.singletonList("child1");

    when(currentField.getNode()).thenReturn(yamlNode);
    when(yamlNode.getField(io.harness.pms.yaml.YAMLFieldNameConstants.STEPS)).thenReturn(stepsField);
    when(yamlNode.getParentNode()).thenReturn(parentNode);

    when(ciPlanCreatorUtils.getDeserializedOptions(dependency))
        .thenReturn(java.util.Optional.of(io.harness.yaml.options.Options.builder().build()));

    DockerInfraYaml d = DockerInfraYaml.builder().spec(DockerInfraYaml.DockerInfraSpec.builder().build()).build();
    when(ciPlanCreatorUtils.getInfrastructure(any(), any(), any())).thenReturn(d);

    when(ciPlanCreatorUtils.getDeserializedOptions(eq(null))).thenReturn(Optional.of(Options.builder().build()));

    when(ciPlanCreatorUtils.getCodebase(eq(ctx), any())).thenReturn(java.util.Optional.empty());

    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(java.util.Optional.empty());

    // Act
    io.harness.pms.sdk.core.steps.io.StepParameters result =
        creator.getStageParameters(ctx, stageNode, childrenNodeIds);

    // Assert
    assertThat(result).isNotNull();
    io.harness.plancreator.steps.common.v1.StageElementParametersV1 stageParams =
        (io.harness.plancreator.steps.common.v1.StageElementParametersV1) result;
    io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS spec =
        (io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS) stageParams.getSpec();
    assertThat(spec.getCaching()).isNotNull();
    assertThat(spec.getCaching().getEnabled().getValue()).isFalse();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_CreatesCombinedRollbackWithStrategySetupId() throws Exception {
    // mocks
    String yaml = "stage:\n"
        + "  runtime: shell\n"
        + "  id: s1\n"
        + "  name: s1\n"
        + "  steps:\n"
        + "    - id: step1\n"
        + "      name: step1\n"
        + "      run:\n"
        + "        script: echo hello\n"
        + "  strategy:\n"
        + "    repeat:\n"
        + "      items:\n"
        + "        - host1\n"
        + "        - host2\n";

    String yamlWithUuids = YamlUtils.injectUuid(yaml);
    YamlField root = YamlUtils.readTree(yamlWithUuids);
    YamlField stageField = root.getNode().getField("stage");

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(stageField)
                                  .dependency(Dependency.newBuilder().build())
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    io.harness.serializer.KryoSerializer kryoSerializer = mock(KryoSerializer.class);
    Whitebox.setInternalState(creator, "kryoSerializer", kryoSerializer, AbstractStagePlanCreator.class);
    Whitebox.setInternalState(creator, "kryoSerializer", kryoSerializer);
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[0]);
    when(kryoSerializer.asDeflatedBytes(any())).thenReturn(new byte[0]);

    DockerInfraYaml dockerInfra =
        DockerInfraYaml.builder().spec(DockerInfraYaml.DockerInfraSpec.builder().build()).build();
    when(ciPlanCreatorUtils.getInfrastructure(any(), any(), any())).thenReturn(dockerInfra);
    when(ciPlanCreatorUtils.getDeserializedOptions(any())).thenReturn(Optional.of(Options.builder().build()));
    when(ciPlanCreatorUtils.getCodebase(eq(ctx), any())).thenReturn(Optional.empty());

    UnifiedStageNodeV1 stageNode = creator.getFieldObject(stageField);

    // Act
    LinkedHashMap<String, PlanCreationResponse> responseMap = creator.createPlanForChildrenNodes(ctx, stageNode);

    String expectedCombinedRollbackId =
        UnifiedMultiDeploymentUtils.getStageNodeUuid(ctx, stageNode) + "_combinedRollback";

    // Assert
    boolean found = false;
    for (PlanCreationResponse resp : responseMap.values()) {
      if (resp.getNodes() != null && resp.getNodes().containsKey(expectedCombinedRollbackId)) {
        found = true;
        break;
      }
    }
    assertThat(found)
        .as("Expected to find combined rollback node with id %s created using strategy setup id",
            expectedCombinedRollbackId)
        .isTrue();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode_SetsRollbackAdvisers_WhenStrategyWrapped() throws Exception {
    // YAML: v1 stage with steps and repeat strategy (simulating group child)
    String yaml = "stage:\n"
        + "  runtime: shell\n"
        + "  id: stage_2\n"
        + "  name: stage_2\n"
        + "  steps:\n"
        + "    - id: step1\n"
        + "      name: step1\n"
        + "      run:\n"
        + "        script: echo hello world\n"
        + "  rollback:\n"
        + "    - wait:\n"
        + "        duration: 10s\n"
        + "  strategy:\n"
        + "    repeat:\n"
        + "      items:\n"
        + "        - host1\n"
        + "        - host2\n";

    String yamlWithUuids = YamlUtils.injectUuid(yaml);
    YamlField root = YamlUtils.readTree(yamlWithUuids);
    YamlField stageField = root.getNode().getField("stage");

    // Prepare Dependency with NEXT_ID like StagesPlanCreatorV1 would set for sibling traversal
    String nextSiblingUuid = "NEXT_NODE_UUID";
    HarnessStruct nodeMetadata =
        HarnessStruct.newBuilder()
            .putData(PlanCreatorConstants.NEXT_ID, HarnessValue.newBuilder().setStringValue(nextSiblingUuid).build())
            .build();
    Dependency dependency = Dependency.newBuilder().setNodeMetadata(nodeMetadata).build();

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(stageField)
                                  .dependency(dependency)
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    KryoSerializer kryoSerializer = mock(KryoSerializer.class);
    Whitebox.setInternalState(creator, "kryoSerializer", kryoSerializer, AbstractStagePlanCreator.class);
    Whitebox.setInternalState(creator, "kryoSerializer", kryoSerializer);
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[0]);
    when(kryoSerializer.asDeflatedBytes(any())).thenReturn(new byte[0]);

    // Minimal infra/options stubs to build stage parameters
    DockerInfraYaml dockerInfra =
        DockerInfraYaml.builder().spec(DockerInfraYaml.DockerInfraSpec.builder().build()).build();
    when(ciPlanCreatorUtils.getInfrastructure(any(), any(), any())).thenReturn(dockerInfra);
    when(ciPlanCreatorUtils.getDeserializedOptions(any())).thenReturn(Optional.of(Options.builder().build()));
    when(ciPlanCreatorUtils.getCodebase(eq(ctx), any())).thenReturn(Optional.empty());

    // Parse stage node and build parent PlanNode
    UnifiedStageNodeV1 stageNode = creator.getFieldObject(stageField);
    List<String> children = Collections.singletonList(stageField.getNode().getField("steps").getUuid());

    PlanNode planNode = creator.createPlanForParentNode(ctx, stageNode, children);

    // Assert: normal advisers can be empty for strategy-wrapped stages
    assertThat(planNode.getAdviserObtainments()).isEmpty();

    // Assert: rollback-mode advisers are present and non-empty (our fix)
    Map<ExecutionMode, java.util.List<AdviserObtainment>> modeAdvisers =
        planNode.getAdvisorObtainmentsForExecutionMode();
    assertThat(modeAdvisers).as("advisorObtainmentsForExecutionMode map should not be null").isNotNull();

    assertThat(modeAdvisers.get(ExecutionMode.PIPELINE_ROLLBACK))
        .as("PIPELINE_ROLLBACK advisers should be present for strategy-wrapped v1 stages")
        .isNotNull()
        .isNotEmpty();

    assertThat(modeAdvisers.get(ExecutionMode.POST_EXECUTION_ROLLBACK))
        .as("POST_EXECUTION_ROLLBACK advisers should be present for strategy-wrapped v1 stages")
        .isNotNull()
        .isNotEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldObject_validYaml_shouldReturnUnifiedStageNodeV1() throws Exception {
    String yaml = "runtime: shell\n"
        + "id: myStage\n"
        + "name: My Stage\n"
        + "steps:\n"
        + "  - id: step1\n"
        + "    name: step1\n"
        + "    run:\n"
        + "      script: echo hello\n";
    YamlField yamlField = YamlUtils.readTree(yaml);

    UnifiedStageNodeV1 result = unifiedStagePMSPlanCreator.getFieldObject(yamlField);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("myStage");
    assertThat(result.getName()).isEqualTo("My Stage");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldObject_invalidYaml_shouldThrowInvalidYamlException() {
    YamlField brokenField = mock(YamlField.class);
    YamlNode brokenNode = mock(YamlNode.class);
    when(brokenField.getNode()).thenReturn(brokenNode);
    when(brokenNode.toString()).thenReturn("{{{invalid yaml");

    try {
      unifiedStagePMSPlanCreator.getFieldObject(brokenField);
      assertThat(false).as("Expected InvalidYamlException").isTrue();
    } catch (InvalidYamlException e) {
      assertThat(e.getMessage()).contains("Unable to parse integration stage yaml");
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStageParameters_WithCacheIntelligenceEnabled() throws Exception {
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    String yaml = "runtime: shell\n"
        + "id: s1\n"
        + "name: s1\n"
        + "steps:\n"
        + "  - id: step1\n"
        + "    name: step1\n"
        + "    run:\n"
        + "      script: echo hello\n";
    Dependency dependency = Dependency.newBuilder().build();
    YamlField yamlField = YamlUtils.readTree(yaml);

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(yamlField)
                                  .dependency(dependency)
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setId("s1");
    stageNode.setName("s1");
    io.harness.beans.steps.v1.CachingV1 cachingV1 =
        io.harness.beans.steps.v1.CachingV1.builder().enabled(ParameterField.createValueField(true)).build();
    stageNode.setCacheIntelligence(cachingV1);

    DockerInfraYaml d = DockerInfraYaml.builder().spec(DockerInfraYaml.DockerInfraSpec.builder().build()).build();
    when(ciPlanCreatorUtils.getInfrastructure(any(), any(), any())).thenReturn(d);
    when(ciPlanCreatorUtils.getDeserializedOptions(any())).thenReturn(Optional.of(Options.builder().build()));
    when(ciPlanCreatorUtils.getCodebase(eq(ctx), any())).thenReturn(Optional.empty());
    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.empty());

    io.harness.pms.sdk.core.steps.io.StepParameters result =
        creator.getStageParameters(ctx, stageNode, Collections.singletonList("child1"));

    assertThat(result).isNotNull();
    io.harness.plancreator.steps.common.v1.StageElementParametersV1 stageParams =
        (io.harness.plancreator.steps.common.v1.StageElementParametersV1) result;
    io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS spec =
        (io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS) stageParams.getSpec();
    assertThat(spec.getCaching()).isNotNull();
    assertThat(spec.getCaching().getEnabled().getValue()).isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStageParameters_WithBuildIntelligenceDisabled_shouldReturnDisabledBuildIntelligence()
      throws Exception {
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    String yaml = "runtime: shell\n"
        + "id: s1\n"
        + "name: s1\n"
        + "steps:\n"
        + "  - id: step1\n"
        + "    name: step1\n"
        + "    run:\n"
        + "      script: echo hello\n";
    Dependency dependency = Dependency.newBuilder().build();
    YamlField yamlField = YamlUtils.readTree(yaml);

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(yamlField)
                                  .dependency(dependency)
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setId("s1");
    stageNode.setName("s1");
    stageNode.setBuildIntelligence(
        BuildIntelligenceV1.builder().enabled(ParameterField.createValueField(false)).build());

    DockerInfraYaml d = DockerInfraYaml.builder().spec(DockerInfraYaml.DockerInfraSpec.builder().build()).build();
    when(ciPlanCreatorUtils.getInfrastructure(any(), any(), any())).thenReturn(d);
    when(ciPlanCreatorUtils.getDeserializedOptions(any())).thenReturn(Optional.of(Options.builder().build()));
    when(ciPlanCreatorUtils.getCodebase(eq(ctx), any())).thenReturn(Optional.empty());
    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.empty());

    io.harness.pms.sdk.core.steps.io.StepParameters result =
        creator.getStageParameters(ctx, stageNode, Collections.singletonList("child1"));

    assertThat(result).isNotNull();
    io.harness.plancreator.steps.common.v1.StageElementParametersV1 stageParams =
        (io.harness.plancreator.steps.common.v1.StageElementParametersV1) result;
    io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS spec =
        (io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS) stageParams.getSpec();
    assertThat(spec.getBuildIntelligence()).isNotNull();
    assertThat(spec.getBuildIntelligence().getEnabled().getValue()).isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode_WithoutStrategy_shouldSetNormalAdvisers() throws Exception {
    String yaml = "stage:\n"
        + "  runtime: shell\n"
        + "  id: s1\n"
        + "  name: s1\n"
        + "  steps:\n"
        + "    - id: step1\n"
        + "      name: step1\n"
        + "      run:\n"
        + "        script: echo hello\n";

    String yamlWithUuids = YamlUtils.injectUuid(yaml);
    YamlField root = YamlUtils.readTree(yamlWithUuids);
    YamlField stageField = root.getNode().getField("stage");

    String nextSiblingUuid = "NEXT_NODE_UUID";
    HarnessStruct nodeMetadata =
        HarnessStruct.newBuilder()
            .putData(PlanCreatorConstants.NEXT_ID, HarnessValue.newBuilder().setStringValue(nextSiblingUuid).build())
            .build();
    Dependency dependency = Dependency.newBuilder().setNodeMetadata(nodeMetadata).build();

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(stageField)
                                  .dependency(dependency)
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    CIPlanCreatorUtils mockCiPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", mockCiPlanCreatorUtils);

    KryoSerializer mockKryoSerializer = mock(KryoSerializer.class);
    Whitebox.setInternalState(creator, "kryoSerializer", mockKryoSerializer, AbstractStagePlanCreator.class);
    Whitebox.setInternalState(creator, "kryoSerializer", mockKryoSerializer);
    when(mockKryoSerializer.asBytes(any())).thenReturn(new byte[0]);
    when(mockKryoSerializer.asDeflatedBytes(any())).thenReturn(new byte[0]);

    DockerInfraYaml dockerInfra =
        DockerInfraYaml.builder().spec(DockerInfraYaml.DockerInfraSpec.builder().build()).build();
    when(mockCiPlanCreatorUtils.getInfrastructure(any(), any(), any())).thenReturn(dockerInfra);
    when(mockCiPlanCreatorUtils.getDeserializedOptions(any())).thenReturn(Optional.of(Options.builder().build()));
    when(mockCiPlanCreatorUtils.getCodebase(eq(ctx), any())).thenReturn(Optional.empty());

    UnifiedStageNodeV1 stageNode = creator.getFieldObject(stageField);
    List<String> children = Collections.singletonList(stageField.getNode().getField("steps").getUuid());

    PlanNode planNode = creator.createPlanForParentNode(ctx, stageNode, children);

    assertThat(planNode).isNotNull();
    assertThat(planNode.getIdentifier()).isEqualTo("s1");
    assertThat(planNode.getName()).isEqualTo("s1");
    assertThat(planNode.getStepType()).isEqualTo(io.harness.ci.execution.states.IntegrationStageStepPMS.STEP_TYPE);
    // Without strategy, normal advisers should be populated
    assertThat(planNode.getAdviserObtainments()).isNotEmpty();
  }

  // ---- Service Environment Yamls - Revisited: length-1 `items` stage is plain (non-multi), end-to-end ----

  private static final String SINGLE_ELEMENT_ITEMS_STAGE_YAML = "stage:\n"
      + "  runtime: shell\n"
      + "  id: s1\n"
      + "  name: s1\n"
      + "  service:\n"
      + "    items:\n"
      + "      - svc1\n"
      + "  environment:\n"
      + "    items:\n"
      + "      - id: env1\n"
      + "        deploy-to: infra1\n"
      + "  steps:\n"
      + "    - id: step1\n"
      + "      name: step1\n"
      + "      run:\n"
      + "        script: echo hello\n";

  private UnifiedStagePMSPlanCreator setUpCreatorWithMocks(PlanCreationContext ctx) {
    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    KryoSerializer kryoSerializer = mock(KryoSerializer.class);
    Whitebox.setInternalState(creator, "kryoSerializer", kryoSerializer, AbstractStagePlanCreator.class);
    Whitebox.setInternalState(creator, "kryoSerializer", kryoSerializer);
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[0]);
    when(kryoSerializer.asDeflatedBytes(any())).thenReturn(new byte[0]);

    DockerInfraYaml dockerInfra =
        DockerInfraYaml.builder().spec(DockerInfraYaml.DockerInfraSpec.builder().build()).build();
    when(ciPlanCreatorUtils.getInfrastructure(any(), any(), any())).thenReturn(dockerInfra);
    when(ciPlanCreatorUtils.getDeserializedOptions(any())).thenReturn(Optional.of(Options.builder().build()));
    when(ciPlanCreatorUtils.getCodebase(eq(ctx), any())).thenReturn(Optional.empty());
    return creator;
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_SingleElementItemsDoesNotSpawnStrategyNode() throws Exception {
    String yamlWithUuids = YamlUtils.injectUuid(SINGLE_ELEMENT_ITEMS_STAGE_YAML);
    YamlField root = YamlUtils.readTree(yamlWithUuids);
    YamlField stageField = root.getNode().getField("stage");

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(stageField)
                                  .dependency(Dependency.newBuilder().build())
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();

    UnifiedStagePMSPlanCreator creator = setUpCreatorWithMocks(ctx);
    UnifiedStageNodeV1 stageNode = creator.getFieldObject(stageField);

    // Sanity: a length-1 `items` service/environment is classified single, not multi (drives the assertions below).
    assertThat(UnifiedMultiDeploymentUtils.isMultiDeployment(stageNode.getService(), stageNode.getEnvironment()))
        .isFalse();

    LinkedHashMap<String, PlanCreationResponse> responseMap = creator.createPlanForChildrenNodes(ctx, stageNode);

    for (PlanCreationResponse resp : responseMap.values()) {
      if (resp.getNodes() != null) {
        for (PlanNode node : resp.getNodes().values()) {
          assertThat(node.getStepType())
              .as("No strategy/matrix spawner node should be created for a length-1 `items` stage")
              .isNotEqualTo(UnifiedMultiDeploymentSpawnerStep.STEP_TYPE);
        }
      }
      if (resp.getPlanNode() != null) {
        assertThat(resp.getPlanNode().getStepType())
            .as("No strategy/matrix spawner node should be created for a length-1 `items` stage")
            .isNotEqualTo(UnifiedMultiDeploymentSpawnerStep.STEP_TYPE);
      }
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode_SingleElementItemsHasNoStrategyPostfixAndNormalAdvisers() throws Exception {
    String yamlWithUuids = YamlUtils.injectUuid(SINGLE_ELEMENT_ITEMS_STAGE_YAML);
    YamlField root = YamlUtils.readTree(yamlWithUuids);
    YamlField stageField = root.getNode().getField("stage");

    String nextSiblingUuid = "NEXT_NODE_UUID";
    HarnessStruct nodeMetadata =
        HarnessStruct.newBuilder()
            .putData(PlanCreatorConstants.NEXT_ID, HarnessValue.newBuilder().setStringValue(nextSiblingUuid).build())
            .build();
    Dependency dependency = Dependency.newBuilder().setNodeMetadata(nodeMetadata).build();

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(stageField)
                                  .dependency(dependency)
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();

    UnifiedStagePMSPlanCreator creator = setUpCreatorWithMocks(ctx);
    UnifiedStageNodeV1 stageNode = creator.getFieldObject(stageField);
    List<String> children = Collections.singletonList(stageField.getNode().getField("steps").getUuid());

    PlanNode planNode = creator.createPlanForParentNode(ctx, stageNode, children);

    // Plain (non-multi) identifier: no strategy identifier postfix appended.
    assertThat(planNode.getIdentifier()).isEqualTo("s1");
    assertThat(planNode.getIdentifier()).doesNotContain(STRATEGY_IDENTIFIER_POSTFIX);
    // Normal advisers attached, same as any other plain (non-multi, non-strategy) stage.
    assertThat(planNode.getAdviserObtainments()).isNotEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfo_SingleElementItemsReturnsPlainGraphLayout() throws Exception {
    String yamlWithUuids = YamlUtils.injectUuid(SINGLE_ELEMENT_ITEMS_STAGE_YAML);
    YamlField root = YamlUtils.readTree(yamlWithUuids);
    YamlField stageField = root.getNode().getField("stage");

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(stageField)
                                  .dependency(Dependency.newBuilder().build())
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();

    UnifiedStagePMSPlanCreator creator = setUpCreatorWithMocks(ctx);
    UnifiedStageNodeV1 stageNode = creator.getFieldObject(stageField);

    // Note: unifiedMultiDeploymentPlanCreatorHelper is intentionally left un-mocked (null) here — reaching the
    // multi-deployment graph-layout branch would NPE, so a passing plain-layout result also proves that branch was
    // not taken for this length-1 `items` stage.
    io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse layoutResponse =
        creator.getLayoutNodeInfo(ctx, stageNode);

    assertThat(layoutResponse).isNotNull();
    assertThat(layoutResponse.getLayoutNodes()).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes_WithoutStrategy_shouldReturnStepsDependency() throws Exception {
    String yaml = "stage:\n"
        + "  runtime: shell\n"
        + "  id: s1\n"
        + "  name: s1\n"
        + "  steps:\n"
        + "    - id: step1\n"
        + "      name: step1\n"
        + "      run:\n"
        + "        script: echo hello\n";

    String yamlWithUuids = YamlUtils.injectUuid(yaml);
    YamlField root = YamlUtils.readTree(yamlWithUuids);
    YamlField stageField = root.getNode().getField("stage");

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(stageField)
                                  .dependency(Dependency.newBuilder().build())
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    CIPlanCreatorUtils mockCiPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", mockCiPlanCreatorUtils);

    KryoSerializer mockKryoSerializer = mock(KryoSerializer.class);
    Whitebox.setInternalState(creator, "kryoSerializer", mockKryoSerializer, AbstractStagePlanCreator.class);
    Whitebox.setInternalState(creator, "kryoSerializer", mockKryoSerializer);
    when(mockKryoSerializer.asBytes(any())).thenReturn(new byte[0]);
    when(mockKryoSerializer.asDeflatedBytes(any())).thenReturn(new byte[0]);

    DockerInfraYaml dockerInfra =
        DockerInfraYaml.builder().spec(DockerInfraYaml.DockerInfraSpec.builder().build()).build();
    when(mockCiPlanCreatorUtils.getInfrastructure(any(), any(), any())).thenReturn(dockerInfra);
    when(mockCiPlanCreatorUtils.getDeserializedOptions(any())).thenReturn(Optional.of(Options.builder().build()));
    when(mockCiPlanCreatorUtils.getCodebase(eq(ctx), any())).thenReturn(Optional.empty());

    UnifiedStageNodeV1 stageNode = creator.getFieldObject(stageField);

    LinkedHashMap<String, PlanCreationResponse> responseMap = creator.createPlanForChildrenNodes(ctx, stageNode);

    assertThat(responseMap).isNotEmpty();
    // Should have an entry for the steps field
    String stepsUuid = stageField.getNode().getField("steps").getUuid();
    assertThat(responseMap).containsKey(stepsUuid);
    PlanCreationResponse stepsResponse = responseMap.get(stepsUuid);
    assertThat(stepsResponse.getDependencies()).isNotNull();
    assertThat(stepsResponse.getDependencies().getDependenciesMap()).containsKey(stepsUuid);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStageParameters_WithNullInputs_shouldReturnEmptyVariables() throws Exception {
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    String yaml = "runtime: shell\n"
        + "id: s1\n"
        + "name: s1\n"
        + "steps:\n"
        + "  - id: step1\n"
        + "    name: step1\n"
        + "    run:\n"
        + "      script: echo hello\n";
    Dependency dependency = Dependency.newBuilder().build();
    YamlField yamlField = YamlUtils.readTree(yaml);

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .currentField(yamlField)
                                  .dependency(dependency)
                                  .globalContext("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setExecutionContext(PlanExecutionContext.newBuilder().build())
                                          .build())
                                  .build();

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setId("s1");
    stageNode.setName("s1");

    DockerInfraYaml d = DockerInfraYaml.builder().spec(DockerInfraYaml.DockerInfraSpec.builder().build()).build();
    when(ciPlanCreatorUtils.getInfrastructure(any(), any(), any())).thenReturn(d);
    when(ciPlanCreatorUtils.getDeserializedOptions(any())).thenReturn(Optional.of(Options.builder().build()));
    when(ciPlanCreatorUtils.getCodebase(eq(ctx), any())).thenReturn(Optional.empty());
    when(ciPlanCreatorUtils.getDeserializedClone(dependency)).thenReturn(Optional.empty());

    io.harness.pms.sdk.core.steps.io.StepParameters result =
        creator.getStageParameters(ctx, stageNode, Collections.singletonList("child1"));

    assertThat(result).isNotNull();
    io.harness.plancreator.steps.common.v1.StageElementParametersV1 stageParams =
        (io.harness.plancreator.steps.common.v1.StageElementParametersV1) result;
    assertThat(stageParams.getVariables()).isNotNull();
    assertThat(stageParams.getVariables().getValue()).isEmpty();
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testCreatePlanForCodebase_DualNodePath_WhenStageOverrideAndPipelineClonePresent() throws Exception {
    CIPlanCreatorUtils ciPlanCreatorUtils = mock(CIPlanCreatorUtils.class);
    KryoSerializer kryoSerializer = mock(KryoSerializer.class);

    UnifiedStagePMSPlanCreator creator = new UnifiedStagePMSPlanCreator();
    Whitebox.setInternalState(creator, "ciPlanCreatorUtils", ciPlanCreatorUtils);
    Whitebox.setInternalState(creator, "kryoSerializer", kryoSerializer);
    when(kryoSerializer.asBytes(any())).thenReturn(new byte[0]);

    PlanCreationContext ctx = mock(PlanCreationContext.class);

    GitCloneStepInfoV1 pipelineClone =
        GitCloneStepInfoV1.builder()
            .connector(ParameterField.createValueField("pipelineConnector"))
            .repo(ParameterField.createValueField("pipeline-repo"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.BRANCH).name(ParameterField.createValueField("main")).build()))
            .build();

    GitCloneStepInfoV1 stageClone =
        GitCloneStepInfoV1.builder()
            .connector(ParameterField.createValueField("stageConnector"))
            .repo(ParameterField.createValueField("stage-repo"))
            .ref(ParameterField.createValueField(
                CloneRef.builder().type(CloneType.TAG).name(ParameterField.createValueField("v1.0")).build()))
            .build();

    CodeBase pipelineCodeBase = CodeBase.builder()
                                    .connectorRef(ParameterField.createValueField("pipelineConnector"))
                                    .repoName(ParameterField.createValueField("pipeline-repo"))
                                    .build(ParameterField.createValueField(
                                        io.harness.yaml.extended.ci.codebase.Build.builder()
                                            .type(BRANCH)
                                            .spec(io.harness.yaml.extended.ci.codebase.spec.BranchBuildSpec.builder()
                                                      .branch(ParameterField.createValueField("main"))
                                                      .build())
                                            .build()))
                                    .build();

    CodeBase stageCodeBase = CodeBase.builder()
                                 .connectorRef(ParameterField.createValueField("stageConnector"))
                                 .repoName(ParameterField.createValueField("stage-repo"))
                                 .build();

    when(ciPlanCreatorUtils.getCodebase(eq(ctx), eq(stageClone))).thenReturn(Optional.of(stageCodeBase));
    when(ciPlanCreatorUtils.getCodebase(eq(ctx), eq(pipelineClone))).thenReturn(Optional.of(pipelineCodeBase));

    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    String childNodeID = "child-node-id";

    CodeBase result = Whitebox.invokeMethod(
        creator, "createPlanForCodebase", ctx, stageClone, pipelineClone, true, planCreationResponseMap, childNodeID);

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(stageCodeBase);

    // Dual-node path: 3 nodes per step set (TASK + SYNC + parent) x 2 = 6 nodes
    assertThat(planCreationResponseMap).hasSize(6);

    int pipelineScopeCount = 0;
    int stageScopeCount = 0;
    for (PlanCreationResponse response : planCreationResponseMap.values()) {
      PlanNode node = response.getPlanNode();
      if (node != null && node.getStepParameters() instanceof CodeBaseTaskStepParameters) {
        CodeBaseTaskStepParameters params = (CodeBaseTaskStepParameters) node.getStepParameters();
        if (Boolean.TRUE.equals(params.getWriteToPipelineScope())) {
          pipelineScopeCount++;
          assertThat(params.getConnectorRef().getValue()).isEqualTo("pipelineConnector");
          assertThat(params.getExecutionSource()).isNotNull();
        } else if (Boolean.FALSE.equals(params.getWriteToPipelineScope())) {
          stageScopeCount++;
          assertThat(params.getConnectorRef().getValue()).isEqualTo("stageConnector");
          assertThat(params.getExecutionSource()).isNull();
        }
      }
    }
    // 2 task nodes per step set (TASK + SYNC facilitator)
    assertThat(pipelineScopeCount).isEqualTo(2);
    assertThat(stageScopeCount).isEqualTo(2);
  }
}
