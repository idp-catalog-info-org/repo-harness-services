/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.mappers;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class NamespaceMapperTest extends CategoryTest {
  static final String TEST_ID = "testNamespaceId";
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccountId";

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToDTO() {
    NamespaceEntity entity = NamespaceEntity.builder().id(TEST_ID).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).build();

    NamespaceInfo result = NamespaceMapper.toDTO(entity);

    assertNotNull(result);
    assertEquals(TEST_ID, result.getNamespace());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, result.getAccountIdentifier());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFromDTO() {
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(TEST_ID);
    namespaceInfo.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    NamespaceEntity result = NamespaceMapper.fromDTO(namespaceInfo);

    assertNotNull(result);
    assertEquals(TEST_ID, result.getId());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, result.getAccountIdentifier());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToDTOWithNullValues() {
    NamespaceEntity entity = NamespaceEntity.builder().id(null).accountIdentifier(null).build();

    NamespaceInfo result = NamespaceMapper.toDTO(entity);

    assertNotNull(result);
    assertEquals(null, result.getNamespace());
    assertEquals(null, result.getAccountIdentifier());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFromDTOWithNullValues() {
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(null);
    namespaceInfo.setAccountIdentifier(null);

    NamespaceEntity result = NamespaceMapper.fromDTO(namespaceInfo);

    assertNotNull(result);
    assertEquals(null, result.getId());
    assertEquals(null, result.getAccountIdentifier());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToDTOPreservesAllFields() {
    NamespaceEntity entity = NamespaceEntity.builder()
                                 .id("namespace-123")
                                 .accountIdentifier("account-456")
                                 .createdAt(1000L)
                                 .lastModifiedAt(2000L)
                                 .build();

    NamespaceInfo result = NamespaceMapper.toDTO(entity);

    assertNotNull(result);
    assertEquals("namespace-123", result.getNamespace());
    assertEquals("account-456", result.getAccountIdentifier());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFromDTOCreatesMinimalEntity() {
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace("namespace-789");
    namespaceInfo.setAccountIdentifier("account-012");

    NamespaceEntity result = NamespaceMapper.fromDTO(namespaceInfo);

    assertNotNull(result);
    assertEquals("namespace-789", result.getId());
    assertEquals("account-012", result.getAccountIdentifier());
    assertEquals(null, result.getCreatedAt());
    assertEquals(null, result.getLastModifiedAt());
  }
}
