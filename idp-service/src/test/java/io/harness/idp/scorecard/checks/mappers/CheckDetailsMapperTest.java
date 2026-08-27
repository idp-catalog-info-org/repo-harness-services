/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.mappers;

import static io.harness.rule.OwnerRule.AGNIVA;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.InputValue;
import io.harness.spec.server.idp.v1.model.Rule;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
public class CheckDetailsMapperTest {
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testConstructExpressionFromRulesWithNonArrayOperatorAndValue() {
    Rule rule = createRule("source", "ruleIdentifier", "==", "3.0.0");
    List<Rule> rules = List.of(rule);

    String expression =
        CheckDetailsMapper.constructExpressionFromRules(rules, CheckDetails.RuleStrategyEnum.ALL_OF, "", false);

    String expectedExpression = "source.\"ruleIdentifier\"==\"3.0.0\"";
    assertEquals(expectedExpression, expression);
  }
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetExpressionWithOperatorAndArrayValue() {
    Rule rule = createRule("dataSource", "identifier", "=~", "[\"value1\",\"value2\",\"value3\"]");
    String dpValueSuffix = "";
    boolean getLhsOnly = false;
    String expression = CheckDetailsMapper.getExpression(rule, dpValueSuffix, getLhsOnly);
    String expectedExpression = "dataSource.\"identifier\"=~[\"value1\",\"value2\",\"value3\"]";
    assertEquals(expectedExpression, expression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetDisplayExpression() {
    Rule regularRule = createRule("dataSource", "identifier", "==", "expectedValue");
    regularRule.setInputValues(createInputValueList("param1", "param2"));
    String regularExpression =
        CheckDetailsMapper.getDisplayExpression(regularRule, CheckDetails.RuleStrategyEnum.ALL_OF);
    assertEquals("dataSource.identifier.param1.param2==expectedValue", regularExpression);

    Rule anyOfRule = createRule("dataSource", "identifier", "!=", "testValue");
    anyOfRule.setInputValues(createInputValueList("input1", "input2"));
    String anyOfExpression = CheckDetailsMapper.getDisplayExpression(anyOfRule, CheckDetails.RuleStrategyEnum.ANY_OF);
    assertEquals("dataSource.identifier.input1.input2!=testValue", anyOfExpression);

    Rule advancedRule = createRule("dataSource", "identifier", "==", "expectedValue");
    advancedRule.setInputValues(createInputValueList("param1", "param2"));
    String advancedExpression =
        CheckDetailsMapper.getDisplayExpression(advancedRule, CheckDetails.RuleStrategyEnum.ADVANCED);
    assertEquals("dataSource.identifier.param1.param2", advancedExpression);

    Rule quotedRule = createRule("dataSource", "identifier", "==", "value");
    quotedRule.setInputValues(createInputValueList("\"quoted\"", "param2"));
    String quotedExpression = CheckDetailsMapper.getDisplayExpression(quotedRule, CheckDetails.RuleStrategyEnum.ALL_OF);
    assertEquals("dataSource.identifier.quoted.param2==value", quotedExpression);

    Rule quotedAdvancedRule = createRule("dataSource", "identifier", "==", "value");
    quotedAdvancedRule.setInputValues(createInputValueList("\"quoted\"", "param2"));
    String quotedAdvancedExpression =
        CheckDetailsMapper.getDisplayExpression(quotedAdvancedRule, CheckDetails.RuleStrategyEnum.ADVANCED);
    assertEquals("dataSource.identifier.\"quoted\".param2", quotedAdvancedExpression);

    Rule customCheckRule = createRule("custom_check", "customCheckId", "==", "expectedValue");
    customCheckRule.setInputValues(createInputValueList("param1"));
    String customCheckExpression =
        CheckDetailsMapper.getDisplayExpression(customCheckRule, CheckDetails.RuleStrategyEnum.ALL_OF);
    assertEquals("custom_check.customCheckId.param1==expectedValue", customCheckExpression);

    Rule noInputsRule = createRule("dataSource", "identifier", "==", "expectedValue");
    noInputsRule.setInputValues(new ArrayList<>());
    String noInputsExpression =
        CheckDetailsMapper.getDisplayExpression(noInputsRule, CheckDetails.RuleStrategyEnum.ALL_OF);
    assertEquals("dataSource.identifier==expectedValue", noInputsExpression);

    Rule noInputsAdvancedRule = createRule("dataSource", "identifier", ">=", "100");
    noInputsAdvancedRule.setInputValues(new ArrayList<>());
    String noInputsAdvancedExpression =
        CheckDetailsMapper.getDisplayExpression(noInputsAdvancedRule, CheckDetails.RuleStrategyEnum.ADVANCED);
    assertEquals("dataSource.identifier", noInputsAdvancedExpression);

    Rule gtRule = createRule("source", "dataPoint", ">", "50");
    gtRule.setInputValues(createInputValueList("threshold"));
    String gtExpression = CheckDetailsMapper.getDisplayExpression(gtRule, CheckDetails.RuleStrategyEnum.ALL_OF);
    assertEquals("source.dataPoint.threshold>50", gtExpression);

    Rule regexRule = createRule("github", "fileContents", "=~", "pattern.*");
    regexRule.setInputValues(createInputValueList("README.md"));
    String regexExpression = CheckDetailsMapper.getDisplayExpression(regexRule, CheckDetails.RuleStrategyEnum.ANY_OF);
    assertEquals("github.fileContents.README.md=~pattern.*", regexExpression);

    Rule multiInputRule = createRule("scm", "branchProtection", "==", "true");
    multiInputRule.setInputValues(createInputValueList("\"main\"", "\"develop\"", "rules"));
    String multiInputExpression =
        CheckDetailsMapper.getDisplayExpression(multiInputRule, CheckDetails.RuleStrategyEnum.ALL_OF);
    assertEquals("scm.branchProtection.main.develop.rules==true", multiInputExpression);

    Rule singleQuotedRule = createRule("kubernetes", "replicas", ">=", "3");
    singleQuotedRule.setInputValues(createInputValueList("\"deployment-name\""));
    String singleQuotedExpression =
        CheckDetailsMapper.getDisplayExpression(singleQuotedRule, CheckDetails.RuleStrategyEnum.ALL_OF);
    assertEquals("kubernetes.replicas.deployment-name>=3", singleQuotedExpression);

    Rule complexCustomCheck = createRule("custom_check", "check123", "&&", "");
    complexCustomCheck.setInputValues(createInputValueList("param1", "param2", "param3"));
    String complexCustomCheckExpression =
        CheckDetailsMapper.getDisplayExpression(complexCustomCheck, CheckDetails.RuleStrategyEnum.ADVANCED);
    assertEquals("custom_check.check123.param1.param2.param3", complexCustomCheckExpression);

    Rule booleanRule = createRule("config", "enabled", "==", "false");
    booleanRule.setInputValues(createInputValueList("featureFlag"));
    String booleanExpression =
        CheckDetailsMapper.getDisplayExpression(booleanRule, CheckDetails.RuleStrategyEnum.ANY_OF);
    assertEquals("config.enabled.featureFlag==false", booleanExpression);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCleanComplexCheck() {
    assertNull(CheckDetailsMapper.cleanComplexCheck(null));
    assertEquals("", CheckDetailsMapper.cleanComplexCheck(""));

    assertEquals("plain text", CheckDetailsMapper.cleanComplexCheck("plain text"));
    assertEquals("text with \"quotes\" in it", CheckDetailsMapper.cleanComplexCheck("text with \\\"quotes\\\" in it"));

    assertEquals("text with \\ backslash", CheckDetailsMapper.cleanComplexCheck("text with \\\\ backslash"));
    assertEquals("text with \"quotes\" and \\ backslash",
        CheckDetailsMapper.cleanComplexCheck("text with \\\"quotes\\\" and \\\\ backslash"));
    assertEquals("text with trailing \\", CheckDetailsMapper.cleanComplexCheck("text with trailing \\"));
    assertEquals("text with \\n newline", CheckDetailsMapper.cleanComplexCheck("text with \\n newline"));
  }

  private Rule createRule(String dataSourceIdentifier, String identifier, String operator, String value) {
    Rule rule = new Rule();
    rule.setDataSourceIdentifier(dataSourceIdentifier);
    rule.setIdentifier(identifier);
    rule.setDataPointIdentifier(identifier);
    rule.setOperator(operator);
    rule.setValue(value);
    return rule;
  }

  private List<InputValue> createInputValueList(String... inputValues) {
    List<InputValue> inputValueList = new ArrayList<>();
    for (String value : inputValues) {
      InputValue inputValue = new InputValue();
      inputValue.setValue(value);
      inputValueList.add(inputValue);
    }
    return inputValueList;
  }
}
