/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;

import static io.harness.NGConstants.CREATED;
import static io.harness.NGConstants.LAST_MODIFIED;
import static io.harness.NGConstants.LOCATION;
import static io.harness.NGConstants.RESOURCE_TYPE;
import static io.harness.NGConstants.VERSION;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PL_NEW_SCIM_STANDARDS;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.NIYASHA;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupUpdateRequest;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rule.Owner;
import io.harness.scim.Member;
import io.harness.scim.PatchOperation;
import io.harness.scim.PatchRequest;
import io.harness.scim.ScimGroup;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimMultiValuedObject;
import io.harness.utils.PmsFeatureFlagHelper;

import io.vavr.collection.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.util.CloseableIterator;
@OwnedBy(PL)
public class NGScimGroupServiceImplTest extends NgManagerTestBase {
  private static final Integer MAX_RESULT_COUNT = 20;

  private UserGroupService userGroupService;
  private NgUserService ngUserService;
  private ScopeInfoService scopeInfoService;

  private NGScimGroupServiceImpl scimGroupService;
  private PmsFeatureFlagHelper ngFeatureFlagHelperService;

  @Before
  public void setup() throws IllegalAccessException {
    ngUserService = mock(NgUserService.class);
    userGroupService = mock(UserGroupService.class);
    ngFeatureFlagHelperService = mock(PmsFeatureFlagHelper.class);
    scopeInfoService = mock(ScopeInfoService.class);

    scimGroupService =
        new NGScimGroupServiceImpl(userGroupService, ngUserService, scopeInfoService, ngFeatureFlagHelperService);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateGroup() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("displayname");
    scimGroup.setId("id");

    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .parentUniqueId(accountId)
                              .accountIdentifier(accountId)
                              .identifier(scimGroup.getDisplayName().replaceAll("\\.", "_"))
                              .build();

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());

    when(scopeInfoService.getScopeInfo(accountId, uniqueIds)).thenReturn(scopeInfoMap);
    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateGroup_shouldReturnMeta_ifFFTurnedOn() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("displayname");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .identifier(scimGroup.getDisplayName().replaceAll("\\.", "_"))
                              .build();
    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    when(ngFeatureFlagHelperService.isEnabled(accountId, PL_NEW_SCIM_STANDARDS)).thenReturn(true);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName());
    assertNotNull(userGroupCreated.getMeta());
    assertNotNull(userGroupCreated.getMeta().get(RESOURCE_TYPE));
    assertNotNull(userGroupCreated.getMeta().get(CREATED));
    assertNotNull(userGroupCreated.getMeta().get(LAST_MODIFIED));
    assertNotNull(userGroupCreated.getMeta().get(LOCATION));
    assertNotNull(userGroupCreated.getMeta().get(VERSION));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateGroup_shouldNotReturnMeta_ifFFTurnedOff() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("displayname");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .identifier(scimGroup.getDisplayName().replaceAll("\\.", "_"))
                              .build();
    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    when(ngFeatureFlagHelperService.isEnabled(accountId, PL_NEW_SCIM_STANDARDS)).thenReturn(false);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName());
    assertNull(userGroupCreated.getMeta());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateGroup2() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display.name");
    scimGroup.setId("id");

    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .identifier(scimGroup.getDisplayName().replaceAll("\\.", "_"))
                              .build();
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName().replaceAll("\\.", "_"));
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateGroup4() {
    String accountId = "accountId";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display_name");
    scimGroup.setId("id");
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .identifier(scimGroup.getDisplayName().replaceAll("\\.", "_"))
                              .build();
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName().replaceAll("\\.", "_"));
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateGroup3() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display.name");
    scimGroup.setId("id");

    when(userGroupService.createForSCIM(any(), any())).thenReturn(null);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNull();
    assertThat(userGroupCreated.getId()).isNull();
    assertThat(userGroupCreated.getMembers()).isNull();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchGroupByName() {
    String accountId = "accountId";
    Integer count = 1;
    Integer startIndex = 1;

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    UserGroup userGroup1 = UserGroup.builder()
                               .name(scimGroup.getDisplayName())
                               .accountIdentifier(accountId)
                               .parentUniqueId(accountId)
                               .identifier(scimGroup.getId())
                               .build();

    when(userGroupService.list(any(Criteria.class), any(), any())).thenReturn(new ArrayList<UserGroup>() {
      { add(userGroup1); }
    });

    ScimListResponse<ScimGroup> response =
        scimGroupService.searchGroup("displayName eq \"testDisplayName\"", accountId, count, startIndex);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(startIndex);
    assertThat(response.getItemsPerPage()).isEqualTo(count);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testSearchGroup_returnsNotNullRefInMembers() {
    String accountId = "accountId";
    Integer count = 1;
    Integer startIndex = 1;

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());

    UserGroup userGroup1 = UserGroup.builder()
                               .accountIdentifier(accountId)
                               .parentUniqueId(accountId)
                               .name(scimGroup.getDisplayName())
                               .identifier(scimGroup.getId())
                               .build();
    UserMetadata userMetadata = UserMetadata.builder().name("testName").email("dummy@gmail.com").userId("UUID").build();

    when(userGroupService.list(any(Criteria.class), any(), any())).thenReturn(new ArrayList<UserGroup>() {
      { add(userGroup1); }
    });

    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    when(userGroupService.getUsersInUserGroup(any(ScopeInfo.class), any()))
        .thenReturn(createCloseableIterator(List.of(userMetadata).iterator()).stream());

    ScimListResponse<ScimGroup> response = scimGroupService.searchGroup(null, accountId, count, startIndex);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(startIndex);
    assertThat(response.getItemsPerPage()).isEqualTo(count);
    ScimGroup scimGroup1 = response.getResources().get(0);
    Member member = scimGroup1.getMembers().get(0);
    assertNotNull(member);
    assertNotNull(member.getRef());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSearchGroupByName_WithStartIndexAndCountAsNULL() {
    String accountId = "accountId";
    Integer count = null;
    Integer startIndex = null;

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    UserGroup userGroup1 = UserGroup.builder()
                               .accountIdentifier(accountId)
                               .parentUniqueId(accountId)
                               .name(scimGroup.getDisplayName())
                               .identifier(scimGroup.getId())
                               .build();

    when(userGroupService.list(any(Criteria.class), any(), any())).thenReturn(new ArrayList<UserGroup>() {
      { add(userGroup1); }
    });

    ScimListResponse<ScimGroup> response =
        scimGroupService.searchGroup("displayName eq \"testDisplayName\"", accountId, count, startIndex);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(0);
    assertThat(response.getItemsPerPage()).isEqualTo(MAX_RESULT_COUNT);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchGroupByNameNoSkipNoCountReturnsDefaultResult() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    UserGroup userGroup1 = UserGroup.builder()
                               .name(scimGroup.getDisplayName())
                               .accountIdentifier(accountId)
                               .parentUniqueId(accountId)
                               .identifier(scimGroup.getId())
                               .build();

    when(userGroupService.list(any(Criteria.class), any(), any())).thenReturn(new ArrayList<>() {
      { add(userGroup1); }
    });

    ScimListResponse<ScimGroup> response =
        scimGroupService.searchGroup("displayName eq \"testDisplayName\"", accountId, null, null);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testNoSearchQueryNoSkipNoCountReturnsDefaultResult() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    UserGroup userGroup1 = UserGroup.builder()
                               .name(scimGroup.getDisplayName())
                               .accountIdentifier(accountId)
                               .parentUniqueId(accountId)
                               .identifier(scimGroup.getId())
                               .build();

    when(userGroupService.list(any(Criteria.class), any(), any())).thenReturn(new ArrayList<>() {
      { add(userGroup1); }
    });

    ScimListResponse<ScimGroup> response = scimGroupService.searchGroup(null, accountId, null, null);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(0);
    assertThat(response.getItemsPerPage()).isEqualTo(20);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testNoSearchQueryNoSkipWithCountReturnsDefaultResult() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("testDisplayName");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    UserGroup userGroup1 = UserGroup.builder()
                               .name(scimGroup.getDisplayName())
                               .accountIdentifier(accountId)
                               .parentUniqueId(accountId)
                               .identifier(scimGroup.getId())
                               .build();

    when(userGroupService.list(any(Criteria.class), any(), any())).thenReturn(new ArrayList<>() {
      { add(userGroup1); }
    });

    int startIdx = 5;
    ScimListResponse<ScimGroup> response = scimGroupService.searchGroup(null, accountId, startIdx, 0);

    assertThat(response.getTotalResults()).isEqualTo(1);
    assertThat(response.getStartIndex()).isEqualTo(0);
    assertThat(response.getItemsPerPage()).isEqualTo(startIdx);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testNoSkipNoCountNoSearchQueryReturnsDefaultResult() {
    String accountId = "accountId";

    UserGroup userGroup1 =
        UserGroup.builder().name("testDisplayName").identifier("testId").externallyManaged(false).build();

    when(userGroupService.list(any(Criteria.class), any(), any()))
        .thenReturn(
            new ArrayList<>()); // the above user group 'usergroup1' wont be returned as it is not externallyManaged

    ScimListResponse<ScimGroup> response = scimGroupService.searchGroup(null, accountId, null, null);

    assertThat(response.getTotalResults()).isEqualTo(0);
    assertThat(response.getStartIndex()).isEqualTo(0);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_createUserGroupForDash1() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("displayname");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    String userGroupId = scimGroup.getDisplayName().replaceAll("-", "_");
    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .identifier(userGroupId)
                              .build();
    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_createUserGroupForDash2() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display-name");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    String userGroupId = scimGroup.getDisplayName().replaceAll("-", "_");
    UserGroup userGroup = UserGroup.builder()
                              .name(scimGroup.getDisplayName())
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .identifier(userGroupId)
                              .build();
    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(userGroupId);
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_createUserGroupForDash3() {
    String accountId = "accountId";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display_name");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    String userGroupId = scimGroup.getDisplayName().replaceAll("-", "_");
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .name(scimGroup.getDisplayName())
                              .identifier(userGroupId)
                              .build();
    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(userGroupId);
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_createUserGroupForDash4() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display-name");
    scimGroup.setId("id");

    when(userGroupService.createForSCIM(any(), any())).thenReturn(null);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNull();
    assertThat(userGroupCreated.getId()).isNull();
    assertThat(userGroupCreated.getMembers()).isNull();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void test_createUserGroupForSpace1() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("displayname");
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    String userGroupId = scimGroup.getDisplayName().replaceAll(" ", "_");
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .name(scimGroup.getDisplayName())
                              .identifier(userGroupId)
                              .build();
    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(scimGroup.getDisplayName());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void test_createUserGroupForSpace2() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display name");
    scimGroup.setId("id");
    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    String userGroupId = scimGroup.getDisplayName().replaceAll(" ", "_");
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .name(scimGroup.getDisplayName())
                              .identifier(userGroupId)
                              .build();
    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(userGroupId);
    assertThat(userGroupCreated.getId()).isEqualTo("display_name");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void test_createUserGroupForDotAndHyphen() {
    final String nameIdentifier = "test.display-name";
    String accountId = "accountId";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName(nameIdentifier);
    scimGroup.setId("id");

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    String userGroupId = nameIdentifier.replaceAll(" ", "_").replaceAll("\\.", "_").replaceAll("-", "_");
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .name(scimGroup.getDisplayName())
                              .identifier(userGroupId)
                              .build();
    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(nameIdentifier);
    assertThat(userGroupCreated.getId()).isEqualTo(userGroupId);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void test_createUserGroupForSpace4() {
    String accountId = "accountId";

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName("display name");
    scimGroup.setId("id");

    when(userGroupService.createForSCIM(any(), any())).thenReturn(null);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNull();
    assertThat(userGroupCreated.getId()).isNull();
    assertThat(userGroupCreated.getMembers()).isNull();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void test_createSCIMUserGroupInvalidSpecialCharacters() {
    String accountId = "accountId";
    final String invalidName = "display?INVALID#name!";

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(scopeInfo.getUniqueId());
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName(invalidName);
    scimGroup.setId("id");

    String userGroupId = scimGroup.getDisplayName().replaceAll("\\?", "").replaceAll("#", "").replaceAll("!", "");
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(accountId)
                              .parentUniqueId(accountId)
                              .name(scimGroup.getDisplayName())
                              .identifier(userGroupId)
                              .build();
    when(userGroupService.createForSCIM(any(), any())).thenReturn(userGroup);
    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getDisplayName()).isNotNull();
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getId()).isEqualTo(userGroupId);
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(scimGroup.getDisplayName());
    assertThat(userGroupCreated.getDisplayName()).isEqualTo(invalidName);
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testCreateGroup_appendsHashWhenDisplayNameStartsWithDigits() {
    String accountId = "accountId";
    String displayName = "123_displayname";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName(displayName);

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);
    when(userGroupService.get(any(), any())).thenReturn(Optional.empty());
    when(userGroupService.getUsersInUserGroup(any(ScopeInfo.class), any()))
        .thenReturn(createCloseableIterator(new ArrayList<UserMetadata>().iterator()).stream());
    when(userGroupService.createForSCIM(any(), any())).thenAnswer(invocation -> {
      UserGroupDTO userGroupDTO = invocation.getArgument(1);
      return UserGroup.builder()
          .name(userGroupDTO.getName())
          .accountIdentifier(accountId)
          .parentUniqueId(accountId)
          .identifier(userGroupDTO.getIdentifier())
          .build();
    });

    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getId()).matches("_displayname_[0-9a-f]{8}");
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testCreateGroup_reusesLegacyGroupWhenPresentForNormalizedName() {
    String accountId = "accountId";
    String displayName = "123_displayname";
    String legacyIdentifier = "_displayname";
    String hashedIdentifierPrefix = "_displayname_";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName(displayName);

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    UserGroup existingLegacyGroup = UserGroup.builder()
                                        .name(displayName)
                                        .accountIdentifier(accountId)
                                        .parentUniqueId(accountId)
                                        .identifier(legacyIdentifier)
                                        .externallyManaged(true)
                                        .build();
    when(userGroupService.get(any(), any())).thenAnswer(invocation -> {
      String identifier = invocation.getArgument(1);
      if (identifier != null && identifier.startsWith(hashedIdentifierPrefix)) {
        return Optional.empty();
      }
      return legacyIdentifier.equals(identifier) ? Optional.of(existingLegacyGroup) : Optional.empty();
    });
    when(userGroupService.getUsersInUserGroup(any(ScopeInfo.class), any()))
        .thenReturn(createCloseableIterator(new ArrayList<UserMetadata>().iterator()).stream());

    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    verify(userGroupService, never()).createForSCIM(any(), any());
    assertThat(userGroupCreated.getId()).isEqualTo(legacyIdentifier);
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testCreateGroup_limitsIdentifierLengthForLongDisplayName() {
    String accountId = "accountId";
    String displayName = "1team-"
        + "a".repeat(220);
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName(displayName);

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);
    when(userGroupService.get(any(), any())).thenReturn(Optional.empty());
    when(userGroupService.getUsersInUserGroup(any(ScopeInfo.class), any()))
        .thenReturn(createCloseableIterator(new ArrayList<UserMetadata>().iterator()).stream());
    when(userGroupService.createForSCIM(any(), any())).thenAnswer(invocation -> {
      UserGroupDTO userGroupDTO = invocation.getArgument(1);
      return UserGroup.builder()
          .name(userGroupDTO.getName())
          .accountIdentifier(accountId)
          .parentUniqueId(accountId)
          .identifier(userGroupDTO.getIdentifier())
          .build();
    });

    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    ArgumentCaptor<UserGroupDTO> userGroupDTOCaptor = ArgumentCaptor.forClass(UserGroupDTO.class);
    verify(userGroupService).createForSCIM(any(), userGroupDTOCaptor.capture());
    String identifier = userGroupDTOCaptor.getValue().getIdentifier();
    assertThat(identifier.length()).isLessThanOrEqualTo(128);
    assertThat(identifier).matches(".*_[0-9a-f]{8}");
    assertThat(userGroupCreated.getId()).isEqualTo(identifier);
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testCreateGroup_doesNotAppendHashWhenNormalizationNotRequired() {
    String accountId = "accountId";
    String displayName = "display_name";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName(displayName);

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);
    when(userGroupService.get(any(), any())).thenReturn(Optional.empty());
    when(userGroupService.getUsersInUserGroup(any(ScopeInfo.class), any()))
        .thenReturn(createCloseableIterator(new ArrayList<UserMetadata>().iterator()).stream());
    when(userGroupService.createForSCIM(any(), any())).thenAnswer(invocation -> {
      UserGroupDTO userGroupDTO = invocation.getArgument(1);
      return UserGroup.builder()
          .name(userGroupDTO.getName())
          .accountIdentifier(accountId)
          .parentUniqueId(accountId)
          .identifier(userGroupDTO.getIdentifier())
          .build();
    });

    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getId()).isEqualTo(displayName);
    assertThat(userGroupCreated.getId()).doesNotMatch(".*_[0-9a-f]{8}");
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testCreateGroup_usesExistingHashedGroupWithoutFallbackLookup() {
    String accountId = "accountId";
    String displayName = "123_displayname";
    String hashedIdentifier = "_displayname_abcd1234";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName(displayName);

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);

    UserGroup existingHashedGroup = UserGroup.builder()
                                        .name(displayName)
                                        .accountIdentifier(accountId)
                                        .parentUniqueId(accountId)
                                        .identifier(hashedIdentifier)
                                        .externallyManaged(true)
                                        .build();
    when(userGroupService.get(any(), any())).thenAnswer(invocation -> {
      String identifier = invocation.getArgument(1);
      return identifier != null && identifier.startsWith("_displayname_") ? Optional.of(existingHashedGroup)
                                                                          : Optional.empty();
    });
    when(userGroupService.getUsersInUserGroup(any(ScopeInfo.class), any()))
        .thenReturn(createCloseableIterator(new ArrayList<UserMetadata>().iterator()).stream());

    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    verify(userGroupService, times(1)).get(any(), any());
    verify(userGroupService, never()).createForSCIM(any(), any());
    assertThat(userGroupCreated.getId()).isEqualTo(hashedIdentifier);
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testCreateGroup_doesSingleLookupWhenLegacyAndNewIdentifierSame() {
    String accountId = "accountId";
    String displayName = "display_name";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName(displayName);

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);
    when(userGroupService.get(any(), any())).thenReturn(Optional.empty());
    when(userGroupService.getUsersInUserGroup(any(ScopeInfo.class), any()))
        .thenReturn(createCloseableIterator(new ArrayList<UserMetadata>().iterator()).stream());
    when(userGroupService.createForSCIM(any(), any())).thenAnswer(invocation -> {
      UserGroupDTO userGroupDTO = invocation.getArgument(1);
      return UserGroup.builder()
          .name(userGroupDTO.getName())
          .accountIdentifier(accountId)
          .parentUniqueId(accountId)
          .identifier(userGroupDTO.getIdentifier())
          .build();
    });

    scimGroupService.createGroup(scimGroup, accountId);

    ArgumentCaptor<String> identifierCaptor = ArgumentCaptor.forClass(String.class);
    verify(userGroupService, times(1)).get(any(), identifierCaptor.capture());
    assertThat(identifierCaptor.getValue()).isEqualTo(displayName);
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testCreateGroup_doesNotAppendHashWhenDigitsAreNotPrefix() {
    String accountId = "accountId";
    String displayName = "displayName_123";
    ScimGroup scimGroup = new ScimGroup();
    scimGroup.setDisplayName(displayName);

    ScopeInfo scopeInfo = ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfo.getUniqueId(), Optional.of(scopeInfo));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoMap);
    when(userGroupService.get(any(), any())).thenReturn(Optional.empty());
    when(userGroupService.getUsersInUserGroup(any(ScopeInfo.class), any()))
        .thenReturn(createCloseableIterator(new ArrayList<UserMetadata>().iterator()).stream());
    when(userGroupService.createForSCIM(any(), any())).thenAnswer(invocation -> {
      UserGroupDTO userGroupDTO = invocation.getArgument(1);
      return UserGroup.builder()
          .name(userGroupDTO.getName())
          .accountIdentifier(accountId)
          .parentUniqueId(accountId)
          .identifier(userGroupDTO.getIdentifier())
          .build();
    });

    ScimGroup userGroupCreated = scimGroupService.createGroup(scimGroup, accountId);

    assertThat(userGroupCreated.getId()).isEqualTo(displayName);
    assertThat(userGroupCreated.getId()).doesNotMatch(".*_[0-9a-f]{8}");
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testUpdateGroup_withMembersPath_callsProcessMemberReplaceOperation() throws Exception {
    String groupId = "testGroup";
    String accountId = "testAccount";

    UserGroup existingUserGroup = UserGroup.builder()
                                      .identifier(groupId)
                                      .name("Test Group")
                                      .accountIdentifier(accountId)
                                      .externallyManaged(true)
                                      .build();

    java.util.List<UserGroup> existingUserGroupList = Arrays.asList(existingUserGroup);

    @SuppressWarnings("rawtypes") ScimMultiValuedObject user1 = mock(ScimMultiValuedObject.class);
    @SuppressWarnings("rawtypes") ScimMultiValuedObject user2 = mock(ScimMultiValuedObject.class);
    when(user1.getValue()).thenReturn("user1");
    when(user2.getValue()).thenReturn("user2");

    PatchOperation patchOperation = mock(PatchOperation.class);
    when(patchOperation.getOpType()).thenReturn("Replace");
    when(patchOperation.getPath()).thenReturn("members");
    java.util.List<ScimMultiValuedObject> values = Arrays.asList(user1, user2);
    when(patchOperation.getValues(ScimMultiValuedObject.class)).thenReturn(values);

    PatchRequest patchRequest = mock(PatchRequest.class);
    java.util.List<PatchOperation> operations = java.util.Arrays.asList(patchOperation);
    when(patchRequest.getOperations()).thenReturn(operations);

    when(userGroupService.list(any(Criteria.class), any(), any())).thenReturn(existingUserGroupList);
    when(userGroupService.getUsersInUserGroup(any(ScopeInfo.class), eq(groupId)))
        .thenReturn(createCloseableIterator(new ArrayList<UserMetadata>().iterator()).stream());

    when(ngFeatureFlagHelperService.isEnabled(eq(accountId), any(String.class))).thenReturn(false);

    Response response = scimGroupService.updateGroup(groupId, accountId, patchRequest);

    verify(userGroupService, times(1)).update(any(ScopeInfo.class), any(UserGroupUpdateRequest.class));
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }
  private <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<T>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }
}
