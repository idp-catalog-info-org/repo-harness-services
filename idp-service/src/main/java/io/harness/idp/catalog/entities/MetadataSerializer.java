/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.entities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class MetadataSerializer extends JsonSerializer<Map<String, Object>> {
  final List<String> ignorableAnnotations = List.of("backstage.io/managed-by-location",
      "backstage.io/managed-by-origin-location", "backstage.io/view-url", "backstage.io/edit-url",
      "backstage.io/source-location", "backstage.io/source-template", "backstage.io/orphan");

  @Override
  public void serialize(Map<String, Object> metadata, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeStartObject();

    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if ("annotations".equals(key) && value instanceof Map) {
        @SuppressWarnings("unchecked") Map<String, Object> annotations = (Map<String, Object>) value;
        gen.writeObjectField("annotations", filterAnnotations(annotations));
      } else {
        gen.writeObjectField(key, value);
      }
    }

    gen.writeEndObject();
  }

  private Map<String, Object> filterAnnotations(Map<String, Object> annotations) {
    annotations.entrySet().removeIf(entry -> ignorableAnnotations.contains(entry.getKey()));
    return annotations;
  }
}
