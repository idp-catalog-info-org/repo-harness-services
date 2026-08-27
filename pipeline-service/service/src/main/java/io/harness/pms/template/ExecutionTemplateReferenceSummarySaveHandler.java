/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template;

import static io.harness.beans.FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.observers.NodeCreateInfo;
import io.harness.engine.observers.NodeExecutionCreateObserver;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class ExecutionTemplateReferenceSummarySaveHandler implements NodeExecutionCreateObserver, AsyncInformObserver {
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject private NodeExecutionInfoService nodeExecutionInfoService;

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }

  @Override
  public void onNodeCreate(NodeCreateInfo nodeCreateInfo) {
    if (nodeCreateInfo.getNode() == null || nodeCreateInfo.getNode().getTemplateReferenceSummary() == null
        || isEmpty(nodeCreateInfo.getNode().getTemplateReferenceSummary().toString())
        || (!AmbianceUtils.checkIfFeatureFlagEnabled(
                nodeCreateInfo.getAmbiance(), PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.name())
            && !HarnessYamlVersion.isV1(AmbianceUtils.getPipelineVersion(nodeCreateInfo.getAmbiance())))) {
      return;
    }

    String json = RecastOrchestrationUtils.toJson(nodeCreateInfo.getNode().getTemplateReferenceSummary());
    PmsStepDetails stepDetail = PmsStepDetails.parse(json);
    nodeExecutionInfoService.addStepDetail(nodeCreateInfo.getNodeExecutionId(), nodeCreateInfo.getPlanExecutionId(),
        stepDetail, "templateReferenceSummary");
  }
}
