/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.kafka;

import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.gitops.config.CdcKafkaConstants;
import io.harness.ng.gitops.config.CdcKafkaConsumerConfig;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link GitopsApplicationsKafkaConsumer}.
 */
@OwnedBy(HarnessTeam.GITOPS)
@RunWith(MockitoJUnitRunner.class)
public class GitopsApplicationsKafkaConsumerTest extends CategoryTest {
  @Mock private GitopsApplicationsCdcMessageHandler messageHandler;
  @Mock private ConsumerMaintenanceListener consumerMaintenanceListener;

  private ExecutorService executorService;
  private AutoCloseable mocks;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    executorService = Executors.newSingleThreadExecutor();
  }

  @After
  public void tearDown() throws Exception {
    executorService.shutdownNow();
    mocks.close();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void mergedProperties_containStringDeserializerAndGroupAndMaxPoll() throws Exception {
    CdcKafkaConfig cdcKafkaConfig = buildCdcConfig("gitops.harness-gitops.applications", 500);

    GitopsApplicationsKafkaConsumer consumer = new GitopsApplicationsKafkaConsumer(
        messageHandler, plaintextBaseConfig(), consumerMaintenanceListener, executorService, cdcKafkaConfig);

    Properties props = readProperties(consumer);

    // Applications consumer uses JSON (String) instead of Avro to handle MongoDB's tilde in label keys
    assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)).isEqualTo(StringDeserializer.class.getName());
    assertThat(props.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isEqualTo(500);
    assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo(CdcKafkaConstants.APPLICATIONS_CONSUMER_GROUP);
    assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
    assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("latest");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void sendToDLQ_isNoOp() {
    CdcKafkaConfig cdcKafkaConfig = buildCdcConfig("gitops.harness-gitops.applications", 500);
    GitopsApplicationsKafkaConsumer consumer = new GitopsApplicationsKafkaConsumer(
        messageHandler, plaintextBaseConfig(), consumerMaintenanceListener, executorService, cdcKafkaConfig);

    consumer.sendToDLQ("any-topic", null, null);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void missingConsumerConfig_throwsIllegalState() {
    CdcKafkaConfig emptyCfg = CdcKafkaConfig.builder().enabled(true).consumers(Collections.emptyList()).build();

    assertThatThrownBy(()
                           -> new GitopsApplicationsKafkaConsumer(messageHandler, plaintextBaseConfig(),
                               consumerMaintenanceListener, executorService, emptyCfg))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(CdcKafkaConfig.APPLICATIONS_CONSUMER);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void maxPollRecords_customValue() throws Exception {
    CdcKafkaConfig cdcKafkaConfig = buildCdcConfig("gitops.harness-gitops.applications", 250);

    GitopsApplicationsKafkaConsumer consumer = new GitopsApplicationsKafkaConsumer(
        messageHandler, plaintextBaseConfig(), consumerMaintenanceListener, executorService, cdcKafkaConfig);

    Properties props = readProperties(consumer);
    assertThat(props.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isEqualTo(250);
  }

  private static KafkaBaseConfig plaintextBaseConfig() {
    return KafkaBaseConfig.builder().bootstrapServers("localhost:9092").securityProtocol("PLAINTEXT").build();
  }

  private static CdcKafkaConfig buildCdcConfig(String topic, int maxPollRecords) {
    CdcKafkaConsumerConfig consumerCfg = CdcKafkaConsumerConfig.builder()
                                             .name(CdcKafkaConfig.APPLICATIONS_CONSUMER)
                                             .topic(topic)
                                             .maxPollRecords(maxPollRecords)
                                             .build();
    return CdcKafkaConfig.builder().enabled(true).consumers(Collections.singletonList(consumerCfg)).build();
  }

  private static Properties readProperties(GitopsApplicationsKafkaConsumer consumer) throws Exception {
    Field field = Class.forName("io.harness.kafka.consumers.HKafkaConsumer").getDeclaredField("properties");
    field.setAccessible(true);
    return (Properties) field.get(consumer);
  }
}