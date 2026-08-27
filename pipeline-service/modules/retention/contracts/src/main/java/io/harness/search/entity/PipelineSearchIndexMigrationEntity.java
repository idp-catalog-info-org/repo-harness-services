/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.search.entity;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.iterator.interfaces.PersistentRegularIterable;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/*
 * This entity is used for the elastic search migration job, whenever a customer changes their retention plan
 * we have to move their data from one elastic search index to another which is an account specific index
 * The migration iterator will pick up this migration and migrate the data in elastic
 * More details: https://harness.atlassian.net/wiki/spaces/CDNG/pages/21743763510/ElasticSearch+Indexing+Strategy
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.PMS)
@FieldNameConstants(innerTypeName = "PipelineSearchIndexMigrationEntityKeys")
@Entity(value = "searchIndexMigration")
@Document(value = "searchIndexMigration")
@TypeAlias("searchIndexMigration")
@OwnedBy(HarnessTeam.PIPELINE)
@HarnessEntity(exportable = false)
@Persistent
public class PipelineSearchIndexMigrationEntity implements PersistentEntity, PersistentRegularIterable {
  @Id @dev.morphia.annotations.Id String uuid;
  @FdUniqueIndex @NotNull String accountIdentifier;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
  String elasticTaskID;
  // This field stores the task id to sync the records which were inserted b/w migration start time +- 5 minutes
  String elasticBufferSyncTaskID;
  PipelineSearchMigrationStatus status;
  PipelineSearchIndexRetentionPeriods oldIndexRetentionPeriod;
  PipelineSearchIndexRetentionPeriods newIndexRetentionPeriod;
  Long migrationStartTime;
  Long migrationEndTime;

  @Getter @NonFinal @Setter Long nextIteration;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }
}
