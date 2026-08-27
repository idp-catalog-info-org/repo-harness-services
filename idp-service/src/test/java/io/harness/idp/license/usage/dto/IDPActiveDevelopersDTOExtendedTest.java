/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.dto;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPActiveDevelopersDTOExtendedTest extends CategoryTest {
  static final String TEST_IDENTIFIER = "testUser123";
  static final String TEST_EMAIL = "test@example.com";
  static final String TEST_NAME = "Test User";
  static final String TEST_LAST_ACCESSED_AT = "10-26-2023";

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    IDPActiveDevelopersDTO dto = IDPActiveDevelopersDTO.builder()
                                     .identifier(TEST_IDENTIFIER)
                                     .email(TEST_EMAIL)
                                     .name(TEST_NAME)
                                     .lastAccessedAt(TEST_LAST_ACCESSED_AT)
                                     .build();

    assertNotNull(dto);
    assertEquals(TEST_IDENTIFIER, dto.getIdentifier());
    assertEquals(TEST_EMAIL, dto.getEmail());
    assertEquals(TEST_NAME, dto.getName());
    assertEquals(TEST_LAST_ACCESSED_AT, dto.getLastAccessedAt());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSetters() {
    IDPActiveDevelopersDTO dto = IDPActiveDevelopersDTO.builder().build();
    dto.setIdentifier(TEST_IDENTIFIER);
    dto.setEmail(TEST_EMAIL);
    dto.setName(TEST_NAME);
    dto.setLastAccessedAt(TEST_LAST_ACCESSED_AT);

    assertEquals(TEST_IDENTIFIER, dto.getIdentifier());
    assertEquals(TEST_EMAIL, dto.getEmail());
    assertEquals(TEST_NAME, dto.getName());
    assertEquals(TEST_LAST_ACCESSED_AT, dto.getLastAccessedAt());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFromActiveDevelopersEntityWithVariousTimestamps() {
    long timestamp1 = 1698294600000L;
    ActiveDevelopersEntity entity1 = ActiveDevelopersEntity.builder()
                                         .accountIdentifier("account1")
                                         .userIdentifier(TEST_IDENTIFIER)
                                         .email(TEST_EMAIL)
                                         .userName(TEST_NAME)
                                         .lastAccessedAt(timestamp1)
                                         .build();

    IDPActiveDevelopersDTO dto1 = IDPActiveDevelopersDTO.fromActiveDevelopersEntity(entity1);
    assertNotNull(dto1);
    assertEquals(TEST_IDENTIFIER, dto1.getIdentifier());

    long timestamp2 = 1609459200000L;
    ActiveDevelopersEntity entity2 = ActiveDevelopersEntity.builder()
                                         .accountIdentifier("account2")
                                         .userIdentifier("user2")
                                         .email("user2@test.com")
                                         .userName("User Two")
                                         .lastAccessedAt(timestamp2)
                                         .build();

    IDPActiveDevelopersDTO dto2 = IDPActiveDevelopersDTO.fromActiveDevelopersEntity(entity2);
    assertNotNull(dto2);
    assertEquals("user2", dto2.getIdentifier());
    assertEquals("user2@test.com", dto2.getEmail());
  }
}
