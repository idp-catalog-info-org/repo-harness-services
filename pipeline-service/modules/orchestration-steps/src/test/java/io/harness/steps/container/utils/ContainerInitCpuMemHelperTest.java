/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.plugin.StepInfo;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerInitCpuMemHelperTest extends CategoryTest {
  private static final String STEP_UUID = "reuse-consumer-uuid";

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void getStepCpuLimit_emptyPluginResponseList_returnsZero() {
    ContainerInitCpuMemHelper helper = new ContainerInitCpuMemHelper();
    Integer cpu = helper.getStepCpuLimit(stepWrapper(STEP_UUID), "account", emptyPluginsData(STEP_UUID));
    assertThat(cpu).isZero();
  }

  private static ExecutionWrapperConfig stepWrapper(String uuid) {
    ObjectNode step = JsonNodeFactory.instance.objectNode();
    step.put(YamlNode.UUID_FIELD_NAME, uuid);
    return ExecutionWrapperConfig.builder().step(step).build();
  }

  private static Map<StepInfo, PluginCreationResponseList> emptyPluginsData(String uuid) {
    return Map.of(StepInfo.builder().stepUuid(uuid).build(), PluginCreationResponseList.newBuilder().build());
  }
}
