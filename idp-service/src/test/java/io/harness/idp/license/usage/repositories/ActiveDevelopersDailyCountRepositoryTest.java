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
import io.harness.idp.license.usage.entities.ActiveDevelopersDailyCountEntity;
import io.harness.rule.Owner;

import java.util.Date;
import java.util.List;
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
public class ActiveDevelopersDailyCountRepositoryTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_DATE_STRING = "2023-10-26";
  static final long TEST_COUNT = 100L;

  @Mock ActiveDevelopersDailyCountRepository repository;
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndDateInDateFormatBetween() {
    Date fromDate = new Date(1698264000000L);
    Date toDate = new Date(1698350400000L);

    ActiveDevelopersDailyCountEntity entity = ActiveDevelopersDailyCountEntity.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                  .dateInStringFormat(TEST_DATE_STRING)
                                                  .dateInDateFormat(fromDate)
                                                  .count(TEST_COUNT)
                                                  .build();

    Mockito
        .when(repository.findByAccountIdentifierAndDateInDateFormatBetween(TEST_ACCOUNT_IDENTIFIER, fromDate, toDate))
        .thenReturn(List.of(entity));

    List<ActiveDevelopersDailyCountEntity> result =
        repository.findByAccountIdentifierAndDateInDateFormatBetween(TEST_ACCOUNT_IDENTIFIER, fromDate, toDate);

    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(result.get(0).getCount()).isEqualTo(TEST_COUNT);
  }
}
