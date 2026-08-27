/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Value
@Builder
@StoreIn(DbAliases.PMS)
@Entity(value = "dynamicExecutionInstance", noClassnameStored = true)
@Document("dynamicExecutionInstance")
@FieldNameConstants(innerTypeName = "DynamicExecutionInstanceKeys")
@TypeAlias("dynamicExecutionInstance")
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class DynamicExecutionInstance {
  // Reduce this after on-boarding on data-retention.
  public static final long TTL_MONTHS = 6;

  @FdUniqueIndex @NotNull String nodeExecutionId;
  @FdIndex @NotNull String planExecutionId;
  // Identifier of the node in the Parent execution which corresponds to this dynamic execution instance
  String identifier;
  @NotNull String yaml;
  String processedYaml;
  // TTL index
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());
}