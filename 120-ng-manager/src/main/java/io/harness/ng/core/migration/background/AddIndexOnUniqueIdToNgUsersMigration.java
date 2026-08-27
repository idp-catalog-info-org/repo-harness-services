/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class AddIndexOnUniqueIdToNgUsersMigration extends NGAbstractTimeScaleMigration {
  private static final String ADD_INDEX_ON_UNIQUE_ID_TO_NG_USERS_FILE_NAME =
      "timescale/add_index_on_unique_id_to_ng_users.sql";

  @Override
  public String getFileName() {
    return ADD_INDEX_ON_UNIQUE_ID_TO_NG_USERS_FILE_NAME;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
