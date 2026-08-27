/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/** Canonical Git HTTP auth modes supported by onboarding (across GitHub, Bitbucket and GitLab). */
@OwnedBy(HarnessTeam.CDC)
public enum GitAuthMode {
  USERNAME_TOKEN,
  OAUTH;
}
