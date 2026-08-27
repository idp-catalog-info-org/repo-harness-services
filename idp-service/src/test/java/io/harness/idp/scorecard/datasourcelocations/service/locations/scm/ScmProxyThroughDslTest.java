/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service.locations.scm;

import static io.harness.idp.catalog.utils.Constants.ANNOTATIONS;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.HARNESS_API_VERSION;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.EXTRACT_STRING_FROM_A_FILE;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.BRANCH_NAME;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.FILE_PATH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_SUB_FOLDER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;
import static io.harness.idp.scorecard.datasources.providers.scm.ScmBaseProvider.SOURCE_LOCATION_ANNOTATION;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DirectDslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.ScmProxyThroughDsl;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.ArrayList;
import java.util.Collection;
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
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ScmProxyThroughDslTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks ScmProxyThroughDsl scmProxyThroughDsl;
  @Mock DslClientFactory dslClientFactory;
  @Mock DirectDslClient directDslClient;
  @Mock IdpAuthInterceptor idpAuthInterceptor;
  private static final String ACCOUNT_ID = "123";
  private static final String PROJECT_ID = "project1";
  private static final String ORG_ID = "org1";
  private static final String IDP_SERVICE_ENTITY_ID = "03bc314a-437b-4d15-b75b-b819179e7859";
  private static final String IDP_SERVICE_ENTITY_NAME = "idp-service";
  private static final String RULE_IDENTIFIER = "rule1";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
    when(idpAuthInterceptor.getAuthHeaders()).thenReturn(Map.of("Authorization", "token"));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData() {
    Response response = Response.ok().entity("{\"data\": \"Success Response\"}").build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    when(idpAuthInterceptor.getAuthHeaders()).thenReturn(Map.of("Authorization", "token"));
    MockedStatic<CommonUtils> mockedStatic = mockStatic(CommonUtils.class);
    Map<String, Object> data = scmProxyThroughDsl.fetchData(ACCOUNT_ID, getBackstageCatalogEntity(),
        getDataSourceLocationEntity(), List.of(getDataFetchDTO(false)), replaceableHeaders(),
        possibleReplaceableRequestBody(), possibleReplaceableUrlBody(), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(DSL_RESPONSE));
    mockedStatic.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForUnresolvedExpression() {
    when(idpAuthInterceptor.getAuthHeaders()).thenReturn(Map.of("Authorization", "token"));
    Map<String, Object> data = scmProxyThroughDsl.fetchData(ACCOUNT_ID, getBackstageCatalogEntity(),
        getDataSourceLocationEntity(), List.of(getDataFetchDTO(true)), replaceableHeaders(),
        possibleReplaceableRequestBody(), possibleReplaceableUrlBody(), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForUnresolvedExpressionForCatalogEntity() {
    when(idpAuthInterceptor.getAuthHeaders()).thenReturn(Map.of("Authorization", "token"));
    Map<String, Object> data = scmProxyThroughDsl.fetchData(ACCOUNT_ID, getCatalogEntity(),
        getDataSourceLocationEntity(), List.of(getDataFetchDTO(true)), replaceableHeaders(),
        possibleReplaceableRequestBody(), possibleReplaceableUrlBody(), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(ERROR_MESSAGE_KEY));
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

  private BackstageCatalogEntity getBackstageCatalogEntity() {
    return BackstageCatalogComponentEntity.builder()
        .entityUid(IDP_SERVICE_ENTITY_ID)
        .kind(BackstageCatalogEntityTypes.COMPONENT.kind)
        .spec(BackstageCatalogComponentEntity.Spec.builder()
                  .domain(ORG_ID)
                  .system(Collections.singletonList(PROJECT_ID))
                  .type("service")
                  .owner("team-a")
                  .build())
        .metadata(Map.of(MetadataFieldConstants.NAME, IDP_SERVICE_ENTITY_NAME, MetadataFieldConstants.ANNOTATIONS,
            Map.of(SOURCE_LOCATION_ANNOTATION, "url:https://github.com/harness/harness-core/tree/develop"),
            MetadataFieldConstants.HARNESS_DATA, Map.of("branch", "develop")))
        .build();
  }

  private CatalogEntity getCatalogEntity() {
    InlineCatalogEntity inlineComponentEntity = new InlineCatalogEntity();
    inlineComponentEntity.setIdentifier(IDP_SERVICE_ENTITY_NAME);
    inlineComponentEntity.setReferenceType(ReferenceType.INLINE);
    inlineComponentEntity.setApiVersion(HARNESS_API_VERSION);
    inlineComponentEntity.setKind(COMPONENT_KIND);
    inlineComponentEntity.setType("service");
    Map<String, String> annotations = new HashMap<>();
    annotations.put(SOURCE_LOCATION_ANNOTATION, "url:https://github.com/harness/harness-core/tree/develop");
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(ANNOTATIONS, annotations);
    metadata.put(MetadataFieldConstants.HARNESS_DATA, Map.of("branch", "develop"));
    inlineComponentEntity.setMetadata(metadata);
    inlineComponentEntity.setYaml(
        "apiVersion: harness.io/v1\nkind: component\nmetadata:\n  harnessData: \n    branch: develop\n  annotations:\n "
        + "     backstage.io/source-location: url:https://github.com/harness/harness-core/tree/develop");
    return inlineComponentEntity;
  }

  private DataFetchDTO getDataFetchDTO(boolean isPathExpression) {
    DataPointEntity dataPointEntity = DataPointEntity.builder()
                                          .dataSourceIdentifier(GITHUB_IDENTIFIER)
                                          .identifier(EXTRACT_STRING_FROM_A_FILE)
                                          .build();
    List<InputValue> inputValues = new ArrayList<>();
    InputValue inputValue = new InputValue();
    inputValue.setKey(BRANCH_NAME);
    inputValue.setValue("<+metadata.harnessData.branch>");
    inputValue.setKey(FILE_PATH);
    if (isPathExpression) {
      inputValue.setValue("<+metadata.harnessData.path>");
    } else {
      inputValue.setValue("idp");
    }
    inputValues.add(inputValue);
    return DataFetchDTO.builder()
        .ruleIdentifier(RULE_IDENTIFIER)
        .dataPoint(dataPointEntity)
        .inputValues(inputValues)
        .build();
  }

  private Map<String, String> possibleReplaceableRequestBody() {
    Map<String, String> pairs = new HashMap<>();
    pairs.put(REPO_SCM, "github.com");
    pairs.put(REPOSITORY_OWNER, "harness");
    pairs.put(REPOSITORY_NAME, "harness-core");
    pairs.put(REPOSITORY_SUB_FOLDER, "idp");
    return pairs;
  }

  private Map<String, String> possibleReplaceableUrlBody() {
    Map<String, String> pairs = new HashMap<>();
    pairs.put(API_BASE_URL, "api.github.com");
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
}
