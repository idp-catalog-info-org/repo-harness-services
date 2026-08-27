/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resources;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.entities.CustomPluginV2Entity;
import io.harness.idp.configmanager.resource.CustomPluginsV2ApiImpl;
import io.harness.idp.configmanager.service.CustomPluginsV2Service;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CustomPluginV2CreateRequest;
import io.harness.spec.server.idp.v1.model.CustomPluginV2Response;
import io.harness.spec.server.idp.v1.model.CustomPluginV2UpdateRequest;

import java.util.Collections;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(HarnessTeam.IDP)
public class CustomPluginsV2ApiImplTest {
  @Mock private CustomPluginsV2Service customPluginV2Service;
  @Mock private IdpCommonService idpCommonService;
  @InjectMocks private CustomPluginsV2ApiImpl customPluginsV2ApiImpl;

  private static final String ACCOUNT_ID = "test-account-id";
  private static final String PLUGIN_ID = "my-custom-plugin";
  private static final String PLUGIN_NAME = "My Custom Plugin";
  private static final String PLUGIN_DESCRIPTION = "A custom plugin description";
  private static final String PLUGIN_ICON = "icon-url";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateCustomPluginsV2() {
    CustomPluginV2CreateRequest request = buildCreateRequest();
    CustomPluginV2Response serviceResponse = buildPluginResponse();
    when(customPluginV2Service.createCustomPlugin(ACCOUNT_ID, request)).thenReturn(serviceResponse);

    Response response = customPluginsV2ApiImpl.createCustomPluginsV2(request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    CustomPluginV2Response entity = (CustomPluginV2Response) response.getEntity();
    assertThat(entity.getIdentifier()).isEqualTo(PLUGIN_ID);
    assertThat(entity.getName()).isEqualTo(PLUGIN_NAME);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateCustomPluginsV2DuplicateKey() {
    CustomPluginV2CreateRequest request = buildCreateRequest();
    when(customPluginV2Service.createCustomPlugin(ACCOUNT_ID, request)).thenThrow(new DuplicateKeyException("dup"));

    Response response = customPluginsV2ApiImpl.createCustomPluginsV2(request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("Custom Plugin already exists with the same identifier");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateCustomPluginsV2Error() {
    CustomPluginV2CreateRequest request = buildCreateRequest();
    when(customPluginV2Service.createCustomPlugin(ACCOUNT_ID, request)).thenThrow(new RuntimeException("unexpected"));

    Response response = customPluginsV2ApiImpl.createCustomPluginsV2(request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("Failed to create custom plugin");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCustomPluginV2() {
    doNothing().when(customPluginV2Service).deleteCustomPlugin(ACCOUNT_ID, PLUGIN_ID);

    Response response = customPluginsV2ApiImpl.deleteCustomPluginV2(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCustomPluginV2NotFound() {
    doThrow(new NotFoundException("not found")).when(customPluginV2Service).deleteCustomPlugin(ACCOUNT_ID, PLUGIN_ID);

    Response response = customPluginsV2ApiImpl.deleteCustomPluginV2(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCustomPluginV2Error() {
    doThrow(new RuntimeException("unexpected")).when(customPluginV2Service).deleteCustomPlugin(ACCOUNT_ID, PLUGIN_ID);

    Response response = customPluginsV2ApiImpl.deleteCustomPluginV2(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("Failed to delete custom plugin");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginV2() {
    CustomPluginV2Response serviceResponse = buildPluginResponse();
    when(customPluginV2Service.getCustomPlugin(ACCOUNT_ID, PLUGIN_ID)).thenReturn(serviceResponse);

    Response response = customPluginsV2ApiImpl.getCustomPluginV2(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    CustomPluginV2Response entity = (CustomPluginV2Response) response.getEntity();
    assertThat(entity.getIdentifier()).isEqualTo(PLUGIN_ID);
    assertThat(entity.getName()).isEqualTo(PLUGIN_NAME);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginV2NotFound() {
    when(customPluginV2Service.getCustomPlugin(ACCOUNT_ID, PLUGIN_ID)).thenThrow(new NotFoundException("not found"));

    Response response = customPluginsV2ApiImpl.getCustomPluginV2(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginV2Error() {
    when(customPluginV2Service.getCustomPlugin(ACCOUNT_ID, PLUGIN_ID)).thenThrow(new RuntimeException("unexpected"));

    Response response = customPluginsV2ApiImpl.getCustomPluginV2(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("Failed to fetch custom plugin");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginsV2WithDefaults() {
    Page<CustomPluginV2Entity> page = new PageImpl<>(Collections.singletonList(buildEntity()));
    when(customPluginV2Service.getAllCustomPlugins(ACCOUNT_ID, 0, 10, null, null)).thenReturn(page);
    when(idpCommonService.buildPageResponse(eq(0), eq(10), anyLong(), any()))
        .thenReturn(Response.ok().entity(Collections.emptyList()).build());

    Response response = customPluginsV2ApiImpl.getCustomPluginsV2(ACCOUNT_ID, null, null, null, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginsV2WithExplicitParams() {
    int pageIndex = 2;
    int pageLimit = 20;
    String sort = "name";
    String searchTerm = "test";
    Page<CustomPluginV2Entity> page = new PageImpl<>(Collections.singletonList(buildEntity()));
    when(customPluginV2Service.getAllCustomPlugins(ACCOUNT_ID, pageIndex, pageLimit, sort, searchTerm))
        .thenReturn(page);
    when(idpCommonService.buildPageResponse(eq(pageIndex), eq(pageLimit), anyLong(), any()))
        .thenReturn(Response.ok().entity(Collections.emptyList()).build());

    Response response = customPluginsV2ApiImpl.getCustomPluginsV2(ACCOUNT_ID, pageIndex, pageLimit, sort, searchTerm);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateCustomPluginV2() {
    CustomPluginV2UpdateRequest request = buildUpdateRequest();
    CustomPluginV2Response serviceResponse = buildPluginResponse();
    when(customPluginV2Service.updateCustomPlugin(ACCOUNT_ID, PLUGIN_ID, request)).thenReturn(serviceResponse);

    Response response = customPluginsV2ApiImpl.updateCustomPluginV2(PLUGIN_ID, request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    CustomPluginV2Response entity = (CustomPluginV2Response) response.getEntity();
    assertThat(entity.getIdentifier()).isEqualTo(PLUGIN_ID);
    assertThat(entity.getName()).isEqualTo(PLUGIN_NAME);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateCustomPluginV2NotFound() {
    CustomPluginV2UpdateRequest request = buildUpdateRequest();
    when(customPluginV2Service.updateCustomPlugin(ACCOUNT_ID, PLUGIN_ID, request))
        .thenThrow(new NotFoundException("not found"));

    Response response = customPluginsV2ApiImpl.updateCustomPluginV2(PLUGIN_ID, request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateCustomPluginV2Error() {
    CustomPluginV2UpdateRequest request = buildUpdateRequest();
    when(customPluginV2Service.updateCustomPlugin(ACCOUNT_ID, PLUGIN_ID, request))
        .thenThrow(new RuntimeException("unexpected"));

    Response response = customPluginsV2ApiImpl.updateCustomPluginV2(PLUGIN_ID, request, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("Failed to update custom plugin");
  }

  private CustomPluginV2CreateRequest buildCreateRequest() {
    CustomPluginV2CreateRequest request = new CustomPluginV2CreateRequest();
    request.setIdentifier(PLUGIN_ID);
    request.setName(PLUGIN_NAME);
    request.setDescription(PLUGIN_DESCRIPTION);
    request.setIcon(PLUGIN_ICON);
    return request;
  }

  private CustomPluginV2UpdateRequest buildUpdateRequest() {
    CustomPluginV2UpdateRequest request = new CustomPluginV2UpdateRequest();
    request.setName(PLUGIN_NAME);
    request.setDescription(PLUGIN_DESCRIPTION);
    request.setIcon(PLUGIN_ICON);
    return request;
  }

  private CustomPluginV2Response buildPluginResponse() {
    CustomPluginV2Response response = new CustomPluginV2Response();
    response.setIdentifier(PLUGIN_ID);
    response.setName(PLUGIN_NAME);
    response.setDescription(PLUGIN_DESCRIPTION);
    response.setIcon(PLUGIN_ICON);
    return response;
  }

  private CustomPluginV2Entity buildEntity() {
    return CustomPluginV2Entity.builder()
        .identifier(PLUGIN_ID)
        .accountIdentifier(ACCOUNT_ID)
        .name(PLUGIN_NAME)
        .description(PLUGIN_DESCRIPTION)
        .icon(PLUGIN_ICON)
        .createdBy(EmbeddedUser.builder().build())
        .build();
  }
}
