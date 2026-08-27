/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.annotations;

import static io.harness.annotations.dev.HarnessTeam.CI;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.annotations.PipelineAnnotationEntity;
import io.harness.pms.annotations.PipelineAnnotationEntity.PipelineAnnotationEntityKeys;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CI)
public class PipelineAnnotationRepositoryCustomImpl implements PipelineAnnotationRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public PipelineAnnotationEntity upsertAnnotation(PipelineAnnotationEntity annotationEntity, String mode) {
    Query query = buildUpsertQuery(annotationEntity);
    Update update = buildUpdateObject(annotationEntity);
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);
    PipelineAnnotationEntity result =
        mongoTemplate.findAndModify(query, update, options, PipelineAnnotationEntity.class);
    log.debug("Upserted annotation for planExecutionId: {}, context: {}, mode: {}",
        annotationEntity.getPlanExecutionId(), annotationEntity.getContextId(),
        mode != null ? mode.toLowerCase() : "replace");

    return result;
  }

  private Query buildUpsertQuery(PipelineAnnotationEntity annotationEntity) {
    return new Query(where(PipelineAnnotationEntityKeys.planExecutionId)
                         .is(annotationEntity.getPlanExecutionId())
                         .and(PipelineAnnotationEntityKeys.contextId)
                         .is(annotationEntity.getContextId()));
  }

  private Update buildUpdateObject(PipelineAnnotationEntity annotationEntity) {
    Update update = new Update();

    setBasicFields(update, annotationEntity);
    setSummary(update, annotationEntity);
    setAnnotationFields(update, annotationEntity);
    setInsertOnlyFields(update, annotationEntity);

    return update;
  }

  private void setBasicFields(Update update, PipelineAnnotationEntity annotationEntity) {
    if (annotationEntity.getStageExecutionId() != null) {
      update.set(PipelineAnnotationEntityKeys.stageExecutionId, annotationEntity.getStageExecutionId());
    }
    if (annotationEntity.getOrgId() != null) {
      update.set(PipelineAnnotationEntityKeys.orgId, annotationEntity.getOrgId());
    }
    if (annotationEntity.getProjectId() != null) {
      update.set(PipelineAnnotationEntityKeys.projectId, annotationEntity.getProjectId());
    }
    if (annotationEntity.getPipelineId() != null) {
      update.set(PipelineAnnotationEntityKeys.pipelineId, annotationEntity.getPipelineId());
    }

    update.set(PipelineAnnotationEntityKeys.lastUpdatedAt, System.currentTimeMillis());
  }

  private void setSummary(Update update, PipelineAnnotationEntity annotationEntity) {
    // Service layer handles GCS operations and extracts preview - repository just stores it
    if (annotationEntity.getSummary() != null && !annotationEntity.getSummary().isEmpty()) {
      update.set(PipelineAnnotationEntityKeys.summary, annotationEntity.getSummary());
    }
  }

  private void setAnnotationFields(Update update, PipelineAnnotationEntity annotationEntity) {
    if (annotationEntity.getPriority() != null) {
      update.set(PipelineAnnotationEntityKeys.priority, annotationEntity.getPriority());
    }
    if (annotationEntity.getStyle() != null && !annotationEntity.getStyle().isEmpty()) {
      update.set(PipelineAnnotationEntityKeys.style, annotationEntity.getStyle());
    }
    if (annotationEntity.getStepId() != null) {
      update.set(PipelineAnnotationEntityKeys.stepId, annotationEntity.getStepId());
    }
  }

  private void setInsertOnlyFields(Update update, PipelineAnnotationEntity annotationEntity) {
    update.setOnInsert(PipelineAnnotationEntityKeys.contextId, annotationEntity.getContextId());
    update.setOnInsert(PipelineAnnotationEntityKeys.accountId, annotationEntity.getAccountId());
    update.setOnInsert(PipelineAnnotationEntityKeys.planExecutionId, annotationEntity.getPlanExecutionId());
  }

  @Override
  public boolean deleteAnnotation(String planExecutionId, String contextId) {
    // Delete document by composite key
    Query query = new Query(where(PipelineAnnotationEntityKeys.planExecutionId)
                                .is(planExecutionId)
                                .and(PipelineAnnotationEntityKeys.contextId)
                                .is(contextId));

    long deletedCount = mongoTemplate.remove(query, PipelineAnnotationEntity.class).getDeletedCount();

    log.info("Deleted annotation for planExecutionId: {}, context: {}, deletedCount: {}", planExecutionId, contextId,
        deletedCount);

    return deletedCount > 0;
  }

  @Override
  public List<PipelineAnnotationEntity> findAllByPlanExecutionId(String planExecutionId) {
    // Find ALL documents with this planExecutionId (one per context)
    Query query = new Query(where(PipelineAnnotationEntityKeys.planExecutionId).is(planExecutionId));

    return mongoTemplate.find(query, PipelineAnnotationEntity.class);
  }

  @Override
  public PipelineAnnotationEntity findByPlanExecutionIdAndContextId(String planExecutionId, String contextId) {
    Query query = new Query(where(PipelineAnnotationEntityKeys.planExecutionId)
                                .is(planExecutionId)
                                .and(PipelineAnnotationEntityKeys.contextId)
                                .is(contextId));

    return mongoTemplate.findOne(query, PipelineAnnotationEntity.class);
  }
}
