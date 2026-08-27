/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator.V1;

import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.PluginStepNode;
import io.harness.beans.steps.nodes.V1.PluginStepNodeV1;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfoV1;
import io.harness.ci.execution.integrationstage.V1.CIPlanCreatorUtils;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Set;

public class PluginStepPlanCreatorV1 extends CIPMSStepPlanCreatorV2<PluginStepNodeV1> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.PLUGIN_V1.getDisplayName());
  }

  @Override
  public PluginStepNodeV1 getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), PluginStepNodeV1.class);
    } catch (IOException e) {
      throw new InvalidYamlException("Unable to parse plugin step yaml. Please ensure that it is in correct format", e);
    }
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, PluginStepNodeV1 stepElement) {
    return super.createPlanForFieldV2(ctx, stepElement);
  }

  @Override
  public CIAbstractStepNode getStepNode(PluginStepNodeV1 stepElement) {
    PluginStepInfoV1 stepInfo = stepElement.getPluginStepInfo();
    return PluginStepNode.builder()
        .uuid(stepElement.getUuid())
        .identifier(IdentifierGeneratorUtils.getId(stepElement.getName()))
        .name(stepElement.getName())
        .failureStrategies(stepElement.getFailureStrategies())
        .timeout(stepElement.getTimeout())
        .pluginStepInfo(PluginStepInfo.builder()
                            .image(stepInfo.getImage())
                            .uses(stepInfo.getUses())
                            .envVariables(stepInfo.getEnvs())
                            .privileged(stepInfo.getPrivileged())
                            .resources(stepInfo.getResources())
                            .runAsUser(stepInfo.getUser())
                            .retry(stepInfo.getRetry())
                            .settings(stepInfo.getWith())
                            .imagePullPolicy(CIPlanCreatorUtils.getImagePullPolicy(stepInfo.getPull()))
                            .build())
        .build();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }
}
