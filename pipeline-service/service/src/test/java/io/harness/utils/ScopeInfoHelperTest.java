/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.MEENA;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class ScopeInfoHelperTest extends CategoryTest {
  @InjectMocks ScopeInfoHelper scopeInfoHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  private ScopeInfo getScopeInfo() {
    return ScopeInfo.builder()
        .accountIdentifier("account1")
        .orgIdentifier("org1")
        .projectIdentifier("project1")
        .uniqueId("unique-id")
        .build();
  }

  private InputSetEntity getInputSetEntity() {
    return InputSetEntity.builder()
        .accountId("account2")
        .orgIdentifier("org2")
        .projectIdentifier("project2")
        .identifier("abc")
        .build();
  }

  private PipelineEntity getPipelineEntity() {
    return PipelineEntity.builder()
        .accountId("account2")
        .orgIdentifier("org2")
        .projectIdentifier("project2")
        .identifier("xyz")
        .build();
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetAccountIdentifier() {
    ScopeInfo scopeInfo = getScopeInfo();
    InputSetEntity inputSetEntity = getInputSetEntity();

    String result = scopeInfoHelper.getAccountIdentifier(scopeInfo, inputSetEntity, InputSetEntity::getAccountId);
    assertEquals("account1", result);

    result = scopeInfoHelper.getAccountIdentifier(null, inputSetEntity, InputSetEntity::getAccountId);
    assertEquals("account2", result);

    PipelineEntity pipelineEntity = getPipelineEntity();

    result = scopeInfoHelper.getAccountIdentifier(scopeInfo, pipelineEntity, PipelineEntity::getAccountId);
    assertEquals("account1", result);

    result = scopeInfoHelper.getAccountIdentifier(null, pipelineEntity, PipelineEntity::getAccountId);
    assertEquals("account2", result);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetOrgIdentifier() {
    ScopeInfo scopeInfo = getScopeInfo();
    InputSetEntity inputSetEntity = getInputSetEntity();

    String result = scopeInfoHelper.getOrgIdentifier(scopeInfo, inputSetEntity, InputSetEntity::getOrgIdentifier);
    assertEquals("org1", result);

    result = scopeInfoHelper.getOrgIdentifier(null, inputSetEntity, InputSetEntity::getOrgIdentifier);
    assertEquals("org2", result);

    PipelineEntity pipelineEntity = getPipelineEntity();

    result = scopeInfoHelper.getOrgIdentifier(scopeInfo, pipelineEntity, PipelineEntity::getOrgIdentifier);
    assertEquals("org1", result);

    result = scopeInfoHelper.getOrgIdentifier(null, pipelineEntity, PipelineEntity::getOrgIdentifier);
    assertEquals("org2", result);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetProjectIdentifier() {
    ScopeInfo scopeInfo = getScopeInfo();
    InputSetEntity inputSetEntity = getInputSetEntity();

    String result =
        scopeInfoHelper.getProjectIdentifier(scopeInfo, inputSetEntity, InputSetEntity::getProjectIdentifier);
    assertEquals("project1", result);

    result = scopeInfoHelper.getProjectIdentifier(null, inputSetEntity, InputSetEntity::getProjectIdentifier);
    assertEquals("project2", result);

    PipelineEntity pipelineEntity = getPipelineEntity();

    result = scopeInfoHelper.getProjectIdentifier(scopeInfo, pipelineEntity, PipelineEntity::getProjectIdentifier);
    assertEquals("project1", result);

    result = scopeInfoHelper.getProjectIdentifier(null, pipelineEntity, PipelineEntity::getProjectIdentifier);
    assertEquals("project2", result);
  }
}
