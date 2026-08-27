/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.debezium;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.junit.Assert.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Consumer;
import io.harness.ff.FeatureFlagService;
import io.harness.idp.config.CdcKafkaConfig;
import io.harness.queue.QueueController;
import io.harness.rule.Owner;

import javax.cache.Cache;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class CatalogRedisEventConsumerTest extends CategoryTest {
  @Mock Consumer redisConsumer;
  @Mock QueueController queueController;
  @Mock CatalogChangeEventHandler eventHandler;
  @Mock Cache<String, Long> eventsCache;
  @Mock FeatureFlagService featureFlagService;

  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConsumerInitialization() {
    CatalogRedisEventConsumer consumer = new CatalogRedisEventConsumer(
        redisConsumer, queueController, eventHandler, eventsCache, featureFlagService, CdcKafkaConfig.defaultConfig());
    assertNotNull(consumer);
  }
}