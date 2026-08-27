/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.UnexpectedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class JacksonUtils {
  private JacksonUtils() {}

  public static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  public static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  public static <T> List<T> readValue(String entities, Class<?> clazz) {
    try {
      Class<?> clz = Class.forName(clazz.getName());
      JavaType type = JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, clz);
      return JSON_MAPPER.readValue(entities, type);
    } catch (ClassNotFoundException | JsonProcessingException e) {
      log.error("Error in readValue json string to corresponding list<clazz> pojo's. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(
          "Error in readValue json string to corresponding list<clazz> pojo's. Error = " + e.getMessage());
    }
  }

  public static <T> T readValueForSingleEntity(String entity, Class<?> clazz) {
    try {
      Class<?> clz = Class.forName(clazz.getName());
      return (T) JSON_MAPPER.readValue(entity, clz);
    } catch (ClassNotFoundException | JsonProcessingException e) {
      log.error(
          "Error in readValue json string to corresponding {} pojo. Error = {}", clazz.getName(), e.getMessage(), e);
      throw new UnexpectedException(String.format(
          "Error in readValue json string to corresponding %s pojo. Error = %s", clazz.getName(), e.getMessage()));
    }
  }

  public static <T> T readValueForObject(Object object, Class<T> clazz) {
    if (object == null) {
      return null;
    }
    try {
      return clazz.cast(JSON_MAPPER.readValue(JSON_MAPPER.writeValueAsString(object), clazz));
    } catch (Exception ex) {
      log.error("Error in readValue json object to corresponding clazz pojo. Error = {}", ex.getMessage(), ex);
      throw new UnexpectedException(
          "Error in readValue json object to corresponding clazz pojo. Error = " + ex.getMessage());
    }
  }

  public static <T> List<T> convert(Object entities, Class<?> clazz) {
    return convert(JSON_MAPPER, entities, clazz);
  }

  public static <T> List<T> convert(ObjectMapper mapper, Object entities, Class<?> clazz) {
    try {
      Class<?> clz = Class.forName(clazz.getName());
      JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, clz);
      return mapper.convertValue(entities, type);
    } catch (ClassNotFoundException e) {
      log.error("Error in convert json string to corresponding list<clazz> pojo's. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(
          "Error in convert json string to corresponding list<clazz> pojo's. Error = " + e.getMessage());
    }
  }

  public static String write(Object obj) {
    try {
      return JSON_MAPPER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      log.error("Error in convert object to string. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(e.getMessage());
    }
  }

  public static Map<String, Object> convert(Object entity) {
    return JSON_MAPPER.convertValue(entity, new TypeReference<>() {});
  }

  public static Object convert(Map<String, Object> entity) {
    return JSON_MAPPER.convertValue(entity, Object.class);
  }

  public static JsonNode readTree(String jsonString) {
    try {
      return JSON_MAPPER.readTree(jsonString);
    } catch (JsonProcessingException e) {
      log.error("Error in convert jsonString to JsonNode. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(e.getMessage());
    }
  }
}
