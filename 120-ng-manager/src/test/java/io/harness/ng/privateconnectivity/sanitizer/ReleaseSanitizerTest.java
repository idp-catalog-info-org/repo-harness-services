/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.sanitizer;

import static io.harness.ng.privateconnectivity.sanitizer.ReleaseSanitizer.ReleaseSanitizerException;
import static io.harness.rule.OwnerRule.DHIRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityOperationType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityReleasePhase;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityChildCredentialService;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityMetrics;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InOrder;

public class ReleaseSanitizerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account";
  private static final String NETWORK_REF = "tailnet";
  private static final String SECRET_REF = "__INTERNAL_pc_tailnet_oauth_pc_account_12345__";

  private PrivateConnectivityConfigRepository repository;
  private ProviderNetworkClient providerClient;
  private PrivateConnectivityChildCredentialService childCredentialService;
  private ReleaseSanitizer sanitizer;

  @Before
  public void setUp() {
    repository = mock(PrivateConnectivityConfigRepository.class);
    providerClient = mock(ProviderNetworkClient.class);
    PrivateConnectivityMetrics metrics = mock(PrivateConnectivityMetrics.class);
    childCredentialService = mock(PrivateConnectivityChildCredentialService.class);
    sanitizer = new ReleaseSanitizer(repository, providerClient, metrics, childCredentialService);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldExecuteDurableReleaseInOrderAndDeduplicateTrackedKeys() {
    PrivateConnectivityConfig config = releasingBinding();
    when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(config));
    when(providerClient.listDeviceIds(NETWORK_REF)).thenReturn(List.of("device-1", "device-2"));

    sanitizer.sanitize(ACCOUNT_ID);

    InOrder order = inOrder(providerClient, childCredentialService);
    order.verify(providerClient).validatePolicy(eq(NETWORK_REF), any());
    order.verify(providerClient).applyPolicy(eq(NETWORK_REF), any());
    order.verify(providerClient).deleteWifCredential(NETWORK_REF, "wif");
    order.verify(providerClient).revokeJoinCredentials(NETWORK_REF, List.of("customer-1", "shared", "helper-1"));
    order.verify(providerClient).configureDns(eq(NETWORK_REF), any());
    order.verify(providerClient).listDeviceIds(NETWORK_REF);
    order.verify(providerClient).deleteDevices(NETWORK_REF, List.of("device-1", "device-2"));
    order.verify(providerClient).deleteNetwork(NETWORK_REF);
    order.verify(childCredentialService).delete(ACCOUNT_ID, SECRET_REF);

    assertThat(config.getStatus()).isEqualTo(PrivateConnectivityStatus.NOT_PROVISIONED);
    assertThat(config.getReleasePhase()).isEqualTo(PrivateConnectivityReleasePhase.UNBOUND);
    assertThat(config.getProviderNetworkRef()).isNull();
    assertThat(config.getCustomerJoinKeyIds()).isNull();
    assertThat(config.getHelperJoinKeyIds()).isNull();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldNotAdvanceWhenTrafficCutFails() {
    PrivateConnectivityConfig config = releasingBinding();
    when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(config));
    RuntimeException failure = new RuntimeException("provider unavailable");
    org.mockito.Mockito.doThrow(failure).when(providerClient).validatePolicy(eq(NETWORK_REF), any());
    when(providerClient.networkExists(NETWORK_REF)).thenReturn(true);

    assertThatThrownBy(() -> sanitizer.sanitize(ACCOUNT_ID))
        .isInstanceOf(ReleaseSanitizerException.class)
        .hasCause(failure);

    assertThat(config.getReleasePhase()).isEqualTo(PrivateConnectivityReleasePhase.FENCED);
    assertThat(config.getStatus()).isEqualTo(PrivateConnectivityStatus.RELEASING);
    assertThat(config.getRetryCount()).isEqualTo(1);
    verify(providerClient, never()).revokeJoinCredentials(any(), any());
    verify(providerClient, never()).deleteNetwork(any());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldBlockNetworkDeletionWhenDeviceInventoryIsMalformed() {
    PrivateConnectivityConfig config = releasingBinding();
    when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(config));
    when(providerClient.listDeviceIds(NETWORK_REF))
        .thenThrow(new ProviderNetworkClient.ProviderNetworkException("incomplete device inventory"));
    when(providerClient.networkExists(NETWORK_REF)).thenReturn(true);

    assertThatThrownBy(() -> sanitizer.sanitize(ACCOUNT_ID))
        .isInstanceOf(ReleaseSanitizerException.class)
        .hasMessageContaining("Release failed");

    assertThat(config.getReleasePhase()).isEqualTo(PrivateConnectivityReleasePhase.CREDENTIALS_REVOKED);
    verify(providerClient, never()).deleteNetwork(any());
    verify(childCredentialService, never()).delete(any(), any());
  }

  private static PrivateConnectivityConfig releasingBinding() {
    return PrivateConnectivityConfig.builder()
        .accountIdentifier(ACCOUNT_ID)
        .status(PrivateConnectivityStatus.RELEASING)
        .operationType(PrivateConnectivityOperationType.RELEASE)
        .releasePhase(PrivateConnectivityReleasePhase.FENCED)
        .providerNetworkRef(NETWORK_REF)
        .providerTailnetOAuthSecretRef(SECRET_REF)
        .wifCredentialId("wif")
        .customerJoinKeyIds(List.of("customer-1", "shared"))
        .helperJoinKeyIds(List.of("shared", "helper-1"))
        .retryCount(0)
        .build();
  }
}
