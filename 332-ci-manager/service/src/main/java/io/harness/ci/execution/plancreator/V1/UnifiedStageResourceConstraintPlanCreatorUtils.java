/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator.V1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.YamlException;
import io.harness.ng.core.utils.InfrastructureExecutionConstants;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.utilities.ResourceConstraintUtility;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.LinkedHashMap;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@UtilityClass
@Slf4j
public class UnifiedStageResourceConstraintPlanCreatorUtils {
  public static LinkedHashMap<String, PlanCreationResponse> addResourceConstraintDependency(PlanCreationContext context,
      String whenCondition, boolean isProjectScopedResourceConstraintQueue, String nextId) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    YamlField rcYamlField =
        constructResourceConstraintYamlField(context, whenCondition, isProjectScopedResourceConstraintQueue);
    return updateResourceConstraintYamlField(rcYamlField, planCreationResponseMap, nextId);
  }

  private static YamlField constructResourceConstraintYamlField(
      PlanCreationContext context, String whenCondition, boolean isProjectScopedResourceConstraintQueue) {
    JsonNode resourceConstraintJsonNode =
        getResourceConstraintJsonNode(context, whenCondition, isProjectScopedResourceConstraintQueue);
    // current field is steps, yaml updates for resource constraint should be at the same level as steps, that's why
    // passing steps parent node as parent node for resource constraint
    return new YamlField(YAMLFieldNameConstants.STEP,
        new YamlNode(StepSpecTypeConstants.RESOURCE_CONSTRAINT, resourceConstraintJsonNode,
            context.getCurrentField().getNode().getParentNode()));
  }

  private static JsonNode getResourceConstraintJsonNode(
      PlanCreationContext context, String whenCondition, boolean isProjectScopedResourceConstraintQueue) {
    String resourceUnit = getResourceConstraintUnit(context, isProjectScopedResourceConstraintQueue);
    return ResourceConstraintUtility.getResourceConstraintJsonNode(resourceUnit, whenCondition);
  }

  private static String getResourceConstraintUnit(
      PlanCreationContext context, boolean isProjectScopedResourceConstraintQueue) {
    String resourceUnit = String.format("<+%s>", InfrastructureExecutionConstants.INFRA_KEY);
    if (isProjectScopedResourceConstraintQueue && context != null) {
      String accountIdentifier = context.getAccountIdentifier();
      String orgIdentifier = context.getOrgIdentifier();
      String projectIdentifier = context.getProjectIdentifier();
      resourceUnit = String.join("_", resourceUnit,
          String.valueOf(String.join("_", accountIdentifier, orgIdentifier, projectIdentifier).hashCode()));
    }
    return resourceUnit;
  }

  private static LinkedHashMap<String, PlanCreationResponse> updateResourceConstraintYamlField(
      YamlField rcYamlField, LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, String nextId) {
    try {
      YamlUpdates yamlUpdates =
          YamlUpdates.newBuilder()
              .putFqnToYaml(rcYamlField.getYamlPath(), YamlUtils.writeYamlString(rcYamlField).replace("---\n", ""))
              .build();
      planCreationResponseMap.put(rcYamlField.getNode().getUuid(),
          PlanCreationResponse.builder()
              .dependencies(
                  Dependencies.newBuilder()
                      .putDependencies(rcYamlField.getUuid(), rcYamlField.getYamlPath())
                      .putDependencyMetadata(rcYamlField.getUuid(),
                          Dependency.newBuilder()
                              .setNodeMetadata(HarnessStruct.newBuilder()
                                                   .putData(PlanCreatorConstants.NEXT_ID,
                                                       HarnessValue.newBuilder().setStringValue(nextId).build())
                                                   .build())
                              .build())
                      .build())
              .yamlUpdates(yamlUpdates)
              .build());
    } catch (IOException e) {
      log.error("Exception occurred while converting resource constraint yaml field to yaml string", e);
      throw new YamlException("Yaml created for resource constraint at " + rcYamlField.getYamlPath()
          + " could not be converted into a yaml string. " + e.getMessage());
    }
    return planCreationResponseMap;
  }
}
