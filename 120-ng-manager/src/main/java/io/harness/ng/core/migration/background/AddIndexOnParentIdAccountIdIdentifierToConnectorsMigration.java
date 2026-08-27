/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class AddIndexOnParentIdAccountIdIdentifierToConnectorsMigration extends NGAbstractTimeScaleMigration {
  private static final String ADD_INDEX_ON_PARENT_UNIQUE_ID_ACCOUNT_ID_IDENTIFIER_TO_CONNECTORS_FILE_NAME =
      "timescale/add_index_on_parent_id_account_id_identifier_to_connectors.sql";

  @Override
  public String getFileName() {
    return ADD_INDEX_ON_PARENT_UNIQUE_ID_ACCOUNT_ID_IDENTIFIER_TO_CONNECTORS_FILE_NAME;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
