/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.repositories;

import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersRepositoryTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_EMAIL = "test@example.com";
  static final String TEST_USER_NAME = "Test User";
  static final long TEST_LAST_ACCESSED_AT = 1698294600000L;

  @Mock ActiveDevelopersRepository repository;
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndUserIdentifier() {
    ActiveDevelopersEntity entity = ActiveDevelopersEntity.builder()
                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                        .userIdentifier(TEST_USER_IDENTIFIER)
                                        .email(TEST_EMAIL)
                                        .userName(TEST_USER_NAME)
                                        .lastAccessedAt(TEST_LAST_ACCESSED_AT)
                                        .build();

    Mockito.when(repository.findByAccountIdentifierAndUserIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER))
        .thenReturn(Optional.of(entity));

    Optional<ActiveDevelopersEntity> result =
        repository.findByAccountIdentifierAndUserIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER);

    assertThat(result).isPresent();
    assertThat(result.get().getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(result.get().getUserIdentifier()).isEqualTo(TEST_USER_IDENTIFIER);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFindByLastAccessedAtBetween() {
    long startTime = 1698264000000L;
    long endTime = 1698350400000L;

    ActiveDevelopersEntity entity = ActiveDevelopersEntity.builder()
                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                        .userIdentifier(TEST_USER_IDENTIFIER)
                                        .email(TEST_EMAIL)
                                        .userName(TEST_USER_NAME)
                                        .lastAccessedAt(TEST_LAST_ACCESSED_AT)
                                        .build();

    Mockito.when(repository.findByLastAccessedAtBetween(startTime, endTime)).thenReturn(List.of(entity));

    List<ActiveDevelopersEntity> result = repository.findByLastAccessedAtBetween(startTime, endTime);

    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getLastAccessedAt()).isEqualTo(TEST_LAST_ACCESSED_AT);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifier() {
    ActiveDevelopersEntity entity = ActiveDevelopersEntity.builder()
                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                        .userIdentifier(TEST_USER_IDENTIFIER)
                                        .email(TEST_EMAIL)
                                        .userName(TEST_USER_NAME)
                                        .lastAccessedAt(TEST_LAST_ACCESSED_AT)
                                        .build();

    Mockito.when(repository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(List.of(entity));

    List<ActiveDevelopersEntity> result = repository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndLastAccessedAtGreaterThan() {
    long lastAccessedAtThreshold = 1698264000000L;

    ActiveDevelopersEntity entity = ActiveDevelopersEntity.builder()
                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                        .userIdentifier(TEST_USER_IDENTIFIER)
                                        .email(TEST_EMAIL)
                                        .userName(TEST_USER_NAME)
                                        .lastAccessedAt(TEST_LAST_ACCESSED_AT)
                                        .build();

    Mockito
        .when(repository.findByAccountIdentifierAndLastAccessedAtGreaterThan(
            TEST_ACCOUNT_IDENTIFIER, lastAccessedAtThreshold))
        .thenReturn(List.of(entity));

    List<ActiveDevelopersEntity> result = repository.findByAccountIdentifierAndLastAccessedAtGreaterThan(
        TEST_ACCOUNT_IDENTIFIER, lastAccessedAtThreshold);

    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getLastAccessedAt()).isGreaterThan(lastAccessedAtThreshold);
  }
}
