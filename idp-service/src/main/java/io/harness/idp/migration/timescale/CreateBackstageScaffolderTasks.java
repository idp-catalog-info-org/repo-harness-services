/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration.timescale;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

@OwnedBy(HarnessTeam.IDP)
public class CreateBackstageScaffolderTasks extends NGAbstractTimeScaleMigration {
  @Override
  public String getFileName() {
    return "timescale/2_create_backstage_scaffolder_tasks_timescale.sql";
  }
}
