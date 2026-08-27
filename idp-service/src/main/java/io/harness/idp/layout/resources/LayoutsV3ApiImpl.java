/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.layout.resources;

import static io.harness.idp.common.RbacConstants.IDP_LAYOUT;
import static io.harness.idp.common.RbacConstants.IDP_LAYOUT_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.layout.service.LayoutService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.LayoutsV3Api;
import io.harness.spec.server.idp.v1.model.LayoutIngestRequest;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.Collections;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Timed
@ResponseMetered
public class LayoutsV3ApiImpl implements LayoutsV3Api {
  private LayoutService layoutService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response createLayoutV3(@Valid LayoutRequest layoutRequest, @AccountIdentifier String harnessAccount) {
    return layoutService.create(harnessAccount, layoutRequest);
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response deleteLayoutV3(@Valid LayoutRequest layoutRequest, @AccountIdentifier String harnessAccount) {
    return layoutService.delete(harnessAccount, layoutRequest);
  }

  @Override
  public Response getLayoutV3(String layoutIdentifier, @AccountIdentifier String harnessAccount) {
    return layoutService.get(harnessAccount, layoutIdentifier);
  }

  @Override
  public Response getLayoutHealthV3(@AccountIdentifier String harnessAccount) {
    return Response.ok(Collections.singletonMap("status", "ok")).build();
  }

  @Override
  public Response getLayoutsV3(@AccountIdentifier String harnessAccount) {
    return layoutService.get(harnessAccount);
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response layoutIngestV3(
      @Valid LayoutIngestRequest layoutIngestRequest, @AccountIdentifier String harnessAccount) {
    return layoutService.ingest(harnessAccount, layoutIngestRequest);
  }
}
