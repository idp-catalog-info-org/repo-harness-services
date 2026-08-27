/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.dtos.InstanceSyncPerpetualTaskMappingDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.migration.beans.NGMigration;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandler.K8sInstanceSyncHandler;
import io.harness.service.instancesynchandler.NativeHelmInstanceSyncHandler;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;
import io.harness.service.instancesyncperpetualtaskmapping.InstanceSyncPerpetualTaskMappingService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResetKubernetesAndHelmPerpetualTaskMigration implements NGMigration {
  private static final Set<String> MIGRATE_PERPETUAL_TASK_DEPLOYMENT_TYPES = Set.of("Kubernetes", "NativeHelm");
  private static final Set<ConnectorType> MIGRATE_CONNECTOR_TYPES =
      Set.of(ConnectorType.GCP, ConnectorType.AZURE, ConnectorType.AWS, ConnectorType.RANCHER);

  private final InstanceSyncPerpetualTaskMappingService perpetualTaskMappingService;
  private final K8sInstanceSyncHandler k8sInstanceSyncHandler;
  private final NativeHelmInstanceSyncHandler helmInstanceSyncHandler;
  private final ConnectorService connectorService;
  private final InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;
  private final RateLimiter rateLimiter;
  private final Retry retry;

  @Inject
  public ResetKubernetesAndHelmPerpetualTaskMigration(
      InstanceSyncPerpetualTaskMappingService perpetualTaskMappingService,
      K8sInstanceSyncHandler k8sInstanceSyncHandler, NativeHelmInstanceSyncHandler helmInstanceSyncHandler,
      @Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService) {
    this(perpetualTaskMappingService, k8sInstanceSyncHandler, helmInstanceSyncHandler, connectorService,
        instanceSyncPerpetualTaskService,
        RateLimiter.of("ResetKubernetesAndHelmPerpetualTaskMigration",
            RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofMinutes(5))
                .build()),
        Retry.of("ResetKubernetesAndHelmPerpetualTaskMigration",
            RetryConfig.custom()
                .retryExceptions(RequestNotPermitted.class)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(10)))
                .maxAttempts(10)
                .build()));
  }

  public ResetKubernetesAndHelmPerpetualTaskMigration(
      InstanceSyncPerpetualTaskMappingService perpetualTaskMappingService,
      K8sInstanceSyncHandler k8sInstanceSyncHandler, NativeHelmInstanceSyncHandler helmInstanceSyncHandler,
      @Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService, RateLimiter rateLimiter, Retry retry) {
    this.perpetualTaskMappingService = perpetualTaskMappingService;
    this.k8sInstanceSyncHandler = k8sInstanceSyncHandler;
    this.helmInstanceSyncHandler = helmInstanceSyncHandler;
    this.connectorService = connectorService;
    this.instanceSyncPerpetualTaskService = instanceSyncPerpetualTaskService;
    this.rateLimiter = rateLimiter;
    this.retry = retry;
  }

  @Override
  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  public void migrate() {
    try (var perpetualTaskMappingStream =
             perpetualTaskMappingService.listAllByDeploymentTypes(MIGRATE_PERPETUAL_TASK_DEPLOYMENT_TYPES)) {
      perpetualTaskMappingStream.forEach(perpetualTaskMapping -> {
        try {
          resetByPerpetualTaskMappingWithRateLimit(perpetualTaskMapping);
        } catch (Throwable e) {
          log.error("Failed to reset perpetual task id {}", perpetualTaskMapping.getPerpetualTaskId(), e);
        }
      });
    }
  }

  private void resetByPerpetualTaskMappingWithRateLimit(InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping)
      throws Throwable {
    Retry
        .decorateCheckedRunnable(retry,
            ()
                -> RateLimiter
                       .decorateCheckedRunnable(rateLimiter, () -> resetByPerpetualTaskMapping(perpetualTaskMapping))
                       .run())
        .run();
  }

  private void resetByPerpetualTaskMapping(InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping) {
    var instanceSyncHandler = getInstanceSyncHandler(perpetualTaskMapping);
    var connectorResponse =
        connectorService.getByRef(perpetualTaskMapping.getAccountId(), perpetualTaskMapping.getOrgId(),
            perpetualTaskMapping.getProjectId(), perpetualTaskMapping.getConnectorIdentifier());
    if (connectorResponse.isEmpty()) {
      throw new InvalidArgumentsException(String.format("Unable to find connector [%s/%s/%s: %s]",
          perpetualTaskMapping.getAccountId(), perpetualTaskMapping.getOrgId(), perpetualTaskMapping.getProjectId(),
          perpetualTaskMapping.getConnectorIdentifier()));
    }

    var connector = connectorResponse.get().getConnector();
    if (!MIGRATE_CONNECTOR_TYPES.contains(connector.getConnectorType())) {
      log.info("Ignore migration for perpetual task [{}] and connector type [{}]",
          perpetualTaskMapping.getPerpetualTaskId(), connector.getConnectorType());
      return;
    }

    instanceSyncPerpetualTaskService.resetPerpetualTaskV2(
        perpetualTaskMapping.getAccountId(), perpetualTaskMapping.getPerpetualTaskId(), instanceSyncHandler, connector);
    log.info("Successfully reset perpetual task {}", perpetualTaskMapping.getPerpetualTaskId());
  }

  private AbstractInstanceSyncHandler getInstanceSyncHandler(InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping) {
    return switch (perpetualTaskMapping.getDeploymentType()) {
            case "Kubernetes" -> k8sInstanceSyncHandler;
            case "NativeHelm" -> helmInstanceSyncHandler;
            default -> throw new InvalidArgumentsException(String.format("Unexpected deployment type [%s] from perpetualTaskMapping [%s]",
                    perpetualTaskMapping.getDeploymentType(), perpetualTaskMapping.getId()));
        };
    }
}
