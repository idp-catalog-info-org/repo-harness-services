/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.git.GitClientHelper.fetchCustomBitbucketDomainV2;
import static io.harness.idp.common.CommonUtils.readFileFromClassPath;
import static io.harness.idp.common.Constants.INTEGRATIONS_BITBUCKET_SERVER_PASSWORD;
import static io.harness.idp.common.Constants.INTEGRATIONS_BITBUCKET_SERVER_USERNAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.cistatus.service.bitbucket.BitbucketConfig;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.task.idp.gitintegration.BitbucketIntegrationDto;
import io.harness.delegate.task.idp.gitintegration.GitIntegrationDto;
import io.harness.git.GitClientHelper;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationUsernamePasswordAuth;
import io.harness.idp.integrations.entities.IntegrationEntity.Integration;
import io.harness.idp.integrations.entities.IntegrationEntity.ParentType;
import io.harness.idp.integrations.entities.git.BitbucketIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketServerIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public final class BitbucketServerIntegrationOpsImpl extends BitbucketIntegrationOpsImpl {
  @Inject BitbucketService bitbucketService;

  @Override
  public BitbucketServerIntegrationEntity prepare(ConnectorInfoDTO connectorDTO) {
    BitbucketConnectorDTO bitbucketConnectorDTO = getConnectorConfigDTO(connectorDTO);
    return BitbucketServerIntegrationEntity.builder()
        .accountIdentifier(connectorDTO.getAccountIdentifier())
        .identifier(Constants.IDP_PREFIX + connectorDTO.getIdentifier())
        .integration(Integration.GIT)
        .parentType(ParentType.BITBUCKET_SERVER)
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
  public String getHost(BitbucketConnectorDTO bitbucketConnectorDTO) {
    return getDomainFromUrl(bitbucketConnectorDTO.getUrl());
  }

  @Override
  Map<String, String> getIntegrationConfigs(BitbucketIntegrationEntity bitbucketIntegrationEntity) {
    Map<String, String> integrationConfigs = new HashMap<>();
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = bitbucketIntegrationEntity.getAuth();
    if (isNotEmpty(gitIntegrationUsernamePasswordAuth.getUsername())) {
      integrationConfigs.put(
          INTEGRATIONS_BITBUCKET_SERVER_USERNAME + "_" + sanitizeHost(bitbucketIntegrationEntity.getHost()),
          gitIntegrationUsernamePasswordAuth.getUsername());
    }
    return integrationConfigs;
  }

  @Override
  public Map<String, String> getIntegrationSecrets(BitbucketIntegrationEntity gitIntegrationEntity) {
    Map<String, String> integrationSecrets = new HashMap<>();
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = gitIntegrationEntity.getAuth();
    if (isEmpty(gitIntegrationUsernamePasswordAuth.getUsername())) {
      integrationSecrets.put(
          INTEGRATIONS_BITBUCKET_SERVER_USERNAME + "_" + sanitizeHost(gitIntegrationEntity.getHost()),
          String.valueOf(gitIntegrationUsernamePasswordAuth.getUsernameSecretIdentifier()));
    }
    integrationSecrets.put(INTEGRATIONS_BITBUCKET_SERVER_PASSWORD + "_" + sanitizeHost(gitIntegrationEntity.getHost()),
        String.valueOf(gitIntegrationUsernamePasswordAuth.getPasswordSecretIdentifier()));
    return integrationSecrets;
  }

  @Override
  public String getIntegrationAppConfig(
      BitbucketIntegrationEntity bitbucketIntegrationEntity, BitbucketConnectorDTO bitbucketConnectorDTO) {
    String integrationConfig = readFileFromClassPath("integrations/git/bitbucket-server.yaml");
    integrationConfig = integrationConfig.replace("${HOST}", getHost(bitbucketConnectorDTO));
    integrationConfig = integrationConfig.replace("${API_BASE_URL}",
        fetchCustomBitbucketDomainV2(bitbucketConnectorDTO.getUrl(),
            GitClientHelper.getGitSCM(bitbucketConnectorDTO.getUrl()), bitbucketConnectorDTO.fetchApiUrl()));
    integrationConfig = integrationConfig.replace("${BITBUCKET_SERVER_USERNAME}",
        "${BITBUCKET_SERVER_USERNAME_" + sanitizeHost(getHost(bitbucketConnectorDTO)) + "}");
    integrationConfig = integrationConfig.replace("${BITBUCKET_SERVER_PASSWORD}",
        "${BITBUCKET_SERVER_PASSWORD_" + sanitizeHost(getHost(bitbucketConnectorDTO)) + "}");
    return integrationConfig;
  }

  @Override
  String getRepoUrl(BitbucketConnectorDTO bitbucketConnectorDTO) {
    return bitbucketConnectorDTO.getUrl();
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
      GitIntegrationDto gitIntegrationDto =
          BitbucketIntegrationDto.builder()
              .url("https://"
                  + fetchCustomBitbucketDomainV2(bitbucketConnectorDTO.getUrl(),
                      GitClientHelper.getGitSCM(bitbucketConnectorDTO.getUrl()), bitbucketConnectorDTO.fetchApiUrl()))
              .projectOrWorkspace(pathParts[pathParts.length - 2])
              .repository(pathParts[pathParts.length - 1])
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
          BitbucketConfig.builder()
              .bitbucketUrl("https://"
                  + fetchCustomBitbucketDomainV2(bitbucketConnectorDTO.getUrl(),
                      GitClientHelper.getGitSCM(bitbucketConnectorDTO.getUrl()), bitbucketConnectorDTO.fetchApiUrl()))
              .build(),
          username, password, pathParts[pathParts.length - 2], pathParts[pathParts.length - 1]);
      readPermissionValidation = frameReadPermissionValidation(urlForValidation, response);
    }
    bitbucketIntegrationEntity.setReadPermissionValidation(readPermissionValidation);
  }

  @Override
  List<String> getAdditionalHosts(ConnectorInfoDTO connectorDTO) {
    return List.of();
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(ConnectorInfoDTO connectorInfoDTO) {
    return null;
  }
}
