/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.utils;

import static io.harness.rule.OwnerRule.ARYA;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ProxyTargetValidatorTest extends CategoryTest {
  private static String configWithTarget(String target) {
    return "proxy:\n"
        + "  endpoints:\n"
        + "    /canary:\n"
        + "      target: " + target + "\n";
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void blocksCloudMetadataTargets() {
    // The exact payload from the Synack report (IDP-10919 / raven-w001-50).
    assertThatThrownBy(
        () -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://metadata.google.internal")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("/canary");
    assertThatThrownBy(
        () -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://metadata.google.internal./")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        () -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://METADATA.GOOGLE.INTERNAL")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://metadata.goog")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://metadata.azure.com")))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void blocksInternalOnlyHostnames() {
    assertThatThrownBy(
        () -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://foo.svc.cluster.local/api")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        () -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://idp-service.prod1.svc")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("https://build.internal")))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void blocksLiteralAddressesInBlockedRanges() {
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://169.254.169.254")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://127.0.0.1:8080")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://10.0.0.1")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://192.168.1.1")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://172.16.5.5")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        () -> ProxyTargetValidator.validateProxyTargets(configWithTarget("\"http://[64:ff9b::a9fe:a9fe]\"")))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void blocksNonDottedQuadIpv4LiteralForms() {
    // Decimal form of 169.254.169.254 (cloud metadata). curl, wget and most HTTP clients accept this the same as
    // the dotted-quad form; the dotted-quad-only regex this validator used to have would have let it through.
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://2852039166")))
        .isInstanceOf(InvalidRequestException.class);
    // Partial form of 127.0.0.1.
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://127.1")))
        .isInstanceOf(InvalidRequestException.class);
    // Decimal form of a public address must still be allowed.
    assertThatCode(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("http://134744072"))) // 8.8.8.8
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void blocksNonHttpSchemes() {
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("file:///etc/passwd")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("http or https");
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("gopher://example.com")))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void blocksCloudMetadataHeaders() {
    String config = "proxy:\n"
        + "  endpoints:\n"
        + "    /canary:\n"
        + "      target: https://api.example.com\n"
        + "      headers:\n"
        + "        Metadata-Flavor: Google\n";
    assertThatThrownBy(() -> ProxyTargetValidator.validateProxyTargets(config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Metadata-Flavor");
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void allowsLegitimateTargets() {
    assertThatCode(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("https://circleci.com/api/v1.1")))
        .doesNotThrowAnyException();
    // Customer-internal hostname reached through a delegate. It does not resolve from Harness, which is exactly why
    // this validator must not resolve names.
    assertThatCode(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("https://jira.corp.customer.com")))
        .doesNotThrowAnyException();
    // Substituted inside the Backstage pod; the egress interceptor is the enforcement point for these.
    assertThatCode(() -> ProxyTargetValidator.validateProxyTargets(configWithTarget("https://${JIRA_HOST}/rest/api")))
        .doesNotThrowAnyException();
    assertThatCode(()
                       -> ProxyTargetValidator.validateProxyTargets("proxy:\n"
                           + "  endpoints:\n"
                           + "    /jira/api:\n"
                           + "      target: https://jira.example.com\n"
                           + "      headers:\n"
                           + "        Authorization: Basic ${JIRA_TOKEN}\n"
                           + "        Accept: application/json\n"))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ARYA)
  @Category(UnitTests.class)
  public void ignoresConfigsWithoutProxyEndpoints() {
    assertThatCode(() -> ProxyTargetValidator.validateProxyTargets(null)).doesNotThrowAnyException();
    assertThatCode(() -> ProxyTargetValidator.validateProxyTargets("")).doesNotThrowAnyException();
    assertThatCode(() -> ProxyTargetValidator.validateProxyTargets("grafana:\n  domain: https://grafana.example.com\n"))
        .doesNotThrowAnyException();
    assertThatCode(() -> ProxyTargetValidator.validateProxyTargets("proxy:\n  endpoints: {}\n"))
        .doesNotThrowAnyException();
  }
}
