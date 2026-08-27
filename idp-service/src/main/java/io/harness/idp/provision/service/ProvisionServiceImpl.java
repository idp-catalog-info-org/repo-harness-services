/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.provision.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.JacksonUtils.readValue;
import static io.harness.idp.provision.ProvisionConstants.PROVISION_MODULE_CONFIG;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.helpers.IDPToHarnessHelper;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.PipelineTriggerUtils;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.layout.entities.LayoutEntity;
import io.harness.idp.layout.repositories.LayoutEntityRepository;
import io.harness.idp.namespace.mappers.NamespaceMapper;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.personaview.service.PersonaViewService;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretRequestWrapper;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.springdata.TransactionHelper;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class ProvisionServiceImpl implements ProvisionService {
  private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static SecureRandom rnd = new SecureRandom();
  private static final int SECRET_LENGTH = 32;
  static final String ERROR_MESSAGE = "HTTP Error Status (400 - Invalid Format) received. Invalid request: Secret with "
      + "identifier IDP_BACKEND_SECRET already exists in this scope";
  @Inject @Named(PROVISION_MODULE_CONFIG) ProvisionModuleConfig provisionModuleConfig;
  private static final Retry retry =
      CommonUtils.buildRetryAndRegisterListeners(ProvisionServiceImpl.class.getSimpleName());

  @Inject ConfigManagerService configManagerService;
  private static final List<String> permissions =
      List.of("user_read", "user_update", "user_delete", "owner_read", "owner_update", "owner_delete", "all_create");
  @Inject BackstagePermissionsService backstagePermissionsService;
  @Inject BackstageEnvVariableService backstageEnvVariableService;
  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;
  @Inject NamespaceService namespaceService;
  @Inject NgLicenseHttpClient ngLicenseHttpClient;
  @Inject GitIntegrationServiceImpl gitIntegrationService;
  @Inject IdpCommonService idpCommonService;
  @Inject @Named("base") private String base;
  @Inject @Named("dynamicConfigResolution") boolean dynamicConfigResolution;
  @Inject @Named("idpAutomationXApiKey") String xApiKey;
  @Inject LayoutEntityRepository layoutEntityRepository;
  @Inject TransactionHelper transactionHelper;
  @Inject PersonaViewService personaViewService;
  @Inject IDPToHarnessHelper idpToHarnessHelper;

  private static final String OVERRIDE_CONFIG_MAP_CREATE_ERROR =
      "While provisioning error in creating the backstage-override-config for account - {}, Exception - {}";

  @Override
  public NamespaceInfo provision(String accountIdentifier) {
    NamespaceInfo namespaceInfo = null;
    try {
      namespaceInfo = NamespaceMapper.toDTO(namespaceService.saveAccountIdNamespace(accountIdentifier));
    } catch (DuplicateKeyException e) {
      String logMessage = String.format("Namespace already created for given account Id - %s", accountIdentifier);
      log.info(logMessage);
    }
    if (namespaceInfo == null) {
      namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier);
    }

    triggerPipelineAndCreatePermissions(namespaceInfo.getAccountIdentifier(), namespaceInfo.getNamespace());
    setupDefaultConnectorLessManagedHarnessCodeRepoIntegrationIfApplicable(accountIdentifier);
    seedLayouts(accountIdentifier);
    seedOotbPersonaViews(accountIdentifier);
    seedUsersAndUserGroups(accountIdentifier);
    return namespaceInfo;
  }

  /**
   * Seed the OOTB persona views ({@code platform}, {@code leadership}) for the account as part of base IDP
   * provisioning. Idempotent and best-effort: failures here are logged and do not fail the provision call.
   */
  private void seedOotbPersonaViews(String accountIdentifier) {
    try {
      personaViewService.seedOotbPersonaViewsIfNotAlready(accountIdentifier);
    } catch (Exception ex) {
      log.error("Error in seeding OOTB persona views for accountIdentifier = {} Error = {}", accountIdentifier,
          ex.getMessage(), ex);
    }
  }

  @Override
  public void triggerPipelineAndCreatePermissions(String accountIdentifier, String namespace) {
    createBackstageBackendSecret(accountIdentifier);
    createDefaultPermissions(accountIdentifier);
    if (!dynamicConfigResolution) {
      createBackstageOverrideConfig(accountIdentifier);
    }
    makeTriggerApi(accountIdentifier, namespace);
  }

  @Override
  public void createDefaultPermissions(String accountIdentifier) {
    try {
      BackstagePermissions backstagePermissions = new BackstagePermissions();
      backstagePermissions.setUserGroup(" ");
      backstagePermissions.setUserGroups(Collections.emptyList());
      backstagePermissions.setPermissions(permissions);
      backstagePermissionsService.createPermissions(backstagePermissions, accountIdentifier);
    } catch (DuplicateKeyException e) {
      String logMessage = String.format("Permissions already created for given account Id - %s", accountIdentifier);
      log.info(logMessage);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new InvalidRequestException(e.getMessage(), e);
    }
  }

  @Override
  public void createBackstageBackendSecret(String accountIdentifier) {
    String actualSecret = generateEncodedSecret();
    SecretRequestWrapper secretRequestWrapper =
        SecretRequestWrapper.builder()
            .secret(SecretDTOV2.builder()
                        .identifier(Constants.IDP_BACKEND_SECRET)
                        .name(Constants.IDP_BACKEND_SECRET)
                        .description("Auto Generated Secret for Backstage Backend")
                        .type(SecretType.SecretText)
                        .spec(SecretTextSpecDTO.builder()
                                  .secretManagerIdentifier("harnessSecretManager")
                                  .value(actualSecret)
                                  .valueType(ValueType.Inline)
                                  .build())
                        .build())
            .build();

    SecretResponseWrapper secretDto = createSecret(accountIdentifier, secretRequestWrapper);
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(Constants.BACKEND_SECRET);
    backstageEnvSecretVariable.setHarnessSecretIdentifier(secretDto.getSecret().getIdentifier());
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    try {
      backstageEnvVariableService.create(backstageEnvSecretVariable, accountIdentifier);
      log.info("Created BACKEND_SECRET for account Id - {}", accountIdentifier);
    } catch (DuplicateKeyException e) {
      backstageEnvVariableService.update(backstageEnvSecretVariable, accountIdentifier);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new InvalidRequestException(e.getMessage(), e);
    }
  }

  private SecretResponseWrapper createSecret(String accountIdentifier, SecretRequestWrapper secretRequestWrapper) {
    // Source principal should match the owner in case of a private secret
    // In our case, the source principal is USER, but the owner is IDP Service which is set while creating the client
    // Hence we are setting source principal manually to IDPService and unsetting it after the create call.
    Principal currentPrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    try {
      return ngSecretService.create(accountIdentifier, null, null, true, secretRequestWrapper);
    } catch (Exception e) {
      if (e.getMessage().equals(ERROR_MESSAGE)) {
        return ngSecretService.updateSecret(
            Constants.IDP_BACKEND_SECRET, accountIdentifier, null, null, secretRequestWrapper);
      }
      log.error("Could not create IDP_BACKEND_SECRET for account Id - {}", accountIdentifier);
      throw new InvalidRequestException(e.getMessage());
    } finally {
      SourcePrincipalContextBuilder.setSourcePrincipal(currentPrincipal);
    }
  }

  public static String generateEncodedSecret() {
    return Base64.getEncoder().encodeToString(generateSecret().getBytes());
  }

  static String generateSecret() {
    StringBuilder sb = new StringBuilder(SECRET_LENGTH);
    for (int i = 0; i < SECRET_LENGTH; i++) {
      sb.append(ALPHANUMERIC.charAt(rnd.nextInt(ALPHANUMERIC.length())));
    }
    return sb.toString();
  }

  private void makeTriggerApi(String accountIdentifier, String namespace) {
    String url = provisionModuleConfig.getTriggerPipelineUrl();
    String storedVanityUrl = idpCommonService.getAccountDTO(accountIdentifier).getSubdomainURL();
    String vanityUrlForPayload = !isEmpty(storedVanityUrl) ? storedVanityUrl : base;
    PipelineTriggerUtils.trigger(accountIdentifier, namespace, "", url, vanityUrlForPayload, retry, xApiKey);
  }

  @Override
  public void createBackstageOverrideConfig(String accountIdentifier) {
    try {
      /*passing oldMergedConfig as empty string as first time this should be empty otherwise in
      mergeAndUpdateConfigInNamespace update will not happen */
      configManagerService.mergeAndUpdateConfigInNamespace(accountIdentifier, "");
    } catch (Exception e) {
      log.error(OVERRIDE_CONFIG_MAP_CREATE_ERROR, accountIdentifier, e);
      throw new GeneralException("Failed to create base-override-config", e);
    }
  }

  private void setupDefaultConnectorLessManagedHarnessCodeRepoIntegrationIfApplicable(String accountIdentifier) {
    try {
      LicensesWithSummaryDTO codeLicenseWithSummaryDTO =
          NGRestUtils.getResponse(ngLicenseHttpClient.getLicenseSummary(accountIdentifier, ModuleType.CODE.name()));
      if (codeLicenseWithSummaryDTO != null
          && codeLicenseWithSummaryDTO.getMaxExpiryTime() > System.currentTimeMillis()) {
        gitIntegrationService.setupDefaultConnectorLessManagedHarnessCodeRepoIntegrationIfNotAlready(accountIdentifier);
      }
    } catch (Exception ex) {
      log.error("Error in setting up default connector less managed HarnessCodeRepo integration if applicable for "
              + "accountIdentifier = {} Error = {}",
          accountIdentifier, ex.getMessage(), ex);
    }
  }

  private void seedLayouts(String accountIdentifier) {
    try {
      List<LayoutEntity> layoutEntitiesForAccount =
          layoutEntityRepository.findAllByAccountIdentifier(accountIdentifier);
      if (!isEmpty(layoutEntitiesForAccount)) {
        return;
      }
      String layoutsJson = loadResourceFileAsString("migrations/layouts.json");
      List<LayoutEntity> layoutEntities = readValue(layoutsJson, LayoutEntity.class);
      layoutEntities.forEach(layoutEntity -> {
        layoutEntity.setParentUniqueId(accountIdentifier);
        layoutEntity.setAccountIdentifier(accountIdentifier);
      });
      transactionHelper.performTransaction(() -> {
        layoutEntityRepository.saveAll(layoutEntities);
        return null;
      });
    } catch (Exception ex) {
      log.error(
          "Error in seeding layouts for accountIdentifier = {} Error = {}", accountIdentifier, ex.getMessage(), ex);
    }
  }

  private String loadResourceFileAsString(String resourcePath) {
    try {
      return Resources.toString(Resources.getResource(resourcePath), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("Error in loading resource {} as string. Error = {}", resourcePath, e.getMessage(), e);
      throw new UnexpectedException(
          "Error in loading resource " + resourcePath + " as string. Error = " + e.getMessage());
    }
  }

  private void seedUsersAndUserGroups(String accountIdentifier) {
    try {
      idpToHarnessHelper.seedUsersAndUserGroups(accountIdentifier);
    } catch (Exception ex) {
      log.error("Error in seeding users and user groups for accountIdentifier = {} Error = {}", accountIdentifier,
          ex.getMessage(), ex);
    }
  }
}
