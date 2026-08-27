/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import java.lang.reflect.Field;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FieldNameHelper {
  public static Object readNestedFieldValue(Object obj, String fieldName) {
    try {
      String[] fieldNames = fieldName.split("\\.");
      Object currentObject = obj;
      for (String name : fieldNames) {
        if (currentObject == null) {
          continue;
        }
        Field field = getField(currentObject.getClass(), name);
        if (field == null) {
          throw new NoSuchFieldException(
              "Field '" + name + "' not found in class '" + currentObject.getClass().getName() + "'");
        }
        field.setAccessible(true);
        currentObject = field.get(currentObject);
      }
      return currentObject;
    } catch (NoSuchFieldException | IllegalAccessException e) {
      return null;
    }
  }

  private static Field getField(Class<?> clazz, String fieldName) {
    Field field = null;
    try {
      field = clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      Class<?> superClass = clazz.getSuperclass();
      if (superClass != null) {
        field = getField(superClass, fieldName);
      }
    }
    return field;
  }

  public static boolean isFieldSupported(Object obj, String fieldName) {
    try {
      String[] fieldNames = fieldName.split("\\.");
      Object currentObject = obj;
      for (String name : fieldNames) {
        if (currentObject == null) {
          return false;
        }
        Field field = getField(currentObject.getClass(), name);
        if (field == null) {
          return false;
        }
        field.setAccessible(true);
        currentObject = field.get(currentObject);
      }
      return true;
    } catch (IllegalAccessException e) {
      return false;
    }
  }
}
