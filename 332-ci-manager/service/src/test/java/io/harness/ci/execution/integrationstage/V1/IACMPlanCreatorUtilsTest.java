/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage.V1;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.StageChildrenEntitiesType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.ListValue;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.TemplateType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.IACM)
public class IACMPlanCreatorUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIacmNodesInfoWithPlaybooksAndInventories() {
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setPlaybooks(ParameterField.createValueField(Arrays.asList("playbook-1", "playbook-2")));
    stageNode.setInventories(ParameterField.createValueField(Arrays.asList("inventory-1")));

    Map<String, Object> result = IACMPlanCreatorUtils.getIacmNodesInfo(null, stageNode);

    assertThat(result).containsKey(YAMLFieldNameConstants.IACM_PLAYBOOKS);
    assertThat(result).containsKey(YAMLFieldNameConstants.IACM_INVENTORIES);
    ParameterField<List<String>> playbooks =
        (ParameterField<List<String>>) result.get(YAMLFieldNameConstants.IACM_PLAYBOOKS);
    ParameterField<List<String>> inventories =
        (ParameterField<List<String>>) result.get(YAMLFieldNameConstants.IACM_INVENTORIES);
    assertThat(playbooks.getValue()).containsExactly("playbook-1", "playbook-2");
    assertThat(inventories.getValue()).containsExactly("inventory-1");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIacmNodesInfoWithWorkspaceOnly() {
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setWorkspace(ParameterField.createValueField("my-workspace"));

    Map<String, Object> result = IACMPlanCreatorUtils.getIacmNodesInfo(null, stageNode);

    assertThat(result).containsKey(YAMLFieldNameConstants.IACM_WORKSPACE);
    assertThat(result).doesNotContainKey(YAMLFieldNameConstants.IACM_PLAYBOOKS);
    assertThat(result).doesNotContainKey(YAMLFieldNameConstants.IACM_INVENTORIES);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIacmNodesInfoWithAllFields() {
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setWorkspace(ParameterField.createValueField("ws-1"));
    stageNode.setTofuModule(ParameterField.createValueField("module-1"));
    stageNode.setRemoteExecution(ParameterField.createValueField("remote-1"));
    stageNode.setPlaybooks(ParameterField.createValueField(Arrays.asList("pb-1")));
    stageNode.setInventories(ParameterField.createValueField(Arrays.asList("inv-1")));

    Map<String, Object> result = IACMPlanCreatorUtils.getIacmNodesInfo(null, stageNode);

    assertThat(result).hasSize(5);
    assertThat(result).containsKey(YAMLFieldNameConstants.IACM_WORKSPACE);
    assertThat(result).containsKey(YAMLFieldNameConstants.IACM_TOFU_MODULE);
    assertThat(result).containsKey(YAMLFieldNameConstants.IACM_REMOTE_EXECUTION);
    assertThat(result).containsKey(YAMLFieldNameConstants.IACM_PLAYBOOKS);
    assertThat(result).containsKey(YAMLFieldNameConstants.IACM_INVENTORIES);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIacmNodesInfoWithNullPlaybooks() {
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setPlaybooks(null);
    stageNode.setInventories(null);

    Map<String, Object> result = IACMPlanCreatorUtils.getIacmNodesInfo(null, stageNode);

    assertThat(result).doesNotContainKey(YAMLFieldNameConstants.IACM_PLAYBOOKS);
    assertThat(result).doesNotContainKey(YAMLFieldNameConstants.IACM_INVENTORIES);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIacmStageChildrenEntitiesInfoWithPlaybooks() {
    Map<String, Object> iacmModuleInfo = new HashMap<>();
    iacmModuleInfo.put(YAMLFieldNameConstants.IACM_PLAYBOOKS, ParameterField.createValueField(Arrays.asList("pb-1")));
    iacmModuleInfo.put(
        YAMLFieldNameConstants.IACM_INVENTORIES, ParameterField.createValueField(Arrays.asList("inv-1")));

    Map<String, Object> modulesImplicitNodesInfo = new HashMap<>();
    modulesImplicitNodesInfo.put(TemplateType.IACM.getName(), iacmModuleInfo);

    ListValue.Builder stageChildren = ListValue.newBuilder();
    IACMPlanCreatorUtils.getIacmStageChildrenEntitiesInfo(modulesImplicitNodesInfo, stageChildren);

    List<HarnessValue> values = stageChildren.getValuesList();
    assertThat(values).hasSize(2);
    assertThat(values.stream().map(HarnessValue::getStringValue))
        .contains(StageChildrenEntitiesType.PLAYBOOKS.getDisplayName(),
            StageChildrenEntitiesType.INVENTORIES.getDisplayName());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIacmStageChildrenEntitiesInfoWithWorkspaceAndPlaybooks() {
    Map<String, Object> iacmModuleInfo = new HashMap<>();
    iacmModuleInfo.put(YAMLFieldNameConstants.IACM_WORKSPACE, ParameterField.createValueField("ws-1"));
    iacmModuleInfo.put(YAMLFieldNameConstants.IACM_PLAYBOOKS, ParameterField.createValueField(Arrays.asList("pb-1")));

    Map<String, Object> modulesImplicitNodesInfo = new HashMap<>();
    modulesImplicitNodesInfo.put(TemplateType.IACM.getName(), iacmModuleInfo);

    ListValue.Builder stageChildren = ListValue.newBuilder();
    IACMPlanCreatorUtils.getIacmStageChildrenEntitiesInfo(modulesImplicitNodesInfo, stageChildren);

    List<HarnessValue> values = stageChildren.getValuesList();
    assertThat(values).hasSize(2);
    assertThat(values.stream().map(HarnessValue::getStringValue))
        .contains(
            StageChildrenEntitiesType.WORKSPACE.getDisplayName(), StageChildrenEntitiesType.PLAYBOOKS.getDisplayName());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetIacmStageChildrenEntitiesInfoNoIacm() {
    Map<String, Object> modulesImplicitNodesInfo = new HashMap<>();

    ListValue.Builder stageChildren = ListValue.newBuilder();
    IACMPlanCreatorUtils.getIacmStageChildrenEntitiesInfo(modulesImplicitNodesInfo, stageChildren);

    assertThat(stageChildren.getValuesList()).isEmpty();
  }
}
