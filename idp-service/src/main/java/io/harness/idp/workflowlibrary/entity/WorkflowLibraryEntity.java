/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.workflowlibrary.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "WorkflowLibraryKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "workflowLibrary", noClassnameStored = true)
@Document("workflowLibrary")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class WorkflowLibraryEntity implements PersistentEntity {
  public static final String STATUS_GA = "ga";
  public static final String STATUS_PREVIEW = "preview";

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_identifier_version")
                 .unique(true)
                 .field(WorkflowLibraryKeys.identifier)
                 .field(WorkflowLibraryKeys.version)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("idx_category_stable_deprecated")
                 .field(WorkflowLibraryKeys.category)
                 .field(WorkflowLibraryKeys.isStable)
                 .field(WorkflowLibraryKeys.deprecated)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("idx_stable_deprecated")
                 .field(WorkflowLibraryKeys.isStable)
                 .field(WorkflowLibraryKeys.deprecated)
                 .build())
        .build();
  }

  @Id private String id;
  private String identifier;
  private String version;
  @JsonProperty("isStable") private boolean isStable;
  private boolean deprecated;

  private String name;
  private String description;
  private String longDescription;
  private String category;
  private String icon;
  private List<String> tags;

  private List<WorkflowAdminInput> adminInputs;
  private String workflowYaml;
  private List<WorkflowPipelineSnapshot> pipelines;
  private List<WorkflowTemplateSnapshot> templates;

  private String status;

  private String gitCommitId;
  private long syncedAt;
  @CreatedDate private long createdAt;
}
