/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import static io.harness.rule.OwnerRule.HARJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.platform.config.service.api.v1.Config;
import io.harness.platform.config.service.api.v1.ConfigPayload;
import io.harness.platform.config.service.api.v1.ConfigServiceGrpc;
import io.harness.platform.config.service.api.v1.GetConfigResponse;
import io.harness.rule.Owner;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class ConfigServiceUdpEventDerivationConfigReaderTest extends CategoryTest {
  private static final String UUID = UdpEventDerivationConstants.derivationConfigUuid("acc", "service");

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetConfigReturnsEmptyWhenStubNotConfigured() {
    UdpEventDerivationConfigReader reader = new ConfigServiceUdpEventDerivationConfigReader(null);
    assertThat(reader.getConfig("acc", UUID)).isEmpty();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetConfigReturnsEmptyWhenNotFound() throws Exception {
    ConfigServiceGrpc.ConfigServiceBlockingV2Stub stub =
        Mockito.mock(ConfigServiceGrpc.ConfigServiceBlockingV2Stub.class);
    when(stub.withInterceptors(any())).thenReturn(stub);
    when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
    when(stub.getConfig(Mockito.any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

    UdpEventDerivationConfigReader reader = new ConfigServiceUdpEventDerivationConfigReader(stub);
    assertThat(reader.getConfig("acc", UUID)).isEmpty();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetConfigPropagatesTransientFailures() throws Exception {
    ConfigServiceGrpc.ConfigServiceBlockingV2Stub stub =
        Mockito.mock(ConfigServiceGrpc.ConfigServiceBlockingV2Stub.class);
    when(stub.withInterceptors(any())).thenReturn(stub);
    when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
    when(stub.getConfig(Mockito.any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    UdpEventDerivationConfigReader reader = new ConfigServiceUdpEventDerivationConfigReader(stub);
    assertThatThrownBy(() -> reader.getConfig("acc", UUID)).isInstanceOf(StatusRuntimeException.class);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetConfigThrowsOnUnparseableExistingConfig() throws Exception {
    ConfigServiceGrpc.ConfigServiceBlockingV2Stub stub =
        Mockito.mock(ConfigServiceGrpc.ConfigServiceBlockingV2Stub.class);
    when(stub.withInterceptors(any())).thenReturn(stub);
    when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
    GetConfigResponse response =
        GetConfigResponse.newBuilder()
            .setConfig(Config.newBuilder().setPayload(ConfigPayload.newBuilder().setJsonValue("not-json")).build())
            .build();
    when(stub.getConfig(Mockito.any())).thenReturn(response);

    UdpEventDerivationConfigReader reader = new ConfigServiceUdpEventDerivationConfigReader(stub);
    assertThatThrownBy(() -> reader.getConfig("acc", UUID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to parse existing event derivation config");
  }
}
