/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.dto.OnboardingExecuteResponseDTO;

/**
 * Orchestrates onboarding of a set of interdependent NG resources from a single declarative
 * context: secrets, connectors, and a service (with manifest + artifact). Resources are created
 * in dependency order and their identifiers threaded forward.
 */
@OwnedBy(HarnessTeam.CDC)
public interface OnboardingOrchestrationService {
  OnboardingExecuteResponseDTO execute(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, OnboardingContextDTO context);
}
