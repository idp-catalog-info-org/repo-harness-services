/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline.stages.node;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.pipeline.IDPStageSpecTypeConstants;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IDPStageNodeTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetType() {
    IDPStageNode stageNode = new IDPStageNode();

    assertEquals(IDPStageSpecTypeConstants.IDP_STAGE, stageNode.getType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetStageInfoConfig() {
    IDPStageNode stageNode = new IDPStageNode();
    IDPStageConfigImpl config = IDPStageConfigImpl.builder().build();
    stageNode.setIdpStageConfig(config);

    assertNotNull(stageNode.getStageInfoConfig());
    assertEquals(config, stageNode.getStageInfoConfig());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testStepTypeEnum() {
    IDPStageNode.StepType stepType = IDPStageNode.StepType.IDP;

    assertNotNull(stepType);
    assertEquals(IDPStageSpecTypeConstants.IDP_STAGE, stepType.getName());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDefaultType() {
    IDPStageNode stageNode = new IDPStageNode();

    assertNotNull(stageNode.type);
    assertEquals(IDPStageNode.StepType.IDP, stageNode.type);
  }
}
