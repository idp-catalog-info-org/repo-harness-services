/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.HARSHIT;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretJsonFunctorTest extends CategoryTest {
  private static final String JSON = "${sweepingOutputSecrets.obtain(\"jsonString\",\"encodedValue\")}";
  private static final String PATH = "data.attributes.path";
  private static final String EXPECTED_EXPRESSION_SELECT =
      "${sweepingOutputSecrets.select(\"data.attributes.path\",${sweepingOutputSecrets.obtain(\"jsonString\",\"encodedValue\")})}";
  private static final String EXPECTED_EXPRESSION_LIST =
      "${sweepingOutputSecrets.list(\"data.attributes.path\",${sweepingOutputSecrets.obtain(\"jsonString\",\"encodedValue\")})}";
  private static final String MAP_AS_STRING = "{\"data\":\"someData\"}";
  private static final String EXPECTED_EXPRESSION_EXISTS =
      "${sweepingOutputSecrets.exists(\"data.attributes.path\",${sweepingOutputSecrets.obtain(\"jsonString\",\"encodedValue\")})}";
  private static final String EXPECTED_EXPRESSION_OBJECT = "${sweepingOutputSecrets.object({\"data\":\"someData\"})}";
  private static final String EXPECTED_EXPRESSION_IS_VALID =
      "${sweepingOutputSecrets.isValid({\"data\":\"someData\"})}";
  Map<String, Object> map = new HashMap<>();

  SecretJsonFunctor secretJsonFunctor;

  @Before
  public void setup() {
    secretJsonFunctor = new SecretJsonFunctor();
    map.put("data", "someData");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testSelect() {
    Object object = secretJsonFunctor.select(PATH, JSON);
    String value = object.toString();
    assertEquals(value, EXPECTED_EXPRESSION_SELECT);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testList() {
    Object object = secretJsonFunctor.list(PATH, JSON);
    String value = object.toString();
    assertEquals(value, EXPECTED_EXPRESSION_LIST);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testObject() {
    Object object = secretJsonFunctor.object(MAP_AS_STRING);
    String value = object.toString();
    assertEquals(value, EXPECTED_EXPRESSION_OBJECT);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testExists() {
    Object object = secretJsonFunctor.exists(PATH, JSON);
    String value = object.toString();
    assertEquals(value, EXPECTED_EXPRESSION_EXISTS);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testIsValid() {
    Object object = secretJsonFunctor.isValid(MAP_AS_STRING);
    String value = object.toString();
    assertEquals(value, EXPECTED_EXPRESSION_IS_VALID);
  }
}
