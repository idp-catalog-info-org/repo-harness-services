/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers.validate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.inputmetadata.InputsMetadata;
import io.harness.pms.contracts.inputmetadata.InputsMetadataResponse;
import io.harness.pms.contracts.plan.InputsMetadataInfo;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.inputset.inputmetadata.InputsMetadataGenerator;
import io.harness.pms.inputset.inputmetadata.InputsMetadataRequest;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.security.Principal;
import io.harness.security.PrincipalProtoMapper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.UserPrincipal;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
@Category(UnitTests.class)
public class InputsMetadataHelperTest extends PipelineServiceTestBase {
  @Mock private InputsMetadataGenerator inputsMetadataGenerator;

  @Mock private PmsGitSyncHelper gitSyncHelper;

  @Mock private PmsSdkInstanceService pmsSdkInstanceService;
  @InjectMocks private InputsMetadataHelper inputsMetadataHelper;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";

  String pipelineYaml = "pipeline:\n"
      + "  name: deploy\n"
      + "  identifier: deploy\n"
      + "  projectIdentifier: Brijesh\n"
      + "  orgIdentifier: default\n"
      + "  tags:\n"
      + "    T1: V11\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        name: dep\n"
      + "        identifier: dep\n"
      + "        description: \"\"\n"
      + "        type: Deployment\n"
      + "        spec:\n"
      + "          deploymentType: Kubernetes\n"
      + "          service:\n"
      + "            serviceRef: test_service\n"
      + "            serviceInputs:\n"
      + "              serviceDefinition:\n"
      + "                type: Kubernetes\n"
      + "                spec:\n"
      + "                  variables:\n"
      + "                    - name: var1\n"
      + "                      type: String\n"
      + "                      value: <+input>\n"
      + "                    - name: var3\n"
      + "                      type: String\n"
      + "                      value: <+input>\n"
      + "          environment:\n"
      + "            environmentRef: Env\n"
      + "            deployToAll: false\n"
      + "            infrastructureDefinitions:\n"
      + "              - identifier: Infra\n"
      + "          execution:\n"
      + "            steps:\n"
      + "              - step:\n"
      + "                  type: K8sRollingDeploy\n"
      + "                  name: K8sRollingDeploy_1\n"
      + "                  identifier: K8sRollingDeploy_1\n"
      + "                  spec:\n"
      + "                    skipDryRun: false\n"
      + "                    pruningEnabled: false\n"
      + "                  timeout: 10m\n"
      + "        tags: {}\n"
      + "        failureStrategies:\n"
      + "          - onFailure:\n"
      + "              errors:\n"
      + "                - AllErrors\n"
      + "              action:\n"
      + "                type: StageRollback\n"
      + "  variables:\n"
      + "    - name: Var1\n"
      + "      type: Secret\n"
      + "      required: false\n"
      + "      value: github_token\n"
      + "  notificationRules: []\n";
  String runtimeInputFormYaml = "pipeline:\n"
      + "  identifier: deploy\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: dep\n"
      + "        type: Deployment\n"
      + "        spec:\n"
      + "          service:\n"
      + "            serviceInputs:\n"
      + "              serviceDefinition:\n"
      + "                type: Kubernetes\n"
      + "                spec:\n"
      + "                  variables:\n"
      + "                    - name: var1\n"
      + "                      type: String\n"
      + "                      value: <+input>\n"
      + "                    - name: var3\n"
      + "                      type: String\n"
      + "                      value: <+input>\n";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    on(inputsMetadataHelper).set("pmsSdkInstanceService", pmsSdkInstanceService);
    on(inputsMetadataHelper).set("inputsMetadataGenerator", inputsMetadataGenerator);
    SecurityContextBuilder.setContext(PrincipalProtoMapper.toPrincipalDTO(ACCOUNT_ID,
        Principal.newBuilder()
            .setUserPrincipal(UserPrincipal.newBuilder()
                                  .setUserId(StringValue.of("user_id"))
                                  .setUserName(StringValue.of("user_name"))
                                  .build())
            .build()));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetEntityTypeToEntityRefKeyMap() {
    // Given
    List<PmsSdkInstance> activeInstances = List.of(
        PmsSdkInstance.builder()
            .name("cd")
            .inputsMetadataInfo(List.of(
                InputsMetadataInfo.newBuilder().setEntityInputsKey("serviceInputs").setEntityKey("serviceRef").build(),
                InputsMetadataInfo.newBuilder()
                    .setEntityInputsKey("environmentInputs")
                    .setEntityKey("environmentRef")
                    .build()))
            .build(),
        PmsSdkInstance.builder()
            .name("cf")
            .inputsMetadataInfo(List.of(
                InputsMetadataInfo.newBuilder().setEntityInputsKey("infraInputs").setEntityKey("infraRef").build()))
            .build());

    Map<String, String> result = inputsMetadataHelper.getEntityInputsKeyToEntityRefKeyMap(activeInstances);

    assertThat(result).isNotNull();
    assertThat(result).containsEntry("serviceInputs", "serviceRef");
    assertThat(result).containsEntry("environmentInputs", "environmentRef");
    assertThat(result).containsEntry("infraInputs", "infraRef");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetEntityTypeToModuleMap() {
    // Given
    List<PmsSdkInstance> activeInstances = List.of(
        PmsSdkInstance.builder()
            .name("cd")
            .inputsMetadataInfo(List.of(InputsMetadataInfo.newBuilder().setEntityInputsKey("serviceInputs").build(),
                InputsMetadataInfo.newBuilder().setEntityInputsKey("environmentInputs").build()))
            .build(),
        PmsSdkInstance.builder()
            .name("cf")
            .inputsMetadataInfo(List.of(InputsMetadataInfo.newBuilder().setEntityInputsKey("infraInputs").build()))
            .build());

    Map<String, ModuleType> result = inputsMetadataHelper.getEntityInputsKeyToModuleMap(activeInstances);

    assertThat(result).isNotNull();
    assertThat(result).containsEntry("serviceInputs", ModuleType.CD);
    assertThat(result).containsEntry("environmentInputs", ModuleType.CD);
    assertThat(result).containsEntry("infraInputs", ModuleType.CF);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetInputsMetadataRequests() throws IOException {
    JsonNode runtimeInputFormJsonNode = YamlUtils.readTree(runtimeInputFormYaml).getNode().getCurrJsonNode();

    List<PmsSdkInstance> activeInstances = List.of(
        PmsSdkInstance.builder()
            .name("cd")
            .inputsMetadataInfo(List.of(
                InputsMetadataInfo.newBuilder().setEntityInputsKey("serviceInputs").setEntityKey("serviceRef").build(),
                InputsMetadataInfo.newBuilder()
                    .setEntityInputsKey("environmentInputs")
                    .setEntityKey("environmentRef")
                    .build()))
            .build());

    doReturn(List.of(activeInstances.get(0))).when(pmsSdkInstanceService).getActiveInstances();

    Set<InputsMetadataRequest> requests =
        inputsMetadataHelper.getInputsMetadataRequests(pipelineYaml, runtimeInputFormJsonNode);

    assertThat(requests).isNotNull();
    assertThat(requests).hasSize(1);

    InputsMetadataRequest inputsMetadataRequest = requests.stream().collect(Collectors.toList()).get(0);
    assertEquals(inputsMetadataRequest.getFqn(), "pipeline.stages.dep.spec.service");
    assertEquals(inputsMetadataRequest.getEntityType(), "serviceInputs");
    assertEquals(inputsMetadataRequest.getEntityId(), "test_service");
    assertEquals(inputsMetadataRequest.getInputFormYaml(),
        "serviceInputs:\n"
            + "  serviceDefinition:\n"
            + "    type: Kubernetes\n"
            + "    spec:\n"
            + "      variables:\n"
            + "        - name: var1\n"
            + "          type: String\n"
            + "          value: <+input>\n"
            + "        - name: var3\n"
            + "          type: String\n"
            + "          value: <+input>\n");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testMergeRuntimeInputsMetadataIntoTemplate() {
    InputsMetadataResponse response =
        InputsMetadataResponse.newBuilder()
            .setFqn("pipeline.stages.dep.spec.service")
            .setSuccess(true)
            .putAllResult(Map.of("serviceInputs.serviceDefinition.spec.variables.var1",
                InputsMetadata.newBuilder().setRequired(true).setDescription("some description here").build()))
            .build();

    when(inputsMetadataGenerator.fetchInputsMetadata(any(), any())).thenReturn(Set.of(response));
    doReturn(List.of(PmsSdkInstance.builder()
                         .name("cd")
                         .inputsMetadataInfo(List.of(InputsMetadataInfo.newBuilder()
                                                         .setEntityKey("serviceRef")
                                                         .setEntityInputsKey("serviceInputs")
                                                         .build()))
                         .build()))
        .when(pmsSdkInstanceService)
        .getActiveInstances();

    String result = inputsMetadataHelper.mergeRuntimeInputsMetadataIntoTemplate(IdentifierRef.builder()
                                                                                    .accountIdentifier(ACCOUNT_ID)
                                                                                    .orgIdentifier(ORG_ID)
                                                                                    .projectIdentifier(PROJECT_ID)
                                                                                    .identifier(PIPELINE_ID)
                                                                                    .build(),
        pipelineYaml, runtimeInputFormYaml);

    assertThat(result).isEqualTo("pipeline:\n"
        + "  identifier: deploy\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: dep\n"
        + "        type: Deployment\n"
        + "        spec:\n"
        + "          service:\n"
        + "            serviceInputs:\n"
        + "              serviceDefinition:\n"
        + "                type: Kubernetes\n"
        + "                spec:\n"
        + "                  variables:\n"
        + "                    - name: var1\n"
        + "                      type: String\n"
        + "                      value: <+input>\n"
        + "                      required: true\n"
        + "                      description: some description here\n"
        + "                    - name: var3\n"
        + "                      type: String\n"
        + "                      value: <+input>\n");
  }
}
