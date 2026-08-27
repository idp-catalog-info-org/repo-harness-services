/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.iterator.PersistentNGCronIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ngtriggers.beans.dto.TriggerExecutorDTO;
import io.harness.ngtriggers.beans.entity.metadata.BuildMetadata.BuildMetadataKeys;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata.NGTriggerMetadataKeys;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus.TriggerStatusKeys;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Data
@Builder
@FieldNameConstants(innerTypeName = "NGTriggerEntityKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "triggersNG", noClassnameStored = true)
@Document("triggersNG")
@TypeAlias("triggersNG")
@HarnessEntity(exportable = true)
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerEntity implements PersistentEntity, PersistentNGCronIterable, UniqueIdAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_parentUniqueId_targetIdentifier_triggerType_identifier")
                 .unique(true)
                 .field(NGTriggerEntityKeys.accountId)
                 .field(NGTriggerEntityKeys.parentUniqueId)
                 .field(NGTriggerEntityKeys.targetIdentifier)
                 .field(NGTriggerEntityKeys.targetType)
                 .field(NGTriggerEntityKeys.identifier)
                 .build(),
            CompoundMongoIndex.builder()
                .name("unique_accountId_parentUniqueId_identifier")
                .unique(false)
                .field(NGTriggerEntityKeys.accountId)
                .field(NGTriggerEntityKeys.parentUniqueId)
                .field(NGTriggerEntityKeys.identifier)
                .build(),
            CompoundMongoIndex.builder()
                .name("accountId_parentUniqueId_type_enabled_deleted")
                .field(NGTriggerEntityKeys.accountId)
                .field(NGTriggerEntityKeys.parentUniqueId)
                .field(NGTriggerEntityKeys.type)
                .field(NGTriggerEntityKeys.enabled)
                .field(NGTriggerEntityKeys.deleted)
                .build(),
            CompoundMongoIndex.builder()
                .name("type_repoUrl_accountId_parentUniqueId")
                .field(NGTriggerEntityKeys.type)
                .field("metadata.webhook.git.connectorIdentifier")
                .field(NGTriggerEntityKeys.accountId)
                .field(NGTriggerEntityKeys.parentUniqueId)
                .build(),
            CompoundMongoIndex.builder()
                .name("accId_sourcerepo_index")
                .field(NGTriggerEntityKeys.accountId)
                .field("metadata.webhook.type")
                .build(),
            CompoundMongoIndex.builder()
                .name("accId_signature_index")
                .field(NGTriggerEntityKeys.accountId)
                .field("metadata.buildMetadata.pollingConfig.signature")
                .build(),
            CompoundMongoIndex.builder()
                .name("webhookToken_index")
                .field(NGTriggerEntityKeys.customWebhookToken)
                .build(),
            CompoundMongoIndex.builder()
                .name("accId_signature_index_for_multibuildmetadata")
                .field(NGTriggerEntityKeys.accountId)
                .field("metadata.multiBuildMetadata.pollingConfig.signature")
                .build(),
            CompoundMongoIndex.builder()
                .name("type_nextIterations")
                .field(NGTriggerEntityKeys.type)
                .field(NGTriggerEntityKeys.nextIterations)
                .build(),
            CompoundMongoIndex.builder()
                .name("type_enabled_accountId")
                .field(NGTriggerEntityKeys.type)
                .field(NGTriggerEntityKeys.enabled)
                .field(NGTriggerEntityKeys.accountId)
                .build(),
            CompoundMongoIndex
                .builder()
                // For usage in OptimizedS3TriggersMigration.java
                .name("accountId_parentUniqueId_type_buildSourceType_enabled_deleted")
                .field(NGTriggerEntityKeys.accountId)
                .field(NGTriggerEntityKeys.parentUniqueId)
                .field(NGTriggerEntityKeys.type)
                .field(NGTriggerEntityKeys.metadata + "." + NGTriggerMetadataKeys.buildMetadata + "."
                    + BuildMetadataKeys.buildSourceType)
                .field(NGTriggerEntityKeys.enabled)
                .field(NGTriggerEntityKeys.deleted)
                .build(),
            SortCompoundMongoIndex
                .builder()
                // For triggers list view and for finding triggers for custom webhook
                .name("accountId_parentUniqueId_createdAt_targetIdentifier")
                .field(NGTriggerEntityKeys.accountId)
                .field(NGTriggerEntityKeys.parentUniqueId)
                .descSortField(NGTriggerEntityKeys.createdAt)
                .ascRangeField(NGTriggerEntityKeys.targetIdentifier)
                .build(),
            CompoundMongoIndex
                .builder()
                // For triggers of Harness Artifact Registry webhook
                .name("accountId_parentUniqueId_type_enabled_metadataWebhookType_harMetadata")
                .field(NGTriggerEntityKeys.accountId)
                .field(NGTriggerEntityKeys.parentUniqueId)
                .field(NGTriggerEntityKeys.type)
                .field(NGTriggerEntityKeys.enabled)
                .field("metadata.webhook.type")
                .field("metadata.webhook.harMetadata.registryName")
                .field("metadata.webhook.harMetadata.actions")
                .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id String uuid;
  @FdIndex String parentUniqueId;
  @FdIndex String uniqueId;
  @EntityName String name;
  @EntityIdentifier @NotEmpty String identifier;
  @Size(max = 1024) String description;
  @NotEmpty String yaml;
  @NotEmpty NGTriggerType type;
  String status;
  TriggerStatus triggerStatus;
  @NotEmpty String accountId;
  @NotEmpty @Deprecated String orgIdentifier;
  @NotEmpty @Deprecated String projectIdentifier;
  @NotEmpty String targetIdentifier;
  @NotEmpty TargetType targetType;

  @NotEmpty NGTriggerMetadata metadata;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
  @Version Long version;
  @Builder.Default Boolean deleted = Boolean.FALSE;
  @Builder.Default Boolean withServiceV2 = Boolean.FALSE;
  @Singular @Size(max = 128) List<NGTag> tags;
  @Builder.Default Boolean enabled = Boolean.TRUE;
  String pollInterval;
  String webhookId;
  String customWebhookToken;
  String encryptedWebhookSecretIdentifier;
  List<String> stagesToExecute;
  ParameterField<List<String>> stagesToExecuteV2;
  @Setter @NonFinal String harnessVersion;
  @FdIndex private List<Long> nextIterations; // List of activation times for cron triggers
  @Builder.Default Long ymlVersion = Long.valueOf(3);
  String pipelineBranchName;
  Boolean cronAndSemantics;

  @Builder.Default Boolean isUnifiedPipelineFlow = Boolean.FALSE;

  TriggerExecutorDTO executorInfo;

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    if (metadata.getCron() == null || nextIterations == null) {
      return new ArrayList<>();
    }
    try {
      String cronExpr = metadata.getCron().getExpression();
      String cronType = StringUtils.isBlank(metadata.getCron().getType()) ? "UNIX" : metadata.getCron().getType();
      expandNextIterations(skipMissed, throttled, cronExpr, nextIterations, cronType, metadata.getCron().getTimezone(),
          Boolean.TRUE.equals(cronAndSemantics));
    } catch (Exception e) {
      log.error("Failed to schedule executions for trigger {}", uuid, e);
      throw e;
    }
    return nextIterations;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (metadata.getCron() == null || nextIterations == null) {
      return null;
    }
    return nextIterations.get(0);
  }

  @UtilityClass
  public static class NGTriggerEntityKeys {
    public static final String pollingSubscriptionStatus =
        NGTriggerEntityKeys.triggerStatus + "." + TriggerStatusKeys.pollingSubscriptionStatus;
  }
  public Boolean getWithServiceV2() {
    return withServiceV2 != null && withServiceV2;
  }

  public String getHarnessVersion() {
    if (harnessVersion == null) {
      return HarnessYamlVersion.V0;
    }
    return harnessVersion;
  }
}
