/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "IntegrationsKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.IDP)
@Entity(value = "integrations", noClassnameStored = true)
@Document("integrations")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.IDP)
public abstract class IntegrationEntity
    implements PersistentEntity, CreatedAtAware, UpdatedAtAware, CreatedByAware, UpdatedByAware {
  @Id private String id;
  @NotNull private String accountIdentifier;
  @NotNull private String identifier;
  @NotNull private Integration integration;
  @NotNull private ParentType parentType;
  private SubType subType;
  private String additionalIndexer;
  private boolean parentDeleted;
  private boolean managed;
  @NotNull @CreatedDate private long createdAt;
  @NotNull @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedDate private long lastUpdatedAt;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;

  public enum Integration { GIT, CATALOG }

  public enum ParentType { AZURE, BITBUCKET_CLOUD, BITBUCKET_SERVER, GITHUB, GITLAB, HARNESS_CODE_REPO, HARNESS_CD }

  public enum SubType { GITHUB_DIRECT, GITHUB_ENTERPRISE }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_parentType_subType")
                 .field(IntegrationsKeys.accountIdentifier)
                 .field(IntegrationsKeys.parentType)
                 .field(IntegrationsKeys.subType)
                 .field(IntegrationsKeys.additionalIndexer)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_additionalIndexer")
                 .field(IntegrationsKeys.accountIdentifier)
                 .field(IntegrationsKeys.additionalIndexer)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_identifier_integration")
                 .field(IntegrationsKeys.accountIdentifier)
                 .field(IntegrationsKeys.identifier)
                 .field(IntegrationsKeys.integration)
                 .build())
        .build();
  }

  public String getConfigId() {
    return this.getParentType() + (this.getSubType() != null ? "_" + this.getSubType() : "")
        + (this.getAdditionalIndexer() != null ? "_" + this.getAdditionalIndexer().toUpperCase().replace(".", "_")
                                               : "");
  }
}
