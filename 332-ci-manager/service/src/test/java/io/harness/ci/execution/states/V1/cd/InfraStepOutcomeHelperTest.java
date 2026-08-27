/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states.V1.cd;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.cd.beans.outcomes.InfraStepOutcome;
import io.harness.ci.execution.common.InfraConfigOutput;
import io.harness.ci.states.V1.cd.InfraStepOutcomeHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.unified.cd.infrastructure.InfraConfig;
import io.harness.unified.cd.infrastructure.InfraInfoConfig;
import io.harness.unified.cd.infrastructure.InfraType;
import io.harness.unified.cd.infrastructure.InfrastructureMetadata;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InfraStepOutcomeHelperTest {
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @InjectMocks private InfraStepOutcomeHelper infraStepOutcomeHelper;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Map.of("accountId", "acc", "orgIdentifier", "org", "projectIdentifier", "proj"))
        .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetInfraStepOutcome_WithoutInfraOutput() {
    Ambiance ambiance = buildAmbiance();
    InfrastructureMetadata infraMetadata = InfrastructureMetadata.builder()
                                               .identifier("infra1")
                                               .name("Test Infra")
                                               .description("test description")
                                               .tags(new HashMap<>())
                                               .build();
    EnvironmentOutcome envOutcome = EnvironmentOutcome.builder().ref("env1").build();
    InfraInfoConfig infraInfoConfig = InfraInfoConfig.builder()
                                          .uses(InfraType.K8S_DIRECT)
                                          .allowSimultaneousDeployments(ParameterField.createValueField(false))
                                          .build();
    InfraConfig infraConfig = InfraConfig.builder().infraInfoConfig(infraInfoConfig).build();

    when(sweepingOutputService.resolveOptional(
             any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(InfraStepOutcomeHelper.INFRA_OUTPUT))))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    InfraStepOutcome result = infraStepOutcomeHelper.getInfraStepOutcome(
        ambiance, infraMetadata, envOutcome, infraConfig, "svc1", new String[] {"ns", "default"});

    assertThat(result).isNotNull();
    assertThat(result.getIdentifier()).isEqualTo("infra1");
    assertThat(result.getName()).isEqualTo("Test Infra");
    assertThat(result.getKind()).isEqualTo(InfraType.K8S_DIRECT.getDisplayName());
    assertThat(result.getEnvironment()).isEqualTo(envOutcome);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetInfraStepOutcome_WithInfraOutput() {
    Ambiance ambiance = buildAmbiance();
    InfrastructureMetadata infraMetadata =
        InfrastructureMetadata.builder().identifier("infra1").name("Test Infra").tags(new HashMap<>()).build();
    EnvironmentOutcome envOutcome = EnvironmentOutcome.builder().ref("env1").build();
    InfraInfoConfig infraInfoConfig = InfraInfoConfig.builder()
                                          .uses(InfraType.K8S_DIRECT)
                                          .allowSimultaneousDeployments(ParameterField.createValueField(true))
                                          .build();
    InfraConfig infraConfig = InfraConfig.builder().infraInfoConfig(infraInfoConfig).build();

    InfraConfigOutput infraConfigOutput = InfraConfigOutput.builder().build();
    infraConfigOutput.put("namespace", "default");
    infraConfigOutput.put("cluster", "test-cluster");

    when(sweepingOutputService.resolveOptional(
             any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(InfraStepOutcomeHelper.INFRA_OUTPUT))))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(infraConfigOutput).build());

    InfraStepOutcome result = infraStepOutcomeHelper.getInfraStepOutcome(
        ambiance, infraMetadata, envOutcome, infraConfig, "svc1", new String[] {"ns", "default"});

    assertThat(result).isNotNull();
    assertThat(result.getIdentifier()).isEqualTo("infra1");
    assertThat(result.get("namespace")).isEqualTo("default");
    assertThat(result.get("cluster")).isEqualTo("test-cluster");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetInfraStepOutcome_AllowSimultaneousDeploymentsTrue() {
    Ambiance ambiance = buildAmbiance();
    InfrastructureMetadata infraMetadata =
        InfrastructureMetadata.builder().identifier("infra1").name("Test Infra").tags(new HashMap<>()).build();
    EnvironmentOutcome envOutcome = EnvironmentOutcome.builder().ref("env1").build();
    InfraInfoConfig infraInfoConfig = InfraInfoConfig.builder()
                                          .uses(InfraType.K8S_DIRECT)
                                          .allowSimultaneousDeployments(ParameterField.createValueField(true))
                                          .build();
    InfraConfig infraConfig = InfraConfig.builder().infraInfoConfig(infraInfoConfig).build();

    when(sweepingOutputService.resolveOptional(any(Ambiance.class), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    InfraStepOutcome result = infraStepOutcomeHelper.getInfraStepOutcome(
        ambiance, infraMetadata, envOutcome, infraConfig, "svc1", new String[] {"ns"});

    assertThat(result).isNotNull();
    // When allowSimultaneousDeployments is true, addRcStep should be false
    assertThat(result.isAddRcStep()).isFalse();
  }
}
