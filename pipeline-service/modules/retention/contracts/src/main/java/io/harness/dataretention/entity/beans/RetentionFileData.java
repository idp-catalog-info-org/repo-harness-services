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

import java.util.Map;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;

/*
 * This DTO is saves the metadata per object stored in the object stored, like file size/path/type etc.
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@Value
@Builder
@FieldNameConstants(innerTypeName = "RetentionFileDataKeys")
@OwnedBy(HarnessTeam.PIPELINE)
public class RetentionFileData {
  // This uuid is the unique id of the collection being stored
  // For e.g. it's approval instance id for approval instances, planExecutionId for summary entity
  // This will be used in the get calls like approval instances are fetched via approval instance id
  // so an index is created on the same
  String uuid;

  @NonFinal @Setter ExecutionRetentionObjectStoreCollection collection;
  @Deprecated String collectionName;
  @Deprecated String fileName;
  String filePath;
  Long fileSize;
  RetentionFileFormat fileFormat;

  // The below metadata object stores the metadata of the file
  // For approval instance it stores subtype
  // For compressed objects it will store original size(int) so the value is Object
  Map<String, Object> metadata;
}
