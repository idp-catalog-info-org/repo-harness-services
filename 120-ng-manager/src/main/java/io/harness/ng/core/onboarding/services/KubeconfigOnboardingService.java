/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.onboarding.dto.KubeconfigUploadResponseDTO;

import java.io.InputStream;

@OwnedBy(HarnessTeam.CDC)
public interface KubeconfigOnboardingService {
  /**
   * Parses the uploaded kubeconfig and reverse-engineers, per context, a connector-creation hint
   * (auth type, master URL, CA cert, secrets, missing fields, warnings).
   *
   * @param accountIdentifier account scope (required)
   * @param orgIdentifier organization scope (optional)
   * @param projectIdentifier project scope (optional)
   * @param uploadedInputStream the uploaded kubeconfig file content
   * @return per-context connector-creation descriptors plus the current-context
   */
  KubeconfigUploadResponseDTO processKubeconfig(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, InputStream uploadedInputStream);
}
