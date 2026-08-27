/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.SAURABH;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.PluginCompatibleStep;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.CISweepingOutputEvaluator;
import io.harness.connector.IACMUtils;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.iacm.execution.IACMStepsUtils;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.secrets.evaluator.CIVmSecretEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;

@OwnedBy(CI)
public class VmStepSerializerTest extends CategoryTest {
  @Mock private CISweepingOutputEvaluator ciSweepingOutputEvaluator;
  @Mock private IACMStepsUtils iacmStepsUtils;
  @Mock private CIFeatureFlagService featureFlagService;
  @Mock private IACMUtils iacmUtils;
  @Mock private VmStepInfo vmStepInfo;
  @Mock private VmPluginCompatibleStepSerializer vmPluginCompatibleStepSerializer;

  @InjectMocks private VmStepSerializer vmStepSerializer;

  private static final String ACCOUNT_ID = "testAccountId";
  private static final String PROJECT_ID = "testProjectId";
  private static final String ORG_ID = "testOrgId";
  private static final long EXPRESSION_FUNCTOR_TOKEN = 12345L;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetStepSecrets_WhenFFDisabledAndPipelineNotV1_ShouldNotResolveOutputSecrets() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);

    Set<String> baseSecrets = new HashSet<>();
    baseSecrets.add("secret1");
    baseSecrets.add("secret2");

    // Mock feature flags - all disabled except required ones
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_OUTPUT_SECRETS, ACCOUNT_ID)).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.CODE_ENABLED, ACCOUNT_ID)).thenReturn(false);
    mockSecretEvaluators(baseSecrets);

    try (MockedConstruction<CIVmSecretEvaluator> mockedConstruction =
             mockConstruction(CIVmSecretEvaluator.class, (mock, context) -> {
               when(mock.resolve(any(VmStepInfo.class), any(NGAccess.class), anyLong(), anyBoolean(), anyBoolean()))
                   .thenReturn(baseSecrets);
             })) {
      // Act
      Set<String> result = vmStepSerializer.getStepSecrets(vmStepInfo, ambiance);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result).containsExactlyInAnyOrder("secret1", "secret2");

      // Verify CIVmSecretEvaluator was constructed once
      assertThat(mockedConstruction.constructed()).hasSize(1);

      // Verify ciSweepingOutputEvaluator.resolve() was NOT called
      verify(ciSweepingOutputEvaluator, never()).resolve(any(VmStepInfo.class), anyBoolean());
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetStepSecrets_WhenFFEnabledAndPipelineV0_ShouldResolveOutputSecrets() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);

    Set<String> baseSecrets = new HashSet<>();
    baseSecrets.add("secret1");
    baseSecrets.add("secret2");

    Set<String> outputSecrets = new HashSet<>();
    outputSecrets.add("outputSecret1");
    outputSecrets.add("outputSecret2");

    // Mock feature flags - CI_ENABLE_OUTPUT_SECRETS enabled
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_OUTPUT_SECRETS, ACCOUNT_ID)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CODE_ENABLED, ACCOUNT_ID)).thenReturn(false);

    // Mock the return values for secret evaluators
    mockSecretEvaluators(baseSecrets);
    when(ciSweepingOutputEvaluator.resolve(vmStepInfo, true)).thenReturn(outputSecrets);

    try (MockedConstruction<CIVmSecretEvaluator> mockedConstruction =
             mockConstruction(CIVmSecretEvaluator.class, (mock, context) -> {
               when(mock.resolve(any(VmStepInfo.class), any(NGAccess.class), anyLong(), anyBoolean(), anyBoolean()))
                   .thenReturn(baseSecrets);
             })) {
      // Act
      Set<String> result = vmStepSerializer.getStepSecrets(vmStepInfo, ambiance);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result).hasSize(4);
      assertThat(result).containsExactlyInAnyOrder("secret1", "secret2", "outputSecret1", "outputSecret2");

      // Verify CIVmSecretEvaluator was constructed once
      assertThat(mockedConstruction.constructed()).hasSize(1);

      // Verify ciSweepingOutputEvaluator.resolve() was called
      verify(ciSweepingOutputEvaluator, times(1)).resolve(eq(vmStepInfo), eq(true));
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetStepSecrets_WhenFFDisabledAndPipelineV1_ShouldResolveOutputSecrets() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V1);

    Set<String> baseSecrets = new HashSet<>();
    baseSecrets.add("secret1");
    baseSecrets.add("secret2");

    Set<String> outputSecrets = new HashSet<>();
    outputSecrets.add("outputSecret1");
    outputSecrets.add("outputSecret2");

    // Mock feature flags - CI_ENABLE_OUTPUT_SECRETS disabled
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_OUTPUT_SECRETS, ACCOUNT_ID)).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.CODE_ENABLED, ACCOUNT_ID)).thenReturn(false);

    // Mock the return values for secret evaluators
    mockSecretEvaluators(baseSecrets);
    when(ciSweepingOutputEvaluator.resolve(vmStepInfo, true)).thenReturn(outputSecrets);

    try (MockedConstruction<CIVmSecretEvaluator> mockedConstruction =
             mockConstruction(CIVmSecretEvaluator.class, (mock, context) -> {
               when(mock.resolve(any(VmStepInfo.class), any(NGAccess.class), anyLong(), anyBoolean(), anyBoolean()))
                   .thenReturn(baseSecrets);
             })) {
      // Act
      Set<String> result = vmStepSerializer.getStepSecrets(vmStepInfo, ambiance);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result).hasSize(4);
      assertThat(result).containsExactlyInAnyOrder("secret1", "secret2", "outputSecret1", "outputSecret2");

      // Verify CIVmSecretEvaluator was constructed once
      assertThat(mockedConstruction.constructed()).hasSize(1);

      // Verify ciSweepingOutputEvaluator.resolve() was called
      verify(ciSweepingOutputEvaluator, times(1)).resolve(eq(vmStepInfo), eq(true));
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetStepSecrets_WhenFFEnabledAndPipelineV1_ShouldResolveOutputSecrets() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V1);

    Set<String> baseSecrets = new HashSet<>();
    baseSecrets.add("secret1");
    baseSecrets.add("secret2");

    Set<String> outputSecrets = new HashSet<>();
    outputSecrets.add("outputSecret1");
    outputSecrets.add("outputSecret2");

    // Mock feature flags - both enabled
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_OUTPUT_SECRETS, ACCOUNT_ID)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CODE_ENABLED, ACCOUNT_ID)).thenReturn(false);

    // Mock the return values for secret evaluators
    mockSecretEvaluators(baseSecrets);
    when(ciSweepingOutputEvaluator.resolve(vmStepInfo, true)).thenReturn(outputSecrets);

    try (MockedConstruction<CIVmSecretEvaluator> mockedConstruction =
             mockConstruction(CIVmSecretEvaluator.class, (mock, context) -> {
               when(mock.resolve(any(VmStepInfo.class), any(NGAccess.class), anyLong(), anyBoolean(), anyBoolean()))
                   .thenReturn(baseSecrets);
             })) {
      // Act
      Set<String> result = vmStepSerializer.getStepSecrets(vmStepInfo, ambiance);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result).hasSize(4);
      assertThat(result).containsExactlyInAnyOrder("secret1", "secret2", "outputSecret1", "outputSecret2");

      // Verify CIVmSecretEvaluator was constructed once
      assertThat(mockedConstruction.constructed()).hasSize(1);

      // Verify ciSweepingOutputEvaluator.resolve() was called
      verify(ciSweepingOutputEvaluator, times(1)).resolve(eq(vmStepInfo), eq(true));
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetStepSecrets_WhenOutputSecretsResolverThrowsException_ShouldHandleGracefully() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V1);

    Set<String> baseSecrets = new HashSet<>();
    baseSecrets.add("secret1");
    baseSecrets.add("secret2");

    // Mock feature flags
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_OUTPUT_SECRETS, ACCOUNT_ID)).thenReturn(false);
    when(featureFlagService.isEnabled(FeatureName.CODE_ENABLED, ACCOUNT_ID)).thenReturn(false);

    // Mock the return values for secret evaluators
    mockSecretEvaluators(baseSecrets);

    // Mock ciSweepingOutputEvaluator to throw exception
    when(ciSweepingOutputEvaluator.resolve(vmStepInfo, true))
        .thenThrow(new RuntimeException("Error resolving output secrets"));

    try (MockedConstruction<CIVmSecretEvaluator> mockedConstruction =
             mockConstruction(CIVmSecretEvaluator.class, (mock, context) -> {
               when(mock.resolve(any(VmStepInfo.class), any(NGAccess.class), anyLong(), anyBoolean(), anyBoolean()))
                   .thenReturn(baseSecrets);
             })) {
      // Act
      Set<String> result = vmStepSerializer.getStepSecrets(vmStepInfo, ambiance);

      // Assert - should still return base secrets despite exception
      assertThat(result).isNotNull();
      assertThat(result).containsExactlyInAnyOrder("secret1", "secret2");

      // Verify CIVmSecretEvaluator was constructed once
      assertThat(mockedConstruction.constructed()).hasSize(1);
    }
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetStepSecrets_WithSkipNonExpressionEvaluationEnabled_ShouldPassTrueToEvaluators() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V1);

    Set<String> baseSecrets = new HashSet<>();
    baseSecrets.add("secret1");

    Set<String> outputSecrets = new HashSet<>();
    outputSecrets.add("outputSecret1");

    // Mock feature flags
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_OUTPUT_SECRETS, ACCOUNT_ID)).thenReturn(true);
    when(featureFlagService.isEnabled(FeatureName.CODE_ENABLED, ACCOUNT_ID)).thenReturn(false);

    // Mock the return values for secret evaluators
    mockSecretEvaluators(baseSecrets);
    when(ciSweepingOutputEvaluator.resolve(vmStepInfo, true)).thenReturn(outputSecrets);

    try (MockedConstruction<CIVmSecretEvaluator> mockedConstruction =
             mockConstruction(CIVmSecretEvaluator.class, (mock, context) -> {
               when(mock.resolve(any(VmStepInfo.class), any(NGAccess.class), anyLong(), anyBoolean(), eq(true)))
                   .thenReturn(baseSecrets);
             })) {
      // Act
      Set<String> result = vmStepSerializer.getStepSecrets(vmStepInfo, ambiance);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result).hasSize(2);
      assertThat(result).containsExactlyInAnyOrder("secret1", "outputSecret1");

      // Verify CIVmSecretEvaluator was constructed and resolve was called with skipNonExpressionEvaluation=true
      assertThat(mockedConstruction.constructed()).hasSize(1);
      CIVmSecretEvaluator mockEvaluator = mockedConstruction.constructed().get(0);
      verify(mockEvaluator)
          .resolve(eq(vmStepInfo), any(NGAccess.class), eq(EXPRESSION_FUNCTOR_TOKEN), anyBoolean(), eq(true));

      // Verify ciSweepingOutputEvaluator.resolve() was called with skipNonExpressionEvaluation=true
      verify(ciSweepingOutputEvaluator, times(1)).resolve(eq(vmStepInfo), eq(true));
    }
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testPreProcessStep_WithDockerStepType_ShouldDelegateToPluginCompatibleStepSerializer() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);
    PluginCompatibleStep pluginCompatibleStep = buildPluginCompatibleStep(CIStepInfoType.DOCKER);
    StageInfraDetails stageInfraDetails = buildDliteVmStageInfraDetails();
    String identifier = "dockerStep1";
    String osType = "Linux";

    Set<String> expectedSecrets = new HashSet<>();
    expectedSecrets.add("dlcSecret1");
    expectedSecrets.add("dlcSecret2");

    when(vmPluginCompatibleStepSerializer.preProcessStep(
             ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType))
        .thenReturn(expectedSecrets);

    // Act
    Set<String> result =
        vmStepSerializer.preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result).containsExactlyInAnyOrder("dlcSecret1", "dlcSecret2");
    verify(vmPluginCompatibleStepSerializer, times(1))
        .preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testPreProcessStep_WithEcrStepType_ShouldDelegateToPluginCompatibleStepSerializer() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);
    PluginCompatibleStep pluginCompatibleStep = buildPluginCompatibleStep(CIStepInfoType.ECR);
    StageInfraDetails stageInfraDetails = buildVmStageInfraDetails();
    String identifier = "ecrStep1";
    String osType = "Linux";

    Set<String> expectedSecrets = new HashSet<>();
    expectedSecrets.add("ecrSecret");

    when(vmPluginCompatibleStepSerializer.preProcessStep(
             ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType))
        .thenReturn(expectedSecrets);

    // Act
    Set<String> result =
        vmStepSerializer.preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result).containsExactlyInAnyOrder("ecrSecret");
    verify(vmPluginCompatibleStepSerializer, times(1))
        .preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testPreProcessStep_WithGcrStepType_ShouldDelegateToPluginCompatibleStepSerializer() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);
    PluginCompatibleStep pluginCompatibleStep = buildPluginCompatibleStep(CIStepInfoType.GCR);
    StageInfraDetails stageInfraDetails = buildDliteVmStageInfraDetails();
    String identifier = "gcrStep1";
    String osType = "Windows";

    Set<String> expectedSecrets = new HashSet<>();
    expectedSecrets.add("gcrSecret");

    when(vmPluginCompatibleStepSerializer.preProcessStep(
             ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType))
        .thenReturn(expectedSecrets);

    // Act
    Set<String> result =
        vmStepSerializer.preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).containsExactlyInAnyOrder("gcrSecret");
    verify(vmPluginCompatibleStepSerializer, times(1))
        .preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testPreProcessStep_WithGarStepType_ShouldDelegateToPluginCompatibleStepSerializer() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);
    PluginCompatibleStep pluginCompatibleStep = buildPluginCompatibleStep(CIStepInfoType.GAR);
    StageInfraDetails stageInfraDetails = buildVmStageInfraDetails();
    String identifier = "garStep1";
    String osType = "Linux";

    Set<String> expectedSecrets = new HashSet<>();
    expectedSecrets.add("garSecret1");
    expectedSecrets.add("garSecret2");

    when(vmPluginCompatibleStepSerializer.preProcessStep(
             ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType))
        .thenReturn(expectedSecrets);

    // Act
    Set<String> result =
        vmStepSerializer.preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result).containsExactlyInAnyOrder("garSecret1", "garSecret2");
    verify(vmPluginCompatibleStepSerializer, times(1))
        .preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testPreProcessStep_WithAcrStepType_ShouldDelegateToPluginCompatibleStepSerializer() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);
    PluginCompatibleStep pluginCompatibleStep = buildPluginCompatibleStep(CIStepInfoType.ACR);
    StageInfraDetails stageInfraDetails = buildDliteVmStageInfraDetails();
    String identifier = "acrStep1";
    String osType = "Linux";

    Set<String> expectedSecrets = new HashSet<>();
    expectedSecrets.add("acrSecret");

    when(vmPluginCompatibleStepSerializer.preProcessStep(
             ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType))
        .thenReturn(expectedSecrets);

    // Act
    Set<String> result =
        vmStepSerializer.preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).containsExactlyInAnyOrder("acrSecret");
    verify(vmPluginCompatibleStepSerializer, times(1))
        .preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testPreProcessStep_WithRunStepType_ShouldReturnEmptySet() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);
    CIStepInfo stepInfo = buildCIStepInfo(CIStepInfoType.RUN);
    StageInfraDetails stageInfraDetails = buildVmStageInfraDetails();
    String identifier = "runStep1";
    String osType = "Linux";

    // Act
    Set<String> result = vmStepSerializer.preProcessStep(ambiance, stepInfo, stageInfraDetails, identifier, osType);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
    verify(vmPluginCompatibleStepSerializer, never()).preProcessStep(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testPreProcessStep_WithPluginStepType_ShouldReturnEmptySet() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);
    CIStepInfo stepInfo = buildCIStepInfo(CIStepInfoType.PLUGIN);
    StageInfraDetails stageInfraDetails = buildDliteVmStageInfraDetails();
    String identifier = "pluginStep1";
    String osType = "Linux";

    // Act
    Set<String> result = vmStepSerializer.preProcessStep(ambiance, stepInfo, stageInfraDetails, identifier, osType);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
    verify(vmPluginCompatibleStepSerializer, never()).preProcessStep(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testPreProcessStep_WithDockerStepType_WhenDelegateReturnsEmpty_ShouldReturnEmptySet() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);
    PluginCompatibleStep pluginCompatibleStep = buildPluginCompatibleStep(CIStepInfoType.DOCKER);
    StageInfraDetails stageInfraDetails = buildVmStageInfraDetails();
    String identifier = "dockerStep1";
    String osType = "Linux";

    when(vmPluginCompatibleStepSerializer.preProcessStep(
             ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType))
        .thenReturn(new HashSet<>());

    // Act
    Set<String> result =
        vmStepSerializer.preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
    verify(vmPluginCompatibleStepSerializer, times(1))
        .preProcessStep(ambiance, pluginCompatibleStep, stageInfraDetails, identifier, osType);
  }

  @Test
  @Owner(developers = SAURABH)
  @Category(UnitTests.class)
  public void testPreProcessStep_WithBackgroundStepType_ShouldReturnEmptySet() {
    // Arrange
    Ambiance ambiance = buildAmbiance(HarnessYamlVersion.V0);
    CIStepInfo stepInfo = buildCIStepInfo(CIStepInfoType.BACKGROUND);
    StageInfraDetails stageInfraDetails = buildVmStageInfraDetails();
    String identifier = "backgroundStep1";
    String osType = "Linux";

    // Act
    Set<String> result = vmStepSerializer.preProcessStep(ambiance, stepInfo, stageInfraDetails, identifier, osType);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
    verify(vmPluginCompatibleStepSerializer, never()).preProcessStep(any(), any(), any(), any(), any());
  }

  private PluginCompatibleStep buildPluginCompatibleStep(CIStepInfoType stepInfoType) {
    PluginCompatibleStep step = org.mockito.Mockito.mock(PluginCompatibleStep.class);
    TypeInfo typeInfo = TypeInfo.builder()
                            .stepInfoType(stepInfoType)
                            .stepType(StepType.newBuilder().setType(stepInfoType.getDisplayName()).build())
                            .build();
    when(step.getNonYamlInfo()).thenReturn(typeInfo);
    return step;
  }

  private CIStepInfo buildCIStepInfo(CIStepInfoType stepInfoType) {
    CIStepInfo stepInfo = org.mockito.Mockito.mock(CIStepInfo.class);
    TypeInfo typeInfo = TypeInfo.builder()
                            .stepInfoType(stepInfoType)
                            .stepType(StepType.newBuilder().setType(stepInfoType.getDisplayName()).build())
                            .build();
    when(stepInfo.getNonYamlInfo()).thenReturn(typeInfo);
    return stepInfo;
  }

  private StageInfraDetails buildDliteVmStageInfraDetails() {
    DliteVmStageInfraDetails details = org.mockito.Mockito.mock(DliteVmStageInfraDetails.class);
    when(details.getType()).thenReturn(StageInfraDetails.Type.DLITE_VM);
    return details;
  }

  private StageInfraDetails buildVmStageInfraDetails() {
    VmStageInfraDetails details = org.mockito.Mockito.mock(VmStageInfraDetails.class);
    when(details.getType()).thenReturn(StageInfraDetails.Type.VM);
    return details;
  }

  private Ambiance buildAmbiance(String harnessVersion) {
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(
            Maps.of("accountId", ACCOUNT_ID, "projectIdentifier", PROJECT_ID, "orgIdentifier", ORG_ID))
        .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(harnessVersion).build())
        .setExpressionFunctorToken(EXPRESSION_FUNCTOR_TOKEN)
        .build();
  }

  private void mockSecretEvaluators(Set<String> baseSecrets) {
    // Note: CIVmSecretEvaluator.builder().build() creates a new instance each time,
    // so we can't directly mock it. However, we can ensure the mocks return empty sets
    // for the utility methods that are always called.
    when(iacmUtils.getHarnessCodeTokenSecret(vmStepInfo)).thenReturn(new ArrayList<>());
    when(iacmStepsUtils.getDroneTerraformSecrets(vmStepInfo)).thenReturn(Collections.emptySet());
  }
}