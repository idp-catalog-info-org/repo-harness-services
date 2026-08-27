/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class Constants {
  public static final String USERNAME_AND_TOKEN = "UsernameToken";
  public static final String USERNAME_PASSWORD = "UsernamePassword";
  public static final String GITHUB_APP = "GitHubApp";
  public static final String MANAGED_TOKEN = "ManagedToken";

  public static final String IDP_GIT_INTEGRATION_MANAGED_HCR = "IDP_GIT_INTEGRATION_MANAGED_HCR";
  public static final String IDP_MANAGED_HCR_WRITE = "IDP_MANAGED_HCR_WRITE";
  public static final String HCR_CONNECTOR_IDENTIFIER = "__hcr__";

  public static final String HARNESS_CD_CATALOG_INTEGRATION = "_harness_cd";
}
