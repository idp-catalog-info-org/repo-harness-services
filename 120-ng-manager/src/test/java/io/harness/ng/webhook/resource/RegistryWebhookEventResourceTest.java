/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.resource;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.webhook.UpsertRegistryWebhookRequestDTO;
import io.harness.ng.webhook.UpsertRegistryWebhookResponseDTO;
import io.harness.ng.webhook.resources.RegistryWebhookEventResource;
import io.harness.ng.webhook.services.api.RegistryWebhookEventService;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RegistryWebhookEventResourceTest extends CategoryTest {
  @InjectMocks RegistryWebhookEventResource webhookEventResource;
  @Mock RegistryWebhookEventService webhookEventService;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUpsertWebhook() {
    UpsertRegistryWebhookResponseDTO upsertWebhookResponseDTO =
        UpsertRegistryWebhookResponseDTO.builder().status(200).build();
    when(webhookEventService.upsertWebhook(any())).thenReturn(upsertWebhookResponseDTO);
    assertThat(
        webhookEventResource.upsertWebhook(UpsertRegistryWebhookRequestDTO.builder().build()).getData().getStatus())
        .isEqualTo(200);
  }
}
