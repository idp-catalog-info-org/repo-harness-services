/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.Getter;

@Getter
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public enum BulkUpdatableField {
  OWNER("owner");

  private final String key;

  BulkUpdatableField(String key) {
    this.key = key;
  }

  public static BulkUpdatableField fromKey(String key) {
    for (BulkUpdatableField field : values()) {
      if (field.key.equals(key)) {
        return field;
      }
    }
    throw new IllegalArgumentException("Unknown field key: " + key);
  }
}
