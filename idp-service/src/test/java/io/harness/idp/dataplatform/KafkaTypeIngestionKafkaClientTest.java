/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import static io.harness.rule.OwnerRule.HARJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.platform.type.ingestion.TypeIngestionRequest;
import io.harness.rule.Owner;

import java.util.Optional;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class KafkaTypeIngestionKafkaClientTest extends CategoryTest {
  private static void mockSendSuccess(HKafkaProtoProducer producer) {
    doAnswer(invocation -> {
      Callback cb = invocation.getArgument(4);
      RecordMetadata rm = new RecordMetadata(new TopicPartition(invocation.getArgument(0), 0), 0, 0, 0, 0, 0);
      cb.onCompletion(rm, null);
      return null;
    })
        .when(producer)
        .send(any(), any(), anyMap(), any(), any(Callback.class));
  }

  private static void mockSendFailure(HKafkaProtoProducer producer, Exception ex) {
    doAnswer(invocation -> {
      Callback cb = invocation.getArgument(4);
      cb.onCompletion(null, ex);
      return null;
    })
        .when(producer)
        .send(any(), any(), anyMap(), any(), any(Callback.class));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSendTypeIngestionRequestSendsToConfiguredTopic() {
    HKafkaProtoProducer producer = mock(HKafkaProtoProducer.class);
    mockSendSuccess(producer);
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    config.setTypeIngestionRequestTopic("udp-topic");
    TypeIngestionKafkaClient client = new KafkaTypeIngestionKafkaClient(Optional.of(producer), config);
    TypeIngestionRequest request = TypeIngestionRequest.newBuilder().setTenantId("acc1").build();

    boolean sent = client.sendTypeIngestionRequest("acc1", request);

    assertThat(sent).isTrue();
    verify(producer).send(eq("udp-topic"), eq(request), anyMap(), eq("acc1"), any(Callback.class));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSendTypeIngestionRequestReturnsFalseWhenProducerMissing() {
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    TypeIngestionKafkaClient client = new KafkaTypeIngestionKafkaClient(Optional.empty(), config);
    TypeIngestionRequest request = TypeIngestionRequest.newBuilder().setTenantId("acc1").build();

    boolean sent = client.sendTypeIngestionRequest("acc1", request);

    assertThat(sent).isFalse();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSendTypeIngestionRequestReturnsFalseOnProducerFailure() {
    HKafkaProtoProducer producer = mock(HKafkaProtoProducer.class);
    mockSendFailure(
        producer, new org.apache.kafka.common.errors.TopicAuthorizationException("Not authorized to access topics"));
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    TypeIngestionKafkaClient client = new KafkaTypeIngestionKafkaClient(Optional.of(producer), config);
    TypeIngestionRequest request = TypeIngestionRequest.newBuilder().setTenantId("acc1").build();

    boolean sent = client.sendTypeIngestionRequest("acc1", request);

    assertThat(sent).isFalse();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSendTypeIngestionRequestReturnsFalseOnSyncException() {
    HKafkaProtoProducer producer = mock(HKafkaProtoProducer.class);
    doThrow(new RuntimeException("sync failure"))
        .when(producer)
        .send(any(), any(), anyMap(), any(), any(Callback.class));
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    TypeIngestionKafkaClient client = new KafkaTypeIngestionKafkaClient(Optional.of(producer), config);
    TypeIngestionRequest request = TypeIngestionRequest.newBuilder().setTenantId("acc1").build();

    boolean sent = client.sendTypeIngestionRequest("acc1", request);

    assertThat(sent).isFalse();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSendTypeIngestionRequestReturnsFalseWhenRequestTenantMissing() {
    HKafkaProtoProducer producer = mock(HKafkaProtoProducer.class);
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    TypeIngestionKafkaClient client = new KafkaTypeIngestionKafkaClient(Optional.of(producer), config);
    TypeIngestionRequest request = TypeIngestionRequest.newBuilder().build();

    boolean sent = client.sendTypeIngestionRequest("acc1", request);

    assertThat(sent).isFalse();
    verify(producer, never()).send(any(), any(), anyMap(), any(), any(Callback.class));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSendTypeIngestionRequestUsesRequestTenantAsPartitioningKey() {
    HKafkaProtoProducer producer = mock(HKafkaProtoProducer.class);
    mockSendSuccess(producer);
    UdpTypeIngestionConfig config = new UdpTypeIngestionConfig();
    TypeIngestionKafkaClient client = new KafkaTypeIngestionKafkaClient(Optional.of(producer), config);
    TypeIngestionRequest request = TypeIngestionRequest.newBuilder().setTenantId("acc_from_request").build();

    boolean sent = client.sendTypeIngestionRequest("acc_from_method", request);

    assertThat(sent).isTrue();
    verify(producer).send(
        eq("data-platform-type-ingestion-request"), eq(request), anyMap(), eq("acc_from_request"), any(Callback.class));
  }
}
