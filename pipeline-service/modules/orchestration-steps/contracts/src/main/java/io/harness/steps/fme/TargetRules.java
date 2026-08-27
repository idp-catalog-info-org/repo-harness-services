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
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Target rules configuration containing conditions and allocations.
 * This represents the complete rules structure for targeting users.
 */
@Value
@Builder
@Jacksonized
@OwnedBy(FME)
@RecasterAlias("io.harness.steps.fme.TargetRules")
public class TargetRules {
  /**
   * Single condition that determines which users match this targeting rule.
   * The condition contains a list of rules to evaluate.
   */
  @Nullable ParameterField<RuleCondition> condition;

  /**
   * Traffic allocation across treatments.
   * Defines how matched users are distributed across different treatments.
   */
  @NotNull ParameterField<List<RuleAllocation>> allocation;
}
