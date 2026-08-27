/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.enforcement;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersRestrictionUsageImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";

  AutoCloseable openMocks;
  @InjectMocks ActiveDevelopersRestrictionUsageImpl activeDevelopersRestrictionUsage;
  @Mock IDPModuleLicenseUsage idpModuleLicenseUsage;

  @Before
  public void setUp() throws IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetCurrentValue() {
    when(idpModuleLicenseUsage.getActiveDevelopers(TEST_ACCOUNT_IDENTIFIER)).thenReturn(1L);
    long activeDevelopers = activeDevelopersRestrictionUsage.getCurrentValue(TEST_ACCOUNT_IDENTIFIER, null);

    assertEquals(1, activeDevelopers);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
