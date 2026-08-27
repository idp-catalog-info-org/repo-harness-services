/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InternalServerErrorException;
import io.harness.migration.beans.NGMigration;
import io.harness.migration.ng.ObjectStoreNotAvailableException;
import io.harness.objectstore.ObjectStoreClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import javax.annotation.Nullable;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@NoArgsConstructor
@Slf4j
public class PipelineDataRetentionCreateObjectStoreBucket implements NGMigration {
  @Nullable @Inject @Named("DataRetentionObjectStoreClient") private ObjectStoreClient objectStoreClient;

  @Override
  public void migrate() {
    if (objectStoreClient != null) {
      try {
        objectStoreClient.createBucket();
      } catch (Exception ex) {
        throw new InternalServerErrorException("Unable to create bucket for data retention in object store", ex);
      }
    } else {
      throw new ObjectStoreNotAvailableException(
          String.format("[Migration]: Migration %s failed - OBJECTSTORE NOT AVAILABLE", getClass()));
    }
  }
}
