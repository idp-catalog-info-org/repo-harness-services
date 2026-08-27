/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.registries;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Template Registry for detecting template availability.
 * NEW FRAMEWORK: This is part of the template-based conversion approach.
 *
 * This registry delegates to TemplateOnboardingRegistry which is the single source of truth.
 * All onboarding decisions are made based on the global sets in TemplateOnboardingRegistry.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class TemplateServiceRegistry {
  @Inject private TemplateOnboardingRegistry onboardingRegistry;

  /**
   * Get template type for artifact.
   * Delegates to TemplateOnboardingRegistry.
   */
  public String getArtifactTemplateType(String artifactType) {
    return onboardingRegistry.getArtifactTemplateName(artifactType);
  }

  /**
   * Get template type for manifest.
   * Delegates to TemplateOnboardingRegistry.
   */
  public String getManifestTemplateType(String manifestType, String storeType) {
    return onboardingRegistry.getManifestTemplateName(manifestType, storeType);
  }

  public String getConfigFileTemplateType(String storeTypeDisplayName) {
    return onboardingRegistry.getConfigFileTemplateName(storeTypeDisplayName);
  }
}
