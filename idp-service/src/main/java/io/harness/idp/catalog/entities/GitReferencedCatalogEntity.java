/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.StoreType;
import io.harness.persistence.gitaware.GitAware;

import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "GitReferencedCatalogEntityKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GitReferencedCatalogEntity extends CatalogEntity implements GitAware {
  @NotEmpty StoreType storeType;
  @NotEmpty String repo;
  @NotEmpty String filePath;
  String connectorRef;
  @NotEmpty String repoURL;
  @NotEmpty String fallBackBranch;

  @Override
  public String getData() {
    return super.getYaml();
  }

  @Override
  public void setData(String data) {
    super.setYaml(data);
  }

  @Override
  public EntityType getEntityType() {
    return EntityType.IDP_CATALOG;
  }
}
