/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.agent;

import static io.harness.rule.OwnerRule.FJUNIOR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AgentStepValidatorTest extends CategoryTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private AgentStepValidator validator;

  @Before
  public void setUp() {
    validator = new AgentStepValidator();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testValidateAgentName_present() {
    ObjectNode spec = objectMapper.createObjectNode();
    spec.put("agentName", "myTemplate");

    validator.validateAgentName(spec, "step1");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testValidateAgentName_missing() {
    ObjectNode spec = objectMapper.createObjectNode();

    assertThatThrownBy(() -> validator.validateAgentName(spec, "step1"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("agentName");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testValidateAgentName_empty() {
    ObjectNode spec = objectMapper.createObjectNode();
    spec.put("agentName", "");

    assertThatThrownBy(() -> validator.validateAgentName(spec, "step1"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("agentName");
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testValidateContainerizedStepGroup_valid() {
    assertThat(validator.isInsideContainerizedStepGroup("KubernetesDirect")).isTrue();
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testValidateContainerizedStepGroup_null() {
    assertThat(validator.isInsideContainerizedStepGroup(null)).isFalse();
  }
}
