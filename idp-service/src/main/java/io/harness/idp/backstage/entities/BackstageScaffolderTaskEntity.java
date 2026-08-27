/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "BackstageScaffolderTasksKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@StoreIn(DbAliases.IDP)
@Entity(value = "backstageScaffolderTasks", noClassnameStored = true)
@Document("backstageScaffolderTasks")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.IDP)
public class BackstageScaffolderTaskEntity implements PersistentEntity {
  @Id private String id;
  @NotNull String accountIdentifier;
  @NotNull String identifier;
  @NotNull String spec;
  @NotNull String status;
  long taskCreatedAt;
  long lastHeartbeatAt;
  @NotNull String secrets;
  @NotNull String taskCreatedBy;

  String entityRef;
  String name;

  @JsonIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(12).toInstant());
  @JsonIgnore private String yaml;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_identifier")
                 .field(BackstageScaffolderTasksKeys.accountIdentifier)
                 .field(BackstageScaffolderTasksKeys.identifier)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_entityRef_createdBy_status_createdAt")
                 .field(BackstageScaffolderTasksKeys.accountIdentifier)
                 .field(BackstageScaffolderTasksKeys.entityRef)
                 .field(BackstageScaffolderTasksKeys.taskCreatedBy)
                 .field(BackstageScaffolderTasksKeys.status)
                 .field(BackstageScaffolderTasksKeys.taskCreatedAt)
                 .build())
        .build();
  }
}
