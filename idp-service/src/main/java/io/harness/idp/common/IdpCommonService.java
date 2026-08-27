/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ngmanager.NgConnectorManagerClient;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.SlackChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class IdpCommonService {
  NgConnectorManagerClient ngConnectorManagerClient;
  private String env;
  AccountClient accountClient;
  NotificationClient notificationClient;
  OrganizationClient organizationClient;
  ProjectClient projectClient;
  private String proxyEndPointEnv;
  private String base;
  private static final String PROXY_ENDPOINT_ENV_KEY = "${HARNESS_PROXY_END_POINT}";
  private static final String ENV_BASE_URL_KEY = "${HARNESS_BASE_URL}";

  @Inject
  public IdpCommonService(NgConnectorManagerClient ngConnectorManagerClient, @Named("env") String env,
      AccountClient accountClient, NotificationClient notificationClient,
      @Named("PRIVILEGED") OrganizationClient organizationClient, @Named("PRIVILEGED") ProjectClient projectClient,
      @Named("proxyEndPointEnv") String proxyEndPointEnv, @Named("base") String base) {
    this.ngConnectorManagerClient = ngConnectorManagerClient;
    this.env = env;
    this.accountClient = accountClient;
    this.notificationClient = notificationClient;
    this.organizationClient = organizationClient;
    this.projectClient = projectClient;
    this.proxyEndPointEnv = proxyEndPointEnv;
    this.base = base;
  }

  public void checkUserAuthorization() {
    String userId = SecurityContextBuilder.getPrincipal().getName();
    boolean isAuthorized = getResponse(ngConnectorManagerClient.isHarnessSupportUser(userId));
    if (!isAuthorized) {
      String errorMessage = String.format("User : %s not allowed to do action on IDP module", userId);
      log.error(errorMessage);
      throw new AccessDeniedException(errorMessage, WingsException.USER);
    }
  }

  public <T> Response buildPageResponse(int pageIndex, int pageLimit, long totalElements, T response) {
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, totalElements, pageIndex, pageLimit);
    return responseBuilderWithLinks.entity(response).build();
  }

  public void sendSlackNotification(SlackChannel slackChannel) {
    sendNotification(slackChannel);
  }

  public void sendNotification(NotificationChannel notificationChannel) {
    try {
      AccountDTO accountDTO = getAccountDTO(notificationChannel.getAccountId());
      notificationChannel.getTemplateData().put("accountIdentifier", accountDTO.getIdentifier());
      notificationChannel.getTemplateData().put("accountName", accountDTO.getName());
      notificationChannel.getTemplateData().put("env", env);
      log.info("Sending notification - accountIdentifier = {}, templateId = {}", notificationChannel.getAccountId(),
          notificationChannel.getTemplateId());
      notificationClient.sendNotificationAsync(notificationChannel);
      log.info("Sent notification - accountIdentifier = {}, templateId = {}", notificationChannel.getAccountId(),
          notificationChannel.getTemplateId());
    } catch (Exception ex) {
      log.error("Error in sending notification - accountIdentifier = {}, templateId = {}, error = {}",
          notificationChannel.getAccountId(), notificationChannel.getTemplateId(), ex.getMessage(), ex);
    }
  }

  public AccountDTO getAccountDTO(String accountIdentifier) {
    return CGRestUtils.getResponse(accountClient.getAccountDTO(accountIdentifier));
  }

  public void idpV2Check(String harnessAccount) {
    if (!idpV2Enabled(harnessAccount)) {
      throw new InvalidRequestException("Account not enabled for IDP 2.0");
    }
  }

  public boolean idpV2Enabled(String accountIdentifier) {
    return true;
  }

  public boolean idpScorecardTiersEnabled(String accountIdentifier) {
    return CGRestUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FeatureName.IDP_SCORECARD_TIERS.name(), accountIdentifier));
  }

  public boolean idpStoEnabled(String accountIdentifier) {
    return CGRestUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FeatureName.IDP_STO_INTEGRATION.name(), accountIdentifier));
  }

  public boolean idpApiEndpointExtractionEnabled(String accountIdentifier) {
    return CGRestUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FeatureName.IDP_API_ENDPOINT_EXTRACTION.name(), accountIdentifier));
  }

  public boolean idpCatalogCDAutoDiscoveryEnabled(String accountIdentifier) {
    return CGRestUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FeatureName.IDP_CATALOG_CD_AUTO_DISCOVERY.name(), accountIdentifier));
  }

  public String getOrgName(String harnessAccount, String orgIdentifier) {
    String orgName = null;
    if (!isEmpty(orgIdentifier)) {
      Optional<OrganizationResponse> optionalOrganizationResponse =
          NGRestUtils.getResponse(organizationClient.getOrganization(orgIdentifier, harnessAccount));
      if (optionalOrganizationResponse.isEmpty()) {
        throw new InvalidRequestException("Organization " + orgIdentifier + " not found");
      }
      orgName = optionalOrganizationResponse.get().getOrganization().getName();
    }
    return orgName;
  }

  public String getProjectName(String harnessAccount, String orgIdentifier, String projectIdentifier) {
    String projectName = null;
    if (!isEmpty(projectIdentifier)) {
      Optional<ProjectResponse> optionalProjectResponse =
          NGRestUtils.getResponse(projectClient.getProject(projectIdentifier, harnessAccount, orgIdentifier));
      if (optionalProjectResponse.isEmpty()) {
        throw new InvalidRequestException("Project " + projectIdentifier + " not found");
      }
      projectName = optionalProjectResponse.get().getProject().getName();
    }
    return projectName;
  }

  public String getConfigWithEnvSpecificValuesReplaced(String config) {
    config = config.replace(PROXY_ENDPOINT_ENV_KEY, proxyEndPointEnv);
    config = config.replace(ENV_BASE_URL_KEY, base);
    return config;
  }

  public void allowCreateUpdateDeleteOnHierarchyKindEntity(
      String harnessAccount, boolean metadataEnrichmentByUser, boolean deleteHierarchyKindEntity) {
    if (!isHarnessScopeEnabled(harnessAccount)) {
      throw new InvalidRequestException("Account not enabled for Harness Scope");
    }
    if (!SecurityContextBuilder.getPrincipal().getType().equals(PrincipalType.SERVICE) && !metadataEnrichmentByUser
        && !deleteHierarchyKindEntity) {
      throw new InvalidRequestException("Create / Update / Delete operation is not allowed on hierarchy kind entity");
    }
    if (GitAwareContextHelper.isRemoteEntity()) {
      throw new InvalidRequestException("Hierarchy kind entity cannot be a gitx entity");
    }
  }

  public boolean idpIntegrationsEnabled(String accountIdentifier) {
    return CGRestUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FeatureName.IDP_INTEGRATIONS.name(), accountIdentifier));
  }

  public boolean idpAggregationRulesEnabled(String accountIdentifier) {
    return CGRestUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FeatureName.IDP_AGGREGATION_RULES.name(), accountIdentifier));
  }

  public boolean idpEntityListOptimizedPathEnabled(String accountIdentifier) {
    return CGRestUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FeatureName.IDP_ENTITY_LIST_OPTIMIZED_PATH.name(), accountIdentifier));
  }

  public boolean isLegacyCDFlow(String accountIdentifier) {
    return !idpIntegrationsEnabled(accountIdentifier) && idpCatalogCDAutoDiscoveryEnabled(accountIdentifier);
  }

  public void newFlowCheck(String accountIdentifier) {
    if (isLegacyCDFlow(accountIdentifier)) {
      throw new InvalidRequestException("Account is in legacy CD flow");
    }
  }

  public boolean isHarnessScopeEnabled(String accountIdentifier) {
    return idpAggregationRulesEnabled(accountIdentifier) && !isLegacyCDFlow(accountIdentifier);
  }

  public void harnessScopeCheck(String accountIdentifier) {
    if (!isHarnessScopeEnabled(accountIdentifier)) {
      throw new InvalidRequestException("Account not enabled for Harness Scope");
    }
  }
}
