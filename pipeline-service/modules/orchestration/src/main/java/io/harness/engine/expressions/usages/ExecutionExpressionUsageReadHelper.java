/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.engine.expressions.usages;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.expressions.usages.beans.ExecutionExpressionUsagesEntity;
import io.harness.exception.InvalidRequestException;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionExpressionUsageReadHelper {
  private final int MAX_BATCH_SIZE = 1000;
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public ExecutionExpressionUsageReadHelper(SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }
  public Stream<ExecutionExpressionUsagesEntity> fetchExecutionExpressionUsagesEntity(Query query) {
    query.cursorBatchSize(MAX_BATCH_SIZE);
    validateExecutionExpressionUsagesEntityStreamQuery(query);
    return secondaryMongoTemplate.stream(query, ExecutionExpressionUsagesEntity.class);
  }

  private void validateExecutionExpressionUsagesEntityStreamQuery(Query query) {
    if (query.getMeta().getCursorBatchSize() == null || query.getMeta().getCursorBatchSize() <= 0
        || query.getMeta().getCursorBatchSize() > MAX_BATCH_SIZE) {
      throw new InvalidRequestException(
          "ExecutionExpressionUsagesEntity query should have cursorBatch limit within max batch size- "
          + MAX_BATCH_SIZE);
    }
    validateExecutionExpressionUsagesEntityProjection(query);
  }

  private void validateExecutionExpressionUsagesEntityProjection(Query query) {
    if (query.getFieldsObject().isEmpty()) {
      throw new InvalidRequestException("ExecutionExpressionUsagesEntity list query should have projection fields");
    }
  }
}
