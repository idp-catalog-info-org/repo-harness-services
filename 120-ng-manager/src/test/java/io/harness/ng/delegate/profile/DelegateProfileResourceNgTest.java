/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.delegate.profile;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.NICOLAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateProfileDetailsNg;
import io.harness.ng.core.api.DelegateProfileManagerNgService;
import io.harness.ng.core.delegate.resources.DelegateProfileNgResource;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
public class DelegateProfileResourceNgTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = generateUuid();
  private static final String TEST_ORG_ID = generateUuid();
  private static final String TEST_PROJECT_ID = generateUuid();
  private static final String TEST_DELEGATE_PROFILE_ID = generateUuid();

  private DelegateProfileNgResource delegateProfileNgResource;

  @Mock private AccessControlClient accessControlClient;
  @Mock private DelegateProfileManagerNgService delegateProfileManagerNgService;

  @Before
  public void setup() {
    initMocks(this);
    delegateProfileNgResource = new DelegateProfileNgResource(delegateProfileManagerNgService, accessControlClient);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void shouldListDelegateProfiles() {
    PageRequest<DelegateProfileDetailsNg> pageRequest = new PageRequest<>();
    pageRequest.setOffset("0");

    PageResponse<DelegateProfileDetailsNg> pageResponse = new PageResponse<>();
    pageResponse.setResponse(Collections.singletonList(DelegateProfileDetailsNg.builder().build()));
    pageResponse.setTotal(1L);

    when(delegateProfileManagerNgService.list(TEST_ACCOUNT_ID, pageRequest, TEST_ACCOUNT_ID, TEST_PROJECT_ID))
        .thenReturn(pageResponse);

    RestResponse<PageResponse<DelegateProfileDetailsNg>> restResponse =
        delegateProfileNgResource.list(pageRequest, TEST_ACCOUNT_ID, TEST_ACCOUNT_ID, TEST_PROJECT_ID);

    verify(delegateProfileManagerNgService, times(1))
        .list(TEST_ACCOUNT_ID, pageRequest, TEST_ACCOUNT_ID, TEST_PROJECT_ID);
    assertThat(restResponse.getResource().size()).isEqualTo(1);
    assertThat(restResponse.getResource().get(0)).isNotNull();
  }
}
