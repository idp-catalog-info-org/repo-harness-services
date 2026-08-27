/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entity.accountoverrides;

import static io.harness.entity.accountoverrides.beans.AccountOverridesConstants.DEFAULT_RETENTION_PERIOD_IN_MONTHS;

import io.harness.annotations.StoreIn;
import io.harness.entity.accountoverrides.DataRetentionSettings.DataRetentionSettingsKeys;
import io.harness.entity.accountoverrides.ExportSettings.ExportSettingsKeys;
import io.harness.entity.accountoverrides.LogStreamingLimits.LogStreamingLimitsKeys;
import io.harness.entity.accountoverrides.SearchSettings.SearchSettingsKeys;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.pms.accountoverrides.ExpressionCallType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

// Todo: Rename to AccountOverridesEntity.
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.PMS)
@FieldNameConstants(innerTypeName = "DataRetentionEntityKeys")
@Entity(value = "dataRetentionOverrides")
@Document(value = "dataRetentionOverrides")
@TypeAlias("dataRetentionOverrides")
public class DataRetentionEntity {
  @Id @dev.morphia.annotations.Id String id;
  @FdUniqueIndex @NotNull String accountIdentifier;
  Integer retentionPeriodInMonths;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastUpdatedAt;
  Long maxConcurrentExecutions;
  Long maxInputParameterSize;
  Long maxOutcomeResponseSize;
  Integer maxQueuedExecutionLimit;
  Integer maxTriggerCreationLimit;
  Long maxFileSize;
  Integer maxPipelineCreationLimit;
  Integer stepOrStageMaxConcurrency;
  Integer maxLeafStepConcurrency;
  DataRetentionSettings dataRetentionSettings;
  SearchSettings searchSettings;
  ExportSettings exportSettings;
  LogStreamingLimits logStreamingLimits;
  Long maxCustomWebhookPayloadSize;
  // Per-account overrides for the per-node expression-resolution call budget, keyed by call type (PIPE-34261).
  // Read-only from the pipeline side: there is no update/writeback API for these fields.
  Map<ExpressionCallType, Integer> maxExpressionCalls;

  @UtilityClass
  public static class DataRetentionEntityKeys {
    public static final String dataRetentionPeriod =
        DataRetentionEntityKeys.dataRetentionSettings + "." + DataRetentionSettingsKeys.dataRetentionPeriod;
    public static final String searchIndexMigrationStatus =
        DataRetentionEntityKeys.searchSettings + "." + SearchSettingsKeys.indexMigrationStatus;
    public static final String searchIndexMigrationOldIndexName =
        DataRetentionEntityKeys.searchSettings + "." + SearchSettingsKeys.oldIndexName;
    public static final String searchIndexMigrationNewIndexName =
        DataRetentionEntityKeys.searchSettings + "." + SearchSettingsKeys.newIndexName;
    public static final String maxExportRequestsPerDay =
        DataRetentionEntityKeys.exportSettings + "." + ExportSettingsKeys.maxExportRequestsPerDay;
    public static final String maxLogLines =
        DataRetentionEntityKeys.logStreamingLimits + "." + LogStreamingLimitsKeys.maxLogLines;
    public static final String maxLogLineLength =
        DataRetentionEntityKeys.logStreamingLimits + "." + LogStreamingLimitsKeys.maxLogLineLength;
    public static final String streamExpirationSeconds =
        DataRetentionEntityKeys.logStreamingLimits + "." + LogStreamingLimitsKeys.streamExpirationSeconds;
    public static final String maxLogSizeBytes =
        DataRetentionEntityKeys.logStreamingLimits + "." + LogStreamingLimitsKeys.maxLogSizeBytes;
    public static final String maxWriteLogLinesPerMinute =
        DataRetentionEntityKeys.logStreamingLimits + "." + LogStreamingLimitsKeys.maxWriteLogLinesPerMinute;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("dataRetentionSettings_idx")
                 .field(DataRetentionEntityKeys.dataRetentionSettings)
                 .build())
        .build();
  }

  public int getRetentionPeriodInMonths() {
    if (retentionPeriodInMonths == null) {
      return DEFAULT_RETENTION_PERIOD_IN_MONTHS;
    }
    return retentionPeriodInMonths;
  }
}
