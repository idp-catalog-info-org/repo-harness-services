/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.service;

import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.NamespaceRequest;

import java.util.List;
import java.util.Optional;

public interface NamespaceService {
  NamespaceInfo getNamespaceForAccountIdentifier(String accountIdentifier);
  NamespaceInfo getAccountIdForNamespace(String namespace);
  NamespaceEntity saveAccountIdNamespace(String accountIdentifier);
  NamespaceInfo update(String accountIdentifier, NamespaceRequest namespaceRequest);
  List<String> getAccountIds();
  Boolean getAccountIdpStatus(String accountIdentifier);

  NamespaceEntity createDevSpaceEnvDefaultMappingEntry(String accountIdentifier, String namespace);
  List<NamespaceEntity> getActiveAccounts();
  Optional<NamespaceEntity> getEntityForAccountIdentifier(String accountIdentifier);

  void save(NamespaceEntity namespaceEntity);

  void updateIdpV2MigrationInfoAndSave(NamespaceEntity namespaceEntity, boolean idpV2FFState);
}
