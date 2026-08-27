/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.registries;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.delegate.task.artifacts.source.ArtifactSourceType;
import io.harness.unified.cd.service.artifacts.ArtifactType;
import io.harness.unified.cd.service.manifests.ManifestType;
import io.harness.unified.cd.service.manifests.StoreType;

import com.google.inject.Singleton;
import java.util.Map;
import lombok.Getter;

/**
 * GLOBAL REGISTRY: Template Onboarding Registry
 *
 * <p>This is the SINGLE SOURCE OF TRUTH for all types onboarded to the new template-based approach.
 *
 * <p>TO ONBOARD A NEW TYPE:
 * <ol>
 *   <li>Add the type to the appropriate Set below (grouped by type: Service, Artifact, Manifest, Store)
 *   <li>Add template name mapping if needed (for Artifact or Manifest+Store combinations)
 *   <li>Add combination mappings if needed (for Service+Artifact or Manifest+Store)
 *   <li>Update conversion methods if needed (getArtifactType, getUnifiedManifestType, getUnifiedStoreType)
 *   <li>Implement the mapper interface (ArtifactInputsMapper, ManifestInputsMapper, etc.)
 *   <li>Register the mapper in the appropriate mapper registry
 *   <li>Add the template.yaml file in the templates directory
 * </ol>
 *
 * <p>That's it! No other changes needed.
 */
@Singleton
@OwnedBy(HarnessTeam.CI)
public class TemplateOnboardingRegistry {
  @Getter
  private static final Map<String, String> ARTIFACT_TYPE_TO_TEMPLATE =
      Map.ofEntries(Map.entry(ArtifactSourceType.DOCKER_REGISTRY.getDisplayName(), "docker-registry"),
          Map.entry(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY.getDisplayName(), "google-artifact-registry"),
          Map.entry(ArtifactSourceType.ECR.getDisplayName(), "ecr"),
          Map.entry(ArtifactSourceType.AMAZONS3.getDisplayName(), "s3"),
          Map.entry(ArtifactSourceType.ARTIFACTORY_REGISTRY.getDisplayName(), "artifactory"),
          Map.entry(ArtifactSourceType.ACR.getDisplayName(), "acr"),
          Map.entry(ArtifactSourceType.NEXUS3_REGISTRY.getDisplayName(), "nexus3"),
          Map.entry(ArtifactSourceType.GITHUB_PACKAGES.getDisplayName(), "github-package"),
          Map.entry(ArtifactSourceType.AMI.getDisplayName(), "ami"),
          Map.entry(ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT.getDisplayName(), "gcs"),
          Map.entry(ArtifactSourceType.CUSTOM_ARTIFACT.getDisplayName(), "custom"),
          Map.entry(ArtifactSourceType.HARNESS_ARTIFACT_REGISTRY.getDisplayName(), "har"));

  /**
   * Get template name for artifact type
   */
  public String getArtifactTemplateName(String artifactType) {
    return ARTIFACT_TYPE_TO_TEMPLATE.get(artifactType);
  }

  /**
   * Map NG ArtifactSourceType to Unified ArtifactType.
   * Add new cases here when onboarding new artifact types.
   */
  public ArtifactType getArtifactType(ArtifactSourceType sourceType) {
    switch (sourceType) {
      case DOCKER_REGISTRY:
        return ArtifactType.DOCKER_REGISTRY;
      case GOOGLE_ARTIFACT_REGISTRY:
        return ArtifactType.GOOGLE_ARTIFACT_REGISTRY;
      case ECR:
        return ArtifactType.ECR;
      case AMAZONS3:
        return ArtifactType.S3;
      case ARTIFACTORY_REGISTRY:
        return ArtifactType.ARTIFACTORY;
      case ACR:
        return ArtifactType.ACR;
      case NEXUS3_REGISTRY:
        return ArtifactType.NEXUS3;
      case GITHUB_PACKAGES:
        return ArtifactType.GITHUB_PACKAGE;
      case AMI:
        return ArtifactType.AMI;
      case GOOGLE_CLOUD_STORAGE_ARTIFACT:
        return ArtifactType.GCS;
      case CUSTOM_ARTIFACT:
        return ArtifactType.CUSTOM;
      case HARNESS_ARTIFACT_REGISTRY:
        return ArtifactType.HAR;
      default:
        return null;
    }
  }

  /**
   * Get unified ManifestType from v0 manifest type.
   * Mappings align with UnifiedManifestMapper#toUnifiedServiceManifest.
   */
  public ManifestType getUnifiedManifestType(ManifestConfigType v0ManifestType) {
    return switch (v0ManifestType) {
      case K8_MANIFEST -> ManifestType.K8S;
      case HELM_CHART -> ManifestType.HELM_CHART;
      case VALUES -> ManifestType.VALUES;
      case AWS_SAM_DIRECTORY -> ManifestType.AWS_SAM;
      case SERVERLESS_AWS_LAMBDA -> ManifestType.SERVERLESS;
      case OPEN_SHIFT_TEMPLATE -> ManifestType.OPENSHIFT;
      case OPEN_SHIFT_PARAM -> ManifestType.PARAMS;
      case KUSTOMIZE -> ManifestType.KUSTOMIZE;
      case KUSTOMIZE_PATCHES -> ManifestType.PATCHES;
      case HELM_REPO_OVERRIDE -> ManifestType.HELM_REPO_OVERRIDE;
      case GOOGLE_CLOUD_RUN_SERVICE -> ManifestType.GOOGLE_CLOUD_RUN;
      case ECS_TASK_DEFINITION -> ManifestType.ECS_TASK_DEFINITION;
      case ECS_SERVICE_DEFINITION -> ManifestType.ECS_SERVICE_DEFINITION;
      case ECS_SCALABLE_TARGET_DEFINITION -> ManifestType.ECS_SCALABLE_TARGET;
      case ECS_SCALING_POLICY_DEFINITION -> ManifestType.ECS_SCALING_POLICY;
      default -> null;
    };
  }

  /**
   * Map NG StoreConfigType (cdng) to Unified StoreType.
   * Mappings align with UnifiedStoreMapper#toStoreConfig.
   */
  public static StoreType getUnifiedStoreType(StoreConfigType v0StoreConfigType) {
    if (v0StoreConfigType == null) {
      return null;
    }
    return switch (v0StoreConfigType) {
      case GIT -> StoreType.GIT;
      case GITHUB -> StoreType.GITHUB;
      case GITLAB -> StoreType.GITLAB;
      case BITBUCKET -> StoreType.BITBUCKET;
      case HTTP -> StoreType.HTTP;
      case S3 -> StoreType.S3;
      case GCS -> StoreType.GCS;
      case HARNESS -> StoreType.HARNESS;
      case OCI -> StoreType.OCI_GENERIC;
      case AZURE_REPO -> StoreType.AZURE;
      case HARNESS_CODE -> StoreType.CODE;
      case CUSTOM_REMOTE -> StoreType.CUSTOM;
      case InheritFromManifest -> StoreType.INHERIT;
      case INLINE, ARTIFACTORY, ARTIFACT_BUNDLE -> null;
      default -> null;
    };
  }


  /**
   * Only combinations that resolve to templates present in templates/manifests and commonTemplates.
   */
  @Getter
  private static final Map<String, String> MANIFEST_STORE_TO_TEMPLATE = Map.ofEntries(
      Map.entry(io.harness.cdng.manifest.ManifestType.K8Manifest + ":" + ManifestStoreType.GIT, "k8s-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.K8Manifest + ":" + ManifestStoreType.GITHUB, "k8s-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.K8Manifest + ":" + ManifestStoreType.GITLAB, "k8s-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.K8Manifest + ":" + ManifestStoreType.BITBUCKET, "k8s-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.K8Manifest + ":" + ManifestStoreType.HARNESS_CODE, "k8s-code"),
      Map.entry(io.harness.cdng.manifest.ManifestType.K8Manifest + ":" + ManifestStoreType.CUSTOM_REMOTE, "k8s-custom"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.GIT, "helm-chart-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.GITHUB, "helm-chart-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.GITLAB, "helm-chart-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.BITBUCKET, "helm-chart-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.HARNESS_CODE, "helm-chart-code"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.CUSTOM_REMOTE, "helm-chart-custom"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.GCS, "helm-chart-gcs"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.OCI, "helm-chart-oci"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.S3, "helm-chart-s3"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.HTTP, "helm-chart-http"),
      Map.entry(io.harness.cdng.manifest.ManifestType.HelmChart + ":" + ManifestStoreType.AZURE_REPO, "helm-chart-azure-repo"),
      Map.entry(io.harness.cdng.manifest.ManifestType.VALUES + ":" + ManifestStoreType.GIT, "values-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.VALUES + ":" + ManifestStoreType.GITHUB, "values-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.VALUES + ":" + ManifestStoreType.GITLAB, "values-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.VALUES + ":" + ManifestStoreType.BITBUCKET, "values-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.VALUES + ":" + ManifestStoreType.HARNESS_CODE, "values-code"),
      Map.entry(io.harness.cdng.manifest.ManifestType.VALUES + ":" + ManifestStoreType.CUSTOM_REMOTE, "values-custom"),
      Map.entry(io.harness.cdng.manifest.ManifestType.VALUES + ":" + ManifestStoreType.S3, "values-s3"),
      Map.entry(io.harness.cdng.manifest.ManifestType.ServerlessAwsLambda + ":" + ManifestStoreType.GIT, "serverless-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.ServerlessAwsLambda + ":" + ManifestStoreType.GITHUB, "serverless-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.ServerlessAwsLambda + ":" + ManifestStoreType.GITLAB, "serverless-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.ServerlessAwsLambda + ":" + ManifestStoreType.BITBUCKET, "serverless-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.ServerlessAwsLambda + ":" + ManifestStoreType.HARNESS_CODE, "serverless-code"),
      Map.entry(io.harness.cdng.manifest.ManifestType.ServerlessAwsLambda + ":" + ManifestStoreType.CUSTOM_REMOTE, "serverless-custom"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsSamDirectory + ":" + ManifestStoreType.GIT, "aws-sam-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsSamDirectory + ":" + ManifestStoreType.GITHUB, "aws-sam-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsSamDirectory + ":" + ManifestStoreType.GITLAB, "aws-sam-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsSamDirectory + ":" + ManifestStoreType.BITBUCKET, "aws-sam-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsSamDirectory + ":" + ManifestStoreType.HARNESS_CODE, "aws-sam-code"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsSamDirectory + ":" + ManifestStoreType.CUSTOM_REMOTE, "aws-sam-custom"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsSamDirectory + ":" + ManifestStoreType.S3, "aws-sam-s3"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftTemplate + ":" + ManifestStoreType.GIT, "openshift-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftTemplate + ":" + ManifestStoreType.GITHUB, "openshift-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftTemplate + ":" + ManifestStoreType.GITLAB, "openshift-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftTemplate + ":" + ManifestStoreType.BITBUCKET, "openshift-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftTemplate + ":" + ManifestStoreType.HARNESS_CODE, "openshift-code"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftTemplate + ":" + ManifestStoreType.CUSTOM_REMOTE, "openshift-custom"),
      Map.entry(io.harness.cdng.manifest.ManifestType.Kustomize + ":" + ManifestStoreType.GIT, "kustomize-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.Kustomize + ":" + ManifestStoreType.GITHUB, "kustomize-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.Kustomize + ":" + ManifestStoreType.GITLAB, "kustomize-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.Kustomize + ":" + ManifestStoreType.BITBUCKET, "kustomize-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.Kustomize + ":" + ManifestStoreType.HARNESS_CODE, "kustomize-code"),
      Map.entry(io.harness.cdng.manifest.ManifestType.Kustomize + ":" + ManifestStoreType.CUSTOM_REMOTE, "kustomize-custom"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftParam + ":" + ManifestStoreType.GIT, "params-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftParam + ":" + ManifestStoreType.GITHUB, "params-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftParam + ":" + ManifestStoreType.GITLAB, "params-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftParam + ":" + ManifestStoreType.BITBUCKET, "params-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftParam + ":" + ManifestStoreType.HARNESS_CODE, "params-code"),
      Map.entry(io.harness.cdng.manifest.ManifestType.OpenshiftParam + ":" + ManifestStoreType.CUSTOM_REMOTE, "params-custom"),
      Map.entry(io.harness.cdng.manifest.ManifestType.KustomizePatches + ":" + ManifestStoreType.GIT, "patches-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.KustomizePatches + ":" + ManifestStoreType.GITHUB, "patches-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.KustomizePatches + ":" + ManifestStoreType.GITLAB, "patches-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.KustomizePatches + ":" + ManifestStoreType.BITBUCKET, "patches-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.KustomizePatches + ":" + ManifestStoreType.HARNESS_CODE, "patches-code"),
      Map.entry(io.harness.cdng.manifest.ManifestType.KustomizePatches + ":" + ManifestStoreType.CUSTOM_REMOTE, "patches-custom"),
      Map.entry(io.harness.cdng.manifest.ManifestType.GoogleCloudRunService + ":" + ManifestStoreType.GIT, "google-cloud-run-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.GoogleCloudRunService + ":" + ManifestStoreType.GITHUB, "google-cloud-run-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.GoogleCloudRunService + ":" + ManifestStoreType.GITLAB, "google-cloud-run-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.GoogleCloudRunService + ":" + ManifestStoreType.BITBUCKET, "google-cloud-run-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.GoogleCloudRunService + ":" + ManifestStoreType.HARNESS_CODE, "google-cloud-run-code"),
      Map.entry(io.harness.cdng.manifest.ManifestType.GoogleCloudRunService + ":" + ManifestStoreType.CUSTOM_REMOTE, "google-cloud-run-custom"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsLambdaFunctionDefinition + ":" + ManifestStoreType.GIT, "aws-lambda-function-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsLambdaFunctionDefinition + ":" + ManifestStoreType.GITHUB, "aws-lambda-function-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsLambdaFunctionDefinition + ":" + ManifestStoreType.GITLAB, "aws-lambda-function-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsLambdaFunctionDefinition + ":" + ManifestStoreType.BITBUCKET, "aws-lambda-function-bitbucket"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsLambdaFunctionAliasDefinition + ":" + ManifestStoreType.GIT, "aws-lambda-alias-git"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsLambdaFunctionAliasDefinition + ":" + ManifestStoreType.GITHUB, "aws-lambda-alias-github"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsLambdaFunctionAliasDefinition + ":" + ManifestStoreType.GITLAB, "aws-lambda-alias-gitlab"),
      Map.entry(io.harness.cdng.manifest.ManifestType.AwsLambdaFunctionAliasDefinition + ":" + ManifestStoreType.BITBUCKET, "aws-lambda-alias-bitbucket"));

  /**
   * Get template name for manifest + store combination
   */
  public String getManifestTemplateName(String manifestType, String storeType) {
    return MANIFEST_STORE_TO_TEMPLATE.get(manifestType + ":" + storeType);
  }

  // ============================================================================
  // CONFIG FILE STORE TEMPLATES (store-only; aligns with template-service config file validation)
  // ============================================================================

  /**
   * V0 store type display name (same as {@link StoreConfigType#getDisplayName()}) -> runner template id.
   */
  @Getter
  private static final Map<String, String> CONFIG_FILE_STORE_TO_TEMPLATE = Map.ofEntries(
      Map.entry(ManifestStoreType.GIT, "config-file-git"),
      Map.entry(ManifestStoreType.GITHUB, "config-file-github"),
      Map.entry(ManifestStoreType.GITLAB, "config-file-gitlab"),
      Map.entry(ManifestStoreType.BITBUCKET, "config-file-bitbucket"),
      Map.entry(ManifestStoreType.HARNESS_CODE, "config-file-code"),
      Map.entry(ManifestStoreType.HARNESS, "config-file-harness"));

  public String getConfigFileTemplateName(String storeTypeDisplayName) {
    return CONFIG_FILE_STORE_TO_TEMPLATE.get(storeTypeDisplayName);
  }
}
