/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.calculator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;

@OwnedBy(HarnessTeam.IDP)
public class AggregationRulesCalculatorFactory {
  public static AggregationCalculator getCalculator(AggregationRuleEntity.AggregationFormula formula) {
    return switch (formula) {
            case SUM -> new SumCalculator();
            case AVG -> new AverageCalculator();
            case COUNT -> new CountCalculator();
            case MAX -> new MaxCalculator();
            case MIN -> new MinCalculator();
            case MEDIAN -> new MedianCalculator();
        };
    }
}
