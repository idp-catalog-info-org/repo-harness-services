/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UniqueIdAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Value
@Builder
@FieldNameConstants(innerTypeName = "PlanCreationQueueRequestKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "planCreationQueueRequests", noClassnameStored = true)
@Document("planCreationQueueRequests")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("planCreationQueueRequest")
public class PlanCreationQueueRequest implements PersistentEntity, UuidAware, UniqueIdAware {
  public static final long TTL_MONTHS = 1;
  @Setter @NonFinal @Id @dev.morphia.annotations.Id String uuid;
  String accountId;
  String orgId;
  String projectId;
  @NotEmpty @FdUniqueIndex String planExecutionId;
  ScopeInfo scopeInfo;
  boolean isParentIdQueryingEnabled;
  boolean isDebug;
  boolean isRetry;
  List<String> identifierOfSkipStages;
  String previousExecutionId;
  List<String> retryStagesIdentifier;
  boolean runAllStages;
  String pipelineYamlWithTemplateRef;
  String branch;
  boolean isDynamicExecution;
  @CreatedDate Long createdAt;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());
  @Setter @NonFinal String uniqueId;
  String parentUniqueId;
}