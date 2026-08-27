/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation.facilitator;

import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;

// Marker Interface for Facilitators owned by the Pipeline service. These facilitators do not need to queue any event
public interface CoreFacilitator {
  FacilitatorResponseProto facilitate(Ambiance ambiance, byte[] parameters);

  // Call the default implementation if method not override by facilitator
  default FacilitatorResponseProto facilitateWithMetadata(
      Ambiance ambiance, byte[] parameters, FacilitatorMetadata facilitatorMetadata) {
    return facilitate(ambiance, parameters);
  }

  /*
   Override this function if adding an extra facilitator apart from primary facilitator.
   Primary facilitator is used for identifying the execution mode. We use the response from primary facilitator.
   Secondary facilitator is used for doing any additional task apart from primary. We should never use response of
   secondary facilitator.
   */
  default boolean isPrimaryFacilitator() {
    return true;
  }
}
