/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.USE_NATIVE_TYPE_ID;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.UnexpectedException;
import io.harness.serializer.AnnotationAwareJsonSubtypeResolver;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.serializer.jackson.EdgeCaseRegexModule;
import io.serializer.jackson.NGHarnessJacksonModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class YamlUtils {
  /**
   * Creates a new Yaml instance configured for dumping with BLOCK flow style.
   * Returns a fresh instance to avoid shared state issues during concurrent operations.
   */
  public static Yaml yamlObject() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    Representer representer = new Representer(options);
    representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    return new Yaml(representer, options);
  }

  /**
   * Creates a new ObjectMapper configured for YAML writing.
   * Returns a fresh instance to avoid shared state issues during concurrent operations.
   */
  private static ObjectMapper createYamlMapper() {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory()
                                               .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                               .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                                               .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                               .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                                               .disable(USE_NATIVE_TYPE_ID));
    mapper.registerModule(new EdgeCaseRegexModule());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.setSubtypeResolver(AnnotationAwareJsonSubtypeResolver.newInstance(mapper.getSubtypeResolver()));
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new NGHarnessJacksonModule());
    return mapper;
  }

  public static String writeObjectAsYaml(Object obj) {
    try {
      return createYamlMapper().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new UnexpectedException("Error writing object as YAML", e);
    }
  }

  public static <T> T read(String value, Class<T> cls) {
    try {
      return YamlPipelineUtils.read(value, cls);
    } catch (IOException e) {
      throw new UnexpectedException("Error reading the content", e);
    }
  }

  public static Map<String, Object> loadYamlStringAsMap(String yamlString) {
    return new Yaml().load(yamlString);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> additional) {
    Map<String, Object> merged = new LinkedHashMap<>(base);

    additional.forEach((key, value) -> {
      if (merged.containsKey(key)) {
        Object baseValue = merged.get(key);
        if (baseValue instanceof Map && value instanceof Map) {
          merged.put(key, merge((Map<String, Object>) baseValue, (Map<String, Object>) value));
        } else if (baseValue instanceof List && value instanceof List) {
          List<Object> mergedList = new ArrayList<>((List<Object>) baseValue);
          for (Object item : (List<Object>) value) {
            if (!mergedList.contains(item)) {
              mergedList.add(item);
            }
          }
          merged.put(key, mergedList);
        } else if (!baseValue.equals(value)) {
          List<Object> mergedList = new ArrayList<>();
          mergedList.add(baseValue);
          mergedList.add(value);
          merged.put(key, mergedList);
        }
      } else {
        merged.put(key, value);
      }
    });

    return merged;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> mergeIgnoringEmpty(Map<String, Object> base, Map<String, Object> additional) {
    Map<String, Object> merged = new LinkedHashMap<>(base);

    additional.forEach((key, value) -> {
      if (merged.containsKey(key)) {
        Object baseValue = merged.get(key);
        if (baseValue instanceof Map && value instanceof Map) {
          merged.put(key, mergeIgnoringEmpty((Map<String, Object>) baseValue, (Map<String, Object>) value));
        } else if (baseValue instanceof List && value instanceof List) {
          List<Object> mergedList = new ArrayList<>((List<Object>) baseValue);
          for (Object item : (List<Object>) value) {
            if (!mergedList.contains(item)) {
              mergedList.add(item);
            }
          }
          merged.put(key, mergedList);
        } else {
          merged.put(key, value);
        }
      } else {
        merged.put(key, value);
      }
    });

    return merged;
  }

  @SuppressWarnings("unchecked")
  public static boolean applyDataMerge(Map<String, Object> yamlMap, Object node) {
    if (node instanceof Map<?, ?> map) {
      Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();

      while (it.hasNext()) {
        Map.Entry<?, ?> rawEntry = it.next();
        String key = rawEntry.getKey().toString();
        Object value = rawEntry.getValue();

        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
          Object replacement = findValueRecursively(yamlMap, key);
          if (replacement != null) {
            ((Map<String, Object>) map).put(key, replacement);
          } else {
            it.remove();
          }
          continue;
        }

        if (!applyDataMerge(yamlMap, value)) {
          it.remove();
        }
      }

      return !map.isEmpty();
    }

    if (node instanceof Collection<?> collection) {
      collection.removeIf(e -> !applyDataMerge(yamlMap, e));
      return !collection.isEmpty();
    }

    return true;
  }

  private static Object findValueRecursively(Object node, String key) {
    if (node instanceof Map<?, ?> map) {
      if (map.containsKey(key)) {
        Object value = map.get(key);
        if (value != null && !(value instanceof String && ((String) value).trim().isEmpty())) {
          return value;
        }
      }
      for (Object value : map.values()) {
        Object found = findValueRecursively(value, key);
        if (found != null) {
          return found;
        }
      }
    }

    if (node instanceof Collection<?> collection) {
      for (Object item : collection) {
        Object found = findValueRecursively(item, key);
        if (found != null) {
          return found;
        }
      }
    }

    return null;
  }

  @SuppressWarnings("unchecked")
  public static Object getByPath(Map<String, Object> root, String path) {
    if (root == null || path == null || path.isEmpty()) {
      return null;
    }
    String[] keys = path.split("\\.");
    Object current = root;
    for (String key : keys) {
      if (current instanceof Map) {
        current = ((Map<String, Object>) current).get(key);
      } else {
        return null;
      }
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  @SuppressWarnings("unchecked")
  public static void putByPath(Map<String, Object> root, String path, Object value) {
    String[] keys = path.split("\\.");

    Map<String, Object> current = root;
    for (int i = 0; i < keys.length - 1; i++) {
      String key = keys[i];

      Object next = current.get(key);
      if (!(next instanceof Map)) {
        next = new LinkedHashMap<String, Object>();
        current.put(key, next);
      }

      current = (Map<String, Object>) next;
    }

    current.put(keys[keys.length - 1], value);
  }

  @SuppressWarnings("unchecked")
  public static void removeByPath(Map<String, Object> root, String path) {
    if (root == null || path == null || path.isEmpty()) {
      return;
    }

    String[] keys = path.split("\\.");
    Map<String, Object> current = root;

    for (int i = 0; i < keys.length - 1; i++) {
      Object next = current.get(keys[i]);
      if (!(next instanceof Map)) {
        return; // path does not exist
      }
      current = (Map<String, Object>) next;
    }

    current.remove(keys[keys.length - 1]);
  }

  @SuppressWarnings("unchecked")
  public static String removeFields(String yamlString, String paths) {
    if (yamlString == null || yamlString.isEmpty() || paths == null || paths.isEmpty()) {
      return yamlString;
    }

    Yaml yaml = new Yaml();
    Object loaded = yaml.load(yamlString);

    if (!(loaded instanceof Map)) {
      return yamlString;
    }

    Map<String, Object> root = (Map<String, Object>) loaded;

    for (String path : paths.split(",")) {
      removeByPathUsingTokenizer(root, path.trim());
    }

    return yaml.dump(root);
  }

  @SuppressWarnings("unchecked")
  private static void removeByPathUsingTokenizer(Map<String, Object> root, String path) {
    List<String> tokens = tokenize(path);
    Map<String, Object> current = root;

    for (int i = 0; i < tokens.size() - 1; i++) {
      Object next = current.get(tokens.get(i));
      if (!(next instanceof Map)) {
        return;
      }
      current = (Map<String, Object>) next;
    }

    current.remove(tokens.get(tokens.size() - 1));
  }

  private static List<String> tokenize(String path) {
    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inBracket = false;

    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);

      if (c == '[') {
        if (!current.isEmpty()) {
          tokens.add(current.toString());
          current.setLength(0);
        }
        inBracket = true;
        continue;
      }

      if (c == ']') {
        inBracket = false;
        tokens.add(stripQuotes(current.toString()));
        current.setLength(0);
        continue;
      }

      if (c == '.' && !inBracket) {
        tokens.add(current.toString());
        current.setLength(0);
        continue;
      }

      current.append(c);
    }

    if (!current.isEmpty()) {
      tokens.add(stripQuotes(current.toString()));
    }

    return tokens;
  }

  private static String stripQuotes(String value) {
    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  public static void deepMerge(Map<String, Object> target, Map<String, Object> source) {
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (value instanceof Map && target.get(key) instanceof Map) {
        deepMerge((Map<String, Object>) target.get(key), (Map<String, Object>) value);
      } else {
        target.put(key, value);
      }
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> mergeDecorator(Map<String, Object> yamlMap, Map<String, Object> decorator) {
    // Base decorator is null on entity creation; treat null inputs as empty to keep the merge null-safe.
    if (yamlMap == null) {
      yamlMap = new LinkedHashMap<>();
    }
    if (decorator == null) {
      return yamlMap;
    }
    for (Map.Entry<String, Object> entry : decorator.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (yamlMap.containsKey(key)) {
        if (yamlMap.get(key) instanceof Map && value instanceof Map) {
          yamlMap.put(key, mergeDecorator((Map<String, Object>) yamlMap.get(key), (Map<String, Object>) value));
        } else if (yamlMap.get(key) instanceof List && value instanceof List) {
          List<Object> existingValue = (List) yamlMap.get(key);
          existingValue.addAll((List) value);
          yamlMap.put(key, existingValue);
        } else {
          yamlMap.put(key, value);
        }
      } else {
        yamlMap.put(key, value);
      }
    }
    return yamlMap;
  }
}
