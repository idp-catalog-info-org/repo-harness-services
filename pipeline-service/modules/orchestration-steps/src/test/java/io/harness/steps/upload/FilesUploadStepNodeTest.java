/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.steps.upload;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class FilesUploadStepNodeTest extends OrchestrationStepsTestBase {
  FilesUploadStepNode filesUploadStepNode = new FilesUploadStepNode();
  @Test
  @Owner(developers = OwnerRule.AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetType() {
    assertEquals(filesUploadStepNode.getType(), StepSpecTypeConstants.UPLOAD);
  }

  @Test
  @Owner(developers = OwnerRule.AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepSpecType_WhenUploadStepInfoIsNull() {
    assertNull(filesUploadStepNode.getStepSpecType());
  }
}
