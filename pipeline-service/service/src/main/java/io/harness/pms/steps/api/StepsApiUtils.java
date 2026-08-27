/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.steps.api;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepPalleteFilterWrapper;
import io.harness.pms.pipeline.StepPalleteModuleInfo;
import io.harness.spec.server.pipeline.v1.model.StepData;
import io.harness.spec.server.pipeline.v1.model.StepPalleteFilterRequestBody;
import io.harness.spec.server.pipeline.v1.model.StepsDataResponseBody;

import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class StepsApiUtils {
  public static StepPalleteFilterWrapper mapToStepPalleteFilterWrapperDTO(
      StepPalleteFilterRequestBody stepPalleteFilterWrapper) {
    return StepPalleteFilterWrapper.builder()
        .stepPalleteModuleInfos(stepPalleteFilterWrapper.getStepPalleteModuleInfos()
                                    .stream()
                                    .map(StepsApiUtils::toStepPalleteModuleInfo)
                                    .collect(Collectors.toList()))
        .build();
  }

  private static StepPalleteModuleInfo toStepPalleteModuleInfo(
      io.harness.spec.server.pipeline.v1.model.StepPalleteModuleInfo stepPalleteModuleInfo) {
    return StepPalleteModuleInfo.builder()
        .module(stepPalleteModuleInfo.getModule())
        .category(stepPalleteModuleInfo.getCategory())
        .shouldShowCommonSteps(stepPalleteModuleInfo.isShouldShowCommonSteps())
        .commonStepCategory(stepPalleteModuleInfo.getCommonStepCategory())
        .build();
  }

  public static StepsDataResponseBody toStepsDataResponseBody(StepCategory stepCategory) {
    StepsDataResponseBody stepsDataResponseBody = new StepsDataResponseBody();
    stepsDataResponseBody.setName(stepCategory.getName());
    stepsDataResponseBody.setStepsData(
        stepCategory.getStepsData().stream().map(StepsApiUtils::toStepDataResponse).collect(Collectors.toList()));
    stepsDataResponseBody.setStepCategories(stepCategory.getStepCategories()
                                                .stream()
                                                .map(StepsApiUtils::toStepsDataResponseBody)
                                                .collect(Collectors.toList()));
    return stepsDataResponseBody;
  }

  private static StepData toStepDataResponse(io.harness.pms.pipeline.StepData stepData) {
    StepData stepDataResponse = new StepData();
    stepDataResponse.setName(stepData.getName());
    stepDataResponse.setType(stepData.getType());
    stepDataResponse.setDisabled(stepData.isDisabled());
    if (stepData.getFeatureRestrictionName() != null) {
      stepDataResponse.setFeatureRestrictionName(stepData.getFeatureRestrictionName().name());
    }
    return stepDataResponse;
  }
}
