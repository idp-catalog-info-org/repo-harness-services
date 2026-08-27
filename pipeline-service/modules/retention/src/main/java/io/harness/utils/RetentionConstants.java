/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@UtilityClass
public class RetentionConstants {
  public static final String RETENTION_ITERATOR_DELAY_METRIC_NAME = "execution_retention_iterator_lag";
  public static final String RETENTION_SYNC_ACCOUNT_ID_METRIC_LABEL_KEY = "accountIdentifier";
  public static final String RETENTION_SYNC_METHOD_METRIC_LABEL_KEY = "syncMethod";
  public static final String RETENTION_SYNC_ENTITY_METRIC_LABEL_KEY = "syncEntity";
}
