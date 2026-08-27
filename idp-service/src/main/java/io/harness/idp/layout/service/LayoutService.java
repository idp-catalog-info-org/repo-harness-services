/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.layout.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.LayoutIngestRequest;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import javax.ws.rs.core.Response;

@OwnedBy(HarnessTeam.IDP)
public interface LayoutService {
  Response create(String harnessAccount, LayoutRequest layoutRequest);
  Response delete(String harnessAccount, LayoutRequest layoutRequest);
  Response get(String harnessAccount, String name);
  Response get(String harnessAccount);
  Response ingest(String harnessAccount, LayoutIngestRequest layoutIngestRequest);
  Response getV4(String harnessAccount);
}
