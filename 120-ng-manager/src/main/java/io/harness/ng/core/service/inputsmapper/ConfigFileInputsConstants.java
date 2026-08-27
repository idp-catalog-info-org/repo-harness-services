/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.inputsmapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * Keys for unified {@link io.harness.unified.cd.service.configfiles.ConfigFile#getInputs()} in template-based flow.
 */
@OwnedBy(HarnessTeam.CI)
@UtilityClass
public class ConfigFileInputsConstants {
  /** Same semantic as {@link ManifestInputsConstants#STORE_TYPE}. */
  public static final String STORE_TYPE = ManifestInputsConstants.STORE_TYPE;
}
