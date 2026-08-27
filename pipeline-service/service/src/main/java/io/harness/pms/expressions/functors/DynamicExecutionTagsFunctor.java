/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class DynamicExecutionTagsFunctor implements ExpressionFunctor {
  private final String planExecutionId;
  private final PmsExecutionSummaryService pmsExecutionSummaryService;
  private final PMSExecutionService pmsExecutionService;
  private final int MAX_CHAR_LIMIT = 1000;
  private final int MAX_TAGS_LIMIT = 1000;
  private final String ERROR_MSG =
      "Cannot add the tag to the execution, as the provided %s is longer than %d characters";
  private final String KEY_EMPTY_ERROR_MSG =
      "Cannot add the tag to the execution, as resolved tag key is null/empty string";
  private final String KEYS_EMPTY_ERROR_MSG = "Cannot add tags to the execution, one or more tags have null/empty key";

  public Object addTag(String key) {
    return insertTags(key, StringUtils.EMPTY);
  }

  public Object addTag(String key, String value) {
    return insertTags(key, value);
  }

  public Object addTagsList(String tagsJson) {
    return insertTagsList(tagsJson);
  }

  private String insertTags(String key, String value) {
    NGTag tag = validateAndReturnTags(key, value, KEY_EMPTY_ERROR_MSG);
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.fetchExecutionSummaryFromDb(planExecutionId, Set.of(PlanExecutionSummaryKeys.tags));
    if (pipelineExecutionSummaryEntity.getTags() != null
        && pipelineExecutionSummaryEntity.getTags().size() >= MAX_TAGS_LIMIT) {
      throw new EngineFunctorException(
          String.format("Cannot add the tag %s to the execution, maximum limit of 1000 tags reached", tag.getKey()));
    }
    Update update = new Update();
    update.addToSet(PlanExecutionSummaryKeys.tags, tag);
    pmsExecutionSummaryService.update(planExecutionId, update);
    return String.format("%s:%s", tag.getKey(), tag.getValue());
  }

  private String insertTagsList(String tagsJson) {
    if (isEmpty(tagsJson)) {
      throw new EngineFunctorException("Cannot add tags to the execution, as resolved tags JSON is null/empty string");
    }

    List<NGTag> tagsList;
    try {
      tagsList = JsonUtils.asList(tagsJson, new TypeReference<List<NGTag>>() {});
    } catch (Exception e) {
      throw new EngineFunctorException(
          "Cannot parse tags JSON. Expected format: [{\"key\":\"tag1\",\"value\":\"val1\"}]");
    }

    if (isEmpty(tagsList)) {
      throw new EngineFunctorException("Cannot add tags to the execution, as the tags list is empty");
    }

    // Validate and prepare tags
    List<NGTag> validatedTags = new ArrayList<>();
    for (NGTag tag : tagsList) {
      validatedTags.add(validateAndReturnTags(tag.getKey(), tag.getValue(), KEYS_EMPTY_ERROR_MSG));
    }

    // Check current tag count
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.fetchExecutionSummaryFromDb(planExecutionId, Set.of(PlanExecutionSummaryKeys.tags));
    int currentTagCount =
        pipelineExecutionSummaryEntity.getTags() != null ? pipelineExecutionSummaryEntity.getTags().size() : 0;

    if (currentTagCount + validatedTags.size() > MAX_TAGS_LIMIT) {
      throw new EngineFunctorException(
          String.format("Cannot add %d tags to the execution. Current count: %d, Maximum limit: %d",
              validatedTags.size(), currentTagCount, MAX_TAGS_LIMIT));
    }

    // Add all tags in a single update using addToSet().each() for efficiency
    Update update = new Update();
    update.addToSet(PlanExecutionSummaryKeys.tags).each(validatedTags);
    pmsExecutionSummaryService.update(planExecutionId, update);

    // Return formatted string of all added tags
    return validatedTags.stream()
        .map(tag -> String.format("%s:%s", tag.getKey(), tag.getValue()))
        .collect(Collectors.joining(", "));
  }

  private NGTag validateAndReturnTags(String key, String value, String errorMsg) {
    if (isEmpty(key)) {
      throw new EngineFunctorException(errorMsg);
    }
    if (isNull(value)) {
      value = StringUtils.EMPTY;
    }
    // Trim leading and trailing spaces
    key = key.trim();
    value = value.trim();

    if (key.length() > MAX_CHAR_LIMIT) {
      throw new EngineFunctorException(String.format(ERROR_MSG, "key", MAX_CHAR_LIMIT));
    }
    if (value.length() > MAX_CHAR_LIMIT) {
      throw new EngineFunctorException(String.format(ERROR_MSG, "value", MAX_CHAR_LIMIT));
    }
    return NGTag.builder().key(key).value(value).build();
  }
}
