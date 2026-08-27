/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.common;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class OAuthUtils {
  public String getAuthNameForId(String authId) {
    return switch (authId) {
      case Constants.GITHUB_AUTH -> Constants.GITHUB_AUTH_NAME;
      case Constants.GOOGLE_AUTH -> Constants.GOOGLE_AUTH_NAME;
      case Constants.ATLASSIAN_AUTH -> Constants.ATLASSIAN_AUTH_NAME;
      default -> null;
    };
  }
}
