/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.entities;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityOperationType;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityReleasePhase;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityStatus;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Mongo document for Harness Cloud Private Connectivity per-account configuration.
 *
 * Do NOT implement NGAccountAccess. Harness operators must explicitly release the binding before
 * deleting the account. Reflective Mongo-only deletion would bypass provider cleanup and orphan
 * vendor state.
 *
 */
@Data
@Builder
@FieldNameConstants(innerTypeName = "PrivateConnectivityConfigKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "privateConnectivityConfig", noClassnameStored = true)
@Document("privateConnectivityConfig")
@Persistent
@OwnedBy(CI)
public class PrivateConnectivityConfig implements UuidAware, PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("providerNetworkRef_unique_sparse")
                 .field(PrivateConnectivityConfigKeys.providerNetworkRef)
                 .unique(true)
                 .sparse(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("providerNetworkName_unique_sparse")
                 .field(PrivateConnectivityConfigKeys.providerNetworkName)
                 .unique(true)
                 .sparse(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("status_nextRetryAt")
                 .field(PrivateConnectivityConfigKeys.status)
                 .field(PrivateConnectivityConfigKeys.nextRetryAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("status_lastModifiedAt")
                 .field(PrivateConnectivityConfigKeys.status)
                 .field(PrivateConnectivityConfigKeys.lastModifiedAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("status_createdAt")
                 .field(PrivateConnectivityConfigKeys.status)
                 .field(PrivateConnectivityConfigKeys.createdAt)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id protected String uuid;

  @FdUniqueIndex @NotEmpty @Trimmed private String accountIdentifier;

  // --- Customer-visible config ---

  private PrivateConnectivityStatus status;
  private List<String> advertiseRoutes;
  private List<String> domains;
  private Map<String, List<String>> splitDnsDomains;
  private String lastError;

  // --- Internal fields (never returned on customer APIs) ---

  /**
   * Opaque vendor network reference (e.g. Tailscale tailnetId).
   * MUST NOT contain accountId or any customer-derived identifier.
   * Enforce: at most one PrivateConnectivityConfig may hold a given providerNetworkRef
   * (unique sparse via {@link #mongoIndexes()}).
   */
  private String providerNetworkRef;

  /**
   * Provider display name generated from a provider-safe account identifier and a five-digit suffix.
   * It is persisted before network creation as the exact, non-secret recovery pointer for resolving
   * an ambiguous create response without issuing a blind duplicate. The unique sparse index keeps
   * that recovery pointer unambiguous while allowing unbound records to omit it.
   */
  private String providerNetworkName;

  /** Fingerprint of the stable vendor organization identity and API base used for this binding. */
  private String providerConfigurationFingerprint;

  /**
   * Provider-created child-tailnet OAuth client ID. This is not the deployment-owned organization
   * client and is required for all operations scoped to this tailnet.
   */
  private String providerTailnetOAuthClientId;

  /**
   * Account-scoped internal-secret identifier containing the child OAuth credential. MongoDB never
   * contains the raw provider secret.
   */
  private String providerTailnetOAuthSecretRef;

  // --- WIF (Workload Identity Federation) fields ---

  /** WIF credential ID; deleted on release; recreated on rebind. */
  private String wifCredentialId;

  /** WIF client ID (non-secret); exposed only through trusted internal state for CI workload injection. */
  private String wifClientId;

  /** WIF audience (non-secret); used by CI when requesting the account-bound workload JWT. */
  private String wifAudience;

  /**
   * Opaque WIF create identity persisted before the vendor POST. If the response is lost, the
   * provider client resolves this exact description, deletes the unrecoverable credential, confirms
   * absence, and only then retries.
   */
  private String pendingWifOperationDescription;

  // --- Credential revocation references (never secret values) ---

  /**
   * Provider credential IDs required for release-time revocation.
   * Customer auth keys are reusable, but their secret values are returned once and never persisted.
   * Cleared on release after revoke. Never returned on any customer endpoint.
   */
  private List<String> customerJoinKeyIds;

  /** Provider IDs for helper join keys that still require release-time revocation. */
  private List<String> helperJoinKeyIds;

  /** Opaque customer auth-key create identity retained across ambiguous vendor responses. */
  private String pendingCustomerKeyOperationDescription;

  /** Opaque manual-helper auth-key create identity retained across ambiguous vendor responses. */
  private String pendingHelperKeyOperationDescription;

  private PrivateConnectivityOperationType operationType;
  private PrivateConnectivityReleasePhase releasePhase;
  private Integer retryCount;
  private Long nextRetryAt;

  @CreatedDate private Long createdAt;
  @LastModifiedDate private Long lastModifiedAt;
}
