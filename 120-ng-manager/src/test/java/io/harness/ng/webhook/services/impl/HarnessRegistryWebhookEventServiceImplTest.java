/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessArtifactRegistryHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.core.har.RegistryWebhookResponse;
import io.harness.ng.core.har.WebhookResponseDTO;
import io.harness.ng.webhook.UpsertRegistryWebhookRequestDTO;
import io.harness.ng.webhook.UpsertRegistryWebhookResponseDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HarnessRegistryWebhookEventServiceImplTest extends CategoryTest {
  @Mock AccountOrgProjectHelper accountOrgProjectHelper;
  @Mock HarnessArtifactRegistryHelper harnessArtifactRegistryHelper;
  @Mock DefaultWebhookServiceImpl defaultWebhookService;
  @InjectMocks HarnessRegistryWebhookEventServiceImpl registryWebhookEventService;
  private String accountId = "accountId";
  private String orgId = "orgId";
  private String projectId = "projectId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUpsertRegistryWebhook() {
    doReturn("https://app.harness.io/gateway/ng/api/").when(defaultWebhookService).getWebhookBaseUrl();
    doReturn(null).when(accountOrgProjectHelper).getVanityUrl("abcde");
    UpsertRegistryWebhookRequestDTO upsertRegistryWebhookRequestDTO = UpsertRegistryWebhookRequestDTO.builder()
                                                                          .accountIdentifier(accountId)
                                                                          .projectIdentifier(projectId)
                                                                          .orgIdentifier(orgId)
                                                                          .registry("registry")
                                                                          .build();
    WebhookResponseDTO webhookResponseDTO =
        WebhookResponseDTO.builder()
            .response(RegistryWebhookResponse.builder().identifier("webhookIdentifier").name("webhookName").build())
            .status("SUCCESS")
            .build();

    when(harnessArtifactRegistryHelper.upsertWebhook(any(), any())).thenReturn(webhookResponseDTO);
    assertThat(registryWebhookEventService.upsertWebhook(upsertRegistryWebhookRequestDTO))
        .isEqualTo(UpsertRegistryWebhookResponseDTO.builder()
                       .status(200)
                       .webhookName("webhookName")
                       .webhookIdentifier("webhookIdentifier")
                       .build());

    doThrow(new InvalidRequestException("InvalidRequestException"))
        .when(harnessArtifactRegistryHelper)
        .upsertWebhook(any(), any());
    UpsertRegistryWebhookResponseDTO upsertRegistryWebhookResponseDTO =
        registryWebhookEventService.upsertWebhook(upsertRegistryWebhookRequestDTO);
    assertThat(upsertRegistryWebhookResponseDTO.getStatus()).isEqualTo(500);
    assertThat(upsertRegistryWebhookResponseDTO.getError())
        .isEqualTo("Failed to create registry webhook: InvalidRequestException");
  }
}
