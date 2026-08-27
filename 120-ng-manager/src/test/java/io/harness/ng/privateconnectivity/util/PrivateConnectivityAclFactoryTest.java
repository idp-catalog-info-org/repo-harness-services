/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.util;

import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrivateConnectivityAclFactoryTest extends CategoryTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldGenerateSubnetPolicyWithoutADefaultRouteOrLateralGrant() throws Exception {
    JsonNode policy = policy(List.of("10.10.0.0/16"), List.of());

    assertThat(policy.path("autoApprovers").path("routes").path("10.10.0.0/16").get(0).asText())
        .isEqualTo(PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG);
    assertThat(policy.path("autoApprovers").path("routes").has("0.0.0.0/0")).isFalse();
    assertThat(policy.get("grants").toString()).contains("10.10.0.0/16");
    assertThat(policy.get("tests").toString())
        .contains(PrivateConnectivityHelpers.CI_RUNNER_TAG + ":22")
        .doesNotContain("10.10.0.0/16");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldGenerateAppConnectorPolicyForOnlyConfiguredDomains() throws Exception {
    JsonNode policy = policy(List.of(), List.of("api.example.com", "*.apps.example.com"));

    assertThat(policy.path("autoApprovers").path("routes").path("0.0.0.0/0").get(0).asText())
        .isEqualTo(PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG);
    assertThat(policy.path("autoApprovers").path("routes").path("::/0").get(0).asText())
        .isEqualTo(PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG);
    assertThat(policy.get("nodeAttrs").toString())
        .contains("api.example.com", "*.apps.example.com", PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG);
    assertThat(policy.get("grants").toString()).contains("autogroup:internet", "tcp:53", "udp:53");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldGenerateCombinedPolicyAsTheUnionOfSubnetAndAppBehavior() throws Exception {
    JsonNode policy = policy(List.of("172.16.0.0/16"), List.of("api.example.com"));

    assertThat(policy.get("autoApprovers").toString()).contains("172.16.0.0/16", "0.0.0.0/0", "::/0");
    assertThat(policy.get("nodeAttrs").toString()).contains("api.example.com");
    assertThat(policy.get("tagOwners").size()).isEqualTo(3);
    assertThat(policy.get("tagOwners").has(PrivateConnectivityHelpers.CI_RUNNER_TAG)).isTrue();
    assertThat(policy.get("tagOwners").has(PrivateConnectivityHelpers.CUSTOMER_APPLIANCE_TAG)).isTrue();
    assertThat(policy.get("tagOwners").has(PrivateConnectivityHelpers.HELPER_TAG)).isTrue();
  }

  private static JsonNode policy(List<String> routes, List<String> domains) throws Exception {
    return MAPPER.readTree(PrivateConnectivityAclFactory.buildAclJson(
        PrivateConnectivitySetupRequestDTO.builder().advertiseRoutes(routes).domains(domains).build()));
  }
}
