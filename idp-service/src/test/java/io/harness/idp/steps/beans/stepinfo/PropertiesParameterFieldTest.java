/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.steps.beans.stepinfo;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.walktree.visitor.helper.Visitable;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class PropertiesParameterFieldTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPropertiesParameterFieldBuilder() {
    ParameterField<String> property = ParameterField.createValueField("spec.owner");
    ParameterField<Object> value = ParameterField.createValueField("team-a");
    ParameterField<String> mode = ParameterField.createValueField("replace");

    PropertiesParameterField field =
        PropertiesParameterField.builder().property(property).value(value).mode(mode).build();

    assertNotNull(field);
    assertEquals("spec.owner", field.getProperty().getValue());
    assertEquals("team-a", field.getValue().getValue());
    assertEquals("replace", field.getMode().getValue());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPropertiesParameterFieldSettersAndGetters() {
    PropertiesParameterField field = PropertiesParameterField.builder().build();

    ParameterField<String> property = ParameterField.createValueField("metadata.name");
    ParameterField<Object> value = ParameterField.createValueField("my-service");
    ParameterField<String> mode = ParameterField.createValueField("append");

    field.setProperty(property);
    field.setValue(value);
    field.setMode(mode);

    assertEquals("metadata.name", field.getProperty().getValue());
    assertEquals("my-service", field.getValue().getValue());
    assertEquals("append", field.getMode().getValue());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPropertiesParameterFieldImplementsVisitable() {
    PropertiesParameterField field = PropertiesParameterField.builder().build();
    assertTrue(field instanceof Visitable);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPropertiesParameterFieldWithNullValues() {
    PropertiesParameterField field = PropertiesParameterField.builder().property(null).value(null).mode(null).build();

    assertNotNull(field);
    assertNull(field.getProperty());
    assertNull(field.getValue());
    assertNull(field.getMode());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPropertiesParameterFieldWithExpressionValues() {
    ParameterField<String> property = ParameterField.createExpressionField(true, "<+input>", null, true);
    ParameterField<Object> value = ParameterField.createExpressionField(true, "<+pipeline.variables.val>", null, true);
    ParameterField<String> mode = ParameterField.createValueField("replace");

    PropertiesParameterField field =
        PropertiesParameterField.builder().property(property).value(value).mode(mode).build();

    assertNotNull(field);
    assertTrue(field.getProperty().isExpression());
    assertTrue(field.getValue().isExpression());
  }
}
