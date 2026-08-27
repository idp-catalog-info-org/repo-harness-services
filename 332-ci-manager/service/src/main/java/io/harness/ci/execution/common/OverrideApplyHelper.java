/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_SERVICE_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.INFRA_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.INFRA_SERVICE_OVERRIDE;
import static io.harness.unified.cd.service.manifests.ManifestType.NO_OP_ACTION;
import static io.harness.utils.TemplateYamlSourceType.OVERRIDES_TYPE_TO_TEMPLATE_SOURCE_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.unified.cd.service.configfiles.ConfigFile;
import io.harness.unified.cd.service.manifests.ManifestConfig;
import io.harness.unified.cd.service.manifests.ManifestType;
import io.harness.unified.cd.service.manifests.ManifestWrapper;
import io.harness.unified.cd.service.manifests.ManifestWrapper.ManifestWrapperBuilder;
import io.harness.unified.cd.service.overrides.OverridesInfoConfig;
import io.harness.unified.cd.service.overrides.OverridesWrapperDTO;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.TemplateYamlConfig;
import io.harness.utils.TemplateYamlEntityType;
import io.harness.utils.TemplateYamlGenerator;
import io.harness.utils.TemplateYamlResult;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Template-based override apply helper.
 * Applies service overrides by fetching templates and merging default inputs in CI Manager.
 * Unlike NG Manager's TemplateBasedOverridesMapper (which converts NG to unified format),
 * this class fetches templates for manifests/config files and adds default inputs.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class OverrideApplyHelper {
  private static final List<ServiceOverridesType> OVERRIDE_IN_REVERSE_PRIORITY =
      List.of(ENV_GLOBAL_OVERRIDE, ENV_SERVICE_OVERRIDE, INFRA_GLOBAL_OVERRIDE, INFRA_SERVICE_OVERRIDE);

  @Inject private TemplateYamlGenerator templateYamlGenerator;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;

  public void handleOverrides(
      Ambiance ambiance, ServiceConfig serviceConfig, Map<ServiceOverridesType, OverridesWrapperDTO> overrides) {
    if (isEmpty(overrides)) {
      return;
    }

    updateManifestOverridesToServiceConfig(ambiance, serviceConfig, overrides);
    updateConfigFilesToServiceConfig(ambiance, serviceConfig, overrides);

    cdStepsExpressionResolver.updateExpressions(
        ambiance, serviceConfig, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
  }

  /**
   * Get ServiceOverridesType display name.
   * ENV_GLOBAL_OVERRIDE → "envGlobalOverride"
   * ENV_SERVICE_OVERRIDE → "envServiceOverride"
   * INFRA_GLOBAL_OVERRIDE → "infraGlobalOverride"
   * INFRA_SERVICE_OVERRIDE → "infraServiceOverride"
   */
  private static String getOverrideTypeDisplayName(ServiceOverridesType type) {
    return type.getDisplayName();
  }

  /**
   * Update manifest overrides to service config with template-based input fetching.
   */
  private void updateManifestOverridesToServiceConfig(
      Ambiance ambiance, ServiceConfig serviceConfig, Map<ServiceOverridesType, OverridesWrapperDTO> overrides) {
    if (serviceConfig.getServiceInfoConfig().getWith().getManifests() == null
        || isEmpty(serviceConfig.getServiceInfoConfig().getWith().getManifests().getSources())) {
      return;
    }
    List<ManifestConfig> manifests = serviceConfig.getServiceInfoConfig().getWith().getManifests().getSources() == null
        ? new ArrayList<>()
        : serviceConfig.getServiceInfoConfig().getWith().getManifests().getSources();
    ManifestWrapperBuilder manifestWrapperBuilder = ManifestWrapper.builder();
    ManifestWrapper currentManifests = serviceConfig.getServiceInfoConfig().getWith().getManifests();

    if (currentManifests == null || isEmpty(currentManifests.getSources())) {
      return;
    }

    // TODO: Handle helm repo override

    for (ServiceOverridesType overridesType : OVERRIDE_IN_REVERSE_PRIORITY) {
      if (!overrides.containsKey(overridesType)) {
        continue;
      }

      OverridesInfoConfig overridesInfoConfig = overrides.get(overridesType).getConfig().getOverridesInfoConfig();
      if (isEmpty(overridesInfoConfig.getManifests())) {
        continue;
      }

      List<ManifestConfig> overrideManifests = overridesInfoConfig.getManifests();

      for (ManifestConfig manifestOverride : overrideManifests) {
        // Skip helm repo overrides (already processed)
        if (manifestOverride.getUses() == ManifestType.HELM_REPO_OVERRIDE) {
          continue;
        }

        // Check if manifest already exists in service
        Optional<ManifestConfig> existingManifest =
            manifests.stream().filter(m -> m.getId().equals(manifestOverride.getId())).findFirst();

        if (existingManifest.isEmpty()) {
          // New manifest from override - fetch template and add default inputs
          ManifestConfig manifestWithInputs =
              fetchManifestTemplateAndAddInputs(ambiance, manifestOverride, overridesType);
          manifests.add(manifestWithInputs);
        } else {
          // Manifest exists - merge inputs from service and override (override takes priority)
          ManifestConfig serviceManifest = existingManifest.get();
          ManifestConfig overrideWithInputs =
              fetchManifestTemplateAndAddInputs(ambiance, manifestOverride, overridesType);

          // Merge inputs: service inputs first, then override inputs (override takes priority)
          Map<String, Object> mergedInputs = new HashMap<>();
          if (isNotEmpty(serviceManifest.getInputs())) {
            mergedInputs.putAll(serviceManifest.getInputs());
          }
          if (isNotEmpty(overrideWithInputs.getInputs())) {
            mergedInputs.putAll(overrideWithInputs.getInputs());
          }

          // Build final manifest with merged inputs
          ManifestConfig finalManifest = overrideWithInputs.toBuilder().inputs(mergedInputs).build();

          // Replace existing manifest with merged one
          manifests.removeIf(m -> m.getId().equals(manifestOverride.getId()));
          manifests.add(finalManifest);
        }
      }
    }

    manifestWrapperBuilder.primary(currentManifests.getPrimary());
    ManifestWrapper updatedManifestWrapper = manifestWrapperBuilder.sources(manifests).build();
    serviceConfig.getServiceInfoConfig().getWith().updateManifestsOverride(updatedManifestWrapper);
  }

  /**
   * Fetch manifest template and add default inputs to the manifest.
   * Replaces "ngService" prefix in template expressions with override type display name.
   */
  private ManifestConfig fetchManifestTemplateAndAddInputs(
      Ambiance ambiance, ManifestConfig manifestOverride, ServiceOverridesType overrideType) {
    // If no op action (template type), return as-is (no template to fetch)
    if (NO_OP_ACTION.equals(manifestOverride.getAction())) {
      log.debug("No template action for manifest {}, returning as-is", manifestOverride.getId());
      return manifestOverride;
    }

    try {
      // Build template config
      TemplateYamlConfig templateConfig = TemplateYamlConfig.builder()
                                              .templateType(manifestOverride.getAction())
                                              .entityType(TemplateYamlEntityType.MANIFEST)
                                              .sourceType(OVERRIDES_TYPE_TO_TEMPLATE_SOURCE_TYPE.get(overrideType))
                                              .entityId(manifestOverride.getId())
                                              .structuredInputsMap(new HashMap<>()) // Empty inputs map
                                              .inputsFlattener(Function.identity()) // No flattening needed
                                              .build();

      // Generate template YAML with defaults
      TemplateYamlResult result = templateYamlGenerator.generateTemplateYamlWithDefaults(ambiance, templateConfig);

      // Extract default inputs from template
      Map<String, Object> defaultInputs = result.getMergedInputs();

      if (isEmpty(defaultInputs)) {
        log.debug("No default inputs found in template for manifest {}", manifestOverride.getId());
        return manifestOverride;
      }

      // Merge resolved inputs with existing inputs from manifestOverride
      Map<String, Object> mergedInputs = new HashMap<>();
      if (isNotEmpty(manifestOverride.getInputs())) {
        mergedInputs.putAll(manifestOverride.getInputs());
      }
      mergedInputs.putAll(defaultInputs);

      // Build new ManifestConfig with merged inputs
      return manifestOverride.toBuilder().inputs(mergedInputs).build();

    } catch (Exception e) {
      log.error("Failed to fetch template for manifest {}, returning as-is", manifestOverride.getId(), e);
      return manifestOverride;
    }
  }

  /**
   * Update config file overrides to service config with template-based input fetching.
   */
  private void updateConfigFilesToServiceConfig(
      Ambiance ambiance, ServiceConfig serviceConfig, Map<ServiceOverridesType, OverridesWrapperDTO> overrides) {
    Map<String, ConfigFile> configFiles = new HashMap<>();

    // Add existing service config files
    if (isNotEmpty(serviceConfig.getServiceInfoConfig().getWith().getConfigFiles())) {
      Map<String, ConfigFile> svcConfigFiles =
          serviceConfig.getServiceInfoConfig().getWith().getConfigFiles().stream().collect(
              Collectors.toMap(ConfigFile::getId, Function.identity()));
      configFiles.putAll(svcConfigFiles);
    }

    // Process override config files in priority order
    for (ServiceOverridesType overridesType : OVERRIDE_IN_REVERSE_PRIORITY) {
      if (!overrides.containsKey(overridesType)) {
        continue;
      }

      OverridesInfoConfig overridesInfoConfig = overrides.get(overridesType).getConfig().getOverridesInfoConfig();
      if (isEmpty(overridesInfoConfig.getConfigFiles())) {
        continue;
      }

      List<ConfigFile> overrideConfigFiles = overridesInfoConfig.getConfigFiles();

      for (ConfigFile configFileOverride : overrideConfigFiles) {
        // Fetch template and add default inputs
        ConfigFile configFileWithInputs =
            fetchConfigFileTemplateAndAddInputs(ambiance, configFileOverride, overridesType);

        // Check if config file already exists in service
        if (!configFiles.containsKey(configFileWithInputs.getId())) {
          // New config file from override - add as-is
          configFiles.put(configFileWithInputs.getId(), configFileWithInputs);
        } else {
          // Config file exists - merge inputs from service and override (override takes priority)
          ConfigFile serviceConfigFile = configFiles.get(configFileWithInputs.getId());

          // Merge inputs: service inputs first, then override inputs (override takes priority)
          Map<String, Object> mergedInputs = new HashMap<>();
          if (isNotEmpty(serviceConfigFile.getInputs())) {
            mergedInputs.putAll(serviceConfigFile.getInputs());
          }
          if (isNotEmpty(configFileWithInputs.getInputs())) {
            mergedInputs.putAll(configFileWithInputs.getInputs());
          }

          // Build final config file with merged inputs
          ConfigFile finalConfigFile = configFileWithInputs.toBuilder().inputs(mergedInputs).build();
          configFiles.put(finalConfigFile.getId(), finalConfigFile);
        }
      }
    }

    // Update service config with merged config files
    if (isNotEmpty(configFiles)) {
      serviceConfig.getServiceInfoConfig().getWith().updateConfigFilesOverride(new ArrayList<>(configFiles.values()));
    }
  }

  /**
   * Fetch config file template and add default inputs to the config file.
   * Replaces "ngService" prefix in template expressions with override type display name.
   */
  private ConfigFile fetchConfigFileTemplateAndAddInputs(
      Ambiance ambiance, ConfigFile configFileOverride, ServiceOverridesType overrideType) {
    // If no action (template type), return as-is (no template to fetch)
    if (isEmpty(configFileOverride.getAction())) {
      log.debug("No template action for config file {}, returning as-is", configFileOverride.getId());
      return configFileOverride;
    }

    try {
      // Build template config
      TemplateYamlConfig templateConfig = TemplateYamlConfig.builder()
                                              .templateType(configFileOverride.getAction())
                                              .sourceType(OVERRIDES_TYPE_TO_TEMPLATE_SOURCE_TYPE.get(overrideType))
                                              .entityType(TemplateYamlEntityType.CONFIG_FILES)
                                              .entityId(configFileOverride.getId())
                                              .structuredInputsMap(new HashMap<>()) // Empty inputs map
                                              .inputsFlattener(Function.identity()) // No flattening needed
                                              .build();

      TemplateYamlResult result = templateYamlGenerator.generateTemplateYamlWithDefaults(ambiance, templateConfig);
      Map<String, Object> defaultInputs = result.getMergedInputs();

      // Merge resolved inputs with existing inputs from configFileOverride
      Map<String, Object> mergedInputs = new HashMap<>(defaultInputs);
      if (isNotEmpty(configFileOverride.getInputs())) {
        mergedInputs.putAll(configFileOverride.getInputs());
      }
      // Build new ConfigFile with merged inputs
      return configFileOverride.toBuilder().inputs(mergedInputs).build();

    } catch (Exception e) {
      log.error("Failed to fetch template for config file {}, returning as-is", configFileOverride.getId(), e);
      return configFileOverride;
    }
  }
}
