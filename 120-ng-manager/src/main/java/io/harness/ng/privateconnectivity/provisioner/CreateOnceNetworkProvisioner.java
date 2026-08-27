/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.provisioner;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.oidc.idtoken.OidcIdTokenConstants.ACCOUNT_ID;
import static io.harness.oidc.idtoken.OidcIdTokenUtility.updateClaim;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityCredentialDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityOperationType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.ng.privateconnectivity.entities.PrivateConnectivityConfig;
import io.harness.ng.privateconnectivity.sanitizer.ReleaseSanitizer;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityAclFactory;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityChildCredentialService;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityHelpers;
import io.harness.ng.privateconnectivity.util.PrivateConnectivityLifecycle;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient.CreateOutcome;
import io.harness.ng.privateconnectivity.vendorclient.ProviderNetworkClient.ProviderCreateException;
import io.harness.oidc.config.OidcConfigurationUtility;
import io.harness.oidc.entities.OidcJwks;
import io.harness.oidc.jwks.OidcJwksUtility;
import io.harness.repositories.ng.privateconnectivity.PrivateConnectivityConfigRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Create-once provisioner: one provider network per account; destroy on release.
 *
 * Release paths (admin DELETE and compensation) must share {@code ReleaseSanitizer}.
 */
@Slf4j
@Singleton
@OwnedBy(CI)
public class CreateOnceNetworkProvisioner {
  private static final String PROVIDER_NETWORK_NAME_PREFIX = "pc-";
  private static final int MAX_PROVIDER_NETWORK_NAME_LENGTH = 65;
  private static final int MIN_PROVIDER_NETWORK_RANDOM_SUFFIX = 10_000;
  private static final int MAX_PROVIDER_NETWORK_RANDOM_SUFFIX_EXCLUSIVE = 100_000;
  private static final int PROVIDER_NETWORK_RANDOM_SUFFIX_LENGTH = 5;
  private static final Pattern UNSUPPORTED_PROVIDER_NETWORK_NAME_CHARACTERS = Pattern.compile("[^a-zA-Z0-9-]+");

  private final ProviderNetworkClient providerNetworkClient;
  private final ReleaseSanitizer releaseSanitizer;
  private final PrivateConnectivityConfigRepository configRepository;
  private final OidcConfigurationUtility oidcConfigurationUtility;
  private final OidcJwksUtility oidcJwksUtility;
  private final PrivateConnectivityChildCredentialService childCredentialService;

  /**
   * Result of a successful ensureBound call.
   *
   * Carries both the persisted config and the initial reusable customer credential that must be
   * included in the setup response. The credential value is not stored server-side.
   */
  public record ProvisionResult(PrivateConnectivityConfig config, PrivateConnectivityCredentialDTO credential) {}

  @Inject
  public CreateOnceNetworkProvisioner(ProviderNetworkClient providerNetworkClient, ReleaseSanitizer releaseSanitizer,
      PrivateConnectivityConfigRepository configRepository, OidcConfigurationUtility oidcConfigurationUtility,
      OidcJwksUtility oidcJwksUtility, PrivateConnectivityChildCredentialService childCredentialService) {
    this.providerNetworkClient = providerNetworkClient;
    this.releaseSanitizer = releaseSanitizer;
    this.configRepository = configRepository;
    this.oidcConfigurationUtility = oidcConfigurationUtility;
    this.oidcJwksUtility = oidcJwksUtility;
    this.childCredentialService = childCredentialService;
  }

  /**
   * Ensure a provider network is bound to the given account (create-once).
   *
   * @param accountIdentifier   customer account
   * @param request             validated setup request
   * @param existingConfig      current config doc in PROVISIONING state (set by caller before invoking)
   * @return ProvisionResult with the saved config and initial reusable customer credential
   * @throws RuntimeException on vendor error; implementor must compensate and caller marks ERROR
   */
  public ProvisionResult ensureBound(
      String accountIdentifier, PrivateConnectivitySetupRequestDTO request, PrivateConnectivityConfig existingConfig) {
    if (existingConfig == null) {
      throw new InvalidRequestException("existingConfig required in PROVISIONING state before ensureBound");
    }
    String providerNetworkName = existingConfig.getProviderNetworkName();
    if (StringUtils.isBlank(providerNetworkName)) {
      providerNetworkName = createProviderNetworkName(accountIdentifier);
      existingConfig.setProviderNetworkName(providerNetworkName);
    }
    if (StringUtils.isBlank(existingConfig.getProviderTailnetOAuthSecretRef())) {
      existingConfig.setProviderTailnetOAuthSecretRef(childCredentialService.secretIdentifier(providerNetworkName));
    }
    // Persist the exact generated recovery name before the non-idempotent remote create. If the
    // response is lost, retries stop here instead of creating a second network.
    configRepository.save(existingConfig);
    log.info("Private Connectivity provider network creation started account={} networkName={}", accountIdentifier,
        providerNetworkName);

    ProviderNetworkClient.NetworkCreateResult created;
    try {
      created = providerNetworkClient.createNetwork(providerNetworkName);
    } catch (ProviderCreateException exception) {
      if (CreateOutcome.DEFINITELY_NOT_CREATED == exception.getOutcome()) {
        existingConfig.setProviderNetworkName(null);
        existingConfig.setProviderTailnetOAuthSecretRef(null);
        existingConfig.setStatus(PrivateConnectivityStatus.ERROR);
        existingConfig.setLastError(
            "The provider rejected network creation before creating a resource; correct the configuration and retry");
        configRepository.save(existingConfig);
        throw new InvalidRequestException(
            "The Private Connectivity provider rejected network creation; correct the request or provider "
                + "configuration and retry",
            exception);
      }
      throw exception;
    }
    String providerNetworkRef = created.providerNetworkRef();
    ProviderNetworkClient.ProviderAdminCredential adminCredential = created.adminCredential();
    log.info("Private Connectivity provider network created account={} networkName={} networkRef={}", accountIdentifier,
        providerNetworkName, providerNetworkRef);

    // The create response is the only time this credential is available. Store it before any
    // additional repository operation. The deterministic reference was persisted before create.
    try {
      childCredentialService.store(
          accountIdentifier, providerNetworkName, adminCredential.clientId(), adminCredential.clientSecret());
    } catch (Exception secretPersistenceException) {
      compensateCreateBeforeBinding(
          accountIdentifier, existingConfig, providerNetworkRef, adminCredential, secretPersistenceException);
      throw secretPersistenceException instanceof RuntimeException
          ? (RuntimeException) secretPersistenceException
          : new InvalidRequestException(
                "Failed to persist provider child OAuth credential", secretPersistenceException);
    }

    // Exclusivity: at most one account may hold a given ref.
    // Never teardown on collision — the create response reused/collided with a claimed ref; destroying
    // it would take down another account's network. Ops cleans true vendor orphans.
    final Optional<PrivateConnectivityConfig> existingHolder;
    try {
      existingHolder = configRepository.findByProviderNetworkRef(providerNetworkRef);
    } catch (Exception lookupException) {
      compensateCreateBeforeBinding(
          accountIdentifier, existingConfig, providerNetworkRef, adminCredential, lookupException);
      throw lookupException;
    }
    if (existingHolder.isPresent()) {
      String holderAccount = existingHolder.get().getAccountIdentifier();
      if (!accountIdentifier.equals(holderAccount)) {
        throw new InvalidRequestException(
            "Provider network is already bound to another account; refusing dual binding");
      }
      throw new InvalidRequestException(
          "Provider network ref is already claimed by this account; refusing duplicate bind");
    }

    existingConfig.setProviderNetworkRef(providerNetworkRef);
    existingConfig.setProviderTailnetOAuthClientId(adminCredential.clientId());
    try {
      configRepository.save(existingConfig);
    } catch (Exception persistenceException) {
      compensateCreateBeforeBinding(
          accountIdentifier, existingConfig, providerNetworkRef, adminCredential, persistenceException);
      throw persistenceException instanceof RuntimeException
          ? (RuntimeException) persistenceException
          : new InvalidRequestException("Failed to persist provider network binding", persistenceException);
    }

    try {
      ProviderNetworkClient.NetworkPolicy policy =
          new ProviderNetworkClient.NetworkPolicy(PrivateConnectivityAclFactory.buildAclJson(request));
      providerNetworkClient.validatePolicy(providerNetworkRef, policy);
      providerNetworkClient.applyPolicy(providerNetworkRef, policy);
      log.info("Private Connectivity server-owned policy applied account={} networkRef={}", accountIdentifier,
          providerNetworkRef);

      Map<String, List<String>> splitDns = request.getDns() != null && request.getDns().getSplitDnsDomains() != null
          ? request.getDns().getSplitDnsDomains()
          : Collections.emptyMap();
      providerNetworkClient.configureDns(providerNetworkRef, new ProviderNetworkClient.DnsConfig(splitDns));
      log.info("Private Connectivity DNS configuration applied account={} networkRef={} splitDnsEntryCount={}",
          accountIdentifier, providerNetworkRef, splitDns.size());

      // Initialize the existing account-scoped signing key while setup is serialized. The WIF
      // issuer can then publish a stable JWKS before any number of builds mint tokens concurrently,
      // without changing the shared OIDC key lifecycle for unrelated products.
      OidcJwks oidcJwks = oidcJwksUtility.getJwksKeys(accountIdentifier);
      if (oidcJwks == null || StringUtils.isBlank(oidcJwks.getKeyId()) || oidcJwks.getRsaKeyPair() == null
          || StringUtils.isAnyBlank(
              oidcJwks.getRsaKeyPair().getPublicKey(), oidcJwks.getRsaKeyPair().getPrivateKeyRef())) {
        throw new InvalidRequestException(
            "Private Connectivity could not initialize the account OIDC signing identity");
      }

      String issuer = resolveOidcIssuer(accountIdentifier);
      // Keep subject aligned with CI mint (bare accountId) end-to-end.
      ProviderNetworkClient.WifConfig wifConfig = new ProviderNetworkClient.WifConfig(
          issuer, accountIdentifier, accountIdentifier, new String[] {PrivateConnectivityHelpers.CI_RUNNER_TAG});
      if (StringUtils.isBlank(existingConfig.getPendingWifOperationDescription())) {
        existingConfig.setPendingWifOperationDescription("pc-wif-" + UUID.randomUUID());
        configRepository.save(existingConfig);
      }
      ProviderNetworkClient.WifCredentialInfo wif = providerNetworkClient.createWif(
          providerNetworkRef, wifConfig, existingConfig.getPendingWifOperationDescription());
      existingConfig.setWifCredentialId(wif.credentialId());
      existingConfig.setWifClientId(wif.clientId());
      existingConfig.setWifAudience(wif.audience());
      existingConfig.setPendingWifOperationDescription(null);
      configRepository.save(existingConfig);
      log.info("Private Connectivity workload identity created account={} networkRef={} credentialId={}",
          accountIdentifier, providerNetworkRef, wif.credentialId());

      if (StringUtils.isBlank(existingConfig.getPendingCustomerKeyOperationDescription())) {
        existingConfig.setPendingCustomerKeyOperationDescription(
            PrivateConnectivityHelpers.CUSTOMER_KEY_OPERATION_PREFIX + UUID.randomUUID());
        configRepository.save(existingConfig);
      }
      ProviderNetworkClient.JoinCredentialInfo joinCreds = providerNetworkClient.createJoinCredential(
          providerNetworkRef, PrivateConnectivityHelpers.customerEnrollmentTags(),
          existingConfig.getPendingCustomerKeyOperationDescription());
      log.info("Private Connectivity initial customer credential created account={} networkRef={} providerKeyId={} "
              + "expiresAt={}",
          accountIdentifier, providerNetworkRef, joinCreds.keyId(), joinCreds.expiresAt());
      PrivateConnectivityCredentialDTO credential = PrivateConnectivityCredentialDTO.builder()
                                                        .authKey(joinCreds.authKey())
                                                        .expiresAt(joinCreds.expiresAt())
                                                        .build();
      existingConfig.setCustomerJoinKeyIds(List.of(joinCreds.keyId()));
      existingConfig.setPendingCustomerKeyOperationDescription(null);
      configRepository.save(existingConfig);

      existingConfig.setStatus(PrivateConnectivityStatus.PROVISIONED);
      existingConfig.setAdvertiseRoutes(request.getAdvertiseRoutes());
      existingConfig.setDomains(request.getDomains());
      existingConfig.setSplitDnsDomains(request.getDns() != null ? request.getDns().getSplitDnsDomains() : null);
      existingConfig.setOperationType(null);
      existingConfig.setRetryCount(0);
      existingConfig.setNextRetryAt(null);
      existingConfig.setLastError(null);

      PrivateConnectivityConfig saved = configRepository.save(existingConfig);
      log.info("Private Connectivity provisioning completed account={} networkRef={} status={}", accountIdentifier,
          providerNetworkRef, saved.getStatus());
      return new ProvisionResult(saved, credential);

    } catch (Exception e) {
      // A blank provider ref plus the durable recovery name means the network-create outcome is
      // unknown. Generic release cannot address that remote resource and would clear the only
      // exact recovery identity. Leave it for reconcileAmbiguousCreate instead. Definite create
      // rejection also has no remote state to sanitize.
      if (StringUtils.isNotBlank(existingConfig.getProviderNetworkRef())) {
        safeCompensateRelease(existingConfig);
      }
      // ReleaseSanitizer is the source of truth for status/refs. Never overwrite its write with
      // stale in-memory RELEASING state when compensation actually ran.
      PrivateConnectivityConfig latest =
          configRepository.findByAccountIdentifier(accountIdentifier).orElse(existingConfig);
      syncBindingState(existingConfig, latest);
      throw e instanceof RuntimeException ? (RuntimeException) e
                                          : new InvalidRequestException("Provision failed: " + e.getMessage(), e);
    }
  }

  /** Resolve an ambiguous create without issuing a blind second create. */
  public void reconcileAmbiguousCreate(PrivateConnectivityConfig config) {
    if (config == null || PrivateConnectivityOperationType.PROVISION != config.getOperationType()
        || StringUtils.isNotBlank(config.getProviderNetworkRef())
        || StringUtils.isBlank(config.getProviderNetworkName())) {
      throw new InvalidRequestException("No ambiguous Private Connectivity create operation to reconcile");
    }
    List<ProviderNetworkClient.RecoverableNetwork> matches =
        providerNetworkClient.findNetworksByName(config.getProviderNetworkName());
    if (matches.size() > 1) {
      throw new InvalidRequestException(
          "Multiple provider networks match the persisted operation identity; operator intervention is required");
    }
    if (matches.size() == 1) {
      String recoveredProviderNetworkRef = matches.get(0).providerNetworkRef();
      if (configRepository.findByProviderNetworkRef(recoveredProviderNetworkRef).isPresent()) {
        throw new InvalidRequestException(
            "Recovered provider network is already claimed; refusing destructive ambiguous-create cleanup");
      }
      PrivateConnectivityChildCredentialService.ChildCredential credential =
          childCredentialService
              .loadForProviderNetworkName(config.getAccountIdentifier(), config.getProviderNetworkName())
              .orElseThrow(()
                               -> new InvalidRequestException(
                                   "The provider create response was lost before its one-time child OAuth credential "
                                   + "was stored; operator/provider cleanup is required"));
      if (StringUtils.isNotBlank(config.getProviderTailnetOAuthClientId())
          && !config.getProviderTailnetOAuthClientId().equals(credential.clientId())) {
        throw new InvalidRequestException(
            "Stored provider child OAuth identity does not match the ambiguous create operation");
      }
      providerNetworkClient.deleteNetwork(recoveredProviderNetworkRef,
          new ProviderNetworkClient.ProviderAdminCredential(credential.clientId(), credential.clientSecret()));
      List<ProviderNetworkClient.RecoverableNetwork> remaining =
          providerNetworkClient.findNetworksByName(config.getProviderNetworkName());
      if (!remaining.isEmpty()) {
        throw new InvalidRequestException(
            "Provider network deletion could not be confirmed; refusing to clear the recovery identity");
      }
    }
    childCredentialService.delete(config.getAccountIdentifier(), config.getProviderTailnetOAuthSecretRef());
    config.setStatus(PrivateConnectivityStatus.NOT_PROVISIONED);
    config.setProviderNetworkName(null);
    config.setProviderTailnetOAuthClientId(null);
    config.setProviderTailnetOAuthSecretRef(null);
    config.setOperationType(null);
    config.setRetryCount(0);
    config.setNextRetryAt(null);
    config.setLastError(null);
    configRepository.save(config);
  }

  /**
   * Release the provider network bound to the given account (destroy).
   *
   * Failure in sanitization must leave the binding in ERROR with provider references retained for retry.
   * Final network disposition must be idempotent (crash can occur after vendor success, before DB unbind).
   *
   * @param config current account config in RELEASING state (caller must enter RELEASING before calling)
   */
  public void release(PrivateConnectivityConfig config) {
    releaseSanitizer.sanitize(config.getAccountIdentifier());
  }

  private String resolveOidcIssuer(String accountIdentifier) {
    // Must match CI mint issuer (CUSTOM.payload.iss). Do not allow a divergent org template —
    // WIF trust registration and build-time JWT would disagree.
    String issuerTemplate = null;
    if (oidcConfigurationUtility.getCustomOidcTokenStructure() != null
        && oidcConfigurationUtility.getCustomOidcTokenStructure().getOidcIdTokenPayloadStructure() != null) {
      issuerTemplate = oidcConfigurationUtility.getCustomOidcTokenStructure().getOidcIdTokenPayloadStructure().getIss();
    }
    if (StringUtils.isBlank(issuerTemplate)) {
      throw new InvalidRequestException("CUSTOM.payload.iss is required to register Private Connectivity WIF trust");
    }
    return updateClaim(issuerTemplate, ACCOUNT_ID, accountIdentifier);
  }

  private static String createProviderNetworkName(String accountIdentifier) {
    if (StringUtils.isBlank(accountIdentifier)) {
      throw new InvalidRequestException("Account is required to create a provider network name");
    }
    String providerSafeAccountIdentifier =
        UNSUPPORTED_PROVIDER_NETWORK_NAME_CHARACTERS.matcher(accountIdentifier).replaceAll("-");
    int maxAccountIdentifierLength = MAX_PROVIDER_NETWORK_NAME_LENGTH - PROVIDER_NETWORK_NAME_PREFIX.length() - 1
        - PROVIDER_NETWORK_RANDOM_SUFFIX_LENGTH;
    providerSafeAccountIdentifier = providerSafeAccountIdentifier.substring(
        0, Math.min(providerSafeAccountIdentifier.length(), maxAccountIdentifierLength));
    int randomSuffix = ThreadLocalRandom.current().nextInt(
        MIN_PROVIDER_NETWORK_RANDOM_SUFFIX, MAX_PROVIDER_NETWORK_RANDOM_SUFFIX_EXCLUSIVE);
    return PROVIDER_NETWORK_NAME_PREFIX + providerSafeAccountIdentifier + "-" + randomSuffix;
  }

  private void safeCompensateRelease(PrivateConnectivityConfig config) {
    log.warn("Private Connectivity compensating release started account={} networkRef={}",
        config.getAccountIdentifier(), config.getProviderNetworkRef());
    try {
      PrivateConnectivityLifecycle.beginRelease(config);
      configRepository.save(config);
      releaseSanitizer.sanitize(config.getAccountIdentifier());
    } catch (Exception e) {
      log.error("Private Connectivity compensation release failed account={} networkRef={}; provider resource may be "
              + "orphaned",
          config.getAccountIdentifier(), config.getProviderNetworkRef(), e);
    }
  }

  /**
   * Compensate a create before the provider reference is durably bound. Delete the encrypted
   * credential only after the provider network deletion is confirmed; otherwise the deterministic
   * reference persisted before create remains the operator's recovery pointer.
   */
  private void compensateCreateBeforeBinding(String accountIdentifier, PrivateConnectivityConfig config,
      String providerNetworkRef, ProviderNetworkClient.ProviderAdminCredential adminCredential,
      Throwable originalFailure) {
    try {
      providerNetworkClient.deleteNetwork(providerNetworkRef, adminCredential);
    } catch (Exception teardownError) {
      originalFailure.addSuppressed(teardownError);
      log.error("Private Connectivity provider teardown failed before binding persistence account={} networkRef={}; "
              + "retaining child credential",
          accountIdentifier, providerNetworkRef, teardownError);
      return;
    }
    try {
      childCredentialService.delete(accountIdentifier, config.getProviderTailnetOAuthSecretRef());
    } catch (Exception secretDeletionError) {
      originalFailure.addSuppressed(secretDeletionError);
      log.error("Private Connectivity child credential deletion failed after provider network removal account={} "
              + "networkRef={}",
          accountIdentifier, providerNetworkRef, secretDeletionError);
    }
  }

  /** Copy persisted unbind/error fields into the in-memory entity used by the caller. */
  private static void syncBindingState(PrivateConnectivityConfig target, PrivateConnectivityConfig source) {
    target.setStatus(source.getStatus());
    target.setProviderNetworkRef(source.getProviderNetworkRef());
    target.setProviderTailnetOAuthClientId(source.getProviderTailnetOAuthClientId());
    target.setProviderTailnetOAuthSecretRef(source.getProviderTailnetOAuthSecretRef());
    target.setWifCredentialId(source.getWifCredentialId());
    target.setWifClientId(source.getWifClientId());
    target.setWifAudience(source.getWifAudience());
    target.setCustomerJoinKeyIds(source.getCustomerJoinKeyIds());
    target.setHelperJoinKeyIds(source.getHelperJoinKeyIds());
    target.setLastError(source.getLastError());
  }
}
