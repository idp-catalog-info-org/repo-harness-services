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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityCredentialDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityOperationType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityPublicStatus;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.ng.privateconnectivity.config.PrivateConnectivityOrgConfig;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;
import io.harness.ng.privateconnectivity.provisioner.CreateOnceNetworkProvisioner;
import io.harness.ng.privateconnectivity.services.PrivateConnectivityConflictException;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityAccountLock;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityChildCredentialService;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityMetrics;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrivateConnectivityServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account";
  private static final String NETWORK_REF = "tailnet";
  private static final String NETWORK_NAME = "pc-account-12345";
  private static final String CHILD_SECRET_REF = "__INTERNAL_pc_tailnet_oauth_pc_account_12345__";

  private PrivateConnectivityConfigRepository repository;
  private FeatureFlagService featureFlagService;
  private CreateOnceNetworkProvisioner provisioner;
  private ProviderNetworkClient providerClient;
  private PrivateConnectivityAccountLock accountLock;
  private PrivateConnectivityChildCredentialService childCredentialService;
  private PrivateConnectivityServiceImpl service;

  @Before
  public void setUp() {
    repository = mock(PrivateConnectivityConfigRepository.class);
    featureFlagService = mock(FeatureFlagService.class);
    provisioner = mock(CreateOnceNetworkProvisioner.class);
    providerClient = mock(ProviderNetworkClient.class);
    accountLock = mock(PrivateConnectivityAccountLock.class);
    ExecutorService operationExecutor = mock(ExecutorService.class);
    PrivateConnectivityInternalQueries internalQueries = mock(PrivateConnectivityInternalQueries.class);
    PrivateConnectivityMetrics metrics = mock(PrivateConnectivityMetrics.class);
    childCredentialService = mock(PrivateConnectivityChildCredentialService.class);
    PrivateConnectivityOrgConfig orgConfig = PrivateConnectivityOrgConfig.builder()
                                                 .orgOAuthClientId("org-client")
                                                 .orgOAuthClientSecret("org-secret")
                                                 .organizationIdentity("organization")
                                                 .build();
    service = new PrivateConnectivityServiceImpl(repository, featureFlagService, provisioner, providerClient,
        accountLock, operationExecutor, internalQueries, orgConfig, metrics, childCredentialService);

    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_CLOUD_PRIVATE_CONNECTIVITY, ACCOUNT_ID)).thenReturn(true);
    when(accountLock.executeOrConflict(eq(ACCOUNT_ID), any())).thenAnswer(invocation -> {
      Supplier<?> operation = invocation.getArgument(1);
      return operation.get();
    });
    when(accountLock.executeWithWaitOrConflict(eq(ACCOUNT_ID), any(Duration.class), any())).thenAnswer(invocation -> {
      Supplier<?> operation = invocation.getArgument(2);
      return operation.get();
    });
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldReturnIdempotentSetupWithoutMintingOrReplayingCredential() {
    PrivateConnectivityConfig config = completeBinding();
    when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(config));
    when(childCredentialService.load(ACCOUNT_ID, CHILD_SECRET_REF))
        .thenReturn(new PrivateConnectivityChildCredentialService.ChildCredential("child-client", "child-secret"));

    var response = service.setup(
        ACCOUNT_ID, PrivateConnectivitySetupRequestDTO.builder().advertiseRoutes(List.of("10.10.0.0/16")).build());

    assertThat(response.getStatus()).isEqualTo(PrivateConnectivityPublicStatus.READY);
    assertThat(response.getCredential()).isNull();
    verify(provisioner, never()).ensureBound(any(), any(), any());
    verify(providerClient, never()).createJoinCredential(any(), any(), any());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldFailClosedWhenAnIdempotentBindingIsIncomplete() {
    PrivateConnectivityConfig config = completeBinding();
    config.setWifAudience(null);
    when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(config));

    assertThatThrownBy(
        ()
            -> service.setup(ACCOUNT_ID,
                PrivateConnectivitySetupRequestDTO.builder().advertiseRoutes(List.of("10.10.0.0/16")).build()))
        .isInstanceOf(PrivateConnectivityConflictException.class);
    verify(provisioner, never()).ensureBound(any(), any(), any());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldFailClosedForEveryInProgressOrFailedBindingState() {
    PrivateConnectivitySetupRequestDTO request =
        PrivateConnectivitySetupRequestDTO.builder().advertiseRoutes(List.of("10.10.0.0/16")).build();
    for (PrivateConnectivityStatus status :
        EnumSet.of(PrivateConnectivityStatus.PROVISIONING, PrivateConnectivityStatus.RECONCILING,
            PrivateConnectivityStatus.RELEASING, PrivateConnectivityStatus.ERROR)) {
      PrivateConnectivityConfig config = completeBinding();
      config.setStatus(status);
      when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(config));

      assertThatThrownBy(() -> service.setup(ACCOUNT_ID, request))
          .as("setup must fail closed while status=%s", status)
          .isInstanceOf(PrivateConnectivityConflictException.class);
    }
    verify(provisioner, never()).ensureBound(any(), any(), any());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldMintAndTrackAnIndependentCredentialOnEveryCredentialCall() {
    PrivateConnectivityConfig config = completeBinding();
    when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(config));
    when(providerClient.createJoinCredential(eq(NETWORK_REF), any(), any()))
        .thenReturn(new ProviderNetworkClient.JoinCredentialInfo("key-1", "secret-1", 100L, true, true))
        .thenReturn(new ProviderNetworkClient.JoinCredentialInfo("key-2", "secret-2", 200L, true, true));

    PrivateConnectivityCredentialDTO first = service.getCredential(ACCOUNT_ID);
    PrivateConnectivityCredentialDTO second = service.getCredential(ACCOUNT_ID);

    assertThat(first.getAuthKey()).isEqualTo("secret-1");
    assertThat(second.getAuthKey()).isEqualTo("secret-2");
    assertThat(config.getCustomerJoinKeyIds()).containsExactly("key-1", "key-2");
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldRevokeNewCredentialWhenProviderIdCannotBePersisted() {
    PrivateConnectivityConfig config = completeBinding();
    when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(config));
    when(providerClient.createJoinCredential(eq(NETWORK_REF), any(), any()))
        .thenReturn(new ProviderNetworkClient.JoinCredentialInfo("key-1", "secret-1", 100L, true, true));
    when(repository.save(any())).thenReturn(config).thenThrow(new IllegalStateException("mongo unavailable"));

    assertThatThrownBy(() -> service.getCredential(ACCOUNT_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mongo unavailable");
    verify(providerClient).revokeJoinCredentials(NETWORK_REF, List.of("key-1"));
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldPersistReplacementWithoutMintingCredential() {
    PrivateConnectivityConfig config = completeBinding();
    when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(config));

    var response = service.updateConfig(
        ACCOUNT_ID, PrivateConnectivitySetupRequestDTO.builder().domains(List.of("api.example.com")).build());

    assertThat(response.getStatus()).isEqualTo(PrivateConnectivityPublicStatus.UPDATING);
    assertThat(config.getStatus()).isEqualTo(PrivateConnectivityStatus.RECONCILING);
    assertThat(config.getAdvertiseRoutes()).isEmpty();
    assertThat(config.getDomains()).containsExactly("api.example.com");
    verify(providerClient, never()).createJoinCredential(any(), any(), any());
  }

  @Test
  @Owner(developers = DHIRAJ)
  @Category(UnitTests.class)
  public void shouldRejectDisabledSetupBeforeProviderMutationButPermitReleasePreparation() {
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_CLOUD_PRIVATE_CONNECTIVITY, ACCOUNT_ID)).thenReturn(false);

    assertThatThrownBy(
        ()
            -> service.setup(ACCOUNT_ID,
                PrivateConnectivitySetupRequestDTO.builder().advertiseRoutes(List.of("10.10.0.0/16")).build()))
        .hasMessageContaining("not enabled");
    verify(provisioner, never()).ensureBound(any(), any(), any());

    PrivateConnectivityConfig config = completeBinding();
    when(repository.findByAccountIdentifier(ACCOUNT_ID)).thenReturn(Optional.of(config));
    assertThat(service.release(ACCOUNT_ID)).isTrue();
    assertThat(config.getStatus()).isEqualTo(PrivateConnectivityStatus.RELEASING);
    assertThat(config.getOperationType()).isEqualTo(PrivateConnectivityOperationType.RELEASE);
  }

  private static PrivateConnectivityConfig completeBinding() {
    PrivateConnectivityOrgConfig orgConfig = PrivateConnectivityOrgConfig.builder()
                                                 .orgOAuthClientId("org-client")
                                                 .orgOAuthClientSecret("org-secret")
                                                 .organizationIdentity("organization")
                                                 .build();
    return PrivateConnectivityConfig.builder()
        .accountIdentifier(ACCOUNT_ID)
        .status(PrivateConnectivityStatus.PROVISIONED)
        .advertiseRoutes(List.of("10.10.0.0/16"))
        .domains(List.of())
        .providerNetworkRef(NETWORK_REF)
        .providerNetworkName(NETWORK_NAME)
        .providerConfigurationFingerprint(orgConfig.configurationFingerprint())
        .providerTailnetOAuthClientId("child-client")
        .providerTailnetOAuthSecretRef(CHILD_SECRET_REF)
        .wifCredentialId("wif")
        .wifClientId("wif-client")
        .wifAudience("audience")
        .build();
  }
}
