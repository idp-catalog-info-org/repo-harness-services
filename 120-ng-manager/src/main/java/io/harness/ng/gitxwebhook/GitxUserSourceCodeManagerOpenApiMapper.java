/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import static io.harness.ng.core.Status.SUCCESS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.CorrelationContext;
import io.harness.spec.server.ng.v1.model.UserSourceCodeManagerResponse;
import io.harness.spec.server.ng.v1.model.UserSourceCodeManagerResponseList;
import io.harness.spec.server.ng.v1.model.UserSourceCodeManagersApiResponse;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@UtilityClass
public class GitxUserSourceCodeManagerOpenApiMapper {
  public static UserSourceCodeManagersApiResponse toUserSourceCodeManagersApiResponse(
      io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTOList userSourceCodeManagers) {
    UserSourceCodeManagersApiResponse response = new UserSourceCodeManagersApiResponse();
    response.setStatus(SUCCESS.name());
    response.setCorrelationId(CorrelationContext.getCorrelationId());
    response.setData(toUserSourceCodeManagerResponseList(userSourceCodeManagers));
    return response;
  }

  private static UserSourceCodeManagerResponseList toUserSourceCodeManagerResponseList(
      io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTOList userSourceCodeManagers) {
    if (userSourceCodeManagers == null || userSourceCodeManagers.getUserSourceCodeManagerResponseDTOList() == null) {
      return new UserSourceCodeManagerResponseList().userSourceCodeManagerResponseList(Collections.emptyList());
    }
    List<UserSourceCodeManagerResponse> entries =
        userSourceCodeManagers.getUserSourceCodeManagerResponseDTOList()
            .stream()
            .map(GitxUserSourceCodeManagerOpenApiMapper::toUserSourceCodeManagerResponse)
            .collect(Collectors.toList());
    return new UserSourceCodeManagerResponseList().userSourceCodeManagerResponseList(entries);
  }

  private static UserSourceCodeManagerResponse toUserSourceCodeManagerResponse(
      io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTO userSourceCodeManager) {
    return new UserSourceCodeManagerResponse().userName(userSourceCodeManager.getUserName());
  }
}
