/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer;

import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.beans.steps.v1.Container;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactUtilsTest {
  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testIsArtifactMetadataDockerArtifactMetadata_WithDockerType() {
    ArtifactMetadata artifactMetadata = mock(ArtifactMetadata.class);
    when(artifactMetadata.getType()).thenReturn(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA);

    assertThat(ArtifactUtils.isArtifactMetadataDockerArtifactMetadata(artifactMetadata)).isTrue();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testIsArtifactMetadataDockerArtifactMetadata_WithFileType() {
    ArtifactMetadata artifactMetadata = mock(ArtifactMetadata.class);
    when(artifactMetadata.getType()).thenReturn(ArtifactMetadataType.FILE_ARTIFACT_METADATA);

    assertThat(ArtifactUtils.isArtifactMetadataDockerArtifactMetadata(artifactMetadata)).isFalse();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testIsDockerBuildAndPushStep_WithDockerPluginImage() {
    RunStepInfoV1 runStep1 = createRunStepInfoV1WithImage("plugins/docker");
    RunStepInfoV1 runStep2 = createRunStepInfoV1WithImage("plugins/docker:latest");
    RunStepInfoV1 runStep3 = createRunStepInfoV1WithImage("plugins/buildx");
    RunStepInfoV1 runStep4 = createRunStepInfoV1WithImage("plugins/kaniko");
    RunStepInfoV1 runStep5 = createRunStepInfoV1WithImage("plugins/kaniko:v1.2.3");
    RunStepInfoV1 runStep6 = createRunStepInfoV1WithImage("plugins/buildx:v1.2.3");
    RunStepInfoV1 runStep7 = createRunStepInfoV1WithImage("alpine:latest");
    RunStepInfoV1 runStep8 = createRunStepInfoV1WithImage("plugin/buildx");
    RunStepInfoV1 runStep9 = createRunStepInfoV1WithImage("plugins/acr");
    RunStepInfoV1 runStep10 = createRunStepInfoV1WithImage("docker:latest");
    RunStepInfoV1 runStep11 = createRunStepInfoV1WithImage("plugins/ecr");
    RunStepInfoV1 runStep12 = createRunStepInfoV1WithImage("plugins/gar");
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep1)).isTrue();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep2)).isTrue();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep3)).isTrue();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep4)).isTrue();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep5)).isTrue();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep6)).isTrue();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep7)).isFalse();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep8)).isFalse();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep9)).isFalse();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep10)).isFalse();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep11)).isFalse();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(runStep12)).isFalse();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testValidateIsDockerBuildAndPushStepFalse() {
    Container container = Container.builder().image(null).build();
    RunStepInfoV1 run1 = RunStepInfoV1.builder().container(null).build();
    RunStepInfoV1 run2 = RunStepInfoV1.builder().container(container).build();
    RunStepInfoV1 run3 = createRunStepInfoV1WithImage("");
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(run1)).isFalse();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(run2)).isFalse();
    assertThat(ArtifactUtils.isDockerBuildAndPushStep(run3)).isFalse();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testIsECRBuildAndPushStep_WithECRPluginImage() {
    RunStepInfoV1 runStep1 = createRunStepInfoV1WithImage("plugins/ecr");
    RunStepInfoV1 runStep2 = createRunStepInfoV1WithImage("plugins/buildx-ecr");
    RunStepInfoV1 runStep3 = createRunStepInfoV1WithImage("plugins/kaniko-ecr");
    RunStepInfoV1 runStep4 = createRunStepInfoV1WithImage("plugins/kaniko-ecr:v1.0");
    RunStepInfoV1 runStep5 = createRunStepInfoV1WithImage("plugins/buildx-ecr:v1.0.1");
    RunStepInfoV1 runStep6 = createRunStepInfoV1WithImage("plugins/ecr:latest");
    RunStepInfoV1 runStep7 = createRunStepInfoV1WithImage("alpine:latest");
    RunStepInfoV1 runStep8 = createRunStepInfoV1WithImage("plugin/ecr");
    RunStepInfoV1 runStep9 = createRunStepInfoV1WithImage("ecr:latest");
    RunStepInfoV1 runStep10 = createRunStepInfoV1WithImage("plugins/docker");
    RunStepInfoV1 runStep11 = createRunStepInfoV1WithImage("plugins/gar");
    RunStepInfoV1 runStep12 = createRunStepInfoV1WithImage("plugins/acr");

    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep1)).isTrue();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep2)).isTrue();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep3)).isTrue();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep4)).isTrue();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep5)).isTrue();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep6)).isTrue();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep7)).isFalse();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep8)).isFalse();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep9)).isFalse();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep10)).isFalse();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep11)).isFalse();
    assertThat(ArtifactUtils.isECRBuildAndPushStep(runStep12)).isFalse();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testIsGARBuildAndPushStep_WithGARPluginImage() {
    RunStepInfoV1 runStep1 = createRunStepInfoV1WithImage("plugins/gar");
    RunStepInfoV1 runStep2 = createRunStepInfoV1WithImage("plugins/buildx-gar");
    RunStepInfoV1 runStep3 = createRunStepInfoV1WithImage("plugins/kaniko-gar");
    RunStepInfoV1 runStep4 = createRunStepInfoV1WithImage("plugins/gar:latest");
    RunStepInfoV1 runStep5 = createRunStepInfoV1WithImage("plugins/buildx-gar:v1.0.0");
    RunStepInfoV1 runStep6 = createRunStepInfoV1WithImage("plugins/kaniko-gar:v1.0.2");
    RunStepInfoV1 runStep7 = createRunStepInfoV1WithImage("alpine:latest");
    RunStepInfoV1 runStep8 = createRunStepInfoV1WithImage("plugin/gar");
    RunStepInfoV1 runStep9 = createRunStepInfoV1WithImage("gar:latest");
    RunStepInfoV1 runStep10 = createRunStepInfoV1WithImage("plugins/docker");
    RunStepInfoV1 runStep11 = createRunStepInfoV1WithImage("plugins/ecr");
    RunStepInfoV1 runStep12 = createRunStepInfoV1WithImage("plugins/acr");
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep1)).isTrue();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep2)).isTrue();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep3)).isTrue();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep4)).isTrue();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep5)).isTrue();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep6)).isTrue();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep7)).isFalse();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep8)).isFalse();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep9)).isFalse();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep10)).isFalse();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep11)).isFalse();
    assertThat(ArtifactUtils.isGARBuildAndPushStep(runStep12)).isFalse();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testIsACRBuildAndPushStep_WithACRPluginImage() {
    RunStepInfoV1 runStep1 = createRunStepInfoV1WithImage("plugins/acr");
    RunStepInfoV1 runStep2 = createRunStepInfoV1WithImage("plugins/buildx-acr");
    RunStepInfoV1 runStep3 = createRunStepInfoV1WithImage("plugins/kaniko-acr");
    RunStepInfoV1 runStep4 = createRunStepInfoV1WithImage("plugins/acr:v1.2.3");
    RunStepInfoV1 runStep5 = createRunStepInfoV1WithImage("plugins/buildx-acr:latest");
    RunStepInfoV1 runStep6 = createRunStepInfoV1WithImage("plugins/kaniko-acr:v1.0.0");
    RunStepInfoV1 runStep7 = createRunStepInfoV1WithImage("alpine:latest");
    RunStepInfoV1 runStep8 = createRunStepInfoV1WithImage("plugin/acr");
    RunStepInfoV1 runStep9 = createRunStepInfoV1WithImage("acr:latest");
    RunStepInfoV1 runStep10 = createRunStepInfoV1WithImage("plugins/docker");
    RunStepInfoV1 runStep11 = createRunStepInfoV1WithImage("plugins/ecr");
    RunStepInfoV1 runStep12 = createRunStepInfoV1WithImage("plugins/gar");

    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep1)).isTrue();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep2)).isTrue();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep3)).isTrue();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep4)).isTrue();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep5)).isTrue();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep6)).isTrue();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep7)).isFalse();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep8)).isFalse();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep9)).isFalse();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep10)).isFalse();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep11)).isFalse();
    assertThat(ArtifactUtils.isACRBuildAndPushStep(runStep12)).isFalse();
  }

  // ==================================
  // Helper Methods
  // ==================================

  private RunStepInfoV1 createRunStepInfoV1WithImage(String image) {
    Container container = Container.builder().image(ParameterField.createValueField(image)).build();
    return RunStepInfoV1.builder().container(container).build();
  }
}
