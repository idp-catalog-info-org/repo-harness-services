/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.refresh.bean.EntityRefreshContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ChainedPipelineScopeHelper {
  static final String PIPELINE_STAGE_TYPE = "Pipeline";
  static final String ORG = "org";

  public boolean isChainedPipelineStage(String fieldName, JsonNode value) {
    return YamlTypes.STAGE.equals(fieldName) && value != null && value.isObject()
        && value.get(YAMLFieldNameConstants.TYPE) != null
        && PIPELINE_STAGE_TYPE.equals(value.get(YAMLFieldNameConstants.TYPE).asText());
  }

  /**
   * Derives a child-pipeline scoped context from {@code spec.org} / {@code spec.project}.
   * Returns null when either value is missing, empty, a runtime input, or an expression so callers
   * can skip the subtree instead of falling back to the parent scope.
   */
  public EntityRefreshContext childScopedContext(JsonNode stageValue, EntityRefreshContext context) {
    if (stageValue == null || context == null) {
      return null;
    }
    JsonNode spec = stageValue.get(YAMLFieldNameConstants.SPEC);
    if (spec == null || !spec.isObject()) {
      return null;
    }
    String org = textValue(spec.get(ORG));
    String project = textValue(spec.get(YamlTypes.PROJECT));
    if (!isFixedScopeValue(org) || !isFixedScopeValue(project)) {
      log.debug("Skipping CD inputs walk for chained pipeline stage; child org/project is not a fixed value");
      return null;
    }
    return context.toBuilder().orgId(org).projectId(project).build();
  }

  private String textValue(JsonNode node) {
    if (node == null || node.isNull() || !node.isValueNode()) {
      return null;
    }
    return node.asText();
  }

  private boolean isFixedScopeValue(String value) {
    return EmptyPredicate.isNotEmpty(value) && !NGExpressionUtils.isRuntimeField(value)
        && !NGExpressionUtils.isExpressionField(value);
  }
}
