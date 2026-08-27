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
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.spec.server.pipeline.v1.StepsApi;
import io.harness.spec.server.pipeline.v1.model.StepPalleteFilterRequestBody;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class StepsApiImpl implements StepsApi {
  private final PMSPipelineService pmsPipelineService;
  @Override
  public Response getSteps(@Valid StepPalleteFilterRequestBody body, String harnessAccount) {
    StepCategory stepCategory = pmsPipelineService.getStepsWithVersion(
        harnessAccount, StepsApiUtils.mapToStepPalleteFilterWrapperDTO(body), body.getVersion());
    return Response.ok().entity(StepsApiUtils.toStepsDataResponseBody(stepCategory)).build();
  }
}
