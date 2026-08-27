/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessArtifactRegistryHelper;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.har.WebhookRequestDTO;
import io.harness.ng.core.har.WebhookResponseDTO;
import io.harness.ng.webhook.UpsertRegistryWebhookRequestDTO;
import io.harness.ng.webhook.UpsertRegistryWebhookResponseDTO;
import io.harness.ng.webhook.services.api.RegistryWebhookEventService;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.HAR)
public class HarnessRegistryWebhookEventServiceImpl implements RegistryWebhookEventService {
  private final DefaultWebhookServiceImpl defaultWebhookService;
  private HarnessArtifactRegistryHelper harnessArtifactRegistryHelper;
  NextGenConfiguration nextGenConfiguration;

  @Override
  public UpsertRegistryWebhookResponseDTO upsertWebhook(
      UpsertRegistryWebhookRequestDTO upsertRegistryWebhookRequestDTO) {
    IdentifierRef identifier = IdentifierRefHelper.getIdentifierRef(upsertRegistryWebhookRequestDTO.getRegistry(),
        upsertRegistryWebhookRequestDTO.getAccountIdentifier(), upsertRegistryWebhookRequestDTO.getOrgIdentifier(),
        upsertRegistryWebhookRequestDTO.getProjectIdentifier());

    String registryRef = HarnessStringUtils.joinNullableString("/", identifier.getAccountIdentifier(),
        identifier.getOrgIdentifier(), identifier.getProjectIdentifier(), identifier.getIdentifier());

    try {
      WebhookResponseDTO response = harnessArtifactRegistryHelper.upsertWebhook(
          WebhookRequestDTO.builder()
              .identifier("harnesstriggerwebhok")
              .url(defaultWebhookService.getTargetUrl(identifier.getAccountIdentifier()))
              .name("From Trigger")
              .insecure(true)
              .enabled(true)
              .triggers(upsertRegistryWebhookRequestDTO.getTriggers())
              .build(),
          registryRef);

      return UpsertRegistryWebhookResponseDTO.builder()
          .webhookIdentifier(response.getResponse().getIdentifier())
          .webhookName(response.getResponse().getName())
          .status(200)
          .build();
    } catch (Exception e) {
      log.error("Upsert Registry Webhook Error for accountId: {}, orgId:{}, projectId:{} : ",
          upsertRegistryWebhookRequestDTO.getAccountIdentifier(), upsertRegistryWebhookRequestDTO.getOrgIdentifier(),
          upsertRegistryWebhookRequestDTO.getProjectIdentifier(), e);
      return UpsertRegistryWebhookResponseDTO.builder()
          .status(500)
          .error(String.format("Failed to create registry webhook: %s", e.getMessage()))
          .build();
    }
  }
}
