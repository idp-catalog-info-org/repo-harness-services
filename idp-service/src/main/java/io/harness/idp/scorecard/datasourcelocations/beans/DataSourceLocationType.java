/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum DataSourceLocationType {
  @JsonProperty("DirectHttp") @Deprecated DIRECT_HTTP("DirectHttp"),
  @JsonProperty("CustomHttp") @Deprecated CUSTOM_HTTP("CustomHttp"),
  @JsonProperty("Noop") @Deprecated NO_OP("Noop"),
  @JsonProperty("HQL") HQL("HQL"),
  @JsonProperty("Catalog") CATALOG("Catalog");

  @Getter private final String type;

  private static final Set<DataSourceLocationType> LEGACY_TYPES = Set.of(DIRECT_HTTP, CUSTOM_HTTP, NO_OP);

  public boolean isLegacy() {
    return LEGACY_TYPES.contains(this);
  }

  public static DataSourceLocationType fromString(String stringValue) {
    for (DataSourceLocationType dslType : DataSourceLocationType.values()) {
      if (dslType.type.equalsIgnoreCase(stringValue)) {
        return dslType;
      }
    }
    return null;
  }
}
