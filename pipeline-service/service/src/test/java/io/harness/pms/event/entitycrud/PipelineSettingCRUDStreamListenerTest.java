/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SETTINGS;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SETTINGS_CATEGORY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.entity_crud.settings.SettingsEntityChangeDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.rule.Owner;
import io.harness.telemetry.helpers.PlanConcurrencyInstrumentationHelper;
import io.harness.utils.PmsFeatureFlagService;

import com.google.protobuf.StringValue;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineSettingCRUDStreamListenerTest extends CategoryTest {
  @InjectMocks PipelineSettingCRUDStreamListener pipelineSettingCRUDStreamListener;
  @Mock NGTriggerService ngTriggerService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PlanConcurrencyInstrumentationHelper planConcurrencyInstrumentationHelper;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testEmptyHandleMessage() {
    Message message = Message.newBuilder().build();
    assertTrue(pipelineSettingCRUDStreamListener.handleMessage(message));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testHandleMessage() {
    String ACCOUNT_ID = "accountId";
    String ARTIFACT_COLLECTION_NG_INTERVAL_MINUTES = "artifact_collection_ng_interval_minutes";
    Map<String, String> settingsIdentifier = new HashMap<>();
    settingsIdentifier.put(ARTIFACT_COLLECTION_NG_INTERVAL_MINUTES, "2");
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, SETTINGS)
                                          .putMetadata(ACTION, UPDATE_ACTION)
                                          .putMetadata(SETTINGS_CATEGORY, SettingCategory.PMS.name())
                                          .setData(SettingsEntityChangeDTO.newBuilder()
                                                       .putAllSettingIdentifiers(settingsIdentifier)
                                                       .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                       .build()
                                                       .toByteString())
                                          .build())
                          .build();

    assertTrue(pipelineSettingCRUDStreamListener.handleMessage(message));

    // Verify pipeline metadata delete
    verify(ngTriggerService, times(1)).resetPollingTriggers(any(), anyBoolean(), any());
    // A non-concurrency setting must NOT fire the concurrency config-change telemetry.
    verify(planConcurrencyInstrumentationHelper, never())
        .sendConcurrencyConfigChangeEvent(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void concurrencyModeChangeFiresConfigTelemetry() {
    String ACCOUNT_ID = "accountId";
    Map<String, String> settingsIdentifier = new HashMap<>();
    settingsIdentifier.put("pipeline_execution_concurrency_mode", "PerProject");
    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, SETTINGS)
                                          .putMetadata(ACTION, UPDATE_ACTION)
                                          .putMetadata(SETTINGS_CATEGORY, SettingCategory.PMS.name())
                                          .setData(SettingsEntityChangeDTO.newBuilder()
                                                       .putAllSettingIdentifiers(settingsIdentifier)
                                                       .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                       .build()
                                                       .toByteString())
                                          .build())
                          .build();

    assertTrue(pipelineSettingCRUDStreamListener.handleMessage(message));

    verify(planConcurrencyInstrumentationHelper, times(1))
        .sendConcurrencyConfigChangeEvent(
            eq(ACCOUNT_ID), eq(""), eq(""), eq("pipeline_execution_concurrency_mode"), eq("PerProject"));
  }
}
