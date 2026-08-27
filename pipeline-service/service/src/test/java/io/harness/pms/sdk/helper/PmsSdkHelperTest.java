/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.helper;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.PlanCreationServiceGrpc;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.plan.creation.info.PlanCreatorServiceInfo;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import com.google.api.client.util.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSdkHelperTest {
  @Mock private Map<ModuleType, PlanCreationServiceGrpc.PlanCreationServiceBlockingStub> planCreatorServices;
  @Mock private PmsSdkInstanceService pmsSdkInstanceService;
  @InjectMocks PmsSdkHelper pmsSdkHelper;

  private final String ACCOUNT_ID = "accountId";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetServices() {
    Map<String, Map<String, Set<String>>> sdkInstances = new HashMap<>();
    sdkInstances.put("CD", new HashMap<>());
    sdkInstances.get("CD").put("key", Collections.singleton("value"));
    doReturn(sdkInstances).when(pmsSdkInstanceService).getInstanceNameToSupportedTypes();
    assertEquals(pmsSdkHelper.getServices().size(), 0);
    doReturn(true).when(planCreatorServices).containsKey("CD");
    assertEquals(pmsSdkHelper.getServices().size(), 0);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testContainsSupportedDependencyByYamlPath() throws IOException {
    PlanCreatorServiceInfo planCreatorServiceInfo = new PlanCreatorServiceInfo(
        Collections.singletonMap(YAMLFieldNameConstants.PIPELINE, Collections.singleton(PlanCreatorUtils.ANY_TYPE)),
        null, 0);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    boolean result = pmsSdkHelper.containsSupportedDependencyByYamlPath(planCreatorServiceInfo,
        Dependencies.newBuilder().setYaml(yamlContent).putDependencies("pipeline", "pipeline").build());
    assertTrue(result);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCatchExceptionInContainsSupportedDependencyByYamlPath() throws IOException {
    PlanCreatorServiceInfo planCreatorServiceInfo = new PlanCreatorServiceInfo(
        Collections.singletonMap(YAMLFieldNameConstants.PIPELINE, Collections.singleton(PlanCreatorUtils.ANY_TYPE)),
        null, 0);

    // sending yaml as null in dependencies
    assertThatThrownBy(()
                           -> pmsSdkHelper.containsSupportedDependencyByYamlPath(planCreatorServiceInfo,
                               Dependencies.newBuilder().putDependencies("pipeline", "pipeline").build()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testContainsSupportedDependencyByYamlPathNoDeps() throws IOException {
    PlanCreatorServiceInfo planCreatorServiceInfo = new PlanCreatorServiceInfo(
        Collections.singletonMap("pip", Collections.singleton(PlanCreatorUtils.ANY_TYPE)), null, 0);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);

    boolean result = pmsSdkHelper.containsSupportedDependencyByYamlPath(
        planCreatorServiceInfo, Dependencies.newBuilder().setYaml(yamlContent).build());
    assertFalse(result);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testContainsSupportedDependencyByYamlPathNull() throws IOException {
    PlanCreatorServiceInfo planCreatorServiceInfo = new PlanCreatorServiceInfo(
        Collections.singletonMap("pip", Collections.singleton(PlanCreatorUtils.ANY_TYPE)), null, 0);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);

    boolean result = pmsSdkHelper.containsSupportedDependencyByYamlPath(
        planCreatorServiceInfo, Dependencies.newBuilder().setYaml(yamlContent).build());
    assertFalse(result);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetServiceAffinityForGivenDependency() {
    HashMap<String, String> map = new HashMap<>();
    map.put("pipeline", "yaml");
    Set<Map.Entry<String, String>> s = map.entrySet();

    for (Map.Entry<String, String> it : s) {
      Map<String, String> serviceAffinityMap = new HashMap<>();
      serviceAffinityMap.put("pipeline", "yaml");
      String affinityService = pmsSdkHelper.getServiceAffinityForGivenDependency(serviceAffinityMap, it);
      assertEquals(affinityService, "yaml");
    }
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testCatchExceptionInContainsSupportedSingleDependencyByYamlPath() {
    PlanCreatorServiceInfo planCreatorServiceInfo = new PlanCreatorServiceInfo(
        Collections.singletonMap(YAMLFieldNameConstants.PIPELINE, Collections.singleton(PlanCreatorUtils.ANY_TYPE)),
        null, 0);

    HashMap<String, String> map = new HashMap<>();
    map.put("pipeline", "yaml");
    Set<Map.Entry<String, String>> s = map.entrySet();

    for (Map.Entry<String, String> dependencyEntry : s) {
      assertThatThrownBy(()
                             -> PmsSdkHelper.containsSupportedSingleDependencyByYamlPath(
                                 planCreatorServiceInfo, null, dependencyEntry, "0"))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage("Invalid yaml during plan creation for dependency path - yaml");
    }
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetServicesV2RemovesSto() {
    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("sto", new PlanCreatorServiceInfo(new HashMap<>(), null, 3));
    services.put("cd", new PlanCreatorServiceInfo(new HashMap<>(), null, 0));
    services.put("ci", new PlanCreatorServiceInfo(new HashMap<>(), null, 2));
    services.put("pms", new PlanCreatorServiceInfo(new HashMap<>(), null, 1));

    PmsSdkHelper spySdkHelper = spy(pmsSdkHelper);
    doReturn(services).when(spySdkHelper).getServices();

    Map<String, PlanCreatorServiceInfo> response = spySdkHelper.getServicesV2();

    assertThat(response).doesNotContainKey(ModuleType.STO.name().toLowerCase());
    assertThat(response).containsKeys("cd", "ci", "pms");
    assertThat(response.keySet()).containsExactly("cd", "pms", "ci");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetModulePath() {
    Map<String, PmsSdkInstance> pmsSdkInstanceMap = new HashMap<>();
    pmsSdkInstanceMap.put("cd", PmsSdkInstance.builder().name("cd").modulePath("").build());
    pmsSdkInstanceMap.put("idp", PmsSdkInstance.builder().name("idp").modulePath("idp-admin").build());

    doReturn(pmsSdkInstanceMap).when(pmsSdkInstanceService).getActiveSdkInstanceMapFromSecondary(anyList());
    String moduleName1 = "";
    assertEquals(pmsSdkHelper.getModulePath(moduleName1), "");

    String moduleName2 = "cd";
    assertEquals(pmsSdkHelper.getModulePath(moduleName2), "");

    String moduleName3 = "idp";
    assertEquals(pmsSdkHelper.getModulePath(moduleName3), "idp-admin");
  }
}
