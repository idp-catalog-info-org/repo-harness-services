/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.entities;

import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersEntityTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_EMAIL = "test@example.com";
  static final String TEST_USER_NAME = "Test User";
  static final long TEST_LAST_ACCESSED_AT = 1698294600000L;

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    ActiveDevelopersEntity entity = ActiveDevelopersEntity.builder()
                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                        .userIdentifier(TEST_USER_IDENTIFIER)
                                        .email(TEST_EMAIL)
                                        .userName(TEST_USER_NAME)
                                        .lastAccessedAt(TEST_LAST_ACCESSED_AT)
                                        .build();

    assertThat(entity).isNotNull();
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getUserIdentifier()).isEqualTo(TEST_USER_IDENTIFIER);
    assertThat(entity.getEmail()).isEqualTo(TEST_EMAIL);
    assertThat(entity.getUserName()).isEqualTo(TEST_USER_NAME);
    assertThat(entity.getLastAccessedAt()).isEqualTo(TEST_LAST_ACCESSED_AT);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSetters() {
    ActiveDevelopersEntity entity = ActiveDevelopersEntity.builder().build();

    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setUserIdentifier(TEST_USER_IDENTIFIER);
    entity.setEmail(TEST_EMAIL);
    entity.setUserName(TEST_USER_NAME);
    entity.setLastAccessedAt(TEST_LAST_ACCESSED_AT);

    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getUserIdentifier()).isEqualTo(TEST_USER_IDENTIFIER);
    assertThat(entity.getEmail()).isEqualTo(TEST_EMAIL);
    assertThat(entity.getUserName()).isEqualTo(TEST_USER_NAME);
    assertThat(entity.getLastAccessedAt()).isEqualTo(TEST_LAST_ACCESSED_AT);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMongoIndexes() {
    assertThat(ActiveDevelopersEntity.mongoIndexes()).isNotNull();
    assertThat(ActiveDevelopersEntity.mongoIndexes().size()).isEqualTo(1);
  }
}
