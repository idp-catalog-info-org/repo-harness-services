/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.registrars;

import io.harness.ci.execution.states.IntegrationStageStepPMSFacilitator;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.sdk.core.execution.events.node.facilitate.response.Facilitator;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class STOFacilitatorRegistrar {
  public Map<FacilitatorType, Class<? extends Facilitator>> getEngineFacilitators() {
    Map<FacilitatorType, Class<? extends Facilitator>> engineFacilitators = new HashMap<>();
    engineFacilitators.put(
        IntegrationStageStepPMSFacilitator.FACILITATOR_TYPE, IntegrationStageStepPMSFacilitator.class);
    return engineFacilitators;
  }
}
