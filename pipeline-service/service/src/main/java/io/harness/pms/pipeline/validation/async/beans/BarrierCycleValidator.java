/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.async.beans;

import static io.harness.beans.FeatureName.PIPE_DETECT_BARRIER_CYCLES;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.walktree.beans.VisitElementResult;
import io.harness.walktree.visitor.utilities.DummyVisitableElement;
import io.harness.walktree.visitor.utilities.SimpleVisitor;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class BarrierCycleValidator extends SimpleVisitor<DummyVisitableElement> {
  private static final String BARRIER_TYPE = "Barrier";
  private static final String SPEC_FIELD = "spec";
  private static final String BARRIER_REF_FIELD = "barrierRef";
  private static final String V1_BARRIER_TYPE = "barrier";
  private static final String V1_BARRIER_REF_FIELD = "name";
  private static final String V1_PARALLEL_GROUP_FIELD = "parallel";
  private static final String V1_GROUP_FIELD = "group";
  private static final String EXPRESSION_PATTERN = "<\\+.*>";
  private static final String DEPENDS_ON_FIELD = "dependsOn";

  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  @Inject
  public BarrierCycleValidator(Injector injector) {
    super(injector);
  }

  @Data
  @Builder
  private static class BarrierRefUsage {
    String barrierRef;
    @Builder.Default List<BarrierOccurrence> barrierOccurrences = new ArrayList<>();
  }

  @Data
  @Builder
  private static class BarrierOccurrence {
    String executionPath;
    List<PathSegment> pathSegments;
    boolean inRollbackSteps;
    String dagStageIdentifier;
  }

  @Data
  @Builder
  private static class DagStageInfo {
    String identifier;
    List<String> dependencies;
  }

  public void validate(String accountId, String pipelineYaml) {
    if (!pmsFeatureFlagService.isEnabled(accountId, PIPE_DETECT_BARRIER_CYCLES)) {
      return;
    }

    try {
      Map<String, BarrierRefUsage> barrierRefUsageMap = new HashMap<>();
      Map<String, DagStageInfo> dagStageInfoMap = new HashMap<>();
      YamlField pipelineYamlField = YamlUtils.readTree(pipelineYaml);
      if (pipelineYamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE) != null) {
        YamlNode pipelineNode = pipelineYamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode();
        walkYamlTree(barrierRefUsageMap, pipelineNode, "", dagStageInfoMap, null);
        detectCycles(barrierRefUsageMap, dagStageInfoMap);
      }
    } catch (IOException e) {
      log.error("Failed to parse pipeline YAML for barrier cycle validation", e);
    }
  }

  /**
   * Traverses the YAML tree from root to leaf, tracking parallel block context as we go.
   * @param node Current YAML node
   * @param executionPath Path from root
   * @param dagStageInfoMap Accumulates every DAG stage's identifier/dependsOn found in the tree
   * @param currentDagStageIdentifier The DAG stage (if any) that {@code node} is nested under
   */
  private void walkYamlTree(Map<String, BarrierRefUsage> barrierRefUsageMap, YamlNode node, String executionPath,
      Map<String, DagStageInfo> dagStageInfoMap, String currentDagStageIdentifier) {
    if (node == null) {
      return;
    }

    visitElement(barrierRefUsageMap, node, executionPath, currentDagStageIdentifier);

    String dagStageIdentifierForChildren = currentDagStageIdentifier;
    if (node.isObject() && node.getIdentifier() != null && node.getField(DEPENDS_ON_FIELD) != null) {
      dagStageInfoMap.computeIfAbsent(node.getIdentifier(), identifier -> buildDagStageInfo(node, identifier));
      dagStageIdentifierForChildren = node.getIdentifier();
    }

    if (node.isArray()) {
      int arrayIndex = 0;
      for (YamlNode childNode : node.asArray()) {
        String childPath = executionPath + "[" + arrayIndex + "]";
        walkYamlTree(barrierRefUsageMap, childNode, childPath, dagStageInfoMap, dagStageIdentifierForChildren);
        arrayIndex++;
      }
    } else if (node.isObject()) {
      List<YamlField> fields = node.fields();
      for (YamlField field : fields) {
        String fieldName = field.getName();
        String childPath = executionPath.isEmpty() ? fieldName : executionPath + "." + fieldName;
        walkYamlTree(barrierRefUsageMap, field.getNode(), childPath, dagStageInfoMap, dagStageIdentifierForChildren);
      }
    }
  }

  private DagStageInfo buildDagStageInfo(YamlNode stageNode, String identifier) {
    List<String> dependencies = new ArrayList<>();
    YamlField dependsOnField = stageNode.getField(DEPENDS_ON_FIELD);
    if (dependsOnField != null && dependsOnField.getNode().isArray()) {
      for (YamlNode dependencyNode : dependsOnField.getNode().asArray()) {
        dependencies.add(dependencyNode.asText());
      }
    }
    return DagStageInfo.builder().identifier(identifier).dependencies(dependencies).build();
  }

  @Override
  public VisitElementResult visitElement(Object currentElement) {
    Map<String, BarrierRefUsage> barrierRefUsageMap = new HashMap<>();
    return visitElement(barrierRefUsageMap, currentElement, "", null);
  }

  private VisitElementResult visitElement(Map<String, BarrierRefUsage> barrierRefUsageMap, Object currentElement,
      String executionPath, String dagStageIdentifier) {
    YamlNode element = (YamlNode) currentElement;

    if (StepSpecTypeConstants.PIPELINE_STAGE.equals(element.getType())) {
      return VisitElementResult.SKIP_SUBTREE;
    }

    if (BARRIER_TYPE.equals(element.getType()) || element.getField(V1_BARRIER_TYPE) != null) {
      trackBarrierUsage(barrierRefUsageMap, element, executionPath, dagStageIdentifier);
    }

    return VisitElementResult.CONTINUE;
  }

  private void trackBarrierUsage(Map<String, BarrierRefUsage> barrierRefUsageMap, YamlNode barrierNode,
      String executionPath, String dagStageIdentifier) {
    String barrierRef = extractBarrierRef(barrierNode);

    if (barrierRef == null || isExpression(barrierRef)) {
      return;
    }

    boolean inRollback = isInRollbackSection(executionPath);
    List<PathSegment> pathSegments = parseExecutionPath(executionPath);

    BarrierOccurrence occurrence = BarrierOccurrence.builder()
                                       .executionPath(executionPath)
                                       .pathSegments(pathSegments)
                                       .inRollbackSteps(inRollback)
                                       .dagStageIdentifier(dagStageIdentifier)
                                       .build();

    BarrierRefUsage usage =
        barrierRefUsageMap.computeIfAbsent(barrierRef, ref -> BarrierRefUsage.builder().barrierRef(ref).build());

    usage.getBarrierOccurrences().add(occurrence);
  }

  private String extractBarrierRef(YamlNode barrierNode) {
    try {
      YamlField specField = barrierNode.getField(SPEC_FIELD);
      if (specField != null) {
        return specField.getNode().getStringValue(BARRIER_REF_FIELD);
      }
      YamlField v1BarrierField = barrierNode.getField(V1_BARRIER_TYPE);
      if (v1BarrierField != null) {
        return v1BarrierField.getNode().getStringValue(V1_BARRIER_REF_FIELD);
      }
    } catch (Exception e) {
      log.debug("Failed to extract barrierRef from barrier node", e);
    }
    return null;
  }

  private boolean isExpression(String value) {
    return value != null && value.matches(".*" + EXPRESSION_PATTERN + ".*");
  }

  private boolean isInRollbackSection(String executionPath) {
    return executionPath.contains(YAMLFieldNameConstants.ROLLBACK_STEPS)
        || executionPath.contains(YAMLFieldNameConstants.ROLLBACK_STEPS_V1);
  }

  private void detectCycles(
      Map<String, BarrierRefUsage> barrierRefUsageMap, Map<String, DagStageInfo> dagStageInfoMap) {
    for (Map.Entry<String, BarrierRefUsage> entry : barrierRefUsageMap.entrySet()) {
      BarrierRefUsage usage = entry.getValue();

      if (hasCycleInFlow(usage.getBarrierOccurrences(), dagStageInfoMap)) {
        throw new InvalidRequestException(buildCycleErrorMessage(usage));
      }
    }
  }

  /**
   * Detects if there's a cycle in a list of barrier occurrences.
   * A cycle exists when any two barriers with the same reference have a happens-before relationship.
   *
   * Key principle: Barriers with the same reference must ALL be concurrent (able to execute simultaneously).
   * If any pair has a happens-before relationship, it's a deadlock.
   *
   * Examples:
   * - Sequential barriers [B1, B2] = CYCLE (B1 happens-before B2)
   * - Parallel barriers in same block [B1 || B2] = NO CYCLE (concurrent)
   * - Mixed [B1 || B2, B3] = CYCLE (B1 happens-before B3, B2 happens-before B3)
   * - All parallel in same block [B1 || B2 || B3] = NO CYCLE (all concurrent)
   * - Different parallel blocks [B1 || B2], [B3 || B4] = CYCLE (block1 happens-before block2)
   */
  private boolean hasCycleInFlow(List<BarrierOccurrence> occurrences, Map<String, DagStageInfo> dagStageInfoMap) {
    if (occurrences.size() <= 1) {
      return false; // Single or no barrier = no cycle
    }

    // Check every pair of barriers
    for (int i = 0; i < occurrences.size(); i++) {
      for (int j = i + 1; j < occurrences.size(); j++) {
        BarrierOccurrence b1 = occurrences.get(i);
        BarrierOccurrence b2 = occurrences.get(j);

        // If any pair has a happens-before relationship, it's a cycle
        if (hasHappensBefore(b1, b2, dagStageInfoMap)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Determines if two barriers have a happens-before relationship.
   * Returns true if the barriers execute sequentially (one must complete before the other starts).
   * Returns false if the barriers are concurrent (can execute simultaneously).
   *
   * Algorithm:
   * 1. Parse execution paths into segments (e.g., "stages[0].steps[1].parallel[2]")
   * 2. Find the longest common prefix
   * 3. Compare the first differing segment:
   *    - Different parallel[X] indices → concurrent (no happens-before)
   *    - Different steps[X] indices → sequential (smaller happens-before larger)
   *    - One path is prefix of other → sequential (shorter happens-before longer)
   *
   * DAG stages are a special case: their YAML position does not reflect execution order, so if the
   * two barriers sit in different DAG stages, happens-before is resolved by dependsOn reachability
   * between those stages instead of by comparing execution path segments.
   */
  private boolean hasHappensBefore(
      BarrierOccurrence b1, BarrierOccurrence b2, Map<String, DagStageInfo> dagStageInfoMap) {
    if (b1.dagStageIdentifier != null && b2.dagStageIdentifier != null
        && !b1.dagStageIdentifier.equals(b2.dagStageIdentifier)) {
      return isDagStageAncestor(b1.dagStageIdentifier, b2.dagStageIdentifier, dagStageInfoMap)
          || isDagStageAncestor(b2.dagStageIdentifier, b1.dagStageIdentifier, dagStageInfoMap);
    }

    // Find where paths diverge
    int minLength = Math.min(b1.pathSegments.size(), b2.pathSegments.size());
    for (int i = 0; i < minLength; i++) {
      PathSegment seg1 = b1.pathSegments.get(i);
      PathSegment seg2 = b2.pathSegments.get(i);

      // If segments have different names, they diverged
      if (!seg1.name.equals(seg2.name)) {
        // Different segment names means they're in completely different branches
        // This shouldn't happen in well-formed paths, treat as sequential
        return true;
      }

      if (seg1.index != seg2.index) {
        // parallel[X] entries are concurrent with each other...
        if ("parallel".equals(seg1.name)) {
          return false;
        }
        // ...as are steps/stages directly inside a parallelObject wrapper.
        // Anything else that diverges by index (e.g. top-level steps[X]/stages[X]/group[X])
        // is sequential.
        return !(i > 0 && "parallelObject".equals(b1.pathSegments.get(i - 1).name));
      }
    }

    // One path is a prefix of the other → sequential (shorter happens-before longer)
    return b1.pathSegments.size() != b2.pathSegments.size();
  }

  /**
   * Determines whether {@code candidateAncestor} is a transitive dependency of {@code stageIdentifier},
   * i.e. whether {@code candidateAncestor} must complete before {@code stageIdentifier} starts.
   * Walks the dependsOn edges via DFS, guarding against malformed cyclic dependsOn declarations.
   */
  private boolean isDagStageAncestor(
      String stageIdentifier, String candidateAncestor, Map<String, DagStageInfo> dagStageInfoMap) {
    Set<String> visited = new HashSet<>();
    List<String> toVisit = new ArrayList<>();
    toVisit.add(stageIdentifier);

    while (!toVisit.isEmpty()) {
      String current = toVisit.remove(toVisit.size() - 1);
      if (!visited.add(current)) {
        continue;
      }

      DagStageInfo currentStageInfo = dagStageInfoMap.get(current);
      if (currentStageInfo == null || currentStageInfo.dependencies == null) {
        continue;
      }

      for (String dependency : currentStageInfo.dependencies) {
        if (dependency.equals(candidateAncestor)) {
          return true;
        }
        toVisit.add(dependency);
      }
    }

    return false;
  }

  /**
   * Represents a segment of an execution path.
   * E.g., "stages[0]" → PathSegment(name="stages", index=0)
   */
  private static class PathSegment {
    String name;
    int index;

    PathSegment(String name, int index) {
      this.name = name;
      this.index = index;
    }
  }

  /**
   * Parses an execution path into segments.
   * E.g., "stages[0].stage.spec.execution.steps[1].parallel[2]" →
   *       [PathSegment("stages", 0), PathSegment("steps", 1), PathSegment("parallel", 2)]
   */
  private List<PathSegment> parseExecutionPath(String executionPath) {
    List<PathSegment> segments = new ArrayList<>();
    if (executionPath == null || executionPath.isEmpty()) {
      return segments;
    }

    String[] parts = executionPath.split("\\.");
    for (String part : parts) {
      int bracketPos = part.indexOf('[');
      if (bracketPos > 0 && part.endsWith("]")) {
        String name = part.substring(0, bracketPos);
        String indexStr = part.substring(bracketPos + 1, part.length() - 1);
        try {
          int index = Integer.parseInt(indexStr);
          segments.add(new PathSegment(name, index));
        } catch (NumberFormatException e) {
          // Ignore non-numeric indices
        }
      } else if (part.equals(V1_PARALLEL_GROUP_FIELD)) {
        segments.add(new PathSegment("parallelObject", 0));
      } else if (part.equals(V1_GROUP_FIELD)) {
        segments.add(new PathSegment("groupObject", 0));
      }
    }

    return segments;
  }

  private String buildCycleErrorMessage(BarrierRefUsage usage) {
    boolean inNormalFlowOnly = usage.getBarrierOccurrences().stream().noneMatch(it -> it.inRollbackSteps);
    boolean inRollbackFlowOnly = usage.getBarrierOccurrences().stream().allMatch(it -> it.inRollbackSteps);

    return " Barrier Deadlock Detected: '" + usage.getBarrierRef() + "' ("
        + (inNormalFlowOnly          ? "normal flow"
                : inRollbackFlowOnly ? "rollback flow"
                                     : "mixed in normal flow and rollback flow")
        + ")\n\n"
        + "Problem:\n"
        + "Barriers with the same reference must execute concurrently (at the same time).\n"
        + "Your pipeline has barriers with '" + usage.getBarrierRef() + "' that execute sequentially,\n"
        + "creating a deadlock where each barrier waits for the others that haven't started yet.\n\n";
  }
}
