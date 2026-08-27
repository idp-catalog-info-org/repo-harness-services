/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.NGTriggerType.SCHEDULED;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpecWrapper;
import io.harness.ngtriggers.beans.source.systemevents.PipelineSystemEventSpec;
import io.harness.ngtriggers.beans.source.systemevents.SystemEventType;
import io.harness.ngtriggers.beans.source.v1.NGTriggerYamlSimplSource;
import io.harness.ngtriggers.beans.source.v1.NGTriggerYamlSimplType;
import io.harness.ngtriggers.beans.source.v1.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.v1.artifact.ScheduledTriggerYamlSimplConfig;
import io.harness.ngtriggers.beans.source.v1.artifact.SystemEventTriggerYamlSimplConfig;
import io.harness.ngtriggers.beans.source.v1.artifact.WebhookTriggerYamlSimplConfig;
import io.harness.ngtriggers.beans.source.v1.systemevents.PipelineSystemEventYamlSimplSpec;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.MultiRegionArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.webhook.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.SystemEventTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerElementMapper {
  NGWebhookTriggerElementMapper ngWebhookTriggerElementMapper;
  NGArtifactTriggerElementMapper ngArtifactTriggerElementMapper;
  NGManifestTriggerElementMapper ngManifestTriggerElementMapper;

  NGTriggerType toNGTriggerType(NGTriggerYamlSimplType typeEnum) {
    switch (typeEnum) {
      case WEBHOOK:
        return NGTriggerType.WEBHOOK;
      case MANIFEST:
        return NGTriggerType.MANIFEST;
      case SCHEDULED:
        return SCHEDULED;
      case ARTIFACT:
        return NGTriggerType.ARTIFACT;
      case MULTI_REGION_ARTIFACT:
        return NGTriggerType.MULTI_REGION_ARTIFACT;
      case SYSTEM_EVENT:
        return NGTriggerType.SYSTEM_EVENT;
      default:
        throw new InvalidRequestException(String.format("NGTrigger not supported for type: %s", typeEnum));
    }
  }

  public NGTriggerYamlSimplSource toNGTriggerYamlSimplSource(String yaml) {
    try {
      JsonNode jsonNode = YamlUtils.readTree(yaml).getNode().getCurrJsonNode();
      return YamlUtils.readFromJsonNode(jsonNode.get("spec"), NGTriggerYamlSimplSource.class);
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  public NGTriggerSourceV2 toNGTriggerSourceV2(NGTriggerYamlSimplSource source) {
    return NGTriggerSourceV2.builder()
        .pollInterval(source.getInterval())
        .webhookId(source.getWebhook())
        .type(toNGTriggerType(source.getType()))
        .spec(toNGTriggerSpecV2(source))
        .build();
  }

  NGTriggerSpecV2 toNGTriggerSpecV2(NGTriggerYamlSimplSource source) {
    switch (source.getType()) {
      case SCHEDULED:
        ScheduledTriggerYamlSimplConfig spec = (ScheduledTriggerYamlSimplConfig) source.getSpec();
        io.harness.ngtriggers.beans.source.v1.artifact.CronTriggerSpec cronTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.CronTriggerSpec) spec.getSpec();
        return ScheduledTriggerConfig.builder()
            .type(spec.getType())
            .spec(CronTriggerSpec.builder()
                      .type(cronTriggerSpec.getType())
                      .expression(cronTriggerSpec.getExpression())
                      .build())
            .build();
      case WEBHOOK:
        WebhookTriggerYamlSimplConfig webhookSpec = (WebhookTriggerYamlSimplConfig) source.getSpec();
        return WebhookTriggerConfigV2.builder()
            .type(ngWebhookTriggerElementMapper.toWebhookTriggerType(webhookSpec.getType()))
            .spec(ngWebhookTriggerElementMapper.toWebhookTriggerSpec(webhookSpec))
            .build();
      case ARTIFACT:
        io.harness.ngtriggers.beans.source.v1.artifact.ArtifactTriggerConfig artifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.ArtifactTriggerConfig) source.getSpec();
        return ArtifactTriggerConfig.builder()
            .type(ngArtifactTriggerElementMapper.toArtifactTriggerType(artifactTriggerSpec.getType()))
            .spec(ngArtifactTriggerElementMapper.toArtifactTypeSpec(
                artifactTriggerSpec.getSpec(), artifactTriggerSpec.getType()))
            .build();
      case MANIFEST:
        io.harness.ngtriggers.beans.source.v1.artifact.ManifestTriggerConfig manifestTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.ManifestTriggerConfig) source.getSpec();
        return ManifestTriggerConfig.builder()
            .type(ngManifestTriggerElementMapper.toManifestTriggerType(manifestTriggerSpec.getType()))
            .spec(ngManifestTriggerElementMapper.toManifestTypeSpec(manifestTriggerSpec))
            .build();
      case MULTI_REGION_ARTIFACT:
        io.harness.ngtriggers.beans.source.v1.artifact.MultiRegionArtifactTriggerConfig multiRegionArtifactTriggerSpec =
            (io.harness.ngtriggers.beans.source.v1.artifact.MultiRegionArtifactTriggerConfig) source.getSpec();
        return MultiRegionArtifactTriggerConfig.builder()
            .eventConditions(multiRegionArtifactTriggerSpec.getConditions().getEvent())
            .sources(
                multiRegionArtifactTriggerSpec.getSources()
                    .stream()
                    .map(artifactTypeSpecWrapper
                        -> toArtifactTypeSpecWrapper(artifactTypeSpecWrapper, multiRegionArtifactTriggerSpec.getType()))
                    .collect(Collectors.toList()))
            .jexlCondition(multiRegionArtifactTriggerSpec.getConditions().getJexl())
            .metaDataConditions(multiRegionArtifactTriggerSpec.getConditions().getMetadata())
            .type(ngArtifactTriggerElementMapper.toArtifactTriggerType(multiRegionArtifactTriggerSpec.getType()))
            .build();
      case SYSTEM_EVENT:
        SystemEventTriggerYamlSimplConfig systemEventConfig = (SystemEventTriggerYamlSimplConfig) source.getSpec();
        PipelineSystemEventYamlSimplSpec pipelineSpec = (PipelineSystemEventYamlSimplSpec) systemEventConfig.getSpec();
        if (pipelineSpec == null) {
          throw new InvalidRequestException(
              "SystemEvent trigger [" + source.getType() + "] is missing the required inner 'spec' block");
        }
        return SystemEventTriggerConfig.builder()
            .type(systemEventConfig.getType())
            .spec(PipelineSystemEventSpec.builder()
                      .eventType(toSystemEventType(pipelineSpec.getEventType()))
                      .payloadConditions(pipelineSpec.getPayloadConditions())
                      .build())
            .build();
      default:
        throw new InvalidRequestException("Type " + source.getType().toString() + " is invalid");
    }
  }

  private SystemEventType toSystemEventType(String v1EventType) {
    if ("pipeline-failure".equals(v1EventType)) {
      return SystemEventType.PIPELINE_FAILURE;
    } else if ("pipeline-success".equals(v1EventType)) {
      return SystemEventType.PIPELINE_SUCCESS;
    }
    throw new InvalidRequestException("Unsupported system event type: " + v1EventType);
  }

  ArtifactTypeSpecWrapper toArtifactTypeSpecWrapper(
      io.harness.ngtriggers.beans.source.v1.artifact.ArtifactTypeSpecWrapper artifactTypeSpecWrapper,
      ArtifactType artifactType) {
    return ArtifactTypeSpecWrapper.builder()
        .spec(ngArtifactTriggerElementMapper.toArtifactTypeSpec(artifactTypeSpecWrapper.getSpec(), artifactType))
        .build();
  }
}
