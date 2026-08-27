/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.services.impl;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ng.privateconnectivity.util.PrivateConnectivityLifecycle.PROVISIONING_STALE_AFTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityAdminResponseDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityCredentialDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityHelperCredentialDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityInternalDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityOperationType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityPublicStatus;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityResponseDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityRouterType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupResponseDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.ng.privateconnectivity.config.PrivateConnectivityOrgConfig;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;
import io.harness.ng.privateconnectivity.provisioner.CreateOnceNetworkProvisioner;
import io.harness.ng.privateconnectivity.provisioner.CreateOnceNetworkProvisioner.ProvisionResult;
import io.harness.ng.privateconnectivity.services.PrivateConnectivityConflictException;
import io.harness.ng.privateconnectivity.services.PrivateConnectivityService;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityAccountLock;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityAclFactory;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityChildCredentialService;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityHelpers;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityLifecycle;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityMetrics;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient.CreateOutcome;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient.ProviderCreateException;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Private connectivity service implementation.
 *
 * Vendor bind/release goes through {@link CreateOnceNetworkProvisioner}.
 * CI-internal inject state is read via {@link PrivateConnectivityInternalQueries}.
 */
@OwnedBy(CI)
@Singleton
@Slf4j
public class PrivateConnectivityServiceImpl implements PrivateConnectivityService {
  private static final Duration CUSTOMER_CREDENTIAL_LOCK_WAIT = Duration.ofSeconds(30);

  private final PrivateConnectivityConfigRepository repository;
  private final FeatureFlagService featureFlagService;
  private final CreateOnceNetworkProvisioner networkProvisioner;
  private final ProviderNetworkClient providerNetworkClient;
  private final PrivateConnectivityAccountLock accountLock;
  private final ExecutorService operationExecutor;
  private final PrivateConnectivityInternalQueries internalQueries;
  private final PrivateConnectivityOrgConfig orgConfig;
  private final PrivateConnectivityMetrics metrics;
  private final PrivateConnectivityChildCredentialService childCredentialService;

  @Inject
  public PrivateConnectivityServiceImpl(PrivateConnectivityConfigRepository repository,
      FeatureFlagService featureFlagService, CreateOnceNetworkProvisioner networkProvisioner,
      ProviderNetworkClient providerNetworkClient, PrivateConnectivityAccountLock accountLock,
      @Named("privateConnectivityOperationExecutor") ExecutorService operationExecutor,
      PrivateConnectivityInternalQueries internalQueries, PrivateConnectivityOrgConfig orgConfig,
      PrivateConnectivityMetrics metrics, PrivateConnectivityChildCredentialService childCredentialService) {
    this.repository = repository;
    this.featureFlagService = featureFlagService;
    this.networkProvisioner = networkProvisioner;
    this.providerNetworkClient = providerNetworkClient;
    this.accountLock = accountLock;
    this.operationExecutor = operationExecutor;
    this.internalQueries = internalQueries;
    this.orgConfig = orgConfig;
    this.metrics = metrics;
    this.childCredentialService = childCredentialService;
  }

  @Override
  public PrivateConnectivitySetupResponseDTO setup(
      String accountIdentifier, PrivateConnectivitySetupRequestDTO request) {
    log.info("Private Connectivity setup waiting for account lock account={}", accountIdentifier);
    return withAccountLock(accountIdentifier, () -> setupInternal(accountIdentifier, request));
  }

  private PrivateConnectivitySetupResponseDTO setupInternal(
      String accountIdentifier, PrivateConnectivitySetupRequestDTO request) {
    assertFeatureEnabled(accountIdentifier);
    if (!orgConfig.isConfigured()) {
      throw new InvalidRequestException("Private Connectivity provider configuration requires organization OAuth "
          + "credentials, the exact organizationIdentity "
          + "and the official https://api.tailscale.com API origin");
    }
    request = PrivateConnectivityValidator.normalize(request);
    PrivateConnectivityRouterType mode =
        PrivateConnectivityRouterType.fromConfiguration(request.getAdvertiseRoutes(), request.getDomains());

    Optional<PrivateConnectivityConfig> existing = repository.findByAccountIdentifier(accountIdentifier);
    log.info("Private Connectivity setup validated account={} mode={} bindingPresent={} status={}", accountIdentifier,
        mode, existing.isPresent(), existing.map(PrivateConnectivityConfig::getStatus).orElse(null));

    if (existing.isPresent()) {
      PrivateConnectivityConfig cfg = existing.get();
      PrivateConnectivityStatus status = cfg.getStatus();
      if (PrivateConnectivityStatus.PROVISIONED == status && configurationMatches(cfg, request)) {
        if (!isIdempotentBindingIntact(cfg)) {
          log.warn("Private Connectivity idempotent setup refused account={} because the durable binding is incomplete "
                  + "or uses different provider configuration",
              accountIdentifier);
          throw new PrivateConnectivityConflictException(
              "Private connectivity is provisioned but its durable binding is not usable. Reconcile or release "
              + "the existing binding before retrying setup.");
        }
        log.info("Private Connectivity setup completed idempotently account={}", accountIdentifier);
        // Credentials were returned once on first setup; re-fetch via /credential.
        return toSetupResponseDTO(cfg, null);
      }
      if (!isSetupStartAllowed(cfg)) {
        throw new PrivateConnectivityConflictException("Private connectivity setup cannot start for account "
            + accountIdentifier + " while status=" + status + " and operation=" + cfg.getOperationType()
            + ". Complete or reconcile the current lifecycle first.");
      }
    }

    PrivateConnectivityConfig config =
        existing.orElse(PrivateConnectivityConfig.builder().accountIdentifier(accountIdentifier).build());
    config.setStatus(PrivateConnectivityStatus.PROVISIONING);
    config.setOperationType(PrivateConnectivityOperationType.PROVISION);
    config.setReleasePhase(null);
    config.setProviderConfigurationFingerprint(orgConfig.configurationFingerprint());
    config.setRetryCount(0);
    config.setNextRetryAt(null);
    config.setAdvertiseRoutes(request.getAdvertiseRoutes());
    config.setDomains(request.getDomains());
    config.setSplitDnsDomains(request.getDns() != null ? request.getDns().getSplitDnsDomains() : null);
    config.setLastError(null);
    log.info("Private Connectivity setup entering provisioning account={} mode={}", accountIdentifier, mode);

    try {
      ProvisionResult result = networkProvisioner.ensureBound(accountIdentifier, request, config);
      metrics.count("provision_success");
      log.info("Private Connectivity setup service completed account={} status={} credentialIssued={}",
          accountIdentifier, result.config().getStatus(), result.credential() != null);
      return toSetupResponseDTO(result.config(), result.credential());
    } catch (Exception e) {
      recordProvisionFailure(e);
      log.error("Private Connectivity provisioning failed account={}", accountIdentifier, e);
      // Reload so a successful compensation or a durable release failure is not overwritten by a
      // stale in-memory PROVISIONING entity.
      PrivateConnectivityConfig latest = repository.findByAccountIdentifier(accountIdentifier).orElse(config);
      if (PrivateConnectivityStatus.PROVISIONING == latest.getStatus()) {
        if (isAmbiguousProvisionCreate(latest)) {
          recordOperationFailure(
              latest, "Provider network create outcome is unknown and requires exact reconciliation");
        } else {
          latest.setStatus(PrivateConnectivityStatus.ERROR);
          latest.setLastError("Provisioning failed; contact Harness Support");
          repository.save(latest);
        }
      }
      if (e instanceof InvalidRequestException invalidRequestException) {
        throw invalidRequestException;
      }
      throw new UnexpectedException("Private connectivity provisioning failed", e);
    }
  }

  @Override
  public PrivateConnectivityResponseDTO get(String accountIdentifier) {
    return repository.findByAccountIdentifier(accountIdentifier)
        .map(this::toResponseDTO)
        .orElse(PrivateConnectivityResponseDTO.builder()
                    .accountIdentifier(accountIdentifier)
                    .status(PrivateConnectivityPublicStatus.NOT_PROVISIONED)
                    .build());
  }

  @Override
  public PrivateConnectivityAdminResponseDTO getAdmin(String accountIdentifier) {
    return repository.findByAccountIdentifier(accountIdentifier)
        .map(this::toAdminResponseDTO)
        .orElse(PrivateConnectivityAdminResponseDTO.builder()
                    .accountIdentifier(accountIdentifier)
                    .status(PrivateConnectivityStatus.NOT_PROVISIONED)
                    .build());
  }

  @Override
  public PrivateConnectivityResponseDTO updateConfig(
      String accountIdentifier, PrivateConnectivitySetupRequestDTO request) {
    PrivateConnectivityResponseDTO response =
        withAccountLock(accountIdentifier, () -> updateConfigInternal(accountIdentifier, request));
    if (PrivateConnectivityPublicStatus.UPDATING == response.getStatus()) {
      submitConfigReconciliation(accountIdentifier);
    }
    return response;
  }

  private PrivateConnectivityResponseDTO updateConfigInternal(
      String accountIdentifier, PrivateConnectivitySetupRequestDTO request) {
    assertFeatureEnabled(accountIdentifier);
    PrivateConnectivityConfig config = getOrThrowNotFound(accountIdentifier);
    assertNotTransient(config);
    assertConfigurationReplacementAllowed(config);
    // PUT is a full replacement. Persist exactly the normalized desired document supplied by the
    // customer; omitted lists become empty rather than silently retaining old routes or domains.
    PrivateConnectivitySetupRequestDTO effectiveRequest = PrivateConnectivityValidator.normalize(request);
    PrivateConnectivityRouterType mode = PrivateConnectivityRouterType.fromConfiguration(
        effectiveRequest.getAdvertiseRoutes(), effectiveRequest.getDomains());

    if (PrivateConnectivityStatus.PROVISIONED == config.getStatus() && configurationMatches(config, effectiveRequest)) {
      log.info("Private Connectivity configuration already matches desired state account={}", accountIdentifier);
      return toResponseDTO(config);
    }

    String ref = config.getProviderNetworkRef();
    if (ref == null) {
      throw new InvalidRequestException("No provider network bound for account " + accountIdentifier);
    }

    // Desired state is durable before any remote mutation. The background reconciler always writes
    // complete ACL and DNS documents and marks the binding READY only after both succeed.
    config.setAdvertiseRoutes(effectiveRequest.getAdvertiseRoutes());
    config.setDomains(effectiveRequest.getDomains());
    config.setSplitDnsDomains(
        effectiveRequest.getDns() != null ? effectiveRequest.getDns().getSplitDnsDomains() : null);
    config.setStatus(PrivateConnectivityStatus.RECONCILING);
    config.setOperationType(PrivateConnectivityOperationType.UPDATE);
    config.setRetryCount(0);
    config.setNextRetryAt(null);
    config.setLastError(null);
    repository.save(config);
    log.info("Private Connectivity desired configuration saved account={} mode={} status={}", accountIdentifier, mode,
        config.getStatus());
    return toResponseDTO(config);
  }

  @Override
  public boolean reconcileConfigIfStuck(String accountIdentifier) {
    return withAccountLock(accountIdentifier, () -> reconcileConfigInternal(accountIdentifier));
  }

  private boolean reconcileConfigInternal(String accountIdentifier) {
    log.info("Private Connectivity configuration reconciliation started account={}", accountIdentifier);
    Optional<PrivateConnectivityConfig> optional = repository.findByAccountIdentifier(accountIdentifier);
    if (optional.isEmpty()) {
      return false;
    }
    PrivateConnectivityConfig config = optional.get();
    if (PrivateConnectivityOperationType.UPDATE != config.getOperationType()
        || (PrivateConnectivityStatus.RECONCILING != config.getStatus()
            && PrivateConnectivityStatus.ERROR != config.getStatus())) {
      return false;
    }
    if (config.getNextRetryAt() != null && config.getNextRetryAt() > System.currentTimeMillis()) {
      return false;
    }
    String ref = config.getProviderNetworkRef();
    if (StringUtils.isBlank(ref)) {
      recordOperationFailure(config, "Reconciliation has no provider network reference");
      return false;
    }
    try {
      PrivateConnectivitySetupRequestDTO desired = fromConfig(config);
      ProviderNetworkClient.NetworkPolicy policy =
          new ProviderNetworkClient.NetworkPolicy(PrivateConnectivityAclFactory.buildAclJson(desired));
      providerNetworkClient.validatePolicy(ref, policy);
      providerNetworkClient.applyPolicy(ref, policy);
      mapDns(ref, desired);

      config.setStatus(PrivateConnectivityStatus.PROVISIONED);
      config.setOperationType(null);
      config.setRetryCount(0);
      config.setNextRetryAt(null);
      config.setLastError(null);
      repository.save(config);
      log.info("Private Connectivity configuration reconciliation completed account={}", accountIdentifier);
      return true;
    } catch (Exception exception) {
      metrics.count("reconcile_failure");
      recordOperationFailure(config, "Configuration reconciliation failed; contact Harness Support");
      log.error("Private Connectivity desired-state reconciliation failed account={}", accountIdentifier, exception);
      return false;
    }
  }

  private void submitConfigReconciliation(String accountIdentifier) {
    try {
      operationExecutor.execute(() -> {
        try {
          reconcileConfigIfStuck(accountIdentifier);
        } catch (Exception exception) {
          log.error("Private Connectivity configuration reconciliation worker failed account={}", accountIdentifier,
              exception);
        }
      });
    } catch (RejectedExecutionException exception) {
      log.error("Private Connectivity configuration reconciliation submission rejected account={}; durable state "
              + "will be retried",
          accountIdentifier, exception);
    }
  }

  private void recordOperationFailure(PrivateConnectivityConfig config, String message) {
    int failures = config.getRetryCount() == null ? 1 : config.getRetryCount() + 1;
    config.setStatus(PrivateConnectivityStatus.ERROR);
    config.setRetryCount(failures);
    config.setNextRetryAt(PrivateConnectivityLifecycle.nextRetryAtMillis(failures));
    config.setLastError(message);
    repository.save(config);
    if (PrivateConnectivityLifecycle.requiresIntervention(failures)) {
      log.error("Private Connectivity operation={} account={} has failed {} times; SRE intervention required",
          config.getOperationType(), config.getAccountIdentifier(), failures);
    }
  }

  @Override
  public boolean resume(String accountIdentifier) {
    Optional<PrivateConnectivityConfig> optional = repository.findByAccountIdentifier(accountIdentifier);
    if (optional.isEmpty()) {
      return false;
    }
    PrivateConnectivityConfig config = optional.get();
    PrivateConnectivityOperationType operationType = config.getOperationType();
    if (PrivateConnectivityOperationType.UPDATE == operationType) {
      submitConfigReconciliation(accountIdentifier);
      return true;
    }
    if (PrivateConnectivityOperationType.RELEASE == operationType) {
      return releaseIfStuck(accountIdentifier);
    }
    if (PrivateConnectivityOperationType.PROVISION == operationType
        && StringUtils.isBlank(config.getProviderNetworkRef())
        && StringUtils.isNotBlank(config.getProviderNetworkName())) {
      return withAccountLock(accountIdentifier, () -> {
        PrivateConnectivityConfig current = getOrThrowNotFound(accountIdentifier);
        return recoverAmbiguousCreate(current);
      });
    }
    return false;
  }

  @Override
  public PrivateConnectivityCredentialDTO getCredential(String accountIdentifier) {
    // Credential creation mutates provider and Mongo state, so calls remain serialized. A
    // concurrent caller waits briefly instead of immediately failing; every successful caller
    // receives an independently created and tracked key.
    return accountLock.executeWithWaitOrConflict(
        accountIdentifier, CUSTOMER_CREDENTIAL_LOCK_WAIT, () -> getCredentialInternal(accountIdentifier));
  }

  @Override
  public PrivateConnectivityHelperCredentialDTO getHelperCredential(String accountIdentifier) {
    return withAccountLock(accountIdentifier, () -> getHelperCredentialInternal(accountIdentifier));
  }

  private PrivateConnectivityHelperCredentialDTO getHelperCredentialInternal(String accountIdentifier) {
    log.info("Private Connectivity helper credential creation started account={}", accountIdentifier);
    assertFeatureEnabled(accountIdentifier);
    PrivateConnectivityConfig config = getOrThrowNotFound(accountIdentifier);
    assertNotTransient(config);
    assertProvisioned(config);
    String ref = config.getProviderNetworkRef();
    if (ref == null) {
      throw new InvalidRequestException("No provider network bound for account " + accountIdentifier);
    }

    List<String> priorKeyIds = config.getHelperJoinKeyIds() == null ? List.of() : config.getHelperJoinKeyIds();
    log.info("Private Connectivity helper credential state account={} priorTrackedKeyCount={}", accountIdentifier,
        priorKeyIds.size());
    if (StringUtils.isBlank(config.getPendingHelperKeyOperationDescription())) {
      config.setPendingHelperKeyOperationDescription(
          PrivateConnectivityHelpers.HELPER_KEY_OPERATION_PREFIX + UUID.randomUUID());
      repository.save(config);
    } else {
      // A description already present on entry means a previous process may have lost the create
      // response. Resolve that rare recovery case before issuing another non-idempotent POST.
      providerNetworkClient.reconcileCredentialCreate(ref, config.getPendingHelperKeyOperationDescription());
    }
    ProviderNetworkClient.JoinCredentialInfo joinCreds = providerNetworkClient.createHelperJoinCredential(
        ref, List.of(PrivateConnectivityHelpers.HELPER_TAG), config.getPendingHelperKeyOperationDescription());
    List<String> helperKeyIds =
        config.getHelperJoinKeyIds() == null ? new ArrayList<>() : new ArrayList<>(config.getHelperJoinKeyIds());
    if (!helperKeyIds.contains(joinCreds.keyId())) {
      helperKeyIds.add(joinCreds.keyId());
    }
    config.setHelperJoinKeyIds(List.copyOf(helperKeyIds));
    config.setPendingHelperKeyOperationDescription(null);
    try {
      repository.save(config);
    } catch (Exception persistenceException) {
      boolean revoked = false;
      try {
        providerNetworkClient.revokeJoinCredentials(ref, List.of(joinCreds.keyId()));
        revoked = true;
      } catch (Exception revokeException) {
        persistenceException.addSuppressed(revokeException);
      }
      if (!revoked) {
        // The pre-create opaque operation description remains durable if this retry also fails.
        // Prefer persisting the concrete key ID so release can revoke it directly.
        try {
          repository.save(config);
        } catch (Exception trackingException) {
          persistenceException.addSuppressed(trackingException);
        }
      }
      throw persistenceException;
    }

    if (!priorKeyIds.isEmpty()) {
      try {
        providerNetworkClient.revokeJoinCredentials(ref, priorKeyIds);
        helperKeyIds.removeAll(priorKeyIds);
        config.setHelperJoinKeyIds(List.copyOf(helperKeyIds));
        repository.save(config);
      } catch (Exception revokeException) {
        boolean newKeyRevoked = false;
        try {
          providerNetworkClient.revokeJoinCredentials(ref, List.of(joinCreds.keyId()));
          newKeyRevoked = true;
        } catch (Exception newKeyRevokeException) {
          revokeException.addSuppressed(newKeyRevokeException);
        }
        if (newKeyRevoked) {
          helperKeyIds.remove(joinCreds.keyId());
          config.setHelperJoinKeyIds(List.copyOf(helperKeyIds));
          try {
            repository.save(config);
          } catch (Exception persistenceException) {
            revokeException.addSuppressed(persistenceException);
          }
        }
        throw new InvalidRequestException(
            "Failed to revoke the prior Private Connectivity helper credential for account " + accountIdentifier
                + "; refusing to return a new helper credential",
            revokeException);
      }
    }

    log.info("Private Connectivity helper credential created account={} providerKeyId={} expiresAt={} "
            + "replacedKeyCount={}",
        accountIdentifier, joinCreds.keyId(), joinCreds.expiresAt(), priorKeyIds.size());
    return PrivateConnectivityHelperCredentialDTO.builder()
        .type("AUTH_KEY")
        .value(joinCreds.authKey())
        .expiresAt(joinCreds.expiresAt())
        .reusable(joinCreds.reusable())
        .preauthorized(joinCreds.preauthorized())
        .build();
  }

  private void recordProvisionFailure(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof ProviderCreateException providerCreateException) {
        metrics.count(providerCreateException.getOutcome() == CreateOutcome.DEFINITELY_NOT_CREATED
                ? "provision_failure_definitive"
                : "provision_failure_ambiguous");
        return;
      }
      current = current.getCause();
    }
    metrics.count("provision_failure_other");
  }

  private PrivateConnectivityCredentialDTO getCredentialInternal(String accountIdentifier) {
    assertFeatureEnabled(accountIdentifier);
    PrivateConnectivityConfig config = getOrThrowNotFound(accountIdentifier);
    assertNotTransient(config);
    assertProvisioned(config);

    String ref = config.getProviderNetworkRef();
    if (ref == null) {
      throw new InvalidRequestException("No provider network bound for account " + accountIdentifier);
    }

    // Every successful call creates a new independent reusable key. Existing keys remain valid;
    // every provider ID stays durable so release can revoke the complete accumulated set.
    if (StringUtils.isBlank(config.getPendingCustomerKeyOperationDescription())) {
      config.setPendingCustomerKeyOperationDescription(
          PrivateConnectivityHelpers.CUSTOMER_KEY_OPERATION_PREFIX + UUID.randomUUID());
      repository.save(config);
    } else {
      // Avoid an O(number-of-keys) provider inventory scan on every ordinary credential call.
      // Reconciliation is needed only when a durable operation identity survived an earlier call.
      providerNetworkClient.reconcileCredentialCreate(ref, config.getPendingCustomerKeyOperationDescription());
    }
    ProviderNetworkClient.JoinCredentialInfo joinCreds = providerNetworkClient.createJoinCredential(
        ref, PrivateConnectivityHelpers.customerEnrollmentTags(), config.getPendingCustomerKeyOperationDescription());
    List<String> customerKeyIds =
        config.getCustomerJoinKeyIds() == null ? new ArrayList<>() : new ArrayList<>(config.getCustomerJoinKeyIds());
    if (!customerKeyIds.contains(joinCreds.keyId())) {
      customerKeyIds.add(joinCreds.keyId());
    }
    config.setCustomerJoinKeyIds(List.copyOf(customerKeyIds));
    config.setPendingCustomerKeyOperationDescription(null);
    try {
      repository.save(config);
    } catch (Exception persistenceException) {
      boolean revoked = false;
      try {
        providerNetworkClient.revokeJoinCredentials(ref, List.of(joinCreds.keyId()));
        revoked = true;
      } catch (Exception revokeException) {
        persistenceException.addSuppressed(revokeException);
        log.error("Private Connectivity failed to revoke untracked customer credential account={}", accountIdentifier,
            revokeException);
      }
      if (!revoked) {
        // The pre-create opaque operation description remains durable if this retry also fails.
        // Prefer persisting the concrete key ID so release can revoke it directly.
        try {
          repository.save(config);
        } catch (Exception trackingException) {
          persistenceException.addSuppressed(trackingException);
        }
      }
      throw persistenceException;
    }

    log.info("Private Connectivity customer credential created account={} providerKeyId={} expiresAt={} "
            + "trackedKeyCount={}",
        accountIdentifier, joinCreds.keyId(), joinCreds.expiresAt(), customerKeyIds.size());

    return PrivateConnectivityCredentialDTO.builder()
        .authKey(joinCreds.authKey())
        .expiresAt(joinCreds.expiresAt())
        .build();
  }

  @Override
  public boolean release(String accountIdentifier) {
    Optional<PrivateConnectivityConfig> config =
        withAccountLock(accountIdentifier, () -> prepareRelease(accountIdentifier));
    if (config.isEmpty()) {
      return false;
    }
    submitRelease(config.get());
    return true;
  }

  @Override
  public boolean releaseIfStuck(String accountIdentifier) {
    Optional<PrivateConnectivityConfig> config =
        withAccountLock(accountIdentifier, () -> prepareStuckRelease(accountIdentifier));
    config.ifPresent(this::submitRelease);
    return config.isPresent();
  }

  private Optional<PrivateConnectivityConfig> prepareRelease(String accountIdentifier) {
    Optional<PrivateConnectivityConfig> optConfig = repository.findByAccountIdentifier(accountIdentifier);
    if (optConfig.isEmpty()) {
      log.info("Private Connectivity release found no binding account={}", accountIdentifier);
      return Optional.empty();
    }
    PrivateConnectivityConfig config = optConfig.get();
    if (isAmbiguousProvisionCreate(config)) {
      recoverAmbiguousCreate(config);
      return Optional.empty();
    }
    if (PrivateConnectivityStatus.RELEASING == config.getStatus()) {
      log.info("Private Connectivity release already in progress account={}; scheduling idempotent cleanup",
          accountIdentifier);
      return Optional.of(config);
    }
    if (PrivateConnectivityStatus.ERROR == config.getStatus()
        && PrivateConnectivityOperationType.RELEASE == config.getOperationType() && config.getReleasePhase() != null) {
      config.setStatus(PrivateConnectivityStatus.RELEASING);
      repository.save(config);
      log.info("Private Connectivity release retry requested account={} completedPhase={}", accountIdentifier,
          config.getReleasePhase());
      return Optional.of(config);
    }
    if (PrivateConnectivityStatus.NOT_PROVISIONED == config.getStatus()) {
      return Optional.empty();
    }

    PrivateConnectivityLifecycle.beginRelease(config);
    repository.save(config);
    log.info("Private Connectivity release initiated account={}", accountIdentifier);

    return Optional.of(config);
  }

  /**
   * Reconciler path: re-validate under lock so a stale PROVISIONING snapshot cannot tear down a
   * network that finished as PROVISIONED after the reconciler's unlocked query.
   */
  private Optional<PrivateConnectivityConfig> prepareStuckRelease(String accountIdentifier) {
    Optional<PrivateConnectivityConfig> optConfig = repository.findByAccountIdentifier(accountIdentifier);
    if (optConfig.isEmpty()) {
      return Optional.empty();
    }
    PrivateConnectivityConfig config = optConfig.get();
    PrivateConnectivityStatus status = config.getStatus();
    boolean ambiguousProvisionCreate = isAmbiguousProvisionCreate(config);
    if (ambiguousProvisionCreate
        && (PrivateConnectivityStatus.ERROR == status
            || (PrivateConnectivityStatus.PROVISIONING == status && isProvisioningStale(config)))) {
      recoverAmbiguousCreate(config);
      return Optional.empty();
    }
    if (PrivateConnectivityStatus.RELEASING == status) {
      log.info("Private Connectivity recovery resubmitting release account={}", accountIdentifier);
      return Optional.of(config);
    }
    if (PrivateConnectivityStatus.ERROR == status && StringUtils.isNotBlank(config.getProviderNetworkRef())
        && config.getOperationType() != PrivateConnectivityOperationType.UPDATE) {
      if (config.getOperationType() != PrivateConnectivityOperationType.RELEASE || config.getReleasePhase() == null) {
        PrivateConnectivityLifecycle.beginRelease(config);
      }
      config.setStatus(PrivateConnectivityStatus.RELEASING);
      repository.save(config);
      log.info("Private Connectivity recovery starting release for failed binding account={} completedPhase={}",
          accountIdentifier, config.getReleasePhase());
      return Optional.of(config);
    }
    if (PrivateConnectivityStatus.PROVISIONING == status && isProvisioningStale(config)) {
      PrivateConnectivityLifecycle.beginRelease(config);
      repository.save(config);
      log.info("Private Connectivity recovery releasing stale provisioning account={}", accountIdentifier);
      return Optional.of(config);
    }
    log.info("Private Connectivity recovery skipped account={} status={} because binding is no longer recoverable",
        accountIdentifier, status);
    return Optional.empty();
  }

  private static boolean isProvisioningStale(PrivateConnectivityConfig config) {
    long staleBeforeMs = System.currentTimeMillis() - PROVISIONING_STALE_AFTER.toMillis();
    Long modified = config.getLastModifiedAt();
    if (modified == null) {
      modified = config.getCreatedAt();
    }
    return modified != null && modified < staleBeforeMs;
  }

  private boolean isIdempotentBindingIntact(PrivateConnectivityConfig config) {
    boolean durableBindingIntact = orgConfig.isConfigured()
        && orgConfig.configurationFingerprint().equals(config.getProviderConfigurationFingerprint())
        && PrivateConnectivityHelpers.hasCompleteEnrollmentBinding(config);
    if (!durableBindingIntact) {
      return false;
    }
    try {
      PrivateConnectivityChildCredentialService.ChildCredential credential =
          childCredentialService.load(config.getAccountIdentifier(), config.getProviderTailnetOAuthSecretRef());
      return config.getProviderTailnetOAuthClientId().equals(credential.clientId());
    } catch (RuntimeException exception) {
      log.warn("Private Connectivity durable child OAuth credential is unavailable account={}",
          config.getAccountIdentifier());
      return false;
    }
  }

  private boolean recoverAmbiguousCreate(PrivateConnectivityConfig config) {
    if (!isAmbiguousProvisionCreate(config)
        || (config.getNextRetryAt() != null && config.getNextRetryAt() > System.currentTimeMillis())) {
      return false;
    }
    try {
      networkProvisioner.reconcileAmbiguousCreate(config);
      log.info("Private Connectivity ambiguous provider create reconciled account={}", config.getAccountIdentifier());
      return true;
    } catch (Exception exception) {
      recordOperationFailure(
          config, "Ambiguous provider network creation could not be reconciled; contact Harness Support");
      log.error("Private Connectivity ambiguous provider create recovery failed account={}",
          config.getAccountIdentifier(), exception);
      return false;
    }
  }

  private static boolean isAmbiguousProvisionCreate(PrivateConnectivityConfig config) {
    return config != null && PrivateConnectivityOperationType.PROVISION == config.getOperationType()
        && StringUtils.isBlank(config.getProviderNetworkRef())
        && StringUtils.isNotBlank(config.getProviderNetworkName());
  }

  private static boolean isSetupStartAllowed(PrivateConnectivityConfig config) {
    boolean noRemoteRecoveryState =
        StringUtils.isAllBlank(config.getProviderNetworkRef(), config.getProviderNetworkName(),
            config.getProviderTailnetOAuthClientId(), config.getProviderTailnetOAuthSecretRef(),
            config.getWifCredentialId(), config.getWifClientId(), config.getWifAudience(),
            config.getPendingWifOperationDescription(), config.getPendingCustomerKeyOperationDescription(),
            config.getPendingHelperKeyOperationDescription())
        && (config.getCustomerJoinKeyIds() == null || config.getCustomerJoinKeyIds().isEmpty())
        && (config.getHelperJoinKeyIds() == null || config.getHelperJoinKeyIds().isEmpty());
    if (!noRemoteRecoveryState) {
      return false;
    }
    if (PrivateConnectivityStatus.NOT_PROVISIONED == config.getStatus()) {
      return config.getOperationType() == null;
    }
    return PrivateConnectivityStatus.ERROR == config.getStatus()
        && PrivateConnectivityOperationType.PROVISION == config.getOperationType() && config.getReleasePhase() == null;
  }

  private void submitRelease(PrivateConnectivityConfig config) {
    String accountIdentifier = config.getAccountIdentifier();
    try {
      operationExecutor.execute(() -> {
        try {
          boolean acquired = accountLock.tryRun(accountIdentifier, () -> {
            Optional<PrivateConnectivityConfig> persisted = repository.findByAccountIdentifier(accountIdentifier);
            if (persisted.isEmpty()) {
              log.info("Private Connectivity release worker found no binding account={}", accountIdentifier);
              return;
            }
            PrivateConnectivityConfig current = persisted.get();
            boolean resumableRelease = PrivateConnectivityStatus.RELEASING == current.getStatus()
                || (PrivateConnectivityStatus.ERROR == current.getStatus()
                    && PrivateConnectivityOperationType.RELEASE == current.getOperationType()
                    && current.getReleasePhase() != null);
            if (!resumableRelease) {
              log.info("Private Connectivity release worker skipped account={} status={} operation={} phase={}",
                  accountIdentifier, current.getStatus(), current.getOperationType(), current.getReleasePhase());
              return;
            }
            if (current.getNextRetryAt() != null && current.getNextRetryAt() > System.currentTimeMillis()) {
              log.info("Private Connectivity release worker waiting for retry account={} phase={} nextRetryAt={}",
                  accountIdentifier, current.getReleasePhase(), current.getNextRetryAt());
              return;
            }
            networkProvisioner.release(current);
          });
          if (!acquired) {
            log.info("Private Connectivity release worker skipped busy account lock account={}; "
                    + "status remains durable and the reconciler will retry if needed",
                accountIdentifier);
          }
        } catch (Exception ex) {
          log.error("Private Connectivity release worker failed account={}", accountIdentifier, ex);
        }
      });
    } catch (RejectedExecutionException exception) {
      log.error("Private Connectivity release submission rejected account={}; durable state will be retried",
          accountIdentifier, exception);
    }
  }

  @Override
  public PrivateConnectivityInternalDTO getInternal(String accountIdentifier) {
    return internalQueries.getInternal(accountIdentifier);
  }

  private void mapDns(String ref, PrivateConnectivitySetupRequestDTO request) {
    Map<String, List<String>> splitDns = request.getDns() != null && request.getDns().getSplitDnsDomains() != null
        ? request.getDns().getSplitDnsDomains()
        : Collections.emptyMap();
    providerNetworkClient.configureDns(ref, new ProviderNetworkClient.DnsConfig(splitDns));
  }

  private PrivateConnectivitySetupResponseDTO toSetupResponseDTO(
      PrivateConnectivityConfig config, PrivateConnectivityCredentialDTO credential) {
    PrivateConnectivityRouterType mode = deriveMode(config);
    return PrivateConnectivitySetupResponseDTO.builder()
        .accountIdentifier(config.getAccountIdentifier())
        .status(toPublicStatus(config.getStatus()))
        .credential(credential)
        .installHints(PrivateConnectivityHelpers.buildInstallHints(mode))
        .build();
  }

  private PrivateConnectivityResponseDTO toResponseDTO(PrivateConnectivityConfig config) {
    return PrivateConnectivityResponseDTO.builder()
        .accountIdentifier(config.getAccountIdentifier())
        .status(toPublicStatus(config.getStatus()))
        .advertiseRoutes(config.getAdvertiseRoutes())
        .domains(config.getDomains())
        .dns(toDnsConfig(config))
        .lastError(config.getLastError())
        .build();
  }

  private PrivateConnectivityAdminResponseDTO toAdminResponseDTO(PrivateConnectivityConfig config) {
    boolean providerConfigurationMatches = orgConfig.isConfigured()
        && orgConfig.configurationFingerprint().equals(config.getProviderConfigurationFingerprint());
    boolean tailnetCredentialReferencePresent =
        StringUtils.isNoneBlank(config.getProviderTailnetOAuthClientId(), config.getProviderTailnetOAuthSecretRef());
    return PrivateConnectivityAdminResponseDTO.builder()
        .accountIdentifier(config.getAccountIdentifier())
        .status(config.getStatus())
        .advertiseRoutes(config.getAdvertiseRoutes())
        .domains(config.getDomains())
        .dns(toDnsConfig(config))
        .providerNetworkRef(config.getProviderNetworkRef())
        .providerNetworkName(config.getProviderNetworkName())
        .providerConfigurationMatches(providerConfigurationMatches)
        .tailnetCredentialReferencePresent(tailnetCredentialReferencePresent)
        .wifCredentialId(config.getWifCredentialId())
        .wifClientId(config.getWifClientId())
        .wifAudience(config.getWifAudience())
        .trackedCustomerCredentialCount(size(config.getCustomerJoinKeyIds()))
        .trackedHelperCredentialCount(size(config.getHelperJoinKeyIds()))
        .operationType(config.getOperationType())
        .releasePhase(config.getReleasePhase())
        .retryCount(config.getRetryCount())
        .nextRetryAt(config.getNextRetryAt())
        .lastError(config.getLastError())
        .createdAt(config.getCreatedAt())
        .lastModifiedAt(config.getLastModifiedAt())
        .build();
  }

  private static PrivateConnectivitySetupRequestDTO.DnsConfig toDnsConfig(PrivateConnectivityConfig config) {
    return config.getSplitDnsDomains() == null || config.getSplitDnsDomains().isEmpty()
        ? null
        : PrivateConnectivitySetupRequestDTO.DnsConfig.builder().splitDnsDomains(config.getSplitDnsDomains()).build();
  }

  private static int size(List<?> values) {
    return values == null ? 0 : values.size();
  }

  private static PrivateConnectivityPublicStatus toPublicStatus(PrivateConnectivityStatus status) {
    if (status == null) {
      return PrivateConnectivityPublicStatus.ERROR;
    }
    return switch (status) {
      case NOT_PROVISIONED -> PrivateConnectivityPublicStatus.NOT_PROVISIONED;
      case PROVISIONING -> PrivateConnectivityPublicStatus.PROVISIONING;
      case RECONCILING -> PrivateConnectivityPublicStatus.UPDATING;
      case PROVISIONED -> PrivateConnectivityPublicStatus.READY;
      case RELEASING -> PrivateConnectivityPublicStatus.RELEASING;
      case ERROR -> PrivateConnectivityPublicStatus.ERROR;
    };
  }

  private void assertFeatureEnabled(String accountIdentifier) {
    if (!featureFlagService.isEnabled(FeatureName.CI_ENABLE_CLOUD_PRIVATE_CONNECTIVITY, accountIdentifier)) {
      throw new InvalidRequestException(
          "Harness Cloud Private Connectivity is not enabled for account " + accountIdentifier);
    }
  }

  private PrivateConnectivityConfig getOrThrowNotFound(String accountIdentifier) {
    return repository.findByAccountIdentifier(accountIdentifier)
        .orElseThrow(()
                         -> new NotFoundException("No private connectivity config found for account "
                             + accountIdentifier + "; run setup first"));
  }

  private void assertNotTransient(PrivateConnectivityConfig config) {
    PrivateConnectivityStatus status = config.getStatus();
    if (PrivateConnectivityStatus.PROVISIONING == status || PrivateConnectivityStatus.RECONCILING == status
        || PrivateConnectivityStatus.RELEASING == status) {
      throw new PrivateConnectivityConflictException(
          "Mutating private connectivity is not allowed while status=" + status);
    }
  }

  private void assertProvisioned(PrivateConnectivityConfig config) {
    PrivateConnectivityStatus status = config.getStatus();
    if (PrivateConnectivityStatus.PROVISIONED != status) {
      throw new InvalidRequestException("Private connectivity must be PROVISIONED; current status=" + status);
    }
  }

  private void assertConfigurationReplacementAllowed(PrivateConnectivityConfig config) {
    PrivateConnectivityStatus status = config.getStatus();
    boolean failedUpdate = PrivateConnectivityStatus.ERROR == status
        && PrivateConnectivityOperationType.UPDATE == config.getOperationType()
        && config.getReleasePhase() == null && StringUtils.isNotBlank(config.getProviderNetworkRef());
    if (PrivateConnectivityStatus.PROVISIONED != status && !failedUpdate) {
      throw new InvalidRequestException(
          "Private connectivity configuration cannot be replaced while status=" + status
          + " and operation=" + config.getOperationType());
    }
  }

  private static PrivateConnectivityRouterType deriveMode(PrivateConnectivityConfig config) {
    boolean hasRoutes = config.getAdvertiseRoutes() != null && !config.getAdvertiseRoutes().isEmpty();
    boolean hasDomains = config.getDomains() != null && !config.getDomains().isEmpty();
    return hasRoutes || hasDomains
        ? PrivateConnectivityRouterType.fromConfiguration(config.getAdvertiseRoutes(), config.getDomains())
        :
        null;
    }

    private PrivateConnectivitySetupRequestDTO fromConfig(PrivateConnectivityConfig current) {
      PrivateConnectivitySetupRequestDTO.DnsConfig dns =
          current.getSplitDnsDomains() == null || current.getSplitDnsDomains().isEmpty()
          ? null
          : PrivateConnectivitySetupRequestDTO.DnsConfig.builder()
                .splitDnsDomains(current.getSplitDnsDomains())
                .build();
      return PrivateConnectivitySetupRequestDTO.builder()
          .advertiseRoutes(current.getAdvertiseRoutes())
          .domains(current.getDomains())
          .dns(dns)
          .build();
    }

    private static boolean configurationMatches(
        PrivateConnectivityConfig config, PrivateConnectivitySetupRequestDTO request) {
      Map<String, List<String>> requestedSplitDns =
          request.getDns() == null ? null : request.getDns().getSplitDnsDomains();
      return Objects.equals(config.getAdvertiseRoutes(), request.getAdvertiseRoutes())
          && Objects.equals(config.getDomains(), request.getDomains())
          && Objects.equals(config.getSplitDnsDomains(), requestedSplitDns);
    }

    private <T> T withAccountLock(String accountIdentifier, Supplier<T> operation) {
      return accountLock.executeOrConflict(accountIdentifier, operation);
    }
  }
