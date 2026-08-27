/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.entities;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.persistence.UniqueIdAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Persistent
@FieldNameConstants(innerTypeName = "BrandingAssetKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "brandingAssets", noClassnameStored = true)
@Document(collection = "brandingAssets")
@TypeAlias("brandingAssets")
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.PL)
public class BrandingAsset implements UniqueIdAware, NGAccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_assetType_unique_index")
                 .unique(true)
                 .field(BrandingAssetKeys.accountIdentifier)
                 .field(BrandingAssetKeys.assetType)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String id;
  @FdUniqueIndex @NotEmpty String uniqueId;
  @NotEmpty String parentUniqueId;
  @NotEmpty String accountIdentifier;
  @NotEmpty String assetType;
  @NotEmpty String assetId;
  @NotEmpty String mimeType;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
}
