/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.salesforce.defaultpipelines;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class SalesforcePipelineMappingsServiceTest {
  private final SalesforcePipelineMappingsService service = new SalesforcePipelineMappingsService();

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void getPipelineMappings_returnsAllFiveUseCases() {
    Map<String, String> mappings = service.getPipelineMappings();

    assertThat(mappings).hasSize(5);
    assertThat(mappings).containsEntry("DEPLOY", "salesforce_dx_deploy");
    assertThat(mappings).containsEntry("VALIDATE", "salesforce_dx_validate");
    assertThat(mappings).containsEntry("QUICK_DEPLOY", "salesforce_quick_deploy");
    assertThat(mappings).containsEntry("EVALUATE_DIFF", "salesforce_evaluate_diff");
    assertThat(mappings).containsEntry("SOURCE_BACKUP", "salesforce_source_backup");
  }

  @Test
  @Owner(developers = OwnerRule.HARSHIT)
  @Category(UnitTests.class)
  public void getPipelineMappings_returnsSameInstanceOnMultipleCalls() {
    assertThat(service.getPipelineMappings()).isSameAs(service.getPipelineMappings());
  }
}
