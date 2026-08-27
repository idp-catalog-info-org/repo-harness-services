/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.mappers;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ComplexCheckParserTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithSimpleRule() {
    String validExpression = "github.isFileExist == true";
    ComplexCheckParser.validateExpression(validExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithMultipleRules() {
    String validExpression = "github.isFileExist == true && github.fileContains.\"README.md\" == \"test\"";
    ComplexCheckParser.validateExpression(validExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithComplexNesting() {
    String validExpression =
        "(github.isFileExist == true || github.fileContains.\"README.md\" == \"test\") && (catalog.hasDocs == true)";
    ComplexCheckParser.validateExpression(validExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithDifferentOperators() {
    String validExpression1 = "github.fileCount == 10";
    String validExpression2 = "github.fileCount != 0";
    String validExpression3 = "github.fileCount > 5";
    String validExpression4 = "github.fileCount < 20";
    String validExpression5 = "github.fileCount >= 5";
    String validExpression6 = "github.fileCount <= 20";
    String validExpression7 = "github.fileContains.\"README.md\" =~ \"test\""; // Regex match
    String validExpression8 = "github.fileContains.\"README.md\" !~ \"test\""; // Regex not match
    String validExpression9 = "github.fileContains.\"README.md\" =^ \"test\""; // Starts with
    String validExpression10 = "github.fileContains.\"README.md\" !^ \"test\""; // Not starts with
    String validExpression11 = "github.fileContains.\"README.md\" =$ \"test\""; // Ends with
    String validExpression12 = "github.fileContains.\"README.md\" !$ \"test\""; // Not ends with

    ComplexCheckParser.validateExpression(validExpression1);
    ComplexCheckParser.validateExpression(validExpression2);
    ComplexCheckParser.validateExpression(validExpression3);
    ComplexCheckParser.validateExpression(validExpression4);
    ComplexCheckParser.validateExpression(validExpression5);
    ComplexCheckParser.validateExpression(validExpression6);
    ComplexCheckParser.validateExpression(validExpression7);
    ComplexCheckParser.validateExpression(validExpression8);
    ComplexCheckParser.validateExpression(validExpression9);
    ComplexCheckParser.validateExpression(validExpression10);
    ComplexCheckParser.validateExpression(validExpression11);
    ComplexCheckParser.validateExpression(validExpression12);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithQuotedStrings() {
    String validExpression = "github.fileContains.\"README.md\" == \"test\\\"with\\\"quotes\"";
    ComplexCheckParser.validateExpression(validExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionMalformedRule() {
    String invalidExpression = "github.isFileExist true";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionMismatchedParentheses() {
    String invalidExpression = "(github.isFileExist == true && github.fileContains.\"README.md\" == \"test\"";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionUnexpectedToken() {
    String invalidExpression = "github.isFileExist == true ?? github.fileContains.\"README.md\" == \"test\"";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionEmptyRule() {
    String invalidExpression = "";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseExpressionValid() {
    String validExpression = "github.isFileExist == true && github.fileContains.\"README.md\" == \"test\"";
    ComplexCheckParser.parseExpression(validExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseExpressionInvalid() {
    String invalidExpression = "github.isFileExist == true &&";
    ComplexCheckParser.parseExpression(invalidExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithNumericValues() {
    String validExpression1 = "github.fileCount == 100";
    String validExpression2 = "github.fileCount >= 0";
    String validExpression3 = "github.fileCount < 9999";

    ComplexCheckParser.validateExpression(validExpression1);
    ComplexCheckParser.validateExpression(validExpression2);
    ComplexCheckParser.validateExpression(validExpression3);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithBooleanValues() {
    String validExpression1 = "github.isEnabled == true";
    String validExpression2 = "github.isDisabled == false";
    String validExpression3 = "github.isActive != false";

    ComplexCheckParser.validateExpression(validExpression1);
    ComplexCheckParser.validateExpression(validExpression2);
    ComplexCheckParser.validateExpression(validExpression3);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithMultipleInputParameters() {
    String validExpression1 = "github.fileContains.\"README.md\".\"test\" == \"value\"";
    String validExpression2 = "github.api.\"param1\".\"param2\".\"param3\" == \"result\"";

    ComplexCheckParser.validateExpression(validExpression1);
    ComplexCheckParser.validateExpression(validExpression2);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithDeeplyNestedParentheses() {
    String validExpression = "((github.isFileExist == true) && (catalog.hasDocs == true)) || ((github.fileCount > 5) "
        + "&& (github.isActive == true))";
    ComplexCheckParser.validateExpression(validExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithComplexLogicalOperations() {
    String validExpression =
        "github.isFileExist == true && (catalog.hasDocs == true || github.fileCount > 5) && github.isActive == true";
    ComplexCheckParser.validateExpression(validExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithPatternMatchingOperators() {
    String regexMatch = "github.fileName =~ \"^test.*\\.md$\"";
    String regexNotMatch = "github.fileName !~ \"^test.*\\.md$\"";
    String startsWith = "github.fileName =^ \"test\"";
    String notStartsWith = "github.fileName !^ \"test\"";
    String endsWith = "github.fileName =$ \".md\"";
    String notEndsWith = "github.fileName !$ \".md\"";

    ComplexCheckParser.validateExpression(regexMatch);
    ComplexCheckParser.validateExpression(regexNotMatch);
    ComplexCheckParser.validateExpression(startsWith);
    ComplexCheckParser.validateExpression(notStartsWith);
    ComplexCheckParser.validateExpression(endsWith);
    ComplexCheckParser.validateExpression(notEndsWith);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithSpecialCharactersInIdentifiers() {
    String validExpression = "github-repo.file_exists == true";
    ComplexCheckParser.validateExpression(validExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithWhitespace() {
    String validExpression = "  github.isFileExist   ==   true  &&  catalog.hasDocs   ==   true  ";
    ComplexCheckParser.validateExpression(validExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionMissingOperator() {
    String invalidExpression = "github.isFileExist true";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionMissingValue() {
    String invalidExpression = "github.isFileExist ==";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionMissingDataPoint() {
    String invalidExpression = "== true";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionIncompleteRule() {
    String invalidExpression = "github.";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionUnmatchedOpenParenthesis() {
    String invalidExpression = "(github.isFileExist == true && catalog.hasDocs == true";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionUnmatchedCloseParenthesis() {
    String invalidExpression = "github.isFileExist == true && catalog.hasDocs == true)";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionInvalidOperator() {
    String invalidExpression = "github.isFileExist === true";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionTrailingOperator() {
    String invalidExpression = "github.isFileExist == true ||";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionLeadingOperator() {
    String invalidExpression = "&& github.isFileExist == true";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionDoubleOperator() {
    String invalidExpression = "github.isFileExist == true && && catalog.hasDocs == true";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInvalidExpressionEmptyParentheses() {
    String invalidExpression = "github.isFileExist == true && ()";
    ComplexCheckParser.validateExpression(invalidExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseExpressionWithComplexNesting() {
    String validExpression = "((github.isFileExist == true || catalog.hasDocs == true) && github.fileCount > 0)";
    ComplexCheckParser.parseExpression(validExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseExpressionWithMultipleRules() {
    String validExpression = "github.isFileExist == true && catalog.hasDocs == true && github.fileCount > 0";
    ComplexCheckParser.parseExpression(validExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseExpressionWithOrOperator() {
    String validExpression = "github.isFileExist == true || catalog.hasDocs == true";
    ComplexCheckParser.parseExpression(validExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithEscapedBackslashes() {
    String validExpression = "github.fileContains.\"path\\\\to\\\\file\" == \"value\"";
    ComplexCheckParser.validateExpression(validExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testValidateExpressionWithMixedOperators() {
    String validExpression = "(github.fileCount > 5 && github.fileCount < 100) || github.isFileExist == true";
    ComplexCheckParser.validateExpression(validExpression);
  }
}
