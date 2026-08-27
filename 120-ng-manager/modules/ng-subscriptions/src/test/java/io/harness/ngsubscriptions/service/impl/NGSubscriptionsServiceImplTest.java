/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ABEL_MATHEW;
import static io.harness.rule.OwnerRule.NAMAN_GUPTA;
import static io.harness.rule.OwnerRule.PRAVEEN_SOLANKI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.DevopsEssentialsModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.services.DefaultLicenseServiceImpl;
import io.harness.moduleaccess.ModuleRoleAssignmentHelper;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.ngsubscriptions.entity.AccessEntity;
import io.harness.ngsubscriptions.entity.ModuleAccess;
import io.harness.ngsubscriptions.entity.TotalAccountUsers;
import io.harness.ngsubscriptions.entity.TotalAccountUsers.TotalAccountUsersKeys;
import io.harness.repositories.ngsubscriptions.spring.AccountUsersUsageRepository;
import io.harness.repositories.ngsubscriptions.spring.ModuleAccessRepository;
import io.harness.repositories.ngsubscriptions.spring.TotalAccountUsersRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.ModuleType;
import io.harness.spec.server.ng.v1.model.PrincipalWithAccessResponse;
import io.harness.spec.server.ng.v1.model.SubscriptionUsageDTO;
import io.harness.spec.server.ng.v1.model.UpdateAccessRequest;
import io.harness.spec.server.ng.v1.model.UpdateRequestEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class NGSubscriptionsServiceImplTest extends CategoryTest {
  @Mock private TotalAccountUsersRepository totalAccountUsersRepository;
  @Mock private ModuleAccessRepository moduleAccessRepository;
  @Mock private AccountUsersUsageRepository accountUsersUsageRepository;

  @Mock private ScopeInfoService scopeInfoService;
  @Mock private UserGroupService userGroupService;
  @Mock private ServiceAccountService serviceAccountService;

  @Mock private TransactionTemplate transactionTemplate;
  @Mock private NgUserService ngUserService;
  @Mock private DefaultLicenseServiceImpl defaultLicenseService;
  @Mock private ModuleRoleAssignmentHelper moduleRoleAssignmentHelper;
  NGSubscriptionsServiceImpl subscriptionsService;
  Map<Integer, List<TotalAccountUsers>> totalUsersMap = createTotalAccountUsers(2000, 2024);

  private static final String ACCOUNT_ID = "testAccount";
  private static final String USER_ID = "testUser";
  private static final String USER_GROUP_ID_1 = "ug1";
  private static final String USER_GROUP_UNIQUE_ID_1 = "ug_unique_1";
  private static final String USER_GROUP_ID_2 = "ug2";
  private static final String USER_GROUP_UNIQUE_ID_2 = "ug_unique_2";
  private static final String USER_GROUP_ID_3 = "ug3";
  private static final String USER_GROUP_UNIQUE_ID_3 = "ug_unique_3";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    subscriptionsService = new NGSubscriptionsServiceImpl(totalAccountUsersRepository, accountUsersUsageRepository,
        moduleAccessRepository, scopeInfoService, userGroupService, serviceAccountService, transactionTemplate,
        ngUserService, defaultLicenseService, moduleRoleAssignmentHelper);
  }

  private Map<Integer, List<TotalAccountUsers>> createTotalAccountUsers(int startYear, int endYear) {
    Map<Integer, List<TotalAccountUsers>> result = new HashMap<>();
    for (int year = startYear; year <= endYear; year++) {
      result.put(year, new ArrayList<>());
      for (int month = 0; month < 12; month++) {
        TotalAccountUsers totalAccountUsers = TotalAccountUsers.builder()
                                                  .accountIdentifier("testAccount")
                                                  .year(year)
                                                  .month(month)
                                                  .users((long) year * month)
                                                  .serviceAccounts((long) year + month)
                                                  .build();
        result.get(year).add(totalAccountUsers);
      }
    }
    return result;
  }

  private UserGroup createUserGroup(String id, String uniqueId) {
    return UserGroup.builder().identifier(id).uniqueId(uniqueId).accountIdentifier(ACCOUNT_ID).build();
  }

  private AccessEntity createAccessEntity(String id, String uniqueId) {
    return new AccessEntity(id, uniqueId, "parent_" + uniqueId, null, null);
  }

  private ModuleLicenseDTO createMockLicense(ModuleType moduleType) {
    // using one of the classes for test mock as the base class is abstract class and cannot be instantiated
    // logic of the service is agnostic of the class, if base class fields are populated correctly
    DevopsEssentialsModuleLicenseDTO moduleLicenseDTO = new DevopsEssentialsModuleLicenseDTO();

    moduleLicenseDTO.setModuleType(io.harness.ModuleType.valueOf(moduleType.name()));
    moduleLicenseDTO.setAccountIdentifier(ACCOUNT_ID);
    moduleLicenseDTO.setStatus(LicenseStatus.ACTIVE);

    return moduleLicenseDTO;
  }

  private List<ModuleLicenseDTO> createMockLicenseList(ModuleType... moduleTypes) {
    return Arrays.stream(moduleTypes).map(this::createMockLicense).collect(Collectors.toList());
  }

  private List<io.harness.ModuleType> toHarnessModuleTypes(Collection<ModuleType> moduleTypes) {
    return moduleTypes.stream().map(t -> io.harness.ModuleType.valueOf(t.name())).collect(Collectors.toList());
  }

  @Test
  @Owner(developers = PRAVEEN_SOLANKI)
  @Category(UnitTests.class)
  public void testGetSubscriptionsValidYear() {
    Criteria criteria = Criteria.where(TotalAccountUsersKeys.accountIdentifier)
                            .is("testAccount")
                            .and(TotalAccountUsersKeys.year)
                            .is(2024);

    Page<TotalAccountUsers> pageResponse = new PageImpl<>(totalUsersMap.get(2024), Pageable.ofSize(100), 12);
    when(totalAccountUsersRepository.findAll(criteria, Pageable.ofSize(100))).thenReturn(pageResponse);
    List<SubscriptionUsageDTO> actualResult = subscriptionsService.getSubscriptions("testAccount", 2024);
    List<SubscriptionUsageDTO> expectedResult = new ArrayList<>();
    for (TotalAccountUsers totalAccountUsers : totalUsersMap.get(2024)) {
      SubscriptionUsageDTO subscriptionUsageDTO = new SubscriptionUsageDTO();
      subscriptionUsageDTO.setYear(2024);
      subscriptionUsageDTO.setMonth(totalAccountUsers.getMonth());
      subscriptionUsageDTO.setUsage((int) (totalAccountUsers.getUsers() + totalAccountUsers.getServiceAccounts()));
      expectedResult.add(subscriptionUsageDTO);
    }
    assertEquals(actualResult, expectedResult);
  }
  @Test
  @Owner(developers = PRAVEEN_SOLANKI)
  @Category(UnitTests.class)
  public void testGetSubscriptionsInvalidYear() {
    Criteria criteria = Criteria.where(TotalAccountUsersKeys.accountIdentifier)
                            .is("testAccount")
                            .and(TotalAccountUsersKeys.year)
                            .is(2025);

    when(totalAccountUsersRepository.findAll(criteria, Pageable.ofSize(100))).thenReturn(Page.empty());
    List<SubscriptionUsageDTO> actualResult = subscriptionsService.getSubscriptions("testAccount", 2025);
    List<SubscriptionUsageDTO> expectedResult = new ArrayList<>();
    assertEquals(actualResult, expectedResult);
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_UserHasAccessViaGroup_WithLicense() {
    ModuleType moduleType = ModuleType.CD;
    Set<ModuleType> validInputSet = Collections.singleton(moduleType);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // User belongs to ug1
    List<UserGroup> userGroupsForUser =
        Collections.singletonList(createUserGroup(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1));
    when(userGroupService.getUserGroupsForUser(ACCOUNT_ID, USER_ID, false)).thenReturn(userGroupsForUser);

    // Module CD access is granted to ug1
    ModuleAccess moduleAccess =
        ModuleAccess.builder()
            .accountIdentifier(ACCOUNT_ID)
            .moduleType(moduleType)
            .userGroups(Collections.singletonList(createAccessEntity(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1)))
            .build();
    when(moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(ACCOUNT_ID, validInputSet))
        .thenReturn(Collections.singletonList(moduleAccess));

    // Active license exists for CD
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(createMockLicenseList(moduleType));

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertEquals(1, result.size());
    assertTrue("User should have access to CD via ug1 with license", result.get(moduleType));
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_UserDoesNotHaveAccess_DifferentGroup_WithLicense() {
    ModuleType moduleType = ModuleType.CI;
    Set<ModuleType> validInputSet = Collections.singleton(moduleType);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // User belongs to ug1
    List<UserGroup> userGroupsForUser =
        Collections.singletonList(createUserGroup(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1));
    when(userGroupService.getUserGroupsForUser(ACCOUNT_ID, USER_ID, false)).thenReturn(userGroupsForUser);

    // Module CI access is granted to ug2
    ModuleAccess moduleAccess = ModuleAccess.builder()
                                    .accountIdentifier(ACCOUNT_ID)
                                    .moduleType(moduleType)
                                    .userGroups(Collections.singletonList(
                                        createAccessEntity(USER_GROUP_ID_2, USER_GROUP_UNIQUE_ID_2))) // Access for ug2
                                    .build();
    when(moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(ACCOUNT_ID, validInputSet))
        .thenReturn(Collections.singletonList(moduleAccess));

    // Active license exists for CI
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(createMockLicenseList(moduleType));

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertEquals(1, result.size());
    assertFalse("User should NOT have access to CI (in ug1, access for ug2) even with license", result.get(moduleType));
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_LicenseExists_NoModuleAccessDefined_ShouldGrantAccess() {
    // Scenario: License exists, but no specific ModuleAccess rule is defined. Should default to TRUE.
    ModuleType moduleType = ModuleType.CF;
    Set<ModuleType> validInputSet = Collections.singleton(moduleType);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // User belongs to ug1
    List<UserGroup> userGroupsForUser =
        Collections.singletonList(createUserGroup(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1));
    when(userGroupService.getUserGroupsForUser(ACCOUNT_ID, USER_ID, false)).thenReturn(userGroupsForUser);

    // No ModuleAccess found for CF
    when(moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(ACCOUNT_ID, validInputSet))
        .thenReturn(Collections.emptyList());

    // Active license exists for CF
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(createMockLicenseList(moduleType));

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertEquals(1, result.size());
    assertTrue("User should have access to CF by default when license exists but no ModuleAccess rule",
        result.get(moduleType));
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_LicenseExists_ModuleAccessDefined_NoUserGroupsListed_ShouldGrantAccess() {
    // Scenario: License exists, ModuleAccess exists, but userGroups list is empty. Should default to TRUE.
    ModuleType moduleType = ModuleType.CE;
    Set<ModuleType> validInputSet = Collections.singleton(moduleType);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // User belongs to ug1 (doesn't matter for this test case)
    List<UserGroup> userGroupsForUser =
        Collections.singletonList(createUserGroup(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1));
    when(userGroupService.getUserGroupsForUser(ACCOUNT_ID, USER_ID, false)).thenReturn(userGroupsForUser);

    // Module CE access exists but has no user groups associated
    ModuleAccess moduleAccess = ModuleAccess.builder()
                                    .accountIdentifier(ACCOUNT_ID)
                                    .moduleType(moduleType)
                                    .userGroups(Collections.emptyList())
                                    .build();
    when(moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(ACCOUNT_ID, validInputSet))
        .thenReturn(Collections.singletonList(moduleAccess));

    // Active license exists for CE
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(createMockLicenseList(moduleType));

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertEquals(1, result.size());
    assertTrue("User should have access to CE by default when license exists and ModuleAccess has empty userGroups",
        result.get(moduleType));
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_LicenseExists_ModuleAccessHasNullUserGroupsList_ShouldGrantAccess() {
    // Scenario: License exists, ModuleAccess exists, but userGroups list is null. Should default to TRUE.
    ModuleType moduleType = ModuleType.CD;
    Set<ModuleType> validInputSet = Collections.singleton(moduleType);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // User belongs to ug1 (doesn't matter for this test case)
    List<UserGroup> userGroupsForUser =
        Collections.singletonList(createUserGroup(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1));
    when(userGroupService.getUserGroupsForUser(ACCOUNT_ID, USER_ID, false)).thenReturn(userGroupsForUser);

    // Module CD access exists but has null user groups list
    ModuleAccess moduleAccess =
        ModuleAccess.builder().accountIdentifier(ACCOUNT_ID).moduleType(moduleType).userGroups(null).build(); // null
                                                                                                              // list
    when(moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(ACCOUNT_ID, validInputSet))
        .thenReturn(Collections.singletonList(moduleAccess));

    // Active license exists for CD
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(createMockLicenseList(moduleType));

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertEquals(1, result.size());
    assertTrue("User should have access to CD by default when license exists and ModuleAccess has null userGroups",
        result.get(moduleType));
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_UserBelongsToNoGroups_WithLicense() {
    ModuleType moduleType = ModuleType.CD;
    Set<ModuleType> validInputSet = Collections.singleton(moduleType);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // User belongs to no groups
    when(userGroupService.getUserGroupsForUser(ACCOUNT_ID, USER_ID, false)).thenReturn(Collections.emptyList());

    // Module CD access is granted to ug1
    ModuleAccess moduleAccess =
        ModuleAccess.builder()
            .accountIdentifier(ACCOUNT_ID)
            .moduleType(moduleType)
            .userGroups(Collections.singletonList(createAccessEntity(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1)))
            .build();
    when(moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(ACCOUNT_ID, validInputSet))
        .thenReturn(Collections.singletonList(moduleAccess));

    // Active license exists for CD
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(createMockLicenseList(moduleType));

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertEquals(1, result.size());
    assertFalse("User should NOT have access to CD if they belong to no groups and access is restricted to ug1",
        result.get(moduleType));
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_MultipleModules_MixedAccess_WithLicenses() {
    ModuleType moduleCD = ModuleType.CD; // Access granted to ug1 (user is in ug1)
    ModuleType moduleCI = ModuleType.CI; // Access granted to ug2 (user is in ug2)
    ModuleType moduleCF = ModuleType.CF; // License exists, No access rule -> default TRUE
    ModuleType moduleCE = ModuleType.CE; // License exists, Access rule for ug3 (user not in ug3) -> FALSE
    ModuleType moduleSTO = ModuleType.STO; // License exists, Access rule, null user groups -> default TRUE
    ModuleType moduleChaos = ModuleType.CHAOS; // No License -> FALSE
    Set<ModuleType> validInputSet = Set.of(moduleCD, moduleCI, moduleCF, moduleCE, moduleSTO, moduleChaos);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // User belongs to ug1 and ug2
    List<UserGroup> userGroupsForUser = Arrays.asList(createUserGroup(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1),
        createUserGroup(USER_GROUP_ID_2, USER_GROUP_UNIQUE_ID_2));
    when(userGroupService.getUserGroupsForUser(ACCOUNT_ID, USER_ID, false)).thenReturn(userGroupsForUser);

    // Module Access Rules
    ModuleAccess moduleAccessCD =
        ModuleAccess.builder()
            .accountIdentifier(ACCOUNT_ID)
            .moduleType(moduleCD)
            .userGroups(Collections.singletonList(createAccessEntity(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1)))
            .build();
    ModuleAccess moduleAccessCI =
        ModuleAccess.builder()
            .accountIdentifier(ACCOUNT_ID)
            .moduleType(moduleCI)
            .userGroups(Collections.singletonList(createAccessEntity(USER_GROUP_ID_2, USER_GROUP_UNIQUE_ID_2)))
            .build();
    ModuleAccess moduleAccessCE =
        ModuleAccess.builder()
            .accountIdentifier(ACCOUNT_ID)
            .moduleType(moduleCE)
            .userGroups(Collections.singletonList(createAccessEntity(USER_GROUP_ID_3, USER_GROUP_UNIQUE_ID_3))) // ug3
            .build();
    ModuleAccess moduleAccessSTO = ModuleAccess.builder()
                                       .accountIdentifier(ACCOUNT_ID)
                                       .moduleType(moduleSTO)
                                       .userGroups(null) // Null groups
                                       .build();

    // Mock repository returns access rules for CD, CI, CE, STO (but not CF or FF)
    when(moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(eq(ACCOUNT_ID), any()))
        .thenReturn(Arrays.asList(moduleAccessCD, moduleAccessCI, moduleAccessCE, moduleAccessSTO));

    // Mock Licenses (Active for all except FF)
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(createMockLicenseList(moduleCD, moduleCI, moduleCF, moduleCE, moduleSTO)); // No FF license

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertEquals(5, result.size());
    assertTrue("User should have access to CD (in ug1)", result.get(moduleCD));
    assertTrue("User should have access to CI (in ug2)", result.get(moduleCI));
    assertTrue("User should have access to CF (license, no rule)", result.get(moduleCF));
    assertFalse("User should NOT have access to CE (rule for ug3)", result.get(moduleCE)); // User does not have access
    assertTrue("User should have access to STO (license, null groups)", result.get(moduleSTO));
    assertFalse("User should NOT have access to FF (no license)",
        result.containsKey(moduleChaos)); // Account does not have access
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_EmptyRequestedModules() {
    Set<ModuleType> validInputSet = Collections.emptySet();
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // No need to mock repository of user group service if list is empty
    // Mock license service returning empty for empty input
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(Collections.emptyList());

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertTrue("Result map should be empty for empty input", result.isEmpty());
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_UserInMultipleGroups_OverlappingAccess_WithLicense() {
    ModuleType moduleCD = ModuleType.CD;
    ModuleType moduleCI = ModuleType.CI;
    Set<ModuleType> validInputSet = Set.of(moduleCD, moduleCI);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // User belongs to ug1 and ug2
    List<UserGroup> userGroupsForUser = Arrays.asList(createUserGroup(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1),
        createUserGroup(USER_GROUP_ID_2, USER_GROUP_UNIQUE_ID_2));
    when(userGroupService.getUserGroupsForUser(ACCOUNT_ID, USER_ID, false)).thenReturn(userGroupsForUser);

    // Module CD access granted to both ug1 and ug2
    ModuleAccess moduleAccessCD =
        ModuleAccess.builder()
            .accountIdentifier(ACCOUNT_ID)
            .moduleType(moduleCD)
            .userGroups(Arrays.asList(createAccessEntity(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1),
                createAccessEntity(USER_GROUP_ID_2, USER_GROUP_UNIQUE_ID_2)))
            .build();

    // Module CI access granted only to ug1
    ModuleAccess moduleAccessCI =
        ModuleAccess.builder()
            .accountIdentifier(ACCOUNT_ID)
            .moduleType(moduleCI)
            .userGroups(Collections.singletonList(createAccessEntity(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1)))
            .build();

    // Mock repository returns access rules for both CD and CI
    when(moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(ACCOUNT_ID, validInputSet))
        .thenReturn(Arrays.asList(moduleAccessCD, moduleAccessCI));

    // Active licenses exist for both
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(createMockLicenseList(moduleCD, moduleCI));

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    // Assertions
    assertEquals(2, result.size());
    assertTrue("User should have access to CD (in ug1/ug2)", result.get(moduleCD));
    assertTrue("User should have access to CI (in ug1)", result.get(moduleCI));
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_NoActiveLicenses() {
    // Scenario: No active licenses found for the requested modules.
    ModuleType moduleCD = ModuleType.CD;
    ModuleType moduleCI = ModuleType.CI;
    Set<ModuleType> validInputSet = Set.of(moduleCD, moduleCI);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // Mock license service returns empty list
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(Collections.emptyList());

    // No need to mock repository of user group service as license check fails first
    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertEquals(0, result.size());
    assertFalse("User should NOT have access to CD (no license)", result.containsKey(moduleCD));
    assertFalse("User should NOT have access to CI (no license)", result.containsKey(moduleCI));
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_PartialActiveLicenses() {
    // Scenario: License active for CD, but not for CI. Access rule for CD allows user.
    ModuleType moduleCD = ModuleType.CD;
    ModuleType moduleCI = ModuleType.CI;
    Set<ModuleType> validInputSet = Set.of(moduleCD, moduleCI);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());

    // User belongs to ug1
    List<UserGroup> userGroupsForUser =
        Collections.singletonList(createUserGroup(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1));
    when(userGroupService.getUserGroupsForUser(ACCOUNT_ID, USER_ID, false)).thenReturn(userGroupsForUser);

    // Module CD access granted to ug1
    ModuleAccess moduleAccessCD =
        ModuleAccess.builder()
            .accountIdentifier(ACCOUNT_ID)
            .moduleType(moduleCD)
            .userGroups(Collections.singletonList(createAccessEntity(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1)))
            .build();
    // No access rule needed for CI as license check will fail

    // Mock repository returns access rule only for CD
    when(moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(ACCOUNT_ID, validInputSet))
        .thenReturn(Collections.singletonList(moduleAccessCD));

    // Mock license service returns license only for CD
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(createMockLicenseList(moduleCD)); // Only CD license

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertEquals(1, result.size());
    assertTrue("User should have access to CD (license + group access)", result.get(moduleCD));
    assertFalse(
        "Account does not have access to CI, so result map should not contain it", result.containsKey(moduleCI));
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_InvalidInputGetsExcluded() {
    ModuleType moduleType = ModuleType.CD;
    Set<ModuleType> validInputSet = Collections.singleton(moduleType);
    Set<String> inputSet = validInputSet.stream().map(ModuleType::name).collect(Collectors.toSet());
    inputSet.add("INVALID_MODULE");

    // User belongs to ug1
    List<UserGroup> userGroupsForUser =
        Collections.singletonList(createUserGroup(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1));
    when(userGroupService.getUserGroupsForUser(ACCOUNT_ID, USER_ID, false)).thenReturn(userGroupsForUser);

    // Module CD access is granted to ug1
    ModuleAccess moduleAccess =
        ModuleAccess.builder()
            .accountIdentifier(ACCOUNT_ID)
            .moduleType(moduleType)
            .userGroups(Collections.singletonList(createAccessEntity(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1)))
            .build();
    when(moduleAccessRepository.findByAccountIdentifierAndModuleTypeIn(ACCOUNT_ID, validInputSet))
        .thenReturn(Collections.singletonList(moduleAccess));

    // Active license exists for CD
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(createMockLicenseList(moduleType));

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertEquals(1, result.size());
    assertTrue("User should have access to CD via ug1 with license", result.get(moduleType));
  }

  @Test
  @Owner(developers = ABEL_MATHEW)
  @Category(UnitTests.class)
  public void testGetModuleAccess_AllInputsAreInvalid() {
    Set<ModuleType> validInputSet = Collections.emptySet();
    Set<String> inputSet = Set.of("INVALID_MODULE1", "INVALID_MODULE2");

    // No need to mock repository of user group service if list is empty
    // Mock license service returning empty for empty input
    when(defaultLicenseService.getDev360ModuleLicenses(anyString(), any(), anyList()))
        .thenReturn(Collections.emptyList());

    Map<ModuleType, Boolean> result =
        subscriptionsService.getDev360ModuleAccessForAccountAndUser(ACCOUNT_ID, USER_ID, inputSet);

    assertTrue("Result map should be empty for empty input", result.isEmpty());
  }

  @Test
  @Owner(developers = NAMAN_GUPTA)
  @Category(UnitTests.class)
  public void testFindPrincipals_NamedUserGatingEnabled_NoModuleAccess() {
    // When FF is enabled and no module access exists, should return 0 principals
    ModuleType moduleType = ModuleType.IDP;

    when(moduleRoleAssignmentHelper.isNamedUserGatingEnabled(ACCOUNT_ID, moduleType.name())).thenReturn(true);
    when(moduleAccessRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, moduleType))
        .thenReturn(Optional.empty());

    PrincipalWithAccessResponse response = subscriptionsService.findPrincipals(ACCOUNT_ID, moduleType);

    assertEquals(0, response.getTotalPrincipals().intValue());
    assertTrue(response.getUsersWithAccess() == null || response.getUsersWithAccess().isEmpty());
  }

  @Test
  @Owner(developers = NAMAN_GUPTA)
  @Category(UnitTests.class)
  public void testFindPrincipals_NamedUserGatingDisabled_NoModuleAccess() {
    // When FF is disabled and no module access exists, should return all account users
    ModuleType moduleType = ModuleType.IDP;
    UserGroup allUsersGroup = UserGroup.builder()
                                  .identifier("_account_all_users")
                                  .uniqueId("all_users_unique_id")
                                  .accountIdentifier(ACCOUNT_ID)
                                  .users(Collections.emptyList())
                                  .build();

    when(moduleRoleAssignmentHelper.isNamedUserGatingEnabled(ACCOUNT_ID, moduleType.name())).thenReturn(false);
    when(moduleAccessRepository.findByAccountIdentifierAndModuleType(ACCOUNT_ID, moduleType))
        .thenReturn(Optional.empty());

    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).uniqueId(ACCOUNT_ID).build();
    when(userGroupService.get(scopeInfo, "_account_all_users")).thenReturn(Optional.of(allUsersGroup));

    // Mock scopeInfoService to return scope info for any parent unique ID set
    when(scopeInfoService.getScopeInfo(eq(ACCOUNT_ID), any())).thenAnswer(invocation -> {
      Set<String> uniqueIds = invocation.getArgument(1);
      Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
      for (String uniqueId : uniqueIds) {
        scopeInfoMap.put(uniqueId, Optional.of(scopeInfo));
      }
      return scopeInfoMap;
    });

    PrincipalWithAccessResponse response = subscriptionsService.findPrincipals(ACCOUNT_ID, moduleType);

    // Should have called the default logic (totalPrincipals should be 0 since group has no members)
    assertEquals(0, response.getTotalPrincipals().intValue());
  }

  @Test
  @Owner(developers = NAMAN_GUPTA)
  @Category(UnitTests.class)
  public void testUpdateModuleAccess_CallsModuleRoleAssignmentHelper() {
    // Verify that updateModuleAccess calls moduleRoleAssignmentHelper.syncIfApplicable
    ModuleType moduleType = ModuleType.IDP;
    UpdateAccessRequest updateRequest = new UpdateAccessRequest();
    updateRequest.setModuleType(moduleType);
    updateRequest.setUserGroupsToGrant(Arrays.asList(new UpdateRequestEntity().identifier(USER_GROUP_ID_1)));
    updateRequest.setUserGroupsToRevoke(Collections.emptyList());
    updateRequest.setServiceAccountsToGrant(Collections.emptyList());
    updateRequest.setServiceAccountsToRevoke(Collections.emptyList());

    UserGroup ug1 = createUserGroup(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1);
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).uniqueId(ACCOUNT_ID).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(scopeInfo);
    when(userGroupService.get(any(), eq(USER_GROUP_ID_1))).thenReturn(Optional.of(ug1));
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      // Simulate transaction execution
      return ModuleAccess.builder()
          .accountIdentifier(ACCOUNT_ID)
          .moduleType(moduleType)
          .userGroups(Collections.singletonList(createAccessEntity(USER_GROUP_ID_1, USER_GROUP_UNIQUE_ID_1)))
          .build();
    });

    subscriptionsService.updateModuleAccess(ACCOUNT_ID, updateRequest);

    // Verify that syncIfApplicable was called with correct parameters
    verify(moduleRoleAssignmentHelper, times(1))
        .syncIfApplicable(eq(ACCOUNT_ID), eq(moduleType.name()),
            anyList(), // userGroupIdsToGrant
            anyList(), // serviceAccountIdsToGrant
            anyList(), // userGroupIdsToRevoke
            anyList() // serviceAccountIdsToRevoke
        );
  }
}