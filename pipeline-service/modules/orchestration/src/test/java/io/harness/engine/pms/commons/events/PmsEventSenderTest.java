/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.commons.events;

import static io.harness.beans.FeatureName.PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_FACILITATOR_EVENT_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_ADVISE_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_RESUME_EVENT_TOPIC_WITH_SERVICE_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_RESUME_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_START_EVENT_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_START_EVENT_TOPIC_WITH_SERVICE_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_ORCHESTRATION_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_PROGRESS_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.START_PARTIAL_PLAN_CREATOR_MAX_TOPIC_SIZE;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.OrchestrationTestBase;
import io.harness.RedisEventConfig;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.config.OrchestrationModuleConfig;
import io.harness.config.OrchestrationRedisEventsConfig;
import io.harness.engine.expressions.provider.impl.AmbianceExpressionEvaluatorProvider;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkRedisTopicResolver;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.plan.ConsumerConfig;
import io.harness.pms.contracts.plan.KafkaConsumerSettings;
import io.harness.pms.contracts.plan.Redis;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.redis.RedisConfig;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsEventSenderTest extends OrchestrationTestBase {
  @Mock private OrchestrationModuleConfig moduleConfig;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private Optional<HKafkaProtoProducer> hKafkaProtoProducer;
  @Spy @InjectMocks PmsEventSender eventSenderMock;
  @Inject PmsEventSender eventSender;
  @Inject MongoTemplate mongoTemplate;

  private static final String TOPIC1 = "topic1";
  private static final String TOPIC2 = "topic2";
  public static final String ACCOUNT_ID = generateUuid();
  public static String ORG_ID = "orgId";
  public static String PROJECT_ID = "projectId";
  public static final String APP_ID = generateUuid();
  public static final String RUNTIME_ID = generateUuid();
  public static final String EXECUTION_ID = generateUuid();

  @Before
  public void setup() {
    mongoTemplate.save(
        PmsSdkInstance.builder()
            .name(ModuleType.PMS.name())
            .supportedTypes(new HashMap<>())
            .supportedSdkSteps(new ArrayList<>())
            .interruptConsumerConfig(
                ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(TOPIC1).build()).build())
            .orchestrationEventConsumerConfig(
                ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(TOPIC2).build()).build())
            .build());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldTestSendEventKafka() {
    var ambiance = Ambiance.newBuilder().putAllSetupAbstractions(ImmutableMap.of("accountId", ACCOUNT_ID)).build();
    var event = InterruptEvent.newBuilder().build();
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING)).thenReturn(true);

    HKafkaProtoProducer mockProducer = mock(HKafkaProtoProducer.class);
    when(hKafkaProtoProducer.isPresent()).thenReturn(true);
    when(hKafkaProtoProducer.get()).thenReturn(mockProducer);

    doReturn("topic").when(eventSenderMock).obtainKafkaProducerTopicFromCache(any(), any());
    doReturn(ConsumerConfig.ConsumerPreference.KAFKA).when(eventSenderMock).obtainConsumerPreference(any(), any());

    eventSenderMock.sendEvent(ambiance, event, PmsEventCategory.INTERRUPT_EVENT, ModuleType.PMS.name(), true, false);

    verify(eventSenderMock).obtainKafkaProducerTopicFromCache(PmsEventCategory.INTERRUPT_EVENT, ModuleType.PMS.name());
    verify(eventSenderMock).obtainConsumerPreference(PmsEventCategory.INTERRUPT_EVENT, ModuleType.PMS.name());
    verify(mockProducer).send(anyString(), eq(event), anyMap());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldTestObtainConsumerPreference() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .interruptConsumerConfig(
                ConsumerConfig.newBuilder()
                    .setConsumerPreference(ConsumerConfig.ConsumerPreference.KAFKA)
                    .setRedis(Redis.newBuilder()
                                  .setTopicName(EventsFrameworkRedisTopicResolver.getPipelineInterruptTopic())
                                  .build())
                    .build())
            .build();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(ModuleType.PMS.name());
    doReturn(EventsFrameworkConfiguration.builder().shouldUseKafka(true).build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(false).when(moduleConfig).isStreamPerServiceConfiguration();
    ConsumerConfig.ConsumerPreference consumerPreference =
        eventSenderMock.resolveConsumerPreference(PmsEventCategory.INTERRUPT_EVENT, ModuleType.PMS.name());
    assertThat(consumerPreference).isEqualTo(ConsumerConfig.ConsumerPreference.KAFKA);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldPreferRedisWhenSdkRegisteredWithRedisPreferenceDespiteGlobalKafka() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeStartEventConsumerConfig(
                ConsumerConfig.newBuilder()
                    .setConsumerPreference(ConsumerConfig.ConsumerPreference.REDIS)
                    .setRedis(Redis.newBuilder().setTopicName("pipeline_node_start_rmg").build())
                    .build())
            .build();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance("rmg");
    doReturn(EventsFrameworkConfiguration.builder().shouldUseKafka(true).build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();

    assertThat(eventSenderMock.resolveConsumerPreference(PmsEventCategory.NODE_START, "rmg"))
        .isEqualTo(ConsumerConfig.ConsumerPreference.REDIS);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldPreferRedisForCustomMongoTopicDespiteGlobalKafka() {
    String customTopic = "rmgstart";
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeStartEventConsumerConfig(ConsumerConfig.newBuilder()
                                              .setConsumerPreference(ConsumerConfig.ConsumerPreference.KAFKA)
                                              .setRedis(Redis.newBuilder().setTopicName(customTopic).build())
                                              .build())
            .build();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance("rmg");
    doReturn(EventsFrameworkConfiguration.builder().shouldUseKafka(true).build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(false).when(moduleConfig).isStreamPerServiceConfiguration();

    assertThat(eventSenderMock.resolveConsumerPreference(PmsEventCategory.NODE_START, "rmg"))
        .isEqualTo(ConsumerConfig.ConsumerPreference.REDIS);
    assertThat(eventSenderMock.isCustomRedisStream(PmsEventCategory.NODE_START, "rmg",
                   ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(customTopic).build()).build()))
        .isTrue();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestObtainProducer() {
    Producer producer = eventSender.obtainProducer(PmsEventCategory.INTERRUPT_EVENT, ModuleType.PMS.name());
    assertThat(((NoOpProducer) producer).getTopicName()).isEqualTo(TOPIC1);

    producer = eventSender.obtainProducer(PmsEventCategory.ORCHESTRATION_EVENT, ModuleType.PMS.name());
    assertThat(((NoOpProducer) producer).getTopicName()).isEqualTo(TOPIC2);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldPreferMongoRegisteredRedisTopicOverResolver() {
    String customTopic = "rmgstart";
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeStartEventConsumerConfig(
                ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(customTopic).build()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("rmg").eventCategory(PmsEventCategory.NODE_START).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineNodeStartEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_START_EVENT_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    Producer producer = eventSenderMock.obtainProducer(producerCacheKey);

    assertThat(((NoOpProducer) producer).getTopicName()).isEqualTo(customTopic);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldPreferResolverWhenMongoHasDevspacePollutedPipelineTopic() {
    String pollutedTopic = "pipeline_orchestration_s5ILtAPvR0eUcY7sGNVpiA";
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .orchestrationEventConsumerConfig(
                ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(pollutedTopic).build()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.ORCHESTRATION_EVENT).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineOrchestrationEvent(RedisEventConfig.builder().maxTopicSize(100).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").envNamespace("testtanmaydev").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    Producer producer = eventSenderMock.obtainProducer(producerCacheKey);

    assertThat(((NoOpProducer) producer).getTopicName()).isEqualTo(PIPELINE_ORCHESTRATION_EVENT_TOPIC);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldPreferResolverWhenMongoHasDevspacePollutedNodeStartTopic() {
    String pollutedTopic = "pipeline_node_start_cd_s5ILtAPvR0eUcY7sGNVpiA";
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeStartEventConsumerConfig(
                ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(pollutedTopic).build()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.NODE_START).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineNodeStartEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_START_EVENT_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(true).when(moduleConfig).isStreamPerServiceConfiguration();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").envNamespace("testtanmaydev").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    Producer producer = eventSenderMock.obtainProducer(producerCacheKey);

    assertThat(((NoOpProducer) producer).getTopicName())
        .isEqualTo(String.format(PIPELINE_NODE_START_EVENT_TOPIC_WITH_SERVICE_NAME, "cd"));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldPreferLocalKafkaTopicResolutionOverStoredConfig() {
    doReturn(PmsSdkInstance.builder()
                 .name("cd")
                 .nodeStartEventConsumerConfig(
                     ConsumerConfig.newBuilder()
                         .setKafkaSettings(KafkaConsumerSettings.newBuilder().setTopicName(TOPIC1).build())
                         .build())
                 .build())
        .when(eventSenderMock)
        .getPmsSdkInstance("cd");

    String topic = eventSenderMock.obtainKafkaProducerTopic(
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.NODE_START).build());

    assertThat(topic).isEqualTo("pipeline_node_start_cd");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldUseServiceScopedRedisTopicWhenStreamPerServiceConfigured() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeResumeEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.NODE_RESUME).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineNodeResumeEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_RESUME_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(true).when(moduleConfig).isStreamPerServiceConfiguration();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    Producer producer = eventSenderMock.obtainProducer(producerCacheKey);

    assertThat(((NoOpProducer) producer).getTopicName())
        .isEqualTo(String.format(PIPELINE_NODE_RESUME_EVENT_TOPIC_WITH_SERVICE_NAME, "cd"));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestCache() {
    PmsEventSender spyEventsFrameworkUtils = spy(PmsEventSender.class);
    Reflect.on(spyEventsFrameworkUtils)
        .set("moduleConfig",
            OrchestrationModuleConfig.builder()
                .serviceName(ModuleType.PMS.name())
                .expressionEvaluatorProvider(new AmbianceExpressionEvaluatorProvider())
                .build());
    doReturn(PmsSdkInstance.builder()
                 .name(ModuleType.PMS.name())
                 .supportedTypes(new HashMap<>())
                 .supportedSdkSteps(new ArrayList<>())
                 .interruptConsumerConfig(
                     ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(TOPIC1).build()).build())
                 .orchestrationEventConsumerConfig(
                     ConsumerConfig.newBuilder().setRedis(Redis.newBuilder().setTopicName(TOPIC2).build()).build())
                 .build())
        .when(spyEventsFrameworkUtils)
        .getPmsSdkInstance(ModuleType.PMS.name());
    spyEventsFrameworkUtils.obtainProducer(PmsEventCategory.ORCHESTRATION_EVENT, ModuleType.PMS.name());
    spyEventsFrameworkUtils.obtainProducer(PmsEventCategory.ORCHESTRATION_EVENT, ModuleType.PMS.name());

    verify(spyEventsFrameworkUtils, times(1)).obtainProducer(any(ProducerCacheKey.class));
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerForFacilitatorEvent() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .facilitatorEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.FACILITATOR_EVENT).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineFacilitatorEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_FACILITATOR_EVENT_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerForProgressEvent() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .progressEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.PROGRESS_EVENT).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineProgressEvent(RedisEventConfig.builder().maxTopicSize(PIPELINE_PROGRESS_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerForNodeStartEvent() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeStartEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.NODE_START).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineNodeStartEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_START_EVENT_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerNodeAdviseEvent() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeAdviseEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.NODE_ADVISE).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineNodeAdviseEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_ADVISE_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerForNodeResumeEvent() {
    PmsSdkInstance pmsSdkInstance =
        PmsSdkInstance.builder()
            .nodeResumeEventConsumerConfig(ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
            .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.NODE_RESUME).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineNodeResumeEvent(
                RedisEventConfig.builder().maxTopicSize(PIPELINE_NODE_RESUME_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testObtainProducerForPartialPlanEvent() {
    PmsSdkInstance pmsSdkInstance = PmsSdkInstance.builder()
                                        .startPlanCreationEventConsumerConfig(
                                            ConsumerConfig.newBuilder().setRedis(Redis.getDefaultInstance()).build())
                                        .build();
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.CREATE_PARTIAL_PLAN).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineStartPartialPlanCreator(
                RedisEventConfig.builder().maxTopicSize(START_PARTIAL_PLAN_CREATOR_MAX_TOPIC_SIZE).build())
            .build();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();
    doReturn(pmsSdkInstance).when(eventSenderMock).getPmsSdkInstance(any());

    assertThatCode(() -> eventSenderMock.obtainProducer(producerCacheKey)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldResolveRedisProducerTopicNameFromResolverWhenMongoTopicMissing() {
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.INTERRUPT_EVENT).build();
    doReturn(false).when(moduleConfig).isStreamPerServiceConfiguration();

    assertThat(eventSenderMock.resolveRedisProducerTopicName(null, producerCacheKey))
        .isEqualTo(EventsFrameworkRedisTopicResolver.getPipelineInterruptTopic());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldPreserveCustomMongoTopicInResolveRedisProducerTopicName() {
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("rmg").eventCategory(PmsEventCategory.NODE_START).build();
    String customTopic = "rmgstart";

    assertThat(eventSenderMock.resolveRedisProducerTopicName(
                   Redis.newBuilder().setTopicName(customTopic).build(), producerCacheKey))
        .isEqualTo(customTopic);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldOverrideFlatDefaultPollutionInResolveRedisProducerTopicName() {
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.INTERRUPT_EVENT).build();
    doReturn(false).when(moduleConfig).isStreamPerServiceConfiguration();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").envNamespace("testtanmaydev").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();

    assertThat(eventSenderMock.resolveRedisProducerTopicName(
                   Redis.newBuilder().setTopicName("pipeline_interrupt_cd").build(), producerCacheKey))
        .isEqualTo(EventsFrameworkRedisTopicResolver.getPipelineInterruptTopic());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldPreservePerServiceMongoTopicWhenStreamPerServiceConfigured() {
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.INTERRUPT_EVENT).build();
    String customTopic = "custom_interrupt_cd";
    doReturn(true).when(moduleConfig).isStreamPerServiceConfiguration();

    assertThat(eventSenderMock.resolveRedisProducerTopicName(
                   Redis.newBuilder().setTopicName(customTopic).build(), producerCacheKey))
        .isEqualTo(customTopic);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldObtainProducerWithoutSdkInstanceWhenSkipSdkMongoRegistration() {
    ProducerCacheKey producerCacheKey =
        ProducerCacheKey.builder().serviceName("cd").eventCategory(PmsEventCategory.INTERRUPT_EVENT).build();
    OrchestrationRedisEventsConfig orchestrationRedisEventsConfig =
        OrchestrationRedisEventsConfig.builder()
            .pipelineInterruptEvent(RedisEventConfig.builder().maxTopicSize(100).build())
            .build();
    Reflect.on(eventSenderMock).set("skipSdkMongoRegistration", true);
    doReturn(false).when(moduleConfig).isStreamPerServiceConfiguration();
    doReturn(EventsFrameworkConfiguration.builder()
                 .redisConfig(RedisConfig.builder().redisUrl("dummyRedisUrl").build())
                 .build())
        .when(moduleConfig)
        .getEventsFrameworkConfiguration();
    doReturn(orchestrationRedisEventsConfig).when(moduleConfig).getOrchestrationRedisEventsConfig();

    Producer producer = eventSenderMock.obtainProducer(producerCacheKey);

    assertThat(((NoOpProducer) producer).getTopicName())
        .isEqualTo(EventsFrameworkRedisTopicResolver.getPipelineInterruptTopic());
    verify(eventSenderMock, never()).getPmsSdkInstance(any());
  }

  private static Ambiance getAmbiance() {
    Level sectionLevel = Level.newBuilder()
                             .setRuntimeId(RUNTIME_ID)
                             .setSetupId(RUNTIME_ID)
                             .setStepType(StepType.newBuilder()
                                              .setType(StepSpecTypeConstants.INIT_CONTAINER_STEP_V2)
                                              .setStepCategory(StepCategory.STEP)
                                              .build())
                             .setGroup("SECTION")
                             .build();
    List<Level> levels = new ArrayList<>();
    levels.add(sectionLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId(EXECUTION_ID)
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", ACCOUNT_ID, "appId", APP_ID, "orgIdentifier", ORG_ID, "projectIdentifier", PROJECT_ID))
        .addAllLevels(levels)
        .build();
  }
}
