/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.entities;

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
import io.harness.spec.server.idp.v1.model.CustomPropertiesBase;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder(toBuilder = true)
@FieldNameConstants(innerTypeName = "CatalogCustomPropertyKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "catalogCustomProperties", noClassnameStored = true)
@Document("catalogCustomProperties")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class CatalogCustomPropertyEntity
    implements PersistentEntity, CreatedByAware, UpdatedByAware, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_entityRef_field")
                 .unique(true)
                 .field(CatalogCustomPropertyKeys.accountIdentifier)
                 .field(CatalogCustomPropertyKeys.field)
                 .field(CatalogCustomPropertyKeys.entityRef)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_entityRef")
                 .field(CatalogCustomPropertyKeys.accountIdentifier)
                 .field(CatalogCustomPropertyKeys.entityRef)
                 .build())
        .build();
  }

  @Id private String id;
  private String accountIdentifier;
  private String entityRef;
  private String field;
  private String value;
  private CustomPropertiesBase.ModeEnum mode;
  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @SchemaIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @CreatedDate private long createdAt;
  @LastModifiedDate private long lastUpdatedAt;
}
