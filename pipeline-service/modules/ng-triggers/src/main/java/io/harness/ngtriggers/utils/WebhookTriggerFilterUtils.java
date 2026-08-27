/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.beans.WebhookEvent.Type.ARTIFACT;
import static io.harness.beans.WebhookEvent.Type.CREATE;
import static io.harness.beans.WebhookEvent.Type.DELETE;
import static io.harness.beans.WebhookEvent.Type.ISSUE_COMMENT;
import static io.harness.beans.WebhookEvent.Type.MERGE_QUEUE;
import static io.harness.beans.WebhookEvent.Type.PIPELINE_HOOK;
import static io.harness.beans.WebhookEvent.Type.PR;
import static io.harness.beans.WebhookEvent.Type.PULL_REQUEST_REVIEW;
import static io.harness.beans.WebhookEvent.Type.PUSH;
import static io.harness.beans.WebhookEvent.Type.TAG;
import static io.harness.constants.Constants.BITBUCKET_CLOUD_HEADER_KEY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ngtriggers.Constants.CHANGED_FILES;
import static io.harness.ngtriggers.Constants.CREATE_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.DELETE_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.ISSUE_COMMENT_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.MERGE_QUEUE_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.MERGE_REQUEST_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.MR_COMMENT_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.PIPELINE_HOOK_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.PR_COMMENT_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.PULL_REQUEST_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.PULL_REQUEST_REVIEW_TYPE;
import static io.harness.ngtriggers.Constants.PUSH_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.RELEASE_EVENT_TYPE;
import static io.harness.ngtriggers.Constants.TAG_EVENT_TYPE;
import static io.harness.ngtriggers.beans.source.webhook.WebhookAction.BT_PULL_REQUEST_UPDATED;
import static io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction.REVIEWREADY;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ArtifactWebhookEvent;
import io.harness.beans.CreateWebhookEvent;
import io.harness.beans.DeleteWebhookEvent;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.WebhookEvent;
import io.harness.exception.TriggerException;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.condition.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAction;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.action.HarnessPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.event.HarnessBranchSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.harness.event.HarnessTagSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.HarnessSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.WebhookTriggerSpecV2;
import io.harness.ngtriggers.conditionchecker.ConditionEvaluator;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class WebhookTriggerFilterUtils {
  public boolean evaluateEventAndActionFilters(
      WebhookPayloadData webhookPayloadData, WebhookTriggerSpecV2 webhookTriggerConfigSpec) {
    return checkIfEventTypeMatches(webhookPayloadData.getWebhookEvent(), webhookTriggerConfigSpec)
        && checkIfActionMatches(webhookPayloadData, webhookTriggerConfigSpec);
  }

  public boolean checkIfEventTypeMatches(WebhookEvent webhookEvent, WebhookTriggerSpecV2 webhookTriggerSpec) {
    WebhookEvent.Type eventTypeFromPayload = webhookEvent.getType();
    if (webhookTriggerSpec.fetchGitAware() == null) {
      throw new TriggerException(
          "Invalid Filter used. Event Filter is not compatible with class: " + webhookTriggerSpec.getClass(), USER_SRE);
    }

    String gitEvent = webhookTriggerSpec.fetchGitAware().fetchEvent().getValue();

    if (eventTypeFromPayload.equals(PR)) {
      return gitEvent.equals(PULL_REQUEST_EVENT_TYPE) || gitEvent.equals(MERGE_REQUEST_EVENT_TYPE);
    }

    if (eventTypeFromPayload.equals(PULL_REQUEST_REVIEW)) {
      return gitEvent.equals(PULL_REQUEST_REVIEW_TYPE);
    }

    if (eventTypeFromPayload.equals(PUSH)) {
      return gitEvent.equals(PUSH_EVENT_TYPE);
    }
    if (webhookTriggerSpec.getClass().isAssignableFrom(HarnessSpec.class)) {
      if (eventTypeFromPayload.equals(DELETE) && webhookEvent.getClass().isAssignableFrom(DeleteWebhookEvent.class)) {
        DeleteWebhookEvent deleteWebhookEvent = ((DeleteWebhookEvent) webhookEvent);
        return (deleteWebhookEvent.getDeleteType() == DeleteWebhookEvent.DeleteType.TAG
                   && webhookTriggerSpec.fetchGitAware().getClass().isAssignableFrom(HarnessTagSpec.class))
            || (deleteWebhookEvent.getDeleteType() == DeleteWebhookEvent.DeleteType.BRANCH
                && webhookTriggerSpec.fetchGitAware().getClass().isAssignableFrom(HarnessBranchSpec.class));
      }
      if (eventTypeFromPayload.equals(CREATE) && webhookEvent.getClass().isAssignableFrom(CreateWebhookEvent.class)) {
        CreateWebhookEvent createWebhookEvent = ((CreateWebhookEvent) webhookEvent);
        return (createWebhookEvent.getCreateType() == CreateWebhookEvent.CreateType.TAG
                   && webhookTriggerSpec.fetchGitAware().getClass().isAssignableFrom(HarnessTagSpec.class))
            || (createWebhookEvent.getCreateType() == CreateWebhookEvent.CreateType.BRANCH
                && webhookTriggerSpec.fetchGitAware().getClass().isAssignableFrom(HarnessBranchSpec.class));
      }
    }

    if (eventTypeFromPayload.equals(TAG)) {
      return gitEvent.equals(TAG_EVENT_TYPE);
    }

    if (eventTypeFromPayload.equals(PIPELINE_HOOK)) {
      return gitEvent.equals(PIPELINE_HOOK_EVENT_TYPE);
    }

    if (eventTypeFromPayload.equals(DELETE)) {
      return gitEvent.equals(DELETE_EVENT_TYPE);
    }

    if (eventTypeFromPayload.equals(CREATE)) {
      return gitEvent.equals(CREATE_EVENT_TYPE);
    }

    if (eventTypeFromPayload.equals(ISSUE_COMMENT)) {
      if (webhookTriggerSpec.getClass().isAssignableFrom(HarnessSpec.class)) {
        return gitEvent.equals(PULL_REQUEST_EVENT_TYPE);
      }
      return gitEvent.equals(ISSUE_COMMENT_EVENT_TYPE) || gitEvent.equals(MR_COMMENT_EVENT_TYPE)
          || gitEvent.equals(PR_COMMENT_EVENT_TYPE);
    }

    if (WebhookEvent.Type.RELEASE.equals(eventTypeFromPayload)) {
      return RELEASE_EVENT_TYPE.equals(gitEvent);
    }

    if (eventTypeFromPayload.equals(MERGE_QUEUE)) {
      return gitEvent.equals(MERGE_QUEUE_EVENT_TYPE);
    }
    return false;
  }

  public boolean checkIfActionMatches(WebhookPayloadData webhookPayloadData, WebhookTriggerSpecV2 webhookTriggerSpec) {
    if (webhookTriggerSpec.fetchGitAware() == null) {
      throw new TriggerException(
          "Invalid Filter used. Event Filter is not compatible with class: " + webhookTriggerSpec.getClass(), USER_SRE);
    }

    List<GitAction> actions = webhookTriggerSpec.fetchGitAware().fetchActions();
    // No filter means any actions is valid for trigger invocation
    if (isEmpty(actions)) {
      if (webhookPayloadData.getWebhookEvent() != null && webhookPayloadData.getWebhookEvent().getType() == PR) {
        if (webhookPayloadData.getWebhookEvent().getBaseAttributes() != null
            && Objects.equals(
                webhookPayloadData.getWebhookEvent().getBaseAttributes().getAction(), REVIEWREADY.getParsedValue())) {
          return false;
        }
      }
      return true;
    }

    Set<String> parsedActionValueSet = actions.stream().map(GitAction::getParsedValue).collect(toSet());
    if (actions.contains(BT_PULL_REQUEST_UPDATED)) {
      specialHandlingForBBSPullReqUpdate(webhookPayloadData, parsedActionValueSet);
    }
    String eventActionReceived = webhookPayloadData.getWebhookEvent().getBaseAttributes().getAction();

    if (parsedActionValueSet.contains(eventActionReceived)) {
      return true;
    }

    if (webhookTriggerSpec.getClass().isAssignableFrom(HarnessSpec.class)
        && webhookPayloadData.getWebhookEvent().getType() == ISSUE_COMMENT
        && ((HarnessSpec) webhookTriggerSpec).getSpec().fetchActions().contains(HarnessPRAction.COMMENT)) {
      return true;
    }

    // perform case insensitive check
    String matchedAction = parsedActionValueSet.stream()
                               .filter(parsedValue -> parsedValue.equalsIgnoreCase(eventActionReceived))
                               .findAny()
                               .orElse(null);
    return StringUtils.isNotBlank(matchedAction);
  }

  // SCM returns "sync" for pr:open for BitbucketCloud and "open" for BitbucketServer.
  // So, For BT_PULL_REQUEST_UPDATED, we have associated "sync" as parsedValue,
  // So, here are adding "open" in case, it was bitbucker server payload
  private static void specialHandlingForBBSPullReqUpdate(
      WebhookPayloadData webhookPayloadData, Set<String> parsedActionValueSet) {
    Set<String> headerKeys =
        webhookPayloadData.getOriginalEvent().getHeaders().stream().map(HeaderConfig::getKey).collect(toSet());

    if (!headerKeys.contains(BITBUCKET_CLOUD_HEADER_KEY)
        && !headerKeys.contains(BITBUCKET_CLOUD_HEADER_KEY.toLowerCase())
        && !headerKeys.stream().anyMatch(BITBUCKET_CLOUD_HEADER_KEY::equalsIgnoreCase)) {
      parsedActionValueSet.add(BitbucketPRAction.CREATE.getParsedValue());
    }
  }

  public boolean checkIfPayloadConditionsMatch(WebhookPayloadData webhookPayloadData,
      WebhookTriggerSpecV2 webhookTriggerSpec, TriggerExpressionEvaluator triggerExpressionEvaluator, String accountId,
      PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    // [CI-23187] Guard against null webhookTriggerSpec. For custom triggers with no payload conditions,
    // the inner spec is legitimately null. Prior code NPE'd here, causing PayloadConditionsTriggerFilter to
    // swallow the exception and silently mark the trigger as unmatched.
    if (webhookTriggerSpec == null || webhookTriggerSpec.fetchPayloadAware() == null
        || isEmpty(webhookTriggerSpec.fetchPayloadAware().fetchPayloadConditions())) {
      return true;
    }

    // Remove changed files condition from payload conditions. It will be evaluated separately.
    List<TriggerEventDataCondition> payloadConditions = webhookTriggerSpec.fetchPayloadAware().fetchPayloadConditions();
    payloadConditions = payloadConditions.stream()
                            .filter(payloadCondition -> !CHANGED_FILES.equalsIgnoreCase(payloadCondition.getKey()))
                            .collect(toList());
    if (isEmpty(payloadConditions)) {
      return true;
    }

    String input;
    String standard;
    String operator;
    boolean allConditionsMatched = true;
    for (TriggerEventDataCondition triggerEventDataCondition : payloadConditions) {
      standard = triggerEventDataCondition.getValue();
      operator =
          triggerEventDataCondition.getOperator() != null ? triggerEventDataCondition.getOperator().getValue() : EMPTY;

      // todo: added for easy rollback and early detection of CDS_REMOVE_TAG_TRIGGER_FILTER
      if (triggerEventDataCondition.getKey().equals("tag")) {
        log.info("trigger contains tag condition");
      }

      if (triggerEventDataCondition.getKey().equals("sourceBranch")) {
        input = webhookPayloadData.getWebhookEvent().getBaseAttributes().getSource();
        if (isBlank(input)) {
          // Skipping for push event type, because it doesn't have a source branch
          continue;
        }
      } else if (triggerEventDataCondition.getKey().equals("targetBranch")) {
        input = webhookPayloadData.getWebhookEvent().getBaseAttributes().getTarget();
      } else if (triggerEventDataCondition.getKey().equals("tag")
          && !pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_REMOVE_TAG_TRIGGER_FILTER)) {
        input = webhookPayloadData.getWebhookEvent().getBaseAttributes().getRef();
      } else if (triggerEventDataCondition.getKey().equals("artifactVersion")
          && webhookPayloadData.getWebhookEvent().getType().equals(ARTIFACT)) {
        input = ((ArtifactWebhookEvent) webhookPayloadData.getWebhookEvent()).getVersion();
      } else if (triggerEventDataCondition.getKey().equals("artifactName")
          && webhookPayloadData.getWebhookEvent().getType().equals(ARTIFACT)) {
        input = ((ArtifactWebhookEvent) webhookPayloadData.getWebhookEvent()).getName();
      } else {
        if (triggerExpressionEvaluator == null) {
          triggerExpressionEvaluator = generatorPMSExpressionEvaluator(webhookPayloadData);
        }
        input = readFromPayload(triggerEventDataCondition.getKey(), triggerExpressionEvaluator);
      }

      allConditionsMatched = allConditionsMatched && ConditionEvaluator.evaluate(input, standard, operator);
      if (!allConditionsMatched) {
        break;
      }
    }

    return allConditionsMatched;
  }

  public boolean checkIfJexlConditionsMatch(
      TriggerExpressionEvaluator triggerExpressionEvaluator, String jexlExpression) {
    if (isBlank(jexlExpression)) {
      return true;
    }

    jexlExpression = sanitiseHeaderConditionsForJexl(jexlExpression);

    Object result = triggerExpressionEvaluator.evaluateExpression(jexlExpression);
    if (result != null && Boolean.class.isAssignableFrom(result.getClass())) {
      return (Boolean) result;
    }

    StringBuilder errorMsg = new StringBuilder(128);
    if (result == null) {
      errorMsg.append("Expression ")
          .append(jexlExpression)
          .append(" was evaluated to null. Expected type is Boolean")
          .toString();
    } else {
      errorMsg.append("Expression ")
          .append(jexlExpression)
          .append(":  was evaluated to type: ")
          .append(result.getClass())
          .append(". Expected type is Boolean")
          .toString();
    }

    throw new TriggerException(errorMsg.toString(), USER);
  }

  @VisibleForTesting
  String sanitiseHeaderConditionsForJexl(String expresion) {
    if (isBlank(expresion)) {
      return expresion;
    }

    try {
      Pattern p = Pattern.compile("(<\\+trigger.header\\[[\\'|\"])(.*?)([\\'|\"]\\]>)");
      Matcher m = p.matcher(expresion);

      while (m.find()) {
        expresion = expresion.replace(
            m.group(1) + m.group(2) + m.group(3), "<+trigger.header['" + m.group(2).toLowerCase() + "']>");
      }
    } catch (Exception e) {
      log.error(
          "Failed while converting HeaderKey: " + expresion + " to lower case format. Continuing with key as is", e);
    }

    return expresion;
  }

  public boolean checkIfCustomHeaderConditionsMatch(
      TriggerExpressionEvaluator triggerExpressionEvaluator, List<TriggerEventDataCondition> headerConditions) {
    if (isEmpty(headerConditions)) {
      return true;
    }
    String input;
    String standard;
    String operator;

    for (TriggerEventDataCondition webhookHeaderCondition : headerConditions) {
      String headerConditionKey = webhookHeaderCondition.getKey();
      headerConditionKey = sanitiseHeaderConditionsForJexl(headerConditionKey);

      input = readFromPayload(headerConditionKey, triggerExpressionEvaluator);
      standard = webhookHeaderCondition.getValue();
      operator = webhookHeaderCondition.getOperator().getValue();
      if (!ConditionEvaluator.evaluate(input, standard, operator)) {
        return false;
      }
    }
    return true;
  }

  @VisibleForTesting
  String readFromPayload(String key, TriggerExpressionEvaluator triggerExpressionEvaluator) {
    return triggerExpressionEvaluator.renderExpression(key, true);
  }

  public TriggerExpressionEvaluator generatorPMSExpressionEvaluator(
      WebhookPayloadData webhookPayloadData, TriggerPayload triggerPayload) {
    return new TriggerExpressionEvaluator(triggerPayload, webhookPayloadData.getOriginalEvent().getHeaders(),
        webhookPayloadData.getOriginalEvent().getPayload(), null);
  }

  public TriggerExpressionEvaluator generatorPMSExpressionEvaluator(WebhookPayloadData webhookPayloadData) {
    return generatorPMSExpressionEvaluator(webhookPayloadData.getParseWebhookResponse(),
        webhookPayloadData.getOriginalEvent().getHeaders(), webhookPayloadData.getOriginalEvent().getPayload());
  }

  public TriggerExpressionEvaluator generatorPMSExpressionEvaluator(
      ParseWebhookResponse parseWebhookResponse, List<HeaderConfig> headerConfigs, String payload) {
    return new TriggerExpressionEvaluator(parseWebhookResponse, null, headerConfigs, payload, null);
  }
}
