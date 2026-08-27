/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.AMZ_SUBSCRIPTION_CONFIRMATION_TYPE;
import static io.harness.constants.Constants.X_AMZ_SNS_MESSAGE_TYPE;
import static io.harness.constants.Constants.X_BIT_BUCKET_EVENT;
import static io.harness.constants.Constants.X_EVENT_BRIDGE_TRIGGER;
import static io.harness.constants.Constants.X_GIT_HUB_EVENT;
import static io.harness.constants.Constants.X_GIT_LAB_EVENT;
import static io.harness.constants.Constants.X_HARNESS_ARTIFACT_REGISTRY_TRIGGER;
import static io.harness.constants.Constants.X_HARNESS_SYSTEM_EVENT_TRIGGER;
import static io.harness.constants.Constants.X_HARNESS_TRIGGER;
import static io.harness.constants.Constants.X_HARNESS_TRIGGER_ID;
import static io.harness.constants.Constants.X_VSS_HEADER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.Constants.GIT_WEBHOOK_POLLING_FOR_TRIGGERS;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MULTI_REGION_ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.SYSTEM_EVENT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.AZURE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.BITBUCKET;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.CUSTOM;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.EVENT_RELAY;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.GITLAB;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.HARNESS;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.HARNESS_ARTIFACT_REGISTRY;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.SYSTEM_EVENTS;
import static io.harness.security.PrincipalProtoMapper.toPrincipalDTO;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TriggerException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.beans.config.NGTriggerConfig;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.BuildDetails;
import io.harness.ngtriggers.beans.dto.LastTriggerExecutionDetails;
import io.harness.ngtriggers.beans.dto.NGTriggerCatalogDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO.NGTriggerDetailsResponseDTOBuilder;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO.NGTriggerResponseDTOBuilder;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.WebhookDetails;
import io.harness.ngtriggers.beans.dto.WebhookDetails.WebhookDetailsBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityBuilder;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent.TriggerCustomWebhookEventBuilder;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventBuilder;
import io.harness.ngtriggers.beans.entity.metadata.BuildMetadata;
import io.harness.ngtriggers.beans.entity.metadata.CronMetadata;
import io.harness.ngtriggers.beans.entity.metadata.CustomMetadata;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.HarnessArtifactRegistryMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata.WebhookMetadataBuilder;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.BuildAware;
import io.harness.ngtriggers.beans.source.artifact.HelmManifestSpec;
import io.harness.ngtriggers.beans.source.artifact.ManifestTypeSpec;
import io.harness.ngtriggers.beans.source.v1.NGTriggerYamlSimplSource;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.MultiRegionArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.event.HarArtifactEventSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.harnessartifactregistry.event.HarTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.HarnessArtifactRegistrySpec;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.exceptions.yaml.InvalidTriggerYamlException;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.helpers.WebhookConfigHelper;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PipelineEventHook;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.utils.YamlPipelineUtils;
import io.harness.webhook.WebhookConfigProvider;
import io.harness.webhook.WebhookHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerElementMapper {
  public static final int DAYS_BEFORE_CURRENT_DATE = 6;
  private TriggerEventHistoryRepository triggerEventHistoryRepository;
  private WebhookEventPayloadParser webhookEventPayloadParser;
  private WebhookConfigProvider webhookConfigProvider;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private ConnectorResourceClient connectorResourceClient;
  private final io.harness.ngtriggers.mapper.v1.NGTriggerElementMapper ngTriggerElementMapperV1;
  private final NGSettingsClient settingsClient;
  private final ScopeResolutionHelper scopeResolutionHelper;
  public NGTriggerConfigV2 toTriggerConfigV2(String yaml) {
    try {
      return YamlPipelineUtils.read(yaml, NGTriggerConfigV2.class);
    } catch (IOException e) {
      throw new InvalidRequestException(e.getMessage()); // update this message
    }
  }

  public NGTriggerConfigV2 toTriggerConfigV2ForYamlSimplification(
      NGTriggerEntity entity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerYamlSimplSource source = ngTriggerElementMapperV1.toNGTriggerYamlSimplSource(entity.getYaml());
    return NGTriggerConfigV2.builder()
        .pipelineIdentifier(entity.getTargetIdentifier())
        .identifier(entity.getIdentifier())
        .projectIdentifier(isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : entity.getProjectIdentifier())
        .orgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : entity.getOrgIdentifier())
        .encryptedWebhookSecretIdentifier(entity.getEncryptedWebhookSecretIdentifier())
        .enabled(entity.getEnabled())
        .description(entity.getDescription())
        .inputYaml(source.getInputs())
        .inputSetRefs(source.getInput_set_refs())
        .name(entity.getName())
        .pipelineBranchName(entity.getPipelineBranchName())
        .tags(TagMapper.convertToMap(entity.getTags()))
        .source(ngTriggerElementMapperV1.toNGTriggerSourceV2(source))
        .stagesToExecute(source.getExecute_stages())
        .build();
  }

  public String generateNgTriggerConfigV2Yaml(NGTriggerEntity ngTriggerEntity,
      boolean throwExceptionIfYamlConversionFails, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String yaml = ngTriggerEntity.getYaml();

    try {
      if (ngTriggerEntity.getYmlVersion() == null || ngTriggerEntity.getYmlVersion().longValue() < 2) {
        NGTriggerConfigV2 ngTriggerConfigV2 = toTriggerConfigV2(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
        ngTriggerConfigV2.setEnabled(ngTriggerEntity.getEnabled());
        yaml = generateNgTriggerConfigV2Yaml(ngTriggerConfigV2);
      }
    } catch (Exception e) {
      log.error("Failed while converting Trigger V0 yaml to V2 Yaml. \n" + yaml, e);
      if (throwExceptionIfYamlConversionFails) {
        throw new TriggerException("Failed while converting trigger yaml from version V0 to V2" + e, USER_SRE);
      }
    }
    return yaml;
  }

  public String generateNgTriggerConfigV2Yaml(NGTriggerConfigV2 ngTriggerConfigV2) {
    try {
      return YamlUtils.writeYamlString(ngTriggerConfigV2);
    } catch (Exception e) {
      throw new TriggerException("Failed while converting trigger yaml with version V0" + e, USER_SRE);
    }
  }

  public NGTriggerConfigV2 toTriggerConfigV2WithoutYmlVersion(NGTriggerEntity ngTriggerEntity) {
    boolean isParentIdQueryingEnabled = true;
    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(ngTriggerEntity.getAccountId(), ngTriggerEntity.getParentUniqueId());

    if (HarnessYamlVersion.V1.equals(ngTriggerEntity.getHarnessVersion())) {
      return toTriggerConfigV2ForYamlSimplification(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    } else {
      return toTriggerConfigV2(ngTriggerEntity.getYaml());
    }
  }

  public NGTriggerConfigV2 toTriggerConfigV2(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    if (HarnessYamlVersion.V1.equals(ngTriggerEntity.getHarnessVersion())) {
      return toTriggerConfigV2ForYamlSimplification(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    }
    try {
      if (ngTriggerEntity.getYmlVersion() == null || ngTriggerEntity.getYmlVersion() < 2) {
        NGTriggerConfig ngTriggerConfig = YamlPipelineUtils.read(ngTriggerEntity.getYaml(), NGTriggerConfig.class);
        return NgTriggerConfigAdaptor.convertFromV0ToV2(
            ngTriggerConfig, ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
      } else {
        return toTriggerConfigV2(ngTriggerEntity.getYaml());
      }
    } catch (Exception e) {
      throw new TriggerException("Failed while generating NGTriggerConfigV2", e, USER_SRE); // update this message
    }
  }

  public TriggerDetails toTriggerDetails(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String parentUniqueId, String yaml, boolean withServiceV2) {
    NGTriggerConfigV2 config = toTriggerConfigV2(yaml);
    NGTriggerEntity ngTriggerEntity = toTriggerEntity(
        accountIdentifier, orgIdentifier, projectIdentifier, parentUniqueId, config, yaml, withServiceV2);
    return TriggerDetails.builder().ngTriggerConfigV2(config).ngTriggerEntity(ngTriggerEntity).build();
  }

  public TriggerDetails toTriggerDetails(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerConfigV2 config;
    if (HarnessYamlVersion.V0.equals(ngTriggerEntity.getHarnessVersion())) {
      config = toTriggerConfigV2(ngTriggerEntity.getYaml());
    } else {
      config = toTriggerConfigV2ForYamlSimplification(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    }
    return TriggerDetails.builder().ngTriggerConfigV2(config).ngTriggerEntity(ngTriggerEntity).build();
  }
  @Deprecated
  public TriggerDetails mergeTriggerEntity(NGTriggerEntity existingEntity, String newYaml) {
    NGTriggerConfigV2 config = toTriggerConfigV2(newYaml);
    NGTriggerEntity entity = toTriggerEntity(existingEntity.getAccountId(), existingEntity.getOrgIdentifier(),
        existingEntity.getProjectIdentifier(), existingEntity.getIdentifier(), newYaml,
        existingEntity.getWithServiceV2());
    // Currently Method is not being used, hence null.
    copyEntityFieldsOutsideOfYml(existingEntity, entity, null, false);
    return TriggerDetails.builder().ngTriggerConfigV2(config).ngTriggerEntity(entity).build();
  }

  public void copyEntityFieldsOutsideOfYml(NGTriggerEntity existingEntity, NGTriggerEntity newEntity,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    preserveExecutorFields(existingEntity, newEntity);

    if (newEntity.getType() == ARTIFACT || newEntity.getType() == MANIFEST) {
      copyFields(existingEntity, newEntity);
      return;
    }
    if (newEntity.getType() == MULTI_REGION_ARTIFACT) {
      /* MultiRegionArtifact triggers need different handling here, since we can't just copy the list of BuildMetadata
      from the previously existing entity (e.g.: The number of artifacts it is listening for could have changed in
      the update). So we only copy the previously existing signatures to `ngTriggerEntity.metadata.signatures`.
      These will be used to unsubscribe from the previously subscribed poolingDocuments.
      At a later step (in NGTriggerServiceImpl:stampPollingInfoForMultiArtifactTrigger) we reset the signatures
      with the actual new signatures for this trigger. */
      copyFieldsForMultiArtifactTrigger(existingEntity, newEntity);
    }

    // Currently, enabled only for GITHUB
    if (newEntity.getType() == WEBHOOK) {
      if (!GITHUB.getEntityMetadataName().equalsIgnoreCase(existingEntity.getMetadata().getWebhook().getType())) {
        return;
      }

      // Check if polling was previously enabled or if it is being enabled now
      String pollInterval =
          isEmpty(existingEntity.getPollInterval()) ? newEntity.getPollInterval() : existingEntity.getPollInterval();

      boolean isWebhookPollingEnabled = isWebhookPollingEnabled(existingEntity.getType(), existingEntity.getAccountId(),
          isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : existingEntity.getOrgIdentifier(),
          isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : existingEntity.getProjectIdentifier(),
          pollInterval, scopeInfo, isParentIdQueryingEnabled);

      if (isWebhookPollingEnabled) {
        if (isNotEmpty(existingEntity.getPollInterval()) && isEmpty(newEntity.getPollInterval())) {
          throw new InvalidRequestException(
              String.format("Polling is previously enabled with a value %s. The value cannot be empty or null. "
                      + "Please enter 0 to unsubscribe or a value greater than 2m and less than 60m to subscribe",
                  existingEntity.getPollInterval()));
        }
        // Copy entities for webhook git polling
        copyFields(existingEntity, newEntity);
      }
    }
  }

  private void copyFields(NGTriggerEntity existingEntity, NGTriggerEntity newEntity) {
    if (existingEntity.getMetadata().getBuildMetadata() == null) {
      log.info("Previously polling was not enabled. Trigger {} updated with polling", newEntity.getIdentifier());
      return;
    }
    PollingConfig existingPollingConfig = existingEntity.getMetadata().getBuildMetadata().getPollingConfig();

    if (existingPollingConfig != null && isNotEmpty(existingPollingConfig.getSignature())) {
      newEntity.getMetadata().getBuildMetadata().getPollingConfig().setSignature(existingPollingConfig.getSignature());
    }
    if (existingPollingConfig != null && isNotEmpty(existingPollingConfig.getPollingDocId())) {
      newEntity.getMetadata().getBuildMetadata().getPollingConfig().setPollingDocId(
          existingPollingConfig.getPollingDocId());
    }
  }

  private void copyFieldsForMultiArtifactTrigger(NGTriggerEntity existingEntity, NGTriggerEntity newEntity) {
    if (existingEntity.getMetadata().getSignatures() == null) {
      log.info("Previously polling was not enabled. Trigger {} updated with polling", newEntity.getIdentifier());
      return;
    }
    List<String> previousSignatures = existingEntity.getMetadata().getSignatures();

    if (isNotEmpty(previousSignatures)) {
      newEntity.getMetadata().setSignatures(existingEntity.getMetadata().getSignatures());
    }
  }

  private void preserveExecutorFields(NGTriggerEntity existingEntity, NGTriggerEntity newEntity) {
    if (newEntity.getExecutorInfo() == null && existingEntity.getExecutorInfo() != null) {
      newEntity.setExecutorInfo(existingEntity.getExecutorInfo());
    }
  }

  public NGTriggerEntity toTriggerEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, String yaml, boolean withServiceV2) {
    NGTriggerConfigV2 config = toTriggerConfigV2(yaml);
    if (!identifier.equals(config.getIdentifier())) {
      throw new InvalidRequestException("Identifier in url and yaml do not match");
    }
    return toTriggerEntity(accountIdentifier, orgIdentifier, projectIdentifier, "", config, yaml, withServiceV2);
  }

  public NGTriggerEntity toTriggerEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String parentUniqueId, NGTriggerConfigV2 config, String yaml, boolean withServiceV2) {
    NGTriggerEntityBuilder entityBuilder =
        NGTriggerEntity.builder()
            .name(config.getName())
            .identifier(config.getIdentifier())
            .description(config.getDescription())
            .yaml(yaml)
            .type(config.getSource().getType())
            .accountId(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .parentUniqueId(parentUniqueId)
            .targetIdentifier(config.getPipelineIdentifier())
            .targetType(TargetType.PIPELINE)
            .metadata(toMetadata(config.getSource(), accountIdentifier, orgIdentifier, projectIdentifier))
            .enabled(config.getEnabled())
            .pollInterval(config.getSource().getPollInterval() != null ? config.getSource().getPollInterval() : EMPTY)
            .webhookId(config.getSource().getWebhookId())
            .withServiceV2(withServiceV2)
            .tags(TagMapper.convertToList(config.getTags()))
            .encryptedWebhookSecretIdentifier(config.getEncryptedWebhookSecretIdentifier())
            .stagesToExecuteV2(config.getStagesToExecute())
            .pipelineBranchName(config.getPipelineBranchName());

    if (config.getSource().getType() == NGTriggerType.SCHEDULED) {
      entityBuilder.nextIterations(new ArrayList<>());
      entityBuilder.cronAndSemantics(
          pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_CRON_TRIGGER_AND_SEMANTICS));
    }
    NGTriggerEntity entity = entityBuilder.build();
    if (config.getSource().getType() == NGTriggerType.SCHEDULED) {
      List<Long> nextIterations = entity.recalculateNextIterations("unused", true, 0);
      if (!nextIterations.isEmpty()) {
        entity.setNextIterations(nextIterations);
      }
    }
    return entity;
  }

  public NGTriggerMetadata toMetadata(
      NGTriggerSourceV2 triggerSource, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    switch (triggerSource.getType()) {
      case WEBHOOK:
        WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) triggerSource.getSpec();

        WebhookMetadataBuilder metadata = WebhookMetadata.builder();
        if (webhookTriggerConfig.getType() != null) {
          metadata.type(webhookTriggerConfig.getType().getEntityMetadataName());
        }

        if (webhookTriggerConfig.getType() == CUSTOM) {
          metadata.custom(CustomMetadata.builder().build());
        } else if (WebhookConfigHelper.isGitSpec(webhookTriggerConfig)) {
          metadata.git(prepareGitMetadata(webhookTriggerConfig));
        } else if (webhookTriggerConfig.getType() == HARNESS_ARTIFACT_REGISTRY) {
          HarnessArtifactRegistrySpec harnessArtifactRegistrySpec =
              (HarnessArtifactRegistrySpec) webhookTriggerConfig.getSpec();
          if (harnessArtifactRegistrySpec.getType() == HarTriggerEvent.ARTIFACT) {
            HarArtifactEventSpec harArtifactEventSpec = (HarArtifactEventSpec) harnessArtifactRegistrySpec.getSpec();
            metadata.harMetadata(HarnessArtifactRegistryMetadata.builder()
                                     .registryName(harArtifactEventSpec.getRegistryName())
                                     .actions(harArtifactEventSpec.getActions())
                                     .build());
          }
        }

        if (webhookTriggerConfig.getType() == GITHUB
            && isWebhookPollingEnabled(triggerSource.getType(), accountIdentifier, orgIdentifier, projectIdentifier,
                triggerSource.getPollInterval(), null, false)) {
          return NGTriggerMetadata.builder()
              .webhook(metadata.build())
              .buildMetadata(
                  BuildMetadata.builder()
                      .type(WEBHOOK)
                      .pollingConfig(PollingConfig.builder().buildRef(EMPTY).signature(generateUuid()).build())
                      .build())
              .build();
        }

        return NGTriggerMetadata.builder().webhook(metadata.build()).build();
      case SCHEDULED:
        ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) triggerSource.getSpec();
        CronTriggerSpec cronTriggerSpec = (CronTriggerSpec) scheduledTriggerConfig.getSpec();
        String cronExpressionType = StringUtils.isBlank(cronTriggerSpec.getType()) ? "UNIX" : cronTriggerSpec.getType();
        CronMetadata cronMetadata =
            CronMetadata.builder().expression(cronTriggerSpec.getExpression()).type(cronExpressionType).build();
        cronMetadata.setTimezone(cronTriggerSpec.getTimezone());
        return NGTriggerMetadata.builder().cron(cronMetadata).build();
      case ARTIFACT:
        ArtifactTypeSpec artifactTypeSpec = ((ArtifactTriggerConfig) triggerSource.getSpec()).getSpec();
        String artifactSourceType = artifactTypeSpec.getClass().getName();

        return NGTriggerMetadata.builder()
            .buildMetadata(BuildMetadata.builder()
                               .type(ARTIFACT)
                               .buildSourceType(artifactSourceType)
                               .pollingConfig(PollingConfig.builder().buildRef(EMPTY).signature(generateUuid()).build())
                               .build())
            .build();
      case MULTI_REGION_ARTIFACT:
        MultiRegionArtifactTriggerConfig multiRegionArtifactTriggerConfig =
            (MultiRegionArtifactTriggerConfig) triggerSource.getSpec();
        return NGTriggerMetadata.builder()
            .multiBuildMetadata(
                multiRegionArtifactTriggerConfig.getSources()
                    .stream()
                    .map(source
                        -> BuildMetadata.builder()
                               .type(ARTIFACT)
                               .buildSourceType(source.getClass().getName())
                               .pollingConfig(PollingConfig.builder().buildRef(EMPTY).signature(generateUuid()).build())
                               .build())
                    .collect(Collectors.toList()))
            .build();
      case MANIFEST:
        ManifestTypeSpec manifestTypeSpec = ((ManifestTriggerConfig) triggerSource.getSpec()).getSpec();
        String manifestSourceType = null;
        if (HelmManifestSpec.class.isAssignableFrom(manifestTypeSpec.getClass())) {
          manifestSourceType = manifestTypeSpec.getClass().getName();
        }

        return NGTriggerMetadata.builder()
            .buildMetadata(BuildMetadata.builder()
                               .type(MANIFEST)
                               .buildSourceType(manifestSourceType)
                               .pollingConfig(PollingConfig.builder().buildRef(EMPTY).signature(generateUuid()).build())
                               .build())
            .build();
      case SYSTEM_EVENT:
        return NGTriggerMetadata.builder()
            .webhook(WebhookMetadata.builder().type(io.harness.ngtriggers.Constants.SYSTEM_EVENTS_WEBHOOK).build())
            .build();
      default:
        throw new InvalidRequestException("Type " + triggerSource.getType().toString() + " is invalid");
    }
  }

  private boolean isWebhookPollingEnabled(NGTriggerType type, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pollInterval, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return type == NGTriggerType.WEBHOOK
        && shouldGitWebhookPolling(
            accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, isParentIdQueryingEnabled)
        && !StringUtils.isEmpty(pollInterval);
  }

  public Boolean isGitWebhookPollingSettingEnabled(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String response;
    try {
      response = NGRestUtils
                     .getResponse(settingsClient.getSetting(GIT_WEBHOOK_POLLING_FOR_TRIGGERS,
                         isParentIdQueryingEnabled ? scopeInfo.getAccountIdentifier() : accountIdentifier,
                         isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : orgIdentifier,
                         isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : projectIdentifier))
                     .getValue();
    } catch (Exception ex) {
      log.error("Failed to fetch setting {} for accountId {} orgId {} and projectId {}",
          GIT_WEBHOOK_POLLING_FOR_TRIGGERS,
          isParentIdQueryingEnabled ? scopeInfo.getAccountIdentifier() : accountIdentifier,
          isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : orgIdentifier,
          isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : projectIdentifier, ex);
      return false;
    }
    return Objects.equals(response, "true");
  }

  public Boolean shouldGitWebhookPolling(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    return pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.CD_GIT_WEBHOOK_POLLING)
        || isGitWebhookPollingSettingEnabled(
            accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo, isParentIdQueryingEnabled);
  }

  @VisibleForTesting
  GitMetadata prepareGitMetadata(WebhookTriggerConfigV2 webhookTriggerConfig) {
    if (webhookTriggerConfig == null) {
      return null;
    }

    GitAware gitAware = WebhookConfigHelper.retrieveGitAware(webhookTriggerConfig);
    if (gitAware != null) {
      boolean isHarnessScm = HARNESS.equals(webhookTriggerConfig.getType());
      return GitMetadata.builder()
          .connectorIdentifier(gitAware.fetchConnectorRef())
          .repoName(gitAware.fetchRepoName())
          .isHarnessScm(isHarnessScm)
          .build();
    }

    return null;
  }

  public NGTriggerResponseDTO toResponseDTO(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerResponseDTOBuilder ngTriggerResponseDTOBuilder = NGTriggerResponseDTO.builder();
    String webhookUrl = generateWebhookUrl(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    if (EmptyPredicate.isNotEmpty(webhookUrl)) {
      ngTriggerResponseDTOBuilder.webhookUrl(webhookUrl);
    }
    return ngTriggerResponseDTOBuilder.name(ngTriggerEntity.getName())
        .identifier(ngTriggerEntity.getIdentifier())
        .description(ngTriggerEntity.getDescription())
        .type(ngTriggerEntity.getType())
        .accountIdentifier(isParentIdQueryingEnabled && scopeInfo != null && scopeInfo.getAccountIdentifier() != null
                ? scopeInfo.getAccountIdentifier()
                : ngTriggerEntity.getAccountId())
        .orgIdentifier(isParentIdQueryingEnabled && scopeInfo != null && scopeInfo.getOrgIdentifier() != null
                ? scopeInfo.getOrgIdentifier()
                : ngTriggerEntity.getOrgIdentifier())
        .projectIdentifier(isParentIdQueryingEnabled && scopeInfo != null && scopeInfo.getProjectIdentifier() != null
                ? scopeInfo.getProjectIdentifier()
                : ngTriggerEntity.getProjectIdentifier())
        .targetIdentifier(ngTriggerEntity.getTargetIdentifier())
        .version(ngTriggerEntity.getVersion())
        .yaml(generateNgTriggerConfigV2Yaml(ngTriggerEntity, true, scopeInfo, isParentIdQueryingEnabled))
        .enabled(ngTriggerEntity.getEnabled() == null || ngTriggerEntity.getEnabled())
        .errorResponse(false)
        .stagesToExecute(getStagesToExecute(ngTriggerEntity))
        .yamlVersion(ngTriggerEntity.getHarnessVersion())
        .executorInfo(ngTriggerEntity.getExecutorInfo())
        .build();
  }

  public NGTriggerResponseDTO toErrorDTO(
      InvalidTriggerYamlException e, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    NGTriggerEntity ngTriggerEntity = e.getTriggerDetails().getNgTriggerEntity();
    return NGTriggerResponseDTO.builder()
        .name(ngTriggerEntity.getName())
        .identifier(ngTriggerEntity.getIdentifier())
        .description(ngTriggerEntity.getDescription())
        .type(ngTriggerEntity.getType())
        .orgIdentifier(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier())
        .projectIdentifier(
            isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier())
        .targetIdentifier(ngTriggerEntity.getTargetIdentifier())
        .version(ngTriggerEntity.getVersion())
        .yaml(generateNgTriggerConfigV2Yaml(ngTriggerEntity, true, scopeInfo, isParentIdQueryingEnabled))
        .enabled(ngTriggerEntity.getEnabled() == null || ngTriggerEntity.getEnabled())
        .errors(e.getErrors())
        .errorResponse(true)
        .stagesToExecute(getStagesToExecute(ngTriggerEntity))
        .yamlVersion(ngTriggerEntity.getHarnessVersion())
        .executorInfo(ngTriggerEntity.getExecutorInfo())
        .build();
  }

  public NGTriggerCatalogDTO toCatalogDTO(List<TriggerCatalogItem> list) {
    return NGTriggerCatalogDTO.builder().catalog(list).build();
  }

  public TriggerWebhookEventBuilder toNGTriggerWebhookEvent(WebhookDTO webhookDTO, List<HeaderConfig> headerConfigs) {
    ParseWebhookResponse parsedResponse = webhookDTO.getParsedResponse();
    String orgIdentifier = null;
    String projectIdentifier = null;
    if (parsedResponse != null && parsedResponse.hasSystemEvent()) {
      PipelineEventHook pipelineEvent = parsedResponse.getSystemEvent().getPipelineEvent();
      orgIdentifier = pipelineEvent.getOrgIdentifier();
      projectIdentifier = pipelineEvent.getProjectIdentifier();
    }
    return toNGTriggerWebhookEvent(webhookDTO.getAccountId(), orgIdentifier, projectIdentifier,
        webhookDTO.getJsonPayload(), headerConfigs, webhookDTO.getPrincipal());
  }

  public TriggerWebhookEventBuilder toNGTriggerWebhookEvent(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String payload, List<HeaderConfig> headerConfigs,
      io.harness.security.Principal principal) {
    WebhookTriggerType webhookTriggerType;
    Map<String, List<String>> headers =
        headerConfigs.stream().collect(Collectors.toMap(HeaderConfig::getKey, HeaderConfig::getValues));
    boolean isConfirmationMessage = false;
    if (webhookEventPayloadParser.containsHeaderKey(headers, X_EVENT_BRIDGE_TRIGGER)) {
      webhookTriggerType = EVENT_RELAY;
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_GIT_HUB_EVENT)) {
      webhookTriggerType = GITHUB;
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_GIT_LAB_EVENT)) {
      webhookTriggerType = GITLAB;
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_BIT_BUCKET_EVENT)) {
      webhookTriggerType = BITBUCKET;
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_AMZ_SNS_MESSAGE_TYPE)) {
      webhookTriggerType = AWS_CODECOMMIT;
      List<String> headerValues = webhookEventPayloadParser.getHeaderValue(headers, X_AMZ_SNS_MESSAGE_TYPE);
      if (isNotEmpty(headerValues)) {
        isConfirmationMessage = headerValues.stream().anyMatch(AMZ_SUBSCRIPTION_CONFIRMATION_TYPE::equalsIgnoreCase);
      }
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_VSS_HEADER)) {
      webhookTriggerType = AZURE;
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_HARNESS_TRIGGER)) {
      webhookTriggerType = HARNESS;
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_HARNESS_ARTIFACT_REGISTRY_TRIGGER)) {
      webhookTriggerType = HARNESS_ARTIFACT_REGISTRY;
    } else if (webhookEventPayloadParser.containsHeaderKey(headers, X_HARNESS_SYSTEM_EVENT_TRIGGER)) {
      webhookTriggerType = SYSTEM_EVENTS;
    } else {
      if (isEmpty(accountIdentifier) || isEmpty(orgIdentifier) || isEmpty(projectIdentifier)) {
        throw new InvalidRequestException(
            "AccountIdentifier, OrgIdentifier, ProjectIdentifier can not be null for custom webhook executions");
      }
      webhookTriggerType = CUSTOM;
    }
    TriggerWebhookEventBuilder triggerWebhookEventBuilder =
        TriggerWebhookEvent.builder()
            .accountId(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .sourceRepoType(webhookTriggerType.getEntityMetadataName())
            .headers(headerConfigs)
            .payload(payload)
            .isSubscriptionConfirmation(isConfirmationMessage);
    if (principal != null
        && !principal.getPrincipalCase().equals(io.harness.security.Principal.PrincipalCase.PRINCIPAL_NOT_SET)) {
      triggerWebhookEventBuilder.principal(toPrincipalDTO(accountIdentifier, principal));
    }

    HeaderConfig customTriggerIdentifier = headerConfigs.stream()
                                               .filter(header -> header.getKey().equalsIgnoreCase(X_HARNESS_TRIGGER_ID))
                                               .findAny()
                                               .orElse(null);

    if (customTriggerIdentifier != null && isNotBlank(customTriggerIdentifier.getValues().get(0))) {
      triggerWebhookEventBuilder.triggerIdentifier(customTriggerIdentifier.getValues().get(0));
    }

    return triggerWebhookEventBuilder;
  }

  public TriggerWebhookEventBuilder toNGTriggerWebhookEventForCustom(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String triggerIdentifier, String payload,
      List<HeaderConfig> headerConfigs) {
    WebhookTriggerType webhookTriggerType = CUSTOM;

    TriggerWebhookEventBuilder triggerWebhookEventBuilder =
        TriggerWebhookEvent.builder()
            .accountId(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .triggerIdentifier(triggerIdentifier)
            .pipelineIdentifier(pipelineIdentifier)
            .sourceRepoType(webhookTriggerType.getEntityMetadataName())
            .headers(headerConfigs)
            .payload(payload);
    if (!pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.CDS_NG_SERVICE_PRINCIPAL_FOR_CUSTOM_WEBHOOK)) {
      /* If Feature flag CDS_NG_SERVICE_PRINCIPAL_FOR_CUSTOM_WEBHOOK is enabled, it means we should not set
         user's principal for trigger execution.
       */
      triggerWebhookEventBuilder.principal(SecurityContextBuilder.getPrincipal());
    }
    return triggerWebhookEventBuilder;
  }

  public TriggerCustomWebhookEventBuilder toNGTriggerCustomWebhookEventForCustomTrigger(
      TriggerWebhookEvent triggerWebhookEvent, String status) {
    WebhookTriggerType webhookTriggerType = CUSTOM;
    TriggerCustomWebhookEventBuilder triggerWebhookEventBuilder =
        TriggerCustomWebhookEvent.builder()
            .accountId(triggerWebhookEvent.getAccountId())
            .orgIdentifier(triggerWebhookEvent.getOrgIdentifier())
            .projectIdentifier(triggerWebhookEvent.getProjectIdentifier())
            .parentUniqueId(triggerWebhookEvent.getParentUniqueId())
            .triggerIdentifier(triggerWebhookEvent.getTriggerIdentifier())
            .pipelineIdentifier(triggerWebhookEvent.getPipelineIdentifier())
            .sourceRepoType(webhookTriggerType.getEntityMetadataName())
            .processingStatus(status)
            .headers(triggerWebhookEvent.getHeaders())
            .payload(triggerWebhookEvent.getPayload());
    if (!pmsFeatureFlagService.isEnabled(
            triggerWebhookEvent.getAccountId(), FeatureName.CDS_NG_SERVICE_PRINCIPAL_FOR_CUSTOM_WEBHOOK)) {
      triggerWebhookEventBuilder.principal(SecurityContextBuilder.getPrincipal());
    }
    return triggerWebhookEventBuilder;
  }

  public NGTriggerDetailsResponseDTO toNGTriggerDetailsResponseDTO(NGTriggerEntity ngTriggerEntity, boolean includeYaml,
      boolean throwExceptionIfYamlConversionFails, boolean isPipelineInputOutdated,
      boolean mandatoryAuthForCustomWebhookTriggers, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String webhookCurlCommand = EMPTY;
    String webhookUrl = generateWebhookUrl(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    if (ngTriggerEntity.getType() == WEBHOOK) {
      WebhookMetadata webhookMetadata = ngTriggerEntity.getMetadata().getWebhook();
      if (webhookMetadata.getCustom() != null) {
        webhookCurlCommand =
            WebhookHelper.generateCustomWebhookCurlCommand(webhookUrl, mandatoryAuthForCustomWebhookTriggers);
      }
    }

    NGTriggerDetailsResponseDTOBuilder ngTriggerDetailsResponseDTO =
        NGTriggerDetailsResponseDTO.builder()
            .name(ngTriggerEntity.getName())
            .identifier(ngTriggerEntity.getIdentifier())
            .description(ngTriggerEntity.getDescription())
            .type(ngTriggerEntity.getType())
            .yaml(includeYaml ? generateNgTriggerConfigV2Yaml(ngTriggerEntity, throwExceptionIfYamlConversionFails,
                                    scopeInfo, isParentIdQueryingEnabled)
                              : StringUtils.EMPTY)
            .tags(TagMapper.convertToMap(ngTriggerEntity.getTags()))
            .enabled(ngTriggerEntity.getEnabled() == null || ngTriggerEntity.getEnabled())
            .triggerStatus(ngTriggerEntity.getTriggerStatus())
            .isPipelineInputOutdated(isPipelineInputOutdated)
            .yamlVersion(ngTriggerEntity.getHarnessVersion())
            .webhookUrl(webhookUrl)
            .webhookCurlCommand(webhookCurlCommand)
            .executorInfo(ngTriggerEntity.getExecutorInfo());

    // Webhook Details
    if (ngTriggerEntity.getType() == WEBHOOK) {
      WebhookDetailsBuilder webhookDetails = WebhookDetails.builder();
      webhookDetails.webhookSourceRepo(ngTriggerEntity.getMetadata().getWebhook().getType()).build();
      ngTriggerDetailsResponseDTO.webhookDetails(webhookDetails.build());
      ngTriggerDetailsResponseDTO.registrationStatus(
          ngTriggerEntity.getMetadata().getWebhook().getRegistrationStatus());
    } else if (ngTriggerEntity.getType() == MANIFEST || ngTriggerEntity.getType() == ARTIFACT
        || ngTriggerEntity.getType() == MULTI_REGION_ARTIFACT) {
      NGTriggerConfigV2 ngTriggerConfigV2 = toTriggerConfigV2WithoutYmlVersion(ngTriggerEntity);
      NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerConfigV2.getSource().getSpec();
      if (BuildAware.class.isAssignableFrom(ngTriggerSpecV2.getClass())) {
        BuildAware buildAware = (BuildAware) ngTriggerSpecV2;
        ngTriggerDetailsResponseDTO.buildDetails(BuildDetails.builder().buildType(buildAware.fetchBuildType()).build());
      }
    }

    Optional<TriggerEventHistory> triggerEventHistory =
        fetchLatestExecutionForTrigger(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);

    List<Integer> executions = generateLastWeekActivityData(ngTriggerEntity, scopeInfo, isParentIdQueryingEnabled);
    if (isNotEmpty(executions)) {
      ngTriggerDetailsResponseDTO.executions(executions);
    }

    if (triggerEventHistory.isPresent()) {
      LastTriggerExecutionDetails lastTriggerExecutionDetails =
          LastTriggerExecutionDetails.builder()
              .lastExecutionStatus(triggerEventHistory.get().getFinalStatus())
              .lastExecutionSuccessful(!triggerEventHistory.get().isExceptionOccurred())
              .message(triggerEventHistory.get().getMessage())
              .planExecutionId(triggerEventHistory.get().getPlanExecutionId())
              .lastExecutionTime(triggerEventHistory.get().getCreatedAt())
              .build();
      if (triggerEventHistory.get().getTargetExecutionSummary() != null) {
        lastTriggerExecutionDetails.setPlanExecutionId(
            triggerEventHistory.get().getTargetExecutionSummary().getPlanExecutionId());
      }
      ngTriggerDetailsResponseDTO.lastTriggerExecutionDetails(lastTriggerExecutionDetails);
    }

    return ngTriggerDetailsResponseDTO.build();
  }

  private Map<String, Object> getResMap(YamlNode yamlNode) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    List<YamlField> childFields = yamlNode.fields();

    for (YamlField childYamlField : childFields) {
      String fieldName = childYamlField.getName();
      JsonNode value = childYamlField.getNode().getCurrJsonNode();

      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> ValueNode
        resMap.put(fieldName, value);
      } else if (value.isArray()) {
        // Value -> ArrayNode
        resMap.put(fieldName, getResMapInArray(childYamlField.getNode()));
      } else {
        // Value -> ObjectNode
        resMap.put(fieldName, getResMap(childYamlField.getNode()));
      }
    }
    return resMap;
  }

  public ArtifactoryConnectorDTO getConnector(IdentifierRef artifactoryConnectorRef) {
    Optional<ConnectorDTO> connectorDTO = NGRestUtils.getResponse(connectorResourceClient.get(
        artifactoryConnectorRef.getIdentifier(), artifactoryConnectorRef.getAccountIdentifier(),
        artifactoryConnectorRef.getOrgIdentifier(), artifactoryConnectorRef.getProjectIdentifier()));

    if (!connectorDTO.isPresent() || !isAArtifactoryConnector(connectorDTO.get())) {
      throw new ArtifactoryRegistryException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
          artifactoryConnectorRef.getIdentifier(), artifactoryConnectorRef.getScope()));
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnectorInfo();
    return (ArtifactoryConnectorDTO) connectors.getConnectorConfig();
  }

  private static boolean isAArtifactoryConnector(@NotNull ConnectorDTO connectorDTO) {
    return ConnectorType.ARTIFACTORY == connectorDTO.getConnectorInfo().getConnectorType();
  }

  // Gets the ResMap if the yamlNode is of the type Array
  private List<Object> getResMapInArray(YamlNode yamlNode) {
    List<Object> arrayList = new ArrayList<>();
    // Iterate over the array
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        // Value -> LeafNode
        arrayList.add(arrayElement);
      } else if (arrayElement.isArray()) {
        // Value -> Array
        arrayList.add(getResMapInArray(arrayElement));
      } else {
        // Value -> Object
        arrayList.add(getResMap(arrayElement));
      }
    }
    return arrayList;
  }

  @VisibleForTesting
  YamlNode validateAndGetYamlNode(String yaml) {
    if (isEmpty(yaml)) {
      throw new InvalidRequestException("Service YAML is empty.");
    }
    YamlNode yamlNode = null;
    try {
      yamlNode = YamlUtils.readTree(yaml).getNode();
    } catch (IOException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
    }
    return yamlNode;
  }

  private List<Integer> generateLastWeekActivityData(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    long startTime = System.currentTimeMillis() - Duration.ofDays(DAYS_BEFORE_CURRENT_DATE).toMillis();
    Criteria criteria = TriggerFilterHelper.createCriteriaForTriggerEventCountLastNDays(ngTriggerEntity.getAccountId(),
        ngTriggerEntity.getOrgIdentifier(), ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getIdentifier(),
        ngTriggerEntity.getTargetIdentifier(), startTime, scopeInfo, isParentIdQueryingEnabled);

    List<TriggerEventHistory> triggerActivityList =
        triggerEventHistoryRepository.findAllActivationTimestampsInRange(criteria);

    Integer[] executions = prepareExecutionDataArray(startTime, triggerActivityList);
    return Arrays.asList(executions);
  }

  @VisibleForTesting
  Integer[] prepareExecutionDataArray(long startTime, List<TriggerEventHistory> triggerActivityList) {
    Integer[] executions = new Integer[] {0, 0, 0, 0, 0, 0, 0};
    if (isNotEmpty(triggerActivityList)) {
      List<Long> timeStamps =
          triggerActivityList.stream().map(event -> event.getCreatedAt()).sorted().collect(Collectors.toList());
      timeStamps.forEach(timeStamp -> {
        long diff = DAYS.between(Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault()).toLocalDate(),
            Instant.ofEpochMilli(timeStamp).atZone(ZoneId.systemDefault()).toLocalDate());
        int index = (int) Math.abs(diff);
        if (index >= 0 && index < 7) {
          executions[index]++;
        }
      });
    }
    return executions;
  }

  public Optional<TriggerEventHistory> fetchLatestExecutionForTrigger(
      NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    Criteria criteria;
    if (isParentIdQueryingEnabled) {
      criteria = Criteria.where(TriggerEventHistoryKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    } else {
      criteria = Criteria.where(TriggerEventHistoryKeys.accountId)
                     .is(ngTriggerEntity.getAccountId())
                     .and(TriggerEventHistoryKeys.orgIdentifier)
                     .is(ngTriggerEntity.getOrgIdentifier())
                     .and(TriggerEventHistoryKeys.projectIdentifier)
                     .is(ngTriggerEntity.getProjectIdentifier());
    }

    criteria = criteria.and(TriggerEventHistoryKeys.targetIdentifier)
                   .is(ngTriggerEntity.getTargetIdentifier())
                   .and(TriggerEventHistoryKeys.triggerIdentifier)
                   .is(ngTriggerEntity.getIdentifier())
                   .and(TriggerEventHistoryKeys.executionNotAttempted)
                   .ne(true);

    List<TriggerEventHistory> triggerEventHistoryList = triggerEventHistoryRepository.findOneWithSort(
        criteria, Sort.by(TriggerEventHistoryKeys.createdAt).descending());

    if (!isEmpty(triggerEventHistoryList)) {
      return Optional.of(triggerEventHistoryList.get(0));
    }
    return Optional.empty();
  }

  public void updateEntityYmlWithEnabledValue(NGTriggerEntity ngTriggerEntity) {
    try {
      YamlField yamlField = YamlUtils.readTree(ngTriggerEntity.getYaml());
      if (HarnessYamlVersion.V0.equals(ngTriggerEntity.getHarnessVersion())) {
        YamlNode triggerNode = yamlField.getNode().getField("trigger").getNode();
        ((ObjectNode) triggerNode.getCurrJsonNode()).put("enabled", ngTriggerEntity.getEnabled());
      }
      String updateYml = YamlUtils.writeYamlString(yamlField);
      ngTriggerEntity.setYaml(updateYml);
    } catch (Exception e) {
      log.error(new StringBuilder("Failed to update enable attribute to ")
                    .append(ngTriggerEntity.getEnabled())
                    .append("in trigger yml for Trigger: ")
                    .append(TriggerHelper.getTriggerRef(ngTriggerEntity))
                    .toString());
    }
  }

  private ParameterField<List<String>> getStagesToExecute(NGTriggerEntity ngTriggerEntity) {
    return ParameterField.isNull(ngTriggerEntity.getStagesToExecuteV2())
        ? ParameterField.createValueField(ngTriggerEntity.getStagesToExecute())
        : ngTriggerEntity.getStagesToExecuteV2();
  }

  @VisibleForTesting
  String generateWebhookUrl(NGTriggerEntity ngTriggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String webhookUrl = EMPTY;
    if (ngTriggerEntity.getType() == WEBHOOK) {
      WebhookMetadata webhookMetadata = ngTriggerEntity.getMetadata().getWebhook();
      if (webhookMetadata.getGit() != null) {
        webhookUrl = WebhookHelper.generateWebhookUrl(webhookConfigProvider, ngTriggerEntity.getAccountId());
      } else if (webhookMetadata.getCustom() != null) {
        webhookUrl = WebhookHelper.generateCustomWebhookUrl(webhookConfigProvider, ngTriggerEntity.getAccountId(),
            isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : ngTriggerEntity.getOrgIdentifier(),
            isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : ngTriggerEntity.getProjectIdentifier(),
            ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getIdentifier(),
            ngTriggerEntity.getCustomWebhookToken());
      }
    }
    return webhookUrl;
  }
}
