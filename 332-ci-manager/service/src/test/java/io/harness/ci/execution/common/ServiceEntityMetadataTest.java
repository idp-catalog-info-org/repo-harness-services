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
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceEntityMetadataTest {
  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testServiceEntityMetadataBuilder() {
    Map<String, String> tags = new HashMap<>();
    tags.put("env", "prod");

    ServiceEntityMetadata metadata = ServiceEntityMetadata.builder()
                                         .identifier("svc1")
                                         .name("My Service")
                                         .description("Test service")
                                         .tags(tags)
                                         .gitOpsEnabled(true)
                                         .harnessVersion("V1")
                                         .build();

    assertThat(metadata.getIdentifier()).isEqualTo("svc1");
    assertThat(metadata.getName()).isEqualTo("My Service");
    assertThat(metadata.getDescription()).isEqualTo("Test service");
    assertThat(metadata.getTags()).containsEntry("env", "prod");
    assertThat(metadata.getGitOpsEnabled()).isTrue();
    assertThat(metadata.getHarnessVersion()).isEqualTo("V1");
  }
}
