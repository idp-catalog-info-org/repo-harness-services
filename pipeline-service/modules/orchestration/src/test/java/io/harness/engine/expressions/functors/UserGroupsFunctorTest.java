/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.SIDDHARTHA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EngineFunctorException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserBasicInfo;
import io.harness.ng.core.dto.UserGroupResponseV2DTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.usergroups.UserGroupClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import retrofit2.Call;

@OwnedBy(HarnessTeam.CDP)
@PrepareForTest({NGRestUtils.class})
public class UserGroupsFunctorTest extends CategoryTest {
  @Mock private UserGroupClient userGroupClient;
  @Mock private Call<ResponseDTO<UserGroupResponseV2DTO>> call;
  @InjectMocks private UserGroupsFunctor userGroupsFunctor;

  private Ambiance ambiance = Ambiance.newBuilder().build();
  private Ambiance ambianceWithAccountId = Ambiance.newBuilder()
                                               .putSetupAbstractions("accountId", "testAccountId")
                                               .putSetupAbstractions("orgIdentifier", "testOrg")
                                               .putSetupAbstractions("projectIdentifier", "testProject")
                                               .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testBindWithEmptyAccountId() {
    on(userGroupsFunctor).set("ambiance", ambiance);
    assertNull(userGroupsFunctor.bind());
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testBindWithValidAccountId() {
    on(userGroupsFunctor).set("ambiance", ambianceWithAccountId);
    Object result = userGroupsFunctor.bind();
    assertEquals(userGroupsFunctor, result);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGetWithEmptyGroupIdentifier() {
    on(userGroupsFunctor).set("ambiance", ambianceWithAccountId);
    assertNull(userGroupsFunctor.get(""));
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGetWithValidUserGroup() {
    MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class);
    on(userGroupsFunctor).set("ambiance", ambianceWithAccountId);

    UserBasicInfo user1 = UserBasicInfo.builder().id("user1").email("user1@harness.io").build();
    UserBasicInfo user2 = UserBasicInfo.builder().id("user2").email("user2@harness.io").build();

    UserGroupResponseV2DTO userGroup = UserGroupResponseV2DTO.builder()
                                           .identifier("testGroup")
                                           .name("Test Group")
                                           .users(Arrays.asList(user1, user2))
                                           .build();

    when(userGroupClient.getUserGroupV2(anyString(), anyString(), anyString(), anyString())).thenReturn(call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroup);

    Set<String> result = (Set<String>) userGroupsFunctor.get("testGroup");
    assertEquals(2, result.size());
    assertTrue(result.contains("user1@harness.io"));
    assertTrue(result.contains("user2@harness.io"));

    verify(userGroupClient).getUserGroupV2("testGroup", "testAccountId", "testOrg", "testProject");
    ngRestUtilsMock.close();
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGetWithNullUserGroup() {
    MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class);
    on(userGroupsFunctor).set("ambiance", ambianceWithAccountId);

    when(userGroupClient.getUserGroupV2(anyString(), anyString(), anyString(), anyString())).thenReturn(call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(null);

    Set<String> result = (Set<String>) userGroupsFunctor.get("testGroup");
    assertTrue(result.isEmpty());

    ngRestUtilsMock.close();
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGetWithEmptyUsersList() {
    MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class);
    on(userGroupsFunctor).set("ambiance", ambianceWithAccountId);

    UserGroupResponseV2DTO userGroup = UserGroupResponseV2DTO.builder()
                                           .identifier("testGroup")
                                           .name("Test Group")
                                           .users(Collections.emptyList())
                                           .build();

    when(userGroupClient.getUserGroupV2(anyString(), anyString(), anyString(), anyString())).thenReturn(call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroup);

    Set<String> result = (Set<String>) userGroupsFunctor.get("testGroup");
    assertTrue(result.isEmpty());

    ngRestUtilsMock.close();
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGetWithUsersWithBlankEmails() {
    MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class);
    on(userGroupsFunctor).set("ambiance", ambianceWithAccountId);

    UserBasicInfo user1 = UserBasicInfo.builder().id("user1").email("user1@harness.io").build();
    UserBasicInfo user2 = UserBasicInfo.builder().id("user2").email("").build();
    UserBasicInfo user3 = UserBasicInfo.builder().id("user3").email(null).build();

    UserGroupResponseV2DTO userGroup = UserGroupResponseV2DTO.builder()
                                           .identifier("testGroup")
                                           .name("Test Group")
                                           .users(Arrays.asList(user1, user2, user3))
                                           .build();

    when(userGroupClient.getUserGroupV2(anyString(), anyString(), anyString(), anyString())).thenReturn(call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroup);

    Set<String> result = (Set<String>) userGroupsFunctor.get("testGroup");
    assertEquals(1, result.size());
    assertTrue(result.contains("user1@harness.io"));

    ngRestUtilsMock.close();
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testGetWithException() {
    MockedStatic<NGRestUtils> ngRestUtilsMock = Mockito.mockStatic(NGRestUtils.class);
    on(userGroupsFunctor).set("ambiance", ambianceWithAccountId);

    when(userGroupClient.getUserGroupV2(anyString(), anyString(), anyString(), anyString())).thenReturn(call);
    ngRestUtilsMock.when(() -> NGRestUtils.getResponse(any())).thenThrow(new RuntimeException("Network error"));

    assertThatThrownBy(() -> userGroupsFunctor.get("testGroup"))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessageContaining("Error retrieving UserGroups for group id: testGroup");

    ngRestUtilsMock.close();
  }
}
