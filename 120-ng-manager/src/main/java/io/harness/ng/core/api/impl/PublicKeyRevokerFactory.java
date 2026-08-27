/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.api.PublicKeyRevoker;
import io.harness.ng.core.common.beans.RevocationReason;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Factory for obtaining the appropriate PublicKeyRevoker based on revocation reason.
 * Uses a registry pattern to allow extensibility for different revocation types.
 */
@Singleton
@OwnedBy(PL)
public class PublicKeyRevokerFactory {
  private final Set<PublicKeyRevoker> revokers;

  @Inject
  public PublicKeyRevokerFactory(Set<PublicKeyRevoker> revokers) {
    this.revokers = revokers;
  }

  /**
   * Gets all revokers that handle the given revocation reason.
   *
   * @param reason the revocation reason
   * @return List of revokers that handle this reason, may be empty
   */
  public List<PublicKeyRevoker> getRevokers(RevocationReason reason) {
    return revokers.stream().filter(revoker -> revoker.handles(reason)).collect(Collectors.toList());
  }
}
