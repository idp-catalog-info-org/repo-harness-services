/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.JIRA_IDENTIFIER;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.MEAN_TIME_TO_RESOLVE;
import static io.harness.idp.scorecard.datasources.providers.JiraProvider.AUTH_TOKEN_EXPRESSION_KEY;
import static io.harness.idp.scorecard.datasources.providers.JiraProvider.JIRA_COMPONENT_ANNOTATION;
import static io.harness.idp.scorecard.datasources.providers.JiraProvider.JIRA_PROJECT_ANNOTATION;
import static io.harness.idp.scorecard.datasources.providers.JiraProvider.JIRA_TARGET_URL_EXPRESSION_KEY;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
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
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.parser.jira.JiraIssuesCountParser;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.locations.jira.JiraIssuesCountDsl;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.entity.HttpDataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class JiraProviderTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks JiraProvider jiraProvider;
  @Mock DataPointService dataPointService;
  @Mock DataSourceLocationFactory dataSourceLocationFactory;
  @Mock DataSourceLocationRepository dataSourceLocationRepository;
  @Mock DataPointParserFactory dataPointParserFactory;
  @Mock ConfigReader configReader;
  @Mock DataSourceRepository dataSourceRepository;
  @Mock JiraIssuesCountDsl jiraIssuesCountDsl;
  @Mock JiraIssuesCountParser jiraIssuesCountParser;
  private static final String ACCOUNT_ID = "123";
  private static final String PROJECT_ID = "project1";
  private static final String ORG_ID = "org1";
  private static final String IDP_SERVICE_ENTITY_ID = "03bc314a-437b-4d15-b75b-b819179e7859";
  private static final String IDP_SERVICE_ENTITY_NAME = "idp-service";
  public static final String RULE_IDENTIFIER = "rule1";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    jiraProvider = new JiraProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, configReader, dataSourceRepository);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData() {
    when(configReader.getConfigValues(ACCOUNT_ID, null, JIRA_TARGET_URL_EXPRESSION_KEY))
        .thenReturn("https://harness.atlassian.net");
    when(configReader.getConfigValues(ACCOUNT_ID, null, AUTH_TOKEN_EXPRESSION_KEY)).thenReturn("Basic token");

    Map<String, List<io.harness.idp.scorecard.scores.beans.DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put("dsl", List.of(getDataFetchDTO()));
    when(dataPointService.getDslDataPointsInfo(any(), any(), anyList())).thenReturn(dataToFetchByDsl);

    when(dataSourceLocationFactory.getDataSourceLocation(any())).thenReturn(jiraIssuesCountDsl);
    when(dataSourceLocationRepository.findByIdentifier(any()))
        .thenReturn(HttpDataSourceLocationEntity.builder().build());
    when(dataSourceRepository.findByAccountIdentifierInAndIdentifier(any(), any()))
        .thenReturn(Optional.of(getDataSourceEntity()));
    when(jiraIssuesCountDsl.fetchData(
             any(), any(), any(), anyList(), anyMap(), anyMap(), anyMap(), any(), anyBoolean(), anySet()))
        .thenReturn(Map.of(JIRA_IDENTIFIER, "dslResponse"));
    when(dataPointParserFactory.getParser(any(), any())).thenReturn(jiraIssuesCountParser);
    when(jiraIssuesCountParser.parseDataPoint(anyMap(), any())).thenReturn(Map.of(RULE_IDENTIFIER, 2));
    BackstageCatalogEntity entity = getBackstageCatalogEntity();
    Map<String, Map<String, Object>> data =
        jiraProvider.fetchData(ACCOUNT_ID, entity, List.of(getDataFetchDTO()), null);
    Map<String, Object> dsl = data.get(JIRA_IDENTIFIER);
    assertNotNull(dsl);
    assertEquals(2, dsl.get(RULE_IDENTIFIER));
  }

  private BackstageCatalogEntity getBackstageCatalogEntity() {
    Map<String, String> annotations = new HashMap<>();
    annotations.put(JIRA_PROJECT_ANNOTATION, "IDP");
    annotations.put(JIRA_COMPONENT_ANNOTATION, "component");
    return BackstageCatalogComponentEntity.builder()
        .entityUid(IDP_SERVICE_ENTITY_ID)
        .kind(BackstageCatalogEntityTypes.COMPONENT.kind)
        .spec(BackstageCatalogComponentEntity.Spec.builder()
                  .domain(ORG_ID)
                  .system(Collections.singletonList(PROJECT_ID))
                  .type("service")
                  .owner("team-a")
                  .build())
        .metadata(Map.of(
            MetadataFieldConstants.NAME, IDP_SERVICE_ENTITY_NAME, MetadataFieldConstants.ANNOTATIONS, annotations))
        .build();
  }

  private io.harness.idp.scorecard.scores.beans.DataFetchDTO getDataFetchDTO() {
    DataPointEntity dataPointEntity =
        DataPointEntity.builder().dataSourceIdentifier(JIRA_IDENTIFIER).identifier(MEAN_TIME_TO_RESOLVE).build();
    return io.harness.idp.scorecard.scores.beans.DataFetchDTO.builder()
        .ruleIdentifier(RULE_IDENTIFIER)
        .dataPoint(dataPointEntity)
        .build();
  }

  private DataSourceEntity getDataSourceEntity() {
    HttpConfig httpConfig = new HttpConfig();
    httpConfig.setTarget("{API_BASE_URL}");
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "{BEARER_AUTH}");
    httpConfig.setHeaders(headers);
    return HttpDataSourceEntity.builder()
        .name(JIRA_IDENTIFIER)
        .identifier(JIRA_IDENTIFIER)
        .description(JIRA_IDENTIFIER)
        .httpConfig(httpConfig)
        .build();
  }
}
