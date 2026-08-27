/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.entities;

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
import io.harness.spec.server.idp.v1.model.BannerInfo;
import io.harness.spec.server.idp.v1.model.HeaderInfo;

import com.google.common.collect.ImmutableList;
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
@FieldNameConstants(innerTypeName = "PersonaViewEntityKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "personaViews", noClassnameStored = true)
@Document("personaViews")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class PersonaViewEntity
    implements PersistentEntity, CreatedAtAware, UpdatedAtAware, CreatedByAware, UpdatedByAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_identifier")
                 .unique(true)
                 .field(PersonaViewEntityKeys.accountIdentifier)
                 .field(PersonaViewEntityKeys.identifier)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String id;
  @NotNull private String accountIdentifier;
  @NotNull private String identifier;
  @NotNull private String name;
  private String description;
  private List<String> userGroupIdentifiers;
  /**
   * Ordered list of card identifiers. Identifiers prefixed with {@code ootb:} resolve under the reserved
   * {@code __GLOBAL_ACCOUNT_ID__} account; all other identifiers resolve under {@link #accountIdentifier}.
   */
  @NotNull private List<String> cards;
  /**
   * Optional header/banner chrome rendered around the card grid. Stored as the API model types (same shape the
   * homepage uses) so every persona view — OOTB ({@code platform}, {@code leadership}) and custom alike — can carry
   * its own chrome. The synthetic Developer's View does not use these fields; its chrome lives on the homepage
   * layout instead.
   */
  private HeaderInfo header;
  private BannerInfo banner;
  /** True for the Harness-managed OOTB views ({@code platform}, {@code leadership}). */
  private Boolean ootb;

  @NotNull @CreatedDate private long createdAt;
  @NotNull @CreatedBy private EmbeddedUser createdBy;
  @LastModifiedDate private long lastUpdatedAt;
  @LastModifiedBy private EmbeddedUser lastUpdatedBy;
}
