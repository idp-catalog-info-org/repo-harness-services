/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.webhook.condition;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
public interface OperationEvaluator {
  String EQUALS_OPERATOR = "equals";
  String NOT_EQUALS_OPERATOR = "not-equals";
  String STARTS_WITH_OPERATOR = "starts-with";
  String ENDS_WITH_OPERATOR = "ends-with";
  String CONTAINS_OPERATOR = "contains";
  String DOES_NOT_CONTAIN_OPERATOR = "does-not-contain";
  String REGEX_OPERATOR = "regex";
  String IN_OPERATOR = "in";
  String NOT_IN_OPERATOR = "not-in";

  boolean evaluate(String input, String standard);
}
