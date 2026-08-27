/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.platform.type.ingestion.TypeIngestionRequest;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class KafkaTypeIngestionKafkaClient implements TypeIngestionKafkaClient {
  private static final String DEFAULT_TYPE_INGESTION_REQUEST_TOPIC = "data-platform-type-ingestion-request";
  private final Optional<HKafkaProtoProducer> kafkaProtoProducer;
  private final UdpTypeIngestionConfig udpTypeIngestionConfig;

  @Inject
  public KafkaTypeIngestionKafkaClient(
      @KafkaModule.General Optional<HKafkaProtoProducer> kafkaProtoProducer, UdpTypeIngestionConfig config) {
    this.kafkaProtoProducer = kafkaProtoProducer;
    this.udpTypeIngestionConfig = config;
  }

  @Override
  public boolean sendTypeIngestionRequest(String tenantId, TypeIngestionRequest request) {
    if (request == null || isEmpty(request.getTenantId())) {
      log.warn("{} kafka producer skip type ingestion request due to missing request.tenant_id",
          UdpEventDerivationConstants.LOG_PREFIX);
      return false;
    }
    if (kafkaProtoProducer.isEmpty()) {
      log.warn("{} kafka producer skip type ingestion request tenant={} because confluent producer is not initialized",
          UdpEventDerivationConstants.LOG_PREFIX, request.getTenantId());
      return false;
    }

    String requestTenantId = request.getTenantId();
    if (!isEmpty(tenantId) && !tenantId.equals(requestTenantId)) {
      log.warn("{} kafka producer tenant mismatch methodTenantId={} requestTenantId={} using requestTenantId as key",
          UdpEventDerivationConstants.LOG_PREFIX, tenantId, requestTenantId);
    }

    String configuredTopic = udpTypeIngestionConfig.getTypeIngestionRequestTopic();
    String topic = isEmpty(configuredTopic) ? DEFAULT_TYPE_INGESTION_REQUEST_TOPIC : configuredTopic;

    try {
      log.info("{} kafka producer send start tenant={} topic={} objectSpecs={} configSpecs={}",
          UdpEventDerivationConstants.LOG_PREFIX, requestTenantId, topic,
          request.getUpgradePack().getObjectTypeUpgradeSpecs().getSpecsCount(),
          request.getUpgradePack().getConfigUpgradeSpecs().getSpecsCount());
      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<Exception> callbackError = new AtomicReference<>();
      kafkaProtoProducer.get().send(topic, request,
          Map.of("entity_type", "idp_kind", "action", "upsert", "tenant_id", requestTenantId), requestTenantId,
          (recordMetadata, exception) -> {
            if (exception != null) {
              callbackError.set(exception);
              log.error("{} kafka producer async callback FAILED tenant={} topic={} error={}",
                  UdpEventDerivationConstants.LOG_PREFIX, requestTenantId, topic, exception.getMessage(), exception);
            } else {
              log.info("{} kafka producer async callback SUCCESS tenant={} topic={} partition={} offset={}",
                  UdpEventDerivationConstants.LOG_PREFIX, requestTenantId, topic, recordMetadata.partition(),
                  recordMetadata.offset());
            }
            latch.countDown();
          });
      boolean completed = latch.await(30, TimeUnit.SECONDS);
      if (!completed) {
        log.error("{} kafka producer send TIMEOUT (30s) tenant={} topic={}", UdpEventDerivationConstants.LOG_PREFIX,
            requestTenantId, topic);
        return false;
      }
      if (callbackError.get() != null) {
        log.error("{} kafka producer send FAILED tenant={} topic={}", UdpEventDerivationConstants.LOG_PREFIX,
            requestTenantId, topic);
        return false;
      }
      log.info("{} kafka producer send CONFIRMED tenant={} topic={}", UdpEventDerivationConstants.LOG_PREFIX,
          requestTenantId, topic);
      return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.error("{} kafka producer send INTERRUPTED tenant={} topic={}", UdpEventDerivationConstants.LOG_PREFIX,
          requestTenantId, topic, ex);
      return false;
    } catch (Exception ex) {
      log.error("{} kafka producer failed to send type ingestion request tenant={} topic={}",
          UdpEventDerivationConstants.LOG_PREFIX, requestTenantId, topic, ex);
      return false;
    }
  }
}
