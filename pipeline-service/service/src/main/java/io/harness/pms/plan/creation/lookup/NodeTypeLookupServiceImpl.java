/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation.lookup;

import static io.harness.cf.pipeline.FeatureFlagStageFilterJsonCreator.FEATURE_FLAG_SUPPORTED_TYPE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.plan.Node;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.creation.lookup.intfc.NodeTypeLookupService;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.helper.PmsSdkHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
public class NodeTypeLookupServiceImpl implements NodeTypeLookupService {
  @Inject private PmsSdkInstanceService pmsSdkInstanceService;
  @Inject private PmsSdkHelper pmsSdkHelper;

  private static final String FLAG_STAGE = "FLAG_STAGE";
  private static final String CUSTOM_STAGE = "CUSTOM_STAGE";
  private static final List<String> DBOPS_STEP_TYPES = List.of("DBSchemaRollback", "DBSchemaApply", "DBCommand",
      "LiquibaseCommand", "DBSchemaUpdateSQL", "DBTestAndPreview", "DBSchemaRollbackSQL", "FlywayCommand");
  private static final String DBOPS_MODULE = "dbops";

  // following stage types not to be considered while deciding module addition
  private static final List<String> STAGES_TO_EXCLUDE_FROM_MODULE_ADDITION = List.of(CUSTOM_STAGE, FLAG_STAGE);

  @Override
  public String findNodeTypeServiceName(String nodeType) {
    Map<String, Map<String, Set<String>>> map = pmsSdkInstanceService.getInstanceNameToSupportedTypes();
    if (isEmpty(map)) {
      throw new InvalidRequestException("Supported Types Map is empty");
    }
    for (Map.Entry<String, Map<String, Set<String>>> entry : map.entrySet()) {
      Set<String> supportedNodeTypes = new HashSet<>();
      for (Set<String> stringSet : entry.getValue().values()) {
        supportedNodeTypes.addAll(stringSet);
        if (isEmpty(supportedNodeTypes)) {
          continue;
        }

        if (supportedNodeTypes.stream().anyMatch(st -> st.equals(nodeType))) {
          if (nodeType.equals(FEATURE_FLAG_SUPPORTED_TYPE)) {
            return "cf";
          }
          if (nodeType.equals("Custom")) {
            return "pms";
          }
          return entry.getKey();
        }
      }
    }

    throw new InvalidRequestException("Unknown Node type: " + nodeType);
  }

  @Override
  public List<String> modulesThatSupportStepTypes(List<Node> planNodeList) {
    // We get the supportedSdkSteps corresponding to all the modules
    Map<String, Set<SdkStep>> allSupportedSdkSteps = pmsSdkInstanceService.getSdkSteps();
    if (isEmpty(allSupportedSdkSteps)) {
      throw new InvalidRequestException("Supported Types Map is empty");
    }

    Map<String, Set<String>> stepsSupportedByModules = new LinkedHashMap<>();

    // Mapping all the isPalleteTrue steps to the corresponding modules
    for (Map.Entry<String, Set<SdkStep>> entry : allSupportedSdkSteps.entrySet()) {
      Set<String> isPalleteTrue = new HashSet<>();
      for (SdkStep sdkStep : entry.getValue()) {
        if (sdkStep.getIsPartOfStepPallete()) {
          isPalleteTrue.add(sdkStep.getStepType().getType());
        }
      }
      if (!isPalleteTrue.isEmpty()) {
        stepsSupportedByModules.put(entry.getKey(), isPalleteTrue);
      }
    }

    Set<String> modulesBasedOnStage = new LinkedHashSet<>();
    Set<String> modulesBasedOnStep = new LinkedHashSet<>();

    // iterating over the planNodeList and then checking if the stepType is supported by any of the modules in the
    // stepsSupportedByModules map
    for (Node node : planNodeList) {
      // extracting modules based on stage i.e. stage belongs to which module
      if (StepCategory.STAGE.equals(node.getStepType().getStepCategory())) {
        if (node.getStepType().getType().equals(FLAG_STAGE)) {
          modulesBasedOnStage.add("cf");
        } else if (node.getStepType().getType().equals(CUSTOM_STAGE)) {
          modulesBasedOnStage.add("pms");
        } else {
          modulesBasedOnStage.add(node.getServiceName());
        }
      }

      // explicit handling of modules based on step type
      if (StepCategory.STEP.equals(node.getStepType().getStepCategory())) {
        // handling for DBOps related steps
        if (DBOPS_STEP_TYPES.contains(node.getStepType().getType())) {
          modulesBasedOnStep.add(DBOPS_MODULE);
        }
      }

      for (Map.Entry<String, Set<String>> entry : stepsSupportedByModules.entrySet()) {
        if (entry.getValue().contains(node.getStepType().getType().toString())) {
          // Extracting modules based on step i.e. step belongs to which module.
          // Filtering out step types which have definition in other module and belongs to others.
          if (!(STAGES_TO_EXCLUDE_FROM_MODULE_ADDITION.contains(node.getStepType().getType())
                  || DBOPS_STEP_TYPES.contains(node.getStepType().getType()))) {
            modulesBasedOnStep.add(entry.getKey());
          }
        }
      }
    }

    // sorting the steps based on the service priority coming from config.yml
    modulesBasedOnStep =
        modulesBasedOnStep.stream()
            .sorted(Comparator.comparingInt(module -> pmsSdkHelper.getPipelineSdkPriority().getOrDefault(module, 100)))
            .collect(Collectors.toCollection(LinkedHashSet::new));

    modulesBasedOnStage.addAll(modulesBasedOnStep);

    return new ArrayList<>(modulesBasedOnStage);
  }
}
