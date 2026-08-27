/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.expressions.functors.ExpressionFunctorMetricsHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.expression.LateBindingValue;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class InputSetFunctor implements LateBindingValue {
  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final PMSExecutionService pmsExecutionService;
  private final PMSInputSetService pmsInputSetService;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final MetricService metricService;
  private final Ambiance ambiance;

  public InputSetFunctor(PlanExecutionMetadataService planExecutionMetadataService,
      PMSExecutionService pmsExecutionService, PMSInputSetService pmsInputSetService,
      ScopeResolutionHelper scopeResolutionHelper, Ambiance ambiance, MetricService metricService) {
    this.planExecutionMetadataService = planExecutionMetadataService;
    this.pmsExecutionService = pmsExecutionService;
    this.pmsInputSetService = pmsInputSetService;
    this.scopeResolutionHelper = scopeResolutionHelper;
    this.ambiance = ambiance;
    this.metricService = metricService;
  }

  @Override
  public Object bind() {
    Instant start = Instant.now();
    String result = ExpressionFunctorMetricsHelper.RESULT_HIT;
    try {
      Map<String, Object> resultMap = new HashMap<>(
          YamlUtils.read(planExecutionMetadataService
                             .getWithFieldsIncludedFromSecondary(AmbianceUtils.getAccountId(ambiance),
                                 ambiance.getPlanExecutionId(), Sets.newHashSet(PlanExecutionMetadataKeys.inputSetYaml))
                             .getInputSetYaml(),
              HashMap.class));
      resultMap.put("details", fetchInputSetDetails());
      return resultMap;
    } catch (IOException e) {
      result = ExpressionFunctorMetricsHelper.RESULT_ERROR;
      throw new InvalidRequestException("Input Set Yaml could not be converted to a hashmap");
    } catch (Exception e) {
      result = ExpressionFunctorMetricsHelper.RESULT_ERROR;
      throw e;
    } finally {
      ExpressionFunctorMetricsHelper.recordMetrics(
          metricService, ExpressionFunctorMetricsHelper.FUNCTOR_INPUT_SET, result, start);
    }
  }

  private List<Map<String, Object>> fetchInputSetDetails() {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    PipelineExecutionSummaryEntity executionSummary =
        pmsExecutionService.fetchExecutionSummaryFromDb(ambiance.getPlanExecutionId(),
            ImmutableSet.of(PlanExecutionSummaryKeys.inputSetIdentifiers, PlanExecutionSummaryKeys.pipelineIdentifier));

    List<String> inputSetIds = executionSummary.getInputSetIdentifiers();
    if (isEmpty(inputSetIds)) {
      return Collections.emptyList();
    }

    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId);
    BulkInputSetsResponseDTO response =
        pmsInputSetService.getBulkInputSets(scopeInfo, executionSummary.getPipelineIdentifier(),
            BulkInputSetsRequestDTO.builder().inputSetIdentifiers(inputSetIds).build());

    List<InputSetSummaryResponseDTOPMS> inputSets =
        Optional.ofNullable(response).map(BulkInputSetsResponseDTO::getInputSets).orElse(Collections.emptyList());

    return inputSetIds.stream()
        .map(id -> toDetailMap(id, findById(inputSets, id), orgId, projectId))
        .collect(Collectors.toList());
  }

  private InputSetSummaryResponseDTOPMS findById(List<InputSetSummaryResponseDTOPMS> inputSets, String id) {
    return inputSets.stream().filter(is -> id.equals(is.getIdentifier())).findFirst().orElse(null);
  }

  private Map<String, Object> toDetailMap(
      String id, InputSetSummaryResponseDTOPMS inputSet, String orgId, String projectId) {
    Map<String, Object> entry = new HashMap<>();
    entry.put("identifier", id);
    if (inputSet == null) {
      return entry;
    }
    entry.put("name", inputSet.getName());
    entry.put("description", inputSet.getDescription());
    entry.put("orgIdentifier", orgId);
    entry.put("projectIdentifier", projectId);
    entry.put("pipelineIdentifier", inputSet.getPipelineIdentifier());
    entry.put(
        "inputSetType", Optional.ofNullable(inputSet.getInputSetType()).map(InputSetEntityType::name).orElse(null));
    entry.put("tags", inputSet.getTags());
    return entry;
  }
}
