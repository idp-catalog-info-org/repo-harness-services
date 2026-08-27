/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers.validate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGES;
import static io.harness.rule.OwnerRule.BHUMIJ;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.stages.BasicStageInfo;
import io.harness.pms.stages.StageExpressionInfo;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class StagesExpressionExtractorTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";

  @Mock private PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock private PMSPipelineService pmsPipelineService;
  @Mock private PMSPipelineServiceHelper pmsPipelineServiceHelper;

  @InjectMocks private StagesExpressionExtractor stagesExpressionExtractor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetNonLocalExpressions() {
    String pipelineYaml = getPipelineYaml();
    Set<String> nonLocalExpressions1 = stagesExpressionExtractor.getNonLocalExpressions(
        pipelineYaml, Arrays.asList("a1", "d1", "p_d1"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    assertThat(nonLocalExpressions1).hasSize(1);
    assertThat(nonLocalExpressions1).contains("<+pipeline.stages.a2.name>");

    Set<String> nonLocalExpressions2 = stagesExpressionExtractor.getNonLocalExpressions(
        pipelineYaml, Arrays.asList("a2", "d1_again", "p_d2"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    assertThat(nonLocalExpressions2).hasSize(0);

    Set<String> nonLocalExpressions3 = stagesExpressionExtractor.getNonLocalExpressions(
        pipelineYaml, Collections.singletonList("d1"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    assertThat(nonLocalExpressions3).hasSize(1);
    assertThat(nonLocalExpressions3).contains("<+stages.a1.name>");

    Set<String> nonLocalExpressions4 = stagesExpressionExtractor.getNonLocalExpressions(pipelineYaml,
        Collections.singletonList("pipeline_chaining"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    assertThat(nonLocalExpressions4).hasSize(1);
    assertThat(nonLocalExpressions4)
        .containsOnly("<+pipeline.stages.stage1.spec.execution.steps.ShellScript_1.output.outputVariables.stage1Var>");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetNonLocalExpressionsChildPipeline() {
    String pipelineYaml = readFile("parent-pipeline.yaml");
    Set<String> nonLocalExpressions1 = stagesExpressionExtractor.getNonLocalExpressions(
        pipelineYaml, Arrays.asList("child"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    assertThat(nonLocalExpressions1).hasSize(1);
    assertThat(nonLocalExpressions1).contains("<+pipeline.stages.dev.spec.artifacts.primary.tag>");
    verify(pmsPipelineService, times(0))
        .getPipeline(
            ACCOUNT_ID, "defaultChildOrg", "defaultChildProject", "childPipe", false, false, false, false, null, false);

    PipelineEntity pipelineEntity = PipelineEntity.builder().yaml(readFile("child-pipeline.yaml")).build();
    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline(
            ACCOUNT_ID, "defaultChildOrg", "defaultChildProject", "childPipe", false, false, false, false, null, false);
    Set<String> nonLocalExpressions2 = stagesExpressionExtractor.getNonLocalExpressions(
        pipelineYaml, Arrays.asList("child"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, true);
    assertThat(nonLocalExpressions2).hasSize(0);
    verify(pmsPipelineService)
        .getPipeline(
            ACCOUNT_ID, "defaultChildOrg", "defaultChildProject", "childPipe", false, false, false, false, null, false);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testGetNonLocalExpressionsPerStage() {
    String pipelineYaml = getPipelineYaml();
    Map<String, List<String>> nonLocalExpressions1 = stagesExpressionExtractor.getNonLocalExpressionsPerStage(
        pipelineYaml, Arrays.asList("a1", "d1", "p_d1"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    assertThat(nonLocalExpressions1).containsOnly(Map.entry("p_d1", List.of("<+pipeline.stages.a2.name>")));

    Map<String, List<String>> nonLocalExpressions2 = stagesExpressionExtractor.getNonLocalExpressionsPerStage(
        pipelineYaml, Arrays.asList("a2", "d1_again", "p_d2"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    assertThat(nonLocalExpressions2).hasSize(0);

    Map<String, List<String>> nonLocalExpressions3 = stagesExpressionExtractor.getNonLocalExpressionsPerStage(
        pipelineYaml, Collections.singletonList("d1"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    assertThat(nonLocalExpressions3).containsOnly(Map.entry("d1", List.of("<+stages.a1.name>")));

    Map<String, List<String>> nonLocalExpressions4 =
        stagesExpressionExtractor.getNonLocalExpressionsPerStage(pipelineYaml,
            Collections.singletonList("pipeline_chaining"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    assertThat(nonLocalExpressions4)
        .containsOnly(Map.entry("pipeline_chaining",
            List.of("<+pipeline.stages.stage1.spec.execution.steps.ShellScript_1.output.outputVariables.stage1Var>")));

    Map<String, List<String>> preservedLocalExpressionOrder = stagesExpressionExtractor.getNonLocalExpressionsPerStage(
        pipelineYaml, Arrays.asList("d1", "p_d1"), HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    Map<String, List<String>> expectedOrderedMap = new LinkedHashMap<>();
    expectedOrderedMap.put("d1", List.of("<+stages.a1.name>"));
    expectedOrderedMap.put("p_d1", List.of("<+pipeline.stages.a2.name>"));
    assertThat(preservedLocalExpressionOrder).containsExactlyEntriesOf(expectedOrderedMap);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetAllExpressionsInListOfStages() {
    String pipelineYaml = getPipelineYaml();
    StageExpressionInfo stageExpressionInfo = stagesExpressionExtractor.getAllExpressionsInListOfStages(
        pipelineYaml, Arrays.asList("d1", "p_d1"), HarnessYamlVersion.V0);
    Map<String, List<String>> d1AndPD1 = stageExpressionInfo.getExpressionsMap();
    assertThat(d1AndPD1).hasSize(2);
    assertThat(d1AndPD1.containsKey("d1")).isTrue();
    List<String> d1 = d1AndPD1.get("d1");
    assertThat(d1).hasSize(1);
    assertThat(d1.contains("<+stages.a1.name>")).isTrue();
    assertThat(d1AndPD1.containsKey("p_d1")).isTrue();
    List<String> pd1 = d1AndPD1.get("p_d1");
    assertThat(pd1).hasSize(1);
    assertThat(pd1.contains("<+pipeline.stages.a2.name>")).isTrue();

    stageExpressionInfo = stagesExpressionExtractor.getAllExpressionsInListOfStages(
        pipelineYaml, Collections.singletonList("a1"), HarnessYamlVersion.V0);
    Map<String, List<String>> a1 = stageExpressionInfo.getExpressionsMap();
    assertThat(a1).hasSize(1);
    assertThat(a1.get("a1")).hasSize(0);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageYamlList() {
    String invalidYaml = "pipeline:\nidentifier:s1";
    assertThatThrownBy(() -> stagesExpressionExtractor.getStageYamlList(invalidYaml, Collections.singletonList("a")))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Could not read pipeline yaml while extracting stage yaml list");

    String pipelineYaml = getPipelineYaml();
    List<String> stageYamlList =
        stagesExpressionExtractor
            .getStageYamlList(pipelineYaml, Arrays.asList("a1", "a2", "d1", "p_d1", "p_d2", "d1_again"))
            .stream()
            .map(BasicStageInfo::getYaml)
            .collect(Collectors.toList());
    assertThat(stageYamlList).hasSize(6);
    assertThat(stageYamlList.get(0)).isEqualTo(getStage("a1", "a1", "Approval", "notAnExpression"));
    assertThat(stageYamlList.get(1)).isEqualTo(getStage("a2", "a2", "Approval", "<+stage.name>"));
    assertThat(stageYamlList.get(2)).isEqualTo(getStage("d1", "d1", "Deployment", "<+stages.a1.name>"));
    assertThat(stageYamlList.get(3)).isEqualTo(getStage("p_d1", "p d1", "Deployment", "<+pipeline.stages.a2.name>"));
    assertThat(stageYamlList.get(4)).isEqualTo(getStage("p_d2", "p d2", "Deployment", "<+input>"));
    assertThat(stageYamlList.get(5)).isEqualTo(getStage("d1_again", "d1 again", "Deployment", "<+that.other.field>"));

    List<String> stageYamlListForD1 =
        stagesExpressionExtractor.getStageYamlList(pipelineYaml, Collections.singletonList("d1"))
            .stream()
            .map(BasicStageInfo::getYaml)
            .collect(Collectors.toList());
    assertThat(stageYamlListForD1).hasSize(1);
    assertThat(stageYamlListForD1.get(0)).isEqualTo(getStage("d1", "d1", "Deployment", "<+stages.a1.name>"));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetStageYamlListWithInsert() {
    String pipelineYaml = getPipelineYamlWithInsert();
    List<String> stageYamlList =
        stagesExpressionExtractor
            .getStageYamlList(pipelineYaml, Arrays.asList("cc", "xx", "sda", "wqw", "sda2", "dsa", "dsadsa"))
            .stream()
            .map(BasicStageInfo::getYaml)
            .collect(Collectors.toList());
    assertThat(stageYamlList).hasSize(7);
    assertThat(stageYamlList.get(0)).isEqualTo(getStage("wqw", "wqw", "Custom", "<+input>"));
    assertThat(stageYamlList.get(1)).isEqualTo(getStage("sda", "sda", "Deployment", "<+that.other.field>"));
    assertThat(stageYamlList.get(2)).isEqualTo(getStage("xx", "xx", "Custom", "<+random.other.field>"));
    assertThat(stageYamlList.get(3)).isEqualTo(getStage("cc", "cc", "Custom", "<+hello.other.field>"));
    assertThat(stageYamlList.get(4)).isEqualTo(getStage("sda2", "sda2", "Deployment", "<+yes.other.field>"));
    assertThat(stageYamlList.get(5)).isEqualTo(getStage("dsa", "dsa", "Custom", "<+no.other.field>"));
    assertThat(stageYamlList.get(6)).isEqualTo(getStage("dsadsa", "dsadsa", "Custom", "notAnExpression"));
  }

  private String getPipelineYamlWithInsert() {
    return "pipeline:\n"
        + "  identifier: pipelineTestingInjectRollback\n"
        + "  name: pipelineTestingInjectRollback\n"
        + "  projectIdentifier: test\n"
        + "  orgIdentifier: default\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: wqw\n"
        + "        name: wqw\n"
        + "        type: Custom\n"
        + "        field: <+input>\n"
        + "    - stage:\n"
        + "        identifier: sda\n"
        + "        name: sda\n"
        + "        type: Deployment\n"
        + "        field: <+that.other.field>\n"
        + "    - stage:\n"
        + "        identifier: eqwe\n"
        + "        name: eqwe\n"
        + "        type: Deployment\n"
        + "        field: <+some.other.field>\n"
        + "    - parallel:\n"
        + "        - insert:\n"
        + "            identifier: inject2\n"
        + "            name: inject2\n"
        + "            stages:\n"
        + "              - stage:\n"
        + "                  identifier: xx\n"
        + "                  name: xx\n"
        + "                  type: Custom\n"
        + "                  field: <+random.other.field>\n"
        + "        - stage:\n"
        + "            identifier: cc\n"
        + "            name: cc\n"
        + "            type: Custom\n"
        + "            field: <+hello.other.field>\n"
        + "    - insert:\n"
        + "        identifier: inject1\n"
        + "        name: inject1\n"
        + "        stages:\n"
        + "          - stage:\n"
        + "              identifier: fdsf1\n"
        + "              name: fdsf1sd\n"
        + "              type: Custom\n"
        + "              field: <+input>\n"
        + "          - stage:\n"
        + "              identifier: fdsf2\n"
        + "              name: fdsf2              \n"
        + "              type: Custom\n"
        + "              field: <+input>\n"
        + "          - stage:\n"
        + "              identifier: sda2\n"
        + "              name: sda2\n"
        + "              type: Deployment\n"
        + "              field: <+yes.other.field>\n"
        + "          - parallel:\n"
        + "              - stage:\n"
        + "                  identifier: dsa\n"
        + "                  name: dsa                  \n"
        + "                  type: Custom\n"
        + "                  field: <+no.other.field>\n"
        + "              - stage:\n"
        + "                  identifier: dsadsa\n"
        + "                  name: dsadsa                  \n"
        + "                  type: Custom\n"
        + "                  field: notAnExpression\n"
        + "  allowStageExecutions: true\n";
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetListOfExpressions() {
    String a1 = getStage("a1", "a1", "Approval", "notAnExpression");
    assertThat(stagesExpressionExtractor.getListOfExpressions(a1)).hasSize(0);

    String a2 = getStage("a2", "a2", "Approval", "<+stage.name>");
    List<String> a2Expressions = stagesExpressionExtractor.getListOfExpressions(a2);
    assertThat(a2Expressions).hasSize(1);
    assertThat(a2Expressions.get(0)).isEqualTo("<+stage.name>");

    String pd2 = getStage("p_d2", "<+a>", "Deployment", "<+input>");
    List<String> pd2Expressions = stagesExpressionExtractor.getListOfExpressions(pd2);
    assertThat(pd2Expressions).hasSize(2);
    assertThat(pd2Expressions.get(0)).isEqualTo("<+a>");
    assertThat(pd2Expressions.get(1)).isEqualTo("<+input>");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRemoveLocalExpressions() {
    String s1 = "s1";
    List<String> s1Expressions =
        Arrays.asList("<+pipeline.stages.s1.name>", "<+stages.s10.description>", "<+stages.s2.description>",
            "<+artifact.name>", "<+input>", "<+input>", "<+input>", "<+input>", "<+step.timeout>", "<+pipeline.name>");
    String s2 = "s2";
    List<String> s2Expressions = Arrays.asList("<+pipeline.stages.a1.name>", "<+stages.s1.description>",
        "<+stages.s20.description>", "<+pipeline.variables.v1>", "<+pipeline.properties.ci.codebase.connectorRef>",
        "<+step.timeout>", "<+pipeline.name>");
    String s3 = "s3";
    List<String> s3Expressions = Arrays.asList("<+pipeline.stages.s3.name>", "<+stage.name>");
    String s4 = "s4";
    List<String> s4Expressions = Collections.emptyList();
    Map<String, List<String>> expressionsMap = new LinkedHashMap<>();
    expressionsMap.put(s1, s1Expressions);
    expressionsMap.put(s2, s2Expressions);
    expressionsMap.put(s3, s3Expressions);
    expressionsMap.put(s4, s4Expressions);
    Set<String> expressionsToOtherStages = stagesExpressionExtractor.removeLocalExpressions(
        StageExpressionInfo.builder().expressionsMap(expressionsMap).stageYamlList(new HashMap<>()).build(),
        HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    assertThat(expressionsToOtherStages).hasSize(3);
    assertThat(expressionsToOtherStages)
        .contains("<+stages.s10.description>", "<+pipeline.stages.a1.name>", "<+stages.s20.description>");
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRemoveLocalExpressionsPreservingStageMappingAndOrder() {
    String s1 = "s1";
    List<String> s1Expressions =
        Arrays.asList("<+pipeline.stages.s1.name>", "<+stages.s10.description>", "<+stages.s2.description>",
            "<+artifact.name>", "<+input>", "<+input>", "<+input>", "<+input>", "<+step.timeout>", "<+pipeline.name>");
    String s2 = "s2";
    List<String> s2Expressions = Arrays.asList("<+pipeline.stages.a1.name>", "<+stages.s1.description>",
        "<+stages.s20.description>", "<+pipeline.variables.v1>", "<+pipeline.properties.ci.codebase.connectorRef>",
        "<+step.timeout>", "<+pipeline.name>");
    String s3 = "s3";
    List<String> s3Expressions = Arrays.asList("<+pipeline.stages.s3.name>", "<+stage.name>");
    String s4 = "s4";
    List<String> s4Expressions = Collections.emptyList();
    Map<String, List<String>> expressionsMap = new LinkedHashMap<>();
    expressionsMap.put(s1, s1Expressions);
    expressionsMap.put(s2, s2Expressions);
    expressionsMap.put(s3, s3Expressions);
    expressionsMap.put(s4, s4Expressions);

    Map<String, List<String>> expressionsToOtherStages =
        stagesExpressionExtractor.removeLocalExpressionsPreservingStageMappingAndOrder(
            StageExpressionInfo.builder().expressionsMap(expressionsMap).stageYamlList(new HashMap<>()).build(),
            HarnessYamlVersion.V0, ACCOUNT_ID, false, false, false);
    Map<String, List<String>> expectedExpressionsToOtherStages = new LinkedHashMap<>();
    expectedExpressionsToOtherStages.put("s1", List.of("<+stages.s10.description>"));
    expectedExpressionsToOtherStages.put("s2", List.of("<+pipeline.stages.a1.name>", "<+stages.s20.description>"));
    assertThat(expressionsToOtherStages).containsExactlyEntriesOf(expectedExpressionsToOtherStages);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIsLocalToStage() {
    assertThat(stagesExpressionExtractor.isLocalToStage("<+pipeline.stages.s1.name>")).isFalse();
    assertThat(stagesExpressionExtractor.isLocalToStage("<+stages.s1.description>")).isFalse();
    assertThat(stagesExpressionExtractor.isLocalToStage("<+input>")).isTrue();
    assertThat(stagesExpressionExtractor.isLocalToStage("<+step.name>")).isTrue();
    assertThat(stagesExpressionExtractor.isLocalToStage("<+artifact.image>")).isTrue();
    assertThatThrownBy(() -> stagesExpressionExtractor.isLocalToStage("staticValue"))
        .hasMessage("staticValue is not a syntactically valid pipeline expression")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageIdentifierInExpression() {
    assertThat(
        stagesExpressionExtractor.getStageIdentifierInExpression("<+pipeline.stages.s1.name>", HarnessYamlVersion.V0))
        .isEqualTo("s1");
    assertThat(stagesExpressionExtractor.getStageIdentifierInExpression("<+stages.s1.name>", HarnessYamlVersion.V0))
        .isEqualTo("s1");
    assertThat(
        stagesExpressionExtractor.getStageIdentifierInExpression("<+pipeline.stages.s_2.name>", HarnessYamlVersion.V0))
        .isEqualTo("s_2");
    assertThatThrownBy(
        () -> stagesExpressionExtractor.getStageIdentifierInExpression("<+artifact.image>", HarnessYamlVersion.V0))
        .hasMessage("<+artifact.image> is not a pipeline level or stages level expression")
        .isInstanceOf(InvalidRequestException.class);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private String getPipelineYaml() {
    return "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "     identifier: a1\n"
        + "     name: a1\n"
        + "     type: Approval\n"
        + "     field: notAnExpression\n"
        + "  - stage:\n"
        + "     identifier: a2\n"
        + "     name: a2\n"
        + "     type: Approval\n"
        + "     field: <+stage.name>\n"
        + "  - stage:\n"
        + "      identifier: d1\n"
        + "      name: d1\n"
        + "      type: Deployment\n"
        + "      field: <+stages.a1.name>\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: p_d1\n"
        + "        name: p d1\n"
        + "        type: Deployment\n"
        + "        field: <+pipeline.stages.a2.name>\n"
        + "    - stage:\n"
        + "        identifier: p_d2\n"
        + "        name: p d2\n"
        + "        type: Deployment\n"
        + "        field: <+input>\n"
        + "  - stage:\n"
        + "      identifier: d1_again\n"
        + "      name: d1 again\n"
        + "      type: Deployment\n"
        + "      field: <+that.other.field>\n"
        + "  - stage:\n"
        + "      identifier: pipeline_chaining\n"
        + "      name: pipeline chaining\n"
        + "      type: Pipeline\n"
        + "      field: <+that.other.field>\n"
        + "      spec:\n"
        + "        outputs:\n"
        + "          - name: chainedPipelineOutput\n"
        + "            value: "
        + "<+pipeline.stages.stage1.spec.execution.steps.ShellScript_1.output.outputVariables.var1>\n"
        + "        inputs:\n"
        + "            identifier: pipelineForChainingReplacedExpressions\n"
        + "            stages:\n"
        + "              - stage:\n"
        + "                  identifier: stage2\n"
        + "                  type: Custom\n"
        + "                  spec:\n"
        + "                    execution:\n"
        + "                      steps:\n"
        + "                        - step:\n"
        + "                            identifier: ShellScript_1\n"
        + "                            type: ShellScript\n"
        + "                            spec:\n"
        + "                              environmentVariables:\n"
        + "                                - name: var2Input\n"
        + "                                  type: String\n"
        + "                                  value: "
        + "<+pipeline.stages.stage1.spec.execution.steps.ShellScript_1.output.outputVariables.stage1Var>\n";
  }

  private String getStage(String identifier, String name, String type, String field) {
    return "stage:\n"
        + "  identifier: " + identifier + "\n"
        + "  name: " + name + "\n"
        + "  type: " + type + "\n"
        + "  field: " + field + "\n";
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetBasicStageInfoWithYaml() throws IOException {
    BasicStageInfo basicStageInfo =
        stagesExpressionExtractor.getBasicStageInfoWithYaml(YamlUtils.readTree(getPipelineYaml())
                                                                .getNode()
                                                                .getField(YAMLFieldNameConstants.PIPELINE)
                                                                .getNode()
                                                                .getField(STAGES)
                                                                .getNode()
                                                                .asArray()
                                                                .get(0));
    assertEquals("a1", basicStageInfo.getIdentifier());
    assertEquals("a1", basicStageInfo.getName());
    assertEquals("Approval", basicStageInfo.getType());
    assertEquals(YamlPipelineUtils.getYamlString(YamlUtils.readTree(getPipelineYaml())
                                                     .getNode()
                                                     .getField(YAMLFieldNameConstants.PIPELINE)
                                                     .getNode()
                                                     .getField(STAGES)
                                                     .getNode()
                                                     .asArray()
                                                     .get(0)
                                                     .getCurrJsonNode()),
        basicStageInfo.getYaml());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testIsReferringToNonStageValue() {
    assertTrue(stagesExpressionExtractor.isReferringToNonStageValue("<+pipeline.variables.test>", "0"));
    assertFalse(stagesExpressionExtractor.isReferringToNonStageValue("<+pipeline.stages.s1.variables.test>", "0"));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testProcessChildPipelineStages() {
    // Test with null stage info - should return empty
    Set<String> result1 = stagesExpressionExtractor.processChildPipelineStages(null, ACCOUNT_ID, false, false, true);
    assertThat(result1).isEmpty();

    // Test with non-pipeline stage type - should return empty
    BasicStageInfo nonPipelineStage = BasicStageInfo.builder().identifier("deploy1").type("Deployment").build();
    Set<String> result2 =
        stagesExpressionExtractor.processChildPipelineStages(nonPipelineStage, ACCOUNT_ID, false, false, true);
    assertThat(result2).isEmpty();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testExtractStageIdentifiersFromPipelineYaml() {
    // Test V0 pipeline
    Set<String> v0StageIds =
        stagesExpressionExtractor.extractStageIdentifiersFromPipelineYaml(getPipelineYaml(), HarnessYamlVersion.V0);
    assertThat(v0StageIds).contains("a1", "a2", "d1", "p_d1", "p_d2", "d1_again", "pipeline_chaining");
  }
}
