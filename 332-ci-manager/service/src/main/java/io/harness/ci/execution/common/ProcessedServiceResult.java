/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.unified.cd.service.overrides.OverridesConfig;
import io.harness.unified.cd.service.spec.ServiceConfig;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProcessedServiceResult {
  /** Key for artifacts map in {@link #serviceOutputMap}. */
  public static final String ARTIFACTS_KEY = "artifacts";
  /** Key for manifests map in {@link #serviceOutputMap}. */
  public static final String MANIFESTS_KEY = "manifests";
  /** Key for config files map in {@link #serviceOutputMap}. */
  public static final String CONFIG_FILES_KEY = "configFiles";

  ServiceEntityMetadata serviceEntityMetadata;
  ServiceConfig serviceConfig;
  Map<ServiceOverridesType, OverridesConfig> overrides;
  EnvironmentOutcome environmentOutcome;
  /**
   * Map of entity type -> entity map. Keys: {@link #ARTIFACTS_KEY}, {@link #MANIFESTS_KEY},
   * {@link #CONFIG_FILES_KEY}, and any future entity.
   * Each inner map is id -> entry (e.g. artifacts: id -> entry with inputs/templateYaml).
   */
  Map<String, Map<String, Object>> serviceOutputMap;

  /** Convenience: artifact map (id -> entry). Non-null. */
  public Map<String, Object> getArtifactMap() {
    if (serviceOutputMap == null || !serviceOutputMap.containsKey(ARTIFACTS_KEY)) {
      return new HashMap<>();
    }
    Map<String, Object> map = serviceOutputMap.get(ARTIFACTS_KEY);
    return map != null ? map : new HashMap<>();
  }

  /** Convenience: manifest map (id -> entry). Non-null. */
  public Map<String, Object> getManifestMap() {
    if (serviceOutputMap == null || !serviceOutputMap.containsKey(MANIFESTS_KEY)) {
      return new HashMap<>();
    }
    Map<String, Object> map = serviceOutputMap.get(MANIFESTS_KEY);
    return map != null ? map : new HashMap<>();
  }

  /** Convenience: config file map (id -> flattened entry). Non-null. */
  public Map<String, Object> getConfigFileMap() {
    if (serviceOutputMap == null || !serviceOutputMap.containsKey(CONFIG_FILES_KEY)) {
      return new HashMap<>();
    }
    Map<String, Object> map = serviceOutputMap.get(CONFIG_FILES_KEY);
    return map != null ? map : new HashMap<>();
  }
}
