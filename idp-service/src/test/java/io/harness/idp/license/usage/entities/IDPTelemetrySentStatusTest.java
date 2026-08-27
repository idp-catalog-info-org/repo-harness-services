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
public class IDPTelemetrySentStatusTest extends CategoryTest {
  static final String TEST_UUID = "uuid123";
  static final String TEST_ACCOUNT_ID = "testAccount123";
  static final long TEST_LAST_SENT = 1698294600000L;

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    IDPTelemetrySentStatus entity =
        IDPTelemetrySentStatus.builder().uuid(TEST_UUID).accountId(TEST_ACCOUNT_ID).lastSent(TEST_LAST_SENT).build();

    assertThat(entity).isNotNull();
    assertThat(entity.getUuid()).isEqualTo(TEST_UUID);
    assertThat(entity.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(entity.getLastSent()).isEqualTo(TEST_LAST_SENT);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSetters() {
    IDPTelemetrySentStatus entity = IDPTelemetrySentStatus.builder().build();

    entity.setUuid(TEST_UUID);
    entity.setAccountId(TEST_ACCOUNT_ID);
    entity.setLastSent(TEST_LAST_SENT);

    assertThat(entity.getUuid()).isEqualTo(TEST_UUID);
    assertThat(entity.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(entity.getLastSent()).isEqualTo(TEST_LAST_SENT);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMongoIndexes() {
    assertThat(IDPTelemetrySentStatus.mongoIndexes()).isNotNull();
    assertThat(IDPTelemetrySentStatus.mongoIndexes().size()).isEqualTo(1);
  }
}
