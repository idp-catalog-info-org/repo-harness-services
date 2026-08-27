/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import static io.harness.idp.common.Constants.AUTH_GITHUB_ENTERPRISE_INSTANCE_URL;
import static io.harness.idp.configmanager.utils.ConfigManagerUtils.asJsonNode;
import static io.harness.idp.configmanager.utils.ConfigManagerUtils.asYaml;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.common.Constants;
import io.harness.idp.common.OAuthUtils;
import io.harness.idp.configmanager.events.oauth.OAuthConfigCreateEvent;
import io.harness.idp.configmanager.events.oauth.OAuthConfigUpdateEvent;
import io.harness.idp.configmanager.mappers.AuthInfoMapper;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.AuthInfo;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang.StringUtils;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class AuthInfoServiceImpl implements AuthInfoService {
  private BackstageEnvVariableService backstageEnvVariableService;
  private ConfigManagerService configManagerService;
  private NamespaceService namespaceService;
  private TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;

  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private static final String INVALID_SCHEMA_FOR_AUTH = "Invalid json schema for auth config for account - %s";
  private static final Map<String, String> GITHUB_OPTIONAL_VARIABLES =
      Map.of(AUTH_GITHUB_ENTERPRISE_INSTANCE_URL, "enterpriseInstanceUrl");

  @Inject
  AuthInfoServiceImpl(BackstageEnvVariableService backstageEnvVariableService,
      ConfigManagerService configManagerService, NamespaceService namespaceService,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.backstageEnvVariableService = backstageEnvVariableService;
    this.configManagerService = configManagerService;
    this.namespaceService = namespaceService;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public AuthInfo getAuthInfo(String authId, String harnessAccount) {
    List<String> envNames = getEnvNamesForAuthId(authId);
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(harnessAccount);
    List<BackstageEnvVariable> backstageEnvVariables =
        backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(envNames, harnessAccount);
    return AuthInfoMapper.toDTO(namespaceInfo, backstageEnvVariables);
  }

  @Override
  public List<BackstageEnvVariable> saveAuthEnvVariables(
      String authId, List<BackstageEnvVariable> envVariables, String harnessAccount) {
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      List<BackstageEnvVariable> oldBackstageEnvVariables =
          backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(getEnvNamesForAuthId(authId), harnessAccount);
      backstageEnvVariableService.deleteMultiUsingEnvNames(getEnvNamesForAuthId(authId), harnessAccount);
      List<BackstageEnvVariable> backstageEnvVariables =
          backstageEnvVariableService.createOrUpdate(envVariables, harnessAccount);
      if (oldBackstageEnvVariables.size() == 0) {
        outboxService.save(new OAuthConfigCreateEvent(harnessAccount, authId, backstageEnvVariables));
      } else {
        outboxService.save(
            new OAuthConfigUpdateEvent(harnessAccount, authId, backstageEnvVariables, oldBackstageEnvVariables));
      }
      createOrUpdateAppConfigForAuth(authId, harnessAccount, backstageEnvVariables);
      return backstageEnvVariables;
    }));
  }

  private void createOrUpdateAppConfigForAuth(
      String authId, String accountIdentifier, List<BackstageEnvVariable> envVariables) {
    JsonNode rootNode = asJsonNode(ConfigManagerUtils.getAuthConfig(authId));
    insertOptionalConfig(authId, rootNode, envVariables);
    String authConfig = asYaml(rootNode.toString());
    String authSchema = ConfigManagerUtils.getAuthConfigSchema(authId);
    if (!ConfigManagerUtils.isValidSchema(authConfig, authSchema).isEmpty()) {
      log.error(String.format(INVALID_SCHEMA_FOR_AUTH, accountIdentifier));
    }
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(authId);
    appConfig.setConfigs(authConfig);
    appConfig.setEnabled(true);
    appConfig.setConfigName(OAuthUtils.getAuthNameForId(authId));

    List<BackstageEnvSecretVariable> backstageEnvSecretVariables =
        envVariables.stream()
            .filter(backstageEnvVariable -> backstageEnvVariable.getType().equals(BackstageEnvVariable.TypeEnum.SECRET))
            .map(backstageEnvVariable -> (BackstageEnvSecretVariable) backstageEnvVariable)
            .toList();
    appConfig.setEnvVariables(backstageEnvSecretVariables);

    configManagerService.saveUpdateAndMergeConfigForAccount(appConfig, accountIdentifier, ConfigType.AUTH, true);

    log.info("Merging for auth config completed for authId - {}", authId);
  }

  private List<String> getEnvNamesForAuthId(String authId) {
    switch (authId) {
      case Constants.GITHUB_AUTH:
        return Constants.GITHUB_AUTH_ENV_VARIABLES;
      case Constants.GOOGLE_AUTH:
        return Constants.GOOGLE_AUTH_ENV_VARIABLES;
      case Constants.ATLASSIAN_AUTH:
        return Constants.ATLASSIAN_AUTH_ENV_VARIABLES;
      default:
        return null;
    }
  }

  private void insertOptionalConfig(String authId, JsonNode rootNode, List<BackstageEnvVariable> envVariables) {
    if (authId.equals(Constants.GITHUB_AUTH)) {
      for (BackstageEnvVariable envVariable : envVariables) {
        if (GITHUB_OPTIONAL_VARIABLES.containsKey(envVariable.getEnvName())
            && StringUtils.isNotEmpty(((BackstageEnvConfigVariable) envVariable).getValue())) {
          JsonNode targetNode = ConfigManagerUtils.getNodeByName(rootNode, "development");
          ((ObjectNode) targetNode)
              .put(GITHUB_OPTIONAL_VARIABLES.get(envVariable.getEnvName()), "${" + envVariable.getEnvName() + "}");
          ObjectNode harnessIdpConfig = ((ObjectNode) rootNode).putObject("harnessIdpConfig");
          harnessIdpConfig.put(Constants.GHE_HOST, "${" + envVariable.getEnvName() + "}");
        }
      }
    }
  }
}
