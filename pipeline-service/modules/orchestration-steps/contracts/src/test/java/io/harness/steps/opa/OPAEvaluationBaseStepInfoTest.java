/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.extended.ci.container.ContainerResource;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class OPAEvaluationBaseStepInfoTest extends CategoryTest {
  private OPAEvaluationBaseStepInfo baseStepInfo;

  @Before
  public void setUp() {
    baseStepInfo = new OPAEvaluationBaseStepInfo();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testAllArgsConstructor() {
    ParameterField<String> image = ParameterField.createValueField("test-image");
    ParameterField<String> connectorRef = ParameterField.createValueField("test-connector");
    ContainerResource resources = ContainerResource.builder()
                                      .limits(ContainerResource.Limits.builder()
                                                  .cpu(ParameterField.createValueField("500m"))
                                                  .memory(ParameterField.createValueField("1Gi"))
                                                  .build())
                                      .build();
    Map<String, String> envVars = new HashMap<>();
    envVars.put("KEY1", "VALUE1");
    ParameterField<Map<String, String>> envVariables = ParameterField.createValueField(envVars);
    ParameterField<Boolean> privileged = ParameterField.createValueField(true);
    ParameterField<Integer> runAsUser = ParameterField.createValueField(1000);
    ParameterField<ImagePullPolicy> imagePullPolicy = ParameterField.createValueField(ImagePullPolicy.ALWAYS);

    OPAEvaluationBaseStepInfo stepInfo = new OPAEvaluationBaseStepInfo(
        null, image, connectorRef, resources, envVariables, privileged, runAsUser, imagePullPolicy);

    assertThat(stepInfo.getImage()).isEqualTo(image);
    assertThat(stepInfo.getConnectorRef()).isEqualTo(connectorRef);
    assertThat(stepInfo.getResources()).isEqualTo(resources);
    assertThat(stepInfo.getEnvVariables()).isEqualTo(envVariables);
    assertThat(stepInfo.getPrivileged()).isEqualTo(privileged);
    assertThat(stepInfo.getRunAsUser()).isEqualTo(runAsUser);
    assertThat(stepInfo.getImagePullPolicy()).isEqualTo(imagePullPolicy);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testSettersAndGetters() {
    ParameterField<String> image = ParameterField.createValueField("test-image");
    ParameterField<String> connectorRef = ParameterField.createValueField("test-connector");

    baseStepInfo.setImage(image);
    baseStepInfo.setConnectorRef(connectorRef);

    assertThat(baseStepInfo.getImage()).isEqualTo(image);
    assertThat(baseStepInfo.getConnectorRef()).isEqualTo(connectorRef);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testResources() {
    ContainerResource resources = ContainerResource.builder()
                                      .limits(ContainerResource.Limits.builder()
                                                  .cpu(ParameterField.createValueField("500m"))
                                                  .memory(ParameterField.createValueField("1Gi"))
                                                  .build())
                                      .requests(ContainerResource.Limits.builder()
                                                    .cpu(ParameterField.createValueField("100m"))
                                                    .memory(ParameterField.createValueField("256Mi"))
                                                    .build())
                                      .build();

    baseStepInfo.setResources(resources);

    assertThat(baseStepInfo.getResources()).isEqualTo(resources);
    assertThat(baseStepInfo.getResources().getLimits().getCpu().getValue()).isEqualTo("500m");
    assertThat(baseStepInfo.getResources().getLimits().getMemory().getValue()).isEqualTo("1Gi");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testEnvVariables() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put("KEY1", "VALUE1");
    envVars.put("KEY2", "VALUE2");
    ParameterField<Map<String, String>> envVariables = ParameterField.createValueField(envVars);

    baseStepInfo.setEnvVariables(envVariables);

    assertThat(baseStepInfo.getEnvVariables()).isEqualTo(envVariables);
    assertThat(baseStepInfo.getEnvVariables().getValue()).containsEntry("KEY1", "VALUE1");
    assertThat(baseStepInfo.getEnvVariables().getValue()).containsEntry("KEY2", "VALUE2");
  }
}
