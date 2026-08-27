/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.dtos.InstanceSyncPerpetualTaskMappingDTO;
import io.harness.rule.Owner;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandler.K8sInstanceSyncHandler;
import io.harness.service.instancesynchandler.NativeHelmInstanceSyncHandler;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;
import io.harness.service.instancesyncperpetualtaskmapping.InstanceSyncPerpetualTaskMappingService;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ResetKubernetesAndHelmPerpetualTaskMigrationTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String CONNECTOR_ID = "connectorId";
  private static final String PERPETUAL_TASK_ID = "perpetualTaskId";

  @Mock private InstanceSyncPerpetualTaskMappingService perpetualTaskMappingService;
  @Mock private K8sInstanceSyncHandler k8sInstanceSyncHandler;
  @Mock private NativeHelmInstanceSyncHandler helmInstanceSyncHandler;
  @Mock private ConnectorService connectorService;
  @Mock private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;

  private ResetKubernetesAndHelmPerpetualTaskMigration migration;
  private AutoCloseable closeable;
  private RateLimiter rateLimiter;
  private Retry retry;

  @Before
  public void setup() {
    closeable = MockitoAnnotations.openMocks(this);

    // Create a permissive rate limiter for testing
    rateLimiter = RateLimiter.of("test",
        RateLimiterConfig.custom()
            .limitForPeriod(1000)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(10))
            .build());

    // Create a simple retry config for testing
    retry = Retry.of("test", RetryConfig.custom().maxAttempts(1).build());

    migration = new ResetKubernetesAndHelmPerpetualTaskMigration(perpetualTaskMappingService, k8sInstanceSyncHandler,
        helmInstanceSyncHandler, connectorService, instanceSyncPerpetualTaskService, rateLimiter, retry);
  }

  @After
  public void cleanup() throws Exception {
    closeable.close();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMigrate_SuccessfullyResetsKubernetesPerpetualTask() {
    // Given
    InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping = buildPerpetualTaskMapping("Kubernetes");
    ConnectorResponseDTO connectorResponse = buildConnectorResponse(ConnectorType.GCP);

    when(perpetualTaskMappingService.listAllByDeploymentTypes(any())).thenReturn(Stream.of(perpetualTaskMapping));
    when(connectorService.getByRef(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_ID))
        .thenReturn(Optional.of(connectorResponse));

    // When
    migration.migrate();

    // Then
    verify(instanceSyncPerpetualTaskService, times(1))
        .resetPerpetualTaskV2(
            eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), eq(k8sInstanceSyncHandler), eq(connectorResponse.getConnector()));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMigrate_SuccessfullyResetsNativeHelmPerpetualTask() {
    // Given
    InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping = buildPerpetualTaskMapping("NativeHelm");
    ConnectorResponseDTO connectorResponse = buildConnectorResponse(ConnectorType.AWS);

    when(perpetualTaskMappingService.listAllByDeploymentTypes(any())).thenReturn(Stream.of(perpetualTaskMapping));
    when(connectorService.getByRef(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_ID))
        .thenReturn(Optional.of(connectorResponse));

    // When
    migration.migrate();

    // Then
    verify(instanceSyncPerpetualTaskService, times(1))
        .resetPerpetualTaskV2(
            eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), eq(helmInstanceSyncHandler), eq(connectorResponse.getConnector()));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMigrate_SkipsNonMigrateConnectorTypes() {
    // Given
    InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping = buildPerpetualTaskMapping("Kubernetes");
    ConnectorResponseDTO connectorResponse = buildConnectorResponse(ConnectorType.KUBERNETES_CLUSTER);

    when(perpetualTaskMappingService.listAllByDeploymentTypes(any())).thenReturn(Stream.of(perpetualTaskMapping));
    when(connectorService.getByRef(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_ID))
        .thenReturn(Optional.of(connectorResponse));

    // When
    migration.migrate();

    // Then
    verify(instanceSyncPerpetualTaskService, never()).resetPerpetualTaskV2(anyString(), anyString(), any(), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMigrate_HandlesConnectorNotFound() {
    // Given
    InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping = buildPerpetualTaskMapping("Kubernetes");

    when(perpetualTaskMappingService.listAllByDeploymentTypes(any())).thenReturn(Stream.of(perpetualTaskMapping));
    when(connectorService.getByRef(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_ID)).thenReturn(Optional.empty());

    // When
    migration.migrate();

    // Then
    verify(instanceSyncPerpetualTaskService, never()).resetPerpetualTaskV2(anyString(), anyString(), any(), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMigrate_ContinuesOnException() {
    // Given
    InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping1 = buildPerpetualTaskMapping("Kubernetes");
    InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping2 =
        buildPerpetualTaskMappingWithId("Kubernetes", "perpetualTask2");
    ConnectorResponseDTO connectorResponse = buildConnectorResponse(ConnectorType.GCP);

    when(perpetualTaskMappingService.listAllByDeploymentTypes(any()))
        .thenReturn(Stream.of(perpetualTaskMapping1, perpetualTaskMapping2));
    when(connectorService.getByRef(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_ID))
        .thenReturn(Optional.of(connectorResponse));

    doThrow(new RuntimeException("Test exception"))
        .doNothing()
        .when(instanceSyncPerpetualTaskService)
        .resetPerpetualTaskV2(anyString(), anyString(), any(), any());

    // When
    migration.migrate();

    // Then - both perpetual tasks should be attempted
    verify(instanceSyncPerpetualTaskService, times(2)).resetPerpetualTaskV2(eq(ACCOUNT_ID), anyString(), any(), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMigrate_HandlesInvalidDeploymentType() {
    // Given
    InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping = buildPerpetualTaskMapping("InvalidType");
    ConnectorResponseDTO connectorResponse = buildConnectorResponse(ConnectorType.GCP);

    when(perpetualTaskMappingService.listAllByDeploymentTypes(any())).thenReturn(Stream.of(perpetualTaskMapping));
    when(connectorService.getByRef(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_ID))
        .thenReturn(Optional.of(connectorResponse));

    // When
    migration.migrate();

    // Then - should catch the InvalidArgumentsException and continue
    verify(instanceSyncPerpetualTaskService, never()).resetPerpetualTaskV2(anyString(), anyString(), any(), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMigrate_ProcessesAllConnectorTypes() {
    // Test GCP
    testConnectorType(ConnectorType.GCP, true);
    // Test AZURE
    testConnectorType(ConnectorType.AZURE, true);
    // Test AWS
    testConnectorType(ConnectorType.AWS, true);
    // Test RANCHER
    testConnectorType(ConnectorType.RANCHER, true);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMigrate_ProcessesMultiplePerpetualTasks() {
    // Given
    InstanceSyncPerpetualTaskMappingDTO k8sTask = buildPerpetualTaskMappingWithId("Kubernetes", "task1");
    InstanceSyncPerpetualTaskMappingDTO helmTask = buildPerpetualTaskMappingWithId("NativeHelm", "task2");
    ConnectorResponseDTO connectorResponse = buildConnectorResponse(ConnectorType.GCP);

    when(perpetualTaskMappingService.listAllByDeploymentTypes(any())).thenReturn(Stream.of(k8sTask, helmTask));
    when(connectorService.getByRef(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_ID))
        .thenReturn(Optional.of(connectorResponse));

    // When
    migration.migrate();

    // Then
    verify(instanceSyncPerpetualTaskService, times(1))
        .resetPerpetualTaskV2(
            eq(ACCOUNT_ID), eq("task1"), eq(k8sInstanceSyncHandler), eq(connectorResponse.getConnector()));
    verify(instanceSyncPerpetualTaskService, times(1))
        .resetPerpetualTaskV2(
            eq(ACCOUNT_ID), eq("task2"), eq(helmInstanceSyncHandler), eq(connectorResponse.getConnector()));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testMigrate_EmptyStream() {
    // Given
    when(perpetualTaskMappingService.listAllByDeploymentTypes(any())).thenReturn(Stream.empty());

    // When
    assertThatCode(() -> migration.migrate()).doesNotThrowAnyException();

    // Then
    verify(instanceSyncPerpetualTaskService, never()).resetPerpetualTaskV2(anyString(), anyString(), any(), any());
  }

  private void testConnectorType(ConnectorType connectorType, boolean shouldReset) {
    // Given
    InstanceSyncPerpetualTaskMappingDTO perpetualTaskMapping = buildPerpetualTaskMapping("Kubernetes");
    ConnectorResponseDTO connectorResponse = buildConnectorResponse(connectorType);

    when(perpetualTaskMappingService.listAllByDeploymentTypes(any())).thenReturn(Stream.of(perpetualTaskMapping));
    when(connectorService.getByRef(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_ID))
        .thenReturn(Optional.of(connectorResponse));

    // When
    migration.migrate();

    // Then
    if (shouldReset) {
      verify(instanceSyncPerpetualTaskService, times(1))
          .resetPerpetualTaskV2(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), any(AbstractInstanceSyncHandler.class),
              eq(connectorResponse.getConnector()));
    } else {
      verify(instanceSyncPerpetualTaskService, never()).resetPerpetualTaskV2(anyString(), anyString(), any(), any());
    }
  }

  private InstanceSyncPerpetualTaskMappingDTO buildPerpetualTaskMapping(String deploymentType) {
    return buildPerpetualTaskMappingWithId(deploymentType, PERPETUAL_TASK_ID);
  }

  private InstanceSyncPerpetualTaskMappingDTO buildPerpetualTaskMappingWithId(
      String deploymentType, String perpetualTaskId) {
    return InstanceSyncPerpetualTaskMappingDTO.builder()
        .id("id")
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .perpetualTaskId(perpetualTaskId)
        .connectorIdentifier(CONNECTOR_ID)
        .deploymentType(deploymentType)
        .build();
  }

  private ConnectorResponseDTO buildConnectorResponse(ConnectorType connectorType) {
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .connectorType(connectorType)
                                         .connectorConfig(GcpConnectorDTO.builder().build())
                                         .build();
    return ConnectorResponseDTO.builder().connector(connectorInfo).build();
  }
}
