/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

@OwnedBy(HarnessTeam.PIPELINE)
class PipelineServiceOpaContextGuard implements AutoCloseable {
  private final Principal previousPrincipal;
  private final Principal previousSourcePrincipal;
  private final boolean attached;

  PipelineServiceOpaContextGuard() {
    previousPrincipal = SecurityContextBuilder.getPrincipal();
    previousSourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    attached = previousPrincipal == null;
    if (attached) {
      ServicePrincipal servicePrincipal = new ServicePrincipal(PIPELINE_SERVICE.getServiceId());
      SecurityContextBuilder.setContext(servicePrincipal);
      SourcePrincipalContextBuilder.setSourcePrincipal(servicePrincipal);
    }
  }

  @Override
  public void close() {
    if (attached) {
      SecurityContextBuilder.setContext(previousPrincipal);
      SourcePrincipalContextBuilder.setSourcePrincipal(previousSourcePrincipal);
    }
  }
}
