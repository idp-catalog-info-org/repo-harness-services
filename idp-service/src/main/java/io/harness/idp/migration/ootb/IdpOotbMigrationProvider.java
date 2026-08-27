/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration.ootb;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.MigrationDetails;
import io.harness.migration.entities.NGSchema;
import io.harness.migration.ng.MigrationProvider;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public class IdpOotbMigrationProvider implements MigrationProvider {
  @Override
  public String getServiceName() {
    return "idpOotb";
  }

  @Override
  public Class<? extends NGSchema> getSchemaClass() {
    return IdpOotbMigrationSchema.class;
  }

  @Override
  public List<Class<? extends MigrationDetails>> getMigrationDetailsList() {
    List<Class<? extends MigrationDetails>> details = new ArrayList<>();
    details.add(IdpOotbBGMigrationDetails.class);
    return details;
  }
}
