/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.cache.CatalogRbacPermissionsCache;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.rbac.KindResourceTypeMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.RbacUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.dto.UserPrincipal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class CatalogRbacResolverTest extends CategoryTest {
  private static final String TEST_ACCOUNT = "testAccount";
  private static final String USER_ID = "testUser";
  private static final String ORG_1 = "org1";
  private static final String PROJECT_1 = "project1";
  private static final String UNIQUE_ID_ACCOUNT = "testAccount";
  private static final String UNIQUE_ID_ORG_1 = "testAccount_org1";
  private static final String UNIQUE_ID_PROJECT_1 = "testAccount_org1_project1";
  private static final String UNIQUE_ID_PROJECT_2 = "testAccount_org1_project2";

  @Mock private CatalogRbacPermissionsCache rbacPermissionsCache;
  @Mock private CatalogServiceHelper catalogServiceHelper;
  @Mock private CatalogEntityRepository catalogEntityRepository;

  private CatalogRbacResolver rbacResolver;
  private UserPrincipal userPrincipal;
  private ScopeTopology topology;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    rbacResolver = new CatalogRbacResolver(rbacPermissionsCache, catalogServiceHelper, catalogEntityRepository);
    userPrincipal = new UserPrincipal(USER_ID, "test@example.com", "Test User", TEST_ACCOUNT);

    Map<String, String> org1Projects = new HashMap<>();
    org1Projects.put(PROJECT_1, UNIQUE_ID_PROJECT_1);
    org1Projects.put("project2", UNIQUE_ID_PROJECT_2);

    Map<String, ScopeTopology.OrgNode> orgs = new HashMap<>();
    orgs.put(ORG_1, ScopeTopology.OrgNode.builder().uniqueId(UNIQUE_ID_ORG_1).projects(org1Projects).build());

    topology = ScopeTopology.builder().accountUniqueId(UNIQUE_ID_ACCOUNT).orgs(orgs).build();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testResolve_ServiceToServiceCall_ReturnsAllScopes() {
    List<ScopeInfo> requestedScopes = buildScopeInfos(UNIQUE_ID_ACCOUNT, UNIQUE_ID_ORG_1);
    List<String> resolvedKinds = List.of("component", "workflow");

    try (MockedStatic<RbacUtils> rbacUtilsMock = mockStatic(RbacUtils.class)) {
      rbacUtilsMock.when(RbacUtils::isPureServiceToServiceCall).thenReturn(true);

      CatalogRbacResolver.RbacResolveResult result =
          rbacResolver.resolve(TEST_ACCOUNT, requestedScopes, topology, resolvedKinds);

      Map<String, List<ScopeInfo>> rtScopes = result.getResourceTypeToPermittedScopes();
      assertThat(rtScopes).containsKey(KindResourceTypeMapper.CATALOG_RESOURCE_TYPE);
      assertThat(rtScopes).containsKey(KindResourceTypeMapper.WORKFLOW_RESOURCE_TYPE);
      assertThat(rtScopes.get(KindResourceTypeMapper.CATALOG_RESOURCE_TYPE)).hasSize(2);
      assertThat(rtScopes.get(KindResourceTypeMapper.WORKFLOW_RESOURCE_TYPE)).hasSize(2);
      assertThat(result.getPermittedEntityRefs()).isEmpty();
      verify(catalogServiceHelper, never())
          .scopeInfosRbacByResourceType(anyString(), anyList(), anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testResolve_AllScopesAllowed_SingleResourceType() {
    List<ScopeInfo> requestedScopes = buildScopeInfos(UNIQUE_ID_ACCOUNT, UNIQUE_ID_ORG_1, UNIQUE_ID_PROJECT_1);
    List<String> resolvedKinds = List.of("component");

    when(catalogServiceHelper.scopeInfosRbacByResourceType(
             eq(TEST_ACCOUNT), anyList(), eq("IDP_CATALOG"), eq("idp_catalog_view")))
        .thenReturn(Arrays.asList(buildScopeInfo(UNIQUE_ID_ACCOUNT), buildScopeInfo(UNIQUE_ID_ORG_1),
            buildScopeInfo(UNIQUE_ID_PROJECT_1), buildScopeInfo(UNIQUE_ID_PROJECT_2)));

    try (MockedStatic<RbacUtils> rbacUtilsMock = mockStatic(RbacUtils.class);
         MockedStatic<CommonUtils> commonUtilsMock = mockStatic(CommonUtils.class)) {
      rbacUtilsMock.when(RbacUtils::isPureServiceToServiceCall).thenReturn(false);
      commonUtilsMock.when(CommonUtils::getUserPrincipalFromPrincipal).thenReturn(userPrincipal);

      CatalogRbacResolver.RbacResolveResult result =
          rbacResolver.resolve(TEST_ACCOUNT, requestedScopes, topology, resolvedKinds);

      Map<String, List<ScopeInfo>> rtScopes = result.getResourceTypeToPermittedScopes();
      assertThat(rtScopes.get(KindResourceTypeMapper.CATALOG_RESOURCE_TYPE)).hasSize(3);
      assertThat(result.getPermittedEntityRefs()).isEmpty();
    }
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testResolve_MultipleResourceTypes_DifferentScopes() {
    List<ScopeInfo> requestedScopes = buildScopeInfos(UNIQUE_ID_ACCOUNT, UNIQUE_ID_ORG_1, UNIQUE_ID_PROJECT_1);
    List<String> resolvedKinds = List.of("component", "workflow");

    when(catalogServiceHelper.scopeInfosRbacByResourceType(
             eq(TEST_ACCOUNT), anyList(), eq("IDP_CATALOG"), eq("idp_catalog_view")))
        .thenReturn(Arrays.asList(
            buildScopeInfo(UNIQUE_ID_ACCOUNT), buildScopeInfo(UNIQUE_ID_ORG_1), buildScopeInfo(UNIQUE_ID_PROJECT_1)));

    when(catalogServiceHelper.scopeInfosRbacByResourceType(
             eq(TEST_ACCOUNT), anyList(), eq("IDP_WORKFLOW"), eq("idp_workflow_view")))
        .thenReturn(Collections.singletonList(buildScopeInfo(UNIQUE_ID_ACCOUNT)));

    when(catalogServiceHelper.filterPermittedEntityRefs(eq(TEST_ACCOUNT), anyList()))
        .thenReturn(Collections.emptyList());

    try (MockedStatic<RbacUtils> rbacUtilsMock = mockStatic(RbacUtils.class);
         MockedStatic<CommonUtils> commonUtilsMock = mockStatic(CommonUtils.class)) {
      rbacUtilsMock.when(RbacUtils::isPureServiceToServiceCall).thenReturn(false);
      commonUtilsMock.when(CommonUtils::getUserPrincipalFromPrincipal).thenReturn(userPrincipal);

      CatalogRbacResolver.RbacResolveResult result =
          rbacResolver.resolve(TEST_ACCOUNT, requestedScopes, topology, resolvedKinds);

      Map<String, List<ScopeInfo>> rtScopes = result.getResourceTypeToPermittedScopes();
      assertThat(rtScopes.get(KindResourceTypeMapper.CATALOG_RESOURCE_TYPE)).hasSize(3);
      assertThat(rtScopes.get(KindResourceTypeMapper.WORKFLOW_RESOURCE_TYPE)).hasSize(1);
      assertThat(rtScopes.get(KindResourceTypeMapper.WORKFLOW_RESOURCE_TYPE).get(0).getUniqueId())
          .isEqualTo(UNIQUE_ID_ACCOUNT);
    }
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testResolve_NoScopesAllowed_ForResourceType() {
    List<ScopeInfo> requestedScopes = buildScopeInfos(UNIQUE_ID_PROJECT_1, UNIQUE_ID_PROJECT_2);
    List<String> resolvedKinds = List.of("workflow");

    when(catalogServiceHelper.scopeInfosRbacByResourceType(
             eq(TEST_ACCOUNT), anyList(), eq("IDP_WORKFLOW"), eq("idp_workflow_view")))
        .thenReturn(Collections.singletonList(buildScopeInfo(UNIQUE_ID_ACCOUNT)));

    when(catalogServiceHelper.filterPermittedEntityRefs(eq(TEST_ACCOUNT), anyList()))
        .thenReturn(Collections.emptyList());

    try (MockedStatic<RbacUtils> rbacUtilsMock = mockStatic(RbacUtils.class);
         MockedStatic<CommonUtils> commonUtilsMock = mockStatic(CommonUtils.class)) {
      rbacUtilsMock.when(RbacUtils::isPureServiceToServiceCall).thenReturn(false);
      commonUtilsMock.when(CommonUtils::getUserPrincipalFromPrincipal).thenReturn(userPrincipal);

      CatalogRbacResolver.RbacResolveResult result =
          rbacResolver.resolve(TEST_ACCOUNT, requestedScopes, topology, resolvedKinds);

      Map<String, List<ScopeInfo>> rtScopes = result.getResourceTypeToPermittedScopes();
      assertThat(rtScopes.get(KindResourceTypeMapper.WORKFLOW_RESOURCE_TYPE)).isEmpty();
    }
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testResolve_DeniedScopes_ResolvesEntityLevelRbac() {
    List<ScopeInfo> requestedScopes = buildScopeInfos(UNIQUE_ID_ACCOUNT, UNIQUE_ID_PROJECT_1);
    List<String> resolvedKinds = List.of("component");

    when(catalogServiceHelper.scopeInfosRbacByResourceType(
             eq(TEST_ACCOUNT), anyList(), eq("IDP_CATALOG"), eq("idp_catalog_view")))
        .thenReturn(Collections.singletonList(buildScopeInfo(UNIQUE_ID_ACCOUNT)));

    CatalogEntity entity1 = InlineCatalogEntity.builder()
                                .kind("component")
                                .identifier("comp1")
                                .orgIdentifier(ORG_1)
                                .projectIdentifier("project1")
                                .parentUniqueId(UNIQUE_ID_PROJECT_1)
                                .accountIdentifier(TEST_ACCOUNT)
                                .referenceType(ReferenceType.INLINE)
                                .build();
    CatalogEntity entity2 = InlineCatalogEntity.builder()
                                .kind("component")
                                .identifier("comp2")
                                .orgIdentifier(ORG_1)
                                .projectIdentifier("project1")
                                .parentUniqueId(UNIQUE_ID_PROJECT_1)
                                .accountIdentifier(TEST_ACCOUNT)
                                .referenceType(ReferenceType.INLINE)
                                .build();

    when(catalogEntityRepository.findKindIdentifierScopeByParentUniqueIdInAndKindIn(
             eq(Collections.singletonList(UNIQUE_ID_PROJECT_1)), eq(List.of("component"))))
        .thenReturn(Arrays.asList(entity1, entity2));

    List<String> permittedRefs = Collections.singletonList("component:account.org1.project1/comp1");
    when(catalogServiceHelper.filterPermittedEntityRefs(eq(TEST_ACCOUNT), anyList())).thenReturn(permittedRefs);

    try (MockedStatic<RbacUtils> rbacUtilsMock = mockStatic(RbacUtils.class);
         MockedStatic<CommonUtils> commonUtilsMock = mockStatic(CommonUtils.class)) {
      rbacUtilsMock.when(RbacUtils::isPureServiceToServiceCall).thenReturn(false);
      commonUtilsMock.when(CommonUtils::getUserPrincipalFromPrincipal).thenReturn(userPrincipal);

      CatalogRbacResolver.RbacResolveResult result =
          rbacResolver.resolve(TEST_ACCOUNT, requestedScopes, topology, resolvedKinds);

      Map<String, List<ScopeInfo>> rtScopes = result.getResourceTypeToPermittedScopes();
      assertThat(rtScopes.get(KindResourceTypeMapper.CATALOG_RESOURCE_TYPE)).hasSize(1);
      assertThat(rtScopes.get(KindResourceTypeMapper.CATALOG_RESOURCE_TYPE).get(0).getUniqueId())
          .isEqualTo(UNIQUE_ID_ACCOUNT);
      assertThat(result.getPermittedEntityRefs()).hasSize(1);
      assertThat(result.getPermittedEntityRefs()).contains("component:account.org1.project1/comp1");

      verify(catalogEntityRepository)
          .findKindIdentifierScopeByParentUniqueIdInAndKindIn(
              eq(Collections.singletonList(UNIQUE_ID_PROJECT_1)), eq(List.of("component")));
      verify(catalogServiceHelper).filterPermittedEntityRefs(eq(TEST_ACCOUNT), anyList());
    }
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testResolve_NullUserPrincipal_UsesServiceAsUserId() {
    List<ScopeInfo> requestedScopes = buildScopeInfos(UNIQUE_ID_ACCOUNT);
    List<String> resolvedKinds = List.of("component");

    try (MockedStatic<RbacUtils> rbacUtilsMock = mockStatic(RbacUtils.class);
         MockedStatic<CommonUtils> commonUtilsMock = mockStatic(CommonUtils.class)) {
      rbacUtilsMock.when(RbacUtils::isPureServiceToServiceCall).thenReturn(false);
      commonUtilsMock.when(CommonUtils::getUserPrincipalFromPrincipal).thenReturn(null);

      when(catalogServiceHelper.scopeInfosRbacByResourceType(
               eq(TEST_ACCOUNT), anyList(), eq("IDP_CATALOG"), eq("idp_catalog_view")))
          .thenReturn(Collections.singletonList(buildScopeInfo(UNIQUE_ID_ACCOUNT)));

      CatalogRbacResolver.RbacResolveResult result =
          rbacResolver.resolve(TEST_ACCOUNT, requestedScopes, topology, resolvedKinds);

      Map<String, List<ScopeInfo>> rtScopes = result.getResourceTypeToPermittedScopes();
      assertThat(rtScopes.get(KindResourceTypeMapper.CATALOG_RESOURCE_TYPE)).hasSize(1);
      verify(catalogServiceHelper)
          .scopeInfosRbacByResourceType(eq(TEST_ACCOUNT), anyList(), eq("IDP_CATALOG"), eq("idp_catalog_view"));
    }
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testResolve_EnvironmentKind_UsesCorrectResourceType() {
    List<ScopeInfo> requestedScopes = buildScopeInfos(UNIQUE_ID_ACCOUNT, UNIQUE_ID_ORG_1);
    List<String> resolvedKinds = List.of("environment");

    when(catalogServiceHelper.scopeInfosRbacByResourceType(
             eq(TEST_ACCOUNT), anyList(), eq("IDP_ENVIRONMENT"), eq("idp_idpenvironment_view")))
        .thenReturn(Arrays.asList(buildScopeInfo(UNIQUE_ID_ACCOUNT), buildScopeInfo(UNIQUE_ID_ORG_1)));

    try (MockedStatic<RbacUtils> rbacUtilsMock = mockStatic(RbacUtils.class);
         MockedStatic<CommonUtils> commonUtilsMock = mockStatic(CommonUtils.class)) {
      rbacUtilsMock.when(RbacUtils::isPureServiceToServiceCall).thenReturn(false);
      commonUtilsMock.when(CommonUtils::getUserPrincipalFromPrincipal).thenReturn(userPrincipal);

      CatalogRbacResolver.RbacResolveResult result =
          rbacResolver.resolve(TEST_ACCOUNT, requestedScopes, topology, resolvedKinds);

      Map<String, List<ScopeInfo>> rtScopes = result.getResourceTypeToPermittedScopes();
      assertThat(rtScopes.get(KindResourceTypeMapper.ENVIRONMENT_RESOURCE_TYPE)).hasSize(2);
      verify(catalogServiceHelper)
          .scopeInfosRbacByResourceType(
              eq(TEST_ACCOUNT), anyList(), eq("IDP_ENVIRONMENT"), eq("idp_idpenvironment_view"));
    }
  }

  private List<ScopeInfo> buildScopeInfos(String... uniqueIds) {
    return Arrays.stream(uniqueIds).map(this::buildScopeInfo).toList();
  }

  private ScopeInfo buildScopeInfo(String uniqueId) {
    ScopeLevel level;
    String orgId = null;
    String projectId = null;

    if (uniqueId.equals(UNIQUE_ID_ACCOUNT)) {
      level = ScopeLevel.ACCOUNT;
    } else if (uniqueId.equals(UNIQUE_ID_ORG_1)) {
      level = ScopeLevel.ORGANIZATION;
      orgId = ORG_1;
    } else {
      level = ScopeLevel.PROJECT;
      orgId = ORG_1;
      projectId = uniqueId.substring(uniqueId.lastIndexOf('_') + 1);
    }

    return ScopeInfo.builder()
        .accountIdentifier(TEST_ACCOUNT)
        .scopeType(level)
        .uniqueId(uniqueId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .build();
  }
}
