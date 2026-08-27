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

import java.util.Date;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersDailyCountEntityTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_DATE_STRING = "2023-10-26";
  static final long TEST_COUNT = 100L;

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    Date dateInDateFormat = new Date();
    ActiveDevelopersDailyCountEntity entity = ActiveDevelopersDailyCountEntity.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                  .dateInStringFormat(TEST_DATE_STRING)
                                                  .dateInDateFormat(dateInDateFormat)
                                                  .count(TEST_COUNT)
                                                  .build();

    assertThat(entity).isNotNull();
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getDateInStringFormat()).isEqualTo(TEST_DATE_STRING);
    assertThat(entity.getDateInDateFormat()).isEqualTo(dateInDateFormat);
    assertThat(entity.getCount()).isEqualTo(TEST_COUNT);
    assertThat(entity.getValidUntil()).isNotNull();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSetters() {
    Date dateInDateFormat = new Date();
    ActiveDevelopersDailyCountEntity entity = ActiveDevelopersDailyCountEntity.builder().build();

    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setDateInStringFormat(TEST_DATE_STRING);
    entity.setDateInDateFormat(dateInDateFormat);
    entity.setCount(TEST_COUNT);

    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getDateInStringFormat()).isEqualTo(TEST_DATE_STRING);
    assertThat(entity.getDateInDateFormat()).isEqualTo(dateInDateFormat);
    assertThat(entity.getCount()).isEqualTo(TEST_COUNT);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMongoIndexes() {
    assertThat(ActiveDevelopersDailyCountEntity.mongoIndexes()).isNotNull();
    assertThat(ActiveDevelopersDailyCountEntity.mongoIndexes().size()).isEqualTo(1);
  }
}
