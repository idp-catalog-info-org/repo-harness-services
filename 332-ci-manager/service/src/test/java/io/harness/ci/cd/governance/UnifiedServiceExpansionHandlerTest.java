/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.cd.governance;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.ServiceBasicInfo;
import io.harness.app.beans.entities.ServiceEntity;
import io.harness.category.element.UnitTests;
import io.harness.ci.cd.service.ServiceEntityService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.handler.ExpansionResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.unified.service.NgServiceResourceClient;
import io.harness.unified.service.UnifiedServiceConverterResponse;
import io.harness.unified.service.UnifiedServiceConverterResponseDTO;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import retrofit2.Call;

@OwnedBy(HarnessTeam.CI)
public class UnifiedServiceExpansionHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SERVICE_ID1 = "serviceId";
  private static final String SERVICE_ID2 = "serviceId2";
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private NgServiceResourceClient ngServiceResourceClient;
  @InjectMocks private UnifiedServiceExpansionHandler expansionHandler;
  private MockedStatic<NGRestUtils> mockedNGRestUtils;
  private ExpansionRequestMetadata metadata;
  private ServiceEntity serviceEntity1;
  private ServiceEntity serviceEntity2;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    mockedNGRestUtils = Mockito.mockStatic(NGRestUtils.class);

    // Setup metadata
    metadata = ExpansionRequestMetadata.newBuilder()
                   .setAccountId(ACCOUNT_ID)
                   .setOrgId(ORG_ID)
                   .setProjectId(PROJECT_ID)
                   .build();

    // Create service entities
    serviceEntity1 = ServiceEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_ID)
                         .projectIdentifier(PROJECT_ID)
                         .identifier(SERVICE_ID1)
                         .name(SERVICE_ID1)
                         .description("test service one")
                         .tag(NGTag.builder().key("env").value("dev").build())
                         .build();

    serviceEntity2 = ServiceEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_ID)
                         .projectIdentifier(PROJECT_ID)
                         .identifier(SERVICE_ID2)
                         .name(SERVICE_ID2)
                         .description("test Service two")
                         .tag(NGTag.builder().key("env").value("prod").build())
                         .build();

    // Mock service entity service
    doReturn(Optional.of(serviceEntity1))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), eq(SERVICE_ID1), anyBoolean());

    doReturn(Optional.of(serviceEntity2))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), eq(SERVICE_ID2), anyBoolean());
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpandWithSingleService() throws IOException {
    String serviceYaml = "service: serviceId";
    JsonNode serviceNode = YamlUtils.readTree(serviceYaml).getNode().getField("service").getNode().getCurrJsonNode();
    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    assertThat(response).as("response should not be null").isNotNull();
    assertThat(response.getPlacement()).as("placement should be REPLACE").isEqualTo(ExpansionPlacementStrategy.REPLACE);
    assertThat(response.getKey()).as("key should be service").isEqualTo("service");

    UnifiedServiceExpandedValue value = (UnifiedServiceExpandedValue) response.getValue();
    assertThat(value).as("expanded value should not be null").isNotNull();
    assertThat(value.getServicesInfo()).as("should have 1 service").hasSize(1);
    assertThat(value.getServicesInfo().get(0).getId()).as("service id").isEqualTo(SERVICE_ID1);
    assertThat(value.getServicesInfo().get(0).getName()).as("service name").isEqualTo(SERVICE_ID1);
    verify(serviceEntityService, times(1)).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID1, false);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testExpandWithMultipleServices() throws IOException {
    String multiServiceYaml = "service:\n"
        + " items:\n"
        + "    - serviceId\n"
        + "    - id: serviceId2";
    JsonNode serviceNode =
        YamlUtils.readTree(multiServiceYaml).getNode().getField("service").getNode().getCurrJsonNode();

    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    UnifiedServiceExpandedValue value = (UnifiedServiceExpandedValue) response.getValue();
    assertThat(value.getServicesInfo()).as("should have 2 services").hasSize(2);

    List<String> serviceIds = new ArrayList<>();
    for (ServiceBasicInfo serviceBasicInfo : value.getServicesInfo()) {
      serviceIds.add(serviceBasicInfo.getId());
    }

    assertThat(serviceIds).as("should contain both service ids").contains(SERVICE_ID1, SERVICE_ID2);

    verify(serviceEntityService, times(1)).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID1, false);
    verify(serviceEntityService, times(1)).get(ACCOUNT_ID, ORG_ID, PROJECT_ID, SERVICE_ID2, false);
  }

  @After
  public void tearDown() {
    mockedNGRestUtils.close();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExpand_whenNullServiceNode_shouldReturnErrorResponse() {
    ExpansionResponse response = expansionHandler.expand(null, metadata, "service");

    assertThat(response.isSuccess()).as("null node should return failure").isFalse();
    assertThat(response.getErrorMessage())
        .as("error message should mention empty services")
        .contains("No unified services are present");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExpand_whenServiceNodeHasNoIdOrItems_shouldReturnErrorResponse() throws IOException {
    String yaml = "service:\n  name: something";
    JsonNode serviceNode = YamlUtils.readTree(yaml).getNode().getField("service").getNode().getCurrJsonNode();

    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    assertThat(response.isSuccess()).as("node without id or items should fail").isFalse();
    assertThat(response.getErrorMessage())
        .as("should report empty services")
        .contains("No unified services are present");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExpand_whenServiceIdIsExpression_shouldReturnErrorResponse() throws IOException {
    String yaml = "service: <+pipeline.variables.svc>";
    JsonNode serviceNode = YamlUtils.readTree(yaml).getNode().getField("service").getNode().getCurrJsonNode();

    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    assertThat(response.isSuccess()).as("expression service id should fail").isFalse();
    assertThat(response.getErrorMessage()).as("error should mention expression").contains("expression");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExpand_whenServiceNotFoundLocally_shouldFallbackToNgClient() throws IOException {
    String yaml = "service: newSvc";
    JsonNode serviceNode = YamlUtils.readTree(yaml).getNode().getField("service").getNode().getCurrJsonNode();

    doReturn(Optional.empty())
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), eq("newSvc"), anyBoolean());

    UnifiedServiceConverterResponseDTO responseDTO = UnifiedServiceConverterResponseDTO.builder()
                                                         .identifier("newSvc")
                                                         .name("New Service")
                                                         .description("from NG")
                                                         .tags(Map.of("team", "ci"))
                                                         .build();
    UnifiedServiceConverterResponse converterResponse =
        UnifiedServiceConverterResponse.builder().responseDTO(responseDTO).build();

    Call mockCall = mock(Call.class);
    doReturn(mockCall)
        .when(ngServiceResourceClient)
        .convertToUnified(anyString(), anyString(), anyString(), anyString(), isNull(), isNull(), any());
    mockedNGRestUtils.when(() -> NGRestUtils.getResponse(eq(mockCall))).thenReturn(converterResponse);

    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    assertThat(response.isSuccess()).as("NG fallback should succeed").isTrue();
    UnifiedServiceExpandedValue value = (UnifiedServiceExpandedValue) response.getValue();
    assertThat(value.getServicesInfo()).as("should have one service from NG").hasSize(1);
    assertThat(value.getServicesInfo().get(0).getName()).as("name from NG response").isEqualTo("New Service");
    assertThat(value.getServicesInfo().get(0).getDescription()).as("description from NG").isEqualTo("from NG");
    assertThat(value.getServicesInfo().get(0).getTags()).as("tags from NG").containsEntry("team", "ci");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExpand_whenServiceNotFoundAnywhereLocal_shouldReturnNotFoundError() throws IOException {
    String yaml = "service: missingSvc";
    JsonNode serviceNode = YamlUtils.readTree(yaml).getNode().getField("service").getNode().getCurrJsonNode();

    doReturn(Optional.empty())
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), eq("missingSvc"), anyBoolean());

    Call mockCall = mock(Call.class);
    doReturn(mockCall)
        .when(ngServiceResourceClient)
        .convertToUnified(anyString(), anyString(), anyString(), anyString(), isNull(), isNull(), any());
    mockedNGRestUtils.when(() -> NGRestUtils.getResponse(eq(mockCall))).thenReturn(null);

    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    assertThat(response.isSuccess()).as("missing service should fail").isFalse();
    assertThat(response.getErrorMessage())
        .as("error should mention not found")
        .contains("Could not find unified service");
    assertThat(response.getErrorMessage()).as("should include service id").contains("missingSvc");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExpand_whenNgClientReturnsNullResponseDto_shouldReturnNotFoundError() throws IOException {
    String yaml = "service: partialSvc";
    JsonNode serviceNode = YamlUtils.readTree(yaml).getNode().getField("service").getNode().getCurrJsonNode();

    doReturn(Optional.empty())
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), eq("partialSvc"), anyBoolean());

    UnifiedServiceConverterResponse converterResponse =
        UnifiedServiceConverterResponse.builder().responseDTO(null).build();

    Call mockCall = mock(Call.class);
    doReturn(mockCall)
        .when(ngServiceResourceClient)
        .convertToUnified(anyString(), anyString(), anyString(), anyString(), isNull(), isNull(), any());
    mockedNGRestUtils.when(() -> NGRestUtils.getResponse(eq(mockCall))).thenReturn(converterResponse);

    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    assertThat(response.isSuccess()).as("null responseDTO should fail").isFalse();
    assertThat(response.getErrorMessage()).as("should report not found").contains("Could not find unified service");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExpand_whenMultipleServicesWithExpressionMixed_shouldReturnExpressionError() throws IOException {
    String yaml = "service:\n"
        + "  items:\n"
        + "    - serviceId\n"
        + "    - <+stage.variables.svc>";
    JsonNode serviceNode = YamlUtils.readTree(yaml).getNode().getField("service").getNode().getCurrJsonNode();

    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    assertThat(response.isSuccess()).as("mixed expression should fail").isFalse();
    assertThat(response.getErrorMessage()).as("should mention expression ids").contains("expression");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExpand_whenServiceNodeIsObjectWithId_shouldResolveById() throws IOException {
    String yaml = "service:\n  id: serviceId";
    JsonNode serviceNode = YamlUtils.readTree(yaml).getNode().getField("service").getNode().getCurrJsonNode();

    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    assertThat(response.isSuccess()).as("object with id field should succeed").isTrue();
    UnifiedServiceExpandedValue value = (UnifiedServiceExpandedValue) response.getValue();
    assertThat(value.getServicesInfo().get(0).getId()).as("should resolve by id field").isEqualTo(SERVICE_ID1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToServiceBasicInfo_fromServiceEntity_shouldMapAllFields() {
    ServiceEntity entity =
        ServiceEntity.builder()
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .identifier("svc-id")
            .name("My Service")
            .description("desc")
            .tags(List.of(NGTag.builder().key("k1").value("v1").build(), NGTag.builder().key("k2").value("v2").build()))
            .build();

    ServiceBasicInfo info = expansionHandler.toServiceBasicInfo(entity);

    assertThat(info.getId()).as("id from identifier").isEqualTo("svc-id");
    assertThat(info.getName()).as("name mapped").isEqualTo("My Service");
    assertThat(info.getDescription()).as("description mapped").isEqualTo("desc");
    assertThat(info.getAccountIdentifier()).as("account mapped").isEqualTo("acc");
    assertThat(info.getOrgIdentifier()).as("org mapped").isEqualTo("org");
    assertThat(info.getProjectIdentifier()).as("project mapped").isEqualTo("proj");
    assertThat(info.getTags()).as("tags should have 2 entries").hasSize(2);
    assertThat(info.getTags()).as("tags mapped from NGTag list").containsEntry("k1", "v1").containsEntry("k2", "v2");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExpand_whenItemsArrayHasEmptyNodes_shouldSkipEmptyIds() throws IOException {
    String yaml = "service:\n"
        + "  items:\n"
        + "    - serviceId\n"
        + "    - name: noIdNode";
    JsonNode serviceNode = YamlUtils.readTree(yaml).getNode().getField("service").getNode().getCurrJsonNode();

    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    assertThat(response.isSuccess()).as("should succeed with valid service").isTrue();
    UnifiedServiceExpandedValue value = (UnifiedServiceExpandedValue) response.getValue();
    assertThat(value.getServicesInfo()).as("should only have 1 service (noIdNode skipped)").hasSize(1);
    assertThat(value.getServicesInfo().get(0).getId()).as("should be the valid service").isEqualTo(SERVICE_ID1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExpand_whenServiceEntityHasNullTags_shouldHandleGracefully() throws IOException {
    String yaml = "service: serviceId";
    JsonNode serviceNode = YamlUtils.readTree(yaml).getNode().getField("service").getNode().getCurrJsonNode();

    ServiceEntity noTagEntity = ServiceEntity.builder()
                                    .accountId(ACCOUNT_ID)
                                    .orgIdentifier(ORG_ID)
                                    .projectIdentifier(PROJECT_ID)
                                    .identifier(SERVICE_ID1)
                                    .name("no-tag-svc")
                                    .build();

    doReturn(Optional.of(noTagEntity))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), eq(SERVICE_ID1), anyBoolean());

    ExpansionResponse response = expansionHandler.expand(serviceNode, metadata, "service");

    assertThat(response.isSuccess()).as("null tags should not cause failure").isTrue();
    UnifiedServiceExpandedValue value = (UnifiedServiceExpandedValue) response.getValue();
    assertThat(value.getServicesInfo().get(0).getName()).as("service name").isEqualTo("no-tag-svc");
    assertThat(value.getServicesInfo().get(0).getTags()).as("null tags should result in empty map").isEmpty();
  }
}
