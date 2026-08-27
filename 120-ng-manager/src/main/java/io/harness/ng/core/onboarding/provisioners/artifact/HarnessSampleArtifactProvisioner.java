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
import io.harness.connector.ConnectorInfoDTO;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.mapper.ArtifactProviderType;
import io.harness.ng.core.onboarding.provisioners.spec.ArtifactProvisioner;
import io.harness.ng.core.onboarding.support.OnboardingIdentifiers;
import io.harness.ng.core.onboarding.support.OnboardingProvisionContext;

import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 * HarnessArtifactSample artifact source. A fully backend-defined DockerRegistry artifact: the caller sends only
 * artifact_type and onboarding seeds the id, image path and tag and reuses the built-in account-level Docker connector
 * ({@value #HARNESS_SAMPLE_CONNECTOR_REF}) instead of provisioning one. The emitted source is an ordinary
 * DockerRegistry source, so it flows through the same path as the Docker provider.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class HarnessSampleArtifactProvisioner implements ArtifactProvisioner {
  // HarnessArtifactSample reuses the built-in account-level Docker connector instead of provisioning one; the
  // image path and tag are fixed by the backend so the caller only sends artifact_type.
  private static final String HARNESS_SAMPLE_CONNECTOR_REF = "account.harnessImage";
  private static final String HARNESS_SAMPLE_IMAGE_PATH = "harness/nginx";
  private static final String HARNESS_SAMPLE_TAG = "safe";

  @Override
  public ArtifactProviderType type() {
    return ArtifactProviderType.HARNESS_ARTIFACT_SAMPLE;
  }

  @Override
  public boolean requiresConnector() {
    return ArtifactProviderType.HARNESS_ARTIFACT_SAMPLE.requiresConnector();
  }

  /**
   * HarnessArtifactSample lets the caller send only artifact_type: the backend fills in the artifact id, a default
   * image path ({@value #HARNESS_SAMPLE_IMAGE_PATH}) and tag ({@value #HARNESS_SAMPLE_TAG}) so the request flows
   * through the shared DockerRegistry artifact path unchanged. Only truly-omitted fields are seeded; any
   * caller-supplied artifact_id/imagePath/tag is respected (and keeps repeat calls idempotent).
   */
  @Override
  public void applyDefaults(OnboardingContextDTO context) {
    if (StringUtils.isBlank(context.getArtifactId())) {
      context.setArtifactId(OnboardingIdentifiers.generateIdentifier("onboarding_artifact"));
    }
    if (StringUtils.isBlank(context.getArtifactImagePath())) {
      context.setArtifactImagePath(HARNESS_SAMPLE_IMAGE_PATH);
    }
    if (StringUtils.isBlank(context.getArtifactTag())) {
      context.setArtifactTag(HARNESS_SAMPLE_TAG);
    }
  }

  @Override
  public String reusedConnectorRef() {
    return HARNESS_SAMPLE_CONNECTOR_REF;
  }

  @Override
  public void validate(OnboardingContextDTO context) {
    // HarnessArtifactSample is fully backend-defined; there is no provider-specific required-field validation.
  }

  @Override
  public ConnectorInfoDTO buildConnector(OnboardingProvisionContext context) {
    // HarnessArtifactSample reuses the built-in account-level Docker connector; no connector is provisioned.
    return null;
  }

  @Override
  public ArtifactSource buildArtifactSource(OnboardingContextDTO context, String connectorRef) {
    return ArtifactSourceSupport.dockerRegistrySource(context, connectorRef);
  }
}
