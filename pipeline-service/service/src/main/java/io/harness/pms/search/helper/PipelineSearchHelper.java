/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.search.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.elasticsearch.framework.OperatorEnum.CONSTANT_SCORE;
import static io.harness.elasticsearch.framework.OperatorEnum.CONTAINS_CASE_INSENSITIVE;
import static io.harness.elasticsearch.framework.OperatorEnum.EQUALS;
import static io.harness.elasticsearch.framework.OperatorEnum.EQUALS_ANY;
import static io.harness.elasticsearch.framework.OperatorEnum.EXISTS;
import static io.harness.elasticsearch.framework.OperatorEnum.GREATER_THAN_EQUALS;
import static io.harness.elasticsearch.framework.OperatorEnum.LESS_THAN_EQUALS;
import static io.harness.elasticsearch.framework.OperatorEnum.MUST_MATCH_ALL;
import static io.harness.elasticsearch.framework.OperatorEnum.MUST_NOT_MATCH_ALL;
import static io.harness.elasticsearch.framework.OperatorEnum.NESTED;
import static io.harness.elasticsearch.framework.OperatorEnum.RANGE_EXCLUDING_ENDS;
import static io.harness.elasticsearch.framework.OperatorEnum.RANGE_INCLUDING_ENDS;
import static io.harness.elasticsearch.framework.OperatorEnum.SHOULD_MATCH_AT_LEAST_ONE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.elasticsearch.ElasticSearchFilterContext;
import io.harness.elasticsearch.framework.OperatorEnum;
import io.harness.elasticsearch.utils.ElasticSearchQueryBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.common.beans.FilterWithOperator;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.pms.plan.execution.ModuleInfoOperators;
import io.harness.pms.plan.execution.beans.dto.CDModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.CDModulePropertiesDTO.CDModulePropertiesDTOKeys;
import io.harness.pms.plan.execution.beans.dto.CIExecutionInfoDTO;
import io.harness.pms.plan.execution.beans.dto.CIModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.CIModulePropertiesDTO.CIModulePropertiesDTOKeys;
import io.harness.pms.plan.execution.beans.dto.CIPullRequestDTO;
import io.harness.pms.plan.execution.beans.dto.ModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.ModulePropertiesDTO.ModulePropertiesDTOKeys;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;
import io.harness.search.utils.PipelineSearchUtils;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@UtilityClass
@OwnedBy(PIPELINE)
public class PipelineSearchHelper {
  private static final String EMAIL = "email";
  private static final String COMMON_MODULE_FOR_CUSTOM_STAGE = "common";

  public static Query getPipelineTagsQuery(List<NGTag> pipelineTags) {
    List<String> tags = new ArrayList<>();
    List<Query> tagQueries = new ArrayList<>();

    GlobalContextManager.upsertGlobalContextRecord(
        ElasticSearchFilterContext.builder().property("pipeline_tags").operator("OR").build());

    pipelineTags.forEach(o -> {
      if (o.getKey() == null) {
        throw new InvalidRequestException("Key in Pipeline Tags filter cannot be null");
      } else if (o.getValue() == null) {
        tags.add(o.getKey());
      } else {
        Query keyQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
            EQUALS, PipelineSearchExecutionSummaryDTOKeys.tagsKey, o.getKey());
        Query valueQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
            EQUALS, PipelineSearchExecutionSummaryDTOKeys.tagsValue, o.getValue());
        tagQueries.add(
            ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, Arrays.asList(keyQuery, valueQuery)));
      }
    });
    if (!tags.isEmpty()) {
      tagQueries.add(ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
          EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.tagsKey, tags));
      tagQueries.add(ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
          EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.tagsValue, tags));
    }
    Query combinedTagsQuery = ElasticSearchQueryBuilder.buildCombinedQuery(SHOULD_MATCH_AT_LEAST_ONE, tagQueries);
    return ElasticSearchQueryBuilder.buildNestedQuery(
        NESTED, PipelineSearchExecutionSummaryDTOKeys.tags, combinedTagsQuery);
  }

  public static Query getPipelineTagsQueryV2(FilterWithOperator<NGTag> pipelineTagsV2) {
    List<Query> nestedTagQueries = new ArrayList<>();
    List<NGTag> tags = pipelineTagsV2.getItemsList();

    String operator = pipelineTagsV2.getOperator() == FilterWithOperator.FilterOperator.AND ? "AND" : "OR";
    GlobalContextManager.upsertGlobalContextRecord(
        ElasticSearchFilterContext.builder().property("pipeline_tags_v2").operator(operator).build());

    tags.forEach(tag -> {
      if (tag.getKey() == null) {
        throw new InvalidRequestException("Key in Pipeline Tags filter cannot be null");
      }

      Query keyQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.tagsKey, tag.getKey());

      Query innerQuery;
      if (tag.getValue() == null) {
        // Key-only tag: match either key or value within the same nested object
        Query valueQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
            EQUALS, PipelineSearchExecutionSummaryDTOKeys.tagsValue, tag.getKey());
        innerQuery = ElasticSearchQueryBuilder.buildCombinedQuery(
            SHOULD_MATCH_AT_LEAST_ONE, Arrays.asList(keyQuery, valueQuery));
      } else {
        // Key-value pair: must match both key and value within the same nested object
        Query valueQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
            EQUALS, PipelineSearchExecutionSummaryDTOKeys.tagsValue, tag.getValue());
        innerQuery = ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, Arrays.asList(keyQuery, valueQuery));
      }

      // Wrap each tag's query in its own nested query to preserve key-value pairing
      Query nestedQuery =
          ElasticSearchQueryBuilder.buildNestedQuery(NESTED, PipelineSearchExecutionSummaryDTOKeys.tags, innerQuery);
      nestedTagQueries.add(nestedQuery);
    });

    OperatorEnum operatorEnum = pipelineTagsV2.getOperator() == FilterWithOperator.FilterOperator.AND
        ? MUST_MATCH_ALL
        : SHOULD_MATCH_AT_LEAST_ONE;
    return ElasticSearchQueryBuilder.buildCombinedQuery(operatorEnum, nestedTagQueries);
  }

  public static Query getModuleNameQuery(String moduleName) {
    List<Query> moduleNameQuery = new ArrayList<>();
    // Pipelines having only pipeline stages like custom and approval
    moduleNameQuery.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.modules, COMMON_MODULE_FOR_CUSTOM_STAGE));
    moduleNameQuery.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.modules, moduleName));
    return ElasticSearchQueryBuilder.buildCombinedQuery(SHOULD_MATCH_AT_LEAST_ONE, moduleNameQuery);
  }

  public static Query getExecutionModeQuery() {
    List<String> executionModes = Arrays.asList(ExecutionMode.POST_EXECUTION_ROLLBACK.toString(),
        ExecutionMode.NORMAL.toString(), ExecutionMode.UNDEFINED_MODE.toString());
    return ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
        EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.executionMode, executionModes);
  }

  public static Query getTriggeredByQuery(String triggerType, TriggeredBy triggeredBy) {
    Query triggerTypeQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.triggerType, triggerType);
    String email = triggeredBy.getExtraInfoMap().get(EMAIL);
    Query emailQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.triggeredByEmail, email);
    return ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, Arrays.asList(triggerTypeQuery, emailQuery));
  }

  public static Query getPipelineNameQuery(String pipelineName) {
    List<Query> pipelineNameQuery = new ArrayList<>();
    pipelineNameQuery.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        CONTAINS_CASE_INSENSITIVE, PipelineSearchExecutionSummaryDTOKeys.pipelineIdentifier, pipelineName));
    pipelineNameQuery.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        CONTAINS_CASE_INSENSITIVE, PipelineSearchExecutionSummaryDTOKeys.name, pipelineName));
    return ElasticSearchQueryBuilder.buildCombinedQuery(SHOULD_MATCH_AT_LEAST_ONE, pipelineNameQuery);
  }

  public static Query getStatusQuery(List<ExecutionStatus> status) {
    List<String> statusList = status.stream().map(Enum::toString).toList();
    return ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
        EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.status, statusList);
  }

  public static Query getSearchTermQuery(String searchTerm) {
    List<Query> searchTermQuery = new ArrayList<>();
    searchTermQuery.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        CONTAINS_CASE_INSENSITIVE, PipelineSearchExecutionSummaryDTOKeys.pipelineIdentifier, searchTerm));
    searchTermQuery.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        CONTAINS_CASE_INSENSITIVE, PipelineSearchExecutionSummaryDTOKeys.name, searchTerm));

    Query tagKeyQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        CONTAINS_CASE_INSENSITIVE, PipelineSearchExecutionSummaryDTOKeys.tagsKey, searchTerm);
    Query tagValueQuery = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        CONTAINS_CASE_INSENSITIVE, PipelineSearchExecutionSummaryDTOKeys.tagsValue, searchTerm);
    Query combinedTagsQuery = ElasticSearchQueryBuilder.buildCombinedQuery(
        SHOULD_MATCH_AT_LEAST_ONE, Arrays.asList(tagKeyQuery, tagValueQuery));
    searchTermQuery.add(ElasticSearchQueryBuilder.buildNestedQuery(
        NESTED, PipelineSearchExecutionSummaryDTOKeys.tags, combinedTagsQuery));

    return ElasticSearchQueryBuilder.buildCombinedQuery(SHOULD_MATCH_AT_LEAST_ONE, searchTermQuery);
  }

  public static List<Query> getScopeQuery(String accountId, String parentUniqueId) {
    List<Query> matchQueries = new ArrayList<>();
    if (isNotEmpty(accountId)) {
      matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.accountId, accountId));
    }
    if (isNotEmpty(parentUniqueId)) {
      matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.parentUniqueId, parentUniqueId));
    }
    // This isDeleted field is for the pms-running-executions index, which is true if the execution is complete
    // On completion the execution is stored in the index alias pms-execution-alias-6-month
    matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.isDeleted, false));
    return matchQueries;
  }

  public static List<Query> getScopeQuery(String accountId, String orgId, String projectId) {
    List<Query> matchQueries = new ArrayList<>();
    if (isNotEmpty(accountId)) {
      matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.accountId, accountId));
    }
    if (isNotEmpty(orgId)) {
      matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.orgIdentifier, orgId));
    }
    if (isNotEmpty(projectId)) {
      matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.projectIdentifier, projectId));
    }

    // This isDeleted field is for the pms-running-executions index, which is true if the execution is complete
    // On completion the execution is stored in the index alias pms-execution-alias-6-month
    matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.isDeleted, false));
    return matchQueries;
  }

  public static List<Query> getModulePropertiesQuery(
      ModulePropertiesDTO modulePropertiesDTO, String parentPath, ModuleInfoOperators operatorOnModules) {
    if (operatorOnModules.name().equals(ModuleInfoOperators.Operators.OR)) {
      return getModulePropertiesQueryOrOperator(modulePropertiesDTO, parentPath);
    }
    return getModulePropertiesQueryAndOperator(modulePropertiesDTO, parentPath);
  }

  private static List<Query> getModulePropertiesQueryOrOperator(
      ModulePropertiesDTO modulePropertiesDTO, String parentPath) {
    List<Query> modulePropertiesQuery = new ArrayList<>();
    if (modulePropertiesDTO == null) {
      return modulePropertiesQuery;
    }
    if (modulePropertiesDTO.getCd() != null) {
      List<Query> cdModulePropertiesQuery = new ArrayList<>();
      processCDModuleInfoFilter(modulePropertiesDTO.getCd(), parentPath, cdModulePropertiesQuery);
      if (isNotEmpty(cdModulePropertiesQuery)) {
        modulePropertiesQuery.add(
            ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, cdModulePropertiesQuery));
      }
    }
    if (modulePropertiesDTO.getCi() != null) {
      List<Query> ciModulePropertiesQuery = new ArrayList<>();
      processCIModuleInfoFilter(modulePropertiesDTO.getCi(), parentPath, ciModulePropertiesQuery);
      if (isNotEmpty(ciModulePropertiesQuery)) {
        modulePropertiesQuery.add(
            ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, ciModulePropertiesQuery));
      }
    }
    return modulePropertiesQuery;
  }

  private static List<Query> getModulePropertiesQueryAndOperator(
      ModulePropertiesDTO modulePropertiesDTO, String parentPath) {
    List<Query> modulePropertiesQuery = new ArrayList<>();
    if (modulePropertiesDTO == null) {
      return modulePropertiesQuery;
    }
    if (modulePropertiesDTO.getCd() != null) {
      processCDModuleInfoFilter(modulePropertiesDTO.getCd(), parentPath, modulePropertiesQuery);
    }
    if (modulePropertiesDTO.getCi() != null) {
      processCIModuleInfoFilter(modulePropertiesDTO.getCi(), parentPath, modulePropertiesQuery);
    }
    return modulePropertiesQuery;
  }

  private void processCDModuleInfoFilter(
      CDModulePropertiesDTO moduleProperties, String parentPath, List<Query> queries) {
    if (moduleProperties == null) {
      return;
    }
    String modulePath = String.format("%s.%s", parentPath, ModulePropertiesDTOKeys.cd);
    addQueryForCDModuleProperties(moduleProperties.getEnvIdentifiers(),
        buildFieldPath(modulePath, CDModulePropertiesDTOKeys.envIdentifiers), queries);
    addQueryForCDModuleProperties(moduleProperties.getArtifactDisplayNames(),
        buildFieldPath(modulePath, CDModulePropertiesDTOKeys.artifactDisplayNames), queries);
    addQueryForCDModuleProperties(moduleProperties.getServiceIdentifiers(),
        buildFieldPath(modulePath, CDModulePropertiesDTOKeys.serviceIdentifiers), queries);
    addQueryForCDModuleProperties(moduleProperties.getServiceDefinitionTypes(),
        buildFieldPath(modulePath, CDModulePropertiesDTOKeys.serviceDefinitionTypes), queries);
    addQueryForCDModuleProperties(moduleProperties.getHelmChartVersions(),
        buildFieldPath(modulePath, CDModulePropertiesDTOKeys.helmChartVersions), queries);
    addQueryForCDModuleProperties(moduleProperties.getGitOpsAppIdentifiers(),
        buildFieldPath(modulePath, CDModulePropertiesDTOKeys.gitOpsAppIdentifiers), queries);
  }

  private String buildFieldPath(String modulePath, String fieldName) {
    return PipelineSearchUtils.getModuleInfoFieldPath(String.format("%s.%s", modulePath, fieldName));
  }

  private void processCIModuleInfoFilter(
      CIModulePropertiesDTO moduleProperties, String parentPath, List<Query> queries) {
    if (moduleProperties == null) {
      return;
    }
    String modulePath = String.format("%s.%s", parentPath, ModulePropertiesDTOKeys.ci);
    if (moduleProperties.getBranch() != null) {
      queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, buildFieldPath(modulePath, CIModulePropertiesDTOKeys.branch), moduleProperties.getBranch()));
    }
    if (moduleProperties.getBuildType() != null) {
      queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, buildFieldPath(modulePath, CIModulePropertiesDTOKeys.buildType), moduleProperties.getBuildType()));
    }
    if (moduleProperties.getTag() != null) {
      queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, buildFieldPath(modulePath, CIModulePropertiesDTOKeys.tag), moduleProperties.getTag()));
    }
    if (moduleProperties.getRepoName() != null) {
      queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, buildFieldPath(modulePath, CIModulePropertiesDTOKeys.repoName), moduleProperties.getRepoName()));
    }
    if (moduleProperties.getCiExecutionInfoDTO() != null) {
      CIExecutionInfoDTO ciExecutionInfoDTO = moduleProperties.getCiExecutionInfoDTO();
      if (ciExecutionInfoDTO.getEvent() != null) {
        queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
            EQUALS, buildFieldPath(modulePath, CIModulePropertiesDTOKeys.event), ciExecutionInfoDTO.getEvent()));
      }
      if (ciExecutionInfoDTO.getPullRequest() != null) {
        CIPullRequestDTO ciPullRequestDTO = ciExecutionInfoDTO.getPullRequest();
        if (ciPullRequestDTO.getSourceBranch() != null) {
          queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(EQUALS,
              buildFieldPath(modulePath, CIModulePropertiesDTOKeys.sourceBranch), ciPullRequestDTO.getSourceBranch()));
        }
        if (ciPullRequestDTO.getTargetBranch() != null) {
          queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(EQUALS,
              buildFieldPath(modulePath, CIModulePropertiesDTOKeys.targetBranch), ciPullRequestDTO.getTargetBranch()));
        }
      }
    }
  }

  private void addQueryForCDModuleProperties(List<String> value, String path, List<Query> queries) {
    if (value == null) {
      return;
    }
    List<String> nonNullValues = value.stream().filter(Objects::nonNull).toList();
    Query matchQuery = ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(EQUALS_ANY, path, nonNullValues);
    boolean isNullValuePresent = value.stream().anyMatch(Objects::isNull);
    if (isNullValuePresent) {
      Query existsQuery = ElasticSearchQueryBuilder.buildFieldQuery(EXISTS, path);
      Query notExistsQuery = ElasticSearchQueryBuilder.buildCombinedQuery(MUST_NOT_MATCH_ALL, List.of(existsQuery));
      queries.add(ElasticSearchQueryBuilder.buildCombinedQuery(
          SHOULD_MATCH_AT_LEAST_ONE, Arrays.asList(matchQuery, notExistsQuery)));
    } else {
      queries.add(matchQuery);
    }
  }

  public static List<Query> formFilterQueryForExecutionOutlines(List<ExecutionStatus> statusList,
      String pipelineIdentifier, List<String> planExecutionIds, boolean isQueueBasedPlanCreationFFEnabled) {
    List<Query> matchQueries = new ArrayList<>();
    if (isNotEmpty(statusList)) {
      if (isQueueBasedPlanCreationFFEnabled) {
        statusList = ExecutionStatus.getCompleteListWithInternalStatuses(statusList);
      }
      matchQueries.add(PipelineSearchHelper.getStatusQuery(statusList));
    }

    if (isNotEmpty(pipelineIdentifier)) {
      matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.pipelineIdentifier, pipelineIdentifier));
    }

    if (isNotEmpty(planExecutionIds)) {
      matchQueries.add(ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
          EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.planExecutionId, planExecutionIds));
    }
    return matchQueries;
  }

  public static Query buildTimeRangeQuery(TimeRange timeRange) {
    Long startTime = timeRange.getStartTime();
    Long endTime = timeRange.getEndTime();
    if (startTime != null && endTime != null) {
      return ElasticSearchQueryBuilder.buildRangeQuery(
          RANGE_INCLUDING_ENDS, PipelineSearchExecutionSummaryDTOKeys.startTs, startTime, endTime);
    } else if (startTime != null) {
      return ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          GREATER_THAN_EQUALS, PipelineSearchExecutionSummaryDTOKeys.startTs, startTime);
    } else if (endTime != null) {
      return ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          LESS_THAN_EQUALS, PipelineSearchExecutionSummaryDTOKeys.startTs, endTime);
    }
    return null;
  }

  public Query formQueryWithScopeAndPipelineIdentifier(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String parentUniqueId) {
    List<Query> matchQueries = parentUniqueId != null
        ? PipelineSearchHelper.getScopeQuery(accountIdentifier, parentUniqueId)
        : PipelineSearchHelper.getScopeQuery(accountIdentifier, orgIdentifier, projectIdentifier);
    if (EmptyPredicate.isNotEmpty(pipelineIdentifier)) {
      matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.pipelineIdentifier, pipelineIdentifier));
    }
    return ElasticSearchQueryBuilder.buildNestedQuery(
        CONSTANT_SCORE, null, ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, matchQueries));
  }

  public Query formQueryWithScopeAndPipelineIdentifierAndCreatedAt(
      String accountIdentifier, ScopeInfo scopeInfo, String pipelineIdentifier, Long startTs, Long endTs) {
    List<Query> matchQueries = PipelineSearchHelper.getScopeQuery(accountIdentifier, scopeInfo.getUniqueId());
    if (EmptyPredicate.isNotEmpty(pipelineIdentifier)) {
      matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.pipelineIdentifier, pipelineIdentifier));
    }
    matchQueries.add(ElasticSearchQueryBuilder.buildRangeQuery(
        RANGE_EXCLUDING_ENDS, PipelineSearchExecutionSummaryDTOKeys.createdAt, startTs, endTs));
    return ElasticSearchQueryBuilder.buildNestedQuery(
        CONSTANT_SCORE, null, ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, matchQueries));
  }

  /**
   * Builds the base ElasticSearch query for fetching queued pipeline executions for an account.
   * Filters by accountId, queued statuses, and non-deleted entries.
   */
  public static Query buildQueuedExecutionsQuery(String accountId, List<ExecutionStatus> queuedStatuses) {
    List<Query> matchQueries = new ArrayList<>();
    matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.accountId, accountId));
    matchQueries.add(getStatusQuery(queuedStatuses));
    matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.isDeleted, false));
    return ElasticSearchQueryBuilder.buildNestedQuery(
        CONSTANT_SCORE, null, ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, matchQueries));
  }

  public static Query getNotesQuery(List<String> notesTerms) {
    List<Query> queries = notesTerms.stream()
                              .filter(term -> term != null && !term.isBlank())
                              .map(term
                                  -> ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
                                      CONTAINS_CASE_INSENSITIVE, PipelineSearchExecutionSummaryDTOKeys.notes, term))
                              .collect(Collectors.toList());

    if (queries.isEmpty()) {
      return null;
    }

    return ElasticSearchQueryBuilder.buildCombinedQuery(SHOULD_MATCH_AT_LEAST_ONE, queries);
  }
}
