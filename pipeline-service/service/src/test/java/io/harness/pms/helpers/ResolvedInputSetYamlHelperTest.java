/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResolvedInputSetYamlHelperTest extends CategoryTest {
  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRevertSecretExpressionsConvertsNgSecretManagerObtain() {
    String yaml = "pipeline:\n"
        + "  variables:\n"
        + "    - name: github_app_private_key\n"
        + "      type: String\n"
        + "      value: ${ngSecretManager.obtain(\"account.plateng-iac-github-app-private-key\", -62375426)}\n";

    String reverted = ResolvedInputSetYamlHelper.revertSecretExpressions(yaml);

    assertThat(reverted).doesNotContain("ngSecretManager.obtain");
    assertThat(reverted).contains("<+secrets.getValue('account.plateng-iac-github-app-private-key')>");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRevertSecretExpressionsNoOpWhenNoSecretFunctor() {
    String yaml = "pipeline:\n"
        + "  variables:\n"
        + "    - name: chart_version\n"
        + "      value: 0.1.1\n";

    assertThat(ResolvedInputSetYamlHelper.revertSecretExpressions(yaml)).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testRevertSecretExpressionsHandlesNullAndEmpty() {
    assertThat(ResolvedInputSetYamlHelper.revertSecretExpressions(null)).isNull();
    assertThat(ResolvedInputSetYamlHelper.revertSecretExpressions("")).isEqualTo("");
  }
}
