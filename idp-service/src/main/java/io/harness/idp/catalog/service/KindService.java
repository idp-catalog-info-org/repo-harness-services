/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.beans.KindRequestDTO;
import io.harness.idp.catalog.beans.KindResponseDTO;
import io.harness.spec.server.idp.v1.model.KindResponseBody;
import io.harness.spec.server.idp.v1.model.KindSchemaResponseBody;

@OwnedBy(HarnessTeam.IDP)
public interface KindService {
  KindResponseBody save(String accountIdentifier, KindRequestDTO kindRequestDTO);
  KindResponseBody update(String accountIdentifier, String identifier, KindRequestDTO kindRequestDTO);
  void delete(String accountIdentifier, String identifier);
  void processKindDelete(String accountIdentifier, String identifier);
  KindResponseBody get(String accountIdentifier, String identifier, Boolean custom);
  KindSchemaResponseBody getSchema();
  KindResponseDTO get(
      String accountIdentifier, int pageIndex, int pageLimit, String sort, String searchTerm, Boolean custom);
  void validateSchema(String schema);
}
