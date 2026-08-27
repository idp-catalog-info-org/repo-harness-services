/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import static io.harness.idp.common.Constants.PLUGIN_REQUEST_NOTIFICATION_SLACK_WEBHOOK;
import static io.harness.idp.configmanager.service.PluginInfoServiceImpl.CUSTOM_PLUGIN_IDENTIFIER_FORMAT;
import static io.harness.idp.configmanager.service.PluginInfoServiceImpl.IMAGES_DIR;
import static io.harness.idp.configmanager.service.PluginInfoServiceImpl.METADATA_FILE_NAME;
import static io.harness.idp.configmanager.service.PluginInfoServiceImpl.RANDOM_STRING_LENGTH;
import static io.harness.rule.OwnerRule.AJINKYA_SHINGANE;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.common.FileType;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.beans.ExportsData;
import io.harness.idp.configmanager.entities.CustomPluginInfoEntity;
import io.harness.idp.configmanager.entities.DefaultPluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginInfoEntity;
import io.harness.idp.configmanager.entities.PluginRequestEntity;
import io.harness.idp.configmanager.enums.ExportType;
import io.harness.idp.configmanager.mappers.CustomPluginDetailedInfoMapper;
import io.harness.idp.configmanager.mappers.DefaultPluginDetailedInfoMapper;
import io.harness.idp.configmanager.mappers.PluginDetailedInfoMapper;
import io.harness.idp.configmanager.repositories.PluginInfoRepository;
import io.harness.idp.configmanager.repositories.PluginRequestRepository;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.configmanager.utils.FetchMarketPlacePluginsUtil;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.plugin.config.CustomPluginsConfig;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.Artifact;
import io.harness.spec.server.idp.v1.model.CustomPluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginInfo;
import io.harness.spec.server.idp.v1.model.PluginRequestStatus;
import io.harness.spec.server.idp.v1.model.RequestPlugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang.RandomStringUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(HarnessTeam.IDP)
public class PluginInfoServiceImplTest {
  private static final String CUSTOM_PLUGINS_BUCKET_NAME = "idp-custom-plugins";
  private static final String CUSTOM_PLUGIN_IDENTIFIER_PREFIX = "my_custom_plugin_";
  private static final String CUSTOM_PLUGIN_ID = "my_custom_plugin_odkfjvw";
  private static final String CUSTOM_PLUGIN_NAME = "test-cutome-plugin-name";
  private static final String TEST_GCS_BUCKET_URL =
      "https://storage.googleapis.com/idp-custom-plugins/static/harness.png";
  private PluginInfoServiceImpl pluginInfoServiceImpl;
  @Mock private PluginInfoRepository pluginInfoRepository;
  @Mock private PluginRequestRepository pluginRequestRepository;
  @Mock private ConfigManagerService configManagerService;
  @Mock private ConfigEnvVariablesService configEnvVariablesService;
  @Mock private BackstageEnvVariableService backstageEnvVariableService;
  @Mock private PluginsProxyInfoService pluginsProxyInfoService;
  @Mock private IdpCommonService idpCommonService;
  @Mock private FetchMarketPlacePluginsUtil fetchMarketPlacePluginsUtil;
  @Mock private HashMap<String, String> notificationConfigs = new HashMap<>();
  @Mock private CloudStorageUtil cloudStorageUtil;
  @Mock private CustomPluginService customPluginService;
  @Mock private CustomPluginsConfig customPluginsConfig;
  @Mock private NamespaceService namespaceService;
  private Map<PluginInfo.PluginTypeEnum, PluginDetailedInfoMapper> mapBinder;
  private final ObjectMapper objectMapper = mock(ObjectMapper.class);

  private static final String ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private static final String TEST_ACCOUNT_ID = "accountId";
  private static final String PAGER_DUTY_NAME = "PagerDuty";
  private static final String PAGER_DUTY_ID = "pager-duty";
  private static final String HARNESS_CI_CD_NAME = "Harnes CI/CD";
  private static final String HARNESS_CI_CD_ID = "harness-ci-cd";
  private static final String INVALID_PLUGIN_ID = "invalid-plugin";

  private static final String PLUGIN_REQUEST_NAME = "pluginName";
  private static final String PLUGIN_REQUEST_CREATOR = "foo";
  private static final String PLUGIN_REQUEST_PACKAGE_LINK = "https://www.harness.io";
  private static final String PLUGIN_REQUEST_DOC_LINK = "https://www.harness.io";
  @Captor private ArgumentCaptor<PluginInfoEntity> pluginInfoEntityCaptor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    PluginDetailedInfoMapper<?, ?> defaultPluginDetailedInfoMapper = new DefaultPluginDetailedInfoMapper();
    PluginDetailedInfoMapper<?, ?> customPluginDetailedInfoMapper = new CustomPluginDetailedInfoMapper();
    mapBinder = Map.of(PluginInfo.PluginTypeEnum.DEFAULT, defaultPluginDetailedInfoMapper,
        PluginInfo.PluginTypeEnum.CUSTOM, customPluginDetailedInfoMapper);
    when(customPluginsConfig.getBucketName()).thenReturn(CUSTOM_PLUGINS_BUCKET_NAME);
    pluginInfoServiceImpl = new PluginInfoServiceImpl(pluginInfoRepository, pluginRequestRepository,
        configManagerService, configEnvVariablesService, backstageEnvVariableService, pluginsProxyInfoService,
        idpCommonService, "local", notificationConfigs, mapBinder, cloudStorageUtil, customPluginService,
        customPluginsConfig, namespaceService, "test");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetAllPluginsInfo() {
    List<PluginInfoEntity> pluginInfoEntityList = new ArrayList<>();
    pluginInfoEntityList.add(getPagerDutyInfoEntity());
    pluginInfoEntityList.add(getHarnessCICDInfoEntity());
    when(pluginInfoRepository.findAllActivePlugins(any(), any())).thenReturn(pluginInfoEntityList);
    Map<String, Boolean> map = new HashMap<>();
    map.put(PAGER_DUTY_ID, false);
    map.put(HARNESS_CI_CD_ID, true);
    when(configManagerService.getAllPluginIdsMap(ACCOUNT_ID)).thenReturn(map);
    List<PluginInfo> pluginDTOs = pluginInfoServiceImpl.getAllPluginsInfo(ACCOUNT_ID, false);
    assertEquals(2, pluginDTOs.size());
    assertFalse(pluginDTOs.get(0).isEnabled());
    assertTrue(pluginDTOs.get(1).isEnabled());
  }

  @Test
  @Owner(developers = AJINKYA_SHINGANE)
  @Category(UnitTests.class)
  public void testTransferMarketPlacePlugins() throws IOException {
    List<PluginInfoEntity> integratedPlugins = new ArrayList<>();
    integratedPlugins.add(getPagerDutyInfoEntity());
    integratedPlugins.add(getHarnessCICDInfoEntity());
    when(pluginInfoRepository.findAllActivePlugins(any(), any())).thenReturn(integratedPlugins);

    ClassLoader classLoader = this.getClass().getClassLoader();
    URL fileListResponseTXT = classLoader.getResource("responses/fileListResponse.txt");
    String fileListResponse = Resources.toString(fileListResponseTXT, Charsets.UTF_8);

    ClassLoader classLoader2 = this.getClass().getClassLoader();
    URL fileContentTXT = classLoader2.getResource("responses/fileContent.txt");
    String fileContent = Resources.toString(fileContentTXT, Charsets.UTF_8);

    try (MockedStatic<FetchMarketPlacePluginsUtil> mockRestUtils =
             Mockito.mockStatic(FetchMarketPlacePluginsUtil.class)) {
      when(FetchMarketPlacePluginsUtil.fetch(any(), any(), any())).thenReturn(fileListResponse).thenReturn(fileContent);
      pluginInfoServiceImpl.syncMarketPlacePlugins();
      mockRestUtils.verify(() -> FetchMarketPlacePluginsUtil.fetch(any(), any(), any()), times(2));
    }
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetPluginDetailedInfo() {
    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierIn(PAGER_DUTY_ID, Collections.singleton(ACCOUNT_ID)))
        .thenReturn(Optional.of(getPagerDutyInfoEntity()));
    when(configManagerService.getAppConfig(ACCOUNT_ID, PAGER_DUTY_ID, ConfigType.PLUGIN)).thenReturn(null);
    when(pluginsProxyInfoService.getProxyHostDetailsForPluginId(ACCOUNT_ID, PAGER_DUTY_ID))
        .thenReturn(new ArrayList<>());
    PluginDetailedInfo pluginDetailedInfo =
        pluginInfoServiceImpl.getPluginDetailedInfo(PAGER_DUTY_ID, ACCOUNT_ID, false);
    assertNotNull(pluginDetailedInfo);
    assertFalse(pluginDetailedInfo.getPluginDetails().isEnabled());
    assertEquals(1, (int) pluginDetailedInfo.getExports().getCards());
    assertEquals(0, (int) pluginDetailedInfo.getExports().getPages());
    assertEquals(0, (int) pluginDetailedInfo.getExports().getTabContents());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetPluginDetailedInfoThrowsException() {
    when(pluginInfoRepository.findByIdentifier(INVALID_PLUGIN_ID)).thenReturn(Optional.empty());
    pluginInfoServiceImpl.getPluginDetailedInfo(INVALID_PLUGIN_ID, ACCOUNT_ID, false);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetPluginDetailedInfoMeta() {
    CustomPluginInfoEntity entity = CustomPluginInfoEntity.builder().build();
    entity.setIdentifier(CUSTOM_PLUGIN_ID);
    entity.setAccountIdentifier(TEST_ACCOUNT_ID);
    entity.setType(PluginInfo.PluginTypeEnum.CUSTOM);

    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierIn(
             CUSTOM_PLUGIN_ID, Collections.singleton(TEST_ACCOUNT_ID)))
        .thenReturn(Optional.of(entity));
    when(configManagerService.getAppConfig(TEST_ACCOUNT_ID, CUSTOM_PLUGIN_ID, ConfigType.PLUGIN)).thenReturn(null);
    when(pluginsProxyInfoService.getProxyHostDetailsForPluginId(TEST_ACCOUNT_ID, CUSTOM_PLUGIN_ID))
        .thenReturn(new ArrayList<>());

    PluginDetailedInfo pluginDetailedInfo =
        pluginInfoServiceImpl.getPluginDetailedInfo(CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, true);

    assertNotNull(pluginDetailedInfo);
    assertFalse(pluginDetailedInfo.getPluginDetails().isEnabled());
    assertEquals(1, pluginDetailedInfo.getEnvVariables().size());
    assertEquals("MY_CUSTOM_PLUGIN_TOKEN", pluginDetailedInfo.getEnvVariables().get(0).getEnvName());
    assertEquals(0, (int) pluginDetailedInfo.getExports().getCards());
    assertEquals(0, (int) pluginDetailedInfo.getExports().getPages());
    assertEquals(0, (int) pluginDetailedInfo.getExports().getTabContents());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveAllPluginInfo() {
    PluginInfoEntity pluginInfoEntity = DefaultPluginInfoEntity.builder().build();
    when(pluginInfoRepository.saveOrUpdate(any(PluginInfoEntity.class))).thenReturn(pluginInfoEntity);
    pluginInfoServiceImpl.saveAllPluginInfo();
    verify(pluginInfoRepository, times(45)).saveOrUpdate(pluginInfoEntityCaptor.capture());
    long deletedPlugins = pluginInfoEntityCaptor.getAllValues()
                              .stream()
                              .filter(entity -> ((DefaultPluginInfoEntity) entity).isDeleted())
                              .count();
    assertEquals(3, deletedPlugins); // 22 active plugins
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteAllPluginInfo() {
    doNothing().when(pluginInfoRepository).deleteAll();
    pluginInfoServiceImpl.deleteAllPluginInfo();
    verify(pluginInfoRepository).deleteAll();
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSavePluginRequest() {
    PluginRequestEntity pluginRequestEntity = PluginRequestEntity.builder()
                                                  .createdBy(EmbeddedUser.builder().build())
                                                  .status(PluginRequestStatus.IN_PROGRESS)
                                                  .build();
    when(pluginRequestRepository.save(any(PluginRequestEntity.class))).thenReturn(pluginRequestEntity);
    when(notificationConfigs.get(PLUGIN_REQUEST_NOTIFICATION_SLACK_WEBHOOK)).thenReturn("");
    doNothing().when(idpCommonService).sendSlackNotification(any());
    pluginInfoServiceImpl.savePluginRequest(ACCOUNT_ID, getRequestPlugin());
    verify(pluginRequestRepository, times(1)).save(any(PluginRequestEntity.class));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetPluginRequests() {
    when(pluginRequestRepository.findAll(any(), any())).thenReturn(getPagePluginRequestEntity());
    Page<PluginRequestEntity> pluginRequestEntityPage = pluginInfoServiceImpl.getPluginRequests(ACCOUNT_ID, 0, 10);
    assertEquals(1, pluginRequestEntityPage.getTotalElements());
    assertEquals(1, pluginRequestEntityPage.getContent().size());
    assertThat(pluginRequestEntityPage.getContent().get(0).getName()).isEqualTo(PLUGIN_REQUEST_NAME);
    assertThat(pluginRequestEntityPage.getContent().get(0).getCreator()).isEqualTo(PLUGIN_REQUEST_CREATOR);
    assertThat(pluginRequestEntityPage.getContent().get(0).getPackageLink()).isEqualTo(PLUGIN_REQUEST_PACKAGE_LINK);
    assertThat(pluginRequestEntityPage.getContent().get(0).getDocLink()).isEqualTo(PLUGIN_REQUEST_DOC_LINK);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGenerateIdentifierAndSaveCustomPluginInfo() {
    CustomPluginInfoEntity entity = CustomPluginInfoEntity.builder().build();
    entity.setType(PluginInfo.PluginTypeEnum.CUSTOM);
    entity.setIdentifier(
        String.format(CUSTOM_PLUGIN_IDENTIFIER_FORMAT, RandomStringUtils.randomAlphanumeric(RANDOM_STRING_LENGTH)));
    when(pluginInfoRepository.save(any())).thenReturn(entity);

    CustomPluginDetailedInfo inputPluginInfo = new CustomPluginDetailedInfo();
    PluginInfo pluginDetails = new PluginInfo();
    inputPluginInfo.setPluginDetails(pluginDetails);

    when(configManagerService.getAppConfig(eq(TEST_ACCOUNT_ID), anyString(), eq(ConfigType.PLUGIN)))
        .thenReturn(new AppConfig());

    CustomPluginDetailedInfo customPluginInfo =
        pluginInfoServiceImpl.generateIdentifierAndSaveCustomPluginInfo(TEST_ACCOUNT_ID, inputPluginInfo);
    PluginInfo details = customPluginInfo.getPluginDetails();

    assertEquals(PluginInfo.PluginTypeEnum.CUSTOM, details.getPluginType());
    assertNotNull(details.getId());
    assertTrue(details.getId().startsWith(CUSTOM_PLUGIN_IDENTIFIER_PREFIX));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUploadFile() throws FileNotFoundException {
    File file = new File("idp-service/src/test/resources/images/harness.png");
    FileInputStream fileStream = new FileInputStream(file);
    FormDataContentDisposition disposition = FormDataContentDisposition.name("file")
                                                 .fileName(URLEncoder.encode("harness.png", StandardCharsets.UTF_8))
                                                 .build();
    CustomPluginInfoEntity entity = CustomPluginInfoEntity.builder().build();
    entity.setIdentifier(CUSTOM_PLUGIN_ID);
    entity.setAccountIdentifier(TEST_ACCOUNT_ID);
    CustomPluginInfoEntity updatedEntity = CustomPluginInfoEntity.builder().build();
    updatedEntity.setIdentifier(CUSTOM_PLUGIN_ID);
    updatedEntity.setAccountIdentifier(TEST_ACCOUNT_ID);

    when(cloudStorageUtil.uploadFile(eq(CUSTOM_PLUGINS_BUCKET_NAME), eq(IMAGES_DIR), any(), any()))
        .thenReturn(TEST_GCS_BUCKET_URL);
    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.of(entity));
    List<String> images = new ArrayList<>();
    images.add(TEST_GCS_BUCKET_URL);
    updatedEntity.setImages(images);

    when(configManagerService.getAppConfig(TEST_ACCOUNT_ID, CUSTOM_PLUGIN_ID, ConfigType.PLUGIN))
        .thenReturn(mockAppConfig());
    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.of(entity));
    when(pluginInfoRepository.findByAccountIdentifierAndType(TEST_ACCOUNT_ID, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Collections.singletonList(updatedEntity));
    when(pluginInfoRepository.update(CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, updatedEntity)).thenReturn(updatedEntity);

    CustomPluginDetailedInfo info = pluginInfoServiceImpl.uploadFile(
        CUSTOM_PLUGIN_ID, FileType.SCREENSHOT.name(), fileStream, disposition, TEST_ACCOUNT_ID);

    verify(cloudStorageUtil)
        .uploadFile(eq(CUSTOM_PLUGINS_BUCKET_NAME), eq("plugins/local/accountId"), eq(METADATA_FILE_NAME), any());
    assertFalse(info.getPluginDetails().getImages().isEmpty());
    assertEquals(TEST_GCS_BUCKET_URL, info.getPluginDetails().getImages().get(0));
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUploadFileInvalidFileType() throws FileNotFoundException {
    File file = new File("idp-service/src/test/resources/images/harness.png");
    FileInputStream fileStream = new FileInputStream(file);
    FormDataContentDisposition disposition = FormDataContentDisposition.name("file")
                                                 .fileName(URLEncoder.encode("harness.xml", StandardCharsets.UTF_8))
                                                 .build();

    pluginInfoServiceImpl.uploadFile(
        CUSTOM_PLUGIN_ID, FileType.SCREENSHOT.name(), fileStream, disposition, TEST_ACCOUNT_ID);

    verify(cloudStorageUtil, never())
        .uploadFile(eq(CUSTOM_PLUGINS_BUCKET_NAME), eq("plugins/local/accountId"), eq(METADATA_FILE_NAME), any());
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUploadFileMissingPlugin() throws FileNotFoundException {
    File file = new File("idp-service/src/test/resources/images/harness.png");
    FileInputStream fileStream = new FileInputStream(file);
    FormDataContentDisposition disposition = FormDataContentDisposition.name("file")
                                                 .fileName(URLEncoder.encode("harness.png", StandardCharsets.UTF_8))
                                                 .build();

    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.empty());

    pluginInfoServiceImpl.uploadFile(
        CUSTOM_PLUGIN_ID, FileType.SCREENSHOT.name(), fileStream, disposition, TEST_ACCOUNT_ID);

    verify(cloudStorageUtil, never())
        .uploadFile(eq(CUSTOM_PLUGINS_BUCKET_NAME), eq("plugins/local/accountId"), eq(METADATA_FILE_NAME), any());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteFileScreenshot() {
    CustomPluginInfoEntity entity = CustomPluginInfoEntity.builder().build();
    List<String> images = new ArrayList<>();
    images.add(TEST_GCS_BUCKET_URL);
    entity.setIdentifier(CUSTOM_PLUGIN_ID);
    entity.setAccountIdentifier(TEST_ACCOUNT_ID);
    entity.setImages(images);
    CustomPluginInfoEntity updatedEntity = CustomPluginInfoEntity.builder().build();
    updatedEntity.setIdentifier(CUSTOM_PLUGIN_ID);
    updatedEntity.setAccountIdentifier(TEST_ACCOUNT_ID);
    updatedEntity.setImages(Collections.emptyList());

    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.of(entity));
    when(pluginInfoRepository.update(CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, updatedEntity)).thenReturn(updatedEntity);

    CustomPluginDetailedInfo info = pluginInfoServiceImpl.deleteFile(
        CUSTOM_PLUGIN_ID, FileType.SCREENSHOT.name(), TEST_GCS_BUCKET_URL, TEST_ACCOUNT_ID);

    verify(cloudStorageUtil).deleteFile(TEST_GCS_BUCKET_URL);
    assertTrue(info.getPluginDetails().getImages().isEmpty());
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteFileMissingPlugin() {
    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.empty());

    pluginInfoServiceImpl.deleteFile(
        CUSTOM_PLUGIN_ID, FileType.SCREENSHOT.name(), TEST_GCS_BUCKET_URL, TEST_ACCOUNT_ID);

    verify(cloudStorageUtil, never()).deleteFile(TEST_GCS_BUCKET_URL);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdatePluginInfo() {
    Artifact artifact = new Artifact();
    artifact.setUrl(TEST_GCS_BUCKET_URL);
    artifact.setType(Artifact.TypeEnum.ZIP);
    List<String> images = new ArrayList<>();
    images.add(TEST_GCS_BUCKET_URL);
    CustomPluginDetailedInfo info = new CustomPluginDetailedInfo();
    PluginInfo pluginDetails = new PluginInfo();
    pluginDetails.setId(CUSTOM_PLUGIN_ID);
    pluginDetails.setPluginType(PluginInfo.PluginTypeEnum.CUSTOM);
    pluginDetails.setIconUrl(TEST_GCS_BUCKET_URL);
    pluginDetails.setImages(images);
    info.setPluginDetails(pluginDetails);
    info.setArtifact(artifact);
    CustomPluginDetailedInfoMapper mapper = new CustomPluginDetailedInfoMapper();
    CustomPluginInfoEntity entity = mapper.fromDto(info, TEST_ACCOUNT_ID);
    entity.setIdentifier(CUSTOM_PLUGIN_ID);

    pluginDetails.setName(CUSTOM_PLUGIN_ID);
    info.setPluginDetails(pluginDetails);
    CustomPluginInfoEntity updatedEntity = mapper.fromDto(info, TEST_ACCOUNT_ID);
    updatedEntity.setIdentifier(CUSTOM_PLUGIN_ID);

    when(configManagerService.getAppConfig(TEST_ACCOUNT_ID, CUSTOM_PLUGIN_ID, ConfigType.PLUGIN))
        .thenReturn(mockAppConfig());
    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierAndType(
             CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, PluginInfo.PluginTypeEnum.CUSTOM))
        .thenReturn(Optional.of(entity));
    when(pluginInfoRepository.update(CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, updatedEntity)).thenReturn(updatedEntity);

    CustomPluginDetailedInfo updatedInfo =
        pluginInfoServiceImpl.updatePluginInfo(CUSTOM_PLUGIN_ID, info, TEST_ACCOUNT_ID);

    verify(cloudStorageUtil)
        .uploadFile(eq(CUSTOM_PLUGINS_BUCKET_NAME), eq("plugins/local/accountId"), eq(METADATA_FILE_NAME), any());
    assertEquals(pluginDetails.getIconUrl(), updatedInfo.getPluginDetails().getIconUrl());
    assertEquals(pluginDetails.getImages(), updatedInfo.getPluginDetails().getImages());
    assertEquals(info.getArtifact(), updatedInfo.getArtifact());
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdatePluginInfoMissingPlugin() {
    CustomPluginDetailedInfo info = new CustomPluginDetailedInfo();
    PluginInfo pluginDetails = new PluginInfo();
    pluginDetails.setId(CUSTOM_PLUGIN_ID);
    pluginDetails.setPluginType(PluginInfo.PluginTypeEnum.CUSTOM);
    pluginDetails.setIconUrl(TEST_GCS_BUCKET_URL);
    info.setPluginDetails(pluginDetails);

    when(pluginInfoRepository.update(eq(CUSTOM_PLUGIN_ID), eq(TEST_ACCOUNT_ID), any())).thenReturn(null);

    pluginInfoServiceImpl.updatePluginInfo(CUSTOM_PLUGIN_ID, info, TEST_ACCOUNT_ID);

    verify(customPluginService, never()).triggerBuildPipeline(TEST_ACCOUNT_ID, CUSTOM_PLUGIN_ID);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeletePluginInfo() {
    Artifact artifact = new Artifact();
    artifact.setUrl(TEST_GCS_BUCKET_URL);
    artifact.setType(Artifact.TypeEnum.ZIP);
    List<String> images = new ArrayList<>();
    images.add(TEST_GCS_BUCKET_URL);
    CustomPluginInfoEntity entity = CustomPluginInfoEntity.builder().build();
    entity.setIdentifier(CUSTOM_PLUGIN_ID);
    entity.setAccountIdentifier(TEST_ACCOUNT_ID);
    entity.setIconUrl(TEST_GCS_BUCKET_URL);
    entity.setImages(images);
    entity.setArtifact(artifact);

    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierIn(
             CUSTOM_PLUGIN_ID, Collections.singleton(TEST_ACCOUNT_ID)))
        .thenReturn(Optional.of(entity));

    pluginInfoServiceImpl.deletePluginInfo(CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, CUSTOM_PLUGIN_NAME);

    verify(pluginInfoRepository).delete(entity);
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeletePluginInfoMissingPlugin() {
    when(pluginInfoRepository.findByIdentifierAndAccountIdentifierIn(
             CUSTOM_PLUGIN_ID, Collections.singleton(TEST_ACCOUNT_ID)))
        .thenReturn(Optional.empty());

    pluginInfoServiceImpl.deletePluginInfo(CUSTOM_PLUGIN_ID, TEST_ACCOUNT_ID, CUSTOM_PLUGIN_NAME);

    verify(cloudStorageUtil, times(3)).deleteFile(TEST_GCS_BUCKET_URL);
    verify(pluginInfoRepository, never()).delete(any());
  }

  private PluginInfoEntity getPagerDutyInfoEntity() {
    List<ExportsData.ExportDetails> exportDetails = new ArrayList<>();
    ExportsData.ExportDetails export = new ExportsData.ExportDetails();
    export.setType(ExportType.CARD);
    export.setName("EntityPagerDutyCard");
    exportDetails.add(export);
    ExportsData exportsData = new ExportsData();
    exportsData.setExportDetails(exportDetails);
    DefaultPluginInfoEntity entity = DefaultPluginInfoEntity.builder().core(false).build();
    entity.setName(PAGER_DUTY_NAME);
    entity.setIdentifier(PAGER_DUTY_ID);
    entity.setExports(exportsData);
    entity.setType(PluginInfo.PluginTypeEnum.DEFAULT);
    return entity;
  }

  private PluginInfoEntity getHarnessCICDInfoEntity() {
    DefaultPluginInfoEntity entity = DefaultPluginInfoEntity.builder().core(false).build();
    entity.setName(HARNESS_CI_CD_NAME);
    entity.setIdentifier(HARNESS_CI_CD_ID);
    entity.setExports(new ExportsData());
    return entity;
  }

  private RequestPlugin getRequestPlugin() {
    RequestPlugin requestPlugin = new RequestPlugin();
    requestPlugin.setName(PLUGIN_REQUEST_NAME);
    requestPlugin.setCreator(PLUGIN_REQUEST_CREATOR);
    requestPlugin.setPackageLink(PLUGIN_REQUEST_PACKAGE_LINK);
    requestPlugin.setDocLink(PLUGIN_REQUEST_DOC_LINK);
    return requestPlugin;
  }

  private Page<PluginRequestEntity> getPagePluginRequestEntity() {
    PluginRequestEntity pluginRequestEntity = PluginRequestEntity.builder()
                                                  .name(PLUGIN_REQUEST_NAME)
                                                  .creator(PLUGIN_REQUEST_CREATOR)
                                                  .packageLink(PLUGIN_REQUEST_PACKAGE_LINK)
                                                  .docLink(PLUGIN_REQUEST_DOC_LINK)
                                                  .build();
    List<PluginRequestEntity> pluginRequestEntityList = new ArrayList<>();
    pluginRequestEntityList.add(pluginRequestEntity);
    return new PageImpl<>(pluginRequestEntityList);
  }

  private AppConfig mockAppConfig() {
    AppConfig appConfig = new AppConfig();
    appConfig.setEnabled(true);
    return appConfig;
  }
}
