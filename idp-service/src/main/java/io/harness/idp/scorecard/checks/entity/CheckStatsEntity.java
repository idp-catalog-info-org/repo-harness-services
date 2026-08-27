/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scorecards.beans.StatsMetadata;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "CheckStatsKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "checkStats", noClassnameStored = true)
@Document("checkStats")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class CheckStatsEntity implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_entityIdentifier_checkIdentifier_custom")
                 .unique(true)
                 .field(CheckStatsKeys.accountIdentifier)
                 .field(CheckStatsKeys.entityIdentifier)
                 .field(CheckStatsKeys.checkIdentifier)
                 .field(CheckStatsKeys.isCustom)
                 .build())
        .build();
  }

  @Id private String id;
  private String accountIdentifier;
  private String entityIdentifier;
  private String checkIdentifier;
  private boolean isCustom;
  private StatsMetadata metadata;
  private String status;

  private long createdAt;
  private long lastUpdatedAt;
}
