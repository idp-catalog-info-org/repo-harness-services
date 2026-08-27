/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.provisioners.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GitLabStore.GitLabStoreBuilder;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.dto.OnboardingGitAuthType;
import io.harness.ng.core.onboarding.mapper.GitAuthMode;
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
 * GitLab manifest source. Always needs a repo URL and supports two auth types: 'UsernameToken' (the default) requires
 * manifest_username plus manifest_token (stored as a new secret), while 'OAuth' references two already-existing
 * secrets (manifest_tokenRef access token + manifest_refreshTokenRef refresh token) and enables API access.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class GitlabManifestProvisioner implements ManifestProvisioner {
  private final OnboardingSecretCreation secretCreation;

  @Inject
  public GitlabManifestProvisioner(OnboardingSecretCreation secretCreation) {
    this.secretCreation = secretCreation;
  }

  @Override
  public ManifestProviderType type() {
    return ManifestProviderType.GITLAB;
  }

  @Override
  public boolean requiresConnector() {
    return ManifestProviderType.GITLAB.requiresConnector();
  }

  @Override
  public void validate(OnboardingContextDTO context) {
    if (context.getManifestAuthType() != null && context.getManifestAuthType() != OnboardingGitAuthType.USERNAME_TOKEN
        && context.getManifestAuthType() != OnboardingGitAuthType.OAUTH) {
      throw new InvalidRequestException("manifest_authType must be 'UsernameToken' or 'OAuth' for a gitlab manifest");
    }
    if (StringUtils.isBlank(context.getManifestRepoUrl())) {
      throw new InvalidRequestException("manifest_repoUrl (full repository URL) is required for a gitlab manifest");
    }
    if (context.getManifestAuthType() == OnboardingGitAuthType.OAUTH) {
      if (StringUtils.isBlank(context.getManifestTokenRef())) {
        throw new InvalidRequestException(
            "manifest_tokenRef (reference to an existing secret) is required when manifest_authType is 'oauth'");
      }
      if (StringUtils.isBlank(context.getManifestRefreshTokenRef())) {
        throw new InvalidRequestException(
            "manifest_refreshTokenRef (reference to an existing secret) is required when manifest_authType is 'oauth'");
      }
    } else {
      if (StringUtils.isBlank(context.getManifestUsername())) {
        throw new InvalidRequestException("manifest_username is required for a gitlab manifest");
      }
      if (StringUtils.isBlank(context.getManifestToken())) {
        throw new InvalidRequestException("manifest_token is required for a gitlab manifest");
      }
    }
  }

  @Override
  public ConnectorInfoDTO buildConnector(OnboardingProvisionContext provisionContext) {
    OnboardingContextDTO context = provisionContext.getRequest();
    GitAuthMode gitlabAuthMode = OnboardingContextNormalizer.resolveGitAuthMode(context.getManifestAuthType());
    if (gitlabAuthMode == GitAuthMode.OAUTH) {
      // OAuth references already-existing secrets (manifest_tokenRef access token + manifest_refreshTokenRef
      // refresh token); no secret is created here.
      return buildGitlabConnector(context, provisionContext.getOrgIdentifier(), provisionContext.getProjectIdentifier(),
          gitlabAuthMode, context.getManifestTokenRef(), context.getManifestRefreshTokenRef());
    }
    // username/token materializes manifest_token into a new secret used as token.
    String gitlabSecretRef = secretCreation.upsertSecret(provisionContext.getScopeInfo(),
        provisionContext.getOrgIdentifier(), provisionContext.getProjectIdentifier(),
        context.getManifestId() + "_credential", context.getManifestToken(), provisionContext.getCreatedSecrets());
    return buildGitlabConnector(context, provisionContext.getOrgIdentifier(), provisionContext.getProjectIdentifier(),
        gitlabAuthMode, gitlabSecretRef, null);
  }

  @Override
  public ManifestConfigWrapper buildManifest(OnboardingContextDTO context, String connectorRef) {
    FetchType gitFetchType = OnboardingContextNormalizer.resolveGitFetchType(context.getManifestFetchType());
    GitLabStoreBuilder storeBuilder = GitLabStore.builder()
                                          .connectorRef(ParameterField.createValueField(connectorRef))
                                          .gitFetchType(gitFetchType)
                                          .paths(ParameterField.createValueField(
                                              ManifestStoreSupport.resolveManifestPaths(context.getManifestPaths())));
    if (StringUtils.isNotBlank(context.getManifestRepoName())) {
      storeBuilder.repoName(ParameterField.createValueField(context.getManifestRepoName()));
    }
    ManifestStoreSupport.applyGitFetchRef(context, gitFetchType, storeBuilder::branch, storeBuilder::commitId);
    StoreConfigWrapper store =
        StoreConfigWrapper.builder().type(StoreConfigType.GITLAB).spec(storeBuilder.build()).build();
    return ManifestStoreSupport.k8sManifest(context, store);
  }

  private ConnectorInfoDTO buildGitlabConnector(OnboardingContextDTO context, String orgIdentifier,
      String projectIdentifier, GitAuthMode authMode, String tokenRef, String refreshTokenRef) {
    GitlabHttpCredentialsDTO httpCredentials;
    GitlabApiAccessDTO apiAccess = null;
    if (authMode == GitAuthMode.OAUTH) {
      SecretRefData tokenRefData = SecretRefHelper.createSecretRef(tokenRef);
      SecretRefData refreshTokenRefData = SecretRefHelper.createSecretRef(refreshTokenRef);
      // GitLab OAuth carries both an access token and a refresh token, and also enables API access (apiAccess)
      // backed by the same OAuth credential, mirroring a UI-created connector.
      httpCredentials =
          GitlabHttpCredentialsDTO.builder()
              .type(GitlabHttpAuthenticationType.OAUTH)
              .httpCredentialsSpec(
                  GitlabOauthDTO.builder().tokenRef(tokenRefData).refreshTokenRef(refreshTokenRefData).build())
              .build();
      apiAccess =
          GitlabApiAccessDTO.builder()
              .type(GitlabApiAccessType.OAUTH)
              .spec(GitlabOauthDTO.builder().tokenRef(tokenRefData).refreshTokenRef(refreshTokenRefData).build())
              .build();
    } else {
      // username/token: manifest_token is stored as a secret and used as the token; manifest_username is the login.
      SecretRefData tokenRefData = SecretRefHelper.createSecretRef(tokenRef);
      httpCredentials = GitlabHttpCredentialsDTO.builder()
                            .type(GitlabHttpAuthenticationType.USERNAME_AND_TOKEN)
                            .httpCredentialsSpec(GitlabUsernameTokenDTO.builder()
                                                     .username(context.getManifestUsername())
                                                     .tokenRef(tokenRefData)
                                                     .build())
                            .build();
    }

    GitlabConnectorDTO gitlabConnector =
        GitlabConnectorDTO.builder()
            .connectionType(GitConnectionType.REPO)
            .url(context.getManifestRepoUrl())
            .authentication(
                GitlabAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(httpCredentials).build())
            .apiAccess(apiAccess)
            .build();

    String identifier = OnboardingIdentifiers.sanitizeIdentifier(context.getManifestId());
    return ConnectorInfoDTO.builder()
        .identifier(identifier)
        .name(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .connectorType(ConnectorType.GITLAB)
        .connectorConfig(gitlabConnector)
        .build();
  }
}
