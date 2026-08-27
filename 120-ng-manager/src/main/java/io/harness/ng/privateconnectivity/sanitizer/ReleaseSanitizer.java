/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.sanitizer;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityOperationType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityReleasePhase;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityChildCredentialService;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityLifecycle;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityMetrics;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Durable, linear release FSM shared by admin deletion and provisioning compensation. A phase is
 * persisted only after its mandatory operation succeeds. No failed vendor
 * operation is treated as best effort and no cleanup pointer is discarded automatically.
 */
@OwnedBy(CI)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class ReleaseSanitizer {
  private final PrivateConnectivityConfigRepository repository;
  private final ProviderNetworkClient providerNetworkClient;
  private final PrivateConnectivityMetrics metrics;
  private final PrivateConnectivityChildCredentialService childCredentialService;

  public void sanitize(String accountIdentifier) {
    Optional<PrivateConnectivityConfig> optional = repository.findByAccountIdentifier(accountIdentifier);
    if (optional.isEmpty()) {
      log.debug("Private Connectivity release found no binding account={}", accountIdentifier);
      return;
    }
    PrivateConnectivityConfig config = optional.get();
    if (PrivateConnectivityStatus.RELEASING != config.getStatus()) {
      log.warn("Private Connectivity release refused account={} status={} expectedStatus={}", accountIdentifier,
          config.getStatus(), PrivateConnectivityStatus.RELEASING);
      return;
    }
    log.info("Private Connectivity release worker started account={} completedPhase={} networkRefPresent={}",
        accountIdentifier, config.getReleasePhase(), StringUtils.isNotBlank(config.getProviderNetworkRef()));

    try {
      runPhase(config, PrivateConnectivityReleasePhase.TRAFFIC_CUT, () -> {
        if (StringUtils.isNotBlank(config.getProviderNetworkRef())) {
          applyDenyAllOrConfirmNetworkAbsent(config);
        }
      });
      runPhase(config, PrivateConnectivityReleasePhase.CREDENTIALS_REVOKED, () -> revokeCredentials(config));
      runPhase(config, PrivateConnectivityReleasePhase.NETWORK_REMOVED, () -> {
        if (StringUtils.isNotBlank(config.getProviderNetworkRef())) {
          clearDnsOrConfirmNetworkAbsent(config);
          runOrConfirmNetworkAbsent(config, () -> {
            List<String> deviceIds = providerNetworkClient.listDeviceIds(config.getProviderNetworkRef());
            if (!deviceIds.isEmpty()) {
              providerNetworkClient.deleteDevices(config.getProviderNetworkRef(), deviceIds);
            }
          });
          providerNetworkClient.deleteNetwork(config.getProviderNetworkRef());
          childCredentialService.delete(config.getAccountIdentifier(), config.getProviderTailnetOAuthSecretRef());
        } else if (StringUtils.isNotBlank(config.getProviderTailnetOAuthSecretRef())) {
          throw new ReleaseSanitizerException(
              "Cannot delete the provider child credential before network absence is confirmed");
        }
      });
      // Always persist the final local transition. If the provider cleanup and child-secret delete
      // succeeded but this save failed, recordFailure may durably retain UNBOUND + RELEASING. The
      // next retry must finish the local transition instead of treating UNBOUND as already complete.
      config.setCustomerJoinKeyIds(null);
      config.setHelperJoinKeyIds(null);
      config.setAdvertiseRoutes(null);
      config.setDomains(null);
      config.setSplitDnsDomains(null);
      config.setWifCredentialId(null);
      config.setWifClientId(null);
      config.setWifAudience(null);
      config.setPendingWifOperationDescription(null);
      config.setPendingCustomerKeyOperationDescription(null);
      config.setPendingHelperKeyOperationDescription(null);
      config.setProviderNetworkRef(null);
      config.setProviderTailnetOAuthClientId(null);
      config.setProviderTailnetOAuthSecretRef(null);
      config.setProviderNetworkName(null);
      config.setProviderConfigurationFingerprint(null);
      config.setReleasePhase(PrivateConnectivityReleasePhase.UNBOUND);
      config.setStatus(PrivateConnectivityStatus.NOT_PROVISIONED);
      config.setOperationType(null);
      config.setRetryCount(0);
      config.setNextRetryAt(null);
      config.setLastError(null);
      repository.save(config);
      log.info("Private Connectivity release completed account={}", accountIdentifier);
    } catch (Exception exception) {
      recordFailure(config, exception);
      throw exception instanceof ReleaseSanitizerException
          ? (ReleaseSanitizerException) exception
          : new ReleaseSanitizerException("Release failed for account=" + accountIdentifier, exception);
    }
  }

  private void reconcilePendingJoinCredentialCreates(PrivateConnectivityConfig config) {
    String customerOperation = config.getPendingCustomerKeyOperationDescription();
    String helperOperation = config.getPendingHelperKeyOperationDescription();
    if (StringUtils.isAllBlank(customerOperation, helperOperation)) {
      return;
    }
    requireProviderRef(config, "reconcile pending join credential creates");
    if (StringUtils.isNotBlank(customerOperation)) {
      runOrConfirmNetworkAbsent(config,
          () -> providerNetworkClient.reconcileCredentialCreate(config.getProviderNetworkRef(), customerOperation));
      config.setPendingCustomerKeyOperationDescription(null);
      repository.save(config);
    }
    if (StringUtils.isNotBlank(helperOperation)) {
      runOrConfirmNetworkAbsent(config,
          () -> providerNetworkClient.reconcileCredentialCreate(config.getProviderNetworkRef(), helperOperation));
      config.setPendingHelperKeyOperationDescription(null);
      repository.save(config);
    }
  }

  private void revokeCredentials(PrivateConnectivityConfig config) {
    if (StringUtils.isNotBlank(config.getPendingWifOperationDescription())) {
      requireProviderRef(config, "reconcile pending WIF credential create");
      runOrConfirmNetworkAbsent(config,
          ()
              -> providerNetworkClient.reconcileCredentialCreate(
                  config.getProviderNetworkRef(), config.getPendingWifOperationDescription()));
      config.setPendingWifOperationDescription(null);
    }
    if (StringUtils.isNotBlank(config.getWifCredentialId())) {
      requireProviderRef(config, "delete WIF credential");
      runOrConfirmNetworkAbsent(config,
          () -> providerNetworkClient.deleteWifCredential(config.getProviderNetworkRef(), config.getWifCredentialId()));
    }
    reconcilePendingJoinCredentialCreates(config);
    LinkedHashSet<String> trackedKeyIds = new LinkedHashSet<>();
    if (config.getCustomerJoinKeyIds() != null) {
      trackedKeyIds.addAll(config.getCustomerJoinKeyIds());
    }
    if (config.getHelperJoinKeyIds() != null) {
      trackedKeyIds.addAll(config.getHelperJoinKeyIds());
    }
    List<String> keyIds = List.copyOf(trackedKeyIds);
    if (!keyIds.isEmpty()) {
      requireProviderRef(config, "revoke join credentials");
      runOrConfirmNetworkAbsent(
          config, () -> providerNetworkClient.revokeJoinCredentials(config.getProviderNetworkRef(), keyIds));
    }
  }

  private void runPhase(PrivateConnectivityConfig config, PrivateConnectivityReleasePhase phase, Runnable action) {
    if (completed(config, phase)) {
      log.info("Private Connectivity release phase already complete account={} requestedPhase={} completedPhase={}",
          config.getAccountIdentifier(), phase, config.getReleasePhase());
      return;
    }
    log.info("Private Connectivity release phase started account={} phase={}", config.getAccountIdentifier(), phase);
    action.run();
    config.setReleasePhase(phase);
    config.setStatus(PrivateConnectivityStatus.RELEASING);
    config.setLastError(null);
    repository.save(config);
    log.info("Private Connectivity release phase completed account={} phase={}", config.getAccountIdentifier(), phase);
  }

  private static boolean completed(PrivateConnectivityConfig config, PrivateConnectivityReleasePhase requestedPhase) {
    return config.getReleasePhase() != null && config.getReleasePhase().ordinal() >= requestedPhase.ordinal();
  }

  private static void requireProviderRef(PrivateConnectivityConfig config, String operation) {
    if (StringUtils.isBlank(config.getProviderNetworkRef())) {
      throw new ReleaseSanitizerException(
          "Cannot " + operation + " without providerNetworkRef for account=" + config.getAccountIdentifier());
    }
  }

  private void applyDenyAllOrConfirmNetworkAbsent(PrivateConnectivityConfig config) {
    runOrConfirmNetworkAbsent(config, () -> {
      ProviderNetworkClient.NetworkPolicy denyAll = new ProviderNetworkClient.NetworkPolicy(DENY_ALL_ACL);
      providerNetworkClient.validatePolicy(config.getProviderNetworkRef(), denyAll);
      providerNetworkClient.applyPolicy(config.getProviderNetworkRef(), denyAll);
    });
  }

  private void clearDnsOrConfirmNetworkAbsent(PrivateConnectivityConfig config) {
    runOrConfirmNetworkAbsent(config,
        ()
            -> providerNetworkClient.configureDns(
                config.getProviderNetworkRef(), ProviderNetworkClient.DnsConfig.cleared()));
  }

  private void runOrConfirmNetworkAbsent(PrivateConnectivityConfig config, Runnable operation) {
    try {
      operation.run();
    } catch (RuntimeException operationFailure) {
      try {
        if (!providerNetworkClient.networkExists(config.getProviderNetworkRef())) {
          return;
        }
      } catch (RuntimeException confirmationFailure) {
        operationFailure.addSuppressed(confirmationFailure);
      }
      throw operationFailure;
    }
  }

  private void recordFailure(PrivateConnectivityConfig config, Exception exception) {
    int failures = config.getRetryCount() == null ? 1 : config.getRetryCount() + 1;
    boolean interventionRequired = PrivateConnectivityLifecycle.requiresIntervention(failures);
    // A transient cleanup error must not present the release as terminally failed on its first
    // attempt. Keep it visibly in progress until the alert threshold;
    // the durable phase and backoff still prevent any unsafe forward progress.
    config.setStatus(interventionRequired ? PrivateConnectivityStatus.ERROR : PrivateConnectivityStatus.RELEASING);
    config.setOperationType(PrivateConnectivityOperationType.RELEASE);
    config.setRetryCount(failures);
    config.setNextRetryAt(PrivateConnectivityLifecycle.nextRetryAtMillis(failures));
    config.setLastError(interventionRequired
            ? "Release repeatedly failed at " + nextPhase(config) + "; contact Harness Support"
            : "Release is waiting to retry phase " + nextPhase(config));
    try {
      repository.save(config);
    } catch (Exception persistenceException) {
      exception.addSuppressed(persistenceException);
      log.error("Private Connectivity release failed to persist retry state account={}", config.getAccountIdentifier(),
          persistenceException);
    }
    if (interventionRequired) {
      log.error("Private Connectivity release account={} has failed {} times at phase={}; SRE intervention required",
          config.getAccountIdentifier(), failures, nextPhase(config));
    }
    metrics.releaseFailure(config.getReleasePhase(), interventionRequired);
  }

  private static String nextPhase(PrivateConnectivityConfig config) {
    PrivateConnectivityReleasePhase current = config.getReleasePhase();
    if (current == null) {
      return PrivateConnectivityReleasePhase.FENCED.name();
    }
    int next = Math.min(current.ordinal() + 1, PrivateConnectivityReleasePhase.UNBOUND.ordinal());
    return PrivateConnectivityReleasePhase.values()[next].name();
  }

  private static final String DENY_ALL_ACL = "{\"grants\":[],\"tagOwners\":{}}";

  public static class ReleaseSanitizerException extends RuntimeException {
    public ReleaseSanitizerException(String message) {
      super(message);
    }

    public ReleaseSanitizerException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
