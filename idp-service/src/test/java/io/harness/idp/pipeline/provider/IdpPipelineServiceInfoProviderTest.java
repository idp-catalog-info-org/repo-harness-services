/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline.provider;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.sdk.core.variables.helper.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IdpPipelineServiceInfoProviderTest extends CategoryTest {
  @Mock private InjectorUtils injectorUtils;

  private IdpPipelineServiceInfoProvider provider;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    provider = new IdpPipelineServiceInfoProvider();
    provider.injectorUtils = injectorUtils;
    doNothing().when(injectorUtils).injectMembers(anyList());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = provider.getPlanCreators();

    assertNotNull(planCreators);
    assertTrue(planCreators.size() > 0);
    assertTrue(planCreators.size() >= 15);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = provider.getFilterJsonCreators();

    assertNotNull(filterJsonCreators);
    assertTrue(filterJsonCreators.size() >= 2);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetVariableCreators() {
    List<VariableCreator> variableCreators = provider.getVariableCreators();

    assertNotNull(variableCreators);
    assertTrue(variableCreators.size() >= 3);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetStepInfo() {
    List<StepInfo> stepInfos = provider.getStepInfo();

    assertNotNull(stepInfos);
    assertTrue(stepInfos.size() >= 13);

    // Verify some key steps are present
    boolean hasRunStep = stepInfos.stream().anyMatch(s -> s.getName().equals("Run"));
    boolean hasPluginStep = stepInfos.stream().anyMatch(s -> s.getName().equals("Plugin"));
    boolean hasGitCloneStep = stepInfos.stream().anyMatch(s -> s.getName().equals("Git Clone"));
    boolean hasCookiecutter = stepInfos.stream().anyMatch(s -> s.getName().equals("Cookiecutter"));
    boolean hasCreateRepo = stepInfos.stream().anyMatch(s -> s.getName().equals("Create Repo"));
    boolean hasDirectPush = stepInfos.stream().anyMatch(s -> s.getName().equals("Direct Push"));
    boolean hasRegisterCatalog = stepInfos.stream().anyMatch(s -> s.getName().equals("Register Catalog"));
    boolean hasCreateCatalog = stepInfos.stream().anyMatch(s -> s.getName().equals("Create Catalog"));
    boolean hasSlackNotify = stepInfos.stream().anyMatch(s -> s.getName().equals("Slack Notify"));
    boolean hasCreateOrganization = stepInfos.stream().anyMatch(s -> s.getName().equals("Create Organization"));
    boolean hasCreateProject = stepInfos.stream().anyMatch(s -> s.getName().equals("Create Project"));
    boolean hasCreateResource = stepInfos.stream().anyMatch(s -> s.getName().equals("Create Resource"));
    boolean hasUpdateCatalogProperty = stepInfos.stream().anyMatch(s -> s.getName().equals("Update Catalog Property"));

    assertTrue(hasRunStep);
    assertTrue(hasPluginStep);
    assertTrue(hasGitCloneStep);
    assertTrue(hasCookiecutter);
    assertTrue(hasCreateRepo);
    assertTrue(hasDirectPush);
    assertTrue(hasRegisterCatalog);
    assertTrue(hasCreateCatalog);
    assertTrue(hasSlackNotify);
    assertTrue(hasCreateOrganization);
    assertTrue(hasCreateProject);
    assertTrue(hasCreateResource);
    assertTrue(hasUpdateCatalogProperty);
  }
}
