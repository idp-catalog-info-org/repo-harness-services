/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.ArtifactTriggerEventInfo;
import io.harness.ngtriggers.beans.dto.ManifestTriggerEventInfo;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO;
import io.harness.ngtriggers.beans.dto.PollingDocumentInfo;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NGTriggerEventHistoryMapperTest extends CategoryTest {
  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pipelineId = "pipelineId";
  String triggerIdentifier = "triggerIdentifier";
  String pollingDocId = "pollingDocId";
  String message = "message";
  String uuid = "uuid";
  String payload = "payload";
  Long createdAt = 12L;

  List<HeaderConfig> headers = List.of(HeaderConfig.builder().key("testKey").values(List.of("testValue")).build(),
      HeaderConfig.builder().key("X-Api-Key").values(List.of("pat.aaaaaa.bbbbbb.cccccc")).build(),
      HeaderConfig.builder()
          .key("custom-key-with-jwt")
          .values(List.of("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.sometokenbody.signature"))
          .build(),
      HeaderConfig.builder().key("custom-key-with-base64").values(List.of("9aBzYxCwOFBZ2HK+RQF1+NdZ5UQ=")).build());

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testToTriggerEventHistoryDto() {
    TriggerEventHistory triggerEventHistory = TriggerEventHistory.builder()
                                                  .triggerIdentifier(triggerIdentifier)
                                                  .accountId(accountId)
                                                  .pollingDocId(pollingDocId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .build("build")
                                                  .build();
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(accountId)
                                          .orgIdentifier(orgId)
                                          .identifier(triggerIdentifier)
                                          .projectIdentifier(projectId)
                                          .type(NGTriggerType.ARTIFACT)
                                          .build();
    NGTriggerEventHistoryDTO ngTriggerEventHistoryDTO =
        NGTriggerEventHistoryDTO.builder()
            .accountId(accountId)
            .projectIdentifier(projectId)
            .type(NGTriggerType.ARTIFACT)
            .orgIdentifier(orgId)
            .triggerIdentifier(triggerIdentifier)
            .ngTriggerEventInfo(
                ArtifactTriggerEventInfo.builder()
                    .build("build")
                    .pollingDocumentInfo(PollingDocumentInfo.builder().pollingDocumentId(pollingDocId).build())
                    .build())
            .build();

    NGTriggerEventHistoryDTO response =
        NGTriggerEventHistoryMapper.toTriggerEventHistoryDto(triggerEventHistory, ngTriggerEntity, true, null, false);
    assertThat(ngTriggerEventHistoryDTO).isEqualTo(response);

    // Manifest triggers
    ngTriggerEntity.setType(NGTriggerType.MANIFEST);
    ngTriggerEventHistoryDTO.setType(NGTriggerType.MANIFEST);
    ngTriggerEventHistoryDTO.setNgTriggerEventInfo(
        ManifestTriggerEventInfo.builder()
            .build("build")
            .pollingDocumentInfo(PollingDocumentInfo.builder().pollingDocumentId(pollingDocId).build())
            .build());
    response =
        NGTriggerEventHistoryMapper.toTriggerEventHistoryDto(triggerEventHistory, ngTriggerEntity, true, null, false);
    assertThat(ngTriggerEventHistoryDTO).isEqualTo(response);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testToTriggerEventHistoryDtoWithHeaders() {
    TriggerEventHistory triggerEventHistory = TriggerEventHistory.builder()
                                                  .triggerIdentifier(triggerIdentifier)
                                                  .accountId(accountId)
                                                  .pollingDocId(pollingDocId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .build("build")
                                                  .headers(headers)
                                                  .ngTriggerType(NGTriggerType.WEBHOOK)
                                                  .triggerSubType("CUSTOM")
                                                  .build();
    NGTriggerEventHistoryDTO ngTriggerEventHistoryDTO =
        NGTriggerEventHistoryDTO.builder()
            .accountId(accountId)
            .projectIdentifier(projectId)
            .orgIdentifier(orgId)
            .triggerIdentifier(triggerIdentifier)
            .headers(Map.of("testKey", "testValue", "X-Api-Key", "****", "custom-key-with-jwt", "****",
                "custom-key-with-base64", "****"))
            .build();

    NGTriggerEventHistoryDTO response =
        NGTriggerEventHistoryMapper.toTriggerEventHistoryDto(triggerEventHistory, null, false);
    assertThat(ngTriggerEventHistoryDTO).isEqualTo(response);
    assertThat(ngTriggerEventHistoryDTO.getHeaders()).isEqualTo(response.getHeaders());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testToTriggerEventHistoryDtoWithHeadersAreNotAddedIfNotCustomWebhook() {
    TriggerEventHistory triggerEventHistory = TriggerEventHistory.builder()
                                                  .triggerIdentifier(triggerIdentifier)
                                                  .accountId(accountId)
                                                  .pollingDocId(pollingDocId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .build("build")
                                                  .headers(headers)
                                                  .ngTriggerType(NGTriggerType.ARTIFACT)
                                                  .triggerSubType("CUSTOM")
                                                  .build();
    NGTriggerEventHistoryDTO ngTriggerEventHistoryDTO = NGTriggerEventHistoryDTO.builder()
                                                            .accountId(accountId)
                                                            .projectIdentifier(projectId)
                                                            .orgIdentifier(orgId)
                                                            .triggerIdentifier(triggerIdentifier)
                                                            .build();

    NGTriggerEventHistoryDTO response =
        NGTriggerEventHistoryMapper.toTriggerEventHistoryDto(triggerEventHistory, null, false);
    assertThat(ngTriggerEventHistoryDTO).isEqualTo(response);
  }
}
