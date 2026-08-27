/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class DropAndRecreateParentUniqueIdServiceIdIndexOnServiceInfraInfo extends NGAbstractTimeScaleMigration {
  private static final String FILE_NAME =
      "timescale/drop_and_recreate_parent_unique_id_serviceid_index_on_service_infra_info.sql";

  @Override
  public String getFileName() {
    return FILE_NAME;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
