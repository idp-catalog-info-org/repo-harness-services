/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.service.mapper.TemplateBasedServiceMapperValidator.getStoreConfigTypeNG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.OciHelmChartConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.ng.core.service.registries.UnifiedConversionRegistry;
import io.harness.ng.core.service.registries.UnifiedConversionRegistry.ConversionResult;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.manifests.ManifestType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OPTIMIZED V2: Template-based Manifest Mapper.
 * Uses UnifiedConversionRegistry for simplified, minimal-change onboarding.
 *
 * <p><strong>Changes from V1:</strong>
 * <ul>
 *   <li>Single registry dependency (UnifiedConversionRegistry) instead of two
 *   <li>One method call for both type conversion and template name
 *   <li>Cleaner code, easier to understand
 * </ul>
 */
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class TemplateBasedManifestMapper {
  private final UnifiedConversionRegistry conversionRegistry;

  public List<ManifestConfig> toUnifiedManifestsWithInputs(List<ManifestConfigWrapper> manifests) {
    if (isEmpty(manifests)) {
      return new ArrayList<>();
    }

    return manifests.stream().map(this::toUnifiedManifestWithInputs).filter(Objects::nonNull).toList();
  }

  /**
   * Convert single manifest config with inputs mapping.
   * Directly maps v0 manifest config to inputs map without building full POJO structure.
   */
  private ManifestConfig toUnifiedManifestWithInputs(ManifestConfigWrapper manifestWrapper) {
    io.harness.cdng.manifest.yaml.ManifestConfig manifestConfig = manifestWrapper.getManifest();

    // Get NG types
    var manifestType = manifestConfig.getType();
    var storeConfigType = getStoreConfigTypeNG(manifestConfig);

    // Some store types (e.g. OCI) carry an inner sub-type discriminator that selects the template.
    // When a sub-type is present it must be explicitly registered in STORE_SUB_TYPE_ACTION_MAP —
    // there is no silent fallback to STORE_ACTION_MAP for sub-typed stores.
    String storeSubType = extractStoreSubType(manifestConfig);
    ConversionResult<ManifestType> result;
    if (storeSubType != null) {
      String subTypeAction = conversionRegistry.getSubTypeStoreAction(storeConfigType, storeSubType);
      if (subTypeAction == null) {
        log.debug("Manifest type {} with store {} sub-type {} has no registered template action — skipping",
            manifestType.getDisplayName(), storeConfigType != null ? storeConfigType.getDisplayName() : "null",
            storeSubType);
        return null;
      }
      result = conversionRegistry.convertManifestWithAction(manifestType, subTypeAction);
    } else {
      result = conversionRegistry.convertManifest(manifestType, storeConfigType);
    }

    if (result == null) {
      log.debug("Manifest type {} with store {} not onboarded for template-based conversion",
          manifestType.getDisplayName(), storeConfigType != null ? storeConfigType.getDisplayName() : "null");
      return null;
    }

    // Build minimal ManifestConfig with id, uses, and action
    return ManifestConfig.builder()
        .id(manifestConfig.getIdentifier())
        .uses(result.getUnifiedType())
        .action(result.getTemplateAction())
        .build();
  }

  private String extractStoreSubType(io.harness.cdng.manifest.yaml.ManifestConfig manifestConfig) {
    ManifestAttributes spec = manifestConfig.getSpec();
    if (spec == null) {
      return null;
    }
    StoreConfig storeConfig = spec.getStoreConfig();
    if (storeConfig instanceof OciHelmChartConfig) {
      OciHelmChartConfig ociConfig = (OciHelmChartConfig) storeConfig;
      if (ociConfig.getConfig() != null && ociConfig.getConfig().getValue() != null
          && ociConfig.getConfig().getValue().getType() != null) {
        return ociConfig.getConfig().getValue().getType().getDisplayName();
      }
    }
    return null;
  }
}
