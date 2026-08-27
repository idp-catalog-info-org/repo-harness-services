/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.provisioners.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.BitbucketStore.BitbucketStoreBuilder;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.dto.OnboardingGitAuthType;
import io.harness.ng.core.onboarding.mapper.ManifestProviderType;
import io.harness.ng.core.onboarding.mapper.OnboardingContextNormalizer;
import io.harness.ng.core.onboarding.provisioners.spec.ManifestProvisioner;
import io.harness.ng.core.onboarding.support.OnboardingIdentifiers;
import io.harness.ng.core.onboarding.support.OnboardingProvisionContext;
import io.harness.ng.core.onboarding.support.OnboardingSecretCreation;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 * Bitbucket manifest source. Needs a repo URL and username/password credentials (manifest_username plus
 * manifest_token, stored as a secret used as the password). Onboarding supports only the UsernamePassword auth type.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class BitbucketManifestProvisioner implements ManifestProvisioner {
  private final OnboardingSecretCreation secretCreation;

  @Inject
  public BitbucketManifestProvisioner(OnboardingSecretCreation secretCreation) {
    this.secretCreation = secretCreation;
  }

  @Override
  public ManifestProviderType type() {
    return ManifestProviderType.BITBUCKET;
  }

  @Override
  public boolean requiresConnector() {
    return ManifestProviderType.BITBUCKET.requiresConnector();
  }

  @Override
  public void validate(OnboardingContextDTO context) {
    if (context.getManifestAuthType() != null
        && context.getManifestAuthType() != OnboardingGitAuthType.USERNAME_PASSWORD) {
      throw new InvalidRequestException("manifest_authType must be 'UsernamePassword' for a bitbucket manifest");
    }
    if (StringUtils.isBlank(context.getManifestRepoUrl())) {
      throw new InvalidRequestException("manifest_repoUrl (full repository URL) is required for a bitbucket manifest");
    }
    if (StringUtils.isBlank(context.getManifestUsername())) {
      throw new InvalidRequestException("manifest_username is required for a bitbucket manifest");
    }
    if (StringUtils.isBlank(context.getManifestToken())) {
      throw new InvalidRequestException("manifest_token (password) is required for a bitbucket manifest");
    }
  }

  @Override
  public ConnectorInfoDTO buildConnector(OnboardingProvisionContext provisionContext) {
    OnboardingContextDTO context = provisionContext.getRequest();
    // Bitbucket uses username/password: manifest_token is materialized into a new secret used as password.
    String secretRef = secretCreation.upsertSecret(provisionContext.getScopeInfo(), provisionContext.getOrgIdentifier(),
        provisionContext.getProjectIdentifier(), context.getManifestId() + "_credential", context.getManifestToken(),
        provisionContext.getCreatedSecrets());
    return buildBitbucketConnector(
        context, provisionContext.getOrgIdentifier(), provisionContext.getProjectIdentifier(), secretRef);
  }

  @Override
  public ManifestConfigWrapper buildManifest(OnboardingContextDTO context, String connectorRef) {
    FetchType gitFetchType = OnboardingContextNormalizer.resolveGitFetchType(context.getManifestFetchType());
    BitbucketStoreBuilder storeBuilder =
        BitbucketStore.builder()
            .connectorRef(ParameterField.createValueField(connectorRef))
            .gitFetchType(gitFetchType)
            .paths(
                ParameterField.createValueField(ManifestStoreSupport.resolveManifestPaths(context.getManifestPaths())));
    if (StringUtils.isNotBlank(context.getManifestRepoName())) {
      storeBuilder.repoName(ParameterField.createValueField(context.getManifestRepoName()));
    }
    ManifestStoreSupport.applyGitFetchRef(context, gitFetchType, storeBuilder::branch, storeBuilder::commitId);
    StoreConfigWrapper store =
        StoreConfigWrapper.builder().type(StoreConfigType.BITBUCKET).spec(storeBuilder.build()).build();
    return ManifestStoreSupport.k8sManifest(context, store);
  }

  private ConnectorInfoDTO buildBitbucketConnector(
      OnboardingContextDTO context, String orgIdentifier, String projectIdentifier, String secretRef) {
    SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretRef);
    BitbucketHttpCredentialsDTO httpCredentials = BitbucketHttpCredentialsDTO.builder()
                                                      .type(BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                      .httpCredentialsSpec(BitbucketUsernamePasswordDTO.builder()
                                                                               .username(context.getManifestUsername())
                                                                               .passwordRef(secretRefData)
                                                                               .build())
                                                      .build();

    BitbucketConnectorDTO bitbucketConnector =
        BitbucketConnectorDTO.builder()
            .connectionType(GitConnectionType.REPO)
            .url(context.getManifestRepoUrl())
            .authentication(
                BitbucketAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(httpCredentials).build())
            .build();

    String identifier = OnboardingIdentifiers.sanitizeIdentifier(context.getManifestId());
    return ConnectorInfoDTO.builder()
        .identifier(identifier)
        .name(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .connectorType(ConnectorType.BITBUCKET)
        .connectorConfig(bitbucketConnector)
        .build();
  }
}
