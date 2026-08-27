/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.mapper.git;

import static io.harness.idp.common.Constants.AZURE_REPO;
import static io.harness.idp.integrations.utils.Constants.GITHUB_APP;
import static io.harness.idp.integrations.utils.Constants.MANAGED_TOKEN;
import static io.harness.idp.integrations.utils.Constants.USERNAME_AND_TOKEN;
import static io.harness.idp.integrations.utils.Constants.USERNAME_PASSWORD;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.GitIntegrationResponse;
import io.harness.spec.server.idp.v1.model.ValidationResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class GitIntegrationMapper {
  public GitIntegrationResponse toResponse(IntegrationEntity integrationEntity) {
    GitIntegrationEntity entity = (GitIntegrationEntity) integrationEntity;
    GitIntegrationResponse response = new GitIntegrationResponse();
    response.setConnectorIdentifier(entity.getConnectorIdentifier());
    response.setHost(entity.getHost()
        + (entity.getAdditionalIndexer() != null && !Objects.equals(entity.getHost(), entity.getAdditionalIndexer())
                ? "/" + entity.getAdditionalIndexer()
                : ""));
    response.setAuthType(mapToActualConnectorAuthType(entity.getAuthMode()));
    response.setViaDelegate(entity.isExecuteOnDelegate());
    response.setIdentifier(entity.getIdentifier());
    response.setName(entity.getIdentifier());
    response.setConnectorType(mapToActualConnectorType(entity.getParentType()));
    response.setDisplayType(
        (entity.getSubType() != null) ? entity.getSubType().toString() : entity.getParentType().toString());
    if (entity.getReadPermissionValidation() != null) {
      response.setValidation(new ValidationResponse()
                                 .url(entity.getReadPermissionValidation().getFileUrl())
                                 .status(entity.getReadPermissionValidation().getStatus())
                                 .error(entity.getReadPermissionValidation().getError())
                                 .validated(entity.getReadPermissionValidation().getLastValidatedAt()));
    }
    response.setType(BaseIntegrationResponse.TypeEnum.GIT);
    response.setUpdatedAt(entity.getLastUpdatedAt());
    response.setParentDeleted(entity.isParentDeleted());
    response.setManaged(entity.isManaged());
    return response;
  }

  public List<GitIntegrationResponse> toResponse(List<IntegrationEntity> entities) {
    List<GitIntegrationResponse> responses = new ArrayList<>();
    entities.forEach(entity -> responses.add(toResponse(entity)));
    return responses;
  }

  private String mapToActualConnectorType(IntegrationEntity.ParentType gitIntegrationParentType) {
    switch (gitIntegrationParentType) {
      case AZURE -> {
        return AZURE_REPO;
      }
      case BITBUCKET_CLOUD, BITBUCKET_SERVER -> {
        return Constants.BITBUCKET;
      }
      case GITHUB -> {
        return Constants.GITHUB;
      }
      case GITLAB -> {
        return Constants.GITLAB;
      }
      case HARNESS_CODE_REPO -> {
        return Constants.HARNESS;
      }
      default -> throw new UnexpectedException("Unknown git integration parent type " + gitIntegrationParentType);
    }
  }

  private String mapToActualConnectorAuthType(GitIntegrationEntity.AuthMode authMode) {
    switch (authMode) {
      case TOKEN -> {
        return USERNAME_AND_TOKEN;
      }
      case USERNAME_PASSWORD -> {
        return USERNAME_PASSWORD;
      }
      case GITHUB_APP -> {
        return GITHUB_APP;
      }
      case MANAGED_TOKEN -> {
        return MANAGED_TOKEN;
      }
      default -> throw new UnexpectedException("Unknown git integration auth mode " + authMode);
    }
  }
}
