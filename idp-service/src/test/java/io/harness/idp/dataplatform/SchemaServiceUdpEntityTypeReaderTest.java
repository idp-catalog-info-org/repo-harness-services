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
import io.harness.platform.schema.service.api.v1.EntityType;
import io.harness.platform.schema.service.api.v1.GetTypeResponse;
import io.harness.platform.schema.service.api.v1.ObjectType;
import io.harness.platform.schema.service.api.v1.SchemaServiceGrpc;
import io.harness.rule.Owner;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class SchemaServiceUdpEntityTypeReaderTest extends CategoryTest {
  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetEntityTypeReturnsEmptyWhenStubNotConfigured() {
    UdpEntityTypeReader reader = new SchemaServiceUdpEntityTypeReader(null);
    assertThat(reader.getEntityType("acc", "idp:service")).isEmpty();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetEntityTypeReturnsTypeWhenFound() throws Exception {
    SchemaServiceGrpc.SchemaServiceBlockingV2Stub stub =
        Mockito.mock(SchemaServiceGrpc.SchemaServiceBlockingV2Stub.class);
    when(stub.withInterceptors(any())).thenReturn(stub);
    when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
    ObjectType type =
        ObjectType.newBuilder().setEntityType(EntityType.newBuilder().setId("idp:service").build()).build();
    when(stub.getType(Mockito.any())).thenReturn(GetTypeResponse.newBuilder().setType(type).build());

    UdpEntityTypeReader reader = new SchemaServiceUdpEntityTypeReader(stub);
    assertThat(reader.getEntityType("acc", "idp:service")).contains(type);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetEntityTypeReturnsEmptyWhenNotFound() throws Exception {
    SchemaServiceGrpc.SchemaServiceBlockingV2Stub stub =
        Mockito.mock(SchemaServiceGrpc.SchemaServiceBlockingV2Stub.class);
    when(stub.withInterceptors(any())).thenReturn(stub);
    when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
    when(stub.getType(Mockito.any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

    UdpEntityTypeReader reader = new SchemaServiceUdpEntityTypeReader(stub);
    assertThat(reader.getEntityType("acc", "idp:service")).isEmpty();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetEntityTypePropagatesTransientFailures() throws Exception {
    SchemaServiceGrpc.SchemaServiceBlockingV2Stub stub =
        Mockito.mock(SchemaServiceGrpc.SchemaServiceBlockingV2Stub.class);
    when(stub.withInterceptors(any())).thenReturn(stub);
    when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
    when(stub.getType(Mockito.any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

    UdpEntityTypeReader reader = new SchemaServiceUdpEntityTypeReader(stub);
    assertThatThrownBy(() -> reader.getEntityType("acc", "idp:service"))
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(
            ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
  }
}
