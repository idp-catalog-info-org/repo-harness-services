/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.code.impl;

import static io.harness.annotations.dev.HarnessTeam.CODE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.code.CodeRepoResponseDTO;
import io.harness.code.CodeResourceClient;
import io.harness.code.HarnessCodeRepoPayload;
import io.harness.gitx.InlineHCConstants;
import io.harness.ng.code.services.HarnessCodeService;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@OwnedBy(CODE)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class HarnessCodeServiceImpl implements HarnessCodeService {
  CodeResourceClient codeResourceClient;

  public CodeRepoResponseDTO createHarnessDefaultRepository(String accountIdentifier) {
    return NGRestUtils.getGeneralResponse(codeResourceClient.createRepo(accountIdentifier, null, null,
        HarnessCodeRepoPayload.builder()
            .identifier(InlineHCConstants.REPO_NAME)
            .defaultBranch(InlineHCConstants.REPO_DEFAULT_BRANCH)
            .description(InlineHCConstants.REPO_DESCRIPTION)
            .parentRef(accountIdentifier)
            .isPublic(false)
            .readme(true)
            .build()));
  }
}
