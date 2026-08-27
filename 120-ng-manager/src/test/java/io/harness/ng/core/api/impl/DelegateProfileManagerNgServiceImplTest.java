/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.filter.FilterType.DELEGATEPROFILE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.DelegateProfileDetailsNg;
import io.harness.delegate.filter.DelegateProfileFilterPropertiesDTO;
import io.harness.delegateprofile.DelegateProfilePageResponseGrpc;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.grpc.DelegateProfileServiceGrpcClient;
import io.harness.owner.OrgIdentifier;
import io.harness.owner.ProjectIdentifier;
import io.harness.paging.PageRequestGrpc;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.DEL)
public class DelegateProfileManagerNgServiceImplTest {
  private static final String TEST_ACCOUNT_ID = generateUuid();
  public static final String TEST_FILTER_ID = "filterId";

  @Mock private DelegateProfileServiceGrpcClient delegateProfileServiceGrpcClient;
  @Mock private FilterService filterService;
  private DelegateProfileManagerNgServiceImpl underTest;

  @Before
  public void setup() throws IllegalAccessException {
    underTest = new DelegateProfileManagerNgServiceImpl(delegateProfileServiceGrpcClient, filterService);
  }

  @Test
  @Owner(developers = OwnerRule.NICOLAS)
  @Category(UnitTests.class)
  public void shouldList() {
    PageRequest<DelegateProfileDetailsNg> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    pageRequest.setLimit("0");
    DelegateProfilePageResponseGrpc delegateProfilePageResponseGrpc =
        DelegateProfilePageResponseGrpc.newBuilder().build();

    when(delegateProfileServiceGrpcClient.listProfiles(
             any(AccountId.class), any(PageRequestGrpc.class), eq(true), any(OrgIdentifier.class), eq(null)))
        .thenReturn(null);
    when(delegateProfileServiceGrpcClient.listProfiles(
             any(AccountId.class), any(PageRequestGrpc.class), eq(true), eq(null), any(ProjectIdentifier.class)))
        .thenReturn(delegateProfilePageResponseGrpc);

    PageResponse<DelegateProfileDetailsNg> delegateProfileDetailsPageResponse =
        underTest.list(TEST_ACCOUNT_ID, pageRequest, "orgId", null);
    assertThat(delegateProfileDetailsPageResponse).isNull();

    delegateProfileDetailsPageResponse = underTest.list(TEST_ACCOUNT_ID, pageRequest, null, "projectId");
    assertThat(delegateProfileDetailsPageResponse).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category(UnitTests.class)
  public void listV2WithFilterShouldReturnList() {
    PageRequest<DelegateProfileDetailsNg> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    pageRequest.setLimit("0");
    DelegateProfilePageResponseGrpc delegateProfilePageResponseGrpc =
        DelegateProfilePageResponseGrpc.newBuilder().build();

    when(delegateProfileServiceGrpcClient.listProfilesV2(eq(""), eq(null), any()))
        .thenReturn(delegateProfilePageResponseGrpc);

    PageResponse<DelegateProfileDetailsNg> delegateProfileDetailsPageResponse =
        underTest.listV2(TEST_ACCOUNT_ID, "orgId", "projectId", "", "", null, pageRequest);
    assertThat(delegateProfileDetailsPageResponse).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category(UnitTests.class)
  public void listV2WithFilterShouldGetExistingFilter() {
    PageRequest<DelegateProfileDetailsNg> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");
    pageRequest.setLimit("0");
    DelegateProfilePageResponseGrpc delegateProfilePageResponseGrpc =
        DelegateProfilePageResponseGrpc.newBuilder().build();

    when(delegateProfileServiceGrpcClient.listProfilesV2(eq(""), eq(null), any()))
        .thenReturn(delegateProfilePageResponseGrpc);
    when(filterService.get(TEST_ACCOUNT_ID, "orgId", "projectId", TEST_FILTER_ID, DELEGATEPROFILE))
        .thenReturn(new FilterDTO());

    PageResponse<DelegateProfileDetailsNg> delegateProfileDetailsPageResponse =
        underTest.listV2(TEST_ACCOUNT_ID, "orgId", "projectId", TEST_FILTER_ID, "", null, pageRequest);

    verify(filterService).get(TEST_ACCOUNT_ID, "orgId", "projectId", TEST_FILTER_ID, DELEGATEPROFILE);
    assertThat(delegateProfileDetailsPageResponse).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.BOJAN)
  @Category(UnitTests.class)
  public void listV2WithFilterShouldThrowException() {
    PageRequest<DelegateProfileDetailsNg> pageRequest = new PageRequest<>();
    assertThatThrownBy(()
                           -> underTest.listV2(TEST_ACCOUNT_ID, "orgId", "projectId", "filterId", "",
                               DelegateProfileFilterPropertiesDTO.builder().build(), pageRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Can not apply both filter properties and saved filter together");
  }
}
