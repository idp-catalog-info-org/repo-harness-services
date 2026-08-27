/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.governance;

import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.EnvironmentGroupEntity;
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.category.element.UnitTests;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.ci.cd.service.EnvironmentGroupService;
import io.harness.ci.cd.service.InfrastructureEntityService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.envgroup.remote.EnvironmentGroupResourceClient;
import io.harness.environment.remote.EnvironmentResourceClient;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.handler.ExpansionResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;
import retrofit2.Call;

@OwnedBy(HarnessTeam.CI)
public class UnifiedEnvironmentExpansionHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account";
  private static final String ORG_ID = "org";
  private static final String PROJECT_ID = "project";
  private static final String ENV_ID = "envId";
  private static final String ENV_ID2 = "envId2";
  private static final String INFRA_ID = "infraId";
  private static final String INFRA_ID2 = "infraId2";
  private static final String CONNECTOR_ID = "connectorId";
  private static final String ENV_GROUP_ID = "envGroupId";
  @Mock private EnvironmentEntityService environmentEntityService;
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private EnvironmentResourceClient environmentResourceClient;
  @Mock private InfrastructureResourceClient infrastructureResourceClient;
  @Mock private EnvironmentGroupService environmentGroupService;
  @Mock private EnvironmentGroupResourceClient environmentGroupResourceClient;
  @Mock private ConnectorInputsMapper connectorInputsMapper;
  @InjectMocks private UnifiedEnvironmentExpansionHandler expansionHandler;
  private ExpansionRequestMetadata metadata;
  private MockedStatic<NGRestUtils> mockedStatic;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    mockedStatic = Mockito.mockStatic(NGRestUtils.class);

    // Setup metadata
    metadata = ExpansionRequestMetadata.newBuilder()
                   .setAccountId(ACCOUNT_ID)
                   .setOrgId(ORG_ID)
                   .setProjectId(PROJECT_ID)
                   .build();

    // Mock environment service
    EnvironmentEntity environmentEntity = EnvironmentEntity.builder()
                                              .accountId(ACCOUNT_ID)
                                              .orgIdentifier(ORG_ID)
                                              .projectIdentifier(PROJECT_ID)
                                              .identifier(ENV_ID)
                                              .name("Test Environment")
                                              .description("Test Environment Description")
                                              .type(EnvironmentType.PreProduction)
                                              .color("#FF0000")
                                              .build();
    doReturn(Optional.of(environmentEntity))
        .when(environmentEntityService)
        .get(anyString(), anyString(), anyString(), anyString());

    // Mock infrastructure service
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .orgIdentifier(ORG_ID)
                                                    .projectIdentifier(PROJECT_ID)
                                                    .identifier(INFRA_ID)
                                                    .name("Test Infrastructure")
                                                    .description("Test Infrastructure Description")
                                                    .yaml(getInfraYaml(INFRA_ID, CONNECTOR_ID))
                                                    .build();
    InfrastructureEntity infrastructureEntity2 = InfrastructureEntity.builder()
                                                     .accountId(ACCOUNT_ID)
                                                     .orgIdentifier(ORG_ID)
                                                     .projectIdentifier(PROJECT_ID)
                                                     .identifier(INFRA_ID2)
                                                     .name("Test Infrastructure 2")
                                                     .description("Test Infrastructure 2 Description")
                                                     .yaml(getInfraYaml(INFRA_ID2, CONNECTOR_ID))
                                                     .build();
    List<InfrastructureEntity> infrastructureEntities = List.of(infrastructureEntity, infrastructureEntity2);
    doReturn(infrastructureEntities)
        .when(infrastructureEntityService)
        .listByEnvRef(anyString(), anyString(), anyString(), anyString(), any(), any(Pageable.class));

    // Mock connector service
    ConnectorDTO connectorDTO =
        ConnectorDTO.builder().connectorInfo(ConnectorInfoDTO.builder().identifier(CONNECTOR_ID).build()).build();
    Call<ResponseDTO<Optional<ConnectorDTO>>> requestToClient = mock(Call.class);
    doReturn(requestToClient).when(connectorResourceClient).get(CONNECTOR_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID);
    when(NGRestUtils.getResponse(requestToClient)).thenReturn(Optional.of(connectorDTO));
  }

  @After
  public void tearDown() throws Exception {
    mockedStatic.close();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpandWithSingleEnvironment() throws IOException {
    String environmentYaml = "environment:\n"
        + "   id:  envId\n"
        + "   deploy-to: infraId";
    JsonNode envNode =
        YamlUtils.readTree(environmentYaml).getNode().getField("environment").getNode().getCurrJsonNode();
    ExpansionResponse response = expansionHandler.expand(envNode, metadata, "environment");

    // Assert
    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.isSuccess()).as("response should be successful").isTrue();
    assertThat(response.getPlacement()).as("placement strategy").isEqualTo(ExpansionPlacementStrategy.REPLACE);
    assertThat(response.getKey()).as("response key").isEqualTo("environment");

    UnifiedEnvironmentExpandedValue value = (UnifiedEnvironmentExpandedValue) response.getValue();
    assertThat(value.getEnvironments()).as("environments should be null for single env").isNull();
    assertThat(value.getEnvironmentGroup()).as("environment group should be null for single env").isNull();
    assertThat(value.getIsMultiEnv()).as("isMultiEnv should be null for single env").isNull();
    assertThat(value.getIsEnvGroup()).as("isEnvGroup should be null for single env").isNull();
    assertThat(value).as("expanded value should not be null").isNotNull();
    assertThat(value.getSequential()).as("sequential should be false for single env").isFalse();

    UnifiedSingleEnvironmentExpandedValue envValue = value.getEnvironment();
    assertThat(envValue.getId()).as("environment id").isEqualTo(ENV_ID);
    assertThat(envValue.getName()).as("environment name").isEqualTo("Test Environment");
    assertThat(envValue.getDescription()).as("environment description").isEqualTo("Test Environment Description");
    assertThat(envValue.getType()).as("environment type").isEqualTo(EnvironmentType.PreProduction);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpandWithMultiEnvironment() throws IOException {
    String multiEnvYaml = "environment: \n"
        + "   sequential: true\n"
        + "   items:\n"
        + "     - id: envId\n"
        + "       deploy-to:\n"
        + "         - infraId\n"
        + "         - infraId2\n"
        + "     - id: envId2\n"
        + "       deploy-to: \n"
        + "         id: infraId2";

    JsonNode envNode = YamlUtils.readTree(multiEnvYaml).getNode().getField("environment").getNode().getCurrJsonNode();
    EnvironmentEntity environmentEntity2 = EnvironmentEntity.builder()
                                               .accountId(ACCOUNT_ID)
                                               .orgIdentifier(ORG_ID)
                                               .projectIdentifier(PROJECT_ID)
                                               .identifier(ENV_ID2)
                                               .name("Test Environment 2")
                                               .description("Test Environment Description 2")
                                               .type(EnvironmentType.Production)
                                               .build();
    doReturn(Optional.of(environmentEntity2))
        .when(environmentEntityService)
        .get(anyString(), anyString(), anyString(), eq(ENV_ID2));

    ExpansionResponse response = expansionHandler.expand(envNode, metadata, "environment");
    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.isSuccess()).as("response should be successful").isTrue();

    UnifiedEnvironmentExpandedValue value = (UnifiedEnvironmentExpandedValue) response.getValue();
    assertThat(value).as("expanded value should not be null").isNotNull();
    assertThat(value.getEnvironments()).as("environments list size").hasSize(2);
    assertThat(value.getIsMultiEnv()).as("isMultiEnv should be true").isTrue();
    assertThat(value.getSequential()).as("sequential should be true").isTrue();

    UnifiedSingleEnvironmentExpandedValue envValue = value.getEnvironments().get(0);
    assertThat(envValue.getId()).as("first environment id").isEqualTo(ENV_ID);
    assertThat(envValue.getName()).as("first environment name").isEqualTo("Test Environment");
    assertThat(envValue.getDescription()).as("first environment description").isEqualTo("Test Environment Description");
    assertThat(envValue.getType()).as("first environment type").isEqualTo(EnvironmentType.PreProduction);

    UnifiedSingleEnvironmentExpandedValue envValue2 = value.getEnvironments().get(1);
    assertThat(envValue2.getId()).as("second environment id").isEqualTo(ENV_ID2);
    assertThat(envValue2.getName()).as("second environment name").isEqualTo("Test Environment 2");
    assertThat(envValue2.getDescription())
        .as("second environment description")
        .isEqualTo("Test Environment Description 2");
    assertThat(envValue2.getType()).as("second environment type").isEqualTo(EnvironmentType.Production);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpandMultiEnvironmentWithEnvGroup() throws IOException {
    String multiEnvWithEnvGroupYaml = "environment:\n"
        + "  sequential: false\n"
        + "  group:\n"
        + "    id: envGroupId\n"
        + "    items:\n"
        + "      - id: envId\n"
        + "        deploy-to:\n"
        + "          - infraId\n"
        + "          - infraId2\n"
        + "      - id: envId2\n"
        + "        deploy-to: infraId2";

    JsonNode envNode =
        YamlUtils.readTree(multiEnvWithEnvGroupYaml).getNode().getField("environment").getNode().getCurrJsonNode();

    EnvironmentGroupEntity envGroupEntity =
        EnvironmentGroupEntity.builder().identifier(ENV_GROUP_ID).name(ENV_GROUP_ID).build();
    doReturn(Optional.of(envGroupEntity))
        .when(environmentGroupService)
        .get(anyString(), anyString(), anyString(), eq(ENV_GROUP_ID));

    EnvironmentEntity environmentEntity2 = EnvironmentEntity.builder()
                                               .accountId(ACCOUNT_ID)
                                               .orgIdentifier(ORG_ID)
                                               .projectIdentifier(PROJECT_ID)
                                               .identifier(ENV_ID2)
                                               .name("Test Environment 2")
                                               .description("Test Environment Description 2")
                                               .type(EnvironmentType.Production)
                                               .build();
    doReturn(Optional.of(environmentEntity2))
        .when(environmentEntityService)
        .get(anyString(), anyString(), anyString(), eq(ENV_ID2));

    ExpansionResponse response = expansionHandler.expand(envNode, metadata, "environment");
    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.isSuccess()).as("response should be successful").isTrue();

    UnifiedEnvironmentExpandedValue value = (UnifiedEnvironmentExpandedValue) response.getValue();
    assertThat(value).as("expanded value should not be null").isNotNull();
    assertThat(value.getEnvironmentGroup()).as("environment group should not be null").isNotNull();
    assertThat(value.getEnvironments()).as("environments should be null for env group").isNull();
    assertThat(value.getEnvironment()).as("single environment should be null for env group").isNull();
    assertThat(value.getIsMultiEnv()).as("isMultiEnv should be true").isTrue();
    assertThat(value.getSequential()).as("sequential should be false").isFalse();
    assertThat(value.getIsEnvGroup()).as("isEnvGroup should be true").isTrue();

    UnifiedEnvGroupExpandedValue envGroupExpandedValue = value.getEnvironmentGroup();
    assertThat(envGroupExpandedValue.getId()).as("env group id").isEqualTo(ENV_GROUP_ID);
    assertThat(envGroupExpandedValue.getName()).as("env group name").isEqualTo(ENV_GROUP_ID);

    List<UnifiedSingleEnvironmentExpandedValue> environments = envGroupExpandedValue.getItems();
    UnifiedSingleEnvironmentExpandedValue envValue = environments.get(0);
    assertThat(envValue.getId()).as("first env id in group").isEqualTo(ENV_ID);
    assertThat(envValue.getName()).as("first env name in group").isEqualTo("Test Environment");
    assertThat(envValue.getDescription())
        .as("first env description in group")
        .isEqualTo("Test Environment Description");
    assertThat(envValue.getType()).as("first env type in group").isEqualTo(EnvironmentType.PreProduction);

    UnifiedSingleEnvironmentExpandedValue envValue2 = environments.get(1);
    assertThat(envValue2.getId()).as("second env id in group").isEqualTo(ENV_ID2);
    assertThat(envValue2.getName()).as("second env name in group").isEqualTo("Test Environment 2");
    assertThat(envValue2.getDescription())
        .as("second env description in group")
        .isEqualTo("Test Environment Description 2");
    assertThat(envValue2.getType()).as("second env type in group").isEqualTo(EnvironmentType.Production);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testValidateEnvNode_whenNull_shouldReturnFalse() {
    assertThat(expansionHandler.validateEnvNode(null)).as("null node should be invalid").isFalse();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testValidateEnvNode_whenNoIdOrItems_shouldReturnFalse() throws IOException {
    String yaml = "environment:\n  sequential: true";
    JsonNode envNode = YamlUtils.readTree(yaml).getNode().getField("environment").getNode().getCurrJsonNode();
    assertThat(expansionHandler.validateEnvNode(envNode)).as("node without id or items should be invalid").isFalse();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testValidateEnvNode_whenHasId_shouldReturnTrue() throws IOException {
    String yaml = "environment:\n  id: envId";
    JsonNode envNode = YamlUtils.readTree(yaml).getNode().getField("environment").getNode().getCurrJsonNode();
    assertThat(expansionHandler.validateEnvNode(envNode)).as("node with id should be valid").isTrue();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testValidateEnvNode_whenHasItems_shouldReturnTrue() throws IOException {
    String yaml = "environment:\n  items:\n    - id: envId";
    JsonNode envNode = YamlUtils.readTree(yaml).getNode().getField("environment").getNode().getCurrJsonNode();
    assertThat(expansionHandler.validateEnvNode(envNode)).as("node with items should be valid").isTrue();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testValidateEnvNode_whenHasGroupWithItems_shouldReturnTrue() throws IOException {
    String yaml = "environment:\n  group:\n    id: grpId\n    items:\n      - id: envId";
    JsonNode envNode = YamlUtils.readTree(yaml).getNode().getField("environment").getNode().getCurrJsonNode();
    assertThat(expansionHandler.validateEnvNode(envNode)).as("node with group items should be valid").isTrue();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpand_whenInvalidEnvNode_shouldReturnErrorResponse() {
    ExpansionResponse response = expansionHandler.expand(null, metadata, "environment");
    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.isSuccess()).as("response should indicate failure").isFalse();
    assertThat(response.getErrorMessage())
        .as("error message should indicate empty environments")
        .isEqualTo("No unified environments are present");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpand_whenExceptionThrown_shouldReturnErrorResponse() throws IOException {
    String yaml = "environment:\n  id: envId\n  deploy-to: infraId";
    JsonNode envNode = YamlUtils.readTree(yaml).getNode().getField("environment").getNode().getCurrJsonNode();

    doReturn(Optional.empty()).when(environmentEntityService).get(anyString(), anyString(), anyString(), anyString());
    Call mockCall = mock(Call.class);
    doReturn(mockCall)
        .when(environmentResourceClient)
        .convertToUnifiedEnvironment(anyString(), anyString(), anyString(), anyString(), any(), any());
    when(NGRestUtils.getResponse(eq(mockCall))).thenThrow(new RuntimeException("Service unavailable"));

    ExpansionResponse response = expansionHandler.expand(envNode, metadata, "environment");
    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.isSuccess()).as("response should indicate failure").isFalse();
    assertThat(response.getErrorMessage()).as("error message should contain exception").contains("Service unavailable");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpand_whenEnvironmentIsExpression_shouldReturnMinimalValue() throws IOException {
    String yaml = "environment:\n  id: <+stage.variables.env>\n  deploy-to: infraId";
    JsonNode envNode = YamlUtils.readTree(yaml).getNode().getField("environment").getNode().getCurrJsonNode();

    ExpansionResponse response = expansionHandler.expand(envNode, metadata, "environment");
    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.isSuccess()).as("response should succeed for expression envs").isTrue();

    UnifiedEnvironmentExpandedValue value = (UnifiedEnvironmentExpandedValue) response.getValue();
    assertThat(value.getEnvironment().getId())
        .as("expression env should preserve the expression as id")
        .isEqualTo("<+stage.variables.env>");
    assertThat(value.getEnvironment().getName()).as("expression env should have null name").isNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpand_whenEnvironmentDoesNotExist_shouldReturnMinimalValue() throws IOException {
    String yaml = "environment:\n  id: nonExistentEnv\n  deploy-to: infraId";
    JsonNode envNode = YamlUtils.readTree(yaml).getNode().getField("environment").getNode().getCurrJsonNode();

    doReturn(Optional.empty())
        .when(environmentEntityService)
        .get(anyString(), anyString(), anyString(), eq("nonExistentEnv"));
    Call mockCall = mock(Call.class);
    doReturn(mockCall)
        .when(environmentResourceClient)
        .convertToUnifiedEnvironment(eq("nonExistentEnv"), anyString(), anyString(), anyString(), any(), any());
    when(NGRestUtils.getResponse(eq(mockCall))).thenReturn(null);

    ExpansionResponse response = expansionHandler.expand(envNode, metadata, "environment");
    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.isSuccess()).as("response should succeed").isTrue();

    UnifiedEnvironmentExpandedValue value = (UnifiedEnvironmentExpandedValue) response.getValue();
    assertThat(value.getEnvironment().getId())
        .as("non-existent env should preserve the ref as id")
        .isEqualTo("nonExistentEnv");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetRequestDTO_withExpressionInfraIds_shouldFilterThem() {
    List<InfraData> infraDataList = List.of(InfraData.builder().id("infra1").build(),
        InfraData.builder().id("<+stage.variables.infra>").build(), InfraData.builder().id("infra2").build());

    var requestDTO = expansionHandler.getRequestDTO(infraDataList);
    assertThat(requestDTO.getInfraIdsToInputYaml())
        .as("expression infra ids should be filtered out")
        .containsOnlyKeys("infra1", "infra2");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildInfrastructureList_whenNoInfraEntities_shouldReturnEmptyList() {
    doReturn(List.of())
        .when(infrastructureEntityService)
        .listByEnvRef(anyString(), anyString(), anyString(), anyString(), any(), any(Pageable.class));

    List<InfraData> infraDataList = List.of(InfraData.builder().id(INFRA_ID).build());
    List<UnifiedInfrastructureExpandedValue> result =
        expansionHandler.buildInfrastructureList(metadata, ENV_ID, infraDataList);

    assertThat(result).as("empty infra entities should return empty list").isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildInfrastructureList_whenAllInfra_shouldIncludeAllEntities() {
    List<InfraData> infraDataList = List.of(InfraData.builder().id("all").build());
    List<UnifiedInfrastructureExpandedValue> result =
        expansionHandler.buildInfrastructureList(metadata, ENV_ID, infraDataList);

    assertThat(result).as("all infra should include all entities").hasSize(2);
    assertThat(result.get(0).getId()).as("first infra id").isEqualTo(INFRA_ID);
    assertThat(result.get(1).getId()).as("second infra id").isEqualTo(INFRA_ID2);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testBuildNgInfrastructureList_whenNullResponse_shouldReturnEmptyList() {
    Call mockCall = mock(Call.class);
    doReturn(mockCall)
        .when(infrastructureResourceClient)
        .convertToUnifiedInfrastructureList(anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    when(NGRestUtils.getResponse(eq(mockCall))).thenReturn(null);

    List<InfraData> infraDataList = List.of(InfraData.builder().id(INFRA_ID).build());
    List<UnifiedInfrastructureExpandedValue> result =
        expansionHandler.buildNgInfrastructureList(metadata, ENV_ID, infraDataList);

    assertThat(result).as("null response should return empty list").isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetUnifiedSingleEnvironmentExpandedValueAndIsNG_whenLocalEntityExists_shouldReturnNotNg() {
    var result =
        expansionHandler.getUnifiedSingleEnvironmentExpandedValueAndIsNG(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID);

    assertThat(result.isNg()).as("local entity should not be NG").isFalse();
    assertThat(result.getEnvironment()).as("environment should not be null").isNotNull();
    assertThat(result.getEnvironment().getId()).as("environment id").isEqualTo(ENV_ID);
    assertThat(result.getEnvironment().getName()).as("environment name").isEqualTo("Test Environment");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetUnifiedSingleEnvironmentExpandedValueAndIsNG_whenNeitherLocalNorRemote_shouldReturnNullEnv() {
    doReturn(Optional.empty())
        .when(environmentEntityService)
        .get(anyString(), anyString(), anyString(), eq("missingEnv"));
    Call mockCall = mock(Call.class);
    doReturn(mockCall)
        .when(environmentResourceClient)
        .convertToUnifiedEnvironment(eq("missingEnv"), anyString(), anyString(), anyString(), any(), any());
    when(NGRestUtils.getResponse(eq(mockCall))).thenReturn(null);

    var result =
        expansionHandler.getUnifiedSingleEnvironmentExpandedValueAndIsNG(ACCOUNT_ID, ORG_ID, PROJECT_ID, "missingEnv");

    assertThat(result.getEnvironment()).as("missing env should return null environment").isNull();
    assertThat(result.isNg()).as("missing env should return isNg=false").isFalse();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpandWithSingleEnvironment_deployToArray() throws IOException {
    String yaml = "environment:\n"
        + "  id: envId\n"
        + "  deploy-to:\n"
        + "    - infraId\n"
        + "    - infraId2";
    JsonNode envNode = YamlUtils.readTree(yaml).getNode().getField("environment").getNode().getCurrJsonNode();
    ExpansionResponse response = expansionHandler.expand(envNode, metadata, "environment");

    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.isSuccess()).as("response should succeed").isTrue();

    UnifiedEnvironmentExpandedValue value = (UnifiedEnvironmentExpandedValue) response.getValue();
    assertThat(value.getEnvironment()).as("environment should not be null").isNotNull();
    assertThat(value.getEnvironment().getInfrastructure())
        .as("should have 2 infrastructures from array deploy-to")
        .hasSize(2);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpandWithSingleEnvironment_deployToObject() throws IOException {
    String yaml = "environment:\n"
        + "  id: envId\n"
        + "  deploy-to:\n"
        + "    id: infraId";
    JsonNode envNode = YamlUtils.readTree(yaml).getNode().getField("environment").getNode().getCurrJsonNode();
    ExpansionResponse response = expansionHandler.expand(envNode, metadata, "environment");

    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.isSuccess()).as("response should succeed").isTrue();

    UnifiedEnvironmentExpandedValue value = (UnifiedEnvironmentExpandedValue) response.getValue();
    assertThat(value.getEnvironment()).as("environment should not be null").isNotNull();
    assertThat(value.getEnvironment().getInfrastructure())
        .as("should have 1 infrastructure from object deploy-to")
        .hasSize(1);
    assertThat(value.getEnvironment().getInfrastructure().get(0).getId())
        .as("infra id from object deploy-to")
        .isEqualTo(INFRA_ID);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpandWithSingleEnvironment_noDeployTo_shouldReturnEmptyInfra() throws IOException {
    String yaml = "environment:\n  id: envId";
    JsonNode envNode = YamlUtils.readTree(yaml).getNode().getField("environment").getNode().getCurrJsonNode();
    ExpansionResponse response = expansionHandler.expand(envNode, metadata, "environment");

    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.isSuccess()).as("response should succeed").isTrue();

    UnifiedEnvironmentExpandedValue value = (UnifiedEnvironmentExpandedValue) response.getValue();
    assertThat(value.getEnvironment()).as("environment should not be null").isNotNull();
    assertThat(value.getEnvironment().getInfrastructure())
        .as("no deploy-to should result in empty infra list")
        .isEmpty();
  }

  private String getInfraYaml(String infraId, String connectorId) {
    return String.format("infrastructure:\n"
            + "  id: %s\n"
            + "  name: %s\n"
            + "  uses: k8s-direct\n"
            + "  with:\n"
            + "    connector: %s\n"
            + "    release: release-test\n"
            + "    namespace: test-name",
        infraId, infraId, connectorId);
  }
}
