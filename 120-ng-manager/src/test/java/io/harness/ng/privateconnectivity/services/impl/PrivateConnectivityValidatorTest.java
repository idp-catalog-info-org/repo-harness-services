/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.services.impl;

import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import javax.ws.rs.BadRequestException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrivateConnectivityValidatorTest extends CategoryTest {
  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldNormalizeTheCompleteDesiredConfiguration() {
    PrivateConnectivitySetupRequestDTO normalized = PrivateConnectivityValidator.normalize(
        PrivateConnectivitySetupRequestDTO.builder()
            .advertiseRoutes(List.of(" 10.20.0.0/16 ", "10.10.0.0/16", "10.20.0.0/16"))
            .domains(List.of(" API.Example.COM ", "*.Apps.Example.com", "api.example.com"))
            .dns(PrivateConnectivitySetupRequestDTO.DnsConfig.builder()
                     .splitDnsDomains(Map.of(" Corp.Example.com ", List.of("10.20.0.11", "10.20.0.10")))
                     .build())
            .build());

    assertThat(normalized.getAdvertiseRoutes()).containsExactly("10.10.0.0/16", "10.20.0.0/16");
    assertThat(normalized.getDomains()).containsExactly("*.apps.example.com", "api.example.com");
    assertThat(normalized.getDns().getSplitDnsDomains())
        .containsOnlyKeys("corp.example.com")
        .containsEntry("corp.example.com", List.of("10.20.0.10", "10.20.0.11"));
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldRejectRoutesThatAreUnsafeOrNotCanonical() {
    for (String route : List.of("0.0.0.0/0", "100.64.0.0/10", "8.8.8.0/24", "10.0.0.1/24", "010.0.0.0/8")) {
      assertThatThrownBy(()
                             -> PrivateConnectivityValidator.normalize(
                                 PrivateConnectivitySetupRequestDTO.builder().advertiseRoutes(List.of(route)).build()))
          .as("route %s", route)
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldRequireSplitDnsResolversToUseAnAdvertisedPrivateRoute() {
    PrivateConnectivitySetupRequestDTO request =
        PrivateConnectivitySetupRequestDTO.builder()
            .advertiseRoutes(List.of("10.20.0.0/24"))
            .dns(PrivateConnectivitySetupRequestDTO.DnsConfig.builder()
                     .splitDnsDomains(Map.of("corp.example.com", List.of("10.21.0.10")))
                     .build())
            .build();

    assertThatThrownBy(() -> PrivateConnectivityValidator.normalize(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("must be within an advertised private route");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldRejectEmptyConfigurationAndAppOnlySplitDns() {
    assertThatThrownBy(
        () -> PrivateConnectivityValidator.normalize(PrivateConnectivitySetupRequestDTO.builder().build()))
        .isInstanceOf(BadRequestException.class);

    PrivateConnectivitySetupRequestDTO appOnly =
        PrivateConnectivitySetupRequestDTO.builder()
            .domains(List.of("api.example.com"))
            .dns(PrivateConnectivitySetupRequestDTO.DnsConfig.builder()
                     .splitDnsDomains(Map.of("corp.example.com", List.of("10.20.0.10")))
                     .build())
            .build();
    assertThatThrownBy(() -> PrivateConnectivityValidator.normalize(appOnly))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("requires an advertised private route");
  }
}
