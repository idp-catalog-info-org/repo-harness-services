/*
# Copyright 2024 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.smp.resources;

import io.harness.configuration.DeployMode;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;
import io.harness.version.RuntimeInfo;
import io.harness.version.VersionInfo;
import io.harness.version.VersionPackage;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;

@Api("version/smp")
@Path("/version/smp")
@Produces(MediaType.APPLICATION_JSON)
@PublicApi
public class SMPVersionResource {
  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get version for SMP", nickname = "getSMPVersion")
  public RestResponse<VersionPackage> get() {
    String version = System.getenv("SMP_VERSION");
    String deployMode = System.getenv("DEPLOY_MODE");
    if (StringUtils.isEmpty(version) || !DeployMode.isOnPrem(deployMode)) {
      throw new BadRequestException("this is not an smp environment");
    }
    return new RestResponse<>(VersionPackage.builder()
                                  .versionInfo(VersionInfo.builder().version(version).build())
                                  .runtimeInfo(RuntimeInfo.builder().deployMode(deployMode).build())
                                  .build());
  }
}
