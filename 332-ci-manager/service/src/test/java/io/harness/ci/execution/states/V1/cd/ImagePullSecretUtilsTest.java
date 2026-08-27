/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.V1.cd;

import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.ACR_NAME;
import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.DOCKER_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.source.ArtifactSourceConstants.ECR_NAME;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.TokenBasedImagePullSecretRegistry;
import io.harness.ci.states.V1.cd.ImagePullSecretUtils;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ImagePullSecretUtilsTest {
  private final ImagePullSecretUtils utils = new ImagePullSecretUtils();

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void tokenBasedImagePullSecretRegistry_imagePullSecretTaskId_isDeterministicAndArtifactScoped() {
    assertThat(TokenBasedImagePullSecretRegistry.imagePullSecretTaskId("primary"))
        .isEqualTo("image_pull_secret__primary");
    assertThat(TokenBasedImagePullSecretRegistry.imagePullSecretTaskId("sidecar1"))
        .isEqualTo("image_pull_secret__sidecar1");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void tokenBasedImagePullSecretRegistry_forArtifactType_ecrIsRecognised() {
    Optional<TokenBasedImagePullSecretRegistry> registry = TokenBasedImagePullSecretRegistry.forArtifactType(ECR_NAME);
    assertThat(registry).isPresent();
    assertThat(registry.get().getUsername()).isEqualTo("AWS");
    assertThat(registry.get().getBinaryName()).isEqualTo("ecr-docker-auth-token");
    assertThat(registry.get().getEnvVarFields()).containsExactly(Map.entry("PLUGIN_REGION", "region"));
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void tokenBasedImagePullSecretRegistry_forArtifactType_acrIsRecognised() {
    Optional<TokenBasedImagePullSecretRegistry> registry = TokenBasedImagePullSecretRegistry.forArtifactType(ACR_NAME);
    assertThat(registry).isPresent();
    assertThat(registry.get().getUsername()).isEqualTo("00000000-0000-0000-0000-000000000000");
    assertThat(registry.get().getBinaryName()).isEqualTo("acr-docker-auth-token");
    assertThat(registry.get().getEnvVarFields()).containsExactly(Map.entry("PLUGIN_REGISTRY_URL", "registry-hostname"));
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void tokenBasedImagePullSecretRegistry_forArtifactType_dockerIsNotTokenBased() {
    assertThat(TokenBasedImagePullSecretRegistry.forArtifactType(DOCKER_REGISTRY_NAME)).isEmpty();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void tokenBasedImagePullSecretRegistry_secretPlaceholderPrefix_matchesIdPrefix() {
    assertThat(TokenBasedImagePullSecretRegistry.SECRET_PLACEHOLDER_PREFIX).isEqualTo("${{secrets.image_pull_secret__");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void getImagePullSecretExpression_ecrPrimary_withExplicitRegistryUrl_buildsBase64WrappedSecretPlaceholder() {
    Map<String, Object> artifact = new HashMap<>();
    artifact.put("type", ECR_NAME);
    artifact.put("registry-hostname", "123456789012.dkr.ecr.us-east-1.amazonaws.com");
    artifact.put("region", "us-east-1");
    artifact.put("connectorRef", "account.awsEcr");

    String expr = utils.getImagePullSecretExpression(ECR_NAME, "primary", artifact);

    assertThat(expr).isEqualTo("<{ {\"123456789012.dkr.ecr.us-east-1.amazonaws.com\":{\"username\":\"AWS\","
        + "\"password\":\"${{secrets.image_pull_secret__primary}}\"}} | getAsBase64 }>");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void getImagePullSecretExpression_ecrSidecar_usesSidecarScopedSecretId() {
    Map<String, Object> artifact = new HashMap<>();
    artifact.put("type", ECR_NAME);
    artifact.put("registryUrl", "111.dkr.ecr.eu-west-1.amazonaws.com");

    String expr = utils.getImagePullSecretExpression(ECR_NAME, "sidecar1", artifact);

    assertThat(expr).contains("${{secrets.image_pull_secret__sidecar1}}");
    assertThat(expr).startsWith("<{ ");
    assertThat(expr).endsWith(" | getAsBase64 }>");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void getImagePullSecretExpression_ecr_fallsBackToRegistryHostnameKey() {
    Map<String, Object> artifact = new HashMap<>();
    artifact.put("type", ECR_NAME);
    artifact.put("registry-hostname", "999.dkr.ecr.ap-south-1.amazonaws.com");

    String expr = utils.getImagePullSecretExpression(ECR_NAME, "primary", artifact);

    assertThat(expr).contains("999.dkr.ecr.ap-south-1.amazonaws.com");
    assertThat(expr).contains("${{secrets.image_pull_secret__primary}}");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void getImagePullSecretExpression_ecr_missingRegistryUrl_fallsBackToStageStepsExpression() {
    Map<String, Object> artifact = new HashMap<>();
    artifact.put("type", ECR_NAME);

    String expr = utils.getImagePullSecretExpression(ECR_NAME, "primary", artifact);

    assertThat(expr).contains("${{stage.steps.artifacts.primary.registryUrl}}");
    assertThat(expr).contains("${{secrets.image_pull_secret__primary}}");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void getImagePullSecretExpression_docker_unchanged() {
    String expr = utils.getImagePullSecretExpression(DOCKER_REGISTRY_NAME, "primary", new HashMap<>());

    assertThat(expr).startsWith("<{ ");
    assertThat(expr).contains("connectorInputs.get(");
    assertThat(expr).doesNotContain("image_pull_secret__");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void getImagePullSecretExpression_acrPrimary_withExplicitRegistryUrl_buildsBase64WrappedSecretPlaceholder() {
    Map<String, Object> artifact = new HashMap<>();
    artifact.put("type", ACR_NAME);
    artifact.put("registry-hostname", "myacr.azurecr.io");
    artifact.put("connectorRef", "account.azureConnector");

    String expr = utils.getImagePullSecretExpression(ACR_NAME, "primary", artifact);

    assertThat(expr).isEqualTo("<{ {\"myacr.azurecr.io\":{\"username\":\"00000000-0000-0000-0000-000000000000\","
        + "\"password\":\"${{secrets.image_pull_secret__primary}}\"}} | getAsBase64 }>");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void getImagePullSecretExpression_acrSidecar_usesSidecarScopedSecretId() {
    Map<String, Object> artifact = new HashMap<>();
    artifact.put("type", ACR_NAME);
    artifact.put("registry-hostname", "otheracr.azurecr.io");

    String expr = utils.getImagePullSecretExpression(ACR_NAME, "sidecar1", artifact);

    assertThat(expr).contains("${{secrets.image_pull_secret__sidecar1}}");
    assertThat(expr).startsWith("<{ ");
    assertThat(expr).endsWith(" | getAsBase64 }>");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void getImagePullSecretExpression_acr_missingRegistryUrl_fallsBackToStageStepsExpression() {
    Map<String, Object> artifact = new HashMap<>();
    artifact.put("type", ACR_NAME);

    String expr = utils.getImagePullSecretExpression(ACR_NAME, "primary", artifact);

    assertThat(expr).contains("${{stage.steps.artifacts.primary.registryUrl}}");
    assertThat(expr).contains("${{secrets.image_pull_secret__primary}}");
  }
}
