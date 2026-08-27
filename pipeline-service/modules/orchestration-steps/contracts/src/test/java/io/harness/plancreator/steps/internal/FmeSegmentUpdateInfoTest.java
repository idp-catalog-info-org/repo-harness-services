/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.fme.FmeSegmentUpdateParameters;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.FME)
public class FmeSegmentUpdateInfoTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetStepType() {
    FmeSegmentUpdateInfo info = new FmeSegmentUpdateInfo();
    StepType stepType = info.getStepType();
    assertThat(stepType).isEqualTo(StepSpecTypeConstants.FME_SEGMENT_UPDATE_STEP_TYPE);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    FmeSegmentUpdateInfo info = new FmeSegmentUpdateInfo();
    String facilitatorType = info.getFacilitatorType();
    assertThat(facilitatorType).isEqualTo(OrchestrationFacilitatorType.SYNC);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    FmeSegmentUpdateInfo info = FmeSegmentUpdateInfo.builder()
                                    .name(ParameterField.createValueField("test-segment"))
                                    .description(ParameterField.createValueField("test description"))
                                    .owners(ParameterField.createValueField(Arrays.asList("owner1")))
                                    .tags(ParameterField.createValueField(Arrays.asList("tag1")))
                                    .build();

    FmeSegmentUpdateParameters params = (FmeSegmentUpdateParameters) info.getSpecParameters();

    assertThat(params).isNotNull();
    assertThat(params.getName().getValue()).isEqualTo("test-segment");
    assertThat(params.getDescription().getValue()).isEqualTo("test description");
    assertThat(params.getOwners().getValue()).containsExactly("owner1");
    assertThat(params.getTags().getValue()).containsExactly("tag1");
  }

  @Test
  @Owner(developers = OwnerRule.ROHITPAL)
  @Category(UnitTests.class)
  public void testBuilderAndGetters() {
    FmeSegmentUpdateInfo info =
        FmeSegmentUpdateInfo.builder().name(ParameterField.createValueField("segment-name")).build();

    assertThat(info.getName().getValue()).isEqualTo("segment-name");
  }
}
