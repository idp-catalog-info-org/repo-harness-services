/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.serviceaccount.ServiceAccountDTOInternal;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class ServiceAccountDTOMapper {
  public ServiceAccountDTO getDTOFromServiceAccount(ServiceAccount serviceAccount, ScopeInfo scopeInfo) {
    return ServiceAccountDTO.builder()
        .identifier(serviceAccount.getIdentifier())
        .uniqueId(serviceAccount.getUniqueId())
        .parentUniqueId(scopeInfo.getUniqueId())
        .name(serviceAccount.getName())
        .email(serviceAccount.getEmail())
        .description(serviceAccount.getDescription())
        .tags(TagMapper.convertToMap(serviceAccount.getTags()))
        .accountIdentifier(scopeInfo.getAccountIdentifier())
        .orgIdentifier(scopeInfo.getOrgIdentifier())
        .projectIdentifier(scopeInfo.getProjectIdentifier())
        .build();
  }

  public ServiceAccountDTOInternal getServiceAccountDTOInternalFromServiceAccount(
      ServiceAccount serviceAccount, ScopeInfo scopeInfo) {
    return ServiceAccountDTOInternal.builder()
        .identifier(serviceAccount.getIdentifier())
        .uniqueIdInternal(serviceAccount.getUniqueId())
        .parentUniqueIdInternal(serviceAccount.getParentUniqueId())
        .uniqueId(serviceAccount.getUniqueId())
        .parentUniqueId(serviceAccount.getParentUniqueId())
        .name(serviceAccount.getName())
        .email(serviceAccount.getEmail())
        .description(serviceAccount.getDescription())
        .tags(TagMapper.convertToMap(serviceAccount.getTags()))
        .accountIdentifier(serviceAccount.getAccountIdentifier())
        .orgIdentifier(scopeInfo.getOrgIdentifier())
        .projectIdentifier(scopeInfo.getProjectIdentifier())
        .build();
  }

  public ServiceAccountDTOInternal getDTOFromServiceAccountInternal(ServiceAccountDTO serviceAccountDTO) {
    return (ServiceAccountDTOInternal) ServiceAccountDTOInternal.builder()
        .uniqueIdInternal(serviceAccountDTO.getUniqueId())
        .parentUniqueIdInternal(serviceAccountDTO.getParentUniqueId())
        .uniqueId(serviceAccountDTO.getUniqueId())
        .parentUniqueId(serviceAccountDTO.getParentUniqueId())
        .accountIdentifier(serviceAccountDTO.getAccountIdentifier())
        .name(serviceAccountDTO.getName())
        .identifier(serviceAccountDTO.getIdentifier())
        .orgIdentifier(serviceAccountDTO.getOrgIdentifier())
        .projectIdentifier(serviceAccountDTO.getProjectIdentifier())
        .email(serviceAccountDTO.getEmail())
        .tags(serviceAccountDTO.getTags())
        .description(serviceAccountDTO.getDescription())
        .build();
  }

  public ServiceAccount getServiceAccountFromDTO(ServiceAccountDTO serviceAccountDTO, ScopeInfo scopeInfo) {
    return ServiceAccount.builder()
        .accountIdentifier(scopeInfo.getAccountIdentifier())
        .uniqueId(serviceAccountDTO.getUniqueId())
        .parentUniqueId(scopeInfo.getUniqueId())
        .orgIdentifier(scopeInfo.getOrgIdentifier())
        .projectIdentifier(scopeInfo.getProjectIdentifier())
        .name(serviceAccountDTO.getName())
        .identifier(serviceAccountDTO.getIdentifier())
        .description(serviceAccountDTO.getDescription())
        .email(serviceAccountDTO.getEmail().toLowerCase())
        .tags(TagMapper.convertToList(serviceAccountDTO.getTags()))
        .build();
  }
}
