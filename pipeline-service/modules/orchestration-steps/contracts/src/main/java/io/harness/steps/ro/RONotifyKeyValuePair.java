/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.ro;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.PIPELINE)
@Data
@NoArgsConstructor
public class RONotifyKeyValuePair {
  private String key;
  private String value;

  @Builder
  public RONotifyKeyValuePair(String key, String value) {
    this.key = key;
    this.value = value;
  }
}
