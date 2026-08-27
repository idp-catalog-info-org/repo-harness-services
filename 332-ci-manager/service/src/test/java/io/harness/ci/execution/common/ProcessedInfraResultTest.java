/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.rule.Owner;
import io.harness.unified.cd.infrastructure.InfraConfig;
import io.harness.unified.cd.infrastructure.InfrastructureMetadata;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ProcessedInfraResultTest {
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testProcessedInfraResultBuilder() {
    EnvironmentOutcome envOutcome = EnvironmentOutcome.builder().identifier("env1").build();
    InfrastructureMetadata metadata = InfrastructureMetadata.builder().identifier("infra1").name("Infra 1").build();
    InfraConfig infraConfig = InfraConfig.builder().build();

    ProcessedInfraResult result = ProcessedInfraResult.builder()
                                      .serviceRef("svc1")
                                      .envRef("env1")
                                      .infraId("infra1")
                                      .environmentOutcome(envOutcome)
                                      .infraMetadata(metadata)
                                      .infraConfig(infraConfig)
                                      .build();

    assertThat(result.getServiceRef()).isEqualTo("svc1");
    assertThat(result.getEnvRef()).isEqualTo("env1");
    assertThat(result.getInfraId()).isEqualTo("infra1");
    assertThat(result.getEnvironmentOutcome()).isEqualTo(envOutcome);
    assertThat(result.getInfraMetadata().getIdentifier()).isEqualTo("infra1");
    assertThat(result.getInfraConfig()).isNotNull();
  }
}
