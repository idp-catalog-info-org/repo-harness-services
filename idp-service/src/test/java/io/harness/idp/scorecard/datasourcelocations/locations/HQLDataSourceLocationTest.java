/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.rule.OwnerRule.ANKUR;
import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;
import io.harness.idp.scorecard.datasourcelocations.entity.HQLDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.service.IdpAnalyticsService;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class HQLDataSourceLocationTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account-123";
  private static final String RULE_IDENTIFIER = "test-rule-1";
  private static final String ENTITY_NAME = "test-service";
  private static final String ENTITY_KIND = "component";
  private static final String PARENT_UNIQUE_ID = "zEaak-FLS425IEO7OLzMUg";
  private static final String TRACEABLE_ENTITY_FILTER =
      "m.idp_api_identifier = <+catalog.identifier> and m.parent_unique_id = <+catalog.parentUniqueId>";
  private static final String TRACEABLE_ENTITY_FILTER_RESOLVED =
      "m.idp_api_identifier = 'test-service' and m.parent_unique_id = 'zEaak-FLS425IEO7OLzMUg'";

  AutoCloseable openMocks;
  HQLDataSourceLocation hqlDataSourceLocation;
  @Mock IdpAnalyticsService analyticsService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    hqlDataSourceLocation = new HQLDataSourceLocation(analyticsService);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_SuccessfulQueryExecution() {
    // Arrange
    String hqlTemplate = "SELECT count(*) as value FROM table WHERE name = '<+catalog.metadata.name>'";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    CatalogEntity catalogEntity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("value", 42);
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    String expectedHql = "SELECT count(*) as value FROM table WHERE name = ''test-service''";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_EntityNotCatalogEntity() {
    // Arrange
    String hqlTemplate = "SELECT * FROM table";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    Object invalidEntity = new Object(); // Not a CatalogEntity
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, invalidEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(ERROR_MESSAGE_KEY));
    assertEquals("Entity must be of type CatalogEntity", result.get(ERROR_MESSAGE_KEY));
    verify(analyticsService, never()).execute(anyString(), anyString());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_NullEntity() {
    // Arrange
    String hqlTemplate = "SELECT * FROM table";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    // Act
    Map<String, Object> result = hqlDataSourceLocation.fetchData(ACCOUNT_ID, null, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(ERROR_MESSAGE_KEY));
    assertEquals("Entity must be of type CatalogEntity", result.get(ERROR_MESSAGE_KEY));
    verify(analyticsService, never()).execute(anyString(), anyString());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_InvalidPlaceholderExpressionReturnsErrorWithoutExecutingQuery() {
    // Arrange - placeholder references a field that does not exist on the catalog entity
    String hqlTemplate = "SELECT * FROM table WHERE name = '<+catalog.nonexistent.field>'";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    CatalogEntity catalogEntity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert - invalid expression fails during placeholder resolution; query must not run
    assertNotNull(result);
    assertTrue(result.containsKey(ERROR_MESSAGE_KEY));
    String errorMessage = (String) result.get(ERROR_MESSAGE_KEY);
    assertTrue(errorMessage.contains("Failed to resolve placeholders"));
    assertTrue(errorMessage.contains("Failed to resolve placeholder: <+catalog.nonexistent.field>"));
    verify(analyticsService, never()).execute(anyString(), anyString());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_QueryExecutionFailure() {
    // Arrange
    String hqlTemplate = "SELECT * FROM table WHERE name = '<+catalog.metadata.name>'";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    CatalogEntity catalogEntity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    String expectedHql = "SELECT * FROM table WHERE name = ''test-service''";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID)))
        .thenThrow(new RuntimeException("Query execution failed"));

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(ERROR_MESSAGE_KEY));
    String errorMessage = (String) result.get(ERROR_MESSAGE_KEY);
    assertTrue(errorMessage.contains("Failed to execute HQL query"));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_EmptyQueryResult() {
    // Arrange
    String hqlTemplate = "SELECT * FROM table WHERE name = '<+catalog.metadata.name>'";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    CatalogEntity catalogEntity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    String expectedHql = "SELECT * FROM table WHERE name = ''test-service''";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(Collections.emptyList());

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(ERROR_MESSAGE_KEY));
    assertEquals("HQL query returned no results", result.get(ERROR_MESSAGE_KEY));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_NullQueryResult() {
    // Arrange
    String hqlTemplate = "SELECT * FROM table WHERE name = '<+catalog.metadata.name>'";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    CatalogEntity catalogEntity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    String expectedHql = "SELECT * FROM table WHERE name = ''test-service''";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(null);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(ERROR_MESSAGE_KEY));
    assertEquals("HQL query returned no results", result.get(ERROR_MESSAGE_KEY));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_MultiplePlaceholders() {
    // Arrange
    String hqlTemplate = "SELECT * FROM table WHERE name = '<+catalog.metadata.name>' AND kind = '<+catalog.kind>'";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    CatalogEntity catalogEntity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("count", 10);
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    String expectedHql = "SELECT * FROM table WHERE name = ''test-service'' AND kind = ''component''";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_NumericPlaceholder() {
    // Arrange
    Map<String, Object> spec = new HashMap<>();
    spec.put("threshold", 100);
    String hqlTemplate = "SELECT * FROM table WHERE value > <+catalog.spec.threshold>";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    CatalogEntity catalogEntity = createCatalogEntityWithSpec(ENTITY_NAME, ENTITY_KIND, spec);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("result", "success");
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    String expectedHql = "SELECT * FROM table WHERE value > 100";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_BooleanPlaceholder() {
    // Arrange
    Map<String, Object> spec = new HashMap<>();
    spec.put("active", true);
    String hqlTemplate = "SELECT * FROM table WHERE active = <+catalog.spec.active>";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    CatalogEntity catalogEntity = createCatalogEntityWithSpec(ENTITY_NAME, ENTITY_KIND, spec);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("count", 5);
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    String expectedHql = "SELECT * FROM table WHERE active = TRUE";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_StringWithSpecialCharacters() {
    // Arrange
    String nameWithQuotes = "test's-service";
    String hqlTemplate = "SELECT * FROM table WHERE name = '<+catalog.metadata.name>'";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    CatalogEntity catalogEntity = createCatalogEntity(nameWithQuotes, ENTITY_KIND);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("result", "ok");
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    // Single quote should be escaped to double single quote in SQL, and YAML adds another layer
    String expectedHql = "SELECT * FROM table WHERE name = ''test''s-service''";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_ParentUniqueIdPlaceholder() {
    // Arrange - Traceable avg risk score HQL template
    String hqlTemplate = "find view idp:traceable_api_matches | filter idp_api_identifier = <+catalog.identifier> "
        + "and parent_unique_id = <+catalog.parentUniqueId> | select { avg(risk_score) as value }";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    InlineCatalogEntity catalogEntity = createTraceableCatalogEntity();
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("value", 7.5);
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    String expectedHql = "find view idp:traceable_api_matches | filter idp_api_identifier = 'test-service' "
        + "and parent_unique_id = 'zEaak-FLS425IEO7OLzMUg' | select { avg(risk_score) as value }";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_TraceableTotalIssuesQuery() {
    // Arrange - Traceable total issues HQL template
    String hqlTemplate = "find view \"idp:traceable_api_matches\" m\n"
        + "| inner join entity traceable:issues i on m.traceable_api_id = i.affected_entity_id\n"
        + "| filter " + TRACEABLE_ENTITY_FILTER + "\n"
        + "| select { count() as total_issues }";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    InlineCatalogEntity catalogEntity = createTraceableCatalogEntity();
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("total_issues", 12);
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    String expectedHql = "find view \"idp:traceable_api_matches\" m\n"
        + "| inner join entity traceable:issues i on m.traceable_api_id = i.affected_entity_id\n"
        + "| filter " + TRACEABLE_ENTITY_FILTER_RESOLVED + "\n"
        + "| select { count() as total_issues }";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_ListPlaceholderResolvesToCommaSeparatedValuesForInClause() {
    // Arrange - list spec field used in an IN clause
    Map<String, Object> spec = new HashMap<>();
    spec.put("ownerIds", List.of("owner-1", "owner-2", "owner-3"));
    String hqlTemplate = "SELECT count(*) as value FROM table WHERE owner_id IN (<+catalog.spec.ownerIds>)";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    CatalogEntity catalogEntity = createCatalogEntityWithSpec(ENTITY_NAME, ENTITY_KIND, spec);
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("value", 3);
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    String expectedHql = "SELECT count(*) as value FROM table WHERE owner_id IN ('owner-1', 'owner-2', 'owner-3')";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_TraceableMinMaxIssuesQuery() {
    // Arrange - Traceable min/max issues HQL template
    String hqlTemplate = "with issue_counts as (\n"
        + "  find view \"idp:traceable_api_matches\" m\n"
        + "  | inner join entity traceable:issues i on m.traceable_api_id = i.affected_entity_id\n"
        + "  | filter " + TRACEABLE_ENTITY_FILTER + "\n"
        + "  | select { m.traceable_api_id, count() as issue_count }\n"
        + "  | group_by m.traceable_api_id\n"
        + ")\n"
        + "find issue_counts\n"
        + "| aggregate { max(issue_count) as max_issues, min(issue_count) as min_issues }";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    InlineCatalogEntity catalogEntity = createTraceableCatalogEntity();
    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(createDataFetchDTO());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("max_issues", 8);
    queryResult.put("min_issues", 2);
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    String expectedHql = "with issue_counts as (\n"
        + "  find view \"idp:traceable_api_matches\" m\n"
        + "  | inner join entity traceable:issues i on m.traceable_api_id = i.affected_entity_id\n"
        + "  | filter " + TRACEABLE_ENTITY_FILTER_RESOLVED + "\n"
        + "  | select { m.traceable_api_id, count() as issue_count }\n"
        + "  | group_by m.traceable_api_id\n"
        + ")\n"
        + "find issue_counts\n"
        + "| aggregate { max(issue_count) as max_issues, min(issue_count) as min_issues }";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testFetchData_InputPlaceholderCommaSeparatedListResolvedForInClause() {
    // Arrange - HQL template with <+inputs.owasp> placeholder, input is already in HQL list format
    String hqlTemplate = "find entity traceable:issues"
        + " | filter owasp_value in <+inputs.owasp>"
        + " and name = <+catalog.identifier>"
        + " | select { count() as value }";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    InlineCatalogEntity catalogEntity = createTraceableCatalogEntity();

    InputValue owaspInput = new InputValue();
    owaspInput.setKey("owasp");
    owaspInput.setValue("['A01', 'A02', 'A03']");

    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(
        DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER).inputValues(List.of(owaspInput)).build());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("value", 5);
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    String expectedHql = "find entity traceable:issues"
        + " | filter owasp_value in ['A01', 'A02', 'A03']"
        + " and name = 'test-service'"
        + " | select { count() as value }";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testFetchData_InputPlaceholderStripsWrappingDoubleQuotes() {
    // Arrange - input value arrives wrapped in double quotes from rule config UI
    String hqlTemplate = "find entity traceable:issues"
        + " | unnest owasp_values as owasp_value"
        + " | filter owasp_value in <+inputs.owasp>"
        + " and name = <+catalog.identifier>"
        + " | select { count() as value }";
    HQLDataSourceLocationEntity locationEntity = createHQLLocationEntity(hqlTemplate);
    InlineCatalogEntity catalogEntity = createTraceableCatalogEntity();

    InputValue owaspInput = new InputValue();
    owaspInput.setKey("owasp");
    owaspInput.setValue("\"[\"API9:2023\"]\"");

    List<DataFetchDTO> dataFetchDTOs = Collections.singletonList(
        DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER).inputValues(List.of(owaspInput)).build());

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("value", 2);
    List<Map<String, Object>> queryResults = Collections.singletonList(queryResult);

    String expectedHql = "find entity traceable:issues"
        + " | unnest owasp_values as owasp_value"
        + " | filter owasp_value in [\"API9:2023\"]"
        + " and name = 'test-service'"
        + " | select { count() as value }";
    when(analyticsService.execute(eq(expectedHql), eq(ACCOUNT_ID))).thenReturn(queryResults);

    // Act
    Map<String, Object> result =
        hqlDataSourceLocation.fetchData(ACCOUNT_ID, catalogEntity, locationEntity, dataFetchDTOs);

    // Assert - wrapping quotes stripped, inner value preserved
    assertNotNull(result);
    assertTrue(result.containsKey(DSL_RESPONSE));
    assertEquals(queryResult, result.get(DSL_RESPONSE));
    verify(analyticsService).execute(expectedHql, ACCOUNT_ID);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  // Helper methods

  private HQLDataSourceLocationEntity createHQLLocationEntity(String hqlTemplate) {
    HQLDataSourceLocationEntity entity = new HQLDataSourceLocationEntity();
    entity.setHqlTemplate(hqlTemplate);
    entity.setIdentifier("test-hql-location");
    entity.setType(DataSourceLocationType.HQL);
    return entity;
  }

  private InlineCatalogEntity createTraceableCatalogEntity() {
    InlineCatalogEntity catalogEntity = (InlineCatalogEntity) createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    catalogEntity.setParentUniqueId(PARENT_UNIQUE_ID);
    return catalogEntity;
  }

  private CatalogEntity createCatalogEntity(String name, String kind) {
    Map<String, Object> yamlContent = new HashMap<>();
    yamlContent.put("apiVersion", "v1");
    yamlContent.put("kind", kind);
    yamlContent.put("identifier", name);
    yamlContent.put("metadata", Map.of("name", name));
    return createCatalogEntity(yamlContent);
  }

  private CatalogEntity createCatalogEntityWithSpec(String name, String kind, Map<String, Object> spec) {
    Map<String, Object> yamlContent = new HashMap<>();
    yamlContent.put("apiVersion", "v1");
    yamlContent.put("kind", kind);
    yamlContent.put("identifier", name);
    yamlContent.put("metadata", Map.of("name", name));
    yamlContent.put("spec", spec);
    return createCatalogEntity(yamlContent);
  }

  /**
   * Creates a CatalogEntity from an arbitrary YAML content map. Any top-level keys present in {@code yamlContent}
   * are serialized into the entity YAML and become resolvable via {@code catalog.<key>} placeholders.
   */
  @SuppressWarnings("unchecked")
  private CatalogEntity createCatalogEntity(Map<String, Object> yamlContent) {
    Map<String, Object> entityYamlContent = new HashMap<>(yamlContent);

    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier(ACCOUNT_ID);
    entity.setApiVersion((String) entityYamlContent.getOrDefault("apiVersion", "v1"));
    entity.setKind((String) entityYamlContent.getOrDefault("kind", ENTITY_KIND));

    Map<String, Object> metadata = entityYamlContent.get("metadata") instanceof Map
        ? new HashMap<>((Map<String, Object>) entityYamlContent.get("metadata"))
        : new HashMap<>();
    String name = metadata.get("name") instanceof String ? (String) metadata.get("name") : ENTITY_NAME;
    String identifier =
        entityYamlContent.get("identifier") instanceof String ? (String) entityYamlContent.get("identifier") : name;
    entityYamlContent.putIfAbsent("identifier", identifier);

    entity.setName(name);
    entity.setIdentifier(identifier);
    entity.setMetadata(metadata);

    if (entityYamlContent.get("spec") instanceof Map) {
      entity.setSpec(new HashMap<>((Map<String, Object>) entityYamlContent.get("spec")));
    }

    entity.setYaml(YamlUtils.writeObjectAsYaml(entityYamlContent));
    return entity;
  }

  private DataFetchDTO createDataFetchDTO() {
    return DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER).build();
  }
}
