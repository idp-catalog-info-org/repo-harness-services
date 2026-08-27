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
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.pms.plan.execution.dryrun.semantic.SemanticValidationContext;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.DryRunPipelineValidationResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CloudCiDelegateConnectorRuleTest extends CategoryTest {
  private final CloudCiDelegateConnectorRule rule = new CloudCiDelegateConnectorRule();
  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  private SemanticValidationContext ctx(String yaml, Map<String, ConnectorInfoDTO> connectors) throws Exception {
    JsonNode root = mapper.readTree(yaml);
    return SemanticValidationContext.builder()
        .pipelineRoot(root)
        .referredEntities(Collections.emptyList())
        .connectorsByRef(connectors)
        .accountIdentifier("acct")
        .build();
  }

  private SemanticValidationContext v1Ctx(String yaml, Map<String, ConnectorInfoDTO> connectors) throws Exception {
    JsonNode root = mapper.readTree(yaml);
    return SemanticValidationContext.builder()
        .pipelineRoot(root)
        .referredEntities(Collections.emptyList())
        .connectorsByRef(connectors)
        .accountIdentifier("acct")
        .harnessVersion("1")
        .build();
  }

  /** CI stage whose spec contains an optional runtime block plus one docker-push step referencing dockerConn. */
  private String pipeline(String runtimeBlock) {
    return "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        type: CI\n"
        + "        spec:\n" + runtimeBlock + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: push\n"
        + "                  type: BuildAndPushDockerRegistry\n"
        + "                  spec:\n"
        + "                    connectorRef: dockerConn\n";
  }

  private ConnectorInfoDTO dockerConnector(Boolean executeOnDelegate) {
    return ConnectorInfoDTO.builder()
        .identifier("dockerConn")
        .connectorType(ConnectorType.DOCKER)
        .connectorConfig(DockerConnectorDTO.builder()
                             .dockerRegistryUrl("https://index.docker.io")
                             .executeOnDelegate(executeOnDelegate)
                             .build())
        .build();
  }

  private Map<String, ConnectorInfoDTO> connectorMap(ConnectorInfoDTO info) {
    return Collections.singletonMap("dockerConn", info);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void cloudCiDelegateConnectorIsError() throws Exception {
    // runtime absent => defaults to Cloud.
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(pipeline(""), connectorMap(dockerConnector(Boolean.TRUE))));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getSeverity()).isEqualTo("ERROR");
    assertThat(f.get(0).getEntityType()).isEqualTo("CONNECTOR");
    assertThat(f.get(0).getEntityIdentifier()).isEqualTo("dockerConn");
    assertThat(f.get(0).getErrorMessage()).contains("delegate");
    // Single usage names the one referencing step.
    assertThat(f.get(0).getErrorMessage()).contains("Referenced by 1 step: push.");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void connectorUsedByManyStepsIsReportedOnceWithAllStepIds() throws Exception {
    // Same delegate-routed connector referenced by three steps => one finding, listing all step ids.
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        type: CI\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: run_a\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    connectorRef: dockerConn\n"
        + "              - step:\n"
        + "                  identifier: run_b\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    connectorRef: dockerConn\n"
        + "              - step:\n"
        + "                  identifier: run_c\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    connectorRef: dockerConn\n";
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(yaml, connectorMap(dockerConnector(Boolean.TRUE))));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getEntityIdentifier()).isEqualTo("dockerConn");
    assertThat(f.get(0).getErrorMessage()).contains("Referenced by 3 steps: run_a, run_b, run_c.");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void delegateConnectorInsideParallelStageBlockIsError() throws Exception {
    // A CI stage nested under a `parallel` wrapper must be traversed just like a top-level stage.
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            identifier: s1\n"
        + "            type: CI\n"
        + "            spec:\n"
        + "              execution:\n"
        + "                steps:\n"
        + "                  - step:\n"
        + "                      identifier: push\n"
        + "                      type: BuildAndPushDockerRegistry\n"
        + "                      spec:\n"
        + "                        connectorRef: dockerConn\n";
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(yaml, connectorMap(dockerConnector(Boolean.TRUE))));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getEntityIdentifier()).isEqualTo("dockerConn");
    assertThat(f.get(0).getErrorMessage()).contains("Referenced by 1 step: push.");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void explicitCloudRuntimeDelegateConnectorIsError() throws Exception {
    String runtime = "          runtime:\n            type: Cloud\n";
    List<DryRunPipelineValidationResult> f =
        rule.apply(ctx(pipeline(runtime), connectorMap(dockerConnector(Boolean.TRUE))));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getEntityType()).isEqualTo("CONNECTOR");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void dockerRuntimeStageIsSkipped() throws Exception {
    String runtime = "          runtime:\n            type: Docker\n";
    assertThat(rule.apply(ctx(pipeline(runtime), connectorMap(dockerConnector(Boolean.TRUE))))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void selfHostedInfrastructureStageIsSkipped() throws Exception {
    // A CI stage with an `infrastructure` block runs on a delegate cluster, not Harness Cloud, so a
    // delegate-routed connector is valid and must not be flagged.
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        type: CI\n"
        + "        spec:\n"
        + "          infrastructure:\n"
        + "            type: KubernetesDirect\n"
        + "            spec:\n"
        + "              connectorRef: k8sConn\n"
        + "              namespace: harness-delegate-ng\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: run\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    connectorRef: dockerConn\n";
    assertThat(rule.apply(ctx(yaml, connectorMap(dockerConnector(Boolean.TRUE))))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void executeOnDelegateFalseIsNoFinding() throws Exception {
    assertThat(rule.apply(ctx(pipeline(""), connectorMap(dockerConnector(Boolean.FALSE))))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void executeOnDelegateNullDefaultsToDelegateIsError() throws Exception {
    // null executeOnDelegate => connector default is delegate-routed => ERROR.
    List<DryRunPipelineValidationResult> f = rule.apply(ctx(pipeline(""), connectorMap(dockerConnector(null))));
    assertThat(f).hasSize(1);
    assertThat(f.get(0).getEntityType()).isEqualTo("CONNECTOR");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void runtimeTypeRuntimeExpressionIsSkipped() throws Exception {
    String runtime = "          runtime:\n            type: <+input>\n";
    assertThat(rule.apply(ctx(pipeline(runtime), connectorMap(dockerConnector(Boolean.TRUE))))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void connectorAbsentFromMapIsSkipped() throws Exception {
    // Rule 1 flags missing connectors; this rule only reasons over connectors it can resolve.
    assertThat(rule.apply(ctx(pipeline(""), Collections.emptyMap()))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void nonManagerExecutableConfigIsSkipped() throws Exception {
    ConnectorInfoDTO k8s = ConnectorInfoDTO.builder()
                               .identifier("dockerConn")
                               .connectorType(ConnectorType.KUBERNETES_CLUSTER)
                               .connectorConfig(KubernetesClusterConfigDTO.builder().build())
                               .build();
    assertThat(rule.apply(ctx(pipeline(""), connectorMap(k8s)))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1CloudStageWithDelegateConnectorIsFlagged() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      runtime: cloud\n"
        + "      steps:\n"
        + "        - id: push\n"
        + "          run:\n"
        + "            container:\n"
        + "              connector: account.delegateDocker\n";
    ConnectorInfoDTO info = v1DelegateDockerConnector();

    List<DryRunPipelineValidationResult> findings = rule.apply(v1Ctx(yaml, Map.of("account.delegateDocker", info)));

    assertThat(findings).anyMatch(r -> "account.delegateDocker".equals(r.getEntityIdentifier()));
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1SelfHostedVmStageIsSkipped() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      runtime:\n"
        + "        vm:\n"
        + "          pool: linux\n"
        + "      steps:\n"
        + "        - id: push\n"
        + "          run:\n"
        + "            container:\n"
        + "              connector: account.delegateDocker\n";
    ConnectorInfoDTO info = v1DelegateDockerConnector();

    assertThat(rule.apply(v1Ctx(yaml, Map.of("account.delegateDocker", info)))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1MissingRuntimeDefaultsToCloudAndIsFlagged() throws Exception {
    String yaml = "pipeline:\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      steps:\n"
        + "        - id: push\n"
        + "          run:\n"
        + "            container:\n"
        + "              connector: account.delegateDocker\n";
    ConnectorInfoDTO info = v1DelegateDockerConnector();

    List<DryRunPipelineValidationResult> findings = rule.apply(v1Ctx(yaml, Map.of("account.delegateDocker", info)));

    assertThat(findings).anyMatch(r -> "account.delegateDocker".equals(r.getEntityIdentifier()));
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1CloudPipelineCloneDelegateConnectorIsError() throws Exception {
    // Pipeline-level clone alone must be checked on Cloud (getGitClone inherits it onto the stage).
    String yaml = "pipeline:\n"
        + "  clone:\n"
        + "    enabled: true\n"
        + "    connector: account.delegateDocker\n"
        + "    ref:\n"
        + "      type: branch\n"
        + "      name: main\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      runtime: cloud\n"
        + "      steps:\n"
        + "        - id: run\n"
        + "          run:\n"
        + "            script: echo ok\n";
    ConnectorInfoDTO info = v1DelegateDockerConnector();

    List<DryRunPipelineValidationResult> findings = rule.apply(v1Ctx(yaml, Map.of("account.delegateDocker", info)));

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).getEntityIdentifier()).isEqualTo("account.delegateDocker");
    assertThat(findings.get(0).getErrorMessage()).contains("delegate");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1CloudStageInheritsPipelineCloneConnectorIsError() throws Exception {
    // Stage clone enabled with repo only; connector blank => inherited from pipeline.clone.
    String yaml = "pipeline:\n"
        + "  clone:\n"
        + "    enabled: true\n"
        + "    connector: account.delegateDocker\n"
        + "    ref:\n"
        + "      type: branch\n"
        + "      name: main\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      runtime: cloud\n"
        + "      clone:\n"
        + "        enabled: true\n"
        + "        repo: org/repo\n"
        + "      steps:\n"
        + "        - id: run\n"
        + "          run:\n"
        + "            script: echo ok\n";
    ConnectorInfoDTO info = v1DelegateDockerConnector();

    List<DryRunPipelineValidationResult> findings = rule.apply(v1Ctx(yaml, Map.of("account.delegateDocker", info)));

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).getEntityIdentifier()).isEqualTo("account.delegateDocker");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1CloudStageCloneEnabledFalseSkipsPipelineCloneConnector() throws Exception {
    String yaml = "pipeline:\n"
        + "  clone:\n"
        + "    enabled: true\n"
        + "    connector: account.delegateDocker\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      runtime: cloud\n"
        + "      clone:\n"
        + "        enabled: false\n"
        + "      steps:\n"
        + "        - id: run\n"
        + "          run:\n"
        + "            script: echo ok\n";
    ConnectorInfoDTO info = v1DelegateDockerConnector();

    assertThat(rule.apply(v1Ctx(yaml, Map.of("account.delegateDocker", info)))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1K8sRuntimeSkipsEffectivePipelineCloneConnector() throws Exception {
    String yaml = "pipeline:\n"
        + "  clone:\n"
        + "    enabled: true\n"
        + "    connector: account.delegateDocker\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      runtime:\n"
        + "        kubernetes:\n"
        + "          connector: account.k8s\n"
        + "      clone:\n"
        + "        enabled: true\n"
        + "        repo: org/repo\n"
        + "      steps:\n"
        + "        - id: run\n"
        + "          run:\n"
        + "            script: echo ok\n";
    ConnectorInfoDTO info = v1DelegateDockerConnector();

    assertThat(rule.apply(v1Ctx(yaml, Map.of("account.delegateDocker", info)))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v1CloudStageCloneConnectorOverridesPipelineClone() throws Exception {
    // Stage connector wins; pipeline delegate connector must not be flagged when stage uses platform.
    String yaml = "pipeline:\n"
        + "  clone:\n"
        + "    enabled: true\n"
        + "    connector: account.delegateDocker\n"
        + "  stages:\n"
        + "    - id: build\n"
        + "      runtime: cloud\n"
        + "      clone:\n"
        + "        enabled: true\n"
        + "        connector: account.platformDocker\n"
        + "        repo: org/repo\n"
        + "      steps:\n"
        + "        - id: run\n"
        + "          run:\n"
        + "            script: echo ok\n";
    ConnectorInfoDTO delegate = v1DelegateDockerConnector();
    ConnectorInfoDTO platform = ConnectorInfoDTO.builder()
                                    .identifier("platformDocker")
                                    .accountIdentifier("acct")
                                    .connectorType(ConnectorType.DOCKER)
                                    .connectorConfig(DockerConnectorDTO.builder()
                                                         .dockerRegistryUrl("https://index.docker.io")
                                                         .executeOnDelegate(Boolean.FALSE)
                                                         .build())
                                    .build();

    List<DryRunPipelineValidationResult> findings =
        rule.apply(v1Ctx(yaml, Map.of("account.delegateDocker", delegate, "account.platformDocker", platform)));

    assertThat(findings).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v0CloudCloneCodebaseWithDelegateCodebaseConnectorIsError() throws Exception {
    // Aligns dry-run with hosted-infra execution: codebase git connector must not be delegate-routed.
    String yaml = "pipeline:\n"
        + "  properties:\n"
        + "    ci:\n"
        + "      codebase:\n"
        + "        connectorRef: gitConn\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        type: CI\n"
        + "        spec:\n"
        + "          cloneCodebase: true\n"
        + "          runtime:\n"
        + "            type: Cloud\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: run\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    connectorRef: dockerOk\n"
        + "                    image: busybox\n"
        + "                    command: echo ok\n";
    ConnectorInfoDTO git = ConnectorInfoDTO.builder()
                               .identifier("gitConn")
                               .connectorType(ConnectorType.DOCKER)
                               .connectorConfig(DockerConnectorDTO.builder()
                                                    .dockerRegistryUrl("https://index.docker.io")
                                                    .executeOnDelegate(Boolean.TRUE)
                                                    .build())
                               .build();
    ConnectorInfoDTO dockerOk = ConnectorInfoDTO.builder()
                                    .identifier("dockerOk")
                                    .connectorType(ConnectorType.DOCKER)
                                    .connectorConfig(DockerConnectorDTO.builder()
                                                         .dockerRegistryUrl("https://index.docker.io")
                                                         .executeOnDelegate(Boolean.FALSE)
                                                         .build())
                                    .build();

    List<DryRunPipelineValidationResult> f = rule.apply(ctx(yaml, Map.of("gitConn", git, "dockerOk", dockerOk)));

    assertThat(f).hasSize(1);
    assertThat(f.get(0).getEntityIdentifier()).isEqualTo("gitConn");
    assertThat(f.get(0).getErrorMessage()).contains("delegate");
    // Pipeline-level codebase has no step id in the message.
    assertThat(f.get(0).getErrorMessage()).doesNotContain("Referenced by");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v0CloudCloneCodebaseFalseSkipsCodebaseConnector() throws Exception {
    String yaml = "pipeline:\n"
        + "  properties:\n"
        + "    ci:\n"
        + "      codebase:\n"
        + "        connectorRef: gitConn\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        type: CI\n"
        + "        spec:\n"
        + "          cloneCodebase: false\n"
        + "          runtime:\n"
        + "            type: Cloud\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: run\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    connectorRef: dockerOk\n"
        + "                    image: busybox\n"
        + "                    command: echo ok\n";
    ConnectorInfoDTO git = ConnectorInfoDTO.builder()
                               .identifier("gitConn")
                               .connectorType(ConnectorType.DOCKER)
                               .connectorConfig(DockerConnectorDTO.builder()
                                                    .dockerRegistryUrl("https://index.docker.io")
                                                    .executeOnDelegate(Boolean.TRUE)
                                                    .build())
                               .build();
    ConnectorInfoDTO dockerOk = ConnectorInfoDTO.builder()
                                    .identifier("dockerOk")
                                    .connectorType(ConnectorType.DOCKER)
                                    .connectorConfig(DockerConnectorDTO.builder()
                                                         .dockerRegistryUrl("https://index.docker.io")
                                                         .executeOnDelegate(Boolean.FALSE)
                                                         .build())
                                    .build();

    assertThat(rule.apply(ctx(yaml, Map.of("gitConn", git, "dockerOk", dockerOk)))).isEmpty();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void v0K8sInfraSkipsCodebaseEvenWhenCloneEnabled() throws Exception {
    String yaml = "pipeline:\n"
        + "  properties:\n"
        + "    ci:\n"
        + "      codebase:\n"
        + "        connectorRef: gitConn\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        identifier: s1\n"
        + "        type: CI\n"
        + "        spec:\n"
        + "          cloneCodebase: true\n"
        + "          infrastructure:\n"
        + "            type: KubernetesDirect\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  identifier: run\n"
        + "                  type: Run\n"
        + "                  spec:\n"
        + "                    connectorRef: dockerOk\n";
    ConnectorInfoDTO git = ConnectorInfoDTO.builder()
                               .identifier("gitConn")
                               .connectorType(ConnectorType.DOCKER)
                               .connectorConfig(DockerConnectorDTO.builder()
                                                    .dockerRegistryUrl("https://index.docker.io")
                                                    .executeOnDelegate(Boolean.TRUE)
                                                    .build())
                               .build();
    ConnectorInfoDTO dockerOk = ConnectorInfoDTO.builder()
                                    .identifier("dockerOk")
                                    .connectorType(ConnectorType.DOCKER)
                                    .connectorConfig(DockerConnectorDTO.builder()
                                                         .dockerRegistryUrl("https://index.docker.io")
                                                         .executeOnDelegate(Boolean.TRUE)
                                                         .build())
                                    .build();

    assertThat(rule.apply(ctx(yaml, Map.of("gitConn", git, "dockerOk", dockerOk)))).isEmpty();
  }

  private ConnectorInfoDTO v1DelegateDockerConnector() {
    return ConnectorInfoDTO.builder()
        .identifier("delegateDocker")
        .accountIdentifier("acct")
        .connectorType(ConnectorType.DOCKER)
        .connectorConfig(DockerConnectorDTO.builder()
                             .dockerRegistryUrl("https://index.docker.io")
                             .executeOnDelegate(Boolean.TRUE)
                             .build())
        .build();
  }
}
