/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans.unified;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CONDITIONS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.EQ;
import static io.harness.pms.yaml.YAMLFieldNameConstants.IN;
import static io.harness.pms.yaml.YAMLFieldNameConstants.MATCH_ANY_CONDITION;
import static io.harness.pms.yaml.YAMLFieldNameConstants.NOT;

import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.approval.step.beans.Condition;
import io.harness.steps.approval.step.beans.ConditionV1;
import io.harness.steps.approval.step.beans.CriteriaSpecType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapper;
import io.harness.steps.approval.step.beans.JexlCriteriaSpec;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpec;
import io.harness.steps.approval.step.beans.Operator;
import io.harness.steps.shellscript.HarnessFileStoreSource;
import io.harness.steps.shellscript.ShellScriptBaseSource;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UnifiedCriteriaMapper {
  private List<Condition> getConditionsList(List<Map<String, ConditionV1>> conditions) {
    List<Condition> conditionList = new ArrayList<>();
    for (Map<String, ConditionV1> condition : conditions) {
      for (Map.Entry<String, ConditionV1> entry : condition.entrySet()) {
        String key = entry.getKey();
        ConditionV1 value = entry.getValue();
        conditionList.addAll(appendConditionList(key, value));
      }
    }
    return conditionList;
  }

  private List<Condition> appendConditionList(String key, ConditionV1 condition) {
    List<Condition> conditionList = new ArrayList<>();
    if (ParameterField.isNotNull(condition.getEquals())) {
      conditionList.add(Condition.builder().key(key).operator(Operator.EQ).value(condition.getEquals()).build());
    }
    if (isNotEmpty(condition.getIn())) {
      conditionList.add(
          Condition.builder().key(key).operator(Operator.IN).value(listToStringSlice(condition.getIn())).build());
    }
    if (condition.getNotCondition() != null) {
      ConditionV1 notCondition = condition.getNotCondition();
      if (ParameterField.isNotNull(notCondition.getEquals())) {
        conditionList.add(
            Condition.builder().key(key).operator(Operator.NOT_EQ).value(notCondition.getEquals()).build());
      }
      if (isNotEmpty(notCondition.getIn())) {
        conditionList.add(Condition.builder()
                              .key(key)
                              .operator(Operator.NOT_IN)
                              .value(listToStringSlice(notCondition.getIn()))
                              .build());
      }
    }
    return conditionList;
  }

  private ShellScriptInlineSource toShellScriptInlineSource(UnifiedShellScriptSourceWrapper source) {
    UnifiedShellScriptInlineSource spec = (UnifiedShellScriptInlineSource) source.getWith();
    return ShellScriptInlineSource.builder().script(spec.getScript()).build();
  }

  private HarnessFileStoreSource toHarnessFileStoreSource(UnifiedShellScriptSourceWrapper source) {
    UnifiedHarnessFileStoreSource spec = (UnifiedHarnessFileStoreSource) source.getWith();
    return HarnessFileStoreSource.builder().file(spec.getFile()).build();
  }

  public static ShellScriptSourceWrapper toShellScriptSourceWrapper(UnifiedShellScriptSourceWrapper source) {
    if (source != null) {
      if (UnifiedShellScriptSourceType.INLINE.equals(source.getUses())) {
        return ShellScriptSourceWrapper.builder()
            .type(ShellScriptBaseSource.INLINE)
            .spec(toShellScriptInlineSource(source))
            .build();
      } else if (UnifiedShellScriptSourceType.HARNESS.equals(source.getUses())) {
        return ShellScriptSourceWrapper.builder()
            .type(ShellScriptBaseSource.HARNESS)
            .spec(toHarnessFileStoreSource(source))
            .build();
      } else {
        throw new InvalidRequestException("Source type must be from one of harness file store or inline script source");
      }
    }
    return null;
  }

  public static CriteriaSpecWrapper toCriteriaSpecWrapper(JsonNode criteria) {
    if (criteria != null) {
      // If "match-any-condition" is present this means we will have key-value criteria
      if (YamlUtils.isYamlFieldPresent(criteria, MATCH_ANY_CONDITION)) {
        if (!YamlUtils.isYamlFieldPresent(criteria, CONDITIONS)) {
          throw new InvalidYamlException("Conditions field is missing in the criteria");
        }
        JsonNode conditionsNode = criteria.get(CONDITIONS);
        if (!conditionsNode.isArray()) {
          throw new InvalidYamlException("Conditions field must be an array");
        }
        List<Map<String, ConditionV1>> conditionsMapList = populateConditionsMapList(conditionsNode);
        return CriteriaSpecWrapper.builder()
            .type(CriteriaSpecType.KEY_VALUES)
            .criteriaSpec(
                KeyValuesCriteriaSpec.builder()
                    .matchAnyCondition(ParameterField.createValueField(criteria.get(MATCH_ANY_CONDITION).asBoolean()))
                    .conditions(getConditionsList(conditionsMapList))
                    .build())
            .build();

      } else if (criteria.isTextual()) { // If criteria present in form of string then it is expression.
        String expression = criteria.textValue();
        return CriteriaSpecWrapper.builder()
            .type(CriteriaSpecType.JEXL)
            .criteriaSpec(JexlCriteriaSpec.builder().expression(ParameterField.createValueField(expression)).build())
            .build();
      } else {
        throw new InvalidRequestException("Approval or rejection criteria must fit in key values or jexl criteria");
      }
    }
    return null;
  }

  private List<Map<String, ConditionV1>> populateConditionsMapList(JsonNode conditionsNode) {
    List<Map<String, ConditionV1>> conditionsMapList = new ArrayList<>();
    for (JsonNode conditionNode : conditionsNode) {
      if (conditionNode != null && conditionNode.isObject()) {
        Map<String, ConditionV1> condition = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = conditionNode.fields();
        while (fields.hasNext()) {
          Map.Entry<String, JsonNode> field = fields.next();
          condition.put(field.getKey(), toConditionV1(field.getValue()));
        }
        if (isNotEmpty(condition)) {
          conditionsMapList.add(condition);
        }
      }
    }
    return conditionsMapList;
  }

  private ConditionV1 toConditionV1(JsonNode node) {
    var conditionV1Builder = ConditionV1.builder();
    if (YamlUtils.isYamlFieldPresent(node, EQ)) {
      conditionV1Builder.equals(ParameterField.createValueField(node.get(EQ).asText()));
    }
    if (YamlUtils.isYamlFieldPresent(node, IN)) {
      conditionV1Builder.in(YamlUtils.fetchListFromJsonNode(node.get(IN)));
    }
    if (YamlUtils.isYamlFieldPresent(node, NOT)) {
      conditionV1Builder.notCondition(toConditionV1(node.get(NOT)));
    }
    return conditionV1Builder.build();
  }

  private ParameterField<String> listToStringSlice(List<String> stringList) {
    return ParameterField.createValueField(String.join(",", stringList));
  }
}
