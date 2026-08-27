/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.services.impl;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityInternalDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.ng.privateconnectivity.config.PrivateConnectivityOrgConfig;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityHelpers;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** Read path for CI-internal private connectivity state. No customer-path probing occurs here. */
@OwnedBy(CI)
@Singleton
@Slf4j
public class PrivateConnectivityInternalQueries {
  private final PrivateConnectivityConfigRepository repository;
  private final FeatureFlagService featureFlagService;
  private final PrivateConnectivityOrgConfig orgConfig;

  @Inject
  public PrivateConnectivityInternalQueries(PrivateConnectivityConfigRepository repository,
      FeatureFlagService featureFlagService, PrivateConnectivityOrgConfig orgConfig) {
    this.repository = repository;
    this.featureFlagService = featureFlagService;
    this.orgConfig = orgConfig;
  }

  public PrivateConnectivityInternalDTO getInternal(String accountIdentifier) {
    boolean ffEnabled =
        featureFlagService.isEnabled(FeatureName.CI_ENABLE_CLOUD_PRIVATE_CONNECTIVITY, accountIdentifier);
    if (!ffEnabled) {
      log.info("Private Connectivity internal state account={} cloudFlagEnabled=false", accountIdentifier);
      return PrivateConnectivityInternalDTO.builder().build();
    }
    Optional<PrivateConnectivityConfig> optConfig = repository.findByAccountIdentifier(accountIdentifier);
    if (optConfig.isEmpty()) {
      log.info("Private Connectivity internal state account={} cloudFlagEnabled=true bindingPresent=false",
          accountIdentifier);
      return PrivateConnectivityInternalDTO.builder().build();
    }
    PrivateConnectivityConfig config = optConfig.get();
    boolean providerConfigurationMatches = orgConfig.isConfigured()
        && orgConfig.configurationFingerprint().equals(config.getProviderConfigurationFingerprint());
    boolean enrollmentBindingIntact = PrivateConnectivityHelpers.hasCompleteEnrollmentBinding(config);
    boolean controlPlaneReady = PrivateConnectivityStatus.PROVISIONED == config.getStatus() && enrollmentBindingIntact
        && providerConfigurationMatches;
    PrivateConnectivityInternalDTO dto = PrivateConnectivityInternalDTO.builder()
                                             .controlPlaneReady(controlPlaneReady)
                                             .wifClientId(config.getWifClientId())
                                             .wifAudience(config.getWifAudience())
                                             .build();
    log.info("Private Connectivity internal state account={} status={} providerConfigMatches={} "
            + "enrollmentBindingIntact={} controlPlaneReady={}",
        accountIdentifier, config.getStatus(), providerConfigurationMatches, enrollmentBindingIntact,
        controlPlaneReady);
    return dto;
  }
}
