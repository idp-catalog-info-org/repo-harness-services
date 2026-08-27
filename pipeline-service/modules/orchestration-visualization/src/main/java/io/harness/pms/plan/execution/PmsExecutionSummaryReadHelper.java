/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class PmsExecutionSummaryReadHelper {
  private static final int MAX_BATCH_SIZE = 1000;
  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public PmsExecutionSummaryReadHelper(SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
  }

  public long findCount(Query query) {
    return secondaryMongoTemplate.count(Query.of(query).limit(-1).skip(-1), PipelineExecutionSummaryEntity.class);
  }

  public List<PipelineExecutionSummaryEntity> find(Query query) {
    return secondaryMongoTemplate.find(query, PipelineExecutionSummaryEntity.class);
  }

  public Stream<PipelineExecutionSummaryEntity> fetchExecutionSummaryEntityFromSecondary(Query query) {
    query.cursorBatchSize(MAX_BATCH_SIZE);
    validatePipelineExecutionSummaryStreamQuery(query);
    return secondaryMongoTemplate.stream(query, PipelineExecutionSummaryEntity.class);
  }

  public PipelineExecutionSummaryEntity findExecutionSummaryEntityFromSecondary(Query query) {
    return secondaryMongoTemplate.findOne(query, PipelineExecutionSummaryEntity.class);
  }

  private void validatePipelineExecutionSummaryStreamQuery(Query query) {
    if (query.getMeta().getCursorBatchSize() == null || query.getMeta().getCursorBatchSize() <= 0
        || query.getMeta().getCursorBatchSize() > MAX_BATCH_SIZE) {
      throw new InvalidRequestException(
          "PipelineExecutionSummaryEntity query should have cursorBatch limit within max batch size- "
          + MAX_BATCH_SIZE);
    }
    validatePipelineExecutionSummaryProjection(query);
  }

  private void validatePipelineExecutionSummaryProjection(Query query) {
    if (query.getFieldsObject().isEmpty()) {
      throw new InvalidRequestException("PipelineExecutionSummaryEntity list query should have projection fields");
    }
  }
}
