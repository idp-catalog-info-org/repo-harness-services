/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class MultiDeploymentUtils {
  private static final String MATRIX_EXPRESSION = "<+matrix.%s>";
  private static final String ENVIRONMENT_REF = "environmentRef";
  public static final String ENVIRONMENT_REF_EXPRESSION = String.format(MATRIX_EXPRESSION, ENVIRONMENT_REF);
  public static final String SERVICE_REF_EXPRESSION =
      String.format(MATRIX_EXPRESSION, YAMLFieldNameConstants.SERVICE_REF);
  public static final String INFRA_IDENTIFIER_EXPRESSION =
      String.format(MATRIX_EXPRESSION, YAMLFieldNameConstants.IDENTIFIER);
}
