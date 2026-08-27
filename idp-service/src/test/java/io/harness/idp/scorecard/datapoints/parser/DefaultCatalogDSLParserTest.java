/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.INVALID_VALUE_TYPE_ERROR;
import static io.harness.idp.common.Constants.MISSING_DATA;
import static io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser.NO_DATA_FOR_DATA_POINT_ERROR;
import static io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser.NO_DATA_FOUND_ERROR;
import static io.harness.rule.OwnerRule.SARABJYOT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class DefaultCatalogDSLParserTest extends CategoryTest {
  private static final String RULE_IDENTIFIER = "test-rule-1";
  private static final String DATA_POINT_IDENTIFIER = "test-data-point";

  private DefaultCatalogDSLParser parser;

  @Before
  public void setUp() {
    parser = new DefaultCatalogDSLParser();
  }

  // ---------------------------------------------------------------------------------------------
  // Success paths: the Catalog DSL stores the JEXL-resolved value RAW under DSL_RESPONSE (unlike
  // HQL, which wraps it under a "value" key), so the parser passes it straight through.
  // ---------------------------------------------------------------------------------------------

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_SuccessWithRawStringValue() {
    Map<String, Object> input = createInputWithDslResponse("Payments service dashboards");

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals("Payments service dashboards", dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_SuccessWithComplexObjectValue() {
    Map<String, Object> nested = new HashMap<>();
    nested.put("red", List.of("High error rate"));
    nested.put("green", List.of("Service health check"));
    Map<String, Object> input = createInputWithDslResponse(nested);

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals(nested, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_SuccessWithListValue() {
    List<String> languages = List.of("java", "go");
    Map<String, Object> input = createInputWithDslResponse(languages);

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals(languages, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  // ---------------------------------------------------------------------------------------------
  // Type validation (delegated to the shared validateAndConstructDataPointInfo helper).
  // ---------------------------------------------------------------------------------------------

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_NumberTypeAcceptsNumberValue() {
    Map<String, Object> input = createInputWithDslResponse(12);

    Object result = parser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.NUMBER));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals(12, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_NumberTypeAcceptsNumericString() {
    Map<String, Object> input = createInputWithDslResponse("42.5");

    Object result = parser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.NUMBER));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals("42.5", dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_NumberTypeRejectsNonNumericValue() {
    Map<String, Object> input = createInputWithDslResponse("not-a-number");

    Object result = parser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.NUMBER));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(INVALID_VALUE_TYPE_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_BooleanTypeCoercesStringValue() {
    Map<String, Object> input = createInputWithDslResponse("FaLsE");

    Object result = parser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.BOOLEAN));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals(false, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_BooleanTypeRejectsUnsupportedValue() {
    Map<String, Object> input = createInputWithDslResponse("yes");

    Object result = parser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.BOOLEAN));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(INVALID_VALUE_TYPE_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_StringTypeAcceptsAnyValue() {
    Map<String, Object> input = createInputWithDslResponse("payments");

    Object result = parser.parseDataPoint(input, createDataFetchDTO(DataPointEntity.Type.STRING));

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertEquals("payments", dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  // ---------------------------------------------------------------------------------------------
  // "No usable result" paths - each must yield a non-null, descriptive error message.
  // ---------------------------------------------------------------------------------------------

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_EmptyRuleData() {
    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, new HashMap<>());

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(NO_DATA_FOUND_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_NullRuleData() {
    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, null);

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(NO_DATA_FOUND_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_MissingRuleIdentifier() {
    Map<String, Object> input = new HashMap<>();

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(NO_DATA_FOUND_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_WithExplicitErrorMessage() {
    String errorMessage = "Exception while evaluating jexl: boom";
    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(ERROR_MESSAGE_KEY, errorMessage);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(errorMessage, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_MissingDataErrorMessageIsPreserved() {
    // CatalogDataSourceLocation emits this MISSING_DATA-prefixed message for an unresolved/unenriched entity.
    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(ERROR_MESSAGE_KEY, NO_DATA_FOR_DATA_POINT_ERROR);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(NO_DATA_FOR_DATA_POINT_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
    assertTrue(((String) dataPointInfo.get(ERROR_MESSAGE_KEY)).contains(MISSING_DATA));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_ErrorMessageTakesPrecedenceOverDslResponse() {
    String errorMessage = "Exception while evaluating jexl: boom";
    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(ERROR_MESSAGE_KEY, errorMessage);
    ruleData.put(DSL_RESPONSE, "stale-value");

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(errorMessage, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_NullDslResponseWithNoErrorMessage() {
    // Bug 1 guard: dsl_response present-but-null and no explicit error must NOT yield a null error message.
    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(DSL_RESPONSE, null);

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNotNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
    assertEquals(NO_DATA_FOR_DATA_POINT_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testParseDataPoint_AbsentDslResponseWithEmptyErrorMessage() {
    // Empty error string is not treated as an error, but the absent dsl_response still falls into the Bug 1 branch.
    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(ERROR_MESSAGE_KEY, "");

    Map<String, Object> input = new HashMap<>();
    input.put(RULE_IDENTIFIER, ruleData);

    Object result = parser.parseDataPoint(input, createDataFetchDTO());

    Map<String, Object> dataPointInfo = getDataPointInfo(result);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals(NO_DATA_FOR_DATA_POINT_ERROR, dataPointInfo.get(ERROR_MESSAGE_KEY));
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

  private Map<String, Object> createInputWithDslResponse(Object resolvedValue) {
    Map<String, Object> ruleData = new HashMap<>();
    ruleData.put(DSL_RESPONSE, resolvedValue);

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
