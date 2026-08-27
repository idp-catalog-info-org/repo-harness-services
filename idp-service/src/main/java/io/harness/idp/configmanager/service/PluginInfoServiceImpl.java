/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.idp.common.CommonUtils.readFileFromClassPath;
import static io.harness.idp.common.Constants.CUSTOM_PLUGIN_FILE_NAME;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.common.Constants.PLUGIN_REQUEST_NOTIFICATION_SLACK_WEBHOOK;
import static io.harness.idp.common.YamlUtils.yamlObject;
import static io.harness.idp.configmanager.utils.Constants.HARNESS_PLUGIN_IDS;
import static io.harness.notification.templates.PredefinedTemplate.IDP_PLUGIN_REQUESTS_NOTIFICATION_SLACK;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.FileType;
import io.harness.idp.common.FileUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.entities.CustomPluginInfoEntity;
import io.harness.idp.configmanager.entities.DefaultPluginInfoEntity;
import io.harness.idp.configmanager.entities.MarketPlacePluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginRequestEntity;
import io.harness.idp.configmanager.mappers.CustomPluginDetailedInfoMapper;
import io.harness.idp.configmanager.mappers.MarketPlacePluginInfoEntityMapper;
import io.harness.idp.configmanager.mappers.PluginDetailedInfoMapper;
import io.harness.idp.configmanager.mappers.PluginInfoMapper;
import io.harness.idp.configmanager.mappers.PluginInfoToPluginRequestMapper;
import io.harness.idp.configmanager.mappers.PluginRequestMapper;
import io.harness.idp.configmanager.repositories.PluginInfoRepository;
import io.harness.idp.configmanager.repositories.PluginRequestRepository;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.configmanager.utils.FetchMarketPlacePluginsUtil;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.plugin.config.CustomPluginsConfig;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.SlackChannel;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.Artifact;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.CustomPluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginInfo;
import io.harness.spec.server.idp.v1.model.PluginRequestStatus;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;
import io.harness.spec.server.idp.v1.model.RequestPlugin;
import io.harness.spec.server.idp.v1.model.RequestPluginByIdAndStatus;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.github.resilience4j.retry.Retry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.yaml.snakeyaml.Yaml;

@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class PluginInfoServiceImpl implements PluginInfoService {
  private static final String METADATA_FOLDER = "metadata/";
  static final int RANDOM_STRING_LENGTH = 6;
  static final String CUSTOM_PLUGIN_IDENTIFIER_FORMAT = "my_custom_plugin_%s";
  private static final String FILE_NAME_SEPARATOR = "_";
  private static final String GITHUB_API_URL = "https://api.github.com/repos/";
  private static final String KUBERNETES_PLUGIN_NAME = "backstage-kubernetes";
  private static final Retry retry =
      CommonUtils.buildRetryAndRegisterListeners(CustomPluginServiceImpl.class.getSimpleName());
  static final String METADATA_FILE_NAME = "metadata.yaml";
  private static final String PLUGINS_DIR = "plugins";
  static final String IMAGES_DIR = "static";
  private static final String HARNESS_PLUGIN_CONFIG = "configs/plugins/harness-plugin.yaml";
  private final PluginInfoRepository pluginInfoRepository;
  private final PluginRequestRepository pluginRequestRepository;
  private final ConfigManagerService configManagerService;
  private final ConfigEnvVariablesService configEnvVariablesService;
  private final BackstageEnvVariableService backstageEnvVariableService;
  private final PluginsProxyInfoService pluginsProxyInfoService;
  private final IdpCommonService idpCommonService;
  private final String env;
  private final HashMap<String, String> notificationConfigs;
  private final Map<PluginInfo.PluginTypeEnum, PluginDetailedInfoMapper> pluginDetailedInfoMapperMap;
  private final CloudStorageUtil cloudStorageUtil;
  private final CustomPluginService customPluginService;
  private final CustomPluginsConfig customPluginsConfig;
  private final NamespaceService namespaceService;
  private final String token;
  private static final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
  private static final Yaml yaml = yamlObject();

  @Inject
  public PluginInfoServiceImpl(PluginInfoRepository pluginInfoRepository,
      PluginRequestRepository pluginRequestRepository, ConfigManagerService configManagerService,
      ConfigEnvVariablesService configEnvVariablesService, BackstageEnvVariableService backstageEnvVariableService,
      PluginsProxyInfoService pluginsProxyInfoService, IdpCommonService idpCommonService, @Named("env") String env,
      @Named("notificationConfigs") HashMap<String, String> notificationConfigs,
      Map<PluginInfo.PluginTypeEnum, PluginDetailedInfoMapper> pluginDetailedInfoMapperMap,
      CloudStorageUtil cloudStorageUtil, CustomPluginService customPluginService,
      @Named("customPlugins") CustomPluginsConfig customPluginsConfig, NamespaceService namespaceService,
      @Named("idpAutomationGitHubToken") String token) {
    this.pluginInfoRepository = pluginInfoRepository;
    this.pluginRequestRepository = pluginRequestRepository;
    this.configManagerService = configManagerService;
    this.configEnvVariablesService = configEnvVariablesService;
    this.backstageEnvVariableService = backstageEnvVariableService;
    this.pluginsProxyInfoService = pluginsProxyInfoService;
    this.idpCommonService = idpCommonService;
    this.env = env;
    this.notificationConfigs = notificationConfigs;
    this.pluginDetailedInfoMapperMap = pluginDetailedInfoMapperMap;
    this.cloudStorageUtil = cloudStorageUtil;
    this.customPluginService = customPluginService;
    this.customPluginsConfig = customPluginsConfig;
    this.namespaceService = namespaceService;
    this.token = token;
  }

  @Override
  public List<PluginInfo> getAllPluginsInfo(String accountId, Boolean isDeleted) {
    List<PluginInfoEntity> plugins = pluginInfoRepository.findAllActivePlugins(accountId, isDeleted);
    return getPluginInfosFromPluginInfoEntities(accountId, plugins);
  }

  @Override
  public PluginDetailedInfo getPluginDetailedInfo(String identifier, String harnessAccount, boolean meta) {
    PluginInfoEntity pluginEntity;
    AppConfig appConfig = null;

    if (meta) {
      String schema = FileUtils.readFile(METADATA_FOLDER, CUSTOM_PLUGIN_FILE_NAME);
      try {
        pluginEntity = objectMapper.readValue(schema, CustomPluginInfoEntity.class);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Could not read default custom plugin metadata", e);
      }
    } else {
      Optional<PluginInfoEntity> pluginInfoEntity = pluginInfoRepository.findByIdentifierAndAccountIdentifierIn(
          identifier, addGlobalAccountIdentifierAlong(harnessAccount));
      if (pluginInfoEntity.isEmpty()) {
        throw new InvalidRequestException(String.format(
            "Plugin Info not found for plugin identifier [%s] for account [%s]", identifier, harnessAccount));
      }
      pluginEntity = pluginInfoEntity.get();
      appConfig = configManagerService.getAppConfig(harnessAccount, identifier, ConfigType.PLUGIN);
      if (HARNESS_PLUGIN_IDS.contains(pluginEntity.getIdentifier()) && appConfig == null) {
        String harnessConfig = readFileFromClassPath(HARNESS_PLUGIN_CONFIG);
        pluginEntity.setConfig(idpCommonService.getConfigWithEnvSpecificValuesReplaced(harnessConfig));
      }
    }

    List<BackstageEnvSecretVariable> backstageEnvSecretVariables =
        getPluginSecrets(appConfig, pluginEntity, harnessAccount, identifier);
    List<ProxyHostDetail> proxyHostDetails =
        pluginsProxyInfoService.getProxyHostDetailsForPluginId(harnessAccount, identifier);
    return getMapper(pluginEntity.getType())
        .toDto(pluginEntity, appConfig, backstageEnvSecretVariables, proxyHostDetails);
  }

  @Override
  public void saveAllPluginInfo() {
    try {
      Set<String> pluginMetadataFiles = FileUtils.readDirectory(PluginInfoServiceImpl.class, METADATA_FOLDER);
      List<String> accountIdentifiers = namespaceService.getActiveAccounts()
                                            .stream()
                                            .map(NamespaceEntity::getAccountIdentifier)
                                            .collect(Collectors.toList());
      pluginMetadataFiles.forEach(fileName -> {
        try {
          if (!fileName.equals(CUSTOM_PLUGIN_FILE_NAME)) {
            saveDefaultPluginInfo(fileName, accountIdentifiers);
          }
        } catch (Exception e) {
          String errorMessage =
              String.format("Error occurred while saving plugin details for plugin with file: [%s]", fileName);
          log.error(errorMessage, e);
        }
      });
    } catch (Exception e) {
      log.error("Error fetching file names from {} dir", METADATA_FOLDER, e);
    }
  }

  @Override
  public void deleteAllPluginInfo() {
    pluginInfoRepository.deleteAll();
  }

  @Override
  public RequestPlugin savePluginRequest(String harnessAccount, RequestPlugin pluginRequest) {
    PluginRequestEntity pluginRequestEntity = PluginRequestMapper.fromDTO(harnessAccount, pluginRequest);
    pluginRequestEntity = pluginRequestRepository.save(pluginRequestEntity);
    sendSlackNotificationForPluginRequest(harnessAccount, pluginRequestEntity);
    return PluginRequestMapper.toDTO(pluginRequestEntity);
  }

  @Override
  public void savePluginRequestV2(String harnessAccount, RequestPluginByIdAndStatus requestPluginByIdAndStatus) {
    String identifier = requestPluginByIdAndStatus.getIdentifier();
    PluginRequestStatus pluginRequestStatus = requestPluginByIdAndStatus.getStatus();
    Optional<PluginInfoEntity> existingPluginInfoOptional = pluginInfoRepository.findByIdentifier(identifier);

    if (existingPluginInfoOptional.isEmpty()) {
      throw new NotFoundException(
          String.format("Could not find plugin with identifier %s with status %s", identifier, pluginRequestStatus));
    }
    PluginInfoEntity pluginInfoEntity = existingPluginInfoOptional.get();
    PluginRequestEntity pluginRequestEntity =
        PluginInfoToPluginRequestMapper.toPluginRequestFromPluginInfo(pluginInfoEntity);
    pluginRequestEntity.setAccountIdentifier(harnessAccount);
    pluginRequestEntity.setStatus(pluginRequestStatus);
    pluginRequestRepository.save(pluginRequestEntity);
    sendSlackNotificationForPluginRequest(harnessAccount, pluginRequestEntity);
  }

  @Override
  public Page<PluginRequestEntity> getPluginRequests(String harnessAccount, int page, int limit) {
    Criteria criteria = createCriteriaForGetPluginRequests(harnessAccount);
    Pageable pageable = PageRequest.of(page, limit);
    return pluginRequestRepository.findAll(criteria, pageable);
  }

  @Override
  public PluginRequestEntity updatePluginRequest(
      String accountIdentifier, String identifier, PluginRequestStatus pluginStatus) {
    Optional<PluginRequestEntity> optionalPluginRequestEntity =
        pluginRequestRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    if (optionalPluginRequestEntity.isEmpty()) {
      throw new NotFoundException(String.format(
          "Could not find plugin request with identifier %s in account %s", identifier, accountIdentifier));
    }

    PluginRequestEntity pluginRequestEntity = optionalPluginRequestEntity.get();
    pluginRequestEntity.setStatus(pluginStatus);

    return pluginRequestRepository.update(pluginRequestEntity);
  }

  @Override
  public CustomPluginDetailedInfo generateIdentifierAndSaveCustomPluginInfo(
      String accountIdentifier, CustomPluginDetailedInfo customPluginDetailedInfo) {
    CustomPluginDetailedInfoMapper mapper = new CustomPluginDetailedInfoMapper();
    CustomPluginInfoEntity entity = mapper.fromDto(customPluginDetailedInfo, accountIdentifier);
    entity.setType(PluginInfo.PluginTypeEnum.CUSTOM);
    entity.setIdentifier(
        String.format(CUSTOM_PLUGIN_IDENTIFIER_FORMAT, RandomStringUtils.randomAlphanumeric(RANDOM_STRING_LENGTH)));
    CustomPluginInfoEntity savedEntity = pluginInfoRepository.save(entity);
    AppConfig appConfig =
        configManagerService.getAppConfig(accountIdentifier, savedEntity.getIdentifier(), ConfigType.PLUGIN);
    return buildDtoWithAdditionalDetails(savedEntity, accountIdentifier, appConfig);
  }

  @Override
  public CustomPluginDetailedInfo updatePluginInfo(
      String pluginId, CustomPluginDetailedInfo info, String accountIdentifier) {
    CustomPluginDetailedInfoMapper mapper = new CustomPluginDetailedInfoMapper();
    CustomPluginInfoEntity entity = mapper.fromDto(info, accountIdentifier);

    Optional<PluginInfoEntity> existingPluginInfoOptional =
        pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
            pluginId, accountIdentifier, PluginInfo.PluginTypeEnum.CUSTOM);
    if (existingPluginInfoOptional.isEmpty()) {
      throw new NotFoundException(
          String.format("Could not find plugin with identifier %s in account %s", pluginId, accountIdentifier));
    }

    CustomPluginInfoEntity customPluginInfoEntity = ((CustomPluginInfoEntity) existingPluginInfoOptional.get());
    String statusAPIURL = customPluginInfoEntity.getStatusApiUrl();
    entity.setStatusApiUrl(statusAPIURL);
    CustomPluginInfoEntity updatedEntity =
        (CustomPluginInfoEntity) pluginInfoRepository.update(pluginId, accountIdentifier, entity);
    AppConfig appConfig =
        configManagerService.getAppConfig(accountIdentifier, updatedEntity.getIdentifier(), ConfigType.PLUGIN);

    if (PluginInfoEntity.hasChanged(customPluginInfoEntity, updatedEntity) && appConfig != null
        && appConfig.isEnabled()) {
      updatePluginsMetadataOnGcs(accountIdentifier);
    }

    return buildDtoWithAdditionalDetails(updatedEntity, accountIdentifier, appConfig);
  }

  @Override
  public CustomPluginDetailedInfo uploadFile(String pluginId, String fileType, InputStream fileInputStream,
      FormDataContentDisposition fileDetail, String harnessAccount) {
    String fileExtension = FilenameUtils.getExtension(fileDetail.getFileName());
    if (!fileExtension.isBlank() && !FileUtils.isFileFormatSupported(fileType, fileExtension)) {
      throw new UnsupportedOperationException(
          "File format " + fileExtension + " is not supported. Plugin " + pluginId + ". Account " + harnessAccount);
    }

    String filePath = getFilePath(fileType, harnessAccount);
    String fileName = getFileNamePrefix(fileType, pluginId, harnessAccount)
        + RandomStringUtils.randomAlphanumeric(RANDOM_STRING_LENGTH) + "." + fileExtension;
    String gcsBucketUrl = cloudStorageUtil.uploadFile(getBucketName(fileType), filePath, fileName, fileInputStream);

    Optional<PluginInfoEntity> entityOpt = pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
        pluginId, harnessAccount, PluginInfo.PluginTypeEnum.CUSTOM);
    if (entityOpt.isEmpty()) {
      throw new NotFoundException(
          String.format("Could not find plugin details for plugin id %s and account %s", pluginId, harnessAccount));
    }
    PluginInfoEntity entity = entityOpt.get();
    CustomPluginDetailedInfoMapper mapper = new CustomPluginDetailedInfoMapper();
    mapper.addFileUploadDetails(entity, fileType, gcsBucketUrl);
    CustomPluginInfoEntity updatedEntity =
        (CustomPluginInfoEntity) pluginInfoRepository.update(pluginId, harnessAccount, entity);
    AppConfig appConfig =
        configManagerService.getAppConfig(harnessAccount, updatedEntity.getIdentifier(), ConfigType.PLUGIN);

    if (appConfig != null && appConfig.isEnabled()) {
      updatePluginsMetadataOnGcs(harnessAccount);
    }

    return buildDtoWithAdditionalDetails(updatedEntity, harnessAccount, appConfig);
  }

  @Override
  public CustomPluginDetailedInfo deleteFile(String pluginId, String fileType, String fileUrl, String harnessAccount) {
    CustomPluginDetailedInfoMapper mapper = new CustomPluginDetailedInfoMapper();
    cloudStorageUtil.deleteFile(fileUrl);
    Optional<PluginInfoEntity> entityOpt = pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
        pluginId, harnessAccount, PluginInfo.PluginTypeEnum.CUSTOM);
    if (entityOpt.isEmpty()) {
      throw new NotFoundException(
          String.format("Could not find plugin details for plugin id %s and account %s", pluginId, harnessAccount));
    }
    PluginInfoEntity entity = entityOpt.get();
    mapper.removeFileDetails(entity, fileType, fileUrl);
    CustomPluginInfoEntity updatedEntity =
        (CustomPluginInfoEntity) pluginInfoRepository.update(pluginId, harnessAccount, entity);
    AppConfig appConfig =
        configManagerService.getAppConfig(harnessAccount, updatedEntity.getIdentifier(), ConfigType.PLUGIN);
    return buildDtoWithAdditionalDetails(updatedEntity, harnessAccount, appConfig);
  }

  @Override
  public void deletePluginInfo(String pluginId, String harnessAccount, String pluginName) {
    Optional<PluginInfoEntity> optionalPluginInfoEntity =
        pluginInfoRepository.findByIdentifierAndAccountIdentifierIn(pluginId, Collections.singleton(harnessAccount));
    if (optionalPluginInfoEntity.isEmpty()) {
      throw new NotFoundException(
          String.format("Could not find plugin details for plugin id %s and account %s", pluginId, harnessAccount));
    }
    CustomPluginInfoEntity entity = (CustomPluginInfoEntity) optionalPluginInfoEntity.get();

    String iconUrl = entity.getIconUrl();
    if (StringUtils.isNotBlank(iconUrl)) {
      cloudStorageUtil.deleteFile(iconUrl);
    }

    Artifact artifact = entity.getArtifact();
    if (artifact != null) {
      String packageUrl = artifact.getUrl();
      if (StringUtils.isNotBlank(packageUrl)) {
        cloudStorageUtil.deleteFile(packageUrl);
      }
    }

    List<String> images = entity.getImages();
    if (images != null && !images.isEmpty()) {
      images.forEach(cloudStorageUtil::deleteFile);
    }

    pluginInfoRepository.delete(entity);
    configManagerService.deleteConfig(harnessAccount, pluginId, ConfigType.PLUGIN, pluginName);
  }

  @Override
  public void syncMarketPlacePlugins() {
    List<PluginInfo> integratedPlugins = getAllDefaultPlugins();
    List<PluginInfo> marketplacePlugins = getMarketPlacePlugins();

    Set<String> integratedPluginIds = integratedPlugins.stream().map(PluginInfo::getId).collect(Collectors.toSet());

    marketplacePlugins.removeIf(
        plugin -> (integratedPluginIds.contains(plugin.getId()) || KUBERNETES_PLUGIN_NAME.equals(plugin.getId())));

    List<PluginInfoEntity> marketPlacePluginsInDb =
        pluginInfoRepository.findByAccountIdentifierAndType(GLOBAL_ACCOUNT_ID, PluginInfo.PluginTypeEnum.MARKETPLACE);

    List<MarketPlacePluginInfoEntity> marketPlacePluginsInBackstageRepo =
        MarketPlacePluginInfoEntityMapper.toEntityList(marketplacePlugins);

    Map<String, PluginInfoEntity> dbPluginIdMap =
        marketPlacePluginsInDb.stream().collect(Collectors.toMap(PluginInfoEntity::getIdentifier, Function.identity()));

    List<PluginInfoEntity> newPluginsOnBackstage = new ArrayList<>();
    List<PluginInfoEntity> pluginsToUpdate = new ArrayList<>();

    for (MarketPlacePluginInfoEntity marketPlacePluginInfoEntity : marketPlacePluginsInBackstageRepo) {
      if (dbPluginIdMap.containsKey(marketPlacePluginInfoEntity.getIdentifier())) {
        PluginInfoEntity marketPlaceDbEntity = dbPluginIdMap.get(marketPlacePluginInfoEntity.getIdentifier());
        marketPlacePluginInfoEntity.setId(marketPlaceDbEntity.getId());
        if (MarketPlacePluginInfoEntity.hasChanged(marketPlacePluginInfoEntity, marketPlaceDbEntity)) {
          pluginsToUpdate.add(marketPlacePluginInfoEntity);
        }
      } else {
        newPluginsOnBackstage.add(marketPlacePluginInfoEntity);
      }
    }

    pluginInfoRepository.saveAll(newPluginsOnBackstage);
    pluginInfoRepository.saveAll(pluginsToUpdate);
  }

  @Override
  public void updatePluginsMetadataOnGcs(String accountIdentifier) {
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    List<PluginInfoEntity> customPlugins =
        pluginInfoRepository.findByAccountIdentifierAndType(accountIdentifier, PluginInfo.PluginTypeEnum.CUSTOM);
    Map<String, AppConfig> appConfigsByPluginId = configManagerService.getEnabledPluginsAppConfigs(accountIdentifier);
    StringBuilder yamlBuilder = new StringBuilder();
    for (PluginInfoEntity entity : customPlugins) {
      CustomPluginInfoEntity customPlugin = (CustomPluginInfoEntity) entity;
      boolean isEnabled = appConfigsByPluginId.containsKey(entity.getIdentifier());
      if (isEnabled) {
        String pluginYaml = createYaml(customPlugin, isEnabled);
        yamlBuilder.append(pluginYaml);
      }
    }
    String filePath = getArtifactFilePath(accountIdentifier);
    cloudStorageUtil.uploadFile(customPluginsConfig.getBucketName(), filePath, METADATA_FILE_NAME,
        new ByteArrayInputStream(yamlBuilder.toString().getBytes()));
  }

  private String getArtifactFilePath(String accountIdentifier) {
    return PLUGINS_DIR + FileUtils.PATH_SEPARATOR + env + FileUtils.PATH_SEPARATOR + accountIdentifier;
  }

  private String createYaml(CustomPluginInfoEntity entity, boolean isEnabled) {
    // Create a new object for YAML
    CustomPluginDetailedInfo info = new CustomPluginDetailedInfoMapper().toYamlDto(entity, isEnabled);
    try {
      return objectMapper.writeValueAsString(info);
    } catch (JsonProcessingException e) {
      throw new UnexpectedException("Error converting object to yaml string", e);
    }
  }

  private List<PluginInfo> getAllDefaultPlugins() {
    List<PluginInfoEntity> plugins =
        pluginInfoRepository.findByAccountIdentifierAndType(GLOBAL_ACCOUNT_ID, PluginInfo.PluginTypeEnum.DEFAULT);
    return getPluginInfosFromPluginInfoEntities(null, plugins);
  }

  private List<PluginInfo> getPluginInfosFromPluginInfoEntities(String accountId, List<PluginInfoEntity> plugins) {
    List<PluginInfo> pluginDTOs = new ArrayList<>();
    Map<String, Boolean> map = configManagerService.getAllPluginIdsMap(accountId);
    plugins.forEach(pluginInfoEntity -> {
      boolean isEnabled =
          map.containsKey(pluginInfoEntity.getIdentifier()) && map.get(pluginInfoEntity.getIdentifier());
      pluginDTOs.add(PluginInfoMapper.toDTO(pluginInfoEntity, isEnabled));
    });
    return pluginDTOs;
  }

  private String getFileNamePrefix(String fileType, String pluginId, String harnessAccount) {
    switch (FileType.valueOf(fileType)) {
      case ZIP:
        return pluginId + FILE_NAME_SEPARATOR;
      case ICON:
        return harnessAccount + FILE_NAME_SEPARATOR + pluginId + FileType.ICON.name() + FILE_NAME_SEPARATOR;
      case SCREENSHOT:
        return harnessAccount + FILE_NAME_SEPARATOR + pluginId + FileType.SCREENSHOT.name() + FILE_NAME_SEPARATOR;
      default:
        throw new UnsupportedOperationException("File type " + fileType + " is not supported");
    }
  }

  private String getFilePath(String fileType, String harnessAccount) {
    switch (FileType.valueOf(fileType)) {
      case ZIP:
        return getArtifactFilePath(harnessAccount);
      case ICON:
      case SCREENSHOT:
        return IMAGES_DIR;
      default:
        throw new UnsupportedOperationException("File type " + fileType + " is not supported");
    }
  }

  private String getBucketName(String fileType) {
    switch (FileType.valueOf(fileType)) {
      case ZIP:
        return customPluginsConfig.getBucketName();
      case ICON, SCREENSHOT:
        return customPluginsConfig.getImageBucketName();
      default:
        throw new UnsupportedOperationException("File type " + fileType + " is not supported");
    }
  }

  public void saveDefaultPluginInfo(String fileName, List<String> accountIdentifiers) throws Exception {
    String schema = FileUtils.readFile(METADATA_FOLDER, fileName);
    DefaultPluginInfoEntity pluginInfoEntity = objectMapper.readValue(schema, DefaultPluginInfoEntity.class);
    if (pluginInfoEntity.isDeleted()) {
      accountIdentifiers.forEach(accountIdentifier
          -> configManagerService.toggleAndSave(accountIdentifier, pluginInfoEntity.getIdentifier(), false,
              ConfigType.PLUGIN, pluginInfoEntity.getName()));
    }
    pluginInfoRepository.saveOrUpdate(pluginInfoEntity);
  }

  private List<BackstageEnvSecretVariable> getPluginSecrets(
      AppConfig appConfig, PluginInfoEntity pluginEntity, String harnessAccount, String identifier) {
    List<BackstageEnvSecretVariable> backstageEnvSecretVariables = new ArrayList<>();
    if (appConfig != null) {
      List<String> envNames =
          configEnvVariablesService.getAllEnvVariablesForAccountIdentifierAndPluginId(harnessAccount, identifier);
      if (CollectionUtils.isNotEmpty(envNames)) {
        backstageEnvSecretVariables =
            backstageEnvVariableService.getAllSecretIdentifierForMultipleEnvVariablesInAccount(
                harnessAccount, envNames);
      }
    } else if (pluginEntity.getEnvVariables() != null) {
      for (String envVariable : pluginEntity.getEnvVariables()) {
        BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
        backstageEnvSecretVariable.setEnvName(envVariable);
        backstageEnvSecretVariable.setHarnessSecretIdentifier(null);
        backstageEnvSecretVariables.add(backstageEnvSecretVariable);
      }
    }
    return backstageEnvSecretVariables;
  }

  private Criteria createCriteriaForGetPluginRequests(String harnessAccount) {
    Criteria criteria = new Criteria();
    criteria.and(PluginRequestEntity.PluginRequestKeys.accountIdentifier).is(harnessAccount);
    return criteria;
  }

  private void sendSlackNotificationForPluginRequest(String harnessAccount, PluginRequestEntity pluginRequestEntity) {
    SlackChannel slackChannel =
        SlackChannel.builder()
            .accountId(harnessAccount)
            .userGroups(Collections.emptyList())
            .templateId(IDP_PLUGIN_REQUESTS_NOTIFICATION_SLACK.getIdentifier())
            .templateData(pluginRequestEntity.toMap())
            .team(Team.IDP)
            .webhookUrls(Collections.singletonList(notificationConfigs.get(PLUGIN_REQUEST_NOTIFICATION_SLACK_WEBHOOK)))
            .build();
    idpCommonService.sendSlackNotification(slackChannel);
  }

  private PluginDetailedInfoMapper getMapper(PluginInfo.PluginTypeEnum pluginType) {
    PluginDetailedInfoMapper mapper = pluginDetailedInfoMapperMap.get(pluginType);
    if (mapper == null) {
      throw new InvalidRequestException("Plugin type not set");
    }
    return mapper;
  }

  private CustomPluginDetailedInfo buildDtoWithAdditionalDetails(
      PluginInfoEntity pluginEntity, String harnessAccount, AppConfig appConfig) {
    CustomPluginDetailedInfoMapper mapper = new CustomPluginDetailedInfoMapper();
    if (HARNESS_PLUGIN_IDS.contains(pluginEntity.getIdentifier()) && appConfig == null) {
      String harnessConfig = readFileFromClassPath(HARNESS_PLUGIN_CONFIG);
      pluginEntity.setConfig(idpCommonService.getConfigWithEnvSpecificValuesReplaced(harnessConfig));
    }

    List<BackstageEnvSecretVariable> backstageEnvSecretVariables =
        getPluginSecrets(appConfig, pluginEntity, harnessAccount, pluginEntity.getIdentifier());
    List<ProxyHostDetail> proxyHostDetails =
        pluginsProxyInfoService.getProxyHostDetailsForPluginId(harnessAccount, pluginEntity.getIdentifier());
    return mapper.toDto(
        (CustomPluginInfoEntity) pluginEntity, appConfig, backstageEnvSecretVariables, proxyHostDetails);
  }

  private List<PluginInfo> getMarketPlacePlugins() {
    final String directoryPath = "microsite/data/plugins";

    List<PluginInfo> pluginsFromMarketPlace = new ArrayList<>();
    try {
      List<String> files = listFilesInDirectory(directoryPath);

      for (String file : files) {
        String fileContent = fetchFileContent(file);
        PluginInfo createdPluginInfo = parsePluginInfo(file, fileContent);
        if (createdPluginInfo != null) {
          pluginsFromMarketPlace.add(createdPluginInfo);
        }
      }
    } catch (IOException e) {
      String errorMessage = "Error occurred while parsing the MarketPlace plugins.";
      log.error(errorMessage, e);
    }
    return pluginsFromMarketPlace;
  }

  private PluginInfo parsePluginInfo(String name, String content) {
    PluginInfo pluginInfo = new PluginInfo();
    Map<String, String> fields = yaml.load(content);
    if (fields.get("title") == null || fields.get("category") == null || fields.get("authorUrl") == null
        || fields.get("description") == null || fields.get("documentation") == null
        || fields.get("npmPackageName") == null || fields.get("iconUrl") == null || fields.get("author") == null) {
      return null;
    }
    pluginInfo.setId(extractName(name));
    pluginInfo.setName(fields.get("title"));
    pluginInfo.setSource(fields.get("authorUrl"));
    pluginInfo.setCategory(fields.get("category"));
    pluginInfo.setDescription(fields.get("description"));
    pluginInfo.setDocumentation(fields.get("documentation"));
    pluginInfo.setIconUrl(fields.get("iconUrl"));
    pluginInfo.createdBy(fields.get("author"));
    pluginInfo.setSource(fields.get("npmPackageName"));

    return pluginInfo;
  }

  private String extractName(String filePath) {
    int lastSlashIndex = filePath.lastIndexOf('/');
    int lastDotIndex = filePath.lastIndexOf('.');

    if (lastSlashIndex != -1 && lastDotIndex != -1 && lastSlashIndex < lastDotIndex) {
      return filePath.substring(lastSlashIndex + 1, lastDotIndex);
    }
    return "";
  }

  private List<String> listFilesInDirectory(String path) throws IOException {
    String url = GITHUB_API_URL + "backstage/backstage/contents/" + path;
    return extractFilePathsFromJson(FetchMarketPlacePluginsUtil.fetch(url, retry, token));
  }

  private List<String> extractFilePathsFromJson(String jsonResponse) {
    List<String> filePaths = new ArrayList<>();
    JSONArray jsonArray = new JSONArray(jsonResponse);

    for (int i = 0; i < jsonArray.length(); i++) {
      JSONObject jsonObject = jsonArray.getJSONObject(i);
      if (jsonObject.getString("type").equals("file")) {
        filePaths.add(jsonObject.getString("path"));
      }
    }

    return filePaths;
  }

  private String fetchFileContent(String filePath) throws IOException {
    String url = GITHUB_API_URL + "backstage/backstage/contents/" + filePath;
    return extractFileContentFromJson(FetchMarketPlacePluginsUtil.fetch(url, retry, token));
  }

  private String extractFileContentFromJson(String jsonResponse) {
    JSONObject jsonObject = new JSONObject(jsonResponse);
    String encodedContent = jsonObject.getString("content");

    encodedContent = encodedContent.replaceAll("\\s+", "");

    byte[] decodedBytes = Base64.getDecoder().decode(encodedContent);
    return new String(decodedBytes);
  }
}
