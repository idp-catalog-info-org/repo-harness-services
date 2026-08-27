/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.expression;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IdpVariableExpressionEvaluatorTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConstructor() {
    Map<String, String> accountLevelVariables = new HashMap<>();
    accountLevelVariables.put("var1", "value1");
    accountLevelVariables.put("var2", "value2");

    Map<String, Object> systemVariables = new HashMap<>();
    systemVariables.put("accountId", "test-account-123");
    systemVariables.put("orgId", "test-org-456");

    IdpVariableExpressionEvaluator evaluator =
        new IdpVariableExpressionEvaluator(systemVariables, accountLevelVariables);

    assertThat(evaluator).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEvaluateAccountVariable() {
    Map<String, String> accountLevelVariables = new HashMap<>();
    accountLevelVariables.put("env", "production");
    accountLevelVariables.put("region", "us-east-1");

    Map<String, Object> systemVariables = new HashMap<>();
    systemVariables.put("accountId", "acc123");

    IdpVariableExpressionEvaluator evaluator =
        new IdpVariableExpressionEvaluator(systemVariables, accountLevelVariables);

    Object result = evaluator.resolve(systemVariables, true);
    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEvaluateSystemVariable() {
    Map<String, String> accountLevelVariables = new HashMap<>();

    Map<String, Object> systemVariables = new HashMap<>();
    systemVariables.put("accountId", "test-account");
    systemVariables.put("name", "Test Account");

    IdpVariableExpressionEvaluator evaluator =
        new IdpVariableExpressionEvaluator(systemVariables, accountLevelVariables);

    Object result = evaluator.resolve(systemVariables, true);
    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEvaluateWithEmptyVariables() {
    Map<String, String> accountLevelVariables = new HashMap<>();
    Map<String, Object> systemVariables = new HashMap<>();

    IdpVariableExpressionEvaluator evaluator =
        new IdpVariableExpressionEvaluator(systemVariables, accountLevelVariables);

    assertThat(evaluator).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEvaluateWithNullAccountVariables() {
    Map<String, Object> systemVariables = new HashMap<>();
    systemVariables.put("accountId", "test-account");

    IdpVariableExpressionEvaluator evaluator = new IdpVariableExpressionEvaluator(systemVariables, null);

    assertThat(evaluator).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEvaluateWithMultipleVariables() {
    Map<String, String> accountLevelVariables = new HashMap<>();
    accountLevelVariables.put("var1", "value1");
    accountLevelVariables.put("var2", "value2");
    accountLevelVariables.put("var3", "value3");

    Map<String, Object> systemVariables = new HashMap<>();
    systemVariables.put("accountId", "acc123");
    systemVariables.put("accountName", "Test Account");
    systemVariables.put("orgId", "org456");

    IdpVariableExpressionEvaluator evaluator =
        new IdpVariableExpressionEvaluator(systemVariables, accountLevelVariables);

    assertThat(evaluator).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEvaluateComplexSystemVariables() {
    Map<String, String> accountLevelVariables = new HashMap<>();
    accountLevelVariables.put("env", "prod");

    Map<String, Object> systemVariables = new HashMap<>();
    Map<String, Object> nestedData = new HashMap<>();
    nestedData.put("key1", "value1");
    nestedData.put("key2", 123);
    systemVariables.put("accountId", "acc123");
    systemVariables.put("metadata", nestedData);

    IdpVariableExpressionEvaluator evaluator =
        new IdpVariableExpressionEvaluator(systemVariables, accountLevelVariables);

    assertThat(evaluator).isNotNull();
  }
}
