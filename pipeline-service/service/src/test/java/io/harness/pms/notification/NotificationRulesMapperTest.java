/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.notification.PipelineEventType;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.bean.PipelineEvent;
import io.harness.notification.channelDetails.PmsDatadogChannel;
import io.harness.notification.channelDetails.PmsNotificationChannel;
import io.harness.notification.channelDetails.PmsSlackChannel;
import io.harness.notification.channelDetails.PmsWebhookChannel;
import io.harness.pms.pipeline.yaml.UnifiedPipelineYaml;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(UnitTests.class)
public class NotificationRulesMapperTest extends CategoryTest {
  private final NotificationRulesMapper notificationRulesMapper = new NotificationRulesMapper();

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testToNotificationRulesV0() {
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - steps:\n"
        + "        - run:\n"
        + "            script: exit 1\n"
        + "          id: run_1\n"
        + "          name: run_1\n"
        + "      id: stage_1\n"
        + "      name: stage_1\n"
        + "    - steps:\n"
        + "        - run:\n"
        + "            script: exit 1\n"
        + "          id: run_2\n"
        + "          name: run_2\n"
        + "      id: stage_2\n"
        + "      name: stage_2\n"
        + "  notifications:\n"
        + "    - id: test\n"
        + "      \"on\":\n"
        + "        - pipeline: all\n"
        + "        - stage: all\n"
        + "        - step: failed\n"
        + "      uses: webhook\n"
        + "      with:\n"
        + "        url: webhook-url\n"
        + "        headers:\n"
        + "          key: value\n"
        + "      disabled: false\n"
        + "      name: test\n"
        + "    - \"on\":\n"
        + "        - pipeline:\n"
        + "            - failed\n"
        + "        - stage:\n"
        + "            - start: all\n"
        + "            - failed:\n"
        + "                - stage_1\n"
        + "                - stage_2\n"
        + "            - success\n"
        + "      uses: slack\n"
        + "      with:\n"
        + "        webhook: slack-url\n"
        + "      disabled: true\n"
        + "      id: notification_1\n"
        + "      name: notification_1";
    List<io.harness.notification.bean.v1.NotificationRules> v1Rules = createV1NotificationRules(pipelineYaml);

    // When
    List<NotificationRules> v0Rules = notificationRulesMapper.toNotificationRulesV0(v1Rules);

    // Then
    assertThat(v0Rules).hasSize(2);

    // Verify first notification rule (all events)
    NotificationRules firstRule = v0Rules.get(0);
    assertThat(firstRule.getName()).isEqualTo("test");
    assertThat(firstRule.isEnabled()).isTrue();
    assertThat(firstRule.getPipelineEvents()).hasSize(5);

    // Verify webhook channel for first rule
    assertThat(firstRule.getNotificationChannelWrapper()).isNotNull();
    PmsNotificationChannel firstChannel =
        firstRule.getNotificationChannelWrapper().obtainValue().getNotificationChannel();
    assertThat(firstChannel).isInstanceOf(PmsWebhookChannel.class);
    PmsWebhookChannel firstWebhook = (PmsWebhookChannel) firstChannel;
    assertThat(firstWebhook.getWebhookUrl().getValue()).isEqualTo("webhook-url");

    // Verify pipeline all events
    PipelineEvent pipelineAllEvent = firstRule.getPipelineEvents()
                                         .stream()
                                         .filter(e -> e.getType() == PipelineEventType.ALL_EVENTS)
                                         .findFirst()
                                         .orElse(null);
    assertThat(pipelineAllEvent).isNotNull();

    // Verify stage all events
    List<PipelineEvent> stageAllEvent =
        firstRule.getPipelineEvents()
            .stream()
            .filter(e
                -> e.getType() == PipelineEventType.STAGE_FAILED || e.getType() == PipelineEventType.STAGE_START
                    || e.getType() == PipelineEventType.STAGE_SUCCESS)
            .collect(Collectors.toList());
    assertThat(stageAllEvent).hasSize(3);

    // Verify step failed event
    PipelineEvent stepFailedEvent = firstRule.getPipelineEvents()
                                        .stream()
                                        .filter(e -> e.getType() == PipelineEventType.STEP_FAILED)
                                        .findFirst()
                                        .orElse(null);
    assertThat(stepFailedEvent).isNotNull();
    assertThat(stepFailedEvent.getForSteps()).containsExactly(YAMLFieldNameConstants.ALL_STEPS);

    // Verify second notification rule (complex stage events)
    NotificationRules secondRule = v0Rules.get(1);
    assertThat(secondRule.getName()).isEqualTo("notification_1");
    assertThat(secondRule.isEnabled()).isFalse(); // Verify disabled status
    assertThat(secondRule.getPipelineEvents()).hasSize(4);

    // Verify slack channel for second rule
    assertThat(secondRule.getNotificationChannelWrapper()).isNotNull();
    PmsNotificationChannel secondChannel =
        secondRule.getNotificationChannelWrapper().obtainValue().getNotificationChannel();
    assertThat(secondChannel).isInstanceOf(PmsSlackChannel.class);
    PmsSlackChannel slackChannel = (PmsSlackChannel) secondChannel;
    assertThat(slackChannel.getWebhookUrl().getValue()).isEqualTo("slack-url");

    // Verify pipeline failed event
    PipelineEvent failedEvent = secondRule.getPipelineEvents()
                                    .stream()
                                    .filter(e -> e.getType() == PipelineEventType.PIPELINE_FAILED)
                                    .findFirst()
                                    .orElse(null);
    assertThat(failedEvent).isNotNull();

    // Verify stage events
    List<PipelineEvent> stageEvents =
        secondRule.getPipelineEvents()
            .stream()
            .filter(e
                -> e.getType() == PipelineEventType.STAGE_FAILED || e.getType() == PipelineEventType.STAGE_START
                    || e.getType() == PipelineEventType.STAGE_SUCCESS)
            .collect(java.util.stream.Collectors.toList());
    assertThat(stageEvents).hasSize(3);

    // Verify stage start event
    PipelineEvent stageStartEvent =
        stageEvents.stream().filter(e -> e.getType() == PipelineEventType.STAGE_START).findFirst().orElse(null);
    assertThat(stageStartEvent).isNotNull();
    assertThat(stageStartEvent.getForStages()).containsExactly(YAMLFieldNameConstants.ALL_STAGES);

    // Verify stage failed event
    PipelineEvent stageFailedEvent =
        stageEvents.stream().filter(e -> e.getType() == PipelineEventType.STAGE_FAILED).findFirst().orElse(null);
    assertThat(stageFailedEvent).isNotNull();
    assertThat(stageFailedEvent.getForStages()).containsExactlyInAnyOrder("stage_1", "stage_2");

    // Verify stage success event
    PipelineEvent stageSuccessEvent =
        stageEvents.stream().filter(e -> e.getType() == PipelineEventType.STAGE_SUCCESS).findFirst().orElse(null);
    assertThat(stageSuccessEvent).isNotNull();
    assertThat(stageSuccessEvent.getForStages()).containsExactly(YAMLFieldNameConstants.ALL_STAGES);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToNotificationRulesV0_WithTriggerFailedEvent() {
    // Given
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - steps:\n"
        + "        - run:\n"
        + "            script: exit 1\n"
        + "          id: run_1\n"
        + "          name: run_1\n"
        + "      id: stage_1\n"
        + "      name: stage_1\n"
        + "  notifications:\n"
        + "    - id: trigger_notification\n"
        + "      name: TriggerFailureNotification\n"
        + "      \"on\":\n"
        + "        - trigger: failed\n"
        + "        - pipeline: failed\n"
        + "      uses: webhook\n"
        + "      with:\n"
        + "        url: webhook-url\n"
        + "      disabled: false";
    List<io.harness.notification.bean.v1.NotificationRules> v1Rules = createV1NotificationRules(pipelineYaml);

    // When
    List<NotificationRules> v0Rules = notificationRulesMapper.toNotificationRulesV0(v1Rules);

    // Then
    assertThat(v0Rules).hasSize(1);

    NotificationRules rule = v0Rules.get(0);
    assertThat(rule.getName()).isEqualTo("TriggerFailureNotification");
    assertThat(rule.isEnabled()).isTrue();
    assertThat(rule.getPipelineEvents()).hasSize(2);

    // Verify trigger failed event
    PipelineEvent triggerFailedEvent = rule.getPipelineEvents()
                                           .stream()
                                           .filter(e -> e.getType() == PipelineEventType.TRIGGER_FAILED)
                                           .findFirst()
                                           .orElse(null);
    assertThat(triggerFailedEvent).isNotNull();
    assertThat(triggerFailedEvent.getType()).isEqualTo(PipelineEventType.TRIGGER_FAILED);

    // Verify pipeline failed event
    PipelineEvent pipelineFailedEvent = rule.getPipelineEvents()
                                            .stream()
                                            .filter(e -> e.getType() == PipelineEventType.PIPELINE_FAILED)
                                            .findFirst()
                                            .orElse(null);
    assertThat(pipelineFailedEvent).isNotNull();
    assertThat(pipelineFailedEvent.getType()).isEqualTo(PipelineEventType.PIPELINE_FAILED);

    // Verify webhook channel
    assertThat(rule.getNotificationChannelWrapper()).isNotNull();
    PmsNotificationChannel channel = rule.getNotificationChannelWrapper().obtainValue().getNotificationChannel();
    assertThat(channel).isInstanceOf(PmsWebhookChannel.class);
    PmsWebhookChannel webhook = (PmsWebhookChannel) channel;
    assertThat(webhook.getWebhookUrl().getValue()).isEqualTo("webhook-url");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToNotificationRulesV0_WithDatadogChannel() {
    // Given
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - steps:\n"
        + "        - run:\n"
        + "            script: exit 1\n"
        + "          id: run_1\n"
        + "          name: run_1\n"
        + "      id: stage_1\n"
        + "      name: stage_1\n"
        + "  notifications:\n"
        + "    - id: datadog_notification\n"
        + "      name: DatadogNotification\n"
        + "      \"on\":\n"
        + "        - pipeline: failed\n"
        + "        - trigger: failed\n"
        + "      uses: datadog\n"
        + "      with:\n"
        + "        api-key: test-api-key\n"
        + "        url: https://api.datadoghq.com/api/v1/events\n"
        + "      disabled: false";
    List<io.harness.notification.bean.v1.NotificationRules> v1Rules = createV1NotificationRules(pipelineYaml);

    // When
    List<NotificationRules> v0Rules = notificationRulesMapper.toNotificationRulesV0(v1Rules);

    // Then
    assertThat(v0Rules).hasSize(1);

    NotificationRules rule = v0Rules.get(0);
    assertThat(rule.getName()).isEqualTo("DatadogNotification");
    assertThat(rule.isEnabled()).isTrue();
    assertThat(rule.getPipelineEvents()).hasSize(2);

    // Verify pipeline failed event
    PipelineEvent pipelineFailedEvent = rule.getPipelineEvents()
                                            .stream()
                                            .filter(e -> e.getType() == PipelineEventType.PIPELINE_FAILED)
                                            .findFirst()
                                            .orElse(null);
    assertThat(pipelineFailedEvent).isNotNull();

    // Verify trigger failed event
    PipelineEvent triggerFailedEvent = rule.getPipelineEvents()
                                           .stream()
                                           .filter(e -> e.getType() == PipelineEventType.TRIGGER_FAILED)
                                           .findFirst()
                                           .orElse(null);
    assertThat(triggerFailedEvent).isNotNull();

    // Verify datadog channel conversion
    assertThat(rule.getNotificationChannelWrapper()).isNotNull();
    PmsNotificationChannel channel = rule.getNotificationChannelWrapper().obtainValue().getNotificationChannel();
    assertThat(channel).isInstanceOf(PmsDatadogChannel.class);
    PmsDatadogChannel datadogChannel = (PmsDatadogChannel) channel;
    assertThat(datadogChannel.getApiKey().getValue()).isEqualTo("test-api-key");
    assertThat(datadogChannel.getUrl().getValue()).isEqualTo("https://api.datadoghq.com/api/v1/events");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToNotificationRulesV0_WithV1StepEventRefsMapsToForSteps() {
    String pipelineYaml = "pipeline:\n"
        + "  clone:\n"
        + "    disabled: true\n"
        + "  stages:\n"
        + "    - id: deploy\n"
        + "      name: deploy\n"
        + "      steps:\n"
        + "        - id: matrix_step\n"
        + "          name: matrix_step\n"
        + "          run:\n"
        + "            script: exit 1\n"
        + "        - group:\n"
        + "            id: sg1\n"
        + "            name: sg1\n"
        + "            steps:\n"
        + "              - id: inner_1\n"
        + "                name: inner_1\n"
        + "                run:\n"
        + "                  script: exit 1\n"
        + "              - id: inner_2\n"
        + "                name: inner_2\n"
        + "                run:\n"
        + "                  script: exit 1\n"
        + "    - group:\n"
        + "        id: stage_group_1\n"
        + "        stages:\n"
        + "          - id: child_stage\n"
        + "            name: child_stage\n"
        + "            steps:\n"
        + "              - id: child_step\n"
        + "                name: child_step\n"
        + "                run:\n"
        + "                  script: exit 1\n"
        + "  notifications:\n"
        + "    - id: step_ref_rule\n"
        + "      name: StepRefRule\n"
        + "      \"on\":\n"
        + "        - step:\n"
        + "            - failed:\n"
        + "                - deploy.matrix_step\n"
        + "                - deploy.sg1.inner_1\n"
        + "                - deploy.sg1.inner_2\n"
        + "                - stage_group_1.child_stage.child_step\n"
        + "      uses: webhook\n"
        + "      with:\n"
        + "        url: webhook-url\n"
        + "      disabled: false";

    List<io.harness.notification.bean.v1.NotificationRules> v1Rules = createV1NotificationRules(pipelineYaml);
    List<NotificationRules> v0Rules = notificationRulesMapper.toNotificationRulesV0(v1Rules);
    assertThat(v0Rules).hasSize(1);

    PipelineEvent stepFailedEvent = v0Rules.get(0)
                                        .getPipelineEvents()
                                        .stream()
                                        .filter(e -> e.getType() == PipelineEventType.STEP_FAILED)
                                        .findFirst()
                                        .orElse(null);
    assertThat(stepFailedEvent).isNotNull();
    assertThat(stepFailedEvent.getForSteps())
        .containsExactlyInAnyOrder(
            "deploy.matrix_step", "deploy.sg1.inner_1", "deploy.sg1.inner_2", "stage_group_1.child_stage.child_step");
  }

  private List<io.harness.notification.bean.v1.NotificationRules> createV1NotificationRules(String pipelineYaml) {
    try {
      UnifiedPipelineYaml unifiedPipeline = YamlUtils.read(pipelineYaml, UnifiedPipelineYaml.class);
      return unifiedPipeline.getNotificationRules();
    } catch (Exception ex) {
      return List.of();
    }
  }
}
