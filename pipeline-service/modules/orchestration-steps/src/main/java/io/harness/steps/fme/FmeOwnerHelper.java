/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.split.client.dtos.URN;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parses owner strings in "type:id" format (e.g. "User:abc-123", "Group:xyz-456") into URN objects.
 * The type prefix comes from the BFF API response and is normalized to the external types
 * that the Main backend's TypeMapper understands:
 *   "User"  / "user"  -> "User"
 *   "Group" / "group" -> "group"  (TypeMapper maps "group" -> internal "Team")
 * Bare IDs without a prefix default to type "User" for backward compatibility.
 */
@OwnedBy(HarnessTeam.FME)
public final class FmeOwnerHelper {
  private FmeOwnerHelper() {}

  private static final Map<String, String> TYPE_NORMALIZATION =
      Map.of("group", "group", "Group", "group", "user", "User", "User", "User");

  public static List<URN> parseOwners(List<String> owners) {
    return owners.stream().map(FmeOwnerHelper::parseOwner).collect(Collectors.toList());
  }

  static URN parseOwner(String ownerString) {
    if (ownerString == null || ownerString.isEmpty()) {
      throw new IllegalArgumentException("Owner string cannot be null or empty");
    }
    URN urn = new URN();
    int colonIdx = ownerString.indexOf(':');
    if (colonIdx > 0 && colonIdx < ownerString.length() - 1) {
      String prefix = ownerString.substring(0, colonIdx);
      String id = ownerString.substring(colonIdx + 1);
      urn.type = TYPE_NORMALIZATION.getOrDefault(prefix, prefix);
      urn.id = id;
    } else {
      urn.type = "User";
      urn.id = ownerString;
    }
    return urn;
  }
}
