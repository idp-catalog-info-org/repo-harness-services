/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDC)
public class GitXWebhookApiHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks GitXWebhooksApiHelper gitXWebhooksApiHelper;

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testListWebhooksForOlderGitXWebhooksWithWebhookTypePopulatingCorrectly() {
    GitXWebhook webhook = GitXWebhook.builder()
                              .accountIdentifier("accountId")
                              .identifier("id")
                              .name("name")
                              .connectorRef("connectorRef")
                              .webhookType(null)
                              .repoName("repo")
                              .createdAt(1000l)
                              .build();

    GetGitXWebhookResponseDTO getGitXWebhookResponseDTO = gitXWebhooksApiHelper.prepareGitXWebhooks(webhook);

    assertThat(NGCommonEntityConstants.GIT_WEBHOOK_TYPE).isEqualTo(getGitXWebhookResponseDTO.getWebhookType());
  }
}
