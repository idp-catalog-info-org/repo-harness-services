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
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.task.artifacts.source.ArtifactSourceType;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.mapper.ArtifactProviderType;
import io.harness.ng.core.onboarding.provisioners.spec.ArtifactProvisioner;
import io.harness.ng.core.onboarding.support.OnboardingIdentifiers;
import io.harness.ng.core.onboarding.support.OnboardingProvisionContext;
import io.harness.ng.core.onboarding.support.OnboardingSecretCreation;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 * Artifactory artifact source. Provisions an Artifactory connector (username/password) backed by a single password
 * secret and emits an Artifactory (Docker) artifact source pointed at it. Onboarding always provisions Artifactory as
 * a Docker registry, so the repository format is fixed to docker and the image path maps to the artifact path.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class ArtifactoryArtifactProvisioner implements ArtifactProvisioner {
  // Onboarding provisions Artifactory as a Docker registry, so its artifact source always uses the docker format.
  private static final String ARTIFACTORY_DOCKER_REPOSITORY_FORMAT = "docker";

  private final OnboardingSecretCreation secretCreation;

  @Inject
  public ArtifactoryArtifactProvisioner(OnboardingSecretCreation secretCreation) {
    this.secretCreation = secretCreation;
  }

  @Override
  public ArtifactProviderType type() {
    return ArtifactProviderType.ARTIFACTORY;
  }

  @Override
  public boolean requiresConnector() {
    return ArtifactProviderType.ARTIFACTORY.requiresConnector();
  }

  @Override
  public void validate(OnboardingContextDTO context) {
    if (StringUtils.isBlank(context.getArtifactArtifactoryServerUrl())) {
      throw new InvalidRequestException("artifact_artifactoryServerUrl is required for an 'artifactory' artifact");
    }
    if (StringUtils.isBlank(context.getArtifactUsername())) {
      throw new InvalidRequestException("artifact_username is required for an 'artifactory' artifact");
    }
    if (StringUtils.isBlank(context.getArtifactPassword())) {
      throw new InvalidRequestException("artifact_password is required for an 'artifactory' artifact");
    }
    if (StringUtils.isBlank(context.getArtifactRepository())) {
      throw new InvalidRequestException("artifact_repository is required for an 'artifactory' artifact");
    }
    if (StringUtils.isBlank(context.getArtifactImagePath())) {
      throw new InvalidRequestException("artifact_imagePath is required for an 'artifactory' artifact");
    }
  }

  @Override
  public ConnectorInfoDTO buildConnector(OnboardingProvisionContext provisionContext) {
    OnboardingContextDTO context = provisionContext.getRequest();
    String secretRef = secretCreation.upsertSecret(provisionContext.getScopeInfo(), provisionContext.getOrgIdentifier(),
        provisionContext.getProjectIdentifier(), context.getArtifactId() + "_credential", context.getArtifactPassword(),
        provisionContext.getCreatedSecrets());
    return buildArtifactoryConnector(
        context, provisionContext.getOrgIdentifier(), provisionContext.getProjectIdentifier(), secretRef);
  }

  @Override
  public ArtifactSource buildArtifactSource(OnboardingContextDTO context, String connectorRef) {
    return ArtifactSource.builder()
        .identifier(context.getArtifactId())
        .sourceType(ArtifactSourceType.ARTIFACTORY_REGISTRY)
        .spec(buildArtifactoryArtifact(context, connectorRef))
        .build();
  }

  private ArtifactoryRegistryArtifactConfig buildArtifactoryArtifact(
      OnboardingContextDTO context, String connectorRef) {
    // Image path maps to the artifact path; the repository format is fixed to docker.
    return ArtifactoryRegistryArtifactConfig.builder()
        .identifier(context.getArtifactId())
        .connectorRef(ParameterField.createValueField(connectorRef))
        .repository(ParameterField.createValueField(context.getArtifactRepository()))
        .repositoryFormat(ParameterField.createValueField(ARTIFACTORY_DOCKER_REPOSITORY_FORMAT))
        .artifactPath(ParameterField.createValueField(context.getArtifactImagePath()))
        .tag(ArtifactSourceSupport.runtimeInputIfBlank(context.getArtifactTag()))
        .build();
  }

  private ConnectorInfoDTO buildArtifactoryConnector(
      OnboardingContextDTO context, String orgIdentifier, String projectIdentifier, String secretRef) {
    SecretRefData passwordRef = SecretRefHelper.createSecretRef(secretRef);
    ArtifactoryConnectorDTO artifactoryConnector =
        ArtifactoryConnectorDTO.builder()
            .artifactoryServerUrl(context.getArtifactArtifactoryServerUrl())
            .auth(ArtifactoryAuthenticationDTO.builder()
                      .authType(ArtifactoryAuthType.USER_PASSWORD)
                      .credentials(ArtifactoryUsernamePasswordAuthDTO.builder()
                                       .username(context.getArtifactUsername())
                                       .passwordRef(passwordRef)
                                       .build())
                      .build())
            .build();

    String identifier = OnboardingIdentifiers.sanitizeIdentifier(context.getArtifactId());
    return ConnectorInfoDTO.builder()
        .identifier(identifier)
        .name(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .connectorType(ConnectorType.ARTIFACTORY)
        .connectorConfig(artifactoryConnector)
        .build();
  }
}
