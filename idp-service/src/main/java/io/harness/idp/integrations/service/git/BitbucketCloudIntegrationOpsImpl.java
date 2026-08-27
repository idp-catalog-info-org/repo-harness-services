/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.common.CommonUtils.addAccountScopeInIdentifier;
import static io.harness.idp.common.CommonUtils.readFileFromClassPath;
import static io.harness.idp.common.Constants.INTEGRATIONS_BITBUCKET_CLOUD_PASSWORD;
import static io.harness.idp.common.Constants.INTEGRATIONS_BITBUCKET_CLOUD_USERNAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.cistatus.service.bitbucket.BitbucketConfig;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAccessTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketEmailApiTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketOAuthDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.task.idp.gitintegration.BitbucketIntegrationDto;
import io.harness.delegate.task.idp.gitintegration.GitIntegrationDto;
import io.harness.encryption.SecretRefData;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationUsernamePasswordAuth;
import io.harness.idp.integrations.entities.IntegrationEntity.Integration;
import io.harness.idp.integrations.entities.IntegrationEntity.ParentType;
import io.harness.idp.integrations.entities.git.BitbucketCloudIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public final class BitbucketCloudIntegrationOpsImpl extends BitbucketIntegrationOpsImpl {
  @Inject BitbucketService bitbucketService;

  @Override
  BitbucketCloudIntegrationEntity prepare(ConnectorInfoDTO connectorDTO) {
    BitbucketConnectorDTO bitbucketConnectorDTO = getConnectorConfigDTO(connectorDTO);
    return BitbucketCloudIntegrationEntity.builder()
        .accountIdentifier(connectorDTO.getAccountIdentifier())
        .identifier(Constants.IDP_PREFIX + connectorDTO.getIdentifier())
        .integration(Integration.GIT)
        .parentType(ParentType.BITBUCKET_CLOUD)
        .connectorIdentifier(connectorDTO.getIdentifier())
        .host(getHost(bitbucketConnectorDTO))
        .authMode(validateAndGetAuthMode(bitbucketConnectorDTO))
        .executeOnDelegate(bitbucketConnectorDTO.getExecuteOnDelegate())
        .delegateSelectors(getDelegateSelectors(bitbucketConnectorDTO))
        .auth((GitIntegrationUsernamePasswordAuth) getAuth(bitbucketConnectorDTO, connectorDTO.getAccountIdentifier()))
        .additionalIndexer(getHost(bitbucketConnectorDTO))
        .build();
  }

  @Override
  String getHost(BitbucketConnectorDTO connectorConfigDTO) {
    return "bitbucket.org";
  }

  @Override
  Map<String, String> getIntegrationConfigs(BitbucketIntegrationEntity bitbucketIntegrationEntity) {
    Map<String, String> integrationConfigs = new HashMap<>();
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = bitbucketIntegrationEntity.getAuth();
    if (isNotEmpty(gitIntegrationUsernamePasswordAuth.getUsername())) {
      integrationConfigs.put(INTEGRATIONS_BITBUCKET_CLOUD_USERNAME, gitIntegrationUsernamePasswordAuth.getUsername());
    }
    return integrationConfigs;
  }

  @Override
  Map<String, String> getIntegrationSecrets(BitbucketIntegrationEntity gitIntegrationEntity) {
    Map<String, String> integrationSecrets = new HashMap<>();
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = gitIntegrationEntity.getAuth();
    if (isEmpty(gitIntegrationUsernamePasswordAuth.getUsername())) {
      integrationSecrets.put(INTEGRATIONS_BITBUCKET_CLOUD_USERNAME,
          String.valueOf(gitIntegrationUsernamePasswordAuth.getUsernameSecretIdentifier()));
    }
    integrationSecrets.put(INTEGRATIONS_BITBUCKET_CLOUD_PASSWORD,
        String.valueOf(gitIntegrationUsernamePasswordAuth.getPasswordSecretIdentifier()));
    return integrationSecrets;
  }

  @Override
  String getIntegrationAppConfig(
      BitbucketIntegrationEntity bitbucketIntegrationEntity, BitbucketConnectorDTO connectorConfigDTO) {
    String integrationConfig = readFileFromClassPath("integrations/git/bitbucket-cloud.yaml");
    integrationConfig = integrationConfig.replace("${HOST}", getHost(connectorConfigDTO));
    return integrationConfig;
  }

  @Override
  String getRepoUrl(BitbucketConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getUrl();
  }

  @Override
  String getGitConnectionType(BitbucketConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getConnectionTypeForGit().toString();
  }

  @Override
  void validateReadPermission(String accountIdentifier, BitbucketConnectorDTO bitbucketConnectorDTO,
      BitbucketIntegrationEntity bitbucketIntegrationEntity, Map<String, String> configsForGitIntegration,
      Map<String, String> secretsForGitIntegration) {
    if (bitbucketIntegrationEntity.getReadPermissionValidation() != null) {
      validateReadPermissionForUrl(accountIdentifier, bitbucketConnectorDTO, bitbucketIntegrationEntity,
          bitbucketIntegrationEntity.getReadPermissionValidation().getFileUrl());
    }
  }

  @Override
  void validateReadPermissionForUrl(String accountIdentifier, BitbucketConnectorDTO bitbucketConnectorDTO,
      BitbucketIntegrationEntity bitbucketIntegrationEntity, String urlForValidation) {
    String[] pathParts = getPathParts(urlForValidation);
    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation;
    if (bitbucketIntegrationEntity.isExecuteOnDelegate()) {
      GitIntegrationDto gitIntegrationDto = BitbucketIntegrationDto.builder()
                                                .url("https://api." + bitbucketIntegrationEntity.getHost())
                                                .projectOrWorkspace(pathParts[1])
                                                .repository(pathParts[2])
                                                .build();
      readPermissionValidation = validateViaDelegateAndFrameReadPermissionValidation(
          bitbucketIntegrationEntity, gitIntegrationDto, urlForValidation);
    } else {
      String username = isEmpty(bitbucketIntegrationEntity.getAuth().getUsername())
          ? getDecryptedValue(accountIdentifier, bitbucketIntegrationEntity.getAuth().getUsernameSecretIdentifier())
          : bitbucketIntegrationEntity.getAuth().getUsername();
      String password =
          getDecryptedValue(accountIdentifier, bitbucketIntegrationEntity.getAuth().getPasswordSecretIdentifier());
      JSONObject response = bitbucketService.getRepository(
          BitbucketConfig.builder().bitbucketUrl("https://api." + bitbucketIntegrationEntity.getHost()).build(),
          username, password, pathParts[1], pathParts[2]);
      readPermissionValidation = frameReadPermissionValidation(urlForValidation, response);
    }
    bitbucketIntegrationEntity.setReadPermissionValidation(readPermissionValidation);
  }

  @Override
  List<String> getAdditionalHosts(ConnectorInfoDTO connectorDTO) {
    return List.of();
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(
      BitbucketIntegrationEntity bitbucketIntegrationEntity, List<HttpHeaderConfig> headers) {
    removeHeadersForDelegateTask(headers);
    Optional<ConnectorDTO> optionalConnectorDTO = Optional.empty();
    try {
      optionalConnectorDTO =
          NGRestUtils.getResponse(connectorResourceClient.get(bitbucketIntegrationEntity.getConnectorIdentifier(),
              bitbucketIntegrationEntity.getAccountIdentifier(), null, null));
    } catch (Exception ex) {
      log.warn("Error in connector resource get for connector = {} account = {} error = {}",
          bitbucketIntegrationEntity.getConnectorIdentifier(), bitbucketIntegrationEntity.getAccountIdentifier(),
          ex.getMessage(), ex);
    }
    if (optionalConnectorDTO.isPresent()) {
      BitbucketConnectorDTO bitbucketConnectorDTO =
          getConnectorConfigDTO(optionalConnectorDTO.get().getConnectorInfo());
      if (bitbucketConnectorDTO.getApiAccess() != null && bitbucketConnectorDTO.getApiAccess().getSpec() != null) {
        BitbucketApiAccessSpecDTO spec = bitbucketConnectorDTO.getApiAccess().getSpec();
        log.info("Bitbucket API access spec type: {}", spec.getClass().getSimpleName());

        // BitbucketUsernameTokenApiAccessDTO should work as-is for API access
        if (spec instanceof BitbucketUsernameTokenApiAccessDTO) {
          log.info("Using BitbucketUsernameTokenApiAccessDTO for authentication");
          return spec;
        }

        if (spec instanceof BitbucketAccessTokenApiAccessDTO) {
          log.info("Using BitbucketAccessTokenApiAccessDTO for authentication");
          return BitbucketAccessTokenApiAccessDTO.builder()
              .tokenRef(((BitbucketAccessTokenApiAccessDTO) spec).getTokenRef())
              .build();
        }

        // BitbucketEmailApiTokenApiAccessDTO needs to be converted to BitbucketUsernameTokenApiAccessDTO
        // for archive downloads (email should be used as username)
        if (spec instanceof BitbucketEmailApiTokenApiAccessDTO) {
          BitbucketEmailApiTokenApiAccessDTO emailApiToken = (BitbucketEmailApiTokenApiAccessDTO) spec;
          log.info(
              "Converting BitbucketEmailApiTokenApiAccessDTO to BitbucketUsernameTokenApiAccessDTO for API access");
          return BitbucketUsernameTokenApiAccessDTO.builder()
              .username(emailApiToken.getEmail())
              .usernameRef(emailApiToken.getEmailRef())
              .tokenRef(emailApiToken.getTokenRef())
              .build();
        }

        // BitbucketOAuthDTO might also be configured - return as-is and let the delegate handle it
        if (spec instanceof BitbucketOAuthDTO) {
          log.info("Using BitbucketOAuthDTO for authentication");
          return spec;
        }

        // If the spec is BitbucketUsernamePasswordDTO (shouldn't be used for API access)
        // convert it to BitbucketUsernameTokenApiAccessDTO
        if (spec instanceof BitbucketUsernamePasswordDTO) {
          BitbucketUsernamePasswordDTO usernamePassword = (BitbucketUsernamePasswordDTO) spec;
          log.warn("Converting BitbucketUsernamePasswordDTO to BitbucketUsernameTokenApiAccessDTO for API access");
          return BitbucketUsernameTokenApiAccessDTO.builder()
              .username(usernamePassword.getUsername())
              .usernameRef(usernamePassword.getUsernameRef())
              .tokenRef(usernamePassword.getPasswordRef()) // Password is treated as token for API access
              .build();
        }

        log.warn("Unsupported API access spec type: {}, returning as-is", spec.getClass().getSimpleName());
        return spec;
      }
    }
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

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(ConnectorInfoDTO connectorInfoDTO) {
    BitbucketUsernameTokenApiAccessDTO authentication = null;
    GitIntegrationAuth gitIntegrationAuth = getAuth(getConnectorConfigDTO(connectorInfoDTO));
    if (gitIntegrationAuth instanceof GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth) {
      String[] secretRefSplit = gitIntegrationUsernamePasswordAuth.getPasswordSecretIdentifier().split("[.]");
      String secretOrgIdentifier = null;
      String secretProjectIdentifier = null;
      if (secretRefSplit.length == 2 && secretRefSplit[0].equals("org")) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
      }
      if (secretRefSplit.length == 1) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
        secretProjectIdentifier = connectorInfoDTO.getProjectIdentifier();
      }

      return BitbucketUsernameTokenApiAccessDTO.builder()
          .username(gitIntegrationUsernamePasswordAuth.getUsername())
          .usernameRef(isEmpty(gitIntegrationUsernamePasswordAuth.getUsernameSecretIdentifier())
                  ? null
                  : new SecretRefData(isEmpty(secretOrgIdentifier) && isEmpty(secretProjectIdentifier)
                            ? addAccountScopeInIdentifier(
                                  gitIntegrationUsernamePasswordAuth.getUsernameSecretIdentifier())
                            : gitIntegrationUsernamePasswordAuth.getUsernameSecretIdentifier()))
          .tokenRef(isEmpty(gitIntegrationUsernamePasswordAuth.getPasswordSecretIdentifier())
                  ? null
                  : new SecretRefData(isEmpty(secretOrgIdentifier) && isEmpty(secretProjectIdentifier)
                            ? addAccountScopeInIdentifier(
                                  gitIntegrationUsernamePasswordAuth.getPasswordSecretIdentifier())
                            : gitIntegrationUsernamePasswordAuth.getPasswordSecretIdentifier()))
          .build();
    } else if (gitIntegrationAuth instanceof GitIntegrationTokenAuth gitIntegrationTokenAuth) {
      String[] secretRefSplit = gitIntegrationTokenAuth.getTokenSecretIdentifier().split("[.]");
      String secretOrgIdentifier = null;
      String secretProjectIdentifier = null;
      if (secretRefSplit.length == 2 && secretRefSplit[0].equals("org")) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
      }
      if (secretRefSplit.length == 1) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
        secretProjectIdentifier = connectorInfoDTO.getProjectIdentifier();
      }

      return BitbucketAccessTokenApiAccessDTO.builder()
          .tokenRef(isEmpty(gitIntegrationTokenAuth.getTokenSecretIdentifier())
                  ? null
                  : new SecretRefData(isEmpty(secretOrgIdentifier) && isEmpty(secretProjectIdentifier)
                            ? addAccountScopeInIdentifier(gitIntegrationTokenAuth.getTokenSecretIdentifier())
                            : gitIntegrationTokenAuth.getTokenSecretIdentifier()))
          .build();
    }
    return authentication;
  }

  public GitIntegrationAuth getAuth(BitbucketConnectorDTO bitbucketConnectorDTO) {
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

    BitbucketApiAccessSpecDTO bitbucketApiAccessSpecDTO = bitbucketApiAccessSpecDTO(bitbucketConnectorDTO);
    if (bitbucketApiAccessSpecDTO == null) {
      return gitIntegrationUsernamePasswordAuth;
    }

    if (bitbucketApiAccessSpecDTO instanceof BitbucketUsernameTokenApiAccessDTO bitbucketUsernameTokenApiAccessDTO) {
      gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
      gitIntegrationUsernamePasswordAuth.setUsername(bitbucketUsernameTokenApiAccessDTO.getUsername());
      gitIntegrationUsernamePasswordAuth.setUsernameSecretIdentifier(
          bitbucketUsernameTokenApiAccessDTO.getUsernameRef().getIdentifier());
      gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier(
          bitbucketUsernameTokenApiAccessDTO.getTokenRef().getIdentifier());
      return gitIntegrationUsernamePasswordAuth;
    } else if (bitbucketApiAccessSpecDTO instanceof BitbucketAccessTokenApiAccessDTO bitbucketAccessTokenApiAccessDTO) {
      GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
      gitIntegrationTokenAuth.setTokenSecretIdentifier(bitbucketAccessTokenApiAccessDTO.getTokenRef().getIdentifier());
      return gitIntegrationTokenAuth;
    }

    return gitIntegrationUsernamePasswordAuth;
  }

  private BitbucketApiAccessSpecDTO bitbucketApiAccessSpecDTO(BitbucketConnectorDTO bitbucketConnectorDTO) {
    BitbucketApiAccessDTO apiAccess = bitbucketConnectorDTO.getApiAccess();
    if (apiAccess == null) {
      return null;
    }

    BitbucketApiAccessType type = apiAccess.getType();
    if (type != BitbucketApiAccessType.USERNAME_AND_TOKEN && type != BitbucketApiAccessType.ACCESS_TOKEN) {
      log.warn(
          "Bitbucket integration is supported only with UsernameToken / AccessToken authentication for api access");
      return null;
    }

    return apiAccess.getSpec();
  }
}
