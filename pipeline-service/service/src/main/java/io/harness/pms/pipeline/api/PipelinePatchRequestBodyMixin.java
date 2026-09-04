/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.api;

import io.harness.spec.server.pipeline.v1.model.GitUpdateDetails;
import io.harness.spec.server.pipeline.v1.model.PipelinePatchRequestBody;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Distinguishes an omitted description/tags field (leave unchanged) from an explicit JSON null (clear the field)
 * in a pipeline merge patch.
 *
 * <p>The generated OpenAPI model cannot tell "field absent" apart from "field explicitly null": {@code desc}
 * defaults to null either way, and {@code tags} defaults to an empty map either way. This deserializer inspects the
 * raw JSON tree instead of relying on the model's field defaults: an omitted field is left as null so the patch
 * persistence layer leaves it untouched, while an explicit null is mapped to an empty value ("" for desc, {} for
 * tags) so the patch persistence layer recognizes a clear operation.
 */
public final class PipelinePatchRequestBodyMixin {
  private PipelinePatchRequestBodyMixin() {}

  public static void configure(ObjectMapper objectMapper) {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(PipelinePatchRequestBody.class, new PipelinePatchRequestBodyDeserializer());
    objectMapper.registerModule(module);
  }

  private static final class PipelinePatchRequestBodyDeserializer extends JsonDeserializer<PipelinePatchRequestBody> {
    @Override
    public PipelinePatchRequestBody deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      JsonNode node = parser.readValueAsTree();
      PipelinePatchRequestBody body = new PipelinePatchRequestBody();

      if (node.hasNonNull("pipeline_yaml")) {
        body.setPipelineYaml(node.get("pipeline_yaml").asText());
      }
      if (node.hasNonNull("name")) {
        body.setName(node.get("name").asText());
      }
      if (node.has("desc")) {
        JsonNode descNode = node.get("desc");
        body.setDesc(descNode.isNull() ? "" : descNode.asText());
      }
      if (node.has("tags")) {
        body.setTags(readTags(node.get("tags")));
      } else {
        body.setTags(null);
      }
      if (node.hasNonNull("allowDynamicExecutions")) {
        body.setAllowDynamicExecutions(node.get("allowDynamicExecutions").asBoolean());
      }
      if (node.hasNonNull("git_details")) {
        body.setGitDetails(parser.getCodec().treeToValue(node.get("git_details"), GitUpdateDetails.class));
      }
      if (node.hasNonNull("version")) {
        body.setVersion(node.get("version").asText());
      }
      return body;
    }

    private static Map<String, String> readTags(JsonNode tagsNode) {
      Map<String, String> tags = new HashMap<>();
      if (tagsNode.isNull()) {
        return tags;
      }
      Iterator<Entry<String, JsonNode>> fields = tagsNode.fields();
      while (fields.hasNext()) {
        Entry<String, JsonNode> field = fields.next();
        tags.put(field.getKey(), field.getValue().isNull() ? null : field.getValue().asText());
      }
      return tags;
    }
  }
}
