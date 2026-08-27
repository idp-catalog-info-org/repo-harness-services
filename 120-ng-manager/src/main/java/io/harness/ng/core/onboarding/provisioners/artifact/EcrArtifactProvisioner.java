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
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
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
 * ECR artifact source. Provisions an AWS connector using manual credentials — the access key is stored in plaintext
 * on the connector while the secret key (and an optional session token) are referenced as secrets — and emits an ECR
 * artifact source pointed at it.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class EcrArtifactProvisioner implements ArtifactProvisioner {
  private final OnboardingSecretCreation secretCreation;

  @Inject
  public EcrArtifactProvisioner(OnboardingSecretCreation secretCreation) {
    this.secretCreation = secretCreation;
  }

  @Override
  public ArtifactProviderType type() {
    return ArtifactProviderType.ECR;
  }

  @Override
  public boolean requiresConnector() {
    return ArtifactProviderType.ECR.requiresConnector();
  }

  @Override
  public void validate(OnboardingContextDTO context) {
    if (StringUtils.isBlank(context.getArtifactAccessKey())) {
      throw new InvalidRequestException("artifact_accessKey is required for an 'ecr' artifact");
    }
    if (StringUtils.isBlank(context.getArtifactSecretKey())) {
      throw new InvalidRequestException("artifact_secretKey is required for an 'ecr' artifact");
    }
    if (StringUtils.isBlank(context.getArtifactRegion())) {
      throw new InvalidRequestException("artifact_region is required for an 'ecr' artifact");
    }
    if (StringUtils.isBlank(context.getArtifactImagePath())) {
      throw new InvalidRequestException("artifact_imagePath is required for an 'ecr' artifact");
    }
  }

  @Override
  public ConnectorInfoDTO buildConnector(OnboardingProvisionContext provisionContext) {
    OnboardingContextDTO context = provisionContext.getRequest();
    String secretKeyRef = secretCreation.upsertSecret(provisionContext.getScopeInfo(),
        provisionContext.getOrgIdentifier(), provisionContext.getProjectIdentifier(),
        context.getArtifactId() + "_secretKey", context.getArtifactSecretKey(), provisionContext.getCreatedSecrets());
    // The session token is optional; upsertSecret returns null (and creates nothing) when it is absent.
    String sessionTokenRef =
        secretCreation.upsertSecret(provisionContext.getScopeInfo(), provisionContext.getOrgIdentifier(),
            provisionContext.getProjectIdentifier(), context.getArtifactId() + "_sessionToken",
            context.getArtifactSessionKey(), provisionContext.getCreatedSecrets());
    return buildAwsConnector(context, provisionContext.getOrgIdentifier(), provisionContext.getProjectIdentifier(),
        secretKeyRef, sessionTokenRef);
  }

  @Override
  public ArtifactSource buildArtifactSource(OnboardingContextDTO context, String connectorRef) {
    return ArtifactSource.builder()
        .identifier(context.getArtifactId())
        .sourceType(ArtifactSourceType.ECR)
        .spec(EcrArtifactConfig.builder()
                  .identifier(context.getArtifactId())
                  .connectorRef(ParameterField.createValueField(connectorRef))
                  .region(ParameterField.createValueField(context.getArtifactRegion()))
                  .imagePath(ParameterField.createValueField(context.getArtifactImagePath()))
                  .tag(ArtifactSourceSupport.runtimeInputIfBlank(context.getArtifactTag()))
                  .build())
        .build();
  }

  private ConnectorInfoDTO buildAwsConnector(OnboardingContextDTO context, String orgIdentifier,
      String projectIdentifier, String secretKeyRef, String sessionTokenRef) {
    // The session token is optional; leave its ref unset (null) when no session token was provided.
    SecretRefData sessionToken =
        StringUtils.isNotBlank(sessionTokenRef) ? SecretRefHelper.createSecretRef(sessionTokenRef) : null;
    AwsManualConfigSpecDTO manualConfig = AwsManualConfigSpecDTO.builder()
                                              .accessKey(context.getArtifactAccessKey())
                                              .secretKeyRef(SecretRefHelper.createSecretRef(secretKeyRef))
                                              .sessionTokenRef(sessionToken)
                                              .build();

    // Only manual credentials (access key + secret key) are supported today, so the credential type is fixed.
    AwsConnectorDTO awsConnector = AwsConnectorDTO.builder()
                                       .credential(AwsCredentialDTO.builder()
                                                       .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                                       .config(manualConfig)
                                                       .build())
                                       .build();

    String identifier = OnboardingIdentifiers.sanitizeIdentifier(context.getArtifactId());
    return ConnectorInfoDTO.builder()
        .identifier(identifier)
        .name(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .connectorType(ConnectorType.AWS)
        .connectorConfig(awsConnector)
        .build();
  }
}
