/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.rule.OwnerRule.CAMERON;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.aisre.AisreCreateIncidentStepParameters;
import io.harness.steps.aisre.AisreStepUtils;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CHAOS)
public class AisreCreateIncidentStepInfoTest extends CategoryTest {
  private static final String STEP_YAML =
      "{\"type\":\"AISRE_CreateIncident\",\"identifier\":\"AISRE_CreateIncident_1\","
      + "\"name\":\"AISRE_CreateIncident_1\",\"spec\":{"
      + "\"title\":\"A "
      + "Incident\",\"orgIdentifier\":\"rajorg\",\"projectIdentifier\":\"rajproj\",\"severity\":\"1\",\"service\":"
      + "\"newsvc\","
      + "\"environment\":\"test\",\"description\":\"hw\",\"labels\":[\"hello\",\"world\",\"wo\"],"
      + "\"attachPipelineContext\":true,\"pageOnCall\":true,\"incidentType\":\"INC\","
      + "\"commanderHarnessUserId\":\"lv0euRhKRCyiXWzS7pOg6g\","
      + "\"fields\":[{\"name\":\"status\",\"value\":\"new\"}]}}";

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testDeserializeFieldsArrayFromYaml() throws Exception {
    AisreCreateIncidentStepNode stepNode = YamlUtils.read(STEP_YAML, AisreCreateIncidentStepNode.class);

    assertThat(stepNode.getAisreCreateIncidentStepInfo().getLabels().getValue())
        .containsExactly("hello", "world", "wo");
    assertThat(stepNode.getAisreCreateIncidentStepInfo().getOrgIdentifier().getValue()).isEqualTo("rajorg");
    assertThat(stepNode.getAisreCreateIncidentStepInfo().getProjectIdentifier().getValue()).isEqualTo("rajproj");
    assertThat(stepNode.getAisreCreateIncidentStepInfo().getFields()).hasSize(1);
    assertThat(stepNode.getAisreCreateIncidentStepInfo().getFields().get(0).getName()).isEqualTo("status");
    assertThat(stepNode.getAisreCreateIncidentStepInfo().getFields().get(0).getValue().getValue()).isEqualTo("new");
    assertThat(stepNode.getAisreCreateIncidentStepInfo().getPageOnCall().getValue()).isTrue();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testGetSpecParametersConvertsFieldsListToMap() {
    AisreCreateIncidentStepInfo stepInfo = AisreCreateIncidentStepInfo.builder()
                                               .title(ParameterField.createValueField("A Incident"))
                                               .orgIdentifier(ParameterField.createValueField("rajorg"))
                                               .projectIdentifier(ParameterField.createValueField("rajproj"))
                                               .severity(ParameterField.createValueField("1"))
                                               .service(ParameterField.createValueField("newsvc"))
                                               .pageOnCall(ParameterField.createValueField(true))
                                               .fields(List.of(io.harness.steps.aisre.AisreField.builder()
                                                                   .name("status")
                                                                   .value(ParameterField.createValueField("new"))
                                                                   .build()))
                                               .build();

    SpecParameters specParameters = stepInfo.getSpecParameters();

    assertThat(specParameters).isInstanceOf(AisreCreateIncidentStepParameters.class);
    AisreCreateIncidentStepParameters parameters = (AisreCreateIncidentStepParameters) specParameters;
    assertThat(parameters.getPageOnCall().getValue()).isTrue();
    assertThat(parameters.getOrgIdentifier().getValue()).isEqualTo("rajorg");
    assertThat(parameters.getProjectIdentifier().getValue()).isEqualTo("rajproj");
    assertThat(parameters.getFields().getValue()).containsEntry("status", "new");
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void testProcessFieldsListRejectsDuplicates() {
    org.assertj.core.api.Assertions
        .assertThatThrownBy(
            ()
                -> AisreStepUtils.processFieldsList(List.of(io.harness.steps.aisre.AisreField.builder()
                                                                .name("status")
                                                                .value(ParameterField.createValueField("new"))
                                                                .build(),
                    io.harness.steps.aisre.AisreField.builder()
                        .name("status")
                        .value(ParameterField.createValueField("investigating"))
                        .build())))
        .isInstanceOf(io.harness.exception.InvalidRequestException.class)
        .hasMessageContaining("status");
  }
}
