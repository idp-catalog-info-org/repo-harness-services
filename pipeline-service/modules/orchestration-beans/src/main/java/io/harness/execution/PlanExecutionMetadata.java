/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.yaml.HarnessYamlVersion;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Singular;
import lombok.With;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
@Data
@Builder(builderClassName = "Builder", toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "PlanExecutionMetadataKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "planExecutionsMetadata", noClassnameStored = true)
@Document("planExecutionsMetadata")
@TypeAlias("planExecutionMetadata")
public class PlanExecutionMetadata implements PersistentEntity, UuidAware, PmsNodeExecutionMetadata {
  public static final long TTL_MONTHS = 6;

  @With @Id @dev.morphia.annotations.Id private String uuid;

  @FdIndex private String accountIdentifier;

  @With private String planExecutionId;

  // Merged input set given by the customer
  private String inputSetYaml;

  // Final yaml after merging input sets to given yaml, given to plan creation
  private String yaml;

  private String unifiedYaml;

  // Pipeline yaml before resolving templates and input sets
  @Transient @Deprecated private String pipelineYaml;

  // Yaml having injectedUUid which is processed by PlanCreation
  @With @Deprecated private String processedYaml;

  // Expanded pipeline (after connectors, etc) in json format.
  // Note: we're not setting expandedJson, pipelineYaml in planExecutionMetadata anymore, and it'll default to null.
  // expandedJson is now passed as planExecutionMetadataWithContext to executions in order to avoid bloating memory in
  // mongoDB.
  @Deprecated private String expandedPipelineJson;
  @Deprecated private StagesExecutionMetadata stagesExecutionMetadata;
  private Boolean allowStagesExecution;
  private Boolean executionInputConfigured;
  @With private String triggerJsonPayload;
  @With private List<HeaderConfig> triggerHeader;
  @With private TriggerPayload triggerPayload;
  private Boolean notifyOnlyUser;
  @With private String notes;
  private RetryStagesMetadata retryStagesMetadata;
  private RetryExecutionInfo retryExecutionInfo;
  private List<String> referredTemplateIds;
  @Singular @Deprecated private List<PostExecutionRollbackInfo> postExecutionRollbackInfos;
  @Deprecated private Map<String, Object> stageExpressionValuesMap;

  // this token will be used for ambiance's functor token
  @With @Deprecated private Long expressionFunctorToken;

  @Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  // This will be list of id's of evaluations returned by OPA service post polices evaluation.
  private List<Integer> evaluatedPolicyIds;
  String harnessVersion;
  String parentUniqueId;

  @Setter @NonFinal @SchemaIgnore @CreatedDate @lombok.Builder.Default Long createdAt = 0L;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_idx")
                 .field(PlanExecutionMetadataKeys.planExecutionId)
                 .unique(true)
                 .build())
        .build();
  }

  @Override
  public NodeType forNodeType() {
    return NodeType.PLAN;
  }

  public boolean isStagesExecutionAllowed() {
    return allowStagesExecution != null && allowStagesExecution;
  }

  public String getHarnessVersion() {
    if (isEmpty(harnessVersion)) {
      return HarnessYamlVersion.V0;
    }
    return harnessVersion;
  }
}
