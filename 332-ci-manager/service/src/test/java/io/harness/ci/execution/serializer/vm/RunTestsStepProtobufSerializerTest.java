/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.rule.OwnerRule.SAHITHI;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.TIBuildTool;
import io.harness.beans.yaml.extended.TILanguage;
import io.harness.beans.yaml.extended.buildIntelligence.BuildIntelligence;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.serializer.RunTestsStepProtobufSerializer;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.ci.execution.utils.ci.CIInitStripStageVarHelper;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.CISweepingOutputEvaluator;
import io.harness.encryption.SecretRefHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.OutputVariable;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CI)
public class RunTestsStepProtobufSerializerTest {
  @InjectMocks private RunTestsStepProtobufSerializer runTestsStepProtobufSerializer;

  @Mock private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private SerializerUtils serializerUtils;
  @Mock private CISweepingOutputEvaluator ciSweepingOutputEvaluator;
  @Mock private CIInitStripStageVarHelper ciInitStripStageVarHelper;

  private Ambiance ambiance;
  private RunTestsStepInfo stepInfo;
  private final String callbackId = UUID.randomUUID().toString();
  public static final String STEP_ID = "runStepId";
  private StageDetails stageDetails;

  @Before
  public void setUp() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");

    ambiance = Ambiance.newBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setPipelineIdentifier("pipelineId")
                                    .setRunSequence(1)
                                    .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                                    .build())
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("runtimeId")
                                  .setIdentifier("runStepId")
                                  .setOriginalIdentifier("runStepId")
                                  .setRetryIndex(1)
                                  .build())
                   .build();
    stepInfo = RunTestsStepInfo.builder()
                   .identifier(STEP_ID)
                   .connectorRef(ParameterField.<String>builder().value("docker").build())
                   .image(ParameterField.<String>builder().expressionValue("alpine").build())
                   .reports(ParameterField.<UnitTestReport>builder().build())
                   .args(ParameterField.<String>builder().value("abc").build())
                   .buildTool(ParameterField.<TIBuildTool>builder().value(TIBuildTool.BAZEL).build())
                   .language(ParameterField.<TILanguage>builder().value(TILanguage.JAVA).build())
                   .build();
    stageDetails =
        StageDetails.builder()
            .buildIntelligence(
                BuildIntelligence.builder().enabled(ParameterField.<Boolean>builder().value(false).build()).build())
            .build();
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void testRunStepPhotoBuffer() {
    NGVariable outputVariableWithoutType =
        StringNGVariable.builder()
            .name("variableWithoutType")
            .type(NGVariableType.STRING)
            .value(ParameterField.<String>builder().value("variableWithoutType").build())
            .build();
    NGVariable outputVariableWithTypeString =
        StringNGVariable.builder()
            .name("variableWithTypeString")
            .type(NGVariableType.STRING)
            .value(ParameterField.<String>builder().value("variableWithTypeString").build())
            .build();

    NGVariable outputVariableWithTypeSecret =
        SecretNGVariable.builder()
            .name("variableWithTypeSecret")
            .type(NGVariableType.SECRET)
            .value(ParameterField.createValueField(SecretRefHelper.createSecretRef("variableWithTypeSecret")))
            .build();
    List<NGVariable> ngVariableList = new ArrayList<>();
    ngVariableList.add(outputVariableWithoutType);
    ngVariableList.add(outputVariableWithTypeString);
    ngVariableList.add(outputVariableWithTypeSecret);

    stepInfo.setOutputVariables(ParameterField.createValueField(ngVariableList));

    when(delegateCallbackTokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().build());

    UnitStep unitStep = runTestsStepProtobufSerializer.serializeStepWithStepParameters(stepInfo, 123456, callbackId,
        "logKey", stepInfo.getIdentifier(), ParameterField.<Timeout>builder().build(), "accountId", "stepName",
        ambiance, "podName", stageDetails, OSType.Linux);

    OutputVariable protoOutputVariableWithoutType =
        OutputVariable.newBuilder().setValue("variableWithoutType").setKey("variableWithoutType").build();
    OutputVariable protoOutputVariableWithTypeString =
        OutputVariable.newBuilder().setValue("variableWithTypeString").setKey("variableWithTypeString").build();
    OutputVariable protoOutputVariableWithTypeSecret = OutputVariable.newBuilder()
                                                           .setType(OutputVariable.OutputType.SECRET)
                                                           .setValue("variableWithTypeSecret")
                                                           .setKey("variableWithTypeSecret")
                                                           .build();
    List<OutputVariable> outputVariables = new ArrayList<>();
    outputVariables.add(protoOutputVariableWithoutType);
    outputVariables.add(protoOutputVariableWithTypeString);
    outputVariables.add(protoOutputVariableWithTypeSecret);

    List<String> output = new ArrayList<>();
    output.add("variableWithoutType");
    output.add("variableWithTypeString");
    output.add("variableWithTypeSecret");

    assertThat(unitStep.getRunTests().getOutputsList()).isEqualTo(outputVariables);
    assertThat(unitStep.getRunTests().getEnvVarOutputsList()).isEqualTo(output);
  }
}
