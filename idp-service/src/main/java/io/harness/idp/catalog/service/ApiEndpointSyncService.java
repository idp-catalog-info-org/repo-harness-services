/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.processor.api.ApiEndpointSyncFailedException;
import io.harness.idp.catalog.processor.api.ApiEndpointSyncInProgressException;
import io.harness.spec.server.idp.v1.model.ApiEndpointSyncResponse;

/**
 * Synchronously re-processes a single API entity: live-fetches its OpenAPI spec from source
 * (Git placeholder / URL / inline), re-extracts endpoints, and refreshes {@code metadata.apis}.
 * Runs under the caller's ambient request principal (unlike {@code ApiEndpointRefreshHandler},
 * which runs under a service principal).
 */
@OwnedBy(HarnessTeam.IDP)
public interface ApiEndpointSyncService {
  /**
   * @throws io.harness.exception.InvalidRequestException FF disabled for the account, or {@code
   *     kind} is not {@code api} (maps to 400)
   * @throws io.harness.exception.EntityNotFoundException entity does not exist (maps to 404)
   * @throws io.harness.accesscontrol.NGAccessDeniedException caller lacks edit permission (maps
   *     to 403)
   * @throws ApiEndpointSyncInProgressException the per-entity extraction lock is held by a
   *     concurrent sync/iterator run (maps to 409)
   * @throws ApiEndpointSyncFailedException the live spec fetch or parse failed (maps to 500)
   */
  ApiEndpointSyncResponse sync(String account, String org, String project, String kind, String identifier);
}
