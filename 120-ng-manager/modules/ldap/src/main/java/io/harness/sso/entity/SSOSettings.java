/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sso.entity;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.iterator.PersistentCronIterable;
import io.harness.iterator.interfaces.PersistentIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;

import software.wings.beans.sso.SSOType;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@FieldNameConstants(innerTypeName = "NgSsoSettingsKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "ngSsoSettings", noClassnameStored = true)
@Document("ngSsoSettings")
@TypeAlias("ngSsoSettings")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.PL)
@Persistent
public abstract class SSOSettings
    implements PersistentEntity, PersistentIterable, PersistentCronIterable, UniqueIdAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountTypeIdx")
                 .field(NgSsoSettingsKeys.accountIdentifier)
                 .field(NgSsoSettingsKeys.type)
                 .field(NgSsoSettingsKeys.identifier)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("syncNextIterationIdx")
                 .field(NgSsoSettingsKeys.type)
                 .field(NgSsoSettingsKeys.nextIterations)
                 .build())
        .build();
  }
  @Id @dev.morphia.annotations.Id String id;
  @NotEmpty @EntityIdentifier String identifier;
  @NotEmpty @NGEntityName String name;

  @NotNull String accountIdentifier;
  @NotNull protected SSOType type;
  @NotNull protected String displayName;
  @NotNull protected String url;
  @FdIndex List<Long> nextIterations = new ArrayList<>();

  @FdUniqueIndex @NotEmpty String uniqueId;
  @NotEmpty String parentUniqueId;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedDate;

  public abstract SSOType getType();

  public SSOSettings(SSOType type, String name, String identifier, String url, String accountIdentifier) {
    this.type = type;
    this.name = name;
    this.url = url;
    this.accountIdentifier = accountIdentifier;
    this.identifier = identifier;
    this.parentUniqueId = accountIdentifier;
  }
}
