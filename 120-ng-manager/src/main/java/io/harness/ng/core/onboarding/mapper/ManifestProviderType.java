/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/**
 * Git manifest providers supported by onboarding. To add a new provider:
 * <ol>
 *   <li>add a constant here (with {@code requiresConnector} set appropriately),</li>
 *   <li>map its accepted request value(s) in {@link OnboardingContextNormalizer#resolveManifestType},</li>
 *   <li>build its connector in {@code OnboardingOrchestrationImpl} (only if it requires one), and</li>
 *   <li>build its store in {@code OnboardingServiceYamlBuilder}.</li>
 * </ol>
 */
@OwnedBy(HarnessTeam.CDC)
public enum ManifestProviderType {
  /** GitHub: needs a user-provided connector (auth + optional apiAccess) and a secret for its credential. */
  GITHUB(true),
  /** Bitbucket: needs a user-provided connector (username/password auth) and a secret for its credential. */
  BITBUCKET(true),
  /** GitLab: needs a user-provided connector (username/token, or OAuth + apiAccess referencing existing secrets). */
  GITLAB(true),
  /** Harness Code: the built-in Git provider. The connection is implicit, so no connector or secret is created. */
  HARNESS_CODE(false);

  private final boolean requiresConnector;

  ManifestProviderType(boolean requiresConnector) {
    this.requiresConnector = requiresConnector;
  }

  /** Whether onboarding must provision a connector (and its credential secret) for this provider. */
  public boolean requiresConnector() {
    return requiresConnector;
  }
}
