/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.steps.upload;

import static junit.framework.TestCase.assertEquals;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class FilesUploadStepInfoTest extends OrchestrationStepsTestBase {
  FilesUploadStepInfo filesUploadStepInfo = new FilesUploadStepInfo();
  @Test
  @Owner(developers = OwnerRule.AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertEquals(filesUploadStepInfo.getStepType(), StepSpecTypeConstants.UPLOAD_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    assertEquals(filesUploadStepInfo.getFacilitatorType(), OrchestrationFacilitatorType.ASYNC);
  }
}
