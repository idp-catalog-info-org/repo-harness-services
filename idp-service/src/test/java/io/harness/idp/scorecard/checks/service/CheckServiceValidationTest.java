/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.service;

import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.checks.repositories.CheckRepository;
import io.harness.idp.scorecard.checks.repositories.CheckStatsRepository;
import io.harness.idp.scorecard.checks.repositories.CheckStatusRepository;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.InputDetails;
import io.harness.spec.server.idp.v1.model.Rule;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
public class CheckServiceValidationTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccount";
  private static final String DATA_SOURCE = "github";
  private static final String DATA_POINT = "fileContains";
  private static final String DATA_KEY = "github.fileContains";

  private CheckServiceImpl checkService;

  @Mock private CheckRepository checkRepository;
  @Mock private CheckStatusRepository checkStatusRepository;
  @Mock private CheckStatsRepository checkStatsRepository;
  @Mock private ScorecardService scorecardService;
  @Mock private NGSettingsClient settingsClient;
  @Mock private DataPointService dataPointService;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutboxService outboxService;

  @Before
  public void setUp() {
    openMocks(this);
    checkService = new CheckServiceImpl(checkRepository, checkStatusRepository, checkStatsRepository, scorecardService,
        settingsClient, dataPointService, transactionTemplate, outboxService);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateDataPointInputValues() throws Exception {
    // Use reflection to access the private method
    Method method = CheckServiceImpl.class.getDeclaredMethod(
        "validateDataPointInputValues", List.class, String.class, String.class, Map.class, String.class, List.class);
    method.setAccessible(true);

    // Setup test data
    List<String> dataPointInputValues = List.of("README.md");
    Map<String, DataPoint> dataPointMap = new HashMap<>();

    // Create a DataPoint using the correct construction pattern
    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(DATA_POINT);
    dataPoint.setInputDetails(createInputDetails());

    dataPointMap.put(DATA_SOURCE + DOT_SEPARATOR + DATA_POINT, dataPoint);
    List<InputDetails> inputDetails = createInputDetails();

    // Execute the method
    method.invoke(checkService, dataPointInputValues, DATA_SOURCE, DATA_POINT, dataPointMap, ACCOUNT_ID, inputDetails);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateDataPointInputValuesDataPointNotFound() throws Exception {
    // Use reflection to access the private method
    Method method = CheckServiceImpl.class.getDeclaredMethod(
        "validateDataPointInputValues", List.class, String.class, String.class, Map.class, String.class, List.class);
    method.setAccessible(true);

    // Setup test data with missing data point
    List<String> dataPointInputValues = List.of("README.md");
    Map<String, DataPoint> dataPointMap = new HashMap<>();
    List<InputDetails> inputDetails = createInputDetails();

    try {
      // Execute the method
      method.invoke(
          checkService, dataPointInputValues, DATA_SOURCE, DATA_POINT, dataPointMap, ACCOUNT_ID, inputDetails);
    } catch (Exception e) {
      if (e.getCause() instanceof InvalidRequestException) {
        throw (InvalidRequestException) e.getCause();
      }
      throw e;
    }
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateDataPointInputValuesTooManyInputs() throws Exception {
    Method method = CheckServiceImpl.class.getDeclaredMethod(
        "validateDataPointInputValues", List.class, String.class, String.class, Map.class, String.class, List.class);
    method.setAccessible(true);

    List<String> dataPointInputValues = List.of("README.md", "extraInput", "anotherExtraInput");
    Map<String, DataPoint> dataPointMap = new HashMap<>();

    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(DATA_POINT);
    dataPoint.setInputDetails(createInputDetails());

    dataPointMap.put(DATA_SOURCE + DOT_SEPARATOR + DATA_POINT, dataPoint);
    List<InputDetails> inputDetails = createInputDetails();

    try {
      method.invoke(
          checkService, dataPointInputValues, DATA_SOURCE, DATA_POINT, dataPointMap, ACCOUNT_ID, inputDetails);
    } catch (Exception e) {
      if (e.getCause() instanceof InvalidRequestException) {
        throw (InvalidRequestException) e.getCause();
      }
      throw e;
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheck() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String expression = "github.fileContains.\"README.md\" == \"test content\"";

    Map<String, DataPoint> dataPointMap = new HashMap<>();

    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(DATA_POINT);
    dataPoint.setInputDetails(createInputDetails());

    dataPointMap.put(DATA_SOURCE + DOT_SEPARATOR + DATA_POINT, dataPoint);
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

    Object result = method.invoke(checkService, expression, ACCOUNT_ID);

    assertNotNull("Result should not be null", result);

    Field isValidField = result.getClass().getDeclaredField("isExpressionValid");
    isValidField.setAccessible(true);
    Boolean isValid = (Boolean) isValidField.get(result);
    Method getRulesMethod = result.getClass().getMethod("getRules");

    assertTrue("Expression should be valid", isValid);
    List<Rule> rules = (List<Rule>) getRulesMethod.invoke(result);

    assertNotNull("Rules should not be null", rules);
    assertEquals("Should have 1 rule", 1, rules.size());

    Rule rule = rules.get(0);
    assertEquals("Data source should match", DATA_SOURCE, rule.getDataSourceIdentifier());
    assertEquals("Data point should match", DATA_POINT, rule.getDataPointIdentifier());
    assertEquals("Operator should be ==", "==", rule.getOperator());
    assertEquals("Value should match", "test content", rule.getValue());
    assertNotNull("InputValues should not be null", rule.getInputValues());
    assertEquals("Should have 1 input value", 1, rule.getInputValues().size());
    assertEquals("Input key should be filePath", "filePath", rule.getInputValues().get(0).getKey());
    assertEquals("Input value should be README.md", "\"README.md\"", rule.getInputValues().get(0).getValue());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheckMultipleRules() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String expression = "github.fileContains.\"README.md\" == \"test content\" && github.isFileExist == true";

    Map<String, DataPoint> dataPointMap = new HashMap<>();

    DataPoint fileContainsDataPoint = new DataPoint();
    fileContainsDataPoint.setDataPointIdentifier(DATA_POINT);
    fileContainsDataPoint.setInputDetails(createInputDetails());

    DataPoint isFileExistDataPoint = new DataPoint();
    isFileExistDataPoint.setDataPointIdentifier("isFileExist");
    isFileExistDataPoint.setInputDetails(new ArrayList<>());

    dataPointMap.put(DATA_SOURCE + DOT_SEPARATOR + DATA_POINT, fileContainsDataPoint);
    dataPointMap.put(DATA_SOURCE + DOT_SEPARATOR + "isFileExist", isFileExistDataPoint);
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

    Object result = method.invoke(checkService, expression, ACCOUNT_ID);

    assertNotNull("Result should not be null", result);

    Field isValidField = result.getClass().getDeclaredField("isExpressionValid");
    isValidField.setAccessible(true);
    Boolean isValid = (Boolean) isValidField.get(result);
    Method getRulesMethod = result.getClass().getMethod("getRules");

    assertTrue("Expression should be valid", isValid);
    List<Rule> rules = (List<Rule>) getRulesMethod.invoke(result);

    assertNotNull("Rules should not be null", rules);
    assertEquals("Should have 2 rules", 2, rules.size());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheckWithEscapedQuotes() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String expression = "github.fileContains.\"README.md\" == \"test \\\"quoted\\\" content\"";

    Map<String, DataPoint> dataPointMap = new HashMap<>();

    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(DATA_POINT);
    dataPoint.setInputDetails(createInputDetails());

    dataPointMap.put(DATA_SOURCE + DOT_SEPARATOR + DATA_POINT, dataPoint);
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

    Object result = method.invoke(checkService, expression, ACCOUNT_ID);

    assertNotNull("Result should not be null", result);

    Field isValidField = result.getClass().getDeclaredField("isExpressionValid");
    isValidField.setAccessible(true);
    Boolean isValid = (Boolean) isValidField.get(result);
    Method getRulesMethod = result.getClass().getMethod("getRules");

    assertTrue("Expression should be valid", isValid);
    List<Rule> rules = (List<Rule>) getRulesMethod.invoke(result);

    assertEquals("Value should have proper escaping", "test \"quoted\" content", rules.get(0).getValue());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheckWithNumericValue() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String expression = "github.fileCount == 10";

    Map<String, DataPoint> dataPointMap = new HashMap<>();
    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier("fileCount");
    dataPoint.setInputDetails(new ArrayList<>());

    dataPointMap.put("github.fileCount", dataPoint);
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

    Object result = method.invoke(checkService, expression, ACCOUNT_ID);

    assertNotNull("Result should not be null", result);
    Field isValidField = result.getClass().getDeclaredField("isExpressionValid");
    isValidField.setAccessible(true);
    Boolean isValid = (Boolean) isValidField.get(result);
    Method getRulesMethod = result.getClass().getMethod("getRules");

    assertTrue("Expression should be valid", isValid);
    List<Rule> rules = (List<Rule>) getRulesMethod.invoke(result);

    assertEquals("Should have 1 rule", 1, rules.size());
    assertEquals("Value should be 10", "10", rules.get(0).getValue());
    assertEquals("Operator should be ==", "==", rules.get(0).getOperator());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheckWithBooleanValue() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String expression = "github.isFileExist == true";

    Map<String, DataPoint> dataPointMap = new HashMap<>();
    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier("isFileExist");
    dataPoint.setInputDetails(new ArrayList<>());

    dataPointMap.put("github.isFileExist", dataPoint);
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

    Object result = method.invoke(checkService, expression, ACCOUNT_ID);

    assertNotNull("Result should not be null", result);
    Field isValidField = result.getClass().getDeclaredField("isExpressionValid");
    isValidField.setAccessible(true);
    Boolean isValid = (Boolean) isValidField.get(result);
    Method getRulesMethod = result.getClass().getMethod("getRules");

    assertTrue("Expression should be valid", isValid);
    List<Rule> rules = (List<Rule>) getRulesMethod.invoke(result);

    assertEquals("Should have 1 rule", 1, rules.size());
    assertEquals("Value should be true", "true", rules.get(0).getValue());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheckWithComparisonOperators() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String[] operators = {">", "<", ">=", "<=", "!="};

    for (String operator : operators) {
      String expression = "github.fileCount " + operator + " 10";

      Map<String, DataPoint> dataPointMap = new HashMap<>();
      DataPoint dataPoint = new DataPoint();
      dataPoint.setDataPointIdentifier("fileCount");
      dataPoint.setInputDetails(new ArrayList<>());

      dataPointMap.put("github.fileCount", dataPoint);
      when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

      Object result = method.invoke(checkService, expression, ACCOUNT_ID);

      assertNotNull("Result should not be null for operator " + operator, result);
      Field isValidField = result.getClass().getDeclaredField("isExpressionValid");
      isValidField.setAccessible(true);
      Boolean isValid = (Boolean) isValidField.get(result);
      Method getRulesMethod = result.getClass().getMethod("getRules");

      assertTrue("Expression should be valid for operator " + operator, isValid);
      List<Rule> rules = (List<Rule>) getRulesMethod.invoke(result);

      assertEquals("Operator should be " + operator, operator, rules.get(0).getOperator());
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheckWithPatternMatchingOperators() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String[] operators = {"=~", "!~", "=^", "!^", "=$", "!$"};

    for (String operator : operators) {
      String expression = "github.fileName " + operator + " \"test\"";

      Map<String, DataPoint> dataPointMap = new HashMap<>();
      DataPoint dataPoint = new DataPoint();
      dataPoint.setDataPointIdentifier("fileName");
      dataPoint.setInputDetails(new ArrayList<>());

      dataPointMap.put("github.fileName", dataPoint);
      when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

      Object result = method.invoke(checkService, expression, ACCOUNT_ID);

      assertNotNull("Result should not be null for operator " + operator, result);
      Field isValidField = result.getClass().getDeclaredField("isExpressionValid");
      isValidField.setAccessible(true);
      Boolean isValid = (Boolean) isValidField.get(result);
      Method getRulesMethod = result.getClass().getMethod("getRules");

      assertTrue("Expression should be valid for operator " + operator, isValid);
      List<Rule> rules = (List<Rule>) getRulesMethod.invoke(result);

      assertEquals("Operator should be " + operator, operator, rules.get(0).getOperator());
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheckWithMultipleInputValues() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String expression = "github.fileContains.\"README.md\".\"test pattern\" == \"result\"";

    Map<String, DataPoint> dataPointMap = new HashMap<>();
    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(DATA_POINT);
    dataPoint.setInputDetails(createMultipleInputDetails());

    dataPointMap.put(DATA_KEY, dataPoint);
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

    Object result = method.invoke(checkService, expression, ACCOUNT_ID);

    assertNotNull("Result should not be null", result);
    Field isValidField = result.getClass().getDeclaredField("isExpressionValid");
    isValidField.setAccessible(true);
    Boolean isValid = (Boolean) isValidField.get(result);
    Method getRulesMethod = result.getClass().getMethod("getRules");

    assertTrue("Expression should be valid", isValid);
    List<Rule> rules = (List<Rule>) getRulesMethod.invoke(result);

    assertEquals("Should have 1 rule", 1, rules.size());
    assertNotNull("InputValues should not be null", rules.get(0).getInputValues());
    assertEquals("Should have 2 input values", 2, rules.get(0).getInputValues().size());
    assertEquals(
        "First input value should be README.md", "\"README.md\"", rules.get(0).getInputValues().get(0).getValue());
    assertEquals("Second input value should be test pattern", "\"test pattern\"",
        rules.get(0).getInputValues().get(1).getValue());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheckWithInvalidSyntax() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String expression = "github.isFileExist true";

    Map<String, DataPoint> dataPointMap = new HashMap<>();
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

    try {
      method.invoke(checkService, expression, ACCOUNT_ID);
    } catch (Exception e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw e;
    }
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheckWithMismatchedParentheses() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String expression = "(github.isFileExist == true && github.fileCount > 0";

    Map<String, DataPoint> dataPointMap = new HashMap<>();
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

    try {
      method.invoke(checkService, expression, ACCOUNT_ID);
    } catch (Exception e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw e;
    }
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseAndValidateComplexCheckWithNestedParentheses() throws Exception {
    Method method =
        CheckServiceImpl.class.getDeclaredMethod("parseAndValidateComplexCheck", String.class, String.class);
    method.setAccessible(true);

    String expression = "((github.isFileExist == true) && (github.fileCount > 0)) || (catalog.hasDocs == true)";

    Map<String, DataPoint> dataPointMap = new HashMap<>();

    DataPoint dataPoint1 = new DataPoint();
    dataPoint1.setDataPointIdentifier("isFileExist");
    dataPoint1.setInputDetails(new ArrayList<>());

    DataPoint dataPoint2 = new DataPoint();
    dataPoint2.setDataPointIdentifier("fileCount");
    dataPoint2.setInputDetails(new ArrayList<>());

    DataPoint dataPoint3 = new DataPoint();
    dataPoint3.setDataPointIdentifier("hasDocs");
    dataPoint3.setInputDetails(new ArrayList<>());

    dataPointMap.put("github.isFileExist", dataPoint1);
    dataPointMap.put("github.fileCount", dataPoint2);
    dataPointMap.put("catalog.hasDocs", dataPoint3);
    when(dataPointService.getDataPointsMap(ACCOUNT_ID)).thenReturn(dataPointMap);

    Object result = method.invoke(checkService, expression, ACCOUNT_ID);

    assertNotNull("Result should not be null", result);
    Field isValidField = result.getClass().getDeclaredField("isExpressionValid");
    isValidField.setAccessible(true);
    Boolean isValid = (Boolean) isValidField.get(result);
    Method getRulesMethod = result.getClass().getMethod("getRules");

    assertTrue("Expression should be valid", isValid);
    List<Rule> rules = (List<Rule>) getRulesMethod.invoke(result);

    assertEquals("Should have 3 rules", 3, rules.size());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateDataPointInputValuesWithEmptyInputs() throws Exception {
    Method method = CheckServiceImpl.class.getDeclaredMethod(
        "validateDataPointInputValues", List.class, String.class, String.class, Map.class, String.class, List.class);
    method.setAccessible(true);

    List<String> dataPointInputValues = new ArrayList<>();
    Map<String, DataPoint> dataPointMap = new HashMap<>();

    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(DATA_POINT);
    dataPoint.setInputDetails(new ArrayList<>());

    dataPointMap.put(DATA_KEY, dataPoint);
    List<InputDetails> inputDetails = new ArrayList<>();

    // Should not throw exception with empty inputs
    method.invoke(checkService, dataPointInputValues, DATA_SOURCE, DATA_POINT, dataPointMap, ACCOUNT_ID, inputDetails);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateDataPointInputValuesWithExactMatchingInputs() throws Exception {
    Method method = CheckServiceImpl.class.getDeclaredMethod(
        "validateDataPointInputValues", List.class, String.class, String.class, Map.class, String.class, List.class);
    method.setAccessible(true);

    List<String> dataPointInputValues = List.of("README.md", "pattern");
    Map<String, DataPoint> dataPointMap = new HashMap<>();

    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(DATA_POINT);
    dataPoint.setInputDetails(createMultipleInputDetails());

    dataPointMap.put(DATA_KEY, dataPoint);
    List<InputDetails> inputDetails = createMultipleInputDetails();

    // Should not throw exception with exact matching inputs
    method.invoke(checkService, dataPointInputValues, DATA_SOURCE, DATA_POINT, dataPointMap, ACCOUNT_ID, inputDetails);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateDataPointInputValuesWithMissingDataSource() throws Exception {
    Method method = CheckServiceImpl.class.getDeclaredMethod(
        "validateDataPointInputValues", List.class, String.class, String.class, Map.class, String.class, List.class);
    method.setAccessible(true);

    List<String> dataPointInputValues = List.of("README.md");
    Map<String, DataPoint> dataPointMap = new HashMap<>();
    List<InputDetails> inputDetails = createInputDetails();

    try {
      // Should throw exception when data point not found in map
      method.invoke(checkService, dataPointInputValues, "unknown", DATA_POINT, dataPointMap, ACCOUNT_ID, inputDetails);
    } catch (Exception e) {
      if (e.getCause() instanceof InvalidRequestException) {
        throw (InvalidRequestException) e.getCause();
      }
      throw e;
    }
  }

  private List<InputDetails> createInputDetails() {
    List<InputDetails> inputDetails = new ArrayList<>();
    InputDetails details = new InputDetails();
    details.setKey("filePath");
    details.setRequired(true);
    inputDetails.add(details);
    return inputDetails;
  }

  private List<InputDetails> createMultipleInputDetails() {
    List<InputDetails> inputDetails = new ArrayList<>();

    InputDetails details1 = new InputDetails();
    details1.setKey("filePath");
    details1.setRequired(true);
    inputDetails.add(details1);

    InputDetails details2 = new InputDetails();
    details2.setKey("searchPattern");
    details2.setRequired(true);
    inputDetails.add(details2);

    return inputDetails;
  }
}
