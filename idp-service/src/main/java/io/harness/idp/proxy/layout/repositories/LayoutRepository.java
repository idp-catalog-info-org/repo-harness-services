/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.proxy.layout.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.proxy.layout.beans.entity.LayoutEntity;

import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface LayoutRepository extends CrudRepository<LayoutEntity, String>, LayoutRepositoryCustom {
  LayoutEntity findByAccountIdentifierAndNameAndType(String accountIdentifier, String name, String type);
  void delete(LayoutEntity layoutEntity);
}
