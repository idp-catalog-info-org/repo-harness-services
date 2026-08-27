/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.cache;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RbacPermissionsTest extends CategoryTest {
  private RbacPermissions rbacPermissions;

  @Before
  public void setUp() {
    Map<String, List<String>> scopePermissions = new HashMap<>();
    scopePermissions.put("VIEW", Arrays.asList("scope1", "scope2"));
    scopePermissions.put("EDIT", Arrays.asList("scope2", "scope3"));

    List<String> allowedEntityRefs = Arrays.asList("component:default/service1", "component:default/service2",
        "api:org1/project1/api1", "system:default/system1", "component:org2/service3");

    rbacPermissions =
        RbacPermissions.builder().scopePermissions(scopePermissions).allowedEntityRefs(allowedEntityRefs).build();
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetAllowedScopeUniqueIds_ExistingPermissionType() {
    List<String> result = rbacPermissions.getAllowedScopeUniqueIds("VIEW");

    assertEquals(2, result.size());
    assertTrue(result.contains("scope1"));
    assertTrue(result.contains("scope2"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetAllowedScopeUniqueIds_NonExistingPermissionType() {
    List<String> result = rbacPermissions.getAllowedScopeUniqueIds("DELETE");

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetAllAllowedScopeUniqueIds() {
    List<String> result = rbacPermissions.getAllAllowedScopeUniqueIds();

    assertEquals(3, result.size());
    assertTrue(result.contains("scope1"));
    assertTrue(result.contains("scope2"));
    assertTrue(result.contains("scope3"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testGetAllAllowedScopeUniqueIds_EmptyPermissions() {
    RbacPermissions emptyPermissions = RbacPermissions.builder().scopePermissions(new HashMap<>()).build();
    List<String> result = emptyPermissions.getAllAllowedScopeUniqueIds();

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFilterEntityRefsByKindPrefix_WithValidPrefix() {
    List<String> result = rbacPermissions.filterEntityRefsByKindPrefix("component");

    assertEquals(3, result.size());
    assertTrue(result.contains("component:default/service1"));
    assertTrue(result.contains("component:default/service2"));
    assertTrue(result.contains("component:org2/service3"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFilterEntityRefsByKindPrefix_WithDifferentPrefix() {
    List<String> result = rbacPermissions.filterEntityRefsByKindPrefix("api");

    assertEquals(1, result.size());
    assertTrue(result.contains("api:org1/project1/api1"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFilterEntityRefsByKindPrefix_NullPrefix() {
    List<String> result = rbacPermissions.filterEntityRefsByKindPrefix(null);

    assertEquals(5, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFilterEntityRefsByKindPrefix_EmptyPrefix() {
    List<String> result = rbacPermissions.filterEntityRefsByKindPrefix("");

    assertEquals(5, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFilterEntityRefsByKindPrefix_NoMatches() {
    List<String> result = rbacPermissions.filterEntityRefsByKindPrefix("domain");

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFilterEntityRefsByScope_WithMatchingScopes() {
    List<String> scopeUniqueIds = Arrays.asList("org1/project1", "default");

    List<String> result = rbacPermissions.filterEntityRefsByScope(scopeUniqueIds);

    assertEquals(4, result.size());
    assertTrue(result.contains("component:default/service1"));
    assertTrue(result.contains("component:default/service2"));
    assertTrue(result.contains("api:org1/project1/api1"));
    assertTrue(result.contains("system:default/system1"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFilterEntityRefsByScope_NullScopes() {
    List<String> result = rbacPermissions.filterEntityRefsByScope(null);

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFilterEntityRefsByScope_EmptyScopes() {
    List<String> result = rbacPermissions.filterEntityRefsByScope(new ArrayList<>());

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFilterEntityRefsByScope_NoMatches() {
    List<String> scopeUniqueIds = Arrays.asList("org99/project99");

    List<String> result = rbacPermissions.filterEntityRefsByScope(scopeUniqueIds);

    assertEquals(0, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFilterEntityRefsByScope_PartialScopeMatch() {
    List<String> scopeUniqueIds = Arrays.asList("org1");

    List<String> result = rbacPermissions.filterEntityRefsByScope(scopeUniqueIds);

    assertEquals(1, result.size());
    assertTrue(result.contains("api:org1/project1/api1"));
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractScopeFromEntityRef_StandardFormat() {
    List<String> allowedEntityRefs = Arrays.asList("component:org1/project1/service1");
    RbacPermissions permissions = RbacPermissions.builder().allowedEntityRefs(allowedEntityRefs).build();

    List<String> result = permissions.filterEntityRefsByScope(Arrays.asList("org1/project1"));

    assertEquals(1, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractScopeFromEntityRef_NoSlash() {
    List<String> allowedEntityRefs = Arrays.asList("component:default");
    RbacPermissions permissions = RbacPermissions.builder().allowedEntityRefs(allowedEntityRefs).build();

    List<String> result = permissions.filterEntityRefsByScope(Arrays.asList("default"));

    assertEquals(1, result.size());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testExtractScopeFromEntityRef_NoColon() {
    List<String> allowedEntityRefs = Arrays.asList("malformed-entity-ref");
    RbacPermissions permissions = RbacPermissions.builder().allowedEntityRefs(allowedEntityRefs).build();

    List<String> result = permissions.filterEntityRefsByScope(Arrays.asList("malformed-entity-ref"));

    assertEquals(1, result.size());
  }
}
