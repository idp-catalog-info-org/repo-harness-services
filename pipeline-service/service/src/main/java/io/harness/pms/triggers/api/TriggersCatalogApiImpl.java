/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.api;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.spec.server.pipeline.v1.TriggersCatalogApi;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class TriggersCatalogApiImpl implements TriggersCatalogApi {
  private final NGTriggerService ngTriggerService;
  private final NGTriggerApiUtils ngTriggerApiUtils;

  @Override
  public Response getTriggersCatalog(String harnessAccount, String version) {
    List<TriggerCatalogItem> triggerCatalog = ngTriggerService.getTriggerCatalog(harnessAccount);
    return Response.ok().entity(ngTriggerApiUtils.toCatalogResponseDTO(triggerCatalog, version)).build();
  }
}
