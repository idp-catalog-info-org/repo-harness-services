/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.catalog;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.integrations.beans.catalog.CatalogIntegrationSyncRequest;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.spec.server.idp.v1.model.CatalogIntegrationRequest;

import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public abstract sealed class CatalogIntegrationOps<S extends IntegrationEntity, T extends CatalogIntegrationRequest, U
                                                       extends CatalogIntegrationSyncRequest> permits
    HarnessCDIntegrationOpsImpl {
  abstract S prepare(String accountIdentifier, T catalogIntegrationRequest);
  abstract U prepareCatalogIntegrationSyncRequest(S catalogIntegrationEntity);
  abstract CompletableFuture<Void> performSyncInBackground(U catalogIntegrationSyncRequest);
  abstract void performSync(U catalogIntegrationSyncRequest);
  abstract CompletableFuture<Void> performCompleteSyncInBackground(U catalogIntegrationSyncRequest);
  abstract void performCompleteSync(U catalogIntegrationSyncRequest);
  abstract void performIncrementalSync(U catalogIntegrationSyncRequest);
  abstract Object transform(Object rawEntity);
  abstract Object transform(Object rawEntity, Object existingTransformedEntity);
}
