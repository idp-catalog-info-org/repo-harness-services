/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class TierGroupConstants {
  public static final String DEFAULT_TIER_GROUP_IDENTIFIER = "default_tiers";
  public static final int MIN_TIERS = 2;
  public static final int MAX_TIERS = 20;
  public static final int TIER_GROUP_DESCRIPTION_MAX_LENGTH = 2048;
  public static final int MIN_SCORE = 0;
  public static final int MAX_SCORE = 100;
  public static final long TIER_GROUP_LOCK_TIMEOUT_MINUTES = 1L;
  public static final long TIER_GROUP_LOCK_WAIT_TIMEOUT_SECONDS = 30L;
  public static final String TIER_GROUP_LOCK_FORMAT = "idp:tierGroup:%s:%s";

  public static String normalizeIdentifier(String identifier) {
    return identifier == null ? null : identifier.trim();
  }
}
