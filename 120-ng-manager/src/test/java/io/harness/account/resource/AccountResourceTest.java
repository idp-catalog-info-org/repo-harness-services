/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.account.resource;

import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.AccountConfig;
import io.harness.account.services.AccountClient;
import io.harness.beans.FeatureFlag;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class AccountResourceTest extends CategoryTest {
  @Mock private AccountClient accountClient;
  @Mock private AccountConfig accountConfig;
  @Mock private NgLicenseHttpClient ngLicenseHttpClient;
  @Mock private Call<RestResponse<Collection<FeatureFlag>>> featureFlagCall;

  private AccountResource accountResource;

  private static final String TEST_ACCOUNT_ID = "test-account-id";

  @Before
  public void setup() {
    accountResource = new AccountResource(accountClient, accountConfig, ngLicenseHttpClient);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void getEnabledFeatureFlags_WhenBothEnabledAndDisabledFlags_ThenReturnOnlyEnabledFlags() throws IOException {
    FeatureFlag enabledFlag1 = FeatureFlag.builder()
                                   .uuid("uuid-1")
                                   .name("FEATURE_1")
                                   .enabled(true)
                                   .obsolete(false)
                                   .lastUpdatedAt(System.currentTimeMillis())
                                   .build();

    FeatureFlag enabledFlag2 = FeatureFlag.builder()
                                   .uuid("uuid-2")
                                   .name("FEATURE_2")
                                   .enabled(true)
                                   .obsolete(false)
                                   .lastUpdatedAt(System.currentTimeMillis())
                                   .build();

    FeatureFlag disabledFlag = FeatureFlag.builder()
                                   .uuid("uuid-3")
                                   .name("FEATURE_3")
                                   .enabled(false)
                                   .obsolete(false)
                                   .lastUpdatedAt(System.currentTimeMillis())
                                   .build();

    Collection<FeatureFlag> allFlags = Arrays.asList(enabledFlag1, disabledFlag, enabledFlag2);

    when(accountClient.listAllFeatureFlagsForAccount(TEST_ACCOUNT_ID)).thenReturn(featureFlagCall);
    when(featureFlagCall.execute()).thenReturn(Response.success(new RestResponse<>(allFlags)));

    ResponseDTO<Collection<FeatureFlag>> response = accountResource.getEnabledFeatureFlags(TEST_ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData()).hasSize(2);
    assertThat(response.getData()).containsExactlyInAnyOrder(enabledFlag1, enabledFlag2);
    assertThat(response.getData()).doesNotContain(disabledFlag);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void getEnabledFeatureFlags_WhenAllFlagsDisabled_ThenReturnEmptyList() throws IOException {
    FeatureFlag disabledFlag1 = FeatureFlag.builder().name("FEATURE_1").enabled(false).build();
    FeatureFlag disabledFlag2 = FeatureFlag.builder().name("FEATURE_2").enabled(false).build();

    Collection<FeatureFlag> allFlags = Arrays.asList(disabledFlag1, disabledFlag2);

    when(accountClient.listAllFeatureFlagsForAccount(TEST_ACCOUNT_ID)).thenReturn(featureFlagCall);
    when(featureFlagCall.execute()).thenReturn(Response.success(new RestResponse<>(allFlags)));

    ResponseDTO<Collection<FeatureFlag>> response = accountResource.getEnabledFeatureFlags(TEST_ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData()).isEmpty();
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void getEnabledFeatureFlags_WhenBadRequestThen_ThrowInvalidRequestException() throws IOException {
    String errorJson = "{\"responseMessages\":[{\"code\":\"INVALID_REQUEST\",\"message\":\"INVALID_REQUEST: Invalid "
        + "account identifier\"}]}";
    ResponseBody errorBody = ResponseBody.create(MediaType.parse("application/json"), errorJson);

    when(accountClient.listAllFeatureFlagsForAccount(TEST_ACCOUNT_ID)).thenReturn(featureFlagCall);
    when(featureFlagCall.clone()).thenReturn(featureFlagCall);
    when(featureFlagCall.execute()).thenReturn(Response.error(400, errorBody));

    assertThatThrownBy(() -> accountResource.getEnabledFeatureFlags(TEST_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid account identifier");
  }
}
