/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.variables;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.plan.creation.info.PlanCreatorServiceInfo;
import io.harness.pms.sdk.helper.PmsSdkHelper;
import io.harness.rule.Owner;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class VariableCreatorMergeServiceTest extends PipelineServiceTestBase {
  private static final String ACCOUNT_ID = "accountId";
  private static final String PIPELINE_YAML = "pipeline:\n"
      + "  identifier: p1\n"
      + "  name: pipeline1\n"
      + "  stages: []\n";

  @Mock PmsSdkHelper pmsSdkHelper;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;

  VariableCreatorMergeService variableCreatorMergeService;

  @Before
  public void setUp() throws Exception {
    variableCreatorMergeService =
        new VariableCreatorMergeService(pmsSdkHelper, pmsGitSyncHelper, Executors.newSingleThreadExecutor());
    Field serviceExpressionMapField = VariableCreatorMergeService.class.getDeclaredField("serviceExpressionMap");
    serviceExpressionMapField.setAccessible(true);
    serviceExpressionMapField.set(variableCreatorMergeService, new HashMap<String, List<String>>());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateVariablesResponseUsesGetServicesV2() throws IOException {
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("pms", new PlanCreatorServiceInfo(Collections.emptyMap(), null, 1));
    when(pmsSdkHelper.getServicesV2()).thenReturn(services);

    VariableMergeServiceResponse response =
        variableCreatorMergeService.createVariablesResponse(PIPELINE_YAML, false, ACCOUNT_ID);

    assertThat(response).isNotNull();
    assertThat(response.getYaml()).isNotBlank();
    verify(pmsSdkHelper).getServicesV2();
    verify(pmsSdkHelper, never()).getServices();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateVariablesResponsesUsesGetServicesV2() {
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("pms", new PlanCreatorServiceInfo(Collections.emptyMap(), null, 1));
    when(pmsSdkHelper.getServicesV2()).thenReturn(services);

    VariableMergeServiceResponse response =
        variableCreatorMergeService.createVariablesResponses(PIPELINE_YAML, false, ACCOUNT_ID);

    assertThat(response).isNotNull();
    verify(pmsSdkHelper).getServicesV2();
    verify(pmsSdkHelper, never()).getServices();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateVariablesResponseV2UsesGetServicesV2() throws IOException {
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("pms", new PlanCreatorServiceInfo(Collections.emptyMap(), null, 1));
    when(pmsSdkHelper.getServicesV2()).thenReturn(services);

    VariableMergeServiceResponse response =
        variableCreatorMergeService.createVariablesResponseV2(ACCOUNT_ID, "orgId", "projectId", PIPELINE_YAML);

    assertThat(response).isNotNull();
    verify(pmsSdkHelper).getServicesV2();
    verify(pmsSdkHelper, never()).getServices();
  }
}
