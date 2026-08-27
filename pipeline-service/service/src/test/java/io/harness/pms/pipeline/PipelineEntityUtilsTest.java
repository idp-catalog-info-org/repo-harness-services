/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineEntityUtilsTest extends CategoryTest {
  @InjectMocks private PipelineEntityUtils pipelineEntityUtils;
  @Mock private PipelineSdkPrioritySupport pipelineSdkPrioritySupport;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void getModuleNameFromPipelineEntityTest() throws IOException {
    List<String> modules = List.of("pms", "common");

    String module = pipelineEntityUtils.getModuleNameFromPipelineEntity(modules);
    assertThat(module).isEqualTo("cd");

    modules = List.of("ci", "cd", "pms", "sto");
    module = pipelineEntityUtils.getModuleNameFromPipelineEntity(modules);
    assertThat(module).isEqualTo("ci");

    modules = List.of("pms", "sto", "ci", "cd");
    module = pipelineEntityUtils.getModuleNameFromPipelineEntity(modules);
    assertThat(module).isEqualTo("sto");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetModuleNameFromPipelineEntityHonorsSdkPriorityWhenFeatureFlagEnabled() {
    List<String> modules = List.of("sto", "cd", "pms");
    when(pipelineSdkPrioritySupport.isHonorPipelineSdkPriorityEnabled("accountId")).thenReturn(true);
    when(pipelineSdkPrioritySupport.getPipelineSdkPriority()).thenReturn(Map.of("cd", 1, "sto", 4));

    assertThat(pipelineEntityUtils.getModuleNameFromPipelineEntity(modules, "accountId")).isEqualTo("cd");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetModuleNameFromPipelineEntityUsesLegacyOrderWhenFeatureFlagDisabled() {
    List<String> modules = List.of("sto", "cd", "pms");
    when(pipelineSdkPrioritySupport.isHonorPipelineSdkPriorityEnabled("accountId")).thenReturn(false);

    assertThat(pipelineEntityUtils.getModuleNameFromPipelineEntity(modules, "accountId")).isEqualTo("sto");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetModuleNameFromPipelineEntityWithoutAccountIdUsesLegacyOrder() {
    List<String> modules = List.of("sto", "cd", "pms");
    when(pipelineSdkPrioritySupport.isHonorPipelineSdkPriorityEnabled(null)).thenReturn(false);

    assertThat(pipelineEntityUtils.getModuleNameFromPipelineEntity(modules, null)).isEqualTo("sto");
  }
}
