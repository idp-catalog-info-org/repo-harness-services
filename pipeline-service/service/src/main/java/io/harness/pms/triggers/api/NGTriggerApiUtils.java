/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngtriggers.beans.source.NGTriggerType.SCHEDULED;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityBuilder;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogType;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.v1.NGTriggerYamlSimplSource;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.mapper.v1.NGTriggerElementMapper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.NGYamlHelper;
import io.harness.spec.server.pipeline.v1.model.TriggerBody;
import io.harness.spec.server.pipeline.v1.model.TriggerCatalog;
import io.harness.spec.server.pipeline.v1.model.TriggerCatalog.CategoryEnum;
import io.harness.spec.server.pipeline.v1.model.TriggerCatalog.TriggerCatalogTypesEnum;
import io.harness.spec.server.pipeline.v1.model.TriggerCatalogResponseBody;
import io.harness.spec.server.pipeline.v1.model.TriggerGetResponseBody;
import io.harness.spec.server.pipeline.v1.model.TriggerRequestBody;
import io.harness.spec.server.pipeline.v1.model.TriggerResponseBody;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerApiUtils {
  NGTriggerElementMapper ngTriggerElementMapperV1;
  io.harness.ngtriggers.mapper.NGTriggerElementMapper ngTriggerElementMapper;
  public TriggerGetResponseBody toGetResponseDTO(
      NGTriggerEntity triggerEntity, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    TriggerGetResponseBody responseBody = new TriggerGetResponseBody();
    responseBody.setIdentifier(triggerEntity.getIdentifier());
    responseBody.setTrigger(toTriggerBody(triggerEntity));
    responseBody.setDescription(triggerEntity.getDescription());
    responseBody.setName(triggerEntity.getName());
    responseBody.setOrg(isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : triggerEntity.getOrgIdentifier());
    responseBody.setPipeline(triggerEntity.getTargetIdentifier());
    responseBody.setProject(
        isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : triggerEntity.getProjectIdentifier());
    responseBody.setVersion(triggerEntity.getHarnessVersion());
    return responseBody;
  }

  public TriggerBody toTriggerBody(NGTriggerEntity triggerEntity) {
    TriggerBody triggerBody = new TriggerBody();
    triggerBody.setEnabled(triggerEntity.getEnabled());
    triggerBody.setEncryptedWebhookSecretIdentifier(triggerEntity.getEncryptedWebhookSecretIdentifier());
    triggerBody.setPipelineBranchName(triggerEntity.getPipelineBranchName());
    triggerBody.setTags(TagMapper.convertToMap(triggerEntity.getTags()));
    triggerBody.setYaml(triggerEntity.getYaml());
    return triggerBody;
  }

  public TriggerResponseBody toResponseDTO(NGTriggerEntity triggerEntity) {
    TriggerResponseBody responseBody = new TriggerResponseBody();
    responseBody.setIdentifier(triggerEntity.getIdentifier());
    return responseBody;
  }

  public TriggerDetails toTriggerDetails(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String parentUniqueId, TriggerRequestBody body, String pipeline) {
    if (HarnessYamlVersion.isV1(NGYamlHelper.getVersion(body.getYaml()))) {
      NGTriggerYamlSimplSource source = ngTriggerElementMapperV1.toNGTriggerYamlSimplSource(body.getYaml());
      NGTriggerEntity ngTriggerEntity =
          toTriggerEntity(accountIdentifier, orgIdentifier, projectIdentifier, body, pipeline, source);

      return TriggerDetails.builder()
          .ngTriggerConfigV2(toNGTriggerConfigV2(pipeline, orgIdentifier, projectIdentifier, body))
          .ngTriggerEntity(ngTriggerEntity)
          .build();
    } else {
      validateTriggerRequest(body);
      return ngTriggerElementMapper.toTriggerDetails(
          accountIdentifier, orgIdentifier, projectIdentifier, parentUniqueId, body.getYaml(), true);
    }
  }

  private void validateTriggerRequest(TriggerRequestBody body) {
    NGTriggerConfigV2 configV2 = ngTriggerElementMapper.toTriggerConfigV2(body.getYaml());
    if (!configV2.getEnabled().equals(body.isEnabled())) {
      throw new InvalidRequestException(
          String.format("Expected Enabled in YAML to be [%s], but was [%s]", body.isEnabled(), configV2.getEnabled()));
    }
    if (configV2.getIdentifier() != null && !configV2.getIdentifier().equals(body.getIdentifier())) {
      throw new InvalidRequestException(String.format("Expected Trigger identifier in YAML to be [%s], but was [%s]",
          body.getIdentifier(), configV2.getIdentifier()));
    }
    if (configV2.getDescription() != null && !configV2.getDescription().equals(body.getDescription())) {
      throw new InvalidRequestException(String.format("Expected Trigger Description in YAML to be [%s], but was [%s]",
          body.getDescription(), configV2.getDescription()));
    }
    if (configV2.getName() != null && !configV2.getName().equals(body.getName())) {
      throw new InvalidRequestException(
          String.format("Expected Trigger name in YAML to be [%s], but was [%s]", body.getName(), configV2.getName()));
    }
    if (configV2.getPipelineBranchName() != null
        && !configV2.getPipelineBranchName().equals(body.getPipelineBranchName())) {
      throw new InvalidRequestException(String.format("Expected Pipeline branch name in YAML to be [%s], but was [%s]",
          body.getPipelineBranchName(), configV2.getPipelineBranchName()));
    }
    if (configV2.getTags() != null && !configV2.getTags().equals(body.getTags())) {
      throw new InvalidRequestException(
          String.format("Expected Trigger tags in YAML to be [%s], but was [%s]", body.getTags(), configV2.getTags()));
    }
    if (isNotEmpty(configV2.getEncryptedWebhookSecretIdentifier())
        && !configV2.getEncryptedWebhookSecretIdentifier().equals(body.getEncryptedWebhookSecretIdentifier())) {
      throw new InvalidRequestException(
          String.format("Expected Encrypted webhook secret identifier in YAML to be [%s], but was [%s]",
              body.getEncryptedWebhookSecretIdentifier(), configV2.getEncryptedWebhookSecretIdentifier()));
    }
  }

  public NGTriggerEntity toTriggerEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      TriggerRequestBody body, String pipeline, NGTriggerYamlSimplSource sourceV1) {
    NGTriggerSourceV2 source = ngTriggerElementMapperV1.toNGTriggerSourceV2(sourceV1);
    NGTriggerEntityBuilder entityBuilder =
        NGTriggerEntity.builder()
            .name(body.getName())
            .identifier(body.getIdentifier())
            .description(body.getDescription())
            .harnessVersion(HarnessYamlVersion.V1)
            .accountId(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .targetIdentifier(pipeline)
            .targetType(TargetType.PIPELINE)
            .enabled(body.isEnabled())
            .yaml(body.getYaml())
            .type(source.getType())
            .pollInterval(source.getPollInterval())
            .webhookId(source.getWebhookId())
            .metadata(ngTriggerElementMapper.toMetadata(source, accountIdentifier, orgIdentifier, projectIdentifier))
            .withServiceV2(true)
            .tags(TagMapper.convertToList(body.getTags()))
            .encryptedWebhookSecretIdentifier(body.getEncryptedWebhookSecretIdentifier())
            .stagesToExecuteV2(sourceV1.getExecute_stages())
            .pipelineBranchName(body.getPipelineBranchName())
            .tags(TagMapper.convertToList(body.getTags()));

    if (source.getType() == SCHEDULED) {
      entityBuilder.nextIterations(new ArrayList<>());
    }
    NGTriggerEntity entity = entityBuilder.build();
    if (source.getType() == SCHEDULED) {
      List<Long> nextIterations = entity.recalculateNextIterations("unused", true, 0);
      if (!nextIterations.isEmpty()) {
        entity.setNextIterations(nextIterations);
      }
    }
    return entity;
  }

  NGTriggerConfigV2 toNGTriggerConfigV2(String pipeline, String org, String project, TriggerRequestBody body) {
    NGTriggerYamlSimplSource source = ngTriggerElementMapperV1.toNGTriggerYamlSimplSource(body.getYaml());
    return NGTriggerConfigV2.builder()
        .pipelineIdentifier(pipeline)
        .identifier(body.getIdentifier())
        .projectIdentifier(project)
        .orgIdentifier(org)
        .encryptedWebhookSecretIdentifier(body.getEncryptedWebhookSecretIdentifier())
        .enabled(body.isEnabled())
        .description(body.getDescription())
        .inputYaml(source.getInputs())
        .inputSetRefs(source.getInput_set_refs())
        .name(body.getName())
        .pipelineBranchName(body.getPipelineBranchName())
        .tags(body.getTags())
        .stagesToExecute(source.getExecute_stages())
        .source(ngTriggerElementMapperV1.toNGTriggerSourceV2(source))
        .build();
  }

  public TriggerCatalogResponseBody toCatalogResponseDTO(List<TriggerCatalogItem> triggerCatalogItems, String version) {
    List<TriggerCatalog> catalogs = new ArrayList<>();
    for (TriggerCatalogItem triggerCatalogItem : triggerCatalogItems) {
      TriggerCatalog triggerCatalog = new TriggerCatalog();
      if (HarnessYamlVersion.isV1(version)) {
        triggerCatalog.setCategory(toCategoryEnumV1(triggerCatalogItem.getCategory()));
        triggerCatalog.setTriggerCatalogTypes(triggerCatalogItem.getTriggerCatalogType()
                                                  .stream()
                                                  .map(this::toTriggerCatalogTypesEnumV1)
                                                  .collect(Collectors.toList()));
      } else {
        triggerCatalog.setCategory(toCategoryEnumV0(triggerCatalogItem.getCategory()));
        triggerCatalog.setTriggerCatalogTypes(triggerCatalogItem.getTriggerCatalogType()
                                                  .stream()
                                                  .map(this::toTriggerCatalogTypesEnumV0)
                                                  .collect(Collectors.toList()));
      }
      catalogs.add(triggerCatalog);
    }
    TriggerCatalogResponseBody triggerCatalogResponseBody = new TriggerCatalogResponseBody();
    triggerCatalogResponseBody.setCatalog(catalogs);
    return triggerCatalogResponseBody;
  }

  CategoryEnum toCategoryEnumV1(NGTriggerType ngTriggerType) {
    switch (ngTriggerType) {
      case WEBHOOK:
        return CategoryEnum.WEBHOOK;
      case MANIFEST:
        return CategoryEnum.MANIFEST;
      case ARTIFACT:
        return CategoryEnum.ARTIFACT;
      case SCHEDULED:
        return CategoryEnum.SCHEDULED;
      default:
        throw new InvalidRequestException(String.format("NGTriggerType %s not supported", ngTriggerType));
    }
  }

  TriggerCatalogTypesEnum toTriggerCatalogTypesEnumV1(TriggerCatalogType triggerCatalogType) {
    switch (triggerCatalogType) {
      case HELM_CHART:
        return TriggerCatalogTypesEnum.HELM_CHART;
      case ACR:
        return TriggerCatalogTypesEnum.ACR;
      case ECR:
        return TriggerCatalogTypesEnum.ECR;
      case BAMBOO:
        return TriggerCatalogTypesEnum.BAMBOO;
      case JENKINS:
        return TriggerCatalogTypesEnum.JENKINS;
      case GCR:
        return TriggerCatalogTypesEnum.GCR;
      case AMI:
        return TriggerCatalogTypesEnum.AMAZON_MACHINE_IMAGE;
      case AMAZON_S3:
        return TriggerCatalogTypesEnum.AMAZON_S3;
      case AZURE_ARTIFACTS:
        return TriggerCatalogTypesEnum.AZURE;
      case CUSTOM_ARTIFACT:
        return TriggerCatalogTypesEnum.CUSTOM;
      case GITHUB_PACKAGES:
        return TriggerCatalogTypesEnum.GITHUB_PACKAGE_REGISTRY;
      case GOOGLE_CLOUD_STORAGE:
        return TriggerCatalogTypesEnum.GOOGLE_CLOUD_STORAGE;
      case CUSTOM:
        return TriggerCatalogTypesEnum.CUSTOM;
      case BITBUCKET:
        return TriggerCatalogTypesEnum.BITBUCKET;
      case GITLAB:
        return TriggerCatalogTypesEnum.GITLAB;
      case GITHUB:
        return TriggerCatalogTypesEnum.GITHUB;
      case AZURE:
        return TriggerCatalogTypesEnum.AZURE_REPO;
      case HARNESS:
        return TriggerCatalogTypesEnum.HARNESS;
      case CRON:
        return TriggerCatalogTypesEnum.CRON;
      case DOCKER:
        return TriggerCatalogTypesEnum.DOCKER_REGISTRY;
      case NEXUS3:
        return TriggerCatalogTypesEnum.NEXUS3_REGISTRY;
      case ARTIFACTORY:
        return TriggerCatalogTypesEnum.ARTIFACTORY_REGISTRY;
      case GOOGLE_ARTIFACT_REGISTRY:
        return TriggerCatalogTypesEnum.GOOGLE_ARTIFACT_REGISTRY;
      case HARNESS_ARTIFACT_REGISTRY:
        return TriggerCatalogTypesEnum.HARNESSARTIFACTREGISTRY;
      case NEXUS2:
        return TriggerCatalogTypesEnum.NEXUS2_REGISTRY;
      case EVENT_BRIDGE:
        return TriggerCatalogTypesEnum.EVENT_RELAY;
      case GCE_IMAGE:
        return TriggerCatalogTypesEnum.GCE_IMAGE;
      default:
        throw new InvalidRequestException(String.format("TriggerCatalogType %s not supported", triggerCatalogType));
    }
  }

  CategoryEnum toCategoryEnumV0(NGTriggerType ngTriggerType) {
    switch (ngTriggerType) {
      case WEBHOOK:
        return CategoryEnum.WEBHOOK_4;
      case MANIFEST:
        return CategoryEnum.MANIFEST_7;
      case ARTIFACT:
        return CategoryEnum.ARTIFACT_5;
      case SCHEDULED:
        return CategoryEnum.SCHEDULED_6;
      default:
        throw new InvalidRequestException(String.format("NGTriggerType %s not supported", ngTriggerType));
    }
  }

  TriggerCatalogTypesEnum toTriggerCatalogTypesEnumV0(TriggerCatalogType triggerCatalogType) {
    switch (triggerCatalogType) {
      case HELM_CHART:
        return TriggerCatalogTypesEnum.HELMCHART;
      case ACR:
        return TriggerCatalogTypesEnum.ACR_30;
      case ECR:
        return TriggerCatalogTypesEnum.ECR_31;
      case BAMBOO:
        return TriggerCatalogTypesEnum.BAMBOO_37;
      case JENKINS:
        return TriggerCatalogTypesEnum.JENKINS_36;
      case GCR:
        return TriggerCatalogTypesEnum.GCR_29;
      case AMI:
        return TriggerCatalogTypesEnum.AMAZONMACHINEIMAGE;
      case AMAZON_S3:
        return TriggerCatalogTypesEnum.AMAZONS3;
      case AZURE_ARTIFACTS:
        return TriggerCatalogTypesEnum.AZUREARTIFACTS;
      case CUSTOM_ARTIFACT:
        return TriggerCatalogTypesEnum.CUSTOMARTIFACT;
      case GITHUB_PACKAGES:
        return TriggerCatalogTypesEnum.GITHUBPACKAGEREGISTRY;
      case GOOGLE_CLOUD_STORAGE:
        return TriggerCatalogTypesEnum.GOOGLECLOUDSTORAGE;
      case CUSTOM:
        return TriggerCatalogTypesEnum.CUSTOM_26;
      case BITBUCKET:
        return TriggerCatalogTypesEnum.BITBUCKET_28;
      case GITLAB:
        return TriggerCatalogTypesEnum.GITLAB_24;
      case GITHUB:
        return TriggerCatalogTypesEnum.GITHUB_23;
      case AZURE:
        return TriggerCatalogTypesEnum.AZUREREPO;
      case HARNESS:
        return TriggerCatalogTypesEnum.HARNESS_25;
      case CRON:
        return TriggerCatalogTypesEnum.CRON_45;
      case DOCKER:
        return TriggerCatalogTypesEnum.DOCKERREGISTRY;
      case NEXUS3:
        return TriggerCatalogTypesEnum.NEXUS3REGISTRY;
      case ARTIFACTORY:
        return TriggerCatalogTypesEnum.ARTIFACTORYREGISTRY;
      case GOOGLE_ARTIFACT_REGISTRY:
        return TriggerCatalogTypesEnum.GOOGLEARTIFACTREGISTRY;
      case HARNESS_ARTIFACT_REGISTRY:
        return TriggerCatalogTypesEnum.HARNESSARTIFACTREGISTRY;
      case NEXUS2:
        return TriggerCatalogTypesEnum.NEXUS2REGISTRY;
      case EVENT_BRIDGE:
        return TriggerCatalogTypesEnum.EVENTRELAY;
      default:
        throw new InvalidRequestException(String.format("TriggerCatalogType %s not supported", triggerCatalogType));
    }
  }
}
