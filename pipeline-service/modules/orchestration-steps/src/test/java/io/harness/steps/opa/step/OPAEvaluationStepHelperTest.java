/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.expression.EngineExpressionService;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.Evaluation;
import io.harness.opaclient.model.PolicyData;
import io.harness.opaclient.model.PolicySetData;
import io.harness.opaclient.model.SignedUrlPayload;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class OPAEvaluationStepHelperTest extends CategoryTest {
  @Mock private OpaServiceClient opaServiceClient;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private ServiceTokenGenerator tokenGenerator;
  @Mock private PipelineExpressionHelper pipelineExpressionHelper;
  @Mock private PipelineServiceConfiguration pipelineServiceConfiguration;

  @InjectMocks private OPAEvaluationStepHelper opaEvaluationStepHelper;

  private static final String ACCOUNT_ID = "account-id";
  private static final String ORG_ID = "org-id";
  private static final String PROJECT_ID = "project-id";
  private static final String POLICY_SET_ID = "policy-set-id";
  private static final String EVALUATION_ID = "evaluation-id";
  private static final String GCS_SIGNED_URL = "https://storage.googleapis.com/test-bucket/payload.json";

  private Ambiance ambiance;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);

    // Set String fields using reflection since they can't be mocked
    java.lang.reflect.Field baseUrlField = OPAEvaluationStepHelper.class.getDeclaredField("opaServiceBaseUrl");
    baseUrlField.setAccessible(true);
    baseUrlField.set(opaEvaluationStepHelper, "https://opa-service.example.com");

    java.lang.reflect.Field secretField = OPAEvaluationStepHelper.class.getDeclaredField("opaServiceSecret");
    secretField.setAccessible(true);
    secretField.set(opaEvaluationStepHelper, "test-secret");

    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", ACCOUNT_ID)
                   .putSetupAbstractions("orgIdentifier", ORG_ID)
                   .putSetupAbstractions("projectIdentifier", PROJECT_ID)
                   .build();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testFetchPolicySetSuccess() throws Exception {
    PolicySetData policySetData = createPolicySetData();

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<PolicySetData> call = Mockito.mock(Call.class);
      when(opaServiceClient.findOpaPolicySet(anyString(), anyString(), anyString(), anyString())).thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithExceptions(any(Call.class))).thenReturn(policySetData);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      PolicySetData result = opaEvaluationStepHelper.fetchPolicySet(ambiance, POLICY_SET_ID, ORG_ID, PROJECT_ID);

      assertThat(result).isNotNull();
      assertThat(result.getIdentifier()).isEqualTo(POLICY_SET_ID);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testFetchPolicySetWithEmptyPolicySetId() {
    assertThatThrownBy(() -> opaEvaluationStepHelper.fetchPolicySet(ambiance, "", ORG_ID, PROJECT_ID))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Policy Set ID cannot be empty");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPayloadGcsSignedUrlSuccess() throws Exception {
    SignedUrlPayload signedUrlPayload = SignedUrlPayload.builder().signedUrl(GCS_SIGNED_URL).build();

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<SignedUrlPayload> call = Mockito.mock(Call.class);
      when(opaServiceClient.getPayloadSignedUrl(anyString(), anyString())).thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithExceptions(any(Call.class))).thenReturn(signedUrlPayload);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      String result = opaEvaluationStepHelper.getPayloadGcsSignedUrl(ambiance, EVALUATION_ID);

      assertThat(result).isEqualTo(GCS_SIGNED_URL);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPayloadGcsSignedUrlWithEmptyEvaluationId() {
    assertThatThrownBy(() -> opaEvaluationStepHelper.getPayloadGcsSignedUrl(ambiance, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Evaluation ID cannot be empty");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildEnvironmentVariables() {
    try (MockedStatic<SecurityContextBuilder> securityContextBuilderMock =
             Mockito.mockStatic(SecurityContextBuilder.class)) {
      securityContextBuilderMock.when(SecurityContextBuilder::getPrincipal).thenReturn(null);
      when(tokenGenerator.getServiceTokenWithDuration(anyString(), any(Duration.class), any()))
          .thenReturn("test-token");
      when(pipelineServiceConfiguration.getPipelineServiceBaseUrl()).thenReturn("https://app.harness.io/ng/#");
      when(pipelineExpressionHelper.getBaseUrlWithVanitySupport(anyString())).thenReturn("https://app.harness.io/ng/#");

      Map<String, String> envVars = opaEvaluationStepHelper.buildEnvironmentVariables(
          ambiance, POLICY_SET_ID, EVALUATION_ID, GCS_SIGNED_URL, "{}", ORG_ID, PROJECT_ID);

      assertThat(envVars).isNotNull();
      assertThat(envVars.get(OPAEvaluationStepHelper.PLUGIN_POLICY_SET_ID)).isEqualTo(POLICY_SET_ID);
      assertThat(envVars.get(OPAEvaluationStepHelper.PLUGIN_EVALUATION_ID)).isEqualTo(EVALUATION_ID);
      assertThat(envVars.get(OPAEvaluationStepHelper.PLUGIN_GCS_PAYLOAD_SIGNED_URL)).isEqualTo(GCS_SIGNED_URL);
      assertThat(envVars.get(OPAEvaluationStepHelper.PLUGIN_ACCOUNT_ID)).isEqualTo(ACCOUNT_ID);
      assertThat(envVars.get(OPAEvaluationStepHelper.PLUGIN_ORG_IDENTIFIER)).isEqualTo(ORG_ID);
      assertThat(envVars.get(OPAEvaluationStepHelper.PLUGIN_PROJECT_IDENTIFIER)).isEqualTo(PROJECT_ID);
    }
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testBuildEnvironmentVariablesMintsTokenWithPrincipalFromAmbiance() {
    SecurityContextBuilder.unsetCompleteContext();
    ArgumentCaptor<Principal> principalCaptor = ArgumentCaptor.forClass(Principal.class);
    when(tokenGenerator.getServiceTokenWithDuration(anyString(), any(Duration.class), any())).thenReturn("test-token");
    when(pipelineServiceConfiguration.getPipelineServiceBaseUrl()).thenReturn("https://app.harness.io/ng/#");
    when(pipelineExpressionHelper.getBaseUrlWithVanitySupport(anyString())).thenReturn("https://app.harness.io/ng/#");

    opaEvaluationStepHelper.buildEnvironmentVariables(
        ambiance, POLICY_SET_ID, EVALUATION_ID, GCS_SIGNED_URL, "{}", ORG_ID, PROJECT_ID);

    Mockito.verify(tokenGenerator)
        .getServiceTokenWithDuration(anyString(), any(Duration.class), principalCaptor.capture());
    assertThat(principalCaptor.getValue()).isInstanceOf(ServicePrincipal.class);
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildEnvironmentVariablesWithNullOrgAndProject() {
    try (MockedStatic<SecurityContextBuilder> securityContextBuilderMock =
             Mockito.mockStatic(SecurityContextBuilder.class)) {
      securityContextBuilderMock.when(SecurityContextBuilder::getPrincipal).thenReturn(null);
      when(tokenGenerator.getServiceTokenWithDuration(anyString(), any(Duration.class), any()))
          .thenReturn("test-token");
      when(pipelineServiceConfiguration.getPipelineServiceBaseUrl()).thenReturn("https://app.harness.io/ng/#");
      when(pipelineExpressionHelper.getBaseUrlWithVanitySupport(anyString())).thenReturn("https://app.harness.io/ng/#");

      Map<String, String> envVars = opaEvaluationStepHelper.buildEnvironmentVariables(
          ambiance, POLICY_SET_ID, EVALUATION_ID, GCS_SIGNED_URL, "{}", null, null);

      assertThat(envVars).isNotNull();
      assertThat(envVars.get(OPAEvaluationStepHelper.PLUGIN_ORG_IDENTIFIER)).isNull();
      assertThat(envVars.get(OPAEvaluationStepHelper.PLUGIN_PROJECT_IDENTIFIER)).isNull();
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testConvertPolicySetDataToJsonString() throws Exception {
    PolicySetData policySetData = createPolicySetData();
    policySetData.setDescription("Test policy set description");

    when(engineExpressionService.resolve(any(Ambiance.class), any(PolicySetData.class),
             any(io.harness.expression.common.ExpressionMode.class), any()))
        .thenReturn(policySetData);

    Pair<String, Set<String>> result =
        opaEvaluationStepHelper.convertPolicySetDataToJsonString(ambiance, policySetData, false);

    assertThat(result).isNotNull();
    assertThat(result.getLeft()).isNotNull();
    assertThat(result.getLeft()).contains(POLICY_SET_ID);
    assertThat(result.getLeft()).contains("Test policy set description");
    // No single-quote rewrite path -> empty secret set
    assertThat(result.getRight()).isEmpty();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testConvertPolicySetDataToJsonStringWithNullAmbiance() {
    PolicySetData policySetData = createPolicySetData();
    assertThatThrownBy(() -> opaEvaluationStepHelper.convertPolicySetDataToJsonString(null, policySetData, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ambiance cannot be null");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testConvertPolicySetDataToJsonStringWithNullPolicySetData() {
    assertThatThrownBy(() -> opaEvaluationStepHelper.convertPolicySetDataToJsonString(ambiance, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Policy set data cannot be null");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetPayloadGcsSignedUrlWithNullPayload() throws Exception {
    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<SignedUrlPayload> call = Mockito.mock(Call.class);
      when(opaServiceClient.getPayloadSignedUrl(anyString(), anyString())).thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithExceptions(any(Call.class))).thenReturn(null);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);

      assertThatThrownBy(() -> opaEvaluationStepHelper.getPayloadGcsSignedUrl(ambiance, EVALUATION_ID))
          .isInstanceOf(io.harness.exception.InternalServerErrorException.class)
          .hasMessageContaining("Failed to get GCS signed URL for evaluation: " + EVALUATION_ID);
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testFetchEvaluationIdFromPlanExecutionId() throws Exception {
    String planExecutionId = "plan-execution-id";
    // Evaluation ID is a Long, so use a numeric value
    Evaluation evaluation = Evaluation.builder().id(12345L).build();

    List<Evaluation> evaluations = Collections.singletonList(evaluation);

    try (MockedStatic<SafeHttpCall> safeHttpCallMock = Mockito.mockStatic(SafeHttpCall.class);
         MockedStatic<AmbianceUtils> ambianceUtilsMock = Mockito.mockStatic(AmbianceUtils.class)) {
      Call<List<Evaluation>> call = Mockito.mock(Call.class);
      when(opaServiceClient.listEvaluationsByExecutionId(
               anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyInt()))
          .thenReturn(call);
      safeHttpCallMock.when(() -> SafeHttpCall.executeWithErrorMessage(any(Call.class))).thenReturn(evaluations);
      ambianceUtilsMock.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn(ACCOUNT_ID);
      ambianceUtilsMock.when(() -> AmbianceUtils.getOrgIdentifier(any(Ambiance.class))).thenReturn(ORG_ID);
      ambianceUtilsMock.when(() -> AmbianceUtils.getProjectIdentifier(any(Ambiance.class))).thenReturn(PROJECT_ID);

      String result = opaEvaluationStepHelper.fetchEvaluationIdFromPlanExecutionId(ambiance, planExecutionId);

      assertThat(result).isEqualTo("12345");
    }
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testFetchEvaluationIdFromPlanExecutionIdWithEmptyPlanExecutionId() {
    assertThatThrownBy(() -> opaEvaluationStepHelper.fetchEvaluationIdFromPlanExecutionId(ambiance, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Plan Execution ID cannot be empty");
  }

  private PolicySetData createPolicySetData() {
    PolicySetData policySetData = PolicySetData.builder().identifier(POLICY_SET_ID).build();

    PolicyData policy1 = PolicyData.builder()
                             .identifier("policy1")
                             .rego("package test\n\ndefault allow = false")
                             .severity("error")
                             .build();

    List<PolicyData> policies = new ArrayList<>();
    policies.add(policy1);
    policySetData.setPolicies(policies);

    return policySetData;
  }
}
