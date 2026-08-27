/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.plan.execution.beans.dto.CDModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.CIExecutionInfoDTO;
import io.harness.pms.plan.execution.beans.dto.CIModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.CIPullRequestDTO;
import io.harness.pms.plan.execution.beans.dto.ModulePropertiesDTO;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PIPELINE)
public class ModuleInfoFilterUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testProcessModuleProperties() {
    ModulePropertiesDTO modulePropertiesDTO = ModulePropertiesDTO.builder().build();
    Criteria criteria = new Criteria();
    ModuleInfoFilterUtils.processModuleProperties(modulePropertiesDTO, "moduleInfo", criteria);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject).hasSize(0);

    modulePropertiesDTO = ModulePropertiesDTO.builder()
                              .cd(CDModulePropertiesDTO.builder().build())
                              .ci(CIModulePropertiesDTO.builder().build())
                              .build();
    ModuleInfoFilterUtils.processModuleProperties(modulePropertiesDTO, "moduleInfo", criteria);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject).hasSize(0);

    modulePropertiesDTO = ModulePropertiesDTO.builder()
                              .cd(CDModulePropertiesDTO.builder()
                                      .artifactDisplayNames("testArtifact")
                                      .envIdentifiers("testEnv")
                                      .serviceIdentifiers("testService")
                                      .helmChartVersions("helmChart")
                                      .serviceDefinitionTypes("Kubernetes")
                                      .build())
                              .build();

    ModuleInfoFilterUtils.processModuleProperties(modulePropertiesDTO, "moduleInfo", criteria);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject).hasSize(5);
    assertCDModuleInfoFilters(criteriaObject, 1, List.of("testArtifact"), List.of("testEnv"), List.of("helmChart"),
        List.of("Kubernetes"), List.of("testService"));

    criteria = new Criteria();

    modulePropertiesDTO = ModulePropertiesDTO.builder()
                              .cd(CDModulePropertiesDTO.builder()
                                      .artifactDisplayNames(Arrays.asList("testArtifact1", "testArtifact2"))
                                      .envIdentifiers(Arrays.asList("testEnv1", "testEnv2"))
                                      .serviceIdentifiers(Arrays.asList("testService1", "testService2"))
                                      .helmChartVersions(Arrays.asList("helmChart1", "helmChart2"))
                                      .serviceDefinitionTypes(Arrays.asList("Kubernetes", "Helm"))
                                      .build())
                              .build();

    ModuleInfoFilterUtils.processModuleProperties(modulePropertiesDTO, "moduleInfo", criteria);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject).hasSize(5);

    assertCDModuleInfoFilters(criteriaObject, 2, Arrays.asList("testArtifact1", "testArtifact2"),
        Arrays.asList("testEnv1", "testEnv2"), Arrays.asList("helmChart1", "helmChart2"),
        Arrays.asList("Kubernetes", "Helm"), Arrays.asList("testService1", "testService2"));

    criteria = new Criteria();

    modulePropertiesDTO = ModulePropertiesDTO.builder()
                              .ci(CIModulePropertiesDTO.builder().branch("main").buildType("branch").build())
                              .build();
    ModuleInfoFilterUtils.processModuleProperties(modulePropertiesDTO, "moduleInfo", criteria);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject)
        .hasSize(2)
        .containsEntry("moduleInfo.ci.branch", "main")
        .containsEntry("moduleInfo.ci.buildType", "branch");

    criteria = new Criteria();
    modulePropertiesDTO = ModulePropertiesDTO.builder().ci(CIModulePropertiesDTO.builder().tag("test").build()).build();

    ModuleInfoFilterUtils.processModuleProperties(modulePropertiesDTO, "moduleInfo", criteria);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject).hasSize(1).containsEntry("moduleInfo.ci.tag", "test");

    criteria = new Criteria();
    modulePropertiesDTO =
        ModulePropertiesDTO.builder()
            .ci(CIModulePropertiesDTO.builder()
                    .ciExecutionInfoDTO(
                        CIExecutionInfoDTO.builder()
                            .event("pullRequest")
                            .pullRequest(
                                CIPullRequestDTO.builder().sourceBranch("source").targetBranch("target").build())
                            .build())
                    .build())
            .build();

    ModuleInfoFilterUtils.processModuleProperties(modulePropertiesDTO, "moduleInfo", criteria);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject)
        .hasSize(3)
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.event", "pullRequest")
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.pullRequest.sourceBranch", "source")
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.pullRequest.targetBranch", "target");

    criteria = new Criteria();
    modulePropertiesDTO =
        ModulePropertiesDTO.builder()
            .ci(CIModulePropertiesDTO.builder()
                    .ciExecutionInfoDTO(
                        CIExecutionInfoDTO.builder()
                            .event("pullRequest")
                            .pullRequest(
                                CIPullRequestDTO.builder().sourceBranch("source").targetBranch("target").build())
                            .build())
                    .build())
            .cd(CDModulePropertiesDTO.builder()
                    .artifactDisplayNames("testArtifact")
                    .envIdentifiers("testEnv")
                    .serviceIdentifiers("testService")
                    .helmChartVersions("helmChart")
                    .serviceDefinitionTypes("Kubernetes")
                    .build())
            .build();

    ModuleInfoFilterUtils.processModuleProperties(modulePropertiesDTO, "moduleInfo", criteria);
    criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject)
        .hasSize(8)
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.event", "pullRequest")
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.pullRequest.sourceBranch", "source")
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.pullRequest.targetBranch", "target");
    assertCDModuleInfoFilters(criteriaObject, 1, List.of("testArtifact"), List.of("testEnv"), List.of("helmChart"),
        List.of("Kubernetes"), List.of("testService"));
  }

  private void assertCDModuleInfoFilters(Document criteriaObject, int expected, List<String> artifactDisplayNames,
      List<String> envIdentifiers, List<String> helmChartVersions, List<String> serviceDefinitionTypes,
      List<String> serviceIdentifiers) {
    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("moduleInfo.cd.artifactDisplayNames")).get("$in"))
        .hasSize(expected);
    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("moduleInfo.cd.artifactDisplayNames")).get("$in"))
        .isEqualTo(artifactDisplayNames);

    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("moduleInfo.cd.envIdentifiers")).get("$in")).hasSize(expected);
    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("moduleInfo.cd.envIdentifiers")).get("$in"))
        .isEqualTo(envIdentifiers);

    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("moduleInfo.cd.helmChartVersions")).get("$in"))
        .hasSize(expected);
    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("moduleInfo.cd.helmChartVersions")).get("$in"))
        .isEqualTo(helmChartVersions);

    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("moduleInfo.cd.serviceDefinitionTypes")).get("$in"))
        .hasSize(expected);
    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("moduleInfo.cd.serviceDefinitionTypes")).get("$in"))
        .isEqualTo(serviceDefinitionTypes);

    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("moduleInfo.cd.serviceIdentifiers")).get("$in"))
        .hasSize(expected);
    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("moduleInfo.cd.serviceIdentifiers")).get("$in"))
        .isEqualTo(serviceIdentifiers);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testProcessModulePropertiesFail() {
    ModulePropertiesDTO modulePropertiesDTO = ModulePropertiesDTO.builder()
                                                  .cd(CDModulePropertiesDTO.builder()
                                                          .artifactDisplayNames(1)
                                                          .envIdentifiers("testEnv")
                                                          .serviceIdentifiers("testService")
                                                          .helmChartVersions("helmChart")
                                                          .serviceDefinitionTypes("kubernetes")
                                                          .build())
                                                  .build();
    Criteria criteria = new Criteria();
    assertThatThrownBy(() -> ModuleInfoFilterUtils.processModuleProperties(modulePropertiesDTO, "moduleInfo", criteria))
        .hasMessage("Please verify the value for the filter key: moduleInfo.cd.artifactDisplayNames, only "
            + "String/List<String> type of filter is supported");

    Criteria listCriteria = new Criteria();

    ModulePropertiesDTO modulePropertiesDTO2 = ModulePropertiesDTO.builder()
                                                   .cd(CDModulePropertiesDTO.builder()
                                                           .artifactDisplayNames("artifact")
                                                           .envIdentifiers(Arrays.asList(1, 2))
                                                           .serviceIdentifiers("testService")
                                                           .helmChartVersions("helmChart")
                                                           .serviceDefinitionTypes("kubernetes")
                                                           .build())
                                                   .build();

    assertThatThrownBy(
        () -> ModuleInfoFilterUtils.processModuleProperties(modulePropertiesDTO2, "moduleInfo", listCriteria))
        .hasMessage("Please verify the value for the filter key: moduleInfo.cd.envIdentifiers, only "
            + "String/List<String> type of filter is supported");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testProcessModulePropertiesOROperator() {
    List<Criteria> criteriaList = new ArrayList<>();
    ModulePropertiesDTO modulePropertiesDTO = ModulePropertiesDTO.builder().build();
    ModuleInfoFilterUtils.processModulePropertiesOROperator(modulePropertiesDTO, "moduleInfo", criteriaList);
    assertThat(criteriaList).hasSize(0);

    modulePropertiesDTO = ModulePropertiesDTO.builder()
                              .cd(CDModulePropertiesDTO.builder().build())
                              .ci(CIModulePropertiesDTO.builder().build())
                              .build();

    ModuleInfoFilterUtils.processModulePropertiesOROperator(modulePropertiesDTO, "moduleInfo", criteriaList);
    assertThat(criteriaList).hasSize(0);

    modulePropertiesDTO = ModulePropertiesDTO.builder()
                              .cd(CDModulePropertiesDTO.builder()
                                      .artifactDisplayNames("testArtifact")
                                      .envIdentifiers("testEnv")
                                      .serviceIdentifiers("testService")
                                      .helmChartVersions("helmChart")
                                      .serviceDefinitionTypes("Kubernetes")
                                      .build())
                              .build();
    ModuleInfoFilterUtils.processModulePropertiesOROperator(modulePropertiesDTO, "moduleInfo", criteriaList);
    assertThat(criteriaList).hasSize(1);
    Document criteriaObject = criteriaList.get(0).getCriteriaObject();
    assertThat(criteriaObject).hasSize(5);
    assertCDModuleInfoFilters(criteriaObject, 1, List.of("testArtifact"), List.of("testEnv"), List.of("helmChart"),
        List.of("Kubernetes"), List.of("testService"));

    criteriaList = new ArrayList<>();
    modulePropertiesDTO = ModulePropertiesDTO.builder()
                              .cd(CDModulePropertiesDTO.builder()
                                      .artifactDisplayNames(Arrays.asList("testArtifact1", "testArtifact2"))
                                      .envIdentifiers(Arrays.asList("testEnv1", "testEnv2"))
                                      .serviceIdentifiers(Arrays.asList("testService1", "testService2"))
                                      .helmChartVersions(Arrays.asList("helmChart1", "helmChart2"))
                                      .serviceDefinitionTypes(Arrays.asList("Kubernetes", "Helm"))
                                      .build())
                              .build();
    ModuleInfoFilterUtils.processModulePropertiesOROperator(modulePropertiesDTO, "moduleInfo", criteriaList);
    assertThat(criteriaList).hasSize(1);
    criteriaObject = criteriaList.get(0).getCriteriaObject();
    assertThat(criteriaObject).hasSize(5);

    assertCDModuleInfoFilters(criteriaObject, 2, Arrays.asList("testArtifact1", "testArtifact2"),
        Arrays.asList("testEnv1", "testEnv2"), Arrays.asList("helmChart1", "helmChart2"),
        Arrays.asList("Kubernetes", "Helm"), Arrays.asList("testService1", "testService2"));

    criteriaList = new ArrayList<>();
    modulePropertiesDTO = ModulePropertiesDTO.builder()
                              .ci(CIModulePropertiesDTO.builder().branch("main").buildType("branch").build())
                              .build();
    ModuleInfoFilterUtils.processModulePropertiesOROperator(modulePropertiesDTO, "moduleInfo", criteriaList);
    assertThat(criteriaList).hasSize(1);
    criteriaObject = criteriaList.get(0).getCriteriaObject();
    assertThat(criteriaObject)
        .hasSize(2)
        .containsEntry("moduleInfo.ci.branch", "main")
        .containsEntry("moduleInfo.ci.buildType", "branch");

    criteriaList = new ArrayList<>();
    modulePropertiesDTO = ModulePropertiesDTO.builder().ci(CIModulePropertiesDTO.builder().tag("test").build()).build();
    ModuleInfoFilterUtils.processModulePropertiesOROperator(modulePropertiesDTO, "moduleInfo", criteriaList);
    assertThat(criteriaList).hasSize(1);
    criteriaObject = criteriaList.get(0).getCriteriaObject();
    assertThat(criteriaObject).hasSize(1).containsEntry("moduleInfo.ci.tag", "test");

    criteriaList = new ArrayList<>();
    modulePropertiesDTO =
        ModulePropertiesDTO.builder()
            .ci(CIModulePropertiesDTO.builder()
                    .ciExecutionInfoDTO(
                        CIExecutionInfoDTO.builder()
                            .event("pullRequest")
                            .pullRequest(
                                CIPullRequestDTO.builder().sourceBranch("source").targetBranch("target").build())
                            .build())
                    .build())
            .build();
    ModuleInfoFilterUtils.processModulePropertiesOROperator(modulePropertiesDTO, "moduleInfo", criteriaList);
    assertThat(criteriaList).hasSize(1);
    criteriaObject = criteriaList.get(0).getCriteriaObject();
    assertThat(criteriaObject)
        .hasSize(3)
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.event", "pullRequest")
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.pullRequest.sourceBranch", "source")
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.pullRequest.targetBranch", "target");

    criteriaList = new ArrayList<>();
    modulePropertiesDTO =
        ModulePropertiesDTO.builder()
            .ci(CIModulePropertiesDTO.builder()
                    .ciExecutionInfoDTO(
                        CIExecutionInfoDTO.builder()
                            .event("pullRequest")
                            .pullRequest(
                                CIPullRequestDTO.builder().sourceBranch("source").targetBranch("target").build())
                            .build())
                    .build())
            .cd(CDModulePropertiesDTO.builder()
                    .artifactDisplayNames("testArtifact")
                    .envIdentifiers("testEnv")
                    .serviceIdentifiers("testService")
                    .helmChartVersions("helmChart")
                    .serviceDefinitionTypes("Kubernetes")
                    .build())
            .build();

    ModuleInfoFilterUtils.processModulePropertiesOROperator(modulePropertiesDTO, "moduleInfo", criteriaList);
    assertThat(criteriaList).hasSize(2);
    criteriaObject = criteriaList.get(0).getCriteriaObject();
    assertThat(criteriaObject).hasSize(5);
    assertCDModuleInfoFilters(criteriaObject, 1, List.of("testArtifact"), List.of("testEnv"), List.of("helmChart"),
        List.of("Kubernetes"), List.of("testService"));

    criteriaObject = criteriaList.get(1).getCriteriaObject();
    assertThat(criteriaObject)
        .hasSize(3)
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.event", "pullRequest")
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.pullRequest.sourceBranch", "source")
        .containsEntry("moduleInfo.ci.ciExecutionInfoDTO.pullRequest.targetBranch", "target");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void processNode() throws IOException {
    String json = "{\"ci\":{},\"cd\":{\"identifier\": 1, \"name\": \"test\", "
        + "\"serviceDefinitionTypes\":[[{\"label\":\"Kubernetes\",\"value\":\"Kubernetes\"}]]}}";
    YamlField yamlField = YamlUtils.readTree(json);
    assertThat(yamlField).isNotNull();
    Criteria criteria = new Criteria();
    ModuleInfoFilterUtils.processNode(yamlField.getNode().getCurrJsonNode(), "topKey", criteria);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject)
        .hasSize(3)
        .containsEntry("topKey.cd.identifier", 1)
        .containsEntry("topKey.cd.name", "test")
        .containsKey("topKey.cd.serviceDefinitionTypes");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void processNodeEmptyArray() throws IOException {
    String json = "{\"ci\":{},\"cd\":{\"serviceIdentifiers\":[],\"envIdentifiers\":[\"testcluster\"]}}}";
    YamlField yamlField = YamlUtils.readTree(json);
    assertThat(yamlField).isNotNull();
    Criteria criteria = new Criteria();
    ModuleInfoFilterUtils.processNode(yamlField.getNode().getCurrJsonNode(), "topKey", criteria);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject).hasSize(1);
    assertThat(criteriaObject.containsKey("topKey.cd.serviceIdentifiers")).isFalse();
    assertThat((List<?>) ((Map<?, ?>) criteriaObject.get("topKey.cd.envIdentifiers")).get("$in")).hasSize(1);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get("topKey.cd.envIdentifiers")).get("$in")).get(0))
        .isEqualTo("testcluster");
  }
}
