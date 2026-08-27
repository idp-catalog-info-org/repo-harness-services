/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.variable.services.impl;

import static io.harness.rule.OwnerRule.SHIVAM_RAJPUT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class VariableFunctorAdapterTest extends CategoryTest {
  @InjectMocks private VariableFunctorAdapter variableFunctorAdapter;
  private final String identifier = randomAlphabetic(10);
  private final String accountIdentifier = randomAlphabetic(10);
  private final String orgIdentifier = randomAlphabetic(10);
  private final String projectIdentifier = randomAlphabetic(10);

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testGetAmbianceFromScopeInfo_withProjectScopeInfo() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.PROJECT)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(identifier)
                              .build();

    Ambiance result = variableFunctorAdapter.getAmbianceFromScopeInfo(scopeInfo);

    assertThat(result.getSetupAbstractionsMap())
        .containsEntry(SetupAbstractionKeys.accountId, accountIdentifier)
        .containsEntry(SetupAbstractionKeys.orgIdentifier, orgIdentifier)
        .containsEntry(SetupAbstractionKeys.projectIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testGetAmbianceFromScopeInfo_withAccountScopeInfo() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .orgIdentifier(null)
                              .projectIdentifier(null)
                              .uniqueId(identifier)
                              .build();

    Ambiance result = variableFunctorAdapter.getAmbianceFromScopeInfo(scopeInfo);

    assertThat(result.getSetupAbstractionsMap()).containsEntry(SetupAbstractionKeys.accountId, accountIdentifier);
  }

  @Test
  @Owner(developers = SHIVAM_RAJPUT)
  @Category(UnitTests.class)
  public void testGetAmbianceFromScopeInfo_withOrgScopeInfo() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(null)
                              .uniqueId(identifier)
                              .build();

    Ambiance result = variableFunctorAdapter.getAmbianceFromScopeInfo(scopeInfo);

    assertThat(result.getSetupAbstractionsMap())
        .containsEntry(SetupAbstractionKeys.accountId, accountIdentifier)
        .containsEntry(SetupAbstractionKeys.orgIdentifier, orgIdentifier);
  }
}
