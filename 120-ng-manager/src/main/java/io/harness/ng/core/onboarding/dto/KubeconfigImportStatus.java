/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDC)
public enum KubeconfigImportStatus {
  /** Every field the connector DTO requires is present inline; a connector can be created with no further input. */
  COMPLETE,
  /**
   * The context cannot create a manual-credential connector as-is -- unsupported auth mode (gcp/azure/exec), an
   * unresolved cluster/user reference, or a required field the kubeconfig does not provide (see errors).
   */
  UNSUPPORTED
}
