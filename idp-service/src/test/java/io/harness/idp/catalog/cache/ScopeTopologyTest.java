/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.cache;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ScopeTopologyTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account1";
  private static final String ORG1_ID = "org1";
  private static final String ORG2_ID = "org2";
  private static final String PROJECT1_ID = "project1";
  private static final String PROJECT2_ID = "project2";
  private static final String ORG1_UNIQUE_ID = "account1_org1";
  private static final String ORG2_UNIQUE_ID = "account1_org2";
  private static final String PROJECT1_UNIQUE_ID = "account1_org1_project1";
  private static final String PROJECT2_UNIQUE_ID = "account1_org2_project2";

  private ScopeTopology scopeTopology;

  @Before
  public void setUp() {
    Map<String, ScopeTopology.OrgNode> orgs = new HashMap<>();

    Map<String, String> org1Projects = new HashMap<>();
    org1Projects.put(PROJECT1_ID, PROJECT1_UNIQUE_ID);
    orgs.put(ORG1_ID, ScopeTopology.OrgNode.builder().uniqueId(ORG1_UNIQUE_ID).projects(org1Projects).build());

    Map<String, String> org2Projects = new HashMap<>();
    org2Projects.put(PROJECT2_ID, PROJECT2_UNIQUE_ID);
    orgs.put(ORG2_ID, ScopeTopology.OrgNode.builder().uniqueId(ORG2_UNIQUE_ID).projects(org2Projects).build());

    scopeTopology = ScopeTopology.builder().accountUniqueId(ACCOUNT_ID).orgs(orgs).build();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_AccountScope() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account");

    assertEquals(1, result.size());
    assertTrue(result.contains(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_AccountWildcard() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account.*");

    assertEquals(5, result.size());
    assertTrue(result.contains(ACCOUNT_ID));
    assertTrue(result.contains(ORG1_UNIQUE_ID));
    assertTrue(result.contains(ORG2_UNIQUE_ID));
    assertTrue(result.contains(PROJECT1_UNIQUE_ID));
    assertTrue(result.contains(PROJECT2_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_AccountOrgScope() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account.org");

    assertEquals(2, result.size());
    assertTrue(result.contains(ORG1_UNIQUE_ID));
    assertTrue(result.contains(ORG2_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_AccountOrgProjectScope() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account.org.project");

    assertEquals(2, result.size());
    assertTrue(result.contains(PROJECT1_UNIQUE_ID));
    assertTrue(result.contains(PROJECT2_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_SpecificOrg() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account.org1");

    assertEquals(1, result.size());
    assertTrue(result.contains(ORG1_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_SpecificOrgWildcard() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account.org1.*");

    assertEquals(2, result.size());
    assertTrue(result.contains(ORG1_UNIQUE_ID));
    assertTrue(result.contains(PROJECT1_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_SpecificProject() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account.org1.project1");

    assertEquals(1, result.size());
    assertTrue(result.contains(PROJECT1_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_MultipleScopes() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account,account.org1,account.org2.project2");

    assertEquals(3, result.size());
    assertTrue(result.contains(ACCOUNT_ID));
    assertTrue(result.contains(ORG1_UNIQUE_ID));
    assertTrue(result.contains(PROJECT2_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_EmptyString() {
    List<String> result = scopeTopology.resolveParentUniqueIds("");

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_NullString() {
    List<String> result = scopeTopology.resolveParentUniqueIds(null);

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_NonExistentOrg() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account.org99");

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_NonExistentProject() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account.org1.project99");

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_CaseInsensitive() {
    List<String> result1 = scopeTopology.resolveParentUniqueIds("ACCOUNT");
    List<String> result2 = scopeTopology.resolveParentUniqueIds("Account");

    assertEquals(1, result1.size());
    assertEquals(1, result2.size());
    assertTrue(result1.contains(ACCOUNT_ID));
    assertTrue(result2.contains(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetAllUniqueIds() {
    List<String> result = scopeTopology.getAllUniqueIds();

    assertEquals(5, result.size());
    assertTrue(result.contains(ACCOUNT_ID));
    assertTrue(result.contains(ORG1_UNIQUE_ID));
    assertTrue(result.contains(ORG2_UNIQUE_ID));
    assertTrue(result.contains(PROJECT1_UNIQUE_ID));
    assertTrue(result.contains(PROJECT2_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildScopeInfos_AccountLevel() {
    List<String> uniqueIds = List.of(ACCOUNT_ID);
    List<ScopeInfo> result = scopeTopology.buildScopeInfos(uniqueIds);

    assertEquals(1, result.size());
    ScopeInfo scopeInfo = result.get(0);
    assertEquals(ACCOUNT_ID, scopeInfo.getAccountIdentifier());
    assertNull(scopeInfo.getOrgIdentifier());
    assertNull(scopeInfo.getProjectIdentifier());
    assertEquals(ScopeLevel.ACCOUNT, scopeInfo.getScopeType());
    assertEquals(ACCOUNT_ID, scopeInfo.getUniqueId());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildScopeInfos_OrgLevel() {
    List<String> uniqueIds = List.of(ORG1_UNIQUE_ID);
    List<ScopeInfo> result = scopeTopology.buildScopeInfos(uniqueIds);

    assertEquals(1, result.size());
    ScopeInfo scopeInfo = result.get(0);
    assertEquals(ACCOUNT_ID, scopeInfo.getAccountIdentifier());
    assertEquals(ORG1_ID, scopeInfo.getOrgIdentifier());
    assertNull(scopeInfo.getProjectIdentifier());
    assertEquals(ScopeLevel.ORGANIZATION, scopeInfo.getScopeType());
    assertEquals(ORG1_UNIQUE_ID, scopeInfo.getUniqueId());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildScopeInfos_ProjectLevel() {
    List<String> uniqueIds = List.of(PROJECT1_UNIQUE_ID);
    List<ScopeInfo> result = scopeTopology.buildScopeInfos(uniqueIds);

    assertEquals(1, result.size());
    ScopeInfo scopeInfo = result.get(0);
    assertEquals(ACCOUNT_ID, scopeInfo.getAccountIdentifier());
    assertEquals(ORG1_ID, scopeInfo.getOrgIdentifier());
    assertEquals(PROJECT1_ID, scopeInfo.getProjectIdentifier());
    assertEquals(ScopeLevel.PROJECT, scopeInfo.getScopeType());
    assertEquals(PROJECT1_UNIQUE_ID, scopeInfo.getUniqueId());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildScopeInfos_MultipleScopes() {
    List<String> uniqueIds = List.of(ACCOUNT_ID, ORG1_UNIQUE_ID, PROJECT2_UNIQUE_ID);
    List<ScopeInfo> result = scopeTopology.buildScopeInfos(uniqueIds);

    assertEquals(3, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBuildScopeInfos_NonExistentUniqueId() {
    List<String> uniqueIds = List.of("non-existent-id");
    List<ScopeInfo> result = scopeTopology.buildScopeInfos(uniqueIds);

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgIdentifierForUniqueId_OrgLevel() {
    String result = scopeTopology.getOrgIdentifierForUniqueId(ORG1_UNIQUE_ID);

    assertEquals(ORG1_ID, result);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgIdentifierForUniqueId_ProjectLevel() {
    String result = scopeTopology.getOrgIdentifierForUniqueId(PROJECT1_UNIQUE_ID);

    assertEquals(ORG1_ID, result);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetOrgIdentifierForUniqueId_NonExistent() {
    String result = scopeTopology.getOrgIdentifierForUniqueId("non-existent");

    assertNull(result);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectIdentifierForUniqueId_ProjectLevel() {
    String result = scopeTopology.getProjectIdentifierForUniqueId(PROJECT1_UNIQUE_ID);

    assertEquals(PROJECT1_ID, result);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectIdentifierForUniqueId_OrgLevel() {
    String result = scopeTopology.getProjectIdentifierForUniqueId(ORG1_UNIQUE_ID);

    assertNull(result);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetProjectIdentifierForUniqueId_NonExistent() {
    String result = scopeTopology.getProjectIdentifierForUniqueId("non-existent");

    assertNull(result);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testTopology_WithNullOrgs() {
    ScopeTopology emptyTopology = ScopeTopology.builder().accountUniqueId(ACCOUNT_ID).orgs(null).build();

    List<String> allIds = emptyTopology.getAllUniqueIds();
    assertEquals(1, allIds.size());
    assertTrue(allIds.contains(ACCOUNT_ID));

    assertNull(emptyTopology.getOrgIdentifierForUniqueId("any-id"));
    assertNull(emptyTopology.getProjectIdentifierForUniqueId("any-id"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testTopology_OrgWithNullProjects() {
    Map<String, ScopeTopology.OrgNode> orgs = new HashMap<>();
    orgs.put(ORG1_ID, ScopeTopology.OrgNode.builder().uniqueId(ORG1_UNIQUE_ID).projects(null).build());

    ScopeTopology topology = ScopeTopology.builder().accountUniqueId(ACCOUNT_ID).orgs(orgs).build();

    List<String> result = topology.resolveParentUniqueIds("account.org1.*");
    assertEquals(1, result.size());
    assertTrue(result.contains(ORG1_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_WithWhitespace() {
    List<String> result = scopeTopology.resolveParentUniqueIds(" account , account.org1 ");

    assertEquals(2, result.size());
    assertTrue(result.contains(ACCOUNT_ID));
    assertTrue(result.contains(ORG1_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveParentUniqueIds_NoDuplicates() {
    List<String> result = scopeTopology.resolveParentUniqueIds("account,account,account.org1,account.org1");

    assertEquals(2, result.size());
    assertTrue(result.contains(ACCOUNT_ID));
    assertTrue(result.contains(ORG1_UNIQUE_ID));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_AccountLevel() {
    assertEquals(ACCOUNT_ID, scopeTopology.resolveNamespaceToUniqueId("account"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_OrgLevel() {
    assertEquals(ORG1_UNIQUE_ID, scopeTopology.resolveNamespaceToUniqueId("account.org1"));
    assertEquals(ORG2_UNIQUE_ID, scopeTopology.resolveNamespaceToUniqueId("account.org2"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_ProjectLevel() {
    assertEquals(PROJECT1_UNIQUE_ID, scopeTopology.resolveNamespaceToUniqueId("account.org1.project1"));
    assertEquals(PROJECT2_UNIQUE_ID, scopeTopology.resolveNamespaceToUniqueId("account.org2.project2"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_NonExistentOrg() {
    assertNull(scopeTopology.resolveNamespaceToUniqueId("account.nonExistentOrg"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_NonExistentProject() {
    assertNull(scopeTopology.resolveNamespaceToUniqueId("account.org1.nonExistentProject"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_NullAndEmpty() {
    assertNull(scopeTopology.resolveNamespaceToUniqueId(null));
    assertNull(scopeTopology.resolveNamespaceToUniqueId(""));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testResolveNamespaceToUniqueId_NullOrgs() {
    ScopeTopology emptyTopology = ScopeTopology.builder().accountUniqueId(ACCOUNT_ID).orgs(null).build();

    assertEquals(ACCOUNT_ID, emptyTopology.resolveNamespaceToUniqueId("account"));
    assertNull(emptyTopology.resolveNamespaceToUniqueId("account.org1"));
    assertNull(emptyTopology.resolveNamespaceToUniqueId("account.org1.proj1"));
  }
}
