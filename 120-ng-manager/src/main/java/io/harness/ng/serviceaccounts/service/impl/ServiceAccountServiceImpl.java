/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.service.impl;

import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.enforcement.constants.FeatureRestrictionName.MULTIPLE_SERVICE_ACCOUNTS;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_SERVICEACCOUNT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.SERVICEACCOUNT;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.ng.core.utils.NGUtils.verifyValuesNotChanged;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.accesscontrol.scopes.ScopeNameDTO;
import io.harness.ng.accesscontrol.scopes.ScopeNameMapper;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.dto.EntityScopeInfo;
import io.harness.ng.core.dto.ServiceAccountFilterDTO;
import io.harness.ng.core.dto.ServiceAccountFilterType;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.events.ServiceAccountCreateEvent;
import io.harness.ng.core.events.ServiceAccountDeleteEvent;
import io.harness.ng.core.events.ServiceAccountUpdateEvent;
import io.harness.ng.core.role.dto.RoleAssignmentMetadataDTO;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.opa.entities.serviceaccount.ServiceAccountOpaService;
import io.harness.ng.serviceaccounts.dto.ServiceAccountAggregateDTO;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.ng.serviceaccounts.entities.ServiceAccount.ServiceAccountKeys;
import io.harness.ng.serviceaccounts.service.ServiceAccountDTOMapper;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.opaclient.model.OpaConstants;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.ng.serviceaccounts.ServiceAccountRepository;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.utils.PageUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(PL)
public class ServiceAccountServiceImpl implements ServiceAccountService {
  @Inject private ServiceAccountRepository serviceAccountRepository;
  @Inject private OutboxService outboxService;
  @Inject @Named("PRIVILEGED") private AccessControlAdminClient accessControlAdminClient;
  @Inject private AccessControlClient accessControlClient;
  @Inject private ApiKeyService apiKeyService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private TokenService tokenService;
  @Inject private ScopeNameMapper scopeNameMapper;

  @Inject private ProjectService projectService;
  @Inject private OrganizationService organizationService;

  @Inject private ServiceAccountOpaService serviceAccountOpaService;
  @Inject private ScopeInfoService scopeInfoService;

  @Override
  @FeatureRestrictionCheck(MULTIPLE_SERVICE_ACCOUNTS)
  public ServiceAccountDTO createServiceAccount(ScopeInfo scopeInfo, ServiceAccountDTO requestDTO) {
    validateCreateServiceAccountRequest(scopeInfo, requestDTO);
    ServiceAccountDTO opaValidationResponse = evaluateTokenForOPAPolicies(
        scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), requestDTO);
    if (opaValidationResponse.getGovernanceMetadata() != null
        && OpaConstants.OPA_STATUS_ERROR.equals(opaValidationResponse.getGovernanceMetadata().getStatus())) {
      return opaValidationResponse;
    }
    ServiceAccount serviceAccount = ServiceAccountDTOMapper.getServiceAccountFromDTO(requestDTO, scopeInfo);
    validate(serviceAccount);
    serviceAccount.setParentUniqueId(scopeInfo.getUniqueId());
    try {
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        ServiceAccount savedAccount = serviceAccountRepository.save(serviceAccount);
        ServiceAccountDTO savedDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(savedAccount, scopeInfo);
        outboxService.save(new ServiceAccountCreateEvent(savedDTO));
        savedDTO.setGovernanceMetadata(opaValidationResponse.getGovernanceMetadata());
        return savedDTO;
      }));
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format("A service account with identifier %s is already present or was deleted in scope",
              requestDTO.getIdentifier()),
          USER_SRE, ex);
    }
  }
  @Override
  public List<ScopeNameDTO> getInheritingChildScopeList(ScopeInfo scopeInfo, String identifier) {
    String accountIdentifier = scopeInfo.getAccountIdentifier();
    String orgIdentifier = scopeInfo.getOrgIdentifier();
    String projectIdentifier = scopeInfo.getProjectIdentifier();
    ServiceAccount serviceAccount = serviceAccountRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
        accountIdentifier, scopeInfo.getUniqueId(), identifier);
    if (serviceAccount == null) {
      throw new InvalidRequestException(String.format("Service account with identifier: %s doesn't exist", identifier));
    }
    PrincipalDTO principalDTO =
        PrincipalDTO.builder()
            .identifier(identifier)
            .type(SERVICE_ACCOUNT)
            .uniqueId(serviceAccount.getUniqueId())
            .scopeLevel(ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier).toString().toLowerCase())
            .build();
    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder().principalFilter(Collections.singleton(principalDTO)).build();
    List<RoleAssignmentResponseDTO> roleAssignmentsResponse =
        NGRestUtils.getResponse(accessControlAdminClient.getFilteredRoleAssignmentsIncludingChildScopes(
            accountIdentifier, orgIdentifier, projectIdentifier, roleAssignmentFilterDTO));

    ScopeDTO currentScopeDTO = ScopeDTO.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .build();
    return roleAssignmentsResponse.stream()
        .map(RoleAssignmentResponseDTO::getScope)
        .distinct()
        .filter(scopeDTO -> !scopeDTO.equals(currentScopeDTO))
        .map(scopeNameMapper::toScopeNameDTO)
        .collect(Collectors.toList());
  }

  private ServiceAccountDTO evaluateTokenForOPAPolicies(@AccountIdentifier String accountIdentifier,
      String orgIdentifier, String projectIdentifier, ServiceAccountDTO requestDTO) {
    GovernanceMetadata governanceMetadata =
        serviceAccountOpaService.evaluatePoliciesWithEntity(accountIdentifier, requestDTO, orgIdentifier,
            projectIdentifier, OpaConstants.OPA_EVALUATION_ACTION_SAVE, requestDTO.getIdentifier());
    ServiceAccountDTO emptySavedAccountDTO = ServiceAccountDTO.builder().build();
    emptySavedAccountDTO.setGovernanceMetadata(governanceMetadata);
    return emptySavedAccountDTO;
  }
  private void validateCreateServiceAccountRequest(ScopeInfo scopeInfo, ServiceAccountDTO requestDTO) {
    verifyValuesNotChanged(
        Lists.newArrayList(Pair.of(scopeInfo.getAccountIdentifier(), requestDTO.getAccountIdentifier()),
            Pair.of(scopeInfo.getOrgIdentifier(), requestDTO.getOrgIdentifier()),
            Pair.of(scopeInfo.getProjectIdentifier(), requestDTO.getProjectIdentifier())),
        true);
  }

  @Override
  public ServiceAccountDTO updateServiceAccount(ScopeInfo scopeInfo, String identifier, ServiceAccountDTO requestDTO) {
    ServiceAccount serviceAccount = serviceAccountRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), identifier);
    if (serviceAccount == null) {
      throw new InvalidRequestException(String.format("Service account with identifier: %s doesn't exist", identifier));
    }
    validateUpdateServiceAccountRequest(scopeInfo, identifier, requestDTO, serviceAccount);
    ServiceAccountDTO opaValidationResponse = evaluateTokenForOPAPolicies(
        scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), requestDTO);
    if (opaValidationResponse.getGovernanceMetadata() != null
        && OpaConstants.OPA_STATUS_ERROR.equals(opaValidationResponse.getGovernanceMetadata().getStatus())) {
      return opaValidationResponse;
    }

    ServiceAccountDTO oldDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount, scopeInfo);
    ServiceAccount newAccount = ServiceAccountDTOMapper.getServiceAccountFromDTO(requestDTO, scopeInfo);
    newAccount.setUuid(serviceAccount.getUuid());
    newAccount.setCreatedAt(serviceAccount.getCreatedAt());
    newAccount.setUniqueId(serviceAccount.getUniqueId());
    newAccount.setParentUniqueId(serviceAccount.getParentUniqueId());
    validate(newAccount);
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      ServiceAccount savedAccount = serviceAccountRepository.save(newAccount);
      ServiceAccountDTO savedDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(savedAccount, scopeInfo);
      outboxService.save(new ServiceAccountUpdateEvent(oldDTO, savedDTO));
      savedDTO.setGovernanceMetadata(opaValidationResponse.getGovernanceMetadata());
      return savedDTO;
    }));
  }

  private void validateUpdateServiceAccountRequest(
      ScopeInfo scopeInfo, String identifier, ServiceAccountDTO requestDTO, ServiceAccount serviceAccount) {
    verifyValuesNotChanged(
        Lists.newArrayList(Pair.of(scopeInfo.getAccountIdentifier(), requestDTO.getAccountIdentifier()),
            Pair.of(scopeInfo.getOrgIdentifier(), requestDTO.getOrgIdentifier()),
            Pair.of(scopeInfo.getProjectIdentifier(), requestDTO.getProjectIdentifier()),
            Pair.of(identifier, requestDTO.getIdentifier())),
        true);
    verifyValuesNotChanged(
        Lists.newArrayList(Pair.of(serviceAccount.getAccountIdentifier(), requestDTO.getAccountIdentifier()),
            Pair.of(serviceAccount.getParentUniqueId(), scopeInfo.getUniqueId()),
            Pair.of(serviceAccount.getEmail(), requestDTO.getEmail())),
        true);
  }

  @Override
  public boolean deleteServiceAccount(ScopeInfo scopeInfo, String identifier) {
    ServiceAccount serviceAccount = serviceAccountRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), identifier);
    if (serviceAccount == null) {
      throw new InvalidRequestException(String.format("Service account with identifier: %s doesn't exist", identifier));
    }
    ServiceAccountDTO oldDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount, scopeInfo);
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      long deleted = serviceAccountRepository.deleteByAccountIdentifierAndParentUniqueIdAndIdentifier(
          scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), identifier);
      if (deleted > 0) {
        outboxService.save(new ServiceAccountDeleteEvent(oldDTO));
        deleteApiKeysAndTokensForServiceAccount(scopeInfo, identifier);
        return true;
      } else {
        return false;
      }
    }));
  }

  @Override
  public void deleteBatch(ScopeInfo scopeInfo) {
    List<ServiceAccount> serviceAccounts = serviceAccountRepository.findAllByAccountIdentifierAndParentUniqueId(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId());
    for (ServiceAccount serviceAccount : serviceAccounts) {
      if (serviceAccount == null) {
        continue;
      }
      try {
        deleteServiceAccount(scopeInfo, serviceAccount.getIdentifier());
      } catch (NotFoundException ex) {
        log.error(String.format("Unable to delete Service account. No Service account found in account- [%s] org- "
                + "[%s], project- [%s] and identifier- [%s]",
            scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
            serviceAccount.getIdentifier()));
      }
    }
  }

  private void deleteApiKeysAndTokensForServiceAccount(ScopeInfo scopeInfo, String identifier) {
    long deletedApis = apiKeyService.deleteAllByParentIdentifier(scopeInfo, ApiKeyType.SERVICE_ACCOUNT, identifier);
    log.info(String.format("Deleted %d apis for service account %s", deletedApis, identifier));
    long deletedTokens = tokenService.deleteAllByParentIdentifier(scopeInfo, ApiKeyType.SERVICE_ACCOUNT, identifier);
    log.info(String.format("Deleted %d tokens for service account %s", deletedTokens, identifier));
  }

  @Override
  public ServiceAccountDTO getServiceAccountDTO(ScopeInfo scopeInfo, String identifier) {
    ServiceAccount serviceAccount = serviceAccountRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), identifier);
    if (serviceAccount == null) {
      throw new InvalidArgumentsException(String.format(
          "Service account '%s' doesn't exist in account '%s'", identifier, scopeInfo.getAccountIdentifier()));
    }

    return ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount, scopeInfo);
  }

  @Override
  public PageResponse<ServiceAccountAggregateDTO> listAggregateServiceAccounts(
      ScopeInfo scopeInfo, List<String> identifiers, Pageable pageable, ServiceAccountFilterDTO filterDTO) {
    Criteria criteria = createServiceAccountFilterCriteria(filterDTO, filterDTO.getIdentifiers(), scopeInfo);

    if (!accessControlClient.hasAccess(ResourceScope.of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
                                           scopeInfo.getProjectIdentifier()),
            Resource.of(SERVICEACCOUNT, null), VIEW_SERVICEACCOUNT_PERMISSION)) {
      List<ServiceAccount> serviceAccountList =
          serviceAccountRepository.findAll(criteria, Pageable.unpaged()).getContent();
      serviceAccountList = getPermittedServiceAccounts(serviceAccountList, scopeInfo, VIEW_SERVICEACCOUNT_PERMISSION);
      if (isEmpty(serviceAccountList)) {
        return PageUtils.getNGPageResponse(Page.empty());
      }
      criteria = createServiceAccountFilterCriteria(filterDTO,
          serviceAccountList.stream().map(ServiceAccount::getIdentifier).collect(Collectors.toList()), scopeInfo);
    }
    Page<ServiceAccount> serviceAccounts = serviceAccountRepository.findAll(criteria, pageable);

    Map<String, List<String>> parentUniqueIdToServiceAccountIdentifiersMap =
        getParentUniqueIdToServiceAccountIdentifiersMap(serviceAccounts.getContent());

    Set<String> distinctParentUniqueId = parentUniqueIdToServiceAccountIdentifiersMap.keySet();

    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap =
        scopeInfoService.getScopeInfo(scopeInfo.getAccountIdentifier(), distinctParentUniqueId);

    Map<ImmutablePair<String, String>, List<RoleAssignmentMetadataDTO>>
        serviceAccountIdentifierWithScopeToRoleAssignmentsMap =
            getRoleAssignments(scopeInfo, serviceAccounts.getContent(), parentUniqueIdToScopeInfoMap);

    Map<ImmutablePair<String, String>, Integer> apiKeysCountMap =
        getApiKeysCountForServiceAccounts(parentUniqueIdToScopeInfoMap, parentUniqueIdToServiceAccountIdentifiersMap);

    return PageUtils.getNGPageResponse(serviceAccounts.map(serviceAccount -> {
      ScopeInfo serviceAccountScopeInfo =
          parentUniqueIdToScopeInfoMap.get(serviceAccount.getParentUniqueId()).orElseThrow();
      ServiceAccountDTO serviceAccountDTO =
          ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount, serviceAccountScopeInfo);
      return ServiceAccountAggregateDTO.builder()
          .serviceAccount(serviceAccountDTO)
          .createdAt(serviceAccount.getCreatedAt())
          .lastModifiedAt(serviceAccount.getLastModifiedAt())
          .tokensCount(apiKeysCountMap.getOrDefault(
              new ImmutablePair<>(serviceAccount.getParentUniqueId(), serviceAccount.getIdentifier()), 0))
          .roleAssignmentsMetadataDTO(serviceAccountIdentifierWithScopeToRoleAssignmentsMap.getOrDefault(
              new ImmutablePair<>(serviceAccount.getIdentifier(),
                  ScopeLevel
                      .of(serviceAccountScopeInfo.getAccountIdentifier(), serviceAccountScopeInfo.getOrgIdentifier(),
                          serviceAccountScopeInfo.getProjectIdentifier())
                      .toString()
                      .toLowerCase()),
              new ArrayList<>()))
          .build();
    }));
  }

  @Override
  public PageResponse<ServiceAccountDTO> listManageableServiceAccounts(
      ScopeInfo scopeInfo, Pageable pageable, ServiceAccountFilterDTO filterDTO) {
    if (ServiceAccountFilterType.INCLUDE_INHERITED_SERVICE_ACCOUNTS.equals(filterDTO.getFilterType())) {
      throw new InvalidRequestException(
          "Invalid filterType: INCLUDE_INHERITED_SERVICE_ACCOUNTS is not supported for manageable service accounts. "
          + "Service Accounts can only be managed at the scope they are defined at.");
    }
    Criteria criteria = createServiceAccountFilterCriteria(filterDTO, filterDTO.getIdentifiers(), scopeInfo);

    if (ServiceAccountFilterType.INCLUDE_CHILD_SCOPE_SERVICE_ACCOUNTS.equals(filterDTO.getFilterType())
        || !accessControlClient.hasAccess(ResourceScope.of(scopeInfo.getAccountIdentifier(),
                                              scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
            Resource.of(SERVICEACCOUNT, null), MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)) {
      List<ServiceAccount> serviceAccountList =
          serviceAccountRepository.findAll(criteria, Pageable.unpaged()).getContent();
      serviceAccountList =
          getPermittedServiceAccounts(serviceAccountList, scopeInfo, MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION);
      if (isEmpty(serviceAccountList)) {
        return PageUtils.getNGPageResponse(Page.empty());
      }
      criteria = createServiceAccountFilterCriteria(filterDTO,
          serviceAccountList.stream().map(ServiceAccount::getIdentifier).collect(Collectors.toList()), scopeInfo);
    }
    Page<ServiceAccount> serviceAccounts = serviceAccountRepository.findAll(criteria, pageable);

    Map<String, List<String>> parentUniqueIdToServiceAccountIdentifiersMap =
        getParentUniqueIdToServiceAccountIdentifiersMap(serviceAccounts.getContent());

    Set<String> distinctParentUniqueId = parentUniqueIdToServiceAccountIdentifiersMap.keySet();

    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap =
        scopeInfoService.getScopeInfo(scopeInfo.getAccountIdentifier(), distinctParentUniqueId);

    return PageUtils.getNGPageResponse(serviceAccounts.map(serviceAccount -> {
      ScopeInfo serviceAccountScopeInfo =
          parentUniqueIdToScopeInfoMap.get(serviceAccount.getParentUniqueId()).orElseThrow();
      return ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount, serviceAccountScopeInfo);
    }));
  }

  private Map<String, List<String>> getParentUniqueIdToServiceAccountIdentifiersMap(
      List<ServiceAccount> serviceAccounts) {
    if (isEmpty(serviceAccounts)) {
      return new HashMap<>();
    }
    return serviceAccounts.stream().collect(Collectors.groupingBy(
        ServiceAccount::getParentUniqueId, Collectors.mapping(ServiceAccount::getIdentifier, toList())));
  }

  private Map<ImmutablePair<String, String>, Integer> getApiKeysCountForServiceAccounts(
      Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap,
      Map<String, List<String>> parentUniqueIdToServiceAccountIdentifiersMap) {
    Set<String> distinctParentUniqueId = parentUniqueIdToServiceAccountIdentifiersMap.keySet();
    // Map of <(parentUniqueId,serviceAccountIdentifier), apiTokenCount>
    Map<ImmutablePair<String, String>, Integer> serviceAccountToApiTokenMap = new HashMap<>();
    if (isEmpty(distinctParentUniqueId) || isEmpty(parentUniqueIdToScopeInfoMap)) {
      return serviceAccountToApiTokenMap;
    }

    distinctParentUniqueId.forEach(parentUniqueId -> {
      Optional<ScopeInfo> optionalScopeInfo = parentUniqueIdToScopeInfoMap.get(parentUniqueId);
      if (optionalScopeInfo.isPresent()) {
        Map<String, Integer> map = apiKeyService.getApiKeysPerParentIdentifier(optionalScopeInfo.get(),
            ApiKeyType.SERVICE_ACCOUNT, parentUniqueIdToServiceAccountIdentifiersMap.get(parentUniqueId));
        map.keySet().forEach(
            key -> serviceAccountToApiTokenMap.put(new ImmutablePair<>(parentUniqueId, key), map.get(key)));
      }
    });

    return serviceAccountToApiTokenMap;
  }

  private Map<ImmutablePair<String, String>, List<RoleAssignmentMetadataDTO>> getRoleAssignments(ScopeInfo scopeInfo,
      List<ServiceAccount> serviceAccounts, Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap) {
    Set<PrincipalDTO> principalDTOSet =
        serviceAccounts.stream()
            .map(serviceAccount -> {
              ScopeInfo serviceAccountScopeInfo =
                  parentUniqueIdToScopeInfoMap.get(serviceAccount.getParentUniqueId()).orElseThrow();
              String scopeLevel =
                  ScopeLevel
                      .of(serviceAccountScopeInfo.getAccountIdentifier(), serviceAccountScopeInfo.getOrgIdentifier(),
                          serviceAccountScopeInfo.getProjectIdentifier())
                      .toString()
                      .toLowerCase();

              return PrincipalDTO.builder()
                  .identifier(serviceAccount.getIdentifier())
                  .type(SERVICE_ACCOUNT)
                  .scopeLevel(scopeLevel)
                  .build();
            })
            .collect(Collectors.toSet());

    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder().principalFilter(principalDTOSet).build();

    RoleAssignmentAggregateResponseDTO roleAssignmentAggregateResponseDTO =
        getResponse(accessControlAdminClient.getAggregatedFilteredRoleAssignments(scopeInfo.getAccountIdentifier(),
            scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), roleAssignmentFilterDTO));

    Map<String, RoleResponseDTO> roleMap = roleAssignmentAggregateResponseDTO.getRoles().stream().collect(
        toMap(e -> e.getRole().getIdentifier(), Function.identity()));
    Map<String, ResourceGroupDTO> resourceGroupMap =
        roleAssignmentAggregateResponseDTO.getResourceGroups().stream().collect(
            toMap(ResourceGroupDTO::getIdentifier, Function.identity()));

    return roleAssignmentAggregateResponseDTO.getRoleAssignments()
        .stream()
        .filter(roleAssignmentDTO
            -> roleMap.containsKey(roleAssignmentDTO.getRoleIdentifier())
                && resourceGroupMap.containsKey(roleAssignmentDTO.getResourceGroupIdentifier()))
        .collect(Collectors.groupingBy(roleAssignment
            -> new ImmutablePair<>(roleAssignment.getPrincipal().getIdentifier(),
                roleAssignment.getPrincipal().getScopeLevel() == null
                    ? ScopeLevel
                          .of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
                              scopeInfo.getProjectIdentifier())
                          .toString()
                          .toLowerCase()
                    : roleAssignment.getPrincipal().getScopeLevel()),
            // pair of scope level and identifier
            Collectors.mapping(roleAssignment
                -> RoleAssignmentMetadataDTO.builder()
                       .identifier(roleAssignment.getIdentifier())
                       .roleIdentifier(roleAssignment.getRoleIdentifier())
                       .roleScopeLevel(roleAssignment.getRoleReference() == null
                               ? null
                               : roleAssignment.getRoleReference().getScopeLevel())
                       .resourceGroupIdentifier(roleAssignment.getResourceGroupIdentifier())
                       .roleName(roleMap.get(roleAssignment.getRoleIdentifier()).getRole().getName())
                       .resourceGroupName(resourceGroupMap.get(roleAssignment.getResourceGroupIdentifier()).getName())
                       .managedRole(roleMap.get(roleAssignment.getRoleIdentifier()).isHarnessManaged())
                       .managedRoleAssignment(roleAssignment.isManaged())
                       .build(),
                toList())));
  }

  @Override
  public List<ServiceAccount> listServiceAccounts(ScopeInfo scopeInfo, List<String> identifiers) {
    if (identifiers.isEmpty()) {
      return serviceAccountRepository.findAllByAccountIdentifierAndParentUniqueId(
          scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId());
    } else {
      return serviceAccountRepository.findAllByAccountIdentifierAndParentUniqueIdAndIdentifierIsIn(
          scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), identifiers);
    }
  }

  @Override
  public List<ServiceAccount> listServiceAccountsByUniqueIds(String accountIdentifier, List<String> uniqueIds) {
    return serviceAccountRepository.findAllByAccountIdentifierAndUniqueIdIsIn(accountIdentifier, uniqueIds);
  }

  @Override
  public List<ServiceAccount> getPermittedServiceAccounts(
      List<ServiceAccount> serviceAccounts, ScopeInfo scopeInfo, String permission) {
    if (isEmpty(serviceAccounts)) {
      return Collections.emptyList();
    }
    Set<String> saParentUniqueIds =
        serviceAccounts.stream().map(ServiceAccount::getParentUniqueId).collect(Collectors.toSet());
    Map<String, Optional<ScopeInfo>> saScopeInfos =
        scopeInfoService.getScopeInfo(scopeInfo.getAccountIdentifier(), saParentUniqueIds);

    Map<EntityScopeInfo, List<ServiceAccount>> allServiceAccountScopesMap =
        serviceAccounts.stream().collect(Collectors.groupingBy(serviceAccount
            -> getEntityScopeInfoFromServiceAccount(
                serviceAccount, saScopeInfos.get(serviceAccount.getParentUniqueId()).get())));

    List<PermissionCheckDTO> permissionChecks =
        serviceAccounts.stream()
            .map(serviceAccount -> {
              ScopeInfo saScopeInfo = saScopeInfos.get(serviceAccount.getParentUniqueId()).get();
              return PermissionCheckDTO.builder()
                  .permission(permission)
                  .resourceIdentifier(serviceAccount.getIdentifier())
                  .resourceScope(ResourceScope.of(saScopeInfo.getAccountIdentifier(), saScopeInfo.getOrgIdentifier(),
                      saScopeInfo.getProjectIdentifier()))
                  .resourceType(SERVICEACCOUNT)
                  .build();
            })
            .collect(Collectors.toList());
    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);

    List<ServiceAccount> permittedServiceAccounts = new ArrayList<>();
    for (AccessControlDTO accessControlDTO : accessCheckResponse.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        permittedServiceAccounts.add(
            allServiceAccountScopesMap.get(getEntityScopeInfoFromAccessControlDTO(accessControlDTO)).get(0));
      }
    }
    return permittedServiceAccounts;
  }

  private static EntityScopeInfo getEntityScopeInfoFromAccessControlDTO(AccessControlDTO accessControlDTO) {
    return EntityScopeInfo.builder()
        .accountIdentifier(accessControlDTO.getResourceScope().getAccountIdentifier())
        .orgIdentifier(isBlank(accessControlDTO.getResourceScope().getOrgIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getOrgIdentifier())
        .projectIdentifier(isBlank(accessControlDTO.getResourceScope().getProjectIdentifier())
                ? null
                : accessControlDTO.getResourceScope().getProjectIdentifier())
        .identifier(accessControlDTO.getResourceIdentifier())
        .build();
  }

  private static EntityScopeInfo getEntityScopeInfoFromServiceAccount(
      ServiceAccount serviceAccount, ScopeInfo scopeInfo) {
    return EntityScopeInfo.builder()
        .accountIdentifier(serviceAccount.getAccountIdentifier())
        .orgIdentifier(isBlank(scopeInfo.getOrgIdentifier()) ? null : scopeInfo.getOrgIdentifier())
        .projectIdentifier(isBlank(scopeInfo.getProjectIdentifier()) ? null : scopeInfo.getProjectIdentifier())
        .identifier(serviceAccount.getIdentifier())
        .build();
  }

  private Criteria createServiceAccountFilterCriteria(
      ServiceAccountFilterDTO serviceAccountFilterDTO, List<String> identifiers, ScopeInfo scopeInfo) {
    Criteria criteria;
    if (ServiceAccountFilterType.INCLUDE_INHERITED_SERVICE_ACCOUNTS.equals(serviceAccountFilterDTO.getFilterType())) {
      criteria = createScopeCriteriaIncludingInheritedServiceAccounts(scopeInfo);
    } else if (ServiceAccountFilterType.INCLUDE_CHILD_SCOPE_SERVICE_ACCOUNTS.equals(
                   serviceAccountFilterDTO.getFilterType())) {
      criteria = createScopeCriteriaIncludingChildScopeServiceAccounts(scopeInfo);
    } else {
      criteria = createScopeInfoCriteria(scopeInfo);
    }

    if (Objects.nonNull(identifiers) && !identifiers.isEmpty()) {
      criteria.and(ServiceAccountKeys.identifier).in(identifiers);
    }

    Criteria searchCriteria;
    List<Criteria> searchCriteriaList = new ArrayList<>();
    String searchTerm = serviceAccountFilterDTO.getSearchTerm();
    if (isNotBlank(searchTerm)) {
      searchCriteriaList.add(Criteria.where(ServiceAccountKeys.name).regex(searchTerm, "i"));
      searchCriteriaList.add(Criteria.where(ServiceAccountKeys.identifier).regex(searchTerm, "i"));
      searchCriteriaList.add(Criteria.where(ServiceAccountKeys.tags + "." + NGTagKeys.key).regex(searchTerm, "i"));
      searchCriteriaList.add(Criteria.where(ServiceAccountKeys.tags + "." + NGTagKeys.value).regex(searchTerm, "i"));
    }

    if (!searchCriteriaList.isEmpty()) {
      searchCriteria = new Criteria().orOperator(searchCriteriaList.toArray(new Criteria[0]));
      return new Criteria().andOperator(searchCriteria, criteria);
    }
    return criteria;
  }

  private Criteria createScopeCriteriaIncludingChildScopeServiceAccounts(ScopeInfo scopeInfo) {
    Criteria criteria = new Criteria();
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    if (ScopeLevel.ACCOUNT.equals(scopeInfo.getScopeType())) {
      ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                       .accountIdentifier(scopeInfo.getAccountIdentifier())
                                       .uniqueId(scopeInfo.getAccountIdentifier())
                                       .scopeType(ScopeLevel.ACCOUNT)
                                       .build();
      List<Organization> orgs = organizationService.get(accountScopeInfo);
      for (Organization org : orgs) {
        uniqueIds.add(org.getUniqueId());
        ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(org.getAccountIdentifier())
                                     .orgIdentifier(org.getIdentifier())
                                     .uniqueId(org.getUniqueId())
                                     .scopeType(ScopeLevel.ORGANIZATION)
                                     .build();
        List<Project> projects = projectService.get(orgScopeInfo);
        uniqueIds.addAll(projects.stream().map(Project::getUniqueId).toList());
      }
    } else if (ScopeLevel.ORGANIZATION.equals(scopeInfo.getScopeType())) {
      uniqueIds.add(scopeInfo.getUniqueId());
      List<Project> projects = projectService.get(scopeInfo);
      uniqueIds.addAll(projects.stream().map(Project::getUniqueId).toList());
    }
    criteria.and(ServiceAccountKeys.accountIdentifier).is(scopeInfo.getAccountIdentifier());
    criteria.and(ServiceAccountKeys.parentUniqueId).in(uniqueIds);
    return criteria;
  }

  private Criteria createScopeInfoCriteria(ScopeInfo scopeInfo) {
    return Criteria.where(ServiceAccountKeys.accountIdentifier)
        .is(scopeInfo.getAccountIdentifier())
        .and(ServiceAccountKeys.parentUniqueId)
        .is(scopeInfo.getUniqueId());
  }

  private Criteria createScopeCriteriaIncludingInheritedServiceAccounts(ScopeInfo scopeInfo) {
    Criteria scopeCriteria = createScopeInfoCriteria(scopeInfo);
    Set<String> principalScopeLevelFilters = new HashSet<>();

    if ((isNotEmpty(scopeInfo.getProjectIdentifier()) || isNotEmpty(scopeInfo.getOrgIdentifier()))
        && accessControlClient.hasAccess(ResourceScope.of(scopeInfo.getAccountIdentifier(), null, null),
            Resource.of(SERVICEACCOUNT, null), VIEW_SERVICEACCOUNT_PERMISSION)) {
      principalScopeLevelFilters.add(
          ScopeLevel.of(scopeInfo.getAccountIdentifier(), null, null).toString().toLowerCase());
    }
    if (isNotEmpty(scopeInfo.getProjectIdentifier())
        && accessControlClient.hasAccess(
            ResourceScope.of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), null),
            Resource.of(SERVICEACCOUNT, null), VIEW_SERVICEACCOUNT_PERMISSION)) {
      principalScopeLevelFilters.add(
          ScopeLevel.of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), null).toString().toLowerCase());
    }

    if (isNotEmpty(principalScopeLevelFilters)) {
      // call access control and get inherited service account ids
      RoleAssignmentFilterDTO roleAssignmentFilterDTO = RoleAssignmentFilterDTO.builder()
                                                            .principalTypeFilter(Collections.singleton(SERVICE_ACCOUNT))
                                                            .principalScopeLevelFilter(principalScopeLevelFilters)
                                                            .build();
      RoleAssignmentAggregateResponseDTO roleAssignmentAggregateResponseDTO = NGRestUtils.getResponse(
          accessControlAdminClient.getAggregatedFilteredRoleAssignments(scopeInfo.getAccountIdentifier(),
              scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(), roleAssignmentFilterDTO));
      if (isEmpty(roleAssignmentAggregateResponseDTO.getRoleAssignments())) {
        return scopeCriteria;
      }
      List<Criteria> inheritedServiceAccountsCriteria =
          roleAssignmentAggregateResponseDTO.getRoleAssignments()
              .stream()
              .map(roleAssignmentDTO
                  -> Criteria.where(ServiceAccountKeys.identifier)
                         .is(roleAssignmentDTO.getPrincipal().getIdentifier())
                         .andOperator(createScopeCriteriaFromScopeLevel(
                             scopeInfo, roleAssignmentDTO.getPrincipal().getScopeLevel())))
              .collect(toList());

      inheritedServiceAccountsCriteria.add(scopeCriteria);
      return new Criteria().orOperator(inheritedServiceAccountsCriteria.toArray(new Criteria[0]));
    }
    return scopeCriteria;
  }

  private Criteria createScopeCriteriaFromScopeLevel(ScopeInfo scopeInfo, String scopeLevel) {
    Criteria criteria = new Criteria();
    if (scopeLevel.equalsIgnoreCase(ScopeLevel.ACCOUNT.toString())) {
      criteria.and(ServiceAccountKeys.accountIdentifier).is(scopeInfo.getAccountIdentifier());
      criteria.and(ServiceAccountKeys.parentUniqueId).is(scopeInfo.getAccountIdentifier());
    } else if (scopeLevel.equalsIgnoreCase(ScopeLevel.ORGANIZATION.toString())) {
      ScopeInfo orgScopeInfo =
          scopeInfoService.getScopeInfo(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), null);
      criteria.and(ServiceAccountKeys.accountIdentifier).is(scopeInfo.getAccountIdentifier());
      criteria.and(ServiceAccountKeys.parentUniqueId).is(orgScopeInfo.getUniqueId());
    } else if (scopeLevel.equalsIgnoreCase(ScopeLevel.PROJECT.toString())) {
      ScopeInfo projScopeInfo = scopeInfoService.getScopeInfo(
          scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier());
      criteria.and(ServiceAccountKeys.accountIdentifier).is(scopeInfo.getAccountIdentifier());
      criteria.and(ServiceAccountKeys.parentUniqueId).is(projScopeInfo.getUniqueId());
    }
    return criteria;
  }

  @Override
  public ServiceAccountAggregateDTO getServiceAccountAggregateDTO(ScopeInfo scopeInfo, String identifier) {
    ServiceAccount serviceAccount = serviceAccountRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), identifier);
    if (serviceAccount == null) {
      throw new InvalidArgumentsException(String.format("Service account [%s] doesn't exist in scope", identifier));
    }

    ServiceAccountDTO serviceAccountDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount, scopeInfo);
    Map<String, Optional<ScopeInfo>> parentUniqueIdToScopeInfoMap = new HashMap<>();
    parentUniqueIdToScopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));

    Map<ImmutablePair<String, String>, List<RoleAssignmentMetadataDTO>> roleAssignmentsMap =
        getRoleAssignments(scopeInfo, Collections.singletonList(serviceAccount), parentUniqueIdToScopeInfoMap);

    Map<String, Integer> apiKeysCountMap = apiKeyService.getApiKeysPerParentIdentifier(
        scopeInfo, ApiKeyType.SERVICE_ACCOUNT, Collections.singletonList(identifier));
    return ServiceAccountAggregateDTO.builder()
        .serviceAccount(serviceAccountDTO)
        .createdAt(serviceAccount.getCreatedAt())
        .lastModifiedAt(serviceAccount.getLastModifiedAt())
        .tokensCount(apiKeysCountMap.getOrDefault(serviceAccount.getIdentifier(), 0))
        .roleAssignmentsMetadataDTO(
            roleAssignmentsMap.getOrDefault(new ImmutablePair<>(serviceAccount.getIdentifier(),
                                                ScopeLevel
                                                    .of(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
                                                        scopeInfo.getProjectIdentifier())
                                                    .toString()
                                                    .toLowerCase()),
                new ArrayList<>()))
        .build();
  }

  @Override
  public Long countServiceAccounts(String accountIdentifier) {
    return serviceAccountRepository.countByAccountIdentifier(accountIdentifier);
  }
}
