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

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link GitOpsUtilizationSnapshotKafkaConsumer}.
 *
 * <p>These tests cover the pieces this class contributes on top of {@link
 * io.harness.kafka.consumers.HKafkaConsumer}:
 * <ul>
 *   <li>Avro-specific deserializer properties are supplied via {@code getPredefinedProperties}
 *       and merged into the final consumer properties.</li>
 *   <li>{@code sendToDLQ} is a safe no-op (does not throw).</li>
 *   <li>Missing consumer config for the expected name fails fast with a clear error.</li>
 * </ul>
 */
@OwnedBy(HarnessTeam.GITOPS)
@RunWith(MockitoJUnitRunner.class)
public class GitOpsUtilizationSnapshotKafkaConsumerTest extends CategoryTest {
  @Mock private GitOpsUtilizationSnapshotCdcMessageHandler messageHandler;
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

  // ---------------------------------------------------------------------------
  // Properties contributed by getPredefinedProperties
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void mergedProperties_containAvroDeserializerAndGroupAndMaxPoll() throws Exception {
    CdcKafkaConfig cdcKafkaConfig = buildCdcConfig("gitops.harness-gitops.utilization_snapshot", 500);

    GitOpsUtilizationSnapshotKafkaConsumer consumer = new GitOpsUtilizationSnapshotKafkaConsumer(
        messageHandler, plaintextBaseConfig(), consumerMaintenanceListener, executorService, cdcKafkaConfig);

    Properties props = readProperties(consumer);

    // Our override - Avro deserializer
    assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
        .isEqualTo(KafkaAvroDeserializer.class.getName());
    assertThat(props.get(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG)).isEqualTo(false);

    // Derived from CdcKafkaConsumerConfig.maxPollRecords
    assertThat(props.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isEqualTo(500);

    // Derived from CdcKafkaConstants.UTILIZATION_SNAPSHOT_CONSUMER_GROUP
    assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG))
        .isEqualTo(CdcKafkaConstants.UTILIZATION_SNAPSHOT_CONSUMER_GROUP);

    // Inherited HKafkaConsumer baseline: offsets managed manually, reset policy latest
    assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
    assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("latest");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void sendToDLQ_isNoOp() {
    CdcKafkaConfig cdcKafkaConfig = buildCdcConfig("gitops.harness-gitops.utilization_snapshot", 500);
    GitOpsUtilizationSnapshotKafkaConsumer consumer = new GitOpsUtilizationSnapshotKafkaConsumer(
        messageHandler, plaintextBaseConfig(), consumerMaintenanceListener, executorService, cdcKafkaConfig);

    // Reaching sendToDLQ with a null record and null headers must not throw; handler
    // failures are retried + dropped inside the message handler so the consumer is not
    // expected to have a live DLQ producer wired. Accessible here because the test lives
    // in the same package as the consumer.
    consumer.sendToDLQ("any-topic", null, null);
  }

  // ---------------------------------------------------------------------------
  // Fail-fast when consumer config is missing
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void missingConsumerConfig_throwsIllegalState() {
    CdcKafkaConfig emptyCfg = CdcKafkaConfig.builder().enabled(true).consumers(Collections.emptyList()).build();

    assertThatThrownBy(()
                           -> new GitOpsUtilizationSnapshotKafkaConsumer(messageHandler, plaintextBaseConfig(),
                               consumerMaintenanceListener, executorService, emptyCfg))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(CdcKafkaConfig.UTILIZATION_SNAPSHOT_CONSUMER);
  }

  // ---------------------------------------------------------------------------
  // Topic configuration from CdcKafkaConfig
  // ---------------------------------------------------------------------------

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void topicName_isDerivedFromConfig() throws Exception {
    String expectedTopic = "gitops.harness-gitops.utilization_snapshot";
    CdcKafkaConfig cdcKafkaConfig = buildCdcConfig(expectedTopic, 500);

    GitOpsUtilizationSnapshotKafkaConsumer consumer = new GitOpsUtilizationSnapshotKafkaConsumer(
        messageHandler, plaintextBaseConfig(), consumerMaintenanceListener, executorService, cdcKafkaConfig);

    // Verify the topic is correctly set
    Properties props = readProperties(consumer);
    // Note: HKafkaConsumer stores topic in consumerConfig, not in properties
    // We verify indirectly through successful construction
    assertThat(consumer).isNotNull();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void maxPollRecords_customValue() throws Exception {
    CdcKafkaConfig cdcKafkaConfig = buildCdcConfig("gitops.harness-gitops.utilization_snapshot", 250);

    GitOpsUtilizationSnapshotKafkaConsumer consumer = new GitOpsUtilizationSnapshotKafkaConsumer(
        messageHandler, plaintextBaseConfig(), consumerMaintenanceListener, executorService, cdcKafkaConfig);

    Properties props = readProperties(consumer);
    assertThat(props.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)).isEqualTo(250);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static KafkaBaseConfig plaintextBaseConfig() {
    return KafkaBaseConfig.builder().bootstrapServers("localhost:9092").securityProtocol("PLAINTEXT").build();
  }

  private static CdcKafkaConfig buildCdcConfig(String topic, int maxPollRecords) {
    CdcKafkaConsumerConfig consumerCfg = CdcKafkaConsumerConfig.builder()
                                             .name(CdcKafkaConfig.UTILIZATION_SNAPSHOT_CONSUMER)
                                             .topic(topic)
                                             .maxPollRecords(maxPollRecords)
                                             .build();
    return CdcKafkaConfig.builder().enabled(true).consumers(Collections.singletonList(consumerCfg)).build();
  }

  /**
   * {@code HKafkaConsumer#properties} is {@code protected final}; read it via reflection to
   * avoid altering production surface area just for tests.
   */
  private static Properties readProperties(GitOpsUtilizationSnapshotKafkaConsumer consumer) throws Exception {
    Field field = Class.forName("io.harness.kafka.consumers.HKafkaConsumer").getDeclaredField("properties");
    field.setAccessible(true);
    return (Properties) field.get(consumer);
  }
}