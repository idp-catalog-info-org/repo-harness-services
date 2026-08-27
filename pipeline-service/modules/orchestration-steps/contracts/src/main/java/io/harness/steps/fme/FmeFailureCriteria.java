/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.beans.ConstructorProperties;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.FME)
@Data
@Builder
@NoArgsConstructor
public class FmeFailureCriteria {
  @NotNull String type;
  @NotNull FmeFailureCriteriaSpec spec;

  @ConstructorProperties({"type", "spec"})
  public FmeFailureCriteria(String type, FmeFailureCriteriaSpec spec) {
    this.type = type;
    this.spec = spec;
  }
}
