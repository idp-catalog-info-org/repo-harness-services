/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.HAR;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.HARNESS_ARTIFACT_REGISTRY_WEBHOOK_NOT_EXECUTED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_HARNESS_ARTIFACT_REGISTRY_TRIGGER_ACTION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_TRIGGERS_FOUND_FOR_HARNESS_ARTIFACT_REGISTRY_WEBHOOK;
import static io.harness.rule.OwnerRule.TMACARI;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ParsedRegistryWebhook;
import io.harness.beans.Registry;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;

@OwnedBy(HAR)
public class HarWebhookFilterTest extends CategoryTest {
  @Mock private NGTriggerService ngTriggerService;
  @Mock private PmsFeatureFlagService featureFlagService;
  @Inject @InjectMocks HarWebhookFilter harWebhookFilter;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void applyFilterTestInvalidFFNotEnabled() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId("acc")
                                                  .orgIdentifier(null)
                                                  .projectIdentifier("null")
                                                  .sourceRepoType("HARNESS_ARTIFACT_REGISTRY")
                                                  .createdAt(0l)
                                                  .nextIteration(0l)
                                                  .build();
    doReturn(false).when(featureFlagService).isEnabled(any(), eq(FeatureName.HAR_TRIGGERS));

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("acc")
            .webhookPayloadData(WebhookPayloadData.builder()
                                    .registryWebhook(ParsedRegistryWebhook.builder()
                                                         .registry(Registry.builder().name("regName").build())
                                                         .trigger("artifact_created")
                                                         .build())
                                    .originalEvent(triggerWebhookEvent)
                                    .build())
            .build();

    WebhookEventMappingResponse webhookEventMappingResponse = harWebhookFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(HARNESS_ARTIFACT_REGISTRY_WEBHOOK_NOT_EXECUTED);
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getMessage())
        .isEqualTo(
            "Webhook: HARNESS_ARTIFACT_REGISTRY, accountId: acc, registry: regName, not executed. HAR_TRIGGERS FF not enabled.");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void applyFilterTestInvalidAction() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId("acc")
                                                  .orgIdentifier(null)
                                                  .projectIdentifier("null")
                                                  .sourceRepoType("HARNESS_ARTIFACT_REGISTRY")
                                                  .createdAt(0l)
                                                  .nextIteration(0l)
                                                  .build();

    doReturn(true).when(featureFlagService).isEnabled(any(), eq(FeatureName.HAR_TRIGGERS));

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("acc")
            .webhookPayloadData(WebhookPayloadData.builder()
                                    .registryWebhook(ParsedRegistryWebhook.builder().trigger("invalid").build())
                                    .originalEvent(triggerWebhookEvent)
                                    .build())
            .build();

    WebhookEventMappingResponse webhookEventMappingResponse = harWebhookFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(INVALID_HARNESS_ARTIFACT_REGISTRY_TRIGGER_ACTION);
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getMessage())
        .isEqualTo("Invalid trigger action: invalid, SourceRepoType: HARNESS_ARTIFACT_REGISTRY");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void applyFilterTestNoTriggersFound() {
    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId("acc")
                                                  .orgIdentifier(null)
                                                  .projectIdentifier("null")
                                                  .sourceRepoType("HARNESS_ARTIFACT_REGISTRY")
                                                  .createdAt(0l)
                                                  .nextIteration(0l)
                                                  .build();

    PowerMockito.doReturn(null)
        .when(ngTriggerService)
        .findTriggersForHarnessArtifactRegistryByAccountIdAndRegistry(any(), any(), any());
    doReturn(true).when(featureFlagService).isEnabled(any(), eq(FeatureName.HAR_TRIGGERS));

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("acc")
            .webhookPayloadData(WebhookPayloadData.builder()
                                    .registryWebhook(ParsedRegistryWebhook.builder()
                                                         .registry(Registry.builder().name("regName").build())
                                                         .trigger("artifact_created")
                                                         .build())
                                    .originalEvent(triggerWebhookEvent)
                                    .build())
            .build();

    WebhookEventMappingResponse webhookEventMappingResponse = harWebhookFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(NO_TRIGGERS_FOUND_FOR_HARNESS_ARTIFACT_REGISTRY_WEBHOOK);
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getMessage())
        .isEqualTo(
            "No triggers found for: HARNESS_ARTIFACT_REGISTRY, accountId: acc, registry: regName, action: CREATION");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void applyFilterTest() {
    NGTriggerEntity t1 = NGTriggerEntity.builder().identifier("T1").build();
    NGTriggerEntity t2 = NGTriggerEntity.builder().identifier("T2").build();

    TriggerWebhookEvent triggerWebhookEvent = TriggerWebhookEvent.builder()
                                                  .accountId("acc")
                                                  .orgIdentifier(null)
                                                  .projectIdentifier("null")
                                                  .sourceRepoType("HARNESS_ARTIFACT_REGISTRY")
                                                  .createdAt(0l)
                                                  .nextIteration(0l)
                                                  .build();

    PowerMockito.doReturn(Arrays.asList(t1, t2))
        .when(ngTriggerService)
        .findTriggersForHarnessArtifactRegistryByAccountIdAndRegistry(any(), any(), any());
    doReturn(true).when(featureFlagService).isEnabled(any(), eq(FeatureName.HAR_TRIGGERS));

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("acc")
            .webhookPayloadData(WebhookPayloadData.builder()
                                    .registryWebhook(ParsedRegistryWebhook.builder()
                                                         .registry(Registry.builder().name("regName").build())
                                                         .trigger("artifact_created")
                                                         .build())
                                    .originalEvent(triggerWebhookEvent)
                                    .build())
            .build();

    WebhookEventMappingResponse webhookEventMappingResponse = harWebhookFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    List<TriggerDetails> triggerDetails = webhookEventMappingResponse.getTriggers();
    assertThat(triggerDetails.size()).isEqualTo(2);
    List<NGTriggerEntity> entities = triggerDetails.stream().map(TriggerDetails::getNgTriggerEntity).collect(toList());
    assertThat(entities).containsExactlyInAnyOrder(t1, t2);
  }
}
