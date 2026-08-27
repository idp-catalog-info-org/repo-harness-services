/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.aiagent.imports;

import static io.harness.rule.OwnerRule.AYUSHMAN;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.aiagent.AgentDescriptor;
import io.harness.delegate.task.aiagent.AgentPlatform;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.aiagent.dto.AgentConfigVariableDTO;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AiAgentServiceYamlSynthesizerTest extends CategoryTest {
  private AiAgentServiceYamlSynthesizer synthesizer;

  @Before
  public void setUp() {
    synthesizer = new AiAgentServiceYamlSynthesizer();
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void synthesisRejectsConfigVariableCollidingWithReconcilePinKey() {
    AgentDescriptor d = AgentDescriptor.builder()
                            .name("support-agent")
                            .image("acct.dkr.ecr/x:1")
                            .identity("arn:aws:iam::1:role/AC")
                            .reconcilePinKey("agentName")
                            .reconcilePinValue("support-agent")
                            .configVariables(Map.of("agentName", "support-agent"))
                            .build();

    assertThatThrownBy(() -> synthesizer.synthesize(d, AgentPlatform.AWS_AGENT_CORE, "support_agent", "Support Agent"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("agentName")
        .hasMessageContaining("collides");

    assertThatThrownBy(() -> synthesizer.configVariablesFor(d))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("agentName")
        .hasMessageContaining("collides");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void awsSynthesisPinsAgentNameAndSetsExecutionRoleArn() {
    AgentDescriptor d = AgentDescriptor.builder()
                            .name("support-agent")
                            .image("acct.dkr.ecr/x:1")
                            .identity("arn:aws:iam::1:role/AC")
                            .reconcilePinKey("agentName")
                            .reconcilePinValue("support-agent")
                            .configVariables(Map.of("protocol", "HTTP"))
                            .build();
    String yaml = synthesizer.synthesize(d, AgentPlatform.AWS_AGENT_CORE, "support_agent", "Support Agent");
    assertThat(yaml)
        .contains("type: AwsAgentCore")
        .contains("executionRoleArn")
        .contains("agentName")
        .contains("support-agent");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void gcpSynthesisPinsReasoningEngineAndNoExecutionRoleArn() {
    AgentDescriptor d = AgentDescriptor.builder()
                            .name("support")
                            .image("us-docker.pkg.dev/x:1")
                            .identity("sa@proj.iam.gserviceaccount.com")
                            .reconcilePinKey("reasoningEngine")
                            .reconcilePinValue("projects/p/locations/us/reasoningEngines/1")
                            .configVariables(Map.of("agentName", "Original Display Name"))
                            .build();
    String yaml = synthesizer.synthesize(d, AgentPlatform.GOOGLE_AGENT_RUNTIME, "support", "Support");
    assertThat(yaml)
        .contains("type: GoogleAgentRuntime")
        .contains("reasoningEngine")
        // The imported engine's display name rides through as an agentName config
        // var so redeploys preserve it instead of renaming to the service name.
        .contains("agentName")
        .contains("Original Display Name")
        .doesNotContain("executionRoleArn");
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void synthesisSetsNativeServiceDescriptionFromDescriptor() {
    AgentDescriptor aws = AgentDescriptor.builder()
                              .name("support-agent")
                              .image("acct.dkr.ecr/x:1")
                              .identity("arn:aws:iam::1:role/AC")
                              .description("Imported from AWS Bedrock AgentCore")
                              .reconcilePinKey("agentName")
                              .reconcilePinValue("support-agent")
                              .build();
    String awsYaml = synthesizer.synthesize(aws, AgentPlatform.AWS_AGENT_CORE, "support_agent", "Support Agent");
    assertThat(awsYaml).contains("description: Imported from AWS Bedrock AgentCore");

    AgentDescriptor gcp = AgentDescriptor.builder()
                              .name("support")
                              .image("us-docker.pkg.dev/x:1")
                              .identity("sa@proj.iam.gserviceaccount.com")
                              .description("Imported from Vertex Agent Runtime")
                              .reconcilePinKey("reasoningEngine")
                              .reconcilePinValue("projects/p/locations/us/reasoningEngines/1")
                              .build();
    String gcpYaml = synthesizer.synthesize(gcp, AgentPlatform.GOOGLE_AGENT_RUNTIME, "support", "Support");
    assertThat(gcpYaml).contains("description: Imported from Vertex Agent Runtime");
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void descriptionNeverSurfacesAsAConfigVariable() {
    AgentDescriptor d = AgentDescriptor.builder()
                            .name("support-agent")
                            .image("acct.dkr.ecr/x:1")
                            .identity("arn:aws:iam::1:role/AC")
                            .description("Imported from AWS Bedrock AgentCore")
                            .reconcilePinKey("agentName")
                            .reconcilePinValue("support-agent")
                            .build();

    List<AgentConfigVariableDTO> vars = synthesizer.configVariablesFor(d);
    assertThat(vars.stream().map(AgentConfigVariableDTO::getName)).doesNotContain("description");
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void synthesisOmitsDescriptionWhenNotDiscovered() {
    AgentDescriptor d = AgentDescriptor.builder()
                            .name("support-agent")
                            .image("acct.dkr.ecr/x:1")
                            .identity("arn:aws:iam::1:role/AC")
                            .reconcilePinKey("agentName")
                            .reconcilePinValue("support-agent")
                            .build();
    String yaml = synthesizer.synthesize(d, AgentPlatform.AWS_AGENT_CORE, "support_agent", "Support Agent");
    assertThat(yaml).doesNotContain("description:");
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void awsSynthesisWiresLifecycleAndTagsIntoConfigVariables() {
    AgentDescriptor d = AgentDescriptor.builder()
                            .name("support-agent")
                            .image("acct.dkr.ecr/x:1")
                            .identity("arn:aws:iam::1:role/AC")
                            .reconcilePinKey("agentName")
                            .reconcilePinValue("support-agent")
                            .protocol("HTTP")
                            .idleTimeoutSeconds(600)
                            .maxLifetimeSeconds(3600)
                            .tags("env=prod,team=cd")
                            .build();

    List<AgentConfigVariableDTO> vars = synthesizer.configVariablesFor(d);
    assertThat(vars.stream().map(AgentConfigVariableDTO::getName))
        .contains("agentName", "protocol", "idleSessionTimeout", "maxLifetime", "tags");
    assertThat(vars.stream().filter(v -> v.getName().equals("idleSessionTimeout")).findFirst().get().getValue())
        .isEqualTo("600");
    assertThat(vars.stream().filter(v -> v.getName().equals("maxLifetime")).findFirst().get().getValue())
        .isEqualTo("3600");
    assertThat(vars.stream().filter(v -> v.getName().equals("tags")).findFirst().get().getValue())
        .isEqualTo("env=prod,team=cd");

    String yaml = synthesizer.synthesize(d, AgentPlatform.AWS_AGENT_CORE, "support_agent", "Support Agent");
    assertThat(yaml).contains("idleSessionTimeout").contains("maxLifetime").contains("env=prod,team=cd");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void configVariablesForIncludesReconcilePinAndConfig() {
    AgentDescriptor d = AgentDescriptor.builder()
                            .reconcilePinKey("agentName")
                            .reconcilePinValue("support-agent")
                            .configVariables(Map.of("protocol", "HTTP", "port", "8080"))
                            .envVars(List.of("DEBUG=true"))
                            .build();
    List<AgentConfigVariableDTO> vars = synthesizer.configVariablesFor(d);
    assertThat(vars).hasSize(4); // 1 reconcile + 2 config + 1 env
    assertThat(vars.stream().map(AgentConfigVariableDTO::getName)).contains("agentName", "protocol", "port", "DEBUG");
    assertThat(vars.stream().filter(v -> v.getName().equals("agentName")).findFirst().get().getValue())
        .isEqualTo("support-agent");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void synthesisFailsFastWithClearMessageOnInvalidConfigVariableKey() {
    // A cloud-derived config key that starts with a digit violates the Harness variable-name pattern.
    AgentDescriptor d = AgentDescriptor.builder()
                            .name("support-agent")
                            .image("acct.dkr.ecr/x:1")
                            .identity("arn:aws:iam::1:role/AC")
                            .reconcilePinKey("agentName")
                            .reconcilePinValue("support-agent")
                            .configVariables(Map.of("2ndFactor", "value"))
                            .build();
    assertThatThrownBy(() -> synthesizer.synthesize(d, AgentPlatform.AWS_AGENT_CORE, "support_agent", "Support Agent"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("config variable key")
        .hasMessageContaining("2ndFactor")
        .hasMessageContaining("not a valid Harness variable name");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void synthesisFailsFastWithClearMessageOnInvalidEnvVarKey() {
    // A cloud-derived env key containing a space violates the Harness variable-name pattern.
    AgentDescriptor d = AgentDescriptor.builder()
                            .name("support-agent")
                            .image("acct.dkr.ecr/x:1")
                            .identity("arn:aws:iam::1:role/AC")
                            .reconcilePinKey("agentName")
                            .reconcilePinValue("support-agent")
                            .envVars(List.of("my key=value"))
                            .build();
    assertThatThrownBy(() -> synthesizer.configVariablesFor(d))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("environment variable key")
        .hasMessageContaining("my key")
        .hasMessageContaining("not a valid Harness variable name");
  }
}
