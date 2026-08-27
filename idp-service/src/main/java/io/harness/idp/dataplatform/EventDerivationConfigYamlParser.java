/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.dataplatform;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.config_models.derivation_config.v1.EventDerivationConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads the classpath YAML template (protobuf JSON field names) and materializes {@link EventDerivationConfig}.
 */
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IDP)
public class EventDerivationConfigYamlParser {
  private static final String RESOURCE_PATH = "idp_custom_kind_event_derivation_template.yaml";
  private static final String CDC_KIND_PLACEHOLDER = "__CDC_KIND_LITERAL__";
  private static final Pattern CDC_KIND_LITERAL_PATTERN = Pattern.compile("^[A-Za-z0-9_$-]+$");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private volatile String cachedRawTemplate;

  @Inject
  public EventDerivationConfigYamlParser() {}

  public EventDerivationConfig parseForCdcKind(String cdcKindLiteral) {
    log.info("{} parser start cdcKindLiteral={}", UdpEventDerivationConstants.LOG_PREFIX, cdcKindLiteral);
    String yaml = loadRawTemplate().replace(CDC_KIND_PLACEHOLDER, sanitizeCdcKindLiteral(cdcKindLiteral));
    Object loaded = new Yaml().load(yaml);
    String json;
    try {
      json = OBJECT_MAPPER.writeValueAsString(loaded);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to serialize YAML-derived map to JSON", e);
    }
    EventDerivationConfig.Builder builder = EventDerivationConfig.newBuilder();
    try {
      JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Failed to parse EventDerivationConfig JSON derived from template", e);
    }
    EventDerivationConfig parsed = builder.build();
    log.info("{} parser complete cdcKindLiteral={} attributes={} entities={}", UdpEventDerivationConstants.LOG_PREFIX,
        cdcKindLiteral, parsed.getAttributesCount(), parsed.getEntitiesCount());
    return parsed;
  }

  private static String sanitizeCdcKindLiteral(String cdcKindLiteral) {
    if (cdcKindLiteral == null || !CDC_KIND_LITERAL_PATTERN.matcher(cdcKindLiteral).matches()) {
      throw new IllegalArgumentException(
          "CDC kind literal contains characters unsafe for YAML template substitution: " + cdcKindLiteral);
    }
    return cdcKindLiteral;
  }

  private String loadRawTemplate() {
    String local = cachedRawTemplate;
    if (local != null) {
      return local;
    }
    synchronized (this) {
      if (cachedRawTemplate != null) {
        return cachedRawTemplate;
      }
      try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_PATH)) {
        if (in == null) {
          throw new IllegalStateException("Missing classpath resource: " + RESOURCE_PATH);
        }
        cachedRawTemplate = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        log.info("{} parser loaded template resourcePath={} bytes={}", UdpEventDerivationConstants.LOG_PREFIX,
            RESOURCE_PATH, cachedRawTemplate.length());
        return cachedRawTemplate;
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read " + RESOURCE_PATH, e);
      }
    }
  }
}
