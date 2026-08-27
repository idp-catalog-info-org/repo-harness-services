/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.observers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.facilitation.facilitator.FacilitatorMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PreStepCheckObserver {
  /**
   * This is interface function which is called using observer pattern by PreStepCheckFacilitator
   *
   * @param ambiance
   * @param facilitatorMetadata
   */
  void onPreStepCheck(Ambiance ambiance, FacilitatorMetadata facilitatorMetadata);
}