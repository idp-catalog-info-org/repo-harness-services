/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.filter;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CI;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CI_CODE_BASE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PROPERTIES;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.filters.FilterCreatorHelper;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts secrets consumed through the expression form -- {@code <+secrets.getValue("mySecret")>} -- from a
 * YAML subtree, so that they are recorded as pipeline references at save time.
 */
@UtilityClass
@OwnedBy(HarnessTeam.CI)
@Slf4j
public class CISecretExpressionExtractor {
  private static final Pattern SECRET_EXPRESSION =
      Pattern.compile("<\\+\\s*secrets\\.getValue\\(\\s*['\"]([^'\"<>]+)['\"]\\s*\\)\\s*>");

  /**
   * Extracts references from the node the filter creator is currently visiting, using its fully qualified name as
   * the FQN base. Returns an empty list when the context carries no node.
   */
  public List<EntityDetailProtoDTO> extract(FilterCreationContext filterCreationContext) {
    YamlField currentField = filterCreationContext.getCurrentField();
    if (currentField == null || currentField.getNode() == null) {
      return Collections.emptyList();
    }
    YamlNode node = currentField.getNode();
    try {
      return extract(node.getCurrJsonNode(), YamlUtils.getFullyQualifiedName(node),
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId());
    } catch (Exception ex) {
      log.warn("Skipping secret expression references for field [{}]", currentField.getName(), ex);
      return Collections.emptyList();
    }
  }

  public List<EntityDetailProtoDTO> extractFromCodebase(FilterCreationContext filterCreationContext) {
    YamlField currentField = filterCreationContext.getCurrentField();
    if (currentField == null || currentField.getNode() == null) {
      return Collections.emptyList();
    }
    YamlNode stageNode = currentField.getNode();
    YamlNode codeBaseNode;
    try {
      YamlNode properties = YamlUtils.getGivenYamlNodeFromParentPath(stageNode, PROPERTIES);
      codeBaseNode = properties.getField(CI).getNode().getField(CI_CODE_BASE).getNode();
    } catch (Exception ex) {
      // Codebase is not mandatory in case git clone is false.
      return Collections.emptyList();
    }
    try {
      String baseFqn = YamlUtils.getFullyQualifiedName(codeBaseNode);
      return extract(codeBaseNode.getCurrJsonNode(), baseFqn, filterCreationContext.getSetupMetadata().getAccountId(),
          filterCreationContext.getSetupMetadata().getOrgId(), filterCreationContext.getSetupMetadata().getProjectId());
    } catch (Exception ex) {
      log.warn("Skipping codebase secret expression references for stage [{}]", currentField.getName(), ex);
      return Collections.emptyList();
    }
  }

  public List<EntityDetailProtoDTO> extract(
      JsonNode root, String baseFqn, String accountId, String orgId, String projectId) {
    if (root == null) {
      return Collections.emptyList();
    }
    // LinkedHashMap so that references are emitted in the order they appear in the YAML.
    Map<String, EntityDetailProtoDTO> referencesByKey = new LinkedHashMap<>();
    collect(root, baseFqn, referencesByKey, accountId, orgId, projectId);
    return new ArrayList<>(referencesByKey.values());
  }

  private void collect(JsonNode node, String fqn, Map<String, EntityDetailProtoDTO> references, String accountId,
      String orgId, String projectId) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        // __uuid is injected by the YAML processor and is never user content.
        if (YamlNode.UUID_FIELD_NAME.equals(field.getKey())) {
          continue;
        }
        collect(field.getValue(), appendToFqn(fqn, field.getKey()), references, accountId, orgId, projectId);
      }
    } else if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        collect(node.get(index), appendToFqn(fqn, String.valueOf(index)), references, accountId, orgId, projectId);
      }
    } else if (node.isTextual()) {
      collectFromText(node.textValue(), fqn, references, accountId, orgId, projectId);
    }
  }

  private void collectFromText(String text, String fqn, Map<String, EntityDetailProtoDTO> references, String accountId,
      String orgId, String projectId) {
    if (isEmpty(text)) {
      return;
    }
    Matcher matcher = SECRET_EXPRESSION.matcher(text);
    while (matcher.find()) {
      String secretRef = matcher.group(1).trim();
      if (isEmpty(secretRef)) {
        continue;
      }
      // A single field can legitimately hold the same secret twice; keep one reference per (secret, FQN).
      String key = secretRef + "@" + fqn;
      if (references.containsKey(key)) {
        continue;
      }
      toEntityDetail(secretRef, fqn, accountId, orgId, projectId)
          .ifPresent(entityDetail -> references.put(key, entityDetail));
    }
  }

  private Optional<EntityDetailProtoDTO> toEntityDetail(
      String secretRef, String fqn, String accountId, String orgId, String projectId) {
    try {
      return Optional.of(FilterCreatorHelper.convertToEntityDetailProtoDTO(
          accountId, orgId, projectId, fqn, ParameterField.createValueField(secretRef), EntityTypeProtoEnum.SECRETS));
    } catch (Exception ex) {
      // References are best effort. A malformed or out-of-scope reference must never fail a pipeline save.
      log.warn("Skipping unparseable secret expression [{}] at {}", secretRef, fqn, ex);
      return Optional.empty();
    }
  }

  private String appendToFqn(String fqn, String childKey) {
    return isEmpty(fqn) ? childKey : fqn + PATH_CONNECTOR + childKey;
  }
}
