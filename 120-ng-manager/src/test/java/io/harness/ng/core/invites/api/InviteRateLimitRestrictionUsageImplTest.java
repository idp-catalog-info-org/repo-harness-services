/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.invites.api;

import static io.harness.rule.OwnerRule.ZHENYU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.beans.TimeUnit;
import io.harness.enforcement.beans.metadata.RateLimitRestrictionMetadataDTO;
import io.harness.ng.core.invites.api.impl.InviteRateLimitRestrictionUsageImpl;
import io.harness.repositories.invites.spring.InviteRepository;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InviteRateLimitRestrictionUsageImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccountId";

  @Mock private InviteRepository inviteRepository;
  @InjectMocks private InviteRateLimitRestrictionUsageImpl restrictionUsage;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ZHENYU)
  @Category(UnitTests.class)
  public void testGetCurrentValue_DaysUnit_ComputesCorrectWindow() {
    RateLimitRestrictionMetadataDTO metadata = new RateLimitRestrictionMetadataDTO();
    metadata.setTimeUnit(new TimeUnit(ChronoUnit.DAYS, 1));
    when(inviteRepository.countByAccountIdentifierAndCreatedAtAfter(anyString(), anyLong())).thenReturn(42L);

    long before = Instant.now().minus(Duration.ofDays(1)).toEpochMilli();
    long result = restrictionUsage.getCurrentValue(ACCOUNT_ID, metadata);
    long after = Instant.now().minus(Duration.ofDays(1)).toEpochMilli();

    assertThat(result).isEqualTo(42L);

    ArgumentCaptor<Long> thresholdCaptor = ArgumentCaptor.forClass(Long.class);
    verify(inviteRepository).countByAccountIdentifierAndCreatedAtAfter(eq(ACCOUNT_ID), thresholdCaptor.capture());
    long threshold = thresholdCaptor.getValue();
    assertThat(threshold).isBetween(before, after);
  }

  @Test
  @Owner(developers = ZHENYU)
  @Category(UnitTests.class)
  public void testGetCurrentValue_HoursUnit_ComputesCorrectWindow() {
    RateLimitRestrictionMetadataDTO metadata = new RateLimitRestrictionMetadataDTO();
    metadata.setTimeUnit(new TimeUnit(ChronoUnit.HOURS, 12));
    when(inviteRepository.countByAccountIdentifierAndCreatedAtAfter(anyString(), anyLong())).thenReturn(10L);

    long before = Instant.now().minus(Duration.ofHours(12)).toEpochMilli();
    long result = restrictionUsage.getCurrentValue(ACCOUNT_ID, metadata);
    long after = Instant.now().minus(Duration.ofHours(12)).toEpochMilli();

    assertThat(result).isEqualTo(10L);

    ArgumentCaptor<Long> thresholdCaptor = ArgumentCaptor.forClass(Long.class);
    verify(inviteRepository).countByAccountIdentifierAndCreatedAtAfter(eq(ACCOUNT_ID), thresholdCaptor.capture());
    assertThat(thresholdCaptor.getValue()).isBetween(before, after);
  }
}
