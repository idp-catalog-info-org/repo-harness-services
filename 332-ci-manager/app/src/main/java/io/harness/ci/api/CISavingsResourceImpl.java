/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.savings.api.SavingsInfo;
import io.harness.ci.savings.CISavingsService;
import io.harness.cimanager.savings.api.CISavingsResource;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class CISavingsResourceImpl implements CISavingsResource {
  private final CISavingsService ciSavingsService;

  @NGAccessControlCheck(resourceType = PIPELINE_RESOURCE_TYPE, permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<SavingsInfo> getStageSavings(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier String pipelineIdentifier, String stageExecutionId) {
    return ResponseDTO.newResponse(ciSavingsService.getStageSavings(accountIdentifier, stageExecutionId));
  }

  @NGAccessControlCheck(resourceType = PIPELINE_RESOURCE_TYPE, permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<String> getFirstFullRun(@AccountIdentifier String accountIdentifier,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier String pipelineIdentifier) {
    return ResponseDTO.newResponse(
        ciSavingsService.getFirstFullRun(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier));
  }
}
