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
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class CIBuildImageVmConfigResourceImplTest {
  private static final String ACCOUNT_IDENTIFIER = "testAccount";

  @Mock private CIBuildImageVmConfigService ciBuildImageVmConfigService;
  @InjectMocks private CIBuildImageVmConfigResourceImpl ciBuildImageVmConfigResource;

  @Before
  public void setUp() {
    openMocks(this);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testUpdateBuildImageConfig() {
    BuildImageConfigDTO configDTO = BuildImageConfigDTO.builder().accountId(ACCOUNT_IDENTIFIER).build();
    when(ciBuildImageVmConfigService.updateBuildImageConfig(ACCOUNT_IDENTIFIER, configDTO)).thenReturn(true);

    RestResponse<Boolean> response = ciBuildImageVmConfigResource.updateBuildImageConfig(ACCOUNT_IDENTIFIER, configDTO);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isTrue();
    verify(ciBuildImageVmConfigService).updateBuildImageConfig(ACCOUNT_IDENTIFIER, configDTO);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildImageConfigSuccess() {
    BuildImageConfigDTO configDTO = BuildImageConfigDTO.builder().accountId(ACCOUNT_IDENTIFIER).build();
    when(ciBuildImageVmConfigService.getBuildImageConfig(ACCOUNT_IDENTIFIER)).thenReturn(configDTO);

    RestResponse<BuildImageConfigDTO> response = ciBuildImageVmConfigResource.getBuildImageConfig(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isEqualTo(configDTO);
    verify(ciBuildImageVmConfigService).getBuildImageConfig(ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBuildImageConfigException() {
    when(ciBuildImageVmConfigService.getBuildImageConfig(ACCOUNT_IDENTIFIER))
        .thenThrow(new RuntimeException("config not found"));

    RestResponse<BuildImageConfigDTO> response = ciBuildImageVmConfigResource.getBuildImageConfig(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testDeleteBuildImageConfig() {
    when(ciBuildImageVmConfigService.deleteBuildImageConfig(ACCOUNT_IDENTIFIER)).thenReturn(true);

    RestResponse<Boolean> response = ciBuildImageVmConfigResource.deleteBuildImageConfig(ACCOUNT_IDENTIFIER);

    assertThat(response).isNotNull();
    assertThat(response.getResource()).isTrue();
    verify(ciBuildImageVmConfigService).deleteBuildImageConfig(ACCOUNT_IDENTIFIER);
  }
}
