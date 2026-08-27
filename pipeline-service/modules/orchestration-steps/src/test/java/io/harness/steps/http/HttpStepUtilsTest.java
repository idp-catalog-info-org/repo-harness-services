/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.http;

import static io.harness.expression.EngineExpressionEvaluator.PIE_EXECUTION_JSON_SUPPORT;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.delegate.task.http.HttpTaskParametersNg.HttpTaskParametersNgBuilder;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionService;
import io.harness.expression.MaskingExpressionEvaluator;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class HttpStepUtilsTest {
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private MaskingExpressionEvaluator maskingExpressionEvaluator;
  @InjectMocks HttpStepUtils httpStepUtils;
  @Captor private ArgumentCaptor<Map<String, Object>> argCaptor;
  @Mock EngineExpressionService engineExpressionService;
  @Inject private Ambiance ambiance;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private static String certRef = "certRef";
  private static String certKeyRef = "certKeyRef";

  @Before
  public void setup() {
    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    //        Mockito.when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logClient);

    ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", "accountId")
            .setMetadata(
                ExecutionMetadata.newBuilder().putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false).build())
            .build();

    when(pmsFeatureFlagHelper.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testOutputVariablesEvaluation() {
    String body = "{\n"
        + "    \"status\": \"SUCCESS\",\n"
        + "    \"metaData\": \"metadataValue\",\n"
        + "    \"correlationId\": \"333333344444444\"\n"
        + "}";
    HttpStepResponse response1 = HttpStepResponse.builder().httpResponseBody(body).build();
    ParameterField<Object> var1 =
        ParameterField.createExpressionField(true, "<+json.object(httpResponseBody).metaData>", null, true);
    ParameterField<Object> var2 =
        ParameterField.createExpressionField(true, "<+json.object(httpResponseBody).notPresent>", null, true);
    ParameterField<Object> var3 = ParameterField.createExpressionField(true, "<+json.not.a.valid.expr>", null, true);
    ParameterField<Object> var4 = ParameterField.createValueField("directValue");
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("name1", var1);
    variables.put("name4", var4);

    Ambiance ambianceBuilder =
        Ambiance.newBuilder()
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .putSettingToValueMap(NGPipelineSettingsConstant.ENABLE_EXPRESSION_ENGINE_V2.getName(), "true")
                    .build())
            .build();

    doReturn("metadataValue")
        .when(engineExpressionService)
        .evaluateExpression(any(), eq("<+json.object(httpResponseBody).metaData>"), any(), any());

    Map<String, String> evaluatedVariables =
        httpStepUtils.evaluateOutputVariables(variables, response1, ambianceBuilder);
    verify(engineExpressionService).evaluateExpression(eq(ambianceBuilder), anyString(), any(), argCaptor.capture());
    Map<String, Object> output = argCaptor.getValue();
    assertThat(output).isNotEmpty();
    assertThat(output.get("ENABLED_FEATURE_FLAGS")).isEqualTo(PIE_EXECUTION_JSON_SUPPORT);
    assertThat(evaluatedVariables).isNotEmpty();
    assertThat(evaluatedVariables.get("name1")).isEqualTo("metadataValue");
    assertThat(evaluatedVariables.get("name4")).isEqualTo("directValue");
    assertThat(evaluatedVariables.get("name4")).isEqualTo("directValue");

    variables.put("name2", var2);
    variables.put("name3", var3);

    HttpStepResponse response2 = HttpStepResponse.builder().httpResponseBody(body).build();
    evaluatedVariables = httpStepUtils.evaluateOutputVariables(variables, response2, ambiance);
    assertThat(evaluatedVariables).isNotEmpty();
    assertThat(evaluatedVariables.get("name2")).isNull();
    assertThat(evaluatedVariables.get("name3")).isNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateCertificateReturnsEmptyIfCertAndCertKeyIsEmpty() {
    HttpStepParameters httpStepParameters = HttpStepParameters.infoBuilder()
                                                .certificate(ParameterField.createValueField(""))
                                                .certificateKey(ParameterField.createValueField(""))
                                                .build();
    HttpTaskParametersNgBuilder httpTaskParametersNgBuilder = HttpTaskParametersNg.builder();
    HttpEncryptedCertificate httpEncryptedCertificate = httpStepUtils.createCertificate(
        httpStepParameters.getCertificate(), httpStepParameters.getCertificateKey(), ambiance);
    assertThat(httpEncryptedCertificate).isEqualTo(HttpEncryptedCertificate.builder().build());
    verifyNoMoreInteractions(secretManagerClientService);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFetchFinalMaskedValue() {
    // url with no secrets
    String fieldValue = "http://test.com";
    when(maskingExpressionEvaluator.substitute(fieldValue, Collections.emptyMap())).thenReturn(fieldValue);
    ParameterField<String> test = ParameterField.createValueField(fieldValue);

    assertThat(httpStepUtils.fetchFinalMaskedValue(test, null)).isEqualTo(fieldValue);
    assertThat(httpStepUtils.fetchFinalMaskedValue(test, maskingExpressionEvaluator)).isEqualTo(fieldValue);

    // url with secrets
    fieldValue = "http://test.com/$ngSecretManager.obtain('exampleRef',01234)";
    when(maskingExpressionEvaluator.substitute(fieldValue, Collections.emptyMap()))
        .thenReturn("http://test.com/<<<exampleRef>>>");
    test = ParameterField.createValueField(fieldValue);
    assertThat(httpStepUtils.fetchFinalMaskedValue(test, null)).isEqualTo(fieldValue);
    assertThat(httpStepUtils.fetchFinalMaskedValue(test, maskingExpressionEvaluator))
        .isEqualTo("http://test.com/<<<exampleRef>>>");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testEncodeURL() {
    NGLogCallback logCallback = mock(NGLogCallback.class);
    String url1 = "https://www.example.com/path%20with%20encoded%20spaces";
    assertThat(httpStepUtils.encodeURL(url1, logCallback)).isEqualTo(url1);

    String url2 =
        "https://www.example.com/Apply MS patches AMA Prod servers (Monthly-Sun)?api-version=2017-05-15-preview";
    String expected2 =
        "https://www.example.com/Apply%20MS%20patches%20AMA%20Prod%20servers%20(Monthly-Sun)?api-version=2017-05-15-preview";
    assertThat(httpStepUtils.encodeURL(url2, logCallback)).isEqualTo(expected2);
    verify(logCallback)
        .saveExecutionLog(eq(
            "Encoded URL: https://www.example.com/Apply%20MS%20patches%20AMA%20Prod%20servers%20(Monthly-Sun)?api-version=2017-05-15-preview"));

    String url3 = "https://www.example.com/@user?param=value";
    assertThat(httpStepUtils.encodeURL(url3, logCallback)).isEqualTo(url3);
    verify(logCallback).saveExecutionLog(eq("Encoded URL: https://www.example.com/@user?param=value"));

    String url4 = "https://www.example.com/already%20encoded?param=value";
    assertThat(httpStepUtils.encodeURL(url4, logCallback)).isEqualTo(url4);

    String url5 = "";
    assertThat(httpStepUtils.encodeURL(url5, logCallback)).isEqualTo(url5);

    String url6 = "https://www.example.com/${ngSecretManager.obtain('exampleRef', 1234)}?param=value";
    assertThat(httpStepUtils.encodeURL(url6, logCallback)).isEqualTo(url6);

    String url7 = "https://www.example.com/${ngSecretManager.obtain(\"exampleRef\", 1234)}?param=value";
    assertThat(httpStepUtils.encodeURL(url7, logCallback)).isEqualTo(url7);

    String url9 = "https://www.example.com/${sweepingOutputSecrets.obtain(\"secretName\",\"encodeValue\")}?param=value";
    assertThat(httpStepUtils.encodeURL(url9, logCallback)).isEqualTo(url9);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateCertificateCertKeyCanBeEmpty() {
    HttpStepParameters httpStepParameters = HttpStepParameters.infoBuilder()
                                                .certificate(ParameterField.createValueField(certRef))
                                                .certificateKey(ParameterField.createValueField(""))
                                                .build();
    HttpEncryptedCertificate httpEncryptedCertificate = httpStepUtils.createCertificate(
        httpStepParameters.getCertificate(), httpStepParameters.getCertificateKey(), ambiance);
    assertThat(httpEncryptedCertificate.getHttpCertificateNG()).isNotNull();
    assertThat(httpEncryptedCertificate.getHttpCertificateNG().getCertificate())
        .isEqualTo(SecretRefHelper.createSecretRef(certRef));
    assertThat(httpEncryptedCertificate.getHttpCertificateNG().getCertificateKey()).isNull();
    assertThat(httpEncryptedCertificate.getEncryptedDataDetails()).isNotNull();
    verify(secretManagerClientService, times(1)).getEncryptionDetails(eq(AmbianceUtils.getNgAccess(ambiance)), any());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCreateCertificateCertAndCertKeyPresent() {
    HttpStepParameters httpStepParameters = HttpStepParameters.infoBuilder()
                                                .certificate(ParameterField.createValueField(certRef))
                                                .certificateKey(ParameterField.createValueField(certKeyRef))
                                                .build();
    HttpEncryptedCertificate httpEncryptedCertificate = httpStepUtils.createCertificate(
        httpStepParameters.getCertificate(), httpStepParameters.getCertificateKey(), ambiance);
    assertThat(httpEncryptedCertificate.getHttpCertificateNG()).isNotNull();
    assertThat(httpEncryptedCertificate.getHttpCertificateNG().getCertificate())
        .isEqualTo(SecretRefHelper.createSecretRef(certRef));
    assertThat(httpEncryptedCertificate.getHttpCertificateNG().getCertificateKey())
        .isEqualTo(SecretRefHelper.createSecretRef(certKeyRef));
    assertThat(httpEncryptedCertificate.getEncryptedDataDetails()).isNotNull();
    verify(secretManagerClientService, times(1)).getEncryptionDetails(eq(AmbianceUtils.getNgAccess(ambiance)), any());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateCertificateCertCannotBeEmpty() {
    assertThatThrownBy(()
                           -> httpStepUtils.createCertificate(
                               ParameterField.createValueField(""), ParameterField.createValueField("value"), ambiance))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Only certificateKey is provided, we need both certificate and certificateKey or only certificate");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetHttpStepParameters() {
    assertEquals(httpStepUtils.getHttpStepParameters(
                     StepElementParameters.builder()
                         .spec(HttpStepParameters.infoBuilder()
                                   .url(ParameterField.createValueField("https://www.google.com"))
                                   .method(ParameterField.createValueField("GET"))
                                   .build())
                         .build()),
        HttpStepParameters.infoBuilder()
            .url(ParameterField.createValueField("https://www.google.com"))
            .method(ParameterField.createValueField("GET"))
            .build());
    assertEquals(httpStepUtils.getHttpStepParameters(
                     StepElementParameters.builder()
                         .spec(io.harness.steps.http.v1.HttpStepParameters.infoBuilder()
                                   .url(ParameterField.createValueField("https://www.google.com"))
                                   .method(ParameterField.createValueField("GET"))
                                   .build())
                         .build()),
        HttpStepParameters.infoBuilder()
            .url(ParameterField.createValueField("https://www.google.com"))
            .method(ParameterField.createValueField("GET"))
            .build());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetHttpOutcome() {
    assertEquals(httpStepUtils.getHttpOutcome(ambiance, HarnessYamlVersion.V0,
                     HttpStepParameters.infoBuilder()
                         .url(ParameterField.createValueField("https://www.google.com"))
                         .method(ParameterField.createValueField("GET"))
                         .build(),
                     HttpStepResponse.builder().build(), new HashMap<>()),
        HttpOutcome.builder()
            .httpUrl("https://www.google.com")
            .httpMethod("GET")
            .outputVariables(new HashMap<>())
            .build());
    assertEquals(httpStepUtils.getHttpOutcome(ambiance, HarnessYamlVersion.V1,
                     HttpStepParameters.infoBuilder()
                         .url(ParameterField.createValueField("https://www.google.com"))
                         .method(ParameterField.createValueField("GET"))
                         .build(),
                     HttpStepResponse.builder().build(), new HashMap<>()),
        io.harness.steps.http.v1.HttpOutcome.builder()
            .url("https://www.google.com")
            .method("GET")
            .output_vars(new HashMap<>())
            .build());
    assertThatThrownBy(()
                           -> httpStepUtils.getHttpOutcome(ambiance, "v2", HttpStepParameters.infoBuilder().build(),
                               HttpStepResponse.builder().build(), new HashMap<>()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Version v2 not supported");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testBuildContextMapFromResponseWithHeaders() {
    // Create a map of response headers
    Map<String, String> responseHeaders = new HashMap<>();
    responseHeaders.put("content-type", "application/json");
    responseHeaders.put("set-cookie", "sessionId=abc123; Path=/");
    responseHeaders.put("x-request-id", "req-123456");

    // Create HttpStepResponse with the headers
    HttpStepResponse httpStepResponse = HttpStepResponse.builder()
                                            .httpResponseBody("{\"key\":\"value\"}")
                                            .httpResponseCode(200)
                                            .responseHeaders(responseHeaders)
                                            .build();

    // Call the method under test
    Map<String, Object> contextMap = httpStepUtils.buildContextMapFromResponse(httpStepResponse, false);

    // Verify the results
    assertThat(contextMap).isNotNull();
    assertThat(contextMap).containsEntry("httpResponseBody", "{\"key\":\"value\"}");
    assertThat(contextMap).containsEntry("httpResponseCode", "200");
    assertThat(contextMap).containsKey("httpResponseHeaders");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testBuildContextMapFromResponseWithNullHeaders() {
    // Create HttpStepResponse with null headers
    HttpStepResponse httpStepResponse = HttpStepResponse.builder()
                                            .httpResponseBody("{\"key\":\"value\"}")
                                            .httpResponseCode(200)
                                            .responseHeaders(null)
                                            .build();

    // Call the method under test
    Map<String, Object> contextMap = httpStepUtils.buildContextMapFromResponse(httpStepResponse, false);

    // Verify the results
    assertThat(contextMap).isNotNull();
    assertThat(contextMap).containsEntry("httpResponseBody", "{\"key\":\"value\"}");
    assertThat(contextMap).containsEntry("httpResponseCode", "200");
    assertThat(contextMap).doesNotContainKey("httpResponseHeaders");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testBuildContextMapFromResponseWithEmptyHeaders() {
    // Create HttpStepResponse with empty headers
    HttpStepResponse httpStepResponse = HttpStepResponse.builder()
                                            .httpResponseBody("{\"key\":\"value\"}")
                                            .httpResponseCode(200)
                                            .responseHeaders(Collections.emptyMap())
                                            .build();

    // Call the method under test
    Map<String, Object> contextMap = httpStepUtils.buildContextMapFromResponse(httpStepResponse, false);

    // Verify the results
    assertThat(contextMap).isNotNull();
    assertThat(contextMap).containsEntry("httpResponseBody", "{\"key\":\"value\"}");
    assertThat(contextMap).containsEntry("httpResponseCode", "200");
    assertThat(contextMap).doesNotContainKey("httpResponseHeaders");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetHttpOutcomeWithResponseHeaders() {
    when(pmsFeatureFlagHelper.isEnabled(anyString(), eq(FeatureName.CDS_SUPPORT_HTTP_HEADER_HTTP_STEP)))
        .thenReturn(true);
    // Create a map of response headers
    Map<String, String> responseHeaders = new HashMap<>();
    responseHeaders.put("content-type", "application/json");
    responseHeaders.put("set-cookie", "sessionId=abc123; Path=/");
    responseHeaders.put("x-request-id", "req-123456");

    // Create HttpStepResponse with the headers
    HttpStepResponse httpStepResponse = HttpStepResponse.builder()
                                            .httpResponseBody("{\"key\":\"value\"}")
                                            .httpResponseCode(200)
                                            .responseHeaders(responseHeaders)
                                            .build();

    // Create HttpStepParameters
    HttpStepParameters httpStepParameters = HttpStepParameters.infoBuilder()
                                                .url(ParameterField.createValueField("https://www.example.com"))
                                                .method(ParameterField.createValueField("GET"))
                                                .build();

    // Call the method under test for V0
    HttpOutcome outcome = (HttpOutcome) httpStepUtils.getHttpOutcome(
        ambiance, HarnessYamlVersion.V0, httpStepParameters, httpStepResponse, new HashMap<>());

    // Verify the results
    assertThat(outcome).isNotNull();
    assertThat(outcome.getHttpUrl()).isEqualTo("https://www.example.com");
    assertThat(outcome.getHttpMethod()).isEqualTo("GET");
    assertThat(outcome.getHttpResponseCode()).isEqualTo(200);
    assertThat(outcome.getHttpResponseBody()).isEqualTo("{\"key\":\"value\"}");

    // Verify that responseHeaders are included in the outcome
    assertThat(outcome.getResponseHeaders()).isNotNull();
    assertThat(outcome.getResponseHeaders()).isEqualTo(responseHeaders);
    assertThat(outcome.getResponseHeaders()).containsEntry("content-type", "application/json");
    assertThat(outcome.getResponseHeaders()).containsEntry("set-cookie", "sessionId=abc123; Path=/");
    assertThat(outcome.getResponseHeaders()).containsEntry("x-request-id", "req-123456");
  }
}
