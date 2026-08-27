/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.iteratorhandler;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.entities.AppConfigEntity;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ConfigPurgeHandlerTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks private ConfigPurgeHandler handler;
  @Mock private PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private ConfigManagerService configManagerService;
  @Mock private IdpIteratorMetricRecorder idpIteratorMetricRecorder;

  private static final String TEST_ACCOUNT1 = "account1";
  private static final String TEST_ACCOUNT2 = "account2";
  private static final String CONFIG_ID1 = "config-1";
  private static final String CONFIG_ID2 = "config-2";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleWithSuccessfulPurge() {
    AppConfigEntity entity1 = AppConfigEntity.builder().accountIdentifier(TEST_ACCOUNT1).configId(CONFIG_ID1).build();
    AppConfigEntity entity2 = AppConfigEntity.builder().accountIdentifier(TEST_ACCOUNT2).configId(CONFIG_ID2).build();

    when(configManagerService.deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo())
        .thenReturn(Arrays.asList(entity1, entity2));

    handler.handle(IteratorEntity.builder().build());

    verify(configManagerService, times(1)).deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo();
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("ConfigPurgeHandler", TEST_ACCOUNT1);
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("ConfigPurgeHandler", TEST_ACCOUNT2);
    verify(idpIteratorMetricRecorder, times(0)).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleWithEmptyPurgeList() {
    when(configManagerService.deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo())
        .thenReturn(Collections.emptyList());

    handler.handle(IteratorEntity.builder().build());

    verify(configManagerService, times(1)).deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo();
    verify(idpIteratorMetricRecorder, times(0)).recordSuccess(any(), any());
    verify(idpIteratorMetricRecorder, times(0)).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleWithSingleEntity() {
    AppConfigEntity entity = AppConfigEntity.builder().accountIdentifier(TEST_ACCOUNT1).configId(CONFIG_ID1).build();

    when(configManagerService.deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo())
        .thenReturn(Collections.singletonList(entity));

    handler.handle(IteratorEntity.builder().build());

    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("ConfigPurgeHandler", TEST_ACCOUNT1);
    verify(idpIteratorMetricRecorder, times(0)).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleWithException() {
    when(configManagerService.deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo())
        .thenThrow(new RuntimeException("Database error"));

    handler.handle(IteratorEntity.builder().build());

    verify(configManagerService, times(1)).deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo();
    verify(idpIteratorMetricRecorder, times(1)).recordFailure("ConfigPurgeHandler", null);
    verify(idpIteratorMetricRecorder, times(0)).recordSuccess(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleWithMultipleAccountsSameAccount() {
    AppConfigEntity entity1 = AppConfigEntity.builder().accountIdentifier(TEST_ACCOUNT1).configId(CONFIG_ID1).build();
    AppConfigEntity entity2 = AppConfigEntity.builder().accountIdentifier(TEST_ACCOUNT1).configId(CONFIG_ID2).build();

    when(configManagerService.deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo())
        .thenReturn(Arrays.asList(entity1, entity2));

    handler.handle(IteratorEntity.builder().build());

    verify(idpIteratorMetricRecorder, times(2)).recordSuccess("ConfigPurgeHandler", TEST_ACCOUNT1);
    verify(idpIteratorMetricRecorder, times(0)).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    handler.registerIterators(IteratorConfig.builder().threadPoolCount(2).targetIntervalInSeconds(604800).build());
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(ConfigPurgeHandler.class), any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
