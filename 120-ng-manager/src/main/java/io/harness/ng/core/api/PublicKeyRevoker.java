/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.common.beans.RevocationReason;
import io.harness.ng.core.entities.Token;

/**
 * Interface for public key (SSH/PGP) revocation operations.
 * Implementations handle notifying external services when keys are revoked.
 */
@OwnedBy(PL)
public interface PublicKeyRevoker {
  /**
   * Checks if this revoker handles the given revocation reason.
   *
   * @param reason the revocation reason
   * @return true if this revoker handles the reason
   */
  boolean handles(RevocationReason reason);

  /**
   * Revokes the public key by notifying external services.
   *
   * @param scopeInfo the scope information
   * @param token the token/key to revoke
   */
  void revoke(ScopeInfo scopeInfo, Token token);
}
