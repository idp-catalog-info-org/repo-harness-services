/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.provisioners.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactSource;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.delegate.task.artifacts.source.ArtifactSourceType;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.pms.yaml.ParameterField;

/**
 * Artifact-source helpers shared by the artifact provisioners: the {@code <+input>} runtime-input field and the
 * DockerRegistry source (used by both the Docker and HarnessArtifactSample sources). Extracted verbatim from the
 * former {@code OnboardingServiceYamlBuilder} so the emitted nodes are byte-identical.
 */
@OwnedBy(HarnessTeam.CDC)
public final class ArtifactSourceSupport {
  private static final String RUNTIME_INPUT = "<+input>";

  private ArtifactSourceSupport() {}

  /**
   * The DockerRegistry artifact source (Docker Hub), shared by the Docker and HarnessArtifactSample providers. The
   * tag is pinned when supplied and left as a runtime input otherwise.
   */
  public static ArtifactSource dockerRegistrySource(OnboardingContextDTO context, String connectorRef) {
    return ArtifactSource.builder()
        .identifier(context.getArtifactId())
        .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
        .spec(DockerHubArtifactConfig.builder()
                  .identifier(context.getArtifactId())
                  .connectorRef(ParameterField.createValueField(connectorRef))
                  .imagePath(ParameterField.createValueField(context.getArtifactImagePath()))
                  .tag(runtimeInputIfBlank(context.getArtifactTag()))
                  .build())
        .build();
  }

  /** A literal value field, or {@code <+input>} when the caller did not provide a value. */
  public static ParameterField<String> runtimeInputIfBlank(String value) {
    return (value == null || value.trim().isEmpty()) ? runtimeInput() : ParameterField.createValueField(value);
  }

  private static ParameterField<String> runtimeInput() {
    return ParameterField.createExpressionField(true, RUNTIME_INPUT, null, true);
  }
}
