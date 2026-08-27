/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.addAccountScopeInIdentifier;
import static io.harness.idp.common.Constants.SLASH_DELIMITER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationUsernamePasswordAuth;
import io.harness.idp.integrations.entities.git.BitbucketIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity.AuthMode;

import java.util.List;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public abstract sealed class BitbucketIntegrationOpsImpl
    extends GitIntegrationOps<BitbucketIntegrationEntity, BitbucketConnectorDTO> permits
                BitbucketCloudIntegrationOpsImpl,
            BitbucketServerIntegrationOpsImpl {
  @Override
  public BitbucketConnectorDTO getConnectorConfigDTO(ConnectorInfoDTO connectorInfoDTO) {
    return (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
  }

  @Override
  public AuthMode validateAndGetAuthMode(BitbucketConnectorDTO bitbucketConnectorDTO) {
    BitbucketHttpCredentialsDTO credentials = bitbucketHttpCredentialsDTO(bitbucketConnectorDTO);
    BitbucketHttpAuthenticationType type = credentials.getType();
    if (type != BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD) {
      throw new InvalidRequestException("Bitbucket integration is supported only with UsernamePassword authentication");
    }
    return AuthMode.USERNAME_PASSWORD;
  }

  @Override
  public GitIntegrationAuth getAuth(BitbucketConnectorDTO bitbucketConnectorDTO, String accountIdentifier) {
    BitbucketHttpCredentialsDTO credentials = bitbucketHttpCredentialsDTO(bitbucketConnectorDTO);
    BitbucketUsernamePasswordDTO httpCredentialsSpec =
        (BitbucketUsernamePasswordDTO) credentials.getHttpCredentialsSpec();
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsername(httpCredentialsSpec.getUsername());
    if (httpCredentialsSpec.getUsernameRef() != null) {
      gitIntegrationUsernamePasswordAuth.setUsernameSecretIdentifier(
          httpCredentialsSpec.getUsernameRef().getIdentifier());
    }
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier(
        httpCredentialsSpec.getPasswordRef().getIdentifier());
    return gitIntegrationUsernamePasswordAuth;
  }

  @Override
  public Set<String> getDelegateSelectors(BitbucketConnectorDTO bitbucketConnectorDTO) {
    return bitbucketConnectorDTO.getDelegateSelectors();
  }

  @Override
  protected String getRepository(BitbucketConnectorDTO bitbucketConnectorDTO, String repository) {
    String repo = GitClientHelper.getGitRepo(repository);
    if (!GitClientHelper.isBitBucketSAAS(repository)
        && bitbucketConnectorDTO.getConnectionType().equals(GitConnectionType.REPO)) {
      String[] repoSplit = repo.split(SLASH_DELIMITER);
      repo = repoSplit.length > 1 ? repoSplit[repoSplit.length - 1] : repo;
    }
    return repo;
  }

  @Override
  public String getAlreadyExistErrorMessage(BitbucketIntegrationEntity gitIntegrationEntity) {
    return "Bitbucket integration with host " + gitIntegrationEntity.getHost() + " already exists. ";
  }

  public BitbucketHttpCredentialsDTO bitbucketHttpCredentialsDTO(BitbucketConnectorDTO bitbucketConnectorDTO) {
    BitbucketAuthenticationDTO authentication = bitbucketConnectorDTO.getAuthentication();
    GitAuthType authType = authentication.getAuthType();
    if (authType != GitAuthType.HTTP) {
      throw new InvalidRequestException("Bitbucket integration is supported only with HTTP authentication");
    }
    return (BitbucketHttpCredentialsDTO) authentication.getCredentials();
  }

  @Override
  public DecryptableEntity getAuthForDelegateRequest(GitIntegrationEntity gitIntegrationEntity) {
    BitbucketIntegrationEntity bitbucketIntegrationEntity = (BitbucketIntegrationEntity) gitIntegrationEntity;
    GitIntegrationUsernamePasswordAuth authentication = bitbucketIntegrationEntity.getAuth();
    BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO =
        BitbucketUsernamePasswordDTO.builder().username(authentication.getUsername()).build();
    if (!isEmpty(authentication.getUsernameSecretIdentifier())) {
      bitbucketUsernamePasswordDTO.setUsernameRef(
          new SecretRefData(addAccountScopeInIdentifier(authentication.getUsernameSecretIdentifier())));
    }
    if (!isEmpty(authentication.getPasswordSecretIdentifier())) {
      bitbucketUsernamePasswordDTO.setPasswordRef(
          new SecretRefData(addAccountScopeInIdentifier(authentication.getPasswordSecretIdentifier())));
    }
    return bitbucketUsernamePasswordDTO;
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(
      BitbucketIntegrationEntity bitbucketIntegrationEntity, List<HttpHeaderConfig> headers) {
    removeHeadersForDelegateTask(headers);
    GitIntegrationUsernamePasswordAuth auth = bitbucketIntegrationEntity.getAuth();
    return BitbucketUsernamePasswordDTO.builder()
        .username(isEmpty(auth.getUsername()) ? null : auth.getUsername())
        .usernameRef(isEmpty(auth.getUsernameSecretIdentifier())
                ? null
                : new SecretRefData(addAccountScopeInIdentifier(auth.getUsernameSecretIdentifier())))
        .passwordRef(isEmpty(auth.getPasswordSecretIdentifier())
                ? null
                : new SecretRefData(addAccountScopeInIdentifier(auth.getPasswordSecretIdentifier())))
        .build();
  }
}
