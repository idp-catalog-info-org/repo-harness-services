/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.homepage.entities;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.spec.server.idp.v1.model.BannerInfo;
import io.harness.spec.server.idp.v1.model.HeaderInfo;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
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
@Builder
@FieldNameConstants(innerTypeName = "HomePageLayoutEntityKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "homePageLayouts", noClassnameStored = true)
@Document("homePageLayouts")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class HomePageLayoutEntity
    implements PersistentEntity, CreatedAtAware, UpdatedAtAware, CreatedByAware, UpdatedByAware {
  @Id @org.mongodb.morphia.annotations.Id private String id;
  @FdUniqueIndex private String accountIdentifier;
  @NotNull private HeaderInfo header;
  @NotNull private BannerInfo banner;
  @NotNull private List<String> cards;

  @NotNull @CreatedDate private long createdAt;
  @NotNull @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedDate private long lastUpdatedAt;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;
}
