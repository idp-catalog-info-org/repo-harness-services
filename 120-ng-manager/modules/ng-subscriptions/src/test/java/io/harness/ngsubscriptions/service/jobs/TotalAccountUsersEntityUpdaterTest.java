/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.service.jobs;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.PRAVEEN_SOLANKI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accountresourceng.remote.AccountResourceNGClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageResponse;
import io.harness.beans.PageResponse.PageResponseBuilder;
import io.harness.category.element.UnitTests;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.lock.noop.AcquiredNoopLock;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;
import io.harness.ngsubscriptions.entity.TotalAccountUsers;
import io.harness.repositories.ng.serviceaccounts.ServiceAccountRepository;
import io.harness.repositories.ngsubscriptions.spring.TotalAccountUsersRepository;
import io.harness.repositories.user.spring.UserMembershipRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import okhttp3.Request;
import okio.Timeout;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@OwnedBy(PL)
public class TotalAccountUsersEntityUpdaterTest {
  @Mock private TotalAccountUsersRepository totalAccountUsersRepository;
  @Mock private PersistentLocker persistentLocker;
  @Mock private UserMembershipRepository userMembershipRepository;
  @Mock private ServiceAccountRepository serviceAccountRepository;
  @Mock private AccountResourceNGClient accountResourceNGClient;
  TotalAccountUsersEntityUpdater totalAccountUsersEntityUpdater;
  Call<RestResponse<PageResponse<AccountDTO>>> responseCall;

  int year;
  int month;

  private static final String TEST_ACCOUNT = "testAccount";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    totalAccountUsersEntityUpdater = new TotalAccountUsersEntityUpdater(totalAccountUsersRepository, persistentLocker,
        userMembershipRepository, serviceAccountRepository, accountResourceNGClient);

    Calendar calendar = Calendar.getInstance();
    year = calendar.get(Calendar.YEAR);
    month = calendar.get(Calendar.MONTH);
    List<AccountDTO> accountDTOS = new ArrayList<>();
    AccountDTO accountDTO = AccountDTO.builder().identifier(TEST_ACCOUNT).build();
    accountDTOS.add(accountDTO);
    PageResponse<AccountDTO> pageResponse = PageResponseBuilder.aPageResponse().withResponse(accountDTOS).build();
    responseCall = new Call<RestResponse<PageResponse<AccountDTO>>>() {
      @Override
      public Response<RestResponse<PageResponse<AccountDTO>>> execute() throws IOException {
        RestResponse<PageResponse<AccountDTO>> restResponse = new RestResponse<>(pageResponse);
        return Response.success(restResponse);
      }

      @Override
      public void enqueue(Callback<RestResponse<PageResponse<AccountDTO>>> callback) {}

      @Override
      public boolean isExecuted() {
        return false;
      }

      @Override
      public void cancel() {}

      @Override
      public boolean isCanceled() {
        return false;
      }

      @Override
      public Call<RestResponse<PageResponse<AccountDTO>>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }

      @Override
      public Timeout timeout() {
        return null;
      }
    };
  }

  @Test
  @Owner(developers = PRAVEEN_SOLANKI)
  @Category(UnitTests.class)
  public void testFirstUpdate() {
    TotalAccountUsers expectedEntity = TotalAccountUsers.builder()
                                           .accountIdentifier(TEST_ACCOUNT)
                                           .year(year)
                                           .month(month)
                                           .users(1l)
                                           .serviceAccounts(0l)
                                           .build();
    Criteria criteria = Criteria.where(UserMembershipKeys.accountIdentifier)
                            .is(TEST_ACCOUNT)
                            .and(UserMembershipKeys.parentUniqueId)
                            .is(TEST_ACCOUNT);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(any(), any()))
        .thenReturn(AcquiredNoopLock.builder().build());
    when(accountResourceNGClient.getAccounts(0, 1000)).thenReturn(responseCall);
    when(totalAccountUsersRepository.findByAccountIdentifierAndYearAndMonth(TEST_ACCOUNT, year, month))
        .thenReturn(Optional.empty());
    when(userMembershipRepository.count(criteria)).thenReturn(1L);
    when(serviceAccountRepository.countByAccountIdentifier(TEST_ACCOUNT)).thenReturn(0L);
    when(totalAccountUsersRepository.save(expectedEntity)).thenReturn(expectedEntity);
    totalAccountUsersEntityUpdater.run();

    verify(totalAccountUsersRepository, times(1))
        .findByAccountIdentifierAndYearAndMonth(eq(TEST_ACCOUNT), eq(year), eq(month));
    verify(userMembershipRepository, times(1)).count(eq(criteria));
    verify(serviceAccountRepository, times(1)).countByAccountIdentifier(eq(TEST_ACCOUNT));
    verify(totalAccountUsersRepository, times(1)).save(eq(expectedEntity));
  }
  @Test
  @Owner(developers = PRAVEEN_SOLANKI)
  @Category(UnitTests.class)
  public void testUpdateExistingValue() {
    TotalAccountUsers previousEntity = TotalAccountUsers.builder()
                                           .accountIdentifier(TEST_ACCOUNT)
                                           .year(year)
                                           .month(month)
                                           .users(1l)
                                           .serviceAccounts(0l)
                                           .build();
    TotalAccountUsers expectedEntity = TotalAccountUsers.builder()
                                           .accountIdentifier(TEST_ACCOUNT)
                                           .year(year)
                                           .month(month)
                                           .users(10l)
                                           .serviceAccounts(5l)
                                           .build();
    Criteria criteria = Criteria.where(UserMembershipKeys.accountIdentifier)
                            .is(TEST_ACCOUNT)
                            .and(UserMembershipKeys.parentUniqueId)
                            .is(TEST_ACCOUNT);
    when(persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(any(), any()))
        .thenReturn(AcquiredNoopLock.builder().build());
    when(accountResourceNGClient.getAccounts(0, 1000)).thenReturn(responseCall);
    when(totalAccountUsersRepository.findByAccountIdentifierAndYearAndMonth(TEST_ACCOUNT, year, month))
        .thenReturn(Optional.of(previousEntity));
    when(userMembershipRepository.count(criteria)).thenReturn(10L);
    when(serviceAccountRepository.countByAccountIdentifier(TEST_ACCOUNT)).thenReturn(5L);
    when(totalAccountUsersRepository.save(expectedEntity)).thenReturn(expectedEntity);
    totalAccountUsersEntityUpdater.run();

    verify(totalAccountUsersRepository, times(1))
        .findByAccountIdentifierAndYearAndMonth(eq(TEST_ACCOUNT), eq(year), eq(month));
    verify(userMembershipRepository, times(1)).count(eq(criteria));
    verify(serviceAccountRepository, times(1)).countByAccountIdentifier(eq(TEST_ACCOUNT));
    verify(totalAccountUsersRepository, times(1)).save(eq(expectedEntity));
  }
}
