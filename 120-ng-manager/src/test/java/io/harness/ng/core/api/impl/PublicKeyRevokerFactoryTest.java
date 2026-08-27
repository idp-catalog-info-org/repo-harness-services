/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ATEFEH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.api.PublicKeyRevoker;
import io.harness.ng.core.common.beans.RevocationReason;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class PublicKeyRevokerFactoryTest extends CategoryTest {
  @Mock private PublicKeyRevoker compromisedRevoker;
  @Mock private PublicKeyRevoker retiredRevoker;

  private PublicKeyRevokerFactory publicKeyRevokerFactory;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testGetRevokers_returnsRevoker_whenOneHandlesReason() {
    when(compromisedRevoker.handles(RevocationReason.COMPROMISED)).thenReturn(true);
    when(compromisedRevoker.handles(RevocationReason.RETIRED)).thenReturn(false);

    Set<PublicKeyRevoker> revokers = new HashSet<>();
    revokers.add(compromisedRevoker);

    publicKeyRevokerFactory = new PublicKeyRevokerFactory(revokers);

    List<PublicKeyRevoker> result = publicKeyRevokerFactory.getRevokers(RevocationReason.COMPROMISED);

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo(compromisedRevoker);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testGetRevokers_returnsEmpty_whenNoRevokerHandlesReason() {
    when(compromisedRevoker.handles(RevocationReason.RETIRED)).thenReturn(false);

    Set<PublicKeyRevoker> revokers = new HashSet<>();
    revokers.add(compromisedRevoker);

    publicKeyRevokerFactory = new PublicKeyRevokerFactory(revokers);

    List<PublicKeyRevoker> result = publicKeyRevokerFactory.getRevokers(RevocationReason.RETIRED);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testGetRevokers_returnsCorrectRevoker_fromMultiple() {
    when(compromisedRevoker.handles(RevocationReason.COMPROMISED)).thenReturn(true);
    when(compromisedRevoker.handles(RevocationReason.RETIRED)).thenReturn(false);
    when(retiredRevoker.handles(RevocationReason.COMPROMISED)).thenReturn(false);
    when(retiredRevoker.handles(RevocationReason.RETIRED)).thenReturn(true);

    Set<PublicKeyRevoker> revokers = new HashSet<>();
    revokers.add(compromisedRevoker);
    revokers.add(retiredRevoker);

    publicKeyRevokerFactory = new PublicKeyRevokerFactory(revokers);

    List<PublicKeyRevoker> compromisedResult = publicKeyRevokerFactory.getRevokers(RevocationReason.COMPROMISED);
    List<PublicKeyRevoker> retiredResult = publicKeyRevokerFactory.getRevokers(RevocationReason.RETIRED);

    assertThat(compromisedResult).hasSize(1);
    assertThat(compromisedResult.get(0)).isEqualTo(compromisedRevoker);

    assertThat(retiredResult).hasSize(1);
    assertThat(retiredResult.get(0)).isEqualTo(retiredRevoker);
  }

  @Test
  @Owner(developers = ATEFEH)
  @Category(UnitTests.class)
  public void testGetRevokers_withEmptyRevokerSet_returnsEmpty() {
    Set<PublicKeyRevoker> revokers = new HashSet<>();
    publicKeyRevokerFactory = new PublicKeyRevokerFactory(revokers);

    List<PublicKeyRevoker> result = publicKeyRevokerFactory.getRevokers(RevocationReason.COMPROMISED);

    assertThat(result).isEmpty();
  }
}
