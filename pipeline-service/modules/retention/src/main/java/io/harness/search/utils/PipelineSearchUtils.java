/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PIPELINE_SEARCH_EXECUTION_SUMMARY_DTO_ALL_FIELDS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PipelineSearchUtils {
  private static final String CD_MODULE_INFO = "moduleInfo.cd";
  private static final String CI_MODULE_INFO = "moduleInfo.ci";

  /*
   * Currently, UI sends the sort field name according to the PlanExecutionSummaryKeys
   * But in Elasticsearch we might have a different name for the same field, or even we might not want to support it.
   * Although currently we are supporting all the 3 fields which have the same name, but it can be a valid case in
   * future So this function helps in mapping the fields for sort property
   */
  public String getSearchSortFieldMapping(String field) {
    if (field == null) {
      throw new InvalidRequestException("The provided field for sorting results in Elastic cannot be null");
    }
    return switch (field) {
      case PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.name -> PipelineSearchExecutionSummaryDTOKeys.name;
      case PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status ->
              PipelineSearchExecutionSummaryDTOKeys.status;
      case PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.startTs ->
              PipelineSearchExecutionSummaryDTOKeys.startTs;
      case PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.pipelineTimeoutTs ->
              PipelineSearchExecutionSummaryDTOKeys.pipelineTimeoutTs;
      default ->
              throw new InvalidRequestException(String.format("The provided field is not supported for sorting in Elastic: %s", field));
    };
  }

  /*
   * Currently, UI sends the moduleInfo filters starting with path moduleInfo.<moduleName>
   * But in Elasticsearch we have a different path for storing moduleInfo i.e. cdModuleInfo/ciModuleInfo
   * So this function helps in mapping the fields for filtering on module info properties
   */
  public String getModuleInfoFieldPath(String path) {
    if (path == null) {
      throw new InvalidRequestException("The provided path to get Module Info field in Elastic cannot be null");
    }
    if (path.contains(CD_MODULE_INFO)) {
      return path.replace(CD_MODULE_INFO, PipelineSearchExecutionSummaryDTOKeys.cdModuleInfo);
    } else if (path.contains(CI_MODULE_INFO)) {
      return path.replace(CI_MODULE_INFO, PipelineSearchExecutionSummaryDTOKeys.ciModuleInfo);
    }
    return path;
  }

  public List<SortOptions> getSortOptions(Map<String, SortOrder> sortingFields) {
    if(isEmpty(sortingFields)) {
      return null;
    }
    // validate if sortingFields do exists in PipelineSearchExecutionSummaryDTO
    PipelineSearchUtils.validateSortingFields(sortingFields);
    List<SortOptions> sortOptionsList = new ArrayList<>();
      for (Map.Entry<String, SortOrder> entry : sortingFields.entrySet()) {
          sortOptionsList.add(
              new SortOptions.Builder().field(f -> f.field(entry.getKey()).order(entry.getValue())).build());
        }
        return sortOptionsList;
    }

    public boolean checkFieldExistsInElastic(String field) {
      return PIPELINE_SEARCH_EXECUTION_SUMMARY_DTO_ALL_FIELDS.contains(field);
    }

    public void validateSortingFields(Map<String, SortOrder> sortingFields) {
      for (String field : sortingFields.keySet()) {
        if (!checkFieldExistsInElastic(field)) {
          throw new InvalidRequestException(String.format(
              "[ELASTIC_SEARCH] Provided sorting field: %s is invalid. It doesn't exist in elastic DB", field));
        }
      }
    }

    public void validateFieldsToInclude(Set<String> fieldsToInclude) {
      for (String field : fieldsToInclude) {
        if (!checkFieldExistsInElastic(field)) {
          throw new InvalidRequestException(String.format(
              "[ELASTIC_SEARCH] Provided projection field: %s is invalid. It doesn't exist in DB", field));
        }
      }
    }

    public void validateSearchBatchSize(int size) {
      if (PipelineExecutionElasticSearchConstants.PIPELINE_SEARCH_MAX_BATCH_SIZE < size) {
        throw new InvalidRequestException(
            String.format("[ELASTIC_SEARCH] Provided batch size is %s over the max threshold limit %s", size,
                PipelineExecutionElasticSearchConstants.PIPELINE_SEARCH_MAX_BATCH_SIZE));
      }
    }

    public void validateQueryParamsForRootExecutionId(String accountIdentifier, String rootExecutionId) {
      if (isEmpty(accountIdentifier) || isEmpty(rootExecutionId)) {
        throw new InvalidRequestException("[ELASTIC_SEARCH] Required fields accountIdentifier and rootExecutionId "
            + "either both or one of them is empty or null");
      }
    }

    private void validatePlanExecutionIdsAndSummaryEntities(
        List<String> planExecutionIds, List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities) {
      if (isEmpty(planExecutionIds) && isEmpty(pipelineExecutionSummaryEntities)) {
        return;
      }
      if (isEmpty(planExecutionIds) || isEmpty(pipelineExecutionSummaryEntities)) {
        throw new InvalidRequestException(
            "[ELASTIC_SEARCH] Provided planExecutionIds or pipelineExecutionSummaryEntities can not be empty");
      }
      if (planExecutionIds.size() != pipelineExecutionSummaryEntities.size()) {
        throw new InvalidRequestException("[ELASTIC_SEARCH] Provided planExecutionIds and "
            + "pipelineExecutionSummaryEntities can not have different size");
      }
    }

    public List<PipelineExecutionSummaryEntity> getSummaryEntitiesOrderedByExecutionIds(
        List<String> planExecutionIds, List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities) {
      // validating if one of the ids or entities are empty or different in size
      validatePlanExecutionIdsAndSummaryEntities(planExecutionIds, pipelineExecutionSummaryEntities);

      Map<String, PipelineExecutionSummaryEntity> executionIdToSummaryEntityMap =
          pipelineExecutionSummaryEntities.stream().collect(
              Collectors.toMap(PipelineExecutionSummaryEntity::getPlanExecutionId, entity -> entity, (a, b) -> a));
      List<String> uniquePlanExecutionIds = new ArrayList<>(new LinkedHashSet<>(planExecutionIds));
      return uniquePlanExecutionIds.stream().map(executionIdToSummaryEntityMap::get).collect(Collectors.toList());
    }

    public List<FieldValue> getFieldValueList(List<Object> values) {
      if (isEmpty(values)) {
        return Collections.emptyList();
      }
      List<FieldValue> fieldValueList = new ArrayList<>();
      for (Object val : values) {
        if (val instanceof Long) {
          fieldValueList.add(FieldValue.of((Long) val));
        } else if (val instanceof String) {
          fieldValueList.add(FieldValue.of((String) val));
        } else if (val instanceof Double) {
          fieldValueList.add(FieldValue.of((Double) val));
        } else if (val instanceof Boolean) {
          fieldValueList.add(FieldValue.of((Boolean) val));
        } else {
          throw new InvalidRequestException(
              String.format("[ELASTIC_SEARCH]: provided object %s has a type which is not supported", val));
        }
      }
      return fieldValueList;
    }

    public PutMappingRequest buildPutMappingRequestForKeywordType(String indexName, String fieldName) {
      return new PutMappingRequest.Builder()
          .index(indexName)
          .properties(fieldName, prop -> prop.keyword(keyword -> keyword))
          .build();
    }

    public PutMappingRequest buildPutMappingRequestForDateType(String indexName, String fieldName) {
      return new PutMappingRequest.Builder()
          .index(indexName)
          .properties(fieldName, prop -> prop.date(date -> date))
          .build();
    }
  }
