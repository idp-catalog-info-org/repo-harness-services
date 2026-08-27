/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.exception.InvalidArgumentsException;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;
import io.harness.mongo.index.Collation;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.NGOrgAccess;
import io.harness.ng.core.NGProjectAccess;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.common.beans.PGPPublicKey;
import io.harness.ng.core.common.beans.RevocationReason;
import io.harness.ng.core.common.beans.SSHPublicKey;
import io.harness.ng.core.common.beans.ScopedResourceMetadata;
import io.harness.ng.core.common.beans.ScopedResourcePermission;
import io.harness.ng.core.common.beans.TokenMode;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TokenKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "tokens", noClassnameStored = true)
@Document("tokens")
@TypeAlias("tokens")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.PL)
public class Token implements PersistentEntity, UuidAware, NGAccountAccess, NGOrgAccess, NGProjectAccess, UniqueIdAware,
                              PersistentRegularIterable {
  public static final String sshPublicKeyFingerPrint = "sshPublicKey.fingerPrint";
  public static final String pgpPublicKeyFingerPrint = "pgpPublicKey.fingerprint";
  public static final String pgpPublicKeyKeyId = "pgpPublicKey.keyId";
  public static final String pgpPublicKeyParentKeyId = "pgpPublicKey.parentKeyId";
  public static final String scopedResourceMetadataParentResourceId = "scopedResourceMetadata.parentResourceId";
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("list_tokens_idx")
                 .field(TokenKeys.accountIdentifier)
                 .field(TokenKeys.orgIdentifier)
                 .field(TokenKeys.projectIdentifier)
                 .field(TokenKeys.apiKeyType)
                 .field(TokenKeys.parentIdentifier)
                 .field(TokenKeys.apiKeyIdentifier)
                 .build(),
            CompoundMongoIndex.builder()
                .name("parentUniqueId_identifier_apiKeyType_parentIdentifier_apiKeyIdentifier_unique_idx")
                .field(TokenKeys.parentUniqueId)
                .field(TokenKeys.identifier)
                .field(TokenKeys.apiKeyType)
                .field(TokenKeys.parentIdentifier)
                .field(TokenKeys.apiKeyIdentifier)
                .unique(true)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountIdentifier_parentUniqueId_identifier_apiKeyType_parentIdentifier_apiKeyIdentifier")
                .field(TokenKeys.accountIdentifier)
                .field(TokenKeys.parentUniqueId)
                .field(TokenKeys.identifier)
                .field(TokenKeys.apiKeyType)
                .field(TokenKeys.parentIdentifier)
                .field(TokenKeys.apiKeyIdentifier)
                .collation(
                    Collation.builder().locale(CollationLocale.ENGLISH).strength(CollationStrength.PRIMARY).build())
                .unique(true)
                .build(),
            CompoundMongoIndex.builder()
                .name("fingerPrint_accountIdentifier_idx")
                .field(sshPublicKeyFingerPrint)
                .field(TokenKeys.accountIdentifier)
                .build(),
            CompoundMongoIndex.builder()
                .name("pgpFingerPrint_accountIdentifier_idx")
                .field(pgpPublicKeyFingerPrint)
                .field(TokenKeys.accountIdentifier)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountIdentifier_apiKeyType_sshFingerPrint_idx")
                .field(TokenKeys.accountIdentifier)
                .field(TokenKeys.apiKeyType)
                .field(sshPublicKeyFingerPrint)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountIdentifier_apiKeyType_pgpFingerPrint_idx")
                .field(TokenKeys.accountIdentifier)
                .field(TokenKeys.apiKeyType)
                .field(pgpPublicKeyFingerPrint)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountIdentifier_apiKeyType_pgpKeyId_idx")
                .field(TokenKeys.accountIdentifier)
                .field(TokenKeys.apiKeyType)
                .field(pgpPublicKeyKeyId)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountIdentifier_apiKeyType_pgpParentKeyId_idx")
                .field(TokenKeys.accountIdentifier)
                .field(TokenKeys.apiKeyType)
                .field(pgpPublicKeyParentKeyId)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountIdentifier_scopedResourceMetadata_parentResourceId_apiKeyType_idx")
                .field(TokenKeys.accountIdentifier)
                .field(scopedResourceMetadataParentResourceId)
                .field(TokenKeys.apiKeyType)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountIdentifier_parentIdentifier_apiKeyType_tokenMode_idx")
                .field(TokenKeys.accountIdentifier)
                .field(TokenKeys.parentIdentifier)
                .field(TokenKeys.apiKeyType)
                .field(TokenKeys.tokenMode)
                .build())
        .build();
  }

  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  @NotNull String accountIdentifier;
  @EntityIdentifier(allowBlank = true) @Deprecated String orgIdentifier;
  @EntityIdentifier(allowBlank = true) @Deprecated String projectIdentifier;
  @NotNull String parentIdentifier;
  @NotNull ApiKeyType apiKeyType;
  SSHPublicKey sshPublicKey;
  PGPPublicKey pgpPublicKey;

  @EntityIdentifier String apiKeyIdentifier;

  @EntityIdentifier String identifier;
  @NGEntityName String name;
  @FdIndex String encodedPassword;
  Instant validFrom;
  Instant validTo;
  Instant scheduledExpireTime;
  RevocationReason revocationReason;
  @Size(max = 1024) String description;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;

  private Date validUntil;
  @FdUniqueIndex String uniqueId;
  String parentUniqueId;

  List<ScopedResourcePermission> scopedResourcePermissions;
  TokenMode tokenMode;
  ScopedResourceMetadata scopedResourceMetadata;

  @JsonIgnore
  public Instant getExpiryTimestamp() {
    return scheduledExpireTime != null ? scheduledExpireTime : validTo;
  }

  @JsonIgnore
  public boolean isValid() {
    Instant currentTime = Instant.now();
    return currentTime.isAfter(validFrom) && currentTime.isBefore(getExpiryTimestamp());
  }

  @FdIndex Long tokenExpiryAlertNextIteration;

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (TokenKeys.tokenExpiryAlertNextIteration.equals(fieldName)) {
      return this.tokenExpiryAlertNextIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName: " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (TokenKeys.tokenExpiryAlertNextIteration.equals(fieldName)) {
      this.tokenExpiryAlertNextIteration = nextIteration;
      return;
    }
    throw new InvalidArgumentsException("Invalid fieldName: " + fieldName);
  }
}