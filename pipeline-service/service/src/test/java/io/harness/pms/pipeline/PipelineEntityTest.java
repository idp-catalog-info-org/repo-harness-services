/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.rule.OwnerRule.DANIEL;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.gitsync.beans.StoreType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineEntityTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetData() {
    String yaml0 = "pipeline:\n\tidentifier: name\n";
    PipelineEntity pipelineEntity0 = PipelineEntity.builder().yaml(yaml0).build();
    assertThat(pipelineEntity0.getData()).isEqualTo(yaml0);

    String yaml1 = "pipeline:\n\tidentifier: name1\n";
    PipelineEntity pipelineEntity1 = PipelineEntity.builder().yaml(yaml1).storeType(StoreType.INLINE).build();
    assertThat(pipelineEntity1.getData()).isEqualTo(yaml1);
  }

  @Test
  @Owner(developers = DANIEL)
  @Category(UnitTests.class)
  public void testGetGitAttributesYaml() {
    String lineSep = System.lineSeparator();

    // Test with all fields null
    PipelineEntity pipelineEntity0 = PipelineEntity.builder().build();
    assertThat(pipelineEntity0.getGitAttributesYaml()).isEqualTo("");

    // Test with only storeType
    PipelineEntity pipelineEntity1 = PipelineEntity.builder().storeType(StoreType.REMOTE).build();
    assertThat(pipelineEntity1.getGitAttributesYaml()).isEqualTo(lineSep + "storeType: REMOTE");

    // Test with storeType and repo
    PipelineEntity pipelineEntity2 =
        PipelineEntity.builder().storeType(StoreType.REMOTE).repo("harness-private").build();
    assertThat(pipelineEntity2.getGitAttributesYaml())
        .isEqualTo(lineSep + "storeType: REMOTE" + lineSep + "repo: harness-private");

    // Test with all fields populated
    PipelineEntity pipelineEntity3 = PipelineEntity.builder()
                                         .storeType(StoreType.REMOTE)
                                         .repo("harness-private")
                                         .connectorRef("account.ghprivate")
                                         .filePath(".harness/deploy_k8s_git8.yaml")
                                         .build();
    assertThat(pipelineEntity3.getGitAttributesYaml())
        .isEqualTo(lineSep + "storeType: REMOTE" + lineSep + "repo: harness-private" + lineSep
            + "connectorRef: account.ghprivate" + lineSep + "filePath: .harness/deploy_k8s_git8.yaml");

    // Test with some fields null and others populated
    PipelineEntity pipelineEntity4 = PipelineEntity.builder()
                                         .storeType(StoreType.INLINE)
                                         .repo(null)
                                         .connectorRef("account.ghprivate")
                                         .filePath(null)
                                         .build();
    assertThat(pipelineEntity4.getGitAttributesYaml())
        .isEqualTo(lineSep + "storeType: INLINE" + lineSep + "connectorRef: account.ghprivate");
  }
}
