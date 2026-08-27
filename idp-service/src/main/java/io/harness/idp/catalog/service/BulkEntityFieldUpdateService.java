/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.BulkEntityFieldUpdateRequest;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateOperationResponse;
import io.harness.spec.server.idp.v1.model.BulkFieldUpdateSubmitResponse;

@OwnedBy(HarnessTeam.IDP)
public interface BulkEntityFieldUpdateService {
  BulkFieldUpdateSubmitResponse submit(BulkEntityFieldUpdateRequest request, String harnessAccount);

  BulkFieldUpdateOperationResponse getOperation(String harnessAccount, String operationId);

  void execute(String operationId);
}
