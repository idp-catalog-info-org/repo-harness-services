/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.jackson.json;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSecretSanitizer {
  /*
  The below secret regex is to identify secret expression.
  The following expression related to secret gets masked:
  1. "${ngSecretManager.obtain(\"account.docker\", -292012941)}"
  2. "${ngSecretManager.obtain(\\\"account.docker\\\", -292012941)}"    - refer - CDS-85736
  3. "${sweepingOutputSecrets.obtain('key','value')}"                  - single quote variant
   */
  private static final String SECRET_REGEX = "\\$\\{ngSecretManager\\.obtain\\(\\\\\"[\\w|.|-]+\\\\\", "
      + "[\\w|.|-]+\\)}|\\$\\{ngSecretManager\\.obtain\\(\\\\\\\\\\\\\"[\\w|.|-]+\\\\\\\\\\\\\", "
      + "[\\w|.|-]+\\)}|\\$\\{sweepingOutputSecrets\\.obtain\\(\\\\\"[\\S|.]+?\\\\\",\\\\\"[\\S|.]+?\"\\)}|<<"
      + "sweepingOutputSecrets\\.obtain\\(\\\\\"[\\S|.]+?\\\\\",\\\\\"[\\S|.]+?\"\\)>>|\\$\\{sweepingOutputSecrets\\."
      + "obtain\\('[\\S|.]+?','[\\S|.]+?'\\)}|\\$\\{sweepingOutputSecrets\\.(select|exists)\\(\\\\\"[\\S|.]+?\\\\\",.*?"
      + "}\\)}|<<sweepingOutputSecrets\\.(select|exists)\\(\\\\\"[\\S|.]+?\\\\\",.*?}\\)>>|\\$\\{"
      + "sweepingOutputSecrets\\.isValid\\(.*?}\\)}|<<sweepingOutputSecrets\\.isValid\\(.*?}\\)>>";

  private static final String SECRET_MASK = "*******";

  public String sanitize(String json) {
    return json.replaceAll(SECRET_REGEX, SECRET_MASK);
  }
}
