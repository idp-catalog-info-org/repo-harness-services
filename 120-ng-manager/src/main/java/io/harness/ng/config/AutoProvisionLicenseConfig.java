/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.config;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class AutoProvisionLicenseConfig {
  private boolean enabled;
  private Map<Edition, String> editionModuleConfig;

  public List<ModuleType> getModulesForEdition(Edition edition) {
    if (editionModuleConfig == null || !editionModuleConfig.containsKey(edition)) {
      return Collections.emptyList();
    }
    String modules = editionModuleConfig.get(edition);
    if (modules == null || modules.isBlank()) {
      return Collections.emptyList();
    }
    List<ModuleType> result = new ArrayList<>();
    for (String module : modules.split(",")) {
      String trimmed = module.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      try {
        result.add(ModuleType.fromString(trimmed));
      } catch (IllegalArgumentException e) {
        log.warn("Skipping invalid module type '{}' in auto-provision config for edition {}", trimmed, edition);
      }
    }
    return result;
  }
}
