/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mapper;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.aiagent.AbstractAiServiceSpec;
import io.harness.cdng.service.beans.aiagent.AgentSourceSpec;
import io.harness.cdng.service.beans.aiagent.AwsAgentCoreServiceSpec;
import io.harness.cdng.service.beans.aiagent.AwsCoreAgentSource;
import io.harness.cdng.service.beans.aiagent.AwsCoreAgentSourceType;
import io.harness.cdng.service.beans.aiagent.ContainerAgentSource;
import io.harness.cdng.service.beans.aiagent.GoogleAgentRuntimeServiceSpec;
import io.harness.cdng.service.beans.aiagent.GoogleAgentSource;
import io.harness.cdng.service.beans.aiagent.GoogleAgentSourceType;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.unified.cd.service.agent.AwsAgentCoreSourceType;
import io.harness.unified.cd.service.agent.GoogleAgentRuntimeSourceType;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.unified.cd.service.spec.ServiceType;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class TemplateBasedAgentServiceMapperTest extends CategoryTest {
  private TemplateBasedServiceMapper serviceMapper;

  @Before
  public void setUp() {
    serviceMapper = new TemplateBasedServiceMapper(null, null, null, null, null, null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToUnifiedAwsAgentCoreService() {
    AwsAgentCoreServiceSpec serviceSpecNG =
        AwsAgentCoreServiceSpec.builder()
            .source(
                AwsCoreAgentSource.builder()
                    .type(AwsCoreAgentSourceType.CONTAINER)
                    .spec(ContainerAgentSource.builder().image(ParameterField.createValueField("agent:1.0")).build())
                    .build())
            .executionRoleArn(ParameterField.createValueField("arn:aws:iam::1234:role/agent"))
            .configVariables(List.of(
                StringNGVariable.builder().name("MODEL").value(ParameterField.createValueField("claude")).build()))
            .variables(List.of(
                StringNGVariable.builder().name("REGION").value(ParameterField.createValueField("us-east-1")).build()))
            .build();

    ServiceConfig serviceConfig = serviceMapper.toUnifiedServiceWithTemplate(
        ngServiceConfig(ServiceDefinitionType.AWS_AGENT_CORE, serviceSpecNG));

    assertThat(serviceConfig.getServiceInfoConfig().getUses()).isEqualTo(ServiceType.AWS_AGENT_CORE);
    assertThat(serviceConfig.getServiceInfoConfig().getInputs()).containsOnlyKeys("REGION");

    io.harness.unified.cd.service.spec.AwsAgentCoreServiceSpec serviceSpecUnified =
        (io.harness.unified.cd.service.spec.AwsAgentCoreServiceSpec) serviceConfig.getServiceInfoConfig().getWith();
    assertThat(serviceSpecUnified.getSource().getUses()).isEqualTo(AwsAgentCoreSourceType.CONTAINER);
    assertThat(((io.harness.unified.cd.service.agent.ContainerAgentSource) serviceSpecUnified.getSource().getWith())
                   .getImage()
                   .getValue())
        .isEqualTo("agent:1.0");
    assertThat(serviceSpecUnified.getExecutionRoleArn().getValue()).isEqualTo("arn:aws:iam::1234:role/agent");
    assertThat(serviceSpecUnified.getConfigVariables()).containsOnlyKeys("MODEL");
    assertThat((Map<String, Object>) serviceSpecUnified.getConfigVariables().get("MODEL"))
        .containsEntry("type", "string")
        .containsEntry("value", "claude");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToUnifiedGoogleAgentRuntimeService() {
    GoogleAgentRuntimeServiceSpec serviceSpecNG =
        GoogleAgentRuntimeServiceSpec.builder()
            .source(
                GoogleAgentSource.builder()
                    .type(GoogleAgentSourceType.CONTAINER)
                    .spec(ContainerAgentSource.builder().image(ParameterField.createValueField("agent:2.0")).build())
                    .build())
            .build();

    ServiceConfig serviceConfig = serviceMapper.toUnifiedServiceWithTemplate(
        ngServiceConfig(ServiceDefinitionType.GOOGLE_AGENT_RUNTIME, serviceSpecNG));

    assertThat(serviceConfig.getServiceInfoConfig().getUses()).isEqualTo(ServiceType.GOOGLE_AGENT_RUNTIME);

    io.harness.unified.cd.service.spec.GoogleAgentRuntimeServiceSpec serviceSpecUnified =
        (io.harness.unified.cd.service.spec.GoogleAgentRuntimeServiceSpec) serviceConfig.getServiceInfoConfig()
            .getWith();
    assertThat(serviceSpecUnified.getSource().getUses()).isEqualTo(GoogleAgentRuntimeSourceType.CONTAINER);
    assertThat(((io.harness.unified.cd.service.agent.ContainerAgentSource) serviceSpecUnified.getSource().getWith())
                   .getImage()
                   .getValue())
        .isEqualTo("agent:2.0");
    assertThat(serviceSpecUnified.getConfigVariables()).isNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToUnifiedAwsAgentCoreServiceWithDeployConfigFallsBackToPojo() {
    AwsAgentCoreServiceSpec serviceSpecNG =
        AwsAgentCoreServiceSpec.builder()
            .source(
                AwsCoreAgentSource.builder()
                    .type(AwsCoreAgentSourceType.CONTAINER)
                    .spec(ContainerAgentSource.builder().image(ParameterField.createValueField("agent:1.0")).build())
                    .build())
            .executionRoleArn(ParameterField.createValueField("arn:aws:iam::1234:role/agent"))
            .manifests(List.of(ManifestConfigWrapper.builder()
                                   .manifest(ManifestConfig.builder()
                                                 .identifier("deployConfig")
                                                 .type(ManifestConfigType.AGENT_CONFIG)
                                                 .build())
                                   .build()))
            .build();

    assertThat(serviceMapper.toUnifiedServiceWithTemplate(
                   ngServiceConfig(ServiceDefinitionType.AWS_AGENT_CORE, serviceSpecNG)))
        .isNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToUnifiedAwsAgentCoreServiceWithoutSourceFallsBackToPojo() {
    AwsAgentCoreServiceSpec serviceSpecNG =
        AwsAgentCoreServiceSpec.builder()
            .executionRoleArn(ParameterField.createValueField("arn:aws:iam::1234:role/agent"))
            .build();

    assertThat(serviceMapper.toUnifiedServiceWithTemplate(
                   ngServiceConfig(ServiceDefinitionType.AWS_AGENT_CORE, serviceSpecNG)))
        .isNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToUnifiedGoogleAgentRuntimeServiceWithUnsupportedSourceFallsBackToPojo() {
    GoogleAgentRuntimeServiceSpec serviceSpecNG = GoogleAgentRuntimeServiceSpec.builder()
                                                      .source(GoogleAgentSource.builder()
                                                                  .type(GoogleAgentSourceType.CONTAINER)
                                                                  .spec(new AgentSourceSpec() {})
                                                                  .build())
                                                      .build();

    assertThat(serviceMapper.toUnifiedServiceWithTemplate(
                   ngServiceConfig(ServiceDefinitionType.GOOGLE_AGENT_RUNTIME, serviceSpecNG)))
        .isNull();
  }

  private NGServiceConfig ngServiceConfig(ServiceDefinitionType type, AbstractAiServiceSpec serviceSpec) {
    return NGServiceConfig.builder()
        .ngServiceV2InfoConfig(
            NGServiceV2InfoConfig.builder()
                .identifier("agentService")
                .name("agentService")
                .serviceDefinition(ServiceDefinition.builder().type(type).serviceSpec(serviceSpec).build())
                .build())
        .build();
  }
}
