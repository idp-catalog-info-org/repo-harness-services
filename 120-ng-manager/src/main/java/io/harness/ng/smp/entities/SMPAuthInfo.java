/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.smp.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "SMPAuthInfoKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "smpAuthInfo", noClassnameStored = true)
@Document("smpAuthInfo")
@TypeAlias("smpAuthInfo")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.PL)
public class SMPAuthInfo implements PersistentEntity, UuidAware, NGAccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_smp_account_identifier_Index")
                 .unique(true)
                 .field(SMPAuthInfoKeys.smpAccountIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_identifier_Index")
                 .unique(true)
                 .field(SMPAuthInfoKeys.accountIdentifier)
                 .build())

        .build();
  }

  @org.springframework.data.annotation.Id @Id String uuid;
  @CreatedDate long createdAt;
  @LastModifiedDate long lastModifiedAt;

  @NotNull String smpAccountIdentifier;
  @NotNull String accountIdentifier;
  @NotNull private String publicKey;
  @NotNull private String privateKey;
}
