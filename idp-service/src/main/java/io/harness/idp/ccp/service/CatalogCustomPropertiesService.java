/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityGetResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldDeleteResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldGetResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyFilterDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyFilterRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyResponse;
import io.harness.spec.server.idp.v1.model.EntityRefs;

import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public interface CatalogCustomPropertiesService {
  CustomPropertyByFieldResponse resolveEntitiesAndUpsertCustomProperties(
      CustomPropertyFilterRequest request, String accountIdentifier, boolean dryRun);

  CustomPropertyByFieldDeleteResponse deleteCustomProperties(
      CustomPropertyFilterDeleteRequest request, String harnessAccount, boolean dryRun);

  CustomPropertyByEntityGetResponse getCustomPropertiesForEntity(String accountIdentifier, String entityRef);

  CustomPropertyResponse resolveCustomPropertiesForEntity(
      CustomPropertyByEntityRequest request, String accountIdentifier, boolean dryRun);

  CustomPropertyResponse deleteCustomPropertiesForEntity(
      CustomPropertyByEntityDeleteRequest request, String accountIdentifier, boolean dryRun);

  CustomPropertyByFieldGetResponse getCustomPropertiesForCustomProperty(String accountIdentifier, String property);

  CustomPropertyResponse resolveEntitiesForCustomProperty(
      CustomPropertyByFieldRequest request, String accountIdentifier, boolean dryRun);

  CustomPropertyResponse deleteEntitiesForCustomProperty(
      CustomPropertyByFieldDeleteRequest request, String accountIdentifier, boolean dryRun);

  void toggleCustomProperties(String harnessAccount, Boolean enabled);
  EntityRefs fetchEntityRefs(String accountIdentifier, String searchTerm);
  void modifyEntityIdentifierForIdpV2(String accountIdentifier, Set<String> conflictedEntityRefs);
  void modifyScopeForEntityRef(String accountIdentifier, String existingEntityRef, String modifiedEntityRef);
}
