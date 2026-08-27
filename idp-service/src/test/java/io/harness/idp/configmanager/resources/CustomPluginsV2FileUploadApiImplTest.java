/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resources;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.configmanager.resource.CustomPluginsV2FileUploadApiImpl;
import io.harness.idp.configmanager.service.CustomPluginsV2Service;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CustomPluginV2Response;

import com.google.cloud.storage.StorageException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class CustomPluginsV2FileUploadApiImplTest {
  @Mock private CustomPluginsV2Service customPluginV2Service;
  @InjectMocks private CustomPluginsV2FileUploadApiImpl customPluginsV2FileUploadApiImpl;

  private static final String ACCOUNT_ID = "test-account-id";
  private static final String PLUGIN_ID = "my-custom-plugin";
  private static final String FILE_NAME = "page.html";

  private InputStream fileInputStream;
  private FormDataContentDisposition fileDetail;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    fileInputStream = new ByteArrayInputStream("<html></html>".getBytes());
    fileDetail = FormDataContentDisposition.name("file").fileName(FILE_NAME).build();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUploadHtmlFile() {
    CustomPluginV2Response serviceResponse = buildPluginResponse();
    when(customPluginV2Service.uploadHtmlFile(ACCOUNT_ID, PLUGIN_ID, fileInputStream, fileDetail))
        .thenReturn(serviceResponse);

    Response response =
        customPluginsV2FileUploadApiImpl.uploadHtmlFile(PLUGIN_ID, fileInputStream, fileDetail, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    CustomPluginV2Response entity = (CustomPluginV2Response) response.getEntity();
    assertThat(entity.getIdentifier()).isEqualTo(PLUGIN_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUploadHtmlFileNotFound() {
    when(customPluginV2Service.uploadHtmlFile(ACCOUNT_ID, PLUGIN_ID, fileInputStream, fileDetail))
        .thenThrow(new NotFoundException("Plugin not found"));

    Response response =
        customPluginsV2FileUploadApiImpl.uploadHtmlFile(PLUGIN_ID, fileInputStream, fileDetail, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("Plugin not found");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUploadHtmlFileUnsupportedFormat() {
    when(customPluginV2Service.uploadHtmlFile(ACCOUNT_ID, PLUGIN_ID, fileInputStream, fileDetail))
        .thenThrow(new UnsupportedOperationException("File format not supported"));

    Response response =
        customPluginsV2FileUploadApiImpl.uploadHtmlFile(PLUGIN_ID, fileInputStream, fileDetail, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("File format not supported");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUploadHtmlFileStorageException() {
    when(customPluginV2Service.uploadHtmlFile(ACCOUNT_ID, PLUGIN_ID, fileInputStream, fileDetail))
        .thenThrow(new StorageException(503, "Service unavailable"));

    Response response =
        customPluginsV2FileUploadApiImpl.uploadHtmlFile(PLUGIN_ID, fileInputStream, fileDetail, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(503);
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("Service unavailable");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUploadHtmlFileError() {
    when(customPluginV2Service.uploadHtmlFile(ACCOUNT_ID, PLUGIN_ID, fileInputStream, fileDetail))
        .thenThrow(new RuntimeException("unexpected error"));

    Response response =
        customPluginsV2FileUploadApiImpl.uploadHtmlFile(PLUGIN_ID, fileInputStream, fileDetail, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("unexpected error");
  }

  // --- deleteHtmlFile tests ---

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteHtmlFile() {
    doNothing().when(customPluginV2Service).deleteHtmlFile(ACCOUNT_ID, PLUGIN_ID);

    Response response = customPluginsV2FileUploadApiImpl.deleteHtmlFile(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteHtmlFileNotFound() {
    doThrow(new NotFoundException("Plugin not found"))
        .when(customPluginV2Service)
        .deleteHtmlFile(ACCOUNT_ID, PLUGIN_ID);

    Response response = customPluginsV2FileUploadApiImpl.deleteHtmlFile(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("Plugin not found");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteHtmlFileStorageException() {
    doThrow(new StorageException(503, "Service unavailable"))
        .when(customPluginV2Service)
        .deleteHtmlFile(ACCOUNT_ID, PLUGIN_ID);

    Response response = customPluginsV2FileUploadApiImpl.deleteHtmlFile(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(503);
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("Service unavailable");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteHtmlFileError() {
    doThrow(new RuntimeException("unexpected error")).when(customPluginV2Service).deleteHtmlFile(ACCOUNT_ID, PLUGIN_ID);

    Response response = customPluginsV2FileUploadApiImpl.deleteHtmlFile(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("unexpected error");
  }

  // --- previewFile tests ---

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPreviewFile() {
    String htmlContent = "<html><body>Hello</body></html>";
    when(customPluginV2Service.getFileContent(ACCOUNT_ID, PLUGIN_ID)).thenReturn(htmlContent);

    Response response = customPluginsV2FileUploadApiImpl.previewFile(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isEqualTo(htmlContent);
    assertThat(response.getMediaType().toString()).isEqualTo("text/html");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPreviewFileNotFound() {
    when(customPluginV2Service.getFileContent(ACCOUNT_ID, PLUGIN_ID))
        .thenThrow(new NotFoundException("No HTML file found"));

    Response response = customPluginsV2FileUploadApiImpl.previewFile(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("No HTML file found");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testPreviewFileError() {
    when(customPluginV2Service.getFileContent(ACCOUNT_ID, PLUGIN_ID))
        .thenThrow(new RuntimeException("unexpected error"));

    Response response = customPluginsV2FileUploadApiImpl.previewFile(PLUGIN_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    ResponseMessage entity = (ResponseMessage) response.getEntity();
    assertThat(entity.getMessage()).isEqualTo("unexpected error");
  }

  private CustomPluginV2Response buildPluginResponse() {
    CustomPluginV2Response response = new CustomPluginV2Response();
    response.setIdentifier(PLUGIN_ID);
    response.setName("My Custom Plugin");
    return response;
  }
}
