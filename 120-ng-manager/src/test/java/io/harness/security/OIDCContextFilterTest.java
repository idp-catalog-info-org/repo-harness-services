/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.oidc.OIDCContextConstants.X_CONNECTOR_IDENTIFIER;
import static io.harness.oidc.OIDCContextConstants.X_CONNECTOR_NAME;
import static io.harness.oidc.OIDCContextConstants.X_ENVIRONMENT_IDENTIFIER;
import static io.harness.oidc.OIDCContextConstants.X_ENVIRONMENT_TYPE;
import static io.harness.oidc.OIDCContextConstants.X_PIPELINE_IDENTIFIER;
import static io.harness.oidc.OIDCContextConstants.X_SERVICE_IDENTIFIER;
import static io.harness.oidc.OIDCContextConstants.X_SERVICE_NAME;
import static io.harness.oidc.OIDCContextConstants.X_STAGE_TYPE;
import static io.harness.oidc.OIDCContextConstants.X_STEP_TYPE;
import static io.harness.oidc.OIDCContextConstants.X_TRIGGERED_BY_EMAIL;
import static io.harness.oidc.OIDCContextConstants.X_TRIGGERED_BY_NAME;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.manage.GlobalContextManager;
import io.harness.oidc.OIDCContext;
import io.harness.oidc.OIDCContextData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class OIDCContextFilterTest extends CategoryTest {
  private static final String pipelineIdentifier = randomAlphabetic(10);
  private static final String environmentIdentifier = randomAlphabetic(10);
  private static final String serviceIdentifier = randomAlphabetic(10);
  private static final String connectorIdentifier = randomAlphabetic(10);
  private static final String serviceName = randomAlphabetic(10);
  private static final String connectorName = randomAlphabetic(10);
  private static final String environmentType = randomAlphabetic(10);
  private static final String stageType = randomAlphabetic(10);
  private static final String stepType = randomAlphabetic(10);
  private static final String triggeredByName = randomAlphabetic(10);
  private static final String triggeredByEmail = randomAlphabetic(10);

  private OIDCContextFilter oidcContextFilter;
  private ContainerRequestContext requestContext;
  private ContainerResponseContext responseContext;
  private MultivaluedMap<String, String> headers;

  @Before
  public void setup() {
    oidcContextFilter = new OIDCContextFilter();
    requestContext = mock(ContainerRequestContext.class);
    responseContext = mock(ContainerResponseContext.class);
    headers = new MultivaluedHashMap<>();

    when(requestContext.getHeaders()).thenReturn(headers);
    GlobalContextManager.set(new GlobalContext());
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testFilter_withAllHeaders() throws IOException {
    Map<String, String> testHeaders = new HashMap<>();
    testHeaders.put(X_PIPELINE_IDENTIFIER, pipelineIdentifier);
    testHeaders.put(X_ENVIRONMENT_IDENTIFIER, environmentIdentifier);
    testHeaders.put(X_ENVIRONMENT_TYPE, environmentType);
    testHeaders.put(X_STAGE_TYPE, stageType);
    testHeaders.put(X_STEP_TYPE, stepType);
    testHeaders.put(X_CONNECTOR_IDENTIFIER, connectorIdentifier);
    testHeaders.put(X_SERVICE_IDENTIFIER, serviceIdentifier);
    testHeaders.put(X_CONNECTOR_NAME, connectorName);
    testHeaders.put(X_SERVICE_NAME, serviceName);
    testHeaders.put(X_TRIGGERED_BY_NAME, triggeredByName);
    testHeaders.put(X_TRIGGERED_BY_EMAIL, triggeredByEmail);

    testHeaders.forEach((key, value) -> headers.add(key, value));

    // Execute
    oidcContextFilter.filter(requestContext);

    // Verify OIDCContext was set in GlobalContext
    OIDCContext oidcContext = getOIDCContextFromGlobalContext();
    assertThat(oidcContext).isNotNull();
    assertThat(oidcContext.getPipelineIdentifier()).isEqualTo(pipelineIdentifier);
    assertThat(oidcContext.getEnvironmentIdentifier()).isEqualTo(environmentIdentifier);
    assertThat(oidcContext.getEnvironmentType()).isEqualTo(environmentType);
    assertThat(oidcContext.getStageType()).isEqualTo(stageType);
    assertThat(oidcContext.getStepType()).isEqualTo(stepType);
    assertThat(oidcContext.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(oidcContext.getServiceIdentifier()).isEqualTo(serviceIdentifier);
    assertThat(oidcContext.getConnectorName()).isEqualTo(connectorName);
    assertThat(oidcContext.getServiceName()).isEqualTo(serviceName);
    assertThat(oidcContext.getTriggeredByName()).isEqualTo(triggeredByName);
    assertThat(oidcContext.getTriggerByEmail()).isEqualTo(triggeredByEmail);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testFilter_withPartialHeaders() throws IOException {
    // Setup test data with only a few headers
    headers.add(X_PIPELINE_IDENTIFIER, pipelineIdentifier);
    headers.add(X_TRIGGERED_BY_EMAIL, triggeredByEmail);

    // Execute
    oidcContextFilter.filter(requestContext);

    // Verify only the set fields are present
    OIDCContext oidcContext = getOIDCContextFromGlobalContext();
    assertThat(oidcContext).isNotNull();
    assertThat(oidcContext.getPipelineIdentifier()).isEqualTo(pipelineIdentifier);
    assertThat(oidcContext.getTriggerByEmail()).isEqualTo(triggeredByEmail);
    assertThat(oidcContext.getEnvironmentIdentifier()).isNull();
    assertThat(oidcContext.getServiceIdentifier()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testFilter_withNoHeaders() throws IOException {
    oidcContextFilter.filter(requestContext);
    OIDCContext oidcContext = getOIDCContextFromGlobalContext();
    assertThat(oidcContext).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testFilter_withNonOIDCHeaders() throws IOException {
    headers.add("X-SOURCE-PRINCIPAL", "admin@harness.io");
    oidcContextFilter.filter(requestContext);
    OIDCContext oidcContext = getOIDCContextFromGlobalContext();
    assertThat(oidcContext).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testResponseFilter() throws IOException {
    GlobalContextManager.set(new GlobalContext());
    GlobalContextManager.upsertGlobalContextRecord(OIDCContextData.builder()
                                                       .oidcContext(OIDCContext.builder()
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .environmentIdentifier(environmentIdentifier)
                                                                        .build())
                                                       .build());

    oidcContextFilter.filter(requestContext, responseContext);

    assertThat(GlobalContextManager.isAvailable()).isFalse();
  }

  /**
   * Verifies that URL-encoded header values (containing non-ASCII characters) are properly decoded.
   * This tests the server-side decoding that reverses the client-side URL encoding.
   *
   * Example: "Tamás Hanicz" is URL-encoded as "Tam%C3%A1s+Hanicz" by the client,
   * and should be decoded back to "Tamás Hanicz" on the server.
   */
  @Test
  @Owner(developers = OwnerRule.AKSHAY)
  @Category(UnitTests.class)
  public void testFilter_DecodesUrlEncodedNonAsciiCharacters() throws IOException {
    // URL-encoded values that would be sent by the client for non-ASCII characters
    // "Tamás Hanicz" URL-encoded becomes "Tam%C3%A1s+Hanicz"
    headers.add(X_TRIGGERED_BY_NAME, "Tam%C3%A1s+Hanicz");
    // "tamas.hanicz@example.com" with @ encoded becomes "tamas.hanicz%40example.com"
    headers.add(X_TRIGGERED_BY_EMAIL, "tamas.hanicz%40example.com");
    // "Tëst Çonnector" URL-encoded
    headers.add(X_CONNECTOR_NAME, "T%C3%ABst+%C3%87onnector");
    // "Sërvice Nàme" URL-encoded
    headers.add(X_SERVICE_NAME, "S%C3%ABrvice+N%C3%A0me");
    headers.add(X_PIPELINE_IDENTIFIER, "pipeline123");

    oidcContextFilter.filter(requestContext);

    OIDCContext oidcContext = getOIDCContextFromGlobalContext();
    assertThat(oidcContext).isNotNull();
    assertThat(oidcContext.getPipelineIdentifier()).isEqualTo("pipeline123");
    // Verify URL-encoded values are decoded back to original
    assertThat(oidcContext.getTriggeredByName()).isEqualTo("Tamás Hanicz");
    assertThat(oidcContext.getTriggerByEmail()).isEqualTo("tamas.hanicz@example.com");
    assertThat(oidcContext.getConnectorName()).isEqualTo("Tëst Çonnector");
    assertThat(oidcContext.getServiceName()).isEqualTo("Sërvice Nàme");
  }

  /**
   * Verifies that URL-encoded ASCII values are properly decoded.
   * Since we now always URL-encode on the client side, this tests that decoding works for ASCII too.
   */
  @Test
  @Owner(developers = OwnerRule.AKSHAY)
  @Category(UnitTests.class)
  public void testFilter_DecodesUrlEncodedAsciiValues() throws IOException {
    // URL-encoded ASCII values (spaces become +, @ becomes %40)
    headers.add(X_TRIGGERED_BY_NAME, "John+Smith");
    headers.add(X_TRIGGERED_BY_EMAIL, "john.smith%40example.com");
    headers.add(X_CONNECTOR_NAME, "My+Connector");
    headers.add(X_SERVICE_NAME, "My+Service");
    headers.add(X_PIPELINE_IDENTIFIER, "pipeline123");

    oidcContextFilter.filter(requestContext);

    OIDCContext oidcContext = getOIDCContextFromGlobalContext();
    assertThat(oidcContext).isNotNull();
    // URL-encoded values should be decoded back to original
    assertThat(oidcContext.getTriggeredByName()).isEqualTo("John Smith");
    assertThat(oidcContext.getTriggerByEmail()).isEqualTo("john.smith@example.com");
    assertThat(oidcContext.getConnectorName()).isEqualTo("My Connector");
    assertThat(oidcContext.getServiceName()).isEqualTo("My Service");
  }

  private OIDCContext getOIDCContextFromGlobalContext() {
    if (!GlobalContextManager.isAvailable()) {
      return null;
    }
    OIDCContextData oidcContextData = GlobalContextManager.get(OIDCContextData.OIDC_CONTEXT);
    return oidcContextData != null ? oidcContextData.getOidcContext() : null;
  }
}