/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils.configfile;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.unified.cd.service.manifests.StoreType;

import java.util.Map;
import lombok.experimental.UtilityClass;

/** Builds {@link FileStoreSpec} from unified {@code ConfigFile#getInputs()}. */
@UtilityClass
@OwnedBy(HarnessTeam.CI)
public class ResolvedConfigFileStoreSpecFactory {
  /** Same as {@code io.harness.ng.core.service.inputsmapper.ManifestInputsConstants#STORE_TYPE}. */
  public static final String STORE_TYPE_KEY = "storeType";

  public static FileStoreSpec fromInputs(Map<String, Object> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      throw new InvalidRequestException("Config file inputs are required");
    }
    StoreType storeType = parseStoreType(inputs);
    if (StoreType.HARNESS.equals(storeType)) {
      return HarnessFileStoreSpec.fromInputs(inputs);
    }
    if (isGitStoreType(storeType)) {
      return GitFileStoreSpec.fromInputs(inputs, storeType);
    }
    throw new InvalidRequestException("Unsupported config file store type: " + storeType);
  }

  public static StoreType parseStoreType(Map<String, Object> map) {
    Object uses = map.get("uses");
    if (uses == null) {
      uses = map.get(STORE_TYPE_KEY);
    }
    if (uses == null) {
      throw new InvalidRequestException("Config file store type (uses / storeType) is required");
    }
    String display = uses.toString().trim();
    for (StoreType t : StoreType.values()) {
      if (t.getDisplayName().equalsIgnoreCase(display)) {
        return t;
      }
    }
    throw new InvalidRequestException("Unknown config file store type: " + display);
  }

  public static boolean isGitStoreType(StoreType storeType) {
    return switch (storeType) {
      case GITHUB, GIT, GITLAB, BITBUCKET, CODE, AZURE -> true;
      default -> false;
    };
  }
}
