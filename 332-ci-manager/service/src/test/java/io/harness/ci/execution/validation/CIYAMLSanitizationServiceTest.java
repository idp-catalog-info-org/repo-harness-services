/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.validation;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.HEN;
import static io.harness.rule.OwnerRule.JAMIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.beans.steps.stepinfo.RunTestStepV2Info;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfoV1;
import io.harness.beans.steps.stepinfo.StepNodeV1;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.integrationstage.K8InitializeTaskUtilsHelper;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class CIYAMLSanitizationServiceTest extends CIExecutionTestBase {
  @Mock CIMiningPatternJob ciMiningPatternJob;
  @InjectMocks CIYAMLSanitizationServiceImpl ciyamlSanitizationService;

  private static final String MALICIOUS_KEYWORD = "dero-stratum-miner";
  private static final String SAFE_COMMAND = "mvn clean install";

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testValidRun() {
    List<ExecutionWrapperConfig> steps = K8InitializeTaskUtilsHelper.getExecutionWrapperConfigList();
    boolean validate = false;
    try {
      validate = ciyamlSanitizationService.validate(steps);
    } catch (Exception e) {
    }

    assertThat(validate).isEqualTo(true);
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testValidRunTests() {
    List<ExecutionWrapperConfig> steps = K8InitializeTaskUtilsHelper.getRunTestExecutionWrapperConfigList();
    boolean validate = ciyamlSanitizationService.validate(steps);
    assertThat(validate).isEqualTo(true);
  }

  @Test
  @Owner(developers = HEN)
  @Category(UnitTests.class)
  public void testMaliciousRun() {
    List<ExecutionWrapperConfig> steps = K8InitializeTaskUtilsHelper.getExecutionMinerWrapperConfigList();
    Set<String> maliciousMiningPatterns = new HashSet<>();
    maliciousMiningPatterns.add("dero-stratum-miner");
    Mockito.when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(maliciousMiningPatterns);
    boolean validate = false;
    try {
      validate = ciyamlSanitizationService.validate(steps);
    } catch (CIStageExecutionException e) {
    }
    assertThat(validate).isEqualTo(false);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_whenStepIsNull_shouldContinue() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());
    ExecutionWrapperConfig wrapperWithNullStep = ExecutionWrapperConfig.builder().step(null).build();
    List<ExecutionWrapperConfig> steps = Collections.singletonList(wrapperWithNullStep);

    boolean result = ciyamlSanitizationService.validate(steps);

    assertThat(result).as("Should return true when step is null and processing skips it").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v1RunStep_whenMaliciousCommand_shouldThrow() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    RunStepInfoV1 runStepInfoV1 =
        RunStepInfoV1.builder().script(ParameterField.createValueField(MALICIOUS_KEYWORD + " --pool mining")).build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().run(ParameterField.createValueField(runStepInfoV1)).build();

    ExecutionWrapperConfig wrapper = buildV1WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);

      assertThatThrownBy(() -> ciyamlSanitizationService.validate(Collections.singletonList(wrapper)))
          .as("Should throw CIStageExecutionException when V1 run step contains malicious keyword")
          .isInstanceOf(CIStageExecutionException.class)
          .hasMessageContaining("Malicious activity detected");
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v1RunStep_whenSafeCommand_shouldPass() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    RunStepInfoV1 runStepInfoV1 = RunStepInfoV1.builder().script(ParameterField.createValueField(SAFE_COMMAND)).build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().run(ParameterField.createValueField(runStepInfoV1)).build();

    ExecutionWrapperConfig wrapper = buildV1WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);

      boolean result = ciyamlSanitizationService.validate(Collections.singletonList(wrapper));
      assertThat(result).as("Should return true when V1 run step has safe command").isTrue();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v1RunStep_whenDefaultCommand_shouldContinue() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    String defaultVal = "echo default";
    ParameterField<String> scriptField = ParameterField.createValueField(defaultVal);
    scriptField.setDefaultValue(defaultVal);

    RunStepInfoV1 runStepInfoV1 = RunStepInfoV1.builder().script(scriptField).build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().run(ParameterField.createValueField(runStepInfoV1)).build();

    ExecutionWrapperConfig wrapper = buildV1WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);

      boolean result = ciyamlSanitizationService.validate(Collections.singletonList(wrapper));
      assertThat(result).as("Should return true when V1 run step has default command value").isTrue();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v1BackgroundStep_whenMaliciousCommand_shouldThrow() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    RunStepInfoV1 backgroundStepInfoV1 =
        RunStepInfoV1.builder().script(ParameterField.createValueField(MALICIOUS_KEYWORD + " --bg")).build();
    StepNodeV1 stepNodeV1 =
        StepNodeV1.builder().background(ParameterField.createValueField(backgroundStepInfoV1)).build();

    ExecutionWrapperConfig wrapper = buildV1WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);

      assertThatThrownBy(() -> ciyamlSanitizationService.validate(Collections.singletonList(wrapper)))
          .as("Should throw CIStageExecutionException when V1 background step contains malicious keyword")
          .isInstanceOf(CIStageExecutionException.class)
          .hasMessageContaining("Malicious activity detected");
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v1RunTestStep_whenMaliciousCommand_shouldThrow() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    RunTestsStepInfoV1 runTestsStepInfoV1 =
        RunTestsStepInfoV1.builder().script(ParameterField.createValueField(MALICIOUS_KEYWORD + " --test")).build();
    StepNodeV1 stepNodeV1 = StepNodeV1.builder().runTest(ParameterField.createValueField(runTestsStepInfoV1)).build();

    ExecutionWrapperConfig wrapper = buildV1WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);

      assertThatThrownBy(() -> ciyamlSanitizationService.validate(Collections.singletonList(wrapper)))
          .as("Should throw CIStageExecutionException when V1 runTest step contains malicious keyword")
          .isInstanceOf(CIStageExecutionException.class)
          .hasMessageContaining("Malicious activity detected");
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v1Step_whenNoRunOrBackgroundOrRunTest_shouldPass() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    StepNodeV1 stepNodeV1 = StepNodeV1.builder().build();

    ExecutionWrapperConfig wrapper = buildV1WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNodeV1(any())).thenReturn(stepNodeV1);

      boolean result = ciyamlSanitizationService.validate(Collections.singletonList(wrapper));
      assertThat(result)
          .as("Should return true when V1 step has no run/background/runTest - command stays null")
          .isTrue();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v0Step_whenAbstractNodeIsNull_shouldContinue() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    ExecutionWrapperConfig wrapper = buildV0WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNode(any())).thenReturn(null);

      boolean result = ciyamlSanitizationService.validate(Collections.singletonList(wrapper));
      assertThat(result).as("Should return true when abstractNode is null").isTrue();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v0RunStep_whenDefaultCommand_shouldContinue() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    String defaultVal = "echo default";
    ParameterField<String> commandField = ParameterField.createValueField(defaultVal);
    commandField.setDefaultValue(defaultVal);

    RunStepInfo runStepInfo = RunStepInfo.builder().command(commandField).build();
    CIAbstractStepNode abstractNode = Mockito.mock(CIAbstractStepNode.class);
    when(abstractNode.getStepSpecType()).thenReturn(runStepInfo);

    ExecutionWrapperConfig wrapper = buildV0WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNode(any())).thenReturn(abstractNode);

      boolean result = ciyamlSanitizationService.validate(Collections.singletonList(wrapper));
      assertThat(result).as("Should return true when RUN step command equals its default value").isTrue();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v0TestV2Step_whenMaliciousCommand_shouldThrow() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    RunTestStepV2Info testV2Info =
        RunTestStepV2Info.builder().command(ParameterField.createValueField(MALICIOUS_KEYWORD + " --pool xmr")).build();
    CIAbstractStepNode abstractNode = Mockito.mock(CIAbstractStepNode.class);
    when(abstractNode.getStepSpecType()).thenReturn(testV2Info);

    ExecutionWrapperConfig wrapper = buildV0WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNode(any())).thenReturn(abstractNode);

      assertThatThrownBy(() -> ciyamlSanitizationService.validate(Collections.singletonList(wrapper)))
          .as("Should throw CIStageExecutionException when TESTV2 step contains malicious keyword")
          .isInstanceOf(CIStageExecutionException.class)
          .hasMessageContaining("Malicious activity detected");
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v0TestV2Step_whenSafeCommand_shouldPass() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    RunTestStepV2Info testV2Info =
        RunTestStepV2Info.builder().command(ParameterField.createValueField(SAFE_COMMAND)).build();
    CIAbstractStepNode abstractNode = Mockito.mock(CIAbstractStepNode.class);
    when(abstractNode.getStepSpecType()).thenReturn(testV2Info);

    ExecutionWrapperConfig wrapper = buildV0WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNode(any())).thenReturn(abstractNode);

      boolean result = ciyamlSanitizationService.validate(Collections.singletonList(wrapper));
      assertThat(result).as("Should return true when TESTV2 step has safe command").isTrue();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v0TestV2Step_whenDefaultCommand_shouldContinue() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    String defaultVal = "echo test";
    ParameterField<String> commandField = ParameterField.createValueField(defaultVal);
    commandField.setDefaultValue(defaultVal);

    RunTestStepV2Info testV2Info = RunTestStepV2Info.builder().command(commandField).build();
    CIAbstractStepNode abstractNode = Mockito.mock(CIAbstractStepNode.class);
    when(abstractNode.getStepSpecType()).thenReturn(testV2Info);

    ExecutionWrapperConfig wrapper = buildV0WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNode(any())).thenReturn(abstractNode);

      boolean result = ciyamlSanitizationService.validate(Collections.singletonList(wrapper));
      assertThat(result).as("Should return true when TESTV2 step command equals its default value").isTrue();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v0Step_whenUnknownStepType_commandRemainsNull_shouldContinue() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    CIStepInfo unknownStepInfo = Mockito.mock(CIStepInfo.class);
    TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.PLUGIN).build();
    when(unknownStepInfo.getNonYamlInfo()).thenReturn(typeInfo);

    CIAbstractStepNode abstractNode = Mockito.mock(CIAbstractStepNode.class);
    when(abstractNode.getStepSpecType()).thenReturn(unknownStepInfo);

    ExecutionWrapperConfig wrapper = buildV0WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNode(any())).thenReturn(abstractNode);

      boolean result = ciyamlSanitizationService.validate(Collections.singletonList(wrapper));
      assertThat(result)
          .as("Should return true when step type is not RUN/RUN_TESTS/TESTV2 and command stays null")
          .isTrue();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v0RunStep_whenCommandDoesNotMatchAnyPattern_shouldPass() {
    Set<String> patterns = new HashSet<>();
    patterns.add("xmrig");
    patterns.add("cryptonight");
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(patterns);

    RunStepInfo runStepInfo =
        RunStepInfo.builder().command(ParameterField.createValueField("npm install && npm test")).build();
    CIAbstractStepNode abstractNode = Mockito.mock(CIAbstractStepNode.class);
    when(abstractNode.getStepSpecType()).thenReturn(runStepInfo);

    ExecutionWrapperConfig wrapper = buildV0WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNode(any())).thenReturn(abstractNode);

      boolean result = ciyamlSanitizationService.validate(Collections.singletonList(wrapper));
      assertThat(result).as("Should return true when command does not contain any malicious pattern").isTrue();
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v0RunTestsStep_whenMaliciousPreCommand_shouldThrow() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    RunTestsStepInfo runTestsStepInfo = RunTestsStepInfo.builder()
                                            .preCommand(ParameterField.createValueField(MALICIOUS_KEYWORD + " --pre"))
                                            .postCommand(ParameterField.createValueField("echo done"))
                                            .build();
    CIAbstractStepNode abstractNode = Mockito.mock(CIAbstractStepNode.class);
    when(abstractNode.getStepSpecType()).thenReturn(runTestsStepInfo);

    ExecutionWrapperConfig wrapper = buildV0WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNode(any())).thenReturn(abstractNode);

      assertThatThrownBy(() -> ciyamlSanitizationService.validate(Collections.singletonList(wrapper)))
          .as("Should throw when RUN_TESTS preCommand contains malicious keyword")
          .isInstanceOf(CIStageExecutionException.class)
          .hasMessageContaining("Malicious activity detected");
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidate_v0RunTestsStep_whenBothDefaultCommands_shouldContinue() {
    when(ciMiningPatternJob.getMaliciousMiningPatterns()).thenReturn(getMaliciousPatterns());

    String defaultPre = "echo pre";
    String defaultPost = "echo post";
    ParameterField<String> preCommand = ParameterField.createValueField(defaultPre);
    preCommand.setDefaultValue(defaultPre);
    ParameterField<String> postCommand = ParameterField.createValueField(defaultPost);
    postCommand.setDefaultValue(defaultPost);

    RunTestsStepInfo runTestsStepInfo =
        RunTestsStepInfo.builder().preCommand(preCommand).postCommand(postCommand).build();
    CIAbstractStepNode abstractNode = Mockito.mock(CIAbstractStepNode.class);
    when(abstractNode.getStepSpecType()).thenReturn(runTestsStepInfo);

    ExecutionWrapperConfig wrapper = buildV0WrapperWithJsonStep();

    try (MockedStatic<IntegrationStageUtils> mockedStatic = mockStatic(IntegrationStageUtils.class)) {
      mockedStatic.when(() -> IntegrationStageUtils.getStepNode(any())).thenReturn(abstractNode);

      boolean result = ciyamlSanitizationService.validate(Collections.singletonList(wrapper));
      assertThat(result).as("Should return true when both pre and post commands are default values").isTrue();
    }
  }

  private Set<String> getMaliciousPatterns() {
    Set<String> patterns = new HashSet<>();
    patterns.add(MALICIOUS_KEYWORD);
    return patterns;
  }

  private ExecutionWrapperConfig buildV1WrapperWithJsonStep() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepNode = mapper.createObjectNode();
    stepNode.put("id", "v1-step");
    return ExecutionWrapperConfig.builder().version("1").step(stepNode).build();
  }

  private ExecutionWrapperConfig buildV0WrapperWithJsonStep() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepNode = mapper.createObjectNode();
    stepNode.put("identifier", "v0-step");
    stepNode.put("type", "Run");
    stepNode.put("name", "test step");
    return ExecutionWrapperConfig.builder().step(stepNode).build();
  }
}
