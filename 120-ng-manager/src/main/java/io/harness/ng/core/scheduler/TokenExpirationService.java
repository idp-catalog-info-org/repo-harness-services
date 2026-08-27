/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.config.TokenExpirationConfig;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class TokenExpirationService implements Managed {
  private Future<?> future;
  private final ScheduledExecutorService executorService;
  private static final String DEBUG_MESSAGE = "TokenExpirationService: ";
  private final TokenExpirationJob job;
  @Inject @Named("tokenExpirationConfig") private TokenExpirationConfig tokenExpirationConfig;

  @Inject
  public TokenExpirationService(TokenExpirationJob job) {
    this.job = job;
    String threadName = "token-expiration-job";
    this.executorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat(threadName).build());
  }

  @Override
  public void start() throws Exception {
    if (Boolean.TRUE.equals(tokenExpirationConfig.getDisableTokenExpirationJob())) {
      log.info(DEBUG_MESSAGE + "Token expiration job is disabled via config. Skipping start.");
      return;
    }

    log.info(DEBUG_MESSAGE + "started...");
    Random random = new Random();
    // Delay between 900 (15 min) and 1800 (30 min) seconds
    long delay = random.nextInt(901) + tokenExpirationConfig.getTokenExpirationJobInitialDelayInSeconds();
    future = executorService.scheduleWithFixedDelay(
        job, delay, tokenExpirationConfig.getTokenExpirationJobDelayInSeconds(), TimeUnit.SECONDS);
  }

  @Override
  public void stop() throws Exception {
    log.info(DEBUG_MESSAGE + "stopping...");
    future.cancel(false);
    executorService.shutdown();
  }
}
