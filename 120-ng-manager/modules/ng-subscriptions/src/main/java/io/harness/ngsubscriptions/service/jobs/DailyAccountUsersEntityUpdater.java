/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.service.jobs;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.accountresourceng.remote.AccountResourceNGClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ngsubscriptions.entity.AccessEntity;
import io.harness.ngsubscriptions.entity.DailyAccountUsers;
import io.harness.ngsubscriptions.entity.ModuleAccess;
import io.harness.repositories.ngsubscriptions.spring.AccountUsersUsageRepository;
import io.harness.repositories.ngsubscriptions.spring.ModuleAccessRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.ng.v1.model.ModuleType;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(PL)
@Slf4j
public class DailyAccountUsersEntityUpdater implements Runnable {
  private final AccountUsersUsageRepository dailyAccountUsersRepository;
  private final PersistentLocker persistentLocker;
  private final AccountResourceNGClient accountResourceNGClient;
  private final ModuleAccessRepository moduleAccessRepository;
  private final UserGroupService userGroupService;
  private final ScopeInfoService scopeInfoService;

  private static final String ALL_ACCOUNT_USERS = "_account_all_users";
  private static final String DEBUG_MESSAGE = "dailyAccountUsersEntityUpdater";
  private static final String LOCK_NAME = "dailyAccountUsersEntityUpdater";
  private static final int PAGE_SIZE = 1000;

  private final Map<io.harness.ModuleType, ModuleType> nGModuleTypeModuleTypeEnumMap =
      Map.of(io.harness.ModuleType.IDP, ModuleType.IDP, io.harness.ModuleType.CODE, ModuleType.CODE,
          io.harness.ModuleType.CF, ModuleType.CF, io.harness.ModuleType.CI, ModuleType.CI);
  final Set<io.harness.ModuleType> supportedModules = new HashSet<>(Arrays.asList(
      io.harness.ModuleType.CI, io.harness.ModuleType.CF, io.harness.ModuleType.IDP, io.harness.ModuleType.CODE));

  @Inject
  DailyAccountUsersEntityUpdater(AccountUsersUsageRepository dailyAccountUsersRepository,
      PersistentLocker persistentLocker, AccountResourceNGClient accountResourceNGClient,
      ModuleAccessRepository moduleAccessRepository, UserGroupService userGroupService,
      ScopeInfoService scopeInfoService) {
    this.dailyAccountUsersRepository = dailyAccountUsersRepository;
    this.persistentLocker = persistentLocker;
    this.accountResourceNGClient = accountResourceNGClient;
    this.moduleAccessRepository = moduleAccessRepository;
    this.userGroupService = userGroupService;
    this.scopeInfoService = scopeInfoService;
  }

  @Override
  public void run() {
    log.info(DEBUG_MESSAGE + " Started running...");
    log.info(DEBUG_MESSAGE + " Trying to acquire lock...");
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
        log.info(DEBUG_MESSAGE + "Setting SecurityContext completed.");
        pollAndUpdateEntity();
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
        log.info(DEBUG_MESSAGE + " Unsetting SecurityContext completed.");
      }
      log.info(DEBUG_MESSAGE + " Stopped running...");
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
  }

  private void pollAndUpdateEntity() {
    try {
      List<AccountDTO> accountDTOS = new ArrayList<>();
      int page = 0;
      // Fetching all the accounts
      while (true) {
        List<AccountDTO> response = getResponse(accountResourceNGClient.getAccounts(page, PAGE_SIZE)).getResponse();
        if (response == null || response.size() == 0) {
          break;
        }
        accountDTOS.addAll(response);
        if (response.size() < PAGE_SIZE) {
          break;
        }
        page = page + 1;
      }

      log.info("dailyAccountUsersEntityUpdater: Total accounts : {}", accountDTOS.size());
      Set<String> accounts = accountDTOS.stream().map(x -> x.getIdentifier()).collect(Collectors.toSet());
      Calendar calendar = Calendar.getInstance();
      int year = calendar.get(Calendar.YEAR);
      int month = calendar.get(Calendar.MONTH);
      int day = calendar.get(Calendar.DAY_OF_MONTH);
      for (String accountIdentifier : accounts) {
        for (io.harness.ModuleType moduleType : supportedModules) {
          Optional<ModuleAccess> moduleAccess = moduleAccessRepository.findByAccountIdentifierAndModuleType(
              accountIdentifier, nGModuleTypeModuleTypeEnumMap.get(moduleType));
          // if module access is empty then count all users using the user group ALL_ACCOUNT_USERS
          Long userWithAccessCount = 0L;
          Long serviceAccountsWithAccessCount = 0L;

          if (moduleAccess.isEmpty()) {
            ScopeInfo scopeInfo = ScopeInfo.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .scopeType(ScopeLevel.ACCOUNT)
                                      .uniqueId(accountIdentifier)
                                      .build();
            Optional<UserGroup> userGroupOpt = userGroupService.get(scopeInfo, ALL_ACCOUNT_USERS);
            if (!userGroupOpt.isPresent()) {
              log.error(String.format("Failed to find all account users group in account %s", accountIdentifier));
              continue;
            }
            userWithAccessCount = (long) userGroupOpt.get().getUsers().size();

          } else {
            Set<String> userIds = new HashSet<>();
            List<AccessEntity> userGroups =
                moduleAccess.get().getUserGroups(); // Get number of users from the userGroups
            Set<String> uniqueIds =
                userGroups.stream().map(AccessEntity::getParentUniqueId).collect(Collectors.toSet());
            Map<String, Optional<ScopeInfo>> scopeInfoMap = scopeInfoService.getScopeInfo(accountIdentifier, uniqueIds);
            for (AccessEntity entity : userGroups) {
              Optional<ScopeInfo> scopeInfoOpt = scopeInfoMap.get(entity.getParentUniqueId());
              if (!scopeInfoOpt.isPresent()) {
                log.error(String.format("UserGroup : {} doesn't have valid scope.", entity));
                continue;
              }

              Optional<UserGroup> userGroupOpt = userGroupService.get(scopeInfoOpt.get(), entity.getIdentifier());
              if (userGroupOpt.isPresent()) {
                userIds.addAll(userGroupOpt.get().getUsers());
              } else {
                log.error("Unable to find UserGroup with name {}", entity.getIdentifier());
              }
            }
            serviceAccountsWithAccessCount = (long) moduleAccess.get().getServiceAccounts().size();
            userWithAccessCount = (long) userIds.size();
          }

          try {
            DailyAccountUsers dailyAccountUsers = DailyAccountUsers.builder()
                                                      .moduleType(nGModuleTypeModuleTypeEnumMap.get(moduleType))
                                                      .accountIdentifier(accountIdentifier)
                                                      .year(year)
                                                      .month(month)
                                                      .day(day)
                                                      .users(userWithAccessCount)
                                                      .serviceAccounts(serviceAccountsWithAccessCount)
                                                      .build();

            dailyAccountUsersRepository.save(dailyAccountUsers);
          } catch (DuplicateKeyException ex) {
            continue;
          }
        }
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " Fetching all accounts failed : ", ex);
    }
    log.info(DEBUG_MESSAGE + " Execution completed.");
  }
}
