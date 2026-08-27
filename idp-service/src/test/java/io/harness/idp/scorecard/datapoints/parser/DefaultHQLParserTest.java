/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.Constants;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DefaultHQLParserTest extends CategoryTest {
  private static final String RULE_IDENTIFIER = "test-rule-1";
  private static final String DATA_POINT_IDENTIFIER = "test-data-point";

  AutoCloseable openMocks;
  @InjectMocks DefaultHQLParser defaultHQLParser;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_SuccessWithDslResponse() {
    // Arrange
    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("value", 42);

    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(DSL_RESPONSE, queryResult);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertEquals(queryResult.get("value"), dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_EmptyRuleData() {
    // Arrange
    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, new HashMap<>());

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals("No data found for rule", dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_NullRuleData() {
    // Arrange
    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, null);

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals("No data found for rule", dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_MissingRuleIdentifier() {
    // Arrange
    Map<String, Object> input = new HashMap<>();
    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals("No data found for rule", dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_WithErrorMessage() {
    // Arrange
    String errorMessage = "Query execution failed";
    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(ERROR_MESSAGE_KEY, errorMessage);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(errorMessage, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_WithEmptyErrorMessage() {
    // Arrange
    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(ERROR_MESSAGE_KEY, "");

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert - Empty error message is not considered as isEmpty, should parse dsl_response
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals("No dsl_response found in HQL result", dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_NullDslResponse() {
    // Arrange
    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(DSL_RESPONSE, null);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals("No dsl_response found in HQL result", dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_DslResponseMissingValueKey() {
    // Arrange - dsl_response is present but does not contain the required "value" key
    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("someOtherKey", "someValue");

    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(DSL_RESPONSE, queryResult);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(DefaultHQLParser.MISSING_VALUE_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_DslResponseNotAMap() {
    // Arrange - dsl_response is a primitive, not the expected map wrapper
    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(DSL_RESPONSE, 42);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals("No dsl_response found in HQL result", dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_DslResponseWithComplexObject() {
    // Arrange
    Map<String, Object> nestedData = new HashMap<>();
    nestedData.put("field1", "value1");
    nestedData.put("field2", 123);

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("value", nestedData);

    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(DSL_RESPONSE, queryResult);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertEquals(nestedData, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_DslResponseWithPrimitiveValue() {
    // Arrange
    Integer simpleValue = 42;

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("value", simpleValue);

    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(DSL_RESPONSE, queryResult);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertEquals(simpleValue, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_DslResponseWithStringValue() {
    // Arrange
    String stringValue = "success";

    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put("value", stringValue);

    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(DSL_RESPONSE, queryResult);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    // Act
    Object result = defaultHQLParser.parseDataPoint(input, dataFetchDTO);

    // Assert
    assertNotNull(result);
    assertTrue(result instanceof Map);
    Map<String, Object> resultMap = (Map<String, Object>) result;
    assertTrue(resultMap.containsKey(RULE_IDENTIFIER));

    Map<String, Object> dataPointInfo = (Map<String, Object>) resultMap.get(RULE_IDENTIFIER);
    assertEquals(stringValue, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_NumberTypeAcceptsNumericStringWithoutConversion() {
    Map<String, Object> input = createInputWithValue("42.5");

    Object result = defaultHQLParser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.NUMBER));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals("42.5", dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_NumberTypeAcceptsNumberValue() {
    Map<String, Object> input = createInputWithValue(42.5);

    Object result = defaultHQLParser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.NUMBER));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals(42.5, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_NumberTypeRejectsNonNumericString() {
    Map<String, Object> input = createInputWithValue("not-a-number");

    Object result = defaultHQLParser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.NUMBER));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(Constants.INVALID_VALUE_TYPE_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_BooleanTypeAcceptsBooleanValue() {
    Map<String, Object> input = createInputWithValue(true);

    Object result = defaultHQLParser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.BOOLEAN));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals(true, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_BooleanTypeCoercesStringValue() {
    Map<String, Object> input = createInputWithValue("FaLsE");

    Object result = defaultHQLParser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.BOOLEAN));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals(false, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_BooleanTypeCoercesNumericValues() {
    Object[][] testCases = {
        {1, true}, {1.0, true}, {"1", true}, {"1.0", true}, {0, false}, {0.0, false}, {"0", false}, {"0.0", false}};

    for (Object[] testCase : testCases) {
      Object result = defaultHQLParser.parseDataPoint(
          createInputWithValue(testCase[0]), createDataFetchDTO(DataPointEntity.Type.BOOLEAN));

      Map<String, Object> dataPointInfo = getDataPointInfo(result);
      assertEquals(testCase[1], dataPointInfo.get(DATA_POINT_VALUE_KEY));
      assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
    }
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_BooleanTypeRejectsUnsupportedNumericValue() {
    Map<String, Object> input = createInputWithValue(2);

    Object result = defaultHQLParser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.BOOLEAN));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(Constants.INVALID_VALUE_TYPE_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_BooleanTypeRejectsOtherStringValue() {
    Map<String, Object> input = createInputWithValue("yes");

    Object result = defaultHQLParser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.BOOLEAN));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(Constants.INVALID_VALUE_TYPE_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_StringTypeAcceptsStringValue() {
    Map<String, Object> input = createInputWithValue("success");

    Object result = defaultHQLParser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.STRING));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals("success", dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testParseDataPoint_NoMatchingRows() {
    Map<String, Object> input = createInputWithValue(DefaultHQLParser.NO_MATCHING_ROWS);

    Object result = defaultHQLParser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.NUMBER));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(DefaultHQLParser.NO_DATA_FOR_DATA_POINT_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  // Helper methods

  private DataFetchDTO createDataFetchDTO() {
    DataPointEntity dataPointEntity = DataPointEntity.builder().identifier(DATA_POINT_IDENTIFIER).build();
    return DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER).dataPoint(dataPointEntity).build();
  }

  private DataFetchDTO createDataFetchDTO(DataPointEntity.Type type) {
    DataPointEntity dataPointEntity = DataPointEntity.builder().identifier(DATA_POINT_IDENTIFIER).type(type).build();
    return DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER).dataPoint(dataPointEntity).build();
  }

  private Map<String, Object> createInputWithValue(Object value) {
    Map<String, Object> queryResult = new HashMap<>();
    queryResult.put(DATA_POINT_VALUE_KEY, value);

    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(DSL_RESPONSE, queryResult);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);
    return input;
  }

  private Map<String, Object> getDataPointInfo(Object result) {
    assertNotNull(result);
    assertTrue(result instanceof Map);
    return (Map<String, Object>) ((Map<String, Object>) result).get(RULE_IDENTIFIER);
  }
}
