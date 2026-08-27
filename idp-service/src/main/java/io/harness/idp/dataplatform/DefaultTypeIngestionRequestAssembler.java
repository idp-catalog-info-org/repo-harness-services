/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.platform.type.ingestion.TypeIngestionRequest;
import io.harness.platform.type.registry.model.v1.UpgradePack;

@OwnedBy(HarnessTeam.IDP)
public class DefaultTypeIngestionRequestAssembler implements TypeIngestionRequestAssembler {
  @Override
  public TypeIngestionRequest toRequest(String tenantId, UpgradePack upgradePack) {
    return TypeIngestionRequest.newBuilder().setTenantId(tenantId).setUpgradePack(upgradePack).build();
  }
}
