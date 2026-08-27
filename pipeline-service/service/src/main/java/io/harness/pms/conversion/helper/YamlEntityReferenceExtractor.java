/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility to extract entity references (templateRef, pipeline chaining) from V0 pipeline/template YAML.
 *
 * V0 templateRef pattern:
 * <pre>
 *   template:
 *     templateRef: myTemplate
 *     versionLabel: v1
 * </pre>
 *
 * V0 pipeline chaining pattern:
 * <pre>
 *   stage:
 *     type: Pipeline
 *     spec:
 *       org: default
 *       pipeline: childPipelineId
 *       project: projectId
 * </pre>
 *
 * Extensible for future reference types (serviceRef, environmentRef, etc.).
 */
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class YamlEntityReferenceExtractor {
  private static final String TEMPLATE = "template";
  private static final String TEMPLATE_REF = "templateRef";
  private static final String VERSION_LABEL = "versionLabel";
  private static final String TYPE = "type";
  private static final String PIPELINE_STAGE_TYPE = "Pipeline";
  private static final String SPEC = "spec";
  private static final String PIPELINE = "pipeline";
  private static final String ORG = "org";
  private static final String PROJECT = "project";
  private static final String GIT_BRANCH = "gitBranch";
  private static final int MAX_DEPTH = 20;

  /**
   * A template reference found in the YAML.
   * templateRef may contain scope prefix: "account.myTemplate", "org.myTemplate", or "myTemplate" (project scope).
   */
  @Data
  @Builder
  public static class TemplateReference {
    private String templateRef;
    private String versionLabel;
    private String gitBranch;
  }

  /**
   * A chained pipeline reference found in the YAML.
   */
  @Data
  @Builder
  public static class PipelineChainReference {
    private String pipelineIdentifier;
    private String orgIdentifier;
    private String projectIdentifier;
  }

  /**
   * Combined result of all extracted references from the YAML.
   */
  @Data
  @Builder
  public static class ExtractedReferences {
    @Builder.Default private List<TemplateReference> templateReferences = new ArrayList<>();
    @Builder.Default private List<PipelineChainReference> pipelineChainReferences = new ArrayList<>();
  }

  /**
   * Extract all entity references from a V0 YAML string.
   *
   * @param yaml V0 YAML string
   * @return ExtractedReferences containing all found references
   */
  public ExtractedReferences extractReferences(String yaml) {
    JsonNode rootNode = YamlUtils.readAsJsonNode(yaml);
    return extractReferences(rootNode);
  }

  /**
   * Extract only template references from a V0 YAML string.
   */
  public List<TemplateReference> extractTemplateReferences(String yaml) {
    return extractReferences(yaml).getTemplateReferences();
  }

  /**
   * Extract only pipeline chain references from a V0 YAML string.
   */
  public List<PipelineChainReference> extractPipelineChainReferences(String yaml) {
    return extractReferences(yaml).getPipelineChainReferences();
  }

  /**
   * Extract all entity references from a V0 YAML JsonNode.
   * Currently supports: templateRef, pipeline chaining.
   *
   * @param rootNode Parsed V0 YAML as JsonNode
   * @return ExtractedReferences containing all found references
   */
  public ExtractedReferences extractReferences(JsonNode rootNode) {
    Set<String> seenTemplateRefs = new LinkedHashSet<>();
    Set<String> seenPipelineRefs = new LinkedHashSet<>();
    List<TemplateReference> templateRefs = new ArrayList<>();
    List<PipelineChainReference> pipelineChainRefs = new ArrayList<>();

    traverse(rootNode, 0, seenTemplateRefs, seenPipelineRefs, templateRefs, pipelineChainRefs);

    return ExtractedReferences.builder()
        .templateReferences(templateRefs)
        .pipelineChainReferences(pipelineChainRefs)
        .build();
  }

  private void traverse(JsonNode node, int depth, Set<String> seenTemplateRefs, Set<String> seenPipelineRefs,
      List<TemplateReference> templateRefs, List<PipelineChainReference> pipelineChainRefs) {
    if (node == null || depth > MAX_DEPTH) {
      return;
    }

    if (node.isObject()) {
      // Check for template.templateRef pattern
      if (node.has(TEMPLATE) && node.get(TEMPLATE).isObject()) {
        JsonNode templateNode = node.get(TEMPLATE);
        if (templateNode.has(TEMPLATE_REF)) {
          String ref = templateNode.get(TEMPLATE_REF).asText();
          String version = templateNode.has(VERSION_LABEL) ? templateNode.get(VERSION_LABEL).asText() : null;
          String branch = templateNode.has(GIT_BRANCH) ? templateNode.get(GIT_BRANCH).asText() : null;
          String dedupeKey = ref + ":" + version;
          if (seenTemplateRefs.add(dedupeKey)) {
            templateRefs.add(
                TemplateReference.builder().templateRef(ref).versionLabel(version).gitBranch(branch).build());
          }
        }
      }

      // Check for Pipeline chaining: stage with type=Pipeline
      if (node.has(TYPE) && PIPELINE_STAGE_TYPE.equals(node.get(TYPE).asText()) && node.has(SPEC)) {
        JsonNode specNode = node.get(SPEC);
        if (specNode.isObject() && specNode.has(PIPELINE)) {
          String pipelineId = specNode.get(PIPELINE).asText();
          String org = specNode.has(ORG) ? specNode.get(ORG).asText() : null;
          String project = specNode.has(PROJECT) ? specNode.get(PROJECT).asText() : null;
          String dedupeKey = org + ":" + project + ":" + pipelineId;
          if (seenPipelineRefs.add(dedupeKey)) {
            pipelineChainRefs.add(PipelineChainReference.builder()
                                      .pipelineIdentifier(pipelineId)
                                      .orgIdentifier(org)
                                      .projectIdentifier(project)
                                      .build());
          }
        }
      }

      // Recurse into all child fields
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        traverse(field.getValue(), depth + 1, seenTemplateRefs, seenPipelineRefs, templateRefs, pipelineChainRefs);
      }
    } else if (node.isArray()) {
      ArrayNode arrayNode = (ArrayNode) node;
      for (int i = 0; i < arrayNode.size(); i++) {
        traverse(arrayNode.get(i), depth + 1, seenTemplateRefs, seenPipelineRefs, templateRefs, pipelineChainRefs);
      }
    }
  }
}
