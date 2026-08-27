/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.instrumentation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType.GITHUB;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VED;
import static io.harness.telemetry.helpers.InstrumentationConstants.ACCOUNT;
import static io.harness.telemetry.helpers.InstrumentationConstants.COUNT;
import static io.harness.telemetry.helpers.InstrumentationConstants.ORG;
import static io.harness.telemetry.helpers.InstrumentationConstants.PIPELINE_ID;
import static io.harness.telemetry.helpers.InstrumentationConstants.PROJECT;
import static io.harness.telemetry.helpers.InstrumentationConstants.STATUS;
import static io.harness.telemetry.helpers.InstrumentationConstants.TIME_TAKEN;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.BulkTriggerDetailDTO;
import io.harness.ngtriggers.beans.dto.BulkTriggersDataRequestDTO;
import io.harness.ngtriggers.beans.dto.BulkTriggersFilterRequestDTO;
import io.harness.ngtriggers.beans.dto.BulkTriggersRequestDTO;
import io.harness.ngtriggers.beans.dto.BulkTriggersResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.response.TriggerEventStatus;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.artifact.AcrSpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.GithubSpec;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class TriggerTelemetryHelperTest extends CategoryTest {
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  private final String WEBHOOK_ID = "webhook_id";
  @InjectMocks TriggerTelemetryHelper triggerTelemetryHelper;
  @Mock TelemetryReporter telemetryReporter;
  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    Reflect.on(triggerTelemetryHelper).set("telemetryReporter", telemetryReporter);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testArtifactApiEvent() {
    String accountId = "account";
    BulkTriggersRequestDTO bulkTriggersRequestDTO = BulkTriggersRequestDTO.builder()
                                                        .filters(BulkTriggersFilterRequestDTO.builder()
                                                                     .orgIdentifier("org")
                                                                     .projectIdentifier("proj")
                                                                     .pipelineIdentifier("pipeline")
                                                                     .type("ARTIFACT")
                                                                     .build())
                                                        .data(BulkTriggersDataRequestDTO.builder().enable(true).build())
                                                        .build();

    BulkTriggerDetailDTO t1 = BulkTriggerDetailDTO.builder()
                                  .triggerIdentifier("t1")
                                  .orgIdentifier("org")
                                  .projectIdentifier("proj")
                                  .pipelineIdentifier("pipeline")
                                  .type(NGTriggerType.ARTIFACT)
                                  .build();

    BulkTriggerDetailDTO t2 = BulkTriggerDetailDTO.builder()
                                  .triggerIdentifier("t2")
                                  .orgIdentifier("org")
                                  .projectIdentifier("proj")
                                  .pipelineIdentifier("pipeline")
                                  .type(NGTriggerType.ARTIFACT)
                                  .build();

    BulkTriggerDetailDTO t3 = BulkTriggerDetailDTO.builder()
                                  .triggerIdentifier("t3")
                                  .orgIdentifier("org")
                                  .projectIdentifier("proj")
                                  .pipelineIdentifier("pipeline")
                                  .type(NGTriggerType.ARTIFACT)
                                  .build();

    List<BulkTriggerDetailDTO> triggerDetailDTOList = new ArrayList<>();

    triggerDetailDTOList.add(t1);
    triggerDetailDTOList.add(t2);
    triggerDetailDTOList.add(t3);

    BulkTriggersResponseDTO bulkTriggersResponseDTO =
        BulkTriggersResponseDTO.builder().count(3l).bulkTriggerDetailDTOList(triggerDetailDTOList).build();

    triggerTelemetryHelper.sendBulkToggleTriggersApiEvent(
        accountId, bulkTriggersRequestDTO, bulkTriggersResponseDTO, 100l);

    ArgumentCaptor<HashMap> captor = ArgumentCaptor.forClass(HashMap.class);

    verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), captor.capture(), any(), any(), any());

    HashMap<String, Object> eventPropertiesMap = captor.getValue();

    assert (eventPropertiesMap.get(ACCOUNT)).equals("account");
    assert (eventPropertiesMap.get(ORG)).equals("org");
    assert (eventPropertiesMap.get(PROJECT)).equals("proj");
    assert (eventPropertiesMap.get(PIPELINE_ID)).equals("pipeline");
    assert (eventPropertiesMap.get(TriggerTelemetryHelper.TRIGGER_TYPE)).equals("ARTIFACT");
    assert (eventPropertiesMap.get(TriggerTelemetryHelper.TRIGGER_TOGGLE)).equals(true);
    assert (eventPropertiesMap.get(TIME_TAKEN)).equals(100l);
    assert (eventPropertiesMap.get(COUNT)).equals(3l);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testTriggerCreateWebhookEvent() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .targetIdentifier(PIPELINE_IDENTIFIER)
                                          .identifier(IDENTIFIER)
                                          .name(NAME)
                                          .targetType(TargetType.PIPELINE)
                                          .type(NGTriggerType.WEBHOOK)
                                          .yaml("yaml")
                                          .pollInterval("1m")
                                          .version(0L)
                                          .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .source(NGTriggerSourceV2.builder()
                                               .type(NGTriggerType.WEBHOOK)
                                               .spec(WebhookTriggerConfigV2.builder()
                                                         .type(GITHUB)
                                                         .spec(GithubSpec.builder().build())
                                                         .build())
                                               .build())
                                   .inputSetRefs(ParameterField.createValueField(Collections.emptyList()))
                                   .build())
            .build();

    triggerTelemetryHelper.sendTriggersCreateEvent(ngTriggerEntity, triggerDetails, null, false);

    ArgumentCaptor<HashMap> captor = ArgumentCaptor.forClass(HashMap.class);

    verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), captor.capture(), any(), any(), any());

    HashMap<String, Object> eventPropertiesMap = captor.getValue();

    assert (eventPropertiesMap.get(ACCOUNT)).equals(ACCOUNT_ID);
    assert (eventPropertiesMap.get(ORG)).equals(ORG_IDENTIFIER);
    assert (eventPropertiesMap.get(PROJECT)).equals(PROJ_IDENTIFIER);
    assert (eventPropertiesMap.get(TriggerTelemetryHelper.TRIGGER_TYPE)).equals(NGTriggerType.WEBHOOK.name());
    assert (eventPropertiesMap.get(TriggerTelemetryHelper.TRIGGER_SUB_TYPE)).equals(GITHUB.getValue());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testTriggerExecuteArtifactEvent() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .targetIdentifier(PIPELINE_IDENTIFIER)
                                          .identifier(IDENTIFIER)
                                          .name(NAME)
                                          .targetType(TargetType.PIPELINE)
                                          .type(NGTriggerType.ARTIFACT)
                                          .yaml("yaml")
                                          .pollInterval("1m")
                                          .version(0L)
                                          .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .source(NGTriggerSourceV2.builder()
                                               .type(NGTriggerType.ARTIFACT)
                                               .spec(ArtifactTriggerConfig.builder()
                                                         .type(ArtifactType.ACR)
                                                         .spec(AcrSpec.builder().build())
                                                         .build())
                                               .build())
                                   .inputSetRefs(ParameterField.createValueField(Collections.emptyList()))
                                   .build())
            .build();

    triggerTelemetryHelper.sendTriggersExecutionEvent(
        ngTriggerEntity, triggerDetails, TriggerEventStatus.FinalResponse.FAILED, null, false);

    ArgumentCaptor<HashMap> captor = ArgumentCaptor.forClass(HashMap.class);

    verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), captor.capture(), any(), any(), any());

    HashMap<String, Object> eventPropertiesMap = captor.getValue();

    assert (eventPropertiesMap.get(ACCOUNT)).equals(ACCOUNT_ID);
    assert (eventPropertiesMap.get(ORG)).equals(ORG_IDENTIFIER);
    assert (eventPropertiesMap.get(PROJECT)).equals(PROJ_IDENTIFIER);
    assert (eventPropertiesMap.get(TriggerTelemetryHelper.TRIGGER_TYPE)).equals(NGTriggerType.ARTIFACT.name());
    assert (eventPropertiesMap.get(TriggerTelemetryHelper.TRIGGER_SUB_TYPE)).equals(ArtifactType.ACR.getValue());
    assert (eventPropertiesMap.get(STATUS)).equals(TriggerEventStatus.FinalResponse.FAILED);
  }
}
