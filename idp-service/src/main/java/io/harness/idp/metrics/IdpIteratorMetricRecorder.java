/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.metrics;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IdpIteratorMetricRecorder {
  public static final String ITERATOR_SUCCESS_METRIC = "idp_iterator_success_total";
  public static final String ITERATOR_FAILURE_METRIC = "idp_iterator_failure_total";
  private static final String UNKNOWN_ACCOUNT = "unknown";

  private final MetricService metricService;

  @Inject
  public IdpIteratorMetricRecorder(MetricService metricService) {
    this.metricService = metricService;
  }

  public void recordSuccess(String iteratorName, String accountIdentifier) {
    try (IDPIteratorMetricContext ignore =
             new IDPIteratorMetricContext(resolveAccountIdentifier(accountIdentifier), iteratorName)) {
      metricService.incCounter(ITERATOR_SUCCESS_METRIC);
    }
  }

  public void recordFailure(String iteratorName, String accountIdentifier) {
    try (IDPIteratorMetricContext ignore =
             new IDPIteratorMetricContext(resolveAccountIdentifier(accountIdentifier), iteratorName)) {
      metricService.incCounter(ITERATOR_FAILURE_METRIC);
    }
  }

  private String resolveAccountIdentifier(String accountIdentifier) {
    return accountIdentifier == null || accountIdentifier.isEmpty() ? UNKNOWN_ACCOUNT : accountIdentifier;
  }
}
