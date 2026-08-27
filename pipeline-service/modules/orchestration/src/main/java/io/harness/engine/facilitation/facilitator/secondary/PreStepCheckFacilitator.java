/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.facilitation.facilitator.secondary;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.facilitation.facilitator.CoreFacilitator;
import io.harness.engine.facilitation.facilitator.FacilitatorMetadata;
import io.harness.engine.observers.PreStepCheckObserver;
import io.harness.observer.Subject;
import io.harness.opaclient.model.OpaConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import java.util.List;
import lombok.Getter;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PreStepCheckFacilitator implements CoreFacilitator {
  @Getter private final Subject<PreStepCheckObserver> preStepCheckSubject = new Subject<>();

  public static final FacilitatorType FACILITATOR_TYPE =
      FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.PRE_STEP_CHECK).build();

  private static final List<ExecutionMode> EXCLUDED_EXECUTION_MODE =
      ImmutableList.of(ExecutionMode.CHILD, ExecutionMode.CHILDREN, ExecutionMode.CHILD_CHAIN);

  @Override
  public FacilitatorResponseProto facilitate(Ambiance ambiance, byte[] parameters) {
    return FacilitatorResponseProto.newBuilder()
        .setExecutionMode(ExecutionMode.PRE_STEP_CHECK)
        .setIsSuccessful(true)
        .build();
  }

  @Override
  public FacilitatorResponseProto facilitateWithMetadata(
      Ambiance ambiance, byte[] parameters, FacilitatorMetadata facilitatorMetadata) {
    if (AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, OpaConstants.PIPE_IS_ON_STEP_START_POLICY_PRESENT)
        && !EXCLUDED_EXECUTION_MODE.contains(facilitatorMetadata.getMode())) {
      preStepCheckSubject.fireInform(PreStepCheckObserver::onPreStepCheck, ambiance, facilitatorMetadata);
    }

    return FacilitatorResponseProto.newBuilder()
        .setExecutionMode(ExecutionMode.PRE_STEP_CHECK)
        .setIsSuccessful(true)
        .build();
  }

  @Override
  public boolean isPrimaryFacilitator() {
    return false;
  }
}