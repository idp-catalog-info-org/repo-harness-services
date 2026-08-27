/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
import io.harness.beans.HeaderConfig;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.contracts.triggers.TriggerPayload;

import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Data
@Builder
@FieldNameConstants(innerTypeName = "TriggerFailureNotificationDetailsKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "triggerFailureNotificationDetails", noClassnameStored = true)
@Document("triggerFailureNotificationDetails")
@TypeAlias("triggerFailureNotificationDetailsEntity")
@HarnessEntity(exportable = true)
@OwnedBy(PIPELINE)
public class TriggerFailureNotificationDetailsEntity implements PersistentEntity {
  // TODO : add appropriate indexes
  @Id @dev.morphia.annotations.Id String uuid;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String triggerIdentifier;
  String triggerName;
  NGTriggerType ngTriggerType;
  String triggerSubType;
  String eventCorrelationId;
  TriggerPayload triggerPayload;
  List<HeaderConfig> headerConfigs;
  String payload;
  String pipelineIdentifier;
  String pipelineName;
  String errorMessage;
  Long eventCreatedAt; // This is the time when trigger event record is created
  @FdIndex String uniqueId;
  @FdIndex String parentUniqueId;
  @CreatedDate Long createdAt;
  @FdTtlIndex @Default Date validUntil = Date.from(OffsetDateTime.now().plusDays(7).toInstant());
}