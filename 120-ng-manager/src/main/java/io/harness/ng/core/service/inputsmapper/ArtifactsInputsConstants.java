/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.inputsmapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

/**
 * Constants for Docker artifact input field names used in template-based processing.
 * These constants represent the keys in the inputs map that templates expect.
 */
@OwnedBy(HarnessTeam.CI)
@UtilityClass
public class ArtifactsInputsConstants {
  public static final String SIDECAR_ARTIFACT = "sidecarArtifact";
  public static final String ARTIFACT_TYPE = "artifactType";
}
