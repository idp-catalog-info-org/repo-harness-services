/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.dryrun.semantic;

import static io.harness.rule.OwnerRule.FJUNIOR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SemanticValidationContextTest extends CategoryTest {
  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void contextGettersReturnConfiguredValues() throws Exception {
    JsonNode root = new ObjectMapper(new YAMLFactory()).readTree("pipeline: {}");
    SemanticValidationContext ctx = SemanticValidationContext.builder()
                                        .pipelineRoot(root)
                                        .referredEntities(Collections.emptyList())
                                        .connectorsByRef(Collections.emptyMap())
                                        .accountIdentifier("acct")
                                        .orgIdentifier("org")
                                        .projectIdentifier("proj")
                                        .build();

    assertThat(ctx.getPipelineRoot()).isSameAs(root);
    assertThat(ctx.getReferredEntities()).isEmpty();
    assertThat(ctx.getConnectorsByRef()).isEmpty();
    assertThat(ctx.getAccountIdentifier()).isEqualTo("acct");
    assertThat(ctx.getOrgIdentifier()).isEqualTo("org");
    assertThat(ctx.getProjectIdentifier()).isEqualTo("proj");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void contextIsV1_reflectsHarnessVersion() {
    assertThat(SemanticValidationContext.builder().harnessVersion("1").build().isV1()).isTrue();
    assertThat(SemanticValidationContext.builder().harnessVersion("0").build().isV1()).isFalse();
    assertThat(SemanticValidationContext.builder().build().isV1()).isFalse();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void lambdaRuleReturnsEmpty() {
    SemanticRule rule = ctx -> Collections.emptyList();
    assertThat(rule.apply(null)).isEmpty();
    assertThat((List<?>) rule.apply(null)).isNotNull();
  }
}
