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
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidationContext;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConnectorTypeRuleTest extends CategoryTest {
  private final ConnectorTypeRule rule = new ConnectorTypeRule();
  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  private SemanticValidationContext ctx(String yaml, Map<String, ConnectorType> typesByRef) throws Exception {
    JsonNode root = mapper.readTree(yaml);
    Map<String, ConnectorInfoDTO> byRef = new HashMap<>();
    typesByRef.forEach(
        (ref, type) -> byRef.put(ref, ConnectorInfoDTO.builder().identifier(ref).connectorType(type).build()));
    return SemanticValidationContext.builder()
        .pipelineRoot(root)
        .referredEntities(Collections.emptyList())
        .connectorsByRef(byRef)
        .accountIdentifier("acct")
        .build();
  }

  private SemanticValidationContext v1Ctx(String yaml, Map<String, ConnectorType> typesByRef) throws Exception {
    JsonNode root = mapper.readTree(yaml);
    Map<String, ConnectorInfoDTO> byRef = new HashMap<>();
    typesByRef.forEach(
        (ref, type) -> byRef.put(ref, ConnectorInfoDTO.builder().identifier(ref).connectorType(type).build()));
    return SemanticValidationContext.builder()
        .pipelineRoot(root)
        .referredEntities(Collections.emptyList())
        .connectorsByRef(byRef)
        .accountIdentifier("acct")
        .harnessVersion("1")
        .build();
  }

  private String codebaseYaml(String connectorRef) {
    return "pipeline:\n  properties:\n    ci:\n      codebase:\n        connectorRef: " + connectorRef + "\n";
  }

  private String pushStepYaml(String type, String connectorRef) {
    return "pipeline:\n  stages:\n    - stage:\n        spec:\n          execution:\n            steps:\n"
        + "              - step:\n                  type: " + type + "\n                  spec:\n"
        + "                    connectorRef: " + connectorRef + "\n";
  }

  private String infraYaml(String type, String connectorRef) {
    return "pipeline:\n  stages:\n    - stage:\n        spec:\n          infrastructure:\n"
        + "            infrastructureDefinition:\n              type: " + type + "\n              spec:\n"
        + "                connectorRef: " + connectorRef + "\n";
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void codebaseGithubIsValid() throws Exception {
    List<DryRunPipelineValidationResult> f =
        rule.apply(ctx(codebaseYaml("account.gh"), Map.of("account.gh", ConnectorType.GITHUB)));
    assertThat(f).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void codebaseDockerIsError() throws Exception {
    List<DryRunPipelineValidationResult> f =
        rule.apply(ctx(codebaseYaml("account.dk"), Map.of("account.dk", ConnectorType.DOCKER)));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getSeverity()).isEqualTo("ERROR");
    assertThat(f.get(0).getEntityIdentifier()).isEqualTo("account.dk");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void codebaseEmptyRefHarnessCodeSkipped() throws Exception {
    // Empty codebase connectorRef is the Harness Code codebase; there is no connector type to check.
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(codebaseYaml("\"\""), Collections.emptyMap()));
    assertThat(f).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void pushStepDockerIsValid() throws Exception {
    List<DryRunPipelineValidationResult> f = rule.apply(
        ctx(pushStepYaml("BuildAndPushDockerRegistry", "account.dk"), Map.of("account.dk", ConnectorType.DOCKER)));
    assertThat(f).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void pushStepGithubIsError() throws Exception {
    List<DryRunPipelineValidationResult> f = rule.apply(
        ctx(pushStepYaml("BuildAndPushDockerRegistry", "account.gh"), Map.of("account.gh", ConnectorType.GITHUB)));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getSeverity()).isEqualTo("ERROR");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void infraK8sIsValid() throws Exception {
    List<DryRunPipelineValidationResult> f = rule.apply(
        ctx(infraYaml("KubernetesDirect", "account.k8s"), Map.of("account.k8s", ConnectorType.KUBERNETES_CLUSTER)));
    assertThat(f).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void infraGithubIsError() throws Exception {
    List<DryRunPipelineValidationResult> f =
        rule.apply(ctx(infraYaml("KubernetesDirect", "account.gh"), Map.of("account.gh", ConnectorType.GITHUB)));
    assertThat(f).hasSize(1);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void runtimeExpressionRefSkipped() throws Exception {
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(codebaseYaml("<+input>"), Collections.emptyMap()));
    assertThat(f).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void refAbsentFromMapSkipped() throws Exception {
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(codebaseYaml("account.unknown"), Collections.emptyMap()));
    assertThat(f).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1DockerInGitCloneSlotIsFlagged() throws Exception {
    String yaml = "pipeline:\n  stages:\n    - id: s\n      clone:\n        connector: account.dockerAsGit\n";

    List<DryRunPipelineValidationResult> findings =
        rule.apply(v1Ctx(yaml, Map.of("account.dockerAsGit", ConnectorType.DOCKER)));

    assertThat(findings).anyMatch(r -> "account.dockerAsGit".equals(r.getEntityIdentifier()));
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1DockerInRegistryRefSlotIsAccepted() throws Exception {
    String yaml = "pipeline:\n  stages:\n    - id: s\n      run:\n        container:\n          registryRef: "
        + "account.dockerConn\n";

    List<DryRunPipelineValidationResult> findings =
        rule.apply(v1Ctx(yaml, Map.of("account.dockerConn", ConnectorType.DOCKER)));

    assertThat(findings).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1DockerInPipelineRepoConnectorIsFlagged() throws Exception {
    String yaml = "pipeline:\n"
        + "  repo:\n"
        + "    connector: account.dockerAsGit\n"
        + "    name: my-repo\n"
        + "  stages:\n"
        + "    - id: s\n";

    List<DryRunPipelineValidationResult> findings =
        rule.apply(v1Ctx(yaml, Map.of("account.dockerAsGit", ConnectorType.DOCKER)));

    assertThat(findings).anyMatch(r -> "account.dockerAsGit".equals(r.getEntityIdentifier()));
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1GithubInPipelineRepoConnectorIsAccepted() throws Exception {
    String yaml = "pipeline:\n"
        + "  repo:\n"
        + "    connector: account.gh\n"
        + "    name: my-repo\n"
        + "  stages:\n"
        + "    - id: s\n";

    List<DryRunPipelineValidationResult> findings = rule.apply(v1Ctx(yaml, Map.of("account.gh", ConnectorType.GITHUB)));

    assertThat(findings).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v0RunStepGithubConnectorIsFlagged() throws Exception {
    List<DryRunPipelineValidationResult> findings =
        rule.apply(ctx(pushStepYaml("Run", "account.gh"), Map.of("account.gh", ConnectorType.GITHUB)));

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).getEntityIdentifier()).isEqualTo("account.gh");
    assertThat(findings.get(0).getErrorMessage()).contains("image registry");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v0RunStepDockerConnectorIsAccepted() throws Exception {
    assertThat(rule.apply(ctx(pushStepYaml("Run", "account.dk"), Map.of("account.dk", ConnectorType.DOCKER))))
        .isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v0RunStepGcpAwsAzureConnectorsAreAccepted() throws Exception {
    assertThat(rule.apply(ctx(pushStepYaml("Run", "account.gcp"), Map.of("account.gcp", ConnectorType.GCP)))).isEmpty();
    assertThat(rule.apply(ctx(pushStepYaml("Run", "account.aws"), Map.of("account.aws", ConnectorType.AWS)))).isEmpty();
    assertThat(rule.apply(ctx(pushStepYaml("Run", "account.az"), Map.of("account.az", ConnectorType.AZURE)))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1ContainerConnectorGithubIsFlagged() throws Exception {
    String yaml = "pipeline:\n  stages:\n    - id: s\n      run:\n        container:\n"
        + "          connector: account.gh\n          image: busybox\n";

    List<DryRunPipelineValidationResult> findings = rule.apply(v1Ctx(yaml, Map.of("account.gh", ConnectorType.GITHUB)));

    assertThat(findings).anyMatch(r -> "account.gh".equals(r.getEntityIdentifier()));
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1ContainerConnectorImagePullFamilyIsAccepted() throws Exception {
    String yamlTpl = "pipeline:\n  stages:\n    - id: s\n      run:\n        container:\n"
        + "          connector: %s\n          image: busybox\n";

    assertThat(rule.apply(v1Ctx(String.format(yamlTpl, "account.dk"), Map.of("account.dk", ConnectorType.DOCKER))))
        .isEmpty();
    assertThat(rule.apply(v1Ctx(String.format(yamlTpl, "account.gcp"), Map.of("account.gcp", ConnectorType.GCP))))
        .isEmpty();
    assertThat(rule.apply(v1Ctx(String.format(yamlTpl, "account.aws"), Map.of("account.aws", ConnectorType.AWS))))
        .isEmpty();
    assertThat(rule.apply(v1Ctx(String.format(yamlTpl, "account.az"), Map.of("account.az", ConnectorType.AZURE))))
        .isEmpty();
  }
}
