/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.provisioners.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.GithubStore.GithubStoreBuilder;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
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
 * GitHub manifest source. Needs a repo URL and (for OAuth) a reference to an already-existing secret. Onboarding
 * supports only UsernameToken and OAuth auth types. The connector mirrors a UI-created one (apiAccess is enabled for
 * OAuth).
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class GithubManifestProvisioner implements ManifestProvisioner {
  private final OnboardingSecretCreation secretCreation;

  @Inject
  public GithubManifestProvisioner(OnboardingSecretCreation secretCreation) {
    this.secretCreation = secretCreation;
  }

  @Override
  public ManifestProviderType type() {
    return ManifestProviderType.GITHUB;
  }

  @Override
  public boolean requiresConnector() {
    return ManifestProviderType.GITHUB.requiresConnector();
  }

  @Override
  public void validate(OnboardingContextDTO context) {
    if (context.getManifestAuthType() != null && context.getManifestAuthType() != OnboardingGitAuthType.USERNAME_TOKEN
        && context.getManifestAuthType() != OnboardingGitAuthType.OAUTH) {
      throw new InvalidRequestException("manifest_authType must be 'UsernameToken' or 'OAuth' for a github manifest");
    }
    if (StringUtils.isBlank(context.getManifestRepoUrl())) {
      throw new InvalidRequestException("manifest_repoUrl (full repository URL) is required for a github manifest");
    }
    if (OnboardingContextNormalizer.resolveGitAuthMode(context.getManifestAuthType()) == GitAuthMode.OAUTH
        && StringUtils.isBlank(context.getManifestTokenRef())) {
      throw new InvalidRequestException(
          "manifest_tokenRef (reference to an existing secret) is required when manifest_authType is 'oauth'");
    }
  }

  @Override
  public ConnectorInfoDTO buildConnector(OnboardingProvisionContext provisionContext) {
    OnboardingContextDTO context = provisionContext.getRequest();
    GitAuthMode authMode = OnboardingContextNormalizer.resolveGitAuthMode(context.getManifestAuthType());
    // OAuth references an already-existing secret (manifest_tokenRef); no secret is created here.
    // username&password / username&token materialize manifest_token into a new secret.
    String secretRef = authMode == GitAuthMode.OAUTH
        ? context.getManifestTokenRef()
        : secretCreation.upsertSecret(provisionContext.getScopeInfo(), provisionContext.getOrgIdentifier(),
              provisionContext.getProjectIdentifier(), context.getManifestId() + "_credential",
              context.getManifestToken(), provisionContext.getCreatedSecrets());
    return buildGithubConnector(
        context, provisionContext.getOrgIdentifier(), provisionContext.getProjectIdentifier(), authMode, secretRef);
  }

  @Override
  public ManifestConfigWrapper buildManifest(OnboardingContextDTO context, String connectorRef) {
    FetchType gitFetchType = OnboardingContextNormalizer.resolveGitFetchType(context.getManifestFetchType());
    GithubStoreBuilder storeBuilder = GithubStore.builder()
                                          .connectorRef(ParameterField.createValueField(connectorRef))
                                          .gitFetchType(gitFetchType)
                                          .paths(ParameterField.createValueField(
                                              ManifestStoreSupport.resolveManifestPaths(context.getManifestPaths())));
    if (StringUtils.isNotBlank(context.getManifestRepoName())) {
      storeBuilder.repoName(ParameterField.createValueField(context.getManifestRepoName()));
    }
    ManifestStoreSupport.applyGitFetchRef(context, gitFetchType, storeBuilder::branch, storeBuilder::commitId);
    StoreConfigWrapper store =
        StoreConfigWrapper.builder().type(StoreConfigType.GITHUB).spec(storeBuilder.build()).build();
    return ManifestStoreSupport.k8sManifest(context, store);
  }

  private ConnectorInfoDTO buildGithubConnector(OnboardingContextDTO context, String orgIdentifier,
      String projectIdentifier, GitAuthMode authMode, String secretRef) {
    SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretRef);
    GithubHttpCredentialsDTO httpCredentials;
    // OAuth also enables API access (apiAccess) backed by the same credential, mirroring a UI-created connector.
    GithubApiAccessDTO apiAccess = null;
    if (authMode == GitAuthMode.OAUTH) {
      httpCredentials = GithubHttpCredentialsDTO.builder()
                            .type(GithubHttpAuthenticationType.OAUTH)
                            .httpCredentialsSpec(GithubOauthDTO.builder().tokenRef(secretRefData).build())
                            .build();
      apiAccess = GithubApiAccessDTO.builder()
                      .type(GithubApiAccessType.OAUTH)
                      .spec(GithubOauthDTO.builder().tokenRef(secretRefData).build())
                      .build();
    } else {
      httpCredentials = GithubHttpCredentialsDTO.builder()
                            .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                            .httpCredentialsSpec(GithubUsernameTokenDTO.builder()
                                                     .username(context.getManifestUsername())
                                                     .tokenRef(secretRefData)
                                                     .build())
                            .build();
    }

    GithubConnectorDTO githubConnector =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.REPO)
            .url(context.getManifestRepoUrl())
            .authentication(
                GithubAuthenticationDTO.builder().authType(GitAuthType.HTTP).credentials(httpCredentials).build())
            .apiAccess(apiAccess)
            .build();

    String identifier = OnboardingIdentifiers.sanitizeIdentifier(context.getManifestId());
    return ConnectorInfoDTO.builder()
        .identifier(identifier)
        .name(identifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .connectorType(ConnectorType.GITHUB)
        .connectorConfig(githubConnector)
        .build();
  }
}
