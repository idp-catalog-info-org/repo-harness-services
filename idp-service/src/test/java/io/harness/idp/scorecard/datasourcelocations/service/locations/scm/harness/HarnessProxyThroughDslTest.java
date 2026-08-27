/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service.locations.scm.harness;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_SUB_FOLDER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;
import static io.harness.idp.scorecard.datasources.providers.scm.ScmBaseProvider.SOURCE_LOCATION_ANNOTATION;
import static io.harness.rule.OwnerRule.AGNIVA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DirectDslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.harness.HarnessProxyThroughDsl;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.InputValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class HarnessProxyThroughDslTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks HarnessProxyThroughDsl harnessProxyThroughDsl;
  @Mock DslClientFactory dslClientFactory;
  @Mock DirectDslClient directDslClient;
  @Mock IdpAuthInterceptor idpAuthInterceptor;
  private static final String ACCOUNT_ID = "123";
  private static final String HARNESS_IDENTIFIER = "harness";
  private static final String EXTRACT_STRING_FROM_A_FILE_DATAPOINT = "extractStringFromAFile";
  private static final String RULE_IDENTIFIER = "ruleIdentifier";
  private static final String IDP_SERVICE_ENTITY_ID = "03bc314a-437b-4d15-b75b-b819179e7859";
  private static final String IDP_SERVICE_ENTITY_NAME = "idp-service";
  private static final String PROJECT_ID = "project1";
  private static final String ORG_ID = "org1";
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testreplaceInputValuePlaceholdersIfAnyInRequestBody() throws JsonProcessingException {
    String result = harnessProxyThroughDsl.replaceInputValuePlaceholdersIfAnyInRequestBody(
        "{BODY}", List.of(getDataFetchDTO()), getBackstageCatalogEntity());
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(result);
    assertEquals(root.path("request")
                     .path("data_source_location")
                     .path("data_points")
                     .get(0)
                     .path("input_values")
                     .get(0)
                     .path("value")
                     .asText(),
        "exampleValue");
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testFetchData() {
    when(dslClientFactory.getClient(any(), any(), anyBoolean())).thenReturn(directDslClient);
    Response response = Response.ok().entity("{\"data\": \"Success Response\"}").build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    when(idpAuthInterceptor.getAuthHeaders()).thenReturn(Map.of("Authorization", "token"));
    Map<String, Object> data = harnessProxyThroughDsl.fetchData(ACCOUNT_ID, getBackstageCatalogEntity(),
        getDataSourceLocationEntity(), List.of(getDataFetchDTO()), replaceableHeaders(),
        possibleReplaceableRequestBody(), possibleReplaceableUrlBody(), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertEquals(ruleData.get("data"), "Success Response");
  }

  private DataFetchDTO getDataFetchDTO() {
    InputValue inputValue = new InputValue();
    inputValue.setKey("exampleKey");
    inputValue.setValue("exampleValue");
    DataPointEntity dataPointEntity = DataPointEntity.builder()
                                          .dataSourceIdentifier(HARNESS_IDENTIFIER)
                                          .identifier(EXTRACT_STRING_FROM_A_FILE_DATAPOINT)
                                          .build();
    return DataFetchDTO.builder()
        .inputValues(List.of(inputValue))
        .ruleIdentifier(RULE_IDENTIFIER)
        .dataPoint(dataPointEntity)
        .build();
  }

  private DataSourceLocationEntity getDataSourceLocationEntity() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "{SERVICE_TO_SERVICE_AUTH}");
    headers.put("Harness-Account", "{ACCOUNT_IDENTIFIER}");
    headers.put("X-Source-Principal", "{X_SOURCE_PRINCIPAL}");
    return HttpDataSourceLocationEntity.builder()
        .apiRequestDetails(ApiRequestDetails.builder().url("url").requestBody("{BODY}").headers(headers).build())
        .build();
  }

  private Map<String, String> possibleReplaceableRequestBody() {
    Map<String, String> pairs = new HashMap<>();
    pairs.put(REPO_SCM, "harness0.harness.io");
    pairs.put(REPOSITORY_OWNER, "harness");
    pairs.put(REPOSITORY_NAME, "harness-core");
    pairs.put(REPOSITORY_SUB_FOLDER, "idp");
    return pairs;
  }

  private Map<String, String> possibleReplaceableUrlBody() {
    Map<String, String> pairs = new HashMap<>();
    pairs.put(API_BASE_URL, "api.harness0.harness.io");
    return pairs;
  }

  private Map<String, String> replaceableHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION_HEADER, "token");
    return headers;
  }

  private DataSourceConfig getDataSourceConfig() {
    HttpConfig httpConfig = new HttpConfig();
    httpConfig.setTarget("{API_BASE_URL}");
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION_HEADER, "{BEARER_AUTH}");
    httpConfig.setHeaders(headers);
    return httpConfig;
  }

  private BackstageCatalogEntity getBackstageCatalogEntity() {
    return BackstageCatalogComponentEntity.builder()
        .entityUid(IDP_SERVICE_ENTITY_ID)
        .kind(BackstageCatalogEntityTypes.COMPONENT.kind)
        .metadata(Map.of(MetadataFieldConstants.NAME, IDP_SERVICE_ENTITY_NAME))
        .spec(BackstageCatalogComponentEntity.Spec.builder()
                  .domain(ORG_ID)
                  .system(Collections.singletonList(PROJECT_ID))
                  .type("service")
                  .owner("team-a")
                  .build())
        .metadata(Map.of(MetadataFieldConstants.NAME, IDP_SERVICE_ENTITY_NAME, MetadataFieldConstants.ANNOTATIONS,
            Map.of(SOURCE_LOCATION_ANNOTATION,
                "url:https://harness0.harness.io/ng/account/l7B_kbSEQD2wjrM7PShm5w/module/code/orgs/PROD/projects/"
                    + "Harness_Commons/repos/harness-core")))
        .build();
  }
}
