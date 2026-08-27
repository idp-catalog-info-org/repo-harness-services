/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.rule.OwnerRule.RITEK_ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineServiceOpaContextGuardTest extends CategoryTest {
  private static final String PIPELINE_SERVICE_ID = "PipelineService";

  @After
  public void clearContext() {
    SecurityContextBuilder.unsetCompleteContext();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldAttachPipelineServicePrincipalWhenContextIsEmpty() {
    SecurityContextBuilder.setContext((Principal) null);
    SourcePrincipalContextBuilder.setSourcePrincipal(null);

    try (PipelineServiceOpaContextGuard ignore = new PipelineServiceOpaContextGuard()) {
      assertThat(SecurityContextBuilder.getPrincipal())
          .isInstanceOf(ServicePrincipal.class)
          .extracting(Principal::getName)
          .isEqualTo(PIPELINE_SERVICE_ID);
      assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isEqualTo(SecurityContextBuilder.getPrincipal());
    }

    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldPreserveExistingPrincipal() {
    Principal existingPrincipal = new UserPrincipal("user-id", "user@harness.io", "user", "accountId");
    SecurityContextBuilder.setContext(existingPrincipal);
    SourcePrincipalContextBuilder.setSourcePrincipal(existingPrincipal);

    try (PipelineServiceOpaContextGuard ignore = new PipelineServiceOpaContextGuard()) {
      assertThat(SecurityContextBuilder.getPrincipal()).isEqualTo(existingPrincipal);
      assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isEqualTo(existingPrincipal);
    }

    assertThat(SecurityContextBuilder.getPrincipal()).isEqualTo(existingPrincipal);
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isEqualTo(existingPrincipal);
  }
}
