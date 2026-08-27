/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.service.impl;

import static io.harness.ngsubscriptions.resource.PrincipalsWithAccessApiImpl.PRINCIPAL_SERVICE_ACCOUNT;
import static io.harness.ngsubscriptions.resource.PrincipalsWithAccessApiImpl.PRINCIPAL_USER;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.services.DefaultLicenseServiceImpl;
import io.harness.moduleaccess.ModuleRoleAssignmentHelper;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.ngsubscriptions.entity.AccessEntity;
import io.harness.ngsubscriptions.entity.DailyAccountUsers;
import io.harness.ngsubscriptions.entity.ModuleAccess;
import io.harness.ngsubscriptions.entity.ModuleAccess.ModuleAccessKeys;
import io.harness.ngsubscriptions.entity.TotalAccountUsers;
import io.harness.ngsubscriptions.entity.TotalAccountUsers.TotalAccountUsersKeys;
import io.harness.ngsubscriptions.service.NGSubscriptionsService;
import io.harness.repositories.ngsubscriptions.spring.AccountUsersUsageRepository;
import io.harness.repositories.ngsubscriptions.spring.ModuleAccessRepository;
import io.harness.repositories.ngsubscriptions.spring.TotalAccountUsersRepository;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.spec.server.ng.v1.model.DailyModuleAccountAccessDTO;
import io.harness.spec.server.ng.v1.model.ModuleType;
import io.harness.spec.server.ng.v1.model.PrincipalEntity;
import io.harness.spec.server.ng.v1.model.PrincipalWithAccessFilter;
import io.harness.spec.server.ng.v1.model.PrincipalWithAccessResponse;
import io.harness.spec.server.ng.v1.model.SubscriptionUsageDTO;
import io.harness.spec.server.ng.v1.model.UpdateAccessRequest;
import io.harness.spec.server.ng.v1.model.UpdateRequestEntity;
import io.harness.spec.server.ng.v1.model.UserGroupEntity;
import io.harness.spec.server.ng.v1.model.UserWithAccessEntity;
import io.harness.user.remote.UserFilterNG;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.jooq.tools.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NGSubscriptionsServiceImpl implements NGSubscriptionsService {
  private final TotalAccountUsersRepository totalAccountUsersRepository;
  private final AccountUsersUsageRepository accountUsersUsageRepository;
  private final ModuleAccessRepository moduleAccessRepository;
  private final ScopeInfoService scopeInfoService;
  private final UserGroupService userGroupService;
  private final ServiceAccountService serviceAccountService;
  private final TransactionTemplate transactionTemplate;
  private final NgUserService ngUserService;
  private final DefaultLicenseServiceImpl defaultLicenseService;
  private final ModuleRoleAssignmentHelper moduleRoleAssignmentHelper;

  private static final String ALL_ACCOUNT_USERS = "_account_all_users";
  private static final Integer USERS_BATCH_SIZE = 5000;

  private static final Set<String> VALID_MODULE_TYPE_NAMES =
      Arrays.stream(ModuleType.values()).map(ModuleType::name).collect(Collectors.toUnmodifiableSet());

  @Override
  public List<SubscriptionUsageDTO> getSubscriptions(String accountIdentifier, int year) {
    Criteria criteria = Criteria.where(TotalAccountUsersKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(TotalAccountUsersKeys.year)
                            .is(year);
    Page<TotalAccountUsers> totalAccountUsersPage = totalAccountUsersRepository.findAll(criteria, Pageable.ofSize(100));
    if (totalAccountUsersPage == null || !totalAccountUsersPage.hasContent()) {
      return new ArrayList<>();
    }
    return convertEntityToDto(totalAccountUsersPage.getContent());
  }

  private List<SubscriptionUsageDTO> convertEntityToDto(List<TotalAccountUsers> totalAccountUsersList) {
    List<SubscriptionUsageDTO> result = new ArrayList<>();
    for (TotalAccountUsers totalAccountUsers : totalAccountUsersList) {
      SubscriptionUsageDTO subscriptionUsageDTO = new SubscriptionUsageDTO();
      subscriptionUsageDTO.setYear(totalAccountUsers.getYear());
      subscriptionUsageDTO.setMonth(totalAccountUsers.getMonth());
      subscriptionUsageDTO.setUsage((int) (totalAccountUsers.getUsers() + totalAccountUsers.getServiceAccounts()));
      result.add(subscriptionUsageDTO);
    }

    return result;
  }

  List<AccessEntity> enrichedUserGroups(final String accountIdentifier, List<UpdateRequestEntity> userGroups) {
    List<AccessEntity> result = new ArrayList<>();
    for (UpdateRequestEntity userGroupEntity : userGroups) {
      // org and project is being sent to us by ui since we don't expose uniqueId and parentUniqueId in UI
      try {
        ScopeInfo scopeInfo =
            scopeInfoService.getScopeInfo(accountIdentifier, userGroupEntity.getOrg(), userGroupEntity.getProject());
        Optional<UserGroup> userGroupOpt = userGroupService.get(scopeInfo, userGroupEntity.getIdentifier());
        if (!userGroupOpt.isPresent()) {
          log.error(String.format("User group %s not found for grant or revoke module access in account %s",
              userGroupEntity, accountIdentifier));
          continue;
        }
        UserGroup userGroup = userGroupOpt.get();
        AccessEntity entity = new AccessEntity(userGroup.getIdentifier(), userGroup.getUniqueId(),
            userGroup.getParentUniqueId(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier());
        result.add(entity);
      } catch (EntityNotFoundException ex) {
        log.error(String.format("User group entity %s scope is not valid", userGroupEntity));
      }
    }
    return result;
  }

  List<AccessEntity> enrichedServiceAccounts(
      final String accountIdentifier, List<UpdateRequestEntity> serviceAccounts) {
    List<AccessEntity> result = new ArrayList<>();
    for (UpdateRequestEntity serviceAccountsEntity : serviceAccounts) {
      try {
        ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
            accountIdentifier, serviceAccountsEntity.getOrg(), serviceAccountsEntity.getProject());
        List<ServiceAccount> serviceAccountList =
            serviceAccountService.listServiceAccounts(scopeInfo, List.of(serviceAccountsEntity.getIdentifier()));
        if (serviceAccountList.size() == 0) {
          log.error(
              String.format("Service account %s not found in account %s", serviceAccountsEntity, accountIdentifier));
          continue;
        }

        ServiceAccount serviceAccount = serviceAccountList.get(0);
        AccessEntity entity = new AccessEntity(serviceAccount.getIdentifier(), serviceAccount.getUniqueId(),
            scopeInfo.getUniqueId(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier());
        result.add(entity);
      } catch (EntityNotFoundException ex) {
        log.error(String.format("Service account entity %s scope is invalid.", serviceAccountsEntity));
      }
    }

    return result;
  }
  @Override
  public ModuleAccess updateModuleAccess(String accountIdentifier, UpdateAccessRequest updateAccessRequest) {
    log.info(String.format("Update module access for account %s and module %s with request %s", accountIdentifier,
        updateAccessRequest.getModuleType(), updateAccessRequest));
    ModuleType moduleType = updateAccessRequest.getModuleType();
    List<UpdateRequestEntity> userGroupToAdd = updateAccessRequest.getUserGroupsToGrant();
    List<UpdateRequestEntity> userGroupToRemove = updateAccessRequest.getUserGroupsToRevoke();
    List<UpdateRequestEntity> serviceAccountToAdd = updateAccessRequest.getServiceAccountsToGrant();
    List<UpdateRequestEntity> serviceAccountToRemove = updateAccessRequest.getServiceAccountsToRevoke();

    List<AccessEntity> ugEntitiesToAdd = enrichedUserGroups(accountIdentifier, userGroupToAdd);
    List<AccessEntity> ugEntitiesToRemove = enrichedUserGroups(accountIdentifier, userGroupToRemove);
    List<AccessEntity> saEntitiesToAdd = enrichedServiceAccounts(accountIdentifier, serviceAccountToAdd);
    List<AccessEntity> saEntitiesToRemove = enrichedServiceAccounts(accountIdentifier, serviceAccountToRemove);

    try {
      ModuleAccess result = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Criteria criteria = Criteria.where(ModuleAccessKeys.accountIdentifier)
                                .is(accountIdentifier)
                                .and(ModuleAccessKeys.moduleType)
                                .is(moduleType.toString());

        Update updateToAdd = new Update();
        Update updateToRemove = new Update();

        boolean entitiesToRemove = false;
        boolean entitiesToAdd = false;
        if (!ugEntitiesToRemove.isEmpty()) {
          updateToRemove.pullAll(
              ModuleAccessKeys.userGroups, ugEntitiesToRemove.toArray(new Object[ugEntitiesToRemove.size()]));
          entitiesToRemove = true;
        }
        if (!saEntitiesToRemove.isEmpty()) {
          updateToRemove.pullAll(
              ModuleAccessKeys.serviceAccounts, saEntitiesToRemove.toArray(new Object[saEntitiesToRemove.size()]));
          entitiesToRemove = true;
        }

        if (!ugEntitiesToAdd.isEmpty()) {
          updateToAdd.addToSet(ModuleAccessKeys.userGroups).each(ugEntitiesToAdd);
          entitiesToAdd = true;
        }
        if (!saEntitiesToAdd.isEmpty()) {
          updateToAdd.addToSet(ModuleAccessKeys.serviceAccounts).each(saEntitiesToAdd);
          entitiesToAdd = true;
        }

        ModuleAccess updatedModuleAccess = null;
        if (entitiesToRemove) {
          updatedModuleAccess = moduleAccessRepository.findAndModify(criteria, updateToRemove);
        }
        if (entitiesToAdd) {
          updatedModuleAccess = moduleAccessRepository.findAndModify(criteria, updateToAdd);
        }
        if (updatedModuleAccess == null) {
          updatedModuleAccess = ModuleAccess.builder()
                                    .moduleType(moduleType)
                                    .userGroups(ugEntitiesToAdd)
                                    .serviceAccounts(saEntitiesToAdd)
                                    .accountIdentifier(accountIdentifier)
                                    .build();
          moduleAccessRepository.save(updatedModuleAccess);
        }
        log.debug(String.format("Module access entity updated : %s", updatedModuleAccess));

        return updatedModuleAccess;
      }));

      moduleRoleAssignmentHelper.syncIfApplicable(accountIdentifier, moduleType.name(), toIdentifiers(ugEntitiesToAdd),
          toIdentifiers(saEntitiesToAdd), toIdentifiers(ugEntitiesToRemove), toIdentifiers(saEntitiesToRemove));

      return result;
    } catch (Exception ex) {
      log.error(ex.getMessage());
      throw ex;
    }
  }

  private List<String> toIdentifiers(List<AccessEntity> entities) {
    return entities.stream().map(AccessEntity::getIdentifier).collect(Collectors.toList());
  }

  @Override
  public List<DailyModuleAccountAccessDTO> getModuleAccountAccessList(
      String accountIdentifier, ModuleType moduleType, Integer year, Integer month) {
    List<DailyModuleAccountAccessDTO> dailyModuleAccountAccessDTOList = new ArrayList<>();
    List<DailyAccountUsers> dailyAccountUsersList =
        accountUsersUsageRepository.findByAccountIdentifierAndModuleTypeAndYearAndMonth(
            accountIdentifier, moduleType, year, month);
    for (DailyAccountUsers dailyAccountUser : dailyAccountUsersList) {
      DailyModuleAccountAccessDTO dto = new DailyModuleAccountAccessDTO();
      dto.setDay(dailyAccountUser.getDay());
      dto.setMonth(dailyAccountUser.getMonth());
      dto.setYear(dailyAccountUser.getYear());
      dto.setServiceAccountCount(dailyAccountUser.getServiceAccounts());
      dto.setUserAccountCount(dailyAccountUser.getUsers());
      dailyModuleAccountAccessDTOList.add(dto);
    }
    return dailyModuleAccountAccessDTOList;
  }

  PrincipalEntity getPrincipalEntityFromSA(ServiceAccountDTO serviceAccountDTO) {
    PrincipalEntity principalEntity = new PrincipalEntity();
    principalEntity.setIdentifier(serviceAccountDTO.getIdentifier());
    principalEntity.setAccountIdentifier(serviceAccountDTO.getAccountIdentifier());
    principalEntity.setOrg(serviceAccountDTO.getOrgIdentifier());
    principalEntity.setProject(serviceAccountDTO.getProjectIdentifier());
    return principalEntity;
  }
  UserGroupEntity getPrincipalEntityFromUG(UserGroup userGroup, ScopeInfo scopeInfo) {
    UserGroupEntity principalEntity = new UserGroupEntity();
    principalEntity.setIdentifier(userGroup.getIdentifier());
    principalEntity.setAccountIdentifier(scopeInfo.getAccountIdentifier());
    principalEntity.setOrg(scopeInfo.getOrgIdentifier());
    principalEntity.setProject(scopeInfo.getProjectIdentifier());
    principalEntity.setUserCount(userGroup.getUsers().size());
    return principalEntity;
  }

  void getUsersWithAccess(
      List<UserGroup> userGroups, List<ServiceAccountDTO> serviceAccountDTOs, PrincipalWithAccessResponse response) {
    Map<String, UserWithAccessEntity> userToEntity = new HashMap<>();
    Set<String> orgs = new HashSet<>();
    Set<List<String>> projects = new HashSet<>();

    if (!userGroups.isEmpty()) {
      Set<String> uniqueIds = userGroups.stream().map(UserGroup::getParentUniqueId).collect(Collectors.toSet());
      Map<String, Optional<ScopeInfo>> scopeInfoMap =
          scopeInfoService.getScopeInfo(userGroups.get(0).getAccountIdentifier(), uniqueIds);

      for (UserGroup ug : userGroups) {
        ScopeInfo scopeInfo = scopeInfoMap.get(ug.getParentUniqueId()).orElseThrow();
        response.getUserGroups().add(getPrincipalEntityFromUG(ug, scopeInfo));
        if (!StringUtils.isEmpty(scopeInfo.getOrgIdentifier())) {
          if (!orgs.contains(scopeInfo.getOrgIdentifier())) {
            response.getOrgs().add(scopeInfo.getOrgIdentifier());
            orgs.add(scopeInfo.getOrgIdentifier());
          }
        }
        if (!StringUtils.isEmpty(scopeInfo.getProjectIdentifier())) {
          List<String> projectList = Arrays.asList(scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier());
          if (!projects.contains(projectList)) {
            response.getProjects().add(Arrays.asList(scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()));
            projects.add(projectList);
          }
        }

        List<String> users = ug.getUsers();
        // Since we are making a batch call to CG manager its best to not send a large request which can adversely
        // impact CG manager. Here we will batch requests to 5000 or less. This is specially important since by default
        // all users in account have module access.
        List<UserInfo> userInfos = new ArrayList<>();
        int count = users.size();
        int index = 0;
        while (index < count) {
          UserFilterNG userFilterNG =
              UserFilterNG.builder().userIds(users.subList(index, Math.min(index + USERS_BATCH_SIZE, count))).build();
          userInfos.addAll(ngUserService.listCurrentGenUsers(ug.getAccountIdentifier(), userFilterNG));
          index += USERS_BATCH_SIZE;
        }

        for (UserInfo userInfo : userInfos) {
          if (!userToEntity.containsKey(userInfo.getUuid())) {
            UserWithAccessEntity entity = new UserWithAccessEntity();
            entity.setEmail(userInfo.getEmail());
            entity.setName(userInfo.getName());
            entity.setIsServiceAccount(false);
            entity.setLastLogin(userInfo.getLastLogin());
            entity.setUserGroups(new ArrayList<>());
            entity.setOrgs(new ArrayList<>());
            entity.setProjects(new ArrayList<>());
            userToEntity.put(userInfo.getUuid(), entity);
          }
          if (!userToEntity.get(userInfo.getUuid()).getUserGroups().contains(ug.getIdentifier())) {
            userToEntity.get(userInfo.getUuid()).getUserGroups().add(ug.getIdentifier());
          }
          if (!userToEntity.get(userInfo.getUuid()).getOrgs().contains(scopeInfo.getOrgIdentifier())) {
            userToEntity.get(userInfo.getUuid()).getOrgs().add(scopeInfo.getOrgIdentifier());
          }
          if (!userToEntity.get(userInfo.getUuid()).getProjects().contains(scopeInfo.getProjectIdentifier())) {
            userToEntity.get(userInfo.getUuid()).getProjects().add(scopeInfo.getProjectIdentifier());
          }
        }
      }
    }

    for (ServiceAccountDTO serviceAccountDTO : serviceAccountDTOs) {
      response.getServiceAccounts().add(getPrincipalEntityFromSA(serviceAccountDTO));
      if (!userToEntity.containsKey(serviceAccountDTO.getIdentifier())) {
        UserWithAccessEntity entity = new UserWithAccessEntity();
        entity.setEmail(serviceAccountDTO.getEmail());
        entity.setName(serviceAccountDTO.getName());
        entity.setIsServiceAccount(true);
        entity.setLastLogin(-1L);
        entity.setUserGroups(new ArrayList<>());
        entity.setOrgs(new ArrayList<>());
        entity.setProjects(new ArrayList<>());
        userToEntity.put(serviceAccountDTO.getIdentifier(), entity);
      }
      if (!userToEntity.get(serviceAccountDTO.getIdentifier())
               .getOrgs()
               .contains(serviceAccountDTO.getOrgIdentifier())) {
        userToEntity.get(serviceAccountDTO.getIdentifier()).getOrgs().add(serviceAccountDTO.getOrgIdentifier());
      }
      if (!userToEntity.get(serviceAccountDTO.getIdentifier())
               .getProjects()
               .contains(serviceAccountDTO.getProjectIdentifier())) {
        userToEntity.get(serviceAccountDTO.getIdentifier()).getProjects().add(serviceAccountDTO.getProjectIdentifier());
      }
    }

    response.getUsersWithAccess().addAll(userToEntity.values().stream().toList());
  }
  @Override
  public PrincipalWithAccessResponse findPrincipals(final String accountIdentifier, ModuleType moduleType) {
    PrincipalWithAccessResponse response = new PrincipalWithAccessResponse();
    response.setOrgs(new ArrayList<>());
    response.setServiceAccounts(new ArrayList<>());
    response.setUserGroups(new ArrayList<>());
    response.setProjects(new ArrayList<>());
    response.setUsersWithAccess(new ArrayList<>());

    Optional<ModuleAccess> moduleAccessOpt =
        moduleAccessRepository.findByAccountIdentifierAndModuleType(accountIdentifier, moduleType);
    if (!moduleAccessOpt.isPresent()) {
      if (moduleRoleAssignmentHelper.isNamedUserGatingEnabled(accountIdentifier, moduleType.name())) {
        // FF is enabled: no module access data means no one has access yet
        response.setTotalPrincipals(0);
      } else {
        // Default scenario: everyone in _account_all_users is eligible
        ScopeInfo scopeInfo = ScopeInfo.builder()
                                  .accountIdentifier(accountIdentifier)
                                  .scopeType(ScopeLevel.ACCOUNT)
                                  .uniqueId(accountIdentifier)
                                  .build();
        Optional<UserGroup> userGroupOpt = userGroupService.get(scopeInfo, ALL_ACCOUNT_USERS);
        if (!userGroupOpt.isPresent()) {
          throw new RuntimeException(
              String.format("Failed to find all account users group in account %s", accountIdentifier));
        }
        getUsersWithAccess(List.of(userGroupOpt.get()), new ArrayList<>(), response);
        response.setTotalPrincipals(response.getUsersWithAccess().size());
      }
    } else {
      List<UserGroup> userGroups = new ArrayList<>();
      List<ServiceAccountDTO> serviceAccounts = new ArrayList<>();

      // gather all scope info
      Set<String> scopeInfos = new HashSet<>();
      Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
      for (AccessEntity userGroup : moduleAccessOpt.get().getUserGroups()) {
        scopeInfos.add(userGroup.getParentUniqueId());
      }
      for (AccessEntity serviceAccount : moduleAccessOpt.get().getServiceAccounts()) {
        scopeInfos.add(serviceAccount.getParentUniqueId());
      }
      scopeInfoMap = scopeInfoService.getScopeInfo(accountIdentifier, scopeInfos);

      for (AccessEntity userGroupEntity : moduleAccessOpt.get().getUserGroups()) {
        Optional<ScopeInfo> scopeInfoOpt = scopeInfoMap.get(userGroupEntity.getParentUniqueId());
        if (scopeInfoOpt == null || !scopeInfoOpt.isPresent()) {
          log.warn(String.format("Scope %s not found for UserGroup %s, skipping stale entry",
              userGroupEntity.getParentUniqueId(), userGroupEntity.getIdentifier()));
          continue;
        }
        Optional<UserGroup> userGroup = userGroupService.get(scopeInfoOpt.get(), userGroupEntity.getIdentifier());
        if (!userGroup.isPresent()) {
          log.error(String.format("User group %s not found in parent scope %s", userGroupEntity.getIdentifier(),
              userGroupEntity.getParentUniqueId()));
          continue;
        }
        userGroups.add(userGroup.get());
      }
      for (AccessEntity serviceAccountEntity : moduleAccessOpt.get().getServiceAccounts()) {
        Optional<ScopeInfo> scopeInfoOpt = scopeInfoMap.get(serviceAccountEntity.getParentUniqueId());
        if (scopeInfoOpt == null || !scopeInfoOpt.isPresent()) {
          log.warn(String.format("Scope %s not found for ServiceAccount %s, skipping stale entry",
              serviceAccountEntity.getParentUniqueId(), serviceAccountEntity.getIdentifier()));
          continue;
        }
        try {
          ServiceAccountDTO serviceAccountDTO =
              serviceAccountService.getServiceAccountDTO(scopeInfoOpt.get(), serviceAccountEntity.getIdentifier());
          serviceAccounts.add(serviceAccountDTO);
        } catch (InvalidArgumentsException exception) {
          log.error(String.format("Service account %s not found in scope %s: %s", serviceAccountEntity.getIdentifier(),
              serviceAccountEntity.getParentUniqueId(), exception.getMessage()));
        }
      }
      getUsersWithAccess(userGroups, serviceAccounts, response);
      response.setTotalPrincipals(response.getUsersWithAccess().size());
    }

    return response;
  }

  @Override
  public PrincipalWithAccessResponse findPrincipalsWithFilter(
      String accountIdentifier, ModuleType moduleType, PrincipalWithAccessFilter filter) {
    PrincipalWithAccessResponse response = findPrincipals(accountIdentifier, moduleType);
    List<UserWithAccessEntity> filteredResponse = new ArrayList<>();
    Boolean added = false;
    Boolean filterEmpty = true;

    Set<String> ugFilter = new HashSet<>();
    Set<String> orgFilter = new HashSet<>();
    Set<String> projectFilter = new HashSet<>();

    if (filter.getUserGroups() != null && filter.getUserGroups().size() > 0) {
      ugFilter = filter.getUserGroups().stream().map(x -> x.getIdentifier()).collect(Collectors.toSet());
      filterEmpty = false;
    }
    if (filter.getProjects() != null && filter.getProjects().size() > 0) {
      projectFilter = filter.getProjects().stream().map(x -> x.get(1)).collect(Collectors.toSet());
      filterEmpty = false;
    }

    if (filter.getOrgs() != null && filter.getOrgs().size() > 0) {
      orgFilter = filter.getOrgs().stream().collect(Collectors.toSet());
      filterEmpty = false;
    }

    if (filter.getPrincipalType() != null) {
      filterEmpty = false;
    }

    for (UserWithAccessEntity entity : response.getUsersWithAccess()) {
      added = false;
      for (String ug : entity.getUserGroups()) {
        if (ugFilter.contains(ug)) {
          filteredResponse.add(entity);
          added = true;
          break;
        }
      }

      if (added) {
        continue;
      }

      for (String org : entity.getOrgs()) {
        if (orgFilter.contains(org)) {
          filteredResponse.add(entity);
          added = true;
          break;
        }
      }

      if (added) {
        continue;
      }

      for (String project : entity.getProjects()) {
        if (projectFilter.contains(project)) {
          filteredResponse.add(entity);
          added = true;
          break;
        }
      }

      if (added) {
        continue;
      }

      if (filter.getPrincipalType() != null && !filter.getPrincipalType().isEmpty()) {
        if (filter.getPrincipalType().equals(PRINCIPAL_SERVICE_ACCOUNT)) {
          if (entity.isIsServiceAccount()) {
            filteredResponse.add(entity);
          }
        } else if (filter.getPrincipalType().equals(PRINCIPAL_USER)) {
          if (!entity.isIsServiceAccount()) {
            filteredResponse.add(entity);
          }
        }
      }
    }

    if (filteredResponse.size() == 0 && filterEmpty) {
      log.info(String.format("Either filter was empty or it didn't match anything, so we return normal response"));
      return response;
    }
    response.setUsersWithAccess(filteredResponse);
    response.setTotalPrincipals(response.getUsersWithAccess().size());
    return response;
  }

  /**
   *
   * Logic Summary:
   * For the collection of input module types
   * - If account does not have any valid Dev360 license, return empty hashmap
   * - If account has access to a module but the user does not have access, return false in the hashmap
   * - If account has access to a module and the user also has access to the module, return true in the hashmap
   *
   * Logic to determine user access to module:
   * - Account should have access to the module through an active Dev360 license
   * - If no user group is added to the module, all users within the account have access
   * - If at least one user group is added to the module, only users within the module have access.
   */
  @Override
  public Map<ModuleType, Boolean> getDev360ModuleAccessForAccountAndUser(
      String accountIdentifier, String userIdentifier, Set<String> moduleTypeInputSet) {
    // only module types that are supported are retained from the input.
    Set<ModuleType> validModuleTypesInputSet = moduleTypeInputSet.stream()
                                                   .filter(VALID_MODULE_TYPE_NAMES::contains)
                                                   .map(ModuleType::valueOf)
                                                   .collect(Collectors.toSet());

    Collection<io.harness.ModuleType> moduleTypeListForLicense =
        validModuleTypesInputSet.stream().map(t -> io.harness.ModuleType.valueOf(t.name())).toList();
    List<ModuleLicenseDTO> activeDev360ModuleLicenses = defaultLicenseService.getDev360ModuleLicenses(
        accountIdentifier, LicenseStatus.ACTIVE, moduleTypeListForLicense);

    // if there is no active dev360 license, then the module level access is denied
    if (activeDev360ModuleLicenses.isEmpty()) {
      return new HashMap<>();
    }

    Map<ModuleType, ModuleLicenseDTO> moduleLicenseMap = activeDev360ModuleLicenses.stream().collect(
        Collectors.toMap(k -> ModuleType.valueOf(k.getModuleType().name()), v -> v));

    // Get the list of modules for the account
    List<ModuleAccess> moduleAccessList =
        moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(accountIdentifier, validModuleTypesInputSet);
    Map<ModuleType, ModuleAccess> moduleAccessMap =
        moduleAccessList.stream().collect(Collectors.toMap(ModuleAccess::getModuleType, m -> m));

    // Get the list if user groups that user is part of
    List<UserGroup> userGroupsForUser = userGroupService.getUserGroupsForUser(accountIdentifier, userIdentifier, false);
    Set<String> userGroupUniqueIdsForUser =
        userGroupsForUser.stream().map(UserGroup::getUniqueId).collect(Collectors.toSet());

    Map<ModuleType, Boolean> resultAccessMap = new HashMap<>();
    Set<ModuleType> activeModuleTypeForAccount = moduleLicenseMap.keySet();

    for (ModuleType moduleType : activeModuleTypeForAccount) {
      // If valid license exists, but module level access is not present, it assumed that all users have access
      ModuleAccess moduleAccess = moduleAccessMap.get(moduleType);
      if (moduleAccess == null) {
        resultAccessMap.put(moduleType, true);
        continue;
      }
      List<AccessEntity> userGroupsWithModuleAccess = moduleAccess.getUserGroups();
      if (userGroupsWithModuleAccess == null || userGroupsWithModuleAccess.isEmpty()) {
        resultAccessMap.put(moduleType, true);
        continue;
      }

      // If valid license exists, and module level access is granted, then only the granted users would be allowed to
      // access
      Set<String> userGroupsUniqueIdsWithModuleAccess =
          userGroupsWithModuleAccess.stream().map(AccessEntity::getUniqueId).collect(Collectors.toSet());
      boolean hasAccess = !Collections.disjoint(userGroupUniqueIdsForUser, userGroupsUniqueIdsWithModuleAccess);

      resultAccessMap.put(moduleType, hasAccess);
    }
    return resultAccessMap;
  }
}
