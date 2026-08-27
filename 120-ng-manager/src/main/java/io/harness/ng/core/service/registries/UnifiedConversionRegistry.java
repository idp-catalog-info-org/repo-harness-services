/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.registries;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.oci.OciStoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.delegate.task.artifacts.source.ArtifactSourceType;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.unified.cd.infrastructure.InfraType;
import io.harness.unified.cd.service.artifacts.ArtifactType;
import io.harness.unified.cd.service.manifests.ManifestType;
import io.harness.unified.cd.service.manifests.StoreType;

import com.google.inject.Singleton;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * UNIFIED CONVERSION REGISTRY
 *
 * <p>SINGLE SOURCE OF TRUTH for converting NG types to Unified types with template action names.
 *
 * <p><strong>TO ONBOARD A NEW TYPE (Minimized Steps):</strong>
 * <ol>
 *   <li><strong>Artifact:</strong> Add 1 entry to ARTIFACT_CONVERSIONS → create template.yaml</li>
 *   <li><strong>Manifest:</strong> Add 1 entry to MANIFEST_CONVERSIONS → create template.yaml (store combinations
 * auto-handled)</li> <li><strong>Store:</strong> Add 1 entry to STORE_ACTION_MAP → used by manifests & config files
 * automatically</li> <li><strong>Infrastructure:</strong> Add 1 entry to INFRA_CONVERSIONS → create template.yaml</li>
 *   <li>Done! Mappers automatically pick up the new type.</li>
 * </ol>
 *
 * <p><strong>Key Optimizations:</strong>
 * <ul>
 *   <li>Manifest templates use pattern: {manifest-action}-{store-action} (e.g., "k8s-git")</li>
 *   <li>Config file templates use pattern: config-file-{store-action} (e.g., "config-file-git")</li>
 *   <li>Store actions defined once in STORE_ACTION_MAP, reused across manifest & config files</li>
 *   <li>No need to define every manifest+store combination manually</li>
 * </ul>
 *
 * <p>This registry consolidates:
 * - TemplateOnboardingRegistry (type conversions)
 * - TemplateServiceRegistry (template name lookups)
 * - Infrastructure conversion maps
 * All in one place with a unified pattern.
 */
@Singleton
@OwnedBy(HarnessTeam.CI)
public class UnifiedConversionRegistry {
  /**
   * Conversion result containing unified type and template action name.
   * @param <T> The unified type (ArtifactType, ManifestType, InfraType, etc.)
   */
  @Value
  public static class ConversionResult<T> {
    T unifiedType;
    String templateAction;

    public static <T> ConversionResult<T> of(T unifiedType, String templateAction) {
      return new ConversionResult<>(unifiedType, templateAction);
    }
  }

  // ============================================================================
  // ARTIFACT CONVERSIONS
  // ============================================================================

  /**
   * Single map for artifact conversions: NG ArtifactSourceType → (Unified ArtifactType, Template Name)
   * Template name follows pattern: "{artifact-type-name}" (e.g., "docker-registry", "ecr", "s3")
   */
  private static final Map<ArtifactSourceType, ConversionResult<ArtifactType>> ARTIFACT_CONVERSIONS = Map.ofEntries(
      Map.entry(
          ArtifactSourceType.DOCKER_REGISTRY, ConversionResult.of(ArtifactType.DOCKER_REGISTRY, "docker-registry")),
      Map.entry(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY,
          ConversionResult.of(ArtifactType.GOOGLE_ARTIFACT_REGISTRY, "google-artifact-registry")),
      Map.entry(ArtifactSourceType.ECR, ConversionResult.of(ArtifactType.ECR, "ecr")),
      Map.entry(ArtifactSourceType.AMAZONS3, ConversionResult.of(ArtifactType.S3, "s3")),
      Map.entry(ArtifactSourceType.ARTIFACTORY_REGISTRY, ConversionResult.of(ArtifactType.ARTIFACTORY, "artifactory")),
      Map.entry(ArtifactSourceType.ACR, ConversionResult.of(ArtifactType.ACR, "acr")),
      Map.entry(ArtifactSourceType.NEXUS3_REGISTRY, ConversionResult.of(ArtifactType.NEXUS3, "nexus3")),
      Map.entry(ArtifactSourceType.GITHUB_PACKAGES, ConversionResult.of(ArtifactType.GITHUB_PACKAGE, "github-package")),
      Map.entry(ArtifactSourceType.AMI, ConversionResult.of(ArtifactType.AMI, "ami")),
      Map.entry(ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT, ConversionResult.of(ArtifactType.GCS, "gcs")),
      Map.entry(ArtifactSourceType.CUSTOM_ARTIFACT, ConversionResult.of(ArtifactType.CUSTOM, "custom")),
      Map.entry(ArtifactSourceType.JENKINS, ConversionResult.of(ArtifactType.JENKINS, "jenkins")),
      Map.entry(ArtifactSourceType.HARNESS_ARTIFACT_REGISTRY,
          ConversionResult.of(ArtifactType.HAR, ArtifactType.NO_OP_ACTION)));

  /**
   * Convert artifact type from NG to Unified with template action.
   * @return ConversionResult with unified type and template name, or null if not supported
   */
  @Nullable
  public ConversionResult<ArtifactType> convertArtifact(ArtifactSourceType ngType) {
    return ARTIFACT_CONVERSIONS.get(ngType);
  }

  // ============================================================================
  // MANIFEST CONVERSIONS
  // ============================================================================

  /**
   * NG ManifestConfigType → Unified ManifestType and Manifest Action
   * Pattern: Each manifest type has its own action name used in template filenames
   */
  private static final Map<ManifestConfigType, ConversionResult<ManifestType>> MANIFEST_CONVERSIONS = Map.ofEntries(
      Map.entry(ManifestConfigType.K8_MANIFEST, ConversionResult.of(ManifestType.K8S, "k8s")),
      Map.entry(ManifestConfigType.HELM_CHART, ConversionResult.of(ManifestType.HELM_CHART, "helm-chart")),
      Map.entry(ManifestConfigType.VALUES, ConversionResult.of(ManifestType.VALUES, "values")),
      Map.entry(ManifestConfigType.AWS_SAM_DIRECTORY, ConversionResult.of(ManifestType.AWS_SAM, "aws-sam")),
      Map.entry(ManifestConfigType.SERVERLESS_AWS_LAMBDA, ConversionResult.of(ManifestType.SERVERLESS, "serverless")),
      Map.entry(ManifestConfigType.OPEN_SHIFT_TEMPLATE, ConversionResult.of(ManifestType.OPENSHIFT, "openshift")),
      Map.entry(ManifestConfigType.OPEN_SHIFT_PARAM, ConversionResult.of(ManifestType.PARAMS, "params")),
      Map.entry(ManifestConfigType.KUSTOMIZE, ConversionResult.of(ManifestType.KUSTOMIZE, "kustomize")),
      Map.entry(ManifestConfigType.KUSTOMIZE_PATCHES, ConversionResult.of(ManifestType.PATCHES, "patches")),
      Map.entry(ManifestConfigType.HELM_REPO_OVERRIDE,
          ConversionResult.of(ManifestType.HELM_REPO_OVERRIDE, "helm-repo-override")),
      Map.entry(ManifestConfigType.GOOGLE_CLOUD_RUN_SERVICE,
          ConversionResult.of(ManifestType.GOOGLE_CLOUD_RUN, "google-cloud-run")),
      Map.entry(ManifestConfigType.ECS_TASK_DEFINITION,
          ConversionResult.of(ManifestType.ECS_TASK_DEFINITION, "ecs-task-definition")),
      Map.entry(ManifestConfigType.ECS_SERVICE_DEFINITION,
          ConversionResult.of(ManifestType.ECS_SERVICE_DEFINITION, "ecs-service-definition")),
      Map.entry(ManifestConfigType.ECS_SCALABLE_TARGET_DEFINITION,
          ConversionResult.of(ManifestType.ECS_SCALABLE_TARGET, "ecs-scalable-target")),
      Map.entry(ManifestConfigType.ECS_SCALING_POLICY_DEFINITION,
          ConversionResult.of(ManifestType.ECS_SCALING_POLICY, "ecs-scaling-policy")),
      Map.entry(ManifestConfigType.ASG_LAUNCH_TEMPLATE,
          ConversionResult.of(ManifestType.ASG_LAUNCH_TEMPLATE, "asg-launch-template")),
      Map.entry(ManifestConfigType.ASG_CONFIGURATION,
          ConversionResult.of(ManifestType.ASG_CONFIGURATION, "asg-configuration")),
      Map.entry(
          ManifestConfigType.AWS_LAMBDA, ConversionResult.of(ManifestType.AWS_LAMBDA_FUNCTION, "aws-lambda-function")),
      Map.entry(
          ManifestConfigType.AWS_LAMBDA_ALIAS, ConversionResult.of(ManifestType.AWS_LAMBDA_ALIAS, "aws-lambda-alias")));

  /**
   * NG StoreConfigType → Store Action
   * Pattern: Each store type maps to its unified display name for template filenames
   * Uses unified StoreType display names for consistency
   */
  private static final Map<StoreConfigType, String> STORE_ACTION_MAP =
      Map.ofEntries(Map.entry(StoreConfigType.GIT, StoreType.GIT.getDisplayName()),
          Map.entry(StoreConfigType.GITHUB, StoreType.GITHUB.getDisplayName()),
          Map.entry(StoreConfigType.GITLAB, StoreType.GITLAB.getDisplayName()),
          Map.entry(StoreConfigType.BITBUCKET, StoreType.BITBUCKET.getDisplayName()),
          Map.entry(StoreConfigType.HARNESS_CODE, StoreType.CODE.getDisplayName()),
          Map.entry(StoreConfigType.CUSTOM_REMOTE, StoreType.CUSTOM.getDisplayName()),
          Map.entry(StoreConfigType.GCS, StoreType.GCS.getDisplayName()),
          Map.entry(StoreConfigType.OCI, StoreType.OCI_GENERIC.getDisplayName()),
          Map.entry(StoreConfigType.S3, StoreType.S3.getDisplayName()),
          Map.entry(StoreConfigType.HTTP, StoreType.HTTP.getDisplayName()),
          Map.entry(StoreConfigType.AZURE_REPO, StoreType.AZURE.getDisplayName()),
          Map.entry(StoreConfigType.HARNESS, StoreType.HARNESS.getDisplayName()));

  /**
   * Explicit action map for store types whose outer StoreConfigType is shared across multiple inner sub-type
   * implementations. Every known sub-type — including the "default" one — must be listed here.
   *
   * Key format: "{outerStoreDisplayName}:{innerSubType}"  e.g. "OciHelmChart:Generic", "OciHelmChart:ECR"
   * Value: the store action string used in the template filename (e.g. "oci", "ecr").
   *
   * <p>Design contract (enforced in {@link #getSubTypeStoreAction}):
   * <ul>
   *   <li>If {@code extractStoreSubType()} returns {@code null}, the store has no sub-types — use STORE_ACTION_MAP.
   *   <li>If {@code extractStoreSubType()} returns a non-null string, this map is the only lookup.
   *       A missing entry is treated as an unknown/unsupported sub-type, NOT a silent fallback to STORE_ACTION_MAP.
   * </ul>
   *
   * TO ADD A NEW SUB-TYPE: register ALL sub-types of that store here, including the default one.
   */
  static final Map<String, String> STORE_SUB_TYPE_ACTION_MAP = Map.of(
      StoreConfigType.OCI.getDisplayName() + ":" + OciStoreConfigType.GENERIC, StoreType.OCI_GENERIC.getDisplayName(),
      StoreConfigType.OCI.getDisplayName() + ":" + OciStoreConfigType.ECR, StoreType.ECR.getDisplayName(),
      StoreConfigType.OCI.getDisplayName() + ":" + OciStoreConfigType.GAR, StoreType.GAR.getDisplayName());

  /**
   * NG StoreConfigType → Unified StoreType
   */
  private static final Map<StoreConfigType, StoreType> STORE_TYPE_MAP = Map.ofEntries(
      Map.entry(StoreConfigType.GIT, StoreType.GIT), Map.entry(StoreConfigType.GITHUB, StoreType.GITHUB),
      Map.entry(StoreConfigType.GITLAB, StoreType.GITLAB), Map.entry(StoreConfigType.BITBUCKET, StoreType.BITBUCKET),
      Map.entry(StoreConfigType.HTTP, StoreType.HTTP), Map.entry(StoreConfigType.S3, StoreType.S3),
      Map.entry(StoreConfigType.GCS, StoreType.GCS), Map.entry(StoreConfigType.HARNESS, StoreType.HARNESS),
      Map.entry(StoreConfigType.OCI, StoreType.OCI_GENERIC), Map.entry(StoreConfigType.AZURE_REPO, StoreType.AZURE),
      Map.entry(StoreConfigType.HARNESS_CODE, StoreType.CODE),
      Map.entry(StoreConfigType.CUSTOM_REMOTE, StoreType.CUSTOM),
      Map.entry(StoreConfigType.InheritFromManifest, StoreType.INHERIT));

  /**
   * Convert manifest from NG to Unified with template action.
   * Template action pattern: "{manifest-action}-{store-action}" (e.g., "k8s-git", "helm-chart-github")
   *
   * @param ngManifestType NG manifest type
   * @param ngStoreType NG store config type
   * @return ConversionResult with unified manifest type and combined template action, or null if not supported
   */
  @Nullable
  public ConversionResult<ManifestType> convertManifest(
      ManifestConfigType ngManifestType, StoreConfigType ngStoreType) {
    ConversionResult<ManifestType> manifestResult = MANIFEST_CONVERSIONS.get(ngManifestType);
    if (manifestResult == null) {
      return null;
    }

    String storeAction = STORE_ACTION_MAP.get(ngStoreType);
    if (storeAction == null) {
      return null;
    }

    // Combine manifest action and store action: "k8s-git", "helm-chart-github", etc.
    String combinedTemplateAction = manifestResult.getTemplateAction() + "-" + storeAction;

    return ConversionResult.of(manifestResult.getUnifiedType(), combinedTemplateAction);
  }

  /**
   * Look up the store action for a store type that carries an inner sub-type discriminator.
   *
   * <p>Call this only when {@code extractStoreSubType()} returned a non-null value, meaning the store
   * is known to have sub-types. Every sub-type — including the "default" one — must be registered in
   * {@link #STORE_SUB_TYPE_ACTION_MAP}. An unregistered sub-type returns {@code null}, which the caller
   * should treat as an unsupported combination rather than silently falling back to STORE_ACTION_MAP.
   *
   * @param outerStoreType outer v0 StoreConfigType (e.g. StoreConfigType.OCI)
   * @param innerSubType   inner sub-type discriminator string (e.g. OciStoreConfigType.ECR = "ECR")
   * @return store action string, or null if the sub-type is not registered
   */
  @Nullable
  public String getSubTypeStoreAction(StoreConfigType outerStoreType, String innerSubType) {
    if (outerStoreType == null || innerSubType == null) {
      return null;
    }
    return STORE_SUB_TYPE_ACTION_MAP.get(outerStoreType.getDisplayName() + ":" + innerSubType);
  }

  /**
   * Convert manifest using an explicit store action string resolved from a sub-type override.
   * Called by the mapper after {@link #getSubTypeStoreAction} returns a non-null value.
   */
  @Nullable
  public ConversionResult<ManifestType> convertManifestWithAction(
      ManifestConfigType ngManifestType, String storeAction) {
    ConversionResult<ManifestType> manifestResult = MANIFEST_CONVERSIONS.get(ngManifestType);
    if (manifestResult == null) {
      return null;
    }
    return ConversionResult.of(manifestResult.getUnifiedType(), manifestResult.getTemplateAction() + "-" + storeAction);
  }

  /**
   * Convert store type from NG to Unified (used for manifests and config files).
   */
  @Nullable
  public StoreType convertStoreType(StoreConfigType ngStoreType) {
    return STORE_TYPE_MAP.get(ngStoreType);
  }

  // ============================================================================
  // CONFIG FILE CONVERSIONS
  // ============================================================================

  /**
   * Convert config file store type to template action.
   * Pattern: "config-file-{store-action}" (e.g., "config-file-git", "config-file-harness")
   * Reuses STORE_ACTION_MAP for consistency.
   *
   * @param ngStoreType NG store config type
   * @return Template action name, or null if not supported
   */
  @Nullable
  public String convertConfigFileStore(StoreConfigType ngStoreType) {
    String storeAction = STORE_ACTION_MAP.get(ngStoreType);
    if (storeAction == null) {
      return null;
    }
    return "config-file-" + storeAction;
  }

  // ============================================================================
  // INFRASTRUCTURE CONVERSIONS
  // ============================================================================

  /**
   * Single map for infrastructure conversions: NG InfrastructureKind → (Unified InfraType, Template Action)
   * Template action is the InfraType display name (or NO_OP_ACTION for no-task infra types)
   */
  private static final Map<String, ConversionResult<InfraType>> INFRA_CONVERSIONS = Map.ofEntries(
      Map.entry(InfrastructureKind.KUBERNETES_DIRECT,
          ConversionResult.of(InfraType.K8S_DIRECT, InfraType.K8S_DIRECT.getDisplayName())),
      Map.entry(InfrastructureKind.KUBERNETES_GCP,
          ConversionResult.of(InfraType.K8S_GCP, InfraType.K8S_GCP.getDisplayName())),
      Map.entry(InfrastructureKind.AWS_SAM, ConversionResult.of(InfraType.AWS_SAM, InfraType.NO_OP_ACTION)),
      Map.entry(
          InfrastructureKind.SERVERLESS_AWS_LAMBDA, ConversionResult.of(InfraType.SERVERLESS, InfraType.NO_OP_ACTION)),
      Map.entry(InfrastructureKind.KUBERNETES_AWS,
          ConversionResult.of(InfraType.K8S_AWS, InfraType.K8S_AWS.getDisplayName())),
      Map.entry(InfrastructureKind.KUBERNETES_AZURE,
          ConversionResult.of(InfraType.K8S_AZURE, InfraType.K8S_AZURE.getDisplayName())),
      Map.entry(InfrastructureKind.KUBERNETES_RANCHER,
          ConversionResult.of(InfraType.K8S_RANCHER, InfraType.K8S_RANCHER.getDisplayName())),
      Map.entry(
          InfrastructureKind.AZURE_FUNCTION, ConversionResult.of(InfraType.AZURE_FUNCTION, InfraType.NO_OP_ACTION)),
      Map.entry(InfrastructureKind.AZURE_WEB_APP, ConversionResult.of(InfraType.AZURE_WEB_APP, InfraType.NO_OP_ACTION)),
      Map.entry(InfrastructureKind.AWS_LAMBDA, ConversionResult.of(InfraType.AWS_LAMBDA, InfraType.NO_OP_ACTION)),
      Map.entry(InfrastructureKind.ECS, ConversionResult.of(InfraType.ECS, InfraType.NO_OP_ACTION)),
      Map.entry(InfrastructureKind.ASG, ConversionResult.of(InfraType.ASG, InfraType.NO_OP_ACTION)),
      Map.entry(InfrastructureKind.ELASTIGROUP, ConversionResult.of(InfraType.ELASTIGROUP, InfraType.NO_OP_ACTION)),
      Map.entry(
          InfrastructureKind.GOOGLE_CLOUD_RUN, ConversionResult.of(InfraType.GOOGLE_CLOUD_RUN, InfraType.NO_OP_ACTION)),
      Map.entry(
          InfrastructureKind.AWS_AGENT_CORE, ConversionResult.of(InfraType.AWS_AGENT_CORE, InfraType.NO_OP_ACTION)),
      Map.entry(InfrastructureKind.GOOGLE_AGENT_RUNTIME,
          ConversionResult.of(InfraType.GOOGLE_AGENT_RUNTIME, InfraType.NO_OP_ACTION)));

  /**
   * Convert infrastructure from NG to Unified with template action.
   * @param ngInfraKind NG infrastructure kind
   * @return ConversionResult with unified infra type and template action, or null if not supported
   */
  @Nullable
  public static ConversionResult<InfraType> convertInfrastructure(String ngInfraKind) {
    return INFRA_CONVERSIONS.get(ngInfraKind);
  }
}
