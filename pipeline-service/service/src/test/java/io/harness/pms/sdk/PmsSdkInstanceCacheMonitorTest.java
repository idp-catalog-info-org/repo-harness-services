/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSdkInstanceCacheMonitorTest extends CategoryTest {
  @Mock PmsSdkInstanceService pmsSdkInstanceServiceMock;
  @InjectMocks PmsSdkInstanceCacheMonitor pmsSdkInstanceCacheMonitor;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testSyncCacheSkippedWhenDisabled() {
    pmsSdkInstanceServiceMock.shouldUseInstanceCache = false;
    pmsSdkInstanceCacheMonitor.syncCache();
    verifyNoMoreInteractions(pmsSdkInstanceServiceMock);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testSyncCacheWithEmptyDB() {
    pmsSdkInstanceServiceMock.shouldUseInstanceCache = true;
    Cache<String, PmsSdkInstance> cache = Caffeine.newBuilder().build();
    when(pmsSdkInstanceServiceMock.getInstanceCache()).thenReturn(cache);
    when(pmsSdkInstanceServiceMock.getActiveInstancesFromDB()).thenReturn(Collections.emptyList());
    pmsSdkInstanceCacheMonitor.syncCache();
    verify(pmsSdkInstanceServiceMock).getInstanceCache();
    verify(pmsSdkInstanceServiceMock).getActiveInstancesFromDB();
  }
}
