/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.provisioner;

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
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityOperationType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;
import io.harness.ng.privateconnectivity.sanitizer.ReleaseSanitizer;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityChildCredentialService;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient.CreateOutcome;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient.ProviderAdminCredential;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient.ProviderCreateException;
import io.harness.oidc.config.OidcConfigurationUtility;
import io.harness.oidc.jwks.OidcJwksUtility;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InOrder;

public class CreateOnceNetworkProvisionerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account";
  private static final String NETWORK_REF = "tailnet";

  private ProviderNetworkClient providerClient;
  private PrivateConnectivityConfigRepository repository;
  private PrivateConnectivityChildCredentialService childCredentialService;
  private CreateOnceNetworkProvisioner provisioner;

  @Before
  public void setUp() {
    providerClient = mock(ProviderNetworkClient.class);
    ReleaseSanitizer releaseSanitizer = mock(ReleaseSanitizer.class);
    repository = mock(PrivateConnectivityConfigRepository.class);
    OidcConfigurationUtility oidcConfigurationUtility = mock(OidcConfigurationUtility.class);
    OidcJwksUtility oidcJwksUtility = mock(OidcJwksUtility.class);
    childCredentialService = mock(PrivateConnectivityChildCredentialService.class);
    provisioner = new CreateOnceNetworkProvisioner(providerClient, releaseSanitizer, repository,
        oidcConfigurationUtility, oidcJwksUtility, childCredentialService);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.empty());
    when(childCredentialService.secretIdentifier(any()))
        .thenAnswer(invocation -> "secret-" + invocation.getArgument(0));
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldPersistRecoveryIdentityBeforeOutcomeUnknownCreateAndNeverBlindlyRetry() {
    PrivateConnectivityConfig config = provisioningConfig();
    ProviderCreateException failure = new ProviderCreateException("response lost", CreateOutcome.OUTCOME_UNKNOWN);
    when(providerClient.createNetwork(any())).thenThrow(failure);

    assertThatThrownBy(() -> provisioner.ensureBound(ACCOUNT_ID, subnetRequest(), config)).isSameAs(failure);

    InOrder order = inOrder(repository, providerClient);
    order.verify(repository).save(config);
    order.verify(providerClient).createNetwork(config.getProviderNetworkName());
    assertThat(config.getProviderNetworkName()).startsWith("pc-account-");
    assertThat(config.getProviderTailnetOAuthSecretRef()).isEqualTo("secret-" + config.getProviderNetworkName());
    verify(providerClient, never()).deleteNetwork(any());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldCompensateWithInMemoryChildCredentialWhenSecretPersistenceFails() {
    PrivateConnectivityConfig config = provisioningConfig();
    ProviderAdminCredential childCredential = new ProviderAdminCredential("child-client", "child-secret");
    when(providerClient.createNetwork(any()))
        .thenReturn(new ProviderNetworkClient.NetworkCreateResult(NETWORK_REF, childCredential));
    IllegalStateException failure = new IllegalStateException("secret store unavailable");
    when(childCredentialService.store(eq(ACCOUNT_ID), any(), eq("child-client"), eq("child-secret")))
        .thenThrow(failure);

    assertThatThrownBy(() -> provisioner.ensureBound(ACCOUNT_ID, subnetRequest(), config)).isSameAs(failure);

    InOrder order = inOrder(childCredentialService, providerClient);
    order.verify(childCredentialService).store(eq(ACCOUNT_ID), any(), eq("child-client"), eq("child-secret"));
    order.verify(providerClient).deleteNetwork(NETWORK_REF, childCredential);
    order.verify(childCredentialService).delete(ACCOUNT_ID, config.getProviderTailnetOAuthSecretRef());
    assertThat(config.getProviderNetworkRef()).isNull();
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldFailClosedForMultipleAmbiguousMatchesAndRetainRecoveryName() {
    PrivateConnectivityConfig config = provisioningConfig();
    config.setProviderNetworkName("pc-account-12345");
    config.setProviderTailnetOAuthSecretRef("secret-pc-account-12345");
    when(providerClient.findNetworksByName("pc-account-12345"))
        .thenReturn(List.of(
            new ProviderNetworkClient.RecoverableNetwork("one"), new ProviderNetworkClient.RecoverableNetwork("two")));

    assertThatThrownBy(() -> provisioner.reconcileAmbiguousCreate(config))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Multiple provider networks");

    assertThat(config.getProviderNetworkName()).isEqualTo("pc-account-12345");
    verify(providerClient, never()).deleteNetwork(any(), any(ProviderAdminCredential.class));
    verify(childCredentialService, never()).delete(any(), any());
  }

  private static PrivateConnectivityConfig provisioningConfig() {
    return PrivateConnectivityConfig.builder()
        .accountIdentifier(ACCOUNT_ID)
        .status(PrivateConnectivityStatus.PROVISIONING)
        .operationType(PrivateConnectivityOperationType.PROVISION)
        .build();
  }

  private static PrivateConnectivitySetupRequestDTO subnetRequest() {
    return PrivateConnectivitySetupRequestDTO.builder().advertiseRoutes(List.of("10.10.0.0/16")).build();
  }
}
