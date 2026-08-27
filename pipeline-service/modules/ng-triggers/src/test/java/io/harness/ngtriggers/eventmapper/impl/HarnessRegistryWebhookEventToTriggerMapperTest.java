/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.impl;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.beans.ParsedRegistryWebhook;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.metrics.service.api.MetricService;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.impl.PayloadConditionsTriggerFilter;
import io.harness.ngtriggers.helpers.filter.TriggerFilterStore;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.HAR)
public class HarnessRegistryWebhookEventToTriggerMapperTest extends CategoryTest {
  @Mock TriggerFilterStore triggerFilterStore;
  @Mock MetricService metricService;
  @InjectMocks @Inject HarnessRegistryWebhookEventToTriggerMapper mapper;
  @Mock PayloadConditionsTriggerFilter payloadConditionsTriggerFilter;

  private static final String PAYLOAD = "{\n"
      + "   \"trigger\":\"artifact_created\",\n"
      + "   \"registry\":{\n"
      + "      \"id\":1,\n"
      + "      \"name\":\"orgregistry\",\n"
      + "      \"description\":\"description\",\n"
      + "      "
      + "\"url\":\"https://app.harness.io/ng/account/kmpySmUISimoRrJL6NL73w/module/code/orgs/default/projects/tudors/"
      + "repos/tudors\"\n"
      + "   },\n"
      + "   \"principal\":{\n"
      + "      \"id\":4,\n"
      + "      \"uid\":\"admin\",\n"
      + "      \"display_name\":\"Administrator\",\n"
      + "      \"email\":\"admin@gitness.io\",\n"
      + "      \"type\":\"user\",\n"
      + "      \"created\":1738082883367,\n"
      + "      \"updated\":1738082883367\n"
      + "   },\n"
      + "   \"artifact_info\":{\n"
      + "      \"type\":\"DOCKER\",\n"
      + "      \"name\":\"myimg\",\n"
      + "      \"version\":\"v1\",\n"
      + "      \"artifact\":{\n"
      + "         \"url\":\"\",\n"
      + "         \"name\":\"myimg\",\n"
      + "         \"tag\":\"v1\",\n"
      + "         \"ref\":\"myimg:v1\"\n"
      + "      }\n"
      + "   }\n"
      + "}";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(mapper).set("triggerMapperHelper", new TriggerMapperHelper(metricService));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void applyFilterTest() throws IOException {
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-Harness-Registry-Trigger\"").values(Arrays.asList("someValue")).build()))
            .payload(PAYLOAD)
            .build();

    WebhookDTO webhookDTO = WebhookDTO.newBuilder().setJsonPayload(PAYLOAD).build();

    doReturn(Arrays.asList(payloadConditionsTriggerFilter)).when(triggerFilterStore).getWebhookTriggerFilters(any());
    doReturn(WebhookEventMappingResponse.builder().failedToFindTrigger(false).build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(any());

    WebhookEventMappingResponse webhookEventMappingResponse = mapper.mapWebhookEventToTriggers(
        TriggerMappingRequestData.builder().triggerWebhookEvent(triggerWebhookEvent).webhookDTO(webhookDTO).build());

    ArgumentCaptor<WebhookPayloadData> captor = ArgumentCaptor.forClass(WebhookPayloadData.class);
    verify(triggerFilterStore, times(1)).getWebhookTriggerFilters(captor.capture());
    WebhookPayloadData webhookPayloadData = captor.getValue();
    assertThat(webhookPayloadData.getRegistryWebhook().getTrigger()).isNotNull();
    assertThat(webhookPayloadData.getRegistryWebhook().getTrigger()).isEqualTo("artifact_created");
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void skipPipelineExecutionIfSpecialKeywordIsPresentTest() {
    ParsedRegistryWebhook parsedRegistryWebhook = mapper.parseRegistry(PAYLOAD);

    assertThat(parsedRegistryWebhook).isNotNull();
    assertThat(parsedRegistryWebhook.getTrigger()).isEqualTo("artifact_created");
    assertThat(parsedRegistryWebhook.getRegistry().getId()).isEqualTo("1");
    assertThat(parsedRegistryWebhook.getRegistry().getName()).isEqualTo("orgregistry");
    assertThat(parsedRegistryWebhook.getRegistry().getDescription()).isEqualTo("description");
    assertThat(parsedRegistryWebhook.getRegistry().getUrl())
        .isEqualTo("https://app.harness.io/ng/account/kmpySmUISimoRrJL6NL73w/module/code/orgs/default/projects/tudors/"
            + "repos/tudors");
    assertThat(parsedRegistryWebhook.getArtifactInfo().getType()).isEqualTo("DOCKER");
    assertThat(parsedRegistryWebhook.getArtifactInfo().getName()).isEqualTo("myimg");
    assertThat(parsedRegistryWebhook.getArtifactInfo().getVersion()).isEqualTo("v1");
  }
}