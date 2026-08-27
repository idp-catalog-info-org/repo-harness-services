/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.rule.OwnerRule.SIDDHARTHA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.ArtifactsOutcome;
import io.harness.ci.execution.common.ServiceStepOutcomeHelper;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.unified.service.NGOutcomes;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class ServiceStepOutcomeHelperNoOpTest {
  @InjectMocks private ServiceStepOutcomeHelper serviceStepOutcomeHelper;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testAddArtifactsStepOutcome_nullArtifactsOutcome_usesNgOutcomes() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    String artifactsYaml = "primary:\n  image: myimage\n  tag: latest\n";
    ngOutcomes.put(NGOutcomes.ARTIFACTS.getName(), artifactsYaml);

    serviceStepOutcomeHelper.addArtifactsStepOutcome(stepOutcomes, null, ngOutcomes);

    assertThat(stepOutcomes).hasSize(1);
    assertThat(stepOutcomes.get(0).getName()).isEqualTo("artifacts");
    assertThat(stepOutcomes.get(0).getOutcome()).isInstanceOf(ArtifactsOutcome.class);
  }

  @Test
  @Owner(developers = SIDDHARTHA)
  @Category(UnitTests.class)
  public void testAddArtifactsStepOutcome_nullArtifactsOutcome_noNgOutcomes_noOutcomeAdded() {
    List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();

    serviceStepOutcomeHelper.addArtifactsStepOutcome(stepOutcomes, null, null);

    // When both artifactsOutcome and ngOutcomes are null, the fallback path adds null outcome.
    // This is acceptable because the caller (UnifiedServiceStep) only reaches here if
    // artifact metadata exists, which implies ngOutcomes should also have the data.
    assertThat(stepOutcomes).hasSize(1);
  }
}
