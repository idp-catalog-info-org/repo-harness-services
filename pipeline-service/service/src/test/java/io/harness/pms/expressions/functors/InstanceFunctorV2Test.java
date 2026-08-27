/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.instance.outcome.HostOutcome;
import io.harness.cdng.instance.outcome.InstanceOutcome;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ForMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InstanceFunctorV2Test extends CategoryTest {
  @Mock private PmsSweepingOutputService pmsSweepingOutputService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGet() {
    Level level = Level.newBuilder()
                      .setIdentifier("CommandStepIdentifier")
                      .setStrategyMetadata(StrategyMetadata.newBuilder()
                                               .setForMetadata(ForMetadata.newBuilder().setValue("hostName").build())
                                               .setCurrentIteration(1)
                                               .setTotalIterations(1)
                                               .build())
                      .setGroup(StepOutcomeGroup.STEP.name())
                      .build();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(level).build();

    doReturn(RawOptionalSweepingOutput.builder().found(true).output(getInstancesOutcomeRawJson()).build())
        .when(pmsSweepingOutputService)
        .resolveOptional(eq(ambiance), any());
    var instanceFunctorV2 =
        InstanceFunctorV2.builder().ambiance(ambiance).pmsSweepingOutputService(pmsSweepingOutputService).build();

    assertThatThrownBy(() -> instanceFunctorV2.get("name"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Not found step level repeat strategy item");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testProperties() {
    Level level = Level.newBuilder()
                      .setIdentifier("CommandStepIdentifier")
                      .setStrategyMetadata(StrategyMetadata.newBuilder()
                                               .setForMetadata(ForMetadata.newBuilder().setValue("hostName").build())
                                               .setCurrentIteration(1)
                                               .setTotalIterations(1)
                                               .build())
                      .setGroup(StepOutcomeGroup.STEP.name())
                      .setStepType(StepType.newBuilder().setType("CommandStep").build())
                      .build();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(level).build();

    doReturn(RawOptionalSweepingOutput.builder().found(true).output(getInstancesOutcomeRawJson()).build())
        .when(pmsSweepingOutputService)
        .resolveOptional(eq(ambiance), any());
    var instanceFunctorV2 =
        InstanceFunctorV2.builder().ambiance(ambiance).pmsSweepingOutputService(pmsSweepingOutputService).build();

    Map<String, Object> properties = (Map<String, Object>) instanceFunctorV2.get("properties");

    assertThat(properties.get("subnetId")).isEqualTo("subnetId-1234");
    assertThat(properties.get("privateId")).isEqualTo("private-ip-1");
  }

  private String getInstancesOutcomeRawJson() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("subnetId", "subnetId-1234");
    properties.put("privateId", "private-ip-1");

    return RecastOrchestrationUtils.toJson(InstancesOutcome.builder()
                                               .instances(List.of(InstanceOutcome.builder()
                                                                      .name("hostName")
                                                                      .hostName("hostName")
                                                                      .host(HostOutcome.builder()
                                                                                .hostName("hostName")
                                                                                .publicIp("publicIp")
                                                                                .privateIp("privateIp")
                                                                                .properties(properties)
                                                                                .build())
                                                                      .build()))
                                               .build());
  }
}
