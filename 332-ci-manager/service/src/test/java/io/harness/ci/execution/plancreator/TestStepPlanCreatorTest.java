/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.RunTestStepNode;
import io.harness.beans.steps.nodes.V1.TestStepNode;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.steps.stepinfo.TestStepInfo;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.TIBuildTool;
import io.harness.beans.yaml.extended.beans.BuildTool;
import io.harness.beans.yaml.extended.beans.PullPolicy;
import io.harness.beans.yaml.extended.beans.Shell;
import io.harness.beans.yaml.extended.beans.SplitStrategy;
import io.harness.beans.yaml.extended.beans.Splitting;
import io.harness.beans.yaml.extended.reports.V1.Report;
import io.harness.beans.yaml.extended.reports.V1.ReportType;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.execution.plancreator.V1.TestStepPlanCreator;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.yaml.core.timeout.Timeout;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;

public class TestStepPlanCreatorTest extends CategoryTest {
  private TestStepPlanCreator testStepPlanCreator;

  @Before
  public void setUp() {
    testStepPlanCreator = new TestStepPlanCreator();
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes_shouldReturnTestDisplayName() {
    Set<String> supportedStepTypes = testStepPlanCreator.getSupportedStepTypes();

    assertThat(supportedStepTypes)
        .as("should contain TEST display name")
        .containsExactlyInAnyOrder(CIStepInfoType.TEST.getDisplayName());
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSupportedYamlVersions_shouldReturnV1() {
    Set<String> versions = testStepPlanCreator.getSupportedYamlVersions();

    assertThat(versions).as("should contain only V1").containsExactlyInAnyOrder(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldObject_shouldParseYamlField() throws IOException {
    YamlField yamlField = mock(YamlField.class);
    YamlNode yamlNode = mock(YamlNode.class);
    TestStepNode expected = new TestStepNode();

    when(yamlField.getNode()).thenReturn(yamlNode);
    when(yamlNode.toString()).thenReturn("{}");

    try (MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      yamlUtilsMock.when(() -> YamlUtils.read(eq("{}"), eq(TestStepNode.class))).thenReturn(expected);

      TestStepNode result = testStepPlanCreator.getFieldObject(yamlField);

      assertThat(result).as("should return parsed TestStepNode").isEqualTo(expected);
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetFieldObject_whenIOException_shouldThrowInvalidYamlException() throws IOException {
    YamlField yamlField = mock(YamlField.class);
    YamlNode yamlNode = mock(YamlNode.class);

    when(yamlField.getNode()).thenReturn(yamlNode);
    when(yamlNode.toString()).thenReturn("invalid");

    try (MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      yamlUtilsMock.when(() -> YamlUtils.read(eq("invalid"), eq(TestStepNode.class)))
          .thenThrow(new IOException("parse error"));

      assertThatThrownBy(() -> testStepPlanCreator.getFieldObject(yamlField))
          .as("should throw InvalidYamlException on IOException")
          .isInstanceOf(InvalidYamlException.class)
          .hasMessageContaining("Unable to parse test step yaml");
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepNode_shouldBuildRunTestStepNodeWithAllFieldMappings() {
    Report report = new Report(
        "report-uuid", ReportType.JUNIT, ParameterField.createValueField(List.of("**/target/reports/*.xml")));
    Splitting splitting =
        Splitting.builder().enabled(ParameterField.createValueField(true)).strategy(SplitStrategy.CLASS_TIMING).build();

    TestStepInfo testStepInfo = TestStepInfo.builder()
                                    .image(ParameterField.createValueField("maven:3.8"))
                                    .uses(BuildTool.MAVEN)
                                    .envs(ParameterField.createValueField(Collections.emptyMap()))
                                    .privileged(ParameterField.createValueField(true))
                                    .user(ParameterField.createValueField(1000))
                                    .pull(ParameterField.createValueField(PullPolicy.ALWAYS))
                                    .shell(ParameterField.createValueField(Shell.SH))
                                    .reports(ParameterField.createValueField(List.of(report)))
                                    .outputs(ParameterField.createValueField(Collections.emptyList()))
                                    .splitting(splitting)
                                    .build();

    TestStepNode stepElement = new TestStepNode();
    stepElement.setUuid("test-uuid");
    stepElement.setName("my-test-step");
    stepElement.setTestStepInfo(testStepInfo);
    stepElement.setTimeout(ParameterField.createValueField(Timeout.fromString("30m")));

    try (MockedStatic<IdentifierGeneratorUtils> idGenMock = mockStatic(IdentifierGeneratorUtils.class);
         MockedStatic<CIPlanCreatorUtils> planUtilsMock = mockStatic(CIPlanCreatorUtils.class);
         MockedStatic<SerializerUtils> serializerMock = mockStatic(SerializerUtils.class)) {
      idGenMock.when(() -> IdentifierGeneratorUtils.getId("my-test-step")).thenReturn("my_test_step");
      planUtilsMock.when(() -> CIPlanCreatorUtils.getShell(any()))
          .thenReturn(ParameterField.createValueField(CIShellType.SH));
      planUtilsMock.when(() -> CIPlanCreatorUtils.getImagePullPolicy(any()))
          .thenReturn(ParameterField.createValueField(ImagePullPolicy.ALWAYS));
      serializerMock.when(() -> SerializerUtils.getStringFieldFromJsonNodeMap(any(), eq("args")))
          .thenReturn(ParameterField.createValueField("--tests all"));
      serializerMock.when(() -> SerializerUtils.getStringFieldFromJsonNodeMap(any(), eq("pre_command")))
          .thenReturn(ParameterField.createValueField("echo pre"));
      serializerMock.when(() -> SerializerUtils.getStringFieldFromJsonNodeMap(any(), eq("post_command")))
          .thenReturn(ParameterField.createValueField("echo post"));
      serializerMock.when(() -> SerializerUtils.getBooleanFieldFromJsonNodeMap(any(), eq("run_selected_tests")))
          .thenReturn(ParameterField.createValueField(true));
      serializerMock.when(() -> SerializerUtils.getListAsStringFromJsonNodeMap(any(), eq("packages")))
          .thenReturn(ParameterField.createValueField("com.example"));
      serializerMock.when(() -> SerializerUtils.getListAsStringFromJsonNodeMap(any(), eq("annotations")))
          .thenReturn(ParameterField.createValueField("@Test"));
      serializerMock.when(() -> SerializerUtils.getListAsStringFromJsonNodeMap(any(), eq("namespaces")))
          .thenReturn(ParameterField.createValueField("ns1"));
      serializerMock.when(() -> SerializerUtils.getListAsStringFromJsonNodeMap(any(), eq("globs")))
          .thenReturn(ParameterField.createValueField("**/*Test.java"));

      CIAbstractStepNode result = testStepPlanCreator.getStepNode(stepElement);

      assertThat(result).as("should not be null").isNotNull();
      assertThat(result.getUuid()).as("should have correct uuid").isEqualTo("test-uuid");
      assertThat(result.getIdentifier()).as("should have generated identifier").isEqualTo("my_test_step");
      assertThat(result.getName()).as("should have correct name").isEqualTo("my-test-step");

      RunTestStepNode runTestStepNode = (RunTestStepNode) result;
      RunTestsStepInfo runTestsStepInfo = runTestStepNode.getRunTestsStepInfo();
      assertThat(runTestsStepInfo).as("runTestsStepInfo should not be null").isNotNull();
      assertThat(runTestsStepInfo.getImage().getValue()).as("should map image").isEqualTo("maven:3.8");
      assertThat(runTestsStepInfo.getBuildTool().getValue()).as("should map buildTool").isEqualTo(TIBuildTool.MAVEN);
      assertThat(runTestsStepInfo.getShell().getValue()).as("should map shell").isEqualTo(CIShellType.SH);
      assertThat(runTestsStepInfo.getImagePullPolicy().getValue())
          .as("should map imagePullPolicy")
          .isEqualTo(ImagePullPolicy.ALWAYS);
      assertThat(runTestsStepInfo.getArgs().getValue()).as("should map args").isEqualTo("--tests all");
      assertThat(runTestsStepInfo.getPreCommand().getValue()).as("should map preCommand").isEqualTo("echo pre");
      assertThat(runTestsStepInfo.getPostCommand().getValue()).as("should map postCommand").isEqualTo("echo post");
      assertThat(runTestsStepInfo.getRunOnlySelectedTests().getValue())
          .as("should map runOnlySelectedTests")
          .isEqualTo(true);
      assertThat(runTestsStepInfo.getPrivileged().getValue()).as("should map privileged").isEqualTo(true);
      assertThat(runTestsStepInfo.getRunAsUser().getValue()).as("should map runAsUser").isEqualTo(1000);
      assertThat(runTestsStepInfo.getEnableTestSplitting().getValue())
          .as("should map enableTestSplitting")
          .isEqualTo(true);
      assertThat(runTestsStepInfo.getReports().getValue()).as("should map reports").isNotNull();
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepNode_whenUsesIsNull_shouldSetBuildToolToNull() {
    TestStepInfo testStepInfo = TestStepInfo.builder()
                                    .image(ParameterField.createValueField("maven:3.8"))
                                    .uses(null)
                                    .envs(ParameterField.createValueField(Collections.emptyMap()))
                                    .reports(ParameterField.createValueField(Collections.emptyList()))
                                    .outputs(ParameterField.createValueField(Collections.emptyList()))
                                    .build();

    TestStepNode stepElement = new TestStepNode();
    stepElement.setUuid("uuid-1");
    stepElement.setName("step1");
    stepElement.setTestStepInfo(testStepInfo);

    try (MockedStatic<IdentifierGeneratorUtils> idGenMock = mockStatic(IdentifierGeneratorUtils.class);
         MockedStatic<CIPlanCreatorUtils> planUtilsMock = mockStatic(CIPlanCreatorUtils.class);
         MockedStatic<SerializerUtils> serializerMock = mockStatic(SerializerUtils.class)) {
      idGenMock.when(() -> IdentifierGeneratorUtils.getId("step1")).thenReturn("step1");
      planUtilsMock.when(() -> CIPlanCreatorUtils.getShell(any())).thenReturn(ParameterField.ofNull());
      planUtilsMock.when(() -> CIPlanCreatorUtils.getImagePullPolicy(any())).thenReturn(ParameterField.ofNull());
      serializerMock.when(() -> SerializerUtils.getStringFieldFromJsonNodeMap(any(), any()))
          .thenReturn(ParameterField.ofNull());
      serializerMock.when(() -> SerializerUtils.getBooleanFieldFromJsonNodeMap(any(), any()))
          .thenReturn(ParameterField.ofNull());
      serializerMock.when(() -> SerializerUtils.getListAsStringFromJsonNodeMap(any(), any()))
          .thenReturn(ParameterField.ofNull());

      CIAbstractStepNode result = testStepPlanCreator.getStepNode(stepElement);

      RunTestStepNode runTestStepNode = (RunTestStepNode) result;
      RunTestsStepInfo runTestsStepInfo = runTestStepNode.getRunTestsStepInfo();
      assertThat(ParameterField.isNull(runTestsStepInfo.getBuildTool()))
          .as("buildTool should be null when uses is null")
          .isTrue();
    }
  }

  @Test
  @Owner(developers = OwnerRule.CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetStepNode_whenSplittingStrategyIsNull_shouldSetTestSplitStrategyToNull() {
    Splitting splitting = Splitting.builder().enabled(ParameterField.createValueField(false)).strategy(null).build();

    TestStepInfo testStepInfo = TestStepInfo.builder()
                                    .image(ParameterField.createValueField("image"))
                                    .envs(ParameterField.createValueField(Collections.emptyMap()))
                                    .reports(ParameterField.createValueField(Collections.emptyList()))
                                    .outputs(ParameterField.createValueField(Collections.emptyList()))
                                    .splitting(splitting)
                                    .build();

    TestStepNode stepElement = new TestStepNode();
    stepElement.setUuid("uuid-2");
    stepElement.setName("step2");
    stepElement.setTestStepInfo(testStepInfo);

    try (MockedStatic<IdentifierGeneratorUtils> idGenMock = mockStatic(IdentifierGeneratorUtils.class);
         MockedStatic<CIPlanCreatorUtils> planUtilsMock = mockStatic(CIPlanCreatorUtils.class);
         MockedStatic<SerializerUtils> serializerMock = mockStatic(SerializerUtils.class)) {
      idGenMock.when(() -> IdentifierGeneratorUtils.getId("step2")).thenReturn("step2");
      planUtilsMock.when(() -> CIPlanCreatorUtils.getShell(any())).thenReturn(ParameterField.ofNull());
      planUtilsMock.when(() -> CIPlanCreatorUtils.getImagePullPolicy(any())).thenReturn(ParameterField.ofNull());
      serializerMock.when(() -> SerializerUtils.getStringFieldFromJsonNodeMap(any(), any()))
          .thenReturn(ParameterField.ofNull());
      serializerMock.when(() -> SerializerUtils.getBooleanFieldFromJsonNodeMap(any(), any()))
          .thenReturn(ParameterField.ofNull());
      serializerMock.when(() -> SerializerUtils.getListAsStringFromJsonNodeMap(any(), any()))
          .thenReturn(ParameterField.ofNull());

      CIAbstractStepNode result = testStepPlanCreator.getStepNode(stepElement);

      RunTestStepNode runTestStepNode = (RunTestStepNode) result;
      RunTestsStepInfo runTestsStepInfo = runTestStepNode.getRunTestsStepInfo();
      assertThat(ParameterField.isNull(runTestsStepInfo.getTestSplitStrategy()))
          .as("testSplitStrategy should be null when strategy is null")
          .isTrue();
    }
  }
}
