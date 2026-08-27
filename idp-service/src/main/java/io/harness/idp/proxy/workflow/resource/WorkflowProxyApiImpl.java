/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.workflow.resource;

import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.clients.BackstageResourceClient;
import io.harness.clients.BackstageScaffolderTaskRequest;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.WorkflowProxyV2Api;
import io.harness.spec.server.idp.v1.model.WorkflowExecutionRequest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Timed
@ResponseMetered
public class WorkflowProxyApiImpl implements WorkflowProxyV2Api {
  BackstageResourceClient backstageResourceClient;
  CatalogServiceHelper catalogServiceHelper;

  @Override
  public Response executeWorkflowV2(@Valid WorkflowExecutionRequest workflowExecutionRequest,
      @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    try {
      if (workflowExecutionRequest.getIdentifier() == null || workflowExecutionRequest.getIdentifier().isBlank()) {
        throw new InvalidRequestException("Required fields are identifier");
      }
      String workflowRef = CatalogUtils.entityRef(
          "workflow", orgIdentifier, projectIdentifier, workflowExecutionRequest.getIdentifier());
      catalogServiceHelper.checkCrudRbac(
          harnessAccount, orgIdentifier, projectIdentifier, "workflow", workflowRef, "execute");
      String templateRef = CatalogUtils.entityRef(
          "template", orgIdentifier, projectIdentifier, workflowExecutionRequest.getIdentifier());
      BackstageScaffolderTaskRequest backstageScaffolderTaskRequest = new BackstageScaffolderTaskRequest(
          templateRef, workflowExecutionRequest.getValues(), workflowExecutionRequest.getSecrets());
      Object entity = getGeneralResponse(
          backstageResourceClient.executeScaffolderTask(harnessAccount, backstageScaffolderTaskRequest));
      return Response.status(Response.Status.CREATED).entity(entity).build();
    } catch (Exception ex) {
      log.error("Error in executing workflow - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }
}
