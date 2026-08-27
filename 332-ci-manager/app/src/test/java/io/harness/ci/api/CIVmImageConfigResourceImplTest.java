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
import io.harness.ci.execution.execution.intfc.CIBuildImageVmConfigService;
import io.harness.ci.pipeline.executions.beans.BuildImageConfigDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class CIVmImageConfigResourceImplTest {
  private static final String ACCOUNT_IDENTIFIER = "testAccount";

  @Mock private CIBuildImageVmConfigService ciBuildImageVmConfigService;
  @InjectMocks private CIVmImageConfigResourceImpl ciVmImageConfigResource;

  @Before
  public void setUp() {
    openMocks(this);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildVmImageConfigSuccess() {
    BuildImageConfigDTO configDTO = BuildImageConfigDTO.builder().accountId(ACCOUNT_IDENTIFIER).build();
    when(ciBuildImageVmConfigService.getBuildImageConfigOrDefault(ACCOUNT_IDENTIFIER)).thenReturn(configDTO);

    ResponseDTO<BuildImageConfigDTO> response = ciVmImageConfigResource.getBuildVmImageConfig(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isEqualTo(configDTO);
    verify(ciBuildImageVmConfigService).getBuildImageConfigOrDefault(ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildVmImageConfigException() {
    when(ciBuildImageVmConfigService.getBuildImageConfigOrDefault(ACCOUNT_IDENTIFIER))
        .thenThrow(new RuntimeException("config error"));

    ResponseDTO<BuildImageConfigDTO> response = ciVmImageConfigResource.getBuildVmImageConfig(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isNull();
  }
}
