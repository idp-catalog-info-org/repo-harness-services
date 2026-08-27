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
public class EntityRefsParameterFieldTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEntityRefsParameterFieldBuilder() {
    ParameterField<String> entityRef = ParameterField.createValueField("component:default/my-component");
    ParameterField<Object> value = ParameterField.createValueField("testValue");

    EntityRefsParameterField field = EntityRefsParameterField.builder().entityRef(entityRef).value(value).build();

    assertNotNull(field);
    assertEquals("component:default/my-component", field.getEntityRef().getValue());
    assertEquals("testValue", field.getValue().getValue());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEntityRefsParameterFieldSettersAndGetters() {
    EntityRefsParameterField field = EntityRefsParameterField.builder().build();

    ParameterField<String> entityRef = ParameterField.createValueField("component:default/test");
    ParameterField<Object> value = ParameterField.createValueField(123);

    field.setEntityRef(entityRef);
    field.setValue(value);

    assertEquals("component:default/test", field.getEntityRef().getValue());
    assertEquals(123, field.getValue().getValue());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEntityRefsParameterFieldImplementsVisitable() {
    EntityRefsParameterField field = EntityRefsParameterField.builder().build();
    assertTrue(field instanceof Visitable);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEntityRefsParameterFieldWithNullValues() {
    EntityRefsParameterField field = EntityRefsParameterField.builder().entityRef(null).value(null).build();

    assertNotNull(field);
    assertNull(field.getEntityRef());
    assertNull(field.getValue());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEntityRefsParameterFieldWithExpressionValue() {
    ParameterField<String> entityRef = ParameterField.createExpressionField(true, "<+input>", null, true);
    ParameterField<Object> value = ParameterField.createExpressionField(true, "<+pipeline.variables.val>", null, true);

    EntityRefsParameterField field = EntityRefsParameterField.builder().entityRef(entityRef).value(value).build();

    assertNotNull(field);
    assertTrue(field.getEntityRef().isExpression());
    assertTrue(field.getValue().isExpression());
  }
}
