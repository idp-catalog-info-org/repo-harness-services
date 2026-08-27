/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class DiffUtils {
  // ignores duplicates
  public static boolean isCollectionUpdated(Collection<String> oldCollection, Collection<String> newCollection) {
    if (oldCollection == null && newCollection == null) {
      return false;
    }
    if (oldCollection == null || newCollection == null) {
      return true;
    }

    Set<String> removed = new HashSet<>(oldCollection);
    newCollection.forEach(removed::remove);

    Set<String> added = new HashSet<>(newCollection);
    oldCollection.forEach(added::remove);

    return !removed.isEmpty() || !added.isEmpty();
  }
}
