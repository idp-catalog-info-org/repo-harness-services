/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HeaderConfig;
import io.harness.exception.CriticalExpressionEvaluationException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.EngineJexlContext;
import io.harness.expression.common.ExpressionMode;
import io.harness.ngtriggers.beans.dto.BasicPipelineInfo;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData;
import io.harness.ngtriggers.beans.source.webhook.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.NGTriggerSpecV2;
import io.harness.ngtriggers.expressions.functors.PipelineBasicInfoFunctor;
import io.harness.ngtriggers.expressions.functors.TriggerNotificationFunctor;
import io.harness.ngtriggers.expressions.functors.TriggerPayloadFunctor;
import io.harness.ngtriggers.expressions.functors.payload.PayloadFunctor;
import io.harness.ngtriggers.helpers.ArtifactConfigHelper;
import io.harness.pms.contracts.triggers.ArtifactData;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class TriggerExpressionEvaluator extends EngineExpressionEvaluator {
  private final String payload;
  private final TriggerPayload triggerPayload;
  private BasicPipelineInfo basicPipelineInfo;
  private Map<String, Object> contextMap;

  private List<String> featureFlagList = new ArrayList<>(List.of(EngineExpressionEvaluator.PIE_EXECUTION_JSON_SUPPORT));
  private final String NOTIFICATION_FUNCTOR = "notification";

  public TriggerExpressionEvaluator(
      TriggerPayload triggerPayload, List<HeaderConfig> headerConfigs, String payload, NGTriggerSpecV2 spec) {
    super(null);
    TriggerPayload.Builder builder;
    if (triggerPayload != null) {
      /* If we already have a TriggerPayload object, we expect it to have ParsedPayload and ArtifactData already
         (see TriggerEventExecutionHelper:buildTriggerPayloadBuilder and
         TriggerEventExecutionHelper:getTriggerPayloadForWebhookTrigger).
         So here we can just use the already built TriggerPayload object. */
      builder = triggerPayload.toBuilder();
    } else {
      builder = TriggerPayload.newBuilder();
    }
    setCommonDataInTriggerPayload(builder, headerConfigs, spec);
    this.triggerPayload = builder.build();
    this.payload = payload;
  }

  public TriggerExpressionEvaluator(ParseWebhookResponse parseWebhookResponse, ArtifactData artifactData,
      List<HeaderConfig> headerConfigs, String payload, NGTriggerSpecV2 spec) {
    super(null);
    TriggerPayload.Builder builder = TriggerPayload.newBuilder();
    if (parseWebhookResponse != null) {
      if (parseWebhookResponse.hasPr()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPr(parseWebhookResponse.getPr()).build()).build();
      } else if (parseWebhookResponse.hasRelease()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setRelease(parseWebhookResponse.getRelease()).build())
            .build();
      } else if (parseWebhookResponse.hasBranch()
          && Action.DELETE.equals(parseWebhookResponse.getBranch().getAction())) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setBranch(parseWebhookResponse.getBranch()).build())
            .build();
      } else if (parseWebhookResponse.hasTag() && Action.DELETE.equals(parseWebhookResponse.getTag().getAction())) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setTag(parseWebhookResponse.getTag()).build()).build();
      } else if (parseWebhookResponse.hasBranch()
          && Action.CREATE.equals(parseWebhookResponse.getBranch().getAction())) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setBranch(parseWebhookResponse.getBranch()).build())
            .build();
      } else if (parseWebhookResponse.hasTag() && Action.CREATE.equals(parseWebhookResponse.getTag().getAction())) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setTag(parseWebhookResponse.getTag()).build()).build();
      } else if (parseWebhookResponse.hasMergeQueue()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setMergeQueue(parseWebhookResponse.getMergeQueue()).build())
            .build();
      } else {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPush(parseWebhookResponse.getPush()).build()).build();
      }
    }
    if (artifactData != null) {
      builder.setArtifactData(artifactData);
    }
    setCommonDataInTriggerPayload(builder, headerConfigs, spec);
    this.triggerPayload = builder.build();
    this.payload = payload;
  }

  public TriggerExpressionEvaluator(TriggerNotificationData triggerNotificationData, Map<String, Object> contextMap) {
    this(triggerNotificationData.getTriggerPayload(), triggerNotificationData.getHeaderConfigs(),
        triggerNotificationData.getPayload(), null);
    this.basicPipelineInfo = BasicPipelineInfo.builder()
                                 .pipelineIdentifier(triggerNotificationData.getPipelineIdentifier())
                                 .pipelineName(triggerNotificationData.getPipelineName())
                                 .build();
    // For the new flow for sending trigger failed notification we want to resolve expressions to null, so we are
    // passing this FF
    this.featureFlagList.add(EngineExpressionEvaluator.PIPE_RESOLVE_TO_NULL_VALUE_BASED_ON_EXPRESSION_MODE);
    this.contextMap = contextMap;
  }

  private static void setCommonDataInTriggerPayload(
      TriggerPayload.Builder triggerPayloadBuilder, List<HeaderConfig> headerConfigs, NGTriggerSpecV2 spec) {
    if (headerConfigs != null) {
      for (HeaderConfig config : headerConfigs) {
        if (config != null) {
          triggerPayloadBuilder.putHeaders(config.getKey().toLowerCase(), config.getValues().get(0));
        }
      }
    }
    if (spec != null && ArtifactTriggerConfig.class.isAssignableFrom(spec.getClass())) {
      ArtifactConfigHelper.setConnectorAndImage(triggerPayloadBuilder, (ArtifactTriggerConfig) spec);
    }
  }

  @Override
  protected void initialize() {
    addToContext(SetupAbstractionKeys.trigger, new TriggerPayloadFunctor(payload, triggerPayload));
    addToContext(SetupAbstractionKeys.eventPayload, new PayloadFunctor(payload));
    addToContext(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY, String.join(",", featureFlagList));

    // Only adding pipeline to context if pipelineBasicDetails are present
    if (basicPipelineInfo != null) {
      addToContext(SetupAbstractionKeys.pipeline, new PipelineBasicInfoFunctor(basicPipelineInfo));
    }
    // Only adding notification to context if contextMap is present
    if (contextMap != null) {
      addToContext(NOTIFICATION_FUNCTOR, new TriggerNotificationFunctor(contextMap));
    }
  }

  @Override
  protected Object evaluateInternal(@NotNull String expression, @NotNull EngineJexlContext ctx) {
    return evaluateExpressionInJexl(expression, ctx, true);
  }

  @Override
  public Object evaluateExpression(String expression) {
    return evaluateExpression(expression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
  }

  @Override
  public Object evaluateExpression(String expression, ExpressionMode expressionMode) {
    try {
      Object result = evaluateExpression(expression, (Map<String, Object>) null);
      return result == null ? "null" : result;
    } catch (Exception e) {
      log.warn("Failed to evaluated Trigger expression", e);
      return "null";
    }
  }

  public Object evaluateExpressionWithExpressionMode(String expression, ExpressionMode expressionMode) {
    try {
      Object result = evaluateExpression(expression, (Map<String, Object>) null);
      if (result == null && ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED.equals(expressionMode)) {
        throw new CriticalExpressionEvaluationException(
            String.format("Failed to evaluate trigger expression %s", expression), expression);
      }
      return result == null ? "null" : result;
    } catch (Exception e) {
      log.warn("Failed to evaluated Trigger expression", e);
      if (ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED.equals(expressionMode)) {
        throw new CriticalExpressionEvaluationException(
            String.format("Failed to evaluate trigger expression %s", expression), expression, e);
      }
      return "null";
    }
  }
}
