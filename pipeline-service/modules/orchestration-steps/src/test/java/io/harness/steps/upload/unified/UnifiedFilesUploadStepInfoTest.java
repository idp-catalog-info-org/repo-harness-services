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
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.rule.Owner;
import io.harness.steps.upload.FilesUploadStepParameters;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class UnifiedFilesUploadStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    UnifiedFilesUploadStepInfo stepInfo = new UnifiedFilesUploadStepInfo(null);
    SpecParameters specParameters = stepInfo.getSpecParameters();

    assertThat(specParameters).isNotNull();
    assertThat(specParameters).isInstanceOf(FilesUploadStepParameters.class);
    FilesUploadStepParameters filesUploadStepParameters = (FilesUploadStepParameters) specParameters;
    assertThat(filesUploadStepParameters.getInputVariables()).isNull();
    assertThat(filesUploadStepParameters.getOutputVariables()).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetSpecParametersWithNullInputs() {
    UnifiedFilesUploadStepInfo stepInfo = new UnifiedFilesUploadStepInfo(null);
    SpecParameters specParameters = stepInfo.getSpecParameters();

    assertThat(specParameters).isNotNull();
    assertThat(specParameters).isInstanceOf(FilesUploadStepParameters.class);
  }
}
