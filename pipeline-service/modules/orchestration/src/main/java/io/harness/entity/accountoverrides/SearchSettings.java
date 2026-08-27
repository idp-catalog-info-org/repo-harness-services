/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entity.accountoverrides;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Value
@Builder
@FieldNameConstants(innerTypeName = "SearchSettingsKeys")
@OwnedBy(HarnessTeam.PIPELINE)
public class SearchSettings {
  PipelineSearchMigrationStatus indexMigrationStatus;
  String oldIndexName;
  String newIndexName;

  public String getOldIndexName() {
    if (oldIndexName == null) {
      return PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ALIAS_6_MONTH;
    }
    return oldIndexName;
  }
}
