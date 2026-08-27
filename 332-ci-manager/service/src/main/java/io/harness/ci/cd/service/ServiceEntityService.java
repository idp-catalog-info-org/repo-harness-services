/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.app.beans.entities.ServiceEntity;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CI)
public interface ServiceEntityService {
  ServiceEntity create(ServiceEntity serviceEntity);
  Optional<ServiceEntity> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier);

  Optional<ServiceEntity> get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, boolean loadFromFallbackBranch);

  Optional<ServiceEntity> getMetadata(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier);

  ServiceEntity update(ServiceEntity requestService);

  ServiceEntity upsert(ServiceEntity requestService);

  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier);
  Page<ServiceEntity> list(Criteria criteria, Pageable pageable);

  Page<ServiceEntity> list(Query query, Pageable pageable);
  Page<ServiceEntity> list(String accountId, String orgIdentifier, String projectIdentifier, String searchTerm,
      boolean includeChildrenScope, String permission, int page, int size);
}
