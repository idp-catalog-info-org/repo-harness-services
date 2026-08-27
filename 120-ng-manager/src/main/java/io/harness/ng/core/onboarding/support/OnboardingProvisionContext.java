/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.support;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Working state threaded through the onboarding provisioners instead of long argument lists. Carries the resolved
 * scope, the flat request DTO (retained per constraint C1), and the mutable {@code createdSecrets} accumulator that
 * a provisioner appends to whenever it materializes a credential secret. Connector building reads all of these.
 */
@OwnedBy(HarnessTeam.CDC)
@Getter
@Builder
public class OnboardingProvisionContext {
  private final String accountIdentifier;
  private final String orgIdentifier;
  private final String projectIdentifier;
  private final ScopeInfo scopeInfo;
  private final OnboardingContextDTO request;
  private final List<String> createdSecrets;
}
