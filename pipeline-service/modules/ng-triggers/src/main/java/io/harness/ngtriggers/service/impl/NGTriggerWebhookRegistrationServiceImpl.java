/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.apache.commons.lang3.StringUtils.stripStart;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HookEventType;
import io.harness.beans.ScopeInfo;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.exception.ExceptionUtils;
import io.harness.git.GitClientHelper;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.webhook.RegistryWebhookTriggerEventType;
import io.harness.ng.webhook.UpsertRegistryWebhookRequestDTO;
import io.harness.ng.webhook.UpsertRegistryWebhookResponseDTO;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatusData;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatusData.WebhookRegistrationStatusDataBuilder;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.action.HarArtifactAction;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerWebhookRegistrationService;
import io.harness.product.ci.scm.proto.WebhookResponse;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.utils.ConnectorUtils;
import io.harness.webhook.remote.WebhookEventClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerWebhookRegistrationServiceImpl implements NGTriggerWebhookRegistrationService {
  @Inject private final ConnectorUtils connectorUtils;
  @Inject private final NGTriggerElementMapper ngTriggerElementMapper;
  @Inject private final SecretManagerClientService ngSecretService;
  private final WebhookEventClient webhookEventClient;

  @Override

  public WebhookRegistrationStatusData registerWebhook(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    BaseNGAccess ngAccess = BaseNGAccess.builder()
                                .accountIdentifier(ngTriggerEntity.getAccountId())
                                .orgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier()
                                                                         : ngTriggerEntity.getOrgIdentifier())
                                .projectIdentifier(isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier()
                                                                             : ngTriggerEntity.getProjectIdentifier())
                                .build();
    ConnectorDetails connectorDetails;
    if (ngTriggerEntity.getMetadata().getWebhook().getGit() != null
        && Boolean.TRUE.equals(ngTriggerEntity.getMetadata().getWebhook().getGit().getIsHarnessScm())) {
      return handleHarnessScmWebhook(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    }
    if (WebhookTriggerType.HARNESS_ARTIFACT_REGISTRY.getEntityMetadataName().equals(
            ngTriggerEntity.getMetadata().getWebhook().getType())) {
      return handleHarnessArtifactRegistryWebhook(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    }

    try {
      connectorDetails = connectorUtils.getConnectorDetails(
          ngAccess, ngTriggerEntity.getMetadata().getWebhook().getGit().getConnectorIdentifier());
    } catch (Exception ex) {
      log.error("Failed to register webhook, could not fetch connector details", ex);
      WebhookRegistrationStatusDataBuilder metadataBuilder = WebhookRegistrationStatusData.builder();
      metadataBuilder.webhookAutoRegistrationStatus(
          WebhookAutoRegistrationStatus.builder()
              .detailedMessage("Failed to fetch connector details: " + ExceptionUtils.getMessage(ex))
              .registrationResult(WebhookRegistrationStatus.ERROR)
              .build());
      return metadataBuilder.build();
    }
    String url = connectorUtils.retrieveURL(connectorDetails);
    String repoName = ngTriggerEntity.getMetadata().getWebhook().getGit().getRepoName();
    String secretIdentifierRef = ngTriggerEntity.getEncryptedWebhookSecretIdentifier();

    if (connectorUtils.getConnectionType(connectorDetails).equals(GitConnectionType.ACCOUNT)) {
      if (isNotEmpty(repoName)) {
        url = format("%s/%s", stripEnd(url, "/"), stripStart(repoName, "/"));
      } else {
        log.warn("Repo name is empty for account level connector");
      }
    } else if (connectorUtils.getConnectionType(connectorDetails).equals(GitConnectionType.PROJECT)) {
      if (isNotEmpty(repoName)) {
        if (connectorDetails.getConnectorType() == ConnectorType.AZURE_REPO) {
          url = GitClientHelper.getCompleteUrlForProjectLevelAzureConnector(url, repoName);
        }
      } else {
        log.warn("Repo name is empty for project level connector");
      }
    }

    return registerWebhookInternal(
        isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
        isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getAccountId(), url,
        ngTriggerEntity.getMetadata().getWebhook().getGit().getConnectorIdentifier(), secretIdentifierRef);
  }

  private WebhookRegistrationStatusData handleHarnessScmWebhook(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    UpsertWebhookRequestDTO upsertWebhookRequestDTO =
        UpsertWebhookRequestDTO.builder()
            .projectIdentifier(
                isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier())
            .orgIdentifier(
                isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier())
            .accountIdentifier(ngTriggerEntity.getAccountId())
            .repoURL(ngTriggerEntity.getMetadata().getWebhook().getGit().getRepoName())
            .isHarnessScm(true)
            .hookEventType(HookEventType.TRIGGER_EVENTS)
            .build();
    return getWebhookRegistrationStatusData(upsertWebhookRequestDTO);
  }

  private WebhookRegistrationStatusData handleHarnessArtifactRegistryWebhook(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Set<RegistryWebhookTriggerEventType> triggers = new HashSet<>();
    List<HarArtifactAction> actions = ngTriggerEntity.getMetadata().getWebhook().getHarMetadata().getActions();
    if (isNotEmpty(actions)) {
      for (HarArtifactAction action : actions) {
        switch (action) {
          case CREATION:
            triggers.add(RegistryWebhookTriggerEventType.ARTIFACT_CREATION);
            break;
          case DELETION:
            triggers.add(RegistryWebhookTriggerEventType.ARTIFACT_DELETION);
            break;
          default:
            break;
        }
      }
    } else {
      triggers.addAll(List.of(RegistryWebhookTriggerEventType.values()));
    }

    UpsertRegistryWebhookRequestDTO upsertRegistryWebhookRequestDTO =
        UpsertRegistryWebhookRequestDTO.builder()
            .projectIdentifier(
                isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier())
            .orgIdentifier(
                isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier())
            .accountIdentifier(ngTriggerEntity.getAccountId())
            .registry(ngTriggerEntity.getMetadata().getWebhook().getHarMetadata().getRegistryName())
            .triggers(triggers)
            .build();
    return getRegistryWebhookRegistrationStatusData(upsertRegistryWebhookRequestDTO);
  }

  private WebhookRegistrationStatusData registerWebhookInternal(String projectIdentifier, String orgIdentifier,
      String accountIdentifier, String repoUrl, String connectorIdentifierRef, String secretIdentifierRef) {
    UpsertWebhookRequestDTO upsertWebhookRequestDTO = UpsertWebhookRequestDTO.builder()
                                                          .projectIdentifier(projectIdentifier)
                                                          .orgIdentifier(orgIdentifier)
                                                          .accountIdentifier(accountIdentifier)
                                                          .connectorIdentifierRef(connectorIdentifierRef)
                                                          .repoURL(repoUrl)
                                                          .hookEventType(HookEventType.TRIGGER_EVENTS)
                                                          .webhookSecretIdentifierRef(secretIdentifierRef)
                                                          .build();
    return getWebhookRegistrationStatusData(upsertWebhookRequestDTO);
  }

  private WebhookRegistrationStatusData getWebhookRegistrationStatusData(
      UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    UpsertWebhookResponseDTO upsertWebhookResponseDTO = null;

    WebhookRegistrationStatusDataBuilder metadataBuilder = WebhookRegistrationStatusData.builder();

    try {
      upsertWebhookResponseDTO = getResponse(webhookEventClient.upsertWebhook(upsertWebhookRequestDTO));
    } catch (Exception ex) {
      log.error("Failed to register webhook", ex);
      metadataBuilder.webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                        .detailedMessage(ex.getMessage())
                                                        .registrationResult(WebhookRegistrationStatus.ERROR)
                                                        .build());

      return metadataBuilder.build();
    }
    if (upsertWebhookResponseDTO.getStatus() > 300) {
      log.info("Failed to auto register webhook: {}", upsertWebhookResponseDTO.getError());
      metadataBuilder.webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                        .detailedMessage(upsertWebhookResponseDTO.getError())
                                                        .registrationResult(WebhookRegistrationStatus.FAILED)
                                                        .build());

      return metadataBuilder.build();
    }
    WebhookResponse webhookResponse = upsertWebhookResponseDTO.getWebhookResponse();
    if (webhookResponse != null) {
      log.info("Auto registered webhook with following events: {}", webhookResponse.getName());
      metadataBuilder.webhookId(webhookResponse.getId());
    }
    metadataBuilder.webhookAutoRegistrationStatus(
        WebhookAutoRegistrationStatus.builder().registrationResult(WebhookRegistrationStatus.SUCCESS).build());
    return metadataBuilder.build();
  }

  private WebhookRegistrationStatusData getRegistryWebhookRegistrationStatusData(
      UpsertRegistryWebhookRequestDTO upsertWebhookRequestDTO) {
    UpsertRegistryWebhookResponseDTO upsertWebhookResponseDTO;

    WebhookRegistrationStatusDataBuilder metadataBuilder = WebhookRegistrationStatusData.builder();

    try {
      upsertWebhookResponseDTO = getResponse(webhookEventClient.upsertRegistryWebhook(upsertWebhookRequestDTO));
    } catch (Exception ex) {
      log.error("Failed to register webhook", ex);
      metadataBuilder.webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                        .detailedMessage(ex.getMessage())
                                                        .registrationResult(WebhookRegistrationStatus.ERROR)
                                                        .build());

      return metadataBuilder.build();
    }
    if (upsertWebhookResponseDTO.getStatus() > 300) {
      log.info("Failed to auto register webhook: {}", upsertWebhookResponseDTO.getError());
      metadataBuilder.webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                        .detailedMessage(upsertWebhookResponseDTO.getError())
                                                        .registrationResult(WebhookRegistrationStatus.FAILED)
                                                        .build());

      return metadataBuilder.build();
    }
    if (isNotEmpty(upsertWebhookResponseDTO.getWebhookIdentifier())) {
      log.info("Auto registered registry webhook: {}", upsertWebhookResponseDTO.getWebhookName());
      metadataBuilder.webhookId(upsertWebhookResponseDTO.getWebhookIdentifier());
    }
    metadataBuilder.webhookAutoRegistrationStatus(
        WebhookAutoRegistrationStatus.builder().registrationResult(WebhookRegistrationStatus.SUCCESS).build());
    return metadataBuilder.build();
  }
}
