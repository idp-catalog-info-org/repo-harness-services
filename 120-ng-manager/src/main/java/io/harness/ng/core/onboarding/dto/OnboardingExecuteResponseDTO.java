/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of an {@code /onboarding/execute} run: the identifiers of every resource created,
 * in creation order. This does not roll back on partial failure, so on error some of these
 * may already be populated for the resources that succeeded before the failure.
 */
@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "OnboardingExecuteResponse", description = "Identifiers of the resources created during onboarding")
public class OnboardingExecuteResponseDTO {
  @Schema(description = "Identifiers of the secrets created (credentials materialized from the context).")
  @JsonProperty("secret_identifiers")
  List<String> secretIdentifiers;

  @Schema(description = "Identifier of the manifest attached to the service. Populated whenever a manifest is "
          + "provisioned, including providers (e.g. Harness Code) that need no connector.")
  @JsonProperty("manifest_identifier")
  String manifestIdentifier;

  @Schema(description = "Identifier of the created manifest connector. Null for providers whose connection is "
          + "built-in (e.g. Harness Code) and therefore create no connector.")
  @JsonProperty("manifest_connector_identifier")
  String manifestConnectorIdentifier;

  @Schema(description = "Identifier of the artifact attached to the service. Populated whenever an artifact is "
          + "provisioned.")
  @JsonProperty("artifact_identifier")
  String artifactIdentifier;

  @Schema(description = "Identifier of the created Docker (artifact) connector.")
  @JsonProperty("artifact_connector_identifier")
  String artifactConnectorIdentifier;

  @Schema(description = "Identifier of the created service.")
  @JsonProperty("service_identifier")
  String serviceIdentifier;

  @Schema(description = "Identifier of the environment created (or reused) to hold the infrastructure. Populated "
          + "whenever an infrastructure is provisioned.")
  @JsonProperty("environment_identifier")
  String environmentIdentifier;

  @Schema(description = "Identifier of the created infrastructure definition. Populated whenever an infrastructure "
          + "is provisioned.")
  @JsonProperty("infrastructure_identifier")
  String infrastructureIdentifier;

  @Schema(description = "Identifier of the created Kubernetes cluster connector referenced by the infrastructure.")
  @JsonProperty("infra_connector_identifier")
  String infraConnectorIdentifier;

  @Schema(description = "Rendered pipeline YAML for the requested deployment strategy. Populated only for "
          + "strategy-based requests; the caller can display it and run the pipeline as-is.")
  @JsonProperty("pipeline_yaml")
  String pipelineYaml;
}
