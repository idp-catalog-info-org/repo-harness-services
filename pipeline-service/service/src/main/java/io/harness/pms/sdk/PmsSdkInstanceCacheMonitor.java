/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * A monitor to keep the in-memory Caffeine cache in sync with MongoDB.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSdkInstanceCacheMonitor {
  @Inject PmsSdkInstanceService pmsSdkInstanceService;

  protected ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1,
      new ThreadFactoryBuilder()
          .setNameFormat("pipeline-sdk-instance-sync-Thread-%d")
          .setPriority(Thread.NORM_PRIORITY)
          .build());

  public void scheduleCacheSync() {
    long initialDelay = new SecureRandom().nextInt(1);
    try {
      executorService.scheduleAtFixedRate(this::syncCache, initialDelay, 1, TimeUnit.HOURS);
    } catch (Exception e) {
      log.error("Exception while creating a scheduled sdk instance cache sync", e);
    }
  }

  public void syncCache() {
    if (!pmsSdkInstanceService.shouldUseInstanceCache) {
      return;
    }
    log.info("Starting to monitor if sdkInstanceCache and sdkInstances in db are in sync");
    Cache<String, PmsSdkInstance> instanceCache = pmsSdkInstanceService.getInstanceCache();
    List<PmsSdkInstance> pmsSdkInstances = pmsSdkInstanceService.getActiveInstancesFromDB();
    for (PmsSdkInstance sdkInstance : pmsSdkInstances) {
      PmsSdkInstance cached = instanceCache.getIfPresent(sdkInstance.getName());
      if (!Objects.equals(cached, sdkInstance)) {
        log.warn("SdkInstance cache out of sync with mongo for module {}, updating", sdkInstance.getName());
        instanceCache.put(sdkInstance.getName(), sdkInstance);
      }
    }
  }
}
