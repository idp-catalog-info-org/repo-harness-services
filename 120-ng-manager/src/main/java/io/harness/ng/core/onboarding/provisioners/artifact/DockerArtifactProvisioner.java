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
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerRegistryProviderType;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.mapper.ArtifactProviderType;
import io.harness.ng.core.onboarding.provisioners.spec.ArtifactProvisioner;
import io.harness.ng.core.onboarding.support.OnboardingIdentifiers;
import io.harness.ng.core.onboarding.support.OnboardingProvisionContext;
import io.harness.ng.core.onboarding.support.OnboardingSecretCreation;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * DockerRegistry artifact source. Provisions a Docker Hub connector (username/password) backed by a single password
 * secret and emits a DockerRegistry artifact source pointed at it.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class DockerArtifactProvisioner implements ArtifactProvisioner {
  private static final String DOCKER_HUB_REGISTRY_URL = "https://index.docker.io/v2/";

  private final OnboardingSecretCreation secretCreation;

  @Inject
  public DockerArtifactProvisioner(OnboardingSecretCreation secretCreation) {
    this.secretCreation = secretCreation;
  }

  @Override
  public ArtifactProviderType type() {
    return ArtifactProviderType.DOCKER_REGISTRY;
  }

  @Override
  public boolean requiresConnector() {
    return ArtifactProviderType.DOCKER_REGISTRY.requiresConnector();
  }

  @Override
  public void validate(OnboardingContextDTO context) {
    // DockerRegistry has no provider-specific required-field validation beyond the shared checks.
  }

  @Override
  public ConnectorInfoDTO buildConnector(OnboardingProvisionContext provisionContext) {
    OnboardingContextDTO context = provisionContext.getRequest();
    String secretRef = secretCreation.upsertSecret(provisionContext.getScopeInfo(), provisionContext.getOrgIdentifier(),
        provisionContext.getProjectIdentifier(), context.getArtifactId() + "_credential", context.getArtifactPassword(),
        provisionContext.getCreatedSecrets());
    return buildDockerConnector(
        context, provisionContext.getOrgIdentifier(), provisionContext.getProjectIdentifier(), secretRef);
  }

  @Override
  public ArtifactSource buildArtifactSource(OnboardingContextDTO context, String connectorRef) {
    return ArtifactSourceSupport.dockerRegistrySource(context, connectorRef);
  }

  private ConnectorInfoDTO buildDockerConnector(
      OnboardingContextDTO context, String orgIdentifier, String projectIdentifier, String secretRef) {
    SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretRef);
    DockerConnectorDTO dockerConnector = DockerConnectorDTO.builder()
                                             .dockerRegistryUrl(DOCKER_HUB_REGISTRY_URL)
                                             .providerType(DockerRegistryProviderType.DOCKER_HUB)
                                             .auth(DockerAuthenticationDTO.builder()
                                                       .authType(DockerAuthType.USER_PASSWORD)
                                                       .credentials(DockerUserNamePasswordDTO.builder()
                                                                        .username(context.getArtifactUsername())
                                                                        .passwordRef(secretRefData)
                                                                        .build())
                                                       .build())
                                             .build();

    String identifier = OnboardingIdentifiers.sanitizeIdentifier(context.getArtifactId());
    return ConnectorInfoDTO.builder()
        .identifier(identifier)
        .name(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .connectorType(ConnectorType.DOCKER)
        .connectorConfig(dockerConnector)
        .build();
  }
}
