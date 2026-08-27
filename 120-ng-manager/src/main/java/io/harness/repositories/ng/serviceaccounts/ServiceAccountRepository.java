/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.ng.serviceaccounts;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
@OwnedBy(PL)
public interface ServiceAccountRepository
    extends PagingAndSortingRepository<ServiceAccount, String>, CrudRepository<ServiceAccount, String>,
            ServiceAccountCustomRepository {
  List<ServiceAccount> findAllByAccountIdentifierAndParentUniqueId(String accountIdentifier, String parentUniqueId);
  List<ServiceAccount> findAllByAccountIdentifierAndParentUniqueIdAndIdentifierIsIn(
      String accountIdentifier, String parentUniqueId, List<String> identifiers);
  long deleteByAccountIdentifierAndParentUniqueIdAndIdentifier(
      String accountIdentifier, String parentUniqueId, String identifier);
  ServiceAccount findByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);

  ServiceAccount findByAccountIdentifierAndParentUniqueIdAndIdentifier(
      String accountIdentifier, String parentUniqueId, String identifier);
  Long countByAccountIdentifier(String accountIdentifier);

  List<ServiceAccount> findAllByAccountIdentifierAndUniqueIdIsIn(String accountIdentifier, List<String> uniqueIds);
}
