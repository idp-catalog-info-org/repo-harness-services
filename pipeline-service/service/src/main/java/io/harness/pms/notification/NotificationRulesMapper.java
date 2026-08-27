/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.notification.bean.PipelineEvent;
import io.harness.notification.bean.v1.NotificationRules;
import io.harness.notification.bean.v1.NotificationTemplate;
import io.harness.notification.v1.PipelineEventType;
import io.harness.notification.v1.StageEventConfig;
import io.harness.notification.v1.StageEventType;
import io.harness.notification.v1.StepEventConfig;
import io.harness.notification.v1.StepEventType;
import io.harness.notification.v1.TriggerEventType;
import io.harness.notification.v1.channelDetails.NotificationChannelType;
import io.harness.notification.v1.channelDetails.PmsNotificationChannel;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.template.yaml.TemplateLinkConfig;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class NotificationRulesMapper {
  private static final String VERSION_KEY = "version";
  private static final String VERSION_LABEL_KEY = "versionLabel";
  public List<io.harness.notification.bean.NotificationRules> toNotificationRulesV0(
      List<NotificationRules> v1NotificationRules) {
    List<io.harness.notification.bean.NotificationRules> v0NotificationRules = new ArrayList<>();
    if (isEmpty(v1NotificationRules)) {
      return v0NotificationRules;
    }
    for (NotificationRules v1NotificationRule : v1NotificationRules) {
      io.harness.notification.bean.NotificationRules notificationRules1 =
          io.harness.notification.bean.NotificationRules.builder()
              .name(v1NotificationRule.getName())
              .enabled(v1NotificationRule.getDisabled() == null || !v1NotificationRule.getDisabled())
              .pipelineEvents(toPipelineEvents(v1NotificationRule))
              .notificationChannelWrapper(ParameterField.createValueField(
                  toNotificationChannelWrapperV0(v1NotificationRule.getType(), v1NotificationRule.getSpec())))
              .build();
      v0NotificationRules.add(notificationRules1);
    }
    return v0NotificationRules;
  }

  List<PipelineEvent> toPipelineEvents(io.harness.notification.bean.v1.NotificationRules notificationRules) {
    List<PipelineEvent> pipelineEvents = new ArrayList<>();
    if (notificationRules.getNotificationEvents() == null) {
      return pipelineEvents;
    }
    if (isNotEmpty(notificationRules.getNotificationEvents().getPipelineEvents())) {
      pipelineEvents.addAll(notificationRules.getNotificationEvents()
                                .getPipelineEvents()
                                .stream()
                                .map(this::toPipelineEvent)
                                .collect(Collectors.toList()));
    }
    if (notificationRules.getNotificationEvents().getStepEvent() != null
        && isNotEmpty(notificationRules.getNotificationEvents().getStepEvent().getConfigs())) {
      pipelineEvents.addAll(notificationRules.getNotificationEvents()
                                .getStepEvent()
                                .getConfigs()
                                .stream()
                                .map(this::toStepEvent)
                                .collect(Collectors.toList()));
    }
    if (isNotEmpty(notificationRules.getNotificationEvents().getTriggerEvents())) {
      pipelineEvents.addAll(notificationRules.getNotificationEvents()
                                .getTriggerEvents()
                                .stream()
                                .map(this::toTriggerEvent)
                                .collect(Collectors.toList()));
    }
    if (notificationRules.getNotificationEvents().getStageEvent() != null) {
      for (StageEventConfig stageEventConfig : notificationRules.getNotificationEvents().getStageEvent().getConfigs()) {
        PipelineEvent pipelineEvent = toStageEvent(stageEventConfig);
        if (pipelineEvent.getType().equals(io.harness.notification.PipelineEventType.ALL_EVENTS)) {
          pipelineEvents.add(PipelineEvent.builder()
                                 .forStages(stageEventConfig.getRefs())
                                 .type(io.harness.notification.PipelineEventType.STAGE_SUCCESS)
                                 .build());
          pipelineEvents.add(PipelineEvent.builder()
                                 .forStages(stageEventConfig.getRefs())
                                 .type(io.harness.notification.PipelineEventType.STAGE_START)
                                 .build());
          pipelineEvents.add(PipelineEvent.builder()
                                 .forStages(stageEventConfig.getRefs())
                                 .type(io.harness.notification.PipelineEventType.STAGE_FAILED)
                                 .build());
        } else {
          pipelineEvents.add(pipelineEvent);
        }
      }
    }

    return pipelineEvents;
  }

  PipelineEvent toPipelineEvent(PipelineEventType pipelineEventType) {
    switch (pipelineEventType) {
      case ALL:
        return PipelineEvent.builder().type(io.harness.notification.PipelineEventType.ALL_EVENTS).build();
      case END:
        return PipelineEvent.builder().type(io.harness.notification.PipelineEventType.PIPELINE_END).build();
      case START:
        return PipelineEvent.builder().type(io.harness.notification.PipelineEventType.PIPELINE_START).build();
      case FAILED:
        return PipelineEvent.builder().type(io.harness.notification.PipelineEventType.PIPELINE_FAILED).build();
      case SUCCESS:
        return PipelineEvent.builder().type(io.harness.notification.PipelineEventType.PIPELINE_SUCCESS).build();
      default:
        throw new InvalidRequestException(String.format("Invalid value for PipelineEventType: %s", pipelineEventType));
    }
  }

  PipelineEvent toStepEvent(StepEventConfig stepEventConfig) {
    StepEventType stepEventType = stepEventConfig.getType();
    switch (stepEventType) {
      case FAILED:
        return PipelineEvent.builder()
            .type(io.harness.notification.PipelineEventType.STEP_FAILED)
            .forSteps(toStepEventRefs(stepEventConfig.getRefs()))
            .build();
      default:
        throw new InvalidRequestException(String.format("Invalid value for StepEventType: %s", stepEventType));
    }
  }

  List<String> toStepEventRefs(List<String> refs) {
    if (refs == null || refs.contains(YAMLFieldNameConstants.ALL)) {
      return List.of(YAMLFieldNameConstants.ALL_STEPS);
    }
    return refs;
  }

  PipelineEvent toTriggerEvent(TriggerEventType triggerEventType) {
    switch (triggerEventType) {
      case FAILED:
        return PipelineEvent.builder().type(io.harness.notification.PipelineEventType.TRIGGER_FAILED).build();
      default:
        throw new InvalidRequestException(String.format("Invalid value for TriggerEventType: %s", triggerEventType));
    }
  }
  PipelineEvent toStageEvent(StageEventConfig stageEventConfig) {
    return PipelineEvent.builder()
        .forStages(toStageEventRefs(stageEventConfig.getRefs()))
        .type(toStageEventType(stageEventConfig.getType()))
        .build();
  }

  List<String> toStageEventRefs(List<String> refs) {
    if (refs == null || refs.contains(YAMLFieldNameConstants.ALL)) {
      return List.of(YAMLFieldNameConstants.ALL_STAGES);
    }
    return refs;
  }

  io.harness.notification.PipelineEventType toStageEventType(StageEventType stageEventType) {
    switch (stageEventType) {
      case FAILED:
        return io.harness.notification.PipelineEventType.STAGE_FAILED;
      case ALL:
        return io.harness.notification.PipelineEventType.ALL_EVENTS;
      case START:
        return io.harness.notification.PipelineEventType.STAGE_START;
      case SUCCESS:
        return io.harness.notification.PipelineEventType.STAGE_SUCCESS;
      default:
        throw new InvalidRequestException(String.format("Invalid value for StageEventType: %s", stageEventType));
    }
  }

  io.harness.notification.bean.NotificationChannelWrapper toNotificationChannelWrapperV0(
      NotificationChannelType type, PmsNotificationChannel pmsNotificationChannel) {
    return io.harness.notification.bean.NotificationChannelWrapper.builder()
        .type(type.getValue())
        .notificationChannel(pmsNotificationChannel.toPmsNotificationChannelV0())
        .build();
  }

  /**
   * Converts v1 template format to v0 template format.
   *
   * V1 format (uses/with pattern):
   * <pre>
   * template:
   *   uses: account.templateTest
   *   with:
   *     version: v1
   * </pre>
   *
   * V0 format (templateRef/versionLabel pattern):
   * <pre>
   * template:
   *   templateRef: account.templateTest
   *   versionLabel: v1
   * </pre>
   *
   * @param v1Template the v1 template to convert
   * @return TemplateLinkConfig in v0 format, or null if input is null
   */
  public TemplateLinkConfig convertTemplateV1ToV0(NotificationTemplate v1Template) {
    if (v1Template == null) {
      return null;
    }

    TemplateLinkConfig v0Template = new TemplateLinkConfig();

    // Map 'uses' field to 'templateRef'
    v0Template.setTemplateRef(v1Template.getUses());

    // Extract 'version' or 'versionLabel' from 'with' map and set as 'versionLabel'
    if (v1Template.getWith() != null && !v1Template.getWith().isEmpty()) {
      Map<String, Object> withParams = v1Template.getWith();

      // Try 'version' first, then 'versionLabel' as fallback
      String versionLabel = extractVersionLabel(withParams);
      v0Template.setVersionLabel(versionLabel);

      // TODO: Handle templateInputs and custom parameters mapping (to be added later)
    }
    return v0Template;
  }

  /**
   * Extracts version label from 'with' parameters.
   * Looks for 'version' or 'versionLabel' keys.
   */
  private String extractVersionLabel(Map<String, Object> withParams) {
    // Check for 'version' key first
    if (withParams.containsKey(VERSION_KEY)) {
      Object versionValue = withParams.get(VERSION_KEY);
      return versionValue != null ? versionValue.toString() : null;
    }

    // Fall back to 'versionLabel' key
    if (withParams.containsKey(VERSION_LABEL_KEY)) {
      Object versionValue = withParams.get(VERSION_LABEL_KEY);
      return versionValue != null ? versionValue.toString() : null;
    }

    return null;
  }

  /**
   * Converts a JsonNode representing v1 template format to v0 TemplateLinkConfig.
   * This is useful when working with raw JsonNode from YAML parsing.
   *
   * @param templateNode JsonNode containing v1 template structure
   * @return TemplateLinkConfig in v0 format, or null if conversion fails
   */
  public TemplateLinkConfig convertTemplateNodeV1ToV0(JsonNode templateNode) {
    if (templateNode == null || templateNode.isNull()) {
      return null;
    }

    try {
      // Parse JsonNode to NotificationTemplate v1 object
      NotificationTemplate v1Template = JsonPipelineUtils.read(templateNode.toString(), NotificationTemplate.class);
      return convertTemplateV1ToV0(v1Template);
    } catch (Exception e) {
      log.error("Failed to convert template JsonNode from v1 to v0 format", e);
      return null;
    }
  }
}
