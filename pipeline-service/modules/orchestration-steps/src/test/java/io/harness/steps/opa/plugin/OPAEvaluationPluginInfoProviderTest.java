/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa.plugin;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.expression.EngineExpressionService;
import io.harness.opaclient.model.PolicySetData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.opa.OPAEvaluationStepInfo;
import io.harness.steps.opa.OPAEvaluationStepNode;
import io.harness.steps.opa.step.OPAEvaluationStepHelper;
import io.harness.yaml.extended.ci.container.ContainerResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class OPAEvaluationPluginInfoProviderTest extends CategoryTest {
  @InjectMocks private OPAEvaluationPluginInfoProvider opaEvaluationPluginInfoProvider;
  @Mock private OPAEvaluationStepHelper opaEvaluationStepHelper;
  @Mock private SecretUtils secretUtils;
  @Mock private EngineExpressionService engineExpressionService;

  private static final String STEP_IDENTIFIER = "opa_eval_step";
  private static final String STEP_NAME = "OPA Evaluation Step";
  private static final String STEP_UUID = "step-uuid-123";
  private static final String IMAGE = "harness/opa-evaluation-plugin:latest";
  private static final String CONNECTOR_REF = "connector.ref";
  private static final String PLAN_EXECUTION_ID = "plan-execution-id";
  private static final String ACCOUNT_ID = "account-id";

  private Ambiance ambiance;
  private PluginCreationRequest request;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                   .setExpressionFunctorToken(12345L)
                   .build();

    // Mock fetchPolicySet to return empty PolicySetData (no secrets to extract)
    PolicySetData emptyPolicySetData = PolicySetData.builder().policies(new ArrayList<>()).build();
    Mockito
        .when(opaEvaluationStepHelper.fetchPolicySet(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any()))
        .thenReturn(emptyPolicySetData);

    // Mock engineExpressionService.resolve to return the input string as-is
    Mockito.when(engineExpressionService.resolve(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenAnswer(invocation -> invocation.getArgument(1)); // Return the second argument (the rego string)

    String stepJson = createStepJson();
    request = PluginCreationRequest.newBuilder()
                  .setType(StepSpecTypeConstants.OPA_EVALUATION)
                  .setStepJsonNode(stepJson)
                  .build();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testIsSupported() {
    assertThat(opaEvaluationPluginInfoProvider.isSupported(StepSpecTypeConstants.OPA_EVALUATION)).isTrue();
    assertThat(opaEvaluationPluginInfoProvider.isSupported("OtherStep")).isFalse();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPluginInfoSuccess() throws IOException {
    Set<Integer> usedPorts = new HashSet<>(Collections.singletonList(8080));

    try (MockedStatic<io.harness.ci.utils.PortFinder> portFinderMock =
             Mockito.mockStatic(io.harness.ci.utils.PortFinder.class)) {
      io.harness.ci.utils.PortFinder.PortFinderBuilder portFinderBuilder =
          Mockito.mock(io.harness.ci.utils.PortFinder.PortFinderBuilder.class);
      io.harness.ci.utils.PortFinder portFinder = Mockito.mock(io.harness.ci.utils.PortFinder.class);

      portFinderMock.when(() -> io.harness.ci.utils.PortFinder.builder()).thenReturn(portFinderBuilder);
      when(portFinderBuilder.startingPort(anyInt())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.usedPorts(anySet())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.build()).thenReturn(portFinder);
      when(portFinder.getNextPort()).thenReturn(8081);

      PluginCreationResponseWrapper response =
          opaEvaluationPluginInfoProvider.getPluginInfo(request, usedPorts, ambiance);

      assertThat(response).isNotNull();
      assertThat(response.getResponse()).isNotNull();
      assertThat(response.getStepInfo()).isNotNull();
      assertThat(response.getStepInfo().getIdentifier()).isEqualTo(STEP_IDENTIFIER);
      assertThat(response.getStepInfo().getName()).isEqualTo(STEP_NAME);
      assertThat(response.getStepInfo().getUuid()).isEqualTo(STEP_UUID);

      assertThat(response.getResponse().getPluginDetails()).isNotNull();
      assertThat(response.getResponse().getPluginDetails().getImageDetails()).isNotNull();
      assertThat(response.getResponse().getPluginDetails().getImageDetails().getImageInformation()).isNotNull();
      assertThat(
          response.getResponse().getPluginDetails().getImageDetails().getImageInformation().getImageName().getValue())
          .isEqualTo(IMAGE);
      // Verify isHarnessManaged is set to false (OPA uses user-provided images)
      assertThat(response.getResponse().getPluginDetails().getIsHarnessManaged().getValue()).isFalse();
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPluginInfoWithInvalidJson() {
    PluginCreationRequest invalidRequest = PluginCreationRequest.newBuilder()
                                               .setType(StepSpecTypeConstants.OPA_EVALUATION)
                                               .setStepJsonNode("invalid json")
                                               .build();

    Set<Integer> usedPorts = new HashSet<>();

    assertThatThrownBy(() -> opaEvaluationPluginInfoProvider.getPluginInfo(invalidRequest, usedPorts, ambiance))
        .isInstanceOf(ContainerPluginParseException.class);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPluginInfoWithResources() throws IOException {
    Set<Integer> usedPorts = new HashSet<>();

    String stepJsonWithResources = createStepJsonWithResources();
    PluginCreationRequest requestWithResources = PluginCreationRequest.newBuilder()
                                                     .setType(StepSpecTypeConstants.OPA_EVALUATION)
                                                     .setStepJsonNode(stepJsonWithResources)
                                                     .build();

    try (MockedStatic<io.harness.ci.utils.PortFinder> portFinderMock =
             Mockito.mockStatic(io.harness.ci.utils.PortFinder.class)) {
      io.harness.ci.utils.PortFinder.PortFinderBuilder portFinderBuilder =
          Mockito.mock(io.harness.ci.utils.PortFinder.PortFinderBuilder.class);
      io.harness.ci.utils.PortFinder portFinder = Mockito.mock(io.harness.ci.utils.PortFinder.class);

      portFinderMock.when(() -> io.harness.ci.utils.PortFinder.builder()).thenReturn(portFinderBuilder);
      when(portFinderBuilder.startingPort(anyInt())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.usedPorts(anySet())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.build()).thenReturn(portFinder);
      when(portFinder.getNextPort()).thenReturn(8081);

      PluginCreationResponseWrapper response =
          opaEvaluationPluginInfoProvider.getPluginInfo(requestWithResources, usedPorts, ambiance);

      assertThat(response).isNotNull();
      assertThat(response.getResponse().getPluginDetails().getResource()).isNotNull();
      assertThat(response.getResponse().getPluginDetails().getResource().getCpu()).isEqualTo(1000);
      assertThat(response.getResponse().getPluginDetails().getResource().getMemory()).isEqualTo(512);
      // Verify isHarnessManaged is set to false (OPA uses user-provided images)
      assertThat(response.getResponse().getPluginDetails().getIsHarnessManaged().getValue()).isFalse();
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPluginInfoWithEnvVariables() throws IOException {
    Set<Integer> usedPorts = new HashSet<>();

    String stepJsonWithEnvVars = createStepJsonWithEnvVars();
    PluginCreationRequest requestWithEnvVars = PluginCreationRequest.newBuilder()
                                                   .setType(StepSpecTypeConstants.OPA_EVALUATION)
                                                   .setStepJsonNode(stepJsonWithEnvVars)
                                                   .build();

    try (MockedStatic<io.harness.ci.utils.PortFinder> portFinderMock =
             Mockito.mockStatic(io.harness.ci.utils.PortFinder.class)) {
      io.harness.ci.utils.PortFinder.PortFinderBuilder portFinderBuilder =
          Mockito.mock(io.harness.ci.utils.PortFinder.PortFinderBuilder.class);
      io.harness.ci.utils.PortFinder portFinder = Mockito.mock(io.harness.ci.utils.PortFinder.class);

      portFinderMock.when(() -> io.harness.ci.utils.PortFinder.builder()).thenReturn(portFinderBuilder);
      when(portFinderBuilder.startingPort(anyInt())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.usedPorts(anySet())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.build()).thenReturn(portFinder);
      when(portFinder.getNextPort()).thenReturn(8081);

      PluginCreationResponseWrapper response =
          opaEvaluationPluginInfoProvider.getPluginInfo(requestWithEnvVars, usedPorts, ambiance);

      assertThat(response).isNotNull();
      assertThat(response.getResponse().getPluginDetails().getEnvVariablesMap()).isNotEmpty();
      assertThat(response.getResponse().getPluginDetails().getEnvVariablesMap()).containsKey("TEST_VAR");
      assertThat(response.getResponse().getPluginDetails().getEnvVariablesMap().get("TEST_VAR"))
          .isEqualTo("test_value");
      // Verify isHarnessManaged is set to false (OPA uses user-provided images)
      assertThat(response.getResponse().getPluginDetails().getIsHarnessManaged().getValue()).isFalse();
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPluginInfoWithPrivilegedAndRunAsUser() throws IOException {
    Set<Integer> usedPorts = new HashSet<>();

    String stepJsonWithPrivileged = createStepJsonWithPrivileged();
    PluginCreationRequest requestWithPrivileged = PluginCreationRequest.newBuilder()
                                                      .setType(StepSpecTypeConstants.OPA_EVALUATION)
                                                      .setStepJsonNode(stepJsonWithPrivileged)
                                                      .build();

    try (MockedStatic<io.harness.ci.utils.PortFinder> portFinderMock =
             Mockito.mockStatic(io.harness.ci.utils.PortFinder.class)) {
      io.harness.ci.utils.PortFinder.PortFinderBuilder portFinderBuilder =
          Mockito.mock(io.harness.ci.utils.PortFinder.PortFinderBuilder.class);
      io.harness.ci.utils.PortFinder portFinder = Mockito.mock(io.harness.ci.utils.PortFinder.class);

      portFinderMock.when(() -> io.harness.ci.utils.PortFinder.builder()).thenReturn(portFinderBuilder);
      when(portFinderBuilder.startingPort(anyInt())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.usedPorts(anySet())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.build()).thenReturn(portFinder);
      when(portFinder.getNextPort()).thenReturn(8081);

      PluginCreationResponseWrapper response =
          opaEvaluationPluginInfoProvider.getPluginInfo(requestWithPrivileged, usedPorts, ambiance);

      assertThat(response).isNotNull();
      // Privileged mode is now hardcoded to false for OPA evaluation (read-only policy checks)
      assertThat(response.getResponse().getPluginDetails().getPrivileged()).isFalse();
      assertThat(response.getResponse().getPluginDetails().hasRunAsUserV1()).isTrue();
      assertThat(response.getResponse().getPluginDetails().getRunAsUserV1().getValue()).isEqualTo(1000);
      // Verify isHarnessManaged is set to false (OPA uses user-provided images)
      assertThat(response.getResponse().getPluginDetails().getIsHarnessManaged().getValue()).isFalse();
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPluginInfoWithImagePullPolicy() throws IOException {
    Set<Integer> usedPorts = new HashSet<>();

    String stepJsonWithPullPolicy = createStepJsonWithImagePullPolicy();
    PluginCreationRequest requestWithPullPolicy = PluginCreationRequest.newBuilder()
                                                      .setType(StepSpecTypeConstants.OPA_EVALUATION)
                                                      .setStepJsonNode(stepJsonWithPullPolicy)
                                                      .build();

    try (MockedStatic<io.harness.ci.utils.PortFinder> portFinderMock =
             Mockito.mockStatic(io.harness.ci.utils.PortFinder.class)) {
      io.harness.ci.utils.PortFinder.PortFinderBuilder portFinderBuilder =
          Mockito.mock(io.harness.ci.utils.PortFinder.PortFinderBuilder.class);
      io.harness.ci.utils.PortFinder portFinder = Mockito.mock(io.harness.ci.utils.PortFinder.class);

      portFinderMock.when(() -> io.harness.ci.utils.PortFinder.builder()).thenReturn(portFinderBuilder);
      when(portFinderBuilder.startingPort(anyInt())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.usedPorts(anySet())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.build()).thenReturn(portFinder);
      when(portFinder.getNextPort()).thenReturn(8081);

      PluginCreationResponseWrapper response =
          opaEvaluationPluginInfoProvider.getPluginInfo(requestWithPullPolicy, usedPorts, ambiance);

      assertThat(response).isNotNull();
      assertThat(response.getResponse().getPluginDetails().getImageDetails().getImageInformation().getImagePullPolicy())
          .isNotNull();
      // Verify isHarnessManaged is set to false (OPA uses user-provided images)
      assertThat(response.getResponse().getPluginDetails().getIsHarnessManaged().getValue()).isFalse();
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPluginInfoWithNullAmbiance() throws IOException {
    Set<Integer> usedPorts = new HashSet<>();

    try (MockedStatic<io.harness.ci.utils.PortFinder> portFinderMock =
             Mockito.mockStatic(io.harness.ci.utils.PortFinder.class)) {
      io.harness.ci.utils.PortFinder.PortFinderBuilder portFinderBuilder =
          Mockito.mock(io.harness.ci.utils.PortFinder.PortFinderBuilder.class);
      io.harness.ci.utils.PortFinder portFinder = Mockito.mock(io.harness.ci.utils.PortFinder.class);

      portFinderMock.when(() -> io.harness.ci.utils.PortFinder.builder()).thenReturn(portFinderBuilder);
      when(portFinderBuilder.startingPort(anyInt())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.usedPorts(anySet())).thenReturn(portFinderBuilder);
      when(portFinderBuilder.build()).thenReturn(portFinder);
      when(portFinder.getNextPort()).thenReturn(8081);

      PluginCreationResponseWrapper response = opaEvaluationPluginInfoProvider.getPluginInfo(request, usedPorts, null);

      assertThat(response).isNotNull();
      assertThat(response.getResponse()).isNotNull();
      assertThat(response.getStepInfo()).isNotNull();
      // Verify isHarnessManaged is set to false (OPA uses user-provided images)
      assertThat(response.getResponse().getPluginDetails().getIsHarnessManaged().getValue()).isFalse();
    }
  }

  private String createStepJson() {
    try {
      OPAEvaluationStepNode stepNode = new OPAEvaluationStepNode();
      stepNode.setIdentifier(STEP_IDENTIFIER);
      stepNode.setName(STEP_NAME);
      stepNode.setUuid(STEP_UUID);
      stepNode.setOpaEvaluationStepInfo(OPAEvaluationStepInfo.infoBuilder()
                                            .image(ParameterField.createValueField(IMAGE))
                                            .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                            .policySetId(ParameterField.createValueField("policy-set-id"))
                                            .build());

      return YamlUtils.writeYamlString(stepNode);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create step JSON", e);
    }
  }

  private String createStepJsonWithResources() {
    try {
      ContainerResource.Limits limits = ContainerResource.Limits.builder()
                                            .cpu(ParameterField.createValueField("1000m"))
                                            .memory(ParameterField.createValueField("512Mi"))
                                            .build();
      ContainerResource resources = ContainerResource.builder().limits(limits).build();

      OPAEvaluationStepNode stepNode = new OPAEvaluationStepNode();
      stepNode.setIdentifier(STEP_IDENTIFIER);
      stepNode.setName(STEP_NAME);
      stepNode.setUuid(STEP_UUID);
      stepNode.setOpaEvaluationStepInfo(OPAEvaluationStepInfo.infoBuilder()
                                            .image(ParameterField.createValueField(IMAGE))
                                            .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                            .resources(resources)
                                            .policySetId(ParameterField.createValueField("policy-set-id"))
                                            .build());

      return YamlUtils.writeYamlString(stepNode);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create step JSON with resources", e);
    }
  }

  private String createStepJsonWithEnvVars() {
    try {
      Map<String, String> envVars = new HashMap<>();
      envVars.put("TEST_VAR", "test_value");

      OPAEvaluationStepNode stepNode = new OPAEvaluationStepNode();
      stepNode.setIdentifier(STEP_IDENTIFIER);
      stepNode.setName(STEP_NAME);
      stepNode.setUuid(STEP_UUID);
      stepNode.setOpaEvaluationStepInfo(OPAEvaluationStepInfo.infoBuilder()
                                            .image(ParameterField.createValueField(IMAGE))
                                            .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                            .envVariables(ParameterField.createValueField(envVars))
                                            .policySetId(ParameterField.createValueField("policy-set-id"))
                                            .build());

      return YamlUtils.writeYamlString(stepNode);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create step JSON with env vars", e);
    }
  }

  private String createStepJsonWithPrivileged() {
    try {
      OPAEvaluationStepNode stepNode = new OPAEvaluationStepNode();
      stepNode.setIdentifier(STEP_IDENTIFIER);
      stepNode.setName(STEP_NAME);
      stepNode.setUuid(STEP_UUID);
      stepNode.setOpaEvaluationStepInfo(OPAEvaluationStepInfo.infoBuilder()
                                            .image(ParameterField.createValueField(IMAGE))
                                            .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                            .privileged(ParameterField.createValueField(true))
                                            .runAsUser(ParameterField.createValueField(1000))
                                            .policySetId(ParameterField.createValueField("policy-set-id"))
                                            .build());

      return YamlUtils.writeYamlString(stepNode);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create step JSON with privileged", e);
    }
  }

  private String createStepJsonWithImagePullPolicy() {
    try {
      OPAEvaluationStepNode stepNode = new OPAEvaluationStepNode();
      stepNode.setIdentifier(STEP_IDENTIFIER);
      stepNode.setName(STEP_NAME);
      stepNode.setUuid(STEP_UUID);
      stepNode.setOpaEvaluationStepInfo(OPAEvaluationStepInfo.infoBuilder()
                                            .image(ParameterField.createValueField(IMAGE))
                                            .connectorRef(ParameterField.createValueField(CONNECTOR_REF))
                                            .imagePullPolicy(ParameterField.createValueField(ImagePullPolicy.ALWAYS))
                                            .policySetId(ParameterField.createValueField("policy-set-id"))
                                            .build());

      return YamlUtils.writeYamlString(stepNode);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create step JSON with image pull policy", e);
    }
  }
}
