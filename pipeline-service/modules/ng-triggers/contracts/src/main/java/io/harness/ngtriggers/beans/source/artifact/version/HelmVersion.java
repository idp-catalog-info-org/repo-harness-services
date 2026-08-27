/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.artifact.version;

public enum HelmVersion {
  V2,
  V3,
  V380,
  V4;

  public static boolean isHelmV3(HelmVersion helmVersion) {
    return V3.equals(helmVersion) || V380.equals(helmVersion);
  }
  public static boolean isHelmV4(HelmVersion helmVersion) {
    return V4.equals(helmVersion);
  }
  public static HelmVersion fromString(String helmVersion) {
    if (helmVersion == null) {
      return V2;
    }
    switch (helmVersion) {
      case "V3":
        return V3;
      case "V380":
        return V380;
      case "V4":
        return V4;
      default:
        return V2;
    }
  }
}
