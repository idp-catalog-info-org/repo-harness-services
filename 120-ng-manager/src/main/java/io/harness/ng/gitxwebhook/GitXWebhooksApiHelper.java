/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.gitx.GitXWebhhookRbacPermissionsConstants.GITX_WEBHOOKS_RESOURCE_TYPE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InternalServerErrorException;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GenericWebhookConfig;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.HmacConfig;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.SlackHmacConfig;
import io.harness.gitsync.gitxwebhooks.dtos.SlackWebhookConfig;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookCriteriaDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.entity.GenericWebhookSpec;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.HmacSpec;
import io.harness.gitsync.gitxwebhooks.entity.SlackWebhookSpec;
import io.harness.gitsync.gitxwebhooks.helper.GitXWebhookHelper;
import io.harness.gitsync.gitxwebhooks.mapper.GitXWebhookMapper;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookService;
import io.harness.gitx.GitXWebhhookRbacPermissionsConstants;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.CreateWebhookRequest;
import io.harness.spec.server.ng.v1.model.ListWebhookRequest;
import io.harness.spec.server.ng.v1.model.ListWebhookRequest.WebhookTypeEnum;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.UpdateWebhookRequest;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class GitXWebhooksApiHelper {
  private final GitXWebhookService gitXWebhookService;
  private final AccessControlClient accessControlClient;
  private final GitXWebhookHelper gitXWebhookHelper;
  private final ScopeInfoService scopeResolverService;

  public CreateGitXWebhookResponseDTO createGitXWebhook(
      String harnessAccount, String org, String project, CreateGitXWebhookRequest body) {
    checkForGitXWebhookPermission(
        harnessAccount, org, project, GitXWebhhookRbacPermissionsConstants.GitXWebhhook_CREATE_AND_EDIT);
    CreateGitXWebhookRequestDTO createGitXWebhookRequestDTO = GitXWebhookMapper.buildCreateGitXWebhookRequestDTO(
        Scope.of(harnessAccount, org, project, getParentUniqueIdentifier(harnessAccount, org, project)), body);
    return gitXWebhookService.createGitXWebhook(createGitXWebhookRequestDTO);
  }

  public CreateGitXWebhookResponseDTO createWebhook(
      String harnessAccount, String org, String project, CreateWebhookRequest body) {
    checkForGitXWebhookPermission(
        harnessAccount, org, project, GitXWebhhookRbacPermissionsConstants.GitXWebhhook_CREATE_AND_EDIT);
    CreateGitXWebhookRequestDTO createWebhookRequestDTO = GitXWebhookMapper.buildCreateWebhookRequestDTO(
        Scope.of(harnessAccount, org, project, getParentUniqueIdentifier(harnessAccount, org, project)), body);
    return gitXWebhookService.createGitXWebhook(createWebhookRequestDTO);
  }

  public void checkForGitXWebhookPermission(
      String harnessAccount, String org, String project, String gitXWebhookPermission) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(harnessAccount, org, project),
        Resource.of(GITX_WEBHOOKS_RESOURCE_TYPE, null), gitXWebhookPermission);
  }

  public Optional<GetGitXWebhookResponseDTO> getGitXWebhook(
      String harnessAccount, String org, String project, String gitXWebhookIdentifier) {
    checkForGitXWebhookPermission(harnessAccount, org, project, GitXWebhhookRbacPermissionsConstants.GitXWebhhook_VIEW);
    GetGitXWebhookRequestDTO getGitXWebhookRequestDTO = GitXWebhookMapper.buildGetGitXWebhookRequestDTO(
        Scope.of(harnessAccount, org, project, getParentUniqueIdentifier(harnessAccount, org, project)),
        gitXWebhookIdentifier);
    return gitXWebhookService.getGitXWebhook(getGitXWebhookRequestDTO);
  }

  public UpdateGitXWebhookResponseDTO updateGitXWebhook(
      String harnessAccount, String org, String project, String gitXWebhookIdentifier, UpdateGitXWebhookRequest body) {
    checkForGitXWebhookPermission(
        harnessAccount, org, project, GitXWebhhookRbacPermissionsConstants.GitXWebhhook_CREATE_AND_EDIT);
    UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO = GitXWebhookMapper.buildUpdateGitXWebhookRequestDTO(body);
    return gitXWebhookService.updateGitXWebhook(
        UpdateGitXWebhookCriteriaDTO.builder()
            .scope(Scope.of(harnessAccount, org, project, getParentUniqueIdentifier(harnessAccount, org, project)))
            .webhookIdentifier(gitXWebhookIdentifier)
            .build(),
        updateGitXWebhookRequestDTO);
  }

  public UpdateGitXWebhookResponseDTO updateWebhook(
      String harnessAccount, String org, String project, String gitXWebhookIdentifier, UpdateWebhookRequest body) {
    checkForGitXWebhookPermission(
        harnessAccount, org, project, GitXWebhhookRbacPermissionsConstants.GitXWebhhook_CREATE_AND_EDIT);
    UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO = GitXWebhookMapper.buildUpdateWebhookRequestDTO(body);
    return gitXWebhookService.updateGitXWebhook(
        UpdateGitXWebhookCriteriaDTO.builder()
            .scope(Scope.of(harnessAccount, org, project, getParentUniqueIdentifier(harnessAccount, org, project)))
            .webhookIdentifier(gitXWebhookIdentifier)
            .build(),
        updateGitXWebhookRequestDTO);
  }

  public DeleteGitXWebhookResponseDTO deleteGitXWebhook(
      String harnessAccount, String org, String project, String gitXWebhookIdentifier) {
    checkForGitXWebhookPermission(
        harnessAccount, org, project, GitXWebhhookRbacPermissionsConstants.GitXWebhhook_DELETE);
    DeleteGitXWebhookRequestDTO deleteGitXWebhookRequestDTO = GitXWebhookMapper.buildDeleteGitXWebhookRequestDTO(
        Scope.of(harnessAccount, org, project, getParentUniqueIdentifier(harnessAccount, org, project)),
        gitXWebhookIdentifier);
    return gitXWebhookService.deleteGitXWebhook(deleteGitXWebhookRequestDTO);
  }

  public ListGitXWebhookResponseDTO listGitXWebhooks(
      String harnessAccount, String org, String project, String webhookIdentifier) {
    checkForGitXWebhookPermission(harnessAccount, org, project, GitXWebhhookRbacPermissionsConstants.GitXWebhhook_VIEW);
    ListWebhookRequest listWebhookRequest = new ListWebhookRequest();
    listWebhookRequest.setWebhookType(WebhookTypeEnum.GIT);
    listWebhookRequest.setWebhookIdentifier(webhookIdentifier);
    ListGitXWebhookRequestDTO listGitXWebhookRequestDTO = GitXWebhookMapper.buildListGitXWebhookRequestDTO(
        Scope.of(harnessAccount, org, project, getParentUniqueIdentifier(harnessAccount, org, project)),
        listWebhookRequest);
    List<GitXWebhook> gitXWebhookList = gitXWebhookService.listGitXWebhooks(listGitXWebhookRequestDTO);
    return ListGitXWebhookResponseDTO.builder().gitXWebhooksList(prepareGitXWebhooks(gitXWebhookList)).build();
  }

  public List<GetGitXWebhookResponseDTO> prepareGitXWebhooks(List<GitXWebhook> gitXWebhookList) {
    List<GetGitXWebhookResponseDTO> response = new ArrayList<>();

    for (GitXWebhook webhook : gitXWebhookList) {
      GetGitXWebhookResponseDTO getGitXWebhookResponseDTO = prepareGitXWebhooks(webhook);
      response.add(getGitXWebhookResponseDTO);
    }
    return response;
  }

  public GetGitXWebhookResponseDTO prepareGitXWebhooks(GitXWebhook webhook) {
    GenericWebhookConfig genericWebhookConfig = null;
    SlackWebhookConfig slackWebhookConfig = null;
    String webhookType = null;

    if ((NGCommonEntityConstants.GENERIC_WEBHOOK_TYPE).equals(webhook.getWebhookType())) {
      webhookType = NGCommonEntityConstants.GENERIC_WEBHOOK_TYPE;
      HmacConfig hmacConfig = null;
      GenericWebhookSpec genericWebhookSpec = (GenericWebhookSpec) webhook.getSpec();
      if (genericWebhookSpec != null) {
        if ((NGCommonEntityConstants.HMAC_AUTH_TYPE_WEBHOOK).equals(genericWebhookSpec.getAuthType())) {
          HmacSpec hmacSpec = (HmacSpec) genericWebhookSpec.getAuthSpec();
          hmacConfig = HmacConfig.builder()
                           .header(hmacSpec.getHeader())
                           .secretKey(hmacSpec.getSecretKey())
                           .hashAlgorithm(hmacSpec.getHashAlgorithm())
                           .build();
        }

        genericWebhookConfig =
            GenericWebhookConfig.builder()
                .authType(genericWebhookSpec.getAuthType())
                .webhookUrl(gitXWebhookHelper.generateWebhookUrl(webhook.getAccountIdentifier(),
                    webhook.getOrgIdentifier(), webhook.getProjectIdentifier(), webhook.getIdentifier()))
                .hmacConfig(hmacConfig)
                .build();
      }
    } else if ((NGCommonEntityConstants.SLACK_WEBHOOK_TYPE).equals(webhook.getWebhookType())) {
      webhookType = NGCommonEntityConstants.SLACK_WEBHOOK_TYPE;
      SlackHmacConfig hmacConfig = null;
      SlackWebhookSpec slackWebhookSpec = (SlackWebhookSpec) webhook.getSpec();
      if (slackWebhookSpec != null) {
        if ((NGCommonEntityConstants.HMAC_AUTH_TYPE_WEBHOOK).equals(slackWebhookSpec.getAuthType())) {
          HmacSpec hmacSpec = (HmacSpec) slackWebhookSpec.getAuthSpec();
          hmacConfig = SlackHmacConfig.builder().secretKey(hmacSpec.getSecretKey()).build();
        }

        slackWebhookConfig =
            SlackWebhookConfig.builder()
                .authType(slackWebhookSpec.getAuthType())
                .webhookUrl(gitXWebhookHelper.generateWebhookUrl(webhook.getAccountIdentifier(),
                    webhook.getOrgIdentifier(), webhook.getProjectIdentifier(), webhook.getIdentifier()))
                .hmacConfig(hmacConfig)
                .build();
      }
    } else {
      webhookType = NGCommonEntityConstants.GIT_WEBHOOK_TYPE;
    }

    return GetGitXWebhookResponseDTO.builder()
        .accountIdentifier(webhook.getAccountIdentifier())
        .webhookIdentifier(webhook.getIdentifier())
        .webhookName(webhook.getName())
        .connectorRef(webhook.getConnectorRef())
        .folderPaths(webhook.getFolderPaths())
        .isEnabled(webhook.getIsEnabled())
        .repoName(webhook.getRepoName())
        .eventTriggerTime(webhook.getLastEventTriggerTime())
        .webhookType(webhookType)
        .genericWebhookConfig(genericWebhookConfig)
        .slackWebhookConfig(slackWebhookConfig)
        .build();
  }

  public String getParentUniqueIdentifier(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    ScopeInfo scopeInfo = null;
    try {
      scopeInfo = scopeResolverService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    } catch (Exception ex) {
      log.error("Error occurred while fetching scopeInfo", ex);
      throw new InternalServerErrorException(
          "Exception occurred while fetching scope. Please contact harness customer care.", ex);
    }
    return scopeInfo.getUniqueId();
  }
}
