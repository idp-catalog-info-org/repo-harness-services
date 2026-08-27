/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic.rules;

import static io.harness.rule.OwnerRule.FJUNIOR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidationContext;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CloneCodebaseSanityRuleTest extends CategoryTest {
  private final CloneCodebaseSanityRule rule = new CloneCodebaseSanityRule();
  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  private SemanticValidationContext ctx(String yaml) throws Exception {
    JsonNode root = mapper.readTree(yaml);
    return SemanticValidationContext.builder()
        .pipelineRoot(root)
        .referredEntities(Collections.emptyList())
        .connectorsByRef(Collections.emptyMap())
        .accountIdentifier("acct")
        .build();
  }

  private SemanticValidationContext v1Ctx(String yaml) throws Exception {
    JsonNode root = mapper.readTree(yaml);
    return SemanticValidationContext.builder()
        .pipelineRoot(root)
        .referredEntities(Collections.emptyList())
        .connectorsByRef(Collections.emptyMap())
        .accountIdentifier("acct")
        .harnessVersion("1")
        .build();
  }

  /** Full pipeline with an optional properties/codebase block and one stage. */
  private String pipeline(String propertiesBlock, String stageType, String specBlock) {
    return "pipeline:\n" + propertiesBlock + "  stages:\n    - stage:\n        identifier: s1\n"
        + "        type: " + stageType + "\n        spec:\n" + specBlock;
  }

  private String codebase(String connectorRefLine, String repoNameLine, String buildLine) {
    return "  properties:\n    ci:\n      codebase:\n" + connectorRefLine + repoNameLine + buildLine;
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloneHarnessCodeWithRepoNameIsValid() throws Exception {
    // Harness Code codebase: empty connectorRef + a repoName is a valid clone source.
    String yaml =
        pipeline(codebase("", "        repoName: myrepo\n", ""), "Integration", "          cloneCodebase: true\n");
    assertThat(rule.apply(ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloneHarnessCodeWithoutRepoNameIsError() throws Exception {
    // Harness Code codebase (empty connectorRef) with no repoName has no usable clone source.
    String yaml = pipeline(codebase("", "", ""), "Integration", "          cloneCodebase: true\n");
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(yaml));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getSeverity()).isEqualTo("ERROR");
    assertThat(f.get(0).getEntityType()).isEqualTo("CODEBASE");
    assertThat(f.get(0).getEntityIdentifier()).isEqualTo("s1");
    assertThat(f.get(0).getErrorMessage()).contains("repoName");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloneHarnessLiteralRefDefersToOtherRules() throws Exception {
    // 'harness' is no longer a sentinel -- it is a real connector ref, so existence/type is left to
    // Rules 1-2 and this rule emits nothing even without a repoName.
    String yaml =
        pipeline(codebase("        connectorRef: harness\n", "", ""), "Integration", "          cloneCodebase: true\n");
    assertThat(rule.apply(ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloneWithValidGitConnectorDefersToOtherRules() throws Exception {
    String yaml = pipeline(
        codebase("        connectorRef: account.gh\n", "", ""), "Integration", "          cloneCodebase: true\n");
    assertThat(rule.apply(ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloneWithNoCodebaseBlockIsError() throws Exception {
    String yaml = pipeline("", "Integration", "          cloneCodebase: true\n");
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(yaml));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getSeverity()).isEqualTo("ERROR");
    assertThat(f.get(0).getEntityType()).isEqualTo("CODEBASE");
    assertThat(f.get(0).getEntityIdentifier()).isEqualTo("s1");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloneCodebaseAbsentIsNoFinding() throws Exception {
    String yaml = pipeline(codebase("        connectorRef: harness\n", "", ""), "Integration", "          foo: bar\n");
    assertThat(rule.apply(ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloneCodebaseFalseIsNoFinding() throws Exception {
    String yaml = pipeline(
        codebase("        connectorRef: harness\n", "", ""), "Integration", "          cloneCodebase: false\n");
    assertThat(rule.apply(ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void codebaseBuildRuntimeExpressionStillValidatesSource() throws Exception {
    // build (<+input>) is the clone *target* and a webhook may supply it at runtime; it must NOT
    // suppress validation of the clone *source*. Empty ref + no repoName is still broken.
    String yaml =
        pipeline(codebase("", "", "        build: <+input>\n"), "Integration", "          cloneCodebase: true\n");
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(yaml));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getSeverity()).isEqualTo("ERROR");
    assertThat(f.get(0).getEntityType()).isEqualTo("CODEBASE");
    assertThat(f.get(0).getErrorMessage()).contains("repoName");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void connectorRefRuntimeExpressionIsSkipped() throws Exception {
    // A runtime connectorRef may resolve to a real connector; cannot demand repoName here.
    String yaml = pipeline(codebase("        connectorRef: <+input>\n", "", "        build: <+input>\n"), "Integration",
        "          cloneCodebase: true\n");
    assertThat(rule.apply(ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void harnessCodeRepoNameRuntimeExpressionIsSkipped() throws Exception {
    // repoName supplied at runtime is a present (non-blank) value, so the Harness Code source is configured.
    String yaml = pipeline(codebase("", "        repoName: <+input>\n", "        build: <+input>\n"), "Integration",
        "          cloneCodebase: true\n");
    assertThat(rule.apply(ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void securityTestsStageCloneWithoutRepoNameIsError() throws Exception {
    String yaml = pipeline(codebase("", "", ""), "SecurityTests", "          cloneCodebase: true\n");
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(yaml));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getSeverity()).isEqualTo("ERROR");
    assertThat(f.get(0).getEntityType()).isEqualTo("CODEBASE");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloneRuntimeExpressionIsSkipped() throws Exception {
    String yaml = pipeline(
        codebase("        connectorRef: harness\n", "", ""), "Integration", "          cloneCodebase: <+input>\n");
    assertThat(rule.apply(ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloneStageInsideParallelBlockIsChecked() throws Exception {
    // A CI stage nested under a `parallel` wrapper must be traversed just like a top-level stage.
    // No codebase block + cloneCodebase true => the nested stage must still be flagged.
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            identifier: s1\n"
        + "            type: Integration\n"
        + "            spec:\n"
        + "              cloneCodebase: true\n";
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(yaml));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getSeverity()).isEqualTo("ERROR");
    assertThat(f.get(0).getEntityType()).isEqualTo("CODEBASE");
    assertThat(f.get(0).getEntityIdentifier()).isEqualTo("s1");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1CloneEnabledWithoutConnectorOrRepoIsFlagged() throws Exception {
    String yaml = "pipeline:\n  stages:\n    - id: build\n      clone:\n        enabled: true\n";

    List<DryRunPipelineValidationResult> findings = rule.apply(v1Ctx(yaml));

    assertThat(findings).anyMatch(r -> "CODEBASE".equals(r.getEntityType()) && "build".equals(r.getEntityIdentifier()));
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1CloneEnabledWithRepoIsValid() throws Exception {
    String yaml = "pipeline:\n  stages:\n    - id: build\n      clone:\n        enabled: true\n        repo: my-repo\n";

    assertThat(rule.apply(v1Ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1CloneEnabledWithOptionsRepositoryIsValid() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      clone:\n"
        + "        enabled: true\n"
        + "      options:\n"
        + "        repository:\n"
        + "          connector: account.gitConn\n"
        + "          name: my-repo\n";

    assertThat(rule.apply(v1Ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1CloneEnabledWithPipelineRepoShorthandIsValid() throws Exception {
    String yaml = "pipeline:\n"
        + "  repo:\n"
        + "    connector: account.gitConn\n"
        + "    name: my-repo\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      clone:\n"
        + "        enabled: true\n";

    assertThat(rule.apply(v1Ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1CloneDisabledIsSkipped() throws Exception {
    String yaml = "pipeline:\n  stages:\n    - id: build\n      clone:\n        enabled: false\n";

    assertThat(rule.apply(v1Ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1PipelineCloneWithoutSourceIsFlagged() throws Exception {
    // Plan creation inherits pipeline.clone when the stage has no clone; omitted enabled => active.
    String yaml = "pipeline:\n"
        + "  clone: {}\n"
        + "  stages:\n"
        + "    - id: build\n";

    List<DryRunPipelineValidationResult> findings = rule.apply(v1Ctx(yaml));

    assertThat(findings).anyMatch(r -> "CODEBASE".equals(r.getEntityType()) && "build".equals(r.getEntityIdentifier()));
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1StageCloneWithOmittedEnabledWithoutSourceIsFlagged() throws Exception {
    String yaml = "pipeline:\n  stages:\n    - id: build\n      clone: {}\n";

    List<DryRunPipelineValidationResult> findings = rule.apply(v1Ctx(yaml));

    assertThat(findings).anyMatch(r -> "CODEBASE".equals(r.getEntityType()) && "build".equals(r.getEntityIdentifier()));
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1StageCloneInheritsPipelineCloneConnector() throws Exception {
    String yaml = "pipeline:\n"
        + "  clone:\n"
        + "    connector: account.gitConn\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      clone: {}\n";

    assertThat(rule.apply(v1Ctx(yaml))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1PipelineCloneExplicitlyDisabledIsSkipped() throws Exception {
    String yaml = "pipeline:\n"
        + "  clone:\n"
        + "    enabled: false\n"
        + "  stages:\n"
        + "    - id: build\n";

    assertThat(rule.apply(v1Ctx(yaml))).isEmpty();
  }
}
