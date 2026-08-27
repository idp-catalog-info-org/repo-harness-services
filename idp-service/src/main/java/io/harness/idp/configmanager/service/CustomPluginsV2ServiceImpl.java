/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.common.FileUtils;
import io.harness.idp.configmanager.entities.CustomPluginV2Entity;
import io.harness.idp.configmanager.mappers.CustomPluginV2Mapper;
import io.harness.idp.configmanager.repositories.CustomPluginV2Repository;
import io.harness.idp.plugin.config.CustomPluginsConfig;
import io.harness.spec.server.idp.v1.model.CustomPluginV2CreateRequest;
import io.harness.spec.server.idp.v1.model.CustomPluginV2Response;
import io.harness.spec.server.idp.v1.model.CustomPluginV2UpdateRequest;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.springframework.data.domain.Page;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class CustomPluginsV2ServiceImpl implements CustomPluginsV2Service {
  private static final String CUSTOM_PLUGINS_V2_DIR = "custom-plugins-v2";
  private static final String FILE_NAME_SEPARATOR = "_";
  private static final List<String> SUPPORTED_HTML_EXTENSIONS = List.of("html", "htm");
  private static final int RANDOM_STRING_LENGTH = 6;

  private final CustomPluginV2Repository customPluginV2Repository;
  private final CloudStorageUtil cloudStorageUtil;
  private final CustomPluginsConfig customPluginsConfig;
  private final String env;

  @Inject
  public CustomPluginsV2ServiceImpl(CustomPluginV2Repository customPluginV2Repository,
      CloudStorageUtil cloudStorageUtil, @Named("customPlugins") CustomPluginsConfig customPluginsConfig,
      @Named("env") String env) {
    this.customPluginV2Repository = customPluginV2Repository;
    this.cloudStorageUtil = cloudStorageUtil;
    this.customPluginsConfig = customPluginsConfig;
    this.env = env;
  }

  @Override
  public CustomPluginV2Response createCustomPlugin(String accountIdentifier, CustomPluginV2CreateRequest request) {
    CustomPluginV2Entity entity = CustomPluginV2Entity.builder()
                                      .identifier(request.getIdentifier())
                                      .accountIdentifier(accountIdentifier)
                                      .name(request.getName())
                                      .description(request.getDescription())
                                      .icon(request.getIcon())
                                      .build();
    CustomPluginV2Entity savedEntity = customPluginV2Repository.save(entity);
    log.info("Created custom plugin v2 with identifier {} for account {}", request.getIdentifier(), accountIdentifier);
    return CustomPluginV2Mapper.toDTO(savedEntity);
  }

  @Override
  public CustomPluginV2Response getCustomPlugin(String accountIdentifier, String pluginId) {
    CustomPluginV2Entity entity = getEntityOrThrow(accountIdentifier, pluginId);
    return CustomPluginV2Mapper.toDTO(entity);
  }

  @Override
  public Page<CustomPluginV2Entity> getAllCustomPlugins(
      String accountIdentifier, Integer page, Integer limit, String sort, String searchTerm) {
    return customPluginV2Repository.getCustomPluginsV2(accountIdentifier, page, limit, sort, searchTerm);
  }

  @Override
  public CustomPluginV2Response updateCustomPlugin(
      String accountIdentifier, String pluginId, CustomPluginV2UpdateRequest request) {
    CustomPluginV2Entity entity = getEntityOrThrow(accountIdentifier, pluginId);

    entity.setName(request.getName());
    entity.setDescription(request.getDescription());
    entity.setIcon(request.getIcon());

    CustomPluginV2Entity updatedEntity = customPluginV2Repository.save(entity);
    log.info("Updated custom plugin v2 with identifier {} for account {}", pluginId, accountIdentifier);
    return CustomPluginV2Mapper.toDTO(updatedEntity);
  }

  @Override
  public void deleteCustomPlugin(String accountIdentifier, String pluginId) {
    CustomPluginV2Entity entity = getEntityOrThrow(accountIdentifier, pluginId);
    if (StringUtils.isNotBlank(entity.getFileUrl())) {
      cloudStorageUtil.deleteFile(entity.getFileUrl());
    }
    customPluginV2Repository.deleteByAccountIdentifierAndIdentifier(accountIdentifier, pluginId);
    log.info("Deleted custom plugin v2 with identifier {} for account {}", pluginId, accountIdentifier);
  }

  @Override
  public CustomPluginV2Response uploadHtmlFile(
      String accountIdentifier, String pluginId, InputStream fileInputStream, FormDataContentDisposition fileDetail) {
    CustomPluginV2Entity entity = getEntityOrThrow(accountIdentifier, pluginId);

    String fileExtension = FilenameUtils.getExtension(fileDetail.getFileName());
    if (!SUPPORTED_HTML_EXTENSIONS.contains(fileExtension.toLowerCase())) {
      throw new UnsupportedOperationException(
          String.format("File format '%s' is not supported. Only HTML files (html, htm) are allowed.", fileExtension));
    }
    if (StringUtils.isNotBlank(entity.getFileUrl())) {
      cloudStorageUtil.deleteFile(entity.getFileUrl());
    }

    String filePath = getFilePath(accountIdentifier);
    String fileName = pluginId + FILE_NAME_SEPARATOR + RandomStringUtils.randomAlphanumeric(RANDOM_STRING_LENGTH) + "."
        + fileExtension;
    String storageBucketUrl =
        cloudStorageUtil.uploadFile(customPluginsConfig.getBucketName(), filePath, fileName, fileInputStream);
    entity.setFileUrl(storageBucketUrl);
    CustomPluginV2Entity updatedEntity = customPluginV2Repository.save(entity);
    log.info("Uploaded HTML file for custom plugin v2 {} in account {}", pluginId, accountIdentifier);
    return CustomPluginV2Mapper.toDTO(updatedEntity);
  }

  @Override
  public void deleteHtmlFile(String accountIdentifier, String pluginId) {
    CustomPluginV2Entity entity = getEntityOrThrow(accountIdentifier, pluginId);

    if (StringUtils.isBlank(entity.getFileUrl())) {
      throw new NotFoundException(
          String.format("No HTML file found for plugin %s in account %s", pluginId, accountIdentifier));
    }

    cloudStorageUtil.deleteFile(entity.getFileUrl());
    entity.setFileUrl(null);
    customPluginV2Repository.save(entity);
    log.info("Deleted HTML file for custom plugin v2 {} in account {}", pluginId, accountIdentifier);
  }

  @Override
  public String getFileContent(String accountIdentifier, String pluginId) {
    CustomPluginV2Entity entity = getEntityOrThrow(accountIdentifier, pluginId);

    if (StringUtils.isBlank(entity.getFileUrl())) {
      throw new NotFoundException(
          String.format("No HTML file found for plugin %s in account %s", pluginId, accountIdentifier));
    }

    byte[] content = cloudStorageUtil.readFile(entity.getFileUrl());
    return new String(content, StandardCharsets.UTF_8);
  }

  private CustomPluginV2Entity getEntityOrThrow(String accountIdentifier, String pluginId) {
    Optional<CustomPluginV2Entity> entityOpt =
        customPluginV2Repository.findByAccountIdentifierAndIdentifier(accountIdentifier, pluginId);
    if (entityOpt.isEmpty()) {
      throw new NotFoundException(
          String.format("Custom plugin not found with identifier %s for account %s", pluginId, accountIdentifier));
    }
    return entityOpt.get();
  }

  private String getFilePath(String accountIdentifier) {
    return CUSTOM_PLUGINS_V2_DIR + FileUtils.PATH_SEPARATOR + env + FileUtils.PATH_SEPARATOR + accountIdentifier;
  }
}
