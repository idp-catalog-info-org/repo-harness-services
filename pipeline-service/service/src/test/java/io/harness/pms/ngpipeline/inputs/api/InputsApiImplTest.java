/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.ngpipeline.inputs.service.PMSInputsServiceImpl;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.helpers.validate.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.InputsResponseBody;
import io.harness.utils.ScopeResolutionHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class InputsApiImplTest extends PipelineServiceTestBase {
  InputsApiImpl inputsApiImpl;
  @Inject PMSInputsServiceImpl pmsInputsService;
  @Inject ObjectMapper objectMapper;
  @Mock PMSPipelineService pipelineService;
  @Mock ValidateAndMergeHelper validateAndMergeHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock PMSPipelineServiceHelper pipelineServiceHelper;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String PIPELINE_IDENTIFIER = "pipeId";
  String pipelineYaml;
  PipelineEntity pipelineEntity;
  ScopeInfo scopeInfo;

  private String readFile(String filename) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read file " + filename, e);
    }
  }

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    inputsApiImpl = new InputsApiImpl(pmsInputsService, pipelineService, validateAndMergeHelper, scopeResolutionHelper,
        pipelineServiceHelper, pipelineTemplateHelper);
    String pipelineYamlFileName = "pipeline-v1.yaml";
    pipelineYaml = readFile(pipelineYamlFileName);
    pipelineEntity = PipelineEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .yaml(pipelineYaml)
                         .harnessVersion(HarnessYamlVersion.V1)
                         .version(1L)
                         .build();
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_IDENTIFIER)
                    .projectIdentifier(PROJ_IDENTIFIER)
                    .scopeType(ScopeLevel.PROJECT)
                    .uniqueId("uniqueId")
                    .build();
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputs() throws JsonProcessingException {
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, false);

    doReturn(pipelineEntity.getYaml())
        .when(pipelineTemplateHelper)
        .resolvePipelineWithAllTemplatesRuntimeInputs(anyString(), anyString(), anyString(), anyString(), anyString());

    Response response = inputsApiImpl.getPipelineInputs(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", ACCOUNT_ID, null, null, false, null);
    String expectedResponse = readFile("get-inputs-expected-response.json");
    assertThat(response).isNotNull();
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getEntity()).isNotNull();
    InputsResponseBody responseBody = (InputsResponseBody) response.getEntity();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(responseBody)))
        .isEqualTo(objectMapper.readTree(expectedResponse));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputsWithCloneDisabled() throws JsonProcessingException {
    String yaml = readFile("pipeline-v1-disabled-clone.yaml");
    pipelineEntity.setYaml(yaml);
    doReturn(Optional.of(pipelineEntity))
        .when(pipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, false);

    doReturn(pipelineEntity.getYaml())
        .when(pipelineTemplateHelper)
        .resolvePipelineWithAllTemplatesRuntimeInputs(anyString(), anyString(), anyString(), anyString(), anyString());

    Response response = inputsApiImpl.getPipelineInputs(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", ACCOUNT_ID, null, null, false, null);
    String expectedResponse = readFile("get-inputs-clone-disabled-expected-response.json");
    assertThat(response).isNotNull();
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getEntity()).isNotNull();
    InputsResponseBody responseBody = (InputsResponseBody) response.getEntity();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(responseBody)))
        .isEqualTo(objectMapper.readTree(expectedResponse));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetInputsWithTemplateInputs() throws JsonProcessingException {
    String yaml = readFile("pipeline-v1-with-template-inputs.yaml");
    pipelineEntity.setYaml(yaml);
    String processedYaml = readFile("pipeline-v1-with-template-inputs-processed.yaml");
    String preprocessedYaml =
        "pipeline:\n  clone:\n    disabled: true\n  stages:\n    - steps:\n        - group:\n            steps:\n      "
        + "        - template:\n                  uses: group_steps\n                  with:\n                    abc: "
        + "<+input>\n              - template:\n                  uses: group_steps\n                  with:\n         "
        + "           abc: <+input>\n          id: group_1\n        - run:\n            script: <+input>\n          "
        + "id: run_1\n      id: stage_1\n";

    // Parse expected response to extract template_yaml and resolved_yaml
    String expectedResponseJson = readFile("get-inputs-template-inputs-expected-response.json");
    com.fasterxml.jackson.databind.JsonNode expectedJsonNode = objectMapper.readTree(expectedResponseJson);
    String templateYaml = expectedJsonNode.get("template_yaml").asText();
    String resolvedYaml = expectedJsonNode.get("resolved_yaml").asText();

    doReturn(Optional.of(pipelineEntity))
        .when(pipelineService)
        .getPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false, false, false, false,
            scopeInfo, false);

    doReturn(false).when(pipelineServiceHelper).isParentIdQueryingEnabled(ACCOUNT_ID);
    doReturn(false).when(pipelineServiceHelper).isParentIdQueryingEnabledForInputSet(ACCOUNT_ID);

    doReturn(preprocessedYaml).when(pipelineServiceHelper).preProcessPipelineYaml(anyString(), anyBoolean());

    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(resolvedYaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

    doReturn(processedYaml)
        .when(pipelineTemplateHelper)
        .resolvePipelineWithAllTemplatesRuntimeInputs(anyString(), anyString(), anyString(), anyString(), anyString());

    InputSetTemplateResponseDTOPMS inputSetTemplateResponseDTOPMS = InputSetTemplateResponseDTOPMS.builder()
                                                                        .inputSetTemplateYaml(templateYaml)
                                                                        .hasInputSets(false)
                                                                        .replacedExpressions(new ArrayList<>())
                                                                        .replacedExpressionsPerStage(new HashMap<>())
                                                                        .modules(new HashSet<>())
                                                                        .build();

    doReturn(inputSetTemplateResponseDTOPMS)
        .when(validateAndMergeHelper)
        .getInputSetTemplateResponseDTO(eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER),
            eq(PIPELINE_IDENTIFIER), any(), anyBoolean(), any(), anyBoolean(), anyBoolean(), any(), anyString());

    Response response = inputsApiImpl.getPipelineInputs(
        ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, "false", ACCOUNT_ID, null, null, false, null);
    assertThat(response).isNotNull();
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.getEntity()).isNotNull();
    InputsResponseBody responseBody = (InputsResponseBody) response.getEntity();
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(responseBody)))
        .isEqualTo(objectMapper.readTree(expectedResponseJson));
  }
}
