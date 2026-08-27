/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution.consumers;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.execution.events.PmsSdkKafkaTopicResolver;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PmsSdkKafkaTopicResolverTest {
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResolveDefaultServiceScopedKafkaTopics() {
    assertThat(PmsSdkKafkaTopicResolver.getInterruptTopic(Collections.emptyMap(), "pms"))
        .isEqualTo("pipeline_interrupt_pms");
    assertThat(PmsSdkKafkaTopicResolver.getOrchestrationTopic(Collections.emptyMap(), "pms"))
        .isEqualTo("pipeline_orchestration_pms");
    assertThat(PmsSdkKafkaTopicResolver.getFacilitationTopic(Collections.emptyMap(), "pms"))
        .isEqualTo("pipeline_node_facilitation_pms");
    assertThat(PmsSdkKafkaTopicResolver.getNodeStartTopic(Collections.emptyMap(), "pms"))
        .isEqualTo("pipeline_node_start_pms");
    assertThat(PmsSdkKafkaTopicResolver.getProgressTopic(Collections.emptyMap(), "pms"))
        .isEqualTo("pipeline_node_progress_pms");
    assertThat(PmsSdkKafkaTopicResolver.getNodeAdviseTopic(Collections.emptyMap(), "pms"))
        .isEqualTo("pipeline_node_advise_pms");
    assertThat(PmsSdkKafkaTopicResolver.getNodeResumeTopic(Collections.emptyMap(), "pms"))
        .isEqualTo("pipeline_node_resume_pms");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResolveServiceScopedKafkaTopicsFromEnvTemplates() {
    Map<String, String> env = Map.of(PmsSdkKafkaTopicResolver.PIPELINE_INTERRUPT_KAFKA_TOPIC_TEMPLATE_ENV,
        "custom_interrupt_%s", PmsSdkKafkaTopicResolver.PIPELINE_ORCHESTRATION_KAFKA_TOPIC_TEMPLATE_ENV,
        "custom_orchestration_%s", PmsSdkKafkaTopicResolver.PIPELINE_NODE_FACILITATION_KAFKA_TOPIC_TEMPLATE_ENV,
        "custom_facilitation_%s", PmsSdkKafkaTopicResolver.PIPELINE_NODE_START_KAFKA_TOPIC_TEMPLATE_ENV,
        "custom_start_%s", PmsSdkKafkaTopicResolver.PIPELINE_NODE_PROGRESS_KAFKA_TOPIC_TEMPLATE_ENV,
        "custom_progress_%s", PmsSdkKafkaTopicResolver.PIPELINE_NODE_ADVISE_KAFKA_TOPIC_TEMPLATE_ENV,
        "custom_advise_%s", PmsSdkKafkaTopicResolver.PIPELINE_NODE_RESUME_KAFKA_TOPIC_TEMPLATE_ENV, "custom_resume_%s");

    assertThat(PmsSdkKafkaTopicResolver.getInterruptTopic(env, "pms")).isEqualTo("custom_interrupt_pms");
    assertThat(PmsSdkKafkaTopicResolver.getOrchestrationTopic(env, "pms")).isEqualTo("custom_orchestration_pms");
    assertThat(PmsSdkKafkaTopicResolver.getFacilitationTopic(env, "pms")).isEqualTo("custom_facilitation_pms");
    assertThat(PmsSdkKafkaTopicResolver.getNodeStartTopic(env, "pms")).isEqualTo("custom_start_pms");
    assertThat(PmsSdkKafkaTopicResolver.getProgressTopic(env, "pms")).isEqualTo("custom_progress_pms");
    assertThat(PmsSdkKafkaTopicResolver.getNodeAdviseTopic(env, "pms")).isEqualTo("custom_advise_pms");
    assertThat(PmsSdkKafkaTopicResolver.getNodeResumeTopic(env, "pms")).isEqualTo("custom_resume_pms");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResolveFixedKafkaTopicsFromEnv() {
    Map<String, String> env = Map.of("ORCHESTRATION_LOG_KAFKA_TOPIC_NAME", "custom_orchestration_log",
        "INITIATE_NODE_KAFKA_TOPIC_NAME", "custom_initiate_node", "INITIATE_NODE_BATCH_KAFKA_TOPIC_NAME",
        "custom_initiate_node_batch", "PIPELINE_SDK_RESPONSE_KAFKA_TOPIC_NAME", "custom_sdk_response");

    assertThat(PmsSdkKafkaTopicResolver.getOrchestrationLogTopic(env)).isEqualTo("custom_orchestration_log");
    assertThat(PmsSdkKafkaTopicResolver.getInitiateNodeTopic(env)).isEqualTo("custom_initiate_node");
    assertThat(PmsSdkKafkaTopicResolver.getInitiateNodeBatchTopic(env)).isEqualTo("custom_initiate_node_batch");
    assertThat(PmsSdkKafkaTopicResolver.getPipelineSdkResponseTopic(env)).isEqualTo("custom_sdk_response");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldRejectServiceScopedKafkaTemplateWithoutPlaceholder() {
    assertThatThrownBy(
        ()
            -> PmsSdkKafkaTopicResolver.getInterruptTopic(
                Map.of(PmsSdkKafkaTopicResolver.PIPELINE_INTERRUPT_KAFKA_TOPIC_TEMPLATE_ENV, "custom_interrupt"),
                "pms"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Topic template from env var must contain '%s' placeholder, got: custom_interrupt");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResolveKafkaConsumerGroupWithDefault() {
    assertThat(PmsSdkKafkaTopicResolver.getKafkaConsumerGroupId(Collections.emptyMap(), "pms")).isEqualTo("pms");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResolveKafkaConsumerGroupFromEnv() {
    Map<String, String> env = Map.of(PmsSdkKafkaTopicResolver.PMS_SDK_KAFKA_CONSUMER_GROUP_ID_ENV, "pms_testtanmaydev");

    assertThat(PmsSdkKafkaTopicResolver.getKafkaConsumerGroupId(env, "pms")).isEqualTo("pms_testtanmaydev_pms");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResolveGraphUpdateKafkaConsumerGroupWithDefaultSuffix() {
    assertThat(PmsSdkKafkaTopicResolver.getGraphUpdateKafkaConsumerGroupId(Collections.emptyMap(), "pms"))
        .isEqualTo("pms_graph");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResolveGraphUpdateKafkaConsumerGroupFromEnv() {
    Map<String, String> env = Map.of(PmsSdkKafkaTopicResolver.PMS_SDK_KAFKA_CONSUMER_GROUP_ID_ENV, "pms_testtanmaydev");

    assertThat(PmsSdkKafkaTopicResolver.getGraphUpdateKafkaConsumerGroupId(env, "pms"))
        .isEqualTo("pms_testtanmaydev_pms_graph");
  }
}
