/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.sfcomparisonpair.resources;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.salesforce.SalesforceMetadataType;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.SalesforceMetadataTypesApi;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.SALESFORCE})
@NextGenManagerAuth
public class SalesforceMetadataTypesApiImpl implements SalesforceMetadataTypesApi {
  @Inject
  public SalesforceMetadataTypesApiImpl() {}

  @Override
  public Response getSalesforceMetadataTypes() {
    List<String> metadataTypes = Arrays.stream(SalesforceMetadataType.values())
                                     .map(SalesforceMetadataType::getApiName)
                                     .collect(Collectors.toList());
    return Response.ok().entity(metadataTypes).build();
  }
}
