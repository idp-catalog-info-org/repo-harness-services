/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.resource;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.DANIEL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.definition.config.InfrastructureConfig;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.service.registries.UnifiedConversionRegistry;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.unified.cd.infrastructure.InfraConfig;
import io.harness.unified.cd.infrastructure.InfraType;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UnifiedInfrastructureConversionUtilityTest extends CategoryTest {
  private static final String ELASTIGROUP_INFRA_YAML = ""
      + "infrastructureDefinition:\n"
      + "  name: spot-infra\n"
      + "  identifier: spot_infra\n"
      + "  orgIdentifier: org\n"
      + "  projectIdentifier: project\n"
      + "  environmentRef: env\n"
      + "  deploymentType: Elastigroup\n"
      + "  type: Elastigroup\n"
      + "  spec:\n"
      + "    connectorRef: spot_connector\n"
      + "    configuration:\n"
      + "      store:\n"
      + "        type: Harness\n"
      + "        spec:\n"
      + "          files:\n"
      + "            - account:/elastigroup.json\n"
      + "  allowSimultaneousDeployments: false\n";

  private static final String AWS_AGENT_CORE_INFRA_YAML = ""
      + "infrastructureDefinition:\n"
      + "  name: agent-core-infra\n"
      + "  identifier: agent_core_infra\n"
      + "  orgIdentifier: org\n"
      + "  projectIdentifier: project\n"
      + "  environmentRef: env\n"
      + "  deploymentType: AwsAgentCore\n"
      + "  type: AwsAgentCore\n"
      + "  spec:\n"
      + "    connectorRef: aws_connector\n"
      + "    region: us-east-1\n"
      + "  allowSimultaneousDeployments: false\n";

  private static final String GOOGLE_AGENT_RUNTIME_INFRA_YAML = ""
      + "infrastructureDefinition:\n"
      + "  name: agent-runtime-infra\n"
      + "  identifier: agent_runtime_infra\n"
      + "  orgIdentifier: org\n"
      + "  projectIdentifier: project\n"
      + "  environmentRef: env\n"
      + "  deploymentType: GoogleAgentRuntime\n"
      + "  type: GoogleAgentRuntime\n"
      + "  spec:\n"
      + "    connectorRef: gcp_connector\n"
      + "    projectId: gcp-project\n"
      + "    location: us-central1\n"
      + "  allowSimultaneousDeployments: false\n";

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testConvertInfrastructureElastigroup() {
    UnifiedConversionRegistry.ConversionResult<InfraType> result =
        UnifiedConversionRegistry.convertInfrastructure(InfrastructureKind.ELASTIGROUP);

    assertThat(result).isNotNull();
    assertThat(result.getUnifiedType()).isEqualTo(InfraType.ELASTIGROUP);
    assertThat(result.getTemplateAction()).isEqualTo(InfraType.NO_OP_ACTION);
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testToUnifiedInfraElastigroup() throws Exception {
    InfrastructureConfig ngInfrastructureConfig = YamlUtils.read(ELASTIGROUP_INFRA_YAML, InfrastructureConfig.class);

    InfraConfig unifiedInfraConfig = UnifiedInfrastructureConversionUtility.toUnifiedInfra(ngInfrastructureConfig);

    assertThat(unifiedInfraConfig.getInfraInfoConfig().getUses()).isEqualTo(InfraType.ELASTIGROUP);
    assertThat(unifiedInfraConfig.getInfraInfoConfig().getAction()).isEqualTo(InfraType.NO_OP_ACTION);
    assertThat(unifiedInfraConfig.getInfraInfoConfig().getInfraKey()).containsExactly("spot_connector");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToUnifiedInfraAwsAgentCore() throws Exception {
    InfrastructureConfig ngInfrastructureConfig = YamlUtils.read(AWS_AGENT_CORE_INFRA_YAML, InfrastructureConfig.class);

    InfraConfig unifiedInfraConfig = UnifiedInfrastructureConversionUtility.toUnifiedInfra(ngInfrastructureConfig);

    assertThat(unifiedInfraConfig.getInfraInfoConfig().getUses()).isEqualTo(InfraType.AWS_AGENT_CORE);
    assertThat(unifiedInfraConfig.getInfraInfoConfig().getAction()).isEqualTo(InfraType.NO_OP_ACTION);
    assertThat(unifiedInfraConfig.getInfraInfoConfig().getInfraKey()).containsExactly("aws_connector", "us-east-1");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testToUnifiedInfraGoogleAgentRuntime() throws Exception {
    InfrastructureConfig ngInfrastructureConfig =
        YamlUtils.read(GOOGLE_AGENT_RUNTIME_INFRA_YAML, InfrastructureConfig.class);

    InfraConfig unifiedInfraConfig = UnifiedInfrastructureConversionUtility.toUnifiedInfra(ngInfrastructureConfig);

    assertThat(unifiedInfraConfig.getInfraInfoConfig().getUses()).isEqualTo(InfraType.GOOGLE_AGENT_RUNTIME);
    assertThat(unifiedInfraConfig.getInfraInfoConfig().getAction()).isEqualTo(InfraType.NO_OP_ACTION);
    assertThat(unifiedInfraConfig.getInfraInfoConfig().getInfraKey())
        .containsExactly("gcp_connector", "gcp-project", "us-central1");
  }
}
