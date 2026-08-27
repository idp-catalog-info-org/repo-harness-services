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
import io.harness.idp.license.usage.entities.IDPTelemetrySentStatus;
import io.harness.rule.Owner;

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
public class IDPTelemetrySentStatusRepositoryTest extends CategoryTest {
  static final String TEST_ACCOUNT_ID = "testAccount123";
  static final String TEST_UUID = "uuid123";
  static final long TEST_LAST_SENT = 1698294600000L;

  @Mock IDPTelemetrySentStatusRepository repository;
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFindByAccountId() {
    IDPTelemetrySentStatus entity =
        IDPTelemetrySentStatus.builder().uuid(TEST_UUID).accountId(TEST_ACCOUNT_ID).lastSent(TEST_LAST_SENT).build();

    Mockito.when(repository.findByAccountId(TEST_ACCOUNT_ID)).thenReturn(Optional.of(entity));

    Optional<IDPTelemetrySentStatus> result = repository.findByAccountId(TEST_ACCOUNT_ID);

    assertThat(result).isPresent();
    assertThat(result.get().getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
    assertThat(result.get().getLastSent()).isEqualTo(TEST_LAST_SENT);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFindByAccountId_NotFound() {
    Mockito.when(repository.findByAccountId(TEST_ACCOUNT_ID)).thenReturn(Optional.empty());

    Optional<IDPTelemetrySentStatus> result = repository.findByAccountId(TEST_ACCOUNT_ID);

    assertThat(result).isEmpty();
  }
}
