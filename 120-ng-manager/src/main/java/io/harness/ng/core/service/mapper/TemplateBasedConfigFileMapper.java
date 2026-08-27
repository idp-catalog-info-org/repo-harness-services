/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.service.inputsmapper.ConfigFileInputsConstants.STORE_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.ng.core.service.registries.UnifiedConversionRegistry;
import io.harness.pms.yaml.ParameterField;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.manifests.StoreType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OPTIMIZED V2: Template-based Config File Mapper.
 * Uses UnifiedConversionRegistry for simplified, minimal-change onboarding.
 *
 * <p><strong>Changes from V1:</strong>
 * <ul>
 *   <li>Single registry dependency instead of two
 *   <li>Direct store type conversion
 *   <li>Cleaner code, easier to understand
 * </ul>
 */
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class TemplateBasedConfigFileMapper {
  private final UnifiedConversionRegistry conversionRegistry;

  public List<ConfigFile> toUnifiedConfigFilesWithInputs(List<ConfigFileWrapper> configFileWrappers) {
    if (isEmpty(configFileWrappers)) {
      return new ArrayList<>();
    }
    return configFileWrappers.stream().map(this::toUnifiedConfigFileWithInputs).filter(Objects::nonNull).toList();
  }

  /** Spot Elastigroup JSON config files historically used id {@code elastigroupjson}. */
  private static String normalizeSpotElastigroupConfigFileId(String identifier) {
    if ("elastigroupjson".equals(identifier)) {
      return "elastigroup";
    }
    return identifier;
  }

  private ConfigFile toUnifiedConfigFileWithInputs(ConfigFileWrapper wrapper) {
    if (wrapper == null || wrapper.getConfigFile() == null) {
      return null;
    }

    io.harness.cdng.configfile.ConfigFile ngConfigFile = wrapper.getConfigFile();
    ConfigFileAttributes spec = ngConfigFile.getSpec();
    if (spec == null || spec.getStore() == null || ParameterField.isNull(spec.getStore())) {
      log.debug(
          "Config file [{}] has no resolved store; skipping template-based mapping", ngConfigFile.getIdentifier());
      return null;
    }

    StoreConfigWrapper storeWrapper = spec.getStore().obtainValue();
    if (storeWrapper == null || storeWrapper.getType() == null) {
      log.debug(
          "Config file [{}] store wrapper invalid; skipping template-based mapping", ngConfigFile.getIdentifier());
      return null;
    }

    // Convert store type and get template action in one call
    StoreConfigType ngStoreType = storeWrapper.getType();
    StoreType unifiedStoreType = conversionRegistry.convertStoreType(ngStoreType);
    String action = conversionRegistry.convertConfigFileStore(ngStoreType);

    if (action == null) {
      log.debug("Config file store type {} not onboarded for template-based conversion", ngStoreType);
      return null;
    }

    Map<String, Object> inputsMap = new HashMap<>();
    if (unifiedStoreType != null) {
      inputsMap.putIfAbsent(STORE_TYPE, unifiedStoreType.getDisplayName());
    }

    String configFileId = normalizeSpotElastigroupConfigFileId(ngConfigFile.getIdentifier());
    return ConfigFile.builder().id(configFileId).action(action).inputs(inputsMap).build();
  }
}
