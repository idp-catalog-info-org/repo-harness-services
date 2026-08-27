/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.entity.beans;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

/*
 * This enum denotes the type of file stored in object store like json or ZST which is used to serialize/de-serialize
 * the files on store/read from object store
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
public enum RetentionFileFormat {
  JSON("JSON", null, "json"),
  JSON_ZSTD("JSON_ZSTD", JSON, "zst");

  private final String name;
  private final String fileExtension;
  private final RetentionFileFormat originalFileFormat;

  RetentionFileFormat(String name, RetentionFileFormat originalFileFormat, String fileExtension) {
    this.name = name;
    this.fileExtension = fileExtension;
    this.originalFileFormat = originalFileFormat;
  }

  public String getName() {
    return name;
  }

  public String getFileExtension() {
    if (originalFileFormat != null) {
      return String.format("%s.%s", originalFileFormat.getFileExtension(), fileExtension);
    }
    return fileExtension;
  }

  public RetentionFileFormat getOriginalFileFormat() {
    return originalFileFormat;
  }
}
