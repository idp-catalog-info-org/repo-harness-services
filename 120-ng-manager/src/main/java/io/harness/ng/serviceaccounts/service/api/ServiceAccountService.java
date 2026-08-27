/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.service.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.accesscontrol.scopes.ScopeNameDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ServiceAccountFilterDTO;
import io.harness.ng.serviceaccounts.dto.ServiceAccountAggregateDTO;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.serviceaccount.ServiceAccountDTO;

import java.util.List;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public interface ServiceAccountService {
  ServiceAccountDTO createServiceAccount(ScopeInfo scopeInfo, ServiceAccountDTO requestDTO);
  List<ServiceAccount> listServiceAccounts(ScopeInfo scopeInfo, List<String> identifiers);

  List<ServiceAccount> listServiceAccountsByUniqueIds(String accountIdentifier, List<String> uniqueIds);

  ServiceAccountDTO updateServiceAccount(ScopeInfo scopeInfo, String identifier, ServiceAccountDTO requestDTO);

  boolean deleteServiceAccount(ScopeInfo scopeInfo, String identifier);
  void deleteBatch(ScopeInfo scopeInfo);

  List<ScopeNameDTO> getInheritingChildScopeList(ScopeInfo scopeInfo, String identifier);

  ServiceAccountDTO getServiceAccountDTO(ScopeInfo scopeInfo, String identifier);

  PageResponse<ServiceAccountAggregateDTO> listAggregateServiceAccounts(
      ScopeInfo scopeInfo, List<String> identifiers, Pageable pageable, ServiceAccountFilterDTO filterDTO);

  PageResponse<ServiceAccountDTO> listManageableServiceAccounts(
      ScopeInfo scopeInfo, Pageable pageable, ServiceAccountFilterDTO filterDTO);

  ServiceAccountAggregateDTO getServiceAccountAggregateDTO(ScopeInfo scopeInfo, String identifier);
  Long countServiceAccounts(String accountIdentifier);

  List<ServiceAccount> getPermittedServiceAccounts(
      List<ServiceAccount> serviceAccounts, ScopeInfo scopeInfo, String permission);
}
