/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Getter
@Schema(
    name = "TimeRangeFilterType", description = "These are the default filters supported for  specifying time range")
public enum TimeRangeFilterType {
  LAST_7_DAYS("LAST_7_DAYS"),
  LAST_30_DAYS("LAST_30_DAYS"),
  THIS_MONTH("THIS_MONTH"),
  THIS_YEAR("THIS_YEAR"),
  LAST_MONTH("LAST_MONTH"),
  LAST_YEAR("LAST_YEAR"),
  LAST_3_MONTHS("LAST_3_MONTHS"),
  LAST_6_MONTHS("LAST_6_MONTHS"),
  LAST_12_MONTHS("LAST_12_MONTHS"),
  THIS_QUARTER("THIS_QUARTER"),
  LAST_QUARTER("LAST_QUARTER");

  private final String name;

  TimeRangeFilterType(String name) {
    this.name = name;
  }
}
