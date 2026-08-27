/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.service;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class TierGroupLockHelperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account1";
  private static final String TIER_GROUP_ID = "compliance_tiers";

  @Mock private ResourceLocker resourceLocker;
  @Mock private AcquiredLock acquiredLock;

  private TierGroupLockHelper tierGroupLockHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    tierGroupLockHelper = new TierGroupLockHelper(resourceLocker);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void executeWithTierGroupLockRunsSupplierAndReleasesLock() {
    when(resourceLocker.acquireLock(anyString(), anyLong(), anyLong())).thenReturn(acquiredLock);

    String result = tierGroupLockHelper.executeWithTierGroupLock(ACCOUNT_ID, TIER_GROUP_ID, () -> "done");

    assertThat(result).isEqualTo("done");
    verify(resourceLocker).releaseLock(acquiredLock);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void executeWithTierGroupLockThrowsWhenLockNotAcquired() {
    when(resourceLocker.acquireLock(anyString(), anyLong(), anyLong())).thenReturn(null);

    assertThatThrownBy(
        () -> tierGroupLockHelper.executeWithTierGroupLock(ACCOUNT_ID, TIER_GROUP_ID, () -> "should-not-run"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("currently being updated");
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void executeWithTierGroupLockRunnableDelegatesToSupplier() {
    when(resourceLocker.acquireLock(anyString(), anyLong(), anyLong())).thenReturn(acquiredLock);

    tierGroupLockHelper.executeWithTierGroupLock(ACCOUNT_ID, TIER_GROUP_ID, () -> {});

    verify(resourceLocker)
        .acquireLock(eq(String.format(TierGroupConstants.TIER_GROUP_LOCK_FORMAT, ACCOUNT_ID, TIER_GROUP_ID)),
            eq(TierGroupConstants.TIER_GROUP_LOCK_TIMEOUT_MINUTES),
            eq(TierGroupConstants.TIER_GROUP_LOCK_WAIT_TIMEOUT_SECONDS));
    verify(resourceLocker).releaseLock(acquiredLock);
  }
}
