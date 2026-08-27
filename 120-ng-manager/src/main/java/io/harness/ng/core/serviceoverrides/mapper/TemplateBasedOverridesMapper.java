/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverrides.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.service.mapper.ServiceVariableConversionUtils;
import io.harness.ng.core.service.mapper.TemplateBasedConfigFileMapper;
import io.harness.ng.core.service.mapper.TemplateBasedManifestMapper;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.overrides.SingleOverrideConvertorResponseDTO;
import io.harness.pms.yaml.YamlUtils;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.overrides.OverridesConfig;
import io.harness.unified.cd.service.overrides.OverridesInfoConfig;
import io.harness.unified.cd.service.overrides.OverridesInfoConfig.OverridesInfoConfigBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Template-based Override Mapper.
 * Converts NG service overrides to template-compatible structure with inputs mapping.
 * Mirrors TemplateBasedServiceMapper pattern for overrides.
 */
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class TemplateBasedOverridesMapper {
  private final TemplateBasedManifestMapper manifestMapperTemplate;
  private final TemplateBasedConfigFileMapper configFileMapperTemplate;
  /**
   * Convert all override types to template format.
   * Returns null if no overrides can use template path.
   *
   * @param mergedOverrideV2Configs Merged overrides by type
   * @return Map of override responses, or null if template path not applicable
   */
  public Map<ServiceOverridesType, SingleOverrideConvertorResponseDTO> toUnifiedOverridesWithTemplate(
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs) {
    if (isEmpty(mergedOverrideV2Configs)) {
      return null;
    }

    Map<ServiceOverridesType, SingleOverrideConvertorResponseDTO> result = new HashMap<>();

    for (Map.Entry<ServiceOverridesType, NGServiceOverrideConfigV2> entry : mergedOverrideV2Configs.entrySet()) {
      ServiceOverridesType type = entry.getKey();
      NGServiceOverrideConfigV2 overrideConfig = entry.getValue();

      SingleOverrideConvertorResponseDTO response = convertSingleOverride(type, overrideConfig);
      if (response != null) {
        result.put(type, response);
      }
    }

    return isEmpty(result) ? null : result;
  }

  /**
   * Convert a single override config to template format.
   * Uses template-based mappers for manifests and config files.
   */
  private SingleOverrideConvertorResponseDTO convertSingleOverride(
      ServiceOverridesType type, NGServiceOverrideConfigV2 overrideConfig) {
    ServiceOverridesSpec specNg = overrideConfig.getSpec();
    OverridesInfoConfigBuilder builder = OverridesInfoConfig.builder();

    // 1. Convert manifests (template path)
    if (isNotEmpty(specNg.getManifests())) {
      List<ManifestConfig> unifiedManifests =
          manifestMapperTemplate.toUnifiedManifestsWithInputs(specNg.getManifests());
      builder.manifests(unifiedManifests);
    }

    if (isNotEmpty(specNg.getVariables())) {
      Map<String, Object> unifiedInputs = ServiceVariableConversionUtils.toUnifiedInputs(specNg.getVariables());
      builder.inputs(unifiedInputs);
    }

    // 3. Convert config files (template path)
    if (isNotEmpty(specNg.getConfigFiles())) {
      List<ConfigFile> unifiedConfigFiles =
          configFileMapperTemplate.toUnifiedConfigFilesWithInputs(specNg.getConfigFiles());
      builder.configFiles(unifiedConfigFiles);
    }

    OverridesConfig overridesConfig = OverridesConfig.builder().overridesInfoConfig(builder.build()).build();

    String mergedYaml = YamlUtils.writeYamlString(overridesConfig);

    return SingleOverrideConvertorResponseDTO.builder()
        .identifier(overrideConfig.getIdentifier())
        .type(type)
        .environmentRef(overrideConfig.getEnvironmentRef())
        .serviceRef(overrideConfig.getServiceRef())
        .infraId(overrideConfig.getInfraId())
        .mergedYaml(mergedYaml)
        .build();
  }
}
