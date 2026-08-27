/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Declarative onboarding context. Describes the resources to provision (secrets, connectors, a
 * service) in a single request. Field values are intentionally kept as raw strings: they may be
 * slightly malformed or inconsistently cased and are normalized downstream before use. Unknown
 * fields are ignored so the payload can evolve without breaking older callers.
 */
@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "OnboardingContext", description = "Declarative context describing the resources to onboard")
public class OnboardingContextDTO {
  // ---- Pipeline generation ----
  @Schema(description = "Deployment strategy. When set, the call renders a ready-to-run pipeline for this strategy "
          + "(returned as pipelineYaml) instead of provisioning resources. Accepts 'rolling', 'canary' or "
          + "'bluegreen'.")
  @JsonProperty("strategy")
  OnboardingStrategy strategy;

  @Schema(description = "Deployment type for the generated pipeline. Required when 'strategy' is set; together with "
          + "'strategy' it selects the pipeline template. Accepts only 'kubernetes'.")
  @JsonProperty("deployment_type")
  OnboardingDeploymentType deploymentType;

  @Schema(
      description = "Environment identifier to reference in the generated pipeline. Required when 'strategy' is set.")
  @JsonProperty("environment_identifier")
  String pipelineEnvironmentIdentifier;

  @Schema(description = "Infrastructure identifier to reference in the generated pipeline. Required when 'strategy' is "
          + "set.")
  @JsonProperty("infrastructure_identifier")
  String pipelineInfrastructureIdentifier;

  @Schema(description = "Service deployment type. Supports 'kubernetes' only. Required when creating a "
          + "service; may be omitted when updating an existing one (service_id matches an existing service).")
  @JsonProperty("service_type")
  String serviceType;

  @Schema(description = "Service identifier. In provisioning requests: when it matches an existing service, that "
          + "service is UPDATED (the manifest and/or artifact sections in this request are merged in and all other "
          + "sections are preserved); when absent (create), an identifier is generated. In strategy-based requests: "
          + "the service identifier referenced in the generated pipeline (required).")
  @JsonProperty("service_identifier")
  String serviceId;

  @Schema(description = "Name to use for the created service.") @JsonProperty("service_name") String serviceName;

  // ---- Manifest + its connector ----
  @Schema(description = "Manifest store type. Supports 'github', 'bitbucket', 'gitlab' or 'harnessCode'.")
  @JsonProperty("manifest_type")
  OnboardingManifestType manifestType;

  @Schema(description = "Identifier for the created manifest connector (GitHub, Bitbucket or GitLab). "
          + "Optional: when absent, an identifier is generated on the backend for all manifest types.")
  @JsonProperty("manifest_id")
  String manifestId;

  @Schema(description = "Git manifest auth type. Accepts 'UsernameToken' or 'OAuth' (github), "
          + "'UsernamePassword' (bitbucket) and 'UsernameToken' or 'OAuth' (gitlab).")
  @JsonProperty("manifest_authType")
  OnboardingGitAuthType manifestAuthType;

  @Schema(description = "Username for the manifest connector (GitHub or Bitbucket).")
  @JsonProperty("manifest_username")
  String manifestUsername;

  @Schema(description = "Password/token value for the manifest connector; stored as a secret.")
  @JsonProperty("manifest_token")
  String manifestToken;

  @Schema(description = "Reference to an existing secret holding the OAuth access token (used when "
          + "manifest_authType is 'oauth' for github/gitlab). Bare id = project scope; prefix 'org.'/'account.' for "
          + "those scopes. Wired to both authentication and apiAccess; no new secret is created.")
  @JsonProperty("manifest_tokenRef")
  String manifestTokenRef;

  @Schema(description = "Reference to an existing secret holding the OAuth refresh token. Required for a gitlab "
          + "manifest with manifest_authType 'oauth'. Bare id = project scope; prefix 'org.'/'account.' for those "
          + "scopes. No new secret is created.")
  @JsonProperty("manifest_refreshTokenRef")
  String manifestRefreshTokenRef;

  @Schema(description = "Full repository URL of the manifest store (REPO-type connector).")
  @JsonProperty("manifest_repoUrl")
  String manifestRepoUrl;

  @Schema(description = "Git fetch type. Accepts only 'branch' or 'commit'.")
  @JsonProperty("manifest_fetchType")
  OnboardingGitFetchType manifestFetchType;

  @Schema(description = "Git branch to fetch the manifest from (used when manifest_fetchType is 'branch').")
  @JsonProperty("manifest_branch")
  String manifestBranch;

  @Schema(description = "Git commit id to fetch the manifest from (used when manifest_fetchType is 'commit').")
  @JsonProperty("manifest_commitId")
  String manifestCommitId;

  @Schema(description = "Repository name within the manifest store. Used by 'harnessCode' (the Harness Code repo) "
          + "and by account-level Git connectors.")
  @JsonProperty("manifest_repoName")
  String manifestRepoName;

  @Schema(description = "Manifest file path(s) / folder(s) within the repository. Accepts multiple, comma-separated "
          + "(e.g. 'k8s/deployment.yaml, k8s/service.yaml'); each is added to the manifest store.")
  @JsonProperty("manifest_paths")
  String manifestPaths;

  // ---- Artifact + its connector ----
  @Schema(
      description = "Artifact source type. Supports 'DockerRegistry', 'Ecr', 'Artifactory' or 'HarnessArtifactSample'.")
  @JsonProperty("artifact_type")
  OnboardingArtifactType artifactType;

  @Schema(description = "Identifier for the created artifact source and its connector (Docker, AWS or Artifactory). "
          + "Optional: when absent, an identifier is generated on the backend for all artifact types.")
  @JsonProperty("artifact_id")
  String artifactId;

  @Schema(description = "Username for the artifact connector (DockerRegistry or Artifactory).")
  @JsonProperty("artifact_username")
  String artifactUsername;

  @Schema(description = "Password/token value for the artifact connector (DockerRegistry or Artifactory); "
          + "stored as a secret.")
  @JsonProperty("artifact_password")
  String artifactPassword;

  @Schema(description = "AWS access key id for the ECR (aws) connector; stored in plaintext on the connector.")
  @JsonProperty("artifact_accessKey")
  String artifactAccessKey;

  @Schema(description = "AWS secret access key for the ECR (aws) connector; stored as a secret.")
  @JsonProperty("artifact_secretKey")
  String artifactSecretKey;

  @Schema(description = "Optional AWS session token for the ECR (aws) connector; when provided, stored as a secret.")
  @JsonProperty("artifact_sessionKey")
  String artifactSessionKey;

  @Schema(description = "AWS region for the ECR artifact source, e.g. 'us-east-1'.")
  @JsonProperty("artifact_region")
  String artifactRegion;

  @Schema(description = "Artifactory server URL for the artifactory connector, e.g. "
          + "'https://myorg.jfrog.io/artifactory' (artifactory only).")
  @JsonProperty("artifact_artifactoryServerUrl")
  String artifactArtifactoryServerUrl;

  @Schema(description = "Artifactory repository (a Docker repository) the image is pulled from (artifactory only).")
  @JsonProperty("artifact_repository")
  String artifactRepository;

  @Schema(description = "Docker/ECR/Artifactory image path, e.g. 'library/nginx'.")
  @JsonProperty("artifact_imagePath")
  String artifactImagePath;

  @Schema(description = "Image tag.") @JsonProperty("artifact_tag") String artifactTag;

  // ---- Environment (holds the infrastructure) ----
  @Schema(description = "Environment identifier. When it matches an existing environment, that environment is "
          + "reused and the infrastructure is attached to it. When absent, an identifier is generated.")
  @JsonProperty("env_id")
  String envId;

  @Schema(description = "Environment display name. Defaults to the environment identifier when absent.")
  @JsonProperty("env_name")
  String envName;

  @Schema(description = "Environment type. Supports 'PreProduction' (default) or 'Production'.")
  @JsonProperty("env_type")
  String envType;

  // ---- Infrastructure + its Kubernetes cluster connector ----
  @Schema(description = "Infrastructure type. Supports 'KubernetesDirect' only.")
  @JsonProperty("infra_type")
  String infraType;

  @Schema(description = "Infrastructure identifier. When absent, an identifier is generated.")
  @JsonProperty("infra_id")
  String infraId;

  @Schema(description = "Infrastructure display name. Defaults to the infrastructure identifier when absent.")
  @JsonProperty("infra_name")
  String infraName;

  @Schema(description = "Kubernetes namespace the infrastructure deploys into.")
  @JsonProperty("infra_namespace")
  String infraNamespace;

  @Schema(description = "Harness release name expression. When absent, a unique 'release-'-prefixed name is generated.")
  @JsonProperty("infra_releaseName")
  String infraReleaseName;

  @Schema(description = "Cluster connector type. Supports 'K8sCluster' only.")
  @JsonProperty("infra_connectorType")
  String infraConnectorType;

  @Schema(description = "Kubernetes cluster master URL for the connector.")
  @JsonProperty("infra_clusterUrl")
  String infraClusterUrl;

  @Schema(description = "Cluster credential type. Supports 'ManualConfig' only.")
  @JsonProperty("infra_credentialType")
  String infraCredentialType;

  @Schema(description = "Cluster auth type. Supports 'ServiceAccount' only.")
  @JsonProperty("infra_authType")
  String infraAuthType;

  @Schema(description = "Kubernetes service-account token value; stored as a secret and referenced by the connector.")
  @JsonProperty("infra_serviceAccountToken")
  String infraServiceAccountToken;
}
