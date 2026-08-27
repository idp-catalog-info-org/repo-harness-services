/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.beans.FeatureName.PIPE_REVERT_SVC_ENV_INFRA_GIT_DETAILS_OUTPUT;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessArtifactRegistryHelper;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessRegistryConstants;
import io.harness.cdng.artifact.mappers.ArtifactResponseToOutcomeMapperV2;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactSourceCandidatesOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome.ArtifactsOutcomeBuilder;
import io.harness.cdng.artifact.outcome.HarArtifactOutcome;
import io.harness.cdng.artifact.outcome.SidecarsOutcome;
import io.harness.cdng.artifact.resolvers.DeployableArtifactResolver;
import io.harness.cdng.artifact.utils.ArtifactsProcessedResponse;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.configfile.mapper.ConfigFileOutcomeMapper;
import io.harness.cdng.environment.helper.EnvironmentMapper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.steps.outcome.ManifestSourceCandidatesOutcome;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.kinds.AutoScalerManifest;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.OpenshiftManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.kinds.kustomize.KustomizeManifest;
import io.harness.cdng.manifestConfigs.ManifestConfigurations;
import io.harness.cdng.service.ServiceSpec;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.NativeHelmServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcomeHelper;
import io.harness.cdng.service.steps.ServiceStepOverrideHelper;
import io.harness.cdng.service.steps.constants.ServiceStepConstants;
import io.harness.cdng.service.steps.helpers.ServiceOverrideUtilityFacade;
import io.harness.cdng.service.steps.helpers.beans.OverridesFetchRequestParams;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitx.GitDetails;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mapper.PrimaryManifestFilterUtils;
import io.harness.ng.core.service.mapper.ServiceInputMergeUtils;
import io.harness.ng.core.service.mapper.ServiceTypeConversionUtils;
import io.harness.ng.core.service.mapper.TemplateBasedServiceMapper;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverrides.mapper.TemplateBasedOverridesMapper;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.utils.NgManagerErrorResponseUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.unified.cd.service.spec.ServiceType;
import io.harness.unified.error.NgManagerErrorResponseDTO;
import io.harness.unified.service.NGEntityFetchRequest;
import io.harness.unified.service.NGOutcomes;
import io.harness.unified.service.NGServiceEntityMetadata;
import io.harness.unified.service.NgServicePropertiesResponse;
import io.harness.unified.service.NgServicePropertiesResponseDTO;
import io.harness.unified.service.OverrideFetchRequest;
import io.harness.unified.service.UnifiedServiceConverterRequestDTO;
import io.harness.unified.service.UnifiedServiceConverterResponse;
import io.harness.unified.service.UnifiedServiceConverterResponseDTO;
import io.harness.unified.service.UnifiedServiceTypeResponse;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class for converting NG services to unified services.
 * Contains utility methods for processing service entities, artifacts, manifests, and config files.
 */
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class NgToUnifiedServiceHelper {
  private final ServiceEntityService serviceEntityService;
  private final ServiceOverrideUtilityFacade serviceOverrideUtilityFacade;
  private final EnvironmentService environmentService;
  private final NGFeatureFlagHelperService featureFlagHelperService;
  private final ObjectMapper objectMapper;
  private final CDExpressionResolver cdExpressionResolver;
  private final ServiceStepOutcomeHelper serviceStepOutcomeHelper;
  private final GitAwareEntityHelper gitAwareEntityHelper;
  private final DeployableArtifactResolver deployableArtifactResolver;
  private final HarnessArtifactRegistryHelper harnessArtifactRegistryHelper;
  // NEW FRAMEWORK: Template-based conversion dependencies
  private final TemplateBasedServiceMapper stageServiceMapperTemplate;
  private final TemplateBasedOverridesMapper templateBasedOverridesMapper;

  /**
   * Return type for buildNgOutcomes that includes both outcomes and overrides.
   */
  @Value
  @Builder
  public static class NgOutcomesWithOverrides {
    Map<String, String> ngOutcomes;
    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs;
  }

  /**
   * Gets merged service YAML from request or fetches it from service entity.
   *
   * @param requestDTO The request DTO containing optional merged service YAML
   * @param serviceIdentifier The service identifier
   * @param accountId The account identifier
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @param scopeInfo The scope information
   * @param useScopeInfo Whether to use scope info
   * @return Merged service YAML string
   * @throws IOException If there's an error reading the YAML
   */
  public String getMergedServiceYamlOrFromRequest(UnifiedServiceConverterRequestDTO requestDTO,
      String serviceIdentifier, String accountId, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo,
      boolean useScopeInfo) throws IOException {
    if (isNotEmpty(requestDTO.getMergedV0ServiceYaml())) {
      return requestDTO.getMergedV0ServiceYaml();
    }
    return getMergedServiceYaml(
        serviceIdentifier, accountId, orgIdentifier, projectIdentifier, requestDTO, scopeInfo, useScopeInfo);
  }

  /**
   * Builds NG outcomes (artifacts, manifests, config files) from the service configuration.
   *
   * @param mergedNgServiceYaml The merged service YAML
   * @param requestDTO The request DTO containing override information
   * @param accountId The account identifier
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @param serviceIdentifier The service identifier
   * @param useScopeInfo Whether to use scope info
   * @param scopeInfo The scope information
   * @param ambiance The execution ambiance (optional, may be null for non-execution contexts)
   * @return NgOutcomesWithOverrides containing both outcomes and overrides
   */
  public NgOutcomesWithOverrides buildNgOutcomes(String mergedNgServiceYaml,
      UnifiedServiceConverterRequestDTO requestDTO, String accountId, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, boolean useScopeInfo, ScopeInfo scopeInfo) throws IOException {
    Map<String, String> ngOutcomes = new HashMap<>();
    NGServiceConfig ngServiceConfig = YamlUtils.read(mergedNgServiceYaml, NGServiceConfig.class);

    NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();
    if (ngServiceV2InfoConfig == null) {
      return NgOutcomesWithOverrides.builder()
          .ngOutcomes(ngOutcomes)
          .mergedOverrideV2Configs(new EnumMap<>(ServiceOverridesType.class))
          .build();
    }

    ServiceSpec serviceSpec = ngServiceV2InfoConfig.getServiceDefinition().getServiceSpec();
    if (serviceSpec == null) {
      return NgOutcomesWithOverrides.builder()
          .ngOutcomes(ngOutcomes)
          .mergedOverrideV2Configs(new EnumMap<>(ServiceOverridesType.class))
          .build();
    }

    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs =
        new EnumMap<>(ServiceOverridesType.class);

    if (isNotEmpty(requestDTO.getMergedV0OverridesYaml())) {
      mergedOverrideV2Configs = YamlUtils.read(requestDTO.getMergedV0OverridesYaml(),
          new TypeReference<EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2>>() {});
    } else if (requestDTO.getOverrideFetchRequest() != null) {
      String envBranch = requestDTO.getOverrideFetchRequest().getEnvBranch();
      mergedOverrideV2Configs = getMergedOverrideConfigs(requestDTO, accountId, orgIdentifier, projectIdentifier,
          serviceIdentifier, useScopeInfo, scopeInfo, envBranch);
    }

    addArtifactsOutcome(serviceSpec, ngOutcomes, scopeInfo);
    addManifestsOutcome(serviceSpec, mergedOverrideV2Configs, ngOutcomes);
    addConfigFilesOutcome(serviceSpec, mergedOverrideV2Configs, ngOutcomes);

    return NgOutcomesWithOverrides.builder()
        .ngOutcomes(ngOutcomes)
        .mergedOverrideV2Configs(mergedOverrideV2Configs)
        .build();
  }

  /**
   * Validates service-level parameters in the merged NG service YAML before it is converted to the unified service
   * format, failing fast with a clear, service-scoped message instead of an opaque downstream error.
   *
   * <p>Currently it validates the boolean manifest flags ({@code optionalValuesYaml}, {@code skipResourceVersioning}
   * and {@code enableDeclarativeRollback}) across the supported manifest swimlanes: parameters that, if left as a
   * runtime input / expression (e.g. {@code <+input>}) or any other non-boolean value, would otherwise surface later
   * as an obscure "Couldn't convert object to Yaml" serialization error while the manifest outcomes are built.
   *
   * <p>This is the single entry point for such early validation and can be extended to validate additional service
   * parameters as the need arises.
   *
   * @param mergedNgServiceYaml the merged NG service YAML about to be converted
   * @throws IOException if the YAML cannot be read
   */
  public void validateServiceParameters(String mergedNgServiceYaml) throws IOException {
    if (isEmpty(mergedNgServiceYaml)) {
      return;
    }
    validateServiceBooleanParameters(YamlUtils.read(mergedNgServiceYaml, NGServiceConfig.class));
  }

  private void validateServiceBooleanParameters(NGServiceConfig ngServiceConfig) {
    if (ngServiceConfig == null || ngServiceConfig.getNgServiceV2InfoConfig() == null) {
      return;
    }
    NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();
    ServiceDefinition serviceDefinition = ngServiceV2InfoConfig.getServiceDefinition();
    if (serviceDefinition == null || serviceDefinition.getServiceSpec() == null) {
      return;
    }
    List<ManifestConfigWrapper> manifests = serviceDefinition.getServiceSpec().getManifests();
    if (isEmpty(manifests)) {
      return;
    }
    String serviceIdentifier = ngServiceV2InfoConfig.getIdentifier();
    for (ManifestConfigWrapper manifestWrapper : manifests) {
      if (manifestWrapper == null || manifestWrapper.getManifest() == null) {
        continue;
      }
      validateManifestBooleanParameters(manifestWrapper.getManifest().getSpec(), serviceIdentifier);
    }
  }

  /**
   * Validates every boolean manifest flag (e.g. {@code optionalValuesYaml}, {@code skipResourceVersioning},
   * {@code enableDeclarativeRollback}) exposed by the given manifest swimlane. Each flag left as a runtime input /
   * expression (e.g. {@code <+input>}) or any other non-boolean value fails fast with a clear, service-scoped message.
   */
  private void validateManifestBooleanParameters(ManifestAttributes manifestAttributes, String serviceIdentifier) {
    Map<String, ParameterField<Boolean>> booleanParameters = getBooleanManifestParameters(manifestAttributes);
    for (Map.Entry<String, ParameterField<Boolean>> parameter : booleanParameters.entrySet()) {
      validateBooleanParameter(parameter.getKey(), parameter.getValue(), serviceIdentifier);
    }
  }

  private void validateBooleanParameter(
      String parameterName, ParameterField<Boolean> parameter, String serviceIdentifier) {
    if (parameter == null) {
      return;
    }
    Object finalValue = parameter.fetchFinalValue();
    if (finalValue == null) {
      return;
    }
    try {
      ParameterFieldHelper.getBooleanParameterFieldValue(ParameterField.createValueField(finalValue));
    } catch (IllegalArgumentException | ClassCastException ex) {
      throw new InvalidRequestException(String.format(
          "Invalid value [%s] provided for %s in service [%s]. It should be a boolean value either true or false.",
          finalValue, parameterName, serviceIdentifier));
    }
  }

  /**
   * Collects the boolean flags declared by a manifest swimlane, keyed by their YAML field name (used in the validation
   * error message). Manifest kinds without any boolean flag yield an empty map. Extend this when a new swimlane or
   * boolean flag needs the same validation.
   */
  private Map<String, ParameterField<Boolean>> getBooleanManifestParameters(ManifestAttributes manifestAttributes) {
    Map<String, ParameterField<Boolean>> booleanParameters = new LinkedHashMap<>();
    if (manifestAttributes instanceof K8sManifest k8sManifest) {
      booleanParameters.put("optionalValuesYaml", k8sManifest.getOptionalValuesYaml());
      booleanParameters.put("skipResourceVersioning", k8sManifest.getSkipResourceVersioning());
      booleanParameters.put("enableDeclarativeRollback", k8sManifest.getEnableDeclarativeRollback());
    } else if (manifestAttributes instanceof HelmChartManifest helmChartManifest) {
      booleanParameters.put("optionalValuesYaml", helmChartManifest.getOptionalValuesYaml());
      booleanParameters.put("skipResourceVersioning", helmChartManifest.getSkipResourceVersioning());
      booleanParameters.put("enableDeclarativeRollback", helmChartManifest.getEnableDeclarativeRollback());
    } else if (manifestAttributes instanceof OpenshiftManifest openshiftManifest) {
      booleanParameters.put("skipResourceVersioning", openshiftManifest.getSkipResourceVersioning());
      booleanParameters.put("enableDeclarativeRollback", openshiftManifest.getEnableDeclarativeRollback());
    } else if (manifestAttributes instanceof KustomizeManifest kustomizeManifest) {
      booleanParameters.put("skipResourceVersioning", kustomizeManifest.getSkipResourceVersioning());
      booleanParameters.put("enableDeclarativeRollback", kustomizeManifest.getEnableDeclarativeRollback());
    } else if (manifestAttributes instanceof AutoScalerManifest autoScalerManifest) {
      booleanParameters.put("skipResourceVersioning", autoScalerManifest.getSkipResourceVersioning());
    } else if (manifestAttributes instanceof ValuesManifest valuesManifest) {
      booleanParameters.put("optionalValuesYaml", valuesManifest.getOptionalValuesYaml());
    }
    return booleanParameters;
  }

  /**
   * Maps an NG {@link ServiceDefinitionType} to its unified swimlane {@link ServiceType}. This is the branch-agnostic
   * path used when the type is read straight from the service's persisted DB metadata (no YAML parsing, no outcome
   * building), so it works for inline and remote services alike - even when the service still carries unresolved
   * runtime inputs.
   *
   * @param serviceDefinitionType the NG service definition type (may be {@code null})
   * @return the unified {@link ServiceType}, or {@code null} when it is {@code null} or has no unified mapping
   */
  public ServiceType resolveUnifiedServiceType(ServiceDefinitionType serviceDefinitionType) {
    if (serviceDefinitionType == null) {
      return null;
    }
    return ServiceTypeConversionUtils.SERVICE_TYPE_CONVERSION_MAP.get(serviceDefinitionType.getYamlName());
  }

  /**
   * Gets merged override configurations from request or fetches them.
   *
   * @param requestDTO The request DTO
   * @param accountId The account identifier
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @param serviceIdentifier The service identifier
   * @param useScopeInfo Whether to use scope info
   * @param scopeInfo The scope information
   * @return EnumMap of override configurations
   */
  public EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> getMergedOverrideConfigs(
      UnifiedServiceConverterRequestDTO requestDTO, String accountId, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, boolean useScopeInfo, ScopeInfo scopeInfo, String envBranch) throws IOException {
    if (isNotEmpty(requestDTO.getMergedV0OverridesYaml())) {
      return YamlUtils.read(requestDTO.getMergedV0OverridesYaml(),
          new TypeReference<EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2>>() {});
    }

    if (requestDTO.getOverrideFetchRequest() == null || isEmpty(requestDTO.getOverrideFetchRequest().getEnvRef())) {
      return new EnumMap<>(ServiceOverridesType.class);
    }

    Optional<Environment> environment = fetchEnvironment(requestDTO.getOverrideFetchRequest(), accountId, orgIdentifier,
        projectIdentifier, useScopeInfo ? scopeInfo : null, envBranch);

    if (environment.isEmpty()) {
      return new EnumMap<>(ServiceOverridesType.class);
    }

    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs =
        fetchMergedOverrideYaml(requestDTO.getOverrideFetchRequest(), accountId, orgIdentifier, projectIdentifier,
            serviceIdentifier, environment.get());
    return mergedOverrideV2Configs != null ? mergedOverrideV2Configs : new EnumMap<>(ServiceOverridesType.class);
  }

  /**
   * Adds artifacts outcome to the outcomes map if artifacts are present.
   *
   * @param serviceSpec The service specification
   * @param ngOutcomes The outcomes map to update
   * @param scopeInfo The scope information (optional, may be null)
   */
  public void addArtifactsOutcome(ServiceSpec serviceSpec, Map<String, String> ngOutcomes, ScopeInfo scopeInfo) {
    if (serviceSpec.getArtifacts() != null) {
      ArtifactsOutcome artifactsOutcome = getArtifactsOutcome(serviceSpec, scopeInfo);
      ngOutcomes.put(NGOutcomes.ARTIFACTS.getName(), YamlUtils.writeYamlString(artifactsOutcome));

      ArtifactSourceCandidatesOutcome artifactSourceCandidates =
          getArtifactSourceCandidatesOutcome(serviceSpec, scopeInfo);
      if (isNotEmpty(artifactSourceCandidates)) {
        ngOutcomes.put(
            NGOutcomes.ARTIFACT_SOURCE_CANDIDATES.getName(), YamlUtils.writeYamlString(artifactSourceCandidates));
      }
    }
  }

  /**
   * When the primary artifact ref is an expression (unresolved at conversion time), the unified {@code primary}
   * only carries the expression, not a resolved {@link ArtifactOutcome}. Emit every source as a keyed candidate
   * outcome so the CI service step can restore {@code ngOutcomes.artifacts.primary} once the ref is resolved.
   */
  private ArtifactSourceCandidatesOutcome getArtifactSourceCandidatesOutcome(
      ServiceSpec serviceSpec, ScopeInfo scopeInfo) {
    ArtifactSourceCandidatesOutcome candidates = new ArtifactSourceCandidatesOutcome();
    io.harness.cdng.artifact.bean.yaml.PrimaryArtifact primary = serviceSpec.getArtifacts().getPrimary();
    if (primary == null || primary.getSpec() != null || !ParameterField.isNotNull(primary.getPrimaryArtifactRef())
        || !primary.getPrimaryArtifactRef().isExpression() || isEmpty(primary.getSources())) {
      return candidates;
    }
    for (io.harness.cdng.artifact.bean.yaml.ArtifactSource source : primary.getSources()) {
      if (source.getSpec() == null) {
        continue;
      }
      ArtifactOutcome candidateOutcome = ArtifactResponseToOutcomeMapperV2.toArtifactOutcome(
          source.getSpec(), null, false, scopeInfo, deployableArtifactResolver);
      resolveHarDownloadUrl(candidateOutcome, scopeInfo);
      candidates.put(source.getIdentifier(), candidateOutcome);
    }
    return candidates;
  }

  /**
   * Adds manifests outcome to the outcomes map if manifests are present.
   *
   * @param serviceSpec The service specification
   * @param mergedOverrideV2Configs The merged override configurations
   * @param ngOutcomes The outcomes map to update
   */
  public void addManifestsOutcome(ServiceSpec serviceSpec,
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs,
      Map<String, String> ngOutcomes) {
    if (isEmpty(serviceSpec.getManifests())) {
      return;
    }

    List<ManifestConfigWrapper> svcManifests =
        filterManifestsForPrimaryManifestRef(serviceSpec, emptyIfNull(serviceSpec.getManifests()));
    ManifestsOutcome manifestsOutcome = generateManifestOutcomes(svcManifests, mergedOverrideV2Configs, serviceSpec);
    if (isNotEmpty(manifestsOutcome)) {
      ngOutcomes.put(NGOutcomes.MANIFESTS.getName(), YamlUtils.writeYamlString(manifestsOutcome));

      ManifestSourceCandidatesOutcome manifestSourceCandidates =
          getManifestSourceCandidatesOutcome(serviceSpec, manifestsOutcome);
      if (isNotEmpty(manifestSourceCandidates)) {
        ngOutcomes.put(
            NGOutcomes.MANIFEST_SOURCE_CANDIDATES.getName(), YamlUtils.writeYamlString(manifestSourceCandidates));
      }
    }
  }

  /**
   * When {@code primaryManifestRef} is an expression it cannot be resolved at conversion time, so neither the losing
   * manifests can be filtered out nor {@code manifests.primary} can be populated. Emit every manifest as a keyed
   * candidate so the CI service step can restore {@code ngOutcomes.manifests.primary} once the ref is resolved. This
   * mirrors {@link #getArtifactSourceCandidatesOutcome}.
   */
  private ManifestSourceCandidatesOutcome getManifestSourceCandidatesOutcome(
      ServiceSpec serviceSpec, ManifestsOutcome manifestsOutcome) {
    ManifestSourceCandidatesOutcome candidates = new ManifestSourceCandidatesOutcome();
    if (!hasExpressionPrimaryManifestRef(serviceSpec)) {
      return candidates;
    }
    manifestsOutcome.forEach((identifier, manifestOutcome) -> {
      if (!PRIMARY.equals(identifier) && manifestOutcome != null) {
        candidates.put(identifier, manifestOutcome);
      }
    });
    return candidates;
  }

  private static boolean hasExpressionPrimaryManifestRef(ServiceSpec serviceSpec) {
    ManifestConfigurations manifestConfigurations = getManifestConfigurations(serviceSpec);
    return manifestConfigurations != null && ParameterField.isNotNull(manifestConfigurations.getPrimaryManifestRef())
        && manifestConfigurations.getPrimaryManifestRef().isExpression();
  }

  private static ManifestConfigurations getManifestConfigurations(ServiceSpec serviceSpec) {
    if (serviceSpec instanceof KubernetesServiceSpec kubernetesServiceSpec) {
      return kubernetesServiceSpec.getManifestConfigurations();
    }
    if (serviceSpec instanceof NativeHelmServiceSpec nativeHelmServiceSpec) {
      return nativeHelmServiceSpec.getManifestConfigurations();
    }
    return null;
  }

  /**
   * Adds config files outcome to the outcomes map if config files are present.
   *
   * @param serviceSpec The service specification
   * @param mergedOverrideV2Configs The merged override configurations
   * @param ngOutcomes The outcomes map to update
   */
  public void addConfigFilesOutcome(ServiceSpec serviceSpec,
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs,
      Map<String, String> ngOutcomes) {
    List<ConfigFileWrapper> svcConfigFiles = emptyIfNull(serviceSpec.getConfigFiles());
    ConfigFilesOutcome configFilesOutcome = generateConfigFilesOutcomes(svcConfigFiles, mergedOverrideV2Configs);
    if (isNotEmpty(configFilesOutcome)) {
      ngOutcomes.put(NGOutcomes.CONFIG_FILES.getName(), YamlUtils.writeYamlString(configFilesOutcome));
    }
  }

  /**
   * Builds the unified service response.
   *
   * @param mergedUnifiedServiceYaml The merged unified service YAML
   * @param ngOutcomes The NG outcomes map
   * @return Response DTO containing the unified service conversion response
   */
  public ResponseDTO<UnifiedServiceConverterResponse> buildUnifiedServiceResponse(
      String mergedUnifiedServiceYaml, Map<String, String> ngOutcomes) {
    UnifiedServiceConverterResponseDTO responseDTO = UnifiedServiceConverterResponseDTO.builder()
                                                         .mergedServiceYaml(mergedUnifiedServiceYaml)
                                                         .ngOutcomes(ngOutcomes)
                                                         .build();

    UnifiedServiceConverterResponse response =
        UnifiedServiceConverterResponse.builder().responseDTO(responseDTO).build();

    return ResponseDTO.newResponse(response);
  }

  /**
   * NEW FRAMEWORK: Builds unified service response with template path support.
   * Tries template path first, falls back to POJO path if template not available.
   *
   * @param mergedNgServiceYaml The merged NG service YAML
   * @param ngOutcomes The NG outcomes map
   * @param mergedOverrideV2Configs The merged override configurations
   * @return Response DTO containing the unified service conversion response with templateBased flag
   */
  public ResponseDTO<UnifiedServiceConverterResponse> buildUnifiedServiceResponseWithTemplate(
      String mergedNgServiceYaml, Map<String, String> ngOutcomes,
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs) throws IOException {
    NGServiceConfig ngServiceConfig = YamlUtils.read(mergedNgServiceYaml, NGServiceConfig.class);

    io.harness.unified.cd.service.spec.ServiceConfig templateServiceConfig =
        stageServiceMapperTemplate.toUnifiedServiceWithTemplate(ngServiceConfig);

    if (templateServiceConfig == null) {
      throw new InvalidRequestException(String.format(
          "Failed to convert service %s using template-based path. POJO-based conversion has been removed.",
          ngServiceConfig.getNgServiceV2InfoConfig().getIdentifier()));
    }

    String mergedUnifiedServiceYaml = YamlPipelineUtils.writeYamlString(templateServiceConfig);
    log.debug("Using template-based conversion path for service: {}",
        ngServiceConfig.getNgServiceV2InfoConfig().getIdentifier());

    // Build v0OverridesYamlMap and convert overrides using template path
    EnumMap<ServiceOverridesType, String> v0OverridesYamlMap = null;
    Map<ServiceOverridesType, io.harness.overrides.SingleOverrideConvertorResponseDTO> overridesResponses = null;

    if (isNotEmpty(mergedOverrideV2Configs)) {
      v0OverridesYamlMap = new EnumMap<>(ServiceOverridesType.class);
      for (Map.Entry<ServiceOverridesType, NGServiceOverrideConfigV2> entry : mergedOverrideV2Configs.entrySet()) {
        String overrideYaml = YamlUtils.writeYamlString(entry.getValue());
        v0OverridesYamlMap.put(entry.getKey(), overrideYaml);
      }

      overridesResponses = templateBasedOverridesMapper.toUnifiedOverridesWithTemplate(mergedOverrideV2Configs);
    }

    UnifiedServiceConverterResponseDTO responseDTO = UnifiedServiceConverterResponseDTO.builder()
                                                         .mergedServiceYaml(mergedUnifiedServiceYaml)
                                                         .ngOutcomes(ngOutcomes)
                                                         .templateBased(true)
                                                         .mergedV0ServiceYaml(mergedNgServiceYaml)
                                                         .v0OverridesYamlMap(v0OverridesYamlMap)
                                                         .overridesResponses(overridesResponses)
                                                         .build();

    UnifiedServiceConverterResponse response =
        UnifiedServiceConverterResponse.builder().responseDTO(responseDTO).build();

    return ResponseDTO.newResponse(response);
  }

  /**
   * Validates and extracts service identifier from the request.
   *
   * @param requestDTO The NG entity fetch request
   * @return The service identifier
   * @throws InvalidRequestException if service identifier is empty
   */
  public String validateAndGetServiceIdentifier(NGEntityFetchRequest requestDTO) {
    String serviceIdentifier = requestDTO.getServiceFetchRequest().getServiceRef();
    if (isEmpty(serviceIdentifier)) {
      throw new InvalidRequestException("Service identifier is required in ServiceFetchRequest");
    }
    return serviceIdentifier;
  }

  /**
   * Fetches the service entity based on scope info usage.
   *
   * @param accountId The account identifier
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @param serviceIdentifier The service identifier
   * @param useScopeInfo Whether to use scope info
   * @param scopeInfo The scope information
   * @return The service entity
   * @throws NotFoundException if service entity is not found
   */
  public ServiceEntity fetchServiceEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, boolean useScopeInfo, ScopeInfo scopeInfo) {
    Optional<ServiceEntity> serviceEntityOp = useScopeInfo
        ? serviceEntityService.get(scopeInfo, serviceIdentifier, false)
        : serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);

    if (serviceEntityOp.isEmpty()) {
      throw new NotFoundException(
          ServiceElementMapper.getServiceNotFoundError(orgIdentifier, projectIdentifier, serviceIdentifier));
    }

    return serviceEntityOp.get();
  }

  /**
   * Gets merged NG service YAML from the service entity.
   *
   * @param requestDTO The NG entity fetch request
   * @param serviceEntity The service entity
   * @param useScopeInfo Whether to use scope info
   * @param scopeInfo The scope information
   * @return Merged NG service YAML string
   */
  public String getMergedNgServiceYaml(
      NGEntityFetchRequest requestDTO, ServiceEntity serviceEntity, boolean useScopeInfo, ScopeInfo scopeInfo) {
    UnifiedServiceConverterRequestDTO tempRequestDTO =
        UnifiedServiceConverterRequestDTO.builder()
            .serviceInputsYaml(requestDTO.getServiceFetchRequest().getServiceInputsYaml())
            .build();

    return ServiceInputMergeUtils.getMergedNgServiceYaml(
        tempRequestDTO, serviceEntity, useScopeInfo ? scopeInfo : null);
  }

  /**
   * Builds service step outcome from service entity and config.
   *
   * @param serviceEntity The service entity
   * @param ngServiceConfig The NG service config
   * @param accountId The account identifier
   * @param useScopeInfo Whether to use scope info
   * @param scopeInfo The scope information
   * @return Service step outcome
   */
  public ServiceStepOutcome buildServiceStepOutcome(ServiceEntity serviceEntity, NGServiceConfig ngServiceConfig,
      String accountId, boolean useScopeInfo, ScopeInfo scopeInfo) {
    boolean disableGitDetailsOutput =
        featureFlagHelperService.isDisabled(accountId, PIPE_REVERT_SVC_ENV_INFRA_GIT_DETAILS_OUTPUT);

    String serviceRef = getServiceRef(serviceEntity, accountId, useScopeInfo, scopeInfo);
    GitDetails gitDetails = getGitDetails(serviceEntity, disableGitDetailsOutput);

    return serviceStepOutcomeHelper.getServiceStepOutcome(
        serviceRef, ngServiceConfig.getNgServiceV2InfoConfig(), null, gitDetails);
  }

  @Nullable
  private static GitDetails getGitDetails(ServiceEntity serviceEntity, boolean disableGitDetailsOutput) {
    GitDetails gitDetails = null;
    if (!disableGitDetailsOutput) {
      gitDetails = GitDetails.fromEntityGitDetails(ServiceElementMapper.getEntityGitDetails(serviceEntity));
    }
    return gitDetails;
  }

  private static String getServiceRef(
      ServiceEntity serviceEntity, String accountId, boolean useScopeInfo, ScopeInfo scopeInfo) {
    String serviceRef;
    if (useScopeInfo) {
      serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(scopeInfo.getAccountIdentifier(),
          scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), serviceEntity.getIdentifier());
    } else {
      serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, serviceEntity.getOrgIdentifier(),
          serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier());
    }
    return serviceRef;
  }

  /**
   * Gets merged override YAML and environment outcome if override fetch request is present.
   *
   * @param requestDTO The NG entity fetch request
   * @param accountId The account identifier
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @param serviceIdentifier The service identifier
   * @param useScopeInfo Whether to use scope info
   * @param scopeInfo The scope information
   * @return Map containing environment and overrides YAML strings, or empty map if not present
   */
  public Map<String, String> getMergedOverrideYaml(NGEntityFetchRequest requestDTO, String accountId,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier, boolean useScopeInfo,
      ScopeInfo scopeInfo) {
    Map<String, String> result = new HashMap<>();

    if (requestDTO.getOverrideFetchRequest() == null || isEmpty(requestDTO.getOverrideFetchRequest().getEnvRef())) {
      return result;
    }

    String envBranch = requestDTO.getOverrideFetchRequest().getEnvBranch();
    Optional<Environment> environment = fetchEnvironment(requestDTO.getOverrideFetchRequest(), accountId, orgIdentifier,
        projectIdentifier, useScopeInfo ? scopeInfo : null, envBranch);

    if (environment.isEmpty()) {
      return result;
    }

    // Fetch overrides only if environment is present
    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrides =
        fetchMergedOverrideYaml(requestDTO.getOverrideFetchRequest(), accountId, orgIdentifier, projectIdentifier,
            serviceIdentifier, environment.get());

    if (mergedOverrides != null && !mergedOverrides.isEmpty()) {
      result.put(NGOutcomes.OVERRIDES.getName(), YamlUtils.writeYamlString(mergedOverrides));
    }

    // Create environment outcome
    EnvironmentOutcome environmentOutcome = createEnvironmentOutcome(environment.get(), useScopeInfo, scopeInfo,
        mergedOverrides != null ? mergedOverrides : new EnumMap<>(ServiceOverridesType.class));
    result.put(NGOutcomes.ENVIRONMENT.getName(), YamlUtils.writeYamlString(environmentOutcome));

    return result;
  }

  /**
   * Builds service entity metadata from service entity.
   *
   * @param serviceEntity The service entity
   * @return NG service entity metadata
   */
  public NGServiceEntityMetadata buildServiceEntityMetadata(ServiceEntity serviceEntity) {
    return NGServiceEntityMetadata.builder()
        .identifier(serviceEntity.getIdentifier())
        .description(serviceEntity.getDescription())
        .name(serviceEntity.getName())
        .tags(convertToMap(serviceEntity.getTags()))
        .build();
  }

  /**
   * Builds the NG service properties response.
   *
   * @param mergedNgServiceYaml The merged NG service YAML
   * @param overrideAndEnvironmentMap Map containing override and environment YAML strings
   * @param ngServiceEntityMetadata The service entity metadata
   * @param serviceStepOutcome The service step outcome
   * @return Response DTO containing NG service properties
   */
  public ResponseDTO<NgServicePropertiesResponse> buildNgServicePropertiesResponse(String mergedNgServiceYaml,
      Map<String, String> overrideAndEnvironmentMap, NGServiceEntityMetadata ngServiceEntityMetadata,
      ServiceStepOutcome serviceStepOutcome) {
    Map<String, String> ngOutcomes = new HashMap<>();
    ngOutcomes.put(NGOutcomes.SERVICE.getName(), YamlUtils.writeYamlString(serviceStepOutcome));

    // Add environment outcome if present
    if (overrideAndEnvironmentMap != null && overrideAndEnvironmentMap.containsKey(NGOutcomes.ENVIRONMENT.getName())) {
      ngOutcomes.put(NGOutcomes.ENVIRONMENT.getName(), overrideAndEnvironmentMap.get(NGOutcomes.ENVIRONMENT.getName()));
    }

    String mergedOverrideYaml = null;
    if (overrideAndEnvironmentMap != null && overrideAndEnvironmentMap.containsKey(NGOutcomes.OVERRIDES.getName())) {
      mergedOverrideYaml = overrideAndEnvironmentMap.get(NGOutcomes.OVERRIDES.getName());
    }

    NgServicePropertiesResponseDTO responseDTO = NgServicePropertiesResponseDTO.builder()
                                                     .mergedV0ServiceYaml(mergedNgServiceYaml)
                                                     .mergedV0OverrideYaml(mergedOverrideYaml)
                                                     .ngServiceEntityMetadata(ngServiceEntityMetadata)
                                                     .ngOutcomes(ngOutcomes)
                                                     .build();

    NgServicePropertiesResponse response = NgServicePropertiesResponse.builder().responseDTO(responseDTO).build();

    return ResponseDTO.newResponse(response);
  }

  /**
   * Builds an error response for the unified service conversion so the failure detail is propagated back to
   * CI Manager instead of being lost across the network boundary.
   *
   * @param e The exception that occurred while converting the service
   * @param serviceRef The service reference
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @return Response DTO carrying the error details
   */
  public ResponseDTO<UnifiedServiceConverterResponse> buildUnifiedServiceErrorResponse(
      Exception e, String serviceRef, String orgIdentifier, String projectIdentifier) {
    String detailedMessage = String.format(
        "Failed to execute service [%s] in project [%s], in org [%s]", serviceRef, projectIdentifier, orgIdentifier);
    log.error(detailedMessage, e);

    NgManagerErrorResponseDTO error = buildNgManagerErrorResponse(e, detailedMessage);
    return ResponseDTO.newResponse(UnifiedServiceConverterResponse.builder().error(error).build());
  }

  /**
   * Builds an error response for the lightweight service-type resolution so the failure detail is propagated back to
   * CI Manager instead of being lost across the network boundary.
   *
   * @param e The exception that occurred while resolving the service type
   * @param serviceRef The service reference
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @return Response DTO carrying the error details
   */
  public ResponseDTO<UnifiedServiceTypeResponse> buildUnifiedServiceTypeErrorResponse(
      Exception e, String serviceRef, String orgIdentifier, String projectIdentifier) {
    String detailedMessage = String.format("Failed to resolve type for service [%s] in project [%s], in org [%s]",
        serviceRef, projectIdentifier, orgIdentifier);
    log.error(detailedMessage, e);

    NgManagerErrorResponseDTO error = buildNgManagerErrorResponse(e, detailedMessage);
    return ResponseDTO.newResponse(UnifiedServiceTypeResponse.builder().error(error).build());
  }

  /**
   * Builds an error response for the NG service properties fetch so the failure detail is propagated back to
   * CI Manager instead of being lost across the network boundary.
   *
   * @param e The exception that occurred while processing the NG entity
   * @param serviceRef The service reference
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @return Response DTO carrying the error details
   */
  public ResponseDTO<NgServicePropertiesResponse> buildNgServicePropertiesErrorResponse(
      Exception e, String serviceRef, String orgIdentifier, String projectIdentifier) {
    String detailedMessage = String.format("Failed to fetch service entity [%s] in project [%s], in org [%s]",
        serviceRef, projectIdentifier, orgIdentifier);
    log.error(detailedMessage, e);

    NgManagerErrorResponseDTO error = buildNgManagerErrorResponse(e, detailedMessage);
    return ResponseDTO.newResponse(NgServicePropertiesResponse.builder().error(error).build());
  }

  private NgManagerErrorResponseDTO buildNgManagerErrorResponse(Exception e, String contextMessage) {
    return NgManagerErrorResponseUtils.build(e, contextMessage);
  }

  // Private helper methods

  private String getMergedServiceYaml(String serviceIdentifier, String accountId, String orgIdentifier,
      String projectIdentifier, UnifiedServiceConverterRequestDTO requestDTO, ScopeInfo scopeInfo, boolean useScopeInfo)
      throws IOException {
    String mergedServiceYaml = null;
    Optional<ServiceEntity> serviceEntityOp = useScopeInfo
        ? serviceEntityService.get(scopeInfo, serviceIdentifier, false)
        : serviceEntityService.get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);
    if (serviceEntityOp.isEmpty()) {
      return mergedServiceYaml;
    }

    ServiceEntity serviceEntity = serviceEntityOp.get();
    mergedServiceYaml =
        ServiceInputMergeUtils.getMergedNgServiceYaml(requestDTO, serviceEntity, useScopeInfo ? scopeInfo : null);
    return mergedServiceYaml;
  }

  /**
   * Fetches environment entity from override fetch request.
   *
   * @param overrideFetchRequest The override fetch request
   * @param accountId The account identifier
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @param scopeInfo The scope information
   * @param envBranch The environment branch
   * @return Optional environment entity
   */
  private Optional<Environment> fetchEnvironment(OverrideFetchRequest overrideFetchRequest, String accountId,
      String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo, String envBranch) {
    if (overrideFetchRequest == null || isEmpty(overrideFetchRequest.getEnvRef())) {
      return Optional.empty();
    }

    try {
      // Parse envRef to get identifier
      IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(
          overrideFetchRequest.getEnvRef(), accountId, orgIdentifier, projectIdentifier);
      String envId = envIdentifierRef.getIdentifier();

      // Get Environment entity with git guard
      boolean useScopeInfo = scopeInfo != null;
      Optional<Environment> environment;
      try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(envBranch)) {
        environment = useScopeInfo ? environmentService.get(scopeInfo, envId, false)
                                   : environmentService.get(accountId, orgIdentifier, projectIdentifier, envId, false);
      }

      return environment;
    } catch (Exception e) {
      log.warn("Failed to fetch environment: {}", e.getMessage(), e);
      return Optional.empty();
    }
  }

  /**
   * Fetches merged override YAML if environment is present.
   *
   * @param overrideFetchRequest The override fetch request
   * @param accountId The account identifier
   * @param orgIdentifier The organization identifier
   * @param projectIdentifier The project identifier
   * @param serviceIdentifier The service identifier
   * @param environment The environment entity
   * @return EnumMap of override configurations
   */
  private EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> fetchMergedOverrideYaml(
      OverrideFetchRequest overrideFetchRequest, String accountId, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, Environment environment) {
    if (overrideFetchRequest == null || environment == null) {
      return null;
    }

    try {
      // Process override inputs
      Map<String, Object> serviceOverrideInputs = new HashMap<>();
      Map<String, Object> envInputs = new HashMap<>();

      if (isNotEmpty(overrideFetchRequest.getServiceOverridesInputsYaml())) {
        JsonNode jsonNode = YamlUtils.readAsJsonNode(overrideFetchRequest.getServiceOverridesInputsYaml());
        serviceOverrideInputs = JsonPipelineUtils.jsonNodeToMap(jsonNode);
      }

      if (isNotEmpty(overrideFetchRequest.getEnvGlobalOverridesInputsYaml())) {
        JsonNode jsonNode = YamlUtils.readAsJsonNode(overrideFetchRequest.getEnvGlobalOverridesInputsYaml());
        envInputs = JsonPipelineUtils.jsonNodeToMap(jsonNode);
      }

      // Prepare OverridesFetchRequestParams
      OverridesFetchRequestParams overridesParams =
          OverridesFetchRequestParams.builder()
              .accountId(accountId)
              .orgId(orgIdentifier)
              .projectId(projectIdentifier)
              .serviceRef(ParameterField.createValueField(serviceIdentifier))
              .envRef(ParameterField.createValueField(overrideFetchRequest.getEnvRef()))
              .infraId(ParameterField.createValueField(overrideFetchRequest.getInfraId()))
              .envGitBranch(overrideFetchRequest.getEnvBranch())
              .svcGitBranch(overrideFetchRequest.getSvcBranch())
              .serviceOverrideInputs(ParameterField.<Map<String, Object>>builder().value(serviceOverrideInputs).build())
              .envInputs(ParameterField.<Map<String, Object>>builder().value(envInputs).build())
              .build();

      // Fetch merged overrides using V2 method
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs =
          serviceOverrideUtilityFacade.getMergedServiceOverrideConfigsV2(overridesParams);

      if (isEmpty(mergedOverrideV2Configs)) {
        return null;
      }

      return mergedOverrideV2Configs;

    } catch (Exception e) {
      log.warn("Failed to fetch merged override YAML: {}", e.getMessage(), e);
    }

    return null;
  }

  /**
   * Creates environment outcome from environment entity.
   *
   * @param environment The environment entity
   * @param useScopeInfo Whether to use scope info
   * @param scopeInfo The scope information
   * @param mergedOverrideV2Configs The merged override configurations
   * @return Environment outcome
   */
  private EnvironmentOutcome createEnvironmentOutcome(Environment environment, boolean useScopeInfo,
      ScopeInfo scopeInfo, EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs) {
    EntityGitDetails environmentGitDetails = gitAwareEntityHelper.getEntityGitDetails(environment);

    // assuming override v2 is globally enabled for unified
    boolean isOverridesV2Enabled = true;

    NGEnvironmentConfig ngEnvironmentConfig = toNgEnvironmentConfig(scopeInfo, environment, useScopeInfo);

    return useScopeInfo ? EnvironmentMapper.toEnvironmentOutcome(environment, ngEnvironmentConfig,
                              NGServiceOverrideConfig.builder().build(), null, mergedOverrideV2Configs,
                              isOverridesV2Enabled, scopeInfo, environmentGitDetails)

                        : EnvironmentMapper.toEnvironmentOutcome(environment, ngEnvironmentConfig,
                              NGServiceOverrideConfig.builder().build(), null, mergedOverrideV2Configs,
                              isOverridesV2Enabled, environmentGitDetails);
  }

  /**
   * Converts environment entity to NGEnvironmentConfig.
   *
   * @param scopeInfo The scope information
   * @param environment The environment entity
   * @param useScopeInfo Whether to use scope info
   * @return NGEnvironmentConfig
   */
  private static NGEnvironmentConfig toNgEnvironmentConfig(
      ScopeInfo scopeInfo, Environment environment, boolean useScopeInfo) {
    return NGEnvironmentConfig.builder()
        .ngEnvironmentInfoConfig(
            NGEnvironmentInfoConfig.builder()
                .name(environment.getName())
                .identifier(environment.getIdentifier())
                .orgIdentifier(useScopeInfo ? scopeInfo.getOrgIdentifier() : environment.getOrgIdentifier())
                .projectIdentifier(useScopeInfo ? scopeInfo.getProjectIdentifier() : environment.getProjectIdentifier())
                .description(environment.getDescription())
                .tags(convertToMap(environment.getTags()))
                .type(environment.getType())
                .build())
        .build();
  }

  private static final String PRIMARY = "primary";

  private List<ManifestConfigWrapper> filterManifestsForPrimaryManifestRef(
      ServiceSpec serviceSpec, List<ManifestConfigWrapper> svcManifests) {
    ManifestConfigurations manifestConfigurations = getManifestConfigurations(serviceSpec);
    if (manifestConfigurations != null && ParameterField.isNotNull(manifestConfigurations.getPrimaryManifestRef())) {
      return PrimaryManifestFilterUtils.filterManifestWrappersForPrimary(
          svcManifests, manifestConfigurations.getPrimaryManifestRef());
    }
    return svcManifests;
  }

  private static boolean tryPutPrimaryFromManifestConfigurations(
      ManifestsOutcome manifestsOutcome, ManifestConfigurations manifestConfigurations) {
    if (manifestConfigurations == null || !ParameterField.isNotNull(manifestConfigurations.getPrimaryManifestRef())) {
      return false;
    }
    ParameterField<String> primaryManifestRef = manifestConfigurations.getPrimaryManifestRef();
    if (primaryManifestRef.isExpression()) {
      return false;
    }
    String primaryId = primaryManifestRef.obtainValue();
    if (primaryId == null || primaryId.isBlank() || !manifestsOutcome.containsKey(primaryId)) {
      return false;
    }
    manifestsOutcome.put(PRIMARY, manifestsOutcome.get(primaryId));
    return true;
  }

  private ManifestsOutcome generateManifestOutcomes(List<ManifestConfigWrapper> svcManifests,
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs, ServiceSpec serviceSpec) {
    Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride = new HashMap<>();

    // Extract manifests from overrides (similar to ServiceStepOverrideHelper.getManifestsFromOverride)
    if (isNotEmpty(mergedOverrideV2Configs)) {
      mergedOverrideV2Configs.forEach((type, override) -> {
        if (isNotEmpty(override.getSpec().getManifests())) {
          manifestsFromOverride.put(type, emptyIfNull(override.getSpec().getManifests()));
        }
      });
    }

    // Aggregate manifests from service and overrides (similar to ManifestsStepV2.aggregateManifestsFromAllLocationsV2)
    List<ManifestConfigWrapper> allManifests = new ArrayList<>();
    if (isNotEmpty(svcManifests)) {
      allManifests.addAll(svcManifests);
    }
    if (isNotEmpty(manifestsFromOverride)) {
      for (ServiceOverridesType overridesType : ServiceStepConstants.OVERRIDE_IN_REVERSE_PRIORITY) {
        if (manifestsFromOverride.containsKey(overridesType) && isNotEmpty(manifestsFromOverride.get(overridesType))) {
          allManifests.addAll(manifestsFromOverride.get(overridesType));
        }
      }
    }

    if (isEmpty(allManifests)) {
      return null;
    }

    // Convert ManifestConfigWrapper to ManifestAttributes
    List<ManifestAttributes> manifestAttributes = allManifests.stream()
                                                      .map(ManifestConfigWrapper::getManifest)
                                                      .filter(Objects::nonNull)
                                                      .map(ManifestConfig::getSpec)
                                                      .filter(Objects::nonNull)
                                                      .toList();

    if (isEmpty(manifestAttributes)) {
      return null;
    }

    // Create manifest outcomes
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    for (int i = 0; i < manifestAttributes.size(); i++) {
      ManifestAttributes manifestAttribute = manifestAttributes.get(i);
      try {
        // Create manifest outcome without connector config (since we don't have ambiance/connector context)
        ManifestOutcome manifestOutcome = ManifestOutcomeMapper.toManifestOutcome(manifestAttribute, i);
        manifestsOutcome.put(manifestOutcome.getIdentifier(), manifestOutcome);
      } catch (Exception e) {
        log.warn("Failed to create manifest outcome for manifest: {}", manifestAttribute.getIdentifier(), e);
        // Continue with other manifests even if one fails
      }
    }

    // Handle primary manifest: use Kubernetes manifestConfigurations.primaryManifestRef when set (static),
    // else first manifest that supports single deploy path (V1-style).
    addPrimaryManifestOutcome(allManifests, manifestsOutcome, serviceSpec);

    return manifestsOutcome.isEmpty() ? null : manifestsOutcome;
  }

  /**
   * Populates the "primary" key in {@link ManifestsOutcome} so CI {@link
   * io.harness.ci.execution.common.ServiceStepOutcomeHelper} can merge manifest download vars. Prefer {@code
   * manifestConfigurations.primaryManifestRef} on Kubernetes or Native Helm when it resolves to a static id present in
   * outcomes; otherwise use the first manifest whose type supports a single deploy path. When the ref is an expression
   * no primary is written at all: guessing here would surface a manifest the user never selected, so the CI service
   * step fills it in from {@code manifestSourceCandidates} once the ref is resolved.
   */
  private void addPrimaryManifestOutcome(
      List<ManifestConfigWrapper> allManifests, ManifestsOutcome manifestsOutcome, ServiceSpec serviceSpec) {
    if (isEmpty(allManifests) || isEmpty(manifestsOutcome)) {
      return;
    }

    if (hasExpressionPrimaryManifestRef(serviceSpec)) {
      return;
    }

    ManifestConfigurations manifestConfigurations = getManifestConfigurations(serviceSpec);
    if (tryPutPrimaryFromManifestConfigurations(manifestsOutcome, manifestConfigurations)) {
      return;
    }

    Optional<ManifestConfigWrapper> primaryManifestOptional =
        allManifests.stream()
            .filter(wrapper -> wrapper.getManifest() != null && wrapper.getManifest().getSpec() != null)
            .filter(wrapper
                -> ManifestType.SINGLE_DEPLOY_PATH_SUPPORTED_MANIFEST_TYPES.contains(
                    wrapper.getManifest().getSpec().getKind()))
            .findFirst();

    primaryManifestOptional.ifPresent(wrapper -> {
      String identifier = wrapper.getManifest().getIdentifier();
      if (manifestsOutcome.containsKey(identifier)) {
        manifestsOutcome.put(PRIMARY, manifestsOutcome.get(identifier));
      }
    });
  }

  private ConfigFilesOutcome generateConfigFilesOutcomes(List<ConfigFileWrapper> svcConfigFiles,
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs) {
    // Extract config files from service
    Map<String, ConfigFileWrapper> finalConfigFiles = new HashMap<>();
    if (isNotEmpty(svcConfigFiles)) {
      for (ConfigFileWrapper configFileWrapper : svcConfigFiles) {
        if (configFileWrapper != null && configFileWrapper.getConfigFile() != null) {
          finalConfigFiles.put(configFileWrapper.getConfigFile().getIdentifier(), configFileWrapper);
        }
      }
    }

    Map<String, String> configFileLocation = new HashMap<>();
    if (isNotEmpty(mergedOverrideV2Configs)) {
      ServiceStepOverrideHelper.handleOverrideConfigFiles(
          mergedOverrideV2Configs, configFileLocation, finalConfigFiles);
    }

    if (isEmpty(finalConfigFiles)) {
      return null;
    }

    // Create config file outcomes
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    int order = 0;
    for (ConfigFileWrapper configFileWrapper : finalConfigFiles.values()) {
      if (configFileWrapper != null && configFileWrapper.getConfigFile() != null) {
        ConfigFile configFile = configFileWrapper.getConfigFile();
        try {
          ConfigFileAttributes configFileAttributes = configFile.getSpec();
          if (configFileAttributes != null) {
            ConfigFileOutcome configFileOutcome =
                ConfigFileOutcomeMapper.toConfigFileOutcome(configFile.getIdentifier(), order, configFileAttributes);
            configFilesOutcome.put(configFileOutcome.getIdentifier(), configFileOutcome);
            order++;
          }
        } catch (Exception e) {
          log.warn("Failed to create config file outcome for config file: {}", configFile.getIdentifier(), e);
          // Continue with other config files even if one fails
        }
      }
    }

    return configFilesOutcome.isEmpty() ? null : configFilesOutcome;
  }

  private ArtifactsOutcome getArtifactsOutcome(ServiceSpec serviceSpec, ScopeInfo scopeInfo) {
    final ArtifactsOutcomeBuilder outcomeBuilder = ArtifactsOutcome.builder();
    final SidecarsOutcome sidecarsOutcome = new SidecarsOutcome();
    if (serviceSpec.getArtifacts().getPrimary() != null && serviceSpec.getArtifacts().getPrimary().getSpec() != null) {
      ArtifactOutcome primaryArtifactOutcome = ArtifactResponseToOutcomeMapperV2.toArtifactOutcome(
          serviceSpec.getArtifacts().getPrimary().getSpec(), null, false, scopeInfo, deployableArtifactResolver);
      resolveHarDownloadUrl(primaryArtifactOutcome, scopeInfo);
      outcomeBuilder.primary(primaryArtifactOutcome);
    }

    // handle sidecar artifacts
    if (isNotEmpty(serviceSpec.getArtifacts().getSidecars())) {
      for (SidecarArtifactWrapper sidecar : serviceSpec.getArtifacts().getSidecars()) {
        if (sidecar.getSidecar().getSpec() != null) {
          ArtifactOutcome sidecarArtifactOutcome = ArtifactResponseToOutcomeMapperV2.toArtifactOutcome(
              sidecar.getSidecar().getSpec(), null, false, scopeInfo, deployableArtifactResolver);
          resolveHarDownloadUrl(sidecarArtifactOutcome, scopeInfo);
          sidecarsOutcome.put(sidecar.getSidecar().getSpec().getIdentifier(), sidecarArtifactOutcome);
        }
      }

      if (isNotEmpty(sidecarsOutcome)) {
        outcomeBuilder.sidecars(sidecarsOutcome);
      }
    }
    return outcomeBuilder.build();
  }

  private void resolveHarDownloadUrl(ArtifactOutcome artifactOutcome, ScopeInfo scopeInfo) {
    if (!(artifactOutcome instanceof HarArtifactOutcome harArtifactOutcome)) {
      return;
    }
    if (HarnessRegistryConstants.DOCKER.equals(harArtifactOutcome.getRegistryType())) {
      return;
    }
    try {
      harArtifactOutcome.setDownloadUrl(
          harnessArtifactRegistryHelper.resolveArtifactDownloadUrl(scopeInfo, harArtifactOutcome.getRegistryRef(),
              harArtifactOutcome.getArtifact(), harArtifactOutcome.getVersion(), harArtifactOutcome.getFileName()));
    } catch (Exception e) {
      log.warn("Failed to resolve HAR artifact download URL for registry: {}, artifact: {}",
          harArtifactOutcome.getRegistryRef(), harArtifactOutcome.getArtifact(), e);
    }
  }

  public ArtifactsProcessedResponse processArtifactsInYaml(
      Ambiance ambiance, String serviceEntityYaml, Boolean disableArtifactValidation) throws IOException {
    YamlField yamlField = YamlUtils.readTree(serviceEntityYaml);
    YamlField serviceDefField =
        yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY).getNode().getField(YamlTypes.SERVICE_DEFINITION);
    if (serviceDefField == null) {
      throw new InvalidRequestException(
          "Invalid Service being referred as serviceDefinition section is not there in Service");
    }

    YamlField serviceSpecField = serviceDefField.getNode().getField(YamlTypes.SERVICE_SPEC);
    if (serviceSpecField == null) {
      throw new InvalidRequestException(
          "Invalid Service being referred as spec inside serviceDefinition section is not there in Service");
    }

    YamlField artifactsField = serviceSpecField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    if (artifactsField == null) {
      return ArtifactsProcessedResponse.builder().serviceYaml(YamlUtils.writeYamlString(yamlField)).build();
    }

    YamlField primaryArtifactField = artifactsField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT);
    if (primaryArtifactField == null) {
      return ArtifactsProcessedResponse.builder().serviceYaml(YamlUtils.writeYamlString(yamlField)).build();
    }

    YamlField primaryArtifactRef = primaryArtifactField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT_REF);

    YamlField artifactSourcesField = primaryArtifactField.getNode().getField(YamlTypes.ARTIFACT_SOURCES);
    String primaryArtifactRefValue = null;

    if (artifactSourcesField != null && artifactSourcesField.getNode().isArray()) {
      ObjectNode artifactsNode = (ObjectNode) artifactsField.getNode().getCurrJsonNode();
      List<YamlNode> artifactSources = artifactSourcesField.getNode().asArray();

      ObjectNode primaryNode = null;
      // If there is only 1 artifact source, default to that
      if (artifactSources.size() == 1) {
        if (artifactSources.get(0).isObject()) {
          // primary artifact ref is by default chosen
          primaryArtifactRefValue = artifactSources.get(0).getIdentifier();

          primaryNode = (ObjectNode) artifactSources.get(0).getCurrJsonNode();
          primaryNode.remove(YamlTypes.IDENTIFIER);
        }
      } else {
        if (primaryArtifactRef == null) {
          if (Boolean.TRUE.equals(disableArtifactValidation)) {
            return ArtifactsProcessedResponse.builder().serviceYaml(YamlUtils.writeYamlString(yamlField)).build();
          } else {
            throw new InvalidRequestException("Primary artifact ref cannot be empty when multiple sources are present");
          }
        }
        primaryArtifactRefValue = primaryArtifactRef.getNode().asText();
        if (EmptyPredicate.isEmpty(primaryArtifactRefValue)) {
          if (Boolean.TRUE.equals(disableArtifactValidation)) {
            return ArtifactsProcessedResponse.builder().serviceYaml(YamlUtils.writeYamlString(yamlField)).build();
          } else {
            throw new InvalidRequestException("Primary artifact ref cannot be empty");
          }
        }

        if (ambiance != null) {
          primaryArtifactRefValue = resolvePrimaryArtifactRef(ambiance, primaryArtifactRefValue);
        }

        if (NGExpressionUtils.isRuntimeOrExpressionField(primaryArtifactRefValue)) {
          // Expression (or unfilled <+input>) primaryArtifactRef: pass the yaml through unchanged so the unified
          // converter keeps primaryArtifactRef + sources as is. Resolution happens later, in the service step,
          // once service-level context (e.g. serviceVariables) is available.
          return ArtifactsProcessedResponse.builder().serviceYaml(YamlUtils.writeYamlString(yamlField)).build();
        }
        for (YamlNode artifactSource : artifactSources) {
          String artifactSourceIdentifier = artifactSource.getIdentifier();
          if (primaryArtifactRefValue.equals(artifactSourceIdentifier) && artifactSource.isObject()) {
            primaryNode = (ObjectNode) artifactSource.getCurrJsonNode();
            primaryNode.remove(YamlTypes.IDENTIFIER);
            break;
          }
        }
      }

      if (primaryNode != null) {
        artifactsNode.set(YamlTypes.PRIMARY_ARTIFACT, primaryNode);
      } else {
        throw new InvalidRequestException(
            String.format("No artifact source exists with the identifier %s inside service", primaryArtifactRefValue));
      }
    }
    return ArtifactsProcessedResponse.builder()
        .serviceYaml(YamlUtils.writeYamlString(yamlField))
        .primaryArtifactRef(primaryArtifactRefValue)
        .build();
  }

  /**
   * A primary manifest only disambiguates between several charts. When a service has exactly one HelmChart there is
   * nothing to disambiguate, so an unresolved {@code primaryManifestRef} (an unfilled {@code <+input>} or an
   * expression that cannot be evaluated at conversion time) is rewritten to that chart's identifier. Downstream then
   * follows the ordinary static-ref path: the unified {@code primary} becomes a value field, {@code manifests.primary}
   * is populated from the id, and no candidates are emitted. Mirrors {@link #processArtifactsInYaml}.
   *
   * <p>Two or more charts are left untouched, so the ref stays an expression and the CI service step resolves it once
   * service-level context is available. Zero charts are also left untouched: such a service cannot select a primary in
   * any engine, and rewriting would hide that.
   */
  public String processManifestsInYaml(String serviceEntityYaml) throws IOException {
    YamlField yamlField = YamlUtils.readTree(serviceEntityYaml);

    YamlField serviceField = yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY);
    YamlField serviceDefField =
        serviceField == null ? null : serviceField.getNode().getField(YamlTypes.SERVICE_DEFINITION);
    YamlField serviceSpecField =
        serviceDefField == null ? null : serviceDefField.getNode().getField(YamlTypes.SERVICE_SPEC);
    if (serviceSpecField == null) {
      return serviceEntityYaml;
    }

    YamlField manifestConfigurationsField = serviceSpecField.getNode().getField(YamlTypes.MANIFEST_CONFIGURATIONS);
    YamlField primaryManifestRefField = manifestConfigurationsField == null
        ? null
        : manifestConfigurationsField.getNode().getField(YamlTypes.PRIMARY_MANIFEST_REF);
    if (primaryManifestRefField == null) {
      return serviceEntityYaml;
    }

    String primaryManifestRefValue = primaryManifestRefField.getNode().asText();
    if (!NGExpressionUtils.isRuntimeOrExpressionField(primaryManifestRefValue)) {
      return serviceEntityYaml;
    }

    String singleChartIdentifier = findSingleHelmChartIdentifier(serviceSpecField);
    if (singleChartIdentifier == null) {
      return serviceEntityYaml;
    }

    ObjectNode manifestConfigurationsNode = (ObjectNode) manifestConfigurationsField.getNode().getCurrJsonNode();
    manifestConfigurationsNode.put(YamlTypes.PRIMARY_MANIFEST_REF, singleChartIdentifier);
    log.warn(
        "primaryManifestRef [{}] could not be resolved, defaulting to the only [{}] manifest [{}] in the service spec",
        primaryManifestRefValue, ManifestType.HelmChart, singleChartIdentifier);

    return YamlUtils.writeYamlString(yamlField);
  }

  /**
   * Returns the identifier of the only {@link ManifestType#HelmChart} manifest in the service spec, or null when there
   * is not exactly one. HelmChart is the sole type {@code primaryManifestRef} can select among, matching
   * {@code ManifestType.MULTIPLE_SUPPORTED_MANIFEST_TYPES} on the v0 side and
   * {@code ManifestType.getPrimarySupportedManifestTypes()} on the unified side.
   */
  private String findSingleHelmChartIdentifier(YamlField serviceSpecField) {
    YamlField manifestsField = serviceSpecField.getNode().getField(YamlTypes.MANIFEST_LIST_CONFIG);
    if (manifestsField == null || !manifestsField.getNode().isArray()) {
      return null;
    }

    String singleChartIdentifier = null;
    for (YamlNode manifestEntry : manifestsField.getNode().asArray()) {
      YamlField manifestField = manifestEntry.getField(YamlTypes.MANIFEST_CONFIG);
      if (manifestField == null || !ManifestType.HelmChart.equals(manifestField.getNode().getType())) {
        continue;
      }
      if (singleChartIdentifier != null) {
        // More than one chart: the ref is load bearing, leave it for the CI service step to resolve.
        return null;
      }
      singleChartIdentifier = manifestField.getNode().getIdentifier();
    }

    return isEmpty(singleChartIdentifier) ? null : singleChartIdentifier;
  }

  private String resolvePrimaryArtifactRef(Ambiance ambiance, String primaryArtifactRefValue) {
    // handle primaryArtifactRef with input set validators nginx.allowedValues(nginx,http)
    final ParameterField<String> primaryArtifactRefParameterField =
        RuntimeInputValuesValidator.getInputSetParameterField(primaryArtifactRefValue);
    if (primaryArtifactRefParameterField != null) {
      if (primaryArtifactRefParameterField.isExpression()) {
        primaryArtifactRefValue = cdExpressionResolver.renderExpression(ambiance, primaryArtifactRefValue);
      } else {
        primaryArtifactRefValue = primaryArtifactRefParameterField.getValue();
      }
    }
    return primaryArtifactRefValue;
  }
}
