/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.AMAZON_S3_NAME;
import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.AMI_ARTIFACTS_NAME;
import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.AZURE_ARTIFACTS_NAME;
import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.DOCKER_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.GITHUB_PACKAGES_NAME;
import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.GOOGLE_ARTIFACT_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.NEXUS3_REGISTRY_NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cd.beans.outcomes.TokenBasedImagePullSecretRegistry;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_FIRST_GEN, HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
@Slf4j
@OwnedBy(CDP)
public class ImagePullSecretUtils {
  private static final String BASE64_ENCODE_FORMAT = "<{ %s | getAsBase64 }>";
  private static final String TOKEN_BASED_EXPRESSION =
      "{\"%s\":{\"username\":\"%s\",\"password\":\"${{secrets.%s}}\"}}";
  private static final String DOCKER_REGISTRY_EXPRESSION =
      "{\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).url}}\":{\"username\":\"${{"
      + "connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).username}}\",\"password\":\"${{connectorInputs"
      + ".get(${{stage.steps.artifacts.%s.connectorRef}}).password}}\"}}";
  private static final String GOOGLE_ARTIFACT_REGISTRY_EXPRESSION =
      "{\"%s\":{\"username\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).imagePullUsername}}"
      + "\", \"password\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).imagePullPassword}}\"}}";
  private static final String NEXUS_EXPRESSION =
      "{\"%s\":{\"username\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).username}}\","
      + "\"password\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).password}}\"}}";
  private static final String ARTIFACTORY_EXPRESSION =
      "{\"%s\":{\"username\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).username}}\","
      + "\"password\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).password}}\"}}";
  private static final String S3_EXPRESSION =
      "{\"${{stage.steps.artifacts.%s[\"registry-url\"]}}\":{\"username\":\"${{connectorInputs.get(${{stage.steps."
      + "artifacts.%s.connectorRef}}).accessKey}}\",\"password\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s."
      + "connectorRef}}).secretKey}}\"}}";
  private static final String GITHUB_PACKAGES_EXPRESSION = "{\"https://"
      + "ghcr.io\":{\"username\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).username}}\","
      + "\"password\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).token}}\"}}";
  private static final String AZURE_ARTIFACTS_EXPRESSION =
      "{\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).url}}\":{\"username\":null,\"password\":"
      + "\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).password}}\"}}";
  private static final String AMI_EXPRESSION =
      "{\"\":{\"username\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).accessKey}}\","
      + "\"password\":\"${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).secretKey}}\"}}";

  public String getImagePullSecretExpression(
      String artifactType, String artifactIdentifier, Map<String, Object> artifactOutcome) {
    Optional<TokenBasedImagePullSecretRegistry> tokenRegistry =
        TokenBasedImagePullSecretRegistry.forArtifactType(artifactType);
    if (tokenRegistry.isPresent()) {
      return formatTokenBasedExpression(tokenRegistry.get(), artifactIdentifier, artifactOutcome);
    }

    switch (artifactType) {
      case DOCKER_REGISTRY_NAME:
        return String.format(BASE64_ENCODE_FORMAT,
            String.format(DOCKER_REGISTRY_EXPRESSION, artifactIdentifier, artifactIdentifier, artifactIdentifier));
      case GOOGLE_ARTIFACT_REGISTRY_NAME:
        String repositoryUrlExpression;
        if (artifactOutcome.get("registry-hostname") != null) {
          repositoryUrlExpression = (String) artifactOutcome.get("registry-hostname");
        } else {
          repositoryUrlExpression = "${{stage.steps..artifacts.%s.image}}".formatted(artifactIdentifier);
        }
        return String.format(BASE64_ENCODE_FORMAT,
            String.format(
                GOOGLE_ARTIFACT_REGISTRY_EXPRESSION, repositoryUrlExpression, artifactIdentifier, artifactIdentifier));
      case NEXUS3_REGISTRY_NAME:
        String nexusUrlExpression;
        if (artifactOutcome.get("registry-hostname") != null) {
          nexusUrlExpression = (String) artifactOutcome.get("registry-hostname");
        } else {
          nexusUrlExpression = "${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).url}}".formatted(
              artifactIdentifier);
        }
        return String.format(BASE64_ENCODE_FORMAT,
            String.format(NEXUS_EXPRESSION, nexusUrlExpression, artifactIdentifier, artifactIdentifier));
      case ARTIFACTORY_REGISTRY_NAME:
        String artifactoryUrlExpression;
        if (artifactOutcome.get("registry-hostname") != null) {
          artifactoryUrlExpression = (String) artifactOutcome.get("registry-hostname");
        } else {
          artifactoryUrlExpression =
              "${{connectorInputs.get(${{stage.steps.artifacts.%s.connectorRef}}).url}}".formatted(
                  artifactIdentifier);
        }
        return String.format(BASE64_ENCODE_FORMAT,
            String.format(ARTIFACTORY_EXPRESSION, artifactoryUrlExpression, artifactIdentifier, artifactIdentifier));
      case AMAZON_S3_NAME:
        return String.format(BASE64_ENCODE_FORMAT,
            String.format(S3_EXPRESSION, artifactIdentifier, artifactIdentifier, artifactIdentifier));
      case GITHUB_PACKAGES_NAME:
        return String.format(
            BASE64_ENCODE_FORMAT, String.format(GITHUB_PACKAGES_EXPRESSION, artifactIdentifier, artifactIdentifier));
      case AZURE_ARTIFACTS_NAME:
        return String.format(
            BASE64_ENCODE_FORMAT, String.format(AZURE_ARTIFACTS_EXPRESSION, artifactIdentifier, artifactIdentifier));
      case AMI_ARTIFACTS_NAME:
        return String.format(
            BASE64_ENCODE_FORMAT, String.format(AMI_EXPRESSION, artifactIdentifier, artifactIdentifier));
      default:
        return null;
    }
  }

  private String formatTokenBasedExpression(
      TokenBasedImagePullSecretRegistry registry, String artifactIdentifier, Map<String, Object> artifactOutcome) {
    String registryUrlExpression = (String) artifactOutcome.get("registry-hostname");
    if (isEmpty(registryUrlExpression)) {
      registryUrlExpression = "${{stage.steps.artifacts.%s.registryUrl}}".formatted(artifactIdentifier);
    }
    return String.format(BASE64_ENCODE_FORMAT,
        String.format(TOKEN_BASED_EXPRESSION, registryUrlExpression, registry.getUsername(),
            TokenBasedImagePullSecretRegistry.imagePullSecretTaskId(artifactIdentifier)));
  }
}
