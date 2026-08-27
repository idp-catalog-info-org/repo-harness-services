/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.yamlConversion;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@Builder(builderClassName = "Builder", toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "PipelineYamlConversionEntityKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "pipelineYamlConversions", noClassnameStored = true)
@Document("pipelineYamlConversions")
@TypeAlias("pipelineYamlConversion")
public class PipelineYamlConversionEntity implements PersistentEntity, UuidAware {
  public static final long TTL_MONTHS = 6;

  @With @Id @dev.morphia.annotations.Id private String uuid;

  @NotEmpty private String accountIdentifier;
  @NotEmpty private String orgIdentifier;
  @Trimmed @NotEmpty private String projectIdentifier;
  @NotEmpty @EntityIdentifier private String pipelineIdentifier;

  @NotNull private String originalV0Yaml;
  @NotNull private String convertedV1Yaml;

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
  @Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  @FdIndex String uniqueId;
  @FdIndex String parentUniqueId;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList
        .<MongoIndex>builder()
        // Primary lookup index
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_pipelineId_idx")
                 .unique(true)
                 .field(PipelineYamlConversionEntityKeys.accountIdentifier)
                 .field(PipelineYamlConversionEntityKeys.orgIdentifier)
                 .field(PipelineYamlConversionEntityKeys.projectIdentifier)
                 .field(PipelineYamlConversionEntityKeys.pipelineIdentifier)
                 .build())

        .build();
  }
}
