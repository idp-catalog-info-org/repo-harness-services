/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.execution.IPAllowlistServiceImpl;
import io.harness.ci.pipeline.executions.beans.IPAllowlistDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class IPAllowlistResourceImplTest {
  private static final String ACCOUNT_IDENTIFIER = "testAccount";

  @Mock private IPAllowlistServiceImpl ipAllowlistService;
  @InjectMocks private IPAllowlistResourceImpl ipAllowlistResource;

  @Before
  public void setUp() {
    openMocks(this);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetIPAllowlistSuccess() {
    Set<String> ipAddresses = new HashSet<>();
    ipAddresses.add("10.0.0.1");
    ipAddresses.add("192.168.1.1");
    when(ipAllowlistService.getIPAllowlistForAccountAndModule(ACCOUNT_IDENTIFIER)).thenReturn(ipAddresses);

    ResponseDTO<IPAllowlistDTO> response = ipAllowlistResource.getIPAllowlist(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getIpAddresses()).containsExactlyInAnyOrder("10.0.0.1", "192.168.1.1");
    verify(ipAllowlistService).getIPAllowlistForAccountAndModule(ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetIPAllowlistException() {
    when(ipAllowlistService.getIPAllowlistForAccountAndModule(ACCOUNT_IDENTIFIER))
        .thenThrow(new RuntimeException("service error"));

    ResponseDTO<IPAllowlistDTO> response = ipAllowlistResource.getIPAllowlist(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNotNull();
    assertThat(response.getData().getIpAddresses()).isNull();
  }
}
