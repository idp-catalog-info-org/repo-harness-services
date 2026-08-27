/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.platform.query.service.api.v1.ColumnDefinition;
import io.harness.platform.query.service.api.v1.ExecuteQueryResponse;
import io.harness.platform.query.service.api.v1.QueryResult;
import io.harness.platform.query.service.api.v1.Row;
import io.harness.queryservice.grpc.QueryServiceClient;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.protobuf.Value;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for IdpAnalyticsService - wrapper for executing HQL queries via QueryServiceClient
 */
@RunWith(MockitoJUnitRunner.class)
public class IdpAnalyticsServiceTest extends CategoryTest {
  private QueryServiceClient mockQueryServiceClient;
  private IdpAnalyticsService idpAnalyticsService;

  private static final String TEST_ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w"; // Harness internal test account
  private static final String SIMPLE_HQL = "find entity \"idp:backstage_catalog\" | limit 1";

  @Before
  public void setUp() {
    mockQueryServiceClient = mock(QueryServiceClient.class);
    idpAnalyticsService = new IdpAnalyticsService(mockQueryServiceClient);
  }

  @Test
  @Owner(developers = OwnerRule.VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testExecute_WithValidHQL_ReturnsResults() {
    // Arrange
    String hql = "find entity \"idp:backstage_catalog\"\n"
        + "        | filter kind = \"Component\" | select{metadata -> name as name, spec -> owner as owner}\n"
        + "        | limit 5";

    ExecuteQueryResponse mockResponse = createMockResponse(Map.of("name", "payment-service", "owner", "team-platform"),
        Map.of("name", "auth-service", "owner", "team-security"));

    when(mockQueryServiceClient.executeQuery(eq(hql), eq(TEST_ACCOUNT_ID))).thenReturn(mockResponse);

    // Act
    List<Map<String, Object>> results = idpAnalyticsService.execute(hql, TEST_ACCOUNT_ID);

    // Assert
    assertThat(results).isNotNull();
    assertThat(results).hasSize(2);
    assertThat(results.get(0).get("name")).isEqualTo("payment-service");
    assertThat(results.get(0).get("owner")).isEqualTo("team-platform");
    assertThat(results.get(1).get("name")).isEqualTo("auth-service");

    verify(mockQueryServiceClient).executeQuery(eq(hql), eq(TEST_ACCOUNT_ID));
  }

  @Test
  @Owner(developers = OwnerRule.VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testExecute_WithEmptyResults_ReturnsEmptyList() {
    // Arrange
    ExecuteQueryResponse mockResponse = createEmptyResponse();
    when(mockQueryServiceClient.executeQuery(anyString(), eq(TEST_ACCOUNT_ID))).thenReturn(mockResponse);

    // Act
    List<Map<String, Object>> results = idpAnalyticsService.execute(SIMPLE_HQL, TEST_ACCOUNT_ID);

    // Assert
    assertThat(results).isNotNull();
    assertThat(results).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testExecute_WithNumberValues_MapsCorrectly() {
    // Arrange
    String hql = "find entity \"idp:backstage_catalog\" | select { count() as total } | limit 1";

    ExecuteQueryResponse mockResponse = createMockResponse(Map.of("total", 42L, "percentage", 95.5));

    when(mockQueryServiceClient.executeQuery(eq(hql), eq(TEST_ACCOUNT_ID))).thenReturn(mockResponse);

    // Act
    List<Map<String, Object>> results = idpAnalyticsService.execute(hql, TEST_ACCOUNT_ID);

    // Assert - protobuf Value carries numbers as double, so all numeric values map back to Double
    assertThat(results).isNotNull();
    assertThat(results).hasSize(1);
    assertThat(results.get(0).get("total")).isEqualTo(42.0);
    assertThat(results.get(0).get("percentage")).isEqualTo(95.5);
  }

  @Test
  @Owner(developers = OwnerRule.VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testExecute_WithNullValues_HandlesGracefully() {
    // Arrange
    Map<String, Object> rowWithNull = new HashMap<>();
    rowWithNull.put("name", "service-1");
    rowWithNull.put("owner", null);
    ExecuteQueryResponse mockResponse = createMockResponse(rowWithNull);

    when(mockQueryServiceClient.executeQuery(anyString(), eq(TEST_ACCOUNT_ID))).thenReturn(mockResponse);

    // Act
    List<Map<String, Object>> results = idpAnalyticsService.execute(SIMPLE_HQL, TEST_ACCOUNT_ID);

    // Assert
    assertThat(results).isNotNull();
    assertThat(results).hasSize(1);
    assertThat(results.get(0).get("name")).isEqualTo("service-1");
    assertThat(results.get(0).get("owner")).isNull();
  }

  // Helper methods to create mock protobuf responses

  @SafeVarargs
  private ExecuteQueryResponse createMockResponse(Map<String, Object>... rows) {
    QueryResult.Builder resultBuilder = QueryResult.newBuilder();

    // Add column definitions (extract names from first row)
    List<String> columnNames = List.of();
    if (rows.length > 0) {
      columnNames = List.copyOf(rows[0].keySet());
      for (String columnName : columnNames) {
        resultBuilder.addColumns(ColumnDefinition.newBuilder().setName(columnName).build());
      }
    }

    // Add rows
    for (Map<String, Object> row : rows) {
      Row.Builder rowBuilder = Row.newBuilder();
      for (String column : columnNames) {
        Object value = row.get(column);
        rowBuilder.addValues(convertToProtoValue(value));
      }
      resultBuilder.addRows(rowBuilder.build());
    }

    return ExecuteQueryResponse.newBuilder().setResult(resultBuilder.build()).build();
  }

  private ExecuteQueryResponse createEmptyResponse() {
    return ExecuteQueryResponse.newBuilder().setResult(QueryResult.newBuilder().build()).build();
  }

  private Value convertToProtoValue(Object value) {
    if (value == null) {
      return Value.newBuilder().setNullValue(com.google.protobuf.NullValue.NULL_VALUE).build();
    } else if (value instanceof String) {
      return Value.newBuilder().setStringValue((String) value).build();
    } else if (value instanceof Long) {
      return Value.newBuilder().setNumberValue(((Long) value).doubleValue()).build();
    } else if (value instanceof Integer) {
      return Value.newBuilder().setNumberValue(((Integer) value).doubleValue()).build();
    } else if (value instanceof Double) {
      return Value.newBuilder().setNumberValue((Double) value).build();
    } else if (value instanceof Boolean) {
      return Value.newBuilder().setBoolValue((Boolean) value).build();
    } else {
      return Value.newBuilder().setStringValue(value.toString()).build();
    }
  }
}
