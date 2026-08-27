/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.constants.OrchestrationPublisherName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.logging.AutoLogContext;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.timeout.TimeoutParameters;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.waiter.WaitNotifyEngine;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class WaitForExecutionInputHelper {
  private static final Long MILLIS_IN_SIX_MONTHS = 86400 * 30 * 6L;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private ExecutionInputService executionInputService;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PmsFeatureFlagHelper featureFlagService;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;

  @Inject private KryoSerializer kryoSerializer;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  /*
    Added EXECUTION_INPUT_REGEX to find out the pipeline having expression in executionInputs allowedValues.
    (allowedValues\((?:<\+[^>]+>(?:,\s*(?:<\+[^>]+>|[^,()]+))*)\)) -> Regex to identify the single expression
    or comma separated multiple expression
   */
  public static final String EXECUTION_INPUT_REGEX =
      "<\\+input>\\.executionInput\\(\\).*\\.(allowedValues\\((?:<\\+[^>]+>(?:,\\s*(?:<\\+[^>]+>|[^,()]+))*)\\))";
  public static final Pattern EXECUTION_INPUT_REGEX_PATTERN = Pattern.compile(EXECUTION_INPUT_REGEX);

  public boolean waitForExecutionInput(Ambiance ambiance, String nodeExecutionId, PlanNode node) {
    if (EmptyPredicate.isEmpty(node.getExecutionInputTemplate())) {
      return false;
    }
    // If instance is already there then that means we have already processed the user input.
    if (executionInputService.isPresent(nodeExecutionId)) {
      return false;
    }
    Optional<String> yaml =
        planExecutionMetadataService.getYaml(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
    boolean storeTemplateReferenceFFEnabled = AmbianceUtils.checkIfFeatureFlagEnabled(
        ambiance, FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name());
    if (yaml.isPresent()) {
      Long currentTime = System.currentTimeMillis();
      String inputInstanceId = UUIDGenerator.generateUuid();
      EngineExpressionEvaluator evaluator = pmsEngineExpressionService.prepareExpressionEvaluator(ambiance);
      JsonNode fieldJsonNode = null;
      boolean isV1 = HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion());
      if (isV1) {
        try {
          fieldJsonNode = YamlNode.getNodeYaml(
              YamlUtils.readYamlTree(yaml.get()).getNode(), ambiance.getLevelsList(), storeTemplateReferenceFFEnabled);
          fieldJsonNode = (JsonNode) pmsEngineExpressionService.resolve(
              ambiance, fieldJsonNode, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        } catch (Exception e) {
          log.warn("Failed to resolve field yaml for execution input for nodeExecutionId: {}. "
                  + "Falling back to empty field yaml for V1 pipeline.",
              nodeExecutionId, e);
        }
      } else {
        fieldJsonNode = YamlNode.getNodeYaml(
            YamlUtils.readYamlTree(yaml.get()).getNode(), ambiance.getLevelsList(), storeTemplateReferenceFFEnabled);
        fieldJsonNode = (JsonNode) pmsEngineExpressionService.resolve(
            ambiance, fieldJsonNode, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      }
      long timeout = 0;
      if (EmptyPredicate.isNotEmpty(node.getTimeoutObtainments())) {
        // We take the last timeout added as timeout for the step.
        TimeoutParameters timeoutParameters = OrchestrationUtils.buildTimeoutParameters(
            kryoSerializer, evaluator, node.getTimeoutObtainments().get(node.getTimeoutObtainments().size() - 1));
        timeout = timeoutParameters.getTimeoutMillis();
      }

      WaitForExecutionInputCallback waitForExecutionInputCallback = WaitForExecutionInputCallback.builder()
                                                                        .nodeExecutionId(nodeExecutionId)
                                                                        .ambiance(ambiance)
                                                                        .inputInstanceId(inputInstanceId)
                                                                        .build();
      String executionInput = node.getExecutionInputTemplate();
      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
        if (containExpressionInAllowedValues(executionInput)) {
          log.info(String.format(
              "ExecutionInput contains expression %s nodeExecutionId %s", executionInput, nodeExecutionId));
        }
      }
      if (featureFlagService.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.PIE_RESOLVE_EXECUTION_INPUT_EXPRESSION.name())) {
        String resolvedExecutionInput = (String) pmsEngineExpressionService.resolve(
            ambiance, executionInput, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        if (YamlUtils.isValidYaml(resolvedExecutionInput)) {
          executionInput = resolvedExecutionInput;
        }
      }
      waitNotifyEngine.waitForAllOnInList(publisherName, waitForExecutionInputCallback,
          Lists.newArrayList(inputInstanceId), Duration.ofMillis(timeout));
      String fieldYaml = fieldJsonNode != null ? YamlUtils.writeYamlString(fieldJsonNode) : "{}";
      executionInputService.save(ExecutionInputInstance.builder()
                                     .inputInstanceId(inputInstanceId)
                                     .nodeExecutionId(nodeExecutionId)
                                     .fieldYaml(fieldYaml)
                                     .template(executionInput)
                                     .createdAt(currentTime)
                                     .validUntil(currentTime + MILLIS_IN_SIX_MONTHS)
                                     .build());
      // Updating the current node status. InputWaitingStatusUpdateHandler will update status of parent recursively.
      nodeExecutionService.updateStatusWithOps(nodeExecutionId, Status.INPUT_WAITING,
          ops -> setUnset(ops, NodeExecutionKeys.startTs, System.currentTimeMillis()), EnumSet.noneOf(Status.class));
      return true;
    } else {
      log.error("Pipeline for planExecutionId {} is deleted or not does not exist.", ambiance.getPlanExecutionId());
      throw new InvalidRequestException(
          "Pipeline for planExecutionId " + ambiance.getPlanExecutionId() + " is deleted or not does not exist.");
    }
  }

  boolean containExpressionInAllowedValues(String executionInput) {
    try {
      // Compile the pattern
      Matcher matcher = EXECUTION_INPUT_REGEX_PATTERN.matcher(executionInput);
      List<String> matches = new ArrayList<>();

      // Find all matches
      while (matcher.find()) {
        matches.add(matcher.group());
      }
      return !matches.isEmpty();
    } catch (Exception e) {
      log.warn("Failed to parse the expression", e);
    }
    return false;
  }
}
