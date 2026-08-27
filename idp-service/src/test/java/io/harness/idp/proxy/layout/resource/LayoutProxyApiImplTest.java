/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout.resource;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.idp.proxy.layout.service.LayoutService;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class LayoutProxyApiImplTest extends CategoryTest {
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testInitialization() {
    LayoutProxyApiImpl apiImpl = new LayoutProxyApiImpl(
        mock(BackstageResourceClient.class), mock(LayoutService.class), mock(TransactionTemplate.class));
    assertThat(apiImpl).isNotNull();
  }
}
