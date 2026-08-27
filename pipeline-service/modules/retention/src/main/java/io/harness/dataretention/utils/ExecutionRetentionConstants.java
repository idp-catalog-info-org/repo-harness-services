/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.utils;

import static io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection.EXECUTION_GRAPH;
import static io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection.EXECUTION_METADATA;
import static io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;

import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@UtilityClass
public class ExecutionRetentionConstants {
  public static final String APPROVAL_INSTANCE_SUBTYPE_METADATA_KEY = "subType";
  public static final String ZST_DECOMPRESSED_SIZE_METADATA_KEY = "zstDecompressedSize";

  public static final List<ExecutionRetentionObjectStoreCollection> RECORDS_TO_FETCH_FROM_DB_AND_STORE_IN_OBJECT_STORE =
      Arrays.asList(EXECUTION_GRAPH, EXECUTION_METADATA);
  public static final List<ExecutionRetentionObjectStoreCollection>
      RECORDS_WITH_UUID_AS_PLAN_EXECUTION_ID_IN_OBJECT_STORE =
          Arrays.asList(EXECUTION_SUMMARY, EXECUTION_GRAPH, EXECUTION_METADATA);
}
