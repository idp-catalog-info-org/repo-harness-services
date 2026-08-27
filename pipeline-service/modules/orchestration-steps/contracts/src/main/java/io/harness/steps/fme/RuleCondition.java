/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static io.harness.annotations.dev.HarnessTeam.FME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents a condition containing a list of rules.
 * The condition groups multiple rules that determine which users/requests match the targeting rule.
 */
@Value
@Builder
@Jacksonized
@OwnedBy(FME)
@RecasterAlias("io.harness.steps.fme.RuleCondition")
public class RuleCondition {
  /**
   * List of rules that make up this condition.
   * Each rule evaluates specific criteria (attributes, segments, etc.).
   */
  @Nullable ParameterField<List<Rule>> rules;
}
