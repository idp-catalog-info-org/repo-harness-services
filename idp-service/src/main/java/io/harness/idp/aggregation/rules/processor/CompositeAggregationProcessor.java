/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.processor;

import io.harness.idp.aggregation.rules.beans.AggregationRulesDTO;

import java.util.ArrayList;
import java.util.List;

public class CompositeAggregationProcessor implements AggregationProcessor {
  final List<AggregationProcessor> aggregationProcessorList;

  public CompositeAggregationProcessor(List<AggregationProcessor> aggregationProcessorList) {
    this.aggregationProcessorList = aggregationProcessorList;
  }

  @Override
  public List<AggregationRulesDTO> process() {
    List<AggregationRulesDTO> aggregationRulesDTOList = new ArrayList<>();
    aggregationProcessorList.forEach(
        aggregationProcessor -> aggregationRulesDTOList.addAll(aggregationProcessor.process()));
    return aggregationRulesDTOList;
  }

  @Override
  public void save(List<AggregationRulesDTO> aggregationRulesDTOs) {
    aggregationProcessorList.forEach(aggregationProcessor -> aggregationProcessor.save(aggregationRulesDTOs));
  }

  @Override
  public List<AggregationRulesDTO> rename(String oldName) {
    List<AggregationRulesDTO> aggregationRulesDTOList = new ArrayList<>();
    aggregationProcessorList.forEach(
        aggregationProcessor -> aggregationRulesDTOList.addAll(aggregationProcessor.rename(oldName)));
    return aggregationRulesDTOList;
  }

  @Override
  public List<AggregationRulesDTO> cleanup() {
    List<AggregationRulesDTO> aggregationRulesDTOList = new ArrayList<>();
    aggregationProcessorList.forEach(
        aggregationProcessor -> aggregationRulesDTOList.addAll(aggregationProcessor.cleanup()));
    return aggregationRulesDTOList;
  }
}
