/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.user.UserInfo;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.user.remote.UserClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class FmeOwnerResolverTest extends CategoryTest {
  @Mock private UserClient userClient;
  @Mock private Call<RestResponse<Optional<UserInfo>>> mockCall;

  private FmeOwnerResolver fmeOwnerResolver;

  private static final String TEST_EMAIL = "john@company.com";
  private static final String TEST_UUID = "550e8400-e29b-41d4-a716-446655440000";

  @Before
  public void setup() {
    fmeOwnerResolver = new FmeOwnerResolver(userClient);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testResolveOwners_EmailResolvedToUuid() throws Exception {
    UserInfo userInfo = UserInfo.builder().uuid(TEST_UUID).email(TEST_EMAIL).build();
    when(userClient.getUserByEmailId(TEST_EMAIL)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    List<String> result = fmeOwnerResolver.resolveOwners(Arrays.asList(TEST_EMAIL));

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo(TEST_UUID);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testResolveOwners_UuidPassedThrough() {
    List<String> result = fmeOwnerResolver.resolveOwners(Arrays.asList(TEST_UUID));

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo(TEST_UUID);
    verify(userClient, never()).getUserByEmailId(anyString());
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testResolveOwners_PrefixedOwnerPassedThrough() {
    List<String> result = fmeOwnerResolver.resolveOwners(Arrays.asList("User:abc-123", "Group:xyz-456"));

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).isEqualTo("User:abc-123");
    assertThat(result.get(1)).isEqualTo("Group:xyz-456");
    verify(userClient, never()).getUserByEmailId(anyString());
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testResolveOwners_EmailNotFound_PassedThrough() throws Exception {
    when(userClient.getUserByEmailId(TEST_EMAIL)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.empty())));

    List<String> result = fmeOwnerResolver.resolveOwners(Arrays.asList(TEST_EMAIL));

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo(TEST_EMAIL);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testResolveOwners_ApiThrowsException_PassedThrough() throws Exception {
    when(userClient.getUserByEmailId(TEST_EMAIL)).thenReturn(mockCall);
    when(mockCall.execute()).thenThrow(new RuntimeException("Connection refused"));

    List<String> result = fmeOwnerResolver.resolveOwners(Arrays.asList(TEST_EMAIL));

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo(TEST_EMAIL);
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testResolveOwners_MixedList() throws Exception {
    String email2 = "jane@company.com";
    String uuid2 = "660e8400-e29b-41d4-a716-446655440000";

    UserInfo userInfo1 = UserInfo.builder().uuid(TEST_UUID).email(TEST_EMAIL).build();
    UserInfo userInfo2 = UserInfo.builder().uuid(uuid2).email(email2).build();

    Call<RestResponse<Optional<UserInfo>>> mockCall2 = org.mockito.Mockito.mock(Call.class);

    when(userClient.getUserByEmailId(TEST_EMAIL)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo1))));

    when(userClient.getUserByEmailId(email2)).thenReturn(mockCall2);
    when(mockCall2.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo2))));

    List<String> result =
        fmeOwnerResolver.resolveOwners(Arrays.asList(TEST_EMAIL, "User:existing-id", email2, "bare-uuid-123"));

    assertThat(result).hasSize(4);
    assertThat(result.get(0)).isEqualTo(TEST_UUID);
    assertThat(result.get(1)).isEqualTo("User:existing-id");
    assertThat(result.get(2)).isEqualTo(uuid2);
    assertThat(result.get(3)).isEqualTo("bare-uuid-123");
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testResolveOwners_EmptyList() {
    List<String> result = fmeOwnerResolver.resolveOwners(Collections.emptyList());
    assertThat(result).isEmpty();
    verify(userClient, never()).getUserByEmailId(anyString());
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testResolveOwners_NullList() {
    List<String> result = fmeOwnerResolver.resolveOwners(null);
    assertThat(result).isEmpty();
    verify(userClient, never()).getUserByEmailId(anyString());
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testResolveOwners_AtSignOnly_NotTreatedAsEmail() {
    List<String> result = fmeOwnerResolver.resolveOwners(Arrays.asList("@"));
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo("@");
    verify(userClient, never()).getUserByEmailId(anyString());
  }

  @Test
  @Owner(developers = OwnerRule.KESHAV)
  @Category(UnitTests.class)
  public void testResolveOwners_AtSignAtEnd_NotTreatedAsEmail() {
    List<String> result = fmeOwnerResolver.resolveOwners(Arrays.asList("user@"));
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo("user@");
    verify(userClient, never()).getUserByEmailId(anyString());
  }
}
