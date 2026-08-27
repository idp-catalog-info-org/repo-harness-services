/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

/**
 * Validator for template-based service mapping.
 * Validates if entities (artifacts, manifests, config files) are eligible for template-based processing.
 */
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CI)
@Singleton
public class TemplateBasedServiceMapperValidator {
  @Nullable
  public static StoreConfigType getStoreConfigTypeNG(io.harness.cdng.manifest.yaml.ManifestConfig manifestConfig) {
    StoreConfigType storeConfigType = null;
    ManifestAttributes spec = manifestConfig.getSpec();
    if (spec != null) {
      StoreConfig storeConfig = spec.getStoreConfig();
      if (storeConfig != null && storeConfig.getKind() != null) {
        storeConfigType = StoreConfigType.getStoreConfigType(storeConfig.getKind());
      }
    }
    return storeConfigType;
  }
}
