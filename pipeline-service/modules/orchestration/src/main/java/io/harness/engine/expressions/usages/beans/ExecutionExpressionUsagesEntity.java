/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.usages.beans;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.expressions.usages.beans.ExpressionValueMetadata.ExpressionValueMetadataKeys;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import lombok.experimental.UtilityClass;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
@StoreIn(DbAliases.PMS)
@FieldNameConstants(innerTypeName = "ExecutionExpressionUsagesEntityKeys")
@Entity(value = "executionExpressionUsage")
@Document("executionExpressionUsage")
@TypeAlias("executionExpressionUsage")
@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionExpressionUsagesEntity implements PersistentEntity {
  public static final long TTL_MONTHS = 6;
  @NonFinal @Id @dev.morphia.annotations.Id String uuid;
  @NotEmpty String planExecutionId;
  @NotEmpty String nodeExecutionId;
  String expression;
  // Can be string, integer, list, etc
  @Builder.Default Object expressionValue;
  // count give how many times this expression appeared in the nodeExecution
  @Builder.Default Integer count = 1;
  boolean isError;
  ExpressionValueMetadata metadata;
  @Builder.Default Long createdAt = 0L;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_pipelineId_nodeFQN_expressionHash_createdAt")
                 .field(ExecutionExpressionUsagesEntityKeys.accountId)
                 .field(ExecutionExpressionUsagesEntityKeys.orgIdentifier)
                 .field(ExecutionExpressionUsagesEntityKeys.projectIdentifier)
                 .field(ExecutionExpressionUsagesEntityKeys.pipelineIdentifier)
                 .field(ExecutionExpressionUsagesEntityKeys.nodeFQNHash)
                 .field(ExecutionExpressionUsagesEntityKeys.expressionHash)
                 .field(ExecutionExpressionUsagesEntityKeys.isError)
                 .descSortField(ExecutionExpressionUsagesEntityKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_nodeExecutionId")
                 .field(ExecutionExpressionUsagesEntityKeys.planExecutionId)
                 .field(ExecutionExpressionUsagesEntityKeys.nodeExecutionId)
                 .build())
        .build();
  }

  @UtilityClass
  public static class ExecutionExpressionUsagesEntityKeys {
    public String accountId =
        ExecutionExpressionUsagesEntityKeys.metadata + "." + ExpressionValueMetadataKeys.accountIdentifier;
    @Deprecated
    public String orgIdentifier =
        ExecutionExpressionUsagesEntityKeys.metadata + "." + ExpressionValueMetadataKeys.orgIdentifier;
    @Deprecated
    public String projectIdentifier =
        ExecutionExpressionUsagesEntityKeys.metadata + "." + ExpressionValueMetadataKeys.projectIdentifier;
    public String parentUniqueId =
        ExecutionExpressionUsagesEntityKeys.metadata + "." + ExpressionValueMetadataKeys.parentUniqueId;
    public String pipelineIdentifier =
        ExecutionExpressionUsagesEntityKeys.metadata + "." + ExpressionValueMetadataKeys.pipelineIdentifier;
    public String nodeFQNHash =
        ExecutionExpressionUsagesEntityKeys.metadata + "." + ExpressionValueMetadataKeys.nodeFQNHash;
    public String expressionHash =
        ExecutionExpressionUsagesEntityKeys.metadata + "." + ExpressionValueMetadataKeys.expressionHash;
  }
}
