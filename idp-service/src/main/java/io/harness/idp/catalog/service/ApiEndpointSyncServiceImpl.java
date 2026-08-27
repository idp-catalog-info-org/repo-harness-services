/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.idp.catalog.utils.Constants.API_KIND;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.processor.ApiSpecGitRefresher;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor.ProcessingOutcome;
import io.harness.idp.catalog.processor.api.ApiEndpointSyncFailedException;
import io.harness.idp.catalog.processor.api.ApiEndpointSyncInProgressException;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.spec.server.idp.v1.model.ApiEndpointSyncResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;

/**
 * Synchronous "sync now" flow for a single API entity: live-fetches its spec (under the caller's
 * ambient request principal, unlike the iterator's service-principal path) and re-extracts
 * endpoints via the shared {@link ApiEndpointProcessor}.
 */
@Singleton
@OwnedBy(HarnessTeam.IDP)
public class ApiEndpointSyncServiceImpl implements ApiEndpointSyncService {
  private final CatalogServiceHelper catalogServiceHelper;
  private final IdpCommonService idpCommonService;
  private final ApiSpecGitRefresher apiSpecGitRefresher;
  private final ApiEndpointProcessor apiEndpointProcessor;

  @Inject
  public ApiEndpointSyncServiceImpl(CatalogServiceHelper catalogServiceHelper, IdpCommonService idpCommonService,
      ApiSpecGitRefresher apiSpecGitRefresher, ApiEndpointProcessor apiEndpointProcessor) {
    this.catalogServiceHelper = catalogServiceHelper;
    this.idpCommonService = idpCommonService;
    this.apiSpecGitRefresher = apiSpecGitRefresher;
    this.apiEndpointProcessor = apiEndpointProcessor;
  }

  @Override
  public ApiEndpointSyncResponse sync(String account, String org, String project, String kind, String identifier) {
    if (!idpCommonService.idpApiEndpointExtractionEnabled(account)) {
      throw new InvalidRequestException("IDP_API_ENDPOINT_EXTRACTION is not enabled for this account");
    }
    if (!API_KIND.equalsIgnoreCase(kind)) {
      throw new InvalidRequestException("sync-api-endpoints is only supported for API entities");
    }

    catalogServiceHelper.checkCrudRbac(
        account, org, project, kind, CatalogUtils.entityRef(kind, org, project, identifier), "edit");

    // TODO: entity resolution here does not resolve Git-backed (GitReferencedCatalogEntity)
    CatalogEntity entity = catalogServiceHelper.catalogEntity(account, org, project, kind, identifier);
    if (entity == null) {
      throw new EntityNotFoundException("Entity with entityRef = " + kind + ":" + identifier + " not found");
    }

    try {
      apiSpecGitRefresher.refresh(entity, true);
    } catch (Exception ex) {
      throw new ApiEndpointSyncFailedException("Failed to fetch API spec from source: " + ex.getMessage(), ex);
    }

    ProcessingOutcome outcome = apiEndpointProcessor.processEntity(entity);
    return toResponse(outcome);
  }

  private static ApiEndpointSyncResponse toResponse(ProcessingOutcome outcome) {
    switch (outcome.getStatus()) {
      case LOCK_SKIPPED:
        throw new ApiEndpointSyncInProgressException("Endpoint extraction is already in progress for this entity");
      case FAILED:
        throw new ApiEndpointSyncFailedException(outcome.getErrorMessage());
      case HASH_SKIPPED:
        // Spec unchanged: keep the persisted warnings so a previously-degraded entity still reports
        // its partial state even though nothing was re-extracted.
        return buildResponse(false, outcome.getOldKeys().size(), new ArrayList<>(outcome.getWarnings()));
      case PARTIAL:
      case SUCCESS:
      default:
        // A degraded (PARTIAL) extraction still wrote metadata.apis, so it is reported as a
        // successful sync; the degradation is surfaced to the caller via warnings.
        return buildResponse(true, outcome.getNewKeys().size(), new ArrayList<>(outcome.getWarnings()));
    }
  }

  private static ApiEndpointSyncResponse buildResponse(
      boolean changed, int endpointsExtracted, ArrayList<String> warnings) {
    ApiEndpointSyncResponse response = new ApiEndpointSyncResponse();
    response.setChanged(changed);
    response.setEndpointsExtracted(endpointsExtracted);
    response.setWarnings(warnings);
    return response;
  }
}
