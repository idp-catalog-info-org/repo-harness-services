/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.upload.unified;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class UnifiedFilesUploadStepNodeTest extends CategoryTest {
  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetType() {
    UnifiedFilesUploadStepNode stepNode = UnifiedFilesUploadStepNode.builder().build();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.UPLOAD);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    UnifiedFilesUploadStepNode stepNode = UnifiedFilesUploadStepNode.builder().build();
    assertThat(stepNode.getFacilitatorType()).isEqualTo(OrchestrationFacilitatorType.ASYNC);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testUnifiedFilesUploadStepInfo() {
    UnifiedFilesUploadStepInfo stepInfo = new UnifiedFilesUploadStepInfo(null);
    UnifiedFilesUploadStepNode stepNode =
        UnifiedFilesUploadStepNode.builder().unifiedFilesUploadStepInfo(stepInfo).build();

    assertThat(stepNode.getUnifiedFilesUploadStepInfo()).isNotNull();
    assertThat(stepNode.getUnifiedFilesUploadStepInfo()).isEqualTo(stepInfo);
  }
}
