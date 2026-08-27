/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage.V1;

import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.pms.contracts.plan.ListValue;
import io.harness.pms.yaml.TemplateType;
import io.harness.pms.yaml.YamlField;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ModuleSpecificPlanHandlers {
  private static final Map<TemplateType, BiFunction<YamlField, UnifiedStageNodeV1, Map<String, Object>>>
      MODULE_SPECIFIC_INFO_HANDLERS =
          ImmutableMap.<TemplateType, BiFunction<YamlField, UnifiedStageNodeV1, Map<String, Object>>>builder()
              .put(TemplateType.DEPLOY, CDStepsPlanCreatorUtils::getDeployNodesInfo)
              .put(TemplateType.TEST, CDStepsPlanCreatorUtils::getTestNodesInfo)
              .put(TemplateType.CHAOS, CDStepsPlanCreatorUtils::getChaosNodesInfo)
              .put(TemplateType.IDP, CDStepsPlanCreatorUtils::getIdpNodesInfo)
              .put(TemplateType.IACM, IACMPlanCreatorUtils::getIacmNodesInfo)
              .put(TemplateType.STO, CDStepsPlanCreatorUtils::getStoNodesInfo)
              .build();

  private static final BiConsumer<Map<String, Object>, ListValue.Builder> NOOP_GET_STAGE_CHILDREN_INFO = (s, i) -> {};

  private static final Map<TemplateType, BiConsumer<Map<String, Object>, ListValue.Builder>>
      MODULE_SPECIFIC_CHILDREN_HANDLERS =
          ImmutableMap.<TemplateType, BiConsumer<Map<String, Object>, ListValue.Builder>>builder()
              .put(TemplateType.DEPLOY, CDStepsPlanCreatorUtils::getDeployStageChildrenEntitiesInfo)
              .put(TemplateType.TEST, NOOP_GET_STAGE_CHILDREN_INFO)
              .put(TemplateType.CHAOS, NOOP_GET_STAGE_CHILDREN_INFO)
              .put(TemplateType.IDP, NOOP_GET_STAGE_CHILDREN_INFO)
              .put(TemplateType.IACM, IACMPlanCreatorUtils::getIacmStageChildrenEntitiesInfo)
              .put(TemplateType.STO, NOOP_GET_STAGE_CHILDREN_INFO)
              .build();

  public static Map<String, Object> getModulesImplicitNodesInfo(
      YamlField stageYamlField, UnifiedStageNodeV1 stageNode) {
    Map<String, Object> templateTypeBasedInfo = new HashMap<>();

    for (TemplateType templateType : TemplateType.getCustomTypes()) {
      BiFunction<YamlField, UnifiedStageNodeV1, Map<String, Object>> handler =
          MODULE_SPECIFIC_INFO_HANDLERS.get(templateType);
      if (handler != null) {
        Map<String, Object> nodesInfo = handler.apply(stageYamlField, stageNode);
        if (!nodesInfo.isEmpty()) {
          templateTypeBasedInfo.put(templateType.getName(), nodesInfo);
        }
      }
    }
    return templateTypeBasedInfo;
  }

  public static ListValue getStageChildrenEntitiesInfo(Map<String, Object> modulesImplicitNodesInfo) {
    ListValue.Builder childrenEntitiesInfo = ListValue.newBuilder();

    for (TemplateType templateType : TemplateType.getCustomTypes()) {
      BiConsumer<Map<String, Object>, ListValue.Builder> handler = MODULE_SPECIFIC_CHILDREN_HANDLERS.get(templateType);
      if (handler != null) {
        handler.accept(modulesImplicitNodesInfo, childrenEntitiesInfo);
      }
    }

    return childrenEntitiesInfo.build();
  }
}
