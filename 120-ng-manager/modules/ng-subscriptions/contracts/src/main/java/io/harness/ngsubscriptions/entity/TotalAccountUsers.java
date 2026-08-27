/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "TotalAccountUsersKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "totalAccountUsers", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("totalAccountUsers")
@Persistent
@TypeAlias("TotalAccountUsersEntity")
@OwnedBy(HarnessTeam.PL)
public class TotalAccountUsers implements PersistentEntity, NGAccountAccess {
  @Id @dev.morphia.annotations.Id String id;
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed @NotEmpty int year;
  @Trimmed @NotEmpty int month;
  @NotNull long users;
  @NotNull long serviceAccounts;
  // Useless fields for this entity.
  String parentUniqueId;
  String uniqueId;

  @CreatedDate long created;
  @LastModifiedDate long updated;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_year_month_unique_index")
                 .field(TotalAccountUsersKeys.accountIdentifier)
                 .field(TotalAccountUsersKeys.year)
                 .field(TotalAccountUsersKeys.month)
                 .unique(true)
                 .build())
        .build();
  }
}
