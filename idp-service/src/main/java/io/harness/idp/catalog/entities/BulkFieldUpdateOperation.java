/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "BulkFieldUpdateOperationKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.IDP)
@Entity(value = "bulkFieldUpdateOperations", noClassnameStored = true)
@Document("bulkFieldUpdateOperations")
@HarnessEntity(exportable = false)
@OwnedBy(HarnessTeam.IDP)
public class BulkFieldUpdateOperation implements PersistentEntity, CreatedByAware {
  @Id String id;
  String accountIdentifier;
  List<String> permittedEntityRefs;
  List<PropertyUpdate> properties;
  OperationStatus status;
  int matched;
  int permitted;
  int updated;
  List<SkippedItem> skipped;
  List<ErrorItem> errors;
  String errorMessage;
  int retryCount;
  long createdAt;
  long lastUpdatedAt;
  @CreatedBy EmbeddedUser createdBy;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountIdentifier_status")
                 .field(BulkFieldUpdateOperationKeys.accountIdentifier)
                 .field(BulkFieldUpdateOperationKeys.status)
                 .build())
        .build();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PropertyUpdate {
    String key;
    String value;
    String mode;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SkippedItem {
    String entityRef;
    String reason;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ErrorItem {
    String entityRef;
    String errorMessage;
  }
}
