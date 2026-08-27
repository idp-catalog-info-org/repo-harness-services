/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.configmanager.entities.CustomPluginV2Entity;
import io.harness.idp.configmanager.repositories.CustomPluginV2Repository;
import io.harness.idp.plugin.config.CustomPluginsConfig;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CustomPluginV2CreateRequest;
import io.harness.spec.server.idp.v1.model.CustomPluginV2Response;
import io.harness.spec.server.idp.v1.model.CustomPluginV2UpdateRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

public class CustomPluginsV2ServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account-id";
  private static final String PLUGIN_ID = "my-custom-plugin";
  private static final String PLUGIN_NAME = "My Custom Plugin";
  private static final String PLUGIN_DESCRIPTION = "A custom plugin description";
  private static final String PLUGIN_ICON = "icon-url";
  private static final String FILE_URL =
      "https://storage.cloud.google.com/bucket/custom-plugins-v2/qa/test-account-id/my-custom-plugin_abc123.html";
  private static final String BUCKET_NAME = "test-bucket";
  private static final String ENV = "qa";
  private static final String USER_EMAIL = "test@harness.io";

  @Mock private CustomPluginV2Repository customPluginV2Repository;
  @Mock private CloudStorageUtil cloudStorageUtil;

  private CustomPluginsV2ServiceImpl customPluginsV2Service;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    CustomPluginsConfig customPluginsConfig = CustomPluginsConfig.builder().bucketName(BUCKET_NAME).build();
    customPluginsV2Service =
        new CustomPluginsV2ServiceImpl(customPluginV2Repository, cloudStorageUtil, customPluginsConfig, ENV);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateCustomPlugin() {
    CustomPluginV2CreateRequest request = buildCreateRequest();
    CustomPluginV2Entity savedEntity = buildEntity();
    when(customPluginV2Repository.save(any(CustomPluginV2Entity.class))).thenReturn(savedEntity);

    CustomPluginV2Response response = customPluginsV2Service.createCustomPlugin(ACCOUNT_ID, request);

    assertNotNull(response);
    assertEquals(PLUGIN_ID, response.getIdentifier());
    assertEquals(PLUGIN_NAME, response.getName());
    assertEquals(PLUGIN_DESCRIPTION, response.getDescription());
    assertEquals(PLUGIN_ICON, response.getIcon());
    verify(customPluginV2Repository).save(any(CustomPluginV2Entity.class));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPlugin() {
    CustomPluginV2Entity entity = buildEntity();
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(entity));

    CustomPluginV2Response response = customPluginsV2Service.getCustomPlugin(ACCOUNT_ID, PLUGIN_ID);

    assertNotNull(response);
    assertEquals(PLUGIN_ID, response.getIdentifier());
    assertEquals(PLUGIN_NAME, response.getName());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginNotFound() {
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> customPluginsV2Service.getCustomPlugin(ACCOUNT_ID, PLUGIN_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllCustomPlugins() {
    Page<CustomPluginV2Entity> page = new PageImpl<>(Collections.singletonList(buildEntity()));
    when(customPluginV2Repository.getCustomPluginsV2(ACCOUNT_ID, 0, 10, null, null)).thenReturn(page);

    Page<CustomPluginV2Entity> result = customPluginsV2Service.getAllCustomPlugins(ACCOUNT_ID, 0, 10, null, null);

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals(PLUGIN_ID, result.getContent().get(0).getIdentifier());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateCustomPlugin() {
    CustomPluginV2Entity existingEntity = buildEntity();
    String updatedName = "Updated Plugin Name";
    String updatedDescription = "Updated description";
    CustomPluginV2UpdateRequest request = new CustomPluginV2UpdateRequest();
    request.setName(updatedName);
    request.setDescription(updatedDescription);
    request.setIcon(PLUGIN_ICON);

    CustomPluginV2Entity updatedEntity = buildEntityWithValues(updatedName, updatedDescription);
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(existingEntity));
    when(customPluginV2Repository.save(any(CustomPluginV2Entity.class))).thenReturn(updatedEntity);

    CustomPluginV2Response response = customPluginsV2Service.updateCustomPlugin(ACCOUNT_ID, PLUGIN_ID, request);

    assertNotNull(response);
    assertEquals(updatedName, response.getName());
    assertEquals(updatedDescription, response.getDescription());
    verify(customPluginV2Repository).save(any(CustomPluginV2Entity.class));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateCustomPluginNotFound() {
    CustomPluginV2UpdateRequest request = new CustomPluginV2UpdateRequest();
    request.setName(PLUGIN_NAME);
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> customPluginsV2Service.updateCustomPlugin(ACCOUNT_ID, PLUGIN_ID, request))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCustomPluginWithFile() {
    CustomPluginV2Entity entity = buildEntity();
    entity.setFileUrl(FILE_URL);
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(entity));
    doNothing().when(cloudStorageUtil).deleteFile(FILE_URL);
    doNothing().when(customPluginV2Repository).deleteByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID);

    customPluginsV2Service.deleteCustomPlugin(ACCOUNT_ID, PLUGIN_ID);

    verify(cloudStorageUtil).deleteFile(FILE_URL);
    verify(customPluginV2Repository).deleteByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCustomPluginWithoutFile() {
    CustomPluginV2Entity entity = buildEntity();
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(entity));
    doNothing().when(customPluginV2Repository).deleteByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID);

    customPluginsV2Service.deleteCustomPlugin(ACCOUNT_ID, PLUGIN_ID);

    verify(cloudStorageUtil, never()).deleteFile(anyString());
    verify(customPluginV2Repository).deleteByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCustomPluginNotFound() {
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> customPluginsV2Service.deleteCustomPlugin(ACCOUNT_ID, PLUGIN_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUploadHtmlFile() {
    CustomPluginV2Entity entity = buildEntity();
    InputStream fileInputStream = new ByteArrayInputStream("<html></html>".getBytes());
    FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName("page.html").build();

    CustomPluginV2Entity savedEntity = buildEntity();
    savedEntity.setFileUrl(FILE_URL);

    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(entity));
    when(cloudStorageUtil.uploadFile(eq(BUCKET_NAME), anyString(), anyString(), any(InputStream.class)))
        .thenReturn(FILE_URL);
    when(customPluginV2Repository.save(any(CustomPluginV2Entity.class))).thenReturn(savedEntity);

    CustomPluginV2Response response =
        customPluginsV2Service.uploadHtmlFile(ACCOUNT_ID, PLUGIN_ID, fileInputStream, fileDetail);

    assertNotNull(response);
    assertEquals(FILE_URL, response.getFileUrl());
    verify(cloudStorageUtil).uploadFile(eq(BUCKET_NAME), anyString(), anyString(), any(InputStream.class));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUploadHtmlFileReplacesExisting() {
    String oldFileUrl = "https://storage.cloud.google.com/bucket/old-file.html";
    CustomPluginV2Entity entity = buildEntity();
    entity.setFileUrl(oldFileUrl);
    InputStream fileInputStream = new ByteArrayInputStream("<html></html>".getBytes());
    FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName("page.htm").build();

    CustomPluginV2Entity savedEntity = buildEntity();
    savedEntity.setFileUrl(FILE_URL);

    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(entity));
    doNothing().when(cloudStorageUtil).deleteFile(oldFileUrl);
    when(cloudStorageUtil.uploadFile(eq(BUCKET_NAME), anyString(), anyString(), any(InputStream.class)))
        .thenReturn(FILE_URL);
    when(customPluginV2Repository.save(any(CustomPluginV2Entity.class))).thenReturn(savedEntity);

    CustomPluginV2Response response =
        customPluginsV2Service.uploadHtmlFile(ACCOUNT_ID, PLUGIN_ID, fileInputStream, fileDetail);

    assertNotNull(response);
    verify(cloudStorageUtil).deleteFile(oldFileUrl);
    verify(cloudStorageUtil).uploadFile(eq(BUCKET_NAME), anyString(), anyString(), any(InputStream.class));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUploadHtmlFileUnsupportedFormat() {
    CustomPluginV2Entity entity = buildEntity();
    InputStream fileInputStream = new ByteArrayInputStream("data".getBytes());
    FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName("file.pdf").build();

    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> customPluginsV2Service.uploadHtmlFile(ACCOUNT_ID, PLUGIN_ID, fileInputStream, fileDetail))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("pdf");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUploadHtmlFileNotFound() {
    InputStream fileInputStream = new ByteArrayInputStream("data".getBytes());
    FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName("page.html").build();

    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> customPluginsV2Service.uploadHtmlFile(ACCOUNT_ID, PLUGIN_ID, fileInputStream, fileDetail))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteHtmlFile() {
    CustomPluginV2Entity entity = buildEntity();
    entity.setFileUrl(FILE_URL);
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(entity));
    doNothing().when(cloudStorageUtil).deleteFile(FILE_URL);
    when(customPluginV2Repository.save(any(CustomPluginV2Entity.class))).thenReturn(entity);

    customPluginsV2Service.deleteHtmlFile(ACCOUNT_ID, PLUGIN_ID);

    verify(cloudStorageUtil).deleteFile(FILE_URL);
    verify(customPluginV2Repository).save(any(CustomPluginV2Entity.class));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteHtmlFileNoFilePresent() {
    CustomPluginV2Entity entity = buildEntity();
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> customPluginsV2Service.deleteHtmlFile(ACCOUNT_ID, PLUGIN_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("No HTML file found");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteHtmlFileEntityNotFound() {
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> customPluginsV2Service.deleteHtmlFile(ACCOUNT_ID, PLUGIN_ID))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetFileContent() {
    String htmlContent = "<html><body>Hello</body></html>";
    CustomPluginV2Entity entity = buildEntity();
    entity.setFileUrl(FILE_URL);
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(entity));
    when(cloudStorageUtil.readFile(FILE_URL)).thenReturn(htmlContent.getBytes(StandardCharsets.UTF_8));

    String result = customPluginsV2Service.getFileContent(ACCOUNT_ID, PLUGIN_ID);

    assertEquals(htmlContent, result);
    verify(cloudStorageUtil).readFile(FILE_URL);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetFileContentNoFilePresent() {
    CustomPluginV2Entity entity = buildEntity();
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> customPluginsV2Service.getFileContent(ACCOUNT_ID, PLUGIN_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("No HTML file found");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetFileContentEntityNotFound() {
    when(customPluginV2Repository.findByAccountIdentifierAndIdentifier(ACCOUNT_ID, PLUGIN_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> customPluginsV2Service.getFileContent(ACCOUNT_ID, PLUGIN_ID))
        .isInstanceOf(NotFoundException.class);
  }

  private CustomPluginV2CreateRequest buildCreateRequest() {
    CustomPluginV2CreateRequest request = new CustomPluginV2CreateRequest();
    request.setIdentifier(PLUGIN_ID);
    request.setName(PLUGIN_NAME);
    request.setDescription(PLUGIN_DESCRIPTION);
    request.setIcon(PLUGIN_ICON);
    return request;
  }

  private CustomPluginV2Entity buildEntity() {
    return buildEntityWithValues(PLUGIN_NAME, PLUGIN_DESCRIPTION);
  }

  private CustomPluginV2Entity buildEntityWithValues(String name, String description) {
    return CustomPluginV2Entity.builder()
        .identifier(PLUGIN_ID)
        .accountIdentifier(ACCOUNT_ID)
        .name(name)
        .description(description)
        .icon(PLUGIN_ICON)
        .createdBy(EmbeddedUser.builder().email(USER_EMAIL).build())
        .createdAt(System.currentTimeMillis())
        .lastUpdatedAt(System.currentTimeMillis())
        .build();
  }
}
