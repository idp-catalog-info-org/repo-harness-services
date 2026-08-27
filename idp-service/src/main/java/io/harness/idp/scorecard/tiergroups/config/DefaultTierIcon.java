/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.config;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.IDP)
@Getter
public enum DefaultTierIcon {
  BRONZE("https://static.harness.io/ng-static/images/idp/scorecard/tier-bronze.svg"),
  SILVER("https://static.harness.io/ng-static/images/idp/scorecard/tier-silver.svg"),
  GOLD("https://static.harness.io/ng-static/images/idp/scorecard/tier-gold.svg");

  private final String displayUrl;

  DefaultTierIcon(String displayUrl) {
    this.displayUrl = displayUrl;
  }

  public static Optional<DefaultTierIcon> fromStoredValue(String value) {
    if (StringUtils.isBlank(value)) {
      return Optional.empty();
    }
    String normalized = value.trim();
    return Arrays.stream(values()).filter(icon -> icon.name().equalsIgnoreCase(normalized)).findFirst();
  }

  public static String resolveForDisplay(String storedIcon) {
    return fromStoredValue(storedIcon).map(DefaultTierIcon::getDisplayUrl).orElse(storedIcon);
  }

  public static String validIconNamesForMessage() {
    return String.join(", ", Arrays.stream(values()).map(DefaultTierIcon::name).toList());
  }
}
