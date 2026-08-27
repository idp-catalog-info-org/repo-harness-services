/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.api;

import io.harness.ci.execution.execution.intfc.CIBuildImageVmConfigService;
import io.harness.ci.pipeline.executions.beans.BuildImageConfigDTO;
import io.harness.ci.pipeline.executions.beans.CIBuildImageConfigResource;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.AdminPortalAuth;

import com.google.inject.Inject;

@AdminPortalAuth
public class CIBuildImageVmConfigResourceImpl implements CIBuildImageConfigResource {
  @Inject CIBuildImageVmConfigService ciBuildImageVmConfigService;

  @Override
  public RestResponse<Boolean> updateBuildImageConfig(
      String accountIdentifier, BuildImageConfigDTO buildImageConfigDTO) {
    boolean isUpdated = ciBuildImageVmConfigService.updateBuildImageConfig(accountIdentifier, buildImageConfigDTO);
    return new RestResponse<>(isUpdated);
  }

  @Override
  public RestResponse<BuildImageConfigDTO> getBuildImageConfig(String accountIdentifier) {
    try {
      BuildImageConfigDTO configDTO = ciBuildImageVmConfigService.getBuildImageConfig(accountIdentifier);
      return new RestResponse<>(configDTO);
    } catch (Exception e) {
      return new RestResponse<>(null);
    }
  }

  @Override
  public RestResponse<Boolean> deleteBuildImageConfig(String accountIdentifier) {
    return new RestResponse<>(ciBuildImageVmConfigService.deleteBuildImageConfig(accountIdentifier));
  }
}
