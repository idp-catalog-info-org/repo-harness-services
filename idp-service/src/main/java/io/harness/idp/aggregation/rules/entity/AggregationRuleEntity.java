/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.iterator.interfaces.PersistentIterable;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "AggregationRuleKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "aggregationRules", noClassnameStored = true)
@Document("aggregationRules")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class AggregationRuleEntity implements PersistentEntity, CreatedByAware, UpdatedByAware, CreatedAtAware,
                                              UpdatedAtAware, PersistentIterable, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_identifier")
                 .unique(true)
                 .field(AggregationRuleEntity.AggregationRuleKeys.accountIdentifier)
                 .field(AggregationRuleEntity.AggregationRuleKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_name")
                 .unique(true)
                 .field(AggregationRuleEntity.AggregationRuleKeys.accountIdentifier)
                 .field(AggregationRuleEntity.AggregationRuleKeys.name)
                 .build())
        .build();
  }

  @Id private String id;
  private String accountIdentifier;
  private String identifier;
  private String name;
  private String description;
  private String fieldForAgg;
  private AggregationFormula aggFormula;
  private Set<Scope> scopesToAggregateAt;
  private AggregationType aggregationType;
  private EntitySelectionCriteria entitySelectionCriteria;
  private ComputedStatus status;
  private long lastComputedAt;
  private boolean isDeleted;
  private long deletedAt;
  private String lastErrorMessage;
  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @SchemaIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastUpdatedAt;
  @FdIndex Long nextIteration;

  public enum AggregationFormula { SUM, AVG, COUNT, MAX, MIN, MEDIAN }

  public enum Scope { ACCOUNT, ORGANIZATION, PROJECT, SYSTEM, TEAM }

  public enum ComputedStatus { CALCULATING, ERROR, SUCCESS }

  public enum AggregationType { METRIC, SCORECARD }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EntitySelectionCriteria {
    private String kind;
    private String type;
    private List<String> owners;
    private List<String> tags;
    private List<String> lifecycles;
    private List<String> scopes;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return this.nextIteration;
  }

  @Override
  public String getUuid() {
    return this.id;
  }

  public AggregationType getAggregationType() {
    return aggregationType == null ? AggregationType.METRIC : aggregationType;
  }
}
