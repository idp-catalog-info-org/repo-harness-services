/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers.utils;

import static io.harness.idp.common.Constants.*;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrails.eventhandlers.dtos.GitHubOAuthConfigDTO;
import io.harness.idp.audittrails.eventhandlers.dtos.OAuthConfigDTO;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class OAuthEventUtils {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  public String getOAuthConfigYamlForAudit(List<BackstageEnvVariable> backstageEnvVariables, String authId) {
    return switch (authId) {
      case GITHUB_AUTH -> getGitHubOAuthConfigYamlForAudit(backstageEnvVariables, authId);
      case GOOGLE_AUTH, ATLASSIAN_AUTH ->
        getDefaultOAuthConfigYamlForAudit(backstageEnvVariables, authId);
      default -> throw new InvalidArgumentsException(String.format("Not supported Auth Type %s", authId));
    };
  }

  private String getGitHubOAuthConfigYamlForAudit(
      List<BackstageEnvVariable> envVariables, String authId) {
    Map<String, BackstageEnvVariable> mappedEnvVariables = getMappedEnvVariables(envVariables);
    BackstageEnvConfigVariable clientIdEnvVariable = (BackstageEnvConfigVariable) mappedEnvVariables
        .get(AUTH_GITHUB_CLIENT_ID);
    String clientId = clientIdEnvVariable.getValue();

    BackstageEnvSecretVariable clientSecretEnvVariable = (BackstageEnvSecretVariable) mappedEnvVariables
        .get(AUTH_GITHUB_CLIENT_SECRET);
    String clientSecret = clientSecretEnvVariable.getHarnessSecretIdentifier();

    String enterpriseUrl = null;
    if (mappedEnvVariables.get(AUTH_GITHUB_ENTERPRISE_INSTANCE_URL) != null) {
      BackstageEnvConfigVariable enterpriseUrlEnvVariable = (BackstageEnvConfigVariable) mappedEnvVariables
          .get(AUTH_GITHUB_ENTERPRISE_INSTANCE_URL);
      enterpriseUrl = enterpriseUrlEnvVariable.getValue();
    }
    return NGYamlUtils.getYamlString(GitHubOAuthConfigDTO.builder()
        .authIdentifier(authId)
        .clientId(clientId)
        .clientSecret(clientSecret)
        .enterpriseInstanceUrl(enterpriseUrl)
        .build(), objectMapper);
  }

  private String getDefaultOAuthConfigYamlForAudit(
      List<BackstageEnvVariable> envVariables, String authId) {
    Map<String, BackstageEnvVariable> mappedEnvVariables = getMappedEnvVariables(envVariables);
    BackstageEnvConfigVariable clientIdEnvVariable = (BackstageEnvConfigVariable) mappedEnvVariables
        .get(getEnvNamesForAuthId(authId).get(0));
    String clientId = clientIdEnvVariable.getValue();

    BackstageEnvSecretVariable clientSecretEnvVariable = (BackstageEnvSecretVariable) mappedEnvVariables
        .get(getEnvNamesForAuthId(authId).get(1));
    String clientSecret = clientSecretEnvVariable.getHarnessSecretIdentifier();

    return NGYamlUtils.getYamlString(OAuthConfigDTO.builder()
        .authIdentifier(authId)
        .clientId(clientId)
        .clientSecret(clientSecret)
        .build(),
        objectMapper);
  }

  private Map<String, BackstageEnvVariable> getMappedEnvVariables(List<BackstageEnvVariable> backstageEnvVariables) {
    return backstageEnvVariables.stream().collect(
        Collectors.toMap(BackstageEnvVariable::getEnvName, Function.identity()));
  }

  private List<String> getEnvNamesForAuthId(String authId) {
    return GOOGLE_AUTH.equals(authId) ? GOOGLE_AUTH_ENV_VARIABLES :
        ATLASSIAN_AUTH_ENV_VARIABLES;
    }
  }
