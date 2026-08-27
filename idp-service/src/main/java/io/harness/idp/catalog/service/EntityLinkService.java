/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.EntityLinkExistsResponse;
import io.harness.spec.server.idp.v1.model.EntityLinkRequest;
import io.harness.spec.server.idp.v1.model.EntityLinkResponse;
import io.harness.spec.server.idp.v1.model.ResolveFieldMappingsRequest;
import io.harness.spec.server.idp.v1.model.ResolveFieldMappingsResponse;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public interface EntityLinkService {
  EntityLinkResponse createLink(String accountIdentifier, EntityLinkRequest request);

  EntityLinkResponse updateLink(String accountIdentifier, String entityRef, EntityLinkRequest request);

  void deleteLink(String accountIdentifier, String entityRef);

  EntityLinkResponse getLink(String accountIdentifier, String entityRef);

  EntityLinkExistsResponse linkExists(String accountIdentifier, String entityRef);

  List<String> getLinkedEntities(String accountIdentifier, String entityKind, String entityType, String entityRef);

  ResolveFieldMappingsResponse resolveFieldMappings(
      String accountIdentifier, String scope, String kind, String identifier, ResolveFieldMappingsRequest request);

  List<String> getEntityLinksByIntegration(
      String accountIdentifier, String integrationIdentifier, String orgIdentifier, String projectIdentifier);

  void deleteLinksForIntegration(String accountIdentifier, String integrationIdentifier, String spacePath);
}
