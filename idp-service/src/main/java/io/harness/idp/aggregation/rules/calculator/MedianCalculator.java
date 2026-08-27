/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.calculator;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public class MedianCalculator implements AggregationCalculator {
  @Override
  public Double calculate(List<Double> values) {
    if (isEmpty(values)) {
      return null;
    }
    return calculateMedian(values);
  }

  private Double calculateMedian(List<Double> values) {
    List<Double> sortedValues = values.stream().sorted().toList();
    int size = sortedValues.size();
    if (size % 2 == 0) {
      double mid1 = sortedValues.get(size / 2 - 1);
      double mid2 = sortedValues.get(size / 2);
      return (mid1 + mid2) / 2.0;
    } else {
      return sortedValues.get(size / 2);
    }
  }
}
