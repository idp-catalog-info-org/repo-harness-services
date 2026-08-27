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
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;
import io.harness.ngsubscriptions.entity.TotalAccountUsers;
import io.harness.repositories.ng.serviceaccounts.ServiceAccountRepository;
import io.harness.repositories.ngsubscriptions.spring.TotalAccountUsersRepository;
import io.harness.repositories.user.spring.UserMembershipRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Slf4j
public class TotalAccountUsersEntityUpdater implements Runnable {
  private final TotalAccountUsersRepository totalAccountUsersRepository;
  private final PersistentLocker persistentLocker;
  private final UserMembershipRepository userMembershipRepository;
  private final ServiceAccountRepository serviceAccountRepository;
  private final AccountResourceNGClient accountResourceNGClient;

  private static final String DEBUG_MESSAGE = "totalAccountUsersEntityUpdater";
  private static final String LOCK_NAME = "totalAccountUsersEntityUpdater";
  private static final int PAGE_SIZE = 1000;

  @Inject
  TotalAccountUsersEntityUpdater(TotalAccountUsersRepository totalAccountUsersRepository,
      PersistentLocker persistentLocker, UserMembershipRepository userMembershipRepository,
      ServiceAccountRepository serviceAccountRepository, AccountResourceNGClient accountResourceNGClient) {
    this.totalAccountUsersRepository = totalAccountUsersRepository;
    this.persistentLocker = persistentLocker;
    this.userMembershipRepository = userMembershipRepository;
    this.serviceAccountRepository = serviceAccountRepository;
    this.accountResourceNGClient = accountResourceNGClient;
  }

  @Override
  public void run() {
    log.info(DEBUG_MESSAGE + "Started running...");
    log.info(DEBUG_MESSAGE + "Trying to acquire lock...");
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
        log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
      }
      log.info(DEBUG_MESSAGE + "Stopped running...");
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
  }

  private void pollAndUpdateEntity() {
    try {
      List<AccountDTO> accountDTOS = new ArrayList<>();
      int page = 0;
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

      log.info("TotalAccountUsersEntityUpdater: Total accounts : {}", accountDTOS.size());
      Set<String> accounts = accountDTOS.stream().map(x -> x.getIdentifier()).collect(Collectors.toSet());
      Calendar calendar = Calendar.getInstance();
      int year = calendar.get(Calendar.YEAR);
      int month = calendar.get(Calendar.MONTH);
      for (String accountIdentifier : accounts) {
        Optional<TotalAccountUsers> totalAccountUsersOpt =
            totalAccountUsersRepository.findByAccountIdentifierAndYearAndMonth(accountIdentifier, year, month);
        TotalAccountUsers totalAccountUsers = null;
        if (!totalAccountUsersOpt.isPresent()) {
          totalAccountUsers = TotalAccountUsers.builder()
                                  .accountIdentifier(accountIdentifier)
                                  .year(year)
                                  .month(month)
                                  .users(0L)
                                  .serviceAccounts(0L)
                                  .build();
        } else {
          totalAccountUsers = totalAccountUsersOpt.get();
        }

        Criteria criteria = Criteria.where(UserMembershipKeys.accountIdentifier)
                                .is(accountIdentifier)
                                .and(UserMembershipKeys.parentUniqueId)
                                .is(accountIdentifier);

        long currentUsers = Math.max(userMembershipRepository.count(criteria), totalAccountUsers.getUsers());
        long serviceAccountCount = serviceAccountRepository.countByAccountIdentifier(accountIdentifier);
        long currentServiceAccounts = Math.max(serviceAccountCount, totalAccountUsers.getServiceAccounts());

        totalAccountUsers.setUsers(currentUsers);
        totalAccountUsers.setServiceAccounts(currentServiceAccounts);
        totalAccountUsersRepository.save(totalAccountUsers);
      }

    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " Fetching all accounts failed : ", ex);
    }
    log.info(DEBUG_MESSAGE + " Execution completed.");
  }
}
