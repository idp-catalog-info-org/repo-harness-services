/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.entities;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.spec.server.idp.v1.model.Artifact;

import com.github.reinert.jjschema.SchemaIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@StoreIn(DbAliases.IDP)
@FieldNameConstants(innerTypeName = "CustomPluginInfoEntityKeys")
@Persistent
@OwnedBy(HarnessTeam.IDP)
@TypeAlias("io.harness.idp.configmanager.entities.CustomPluginInfoEntity")
public class CustomPluginInfoEntity
    extends PluginInfoEntity implements CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware {
  private Artifact artifact;
  private String packageName;
  private String statusApiUrl;
  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @CreatedDate private long createdAt;
  @SchemaIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @LastModifiedDate private long lastUpdatedAt;
}
