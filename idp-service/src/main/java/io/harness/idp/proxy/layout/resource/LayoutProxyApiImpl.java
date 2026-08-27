/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.layout.resource;

import static io.harness.idp.common.RbacConstants.IDP_LAYOUT;
import static io.harness.idp.common.RbacConstants.IDP_LAYOUT_EDIT;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getGeneralResponse;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.clients.BackstageResourceClient;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.proxy.layout.service.LayoutService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.LayoutProxyApi;
import io.harness.spec.server.idp.v1.model.LayoutIngestRequest;
import io.harness.spec.server.idp.v1.model.LayoutRequest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Timed
@ResponseMetered
public class LayoutProxyApiImpl implements LayoutProxyApi {
  BackstageResourceClient backstageResourceClient;
  LayoutService layoutsService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response createLayout(@Valid LayoutRequest body, @AccountIdentifier String harnessAccount) {
    try {
      if (body.getName() == null || body.getEntityType() == null || body.getYaml() == null
          || body.getEntityKind() == null || body.getType() == null) {
        throw new InvalidRequestException("Required fields are name, type, yaml, entity_type and entity_kind");
      }
      layoutsService.saveOrUpdateLayouts(body, harnessAccount);
      Object entity = getGeneralResponse(backstageResourceClient.createLayout(body, harnessAccount));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in createLayout - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response deleteLayout(@Valid LayoutRequest body, @AccountIdentifier String harnessAccount) {
    try {
      if (body.getName() == null) {
        throw new InvalidRequestException("Please provide required field - name");
      }
      Object entity = layoutsService.deleteLayoutAndSaveAuditEvent(body, harnessAccount);
      return Response.ok(entity).build();
    } catch (NotFoundException ex) {
      log.error("Error in deleteLayout - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    } catch (Exception ex) {
      log.error("Error in deleteLayout - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getAllLayouts(@AccountIdentifier String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.getAllLayouts(harnessAccount));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in getAllLayouts - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getLayout(String layoutIdentifier, @AccountIdentifier String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.getLayout(harnessAccount, layoutIdentifier));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in getLayout - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getLayoutHealth(@AccountIdentifier String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.getHealth(harnessAccount));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in getLayoutHealth - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response layoutIngest(@Valid LayoutIngestRequest body, @AccountIdentifier String harnessAccount) {
    try {
      Object entity = getGeneralResponse(backstageResourceClient.ingestLayout(body, harnessAccount));
      return Response.ok(entity).build();
    } catch (Exception ex) {
      log.error("Error in layoutIngest - account = {}, error = {}", harnessAccount, ex.getMessage(), ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(ex.getMessage()).build())
          .build();
    }
  }
}