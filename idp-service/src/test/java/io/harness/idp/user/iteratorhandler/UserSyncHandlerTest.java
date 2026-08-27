/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.user.iteratorhandler;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.user.beans.entity.UserEventEntity;
import io.harness.idp.user.repositories.UserEventRepository;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class UserSyncHandlerTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks private UserSyncHandler handler;
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private IdpCommonService idpCommonService;
  @Mock private UserEventRepository userEventRepository;
  @Mock private BackstageResourceClient backstageResourceClient;
  @Mock private NamespaceService namespaceService;
  private Call<Object> call;
  private static final String TEST_ACCOUNT1 = "acc1";
  private static final String TEST_ACCOUNT1_USER_GROUP = "acc1Ug";
  private static final String TEST_ACCOUNT2 = "acc2";
  private static final String TEST_ACCOUNT2_USER_GROUP = "acc2Ug";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    call = mock(Call.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandle() throws IOException {
    UserEventEntity acc1 = UserEventEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT1)
                               .userGroupIdentifier(TEST_ACCOUNT1_USER_GROUP)
                               .hasEvent(true)
                               .build();
    UserEventEntity acc2 = UserEventEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT2)
                               .userGroupIdentifier(TEST_ACCOUNT2_USER_GROUP)
                               .hasEvent(true)
                               .build();
    when(namespaceService.getAccountIds()).thenReturn(Arrays.asList(TEST_ACCOUNT1, TEST_ACCOUNT2));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT1)).thenReturn(false);
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT2)).thenReturn(false);
    when(userEventRepository.findAllByHasEvent(true)).thenReturn(Arrays.asList(acc1, acc2));
    Response<Object> response = Response.success("Success");
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.providerRefresh(TEST_ACCOUNT1, TEST_ACCOUNT1_USER_GROUP)).thenReturn(call);
    when(backstageResourceClient.providerRefresh(TEST_ACCOUNT2, TEST_ACCOUNT2_USER_GROUP)).thenReturn(call);
    acc1.setHasEvent(false);
    when(userEventRepository.saveOrUpdate(acc1)).thenReturn(acc1);
    acc2.setHasEvent(false);
    when(userEventRepository.saveOrUpdate(acc2)).thenReturn(acc2);
    handler.handle(IteratorEntity.builder().build());
    verify(userEventRepository, times(1)).saveOrUpdate(acc1);
    verify(userEventRepository, times(1)).saveOrUpdate(acc2);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleThrowsException() throws IOException {
    UserEventEntity acc1 = UserEventEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT1)
                               .userGroupIdentifier(TEST_ACCOUNT1_USER_GROUP)
                               .hasEvent(true)
                               .build();
    when(namespaceService.getAccountIds()).thenReturn(Collections.singletonList(TEST_ACCOUNT1));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT1)).thenReturn(false);
    when(userEventRepository.findAllByHasEvent(true)).thenReturn(Collections.singletonList(acc1));
    when(backstageResourceClient.providerRefresh(TEST_ACCOUNT1, TEST_ACCOUNT1_USER_GROUP)).thenReturn(call);
    given(call.execute()).willAnswer(invocation -> { throw new Exception("Exception Throw"); });
    handler.handle(IteratorEntity.builder().build());
    verify(userEventRepository, times(0)).saveOrUpdate(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleWhenActiveAccountsIsEmpty() {
    UserEventEntity acc1 = UserEventEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT1)
                               .userGroupIdentifier(TEST_ACCOUNT1_USER_GROUP)
                               .hasEvent(true)
                               .build();
    when(namespaceService.getAccountIds()).thenReturn(Collections.emptyList());
    when(userEventRepository.findAllByHasEvent(true)).thenReturn(Collections.singletonList(acc1));
    handler.handle(IteratorEntity.builder().build());
    verify(idpCommonService, times(0)).idpV2Enabled(any());
    verify(backstageResourceClient, times(0)).providerRefresh(any(), any());
    verify(userEventRepository, times(0)).saveOrUpdate(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleWhenAccountNotInActiveAccounts() throws IOException {
    UserEventEntity acc1 = UserEventEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT1)
                               .userGroupIdentifier(TEST_ACCOUNT1_USER_GROUP)
                               .hasEvent(true)
                               .build();
    UserEventEntity acc2 = UserEventEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT2)
                               .userGroupIdentifier(TEST_ACCOUNT2_USER_GROUP)
                               .hasEvent(true)
                               .build();
    // Only TEST_ACCOUNT1 is in active accounts, TEST_ACCOUNT2 should be skipped
    when(namespaceService.getAccountIds()).thenReturn(Collections.singletonList(TEST_ACCOUNT1));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT1)).thenReturn(false);
    when(userEventRepository.findAllByHasEvent(true)).thenReturn(Arrays.asList(acc1, acc2));
    Response<Object> response = Response.success("Success");
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.providerRefresh(TEST_ACCOUNT1, TEST_ACCOUNT1_USER_GROUP)).thenReturn(call);
    acc1.setHasEvent(false);
    when(userEventRepository.saveOrUpdate(acc1)).thenReturn(acc1);
    handler.handle(IteratorEntity.builder().build());
    verify(backstageResourceClient, times(1)).providerRefresh(TEST_ACCOUNT1, TEST_ACCOUNT1_USER_GROUP);
    verify(backstageResourceClient, times(0)).providerRefresh(TEST_ACCOUNT2, TEST_ACCOUNT2_USER_GROUP);
    verify(userEventRepository, times(1)).saveOrUpdate(acc1);
    verify(idpCommonService, times(1)).idpV2Enabled(TEST_ACCOUNT1);
    verify(idpCommonService, times(0)).idpV2Enabled(TEST_ACCOUNT2);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleWhenIdpV2Enabled() throws IOException {
    UserEventEntity acc1 = UserEventEntity.builder()
                               .accountIdentifier(TEST_ACCOUNT1)
                               .userGroupIdentifier(TEST_ACCOUNT1_USER_GROUP)
                               .hasEvent(true)
                               .build();
    when(namespaceService.getAccountIds()).thenReturn(Collections.singletonList(TEST_ACCOUNT1));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT1)).thenReturn(true);
    when(userEventRepository.findAllByHasEvent(true)).thenReturn(Collections.singletonList(acc1));
    acc1.setHasEvent(false);
    when(userEventRepository.saveOrUpdate(acc1)).thenReturn(acc1);
    handler.handle(IteratorEntity.builder().build());
    verify(backstageResourceClient, times(0)).providerRefresh(any(), any());
    verify(userEventRepository, times(1)).saveOrUpdate(acc1);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    handler.registerIterators(IteratorConfig.builder().build());
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(UserSyncHandler.class), any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
