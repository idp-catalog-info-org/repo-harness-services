/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.http;

import static io.harness.beans.constants.JsonConstants.RESOLVE_OBJECTS_VIA_JSON_SELECT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.HttpCertificateNGV1;
import io.harness.beans.HttpCertificateNGV1.HttpCertificateNGV1Builder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionService;
import io.harness.expression.MaskingExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.StepUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class HttpStepUtils {
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject @Named("PRIVILEGED") private SecretManagerClientService secretManagerClientService;
  private static final String URL_ENCODED_CHAR_REGEX = ".*%[0-9a-fA-F]{2}.*";
  private static final Pattern SECRET_EXPRESSION =
      Pattern.compile("\\$\\{ngSecretManager\\.obtain\\(\\\"\\w*[\\.]?\\w*\\\"\\, "
          + "([+-]?\\d*|0)\\)\\}|\\$\\{sweepingOutputSecrets\\.obtain\\(\"[\\S|.]+?\",\"[\\S|.]+?\"\\)}");
  private static final Pattern SECRET_EXPRESSION_WITH_SINGLE_QUOTES =
      Pattern.compile("\\$\\{ngSecretManager\\.obtain\\('\\w*[\\.]?\\w*'\\, "
          + "([+-]?\\d*|0)\\)\\}|\\$\\{sweepingOutputSecrets\\.obtain\\(\"[\\S|.]+?\",\"[\\S|.]+?\"\\)}");

  public Map<String, Object> buildContextMapFromResponse(
      HttpStepResponse httpStepResponse, boolean resolveObjectsViaJSONSelect) {
    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put("httpResponseBody", httpStepResponse.getHttpResponseBody());
    contextMap.put("httpResponseCode", String.valueOf(httpStepResponse.getHttpResponseCode()));

    // Add response headers directly to the context map
    if (EmptyPredicate.isNotEmpty(httpStepResponse.getResponseHeaders())) {
      contextMap.put("httpResponseHeaders", httpStepResponse.getResponseHeaders());
    }

    if (resolveObjectsViaJSONSelect) {
      contextMap.put(RESOLVE_OBJECTS_VIA_JSON_SELECT, "true");
    }
    return contextMap;
  }

  public Map<String, String> evaluateOutputVariables(
      Map<String, Object> outputVariables, HttpStepResponse httpStepResponse, Ambiance ambiance) {
    Map<String, String> outputVariablesEvaluated = new LinkedHashMap<>();
    final boolean resolveObjectsViaJSONSelect = pmsFeatureFlagHelper.isEnabled(
        AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_RESOLVE_OBJECTS_VIA_JSON_SELECT);

    if (outputVariables != null) {
      Map<String, Object> contextMap = buildContextMapFromResponse(httpStepResponse, resolveObjectsViaJSONSelect);
      outputVariables.keySet().forEach(name -> {
        Object expression = outputVariables.get(name);
        if (expression instanceof ParameterField) {
          ParameterField<?> expr = (ParameterField<?>) expression;
          if (expr.isExpression()) {
            // Adding Json Expression Support
            AmbianceUtils.enabledJsonSupportFeatureFlag(ambiance, contextMap);
            Object evaluatedValue = engineExpressionService.evaluateExpression(
                ambiance, expr.getExpressionValue(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED, contextMap);
            if (evaluatedValue != null) {
              outputVariablesEvaluated.put(name, evaluatedValue.toString());
            }
          } else if (expr.getValue() != null) {
            outputVariablesEvaluated.put(name, expr.getValue().toString());
          }
        }
      });
    }
    return outputVariablesEvaluated;
  }

  public String encodeURL(String rawUrl, NGLogCallback logCallback) {
    if (doesURLContainSecretExpression(rawUrl)) {
      logCallback.saveExecutionLog("Provided URL contains secret, skipping encoding the url...");
      log.info("URL contains secret, skipping encoding the url in http step");
      return rawUrl;
    }
    if (!isURLAlreadyEncoded(rawUrl)) {
      try {
        URL url = new URL(rawUrl);
        URI uri = new URI(url.getProtocol(), url.getUserInfo(), IDN.toASCII(url.getHost()), url.getPort(),
            url.getPath(), url.getQuery(), url.getRef());
        String encodedUrl = uri.toASCIIString();
        logCallback.saveExecutionLog(String.format("Encoded URL: %s", encodedUrl));
        return encodedUrl;
      } catch (MalformedURLException | URISyntaxException e) {
        logCallback.saveExecutionLog(String.format("Failed to encode URL: %s", e.getMessage()));
      }
    }
    return rawUrl;
  }

  private static boolean isURLAlreadyEncoded(String url) {
    return url.matches(URL_ENCODED_CHAR_REGEX);
  }

  private static boolean doesURLContainSecretExpression(String url) {
    return SECRET_EXPRESSION.matcher(url).find() || SECRET_EXPRESSION_WITH_SINGLE_QUOTES.matcher(url).find();
  }

  public String fetchFinalValue(ParameterField<String> field) {
    return (String) field.fetchFinalValue();
  }

  public String fetchFinalMaskedValue(ParameterField<String> field, MaskingExpressionEvaluator maskingEvaluator) {
    String fieldValue = fetchFinalValue(field);
    if (maskingEvaluator == null) {
      return fieldValue;
    }
    return maskingEvaluator.substitute(fieldValue, Collections.emptyMap());
  }

  public void closeLogStream(Ambiance ambiance) {
    try {
      Thread.sleep(500, 0);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Close Log Stream was interrupted", e);
    } finally {
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      logStreamingStepClient.closeAllOpenStreamsWithPrefix(StepUtils.generateLogKeys(ambiance, emptyList()).get(0));
    }
  }

  public NGLogCallback getNGLogCallback(LogStreamingStepClientFactory logStreamingStepClientFactory, Ambiance ambiance,
      String logFix, boolean openStream) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, logFix, openStream);
  }

  public static boolean validateAssertions(
      HttpStepResponse httpStepResponse, ParameterField<String> assertionParameterField, NGLogCallback logCallback) {
    if (ParameterField.isNull(assertionParameterField)) {
      return true;
    }

    HttpExpressionEvaluator evaluator = new HttpExpressionEvaluator(httpStepResponse);
    String assertion = (String) assertionParameterField.fetchFinalValue();
    if (assertion == null || isEmpty(assertion.trim())) {
      return true;
    }

    try {
      Object value = evaluator.evaluateExpression(assertion);
      if (!(value instanceof Boolean)) {
        logCallback.saveExecutionLog(String.format("Expected boolean assertion, got %s value",
                                         value == null ? "null" : value.getClass().getSimpleName()),
            LogLevel.ERROR);
        throw new InvalidRequestException(String.format(
            "Expected boolean assertion, got %s value", value == null ? "null" : value.getClass().getSimpleName()));
      }
      if (Boolean.FALSE.equals(value)) {
        logCallback.saveExecutionLog(String.format("Assertion %s resolved as false", assertion), LogLevel.ERROR);
      }

      return (boolean) value;
    } catch (Exception e) {
      throw new InvalidRequestException("Assertion provided is not a valid expression", e);
    }
  }

  @VisibleForTesting
  @NotNull
  public HttpEncryptedCertificate createCertificate(
      ParameterField<String> cert, ParameterField<String> cert_key, Ambiance ambiance) {
    if (isEmpty(cert.getValue()) && isEmpty(cert_key.getValue())) {
      return HttpEncryptedCertificate.builder().build();
    }

    if (isEmpty(cert.getValue()) && isNotEmpty(cert_key.getValue())) {
      throw new InvalidRequestException(
          "Only certificateKey is provided, we need both certificate and certificateKey or only certificate", USER);
    }

    HttpCertificateNGV1Builder httpCertificateBuilder =
        HttpCertificateNGV1.builder().certificate(SecretRefHelper.createSecretRef(cert.getValue()));
    if (isNotEmpty(cert_key.getValue())) {
      httpCertificateBuilder.certificateKey(SecretRefHelper.createSecretRef(cert_key.getValue()));
    }
    HttpCertificateNGV1 httpCertificate = httpCertificateBuilder.build();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManagerClientService.getEncryptionDetails(ngAccess, httpCertificate);
    return HttpEncryptedCertificate.builder()
        .httpCertificateNG(httpCertificate)
        .encryptedDataDetails(encryptedDataDetails)
        .build();
  }

  // We have separate POJO for step outcome for v1 because we also need to support expressions of outcomes following v1
  // rfc
  HttpStepParameters getHttpStepParameters(StepBaseParameters stepParameters) {
    String version = stepParameters.getSpec().getVersion();
    switch (version) {
      case HarnessYamlVersion.V0:
        return (HttpStepParameters) stepParameters.getSpec();
      case HarnessYamlVersion.V1:
        return ((io.harness.steps.http.v1.HttpStepParameters) stepParameters.getSpec()).toHttpStepParametersV0();
      default:
        log.error("Version {} not supported", version);
        throw new InvalidRequestException(String.format("Version %s not supported", version));
    }
  }

  // Convert v1 step parameters to v0, we could not do this during plan creation because we also need to support
  // expressions following v1 rfc
  HttpBaseOutcome getHttpOutcome(Ambiance ambiance, String version, HttpStepParameters httpStepParameters,
      HttpStepResponse httpStepResponse, Map<String, String> outputVariablesEvaluated) {
    switch (version) {
      case HarnessYamlVersion.V1:
        return io.harness.steps.http.v1.HttpOutcome.builder()
            .url(fetchFinalValue(httpStepParameters.getUrl()))
            .method(fetchFinalValue(httpStepParameters.getMethod()))
            .response_code(httpStepResponse.getHttpResponseCode())
            .response_body(httpStepResponse.getHttpResponseBody())
            .status(httpStepResponse.getCommandExecutionStatus())
            .error_msg(httpStepResponse.getErrorMessage())
            .output_vars(outputVariablesEvaluated)
            .build();
      case HarnessYamlVersion.V0:
        var builder = HttpOutcome.builder()
                          .httpUrl(fetchFinalValue(httpStepParameters.getUrl()))
                          .httpMethod(fetchFinalValue(httpStepParameters.getMethod()))
                          .httpResponseCode(httpStepResponse.getHttpResponseCode())
                          .httpResponseBody(httpStepResponse.getHttpResponseBody())
                          .status(httpStepResponse.getCommandExecutionStatus())
                          .errorMsg(httpStepResponse.getErrorMessage())
                          .outputVariables(outputVariablesEvaluated);
        if (pmsFeatureFlagHelper.isEnabled(
                AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_SUPPORT_HTTP_HEADER_HTTP_STEP)) {
          // Adding this because http headers might contain sensitive data & we do not want this new feature to break
          builder.responseHeaders(httpStepResponse.getResponseHeaders());
        }
        return builder.build();
      default:
        log.error("Version {} not supported", version);
        throw new InvalidRequestException(String.format("Version %s not supported", version));
    }
  }
}
